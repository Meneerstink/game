package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.plugins.api.cfg.Objs

/**
 * The canonical set of real bank booth/chest object ids - bound to the Bank interface in
 * [bank_booths.plugin.kts] and reused as the source of truth for deriving real, cache-verified
 * bank safe zones (R03.1) in [gg.rsmod.plugins.content.mechanics.pvp.BankZones]. Deliberately
 * excludes deposit boxes: R03.1 explicitly says a deposit box does not make an automatic safe
 * bubble.
 */
object BankObjects {
    val BOOTHS =
        setOf(
            Objs.BANK_BOOTH,
            Objs.BANK_BOOTH_10517,
            Objs.BANK_BOOTH_11338,
            Objs.BANK_BOOTH_25808,
            Objs.BANK_BOOTH_36786,
            Objs.COUNTER_2019,
            Objs.COUNTER_2015,
            Objs.COUNTER_2012,
            Objs.BANK_BOOTH_35647,
            Objs.BANK_BOOTH_11758,
            Objs.BANK_BOOTH_34752,
            Objs.COUNTER_42217,
            Objs.COUNTER_42378,
            Objs.COUNTER_42377,
            Objs.BANK_BOOTH_2213,
            Objs.BANK_BOOTH_24914,
            Objs.BANK_BOOTH_49018,
            Objs.BANK_BOOTH_52589,
        )

    val CHESTS_USE =
        setOf(
            Objs.BANK_CHEST,
            Objs.BANK_CHEST_8981,
            Objs.BANK_CHEST_20607,
            Objs.BANK_CHEST_21301,
            Objs.BANK_CHEST_42192,
            Objs.BANK_CHEST_57437,
        )

    val CHESTS_BANK = setOf(Objs.BANK_CHEST_27663, Objs.CHEST_12309)

    val ALL: Set<Int> = BOOTHS + CHESTS_USE + CHESTS_BANK
}
