package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.message.impl.HintArrowMessage
import gg.rsmod.game.message.impl.HintArrowTileMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N522 bundle 3c: the OSRS Revenant maledictus, the demi-boss of the Revenant Caves (npc 11246, OsrsNpcImportTool rev-maledictus).
 *
 * Sources: OSRS Wiki "Revenant maledictus" (stats, attack speed 5, max hit 30, 1,250 hitpoints, "functionally multi-combat ... will
 * attack all players in range simultaneously", the four attacks, the drops, Forinthry Surge) and the Near Reality donor
 * (tamerab1/Zaryx-Rsps core-restricted wilderness/revenant/npc/RevenantMaledictus.kt): the eleven spawn tiles with their north /
 * middle / south hint, the rolling spawn chance (the combat levels of every revenant slain, out of 10,000; reset on a spawn) with a
 * 30 minute cooldown, the attack roll (3 in 19 blood, 2 in 19 ice, 1 in 19 air surge, else the standard spike), the blood / ice
 * marker on the players' tiles 3 ticks before it lands, the surge's 5x5 blast, the messages. Animations: RuneLite gameval
 * NPC_REVENANT_SUPERIOR_*; graphics: SpotanimID REVENANT_SUPERIOR_* / WINDSURGE_TRAVEL / WINDBLAST_IMPACT / SHADOW_BURST_IMPACT.
 * Where the two differ the wiki wins: attack speed 5 (donor 6), blood heals half the damage (donor 65%), the top damager gets the
 * artefact + TWO revenant dragon rolls (donor rolled three) and only the OTHER damagers get the blighted supplies.
 */
object RevenantMaledictus {
    const val NPC = Npcs.REVENANT_MALEDICTUS_OSRS

    const val ANIM_SURGE = 16168 // NPC_REVENANT_SUPERIOR_ATTACK_MELEE 9277 (donor: the targeted air surge)
    const val ANIM_ICE = 16169 // _ATTACK_STUN 9278
    const val ANIM_BLOOD = 16170 // _ATTACK_HEAL 9279
    const val ANIM_DEATH = 16171 // _DEATH 9280
    const val ANIM_SPAWN = 16172 // _SPAWN 9281
    const val ANIM_STANDARD = 16173 // _ATTACK_DOT 9282

    const val GFX_STANDARD = 3205 // REVENANT_SUPERIOR_STANDARD_ATTACK 2033
    const val GFX_SURGE_RING = 3206 // REVENANT_SUPERIOR_SPECIAL_2_IMPACT 2034
    const val GFX_ICE_IMPACT = 3207 // REVENANT_SUPERIOR_BARRAGE_IMPACT 2035
    const val GFX_SURGE_TRAVEL = 3067 // WINDSURGE_TRAVEL 1456
    const val GFX_SURGE_CENTRE = 3106 // WINDBLAST_IMPACT 134
    const val GFX_MARKER = 3209 // SHADOW_BURST_IMPACT 382 (donor: the mark on the targeted tiles)

    const val MAX_HIT = 30
    const val BLOOD_MAX = 30
    const val SURGE_MAX = 25
    const val ATTACK_RANGE = 10
    const val MARK_TICKS = 3
    const val FREEZE_TICKS = 6 // donor freeze(6) + attackingDelay = now + 3600 ms (six 600-ms ticks)
    val ICE_ATTACK_LOCK = TimerKey(resetOnDeath = true)
    const val SPAWN_DENOMINATOR = 10_000
    const val COOLDOWN_TICKS = 4_500 // 45 minutes, Wiki revision 15293777

    /** Forinthry Surge: +15% accuracy and damage against revenants for 30 minutes (OSRS Wiki). */
    val FORINTHRY_SURGE = TimerKey(persistenceKey = "forinthry_surge", resetOnDeath = false)
    const val SURGE_TICKS = 3_000

    enum class Spawn(val tile: Tile, val hint: String) {
        CYCLOPS_DEMON(Tile(3173, 10190), "north"),
        DRAGON(Tile(3235, 10200), "north"),
        IMPS(Tile(3214, 10191), "north"),
        HELLHOUNDS(Tile(3243, 10171), "middle"),
        EASTERN_PYREFIEND(Tile(3250, 10142), "middle"),
        WESTERN_PYREFIEND(Tile(3173, 10153), "middle"),
        DARK_BEAST(Tile(3206, 10161), "middle"),
        NORTHERN_ORK(Tile(3222, 10129), "middle"),
        SOUTHERN_ORK(Tile(3213, 10094), "south"),
        SOUTHERN_DEMON(Tile(3159, 10113), "south"),
        SOUTHERN_DEMON_OUTSIDE(Tile(3185, 10116), "south"),
        ;

        /** Donor text, the players in the caves. */
        fun localMessage(): String = "<col=ef1020>A superior revenant has awoken in the $hint of the caves.</col>"

        /** Donor text, everyone online. */
        fun globalMessage(): String = "<col=ef1020>A superior revenant has awoken in the $hint of the revenant caves.</col>"
    }

    private var rollingNumerator = 0
    private var lastSpawnCycle = Int.MIN_VALUE / 2
    var instance: Npc? = null
        private set

    fun active(): Boolean = instance?.let { it.isSpawned() && !it.isDead() } == true

    /** A revenant of the caves died: its combat level joins the rolling chance; a hit spawns the maledictus. */
    fun onRevenantDeath(world: World, combatLevel: Int): Boolean {
        if (active()) return false
        if (world.currentCycle - lastSpawnCycle < COOLDOWN_TICKS) return false
        rollingNumerator += combatLevel
        if (world.random(SPAWN_DENOMINATOR - 1) >= rollingNumerator) return false
        return spawn(world, Spawn.values()[world.random(Spawn.values().size - 1)])
    }

    /** Also the ::maledictus test path. */
    fun spawn(world: World, at: Spawn): Boolean {
        if (active()) return false
        val npc = Npc(NPC, Tile(at.tile), world)
        // setNpcDefaults recomputes respawns during world.spawn; use the persistent per-NPC override.
        npc.respawnOverride = false
        if (!world.spawn(npc)) return false
        npc.animate(ANIM_SPAWN)
        instance = npc
        lastSpawnCycle = world.currentCycle
        rollingNumerator = 0
        world.players.forEach { p ->
            if (RevenantCaveFee.inCaves(p.tile)) {
                p.message(at.localMessage())
                syncHint(p)
            } else {
                p.message(at.globalMessage())
            }
        }
        return true
    }

    private const val HINT_SLOT = 1
    private val LAST_HINT = AttributeKey<Int>()

    /** Tile hints work outside the local NPC update radius, and survive entering/relogging during a spawn. */
    fun syncHint(player: Player) {
        val npc = instance?.takeIf { active() && it.world === player.world && RevenantCaveFee.inCaves(player.tile) }
        val centre = npc?.getCentreTile()
        val previous = player.attr[LAST_HINT]
        if (centre == null) {
            if (previous != null) player.write(HintArrowMessage(slot = HINT_SLOT, type = HintArrowMessage.TYPE_CLEAR))
            player.attr.remove(LAST_HINT)
        } else if (previous != centre.as30BitInteger) {
            player.write(HintArrowTileMessage(slot = HINT_SLOT, x = centre.x, z = centre.z, level = centre.height, drawDistance = 255))
            player.attr[LAST_HINT] = centre.as30BitInteger
        }
    }

    fun clearHints(world: World) {
        world.players.forEach { p ->
            if (p.attr.has(LAST_HINT)) p.write(HintArrowMessage(slot = HINT_SLOT, type = HintArrowMessage.TYPE_CLEAR))
            p.attr.remove(LAST_HINT)
        }
    }

    /** "will attack all players in range simultaneously" - the living players of the caves within [ATTACK_RANGE]. */
    fun targets(npc: Npc): List<Player> {
        val list = mutableListOf<Player>()
        npc.world.players.forEach { p ->
            if (p.isOnline && !p.isDead() && !p.invisible && p.tile.height == npc.tile.height && RevenantCaveFee.inCaves(p.tile) &&
                p.tile.isWithinRadius(npc.getCentreTile(), ATTACK_RANGE) && npc.hasLineOfSightTo(p, projectile = true, maximumDistance = ATTACK_RANGE)
            ) {
                list += p
            }
        }
        return list
    }

    /** The players who damaged it, the top damager first. */
    fun damagers(npc: Npc): List<Player> {
        val list = mutableListOf<Player>()
        npc.world.players.forEach { if (npc.damageMap.getDamageFrom(it) > 0) list += it }
        return list.sortedByDescending { npc.damageMap.getDamageFrom(it) }
    }

    /**
     * OSRS Wiki drops: the top damager an ancient emblem or totem (ours: the tier 1 / tier 2 Deadman emblem, the bundle 3b mapping) and
     * two rolls on the revenant dragon's table, plus Forinthry Surge with an Amulet of avarice; every other damager one blighted super
     * restore and two pieces of blighted food under him.
     */
    fun onDeath(npc: Npc) {
        val world = npc.world
        clearHints(world)
        instance = null
        val damagers = damagers(npc)
        val top = damagers.firstOrNull() ?: return
        val tile = npc.tile
        val artefact = if (world.random(1) == 0) RevenantCaveRevenants.emblem(1) else RevenantCaveRevenants.emblem(2)
        val loot = mutableListOf(Item(artefact))
        repeat(2) { loot += DropTableFactory.getDrop(top, Npcs.REVENANT_DRAGON_OSRS, rate = DropTableFactory.killRate(top)).orEmpty() }
        DropTableFactory.giveDrop(world, top, NPC, tile, loot, allowLootShare = false)
        if (RevenantGear.wearsAvarice(top)) {
            top.timers[FORINTHRY_SURGE] = SURGE_TICKS
            top.message("You feel the power of Forinthry surge granting you additional strength against the revenants.")
        }
        damagers.drop(1).forEach { p ->
            val food = listOf(Items.BLIGHTED_ANGLERFISH, Items.BLIGHTED_KARAMBWAN, Items.BLIGHTED_MANTA_RAY)[world.random(2)]
            DropTableFactory.giveDrop(world, p, NPC, p.tile, listOf(Item(Items.BLIGHTED_SUPER_RESTORE4), Item(food, 2)), allowLootShare = false)
        }
    }

    fun surge(player: Player): Boolean = player.timers.has(FORINTHRY_SURGE)

    fun protectedDamage(damage: Int, protected: Boolean): Int = if (protected) damage / 2 else damage

    object Combat : CombatScript() {
        override val ids = intArrayOf(NPC)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = ATTACK_RANGE, projectile = true)) {
                    attack(npc)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        /** The donor's roll out of 19. */
        fun attackFor(roll: Int): String =
            when {
                roll < 3 -> "blood"
                roll < 5 -> "ice"
                roll == 5 -> "surge"
                else -> "standard"
            }

        private fun attack(npc: Npc) {
            val players = targets(npc)
            if (players.isEmpty()) return
            val action = attackFor(npc.world.random(18))
            gg.rsmod.plugins.content.combat.audio.WildernessBossAudio.begin(npc, action)
            when (action) {
                "blood" -> marked(npc, players, ANIM_BLOOD) { p ->
                    val damage = protectedDamage(npc.world.random(BLOOD_MAX), p.isProtectedFrom(CombatClass.MAGIC))
                    npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 0, hitType = HitType.MAGIC, applyDeflectProtection = false, onHit = { hit ->
                        val dealt = hit.hit.hitmarks.sumOf { h -> h.damage }
                        if (dealt > 0 && !npc.isDead()) {
                            npc.setCurrentLifepoints((npc.getCurrentLifepoints() + dealt / 2).coerceAtMost(npc.getMaximumLifepoints()))
                        }
                    })
                }
                "ice" -> marked(npc, players, ANIM_ICE) { p ->
                    val damage = protectedDamage(npc.world.random(MAX_HIT), p.isProtectedFrom(CombatClass.MAGIC))
                    npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 0, hitType = HitType.MAGIC, applyDeflectProtection = false)
                    p.graphic(GFX_ICE_IMPACT)
                    p.freeze(FREEZE_TICKS, npc) { p.message("You have been frozen in place by the Revenant Maledictus' attack!") }
                    // The ice attack also prevents attacking; a normal freeze only stops walking.
                    p.timers[ICE_ATTACK_LOCK] = FREEZE_TICKS
                }
                "surge" -> surge(npc, players[npc.world.random(players.size - 1)], players)
                else -> standard(npc, players)
            }
        }

        private fun standard(npc: Npc, players: List<Player>) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            npc.animate(ANIM_STANDARD)
            players.forEach { p ->
                // Donor projectile (2033, 75, 25, 25, 15, 18, 64, 5): start 25, end 25, delay 15, angle 18.
                npc.world.spawn(npc.createProjectile(p, GFX_STANDARD, startHeight = 25, endHeight = 25, delay = 15, angle = 18))
                val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(p), p.getCentreTile())
                val land = RangedCombatFormula.getUnprotectedAccuracy(npc, p) >= npc.world.randomDouble()
                val max = RevenantGear.revenantMaxHitAgainst(p, MAX_HIT)
                val damage = protectedDamage(rollDamage(0.0, max), p.isProtectedFrom(CombatClass.RANGED))
                npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = land && damage > 0, delay = delay, hitType = HitType.RANGE, applyDeflectProtection = false)
            }
        }

        /** Blood / ice: the players' tiles are marked; whoever still stands there [MARK_TICKS] ticks later is hit. */
        private fun marked(npc: Npc, players: List<Player>, anim: Int, onLand: (Player) -> Unit) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(anim)
            val world = npc.world
            val life = WildernessBossLife.token(npc)
            val marks = players.map { it to Tile(it.tile) }
            marks.forEach { (_, t) -> world.spawn(TileGraphic(t, GFX_MARKER, 0)) }
            world.queue {
                wait(MARK_TICKS)
                if (!WildernessBossLife.active(npc, life)) return@queue
                marks.forEach { (p, t) -> if (p.isOnline && !p.isDead() && p.tile.sameAs(t)) onLand(p) }
            }
        }

        /** Air surge at one player's tile: up to 25 to everyone within 2 tiles (5x5), halved by Protect from Magic. */
        private fun surge(npc: Npc, aim: Player, players: List<Player>) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(ANIM_SURGE)
            val world = npc.world
            val life = WildernessBossLife.token(npc)
            val tile = Tile(aim.tile)
            // Donor projectile (1456, 85, 0, 25, 50, 45, 64, 5): start 85, end 0, delay 25.
            world.spawn(npc.createProjectile(tile, GFX_SURGE_TRAVEL, startHeight = 85, endHeight = 0, delay = 25, angle = 16, lifespan = 25 + 50))
            world.queue {
                wait(3)
                if (!WildernessBossLife.active(npc, life)) return@queue
                world.spawn(TileGraphic(tile, GFX_SURGE_CENTRE, 0))
                for (dx in -2..2) for (dz in -2..2) {
                    if (dx == 0 && dz == 0) continue
                    world.spawn(TileGraphic(tile.transform(dx, dz), GFX_SURGE_RING, 0, delay = if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == 1) 15 else 25))
                }
                players.forEach { p ->
                    if (!p.isOnline || p.isDead() || p.tile.height != tile.height) return@forEach
                    if (kotlin.math.abs(p.tile.x - tile.x) > 2 || kotlin.math.abs(p.tile.z - tile.z) > 2) return@forEach
                    val max = if (p.isProtectedFrom(CombatClass.MAGIC)) SURGE_MAX / 2.0 else SURGE_MAX.toDouble()
                    npc.dealHit(target = p, maxHit = max, landHit = true, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                }
            }
        }
    }
}
