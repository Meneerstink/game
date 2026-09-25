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
 * - OSRS look (owner reference screenshots compare/order/ancient 2026-09-24, OSRS cache 2686 interfaces 593 and 201): the two
 *   "Spell" boxes fill the whole height of the three style slots (1.5 slots each), the defensive box carries OSRS's big blue shield
 *   (OSRS sprite 760) left of the spell icon, and every icon is the OSRS spell sprite (lit, or OSRS's dark one = lit id + 50).
 *   The panel has no title; the grid is spread over the top 157 px, "Cancel" sits right-aligned above a black "Select a Combat
 *   Spell" box - positions and colours measured on the owner's 1:1 OSRS screenshots.
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
    /** First new 667 sprite group of this UI (the caches end at 7962; 8100.. is left free for it). */
    const val SPRITE_BASE = 8100
    /** OSRS sprite 760: the defensive-autocast shield (36x36). */
    const val SPRITE_SHIELD = SPRITE_BASE
    /** The 667 style box (653 / 654, 68x44) drawn 1.5 slots tall: border rows kept, the stone middle repeated. */
    const val SPRITE_TALL_BOX = SPRITE_BASE + 1
    const val SPRITE_TALL_BOX_SELECTED = SPRITE_BASE + 2
    /** Lit/dark OSRS spell icons: [SPRITE_ICON_BASE] + 2 * n (lit) and + 1 (dark), n = index in [OSRS_ICON_ENTRIES]. */
    const val SPRITE_ICON_BASE = SPRITE_BASE + 3
    const val OSRS_SHIELD = 760
    const val TALL_BOX_HEIGHT = 67

    const val SELECT_INTERFACE = 1170
    const val SELECT_ROOT = 0
    const val SELECT_TITLE = 1
    const val SELECT_STANDARD_LAYER = 2
    const val SELECT_ANCIENT_LAYER = 3
    const val SELECT_FIRST_SPELL = 4

    /** Info box under the grid (OSRS 201:3-5): black fill, 0x726451 outer and 0x2e2b23 inner outline, 0xafaf9f text. */
    const val INFO_X = 5
    const val INFO_Y = 175
    const val INFO_W = 180
    const val INFO_H = 81
    const val GRID_TOP = 6
    const val GRID_HEIGHT = 157

    /**
     * One selectable spell: its spellbook interface/component (the server's CombatSpell key), its name, and the spellbook's own icon
     * pair and Magic level. [sprite] (lit) / [disabledSprite] (dark) / [level] are the arguments of the 667 spellbook's onLoad script 6
     * on that component (read from the cache 2026-09-24); the component's baked sprite is the dark one for most spells, so it is never
     * used as the icon.
     */
    class Entry(
        val book: Int,
        val bookComponent: Int,
        val name: String,
        /** The 667 spellbook's lit / dark icon: only used for spells OSRS does not have ([osrsSprite] = -1). */
        private val bookSprite: Int,
        private val bookDisabledSprite: Int,
        val level: Int,
        /** OSRS lit spell sprite (2686), -1 when OSRS has no such spell; the dark one is always lit + 50 in OSRS. */
        val osrsSprite: Int = -1,
    ) {
        /** The icon shown in the autocast UI: the imported OSRS sprite when OSRS has the spell, else the 667 one. */
        val sprite: Int get() = if (osrsSprite < 0) bookSprite else SPRITE_ICON_BASE + 2 * OSRS_ICON_ENTRIES.indexOf(this)
        val disabledSprite: Int get() = if (osrsSprite < 0) bookDisabledSprite else sprite + 1
    }

    /** Standard spellbook: five elemental tiers (Wind, Water, Earth, Fire per row), then the special spells (OSRS ones first). */
    val STANDARD =
        listOf(
            Entry(192, 25, "Wind Strike", 15, 65, 1, 15), Entry(192, 28, "Water Strike", 17, 67, 5, 17),
            Entry(192, 30, "Earth Strike", 19, 69, 9, 19), Entry(192, 32, "Fire Strike", 21, 71, 13, 21),
            Entry(192, 34, "Wind Bolt", 23, 73, 17, 23), Entry(192, 39, "Water Bolt", 26, 76, 23, 26),
            Entry(192, 42, "Earth Bolt", 29, 79, 29, 29), Entry(192, 45, "Fire Bolt", 32, 82, 35, 32),
            Entry(192, 49, "Wind Blast", 35, 85, 41, 35), Entry(192, 52, "Water Blast", 38, 88, 47, 38),
            Entry(192, 58, "Earth Blast", 40, 90, 53, 40), Entry(192, 63, "Fire Blast", 44, 94, 59, 44),
            Entry(192, 70, "Wind Wave", 46, 96, 62, 46), Entry(192, 73, "Water Wave", 48, 98, 65, 48),
            Entry(192, 77, "Earth Wave", 51, 101, 70, 51), Entry(192, 80, "Fire Wave", 52, 102, 75, 52),
            Entry(192, 84, "Wind Surge", 500, 815, 81, 362), Entry(192, 87, "Water Surge", 501, 816, 85, 363),
            Entry(192, 89, "Earth Surge", 811, 817, 90, 364), Entry(192, 91, "Fire Surge", 814, 818, 95, 365),
            Entry(192, 47, "Crumble Undead", 34, 84, 39, 34), Entry(192, 56, "Magic Dart", 324, 374, 50, 324),
            Entry(192, 54, "Iban Blast", 53, 103, 50, 53), Entry(192, 66, "Saradomin Strike", 61, 111, 60, 61),
            Entry(192, 67, "Claws of Guthix", 60, 110, 60, 60), Entry(192, 68, "Flames of Zamorak", 59, 109, 60, 59),
            // Not OSRS spells (667 content): their own 667 icons, after the OSRS ones. Both were removed from the game (owner
            // 2026-09-24); their slots stay so the component ids of every later entry keep their place - see [RETIRED].
            Entry(192, 98, "Wind Rush", 3759, 3760, 1), Entry(192, 99, "Storm of Armadyl", 7699, 7702, 77),
        )

    /** Ancient Magicks: Smoke, Shadow, Blood, Ice per row (Rush, Burst, Blitz, Barrage), then Miasmic (667 only). */
    val ANCIENT =
        listOf(
            Entry(193, 28, "Smoke Rush", 329, 379, 50, 329), Entry(193, 32, "Shadow Rush", 337, 387, 52, 337),
            Entry(193, 24, "Blood Rush", 333, 383, 56, 333), Entry(193, 20, "Ice Rush", 325, 375, 58, 325),
            Entry(193, 30, "Smoke Burst", 330, 380, 62, 330), Entry(193, 34, "Shadow Burst", 338, 388, 64, 338),
            Entry(193, 26, "Blood Burst", 334, 384, 68, 334), Entry(193, 22, "Ice Burst", 326, 376, 70, 326),
            Entry(193, 29, "Smoke Blitz", 331, 381, 74, 331), Entry(193, 33, "Shadow Blitz", 339, 389, 76, 339),
            Entry(193, 25, "Blood Blitz", 335, 385, 80, 335), Entry(193, 21, "Ice Blitz", 327, 377, 82, 327),
            Entry(193, 31, "Smoke Barrage", 332, 382, 86, 332), Entry(193, 35, "Shadow Barrage", 340, 390, 88, 340),
            Entry(193, 27, "Blood Barrage", 336, 386, 92, 336), Entry(193, 23, "Ice Barrage", 328, 378, 94, 328),
            Entry(193, 36, "Miasmic Rush", 1568, 1574, 61), Entry(193, 38, "Miasmic Burst", 1569, 1575, 73),
            Entry(193, 37, "Miasmic Blitz", 1567, 1573, 85), Entry(193, 39, "Miasmic Barrage", 1566, 1572, 97),
        )

    /** Slots whose spell was removed from the game (Wind Rush, Storm of Armadyl): never shown, no spell behind them. */
    val RETIRED: Set<Pair<Int, Int>> = setOf(192 to 98, 192 to 99)

    const val ICON_SIZE = 24
    private const val COLUMNS = 4
    private const val PITCH_X = 40

    /**
     * (x, y) of the [index]-th of [count] icons: four per row, the rows spread evenly over the grid area as in OSRS (6 standard rows
     * ~26 px apart, 4 ancient rows ~40 px apart). The server packs the allowed spells with it (no gaps).
     */
    fun gridPosition(index: Int, count: Int): Pair<Int, Int> {
        val rows = maxOf(1, (count + COLUMNS - 1) / COLUMNS)
        val centreY = GRID_TOP + ((index / COLUMNS) * 2 + 1) * GRID_HEIGHT / (rows * 2)
        return (15 + (index % COLUMNS) * PITCH_X + (PITCH_X - ICON_SIZE) / 2) to (centreY - ICON_SIZE / 2)
    }

    fun entryOf(book: Int, bookComponent: Int): Entry? = ALL.firstOrNull { it.book == book && it.bookComponent == bookComponent }

    /** Every entry in component order: component [SELECT_FIRST_SPELL] + index. */
    val ALL = STANDARD + ANCIENT
    val SELECT_CANCEL = SELECT_FIRST_SPELL + ALL.size
    val SELECT_INFO_OUTER = SELECT_CANCEL + 1
    val SELECT_INFO_INNER = SELECT_CANCEL + 2
    val SELECT_INFO_FILL = SELECT_CANCEL + 3
    val SELECT_INFO_TEXT = SELECT_CANCEL + 4
    val SELECT_COMPONENTS = SELECT_INFO_TEXT + 1
    /** Component count of the first (titled, info-box-less) panel. */
    val SELECT_COMPONENTS_V1 = SELECT_CANCEL + 1

    /** The entries whose icon is an imported OSRS sprite, in sprite-id order. */
    val OSRS_ICON_ENTRIES: List<Entry> by lazy { ALL.filter { it.osrsSprite >= 0 } }

    fun componentOf(entry: Entry): Int = SELECT_FIRST_SPELL + ALL.indexOf(entry)

    fun entryAt(component: Int): Entry? = ALL.getOrNull(component - SELECT_FIRST_SPELL)
}

