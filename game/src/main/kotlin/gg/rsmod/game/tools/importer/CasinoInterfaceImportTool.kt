package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Builds the four casino screens - Dice, Mines, Blackjack and Flower Poker - as revision-667 if3 interfaces and
 * writes them into both production caches through [CacheTransaction].
 *
 * Owner 2026-09-20, on the first build of these screens: "make the blackyack interface more ui friendly give it some
 * more options and make it more beautifull also why do i see seed [...] check picture mines [...] see picture dice
 * [...] do the same [...] make all interfaces better make the ui better more visualy better [...] everything looks
 * low quality". That feedback drives three changes, and they are the reason this file looks the way it does.
 *
 * **1. The seeds are gone from the screen.** The provably-fair machinery is still there and still provable, but a
 * player who only wants to bet should never have to look at a hex digest. The seed strip that used to sit along the
 * bottom of every screen is now a hidden overlay panel ([Screen.fairnessOverlay]) behind one small "Provably fair"
 * button. Opening it is a single IF_SETHIDE; nothing about the commit/reveal protocol changed.
 *
 * **2. Every button is built from the OSRS three-slice button caps, never from a stretched sprite.** The first draft
 * drew every control as the 68x44 dialog button sprite (OSRS 653) squashed into a 30x24 box, which is exactly what
 * "low quality" looks like: a bitmap resampled to a third of its width. A cap button is a 6px left cap, a *tiled*
 * middle and a 6px right cap at the sprites' native 22px height ([Screen.button]), so it is pixel-crisp at any
 * width - the same construction the 78 Store window uses.
 *
 * **3. Content sits on panels, under a themed header.** Each screen opens with a coloured banner carrying a one-line
 * explanation of the game and the player's coins, and every group of numbers sits on a dark glass panel with a
 * theme-coloured outline instead of floating on bare stone.
 *
 * On top of the looks, each game gained the options the owner asked for: quick-bet chips (1K/10K/100K/1M/10M),
 * halve / double / max, dice gained a roll-over/roll-under switch and target presets, mines gained mine-count
 * presets and a "pick random tile" button, and blackjack gained rebet alongside its hit/stand/double/split/insure.
 *
 * **This tool adds no sprites.** That is a deliberate constraint, not a shortcut:
 *
 *  - the stone frame, button caps, square slot button and close cross reuse the OSRS sprites
 *    [LootKeyInterfaceImportTool] already imported at [LootKeyInterfaceImportTool.SPRITE_BASE];
 *  - flowers, gems and mines are drawn as **real cache items** through `IF_SETOBJECT` (the flower items 2460-2476,
 *    an uncut gem and a cannonball), so they need no art of their own;
 *  - playing cards are drawn as a plate, a border and rank/suit text in the cache's own fonts, because this revision
 *    has no card art and a PNG-to-667 sprite encoder does not exist in this project.
 *
 * The result is that the only cache mutation here is four groups in the interface index - the sprite index is
 * untouched - which is the smallest, most reversible change that still delivers real interfaces.
 *
 * Every screen is static: the server fills the texts with IF_SETTEXT, swaps item slots with IF_SETOBJECT, recolours
 * a button by swapping its three caps with IF_SETGRAPHIC, shows and hides layers with IF_SETHIDE and arms the
 * buttons with IF_SETEVENTS, so nothing depends on a clientscript the 667 cache does not have.
 *
 * Usage: `./gradlew :game:runCasinoInterfaceImportTool --args="[--apply] [--replace-from-journal=<tx id>]"`
 */
object CasinoInterfaceImportTool {
    val TARGETS = OsrsItemImportTool.TARGETS

    const val INDEX_INTERFACES = 3

    /**
     * New 667 interface ids.
     *
     * Probed, not assumed: `--probe` reported the interface index holds 1152 archives with 1151 the highest, so
     * 1152-1155 is the first free run of four. (1149 is the loot-key screen and 1150-1151 were taken after it,
     * which is why the loot-key tool's "last interface is 1148" comment can no longer be trusted.)
     */
    const val DICE_ID = 1152
    const val MINES_ID = 1153
    const val BLACKJACK_ID = 1154
    const val FLOWER_ID = 1155

    // ---- shared frame geometry ----

    /**
     * 480x330 - the same window the 78 Store uses, which is the largest shape proven to fit the fixed-mode main
     * screen (512x334). The extra ten rows over the first draft's 320 are what let the bottom row of content clear
     * the frame's own bottom edge art instead of being drawn over it.
     */
    const val WINDOW_W = 480
    const val WINDOW_H = 330

    const val ROOT = 0
    const val WINDOW = 1
    const val TITLE = 12
    const val CLOSE = 13

    /** Themed header strip: the banner, the game's one-line explanation, and the player's coins. */
    const val BANNER = 16
    const val TAGLINE = 17
    const val COINS_LABEL = 18
    const val COINS_TEXT = 19

    /** First id a screen may use for its own content; 0..19 are the shared frame and header. */
    const val CONTENT_BASE = 20

    /**
     * Content margins. The frame's corner sprites occupy 32x32 at each corner, so the top row starts below them
     * and the bottom row keeps inside x [SAFE_LEFT]..[SAFE_RIGHT] where they reach into the content area.
     */
    const val MARGIN = 12
    const val INNER_W = WINDOW_W - 2 * MARGIN
    const val SAFE_LEFT = 36
    const val SAFE_RIGHT = WINDOW_W - 36

    /** Rows of controls are one cap-sprite tall; anything else stretches the caps and looks resampled. */
    const val ROW_H = 22

    val ORANGE = LootKeyInterfaceImportTool.ORANGE
    val WHITE = LootKeyInterfaceImportTool.WHITE
    val RED = LootKeyInterfaceImportTool.RED
    val LINE = LootKeyInterfaceImportTool.LINE

    /** A softer green than 0x00FF00: a full-saturation green on stone reads as neon rather than as a win. */
    const val GREEN = 0x3CD33C
    const val GOLD = 0xFFD700
    const val GREY = 0x9F9F9F

    /** Per-game theme colour: dice azure, mines gold, blackjack table green, flower poker violet. */
    const val THEME_DICE = 0x2B8FBF
    const val THEME_MINES = 0xC9A227
    const val THEME_BLACKJACK = 0x1E7A3E
    const val THEME_FLOWER = 0x8E3FBF

    /**
     * The two halves of the dice bar: a muted table red and green rather than the pure 0xFF0000/0x00FF00 used for
     * text, so that three hundred pixels of it do not glare, and so the white roll marker still reads on top.
     */
    const val BAR_LOSE = 0x8E2F2F
    const val BAR_WIN = 0x2F8E4A

    /** Card plate and its border; hearts and diamonds print red, spades and clubs black. */
    const val CARD_PLATE = 0xEDE9DC
    const val CARD_BORDER = 0x2B2822
    const val CARD_RED = 0xC00000
    const val CARD_BLACK = 0x101010

    /** Glass panel behind a group of numbers: black at 150/255 transparency with a theme-coloured outline. */
    const val PANEL_FILL = 0x000000
    const val PANEL_TRANSPARENCY = 150

    private const val TYPE_LAYER = 0
    private const val TYPE_RECTANGLE = 3
    private const val TYPE_TEXT = 4
    private const val TYPE_GRAPHIC = 5
    private const val TYPE_LINE = 9

