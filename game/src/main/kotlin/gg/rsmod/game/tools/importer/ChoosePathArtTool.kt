package gg.rsmod.game.tools.importer

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The painted art of "Choose your path" (owner 2026-09-26: premium, Deadman-like, gold and rich colours; the second
 * design the owner approved from C:\RSPS\foto\v2_*.png). [ChoosePathArt] paints every piece at 2x with Java2D and scales it
 * down; this tool stores them as revision-667 sprites ([StoreArtTool.encode]) from [BASE], in both caches, through
 * [CacheTransaction]. [QuestGuideInterfaceImportTool] draws the window from these sprites.
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.ChoosePathArtTool [--apply] [--png=<dir>]`
 */
object ChoosePathArtTool {
    /** First free sprite group above the Death's Office imports (8200-8250); probe at run time. */
    const val BASE = 8300

    const val BACKGROUND = BASE

    /** Card faces: row * 4 + state (0 normal, 1 selected, 2 chosen, 3 locked). */
    const val CARD_FIRST = BASE + 1 // 8301..8312
    const val TICK = BASE + 13
    const val LOCK = BASE + 14
    const val CONFIRM_ACTIVE = BASE + 15
    const val CONFIRM_DISABLED = BASE + 16
    const val DIALOG = BASE + 17
    const val YES = BASE + 18
    const val NO = BASE + 19
    const val CLOSE = BASE + 20
    const val COUNT = 21

    fun card(row: Int, state: Int) = CARD_FIRST + row * 4 + state

    fun all(): Map<Int, BufferedImage> {
        val out = linkedMapOf<Int, BufferedImage>()
        out[BACKGROUND] = ChoosePathArt.background()
        for (row in 0 until 3) for (state in 0 until 4) out[card(row, state)] = ChoosePathArt.card(row, state)
        out[TICK] = ChoosePathArt.tick()
        out[LOCK] = ChoosePathArt.lock()
        out[CONFIRM_ACTIVE] = ChoosePathArt.button(66, 26, 0)
        out[CONFIRM_DISABLED] = ChoosePathArt.button(66, 26, 1)
        out[DIALOG] = ChoosePathArt.dialog()
        out[YES] = ChoosePathArt.button(96, 28, 0)
        out[NO] = ChoosePathArt.button(96, 28, 2)
        out[CLOSE] = ChoosePathArt.close()
        check(out.size == COUNT && out.keys == (BASE until BASE + COUNT).toSet()) { "sprite ids must be $BASE..${BASE + COUNT - 1}" }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val art = all()
        args.firstOrNull { it.startsWith("--png=") }?.substringAfter('=')?.let { dir ->
            File(dir).mkdirs()
            art.forEach { (id, image) -> ImageIO.write(image, "png", File(dir, "path_$id.png")) }
            println("PNG written to $dir")
        }
        val library = com.displee.cache.CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val current: Map<Int, String?>
        try {
            current = art.keys.associateWith { id -> library.data(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0)?.let(CacheItemProbeTool::sha1) }
        } finally {
            library.close()
        }
        val mutations =
            art.map { (id, image) ->
                CacheMutation(LootKeyInterfaceImportTool.INDEX_SPRITES, id, 0, StoreArtTool.encode(image), "choose-your-path sprite $id", expectedCurrentSha1 = current[id])
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()} existing=${current.values.count { it != null }}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write sprites $BASE..${BASE + COUNT - 1}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
