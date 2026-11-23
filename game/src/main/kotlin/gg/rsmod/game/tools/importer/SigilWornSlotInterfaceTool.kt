package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component

/**
 * OSRS cache 2686/2762 interface 387:29 and scripts 5235/5237 (not a screenshot estimate).
 * Script 5235 creates a 36x36 background (sprite 170), then a 36x32 item graphic at (2,2),
 * outline 1, shadow 0x333333. The parent has one operation, "Sigils", with no target name.
 * OSRS layer x=18 relative to head x=77: centre offset -59 in the responsive 667 tab.
 * Sprite 7929 is the byte-identical OSRS 170 already imported by LootKeyInterfaceImportTool.
 * Only the three appended components are changed. Default invocation is a read-only preflight.
 * Usage: SigilWornSlotInterfaceTool [--x=-59] [--y=4] [--apply]
 */
object SigilWornSlotInterfaceTool {
    const val INTERFACE_ID = 387
    const val LAYER = 51
    const val BACKGROUND = 52
    const val ICON = 53
    const val COUNT = 54
    private const val EXISTING = 51
    private val SLOT_SPRITE = LootKeyInterfaceImportTool.sprite(170)
    private const val TYPE_LAYER = 0
    private const val TYPE_GRAPHIC = 5
    const val DEFAULT_X = -59
    const val DEFAULT_Y = 4

    fun components(
        x: Int,
        y: Int,
    ): List<Component> =
        listOf(
            Component(LAYER, TYPE_LAYER, x, y, 36, 36, -1, reposX = 1, ops = listOf("Sigils"), events = 0x2),
            Component(BACKGROUND, TYPE_GRAPHIC, 0, 0, 36, 36, LAYER, graphic = SLOT_SPRITE),
            Component(ICON, TYPE_GRAPHIC, 2, 2, 36, 32, LAYER, graphic = -1, outline = 1, graphicShadow = 0x333333),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val x = args.firstOrNull { it.startsWith("--x=") }?.substringAfter('=')?.toInt() ?: DEFAULT_X
        val y = args.firstOrNull { it.startsWith("--y=") }?.substringAfter('=')?.toInt() ?: DEFAULT_Y
        val index = LootKeyInterfaceImportTool.INDEX_INTERFACES
        val current = HashMap<Int, String>()
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        try {
            val files = library.index(index).archive(INTERFACE_ID)?.fileIds()?.toList() ?: error("interface $INTERFACE_ID missing")
            println("INTERFACE $INTERFACE_ID has ${files.size} components (last ${files.maxOrNull()})")
            require(files.size == EXISTING || files.size == COUNT) { "interface $INTERFACE_ID has ${files.size} components, expected $EXISTING (untouched) or $COUNT (slot already there)" }
            files.filter { it >= EXISTING }.forEach { f -> current[f] = CacheItemProbeTool.sha1(library.data(index, INTERFACE_ID, f)!!) }
        } finally {
            library.close()
        }
        val mutations =
            components(x, y).map { c ->
                CacheMutation(index, INTERFACE_ID, c.id, LootKeyInterfaceImportTool.encode(c), "interface $INTERFACE_ID:${c.id} sigil slot at $x,$y", expectedCurrentSha1 = current[c.id])
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write $INTERFACE_ID:$LAYER..$ICON (sigil slot at $x,$y)")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) println("VERIFY_OK transaction=${transaction.id}") else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
    }
}
