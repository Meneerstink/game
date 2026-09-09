package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula

/**
 * Giant Mole (npc 3340).
 *
 * Ported from the 2009scape GiantMoleNPC handler (same-era ids): dig animation 3314 + gfx 572,
 * surface animation 3315 + gfx 573, dust gfx 571 on the players nearby, and the twelve burrow
 * destinations inside the Falador mole lair. 2011 wiki: after taking a hit the mole has a chance
 * (higher when below half health) to burrow away to another part of the lair, kicking dust into
 * the eyes of nearby players; melee max hit 21.
 */
object GiantMoleCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.GIANT_MOLE)

    private const val MAX_HIT = 21.0

    private val DIG_LOCATIONS =
        listOf(
            Tile(1760, 5183, 0), Tile(1736, 5223, 0), Tile(1777, 5235, 0), Tile(1739, 5150, 0), Tile(1769, 5148, 0),
            Tile(1750, 5195, 0), Tile(1778, 5207, 0), Tile(1772, 5199, 0), Tile(1774, 5173, 0), Tile(1760, 5162, 0),
            Tile(1753, 5151, 0), Tile(1739, 5152, 0),
        )

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val hurt = npc.getCurrentLifepoints() < npc.getMaximumLifepoints()
            val low = npc.getCurrentLifepoints() < npc.getMaximumLifepoints() / 2
            if (hurt && world.random(if (low) 6 else 12) == 0) {
                dig(it)
                break
            }
            if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
                npc.animate(npc.combatDef.attackAnimation)
                val landHit = MeleeCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
                npc.dealHit(target = target, maxHit = MAX_HIT, landHit = landHit, delay = 1, hitType = HitType.MELEE)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private suspend fun dig(it: QueueTask) {
        val npc = it.npc
        val world = npc.world
        npc.resetFacePawn()
        npc.removeCombatTarget()
        npc.lock = gg.rsmod.game.model.LockState.FULL_WITH_DAMAGE_IMMUNITY
        npc.animate(3314)
        npc.graphic(572)
        CorporealBeastCombatScript.nearbyPlayers(npc, radius = 3).forEach { p: Player ->
            p.graphic(571)
            p.message("The giant mole kicks dirt into your eyes as it burrows away.")
        }
        it.wait(3)
        val dest = DIG_LOCATIONS.filter { it.getDistance(npc.tile) > 8 }.randomOrNull() ?: DIG_LOCATIONS.random()
        npc.moveTo(dest.x, dest.z, dest.height)
        npc.animate(3315)
        npc.graphic(573)
        it.wait(2)
        npc.unlock()
    }
}
