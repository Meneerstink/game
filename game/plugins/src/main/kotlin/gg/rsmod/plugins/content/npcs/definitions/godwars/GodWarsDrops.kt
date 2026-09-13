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
 * listed in the JSON `excluded` block. OSRS-flavoured rows kept for owner review: manta ray and crushed nest (Armadyl
 * bodyguards), Zamorakian spear (K'ril and Zamorak bodyguards), dragon bolts (e) (Kree'arra).
 *
 * SOURCE_BLOCKED: Frozen key pieces have no rate in any donor (Void leaves the bodyguard piece commented out). The
 * generals keep the pre-existing 3/1000 piece roll, labelled legacy, so the Ancient Prison stays reachable until the
 * owner decides.
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

    /** Legacy (unsourced) Frozen key piece per general: 3 slots of 1000, exactly the removed placeholder tables' rate. */
    val LEGACY_FROZEN_KEY_PIECE =
        mapOf(
            Npcs.GENERAL_GRAARDOR to Items.FROZEN_KEY_PIECE_BANDOS,
            Npcs.KREEARRA to Items.FROZEN_KEY_PIECE_ARMADYL,
            Npcs.COMMANDER_ZILYANA to Items.FROZEN_KEY_PIECE_SARADOMIN,
            Npcs.KRIL_TSUTSAROTH to Items.FROZEN_KEY_PIECE_ZAMORAK,
        )
    const val LEGACY_PIECE_SLOTS = 3
    const val LEGACY_PIECE_TOTAL = 1000

    fun tables(doc: VoidDropTables.Document): Map<Int, DropTableBuilder.() -> Unit> =
        NPC_IDS.associateWith { id ->
            val root = doc.npcs[id.toString()] ?: error("godwars-drops.json has no table for npc $id")
            val piece = LEGACY_FROZEN_KEY_PIECE[id]
            VoidDropTables.builder(doc, root) {
                if (piece != null) {
                    table("legacy_frozen_key_piece") {
                        total(LEGACY_PIECE_TOTAL)
                        obj(piece, slots = LEGACY_PIECE_SLOTS)
                        nothing(LEGACY_PIECE_TOTAL - LEGACY_PIECE_SLOTS)
                    }
                }
            }
        }

    fun register(doc: VoidDropTables.Document) {
        tables(doc).forEach { (id, table) -> DropTableFactory.register(table, id) }
    }
}