/**
 * Writes the autocast UI of [AutocastInterfaceLayout] into both production caches through [CacheTransaction] (preflight, journal,
 * verify; refuses while the servers run): combat tab 884:32..39, selection panel [AutocastInterfaceLayout.SELECT_INTERFACE], the
 * OSRS shield and spell icons copied byte-for-byte from the OSRS cache (2686) and the two tall box sprites built from 667 653/654.
 *
 * Usage: `./gradlew :game:runAutocastInterfaceImportTool --args="[--apply]"`
 */
object AutocastInterfaceImportTool {
    private const val INDEX_INTERFACES = 3
    private const val INDEX_SPRITES = 8
    private const val ORANGE = 0xFF981F
    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val ICON = AutocastInterfaceLayout.ICON_SIZE

    /** 884:32..36 as written by the single-box version (tx-20260924-011513); only exactly these may be replaced by the two-box layout. */
    private val SINGLE_BOX_SHA1 =
        mapOf(
            32 to "e79dbde5b768c63718d92ff29ec0e13455b53136",
            33 to "333150ae9d8d4218074ec5fb148f2f064902d737",
            34 to "bc40e0a875c0aeff63d8437b8795d8c2c903b4b2",
            35 to "404adfdc0df6def2de3e78a1829f76ea556663e3",
            36 to "c8ebefca392942bb12f14fcf59e40ebc1b5b24ff",
        )