    /** OSRS sprite ids of the three-slice button caps, grey (idle) and red (active). */
    const val GREY_LEFT = 1229
    const val GREY_MIDDLE = 1230
    const val GREY_RIGHT = 1231
    const val RED_LEFT = 1232
    const val RED_MIDDLE = 1233
    const val RED_RIGHT = 1234

    /** The 667 sprite id of an OSRS sprite the loot-key window imported; public so the server can swap caps. */
    fun sprite(osrsId: Int) = LootKeyInterfaceImportTool.sprite(osrsId)

    // ---------------------------------------------------------------- id strides

    /**
     * A cap button is five components: the layer that carries the op, its three cap graphics and the caption. The
     * server arms and hides the *layer*, which hides the whole button in one packet, and swaps the three caps to
     * turn the button red when it is the active choice.
     */
    const val BUTTON_STRIDE = 5
    const val BUTTON_LAYER = 0
    const val BUTTON_LEFT = 1
    const val BUTTON_MIDDLE = 2
    const val BUTTON_RIGHT = 3
    const val BUTTON_TEXT = 4

    /** A panel is its glass fill plus its outline. */
    const val PANEL_STRIDE = 2

    /**
     * A playing card is a layer with six children, so one IF_SETHIDE on the layer draws or clears a whole card.
     * The client cannot recolour a text after the fact, so the rank and the suit each exist twice - once black,
     * once red - and the server shows the pair that matches the suit.
     */
    const val CARD_STRIDE = 7
    const val CARD_LAYER = 0
    const val CARD_PLATE_ID = 1
    const val CARD_BORDER_ID = 2
    const val CARD_RANK_BLACK = 3
    const val CARD_RANK_RED = 4
    const val CARD_SUIT_BLACK = 5
    const val CARD_SUIT_RED = 6

    /** A history chip is one green and one red text stacked, of which the server shows one. */
    const val HISTORY_STRIDE = 2

    /**
     * The provably-fair overlay every screen carries, hidden until the player asks for it.
     *
     * Owner 2026-09-20: "why do i see seed". The answer is that they should not, unless they want to - so the seeds
     * moved off the screen and into this panel, reachable from one button and closed again with one click.
     */
    object Fair {
        const val STRIDE = 28
        const val LAYER = 0
        const val DIM = 1
        const val PANEL = 2
        const val OUTLINE = 3
        const val TITLE = 4
        const val HASH_LABEL = 5
        const val HASH_TEXT = 6
        const val CLIENT_LABEL = 7
        const val CLIENT_TEXT = 8
        const val NONCE_LABEL = 9
        const val NONCE_TEXT = 10
        const val EXPLAIN = 11
        const val PREVIOUS = 12
        const val SET_SEED = 13
        const val NEW_SEED = 18
        const val CLOSE = 23
    }

    /**
     * Hands out component ids in declaration order so no layout constant is ever chosen by hand.
     *
     * Component ids are file ids inside the interface's cache archive and the client walks them by index, so a gap
     * or a collision shifts every later component. The first draft numbered them by hand from a handful of `+ 1`
     * offsets and needed an inert-filler pass in [Screen.build] to paper over the holes that left. Allocating them
     * from one counter removes the whole class of bug: ids are contiguous by construction, and adding a control is
     * a new `val` rather than a re-numbering of everything below it.
     */
    class Seq(
        private var next: Int = CONTENT_BASE,
    ) {
        fun one(): Int = next++

        fun many(count: Int): Int {
            val first = next
            next += count
            return first
        }

        fun button(): Int = many(BUTTON_STRIDE)

        fun panel(): Int = many(PANEL_STRIDE)

        fun card(): Int = many(CARD_STRIDE)

        fun fairness(): Int = many(Fair.STRIDE)
    }

    /** Builder for one screen: shared frame first, then the screen's own components. */
    class Screen(
        val id: Int,
        val title: String,
        val theme: Int,
        val fonts: LootKeyInterfaceImportTool.Fonts,
    ) {
        val list = mutableListOf<LootKeyInterfaceImportTool.Component>()

        fun add(c: LootKeyInterfaceImportTool.Component) {
            require(list.none { it.id == c.id }) { "duplicate component ${c.id} on interface $id" }
            list += c
        }

        fun layer(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            hidden: Boolean = false,
            reposX: Int = 0,
            reposY: Int = 0,
            resizeX: Int = 0,
            resizeY: Int = 0,
            noClickThrough: Boolean = false,
            ops: List<String> = emptyList(),
            opBase: String = "",
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_LAYER, x, y, w, h, parent,
                hidden = hidden, reposX = reposX, reposY = reposY, resizeX = resizeX, resizeY = resizeY,
                noClickThrough = noClickThrough, ops = ops, opBase = opBase,
            ),
        )

