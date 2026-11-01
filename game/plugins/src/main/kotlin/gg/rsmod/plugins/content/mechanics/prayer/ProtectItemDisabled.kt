package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/**
 * N503 D1 (owner 2026-11-01 01:5x, OSRS Deadman / High Risk worlds): Protect Item does not work - in all three books. It stays in the
 * prayer tab, shown with the OSRS High Risk icon (OSRS sprite 912, the pouch with a skull; cache: ProtectItemHighRiskCacheTool), and a
 * click only says why. A death never keeps an extra item for it (DeathRules.keepCount ignores it).
 *
 * Every activation route asks [refuse] first: the normal book (Prayers.toggle / toggleNow / activate), the curse book (its Protect
 * Item slot is the normal prayer, AncientCurses.onBookButton), Ruinous Powers (RuinousPowers.toggle / activate) and the three
 * quick-prayer sets (selection and the orb). [clearAtLogin] switches off what an older save still had on or selected.
 */
object ProtectItemDisabled {
    /** Our own text: OSRS sends the refusal from the server and the wording is not in the cache. */
    const val MESSAGE = "Protect Item does not work in Deadman Mode."

    /** Tells [player] why and plays the OSRS refusal sound. Always true, so callers can write `if (refuse(p)) return`. */
    fun refuse(player: Player): Boolean {
        Prayers.refuseSound(player)
        player.message(MESSAGE)
        return true
    }

    /** At login: Protect Item off and out of every quick set (normal, curses, Ruinous). Nothing else of the save changes. */
    fun clearAtLogin(player: Player) {
        if (player.getVarbit(Prayer.PROTECT_ITEM.varbit) != 0) player.setVarbit(Prayer.PROTECT_ITEM.varbit, 0)
        if (player.getVarbit(Prayer.PROTECT_ITEM.qpVarbit) != 0) player.setVarbit(Prayer.PROTECT_ITEM.qpVarbit, 0)
        if (player.getVarbit(AncientCurse.PROTECT_ITEM_VARBIT) != 0) player.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, 0)
        if (player.getVarbit(RuinousPrayer.PROTECT_ITEM.varbit) != 0) player.setVarbit(RuinousPrayer.PROTECT_ITEM.varbit, 0)
        if (player.getVarbit(RuinousPrayer.PROTECT_ITEM.quickVarbit) != 0) player.setVarbit(RuinousPrayer.PROTECT_ITEM.quickVarbit, 0)
        val quickCurse = AncientCurse.QUICK_VARBIT_BASE + AncientCurse.PROTECT_ITEM_SLOT
        if (player.getVarbit(quickCurse) != 0) player.setVarbit(quickCurse, 0)
        player.attr.remove(PROTECT_ITEM_ATTR)
    }
}
