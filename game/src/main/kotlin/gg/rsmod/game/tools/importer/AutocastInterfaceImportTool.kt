package gg.rsmod.game.tools.importer

/**
 * Component layout of the OSRS-style autocast UI, shared by [AutocastInterfaceImportTool] (which writes it into both caches) and the
 * server's `Autocast` plugin code (which drives it). One table, so the cache and the server can never disagree on a component id.
 *
 * OSRS reference (OSRS Wiki "Autocast"): autocast is chosen on the Combat Options interface - a "Spell" box (offensive) and the same
 * box with a shield (defensive) open a spell-selection panel in the combat tab that lists only what the wielded weapon may autocast.
 *
 * - Combat tab 884 gets one extra box in the empty fourth style slot (layer 10, box 99,58 70x46): the 667 style script 1142 hides
 *   slot 14 for every three-style weapon (staves, bladed staves) and never touches the appended components 32..36. They are baked
 *   hidden, so every other weapon sees exactly the old tab; the server shows them only while an autocast-capable weapon is wielded.
 * - Interface [SELECT_INTERFACE] is the selection panel, opened in the combat tab slot in place of 884.
 */
object AutocastInterfaceLayout {
    const val COMBAT_TAB = 884
    /** Existing 884 file count; the tool refuses to run if the cache no longer matches it. */
    const val COMBAT_TAB_ORIGINAL_COMPONENTS = 32
    const val BOX_LAYER = 32
    const val BOX_BUTTON = 33
    const val BOX_ICON = 34
    const val BOX_SHIELD = 35
    const val BOX_LABEL = 36
    const val COMBAT_TAB_COMPONENTS = 37

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

    /** One selectable spell: its spellbook interface/component (the server's CombatSpell key) and its name. */
    class Entry(val book: Int, val bookComponent: Int, val name: String)

    /** Standard spellbook: five elemental tiers (Wind, Water, Earth, Fire per row), then the special spells. */
    val STANDARD =
        listOf(
            Entry(192, 25, "Wind Strike"), Entry(192, 28, "Water Strike"), Entry(192, 30, "Earth Strike"), Entry(192, 32, "Fire Strike"),
            Entry(192, 34, "Wind Bolt"), Entry(192, 39, "Water Bolt"), Entry(192, 42, "Earth Bolt"), Entry(192, 45, "Fire Bolt"),
            Entry(192, 49, "Wind Blast"), Entry(192, 52, "Water Blast"), Entry(192, 58, "Earth Blast"), Entry(192, 63, "Fire Blast"),
            Entry(192, 70, "Wind Wave"), Entry(192, 73, "Water Wave"), Entry(192, 77, "Earth Wave"), Entry(192, 80, "Fire Wave"),
            Entry(192, 84, "Wind Surge"), Entry(192, 87, "Water Surge"), Entry(192, 89, "Earth Surge"), Entry(192, 91, "Fire Surge"),
            Entry(192, 98, "Wind Rush"), Entry(192, 47, "Crumble Undead"), Entry(192, 56, "Magic Dart"), Entry(192, 54, "Iban Blast"),
            Entry(192, 66, "Saradomin Strike"), Entry(192, 67, "Claws of Guthix"), Entry(192, 68, "Flames of Zamorak"), Entry(192, 99, "Storm of Armadyl"),
        )

    /** Ancient Magicks: Smoke, Shadow, Blood, Ice per row (Rush, Burst, Blitz, Barrage), then Miasmic. */
    val ANCIENT =
        listOf(
            Entry(193, 28, "Smoke Rush"), Entry(193, 32, "Shadow Rush"), Entry(193, 24, "Blood Rush"), Entry(193, 20, "Ice Rush"),
            Entry(193, 30, "Smoke Burst"), Entry(193, 34, "Shadow Burst"), Entry(193, 26, "Blood Burst"), Entry(193, 22, "Ice Burst"),
            Entry(193, 29, "Smoke Blitz"), Entry(193, 33, "Shadow Blitz"), Entry(193, 25, "Blood Blitz"), Entry(193, 21, "Ice Blitz"),
            Entry(193, 31, "Smoke Barrage"), Entry(193, 35, "Shadow Barrage"), Entry(193, 27, "Blood Barrage"), Entry(193, 23, "Ice Barrage"),
            Entry(193, 36, "Miasmic Rush"), Entry(193, 38, "Miasmic Burst"), Entry(193, 37, "Miasmic Blitz"), Entry(193, 39, "Miasmic Barrage"),
        )

