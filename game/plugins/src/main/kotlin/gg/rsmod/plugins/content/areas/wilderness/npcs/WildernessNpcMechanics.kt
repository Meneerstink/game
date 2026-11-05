package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LAST_MAP_BUILD_TIME
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula
import gg.rsmod.plugins.content.combat.formula.DragonfireTable
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.skills.mining.PickaxeType
import gg.rsmod.plugins.content.skills.woodcutting.AxeType
import kotlin.math.floor

/** Behaviour backed by N522-NPCS-ONDERZOEK sections 6/7/9/10. Unknown timing/odds do not receive fallback values. */
object WildernessNpcMechanics {
    val TRUNK_KILLER = AttributeKey<String>()
    val TRUNK_EXPIRES = AttributeKey<Int>()
    val CHOP_ATTEMPTS = AttributeKey<Int>()
    val RESOURCE_ACTIVE = AttributeKey<Boolean>()
    private val DEATH_HANDLED = AttributeKey<Boolean>()

    const val TRUNK_TICKS = 101
    const val ENT_RESPAWN_AFTER_TRUNK = 15
    const val ROCK_TICKS = 100 // report: 60 seconds at 0.6 seconds per server tick

    /** OSRS Wilderness ignores combat-level tolerance. Shared server ten-minute tolerance still applies. */
    fun setupAggro(npc: Npc) {
        if (npc.combatDef.aggressiveRadius <= 0) return
        npc.aggroCheck = { n, p ->
            !p.isDead() && p.isOnline && n.tile.height == p.tile.height &&
                (n.combatDef.aggressiveTimer == Int.MAX_VALUE ||
                    n.world.currentCycle - (p.attr[LAST_MAP_BUILD_TIME] ?: 0) <= n.combatDef.aggressiveTimer)
        }
    }

    fun chooseDragonfire(roll: Int): Boolean = roll == 0 // report: 1/6, after a reachable melee-range check

