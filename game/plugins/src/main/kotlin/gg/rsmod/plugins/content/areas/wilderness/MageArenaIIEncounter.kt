package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max

/**
 * N415 Mage Arena II: the three followers as the symbol summons them, and their fight.
 *
 * Sourced (docs/n415/RESEARCH.md; OSRS Wiki Mage_Arena_II, Justiciar_Zachariah, Derwen, Porazdir; RuneLite gameval names;
 * the RSProx 235/03344 recording of real OSRS as quoted by tsps PR #3964):
 * - arrival: combat npc shown as its spawning form (5977 / 7513 / 7515) playing WILD_ZEALOT_SPAWN 7964 /
 *   ENT_BOSS_SPAWN 7844 / ZAMORAK_DEMON_BOSS_SPAWN 7842, attacking 2 (Justiciar) / 3 (Derwen) ticks later (recorded; = floor of the cache seq length 2.13 / 3.00 ticks,
 *   so Porazdir's 7842 of 5.00 ticks gives 5);
 * - every 6 ticks; god spell: 7962 + 1518 (Justiciar), 7849 + 1511 (Derwen, drains Defence 1 + 5%, "You feel weakened."),
 *   7841 + 78 (Porazdir, drains Magic 1 + 5%, "You feel your magical powers weaken."); magic max 43 (Justiciar 26), melee 16
 *   (Justiciar 43), melee only beside a side; protection prayers reduce, not block ("still hit ... up to 20+": half);
 * - Ice Barrage aimed at the cast tile (dodge by stepping off), Tele Block 2 min / 1 min under Protect from Magic, fading on
 *   the follower's death; Justiciar's blue wave (7966 + 1515) at the cast tile drags into melee and binds, sword every 3 ticks;
 *   Porazdir's energy ball (1514) through prayer, up to 30 beside him, 0 from 12 tiles; Derwen's energy balls (1512 -> npc 7514,
 *   20 HP, any style) on his 2nd attack and every 6th after (recorded), healing him 5 (1513) "every few seconds".
 * - remains always drop for the killer ("Untradeable drop" line as OSRS); death/logout/rotation remove the follower.
 *
 * OWNER QUESTION (no OSRS number, dossier N415 20:4x, advice used until answered): a special replaces one attack in five,
 * specials land 2 ticks after the cast, the bind lasts 8 ticks, a ball heals every 10 ticks, the follower goes when the owner is
 * out of view (as the Mage Arena I duel).
 */
object MageArenaIIEncounter {
    const val ATTACK_TICKS = 6
    const val BOUND_SWORD_TICKS = 3
    const val SPECIAL_ONE_IN = 5
    const val SPECIAL_LAND_TICKS = 2
    const val BIND_TICKS = 8
    const val HEAL_EVERY_TICKS = 10
    const val BARRAGE_MAX = 30
    const val BARRAGE_FREEZE = 32
    const val ENERGY_BALL_MAX = 30
    const val ENERGY_BALL_RANGE = 12
    const val TELE_BLOCK_TICKS = 200
    const val TELE_BLOCK_PROTECTED_TICKS = 100

    data class Follower(
        val god: MageArenaII.God,
        val combat: Int,
        val spawning: Int,
        val spawnSeq: Int,
        val spawnTicks: Int,
        val castSeq: Int,
        val castGfx: Int,
        val castSound: Int,
        val magicMax: Int,
        val meleeSeq: Int,
        val meleeMax: Int,
        val meleeStyle: StyleType,
        val drain: Int?,
        val drainMessage: String?,
    )

