package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * N227 (owner 2026-10-23: "verwijder alle quest iconen in de worldmap zorg er ook voor dat ze niet in de minimap getoond worden
 * wij hebben namelijk custom questen") and N226 ("de quest npcs op de 2e verdieping hebben geen minimap quest iconen ... alleen
 * de quest minimap iconen tonen als je op de 2e verdieping bent").
 *
 * **The standard quest icons.** Every 667 quest start is a MapElementType (config index 2, group 36) of category 1399 - 179 of
 * them, "Start of<br>Black Knights' Fortress" and so on, sprite 1692 - each gated by its quest's var (op 9: shown while the quest
 * var is in 0..0, i.e. not started). Our server never sets those vars, so every icon shows. Both the world map
 * (`WorldMap.isEnabled` / its element iterators) and the minimap (`Minimap.drawMapElement`) ask `MapElementType.variableTest`
 * first; this tool rewrites each element's op-9 range to 1..0, which no value meets, so the icon is gone from both maps
 * wherever it is placed. Everything else of the element stays byte for byte.
 *
 * **Our quest icon.** The short quests (quests/foundation) start at the Archaeologist, the Oneiromancer and Azzanadra, who stand
 * on the Royal Hall's first upper floor. The 667 quest sprite (1692) is a world-map sized starburst, far too big for the
 * minimap (seen in the test client), so the OSRS minimap quest-start icon is imported as it is: OSRS sprite [OSRS_QUEST_SPRITE]
 * (the sprite of 185 of the 2686 cache's AreaTypes, category 1056 - every OSRS quest start) as 667 sprite [QUEST_SPRITE]. A new
 * element [HALL_QUEST_ELEMENT] carries it with op 7 flags 2: drawn on the minimap (`Minimap.drawMapElement` asks only that flag)
 * and not enabled, which keeps it off the world map (`WorldMap.isEnabled` - the world map also gets npc icons through
 * NpcMapIcons / NpcWorldMapIcons, and op 16 alone is not asked there). The three NPCTypes get it as their `mapElement`
 * (op 142, appended as [CasinoMinimapIconTool] does). The minimap draws an npc's element only for npcs on the player's own
 * floor (`Minimap`: `npc.level == PlayerEntity.self.level`), so the icons show upstairs only and move with the npcs when the
 * owner moves them. The same npcs elsewhere (Bandit Camp, the Jaldraocht pyramid, Lunar Isle) start the same quests and carry
 * the same icon.
 *
 * Usage: `./gradlew :game:runQuestMapIconTool --args="plan|apply"`.
 */
object QuestMapIconTool {
    const val CONFIG_INDEX = 2
    const val MAP_ELEMENT_GROUP = 36
    const val QUEST_CATEGORY = 1399
    const val SPRITE_INDEX = 8
    /** The 667 world-map quest sprite (too big for the minimap); the first hall element used it. */
    const val WORLD_MAP_QUEST_SPRITE = 1692

    /** OSRS 2686 sprite of every quest-start AreaType (category 1056). */
    const val OSRS_QUEST_SPRITE = 1454

    /** [OSRS_QUEST_SPRITE] in the 667 sprite index: the first free id after the keybinding import (8500..8517). */
    const val QUEST_SPRITE = 8518

    /** The first free MapElementType id in both caches (the last 667 element is 1115). */
    const val HALL_QUEST_ELEMENT = 1116

    /** Archaeologist, Oneiromancer, Azzanadra - the short quests' start npcs (FoundationQuests.startNpc). */
    val QUEST_NPCS = listOf(1918, 4511, 1971)

    /** One opcode of a MapElementType stream: where it starts and where its payload starts. */
    class Op(val code: Int, val at: Int, val payload: Int, val end: Int)

    /** Walks a MapElementType stream exactly as the client's `MapElementType.decode` reads it. */
    fun ops(b: ByteArray): List<Op> {
        val out = ArrayList<Op>()
        var p = 0
        fun u1() = b[p++].toInt() and 0xFF
        fun str() {
            while (b[p] != 0.toByte()) p++
            p++
        }
        while (true) {
            val at = p
            val code = u1()
            if (code == 0) return out
            val payload = p
            when (code) {
                1, 2, 18, 19 -> p += 2
                3, 17, in 10..14 -> str()
                4, 5 -> p += 3
                6, 7, 8 -> p += 1
                9, 20 -> p += 12
                15 -> {
                    val polygons = u1()
                    p += polygons * 2 * 2 + 4
                    val colours = u1()
                    p += colours * 4 + polygons
                }
                16 -> Unit
                21, 22 -> p += 4
                23 -> p += 3
                24 -> p += 4
                249 -> {
                    repeat(u1()) {
                        val string = u1() == 1
                        p += 3
                        if (string) str() else p += 4
                    }
                }
                else -> error("unknown MapElementType opcode $code at $at")
            }
            out += Op(code, at, payload, p)
        }
    }

    private fun u2(
        b: ByteArray,
        at: Int,
    ) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun i4(
        b: ByteArray,
        at: Int,
    ) = ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    fun category(b: ByteArray): Int = ops(b).firstOrNull { it.code == 19 }?.let { u2(b, it.payload) } ?: -1

    /** The op-9 var range (start, end) of [b], or null without op 9. */
    fun varRange(b: ByteArray): Pair<Int, Int>? = ops(b).firstOrNull { it.code == 9 }?.let { i4(b, it.payload + 4) to i4(b, it.payload + 8) }

    /** A copy of a quest element whose op-9 range 1..0 never passes `variableTest`. */
    fun hidden(b: ByteArray): ByteArray {
        val op9 = requireNotNull(ops(b).firstOrNull { it.code == 9 }) { "quest element without op 9" }
        check(u2(b, op9.payload) != 0xFFFF || u2(b, op9.payload + 2) != 0xFFFF) { "op 9 without a var: variableTest is always true" }
        val out = b.copyOf()
        intArrayOf(0, 0, 0, 1, 0, 0, 0, 0).forEachIndexed { i, v -> out[op9.payload + 4 + i] = v.toByte() }
        return out
    }

    /** True when [b] is a quest element this tool has hidden (or that was never visible). */
    fun isHidden(b: ByteArray): Boolean = varRange(b)?.let { (start, end) -> start > end } ?: false

    /** The hall quest icon: quest sprite, minimap only (op 7 flags 2: shown on the minimap, not enabled on the world map; op 16). */
    fun hallElement(
        sprite: Int = QUEST_SPRITE,
        flags: Int = 2,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            out.write(1)
            out.write(sprite ushr 8)
            out.write(sprite and 0xFF)
            out.write(7)
            out.write(flags)
            out.write(16)
            out.write(0)
            out.toByteArray()
        }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val source = ModernCacheReader(java.io.File(OsrsItemImportTool.SOURCE_CACHE))
        val osrsSprite =
            try {
                source.file(SPRITE_INDEX, OSRS_QUEST_SPRITE, 0) ?: error("OSRS sprite $OSRS_QUEST_SPRITE missing")
            } finally {
                source.close()
            }
        val libraries = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            val library = libraries.first()
            val ids = library.index(CONFIG_INDEX).archive(MAP_ELEMENT_GROUP)!!.fileIds()
            var hidden = 0
            ids.forEach { id ->
                val current = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, id) ?: return@forEach
                if (current.isEmpty() || category(current) != QUEST_CATEGORY || isHidden(current)) return@forEach
                libraries.drop(1).forEach { other ->
                    check(other.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, id)?.contentEquals(current) == true) { "element $id differs between the caches" }
                }
                mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, id, hidden(current), "N227 quest map element $id hidden", CacheItemProbeTool.sha1(current))
                hidden++
            }
            println("quest elements to hide: $hidden")
            val first = library.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, HALL_QUEST_ELEMENT)
            libraries.forEach { lib ->
                val existing = lib.data(CONFIG_INDEX, MAP_ELEMENT_GROUP, HALL_QUEST_ELEMENT)
                check(existing == null || listOf(hallElement(), hallElement(QUEST_SPRITE, 3), hallElement(WORLD_MAP_QUEST_SPRITE, 3)).any { existing.contentEquals(it) }) { "element $HALL_QUEST_ELEMENT is taken in a cache" }
                val sprite = lib.data(SPRITE_INDEX, QUEST_SPRITE, 0)
                check(sprite == null || sprite.contentEquals(osrsSprite)) { "sprite $QUEST_SPRITE is taken in a cache" }
            }
            if (library.data(SPRITE_INDEX, QUEST_SPRITE, 0) == null) {
                mutations += CacheMutation(SPRITE_INDEX, QUEST_SPRITE, 0, osrsSprite, "N226 OSRS quest-start sprite osrs:$OSRS_QUEST_SPRITE -> 667:$QUEST_SPRITE", null)
            }
            if (first == null || !first.contentEquals(hallElement())) {
                mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, HALL_QUEST_ELEMENT, hallElement(), "N226 hall quest icon element", first?.let(CacheItemProbeTool::sha1))
            }
            QUEST_NPCS.forEach { npc ->
                val group = npc ushr 7
                val file = npc and 0x7F
                val current = library.data(CasinoMinimapIconTool.INDEX_NPC, group, file) ?: error("npc $npc missing")
                val before = CasinoMinimapIconTool.appendedMapElement(current)
                if (before == HALL_QUEST_ELEMENT) return@forEach
                check(before == -1) { "npc $npc already carries mapElement $before; refusing" }
                val updated = CasinoMinimapIconTool.patched(current, HALL_QUEST_ELEMENT)
                check(CasinoMinimapIconTool.appendedMapElement(updated) == HALL_QUEST_ELEMENT)
                mutations += CacheMutation(CasinoMinimapIconTool.INDEX_NPC, group, file, updated, "N226 npc $npc quest minimap icon", CacheItemProbeTool.sha1(current))
            }
        } finally {
            libraries.forEach { it.close() }
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("mutations=${mutations.size}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("DRY_RUN transaction=${transaction.id}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
