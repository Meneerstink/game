package gg.rsmod.game.tools.importer

/**
 * Component layout of the OSRS-style autocast UI, shared by [AutocastInterfaceImportTool] (which writes it into both caches) and the
 * server's `Autocast` plugin code (which drives it). One table, so the cache and the server can never disagree on a component id.
 *
 * OSRS reference (OSRS Wiki "Autocast"): autocast is chosen on the Combat Options interface - a "Spell" box (offensive) and the same
 * box with a shield (defensive) open a spell-selection panel in the combat tab that lists only what the wielded weapon may autocast.
 *
 * - OSRS Combat Options with a magic weapon (owner reference screenshots 2026-09-24): the three styles in the left column, and in the
 *   right column the defensive "Spell" box (shield + spell icon) above the normal "Spell" box; the active one is highlighted and both
 *   show the chosen spell's icon.
 * - Combat tab 884 gets the right-column layer 32 with both boxes (components 32..39, baked hidden, so every other weapon sees exactly
 *   the old tab). While an autocast-capable weapon is wielded the server shows it and moves the style slots 11-13, Auto Retaliate and
 *   the special bar with IF_SETPOSITION ([STAFF_POSITIONS]); any other weapon gets the original 667 positions back
 *   ([ORIGINAL_POSITIONS]). The 667 style scripts 1142/1143 only hide/label the slots and never set positions.
 * - Interface [SELECT_INTERFACE] is the selection panel, opened in the combat tab slot in place of 884.
 */
object AutocastInterfaceLayout {
    const val COMBAT_TAB = 884
    /** Existing 884 file count; the tool refuses to run if the cache no longer matches it. */
    const val COMBAT_TAB_ORIGINAL_COMPONENTS = 32
    /** 884 file count written by the first (single-box) version of this tool. */
    const val COMBAT_TAB_SINGLE_BOX_COMPONENTS = 37
    const val BOX_LAYER = 32
    const val DEFENSIVE_BUTTON = 33
    const val DEFENSIVE_SHIELD = 34
    const val DEFENSIVE_ICON = 35
    const val DEFENSIVE_LABEL = 36
    const val BOX_BUTTON = 37
    const val BOX_ICON = 38
    const val BOX_LABEL = 39
    const val COMBAT_TAB_COMPONENTS = 40

    /** Combat tab components the staff layout moves: component -> (x, y) inside its parent. */
    const val SPECIAL_BAR = 3
    const val AUTO_RETALIATE = 15
    val ORIGINAL_POSITIONS: Map<Int, Pair<Int, Int>> =
        mapOf(11 to (16 to 5), 12 to (100 to 5), 13 to (16 to 58), AUTO_RETALIATE to (17 to 112), SPECIAL_BAR to (19 to 205))
    val STAFF_POSITIONS: Map<Int, Pair<Int, Int>> =
        mapOf(11 to (16 to 3), 12 to (16 to 50), 13 to (16 to 97), AUTO_RETALIATE to (17 to 147), SPECIAL_BAR to (19 to 234))

    /** 667 style-box sprites (CS2 1134: 654 = selected style, 653 = not selected). */
    const val SPRITE_BOX = 653
    const val SPRITE_BOX_SELECTED = 654
    /** The 667 spellbook's own Defensive Casting icon (192:3), used as the shield marker. */
    const val SPRITE_SHIELD = 2191

    const val SELECT_INTERFACE = 1170
    const val SELECT_ROOT = 0
    const val SELECT_TITLE = 1
    const val SELECT_STANDARD_LAYER = 2
    const val SELECT_ANCIENT_LAYER = 3
    const val SELECT_FIRST_SPELL = 4

    /**
     * One selectable spell: its spellbook interface/component (the server's CombatSpell key), its name, and the spellbook's own icon
     * pair and Magic level. [sprite] (lit) / [disabledSprite] (dark) / [level] are the arguments of the 667 spellbook's onLoad script 6
     * on that component (read from the cache 2026-09-24); the component's baked sprite is the dark one for most spells, so it is never
     * used as the icon.
     */
    class Entry(val book: Int, val bookComponent: Int, val name: String, val sprite: Int, val disabledSprite: Int, val level: Int)