    /**
     * SHA-1 over 884:32..39 followed by 1170:0..52 as the two-box / titled-panel version wrote them (both caches, 2026-09-24 22:15).
     * When the caches hold exactly that, each of those files may be replaced (its own current SHA-1 is pinned per mutation).
     */
    private const val TWO_BOX_LAYOUT_SHA1 = "e87e59919d6fa0e40f2a442d6dce76719dab308a"

    private fun layer(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_LAYER, x, y, w, h, parent, hidden = hidden)

    private fun graphic(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int, ops: List<String> = emptyList(), opBase: String = "", hidden: Boolean = false) =
        LootKeyInterfaceImportTool.Component(id, TYPE_GRAPHIC, x, y, w, h, parent, graphic = sprite, ops = ops, opBase = opBase, hidden = hidden)

    private fun text(
        id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, font: Int, text: String, ops: List<String> = emptyList(),
        colour: Int = ORANGE, alignX: Int = 1, shadow: Boolean = true, hidden: Boolean = false,
    ) = LootKeyInterfaceImportTool.Component(
        id, TYPE_TEXT, x, y, w, h, parent, font = font, text = text, colour = colour, alignX = alignX, alignY = 1, shadow = shadow, ops = ops,
        hidden = hidden,
    )

    private fun rect(id: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, colour: Int, filled: Boolean) =
        LootKeyInterfaceImportTool.Component(id, TYPE_RECTANGLE, x, y, w, h, parent, colour = colour, filled = filled)

