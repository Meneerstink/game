package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.focusTab
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentSprite

/**
 * The Follower Details entry point, moved off the Summoning orb and into the sidebar tab strip
 * (owner requirement H1).
 *
 * ## Why it has to live here rather than as a menu entry
 *
 * A component's op **labels** are baked into the cache and there is no server packet in this
 * revision that can change them: `ServerConnectionReader` has branches that rewrite a component's
 * `events` mask and its target parameter, and nothing else. `IF_SETEVENTS` can therefore *enable*
 * an op but cannot *name* one, so "Follower Details" could not simply be added as a new right-click
 * entry on some existing component.
 *
 * What the cache does provide is a genuinely empty tab slot. The fixed gameframe's second tab row
 * (548:81) holds Friends List, Friends Chat, Clan Chat, Options, Emotes, Music Player and Notes at
 * x = 33, 63, 93, 123, 153, 183, 213 - and one further button, **548:99**, at x = 3, with every op
 * label blank and only op10 baked. That is the spare tab, the one `disasm 8` maps to gameframe
 * slot 8 and that Stealing Creation and the tutorial borrow. It sits in the row directly beneath
 * the one containing the Skills ("Stats") tab, which is the empty area the owner asked for.
 *
 * Its icon is the sibling graphic at the matching x offset - 548:107, baked with sprite 1825 - and
 * the resizable pane repeats the pair exactly: button **746:47** at x = 210 with icon **746:31**,
 * also sprite 1825.
 *
 * Left-clicking a tab does not need a label: the blank op1 still delivers `IF_BUTTON1` to the
 * server. Only a right-click menu would show the empty string, and a tab is not right-clicked.
 *
 * ## What is authentic here and what is not
 *
 * The components, their ids and the slot-8 mapping are all read from this cache. The **decision**
 * to put Follower Details on that slot is the owner's custom design, not 2011 behaviour - real
 * 2011 reaches the panel from the orb, which is exactly what H1 removes. The icon sprite is
 * likewise a presentation choice: [ICON_SPRITE] is the cache's own Summoning skill icon (the one
 * interface 320 draws on the Summoning skill button), chosen rather than invented, because no
 * authentic sprite exists for a tab that never existed.
 */
object FollowerDetailsTab {
    /** Fixed gameframe: the spare tab button and its icon. */
    private const val FIXED_PANE = 548
    private const val FIXED_BUTTON = 99
    private const val FIXED_ICON = 107

    /** Resizable gameframe: the same pair. */
    private const val RESIZABLE_PANE = 746
    private const val RESIZABLE_BUTTON = 47
    private const val RESIZABLE_ICON = 31

    /**
     * The cache's own Summoning skill icon, as baked on interface 320's Summoning skill button
     * (`320:153 sprite=3028`).
     */
    private const val ICON_SPRITE = 3028

    /**
     * `events` mask enabling op1 only. Bit 0 is the pause-button flag and op *i* is bit *i + 1*,
     * per `ServerActiveProperties.isOpEnabled` - so op1 is bit 1, i.e. 0x2. This is the same value
     * the cache bakes on every other tab button in the strip (`events=0x000002` on 548:129..136).
     */
    private const val OP1_ONLY = 0x2

    /** Every (pane, button, icon) triple, so both layout modes are armed together. */
    private val SURFACES =
        listOf(
            Triple(FIXED_PANE, FIXED_BUTTON, FIXED_ICON),
            Triple(RESIZABLE_PANE, RESIZABLE_BUTTON, RESIZABLE_ICON),
        )

    /** The (pane, button) pairs, for `familiar.plugin.kts` to bind its click handlers to. */
    val buttons: List<Pair<Int, Int>> = SURFACES.map { it.first to it.second }

    /**
     * Arms the tab in both layout modes. Called on login, after the gameframe has been built, for
     * the same reason the rest of the Summoning gating is: every component an interface rebuilds
     * comes back with its baked flags, and the baked flags here are "no op, icon hidden".
     */
    fun install(player: Player) {
        SURFACES.forEach { (pane, button, icon) ->
            player.setEvents(interfaceId = pane, component = button, from = -1, to = -1, setting = OP1_ONLY)
            player.setComponentHidden(pane, button, false)
            player.setComponentHidden(pane, icon, false)
            player.setComponentSprite(pane, icon, ICON_SPRITE)
        }
    }

    /**
     * Opens the Follower Details panel. It is permanently mounted on gameframe slot 95
     * ([Tabs.SUMMONING]); switching to it is a pure client-side tab focus, the same operation the
     * orb's own `onOp` script 2457 performed before H1 removed that entry.
     */
    fun open(player: Player) {
        player.focusTab(Tabs.SUMMONING)
    }
}
