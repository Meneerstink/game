package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.plugins.api.cfg.Items

/** Local ids of the OSRS breach assets imported into both caches (OSRS_IMPORT_MASTER.yml records each transaction). */
object BreachIds {
    /** OSRS loc 49561 "Breach" / 49563 "Boss Spawn" (OsrsLocImportTool deadman-breach, tx-20260919-164612). */
    const val BREACH_LOC = 62747
    const val BOSS_SPAWN_LOC = 62748
}

/** OSRS items imported for the breach drop table (OsrsItemImportTool deadman-breach, tx-20260919-164727). */
object ItemIds {
    const val CHITIN = Items.CHITIN
    const val TRINKET_OF_FAIRIES = Items.TRINKET_OF_FAIRIES
    const val TRINKET_OF_AVARICE = Items.TRINKET_OF_AVARICE
    const val TRINKET_OF_UNDEAD = Items.TRINKET_OF_UNDEAD
    const val TRINKET_OF_FORTUITY_INACTIVE = Items.TRINKET_OF_FORTUITY_INACTIVE
}