        fun graphic(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            spriteId: Int,
            tiling: Boolean = false,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_GRAPHIC, x, y, w, h, parent,
                graphic = spriteId, tiling = tiling, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        /** An item slot: a graphic with no sprite, filled by the server with IF_SETOBJECT. */
        fun item(
            id: Int,
            x: Int,
            y: Int,
            parent: Int,
            size: Int = 32,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_GRAPHIC, x, y, size, size, parent,
                graphic = -1, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        fun text(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            font: Int,
            value: String,
            colour: Int,
            alignX: Int = 1,
            alignY: Int = 1,
            lineHeight: Int = 0,
            ops: List<String> = emptyList(),
            opBase: String = "",
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_TEXT, x, y, w, h, parent,
                font = font, text = value, colour = colour, alignX = alignX, alignY = alignY,
                shadow = true, lineHeight = lineHeight, ops = ops, opBase = opBase, hidden = hidden,
            ),
        )

        fun rect(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            colour: Int,
            filled: Boolean = true,
            transparency: Int = 0,
            hidden: Boolean = false,
        ) = add(
            LootKeyInterfaceImportTool.Component(
                id, TYPE_RECTANGLE, x, y, w, h, parent,
                colour = colour, filled = filled, transparency = transparency, hidden = hidden,
            ),
        )

        fun line(
            id: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            parent: Int,
            colour: Int,
        ) = add(LootKeyInterfaceImportTool.Component(id, TYPE_LINE, x, y, w, h, parent, colour = colour))

        /**
         * A pressable button: three OSRS button-cap slices with a caption centred on them, all hanging off one
         * layer that carries the op.
         *
         * The caps are 6px end slices around a middle that *tiles*, so the button is pixel-exact at any width -
         * unlike the 68x44 dialog-button sprite the first draft squashed into every control. Height should stay at
         * [ROW_H], the caps' own height; anything else resamples them vertically.
         *
         * Hiding or arming the returned id (the layer) hides or arms the whole button, because this client skips a
         * hidden component's entire subtree.
         */
        fun button(
            base: Int,
            x: Int,
            y: Int,
            w: Int,
            caption: String,
            op: String,
            opBase: String = "",
            hidden: Boolean = false,
            active: Boolean = false,
            font: Int = fonts.p12,
            h: Int = ROW_H,
        ): Int {
            require(w >= 16) { "button $base is $w px wide; the two 6px caps need at least 16" }
            layer(base + BUTTON_LAYER, x, y, w, h, WINDOW, hidden = hidden, ops = listOf(op), opBase = opBase)
            val left = if (active) RED_LEFT else GREY_LEFT
            val middle = if (active) RED_MIDDLE else GREY_MIDDLE
            val right = if (active) RED_RIGHT else GREY_RIGHT
            graphic(base + BUTTON_LEFT, 0, 0, 6, h, base + BUTTON_LAYER, sprite(left))
            graphic(base + BUTTON_MIDDLE, 6, 0, w - 12, h, base + BUTTON_LAYER, sprite(middle), tiling = true)
            graphic(base + BUTTON_RIGHT, w - 6, 0, 6, h, base + BUTTON_LAYER, sprite(right))
            text(base + BUTTON_TEXT, 0, 0, w, h, base + BUTTON_LAYER, font, caption, ORANGE)
            return base + BUTTON_LAYER
        }

        /** Dark glass with a theme-coloured outline: the backdrop every group of numbers sits on. */
        fun panel(
            base: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
        ) {
            rect(base, x, y, w, h, WINDOW, PANEL_FILL, filled = true, transparency = PANEL_TRANSPARENCY)
            rect(base + 1, x, y, w, h, WINDOW, theme, filled = false)
        }

        /** A label / value pair on one line, the shape every readout in this file uses. */
        fun readout(
            labelId: Int,
            valueId: Int,
            x: Int,
            y: Int,
            labelW: Int,
            valueW: Int,
            label: String,
            value: String,
            valueColour: Int = WHITE,
            valueFont: Int = fonts.p12,
            h: Int = 16,
        ) {
            text(labelId, x, y, labelW, h, WINDOW, fonts.p11, label, GREY, alignX = 0)
            text(valueId, x + labelW, y, valueW, h, WINDOW, valueFont, value, valueColour, alignX = 0)
        }

        /** The shared stone frame, copied from the loot-key screen's proven geometry. */
        fun frame() {
            layer(ROOT, 0, 0, 0, 0, parent = -1, resizeX = 1, resizeY = 1)
            layer(WINDOW, 0, 0, WINDOW_W, WINDOW_H, parent = ROOT, reposX = 1, reposY = 1)

            graphic(2, 1, 1, WINDOW_W - 2, WINDOW_H - 2, WINDOW, sprite(297), tiling = true)
            graphic(3, 32, -13, WINDOW_W - 64, 20, WINDOW, sprite(820), tiling = true)
            graphic(4, 32, WINDOW_H - 20, WINDOW_W - 64, 20, WINDOW, sprite(822), tiling = true)
            graphic(5, -13, 32, 20, WINDOW_H - 64, WINDOW, sprite(821), tiling = true)
            graphic(6, WINDOW_W - 20, 32, 20, WINDOW_H - 64, WINDOW, sprite(823), tiling = true)
            graphic(7, 0, 0, 32, 32, WINDOW, sprite(824))
            graphic(8, WINDOW_W - 32, 0, 32, 32, WINDOW, sprite(825))
            graphic(9, 0, WINDOW_H - 32, 32, 32, WINDOW, sprite(826))
            graphic(10, WINDOW_W - 32, WINDOW_H - 32, 32, 32, WINDOW, sprite(827))
            graphic(11, 11, 15, WINDOW_W - 22, 20, WINDOW, sprite(828), tiling = true)
            text(TITLE, 0, 5, WINDOW_W, 20, WINDOW, fonts.b12, title, ORANGE)
            graphic(CLOSE, WINDOW_W - 26, 10, 16, 16, WINDOW, sprite(831), ops = listOf("Close"))
            graphic(14, 0, 17, 32, 32, WINDOW, sprite(829))
            graphic(15, WINDOW_W - 11, 17, 11, 32, WINDOW, sprite(830))
        }

        /**
         * The themed header: a translucent band in the game's colour carrying a one-line explanation on the left
         * and the player's coin balance on the right.
         *
         * The tagline is static text baked into the cache - it never changes - so it costs nothing to send and
         * tells a first-time player what the game is before they bet anything.
         */
        fun header(tagline: String) {
            rect(BANNER, MARGIN, 36, INNER_W, ROW_H, WINDOW, theme, filled = true, transparency = 120)
            text(TAGLINE, MARGIN + 6, 36, 286, ROW_H, WINDOW, fonts.p11, tagline, WHITE, alignX = 0)
            text(COINS_LABEL, 300, 36, 56, ROW_H, WINDOW, fonts.p11, "Coins", GREY, alignX = 2)
            text(COINS_TEXT, 360, 36, WINDOW_W - MARGIN - 366, ROW_H, WINDOW, fonts.b12, "0", GOLD, alignX = 2)
        }

        /**
         * The bet row every house game shares: the amount, then halve / double / max / set.
         *
         * Owner 2026-09-20 asked for "some more options"; the cheapest real option in a betting screen is not
         * having to type a number, so the amount can be moved with one click in either direction, taken to the
         * most the player can afford, or typed when they want an exact figure.
         */
        fun betRow(
            labelId: Int,
            valueId: Int,
            halve: Int,
            double: Int,
            max: Int,
            custom: Int,
            y: Int,
        ) {
            text(labelId, 18, y, 70, ROW_H, WINDOW, fonts.p12, "Bet", GREY, alignX = 0)
            text(valueId, 90, y, 110, ROW_H, WINDOW, fonts.b12, "1,000", WHITE, alignX = 0)
            button(halve, 202, y, 34, "1/2", "Halve bet")
            button(double, 240, y, 34, "x2", "Double bet")
            button(max, 278, y, 46, "Max", "Max bet")
            button(custom, 328, y, 70, "Set bet", "Set bet")
        }

        /** The five quick-bet chips, in the order they read: 1K, 10K, 100K, 1M, 10M. */
        fun quickBets(
            first: Int,
            y: Int,
        ) {
            QUICK_BET_LABELS.forEachIndexed { index, label ->
                button(first + index * BUTTON_STRIDE, 18 + index * 66, y, 62, label, "Bet $label")
            }
        }

        /**
         * The provably-fair overlay: a dimmed screen with a stone panel on top of it, hidden until the player
         * presses "Provably fair".
         *
         * `noClickThrough` on the layer is what makes it modal - while it is open the controls underneath cannot
         * be reached, so a player cannot bet from behind the panel that is telling them what their seeds are.
         */
        fun fairnessOverlay(base: Int) {
            val panelX = 54
            val panelY = 70
            val panelW = 372
            val panelH = 196
            layer(base + Fair.LAYER, 0, 0, WINDOW_W, WINDOW_H, WINDOW, hidden = true, noClickThrough = true)
            val root = base + Fair.LAYER
            add(
                LootKeyInterfaceImportTool.Component(
                    base + Fair.DIM, TYPE_RECTANGLE, 0, 0, WINDOW_W, WINDOW_H, root,
                    colour = 0, filled = true, transparency = 160,
                ),
            )
            graphic(base + Fair.PANEL, panelX, panelY, panelW, panelH, root, sprite(297), tiling = true)
            add(
                LootKeyInterfaceImportTool.Component(
                    base + Fair.OUTLINE, TYPE_RECTANGLE, panelX, panelY, panelW, panelH, root,
                    colour = theme, filled = false,
                ),
            )
            text(base + Fair.TITLE, panelX, panelY + 8, panelW, 18, root, fonts.b12, "Provably fair", ORANGE)

            val labelX = panelX + 14
            val valueX = panelX + 150
            val valueW = panelW - 164
            text(base + Fair.HASH_LABEL, labelX, panelY + 36, 130, 14, root, fonts.p11, "Server seed (hashed)", GREY, alignX = 0)
            text(base + Fair.HASH_TEXT, valueX, panelY + 36, valueW, 14, root, fonts.p11, "", WHITE, alignX = 0)
            text(base + Fair.CLIENT_LABEL, labelX, panelY + 54, 130, 14, root, fonts.p11, "Your seed", GREY, alignX = 0)
            text(base + Fair.CLIENT_TEXT, valueX, panelY + 54, valueW, 14, root, fonts.p11, "", WHITE, alignX = 0)
            text(base + Fair.NONCE_LABEL, labelX, panelY + 72, 130, 14, root, fonts.p11, "Rounds on this seed", GREY, alignX = 0)
            text(base + Fair.NONCE_TEXT, valueX, panelY + 72, valueW, 14, root, fonts.p11, "0", WHITE, alignX = 0)
            text(
                base + Fair.EXPLAIN, labelX, panelY + 96, panelW - 28, 42, root, fonts.p11,
                "Before you bet you are shown a hash of the house seed. Pick your own seed at any time. " +
                    "Retire the house seed and it is revealed, so you can replay every round yourself.",
                GREY, lineHeight = 14,
            )
            text(base + Fair.PREVIOUS, labelX, panelY + 140, panelW - 28, 14, root, fonts.p11, "", GOLD, alignX = 0)

            val buttonY = panelY + panelH - 34
            fairnessButton(base + Fair.SET_SEED, panelX + 14, buttonY, 116, "Change my seed", "Set seed", root)
            fairnessButton(base + Fair.NEW_SEED, panelX + 138, buttonY, 130, "New house seed", "New server seed", root)
            fairnessButton(base + Fair.CLOSE, panelX + 276, buttonY, 82, "Close", "Close fairness", root)
        }

        /** [button], but parented to the overlay layer instead of the window, so it hides with the overlay. */
        private fun fairnessButton(
            base: Int,
            x: Int,
            y: Int,
            w: Int,
            caption: String,
            op: String,
            parent: Int,
        ) {
            layer(base + BUTTON_LAYER, x, y, w, ROW_H, parent, ops = listOf(op))
            graphic(base + BUTTON_LEFT, 0, 0, 6, ROW_H, base + BUTTON_LAYER, sprite(GREY_LEFT))
            graphic(base + BUTTON_MIDDLE, 6, 0, w - 12, ROW_H, base + BUTTON_LAYER, sprite(GREY_MIDDLE), tiling = true)
            graphic(base + BUTTON_RIGHT, w - 6, 0, 6, ROW_H, base + BUTTON_LAYER, sprite(GREY_RIGHT))
            text(base + BUTTON_TEXT, 0, 0, w, ROW_H, base + BUTTON_LAYER, fonts.p11, caption, ORANGE)
        }

        /**
         * One playing card. The layer is hidden, so a card only appears when the server shows it.
         *
         * Rank and suit exist twice, once in each ink colour, because this client cannot recolour a text
         * component after the fact - and a single fixed-black rank, which is what the first draft had, makes
         * hearts and diamonds indistinguishable from spades and clubs.
         */
        fun card(
            base: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
        ) {
            layer(base + CARD_LAYER, x, y, w, h, WINDOW, hidden = true)
            val root = base + CARD_LAYER
            add(
                LootKeyInterfaceImportTool.Component(
                    base + CARD_PLATE_ID, TYPE_RECTANGLE, 0, 0, w, h, root,
                    colour = CARD_PLATE, filled = true,
                ),
            )
            add(
                LootKeyInterfaceImportTool.Component(
                    base + CARD_BORDER_ID, TYPE_RECTANGLE, 0, 0, w, h, root,
                    colour = CARD_BORDER, filled = false,
                ),
            )
            text(base + CARD_RANK_BLACK, 0, 3, w, 14, root, fonts.b12, "", CARD_BLACK, hidden = true)
            text(base + CARD_RANK_RED, 0, 3, w, 14, root, fonts.b12, "", CARD_RED, hidden = true)
            text(base + CARD_SUIT_BLACK, 0, h - 15, w, 12, root, fonts.p11, "", CARD_BLACK, hidden = true)
            text(base + CARD_SUIT_RED, 0, h - 15, w, 12, root, fonts.p11, "", CARD_RED, hidden = true)
        }

        /**
         * Returns the components as a gapless `0..n` run.
         *
         * [Seq] allocates content ids contiguously, so the only holes that can remain are inside the shared frame
         * block (ids 0..19) when a screen leaves one unused. Any such gap is filled with an inert hidden layer:
         * it costs a handful of bytes and keeps the archive a true `0..n` run, which is what the client's
         * index walk requires.
         */
        fun build(): List<LootKeyInterfaceImportTool.Component> {
            val used = list.map { it.id }.toSet()
            val filler =
                (0..used.max()).filter { it !in used }.map { missing ->
                    LootKeyInterfaceImportTool.Component(missing, TYPE_LAYER, 0, 0, 0, 0, WINDOW, hidden = true)
                }
            return (list + filler).sortedBy { it.id }
        }
    }

