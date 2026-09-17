package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.sendRunEnergy
import gg.rsmod.plugins.content.npcs.definitions.barrows.BarrowsSetEffects

/**
 * OSRS-IMPORT audit round 2026-09-17b, Torag the Corrupted's "Corruption" set effect. Sourced from
 * [BarrowsSetEffects]'s own OSRS Wiki raw-wikitext doc comment (audited 2026-09-14): "25% chance per
 * successful hit to lower the player's energy by 20% of the current amount". That file's `barrows_set_effects.
 * plugin.kts` already wires this correctly for the NPC brother's own attacks against a player; this is the
 * player-worn side, which was entirely missing before this fix (a `MeleeCombatFormula.isWearingTorag` helper
 * existed, but its only call site was a disabled, unrelated block comment about a not-yet-existing "Amulet of
 * the Damned" mechanic - confirmed via a project-wide grep for "Torag"/"isWearingTorag" that no player-side
 * energy-drain wiring existed anywhere).
 * ADAPTED: only against a [Player] target, matching this codebase's existing NPC-side gate
 * (`barrows_set_effects.plugin.kts`'s `if (target is Player)`) - run energy is a player-only resource.
 * Reuses [BarrowsSetEffects.toragEnergyAfter] and [BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT] so the 25 %/20 %
 * formula is defined once, not duplicated between the NPC and player sides.
 * Wired into [gg.rsmod.plugins.content.combat.PawnExt.dealHit]'s "BATCH 2" block, the same
 * once-per-landed-hit choke point [gg.rsmod.plugins.content.mechanics.lifesteal.GuthanLifesteal] and
 * [AhrimBlightedAura] already use.
 */
object ToragEnergyDrain {
    private fun isWearingTorag(pawn: Pawn): Boolean {
        if (!pawn.entityType.isPlayer) return false
        val player = pawn as Player
        return player.hasEquipped(
            EquipmentType.HEAD,
            Items.TORAGS_HELM, Items.TORAGS_HELM_25, Items.TORAGS_HELM_50, Items.TORAGS_HELM_75, Items.TORAGS_HELM_100,
        ) &&
            player.hasEquipped(
                EquipmentType.WEAPON,
                Items.TORAGS_HAMMERS, Items.TORAGS_HAMMERS_25, Items.TORAGS_HAMMERS_50, Items.TORAGS_HAMMERS_75, Items.TORAGS_HAMMERS_100,
            ) &&
            player.hasEquipped(
                EquipmentType.CHEST,
                Items.TORAGS_PLATEBODY, Items.TORAGS_PLATEBODY_25, Items.TORAGS_PLATEBODY_50, Items.TORAGS_PLATEBODY_75, Items.TORAGS_PLATEBODY_100,
            ) &&
            player.hasEquipped(
                EquipmentType.LEGS,
                Items.TORAGS_PLATELEGS, Items.TORAGS_PLATELEGS_25, Items.TORAGS_PLATELEGS_50, Items.TORAGS_PLATELEGS_75, Items.TORAGS_PLATELEGS_100,
            )
    }

    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        combatClass: CombatClass,
    ) {
        if (attacker !is Player || target !is Player || combatClass != CombatClass.MELEE) return
        if (!isWearingTorag(attacker)) return
        if (!attacker.world.chance(BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT, 100)) return
        target.runEnergy = BarrowsSetEffects.toragEnergyAfter(target.runEnergy)
        target.sendRunEnergy(target.runEnergy.toInt())
    }
}
