package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile

/** 01-route.tsv + 40-loc-placements.tsv: landing neighbours of the corresponding Exit/Entry, in OSRS coordinates. */
enum class ToaPath(val door: Int, val challengeEntry: Tile, val bossDoor: Int, val bossEntry: Tile) {
    SCABARAS(ToaLocs.SCABARAS, Tile(3523, 5279), ToaLocs.KEPHRI_ENTRY, Tile(3535, 5408)),
    HET(ToaLocs.HET, Tile(3697, 5280), ToaLocs.AKKHA_ENTRY, Tile(3697, 5408, 1)),
    APMEKEN(ToaLocs.APMEKEN, Tile(3793, 5280), ToaLocs.BABA_ENTRY, Tile(3790, 5408)),
    CRONDIS(ToaLocs.CRONDIS, Tile(3953, 5280), ToaLocs.ZEBAK_ENTRY, Tile(3959, 5408, 1)),
}