    /** Standard spellbook: five elemental tiers (Wind, Water, Earth, Fire per row), then the special spells. */
    val STANDARD =
        listOf(
            Entry(192, 25, "Wind Strike", 15, 65, 1), Entry(192, 28, "Water Strike", 17, 67, 5),
            Entry(192, 30, "Earth Strike", 19, 69, 9), Entry(192, 32, "Fire Strike", 21, 71, 13),
            Entry(192, 34, "Wind Bolt", 23, 73, 17), Entry(192, 39, "Water Bolt", 26, 76, 23),
            Entry(192, 42, "Earth Bolt", 29, 79, 29), Entry(192, 45, "Fire Bolt", 32, 82, 35),
            Entry(192, 49, "Wind Blast", 35, 85, 41), Entry(192, 52, "Water Blast", 38, 88, 47),
            Entry(192, 58, "Earth Blast", 40, 90, 53), Entry(192, 63, "Fire Blast", 44, 94, 59),
            Entry(192, 70, "Wind Wave", 46, 96, 62), Entry(192, 73, "Water Wave", 48, 98, 65),
            Entry(192, 77, "Earth Wave", 51, 101, 70), Entry(192, 80, "Fire Wave", 52, 102, 75),
            Entry(192, 84, "Wind Surge", 500, 815, 81), Entry(192, 87, "Water Surge", 501, 816, 85),
            Entry(192, 89, "Earth Surge", 811, 817, 90), Entry(192, 91, "Fire Surge", 814, 818, 95),
            Entry(192, 98, "Wind Rush", 3759, 3760, 1), Entry(192, 47, "Crumble Undead", 34, 84, 39),
            Entry(192, 56, "Magic Dart", 324, 374, 50), Entry(192, 54, "Iban Blast", 53, 103, 50),
            Entry(192, 66, "Saradomin Strike", 61, 111, 60), Entry(192, 67, "Claws of Guthix", 60, 110, 60),
            Entry(192, 68, "Flames of Zamorak", 59, 109, 60), Entry(192, 99, "Storm of Armadyl", 7699, 7702, 77),
        )

    /** Ancient Magicks: Smoke, Shadow, Blood, Ice per row (Rush, Burst, Blitz, Barrage), then Miasmic. */
    val ANCIENT =
        listOf(
            Entry(193, 28, "Smoke Rush", 329, 379, 50), Entry(193, 32, "Shadow Rush", 337, 387, 52),
            Entry(193, 24, "Blood Rush", 333, 383, 56), Entry(193, 20, "Ice Rush", 325, 375, 58),
            Entry(193, 30, "Smoke Burst", 330, 380, 62), Entry(193, 34, "Shadow Burst", 338, 388, 64),
            Entry(193, 26, "Blood Burst", 334, 384, 68), Entry(193, 22, "Ice Burst", 326, 376, 70),
            Entry(193, 29, "Smoke Blitz", 331, 381, 74), Entry(193, 33, "Shadow Blitz", 339, 389, 76),
            Entry(193, 25, "Blood Blitz", 335, 385, 80), Entry(193, 21, "Ice Blitz", 327, 377, 82),
            Entry(193, 31, "Smoke Barrage", 332, 382, 86), Entry(193, 35, "Shadow Barrage", 340, 390, 88),
            Entry(193, 27, "Blood Barrage", 336, 386, 92), Entry(193, 23, "Ice Barrage", 328, 378, 94),
            Entry(193, 36, "Miasmic Rush", 1568, 1574, 61), Entry(193, 38, "Miasmic Burst", 1569, 1575, 73),
            Entry(193, 37, "Miasmic Blitz", 1567, 1573, 85), Entry(193, 39, "Miasmic Barrage", 1566, 1572, 97),
        )

    fun entryOf(book: Int, bookComponent: Int): Entry? = ALL.firstOrNull { it.book == book && it.bookComponent == bookComponent }

    /** Every entry in component order: component [SELECT_FIRST_SPELL] + index. */
    val ALL = STANDARD + ANCIENT
    val SELECT_CANCEL = SELECT_FIRST_SPELL + ALL.size
    val SELECT_COMPONENTS = SELECT_CANCEL + 1

