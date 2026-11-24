package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * WORN-FOLLOWER-KNOP (owner 24-11 'Ja, als OSRS'): the Worn Equipment tab gets OSRS's 4th bottom button, "Call follower".
 *
 * Source: OSRS cache 2686 interface 387 (python tools/cache667/osrs_if3.py): four 40x40 button layers at x 7 / 52 / 97 / 142,
 * y 208 in the 190 wide tab (centre offsets -68 / -23 / 22 / 67), the 4th with the op "Call follower" and icon sprite 1343
 * (32x32 whistle). Our tab centres its buttons (repos mode 1), so the three existing buttons 39 / 42 / 45 move to the OSRS
 * offsets and the new button is a copy of the Items Kept on Death button (45 + frame layer 46 + icon 47):
 * - 54 = 45 with x 67, the op "Call follower" and its three hooks (scripts 1150 / 1152 that draw the button frame) pointed at 387:55;
 * - 55 = 46 (frame layer) under 54;
 * - 56 = 47 (icon) under 54 with the OSRS whistle, imported byte-for-byte from OSRS sprite group 1343 into the next free 667
 *   sprite group.
 * Default invocation is a read-only preflight; --apply writes both caches through [CacheTransaction].
 */
object WornFollowerButtonTool {
    const val INTERFACE_ID = 387
    const val BUTTON = 54
    const val FRAME = 55
    const val ICON = 56
    private const val INDEX_SPRITES = 8
    private const val OSRS_WHISTLE = 1343
    private val MOVES = mapOf(39 to -68, 42 to -23, 45 to 22)
    private const val NEW_X = 67

    private fun s16(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = (value shr 8).toByte()
        bytes[at + 1] = value.toByte()
    }

    private fun u16(bytes: ByteArray, at: Int): Int = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun replaceAll(bytes: ByteArray, from: ByteArray, to: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        var hits = 0
        while (i < bytes.size) {
            if (i + from.size <= bytes.size && (from.indices).all { bytes[i + it] == from[it] }) {
                out.write(to); i += from.size; hits++
            } else {
                out.write(bytes[i].toInt()); i++
            }
        }
        println("  replaced ${from.toString(Charsets.ISO_8859_1).filter { it.code in 32..126 }.ifEmpty { from.joinToString("") { "%02x".format(it) } }} x$hits")
        return out.toByteArray()
    }

    private fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val index = LootKeyInterfaceImportTool.INDEX_INTERFACES
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val data = HashMap<Int, ByteArray>()
        val sha = HashMap<Int, String>()
        val spriteId: Int
        try {
            val files = library.index(index).archive(INTERFACE_ID)?.fileIds()?.toList() ?: error("interface $INTERFACE_ID missing")
            println("INTERFACE $INTERFACE_ID has ${files.size} components (last ${files.maxOrNull()})")
            require(files.size == BUTTON) { "interface $INTERFACE_ID has ${files.size} components, expected $BUTTON (no follower button yet)" }
            (MOVES.keys + listOf(46, 47)).forEach { f ->
                val b = library.data(index, INTERFACE_ID, f) ?: error("387:$f missing")
                data[f] = b
                sha[f] = CacheItemProbeTool.sha1(b)
            }
            spriteId = (library.index(INDEX_SPRITES).archiveIds().maxOrNull() ?: error("no sprites")) + 1
        } finally {
            library.close()
        }
        val source = ModernCacheReader(java.io.File(OsrsItemImportTool.SOURCE_CACHE))
        val whistle = source.file(INDEX_SPRITES, OSRS_WHISTLE, 0) ?: error("OSRS sprite $OSRS_WHISTLE missing from ${OsrsItemImportTool.SOURCE_CACHE}")
        val mutations = mutableListOf<CacheMutation>()
        mutations += CacheMutation(INDEX_SPRITES, spriteId, 0, whistle, "sprite osrs:$OSRS_WHISTLE (Call follower whistle) -> 667:$spriteId")
        MOVES.forEach { (f, x) ->
            val b = data[f]!!.copyOf()
            check(b[1].toInt() == 0) { "387:$f is not a layer" }
            println("387:$f x ${u16(b, 4).toShort()} -> $x")
            s16(b, 4, x)
            mutations += CacheMutation(index, INTERFACE_ID, f, b, "interface 387:$f button x -> $x (OSRS layout)", expectedCurrentSha1 = sha[f])
        }
        // 54: copy of 45 (the unmoved source bytes), at x 67, op "Call follower", hooks -> 387:55.
        var button = data[45]!!.copyOf()
        s16(button, 4, NEW_X)
        // The op is a jstr (text + 0): only the text changes, the terminating 0 stays.
        button = replaceAll(button, "Show Items Kept on Death".toByteArray(Charsets.ISO_8859_1), "Call follower".toByteArray(Charsets.ISO_8859_1))
        button = replaceAll(button, int((INTERFACE_ID shl 16) or 46), int((INTERFACE_ID shl 16) or FRAME))
        mutations += CacheMutation(index, INTERFACE_ID, BUTTON, button, "interface 387:$BUTTON Call follower button (copy of 45)")
        val frame = data[46]!!.copyOf()
        check(u16(frame, 16) == 45) { "387:46 parent is not 45" }
        s16(frame, 16, BUTTON)
        mutations += CacheMutation(index, INTERFACE_ID, FRAME, frame, "interface 387:$FRAME follower button frame layer")
        val icon = data[47]!!.copyOf()
        check(u16(icon, 16) == 45) { "387:47 parent is not 45" }
        check(java.nio.ByteBuffer.wrap(icon, 19, 4).int == 912) { "387:47 graphic is not 912" }
        s16(icon, 16, BUTTON)
        java.nio.ByteBuffer.wrap(icon).putInt(19, spriteId)
        mutations += CacheMutation(index, INTERFACE_ID, ICON, icon, "interface 387:$ICON follower whistle icon (sprite $spriteId)")

        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write sprite $spriteId and 387:39/42/45 + 387:$BUTTON..$ICON")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id} sprite=$spriteId") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
