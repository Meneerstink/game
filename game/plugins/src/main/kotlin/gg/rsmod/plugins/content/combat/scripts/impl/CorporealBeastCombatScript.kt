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
 * - Stomp hits 30-51 (Void 300..510). Melee max 51 (Novite max hit 513).
 * - Its magic is only partially blocked by Protect from / Deflect Magic: 60% gets through (Novite
 *   getMagePrayerMultiplier = 0.6).
 * - All damage dealt to it is halved unless it comes from a spear or halberd used on the stab style
 *   (Void Target.damageModifiers / Equipment.isCorpbaneWeapon, corrected to apply to damage dealt TO the
 *   beast rather than by it).
 * - Regeneration (Void corp_stomp timer, every 7 ticks): with nobody in the lair it heals to full (Novite
 *   processNPC), with 8+ players in the lair it restores 25 + 5 per player life points.
 * - Dark energy core (Void spawnDarkCore): 1/8 chance whenever the beast attacks while damaged or takes a
 *   hit of 32+, one core at a time. It flies to a player (proj 1828), sits on their tile and every 2 ticks
 *   (12 while poisoned) steals 1-13 life points for its master with the Void message. When its victim
 *   moves it hops (anim 10393, proj 1828) to a random player in the lair. It has 25 lifepoints and can be
 *   killed; it vanishes when the beast dies or the lair empties.
 */
object CorporealBeastCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.CORPOREAL_BEAST)

    const val DARK_ENERGY_CORE = Npcs.DARK_ENERGY_CORE_8127

    private const val MELEE_MAX = 51.3
    private const val STOMP_MIN = 30.0
    private const val STOMP_MAX = 51.0
    private const val MAGIC_MAX = 65.0
    private const val DRAIN_MAX = 55.0
    private const val SCATTER_CENTRE_MAX = 40.0
    private const val SCATTER_CENTRE_ADJACENT_MAX = 30.0
    private const val SCATTER_SPLASH_MAX = 30.0
    private const val SCATTER_SPLASH_ADJACENT_MAX = 20.0
    private const val MAGIC_PRAYER_MULTIPLIER = 0.6

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

    private const val CORE_SPAWN_CHANCE = 8
    // Void's source stores this rule as 320 in its historical x10 hitmark unit. The local
    // runtime now passes 1:1 real damage from PawnExt, so the gameplay threshold is 32.
    private const val CORE_SPAWN_HIT_THRESHOLD = 32
    private const val CORE_DRAIN_MIN = 10
    private const val CORE_DRAIN_MAX = 130
    private const val CORE_ATTACK_SPEED = 2
    private const val CORE_POISONED_ATTACK_SPEED = 12
    private const val CORE_RETARGET_DELAY = 3
    private const val REGEN_INTERVAL = 7

    val LAIR_X = 2972..3001
    val LAIR_Z = 4370..4397
    const val LAIR_HEIGHT = 2

    private val CORE = AttributeKey<WeakReference<Npc>>()
    private val CORE_OWNER = AttributeKey<WeakReference<Npc>>()
    private val REGEN_TOKEN = AttributeKey<Any>()

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
            if (npc.getCurrentLifepoints() < npc.getMaximumLifepoints()) {
                trySpawnCore(npc, target)
            }

            val underneath = nearby.filter { isUnder(npc, it) }
            if (underneath.isNotEmpty()) {
                stomp(npc, underneath)
            } else {
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
     * stab style. [damage] and the return value are in the x10 hitmark scale.
     */
    fun modifyIncomingDamage(
        attacker: Pawn,
        hitType: HitType,
        damage: Int,
    ): Int {
        if (damage <= 0) return damage
        if (attacker is Player && hitType == HitType.MELEE && isCorpbaneWeapon(attacker) &&
            CombatConfigs.getCombatStyle(attacker) == StyleType.STAB
        ) {
            return damage
        }
        return damage / 2
    }

    private fun isCorpbaneWeapon(player: Player): Boolean {
        val weapon = player.equipment[3] ?: return false
        val name = player.world.definitions.get(ItemDef::class.java, weapon.id).name.lowercase()
        return name.contains("spear") || name.contains("halberd")
    }

    /** Called from the beast's death hook: the core dies with its master. */
    fun onBeastDeath(npc: Npc) {
        val core = npc.attr[CORE]?.get() ?: return
        npc.attr.remove(CORE)
        if (core.isSpawned() && !core.isDead()) {
            npc.world.remove(core)
        }
    }

    /** Called from the core's death hook so the beast may summon a new one. */
    fun onCoreDeath(core: Npc) {
        val beast = core.attr[CORE_OWNER]?.get() ?: return
        if (beast.attr[CORE]?.get() === core) {
            beast.attr.remove(CORE)
        }
    }

    /** Void: a hit of 32+ on the beast has a 1/8 chance of summoning the core onto the attacker. */
    fun onBeastDamaged(
        npc: Npc,
        attacker: Pawn,
        damage: Int,
    ) {
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
            while (npc.isSpawned() && !npc.isDead() && npc.attr[REGEN_TOKEN] === token) {
                wait(REGEN_INTERVAL)
                if (!npc.isSpawned() || npc.isDead() || npc.attr[REGEN_TOKEN] !== token) break
                val count = playersInLair(world).size
                val max = npc.getMaximumLifepoints()
                if (count == 0) {
                    if (npc.getCurrentLifepoints() < max) {
                        npc.setCurrentLifepoints(max)
                    }
                    val core = npc.attr[CORE]?.get()
                    if (core != null) {
                        npc.attr.remove(CORE)
                        if (core.isSpawned() && !core.isDead()) world.remove(core)
                    }
                } else if (count >= 8) {
                    npc.setCurrentLifepoints(minOf(max, npc.getCurrentLifepoints() + 250 + count * 50))
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
     * The beast's magic ignores the prayer short circuit in the formula; 60% of the damage passes
     * through Protect from / Deflect Magic instead (Novite).
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
        victims.forEach { victim ->
            val landHit = MeleeCombatFormula.getAccuracy(npc, victim) >= npc.world.randomDouble()
            npc.dealHit(target = victim, minHit = STOMP_MIN, maxHit = STOMP_MAX, landHit = landHit, delay = 0, hitType = HitType.MELEE)
        }
    }

    private fun melee(
        npc: Npc,
        target: Pawn,
        world: World,
    ) {
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
                when (world.random(2)) {
                    0 -> {
                        target.skills.decrementCurrentLevel(Skills.MAGIC, 1 + world.random(4), capped = false)
                        target.message("Your Magic has been slightly drained!")
                    }
                    1 -> {
                        target.skills.decrementCurrentLevel(Skills.SUMMONING, 1 + world.random(4), capped = false)
                        target.message("Your Summoning has been slightly drained!")
                    }
                    else -> {
                        target.setCurrentPrayerPoints((target.getCurrentPrayerPoints() - (100 + world.random(400))).coerceAtLeast(0))
                        target.message("Your Prayer has been slightly drained!")
                    }
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

    private fun trySpawnCore(
        npc: Npc,
        target: Pawn,
    ) {
        val world = npc.world
        if (world.random(CORE_SPAWN_CHANCE) != 0) return
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
            var victim: Player? = initial
            var awayTicks = 0
            var cooldown = 0
            while (core.isSpawned() && !core.isDead() && beast.isSpawned() && !beast.isDead()) {
                val current = victim
                if (current != null && current.isOnline && !current.isDead() && current.tile.sameAs(core.tile)) {
                    awayTicks = 0
                    if (cooldown <= 0) {
                        val damage = CORE_DRAIN_MIN + world.random(CORE_DRAIN_MAX - CORE_DRAIN_MIN)
                        current.hit(damage = damage, type = HitType.REGULAR_HIT.id)
                        current.message("The dark core creature steals some life from you for its master.")
                        beast.setCurrentLifepoints(minOf(beast.getCurrentLifepoints() + damage, beast.getMaximumLifepoints()))
                        cooldown = if (core.attr.has(POISON_TICKS_LEFT_ATTR)) CORE_POISONED_ATTACK_SPEED else CORE_ATTACK_SPEED
                    }
                } else if (++awayTicks >= CORE_RETARGET_DELAY) {
                    awayTicks = 0
                    val candidates = playersInLair(world)
                    if (candidates.isEmpty()) break
                    val next = candidates.random()
                    victim = next
                    val destination = Tile(next.tile)
                    core.facePawn(next)
                    core.animate(ANIM_CORE_TAKE_OFF)
                    wait(1)
                    if (!core.isSpawned() || core.isDead()) break
                    val travel = MagicCombatStrategy.getHitDelay(core.tile, destination)
                    world.spawn(core.createProjectile(destination, PROJ_CORE_TRAVEL, ProjectileType.MAGIC))
                    core.invisible = true
                    wait(travel)
                    if (!core.isSpawned() || core.isDead()) break
                    core.moveTo(destination)
                    core.invisible = false
                    core.resetFacePawn()
                    continue
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