    val FOLLOWERS = listOf(
        // Sounds: the 667 god-spell impact synths the Breach followers already use (BreachMonsters SFX_*).
        Follower(MageArenaII.God.SARADOMIN, 7858, 5977, 7964, 2, 7962, 1518, 1659, 26, 7853, 43, StyleType.SLASH, null, null),
        Follower(MageArenaII.God.GUTHIX, 7859, 7513, 7844, 3, 7849, 1511, 1653, 43, 7848, 16, StyleType.CRUSH,
            Skills.DEFENCE, "You feel weakened."),
        Follower(MageArenaII.God.ZAMORAK, 7860, 7515, 7842, 5, 7841, 78, 1655, 43, 7840, 16, StyleType.SLASH,
            Skills.MAGIC, "You feel your magical powers weaken."),
    )
    const val HEALER = 7514
    /** Ice Barrage impact: SpotanimID ICE_BARRAGE_IMPACT 369, the same id in the 667 cache. */
    const val ICE_BARRAGE_IMPACT = 369
    /** WILD_ZEALOT_MAGIC2; 7966 WILD_ZEALOT_PROJECTILE is spotanim 1515's own animation (FX import plan). */
    const val SHOCK_WAVE_SEQ = 7963
    const val SHOCK_WAVE_PROJ = 1515
    const val ENERGY_BALL_PROJ = 1514
    const val HEALER_PROJ = 1512
    const val HEAL_PROJ = 1513

    class State(val follower: Follower, val owner: WeakReference<Player>, val point: Int) {
        var attacks = 0
        var boundUntil = -1
        var teleBlockApplied = false
        val healers = mutableListOf<Npc>()
    }

    private val STATE = AttributeKey<State>()
    private val ACTIVE = AttributeKey<MutableMap<MageArenaII.God, WeakReference<Npc>>>()
    /** Wiki: one follower per spawn place; another player's follower there must despawn first. */
    private val occupied = java.util.concurrent.ConcurrentHashMap<Int, WeakReference<Npc>>()

    private fun seq(upstream: Int): Int? = MageArenaAssets.sequence(upstream)
    private fun gfx(upstream: Int): Int = if (upstream == 78) 78 else MageArenaAssets.spotanim(upstream) ?: -1
    fun localNpc(upstream: Int): Int? = MageArenaAssets.npc(upstream)

    fun followerFor(npc: Npc): Follower? = npc.attr[STATE]?.follower
    fun stateOf(npc: Npc): State? = npc.attr[STATE]

    fun active(player: Player, god: MageArenaII.God): Npc? =
        player.attr[ACTIVE]?.get(god)?.get()?.takeIf { it.isSpawned() && it.isAlive() }

    fun summon(player: Player, god: MageArenaII.God, location: MageArenaIITracking.Location): Npc? {
        val follower = FOLLOWERS.first { it.god == god }
        if (active(player, god) != null) return null
        occupied[location.point]?.get()?.let { if (it.isSpawned() && it.isAlive()) return null }
        val combatId = localNpc(follower.combat) ?: return null
        val npc = Npc(player, combatId, location.tile, player.world)
        npc.publicOwner = true
        npc.attr[gg.rsmod.plugins.content.combat.Combat.OWNER_ONLY_TARGET_ATTR] = true
        npc.respawnOverride = false
        npc.attr[STATE] = State(follower, WeakReference(player), location.point)
        val guardian = MageArenaIIGuardians.ALL.first { it.god == god }
        // The launch gate protects XP; this same rule must also cover direct and delayed special hits at impact.
        npc.hitModifier = { hit -> MageArenaIIGuardians.modifyHit(guardian, hit) }
        if (!player.world.spawn(npc)) return null
        occupied[location.point] = WeakReference(npc)
        (player.attr[ACTIVE] ?: mutableMapOf<MageArenaII.God, WeakReference<Npc>>().also { player.attr[ACTIVE] = it })[god] = WeakReference(npc)
        localNpc(follower.spawning)?.let { npc.setTransmogId(it) }
        seq(follower.spawnSeq)?.let { npc.animate(it) }
        npc.queue {
            wait(follower.spawnTicks)
            if (!npc.isSpawned() || !npc.isAlive()) return@queue
            npc.setTransmogId(-1)
            if (player.isOnline && !player.isDead()) npc.attack(player)
        }
        watch(npc, player)
        return npc
    }

    /** Leaves with its owner: logout, death, out of view (as the Mage Arena I duel), or a symbol rotation. */
    private fun watch(npc: Npc, player: Player) {
        player.world.queue {
            while (npc.isSpawned() && npc.isAlive()) {
                wait(1)
                if (!player.isOnline || player.isDead() || !player.tile.isWithinRadius(npc.tile, Player.NORMAL_VIEW_DISTANCE)) {
                    dismiss(npc)
                    return@queue
                }
            }
        }
    }