    /** The quick-bet chips, in reading order. The server maps them by index, so the order is part of the contract. */
    val QUICK_BET_LABELS = listOf("1K", "10K", "100K", "1M", "10M")
    val QUICK_BET_AMOUNTS = listOf(1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L)
    const val QUICK_BETS = 5

    /** Shared row baselines, so the three house games line up with each other. */
    const val ROW_BET_Y = 62
    const val ROW_QUICK_Y = 88
    const val ROW_THIRD_Y = 114

    // ================================================================= Dice

    /**
     * Dice.
     *
     * The screen is built around the win/lose bar, because a dice game whose only feedback is a number is a
     * spreadsheet. The bar is the 0.00-100.00 range drawn as 50 segments of two points each: every segment exists
     * twice, once red and once green, and the server shows whichever half matches the current target and mode.
     * Moving the target therefore slides the colour boundary in real time, and a marker under the bar lights up
     * where the roll actually landed. It is built from plain rectangles because this client has no way to resize or
     * recolour a component after the fact, so "one rectangle that grows" is not available while "a hundred
     * rectangles of which the right half is hidden" is.
     *
     * Texts that must change colour (the roll readout, the outcome line, the history chips) are built the same way:
     * one component per colour, stacked, with all but one hidden.
     */
    object Dice {
        /** Segments of the win/lose bar; each covers two points of the 0-100 range. */
        const val SEGMENTS = 50

        /** How many past rolls the strip along the bottom remembers. */
        const val HISTORY_SLOTS = 5

        private val seq = Seq()

        val BET_LABEL = seq.one()
        val BET_TEXT = seq.one()
        val BET_HALVE = seq.button()
        val BET_DOUBLE = seq.button()
        val BET_MAX = seq.button()
        val BET_CUSTOM = seq.button()

        val QUICK_FIRST = seq.many(BUTTON_STRIDE * QUICK_BETS)

        val MODE_OVER = seq.button()
        val MODE_UNDER = seq.button()

