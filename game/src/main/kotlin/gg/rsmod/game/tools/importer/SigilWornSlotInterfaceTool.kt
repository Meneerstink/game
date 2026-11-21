package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool.Component

/**
 * PROJECT48 SIGIL-WORN-ICOON (owner 21-11, approved design screens/SIGILS/mock/sigil_venster_v3.png panel 2): the sigil slot of the
 * Worn Items tab, as in OSRS Deadman - a slot box beside the head slot that shows a sigil; a click opens the "Attuned Sigils" window
 * (no right-click menu: there is no OSRS source for one).
 *
 * The rev-667 Worn Items tab is interface 387 (51 components; every slot is a 36x36 layer with the slot background sprite 170 and a
 * 0x0 graphic the tab's script fills). This tool APPENDS three components and touches none of the existing ones:
 *  - [LAYER]  the slot layer, 36x36, centred like the other slots (x from the centre, y from the top);
 *  - [BACKGROUND] sprite 170 (the same slot background);
 *  - [ICON] a 32x32 graphic the server fills with a sigil item (IF_SETOBJECT) - op "View", its event baked (like 387:39).
 * Place = OSRS: left of the head slot, in the weapon column (centre offset -56, y 4; measured on the owner's OSRS picture). The
 * Dizana's quiver slot of this client (387:48 at -41,4, shown only while a quiver is worn) would overlap it: the server then moves
 * the sigil slot to the tab's left edge (SigilWornSlot.place). Another place: `--x=<n> --y=<n>` (re-running moves it).
 *
 * Server: plugins `mechanics/deadman/sigils/SigilWornSlot.kt`.
 *
 * Usage: `SigilWornSlotInterfaceTool [--x=-56] [--y=4] [--apply]` (servers stopped for --apply).
 */
object SigilWornSlotInterfaceTool {
    const val INTERFACE_ID = 387
    const val LAYER = 51
    const val BACKGROUND = 52
    const val ICON = 53
    const val COUNT = 54
    private const val EXISTING = 51
    private const val SLOT_SPRITE = 170
    private const val TYPE_LAYER = 0
    private const val TYPE_GRAPHIC = 5
    const val DEFAULT_X = -56
    const val DEFAULT_Y = 4

    fun components(
        x: Int,
        y: Int,
    ): List<Component> =
        listOf(
            Component(LAYER, TYPE_LAYER, x, y, 36, 36, -1, reposX = 1),
            Component(BACKGROUND, TYPE_GRAPHIC, 0, 0, 36, 36, LAYER, graphic = SLOT_SPRITE),
            Component(ICON, TYPE_GRAPHIC, 2, 2, 32, 32, LAYER, graphic = -1, ops = listOf("View"), opBase = "Attuned Sigils", events = 0x2),
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