    fun dismiss(npc: Npc) {
        val state = stateOf(npc) ?: return
        release(state)
        if (npc.isSpawned() && npc.isAlive()) npc.world.remove(npc)
    }

    fun dismissAll(player: Player) {
        player.attr[ACTIVE]?.values?.mapNotNull { it.get() }?.forEach { dismiss(it) }
        player.attr.remove(ACTIVE)
    }

    /** Its energy balls go with it; its Tele Block fades immediately on its death (Wiki). */
    fun release(state: State) {
        state.healers.forEach { if (it.isSpawned()) it.world.remove(it) }
        state.healers.clear()
        val owner = state.owner.get()
        if (state.teleBlockApplied && owner != null) {
            owner.timers.remove(TELEBLOCK_TIMER)
            owner.timers.remove(gg.rsmod.plugins.content.mechanics.hud.TimerExpiry.TELEBLOCK_WARNING_TIMER)
        }
        state.teleBlockApplied = false
        occupied.entries.removeIf { it.key == state.point }
    }

    /**
     * Its death: energy balls and Tele Block go (Wiki). The remains drop through the follower's registered table (Wiki: always),
     * so the Drop Viewer shows them; the symbol never tracks a follower whose remains are held, so no second set can be farmed.
     */
    fun died(npc: Npc) {
        val state = stateOf(npc) ?: return
        release(state)
        state.owner.get()?.attr?.get(ACTIVE)?.remove(state.follower.god)
    }

    private fun protects(target: Pawn, style: CombatClass): Boolean =
        PrayerIcon.values().any { it.id == target.prayerIcon && style in it.protects }

    /** MA2 prayers do not confer immunity. Keep ordinary defence rolls, bypass only the generic NPC full-block rule. */
    fun accuracy(npc: Npc, target: Pawn, style: CombatClass): Double =
        if (style == CombatClass.MELEE) MeleeCombatFormula.getUnprotectedAccuracy(npc, target)
        else MagicCombatFormula.getUnprotectedAccuracy(npc, target)

    /** Existing damage reduction retained; its exact OSRS factor still needs primary-source measurement. */
    private fun hit(npc: Npc, target: Pawn, style: CombatClass, max: Int, delay: Int, landed: Boolean? = null): Int {
        val land = landed ?: (npc.world.randomDouble() < accuracy(npc, target, style))
        val cap = if (protects(target, style)) max / 2.0 else max.toDouble()
        val type = if (style == CombatClass.MELEE) HitType.MELEE else HitType.MAGIC
        return npc.dealHit(target, maxHit = cap, landHit = land, delay = delay, hitType = type, applyDeflectProtection = false)
            .hit.hitmarks.sumOf { it.damage }
    }

    /** Beside a side of the 3x3 footprint, not a corner. */
    fun besideSide(npc: Npc, target: Pawn): Boolean {
        val size = npc.getSize()
        val t = target.tile
        val inX = t.x >= npc.tile.x && t.x < npc.tile.x + size
        val inZ = t.z >= npc.tile.z && t.z < npc.tile.z + size
        val dx = max(npc.tile.x - t.x, max(0, t.x - (npc.tile.x + size - 1)))
        val dz = max(npc.tile.z - t.z, max(0, t.z - (npc.tile.z + size - 1)))
        return max(dx, dz) == 1 && (inX || inZ)
    }

    fun edgeDistance(npc: Npc, target: Pawn): Int {
        val size = npc.getSize()
        val t = target.tile
        val dx = max(npc.tile.x - t.x, max(0, t.x - (npc.tile.x + size - 1)))
        val dz = max(npc.tile.z - t.z, max(0, t.z - (npc.tile.z + size - 1)))
        return max(dx, dz)
    }

    fun bound(npc: Npc): Boolean = (stateOf(npc)?.boundUntil ?: -1) > npc.world.currentCycle