    /** Every entry in component order: component [SELECT_FIRST_SPELL] + index. */
    val ALL = STANDARD + ANCIENT
    val SELECT_CANCEL = SELECT_FIRST_SPELL + ALL.size
    val SELECT_COMPONENTS = SELECT_CANCEL + 1

    fun componentOf(entry: Entry): Int = SELECT_FIRST_SPELL + ALL.indexOf(entry)

    fun entryAt(component: Int): Entry? = ALL.getOrNull(component - SELECT_FIRST_SPELL)
}

/**
 * Writes the autocast UI of [AutocastInterfaceLayout] into both production caches through [CacheTransaction] (preflight, journal,
 * verify; refuses while the servers run). Spell icons are the 667 spellbook's own baked sprites, read from the target cache.
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

    private fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_LAYER, x, y, w, h, parent, hidden = hidden)

    private fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, ops: List<String> = emptyList(), opBase: String = "", hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, ops = ops, opBase = opBase, hidden = hidden)

    private fun text(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, ops: List<String> = emptyList()) =
        LootKeyInterfaceImportTool.Component(id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = ORANGE, alignX = 1, alignY = 1, shadow = true, ops = ops)

    /** Components appended to combat tab 884 (children of its style layer 10). */
    fun combatTabComponents(fonts: LootKeyInterfaceImportTool.Fonts): List<LootKeyInterfaceImportTool.Component> {
        val l = AutocastInterfaceLayout
        return listOf(
            layer(l.BOX_LAYER, 99, 58, 70, 46, parent = 10, hidden = true),
            graphic(l.BOX_BUTTON, 0, 0, 70, 46, l.BOX_LAYER, l.SPRITE_BOX, ops = listOf("Choose spell", "Choose defensive spell")),
            graphic(l.BOX_ICON, 23, 5, ICON, ICON, l.BOX_LAYER, -1),
            graphic(l.BOX_SHIELD, 50, 1, 20, 20, l.BOX_LAYER, l.SPRITE_SHIELD, hidden = true),
            text(l.BOX_LABEL, 0, 30, 70, 13, l.BOX_LAYER, fonts.p11, "Spell"),
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
                            require(ids == (0 until l.COMBAT_TAB_ORIGINAL_COMPONENTS).toList() || ids == (0 until l.COMBAT_TAB_COMPONENTS).toList()) {
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
                    val sprites =
                        l.ALL.associateWith { entry ->
                            val data = library.data(INDEX_INTERFACES, entry.book, entry.bookComponent) ?: error("missing ${entry.book}:${entry.bookComponent}")
                            InterfaceHookProbeTool.componentSprite(data).also { require(it > 0) { "${entry.name}: no baked sprite" } }
                        }
                    LootKeyInterfaceImportTool.fonts(library) to sprites
                } finally {
                    library.close()
                }
            }
        sprites.forEach { (entry, sprite) -> println("SPRITE ${entry.book}:${entry.bookComponent} ${entry.name} -> $sprite") }
        val mutations = mutableListOf<CacheMutation>()
        combatTabComponents(fonts).forEach { c ->
            mutations += CacheMutation(INDEX_INTERFACES, l.COMBAT_TAB, c.id, LootKeyInterfaceImportTool.encode(c), "interface ${l.COMBAT_TAB}:${c.id} autocast box")
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
            println("DRY RUN: pass --apply to write 884:${l.BOX_LAYER}..${l.BOX_LABEL} and interface ${l.SELECT_INTERFACE}")
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
