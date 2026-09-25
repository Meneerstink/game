package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula

object CanifisCitizensCombatScript : CombatScript() {
    override val ids =
        intArrayOf(
            Npcs.EDUARD,
            Npcs.LEV,
            Npcs.YURI,
            Npcs.BORIS,
            Npcs.GEORGY,
            Npcs.JOSEPH,
            Npcs.NIKOLAI,
            Npcs.IMRE,
            Npcs.VERA,
            Npcs.MILLA,
            Npcs.SOFIYA,
            Npcs.IRINA,
            Npcs.SVETLANA,
            Npcs.ZOJA,
            Npcs.YADVIGA,
            Npcs.NIKITA,
            Npcs.LILIYA,
            Npcs.ALEXIS,
            Npcs.KSENIA,
            Npcs.GALINA,
        )

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = it.npc.world

        while (npc.canEngageCombat(target)) {
            npc.facePawn(target)
            // Check if the target is a player and cast it as a player instance
            if (target is Player) {
                val player = target
                if (!player.hasEquipped(EquipmentType.WEAPON, Items.WOLFBANE)) {
                    npc.stopMovement()
                    val werewolf = Npc(npc.id - 20, npc.tile, world)
                    werewolf.walkRadius = 5
                    it.wait(1)
                    // Start transformation
                    npc.animate(Anims.START_HUMAN_TO_WEREWOLF, priority = true)
                    /*
                     * Audit T-03: the swap into the werewolf and the animation reset ran 150 ms and 2000 ms later
                     * on two java.util.Timer threads per fight - never cancelled, so the threads leaked - and
                     * mutated the npc list, chunks and update blocks from outside the game thread. Both now run
                     * on the game thread, on ticks: the swap on the next tick (where the 150 ms timer's result
                     * first became visible) and the reset on the tick where the old 2000 ms timer's reset first
                     * became visible, three ticks after the swap.
                     */
                    it.wait(1)
                    // Transform into werewolf
                    world.spawn(werewolf)
                    world.remove(npc)
                    werewolf.facePawn(target)
                    werewolf.animate(Anims.FINISH_HUMAN_TO_WEREWOLF, priority = true)
                    // TODO: ADD Transformation GFX (ids 1079-1098: werewolf transformation)
                    // A world task: werewolf.attack() below interrupts the werewolf's own queue.
                    world.queue {
                        wait(WEREWOLF_ANIMATION_RESET_TICKS)
                        werewolf.resetAnimation()
                    }
                    // Changes the combat target to the werewolf
                    player.clearActiveCombatTimer()
                    player.setCombatTarget(werewolf)
                    werewolf.setCombatTarget(player)
                    // Attack player
                    werewolf.attack(player)
                    // The citizen has left the world; the werewolf fights on. (The old loop kept running for the
                    // removed npc and could still swing at the player once.)
                    npc.resetFacePawn()
                    npc.removeCombatTarget()
                    return
                }
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false) && npc.isAttackDelayReady()) {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.ACCURATE)
                    npc.animate(npc.combatDef.attackAnimation)
                    npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
                    npc.postAttackLogic(target)
                }
            } else {
                // Exits the loop if the target is null.
                return
            }
            it.wait(4)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /**
     * Audit T-03: the reset replaces a 2000 ms timer started one tick before the swap. The world task starts
     * in the swap tick and, like every queue task, its first `wait(n)` resumes n-1 ticks later - so 4 resets
     * the animation three ticks after the swap, the tick in which the old timer's reset reached the client.
     */
    private const val WEREWOLF_ANIMATION_RESET_TICKS = 4
}