    suspend fun lavaCombat(task: QueueTask) {
        val npc = task.npc
        var target = npc.getCombatTarget() ?: return
        while (task.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.canAttackMelee(task, target, moveIfNeeded = true)) {
                if (chooseDragonfire(npc.world.random(5))) {
                    npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                    npc.animate(15610, priority = true) // imported upstream 81, NEVER gfx sequence 86
                    // Report leaves the fire gfx/projectile assignment unknown. Reuse the existing chromatic protection formula.
                    npc.dealHit(target = target, formula = DragonfireFormula(DragonfireTable.Type.CHROMATIC), delay = 2)
                } else {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
                    npc.animate(16160)
                    npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
                }
                npc.postAttackLogic(target)
            }
            task.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    suspend fun druidCombat(task: QueueTask) {
        val npc = task.npc
        var target = npc.getCombatTarget() ?: return
        while (task.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(task, target, distance = 8, projectile = true)) {
                npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                npc.animate(711)
                // A sourced magic max of 17; choosing a named player spell would invent its projectile and base max.
                val lands = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
                npc.dealHit(target = target, maxHit = 17.0, landHit = lands, delay = 1, hitType = HitType.MAGIC)
                npc.postAttackLogic(target)
            }
            // Tele Block duration/frequency/accuracy and pull timing/landing tile are unknown in section 9.
            // Do not apply a player-spell duration or randomly teleport the target. These remain release blockers.
            task.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** Report's target selection, available for the pull once timing and landing tile have been sourced. */
    fun pullCandidate(npc: Npc, players: Iterable<Player>, anchored: (Player) -> Boolean): Player? =
        players.filter { p ->
            p.isOnline && !p.isDead() && p.tile.height == npc.tile.height &&
                p.tile.getDistance(npc.tile) <= 20 && npc.damageMap.getDamageFrom(p) > 0 &&
                p.getCombatTarget() !== npc && !anchored(p)
        }.maxByOrNull { npc.damageMap.getDamageFrom(it) }

    fun protectResource(npc: Npc) {
        npc.respawns = false
        npc.walkRadius = 0
        npc.followRadius = 0
        npc.hitModifier = { hit -> hit.hitmarks.forEach { it.damage = 0 } }
    }

    fun golemArmour(hit: Hit) {
        hit.hitmarks.forEach { if (it.damage > 0) it.damage = (it.damage - 4).coerceAtLeast(0) }
        // Exact rounding of the Mining/90 melee modifier is unknown; no guessed formula is installed.
    }

    /** New NPC instance: pending hits/combat queues against the slain NPC cannot hit or awaken its resource form. */
    fun entDied(npc: Npc): Npc? {
        if (npc.attr[DEATH_HANDLED] == true) return null
        npc.attr[DEATH_HANDLED] = true
        npc.respawns = false
        val world = npc.world
        val killer = npc.damageMap.getMostDamage() as? Player
        val trunk = Npc(Npcs.ENT_TRUNK_OSRS, Tile(npc.tile), world)
        trunk.respawnOverride = false
        trunk.attr[RESOURCE_ACTIVE] = true
        trunk.attr[TRUNK_EXPIRES] = world.currentCycle + TRUNK_TICKS
        trunk.attr[CHOP_ATTEMPTS] = 0
        if (killer != null) trunk.attr[TRUNK_KILLER] = killer.username.lowercase()
        val spawned = world.spawn(trunk)
        if (!spawned) sourceGap("Ent trunk could not spawn; the original Ent's respawn remains scheduled")
        protectResource(trunk)
        val spawn = Tile(npc.spawnTile)
        val radius = npc.walkRadius
        world.queue {
            wait(TRUNK_TICKS)
            trunk.attr[RESOURCE_ACTIVE] = false
            if (spawned) world.remove(trunk)
            wait(ENT_RESPAWN_AFTER_TRUNK)
            val ent = Npc(Npcs.ENT_OSRS, spawn, world)
            ent.walkRadius = radius
            if (!world.spawn(ent)) sourceGap("Ent respawn failed at $spawn")
        }
        return trunk
    }

    fun golemDied(npc: Npc): Npc? {
        if (npc.attr[DEATH_HANDLED] == true) return null
        npc.attr[DEATH_HANDLED] = true
        npc.respawns = false
        val world = npc.world
        val rocks = Npc(Npcs.RUNITE_ROCKS_OSRS, Tile(npc.tile), world)
        rocks.respawnOverride = false
        rocks.attr[RESOURCE_ACTIVE] = true
        val spawned = world.spawn(rocks)
        if (!spawned) sourceGap("Runite rocks could not spawn")
        protectResource(rocks)
        world.queue {
            wait(ROCK_TICKS)
            rocks.attr[RESOURCE_ACTIVE] = false
            if (spawned) world.remove(rocks)
            // No auto-respawn: report section 10 explicitly leaves the five-minute respawn anchor unresolved.
            sourceGap("Runite Golem respawn anchor is unknown; no second golem scheduled")
        }
        return rocks
    }

    fun canChop(trunk: Npc, player: Player): Boolean =
        trunk.id == Npcs.ENT_TRUNK_OSRS && trunk.isSpawned() && trunk.attr[RESOURCE_ACTIVE] == true &&
            trunk.attr[TRUNK_KILLER] == player.username.lowercase() &&
            player.isOnline && !player.isDead() && player.tile.height == trunk.tile.height &&
            adjacentToResource(trunk, player.tile) &&
            trunk.world.currentCycle < (trunk.attr[TRUNK_EXPIRES] ?: 0) && (trunk.attr[CHOP_ATTEMPTS] ?: 0) < 25

    private val axeOffsets = mapOf(
        AxeType.BRONZE to 1, AxeType.IRON to 1, AxeType.STEEL to 6, AxeType.BLACK to 11,
        AxeType.MITHRIL to 21, AxeType.ADAMANT to 31, AxeType.RUNE to 41, AxeType.DRAGON to 61,
    )

    fun usableAxe(player: Player): AxeType? = axeOffsets.keys.filter {
        player.skills.getMaxLevel(Skills.WOODCUTTING) >= it.level &&
            (player.inventory.contains(it.item) || player.equipment.contains(it.item))
    }.maxByOrNull { axeOffsets.getValue(it) }

    /** Pinned Ent calculator; current level controls success, base level controls log tiers (report prose). */
    fun chopSuccess(current: Int, medium: Boolean): Double {
        val low = if (medium) 22 else 16
        val high = if (medium) 102 else 90
        return ((floor(low * (99.0 - current) / 98 + high * (current - 1.0) / 98 + 0.5) + 1) / 256).coerceIn(0.0, 1.0)
    }

    /** Null at the explicit unresolved base-45 / cap==low boundaries; never choose an undocumented outcome there. */
    fun logForRoll(base: Int, current: Int, axe: AxeType, roll: Int): Int? {
        val offset = axeOffsets[axe] ?: return null
        if (base == 45 || base != current) return null // explicit report conflict: calculator has one level, prose distinguishes both
        val cap = current + offset
        if (cap > 170 || current < 1) return null // no extrapolation beyond the sourced calculator intervals
        val lows = listOf(0, 11, 21, 36, 51, 76)
        if (cap in lows) return null
        if (roll !in 0..cap || roll <= 10) return -1
        val column = when { base <= 15 -> 0; base <= 30 -> 1; base <= 45 -> 2; base <= 60 -> 3; base <= 74 -> 4; else -> 5 }
        val row = when { roll <= 20 -> 0; roll <= 35 -> 1; roll <= 50 -> 2; roll <= 75 -> 3; else -> 4 }
        val tiers = arrayOf(
            intArrayOf(0, 0, 0, 1, 1, 1), intArrayOf(0, 0, 1, 1, 1, 2),
            intArrayOf(0, 0, 1, 1, 2, 3), intArrayOf(0, 0, 1, 2, 3, 4), intArrayOf(0, 1, 2, 3, 4, 5),
        )
        return intArrayOf(Items.LOGS_NOTED, Items.OAK_LOGS_NOTED, Items.WILLOW_LOGS_NOTED,
            Items.MAPLE_LOGS_NOTED, Items.YEW_LOGS_NOTED, Items.MAGIC_LOGS_NOTED)[tiers[row][column]]
    }

    suspend fun chop(task: QueueTask, trunk: Npc) {
        val player = task.player
        while (canChop(trunk, player)) {
            val axe = usableAxe(player) ?: break
            val base = player.skills.getMaxLevel(Skills.WOODCUTTING)
            val current = player.skills.getCurrentLevel(Skills.WOODCUTTING)
            val offset = axeOffsets.getValue(axe)
            val roll = player.world.random((current + offset).coerceAtLeast(0))
            val logs = logForRoll(base, current, axe, roll)
            if (logs == null) {
                sourceGap("Ent log selection at base=$base current=$current offset=$offset is unresolved")
                break
            }
            if (player.inventory.isFull && (logs < 0 || !player.inventory.contains(logs))) break
            trunk.attr[CHOP_ATTEMPTS] = (trunk.attr[CHOP_ATTEMPTS] ?: 0) + 1
            player.animate(axe.animation, idleOnly = true)
            // Up to 25 attempts in 101 ticks (report section 7): four ticks per attempt.
            task.wait(4)
            if (!canChopAfterWait(trunk, player) || usableAxe(player) != axe) break
            if (logs >= 0 && player.world.randomDouble() < chopSuccess(current, WildernessNpcDrops.mediumDiary(player))) {
                if (!player.inventory.isFull || player.inventory.contains(logs)) {
                    player.inventory.add(logs, 2)
                    player.addXp(Skills.WOODCUTTING, 25.0, checkBrawlingGloves = true)
                }
            }
            // Nest occurrence is 1/256, but the Ent-specific contents/modifiers are unknown: no invented nest distribution.
        }
        player.animate(Anims.RESET)
    }

    private fun canChopAfterWait(trunk: Npc, player: Player): Boolean =
        trunk.isSpawned() && trunk.attr[RESOURCE_ACTIVE] == true && trunk.attr[TRUNK_KILLER] == player.username.lowercase() &&
            player.isOnline && !player.isDead() && player.tile.height == trunk.tile.height &&
            adjacentToResource(trunk, player.tile) &&
            trunk.world.currentCycle < (trunk.attr[TRUNK_EXPIRES] ?: 0)

    fun usablePickaxe(player: Player): PickaxeType? = PickaxeType.values.filter {
        player.skills.getMaxLevel(Skills.MINING) >= it.level &&
            (player.inventory.contains(it.item) || player.equipment.contains(it.item))
    }.minByOrNull { it.ticksBetweenRolls }

    fun canMine(rocks: Npc, player: Player): Boolean =
        rocks.id == Npcs.RUNITE_ROCKS_OSRS && rocks.isSpawned() && rocks.attr[RESOURCE_ACTIVE] == true &&
            player.skills.getCurrentLevel(Skills.MINING) >= 85 && usablePickaxe(player) != null &&
            player.isOnline && !player.isDead() && player.tile.height == rocks.tile.height &&
            adjacentToResource(rocks, player.tile) && !player.inventory.isFull

    /** Exact footprint, including both sides of size-2 trunks; getFrontFacingTile rounds an even-sized NPC past its edge. */
    private fun adjacentToResource(npc: Npc, tile: Tile): Boolean {
        if (tile.height != npc.tile.height) return false
        val size = npc.getSize().coerceAtLeast(1)
        val nearestX = tile.x.coerceIn(npc.tile.x, npc.tile.x + size - 1)
        val nearestZ = tile.z.coerceIn(npc.tile.z, npc.tile.z + size - 1)
        return tile.getDistance(Tile(nearestX, nearestZ, npc.tile.height)) <= 1
    }

    fun mine(rocks: Npc, player: Player) {
        if (!canMine(rocks, player)) return
        // Mining.mineRock is object-based and has ordinary rock odds. Applying those to this NPC would invent the report's
        // explicitly unknown 2026 Golem success chance/diary chance. Eligibility and shared tool metadata are wired; yield is blocked.
        sourceGap("Runite rocks mining success and diary extra-ore odds are unknown; no ore or XP granted")
    }

    private fun sourceGap(message: String) = Trace.problem(Trace.Cat.COMBAT) { "N522 SOURCE_GAP: $message" }
}
