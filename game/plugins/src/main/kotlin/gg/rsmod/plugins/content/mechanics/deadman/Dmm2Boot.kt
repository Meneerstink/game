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
        ThunderKhopesh.appearance = khopeshAppearance()
        ThunderKhopesh.register()
    }

    /**
     * DMM2 fase 7 (Claude 17-11, testclient: 4 treffers zonder bliksem - appearance was never set, so passive, special and the chest
     * entry were silently off). Every asset is an applied cache-2686 import (ids.tsv): seq HUMAN_SPECIAL_KHOPESH 11812, spotanims
     * FX_KHOPESH_SPECIAL 3030, FX_KHOPESH_LIGHTNING_BASIC 3032 (passive) and FX_KHOPESH_LIGHTNING_SPECIAL 3033 (special/PvP bolts)
     * (RuneLite gameval names). Missing any of them keeps the weapon's extras off, as before.
     * ONZEKER (no OSRS timing source yet, phase-3 advice): the bolt lands with the melee hit (delay 1); measure in an OSRS video.
     */
    private fun khopeshAppearance(): ThunderKhopesh.Appearance? {
        val specialSeq = Dmm2Ids.seq(11812) ?: return null
        val specialGfx = Dmm2Ids.spot(3030) ?: return null
        val basic = Dmm2Ids.spot(3032) ?: return null
        val specialBolt = Dmm2Ids.spot(3033) ?: return null
        return object : ThunderKhopesh.Appearance {
            override val passiveDelay = ThunderKhopesh.STANDARD_MELEE_DELAY
            override val pvmHitDelays = listOf(ThunderKhopesh.STANDARD_MELEE_DELAY, ThunderKhopesh.STANDARD_MELEE_DELAY)
            override fun swing(player: gg.rsmod.game.model.entity.Player, special: Boolean) {
                if (!special) return // ordinary swings keep the sword animation from CorruptedWeapons
                player.animate(specialSeq)
                player.graphic(specialGfx)
            }
            override fun bolt(player: gg.rsmod.game.model.entity.Player, tile: gg.rsmod.game.model.Tile, special: Boolean) {
                player.world.spawn(gg.rsmod.game.model.TileGraphic(tile, if (special) specialBolt else basic, 0))
            }
        }
    }
}
