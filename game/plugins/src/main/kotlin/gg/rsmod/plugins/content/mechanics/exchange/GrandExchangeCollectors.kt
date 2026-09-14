package gg.rsmod.plugins.content.mechanics.exchange

/**
 * RCV-012.B16 - who opens the Grand Exchange collection box.
 *
 * Owner live: "Collect" at every bank(er) answered "You have nothing to collect". Root cause: the bank booths' `Collect` option paid
 * straight into the inventory (a stand-in written before the native collection box existed) and no banker npc had `Collect` bound at
 * all. Novite (revision 667) opens `ExchangeManagement.openCollectionBox` for both: `ObjectHandler` (bank booth / counter "Collect") and
 * `NPCHandler.handleOption5` (bankers). The native box is `GrandExchangeInterface.openCollectionBox` (interface 109, RCV-010 C3).
 *
 * Npc criterion: the cache definition carries both `Bank` and `Collect`. Novite matches names containing "banker"; the cache pair is the
 * same statement for every bank npc and also covers bank npcs without "banker" in their name (Fadli, Jade, Odovacar, Magnus Gram,
 * TzHaar-Ket-Zuh, Vyre treasurer, ...) - ADAPTED. Excluded by it: Ghost disciple 1686 (ectotokens) and Advisor Ghrim 3120 (Miscellania),
 * whose `Collect` is another feature, and Head Guard 13932 (`Collect-Bank`).
 */
object GrandExchangeCollectors {
    fun isBankCollectNpc(options: Array<String?>): Boolean =
        options.any { it.equals("Collect", ignoreCase = true) } && options.any { it.equals("Bank", ignoreCase = true) }
}
