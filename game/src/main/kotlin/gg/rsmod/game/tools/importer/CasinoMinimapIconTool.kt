package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Puts a minimap icon over the Grand Exchange gambling hall (owner 2026-09-20: "also put an icon on the minimap
 * gambling icon").
 *
 * **How a minimap icon is produced at all.** This client draws minimap icons from three places
 * (`Minimap.draw`): the world-map's own `_staticelements` file, the `mapelement` of every LocType in the loaded
 * scene, and the `mapElement` of every NPC that is `displayOnMiniMap` and `interactive`. The hall is built at
 * runtime by `casino_grandexchange.plugin.kts`, not in the cache's map data, so the first route is unavailable;
 * the scene route would work but would brand every instance of whichever object is chosen, everywhere in the
 * world. The NPC route marks exactly one thing in exactly one place, which is what an icon over a single hall
 * needs - so the icon is hung on one croupier.
 *
 * **Which croupier.** [ICON_NPC] is the blackjack dealer, who stands in the middle of the four-table row, so the
 * star sits over the centre of the hall rather than at one end. The other three carry no icon, which is why the
 * hall gets one star rather than four.
 *
 * **Which icon.** [MAP_ELEMENT] is 635, whose sprite is 1377 - RuneScape's gold "activity" star, the icon the game
 * itself uses to say "there is something to do here". That was not guessed: every MapElementType in this cache was
 * decoded and its sprite rendered to a contact sheet, and 1377 is the star (1288 is the bank's gold `$`, 1314 the
 * pub tankard, 1339 the transport arrow - each of which matches what [FeroxMinimapTool] independently proved those
 * same elements to be). SOURCE_BLOCKED for anything more specific: this revision has no dice, chip or card icon,
 * so the generic activity star is the closest honest choice.
 *
 * **Why appending is safe.** An NPCType stream is a run of opcodes terminated by a 0 byte, so opcode 142
 * (`mapElement`, a big-endian short - `NPCType.decode` in the client) is written immediately before that
 * terminator. The server's own `NpcDef.decode` already reads and ignores opcode 142, so the definition still
 * decodes at boot; the tool re-reads what it wrote and refuses to continue if it does not.
 *
 * Usage: `./gradlew :game:runCasinoMinimapIconTool --args="plan|apply"`
 */
object CasinoMinimapIconTool {
    const val INDEX_NPC = OsrsNpcImportTool.INDEX_NPC
    val TARGETS = OsrsItemImportTool.TARGETS

    /** `Npcs.GAMBLER_3002`, the blackjack croupier in the middle of the hall. */
    const val ICON_NPC = 3002

    /** MapElementType 635 - sprite 1377, the gold activity star. */
    const val MAP_ELEMENT = 635

    /** NPCType opcode 142: `mapElement`, one big-endian short. */
    const val OPCODE_MAP_ELEMENT = 142

    /** Copies an NPCType stream with opcode 142 appended before its terminator. */
    fun patched(
        current: ByteArray,
        mapElement: Int,
    ): ByteArray =
        ByteArrayOutputStream(current.size + 3).use { out ->
            out.write(current, 0, current.size - 1)
            out.write(OPCODE_MAP_ELEMENT)
            out.write(mapElement ushr 8)
            out.write(mapElement and 0xFF)
            out.write(0)
            out.toByteArray()
        }

    /**
     * The `mapElement` this tool appended, or -1 when the stream does not end with one.
     *
     * Only the tail is inspected, and deliberately so. An NPCType stream cannot be walked forwards without a
     * table of every opcode's payload size - a payload byte that happens to be 0 looks exactly like the
     * terminator, which is why the first version of this read -1 back from a definition it had just written
     * correctly. The opcode this tool writes is always the last one before the terminator, so `142 hi lo 00` at
     * the end is both necessary and sufficient to recognise it.
     */
    fun appendedMapElement(bytes: ByteArray): Int {
        if (bytes.size < 4) {
            return -1
        }
        val tail = bytes.size - 4
        if ((bytes[tail].toInt() and 0xFF) != OPCODE_MAP_ELEMENT || bytes[bytes.size - 1] != 0.toByte()) {
            return -1
        }
        return ((bytes[tail + 1].toInt() and 0xFF) shl 8) or (bytes[tail + 2].toInt() and 0xFF)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }

        val group = ICON_NPC ushr 7
        val file = ICON_NPC and 0x7F
        val library = CacheLibrary(TARGETS[0])
        val mutations =
            try {
                val current = library.data(INDEX_NPC, group, file) ?: error("npc $ICON_NPC missing from ${TARGETS[0]}")
                check(current.last() == 0.toByte()) { "npc $ICON_NPC does not end with the opcode-0 terminator" }
                val before = appendedMapElement(current)
                check(before == -1) { "npc $ICON_NPC already carries mapElement $before; refusing" }
                val updated = patched(current, MAP_ELEMENT)
                val after = appendedMapElement(updated)
                check(after == MAP_ELEMENT) { "re-read mismatch for npc $ICON_NPC: got $after" }
                println("NPC $ICON_NPC mapElement $before -> $after, bytes ${current.size}->${updated.size}")
                listOf(
                    CacheMutation(
                        INDEX_NPC,
                        group,
                        file,
                        updated,
                        "casino minimap npc $ICON_NPC mapelement=$MAP_ELEMENT",
                        CacheItemProbeTool.sha1(current),
                    ),
                )
            } finally {
                library.close()
            }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        plan.forEach { println("PREFLIGHT $it") }
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${transaction.id} mutations=${mutations.size} (nothing written)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
