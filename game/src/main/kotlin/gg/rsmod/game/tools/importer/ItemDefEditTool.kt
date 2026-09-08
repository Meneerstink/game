package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * Produces the raw item-definition bytes an import wants to write, by editing the bytes that are
 * really in a cache today - never by authoring a definition from scratch (see [ItemDefCodec]'s class
 * doc for why originating one is unsafe here).
 *
 * Writes nothing to any cache: the edited definition goes to a file, which is then fed to
 * `ItemTransactionTool put <file> <id> --expect-sha1=... --apply`. Splitting "decide the bytes" from
 * "write the bytes" is deliberate - the produced file can be diffed, hashed and reviewed before any
 * cache is touched, and the transaction that writes it still refuses to replace anything but the
 * exact content the caller pinned.
 *
 * Usage:
 *
 * ```
 * edit <cachePath> <itemId> <outFile>
 *      [--name=<text>] [--short=<opcode>:<value>[,...]] [--int=<opcode>:<value>[,...]]
 *      [--remove=<opcode>[,...]] [--remove-param=<paramId>[,...]]
 * ```
 *
 * Every override value must come from a real source (the pinned upstream definition), never from
 * judgement about what looks right; this tool only applies what it is given, and prints the full
 * opcode stream before and after so the diff is on the record.
 */
object ItemDefEditTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { USAGE }
        val verb = args[0]
        check(verb == "edit") { "Unknown verb '$verb'.\n$USAGE" }

        val positional = args.drop(1).filterNot { it.startsWith("--") }
        require(positional.size >= 3) { USAGE }
        val flags =
            args.filter { it.startsWith("--") }
                .associate { flag ->
                    val eq = flag.indexOf('=')
                    if (eq == -1) flag.removePrefix("--") to "true" else flag.substring(2, eq) to flag.substring(eq + 1)
                }

        val cachePath = positional[0]
        val itemId = positional[1].toInt()
        val outFile = File(positional[2])

        val library = CacheLibrary(cachePath)
        val source =
            try {
                CacheItemProbeTool.itemData(library, itemId)
                    ?: error("Cache $cachePath has no data for item $itemId - nothing to edit.")
            } finally {
                library.close()
            }

        val stringOverrides = flags["name"]?.let { mapOf(2 to it) } ?: emptyMap()
        val shortOverrides = parsePairs(flags["short"])
        val intOverrides = parsePairs(flags["int"])
        val removedOpcodes = flags["remove"]?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().toInt() }?.toSet() ?: emptySet()
        val removedParamIds =
            flags["remove-param"]?.split(',')?.filter { it.isNotBlank() }?.map { it.trim().toInt() }?.toSet() ?: emptySet()

        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = source,
                stringOverrides = stringOverrides,
                shortOverrides = shortOverrides,
                intOverrides = intOverrides,
                removedOpcodes = removedOpcodes,
                removedParamIds = removedParamIds,
            )

        println("SOURCE=$cachePath item=$itemId bytes=${source.size} sha1=${CacheItemProbeTool.sha1(source)} name=${ItemDefCodec.readName(source)}")
        println("  BEFORE=${ItemDefCodec.describeOpcodes(source).joinToString(" ")}")
        println("EDITED bytes=${edited.size} sha1=${CacheItemProbeTool.sha1(edited)} name=${ItemDefCodec.readName(edited)}")
        println("  AFTER =${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")

        // Name every change explicitly rather than leaving it to be spotted in the two dumps above.
        val before = ItemDefCodec.describeOpcodes(source).toSet()
        val after = ItemDefCodec.describeOpcodes(edited).toSet()
        (before - after).forEach { println("  CHANGED_FROM $it") }
        (after - before).forEach { println("  CHANGED_TO   $it") }

        outFile.parentFile?.mkdirs()
        outFile.writeBytes(edited)
        println("WROTE=${outFile.absolutePath} (no cache was modified)")
    }

    private fun parsePairs(raw: String?): Map<Int, Int> =
        raw?.split(',')
            ?.filter { it.isNotBlank() }
            ?.associate { pair ->
                val parts = pair.split(':')
                require(parts.size == 2) { "Expected <opcode>:<value> pairs, got '$pair'." }
                parts[0].trim().toInt() to parts[1].trim().toInt()
            }
            ?: emptyMap()

    private const val USAGE =
        "Usage:\n" +
            "  edit <cachePath> <itemId> <outFile> [--name=<text>] [--short=<op>:<value>[,...]] " +
            "[--int=<op>:<value>[,...]] [--remove=<op>[,...]] [--remove-param=<paramId>[,...]]"
}
