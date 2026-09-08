package gg.rsmod.plugins.content.mechanics.lifesteal

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.heal

/**
 * Guthan the Infested's "Infestation" set effect (RSPS_DEFINITIEF_MASTERPLAN.md's "Lifesteal"
 * further-foundations item: "Blood fury, Guthan, Sang"). Sourced from the OSRS Wiki's "Guthan
 * the Infested's equipment" article: while wearing the full Guthan's set (helm, warspear,
 * platebody, chainskirt) there is a 25% (1/4) chance on any successful *melee* hit to heal the
 * wielder HP equal to the damage just dealt, capped at their max HP. The wiki's "+10 above max
 * HP" variant additionally requires the amulet of the damned, whose item id was already
 * confirmed absent from this ~2011/rev-667 cache during the P6 audit - that variant is
 * therefore out of scope here, and this always caps at plain max HP.
 *
 * Blood fury and the Sanguinesti staff, the other two items this master-plan item names, are
 * real 2018+ OSRS items with zero ids anywhere in this cache's `Items.kt` (confirmed via grep)
 * - genuinely absent, not merely unwired, so nothing to build for either of them.
 *
 * Confirmed via `barrows/brothers.plugin.kts`'s own doc comment that this player-side set
 * effect was never implemented anywhere in this codebase prior to this pass (the brothers'
 * combat defs deliberately don't replicate it since it's real player equipment behaviour, not
 * monster behaviour).
 *
 * Wired into [gg.rsmod.plugins.content.combat.PawnExt.dealHit]'s "BATCH 2" block, the same
 * once-per-landed-hit choke point [gg.rsmod.plugins.content.mechanics.prayer.AncientCurses]'s
 * Sap/Leech/Soul Split already uses - kept as its own file/object rather than folded into an
 * existing one since it's a distinct, self-contained equipment-set mechanic with its own
 * sourcing note, matching this session's convention of one file per named set/mechanic.
 */
object GuthanLifesteal {
    private const val PROC_CHANCE_NUMERATOR = 1
    private const val PROC_CHANCE_DENOMINATOR = 4

    private fun isWearingGuthan(pawn: Pawn): Boolean {
        if (!pawn.entityType.isPlayer) return false
        val player = pawn as Player
        return player.hasEquipped(
            EquipmentType.HEAD,
            Items.GUTHANS_HELM,
            Items.GUTHANS_HELM_25,
            Items.GUTHANS_HELM_50,
            Items.GUTHANS_HELM_75,
            Items.GUTHANS_HELM_100,
        ) &&
            player.hasEquipped(
                EquipmentType.WEAPON,
                Items.GUTHANS_WARSPEAR,
                Items.GUTHANS_WARSPEAR_25,
                Items.GUTHANS_WARSPEAR_50,
                Items.GUTHANS_WARSPEAR_75,
                Items.GUTHANS_WARSPEAR_100,
            ) &&
            player.hasEquipped(
                EquipmentType.CHEST,
                Items.GUTHANS_PLATEBODY,
                Items.GUTHANS_PLATEBODY_25,
                Items.GUTHANS_PLATEBODY_50,
                Items.GUTHANS_PLATEBODY_75,
                Items.GUTHANS_PLATEBODY_100,
            ) &&
            player.hasEquipped(
                EquipmentType.LEGS,
                Items.GUTHANS_CHAINSKIRT,
                Items.GUTHANS_CHAINSKIRT_25,
                Items.GUTHANS_CHAINSKIRT_50,
                Items.GUTHANS_CHAINSKIRT_75,
                Items.GUTHANS_CHAINSKIRT_100,
            )
    }

    fun onDamageDealt(
        attacker: Pawn,
        combatClass: CombatClass,
        damage: Int,
    ) {
        if (attacker !is Player || damage <= 0 || combatClass != CombatClass.MELEE) return
        if (!isWearingGuthan(attacker)) return
        if (!attacker.world.chance(PROC_CHANCE_NUMERATOR, PROC_CHANCE_DENOMINATOR)) return
        attacker.heal(damage)
    }
}
