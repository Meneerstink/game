package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import io.netty.buffer.Unpooled

/**
 * N422: two menu slots the rev-667 client and server fix, which three imported OSRS rings missed:
 *  - Wear: the server equips on inventory op 2 only (OpHeld2Handler), so "Wear" in op 1 (opcode 35) did nothing ("Nothing
 *    interesting happens") - the Ring of shadows, the uncharged one and the Ring of 3rd Age could not be worn. Wear moves to op 2
 *    (opcode 36) and the option that was there to op 1.
 *  - Worn: the equipment slot (clientscript 1612) shows params 528-531 as ops 2-5 and param 1211 as op 6; a fifth worn option in
 *    param 532 is never shown. The Ring of shadows' "The Stranglewood" moves to 1211 ([ItemDef] reads 1211 into the same slot).
 *
 * Usage: `./gradlew :game:runWearMenuTool --args="plan|apply"`
 */
object WearMenuTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_ITEMS = 19
    private const val OP1 = 35
    private const val OP2 = 36
    val ITEMS = listOf(23847, 23848, 23570) // Ring of shadows, Ring of shadows (uncharged), Ring of 3rd Age

    private val WEAR = setOf("wear", "wield", "equip")

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            ITEMS.forEach { item ->
                val bytes = library.data(INDEX_ITEMS, item ushr 8, item and 0xFF) ?: error("item $item missing")
                val def = ItemDef(item).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
                println("ITEM $item '${def.name}' inventory=${def.inventoryMenu.toList()} worn=${def.equipmentMenu.toList()}")
                val first = def.inventoryMenu[0]
                val second = def.inventoryMenu[1]
                val strings = HashMap<Int, String>()
                val removed = HashSet<Int>()
                if (first?.lowercase() in WEAR && second?.lowercase() !in WEAR) {
                    strings[OP2] = first!!
                    if (second == null) removed += OP1 else strings[OP1] = second
                }
                val fifth = def.params[532] as? String
                val params = if (fifth != null) mapOf(ItemDef.WORN_OP6_PARAM to fifth) else emptyMap()
                if (strings.isEmpty() && params.isEmpty()) return@forEach
                val edited =
                    ItemDefCodec.cloneWithOverrides(
                        bytes,
                        strings,
                        removedOpcodes = removed,
                        removedParamIds = if (fifth != null) setOf(532) else emptySet(),
                        stringParams = params,
                    )
                val after = ItemDef(item).also { it.decode(Unpooled.wrappedBuffer(edited)) }
                println("ITEM $item AFTER inventory=${after.inventoryMenu.toList()} worn=${after.equipmentMenu.toList()}")
                check(after.inventoryMenu[1]?.lowercase() in WEAR) { "item $item: Wear is not on op 2" }
                check(after.equipmentMenu.filterNotNull() == def.equipmentMenu.filterNotNull()) { "item $item: the worn menu changed" }
                mutations += CacheMutation(INDEX_ITEMS, item ushr 8, item and 0xFF, edited, "item $item: Wear on op 2, fifth worn option in 1211", CacheItemProbeTool.sha1(bytes))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("APPLIED transaction=${tx.id} applied=${result.applied} VERIFY_OK")
    }
}
