package gg.rsmod.game.tools.importer

import gg.rsmod.game.RspsPaths

/**
 * N530 (owner 2026-11-02: Ferox Enclave teleport via the Ring of dueling, as OSRS): the worn menu of the eight rings of duelling
 * (2552..2566 step 2). This cache had the 2011 worn options Duel Arena / Castle Wars / Mobilising Armies / Fist of Guthix; OSRS Wiki
 * "Ring of dueling" infobox: "wornoptions = Emir's Arena, Castle Wars, Ferox Enclave, Fortis Colosseum" (the Clan Wars option became
 * Ferox Enclave). Emir's Arena is our Duel Arena; Fortis Colosseum does not exist here. Worn options are rev-667 string params 528..
 * (server `ItemDef.equipmentMenu`). Handlers: `items/jewellery/ring_of_dueling.plugin.kts` ([RingOfDueling] in plugins).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.RingOfDuelingMenuTool plan|apply` (servers stopped for apply).
 */
object RingOfDuelingMenuTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19
    val RINGS = (2552..2566 step 2).toList()
    val WORN = listOf("Duel Arena", "Castle Wars", "Ferox Enclave").mapIndexed { i, option -> 528 + i to option }.toMap()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = com.displee.cache.CacheLibrary(GAME_CACHE)
        val mutations = mutableListOf<CacheMutation>()
        try {
            for (id in RINGS) {
                val bytes = library.data(INDEX_ITEMS, id ushr 8, id and 0xFF) ?: error("item $id missing")
                val edited = ItemDefCodec.cloneWithOverrides(bytes, emptyMap(), removedParamIds = (528..535).toSet(), stringParams = WORN)
                println("$id BEFORE ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
                println("$id AFTER  ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
                if (!edited.contentEquals(bytes)) {
                    mutations += CacheMutation(INDEX_ITEMS, id ushr 8, id and 0xFF, edited, "ring of duelling $id: OSRS worn Duel Arena / Castle Wars / Ferox Enclave", CacheItemProbeTool.sha1(bytes))
                }
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO the rings already have the OSRS worn menu")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
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
