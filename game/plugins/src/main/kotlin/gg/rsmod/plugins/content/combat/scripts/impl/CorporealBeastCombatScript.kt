package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import java.lang.ref.WeakReference

/**
 * Corporeal Beast (npc 8133) and its Dark energy core (npc 8127).
 *
 * Merged from Void (corporeal_beasts_lair data + CorporealBeast.kt / DarkEnergyCore.kt) and Novite
 * (CorporealBeastCombat / CorporealBeast / DarkEnergyCore):
 * - Attack pick (Novite): stomp (anim 10496, gfx 1834) when a player stands under it, otherwise 2/5 melee
 *   (10057 slap / 10058 swipe, magic instead when out of reach), 1/5 spiky ball (proj 1825, max 65),
 *   1/5 stat-drain ball (proj 1823, max 55, drains Magic/Summoning/Prayer), 1/5 scatter ball (proj 1824,
 *   impact gfx 1806; Void damage: centre 40 / adjacent 30, six splashes 30 / adjacent 20).
 * - OSRS Wiki "Corporeal Beast/Strategies" (2026-09-14): the stomp "will always deal 30–51 damage" and cannot be blocked by
 *   protection prayers; melee "can hit up to 33" (Void table); the drain ball "has a chance to drain Magic or Prayer by 1 or 2"
 *   (SOURCE_GAP: the chance is unquantified, so it drains on every cast); "Protect from Magic will block 1/3 of the damage from
 *   the magic attacks" (Deflect Magic treated the same, ADAPTED: 667 curse).
 * - "50% damage reduction against any melee and ranged weapon that is not a Corpbane weapon. These weapons must also be on the
 *   stab attack style." Magic deals full damage. Corpbane = the OSRS Wiki "Corpbane weapons" list ([CORPBANE_WEAPONS]).
 * - Regeneration + stomp share one 7-tick timer (OSRS Wiki Strategies): stomp every player underneath; empty lair heals 75, then
 *   +10 cumulatively per tick (75, 85, ...); full heal once nobody has attacked it for 300 ticks; 8+ players restore 25 + 5 per
 *   player (Void; the wiki names only the 5 per player). SOURCE_GAP: drained-stat regeneration every 20 ticks not built here.
 * - Dark energy core (OSRS Wiki "Dark energy core", 2026-09-14; visuals from Void spawnDarkCore): 1/8 chance after a hit
 *   of 32+ or when the beast attacks below 1,000 hitpoints, one core at a time. It flies to a player (proj 1828) and every
 *   2 ticks (12 while poisoned, Void value: SOURCE_GAP) deals 5-13 to every player in its 3x3 area, healing the beast 50%
 *   rounded down. With nobody in range it jumps (anim 10393, proj 1828), northernmost player first, then east. Killed
 *   mid-jump, it does not return for the rest of the fight. It has 25 hitpoints; it vanishes when the beast dies or the
 *   lair empties.
 */
object CorporealBeastCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.CORPOREAL_BEAST)

    const val DARK_ENERGY_CORE = Npcs.DARK_ENERGY_CORE_8127

    private const val MELEE_MAX = 33.0
    private const val STOMP_MIN = 30.0
    private const val STOMP_MAX = 51.0
    private const val MAGIC_MAX = 65.0
    private const val DRAIN_MAX = 55.0
    private const val SCATTER_CENTRE_MAX = 40.0
    private const val SCATTER_CENTRE_ADJACENT_MAX = 30.0
    private const val SCATTER_SPLASH_MAX = 30.0
    private const val SCATTER_SPLASH_ADJACENT_MAX = 20.0
    const val MAGIC_PRAYER_MULTIPLIER = 2.0 / 3.0
    const val DRAIN_MIN = 1
    const val DRAIN_MAX_POINTS = 2

    /**
     * OSRS Wiki "Corpbane weapons" (2026-09-14): spears, halberds and others; "The poisoned variants of these weapons are also Corpbane".
     * Crystal halberd is listed as the active variant only. ADAPTED names: 667 degrade suffixes (Guthan's "100".."0", Vesta's "(deg)")
     * are the same weapon. Not listed, so not Corpbane: hastae, Dungeoneering spears, corrupt PvP spears, "Halberd", "Anger spear"
     * (owner question 18).
     */
    val CORPBANE_WEAPONS = setOf(
        "Bronze spear", "Iron spear", "Steel spear", "Black spear", "Mithril spear", "Adamant spear", "Rune spear", "Dragon spear",
        "Bone spear", "Gilded spear", "Leaf-bladed spear", "Guthan's warspear", "Zamorakian spear", "Sunspear", "Vesta's spear",
        "Bronze halberd", "Iron halberd", "Steel halberd", "Black halberd", "White halberd", "Mithril halberd", "Adamant halberd",
        "Rune halberd", "Dragon halberd", "Crystal halberd", "Noxious halberd",
        "Osmumten's fang", "Thunder khopesh", "King's barrage",
    )

    private val POISON_OR_DEGRADE_SUFFIX = Regex("""( \((p|p\+|p\+\+|kp|deg)\)| (100|75|50|25|0))$""")

    fun isCorpbaneWeapon(name: String): Boolean = name.replace(POISON_OR_DEGRADE_SUFFIX, "") in CORPBANE_WEAPONS

    /** Only melee and ranged damage is halved, and not from a Corpbane weapon on the stab style. */
    fun halvesDamage(
        hitType: HitType,
        corpbaneOnStab: Boolean,
    ): Boolean = (hitType == HitType.MELEE || hitType == HitType.RANGE) && !corpbaneOnStab

    private const val ANIM_SLAP = 10057
    private const val ANIM_SWIPE = 10058
    private const val ANIM_MAGIC = 10410
    private const val ANIM_STOMP = 10496
    private const val GFX_STOMP = 1834
    private const val PROJ_STAT_DRAIN = 1823
    private const val PROJ_SCATTER = 1824
    private const val PROJ_SPIKY_BALL = 1825
    private const val PROJ_CORE_TRAVEL = 1828
    private const val GFX_MAGIC_IMPACT = 1806
    private const val ANIM_CORE_TAKE_OFF = 10393

    // OSRS Wiki Dark energy core (raw wikitext 2026-09-14): "The dark core has a 1 in 8 chance of spawning after the Corporeal Beast
    // receives 32 or more damage from a hit, or when it attacks while below 1,000 Hitpoints." World.random(bound) is inclusive,
    // so the roll is random(CORE_SPAWN_CHANCE - 1) == 0.
    const val CORE_SPAWN_CHANCE = 8
    const val CORE_SPAWN_HIT_THRESHOLD = 32
    const val CORE_ATTACK_SPAWN_BELOW_LIFEPOINTS = 1000
    // "damage players within a 3x3 (1 tile) radius of it, dealing 5–13 damage every 2 ticks (1.2 seconds) and healing the
    // Corporeal beast for 50% (rounded down) of the damage dealt."
    const val CORE_DAMAGE_MIN = 5
    const val CORE_DAMAGE_MAX = 13
    const val CORE_RADIUS = 1
    const val CORE_ATTACK_SPEED = 2
    // SOURCE_GAP: the wiki only says a poisoned core's attacks "become significantly slower"; 12 ticks is Void's donor value.
    const val CORE_POISONED_ATTACK_SPEED = 12
    // OSRS Wiki "Corporeal Beast/Strategies" (2026-09-14): "If there are no players in the cave, it will heal 75 hitpoints every 7 game
    // ticks (4.2 seconds), and heals an additional 10 hitpoints cumulatively every 7 ticks thereafter. This timer is shared with the
    // Corporeal Beast's stomp attack timer. It will fully heal after three minutes if no one has attacked it."
    const val REGEN_INTERVAL = 7
    const val EMPTY_LAIR_FIRST_HEAL = 75
    const val EMPTY_LAIR_HEAL_STEP = 10
    const val FULL_HEAL_AFTER_TICKS = 300

    /** Heal on the [step]-th consecutive empty-lair timer tick (0-based): 75, 85, 95, ... */
    fun emptyLairHeal(step: Int): Int = EMPTY_LAIR_FIRST_HEAL + EMPTY_LAIR_HEAL_STEP * step

    val LAIR_X = 2972..3001
    val LAIR_Z = 4370..4397
    const val LAIR_HEIGHT = 2

    private val CORE = AttributeKey<WeakReference<Npc>>()
    private val CORE_OWNER = AttributeKey<WeakReference<Npc>>()
    private val REGEN_TOKEN = AttributeKey<Any>()
    private val LAST_ATTACKED_CYCLE = AttributeKey<Int>()
    private val CORE_JUMPING = AttributeKey<Boolean>()
    private val CORE_DISABLED = AttributeKey<Boolean>()

    fun inLair(tile: Tile): Boolean = tile.height == LAIR_HEIGHT && tile.x in LAIR_X && tile.z in LAIR_Z

    fun playersInLair(world: World): List<Player> {
        val result = mutableListOf<Player>()
        world.players.forEach { p ->
            if (p.isOnline && !p.isDead() && inLair(p.tile)) {
                result.add(p)
            }
        }
        return result
    }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val nearby = nearbyPlayers(npc)
            if (npc.getCurrentLifepoints() < CORE_ATTACK_SPAWN_BELOW_LIFEPOINTS) {
                trySpawnCore(npc, target)
            }

            // The stomp is not part of the attack cycle: it runs on the shared 7-tick timer (startRegeneration).
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            val inMelee = distance <= 1
            when (if (inMelee) world.random(4) else 2 + world.random(2)) {
                0, 1 -> melee(npc, target, world)
                2 -> spikyBall(npc, target)
                3 -> drainBall(npc, target, world)
                else -> scatterBall(npc, target, nearby, world)
            }
            if (!inMelee) {
                npc.moveToAttackRange(it, target, distance = 8, projectile = true)
            }

            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /**
     * Damage dealt to the beast is halved unless the attacker is a player using a spear or halberd on the
     * stab style. [damage] and the return value use the local 1:1 real-damage scale.
     */
    fun modifyIncomingDamage(
        attacker: Pawn,
        hitType: HitType,
        damage: Int,
    ): Int {
        if (damage <= 0) return damage
        val corpbaneOnStab = attacker is Player && isCorpbaneWeapon(attacker) && CombatConfigs.getCombatStyle(attacker) == StyleType.STAB
        return if (halvesDamage(hitType, corpbaneOnStab)) damage / 2 else damage
    }

    private fun isCorpbaneWeapon(player: Player): Boolean {
        val weapon = player.equipment[3] ?: return false
        return isCorpbaneWeapon(player.world.definitions.get(ItemDef::class.java, weapon.id).name)
    }

    /** Called from the beast's death hook: the core dies with its master. */
    fun onBeastDeath(npc: Npc) {
        val core = npc.attr[CORE]?.get() ?: return
        npc.attr.remove(CORE)
        if (core.isSpawned() && !core.isDead()) {
            npc.world.remove(core)
        }
    }

    /**
     * Called from the core's death hook so the beast may summon a new one. OSRS Wiki: "if it is killed during a jump (such as by
     * ranged/mage projectile or by a dwarf multicannon), it will not respawn for the remaining duration of the fight." The flag
     * lives in the beast's attributes, which the engine clears on death/respawn; an empty lair (the fight ending) clears it too.
     */
    fun onCoreDeath(core: Npc) {
        val beast = core.attr[CORE_OWNER]?.get() ?: return
        if (beast.attr[CORE]?.get() === core) {
            beast.attr.remove(CORE)
        }
        if (core.attr[CORE_JUMPING] == true) {
            beast.attr[CORE_DISABLED] = true
        }
    }

    /** OSRS Wiki: a hit of 32+ on the beast has a 1/8 chance of summoning the core. */
    fun onBeastDamaged(
        npc: Npc,
        attacker: Pawn,
        damage: Int,
    ) {
        npc.attr[LAST_ATTACKED_CYCLE] = npc.world.currentCycle
        if (damage < CORE_SPAWN_HIT_THRESHOLD) return
        trySpawnCore(npc, attacker)
    }

    /**
     * Starts the beast's regeneration cycle for the current life (called from on_npc_spawn, which the
     * engine also fires on every respawn).
     */
    fun startRegeneration(npc: Npc) {
        val world = npc.world
        // The engine clears the npc attributes on every death/respawn reset, which ends the previous life's loop.
        val token = Any()
        npc.attr[REGEN_TOKEN] = token
        world.queue {
            var emptyStep = 0
            while (npc.isSpawned() && !npc.isDead() && npc.attr[REGEN_TOKEN] === token) {
                wait(REGEN_INTERVAL)
                if (!npc.isSpawned() || npc.isDead() || npc.attr[REGEN_TOKEN] !== token) break
                val players = playersInLair(world)
                val count = players.size
                val max = npc.getMaximumLifepoints()
                // "a timer that checks if any players are under the Corporeal Beast every 7 ticks"; SOURCE_GAP: "may perform" is
                // unquantified, so it stomps on every check that finds a player underneath.
                val underneath = players.filter { isUnder(npc, it) }
                if (underneath.isNotEmpty()) {
                    stomp(npc, underneath)
                }
                val lastAttacked = npc.attr[LAST_ATTACKED_CYCLE]
                val sinceAttack = if (lastAttacked == null) Int.MAX_VALUE else world.currentCycle - lastAttacked
                if (npc.getCurrentLifepoints() < max && (sinceAttack < 0 || sinceAttack >= FULL_HEAL_AFTER_TICKS)) {
                    npc.setCurrentLifepoints(max)
                }
                if (count == 0) {
                    npc.setCurrentLifepoints(minOf(max, npc.getCurrentLifepoints() + emptyLairHeal(emptyStep)))
                    emptyStep++
                    npc.attr.remove(CORE_DISABLED)
                    val core = npc.attr[CORE]?.get()
                    if (core != null) {
                        npc.attr.remove(CORE)
                        if (core.isSpawned() && !core.isDead()) world.remove(core)
                    }
                } else {
                    emptyStep = 0
                    if (count >= 8) {
                        // Wiki: "eight or more players ... an additional 5 Hitpoints per player every 7 ticks". Void
                        // `levels.restore(Constitution, 250 + count * 50)` is x10 = 25 + 5 per player; the 25 base is Void-only (wiki silent).
                        npc.setCurrentLifepoints(minOf(max, npc.getCurrentLifepoints() + LifepointUnits.fromLedger(250 + count * 50)))
                    }
                }
            }
        }
    }

    private fun isUnder(
        npc: Npc,
        pawn: Pawn,
    ): Boolean {
        val size = npc.getSize()
        val dx = pawn.tile.x - npc.tile.x
        val dz = pawn.tile.z - npc.tile.z
        return pawn.tile.height == npc.tile.height && dx in 0 until size && dz in 0 until size
    }

    fun nearbyPlayers(
        npc: Npc,
        radius: Int = 12,
    ): List<Player> {
        val world = npc.world
        val result = mutableListOf<Player>()
        for (x in -radius..radius + npc.getSize()) {
            for (z in -radius..radius + npc.getSize()) {
                val tile = npc.tile.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { p ->
                    if (p.isOnline && !p.isDead() && p !in result) result.add(p)
                }
            }
        }
        return result
    }

    private fun magicProtected(target: Pawn): Boolean =
        target is Player &&
            (Prayers.isActive(target, Prayer.PROTECT_FROM_MAGIC) || AncientCurses.isCurseActive(target, AncientCurse.DEFLECT_MAGIC))

    /**
     * The beast's magic ignores the prayer short circuit in the formula; OSRS Wiki: "Protect from Magic will block 1/3 of the damage
     * from the magic attacks", so 2/3 of the max hit passes.
     */
    private fun magicHit(
        npc: Npc,
        target: Pawn,
        maxHit: Double,
        delay: Int,
        minHit: Double = 0.1,
    ): PawnHit {
        val world = npc.world
        val landHit = MagicCombatFormula.getUnprotectedAccuracy(npc, target) >= world.randomDouble()
        val protected = magicProtected(target)
        val max = if (protected) maxHit * MAGIC_PRAYER_MULTIPLIER else maxHit
        val min = if (protected) minOf(minHit, max) else minHit
        return npc.dealHit(target = target, minHit = min, maxHit = max, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    }

    private fun stomp(
        npc: Npc,
        victims: List<Player>,
    ) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(ANIM_STOMP)
        npc.graphic(GFX_STOMP)
        // OSRS Wiki: "will always deal 30–51 damage" and cannot be blocked by protection prayers (the melee accuracy roll returns 0
        // against Protect from Melee, so the stomp does not roll accuracy).
        victims.forEach { victim ->
            npc.dealHit(target = victim, minHit = STOMP_MIN, maxHit = STOMP_MAX, landHit = true, delay = 0, hitType = HitType.MELEE)
        }
    }

    private fun melee(
        npc: Npc,
        target: Pawn,
        world: World,
    ) {
        // RCV-005: melee comes from Void corporeal_beast.combat.toml via the shared model. The magic attacks stay
        // in this script: Novite lets 60% of their damage through Protect/Deflect Magic, while Void's data hit
        // would be fully blocked by the prayer (SOURCE_CONFLICT, owner to decide).
        if (gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackWith(npc, target, "melee")) return
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(if (world.random(1) == 0) ANIM_SLAP else ANIM_SWIPE)
        val landHit = MeleeCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(target = target, maxHit = MELEE_MAX, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    }

    private fun spikyBall(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        npc.world.spawn(npc.createProjectile(target, PROJ_SPIKY_BALL, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        magicHit(npc, target, MAGIC_MAX, delay)
    }

    private fun drainBall(
        npc: Npc,
        target: Pawn,
        world: World,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        world.spawn(npc.createProjectile(target, PROJ_STAT_DRAIN, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val hit = magicHit(npc, target, DRAIN_MAX, delay)
        if (target is Player) {
            hit.hit.addAction {
                // OSRS Wiki: "drain Magic or Prayer by 1 or 2" (prayer points are 1:1). Messages: Novite/Void donor text.
                val amount = world.random(DRAIN_MIN..DRAIN_MAX_POINTS)
                if (world.random(1) == 0) {
                    target.skills.decrementCurrentLevel(Skills.MAGIC, amount, capped = false)
                    target.message("Your Magic has been slightly drained!")
                } else {
                    target.setCurrentPrayerPoints((target.getCurrentPrayerPoints() - amount).coerceAtLeast(0))
                    target.message("Your Prayer has been slightly drained!")
                }
            }
        }
    }

    private fun scatterBall(
        npc: Npc,
        target: Pawn,
        nearby: List<Player>,
        world: World,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        val centre = Tile(target.tile)
        world.spawn(npc.createProjectile(centre, PROJ_SCATTER, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), centre)
        world.queue {
            wait(delay)
            world.spawn(TileGraphic(centre, GFX_MAGIC_IMPACT, height = 0))
            splash(npc, centre, nearby, SCATTER_CENTRE_MAX, SCATTER_CENTRE_ADJACENT_MAX)
            repeat(6) {
                val tile = Tile(centre.x - 3 + world.random(6), centre.z - 3 + world.random(6), centre.height)
                if (world.collision.isClipped(tile)) return@repeat
                val travel = MagicCombatStrategy.getHitDelay(centre, tile)
                world.spawn(npc.createProjectile(centre, tile, PROJ_SCATTER, ProjectileType.MAGIC))
                world.queue {
                    wait(travel)
                    world.spawn(TileGraphic(tile, GFX_MAGIC_IMPACT, height = 0))
                    splash(npc, tile, nearby, SCATTER_SPLASH_MAX, SCATTER_SPLASH_ADJACENT_MAX)
                }
            }
        }
    }

    private fun splash(
        npc: Npc,
        tile: Tile,
        nearby: List<Player>,
        centreMax: Double,
        adjacentMax: Double,
    ) {
        nearby.forEach { victim ->
            if (victim.isDead() || !victim.isOnline || victim.tile.height != tile.height) return@forEach
            val distance = victim.tile.getDistance(tile)
            val max =
                when {
                    victim.tile.sameAs(tile) -> centreMax
                    distance <= 1 -> adjacentMax
                    else -> return@forEach
                }
            magicHit(npc, victim, max, delay = 0)
        }
    }

    /** OSRS Wiki: the core damages players "within a 3x3 (1 tile) radius of it". */
    fun inCoreRange(
        core: Tile,
        player: Tile,
    ): Boolean = core.isWithinRadius(player, CORE_RADIUS)

    /** OSRS Wiki: "healing the Corporeal beast for 50% (rounded down) of the damage dealt". */
    fun coreHeal(damage: Int): Int = damage / 2

    /**
     * OSRS Wiki: "The dark energy core will usually jump toward the player standing at the northernmost position relative to the
     * Corporeal Beast. If no players are on the northern side, it will instead prioritise players to the east of the Corporeal Beast."
     * ADAPTED: "usually" and the east ordering are not quantified, so this picks the northernmost player north of the beast's centre,
     * else the easternmost player east of it; null means neither side is occupied and the caller picks a random player.
     */
    fun jumpTargetIndex(
        beastCentre: Tile,
        candidates: List<Tile>,
    ): Int? {
        val north = candidates.indices.filter { candidates[it].z > beastCentre.z }
        if (north.isNotEmpty()) return north.maxByOrNull { candidates[it].z }
        val east = candidates.indices.filter { candidates[it].x > beastCentre.x }
        return east.maxByOrNull { candidates[it].x }
    }

    private fun trySpawnCore(
        npc: Npc,
        target: Pawn,
    ) {
        val world = npc.world
        if (npc.attr[CORE_DISABLED] == true) return
        if (world.random(CORE_SPAWN_CHANCE - 1) != 0) return
        val existing = npc.attr[CORE]?.get()
        if (existing != null && existing.isSpawned() && !existing.isDead()) return
        val victim = target as? Player ?: playersInLair(world).randomOrNull() ?: return
        val landing = Tile(victim.tile)
        val travel = MagicCombatStrategy.getHitDelay(npc.getCentreTile(), landing)
        world.spawn(npc.createProjectile(landing, PROJ_CORE_TRAVEL, ProjectileType.MAGIC))
        val core = Npc(DARK_ENERGY_CORE, landing, world)
        core.respawns = false
        core.walkRadius = 0
        // A full lock keeps the generic combat cycle from making the core walk or retaliate; it stays attackable.
        core.lock = LockState.FULL
        core.attr[CORE_OWNER] = WeakReference(npc)
        npc.attr[CORE] = WeakReference(core)
        world.queue {
            wait(travel)
            if (!npc.isSpawned() || npc.isDead() || npc.attr[CORE]?.get() !== core) return@queue
            if (!world.spawn(core)) {
                npc.attr.remove(CORE)
                return@queue
            }
            runCore(npc, core, victim)
        }
    }

    private fun runCore(
        beast: Npc,
        core: Npc,
        initial: Player,
    ) {
        val world = beast.world
        world.queue {
            var cooldown = 0
            while (core.isSpawned() && !core.isDead() && beast.isSpawned() && !beast.isDead()) {
                if (cooldown <= 0) {
                    val inRange = playersInLair(world).filter { inCoreRange(core.tile, it.tile) }
                    if (inRange.isNotEmpty()) {
                        inRange.forEach { victim ->
                            val damage = world.random(CORE_DAMAGE_MIN..CORE_DAMAGE_MAX)
                            victim.hit(damage = damage, type = HitType.REGULAR_HIT.id)
                            // Void donor message (the OSRS Wiki page quotes none).
                            victim.message("The dark core creature steals some life from you for its master.")
                            beast.setCurrentLifepoints(minOf(beast.getCurrentLifepoints() + coreHeal(damage), beast.getMaximumLifepoints()))
                        }
                        cooldown = if (core.attr.has(POISON_TICKS_LEFT_ATTR)) CORE_POISONED_ATTACK_SPEED else CORE_ATTACK_SPEED
                    } else {
                        // "If no player is actively being damaged by the core, it will instead jump towards a player in the arena."
                        val candidates = playersInLair(world)
                        if (candidates.isEmpty()) break
                        val next = candidates[jumpTargetIndex(beast.getCentreTile(), candidates.map { it.tile }) ?: world.random(candidates.size - 1)]
                        val destination = Tile(next.tile)
                        core.facePawn(next)
                        core.animate(ANIM_CORE_TAKE_OFF)
                        wait(1)
                        if (!core.isSpawned() || core.isDead()) break
                        val travel = MagicCombatStrategy.getHitDelay(core.tile, destination)
                        world.spawn(core.createProjectile(destination, PROJ_CORE_TRAVEL, ProjectileType.MAGIC))
                        core.invisible = true
                        core.attr[CORE_JUMPING] = true
                        wait(travel)
                        if (!core.isSpawned() || core.isDead()) break
                        core.moveTo(destination)
                        core.invisible = false
                        core.attr.remove(CORE_JUMPING)
                        core.resetFacePawn()
                        continue
                    }
                }
                cooldown--
                wait(1)
            }
            if (beast.attr[CORE]?.get() === core) {
                beast.attr.remove(CORE)
            }
            if (core.isSpawned() && !core.isDead()) {
                world.remove(core)
            }
        }
    }
}
