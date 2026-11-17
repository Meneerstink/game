package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot

/** Wiki chest rewards have UNKNOWN probabilities. Explicit local advice, OFF pending the release review; never labelled OSRS rates. */
object Dmm2ChestRewards {
    val enabled = java.lang.Boolean.getBoolean("rsps.dmm2.chestRewards")
    fun entries(): List<DeadmanChestLoot.Entry> {
        if (!enabled) return emptyList()
        return listOfNotNull(
            Dmm2Ids.ItemId.ADVANCED_WEAPONRY.local?.let { DeadmanChestLoot.Entry(it, 1, 8) },
            Dmm2Ids.ItemId.UNDEAD.local?.let { DeadmanChestLoot.Entry(it, 1, 8) },
            Dmm2Ids.ItemId.KHOPESH.local?.takeIf { ThunderKhopesh.appearance != null }?.let { DeadmanChestLoot.Entry(it, 1, 1) },
        )
    }
}
