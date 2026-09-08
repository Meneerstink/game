/**
 * Gets the 'interface hash' of a given interface id and
 * child component. This value is commonly used in ClientScripts when referring
 * to a child component in the game. An 'interface hash' is in the format of (parent >> 16) | child
 *
 * Example: 335.getInterfaceHash(25) would return 21954585, which is the 'absolute' id of the component
 *
 * @param child     The child component
 */
fun Int.getInterfaceHash(child: Int = -1): Int {
    val value = (this shl 16)
    if (child != -1) return value or child
    return value
}

object Tabs {
    const val COMBAT_STYLES = 0
    const val TASK_LIST = 1
    const val SKILLS = 2
    const val QUESTS = 3
    const val INVENTORY = 4
    const val EQUIPMENT = 5
    const val PRAYER = 6
    const val SPELLBOOK = 7
    /** The spare minigame tab ("Production" in Stealing Creation, "Microtutorial" in the tutorial). */
    const val BLANK_TAB = 8
    const val FRIENDS_IGNORE = 9
    const val FRIENDS_CHAT = 10
    const val CLAN_CHAT = 11
    const val SETTINGS = 12
    const val EMOTES = 13
    const val MUSIC = 14
    const val NOTES = 15

    /**
     * The Summoning "Follower Details" panel. Not a numbered sidebar tab: `disasm 8` in this
     * cache maps the gameframe slots and puts it on slot **95** (pane 548:221 fixed / 746:107
     * resizable), which has no tab button, and `disasm 1387` - the panel switcher reached through
     * client script 115 -> 71 - has a real case for 95 alongside 0..15, 98 and 99 (Logout).
     * See [gg.rsmod.plugins.api.InterfaceDestination.SUMMONING_TAB] for the full evidence.
     */
    const val SUMMONING = 95
}