        val TARGET_LABEL = seq.one()
        val TARGET_TEXT = seq.one()
        val TARGET_MINUS = seq.button()
        val TARGET_PLUS = seq.button()
        val TARGET_P25 = seq.button()
        val TARGET_P50 = seq.button()
        val TARGET_P75 = seq.button()
        val TARGET_CUSTOM = seq.button()

        val BAR_PANEL = seq.panel()
        val BAR_FIRST = seq.many(SEGMENTS * 2)
        val MARKER_FIRST = seq.many(SEGMENTS)
        val SCALE_FIRST = seq.many(5)

        val STATS_PANEL = seq.panel()
        val CHANCE_LABEL = seq.one()
        val CHANCE_TEXT = seq.one()
        val MULTIPLIER_LABEL = seq.one()
        val MULTIPLIER_TEXT = seq.one()
        val PAYOUT_LABEL = seq.one()
        val PAYOUT_TEXT = seq.one()
        val PROFIT_LABEL = seq.one()
        val PROFIT_TEXT = seq.one()

        val ROLL_LABEL = seq.one()
        val ROLL_IDLE = seq.one()
        val ROLL_WIN = seq.one()
        val ROLL_LOSE = seq.one()
        val ROLL_BUTTON = seq.button()

        val OUTCOME_IDLE = seq.one()
        val OUTCOME_WIN = seq.one()
        val OUTCOME_LOSE = seq.one()

        val HISTORY_LABEL = seq.one()
        val HISTORY_FIRST = seq.many(HISTORY_SLOTS * HISTORY_STRIDE)
        val FAIR_BUTTON = seq.button()
        val FAIRNESS = seq.fairness()

        /** The red half of segment [index] - shown while that segment loses at the current target. */
        fun loseSegment(index: Int) = BAR_FIRST + index * 2

        /** The green half of segment [index] - shown while that segment wins at the current target. */
        fun winSegment(index: Int) = BAR_FIRST + index * 2 + 1

        /** The tick under segment [index]; exactly one is ever visible, under the segment the roll fell in. */
        fun marker(index: Int) = MARKER_FIRST + index

        fun quickBet(index: Int) = QUICK_FIRST + index * BUTTON_STRIDE

        /** The green chip of history slot [index]; `+ 1` is its red twin. */
        fun historyWin(index: Int) = HISTORY_FIRST + index * HISTORY_STRIDE

        fun historyLose(index: Int) = HISTORY_FIRST + index * HISTORY_STRIDE + 1

        // Bar geometry. 50 segments of 6px sit on a 300px track, so point `v` of the range is at BAR_X + v * 3.
        const val BAR_X = 90
        const val BAR_Y = 146
        const val BAR_H = 18
        const val SEGMENT_W = 6

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(DICE_ID, "Dice", THEME_DICE, fonts)
            s.frame()
            s.header("Beat the target to win - 99% return to player")

            s.betRow(BET_LABEL, BET_TEXT, BET_HALVE, BET_DOUBLE, BET_MAX, BET_CUSTOM, ROW_BET_Y)
            s.quickBets(QUICK_FIRST, ROW_QUICK_Y)
            // The mode switch sits on the quick-bet row, on the side of the screen the bar runs to.
            s.button(MODE_OVER, 352, ROW_QUICK_Y, 56, "Over", "Roll over", active = true)
            s.button(MODE_UNDER, 412, ROW_QUICK_Y, 56, "Under", "Roll under")

            s.text(TARGET_LABEL, 18, ROW_THIRD_Y, 70, ROW_H, WINDOW, fonts.p12, "Target", GREY, alignX = 0)
            s.text(TARGET_TEXT, 90, ROW_THIRD_Y, 60, ROW_H, WINDOW, fonts.b12, "50", WHITE, alignX = 0)
            s.button(TARGET_MINUS, 152, ROW_THIRD_Y, 30, "-", "Lower target")
            s.button(TARGET_PLUS, 186, ROW_THIRD_Y, 30, "+", "Raise target")
            s.button(TARGET_P25, 224, ROW_THIRD_Y, 40, "25", "Target 25")
            s.button(TARGET_P50, 268, ROW_THIRD_Y, 40, "50", "Target 50")
            s.button(TARGET_P75, 312, ROW_THIRD_Y, 40, "75", "Target 75")
            s.button(TARGET_CUSTOM, 356, ROW_THIRD_Y, 70, "Set", "Set target")

            // The track, then the two coloured halves of every segment on top of it.
            s.panel(BAR_PANEL, BAR_X - 6, BAR_Y - 6, SEGMENTS * SEGMENT_W + 12, BAR_H + 30)
            for (i in 0 until SEGMENTS) {
                val x = BAR_X + i * SEGMENT_W
                s.rect(loseSegment(i), x, BAR_Y, SEGMENT_W, BAR_H, WINDOW, BAR_LOSE)
                s.rect(winSegment(i), x, BAR_Y, SEGMENT_W, BAR_H, WINDOW, BAR_WIN, hidden = true)
                s.rect(marker(i), x + 1, BAR_Y + BAR_H + 2, SEGMENT_W - 2, 4, WINDOW, WHITE, hidden = true)
            }
            // Scale under the bar: 0 / 25 / 50 / 75 / 100, each centred on the point it marks.
            for (i in 0 until 5) {
                val value = i * 25
                s.text(SCALE_FIRST + i, BAR_X + value * 3 - 20, BAR_Y + BAR_H + 8, 40, 12, WINDOW, fonts.p11, "$value", GREY)
            }

            s.panel(STATS_PANEL, MARGIN, 190, INNER_W, 44)
            s.readout(CHANCE_LABEL, CHANCE_TEXT, 20, 196, 84, 90, "Win chance", "50.00%")
            s.readout(MULTIPLIER_LABEL, MULTIPLIER_TEXT, 242, 196, 76, 130, "Multiplier", "1.98x")
            s.readout(PAYOUT_LABEL, PAYOUT_TEXT, 20, 214, 84, 130, "Payout", "1,979", valueColour = GOLD)
            s.readout(PROFIT_LABEL, PROFIT_TEXT, 242, 214, 76, 130, "Profit", "979", valueColour = GREEN)

            // The roll readout, left of the Roll button, in three colours of which one is shown at a time.
            s.text(ROLL_LABEL, 18, 240, 70, ROW_H, WINDOW, fonts.p11, "Last roll", GREY, alignX = 0)
            s.text(ROLL_IDLE, 90, 240, 90, ROW_H, WINDOW, fonts.b12, "-", GREY, alignX = 0)
            s.text(ROLL_WIN, 90, 240, 90, ROW_H, WINDOW, fonts.b12, "", GREEN, alignX = 0, hidden = true)
            s.text(ROLL_LOSE, 90, 240, 90, ROW_H, WINDOW, fonts.b12, "", RED, alignX = 0, hidden = true)
            s.button(ROLL_BUTTON, 316, 240, 152, "Roll", "Roll", font = fonts.b12)

            s.text(OUTCOME_IDLE, MARGIN, 266, INNER_W, 18, WINDOW, fonts.p12, "Set your bet and roll.", GREY)
            s.text(OUTCOME_WIN, MARGIN, 266, INNER_W, 18, WINDOW, fonts.p12, "", GREEN, hidden = true)
            s.text(OUTCOME_LOSE, MARGIN, 266, INNER_W, 18, WINDOW, fonts.p12, "", RED, hidden = true)

