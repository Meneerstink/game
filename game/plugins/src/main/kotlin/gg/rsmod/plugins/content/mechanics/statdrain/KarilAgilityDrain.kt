package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.content.npcs.definitions.barrows.BarrowsSetEffects

/**
 * OSRS-IMPORT audit round 2026-09-17b, Karil the Tainted's "Tainted Shot" set effect. Sourced from
 * [BarrowsSetEffects]'s own OSRS Wiki raw-wikitext doc comment (audited 2026-09-14): "25% chance per
 * successful hit to lower the player's Agility by 20%". That file's `barrows_set_effects.plugin.kts` already
 * wires this correctly for the NPC brother's own attacks against a player; the player-worn side was entirely
 * missing before this fix (a project-wide grep for "Karil"/"isWearingKaril" found zero player-side wiring -
 * `RangedCombatFormula.kt`'s only Karil reference is an unrelated doc comment about the NPC's own accuracy roll).
 * ADAPTED: only against a [Player] target, matching this codebase's existing NPC-side gate
 * (`barrows_set_effects.plugin.kts`'s `if (target is Player)`) - the wiki's mechanic is worded as reducing "the
 * player's" Agility, and the NPC side never applies it to a monster target.
 * Reuses [BarrowsSetEffects.karilAgilityDrain] and [BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT] so the formula is
 * defined once, not duplicated between the NPC and player sides.
 * Wired into [gg.rsmod.plugins.content.combat.PawnExt.dealHit]'s "BATCH 2" block, the same
 * once-per-landed-hit choke point [gg.rsmod.plugins.content.mechanics.lifesteal.GuthanLifesteal] and
 * [AhrimBlightedAura] already use.
 */
object KarilAgilityDrain {
    private fun isWearingKaril(pawn: Pawn): Boolean {
        if (!pawn.entityType.isPlayer) return false
        val player = pawn as Player
        return player.hasEquipped(
            EquipmentType.HEAD,
            Items.KARILS_COIF, Items.KARILS_COIF_25, Items.KARILS_COIF_50, Items.KARILS_COIF_75, Items.KARILS_COIF_100,
        ) &&
            player.hasEquipped(
                EquipmentType.WEAPON,
                Items.KARILS_CROSSBOW, Items.KARILS_CROSSBOW_25, Items.KARILS_CROSSBOW_50, Items.KARILS_CROSSBOW_75, Items.KARILS_CROSSBOW_100,
            ) &&
            player.hasEquipped(
                EquipmentType.CHEST,
                Items.KARILS_TOP, Items.KARILS_TOP_25, Items.KARILS_TOP_50, Items.KARILS_TOP_75, Items.KARILS_TOP_100,
            ) &&
            player.hasEquipped(
                EquipmentType.LEGS,
                Items.KARILS_SKIRT, Items.KARILS_SKIRT_25, Items.KARILS_SKIRT_50, Items.KARILS_SKIRT_75, Items.KARILS_SKIRT_100,
            )
    }

    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        combatClass: CombatClass,
    ) {
        if (attacker !is Player || target !is Player || combatClass != CombatClass.RANGED) return
        if (!isWearingKaril(attacker)) return
        if (!attacker.world.chance(BarrowsSetEffects.SET_EFFECT_CHANCE_PERCENT, 100)) return
        val drain = BarrowsSetEffects.karilAgilityDrain(target.skills.getCurrentLevel(Skills.AGILITY))
        if (drain > 0) target.skills.decrementCurrentLevel(Skills.AGILITY, drain, capped = false)
    }
}
