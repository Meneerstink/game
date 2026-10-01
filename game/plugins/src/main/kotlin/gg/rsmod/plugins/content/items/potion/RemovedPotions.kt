package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items

/** Potion families removed from gameplay by the owner on 2026-09-15. */
object RemovedPotions {
    val doseItemIds =
        setOf(
            Items.EXTREME_ATTACK_4,
            Items.EXTREME_ATTACK_3,
            Items.EXTREME_ATTACK_2,
            Items.EXTREME_ATTACK_1,
            Items.EXTREME_STRENGTH_4,
            Items.EXTREME_STRENGTH_3,
            Items.EXTREME_STRENGTH_2,
            Items.EXTREME_STRENGTH_1,
            Items.EXTREME_DEFENCE_4,
            Items.EXTREME_DEFENCE_3,
            Items.EXTREME_DEFENCE_2,
            Items.EXTREME_DEFENCE_1,
            Items.EXTREME_RANGING_4,
            Items.EXTREME_RANGING_3,
            Items.EXTREME_RANGING_2,
            Items.EXTREME_RANGING_1,
            Items.OVERLOAD_4,
            Items.OVERLOAD_3,
            Items.OVERLOAD_2,
            Items.OVERLOAD_1,
            Items.PRAYER_RENEWAL_4,
            Items.PRAYER_RENEWAL_3,
            Items.PRAYER_RENEWAL_2,
            Items.PRAYER_RENEWAL_1,
            Items.RECOVER_SPECIAL_4,
            Items.RECOVER_SPECIAL_3,
            Items.RECOVER_SPECIAL_2,
            Items.RECOVER_SPECIAL_1,
        )

    /**
     * Owner 2026-09-29 ("remove every divine and overload from our server"): every OSRS divine potion and the Deadman blighted
     * overload. Copies players already own stay where they are but cannot be drunk, mixed or decanted (owner's choice).
     */
    val divineAndBlightedDoseIds =
        setOf(
            Items.DIVINE_SUPER_COMBAT_POTION_4, Items.DIVINE_SUPER_COMBAT_POTION_3, Items.DIVINE_SUPER_COMBAT_POTION_2, Items.DIVINE_SUPER_COMBAT_POTION_1,
            Items.DIVINE_SUPER_ATTACK_POTION_4, Items.DIVINE_SUPER_ATTACK_POTION_3, Items.DIVINE_SUPER_ATTACK_POTION_2, Items.DIVINE_SUPER_ATTACK_POTION_1,
            Items.DIVINE_SUPER_STRENGTH_POTION_4, Items.DIVINE_SUPER_STRENGTH_POTION_3, Items.DIVINE_SUPER_STRENGTH_POTION_2, Items.DIVINE_SUPER_STRENGTH_POTION_1,
            Items.DIVINE_SUPER_DEFENCE_POTION_4, Items.DIVINE_SUPER_DEFENCE_POTION_3, Items.DIVINE_SUPER_DEFENCE_POTION_2, Items.DIVINE_SUPER_DEFENCE_POTION_1,
            Items.DIVINE_RANGING_POTION_4, Items.DIVINE_RANGING_POTION_3, Items.DIVINE_RANGING_POTION_2, Items.DIVINE_RANGING_POTION_1,
            Items.DIVINE_MAGIC_POTION_4, Items.DIVINE_MAGIC_POTION_3, Items.DIVINE_MAGIC_POTION_2, Items.DIVINE_MAGIC_POTION_1,
            Items.DIVINE_BASTION_POTION_4, Items.DIVINE_BASTION_POTION_3, Items.DIVINE_BASTION_POTION_2, Items.DIVINE_BASTION_POTION_1,
            Items.DIVINE_BATTLEMAGE_POTION_4, Items.DIVINE_BATTLEMAGE_POTION_3, Items.DIVINE_BATTLEMAGE_POTION_2, Items.DIVINE_BATTLEMAGE_POTION_1,
        ) + gg.rsmod.plugins.content.mechanics.pvp.breach.BlightedOverload.DOSES.toSet()

    /** Includes the four noted Prayer renewal variants exposed by this revision's cache. */
    val itemIds =
        doseItemIds + divineAndBlightedDoseIds +
            setOf(
                Items.PRAYER_RENEWAL_4_NOTED,
                Items.PRAYER_RENEWAL_3_NOTED,
                Items.PRAYER_RENEWAL_2_NOTED,
                Items.PRAYER_RENEWAL_1_NOTED,
            )

    val recipeProducts =
        setOf(
            Items.EXTREME_ATTACK_3,
            Items.EXTREME_STRENGTH_3,
            Items.EXTREME_DEFENCE_3,
            Items.EXTREME_RANGING_3,
            Items.RECOVER_SPECIAL_3,
            Items.PRAYER_RENEWAL_3,
        )
}