    fun componentOf(entry: Entry): Int = SELECT_FIRST_SPELL + ALL.indexOf(entry)

    fun entryAt(component: Int): Entry? = ALL.getOrNull(component - SELECT_FIRST_SPELL)
}

/**
 * Writes the autocast UI of [AutocastInterfaceLayout] into both production caches through [CacheTransaction] (preflight, journal,
 * verify; refuses while the servers run). Spell icons are the 667 spellbook's own lit sprites ([AutocastInterfaceLayout.Entry.sprite]);
 * the server swaps in the dark one per player when the Magic level is too low.
 *
 * Usage: `./gradlew :game:runAutocastInterfaceImportTool --args="[--apply]"`
 */
object AutocastInterfaceImportTool {
    private const val INDEX_INTERFACES = 3
    private const val ORANGE = 0xFF981F
    private const val TYPE_LAYER = 0
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val ICON = 24
    private const val COLUMNS = 4
    private const val PITCH_X = 40
    private const val PITCH_Y = 30

    /** 884:32..36 as written by the single-box version (tx-20260924-011513); only exactly these may be replaced by the two-box layout. */
    private val SINGLE_BOX_SHA1 =
        mapOf(
            32 to "e79dbde5b768c63718d92ff29ec0e13455b53136",
            33 to "333150ae9d8d4218074ec5fb148f2f064902d737",
            34 to "bc40e0a875c0aeff63d8437b8795d8c2c903b4b2",
            35 to "404adfdc0df6def2de3e78a1829f76ea556663e3",
            36 to "c8ebefca392942bb12f14fcf59e40ebc1b5b24ff",
        )