    /** Box width/height of one "Spell" box: the right column (140 px, the three style slots) split in two with a 2 px gap. */
    private const val BOX_W = 70
    private const val BOX_H = 69

    /**
     * Components appended to combat tab 884 (children of its style layer 10). Inside a box the OSRS 593 offsets are kept, relative to
     * the box centre: shield 36x36 at centre-16, spell icon (OSRS 32x36 cell, 24x24 sprite) at centre+14/-7 in the defensive box and
     * centre+1/-3 in the other, "Spell" 16 px tall at centre+15.
     */
    fun combatTabComponents(fonts: LootKeyInterfaceImportTool.Fonts): List<LootKeyInterfaceImportTool.Component> {
        val l = AutocastInterfaceLayout
        val defY = 0
        val spellY = BOX_H + 2
        fun cx(w: Int, offset: Int) = (BOX_W - w) / 2 + offset
        fun cy(h: Int, offset: Int) = (BOX_H - h) / 2 + offset
        return listOf(
            layer(l.BOX_LAYER, 100, 3, BOX_W, 140, parent = 10, hidden = true),
            graphic(l.DEFENSIVE_BUTTON, 0, defY, BOX_W, BOX_H, l.BOX_LAYER, l.SPRITE_TALL_BOX, ops = listOf("Choose spell")),
            graphic(l.DEFENSIVE_SHIELD, cx(36, -16), defY + cy(36, 0), 36, 36, l.BOX_LAYER, l.SPRITE_SHIELD),
            graphic(l.DEFENSIVE_ICON, cx(32, 14) + 4, defY + cy(36, -7) + 6, ICON, ICON, l.BOX_LAYER, -1),
            text(l.DEFENSIVE_LABEL, 0, defY + cy(16, 15), BOX_W, 16, l.BOX_LAYER, fonts.p11, "Spell"),
            graphic(l.BOX_BUTTON, 0, spellY, BOX_W, BOX_H, l.BOX_LAYER, l.SPRITE_TALL_BOX, ops = listOf("Choose spell")),
            graphic(l.BOX_ICON, cx(32, 1) + 4, spellY + cy(36, -3) + 6, ICON, ICON, l.BOX_LAYER, -1),
            text(l.BOX_LABEL, 0, spellY + cy(16, 15), BOX_W, 16, l.BOX_LAYER, fonts.p11, "Spell"),
        )
    }

