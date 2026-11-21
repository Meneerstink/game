package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths

/**
 * PROJECT48 SIGIL-OPTIES (21-11): the inventory options of the 130 sigil items (25501..25630, odd = attuned, even = unattuned; import
 * batch p48-sigils). The OSRS cache carries two generations of sigil items: 38 sigils have the Deadman: Armageddon options
 * (unattuned "Attune / - / Inspect / - / Destroy", attuned "[Activate] / [Check] / Inspect / Unattune / Destroy"), the other 27 came in
 * with the later "Unlock" items (unattuned "Unlock / - / Inspect / - / Destroy", attuned ONLY "Destroy"; Sigil of Mobility's unattuned
 * item has no first option at all). A player could not unattune or inspect those 27 from the item. The owner asked for the Armageddon
 * rules on every sigil, so this tool gives the 27 the options of the passive Armageddon sigils, copied from the cache itself (Sigil of
 * Consistency 25501 / 25502) - no option text is invented here. Activatable sigils all have the full menu already and are left alone.
 *
 * Inventory options are opcodes 35..39 (option 1..5). Handlers: plugins `mechanics/deadman/sigils/sigils.plugin.kts` (bound by name).
 *
 * Usage: `SigilItemOptionsTool plan|apply` (servers stopped for apply).
 */
object SigilItemOptionsTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19
    private val OPTION_OPCODES = 35..39
    const val FIRST = 25501
    const val LAST = 25630
    const val TEMPLATE_ATTUNED = 25501
    const val TEMPLATE_UNATTUNED = 25502

    /** Option opcode -> text, as the item's bytes have them. */
    fun options(bytes: ByteArray): Map<Int, String> =
        ItemDefCodec.describeOpcodes(bytes).mapNotNull { entry ->
            val opcode = entry.substringBefore('=').toIntOrNull() ?: return@mapNotNull null
            if (opcode !in OPTION_OPCODES) return@mapNotNull null
            opcode to entry.substringAfter('=').trim('"')
        }.toMap()

    private fun show(options: Map<Int, String>): String = OPTION_OPCODES.joinToString(" / ") { options[it] ?: "-" }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = com.displee.cache.CacheLibrary(GAME_CACHE)
        val mutations = mutableListOf<CacheMutation>()
        try {
            fun bytes(id: Int): ByteArray = library.data(INDEX_ITEMS, id ushr 8, id and 0xFF) ?: error("item $id missing")
            val attuned = options(bytes(TEMPLATE_ATTUNED))
            val unattuned = options(bytes(TEMPLATE_UNATTUNED))
            println("TEMPLATE attuned   $TEMPLATE_ATTUNED: ${show(attuned)}")
            println("TEMPLATE unattuned $TEMPLATE_UNATTUNED: ${show(unattuned)}")
            require(attuned.values.any { it.equals("Unattune", true) }) { "template $TEMPLATE_ATTUNED has no Unattune" }
            require(attuned.values.none { it.equals("Activate", true) }) { "template $TEMPLATE_ATTUNED is activatable" }
            require(unattuned.values.any { it.equals("Attune", true) }) { "template $TEMPLATE_UNATTUNED has no Attune" }
            for (id in FIRST..LAST) {
                val source = bytes(id)
                val have = options(source)
                val isAttuned = id % 2 == 1
                // An item that already has its Armageddon option is complete (the activatable ones add Activate / Check): untouched.
                val complete = have.values.any { it.equals(if (isAttuned) "Unattune" else "Attune", true) }
                if (complete) continue
                require(have.values.none { it.equals("Activate", true) }) { "item $id: activatable without the full menu - look at it by hand" }
                val want = if (isAttuned) attuned else unattuned
                val edited =
                    ItemDefCodec.cloneWithOverrides(
                        source,
                        stringOverrides = want,
                        removedOpcodes = OPTION_OPCODES.filter { it !in want }.toSet(),
                    )
                println("$id ${if (isAttuned) "attuned  " else "unattuned"} ${show(have)}  ->  ${show(options(edited))}")
                if (!edited.contentEquals(source)) {
                    mutations += CacheMutation(INDEX_ITEMS, id ushr 8, id and 0xFF, edited, "sigil item $id: Armageddon options ${show(want)}", CacheItemProbeTool.sha1(source))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO every sigil item has the Armageddon options")
            return
        }
        println("ITEMS_TO_CHANGE ${mutations.size}")
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        println("PREFLIGHT transaction=${tx.id} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
