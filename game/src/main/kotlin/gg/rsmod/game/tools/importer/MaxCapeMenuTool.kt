package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * Gives the plain max cape (667 item 20767) the OSRS menus (owner 2026-09-24, OSRS Wiki "Max cape": inventory "Wear, Teleports,
 * Spellbook, Features, Drop"; worn "Home, Crafting Guild, Guild Teleports, Skilling Areas, POH Portals, Spellbook, Features"),
 * without "Spellbook" since owner buglist E2 (2026-10-03). The 667 inventory menu has five slots with the wear option in slot 2
 * (opcode 36) and Drop in slot 5, so the inventory order is Teleports, Wear, Features, (empty), Drop. Worn options are the
 * rev-667 string params 528.. (server `ItemDef.equipmentMenu`).
 * Handlers: `max_cape_options.plugin.kts`.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.MaxCapeMenuTool plan|apply`
 */
object MaxCapeMenuTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19
    const val MAX_CAPE = 20767

    /*
     * Owner 2026-10-03 (buglist E2): "Normale max cape heeft een 'Spellbook swap' optie - hoort daar niet." The OSRS Wiki infobox
     * does list "Spellbook" (inventory and worn); the owner's decision overrides it for this server, so the plain max cape keeps
     * every other OSRS option and loses "Spellbook" in both menus (inventory slot 4 / opcode 38 is dropped, Drop stays slot 5).
     */
    val INVENTORY = mapOf(35 to "Teleports", 36 to "Wear", 37 to "Features")

    /** Inventory option opcodes the plain max cape no longer carries (owner E2: the old slot-4 option). */
    val REMOVED_INVENTORY_OPCODES = setOf(38)

    val WORN =
        listOf("Home", "Crafting Guild", "Guild Teleports", "Skilling Areas", "POH Portals", "Features")
            .mapIndexed { i, option -> 528 + i to option }.toMap()

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val bytes: ByteArray
        val edited: ByteArray
        try {
            bytes = library.data(INDEX_ITEMS, MAX_CAPE ushr 8, MAX_CAPE and 0xFF) ?: error("item $MAX_CAPE missing")
            edited =
                ItemDefCodec.cloneWithOverrides(
                    bytes,
                    INVENTORY,
                    removedOpcodes = REMOVED_INVENTORY_OPCODES,
                    removedParamIds = (528..535).toSet(),
                    stringParams = WORN,
                )
        } finally {
            library.close()
        }
        println("BEFORE ${ItemDefCodec.describeOpcodes(bytes).joinToString(" ")}")
        println("AFTER  ${ItemDefCodec.describeOpcodes(edited).joinToString(" ")}")
        if (edited.contentEquals(bytes)) {
            println("NOTHING_TO_DO the max cape already has the OSRS menus")
            return
        }
        val tx =
            CacheTransaction(
                listOf(GAME_CACHE, FILE_SERVER_CACHE),
                listOf(
                    CacheMutation(INDEX_ITEMS, MAX_CAPE ushr 8, MAX_CAPE and 0xFF, edited, "max cape: OSRS inventory and worn menus", CacheItemProbeTool.sha1(bytes)),
                ),
            )
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