            // Bottom row: kept inside the frame's corner sprites (x 36..444).
            s.text(HISTORY_LABEL, SAFE_LEFT, 288, 56, 18, WINDOW, fonts.p11, "Recent", GREY, alignX = 0)
            for (i in 0 until HISTORY_SLOTS) {
                val x = 96 + i * 50
                s.text(historyWin(i), x, 288, 46, 18, WINDOW, fonts.p11, "", GREEN, hidden = true)
                s.text(historyLose(i), x, 288, 46, 18, WINDOW, fonts.p11, "", RED, hidden = true)
            }
            s.button(FAIR_BUTTON, 350, 288, 96, "Provably fair", "Provably fair", font = fonts.p11)
            s.fairnessOverlay(FAIRNESS)
            return s
        }
    }

    // ================================================================= Mines

    /**
     * Mines.
     *
     * A 5x5 board on the left and the numbers that decide whether to cash out on the right, so the two things a
     * player looks at while a board is live - the next multiplier and the money on the table - never move.
     *
     * Each cell is a cover plate over an item slot: the server hides the cover to reveal whatever it put in the
     * slot underneath (an uncut emerald for a gem, a cannonball for a mine), which needs no art of its own.
     */
    object Mines {
        const val CELLS = 25
        const val COLUMNS = 5

        /** The mine-count presets, in the order the buttons read. */
        val PRESETS = listOf(1, 3, 5, 10, 24)

        private val seq = Seq()

        val BET_LABEL = seq.one()
        val BET_TEXT = seq.one()
        val BET_HALVE = seq.button()
        val BET_DOUBLE = seq.button()
        val BET_MAX = seq.button()
        val BET_CUSTOM = seq.button()

        val QUICK_FIRST = seq.many(BUTTON_STRIDE * QUICK_BETS)

        val MINES_LABEL = seq.one()
        val MINES_TEXT = seq.one()
        val MINES_MINUS = seq.button()
        val MINES_PLUS = seq.button()
        val PRESET_FIRST = seq.many(BUTTON_STRIDE * 5)

        val BOARD_PANEL = seq.panel()
        val GRID_FIRST = seq.many(CELLS * 2)

        val ODDS_PANEL = seq.panel()
        val MULTIPLIER_LABEL = seq.one()
        val MULTIPLIER_TEXT = seq.one()
        val NEXT_LABEL = seq.one()
        val NEXT_TEXT = seq.one()
        val GEMS_LABEL = seq.one()
        val GEMS_TEXT = seq.one()

        val VALUE_PANEL = seq.panel()
        val VALUE_LABEL = seq.one()
        val VALUE_TEXT = seq.one()

        val START_BUTTON = seq.button()
        val CASHOUT_BUTTON = seq.button()
        val RANDOM_BUTTON = seq.button()
        val FAIR_BUTTON = seq.button()
        val STATUS_TEXT = seq.one()
        val FAIRNESS = seq.fairness()

        /** Cell `i` occupies [GRID_FIRST] + i * 2 (the cover plate) and + 1 (the item slot under it). */
        fun cover(cell: Int) = GRID_FIRST + cell * 2

        fun slot(cell: Int) = GRID_FIRST + cell * 2 + 1

        fun quickBet(index: Int) = QUICK_FIRST + index * BUTTON_STRIDE

        fun preset(index: Int) = PRESET_FIRST + index * BUTTON_STRIDE

        /**
         * Board geometry: 30px cells on a 32px pitch from y 136, so the fifth row ends at y 296 and the status
         * line below it still clears the frame's bottom edge.
         */
        const val GRID_X = 18
        const val GRID_Y = 136
        const val CELL_PITCH = 32
        const val CELL_SIZE = 30

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(MINES_ID, "Mines", THEME_MINES, fonts)
            s.frame()
            s.header("Reveal gems, cash out before a mine - 98% return to player")

            s.betRow(BET_LABEL, BET_TEXT, BET_HALVE, BET_DOUBLE, BET_MAX, BET_CUSTOM, ROW_BET_Y)
            s.quickBets(QUICK_FIRST, ROW_QUICK_Y)

            s.text(MINES_LABEL, 18, ROW_THIRD_Y, 70, ROW_H, WINDOW, fonts.p12, "Mines", GREY, alignX = 0)
            s.text(MINES_TEXT, 90, ROW_THIRD_Y, 40, ROW_H, WINDOW, fonts.b12, "3", WHITE, alignX = 0)
            s.button(MINES_MINUS, 132, ROW_THIRD_Y, 30, "-", "Fewer mines")
            s.button(MINES_PLUS, 166, ROW_THIRD_Y, 30, "+", "More mines")
            PRESETS.forEachIndexed { index, mines ->
                s.button(preset(index), 204 + index * 44, ROW_THIRD_Y, 40, "$mines", "$mines mines")
            }

            s.panel(BOARD_PANEL, GRID_X - 6, GRID_Y - 6, COLUMNS * CELL_PITCH + 10, COLUMNS * CELL_PITCH + 10)
            for (cell in 0 until CELLS) {
                val x = GRID_X + (cell % COLUMNS) * CELL_PITCH
                val y = GRID_Y + (cell / COLUMNS) * CELL_PITCH
                s.item(slot(cell), x + 3, y + 3, WINDOW, size = 24)
                s.graphic(cover(cell), x, y, CELL_SIZE, CELL_SIZE, WINDOW, sprite(170), ops = listOf("Reveal"), opBase = "Tile ${cell + 1}")
            }

            s.panel(ODDS_PANEL, 190, GRID_Y - 6, 278, 64)
            s.readout(MULTIPLIER_LABEL, MULTIPLIER_TEXT, 198, GRID_Y, 90, 168, "Multiplier", "-", valueColour = WHITE, valueFont = fonts.b12)
            s.readout(NEXT_LABEL, NEXT_TEXT, 198, GRID_Y + 20, 90, 168, "Next gem", "-")
            s.readout(GEMS_LABEL, GEMS_TEXT, 198, GRID_Y + 40, 90, 168, "Gems found", "0")

            s.panel(VALUE_PANEL, 190, GRID_Y + 70, 278, 42)
            s.readout(
                VALUE_LABEL, VALUE_TEXT, 198, GRID_Y + 82, 90, 168, "Cash out", "-",
                valueColour = GOLD, valueFont = fonts.b12, h = 18,
            )

            s.button(START_BUTTON, 190, 254, 134, "Start", "Start", font = fonts.b12)
            s.button(CASHOUT_BUTTON, 334, 254, 134, "Cash out", "Cash out", hidden = true, font = fonts.b12)
            s.button(RANDOM_BUTTON, 190, 282, 134, "Pick random tile", "Pick random", font = fonts.p11)
            s.button(FAIR_BUTTON, 334, 282, 134, "Provably fair", "Provably fair", font = fonts.p11)
            s.text(STATUS_TEXT, SAFE_LEFT, 306, SAFE_RIGHT - SAFE_LEFT, 16, WINDOW, fonts.p12, "Pick your mines and start.", GREY)

            s.fairnessOverlay(FAIRNESS)
            return s
        }
    }

    // ================================================================= Blackjack

    /**
     * Blackjack.
     *
     * The cards sit on the left and the controls in a column on the right. Laid out top to bottom instead, four
     * split rows of cards reach past the bottom of the window, straight through the button row - which is what the
     * first draft did.
     *
     * A card is a layer with a plate, a border and two inked copies of its rank and suit ([Screen.card]); the
     * server shows the pair that matches. The suit is a letter (H/D/S/C) rather than a pip because the cache's
     * fonts have no suit glyphs, and the first draft's `v ^ + *` stand-ins were unreadable.
     */
    object Blackjack {
        const val DEALER_CARDS = 8
        const val PLAYER_ROWS = 4
        const val PLAYER_CARDS = 8

        private val seq = Seq()

        val BET_LABEL = seq.one()
        val BET_TEXT = seq.one()
        val BET_HALVE = seq.button()
        val BET_DOUBLE = seq.button()
        val BET_MAX = seq.button()
        val BET_CUSTOM = seq.button()

        val QUICK_FIRST = seq.many(BUTTON_STRIDE * QUICK_BETS)
        val REBET_BUTTON = seq.button()

        val DEALER_PANEL = seq.panel()
        val DEALER_LABEL = seq.one()
        val DEALER_TOTAL = seq.one()
        val DEALER_FIRST = seq.many(DEALER_CARDS * CARD_STRIDE)

        val PLAYER_PANEL = seq.panel()
        val PLAYER_FIRST = seq.many(PLAYER_ROWS * PLAYER_CARDS * CARD_STRIDE)
        val ROW_TOTAL_FIRST = seq.many(PLAYER_ROWS)

        val DEAL_BUTTON = seq.button()
        val HIT_BUTTON = seq.button()
        val STAND_BUTTON = seq.button()
        val DOUBLE_BUTTON = seq.button()
        val SPLIT_BUTTON = seq.button()
        val INSURE_BUTTON = seq.button()
        val DECLINE_BUTTON = seq.button()
        val FAIR_BUTTON = seq.button()

        val STATUS_PANEL = seq.panel()
        val STATUS_TEXT = seq.one()
        val FAIRNESS = seq.fairness()

        const val CARD_X = 18
        const val CARD_PITCH = 28
        const val CARD_W = 26
        const val CARD_H = 32
        const val DEALER_Y = 130
        const val PLAYER_Y = 176
        const val ROW_PITCH = 32

        /** The card layer of dealer card [index]; the rank/suit ids hang off it at [CARD_RANK_BLACK] and friends. */
        fun dealerCard(index: Int) = DEALER_FIRST + index * CARD_STRIDE

        fun playerCard(
            row: Int,
            index: Int,
        ) = PLAYER_FIRST + (row * PLAYER_CARDS + index) * CARD_STRIDE

        fun rowTotal(row: Int) = ROW_TOTAL_FIRST + row

        fun quickBet(index: Int) = QUICK_FIRST + index * BUTTON_STRIDE

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(BLACKJACK_ID, "Blackjack", THEME_BLACKJACK, fonts)
            s.frame()
            s.header("Blackjack pays 3:2 - dealer stands on 17")

            s.betRow(BET_LABEL, BET_TEXT, BET_HALVE, BET_DOUBLE, BET_MAX, BET_CUSTOM, ROW_BET_Y)
            s.quickBets(QUICK_FIRST, ROW_QUICK_Y)
            s.button(REBET_BUTTON, 352, ROW_QUICK_Y, 116, "Rebet", "Rebet")

            s.panel(DEALER_PANEL, MARGIN, ROW_THIRD_Y, 282, 54)
            s.text(DEALER_LABEL, 18, ROW_THIRD_Y + 4, 52, 14, WINDOW, fonts.p11, "Dealer", GREY, alignX = 0)
            s.text(DEALER_TOTAL, 74, ROW_THIRD_Y + 4, 100, 14, WINDOW, fonts.b12, "", WHITE, alignX = 0)
            for (i in 0 until DEALER_CARDS) {
                s.card(dealerCard(i), CARD_X + i * CARD_PITCH, DEALER_Y, CARD_W, CARD_H)
            }

            s.panel(PLAYER_PANEL, MARGIN, PLAYER_Y - 4, 282, PLAYER_ROWS * ROW_PITCH + 10)
            for (row in 0 until PLAYER_ROWS) {
                val y = PLAYER_Y + row * ROW_PITCH
                for (i in 0 until PLAYER_CARDS) {
                    s.card(playerCard(row, i), CARD_X + i * CARD_PITCH, y, CARD_W, CARD_H - 2)
                }
            }
            // The totals are allocated after every card so the card block stays one contiguous run.
            for (row in 0 until PLAYER_ROWS) {
                s.text(rowTotal(row), 250, PLAYER_Y + row * ROW_PITCH + 8, 42, 14, WINDOW, fonts.p11, "", WHITE, alignX = 0, hidden = true)
            }

            // Controls in their own column, clear of the four possible split rows.
            s.button(DEAL_BUTTON, 302, ROW_THIRD_Y, 80, "Deal", "Deal", font = fonts.b12)
            s.button(HIT_BUTTON, 388, ROW_THIRD_Y, 80, "Hit", "Hit", hidden = true, font = fonts.b12)
            s.button(STAND_BUTTON, 302, ROW_THIRD_Y + 28, 80, "Stand", "Stand", hidden = true)
            s.button(DOUBLE_BUTTON, 388, ROW_THIRD_Y + 28, 80, "Double", "Double", hidden = true)
            s.button(SPLIT_BUTTON, 302, ROW_THIRD_Y + 56, 80, "Split", "Split", hidden = true)
            s.button(INSURE_BUTTON, 388, ROW_THIRD_Y + 56, 80, "Insure", "Insurance", hidden = true)
            s.button(DECLINE_BUTTON, 302, ROW_THIRD_Y + 84, 80, "Decline", "Decline", hidden = true)
            s.button(FAIR_BUTTON, 388, ROW_THIRD_Y + 84, 80, "Fair", "Provably fair", font = fonts.p11)

            s.panel(STATUS_PANEL, 302, 230, 166, 80)
            s.text(STATUS_TEXT, 308, 236, 154, 68, WINDOW, fonts.p12, "Place your bet and deal.", GREY, lineHeight = 14)

            s.fairnessOverlay(FAIRNESS)
            return s
        }
    }

    // ================================================================= Flower Poker

    /**
     * Flower poker, the one player-versus-player game here: both sides plant five flowers and the better hand wins
     * the pot. The viewing player is always drawn on the top row, whichever side of the match they are.
     */
    object Flower {
        const val HAND_SIZE = 5

        private val seq = Seq()

        val CHALLENGER_PANEL = seq.panel()
        val CHALLENGER_LABEL = seq.one()
        val CHALLENGER_NAME = seq.one()
        val CHALLENGER_TICK = seq.one()
        val CHALLENGER_FIRST = seq.many(HAND_SIZE)
        val CHALLENGER_HAND = seq.one()

        val OPPONENT_PANEL = seq.panel()
        val OPPONENT_LABEL = seq.one()
        val OPPONENT_NAME = seq.one()
        val OPPONENT_TICK = seq.one()
        val OPPONENT_FIRST = seq.many(HAND_SIZE)
        val OPPONENT_HAND = seq.one()

        val STAKE_PANEL = seq.panel()
        val POT_LABEL = seq.one()
        val POT_TEXT = seq.one()
        val STAKE_LABEL = seq.one()
        val STAKE_TEXT = seq.one()
        val ROUND_LABEL = seq.one()
        val ROUND_TEXT = seq.one()

        val STAKE_BUTTON = seq.button()
        val ACCEPT_BUTTON = seq.button()
        val DECLINE_BUTTON = seq.button()
        val FAIR_BUTTON = seq.button()

        val STATUS_TEXT = seq.one()
        val FAIRNESS = seq.fairness()

        fun challengerFlower(index: Int) = CHALLENGER_FIRST + index

        fun opponentFlower(index: Int) = OPPONENT_FIRST + index

        fun screen(fonts: LootKeyInterfaceImportTool.Fonts): Screen {
            val s = Screen(FLOWER_ID, "Flower Poker", THEME_FLOWER, fonts)
            s.frame()
            s.header("Five flowers each - the better hand takes the pot")

            s.panel(CHALLENGER_PANEL, MARGIN, 64, INNER_W, 72)
            s.text(CHALLENGER_LABEL, 20, 68, 40, 16, WINDOW, fonts.p11, "You", GREY, alignX = 0)
            s.text(CHALLENGER_NAME, 62, 68, 180, 16, WINDOW, fonts.p12, "", WHITE, alignX = 0)
            s.text(CHALLENGER_TICK, 248, 68, 120, 16, WINDOW, fonts.p11, "", GREEN, alignX = 0)
            for (i in 0 until HAND_SIZE) {
                s.item(challengerFlower(i), 24 + i * 40, 90, WINDOW, size = 32)
            }
            s.text(CHALLENGER_HAND, 236, 96, 226, 20, WINDOW, fonts.b12, "", GOLD, alignX = 0)

            s.panel(OPPONENT_PANEL, MARGIN, 144, INNER_W, 72)
            s.text(OPPONENT_LABEL, 20, 148, 62, 16, WINDOW, fonts.p11, "Opponent", GREY, alignX = 0)
            s.text(OPPONENT_NAME, 84, 148, 158, 16, WINDOW, fonts.p12, "", WHITE, alignX = 0)
            s.text(OPPONENT_TICK, 248, 148, 120, 16, WINDOW, fonts.p11, "", GREEN, alignX = 0)
            for (i in 0 until HAND_SIZE) {
                s.item(opponentFlower(i), 24 + i * 40, 170, WINDOW, size = 32)
            }
            s.text(OPPONENT_HAND, 236, 176, 226, 20, WINDOW, fonts.b12, "", GOLD, alignX = 0)

            s.panel(STAKE_PANEL, MARGIN, 224, INNER_W, 44)
            s.readout(POT_LABEL, POT_TEXT, 20, 230, 40, 150, "Pot", "0", valueColour = GOLD, valueFont = fonts.b12)
            s.readout(STAKE_LABEL, STAKE_TEXT, 226, 230, 52, 150, "Stake", "0")
            s.readout(ROUND_LABEL, ROUND_TEXT, 20, 248, 40, 150, "Plant", "-")

            s.button(STAKE_BUTTON, SAFE_LEFT, 276, 104, "Set stake", "Set stake")
            s.button(ACCEPT_BUTTON, 146, 276, 92, "Accept", "Accept")
            s.button(DECLINE_BUTTON, 244, 276, 92, "Decline", "Decline")
            s.button(FAIR_BUTTON, 342, 276, 102, "Provably fair", "Provably fair", font = fonts.p11)
            s.text(STATUS_TEXT, SAFE_LEFT, 304, SAFE_RIGHT - SAFE_LEFT, 16, WINDOW, fonts.p12, "Agree a stake and both accept.", GREY)

            s.fairnessOverlay(FAIRNESS)
            return s
        }
    }

    // =================================================================

    fun screens(fonts: LootKeyInterfaceImportTool.Fonts): List<Screen> =
        listOf(Dice.screen(fonts), Mines.screen(fonts), Blackjack.screen(fonts), Flower.screen(fonts))

    /**
     * Read-only: prints the highest interface group already in the target cache and the first free run of four.
     *
     * The loot-key tool's "first id above the cache's last interface (1148)" was true when it was written; the
     * cache has grown since, and a stale assumption there is what the preflight caught on 2026-09-20 (1150 and
     * 1151 were already taken). Probing beats assuming.
     */
    private fun probe() {
        val library = com.displee.cache.CacheLibrary(TARGETS[0])
        try {
            val index = library.index(INDEX_INTERFACES)
            val ids = index.archiveIds().toSortedSet()
            println("INTERFACE_INDEX archives=${ids.size} highest=${ids.lastOrNull()}")
            var candidate = (ids.lastOrNull() ?: 0) + 1
            while ((candidate until candidate + 4).any { it in ids }) {
                candidate++
            }
            println("FIRST_FREE_RUN_OF_FOUR=$candidate")
            println("TAKEN_NEAR_1149=${(1145..1175).filter { it in ids }}")
        } finally {
            library.close()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if ("--probe" in args) {
            probe()
            return
        }
        val apply = "--apply" in args

        /*
         * `--replace-from-journal=<tx id>`: re-import over a run this tool already applied.
         *
         * Without it the second apply is refused, and rightly so - the interface files already exist, so every
         * mutation preflights as a CONFLICT rather than a CREATE, which is the guard that stops one tool silently
         * overwriting another's work. Pinning the sha1s this tool itself recorded in its journal says "replace
         * exactly what I wrote last time": a layout revision goes through, while anything else that has touched
         * those files still blocks.
         *
         * The first apply of these four screens was tx-20260920-012546; this rewrite replaces it.
         */
        val previous =
            args.firstOrNull { it.startsWith("--replace-from-journal=") }
                ?.substringAfter('=')
                ?.let(LootKeyInterfaceImportTool::journalIntendedSha1s)
                ?: emptyMap()

        val fonts =
            com.displee.cache.CacheLibrary(TARGETS[0]).let { library ->
                try {
                    LootKeyInterfaceImportTool.fonts(library)
                } finally {
                    library.close()
                }
            }
        println("FONTS p11=${fonts.p11} p12=${fonts.p12} b12=${fonts.b12} q8=${fonts.q8}")

        val mutations = mutableListOf<CacheMutation>()
        screens(fonts).forEach { screen ->
            val components = screen.build()
            println("SCREEN ${screen.id} '${screen.title}' components=${components.size}")
            components.forEach { c ->
                mutations +=
                    CacheMutation(
                        INDEX_INTERFACES,
                        screen.id,
                        c.id,
                        LootKeyInterfaceImportTool.encode(c),
                        "interface ${screen.id}:${c.id} type=${c.type}",
                        expectedCurrentSha1 = previous["idx${INDEX_INTERFACES}_grp${screen.id}_file${c.id}"],
                    )
            }
        }

        val transaction = CacheTransaction(targets = TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write interfaces $DICE_ID, $MINES_ID, $BLACKJACK_ID and $FLOWER_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        if (problems.isEmpty()) {
            println("VERIFY_OK transaction=${transaction.id}")
        } else {
            problems.forEach { println("  VERIFY_PROBLEM: $it") }
            error("verify failed")
        }
        File("C:/RSPS/import-journal/${transaction.id}/casino-interfaces.txt")
            .writeText(screens(fonts).joinToString("\n") { "${it.id}\t${it.title}\t${it.build().size} components" })
    }
}