    /** One attack; returns the delay before the next. */
    fun attack(npc: Npc, target: Pawn): Int {
        val state = stateOf(npc) ?: return ATTACK_TICKS
        val f = state.follower
        state.attacks++
        if (f.god == MageArenaII.God.GUTHIX && state.attacks >= 2 && (state.attacks - 2) % 6 == 0) {
            summonHealer(npc, target, state)
            return ATTACK_TICKS
        }
        val isBound = bound(npc)
        if (!isBound && target is Player && npc.world.random(SPECIAL_ONE_IN - 1) == 0) {
            val options = mutableListOf("barrage")
            if (!target.timers.has(TELEBLOCK_TIMER)) options += "teleblock"
            if (f.god == MageArenaII.God.SARADOMIN) options += "wave"
            if (f.god == MageArenaII.God.ZAMORAK) options += "ball"
            when (options[npc.world.random(options.indices)]) {
                "barrage" -> barrage(npc, target)
                "teleblock" -> teleBlock(npc, target, state)
                "wave" -> shockWave(npc, target, state)
                "ball" -> energyBall(npc, target)
            }
            return ATTACK_TICKS
        }
        if (besideSide(npc, target) && (isBound || npc.world.random(1) == 0)) {
            npc.prepareAttack(CombatClass.MELEE, f.meleeStyle, WeaponStyle.AGGRESSIVE)
            seq(f.meleeSeq)?.let { npc.animate(it) }
            hit(npc, target, CombatClass.MELEE, f.meleeMax, delay = 1)
            return if (isBound && f.god == MageArenaII.God.SARADOMIN) BOUND_SWORD_TICKS else ATTACK_TICKS
        }
        godSpell(npc, target, f)
        return ATTACK_TICKS
    }

