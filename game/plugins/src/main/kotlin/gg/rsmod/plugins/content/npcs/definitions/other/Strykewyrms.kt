package gg.rsmod.plugins.content.npcs.definitions.other

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
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/** Strykewyrm roster and combat behaviour; bindings live in strykewyrms.plugin.kts. */
data class Wyrm(val mound: Int, val wyrm: Int, val slayer: Int, val name: String, val hitpoints: Int, val maxHit: Int, val attack: Int, val strength: Int, val defence: Int, val magic: Int)

object Strykewyrms {
    /** The emerge hit of the burrow attack, in hitpoints (Novite 300 life points / 10). */
    const val BURROW_DAMAGE = 30

    val WYRMS = listOf(
        Wyrm(Npcs.MOUND_9462, Npcs.ICE_STRYKEWYRM, 93, "Ice strykewyrm", 3000, 17, 142, 165, 142, 142),
        Wyrm(Npcs.MOUND_9464, Npcs.DESERT_STRYKEWYRM, 77, "Desert strykewyrm", 1200, 12, 120, 130, 120, 120),
        Wyrm(Npcs.MOUND_9466, Npcs.JUNGLE_STRYKEWYRM, 73, "Jungle strykewyrm", 1100, 9, 100, 110, 100, 100),
    )

    val WYRM_TARGET = AttributeKey<java.lang.ref.WeakReference<Pawn>>()
    val WYRM_IDLE = AttributeKey<Int>()

    const val ANIM_PLAYER_STOMP = 4278
    const val ANIM_EMERGE = 12795
    const val ANIM_BURROW = 12796
    const val ANIM_CAST = 12794
    const val PROJ_CAST = 2314
    const val GFX_ICE_IMPACT = 2315
    const val GFX_JUNGLE_IMPACT = 2313
    const val GFX_DESERT_IMPACT = 2311
    const val GFX_JUNGLE_CAST = 2309
    const val GFX_FREEZE = 369

    fun wyrmFor(npc: Npc): Wyrm? = WYRMS.firstOrNull { it.mound == npc.id }
}

object StrykewyrmCombatScript : CombatScript() {
    override val ids = Strykewyrms.WYRMS.map { it.mound }.toIntArray()

    /**
     * N24 (LC10 2026-10-16: the wyrm cast once and then stood still). The old loop `while (canEngage && isAttackDelayReady)` +
     * `wait(attackSpeed)` woke one tick before its own ATTACK_DELAY ran out (breach trace: delayReady=false delay=1) and quit after
     * every attack; only the player's next hit started it again. Ported Darkan's per-tick npc combat process instead
     * (NPCCombat.java:48-61: each tick the delay counts down, a lost target ends the fight, an attack fires when the delay is 0)
     * with Darkan's strykewyrm attack table (Strykewyrm.kt:69-86: getRandomInclusive(20) <= 7 and in melee range = melee,
     * <= 19 = magic, 20 = burrow).
     */
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        val wyrm = Strykewyrms.wyrmFor(npc) ?: return
        val world = npc.world
        var exit = "no-target"
        while (true) {
            val target = npc.getCombatTarget() ?: break
            if (npc.getTransmogId() == -1) { exit = "asleep"; break }
            if (!npc.canEngageCombat(target)) { exit = "cannot-engage"; break }
            if (!npc.isAttackDelayReady()) {
                it.wait(1)
                continue
            }
            npc.facePawn(target)
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            val roll = world.random(20)
            when {
                roll <= 7 && distance <= 1 -> melee(npc, target)
                roll <= 19 -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) cast(npc, wyrm, target)
                else -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) burrow(it, npc, wyrm, target)
            }
            npc.postAttackLogic(target)
            it.wait(1)
        }
        // N24: why the wyrm stopped attacking - breach trace for the three mounds.
        if (gg.rsmod.game.model.BreachTrace.watches(npc.id)) {
            gg.rsmod.game.model.BreachTrace.log("combat.exit") { "npc=${npc.id}#${npc.index} script=strykewyrm reason=$exit cycle=${world.currentCycle}" }
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }

    private fun cast(npc: Npc, wyrm: Wyrm, target: Pawn) {
        val ANIM_CAST = Strykewyrms.ANIM_CAST; val PROJ_CAST = Strykewyrms.PROJ_CAST; val GFX_JUNGLE_CAST = Strykewyrms.GFX_JUNGLE_CAST; val GFX_FREEZE = Strykewyrms.GFX_FREEZE; val GFX_ICE_IMPACT = Strykewyrms.GFX_ICE_IMPACT; val GFX_JUNGLE_IMPACT = Strykewyrms.GFX_JUNGLE_IMPACT; val GFX_DESERT_IMPACT = Strykewyrms.GFX_DESERT_IMPACT
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_CAST)
        if (wyrm.wyrm == Npcs.JUNGLE_STRYKEWYRM) target.graphic(GFX_JUNGLE_CAST)
        val world = npc.world
        world.spawn(npc.createProjectile(target, PROJ_CAST, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val land = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(target = target, maxHit = wyrm.maxHit.toDouble(), landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = { hit ->
            val damage = hit.hit.hitmarks.sumOf { it.damage }
            when (wyrm.wyrm) {
                Npcs.ICE_STRYKEWYRM -> if (damage > 0 && world.random(9) == 0) { target.freeze(5); target.graphic(GFX_FREEZE) } else target.graphic(GFX_ICE_IMPACT)
                Npcs.JUNGLE_STRYKEWYRM -> { target.graphic(GFX_JUNGLE_IMPACT); if (damage > 0 && world.random(1) == 0) target.poison(6) }
                else -> target.graphic(GFX_DESERT_IMPACT)
            }
        })
    }

    private suspend fun burrow(it: QueueTask, npc: Npc, wyrm: Wyrm, target: Pawn) {
        val ANIM_BURROW = Strykewyrms.ANIM_BURROW; val ANIM_EMERGE = Strykewyrms.ANIM_EMERGE; val GFX_JUNGLE_IMPACT = Strykewyrms.GFX_JUNGLE_IMPACT; val GFX_DESERT_IMPACT = Strykewyrms.GFX_DESERT_IMPACT; val GFX_ICE_IMPACT = Strykewyrms.GFX_ICE_IMPACT
        npc.animate(ANIM_BURROW)
        it.wait(2)
        if (npc.isDead() || target.isDead() || (target is Player && !target.isOnline)) return
        val dest = npc.world.findRandomTileAround(target.tile, radius = 1) ?: Tile(target.tile)
        npc.moveTo(dest)
        npc.animate(ANIM_EMERGE)
        // ROOT_CAUSES #72 (2026-10-01): Novite StrykewyrmCombat hits 300 life points on the emerge (and the desert wyrm a second 300) in
        // the 667 x10 unit - 30 hitpoints here. The unconverted 300 killed any player outright since hitpoints became 1:1.
        target.hit(Strykewyrms.BURROW_DAMAGE, HitType.REGULAR_HIT, 1)
        when (wyrm.wyrm) {
            Npcs.JUNGLE_STRYKEWYRM -> { target.graphic(GFX_JUNGLE_IMPACT); target.poison(6) }
            Npcs.DESERT_STRYKEWYRM -> { target.graphic(GFX_DESERT_IMPACT); target.hit(Strykewyrms.BURROW_DAMAGE, HitType.REGULAR_HIT, 2) }
            else -> target.graphic(GFX_ICE_IMPACT)
        }
    }
}
