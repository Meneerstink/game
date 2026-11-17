package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.content.starter.DeadmanStarterPack
import java.io.File

/** Only reads Claude's explicit mapping output. Missing ids leave that service unavailable, never create placeholder items. */
object Dmm2Boot {
    fun install(directory: File = File(RspsPaths.data("cfg/dmm2"))) {
        // Load the same published mapping for all services before their plugin bindings are registered.
        // In particular, do not retain an earlier lazy lookup after a mapping has been supplied.
        val mapping = File(directory, "ids.tsv")
        if (mapping.isFile) {
            val uiTable = File(directory,"interfaces.tsv")
            val uiAssets = if (uiTable.isFile) uiTable.readLines().filter { it.startsWith("script\t") || it.startsWith("sprite\t") } else emptyList()
            val lines = mapping.readLines()
            val primary = Dmm2Ids.parse(lines)
            val extra = uiAssets.filter { row ->
                val f = row.split('\t'); require(f.size == 3)
                val existing = primary["${f[0]}:${f[1]}"]
                require(existing == null || existing == f[2].toInt()) { "Conflicting DMM2 UI asset mapping: $row" }
                existing == null
            }
            Dmm2Ids.install(lines + extra)
        }
        Dmm2Interactions.safeUi = if (Dmm2SafeDepositUi.ready()) Dmm2SafeDepositUi else null
        Dmm2Interactions.rewardUi = if (Dmm2RewardUi.ready()) Dmm2RewardUi else null
        val sourceIds = listOf(22331, 22333, 22335, 26149, 13660, 13317, 13318, 33063)
        val starter = sourceIds.map { Dmm2Ids.item(it) }
        if (starter.all { it != null }) DeadmanStarterPack.install(DeadmanStarterPack.Ids(
            starter[0]!!, starter[1]!!, starter[2]!!, starter[3]!!, starter[4]!!, starter[5]!!, starter[6]!!, starter[7]!!))
        val rewards = NigelRewardStore.Reward.values().mapNotNull { reward -> Dmm2Ids.item(reward.sourceId)?.let { reward to it } }.toMap()
        if (rewards.size == NigelRewardStore.Reward.values().size) {
            val file = File(directory, "skins.tsv")
            val skins = (if (!file.isFile) emptyList() else file.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.map {
                val f = it.split('\t'); require(f.size in 3..4)
                val base = f[1].toInt()
                NigelRewardStore.Skin(NigelRewardStore.Reward.valueOf(f[0]), base, f[2].toInt(),
                    if (f.size == 4) f[3].split(',').map(String::toInt).toSet() else setOf(base))
            }).toMutableList()
            // Read-only alias for gameplay/death. NigelRewardStore.convert explicitly refuses this recipe-only cape.
            val ancient = Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local
            val baseAncient = gg.rsmod.plugins.content.items.osrs.AncientCape.assets.cape
            if (ancient != null && baseAncient != null && skins.none { it.cosmetic == ancient })
                skins += NigelRewardStore.Skin(NigelRewardStore.Reward.CAPE_FABRIC, baseAncient, ancient)
            NigelRewardStore.install(NigelRewardStore.Contract(rewards, skins, Dmm2Ids.item(33122)))
        }
        ThunderKhopesh.register()
    }
}
