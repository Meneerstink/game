package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * N609 (14-11 nachtrun): the yellow CHARGE BAR above the two Warden statues in phase 1 of the Wardens (Kaoz entry guide 2020-2066 s:
 * the bar fills with the charge and empties after the energy ball). OSRS draws it as a healthbar: cache2686 healthbar 36 (config 2/33:
 * front sprite 2479 = yellow 219,211,0, back sprite 2480, width 100 - measured ~100 px in the 720p video).
 *
 * The 667 client draws an npc hitbar from NPCType opcode 122 (healthBarSprite; OverlayManager.java:143: frame 0 = background drawn
 * full width, frame 1 = fill clipped to the health percentage). So this tool writes:
 *  - ONE new 667 sprite group with 2 frames [OSRS 2480 back, OSRS 2479 front] (exact OSRS pixels, shared palette, no dithering);
 *  - a copy of each local statue npc (11753 Elidinis' Warden -> OSRS 11748, 11756 Tumeken's Warden -> OSRS 11749 =
 *    TOA_WARDEN_*_PHASE1, the active charging statues) with opcode 122 = that sprite. Same models/rig, so every baked seq is reused.
 * New npc ids go to the asset map (fx_kind npc, like ToaCorpseNpcTool) and are printed as ids.tsv rows.
 *
 * Usage: `plan` | `apply`   (java -cp "game\build\install\game\lib\*;<classes>" gg.rsmod.game.tools.importer.ToaChargeBarTool plan)
 */
object ToaChargeBarTool {
    private const val INDEX_SPRITES = 8
    private const val OSRS_BACK = 2480
    private const val OSRS_FRONT = 2479
    /** OSRS PHASE1 statue -> the OSRS npc whose local definition is copied (same models: cache2686 46332 / 46337). */
    val STATUES = listOf(11748 to 11753, 11749 to 11756)

    /** The 667 NPCType bytes with opcode 122 (healthBarSprite) appended before the terminating 0. */
    fun withHealthBar(def: ByteArray, sprite: Int): ByteArray {
        require(sprite in 0..0xFFFF)
        require(def.isNotEmpty() && def.last() == 0.toByte()) { "NPCType must end with opcode 0" }
        return def.copyOf(def.size - 1) + byteArrayOf(122, (sprite ushr 8).toByte(), sprite.toByte(), 0)
    }

    /** One 667 sprite group with several frames of equal canvas size; exact colours (palette <= 255), alpha channel per frame. */
    fun encodeFrames(frames: List<FeroxMapSpriteProbeTool.Frame>): ByteArray {
        val w = frames.maxOf { it.w }
        val h = frames.maxOf { it.h }
        val colours = frames.flatMap { f -> f.argb.filter { it ushr 24 != 0 }.map { it and 0xFFFFFF } }.distinct()
        require(colours.size <= 255) { "too many colours ${colours.size}" }
        val index = colours.withIndex().associate { (i, c) -> c to i + 1 }
        val out = ByteArrayOutputStream()
        for (f in frames) {
            require(f.w == w && f.h == h) { "frame size ${f.w}x${f.h} != $w x $h" }
            out.write(0x2) // row-major + alpha
            for (px in f.argb) out.write(if (px ushr 24 == 0) 0 else index.getValue(px and 0xFFFFFF))
            for (px in f.argb) out.write(px ushr 24)
        }
        for (c in colours) { val v = if (c == 0) 1 else c; out.write(v shr 16 and 0xFF); out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        fun p2(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        p2(w); p2(h); out.write(colours.size)
        repeat(frames.size) { p2(0) }; repeat(frames.size) { p2(0) } // offsets x, y
        repeat(frames.size) { p2(w) }; repeat(frames.size) { p2(h) }
        p2(frames.size)
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = args.firstOrNull() == "apply"
        val targets = OsrsItemImportTool.TARGETS
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = OsrsFxImportTool.existingFx(assetMap)
        val ids = File(gg.rsmod.game.RspsPaths.data("cfg/toa/ids.tsv")).readLines().map { it.split('\t') }
        fun localNpc(osrs: Int): Int = ids.firstOrNull { it.size >= 3 && it[0] == "npc" && it[1] == "$osrs" }?.get(2)?.toInt()
            ?: error("npc $osrs not in ids.tsv")
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val rows = mutableListOf<String>()
        val library = CacheLibrary(targets[0])
        try {
            val frames = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { r ->
                listOf(OSRS_BACK, OSRS_FRONT).map { s ->
                    FeroxMapSpriteProbeTool.decodeSprites(r.file(8, s, 0) ?: error("OSRS sprite $s missing")).single()
                }
            }
            val bytes = encodeFrames(frames)
            check(FeroxMapSpriteProbeTool.decodeSprites(bytes).map { it.argb.toList() } == frames.map { it.argb.toList() }) { "sprite round trip differs" }
            val sprite = existing["sprite:$OSRS_FRONT"] ?: ((library.index(INDEX_SPRITES).archiveIds().maxOrNull() ?: 0) + 1).also { records += "sprite|$OSRS_FRONT|$it" }
            mutations += CacheMutation(INDEX_SPRITES, sprite, 0, bytes, "ToA Wardens P1 charge bar (OSRS healthbar 36: sprites $OSRS_BACK back + $OSRS_FRONT front)")
            println("PLAN sprite $sprite = [$OSRS_BACK ${frames[0].w}x${frames[0].h}, $OSRS_FRONT] ${bytes.size} bytes")
            val group = library.index(OsrsNpcImportTool.INDEX_NPC).archiveIds().maxOrNull() ?: error("no npc index")
            var next = (group shl 7) + (library.index(OsrsNpcImportTool.INDEX_NPC).archive(group)?.fileIds()?.maxOrNull() ?: -1) + 1
            for ((statue, base) in STATUES) {
                val src = localNpc(base)
                val def = library.data(OsrsNpcImportTool.INDEX_NPC, src ushr 7, src and 0x7F) ?: error("local npc $src missing")
                val clone = existing["npc:$statue"] ?: next++.also { records += "npc|$statue|$it" }
                mutations += CacheMutation(OsrsNpcImportTool.INDEX_NPC, clone ushr 7, clone and 0x7F, withHealthBar(def, sprite),
                    "osrs npc $statue (copy of local $src / osrs $base) + healthbar sprite $sprite")
                println("PLAN npc $statue <- local $src (osrs $base) + opcode 122=$sprite -> $clone")
                rows += "npc\t$statue\t$clone\tcopy of $base + charge bar sprite $sprite"
            }
        } finally {
            library.close()
        }
        val tx = CacheTransaction(targets = targets, mutations = mutations)
        val plan = tx.preflight()
        val errors = tx.blockingErrors(plan)
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        rows.forEach { println("ROW $it") }
        if (!apply) { println("DRY_RUN records=${records.size}"); return }
        val result = tx.apply(plan)
        val problems = tx.verify()
        problems.forEach { println("  VERIFY_FAILURE: $it") }
        check(problems.isEmpty()) { "verify failed - journal ${result.journalDir}" }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_TOA_CHARGE_BAR_TOOL\n    transaction: ${tx.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} entries")
        }
    }
}
