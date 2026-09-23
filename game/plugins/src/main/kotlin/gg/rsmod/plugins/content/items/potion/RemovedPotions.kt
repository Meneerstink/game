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

    /** Includes the four noted Prayer renewal variants exposed by this revision's cache. */
    val itemIds =
        doseItemIds +
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