    private fun godSpell(npc: Npc, target: Pawn, f: Follower) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        seq(f.castSeq)?.let { npc.animate(it) }
        val land = npc.world.randomDouble() < accuracy(npc, target, CombatClass.MAGIC)
        // Recorded: the hit lands a tick after the cast; the god-spell graphic on a hit, the splash (85) on a miss.
        target.graphic(Graphic(if (land) gfx(f.castGfx) else 85, if (land) 0 else 124, 30))
        if (target is Player) target.playSound(if (land) f.castSound else 227, delay = 30)
        hit(npc, target, CombatClass.MAGIC, f.magicMax, delay = 1, landed = land)
        if (land && f.drain != null && target is Player) {
            val level = target.skills.getCurrentLevel(f.drain)
            target.skills.setCurrentLevel(f.drain, max(0, level - (1 + level * 5 / 100)))
            f.drainMessage?.let { target.message(it) }
        }
    }

    private fun barrage(npc: Npc, target: Player) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        seq(stateOf(npc)!!.follower.castSeq)?.let { npc.animate(it) }
        val aimed = Tile(target.tile)
        npc.queue {
            wait(SPECIAL_LAND_TICKS)
            if (!npc.isAlive() || !target.isOnline || target.tile != aimed) return@queue
            val land = npc.world.randomDouble() < accuracy(npc, target, CombatClass.MAGIC)
            if (!land) { target.graphic(Graphic(85, 124)); return@queue }
            target.graphic(Graphic(ICE_BARRAGE_IMPACT, 0)); target.playSound(gg.rsmod.plugins.api.cfg.Sfx.ICE_BARRAGE_IMPACT)
            hit(npc, target, CombatClass.MAGIC, BARRAGE_MAX, delay = 0, landed = true)
            target.freeze(BARRAGE_FREEZE, npc)
        }
    }

    private fun teleBlock(npc: Npc, target: Player, state: State) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        seq(state.follower.castSeq)?.let { npc.animate(it) }
        npc.queue {
            wait(SPECIAL_LAND_TICKS)
            if (!npc.isAlive() || !target.isOnline || target.timers.has(TELEBLOCK_TIMER)) return@queue
            if (npc.world.randomDouble() >= accuracy(npc, target, CombatClass.MAGIC)) { target.graphic(Graphic(85, 124)); return@queue }
            gg.rsmod.plugins.content.mechanics.hud.TimerExpiry.teleblock(target, if (protects(target, CombatClass.MAGIC)) TELE_BLOCK_PROTECTED_TICKS else TELE_BLOCK_TICKS)
            state.teleBlockApplied = true
            target.message("A teleport block has been cast on you!")
        }
    }

    private fun shockWave(npc: Npc, target: Player, state: State) {
        seq(SHOCK_WAVE_SEQ)?.let { npc.animate(it) }
        val aimed = Tile(target.tile)
        MageArenaAssets.spotanim(SHOCK_WAVE_PROJ)?.let { npc.world.spawn(npc.createProjectile(aimed, it, ProjectileType.MAGIC)) }
        npc.queue {
            wait(SPECIAL_LAND_TICKS)
            if (!npc.isAlive() || !target.isOnline || target.tile != aimed) return@queue
            val drag = sideTiles(npc).filter { !npc.world.collision.isClipped(it) }.minByOrNull { it.getDistance(target.tile) } ?: return@queue
            target.moveTo(drag)
            if (target.freeze(BIND_TICKS, npc) { target.message("You have been bound.") }) state.boundUntil = npc.world.currentCycle + BIND_TICKS
            npc.timers[ATTACK_DELAY] = 1
        }
    }

    private fun sideTiles(npc: Npc): List<Tile> {
        val s = npc.getSize(); val t = npc.tile
        return (0 until s).flatMap { i -> listOf(t.transform(i, -1), t.transform(i, s), t.transform(-1, i), t.transform(s, i)) }
    }

    private fun energyBall(npc: Npc, target: Player) {
        seq(stateOf(npc)!!.follower.castSeq)?.let { npc.animate(it) }
        target.message("Porazdir fires a ball of energy directly linked to his power!")
        MageArenaAssets.spotanim(ENERGY_BALL_PROJ)?.let { npc.world.spawn(npc.createProjectile(target, it, ProjectileType.MAGIC)) }
        npc.queue {
            wait(SPECIAL_LAND_TICKS)
            if (!npc.isAlive() || !target.isOnline) return@queue
            val distance = edgeDistance(npc, target)
            if (distance >= ENERGY_BALL_RANGE) return@queue
            val cap = ENERGY_BALL_MAX * (ENERGY_BALL_RANGE - distance) / (ENERGY_BALL_RANGE - 1)
            target.hit(npc.world.random(0..cap), HitType.REGULAR_HIT, source = npc)
        }
    }

    private fun summonHealer(npc: Npc, target: Pawn, state: State) {
        seq(state.follower.castSeq)?.let { npc.animate(it) }
        val dx = Integer.signum(npc.getCentreTile().x - target.tile.x)
        val dz = Integer.signum(npc.getCentreTile().z - target.tile.z)
        val steps = npc.world.random(2..4)
        val tile = target.tile.transform(dx * steps, dz * steps)
        MageArenaAssets.spotanim(HEALER_PROJ)?.let { npc.world.spawn(npc.createProjectile(tile, it, ProjectileType.MAGIC)) }
        val healerId = localNpc(HEALER) ?: return
        val owner = state.owner.get() ?: return
        npc.queue {
            wait(3)
            if (!npc.isAlive() || npc.world.collision.isClipped(tile)) return@queue
            val ball = Npc(owner, healerId, tile, npc.world).apply {
                publicOwner = true; respawnOverride = false
                attr[gg.rsmod.plugins.content.combat.Combat.OWNER_ONLY_TARGET_ATTR] = true
            }
            if (!npc.world.spawn(ball)) return@queue
            state.healers += ball
            while (ball.isSpawned() && ball.isAlive() && npc.isSpawned() && npc.isAlive()) {
                wait(HEAL_EVERY_TICKS)
                if (!ball.isSpawned() || !ball.isAlive() || !npc.isAlive()) break
                MageArenaAssets.spotanim(HEAL_PROJ)?.let { ball.world.spawn(ball.createProjectile(npc.getCentreTile(), it, ProjectileType.MAGIC)) }
                npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + 5))
            }
            state.healers.remove(ball)
        }
    }
}

object MageArenaIICombatScript : CombatScript() {
    override val ids: IntArray = MageArenaIIEncounter.FOLLOWERS.mapNotNull { MageArenaIIEncounter.localNpc(it.combat) }.toIntArray()

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            val melee = MageArenaIIEncounter.bound(npc)
            if (!npc.moveToAttackRange(it, target, distance = if (melee) 1 else 8, projectile = !melee)) {
                // A failed route can return without suspending; retry on the next tick (FamiliarCombat).
                it.wait(1)
                target = npc.getCombatTarget() ?: break
                continue
            }
            val next = MageArenaIIEncounter.attack(npc, target)
            npc.postAttackLogic(target)
            npc.timers[ATTACK_DELAY] = next
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }
}
