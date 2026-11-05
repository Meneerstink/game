package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N522 2e (owner 2026-11-05 06:3x: "Slayer-unlocks kopen via het geimporteerde OSRS Slayer Rewards-venster (426)"): the OSRS
 * "Slayer Rewards" screen built as revision-667 if3 interface [INTERFACE_ID], the same route as [DeathsOfficeInterfaceImportTool]
 * (static components, OSRS sprites copied byte for byte, the server fills them with IF_SETTEXT / IF_SETHIDE / IF_SETOBJECT /
 * IF_SETGRAPHIC).
 *
 * Sources (read-only):
 *  - OSRS interface 426 (cache 2686): window 488x325, tab row 468x22 at (10, 38), content 468x257 at (10, 60), the confirm panel
 *    268x200 with Back / Confirm 85x22 at (33, 165) / (150, 165);
 *  - RuneStar cs2-scripts (C:\RSPS\reference\osrs-cs2-slayer-rewards): slayer_rewards_tabs/tab (85x22 "combatboxesmed" 0/1, one
 *    every 86 px, p11 orange shadowed text, "Reward points: N" in the rest of the row), slayer_rewards_unlock_draw (two columns
 *    of (452 - 4) / 2 px boxes: white fill transparency 245, outlines 0e0e0c / 474745, the 36x32 icon at +5,+5, the 16x16 status
 *    graphic at +41,+14, the p12 name at +57,+6 162x32, the p11 description at +5,+37 214 px wide, height lines * 10 + 2, box
 *    height = the taller of the pair + 42, 3 px between pairs), script413 (status: options_radio_buttons 4 = Unlock, 6 = unlocked),
 *    slayer_rewards_etcetera_draw (the "Extend remaining ..." options button under the Extend list), slayer_rewards_buy_init
 *    (five columns of 452 / 5 px: the item 36x32 centred, its price p12 under it);
 *  - the reward data: OSRS dbtable 117 (cache 2686, dbrows 6392-16388: id, points, icon object, name, in-game description,
 *    can-be-disabled flag, [tab, order]) decoded to C:\RSPS\reference\osrs-cs2-slayer-rewards\dbtable-117.txt.
 *
 * Only rewards that do something in this game are listed (HOOFDLIJST open question (c), advice "alleen werkende"): the Unlock
 * entries whose effect exists (Broader Fletching - FeatherAction/ArrowAction, Ring Bling - PlayerExt crafting, Krystilia's three)
 * and the Extend entries of the Krystilia task families with an extended range (Krystilia.TASKS).
 *
 * Usage: `./gradlew :game:runSlayerRewardsInterfaceImportTool --args="[--apply] [--replace-from-journal=<tx>]"` with the servers
 * stopped.
 */
object SlayerRewardsInterfaceImportTool {
    const val INTERFACE_ID = 1185

    /** First new sprite id (cache max 8807, 2026-11-05). */
    const val SPRITE_BASE = 8808

    /** OSRS sprites imported here: combatboxesmed 0/1 (tabs, buttons), options_radio_buttons 4/5/6 (unlock status). */
    val OSRS_SPRITES = intArrayOf(812, 813, 1211, 1212, 1213)

    fun sprite(osrsId: Int): Int {
        val index = OSRS_SPRITES.indexOf(osrsId)
        require(index >= 0) { "OSRS sprite $osrsId is not part of the slayer rewards import" }
        return SPRITE_BASE + index
    }

    val TAB_OFF get() = sprite(812)
    val TAB_ON get() = sprite(813)
    val STATUS_LOCKED get() = sprite(1211)
    val STATUS_DISABLED get() = sprite(1212)
    val STATUS_UNLOCKED get() = sprite(1213)

    const val WIDTH = 488
    const val HEIGHT = 325
    const val ORANGE = 0xFF981F
    const val DARK_ORANGE = 0x7F4C0F

    // ---- component ids ----
    const val TAB_FIRST = 14 // 4 tabs x (graphic, text) = 14..21
    val TAB_NAMES = listOf("Unlock", "Extend", "Buy", "Tasks")
    const val POINTS = 22
    const val PANEL = 23
    const val UNLOCK_LAYER = 24
    const val UNLOCK_SCROLL = 25
    const val UNLOCK_SCROLLBAR = 26
    const val EXTEND_LAYER = 27
    const val EXTEND_SCROLL = 28
    const val EXTEND_SCROLLBAR = 29
    const val EXTEND_BAR = 30 // options button pieces 31..39, text 40
    const val EXTEND_BAR_TEXT = 40
    const val BUY_LAYER = 41
    const val BUY_SCROLL = 42
    const val BUY_SCROLLBAR = 43
    const val BUY_FIRST = 44 // per item: graphic, price text
    const val BUY_SLOTS = 6
    const val TASKS_LAYER = 56
    const val TASKS_TEXT = 57
    const val CONFIRM = 58
    const val CONFIRM_TEXT = 60
    const val CONFIRM_BACK = 61 // layer: sprite 62, text 63
    const val CONFIRM_OK = 64 // layer: sprite 65, text 66
    const val CONFIRM_OPS_FIRST = 67 // overlays 67..71: Confirm, Unlock, Disable, Enable, Extend
    val CONFIRM_OPS = listOf("Confirm", "Unlock", "Disable", "Enable", "Extend")
    const val BOX_FIRST = 72
    const val BOX_STRIDE = 9 // bg, outline, inner outline, icon, status, name, description, "Unlock" overlay, "Disable" overlay

    /** Unlock/Extend box parts (offset from the box's first component). */
    const val PART_ICON = 3
    const val PART_STATUS = 4
    const val PART_UNLOCK_OP = 7
    const val PART_DISABLE_OP = 8

    // ---- reward data (OSRS dbtable 117) ----
    /** [icon] is a 667 item id (the OSRS icon object, or its import - see [ICON_IMPORTS]). */
    data class Reward(val id: Int, val name: String, val description: String, val points: Int, val icon: Int, val canDisable: Boolean)

    /**
     * OSRS icon objects with no 667 item of the same look: imported by OsrsItemImportTool batch 'slayer-reward-icons' (local ids
     * from 24641 in this order). 4141/4145/4148/4149/8138 are the same pre-2011 Slayer icon objects in both caches; the Slayer ring (8)
     * icon of Ring Bling is our Ring of slaying (8) 13281.
     */
    val ICON_IMPORTS = mapOf(4455 to 24641, 3851 to 24642, 3064 to 24643, 23493 to 24636, 6784 to 24644, 1648 to 24645, 11844 to 24646, 1667 to 24647, 11634 to 24648)

    fun icon(osrs: Int): Int =
        when (osrs) {
            11866 -> 13281
            4141, 4145, 4148, 4149, 8138 -> osrs
            else -> ICON_IMPORTS[osrs] ?: error("icon $osrs not imported")
        }

    /** Unlock tab, OSRS order (dbtable column 6 [0, n]). */
    val UNLOCKS =
        listOf(
            Reward(7, "Broader Fletching", "Learn to fletch broad arrows (with level 52 Fletching), broad bolts (with level 55 Fletching) and amethyst broad bolts (with level 76 Fletching).", 300, icon(4455), false),
            Reward(6, "Ring Bling", "Learn to craft your own Slayer Rings, with level 75 Crafting.", 150, icon(11866), false),
            Reward(17, "Watch the Birdie", "Konar, Duradel, Nieve, Chaeldar and Krystilia will be able to assign Aviansies as your task.", 80, icon(3851), true),
            Reward(19, "Like a Boss", "Konar, Duradel, Nieve and Krystilia will be able to assign boss monsters as your task. They will choose which boss you must kill.", 200, icon(3064), true),
            Reward(52, "I Wildy More Slayer", "Krystilia will be able to assign Jellies, Dust Devils, Nechryaels and Abyssal Demons as your task.", 0, icon(23493), true),
        )

    /** Extend tab, OSRS order (column 6 [1, n]); [assignment] = the Krystilia task it extends (SlayerAssignment name). */
    data class Extend(val reward: Reward, val assignment: String)

    val EXTENDS =
        listOf(
            Extend(Reward(8, "Ankou Very Much", "Whenever you get a Ankou task, it will be a bigger task.", 100, icon(6784), true), "ANKOU"),
            Extend(Reward(10, "Fire & Darkness", "Whenever you get a Black Dragon task, it will be a bigger task.", 50, icon(1648), true), "BLACK_DRAGON"),
            Extend(Reward(12, "Spiritual Fervour", "Whenever you get a Spiritual Creature task, it will be a bigger task.", 100, icon(11844), true), "SPIRITUAL_CREATURE"),
            Extend(Reward(22, "Birds of a Feather", "Whenever you get an Aviansie task, it will be a bigger task.", 100, icon(3851), true), "AVIANSIE"),
            Extend(Reward(15, "Greater Challenge", "Whenever you get a Greater Demon task, it will be a bigger task.", 100, icon(8138), true), "GREATER_DEMON"),
            Extend(Reward(14, "It's Dark in Here", "Whenever you get a Black Demon task, it will be a bigger task.", 100, icon(1667), true), "BLACK_DEMON"),
            Extend(Reward(20, "Bleed Me Dry", "Whenever you get a Bloodveld task, it will be a bigger task.", 75, icon(4141), true), "BLOODVELD"),
            Extend(Reward(25, "To Dust You Shall Return", "Whenever you get a Dust Devil task, it will be a bigger task.", 100, icon(4145), true), "DUST_DEVIL"),
            Extend(Reward(28, "Nechs Please", "Whenever you get a Nechryael task, it will be a bigger task.", 100, icon(4148), true), "NECHRYAEL"),
            Extend(Reward(13, "Augment My Abbies", "Whenever you get an Abyssal Demon task, it will be a bigger task.", 100, icon(4149), true), "ABYSSAL_DEMON"),
            Extend(Reward(53, "Revenenenenenants", "Whenever you get a Revenants task, it will be a bigger task.", 100, icon(11634), false), "REVENANT"),
        )

    /** One placed box: its first component and its rectangle inside the scroll layer. */
    data class Box(val first: Int, val x: Int, val y: Int, val w: Int, val h: Int, val descHeight: Int)

    class Layout(val unlockBoxes: List<Box>, val unlockHeight: Int, val extendBoxes: List<Box>, val extendHeight: Int)

    const val SCROLL_WIDTH = 452
    const val CONTENT_HEIGHT = 257
    const val EXTEND_SCROLL_HEIGHT = CONTENT_HEIGHT - 22
    val UNLOCK_FIRST_BOX get() = BOX_FIRST
    val EXTEND_FIRST_BOX get() = BOX_FIRST + UNLOCKS.size * BOX_STRIDE
    val COMPONENT_COUNT get() = BOX_FIRST + (UNLOCKS.size + EXTENDS.size) * BOX_STRIDE

    /** slayer_rewards_unlock_draw's arithmetic for [rewards] starting at component [first]; [lines] = p11 paraheight. */
    fun place(rewards: List<Reward>, first: Int, lines: (String, Int) -> Int): Pair<List<Box>, Int> {
        val boxW = (SCROLL_WIDTH - 4) / 2
        val rightX = SCROLL_WIDTH - (boxW + 1)
        val descW = boxW - 10
        fun descText(r: Reward) = "${r.description} <col=ff0000>(${r.points} points)</col>"
        fun descH(r: Reward) = lines(descText(r), descW) * 10 + 2
        val boxes = ArrayList<Box>()
        var y = 0
        rewards.forEachIndexed { i, r ->
            val partner = if (i % 2 == 1) rewards[i - 1] else rewards.getOrNull(i + 1)
            val h = maxOf(descH(r), partner?.let { descH(it) } ?: 0)
            val boxH = h + 10 + 32
            val x = if (i % 2 == 1) rightX else 0
            boxes += Box(first + i * BOX_STRIDE, x, y, boxW, boxH, descH(r))
            if (i == rewards.size - 1) {
                y += boxH
            } else if ((i + 1) % 2 == 0) {
                y += boxH + 3
            }
        }
        return boxes to y
    }

    fun descriptionText(r: Reward) = "${r.description} <col=ff0000>(${r.points} points)</col>"

    // ---- font metrics (index 13: per font sprite id, byte 2 + char = advance) ----
    private val TAGS = Regex("<[^>]*>")

    class Metrics(private val advances: IntArray) {
        fun width(text: String) = TAGS.replace(text, "").sumOf { advances[it.code and 0xFF] }

        fun lines(text: String, width: Int): Int {
            var lines = 1
            var line = 0
            val space = width(" ")
            for (word in TAGS.replace(text, "").split(' ')) {
                val w = width(word)
                if (line > 0 && line + space + w > width) {
                    lines++
                    line = w
                } else {
                    line += (if (line > 0) space else 0) + w
                }
            }
            return lines
        }
    }

    fun metrics(library: CacheLibrary, fontId: Int): Metrics {
        val data = library.data(13, fontId, 0) ?: error("font metrics $fontId missing")
        return Metrics(IntArray(256) { data[2 + it].toInt() and 0xFF })
    }

    // ---- components ----
    fun components(fonts: LootKeyInterfaceImportTool.Fonts, p11: Metrics): Pair<List<DeathsOfficeInterfaceImportTool.Comp>, Layout> {
        val b = DeathsOfficeInterfaceImportTool.Builder(fonts)
        b.frame(WIDTH, HEIGHT, "Slayer Rewards")

        // Tabs (slayer_rewards_tab): 85x22 every 86 px from the left of the 468 px row at (10, 38).
        TAB_NAMES.forEachIndexed { i, name ->
            val x = 10 + 86 * i
            b.graphic(TAB_FIRST + 2 * i, x, 38, 85, 22, DeathsOfficeInterfaceImportTool.WINDOW, if (i == 0) TAB_ON else TAB_OFF, ops = listOf(name))
            b.text(TAB_FIRST + 2 * i + 1, x, 38, 85, 22, DeathsOfficeInterfaceImportTool.WINDOW, fonts.p11, name)
        }
        // "Reward points: N" in the rest of the row (width 468 - 4 * 86 = 124, right-aligned box, centred text).
        b.text(POINTS, 10 + 4 * 86, 38, 468 - 4 * 86, 22, DeathsOfficeInterfaceImportTool.WINDOW, fonts.p11, "Reward points: 0")

        b.layer(PANEL, 10, 60, 468, CONTENT_HEIGHT, DeathsOfficeInterfaceImportTool.WINDOW)

        val (unlockBoxes, unlockHeight) = place(UNLOCKS, BOX_FIRST, p11::lines)
        val (extendBoxes, extendHeight) = place(EXTENDS.map { it.reward }, BOX_FIRST + UNLOCKS.size * BOX_STRIDE, p11::lines)

        // Unlock: scroll layer + scrollbar.
        b.layer(UNLOCK_LAYER, 0, 0, 468, CONTENT_HEIGHT, PANEL)
        b.layer(UNLOCK_SCROLL, 0, 0, SCROLL_WIDTH, CONTENT_HEIGHT, UNLOCK_LAYER)
        b.scrollbarFor(UNLOCK_SCROLLBAR, SCROLL_WIDTH, 0, CONTENT_HEIGHT, UNLOCK_LAYER, INTERFACE_ID, UNLOCK_SCROLL)

        // Extend: scroll layer over the 20 px "Extend remaining ..." bar.
        b.layer(EXTEND_LAYER, 0, 0, 468, CONTENT_HEIGHT, PANEL, hidden = true)
        b.layer(EXTEND_SCROLL, 0, 0, SCROLL_WIDTH, EXTEND_SCROLL_HEIGHT, EXTEND_LAYER)
        b.scrollbarFor(EXTEND_SCROLLBAR, SCROLL_WIDTH, 0, EXTEND_SCROLL_HEIGHT, EXTEND_LAYER, INTERFACE_ID, EXTEND_SCROLL)
        // slayer_rewards_etcetera_draw sizes the button to its text + 20; the widest text this list can show is baked.
        val barW = p11.width("Extend remaining ${EXTENDS.size} tasks: 9,999 points") + 20
        b.layer(EXTEND_BAR, (468 - barW) / 2, CONTENT_HEIGHT - 20, barW, 20, EXTEND_LAYER, ops = listOf("Extend"))
        b.optionsButton(EXTEND_BAR + 1, barW, 20, EXTEND_BAR)
        b.text(EXTEND_BAR_TEXT, 0, 0, barW, 20, EXTEND_BAR, fonts.p11, "Nothing more to extend", colour = DARK_ORANGE)

        // Buy (slayer_rewards_buy_init): five columns of 452 / 5 = 90 px, rows of 32 + 35 px; item 36x32 centred, price under it.
        b.layer(BUY_LAYER, 0, 0, 468, CONTENT_HEIGHT, PANEL, hidden = true)
        b.layer(BUY_SCROLL, 0, 0, SCROLL_WIDTH, CONTENT_HEIGHT, BUY_LAYER)
        b.scrollbarFor(BUY_SCROLLBAR, SCROLL_WIDTH, 0, CONTENT_HEIGHT, BUY_LAYER, INTERFACE_ID, BUY_SCROLL)
        val column = SCROLL_WIDTH / 5
        for (slot in 0 until BUY_SLOTS) {
            val x = slot % 5 * column
            val y = slot / 5 * (32 + 35)
            b.add(DeathsOfficeInterfaceImportTool.Comp(BUY_FIRST + slot * 2, 5, x + (column - 36) / 2, y, 36, 32, BUY_SCROLL, hidden = true,
                outline = 1, shadow = 0x333333, ops = listOf("Info", "Buy 1", "Buy 5", "Buy 10", "Buy 50", "", "", "", "", "Examine")))
            b.text(BUY_FIRST + slot * 2 + 1, x, y + 32, column, 15, BUY_SCROLL, fonts.p12, "", hidden = true)
        }

        // Tasks: the current-task text for now (the OSRS Tasks tab is a later step).
        b.layer(TASKS_LAYER, 0, 0, 468, CONTENT_HEIGHT, PANEL, hidden = true)
        b.text(TASKS_TEXT, 10, 10, 448, CONTENT_HEIGHT - 20, TASKS_LAYER, fonts.p12, "")

        // Confirm panel (426:3, centred 268x200, 15 px below the middle).
        b.layer(CONFIRM, (WIDTH - 268) / 2, (HEIGHT - 200) / 2 + 15, 268, 200, DeathsOfficeInterfaceImportTool.WINDOW, hidden = true)
        b.graphic(59, 1, 1, 266, 198, CONFIRM, DeathsOfficeInterfaceImportTool.sprite(297), tiling = true)
        b.text(CONFIRM_TEXT, 10, 10, 248, 155, CONFIRM, fonts.p12, "")
        b.layer(CONFIRM_BACK, 33, 165, 85, 22, CONFIRM, ops = listOf("Back"))
        b.graphic(62, 0, 0, 85, 22, CONFIRM_BACK, TAB_OFF)
        b.text(63, 0, 0, 85, 22, CONFIRM_BACK, fonts.p11, "Back")
        b.layer(CONFIRM_OK, 150, 165, 85, 22, CONFIRM)
        b.graphic(65, 0, 0, 85, 22, CONFIRM_OK, TAB_OFF)
        b.text(66, 0, 0, 85, 22, CONFIRM_OK, fonts.p11, "Confirm")
        CONFIRM_OPS.forEachIndexed { i, op ->
            b.add(DeathsOfficeInterfaceImportTool.Comp(CONFIRM_OPS_FIRST + i, 3, 0, 0, 85, 22, CONFIRM_OK, hidden = i != 0, filled = true,
                transparency = 255, ops = listOf(op)))
        }

        fun boxes(rewards: List<Reward>, placed: List<Box>, scroll: Int) {
            rewards.zip(placed).forEach { (r, box) ->
                val f = box.first
                val opBase = "<col=ff9040>${r.name}"
                b.add(DeathsOfficeInterfaceImportTool.Comp(f, 3, box.x, box.y, box.w, box.h, scroll, colour = 0xFFFFFF, filled = true, transparency = 245, opBase = opBase))
                b.rect(f + 1, box.x, box.y, box.w, box.h, scroll, 0x0E0E0C)
                b.rect(f + 2, box.x + 1, box.y + 1, box.w - 2, box.h - 2, scroll, 0x474745)
                b.add(DeathsOfficeInterfaceImportTool.Comp(f + PART_ICON, 5, box.x + 5, box.y + 5, 36, 32, scroll, outline = 1, shadow = 0x333333))
                b.graphic(f + PART_STATUS, box.x + 5 + 36, box.y + 14, 16, 16, scroll, STATUS_LOCKED, tiling = true)
                b.text(f + 5, box.x + 21 + 36, box.y + 6, box.w - (26 + 36), 32, scroll, fonts.p12, r.name)
                b.text(f + 6, box.x + 5, box.y + 5 + 32, box.w - 10, box.descHeight, scroll, fonts.p11, descriptionText(r), alignX = 0)
                b.add(DeathsOfficeInterfaceImportTool.Comp(f + PART_UNLOCK_OP, 3, box.x, box.y, box.w, box.h, scroll, filled = true, transparency = 255,
                    ops = listOf("Unlock"), opBase = opBase))
                b.add(DeathsOfficeInterfaceImportTool.Comp(f + PART_DISABLE_OP, 3, box.x, box.y, box.w, box.h, scroll, hidden = true, filled = true,
                    transparency = 255, ops = listOf("Disable"), opBase = opBase))
            }
        }
        boxes(UNLOCKS, unlockBoxes, UNLOCK_SCROLL)
        boxes(EXTENDS.map { it.reward }, extendBoxes, EXTEND_SCROLL)
        return b.done(COMPONENT_COUNT) to Layout(unlockBoxes, unlockHeight, extendBoxes, extendHeight)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val previous = args.firstOrNull { it.startsWith("--replace-from-journal=") }?.substringAfter('=')?.let(LootKeyInterfaceImportTool::journalIntendedSha1s) ?: emptyMap()
        val mutations = mutableListOf<CacheMutation>()
        val fonts: LootKeyInterfaceImportTool.Fonts
        val p11: Metrics
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            fonts = LootKeyInterfaceImportTool.fonts(library)
            p11 = metrics(library, fonts.p11)
            require(library.index(DeathsOfficeInterfaceImportTool.INDEX_INTERFACES).archive(INTERFACE_ID) == null || previous.isNotEmpty()) { "interface $INTERFACE_ID already exists" }
        } finally {
            library.close()
        }
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { source ->
            OSRS_SPRITES.forEachIndexed { index, osrsId ->
                val bytes = source.file(DeathsOfficeInterfaceImportTool.INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing")
                mutations += CacheMutation(DeathsOfficeInterfaceImportTool.INDEX_SPRITES, SPRITE_BASE + index, 0, bytes, "sprite osrs:$osrsId -> 667:${SPRITE_BASE + index}",
                    expectedCurrentSha1 = previous["idx${DeathsOfficeInterfaceImportTool.INDEX_SPRITES}_grp${SPRITE_BASE + index}_file0"])
            }
        }
        val (comps, layout) = components(fonts, p11)
        println("LAYOUT unlock height=${layout.unlockHeight} boxes=${layout.unlockBoxes} extend height=${layout.extendHeight}")
        comps.forEach { c ->
            mutations += CacheMutation(DeathsOfficeInterfaceImportTool.INDEX_INTERFACES, INTERFACE_ID, c.id, DeathsOfficeInterfaceImportTool.encode(c),
                "interface $INTERFACE_ID:${c.id} type=${c.type}", expectedCurrentSha1 = previous["idx${DeathsOfficeInterfaceImportTool.INDEX_INTERFACES}_grp${INTERFACE_ID}_file${c.id}"])
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) return println("DRY RUN: pass --apply to write interface $INTERFACE_ID and sprites $SPRITE_BASE..${SPRITE_BASE + OSRS_SPRITES.size - 1}")
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
