package gg.rsmod.plugins.content.areas.fremennik

import gg.rsmod.plugins.content.inter.bank.openDepositBox

/*
 * Peer the Seer's "Deposit" (667 cache op 3): he keeps a bank deposit box for adventurers in Rellekka (RuneScape Wiki "Peer the Seer").
 * The cache offered the option with nothing bound.
 */
on_npc_option(npc = Npcs.PEER_THE_SEER, option = "deposit") {
    player.openDepositBox()
}