    /**
     * The selection panel as OSRS 201: the two spellbook grids (the server hides the other book and every spell the weapon cannot
     * autocast, and re-packs the rest), "Cancel" right-aligned at y 159, and the info box with "Select a Combat Spell". The title
     * component of the first version stays as a hidden, empty component so the component ids do not move.
     */
    fun selectComponents(fonts: LootKeyInterfaceImportTool.Fonts): List<LootKeyInterfaceImportTool.Component> {
        val l = AutocastInterfaceLayout
        val list = mutableListOf(
            layer(l.SELECT_ROOT, 0, 0, 190, 261, parent = -1),
            text(l.SELECT_TITLE, 0, 4, 190, 18, l.SELECT_ROOT, fonts.b12, "", hidden = true),
            layer(l.SELECT_STANDARD_LAYER, 0, 0, 190, 170, l.SELECT_ROOT),
            layer(l.SELECT_ANCIENT_LAYER, 0, 0, 190, 170, l.SELECT_ROOT, hidden = true),
        )
        fun grid(entries: List<AutocastInterfaceLayout.Entry>, parent: Int) {
            entries.forEachIndexed { index, entry ->
                val (x, y) = l.gridPosition(index, entries.size)
                list += graphic(l.componentOf(entry), x, y, ICON, ICON, parent, entry.sprite, ops = listOf("Select"), opBase = "<col=00ff00>${entry.name}")
            }
        }
        grid(l.STANDARD, l.SELECT_STANDARD_LAYER)
        grid(l.ANCIENT, l.SELECT_ANCIENT_LAYER)
        list += text(l.SELECT_CANCEL, 110, 159, 74, 16, l.SELECT_ROOT, fonts.p12, "Cancel", ops = listOf("Cancel"), colour = 0x8F8F8F, alignX = 2, shadow = false)
        list += rect(l.SELECT_INFO_OUTER, l.INFO_X, l.INFO_Y, l.INFO_W, l.INFO_H, l.SELECT_ROOT, 0x726451, filled = false)
        list += rect(l.SELECT_INFO_INNER, l.INFO_X + 1, l.INFO_Y + 1, l.INFO_W - 2, l.INFO_H - 2, l.SELECT_ROOT, 0x2E2B23, filled = false)
        list += rect(l.SELECT_INFO_FILL, l.INFO_X + 2, l.INFO_Y + 2, l.INFO_W - 4, l.INFO_H - 4, l.SELECT_ROOT, 0x000000, filled = true)
        list += text(
            l.SELECT_INFO_TEXT, l.INFO_X + 2, l.INFO_Y + 2, l.INFO_W - 4, l.INFO_H - 4, l.SELECT_ROOT, fonts.p12, "Select a Combat Spell",
            colour = 0xAFAF9F, shadow = false,
        )
        require(list.map { it.id }.sorted() == (0 until l.SELECT_COMPONENTS).toList()) { "component ids must be 0..${l.SELECT_COMPONENTS - 1}" }
        return list.sortedBy { it.id }
    }

