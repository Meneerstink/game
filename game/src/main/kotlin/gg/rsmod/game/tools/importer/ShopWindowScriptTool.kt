package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.tools.importer.ClientScriptEditor.Ins

/**
 * The 667 shop window (interface 620, onLoad 2716 -> 2722 -> 2723 builds one 48x52 cell per stock row) for every shop at once.
 *
 * - N42 / N114 / N229 (owner 2026-10-23: "patch het gedeelde winkel-clientscript zodat het label de echte serverprijs toont"):
 *   the price under a cell came from clientscript 2720(obj, slot): the cache value of the item scaled by a stock varc
 *   (946 + slot) and 99,000 for any skillcape - never what the server charges (Breach Trader 50 shown / 5,000 charged, Max cape
 *   99K / 120,000, the Quartermaster's GE prices). Now 2720 first reads the server's price inventory [Shop.PRICE_CONTAINER]: the
 *   count of its slot is the price of one item of that stock row (0 = not for sale, shown "N/A" like before); the old
 *   computation only runs while that inventory has no object in the slot (a server without it).
 * - N62 (owner: "Buy-X: 7e optie + aantal-invoer"): 2723 gives every cell that has Buy 5..500 a 7th op "Buy X" (op 7 = opcode
 *   18 at the server, which asks the amount).
 * - N221 (owner: "het getal 10000 overlapt het item - aantal een paar procent hoger"): the stock number was baked into the item
 *   sprite (CC_IF_SETOBJECT_ALWAYSNUM draws it at the sprite's own (0, 9)). The sprite is now drawn without a number
 *   [OBJECT_DY] px down, and a text child (p11, the same colours as the client's own count: yellow, white K, green M) draws
 *   the number at the top of the cell. That is a 7th child per cell: [CHILDREN_PER_ROW] (the server's slot = child / 7).
 * - Point shops (no currency item: varp 532 = -1) show no coin icon next to a points price.
 * - N222 (owner: "alles moet op 1 pagina"): inventory 4, the shop stock the window draws, holds [Shop.CLIENT_VISIBLE_SLOTS]
 *   rows instead of 40 (config 2/5 file 4); 2723 already scrolls any number of rows.
 *
 * Usage: `[--apply]` (apply only with 50015/50016 stopped; both caches through one [CacheTransaction]).
 */
object ShopWindowScriptTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val PRICE_SCRIPT = 2720
    const val LAYOUT_SCRIPT = 2723
    const val CONFIG_INDEX = 2
    const val INV_GROUP = 5
    const val SHOP_STOCK_INV = 4

    /** Cells per stock row: background, hover, object, coin, price, info icon - and now the stock number. */
    const val CHILDREN_PER_ROW = 7
    const val BUY_X = "Buy X"
    const val OBJECT_DY = 6
    const val NUMBER_X = 6
    const val NUMBER_Y = 0

    private const val PUSH_CONSTANT_INT = 0
    private const val PUSH_VAR = 1
    private const val STRING = 3
    private const val BRANCH = 6
    private const val BRANCH_EQUALS = 8
    private const val BRANCH_LESS_THAN = 9
    private const val BRANCH_GREATER_THAN = 10
    private const val RETURN = 21
    private const val PUSH_INT_LOCAL = 33
    private const val POP_INT_LOCAL = 34
    private const val BRANCH_IF_TRUE = 86
    private const val CC_CREATE = 150
    private const val CC_IF_SETPOSITION = 1000
    private const val CC_IF_SETSIZE = 1001
    private const val CC_IF_SETCOLOUR = 1101
    private const val CC_IF_SETGRAPHIC = 1105
    private const val CC_IF_SETTEXT = 1112
    private const val CC_IF_SETTEXTFONT = 1113
    private const val CC_IF_SETTEXTALIGN = 1114
    private const val CC_IF_SETTEXTSHADOW = 1115
    private const val CC_IF_SETOBJECT_NONUM = 1205
    private const val CC_IF_SETOBJECT_ALWAYSNUM = 1212
    private const val CC_IF_SETOP = 1300
    private const val CC_IF_SETONMOUSELEAVE = 1404
    private const val INV_GETOBJ = 3301
    private const val INV_GETNUM = 3302
    private const val ENUM = 3408
    private const val ADD = 4000
    private const val DIVIDE = 4003
    private const val APPEND = 4101
    private const val TOSTRING = 4106
    private const val SHOP_CURRENCY_VARP = 532
    private const val TYPE_TEXT = 4
    private const val FONT_P11 = 494

    private fun int(value: Int) = Ins(PUSH_CONSTANT_INT, value)

    private fun local(index: Int) = Ins(PUSH_INT_LOCAL, index)

    private fun op(opcode: Int) = Ins(opcode, 0)

    /** 2720 with the server-price lookup in front; unchanged when already there. */
    fun patchPrice(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        if (script.code[0].opcode == PUSH_CONSTANT_INT && script.code[0].int == Shop.PRICE_CONTAINER) return current
        check(script.intArgs == 2) { "clientscript $PRICE_SCRIPT takes ${script.intArgs} int args, expected (obj, slot)" }
        val original = script.code[0]
        val positive = local(2)
        script.insert(
            0,
            listOf(
                int(Shop.PRICE_CONTAINER), local(1), op(INV_GETOBJ), int(-1), Ins(BRANCH_EQUALS, target = original),
                int(Shop.PRICE_CONTAINER), local(1), op(INV_GETNUM), Ins(POP_INT_LOCAL, 2),
                local(2), int(0), Ins(BRANCH_GREATER_THAN, target = positive),
                int(-1), op(RETURN),
                positive, op(RETURN),
            ),
        )
        return script.encode()
    }

    /** 2723 with Buy X, the stock number above the item and no coin icon in a point shop; unchanged when already there. */
    fun patchLayout(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        if (script.code.any { it.opcode == STRING && it.string == BUY_X }) return current
        val code = script.code
        fun single(
            what: String,
            vararg pattern: Pair<Int, Any?>,
        ): Int = script.find(*pattern).also { check(it.size == 1) { "clientscript $LAYOUT_SCRIPT: '$what' found ${it.size} times" } }.single()
        val targets = code.mapNotNull { it.target }.toSet()

        // Buy X after Buy 500 (inside the "not a skillcape" block, so a cape keeps Buy 1 only).
        val buy500 = single("Buy 500", PUSH_CONSTANT_INT to 6, STRING to "Buy 500", CC_IF_SETOP to null)
        script.insert(buy500 + 3, listOf(int(7), Ins(STRING, string = BUY_X), op(CC_IF_SETOP)))

        // The object without its baked number, OBJECT_DY down.
        val alwaysNum = code.indices.single { code[it].opcode == CC_IF_SETOBJECT_ALWAYSNUM }
        code[alwaysNum].opcode = CC_IF_SETOBJECT_NONUM
        val objectAt =
            single(
                "object position",
                PUSH_CONSTANT_INT to 36, PUSH_CONSTANT_INT to 32, PUSH_CONSTANT_INT to 0, PUSH_CONSTANT_INT to 0, CC_IF_SETSIZE to null,
                PUSH_INT_LOCAL to 10, PUSH_CONSTANT_INT to 6, ADD to null, PUSH_INT_LOCAL to 11, PUSH_CONSTANT_INT to 4, ADD to null,
            )
        code[objectAt + 9].int = OBJECT_DY

        // No coin icon while the shop has no currency item (point shops: varp 532 = -1).
        val icon =
            single(
                "currency icon",
                PUSH_CONSTANT_INT to 111, PUSH_CONSTANT_INT to 100, PUSH_CONSTANT_INT to 200, PUSH_VAR to SHOP_CURRENCY_VARP, ENUM to null,
                CC_IF_SETGRAPHIC to null,
            )
        check(code[icon] !in targets) { "a branch lands on the currency icon lookup" }
        script.insert(icon, listOf(Ins(PUSH_VAR, SHOP_CURRENCY_VARP), int(-1), Ins(BRANCH_EQUALS, target = code[icon + 6])))

        // The stock number: a 7th child at the end of every filled cell.
        val leave = single("info icon mouse-leave", PUSH_CONSTANT_INT to 40, PUSH_CONSTANT_INT to (620 shl 16 or 34), STRING to "I", CC_IF_SETONMOUSELEAVE to null)
        val end = code[leave + 4]
        check(end.opcode == BRANCH && end !in targets) { "clientscript $LAYOUT_SCRIPT: no plain BRANCH after the filled cell: $end" }
        val amount = script.intLocals++
        val small = Ins(PUSH_CONSTANT_INT, 0xFFFF00)
        val thousands = Ins(PUSH_CONSTANT_INT, 0xFFFFFF)
        script.insert(
            leave + 4,
            listOf(
                local(0), int(TYPE_TEXT), local(9), op(CC_CREATE),
                local(9), int(1), op(ADD), Ins(POP_INT_LOCAL, 9),
                int(48 - NUMBER_X), int(12), int(0), int(0), op(CC_IF_SETSIZE),
                local(10), int(NUMBER_X), op(ADD), local(11), int(NUMBER_Y), op(ADD), int(0), int(0), op(CC_IF_SETPOSITION),
                int(FONT_P11), op(CC_IF_SETTEXTFONT),
                int(0), int(0), int(0), op(CC_IF_SETTEXTALIGN),
                int(1), op(CC_IF_SETTEXTSHADOW),
                // A skillcape row (local 15) never showed its number.
                local(15), Ins(BRANCH_IF_TRUE, target = end),
                local(2), local(6), op(INV_GETNUM), Ins(POP_INT_LOCAL, amount),
                local(amount), int(100_000), Ins(BRANCH_LESS_THAN, target = small),
                local(amount), int(10_000_000), Ins(BRANCH_LESS_THAN, target = thousands),
                int(0x00FF80), op(CC_IF_SETCOLOUR), local(amount), int(1_000_000), op(DIVIDE), op(TOSTRING), Ins(STRING, string = "M"), op(APPEND),
                op(CC_IF_SETTEXT), Ins(BRANCH, target = end),
                thousands, op(CC_IF_SETCOLOUR), local(amount), int(1_000), op(DIVIDE), op(TOSTRING), Ins(STRING, string = "K"), op(APPEND),
                op(CC_IF_SETTEXT), Ins(BRANCH, target = end),
                small, op(CC_IF_SETCOLOUR), local(amount), op(TOSTRING), op(CC_IF_SETTEXT),
            ),
        )

        // An empty cell keeps the child stride: 7 hidden children instead of 6.
        val hidden = single("empty cell loop", PUSH_INT_LOCAL to 13, PUSH_CONSTANT_INT to 6, BRANCH_LESS_THAN to null)
        code[hidden + 1].int = CHILDREN_PER_ROW
        return script.encode()
    }

    /**
     * N559 (owner 2026-11-02: "Perdu Lost Property toont ook de hoeveelheid bij items"; the OSRS "Lost Property shop" interface shows
     * the item and its price, no stock number): while varp [NO_STOCK_VARP] is 1 the stock number child stays empty, like a skillcape
     * row's. The server sets it before opening a shop (1 for Perdu, 0 for every other shop). Unchanged when already there.
     */
    const val NO_STOCK_VARP = 2407

    fun patchHideStock(current: ByteArray): ByteArray {
        val script = ClientScriptEditor.decode(current)
        if (script.code.any { it.opcode == PUSH_VAR && it.int == NO_STOCK_VARP }) return current
        val code = script.code
        // The skillcape test patchLayout put in front of the number: local 15 -> BRANCH_IF_TRUE end.
        val skillcape =
            script.find(PUSH_INT_LOCAL to 15, BRANCH_IF_TRUE to null).filter {
                code[it + 1].target != null && code.getOrNull(it + 2)?.let { n -> n.opcode == PUSH_INT_LOCAL && n.int == 2 } == true &&
                    code.getOrNull(it + 4)?.opcode == INV_GETNUM
            }
        check(skillcape.size == 1) { "clientscript $LAYOUT_SCRIPT: stock-number skillcape test found ${skillcape.size} times (run patchLayout first)" }
        val end = code[skillcape.single() + 1].target!!
        script.insert(skillcape.single() + 2, listOf(Ins(PUSH_VAR, NO_STOCK_VARP), int(1), Ins(BRANCH_EQUALS, target = end)))
        return script.encode()
    }

    /** Inventory config 4 with [Shop.CLIENT_VISIBLE_SLOTS] slots (opcode 2 = size). */
    fun patchStockInv(current: ByteArray): ByteArray {
        check(current.size == 4 && current[0].toInt() == 2 && current[3].toInt() == 0) { "inv $SHOP_STOCK_INV config is not 'size only': ${current.toList()}" }
        return byteArrayOf(2, (Shop.CLIENT_VISIBLE_SLOTS ushr 8).toByte(), Shop.CLIENT_VISIBLE_SLOTS.toByte(), 0)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            listOf(
                Triple(CLIENTSCRIPT_INDEX to PRICE_SCRIPT, ::patchPrice, "clientscript $PRICE_SCRIPT: the shop price label shows the server price (N42/N114)"),
                Triple(CLIENTSCRIPT_INDEX to LAYOUT_SCRIPT, ::patchLayout, "clientscript $LAYOUT_SCRIPT: Buy X, stock number above the item, no coin icon in point shops (N62/N221)"),
            ).forEach { (key, patch, label) ->
                val current = requireNotNull(library.data(key.first, key.second, 0)) { "clientscript ${key.second} missing" }
                val patched = patch(current)
                if (!patched.contentEquals(current)) {
                    mutations += CacheMutation(key.first, key.second, 0, patched, label, expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
                }
            }
            // N559: the hide-stock flag after the layout patch (same clientscript, applied on top of the bytes above).
            val layout = mutations.lastOrNull { it.indexId == CLIENTSCRIPT_INDEX && it.groupId == LAYOUT_SCRIPT }?.newBytes
                ?: requireNotNull(library.data(CLIENTSCRIPT_INDEX, LAYOUT_SCRIPT, 0))
            val hidden = patchHideStock(layout)
            if (!hidden.contentEquals(layout)) {
                mutations.removeAll { it.indexId == CLIENTSCRIPT_INDEX && it.groupId == LAYOUT_SCRIPT }
                val original = requireNotNull(library.data(CLIENTSCRIPT_INDEX, LAYOUT_SCRIPT, 0))
                mutations += CacheMutation(CLIENTSCRIPT_INDEX, LAYOUT_SCRIPT, 0, hidden, "clientscript $LAYOUT_SCRIPT: no stock number while varp $NO_STOCK_VARP = 1 (N559 Perdu)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(original))
            }
            if (library.data(CONFIG_INDEX, 16, NO_STOCK_VARP) == null) {
                mutations += CacheMutation(CONFIG_INDEX, 16, NO_STOCK_VARP, byteArrayOf(0), "varp $NO_STOCK_VARP (shop without stock numbers, N559)")
            }
            val inv = requireNotNull(library.data(CONFIG_INDEX, INV_GROUP, SHOP_STOCK_INV)) { "inv $SHOP_STOCK_INV config missing" }
            val sized = patchStockInv(inv)
            if (!sized.contentEquals(inv)) {
                mutations += CacheMutation(CONFIG_INDEX, INV_GROUP, SHOP_STOCK_INV, sized, "inv $SHOP_STOCK_INV: ${Shop.CLIENT_VISIBLE_SLOTS} shop rows (N222)", expectedCurrentSha1 = CacheItemProbeTool.sha1(inv))
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println(plan.joinToString("\n"))
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
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
