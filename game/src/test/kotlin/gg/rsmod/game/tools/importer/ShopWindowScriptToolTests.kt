package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.tools.importer.ClientScriptEditor.Ins
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shop window scripts as the client gets them once [ShopWindowScriptTool] is applied (the patches are idempotent, so this
 * checks the installed scripts after the cache window): N42/N114 price label = the server's price, N62 Buy X, N221 the stock
 * number above the item, N222 the window draws [Shop.CLIENT_VISIBLE_SLOTS] rows. Both caches (game + file-server). Read-only.
 */
class ShopWindowScriptToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    private fun read(
        cache: File,
        index: Int,
        group: Int,
        file: Int = 0,
    ): ByteArray {
        val library = CacheLibrary(cache.toString())
        return try {
            requireNotNull(library.data(index, group, file)) { "$cache $index/$group/$file missing" }
        } finally {
            library.close()
        }
    }

    /** Runs [code] from [start] until [stop] (or RETURN) with the ops these scripts use; inventories are (inv, slot) -> (obj, count). */
    private class Run(
        val code: List<Ins>,
        val locals: IntArray,
        val invs: Map<Pair<Int, Int>, Pair<Int, Int>>,
    ) {
        val ints = ArrayDeque<Int>()
        val strings = ArrayDeque<String>()
        var text: String? = null
        var colour: Int? = null
        var returned: Int? = null
        var stoppedAt = -1

        fun run(
            start: Int,
            stop: Ins? = null,
        ): Run {
            var pc = start
            while (true) {
                val ins = code[pc]
                if (ins === stop) {
                    stoppedAt = pc
                    return this
                }
                var next = pc + 1
                fun jump() {
                    next = code.indexOfFirst { it === ins.target }
                }
                when (ins.opcode) {
                    0 -> ints.addLast(ins.int)
                    3 -> strings.addLast(ins.string!!)
                    6 -> jump()
                    8, 9, 10 -> {
                        val b = ints.removeLast()
                        val a = ints.removeLast()
                        if (ins.opcode == 8 && a == b || ins.opcode == 9 && a < b || ins.opcode == 10 && a > b) jump()
                    }
                    21 -> {
                        returned = ints.removeLast()
                        stoppedAt = pc
                        return this
                    }
                    33 -> ints.addLast(locals[ins.int])
                    34 -> locals[ins.int] = ints.removeLast()
                    86 -> if (ints.removeLast() == 1) jump()
                    150 -> repeat(3) { ints.removeLast() }
                    1000, 1001 -> repeat(4) { ints.removeLast() }
                    1101 -> colour = ints.removeLast()
                    1112 -> text = strings.removeLast()
                    1113, 1115 -> ints.removeLast()
                    1114 -> repeat(3) { ints.removeLast() }
                    3301, 3302 -> {
                        val slot = ints.removeLast()
                        val inv = ints.removeLast()
                        val (obj, count) = invs[inv to slot] ?: (-1 to 0)
                        ints.addLast(if (ins.opcode == 3301) obj else count)
                    }
                    4000 -> ints.addLast(ints.removeLast() + ints.removeLast())
                    4003 -> {
                        val b = ints.removeLast()
                        ints.addLast(ints.removeLast() / b)
                    }
                    4101 -> {
                        val b = strings.removeLast()
                        strings.addLast(strings.removeLast() + b)
                    }
                    4106 -> strings.addLast(ints.removeLast().toString())
                    else -> {
                        stoppedAt = pc
                        return this
                    }
                }
                pc = next
            }
        }
    }

    @Test
    fun `the editor encodes every clientscript of the cache byte for byte`() {
        val library = CacheLibrary(caches[0].toString())
        try {
            var checked = 0
            library.index(ShopWindowScriptTool.CLIENTSCRIPT_INDEX).archiveIds().forEach { id ->
                val data = library.data(ShopWindowScriptTool.CLIENTSCRIPT_INDEX, id, 0) ?: return@forEach
                if (data.size < 20) return@forEach
                assertContentEquals(data, ClientScriptEditor.decode(data).encode(), "clientscript $id")
                checked++
            }
            assertTrue(checked > 5000, "only $checked scripts checked")
        } finally {
            library.close()
        }
    }

    @Test
    fun `N42 N114 - the price under a cell is the server's price of that stock row`() {
        caches.forEach { cache ->
            val served = ClientScriptEditor.decode(ShopWindowScriptTool.patchPrice(read(cache, 12, ShopWindowScriptTool.PRICE_SCRIPT)))
            val original = ClientScriptEditor.decode(read(cache, 12, ShopWindowScriptTool.PRICE_SCRIPT))
            fun price(
                slot: Int,
                obj: Int,
                count: Int,
            ) = Run(served.code, IntArray(served.intLocals).also { it[0] = 4151; it[1] = slot }, mapOf((Shop.PRICE_CONTAINER to slot) to (obj to count))).run(0)
            assertEquals(5_000, price(0, 24000, 5_000).returned, "$cache: Breach Trader slot 0 costs 5,000")
            assertEquals(120_000, price(7, 24000, 120_000).returned, "$cache: a price the cache value never gives")
            assertEquals(-1, price(3, 24000, 0).returned, "$cache: 0 = not for sale = N/A")
            // No price inventory (a server without it): the cache's own computation runs, from its first instruction.
            val firstOriginal = served.code.size - original.code.size
            val fallback = Run(served.code, IntArray(served.intLocals), emptyMap()).run(0, stop = served.code[firstOriginal])
            assertNull(fallback.returned)
            assertEquals(firstOriginal, fallback.stoppedAt, "$cache: falls through to the original script")
            assertEquals(original.code[0].opcode, served.code[firstOriginal].opcode)
            assertContentEquals(ShopWindowScriptTool.patchPrice(read(cache, 12, ShopWindowScriptTool.PRICE_SCRIPT)), ShopWindowScriptTool.patchPrice(served.encode()), "idempotent")
        }
    }

    @Test
    fun `N62 N221 - Buy X on every cell that has Buy 500, the stock number drawn above the item, 7 children per row`() {
        caches.forEach { cache ->
            val installed = read(cache, 12, ShopWindowScriptTool.LAYOUT_SCRIPT)
            val served = ClientScriptEditor.decode(ShopWindowScriptTool.patchLayout(installed))
            val code = served.code
            val buy500 = served.find(0 to 6, 3 to "Buy 500", 1300 to null).single()
            assertEquals(listOf<Any?>(7, ShopWindowScriptTool.BUY_X, 0), listOf(code[buy500 + 3].int, code[buy500 + 4].string, code[buy500 + 5].int))
            assertEquals(1300, code[buy500 + 5].opcode)
            // The skillcape branch (local 15) jumps over Buy 5 .. Buy X together.
            val capeSkip = code.indexOfFirst { it.opcode == 87 && code[code.indexOf(it) - 1].let { p -> p.opcode == 33 && p.int == 15 } }
            assertTrue(code.indexOfFirst { it === code[capeSkip + 1].target } > buy500 + 5, "$cache: a skillcape row never gets Buy X")

            assertTrue(code.none { it.opcode == 1212 }, "$cache: no number baked into the item sprite")
            val objectAt = served.find(0 to 36, 0 to 32, 0 to 0, 0 to 0, 1001 to null, 33 to 10, 0 to 6, 4000 to null, 33 to 11).single()
            assertEquals(ShopWindowScriptTool.OBJECT_DY, code[objectAt + 9].int)
            assertTrue(ShopWindowScriptTool.NUMBER_Y < 4 && ShopWindowScriptTool.OBJECT_DY > 4, "the number sits higher, the item lower than before (both at y + 4)")

            // The number child: formatted like the client's own item count.
            val leave = served.find(0 to 40, 0 to (620 shl 16 or 34), 3 to "I", 1404 to null).single()
            val block = leave + 4
            assertTrue(block in served.find(33 to 0, 0 to 4, 33 to 9, 150 to null), "$cache: a text child is created right after the info icon")
            val end = code.drop(block).first { it.opcode == 86 }.target!!
            assertEquals(6, end.opcode, "$cache: the block ends at the cell's own closing BRANCH")
            fun number(
                count: Int,
                cape: Boolean = false,
            ): Run {
                val locals = IntArray(served.intLocals)
                locals[2] = 4
                locals[6] = 3
                locals[15] = if (cape) 1 else 0
                return Run(code, locals, mapOf((4 to 3) to (1234 to count))).run(block, stop = end)
            }
            assertEquals("10000" to 0xFFFF00, number(10_000).let { it.text to it.colour }, "$cache: 10000 in yellow")
            assertEquals("99999" to 0xFFFF00, number(99_999).let { it.text to it.colour })
            assertEquals("250K" to 0xFFFFFF, number(250_000).let { it.text to it.colour })
            assertEquals("12M" to 0x00FF80, number(12_000_000).let { it.text to it.colour })
            assertNull(number(10_000, cape = true).text, "$cache: a skillcape row shows no number, as before")
            assertTrue(number(10_000).stoppedAt == code.indexOf(end), "the block ends at the cell's own end")

            // 7 children per filled cell, 7 hidden ones per empty cell.
            val original = ClientScriptEditor.decode(installed)
            val creates = { c: List<Ins> -> c.count { it.opcode == 150 } }
            if (original.code.none { it.string == ShopWindowScriptTool.BUY_X }) assertEquals(creates(original.code) + 1, creates(code))
            assertEquals(ShopWindowScriptTool.CHILDREN_PER_ROW, code[served.find(33 to 13, 0 to null, 9 to null).single() + 1].int)

            // Point shop (varp 532 = -1): the coin icon lookup is skipped.
            val icon = served.find(1 to 532, 0 to -1, 8 to null).single()
            assertEquals(1105, code[code.indexOf(code[icon + 2].target!!) - 1].opcode, "$cache: skips exactly the graphic set")
            assertContentEquals(ShopWindowScriptTool.patchLayout(installed), ShopWindowScriptTool.patchLayout(served.encode()), "idempotent")
        }
    }

    @Test
    fun `N222 - the shop stock inventory holds the window's rows`() {
        caches.forEach { cache ->
            val inv = ShopWindowScriptTool.patchStockInv(read(cache, 2, 5, 4))
            assertEquals(Shop.CLIENT_VISIBLE_SLOTS, ((inv[1].toInt() and 0xFF) shl 8) or (inv[2].toInt() and 0xFF), "$cache")
        }
    }
}