    private fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_LAYER, x, y, w, h, parent, hidden = hidden)

    private fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, ops: List<String> = emptyList(), opBase: String = "", hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, ops = ops, opBase = opBase, hidden = hidden)

    private fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, ops: List<String> = emptyList()) =
        LootKeyInterfaceImportTool.Component(id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = ORANGE, alignX = 1, alignY = 1, shadow = true, ops = ops)

    /** Components appended to combat tab 884 (children of its style layer 10). */
    fun combatTabComponents(fonts: LootKeyInterfaceImportTool.Fonts): List<LootKeyInterfaceImportTool.Component> {
        val l = AutocastInterfaceLayout
        // Right column beside the moved style slots (layer 10 rows 3..143): defensive box on top, normal box below, centred.
        val defY = 23
        val spellY = defY + 48
        return listOf(
            layer(l.BOX_LAYER, 100, 3, 70, 140, parent = 10, hidden = true),
            graphic(l.DEFENSIVE_BUTTON, 0, defY, 70, 46, l.BOX_LAYER, l.SPRITE_BOX, ops = listOf("Choose spell")),
            graphic(l.DEFENSIVE_SHIELD, 6, defY + 6, 20, 20, l.BOX_LAYER, l.SPRITE_SHIELD),
            graphic(l.DEFENSIVE_ICON, 34, defY + 4, ICON, ICON, l.BOX_LAYER, -1),
            text(l.DEFENSIVE_LABEL, 0, defY + 30, 70, 13, l.BOX_LAYER, fonts.p11, "Spell"),
            graphic(l.BOX_BUTTON, 0, spellY, 70, 46, l.BOX_LAYER, l.SPRITE_BOX, ops = listOf("Choose spell")),
            graphic(l.BOX_ICON, 23, spellY + 4, ICON, ICON, l.BOX_LAYER, -1),
            text(l.BOX_LABEL, 0, spellY + 30, 70, 13, l.BOX_LAYER, fonts.p11, "Spell"),
        )
    }

    /** The selection panel: title, the two spellbook grids (the server hides the other book and every spell the weapon cannot autocast). */
    fun selectComponents(fonts: LootKeyInterfaceImportTool.Fonts, sprites: Map<AutocastInterfaceLayout.Entry, Int>): List<LootKeyInterfaceImportTool.Component> {
        val l = AutocastInterfaceLayout
        val list = mutableListOf(
            layer(l.SELECT_ROOT, 0, 0, 190, 261, parent = -1),
            text(l.SELECT_TITLE, 0, 4, 190, 18, l.SELECT_ROOT, fonts.b12, "Choose a spell"),
            layer(l.SELECT_STANDARD_LAYER, 0, 26, 190, 210, l.SELECT_ROOT),
            layer(l.SELECT_ANCIENT_LAYER, 0, 26, 190, 210, l.SELECT_ROOT, hidden = true),
        )
        fun grid(entries: List<AutocastInterfaceLayout.Entry>, parent: Int) {
            entries.forEachIndexed { index, entry ->
                val x = 15 + (index % COLUMNS) * PITCH_X + (PITCH_X - ICON) / 2
                val y = (index / COLUMNS) * PITCH_Y + 3
                val sprite = sprites[entry] ?: error("no sprite for ${entry.name}")
                list += graphic(l.componentOf(entry), x, y, ICON, ICON, parent, sprite, ops = listOf("Select"), opBase = "<col=00ff00>${entry.name}")
            }
        }
        grid(l.STANDARD, l.SELECT_STANDARD_LAYER)
        grid(l.ANCIENT, l.SELECT_ANCIENT_LAYER)
        list += text(l.SELECT_CANCEL, 45, 238, 100, 18, l.SELECT_ROOT, fonts.p12, "Cancel", ops = listOf("Cancel"))
        require(list.map { it.id }.sorted() == (0 until l.SELECT_COMPONENTS).toList()) { "component ids must be 0..${l.SELECT_COMPONENTS - 1}" }
        return list.sortedBy { it.id }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val l = AutocastInterfaceLayout
        val targets = OsrsItemImportTool.TARGETS
        val (fonts, sprites) =
            com.displee.cache.CacheLibrary(targets[0]).let { library ->
                try {
                    targets.forEach { path ->
                        val lib = if (path == targets[0]) library else com.displee.cache.CacheLibrary(path)
                        try {
                            val index = lib.index(INDEX_INTERFACES)
                            val tab = index.archive(l.COMBAT_TAB) ?: error("$path: interface ${l.COMBAT_TAB} missing")
                            val ids = tab.fileIds().toList()
                            require(listOf(l.COMBAT_TAB_ORIGINAL_COMPONENTS, l.COMBAT_TAB_SINGLE_BOX_COMPONENTS, l.COMBAT_TAB_COMPONENTS).any { ids == (0 until it).toList() }) {
                                "$path: interface ${l.COMBAT_TAB} has files $ids - not the expected 667 layout, refusing"
                            }
                            val existing = index.archive(l.SELECT_INTERFACE)
                            require(existing == null || existing.fileIds().size == l.SELECT_COMPONENTS) {
                                "$path: interface ${l.SELECT_INTERFACE} already holds other content"
                            }
                        } finally {
                            if (lib !== library) lib.close()
                        }
                    }
                    // The spellbook component's baked sprite is the dark (unavailable) icon for most spells - bake the lit one.
                    val sprites = l.ALL.associateWith { it.sprite }
                    LootKeyInterfaceImportTool.fonts(library) to sprites
                } finally {
                    library.close()
                }
            }
        sprites.forEach { (entry, sprite) -> println("SPRITE ${entry.book}:${entry.bookComponent} ${entry.name} -> $sprite") }
        val mutations = mutableListOf<CacheMutation>()
        combatTabComponents(fonts).forEach { c ->
            mutations += CacheMutation(
                INDEX_INTERFACES, l.COMBAT_TAB, c.id, LootKeyInterfaceImportTool.encode(c), "interface ${l.COMBAT_TAB}:${c.id} autocast box",
                expectedCurrentSha1 = SINGLE_BOX_SHA1[c.id],
            )
        }
        selectComponents(fonts, sprites).forEach { c ->
            mutations += CacheMutation(INDEX_INTERFACES, l.SELECT_INTERFACE, c.id, LootKeyInterfaceImportTool.encode(c), "interface ${l.SELECT_INTERFACE}:${c.id} autocast select")
        }
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write 884:${l.BOX_LAYER}..${l.COMBAT_TAB_COMPONENTS - 1} and interface ${l.SELECT_INTERFACE}")
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