    /** [frame] (the 68x44 667 style box) drawn [height] tall: top/bottom 10 border rows kept, the stone rows between repeated. */
    fun tallBox(frame: FeroxMapSpriteProbeTool.Frame, height: Int): java.awt.image.BufferedImage {
        val border = 10
        val image = java.awt.image.BufferedImage(frame.w, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val middle = frame.h - 2 * border
        for (y in 0 until height) {
            val source =
                when {
                    y < border -> y
                    y >= height - border -> frame.h - (height - y)
                    else -> border + (y - border) % middle
                }
            for (x in 0 until frame.w) image.setRGB(x, y, frame.argb[x + source * frame.w])
        }
        return image
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val l = AutocastInterfaceLayout
        val targets = OsrsItemImportTool.TARGETS
        val replaceable = mutableMapOf<Pair<Int, Int>, String>()
        val (fonts, boxes) =
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
                            require(existing == null || existing.fileIds().size in setOf(l.SELECT_COMPONENTS_V1, l.SELECT_COMPONENTS)) {
                                "$path: interface ${l.SELECT_INTERFACE} already holds other content"
                            }
                            if (existing != null && existing.fileIds().size == l.SELECT_COMPONENTS_V1) {
                                val files = (32 until l.COMBAT_TAB_COMPONENTS).map { INDEX_INTERFACES to (l.COMBAT_TAB to it) } +
                                    (0 until l.SELECT_COMPONENTS_V1).map { INDEX_INTERFACES to (l.SELECT_INTERFACE to it) }
                                val digest = java.security.MessageDigest.getInstance("SHA-1")
                                val current = files.map { (idx, gf) -> gf to (lib.data(idx, gf.first, gf.second) ?: ByteArray(0)) }
                                current.forEach { digest.update(it.second) }
                                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                                require(sha == TWO_BOX_LAYOUT_SHA1) { "$path: autocast interfaces changed since the two-box version (sha1 $sha), refusing" }
                                current.forEach { (gf, bytes) -> replaceable[gf] = CacheItemProbeTool.sha1(bytes) }
                            }
                        } finally {
                            if (lib !== library) lib.close()
                        }
                    }
                    val box = { id: Int ->
                        FeroxMapSpriteProbeTool.decodeSprites(library.data(INDEX_SPRITES, id, 0) ?: error("667 sprite $id missing")).first()
                    }
                    LootKeyInterfaceImportTool.fonts(library) to (tallBox(box(653), l.TALL_BOX_HEIGHT) to tallBox(box(654), l.TALL_BOX_HEIGHT))
                } finally {
                    library.close()
                }
            }
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(java.io.File(OsrsItemImportTool.SOURCE_CACHE)).use { osrs ->
            fun copy(osrsId: Int, target: Int, label: String) {
                val bytes = osrs.file(INDEX_SPRITES, osrsId, 0) ?: error("OSRS sprite $osrsId missing")
                mutations += CacheMutation(INDEX_SPRITES, target, 0, bytes, "sprite osrs:$osrsId -> 667:$target $label")
            }
            copy(l.OSRS_SHIELD, l.SPRITE_SHIELD, "defensive autocast shield")
            l.OSRS_ICON_ENTRIES.forEach { entry ->
                copy(entry.osrsSprite, entry.sprite, entry.name)
                copy(entry.osrsSprite + 50, entry.disabledSprite, "${entry.name} (dark)")
            }
        }
        mutations += CacheMutation(INDEX_SPRITES, l.SPRITE_TALL_BOX, 0, StoreArtTool.encode(boxes.first), "autocast tall box")
        mutations += CacheMutation(INDEX_SPRITES, l.SPRITE_TALL_BOX_SELECTED, 0, StoreArtTool.encode(boxes.second), "autocast tall box selected")
        combatTabComponents(fonts).forEach { c ->
            mutations += CacheMutation(
                INDEX_INTERFACES, l.COMBAT_TAB, c.id, LootKeyInterfaceImportTool.encode(c), "interface ${l.COMBAT_TAB}:${c.id} autocast box",
                expectedCurrentSha1 = replaceable[l.COMBAT_TAB to c.id] ?: SINGLE_BOX_SHA1[c.id],
            )
        }
        selectComponents(fonts).forEach { c ->
            mutations += CacheMutation(
                INDEX_INTERFACES, l.SELECT_INTERFACE, c.id, LootKeyInterfaceImportTool.encode(c), "interface ${l.SELECT_INTERFACE}:${c.id} autocast select",
                expectedCurrentSha1 = replaceable[l.SELECT_INTERFACE to c.id],
            )
        }
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write 884:${l.BOX_LAYER}..${l.COMBAT_TAB_COMPONENTS - 1}, interface ${l.SELECT_INTERFACE} and sprites ${l.SPRITE_BASE}..")
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
