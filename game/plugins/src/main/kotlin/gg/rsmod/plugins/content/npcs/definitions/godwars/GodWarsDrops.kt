package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.VoidDropTables

/**
 * RCV-011 Q-043-d: drop tables of the four God Wars generals and their twelve bodyguards.
 *
 * Root cause: the generals rolled one shared placeholder template ("provisional 2011-baseline": invented coin/rune/log
 * slots, no godsword shards) and the bodyguards fell through to the generic osrsbox bulk rows. Owner decision
 * 2026-09-13 ("Void, 2011-filtered"): Void's GWD tables (`data/cfg/npcs/godwars-drops.json`, generated from Void
 * `*.drops.toml`), keeping only 667 items and removing post-2011 rows (elite clue scrolls, long/curved bone) and rows of
 * parked content (hard clue scrolls: Treasure Trails; goblin champion scroll: Champions' Challenge) — every removal is
 * listed in the JSON `excluded` block. Owner 2026-09-13 "remove the osrs items": the OSRS-only rows manta ray and
 * crushed nest (Armadyl bodyguards), Zamorakian spear (K'ril and Zamorak bodyguards) and dragon bolts (e) (Kree'arra)
 * are excluded too; their roll share becomes nothing, every other rate is unchanged.
 *
 * Frozen key pieces are not in these tables: Darkan Player.sendGodwarsKill drops them from the followers
 * ([gg.rsmod.plugins.content.areas.godwars.GodWars.frozenKeyPiece]); the generals (combat > 134) never drop one.
 */
object GodWarsDrops {
    const val PATH = "./data/cfg/npcs/godwars-drops.json"

    val NPC_IDS =
        intArrayOf(
            Npcs.GENERAL_GRAARDOR, Npcs.SERGEANT_STRONGSTACK, Npcs.SERGEANT_STEELWILL, Npcs.SERGEANT_GRIMSPIKE,
            Npcs.KREEARRA, Npcs.FLIGHT_KILISA, Npcs.WINGMAN_SKREE, Npcs.FLOCKLEADER_GEERIN,
            Npcs.COMMANDER_ZILYANA, Npcs.STARLIGHT, Npcs.GROWLER, Npcs.BREE,
            Npcs.KRIL_TSUTSAROTH, Npcs.TSTANON_KARLAK, Npcs.ZAKLN_GRITCH, Npcs.BALFRUG_KREEYATH,
        )

    fun tables(doc: VoidDropTables.Document): Map<Int, DropTableBuilder.() -> Unit> =
        NPC_IDS.associateWith { id ->
            val root = doc.npcs[id.toString()] ?: error("godwars-drops.json has no table for npc $id")
            VoidDropTables.builder(doc, root)
        }

    fun register(doc: VoidDropTables.Document) {
        tables(doc).forEach { (id, table) -> DropTableFactory.register(table, id) }
    }
}
