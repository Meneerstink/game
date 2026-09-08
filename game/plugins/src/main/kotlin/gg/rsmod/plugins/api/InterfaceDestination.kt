package gg.rsmod.plugins.api

import gg.rsmod.game.model.interf.DisplayMode

enum class InterfaceDestination(
    val interfaceId: Int,
    val fixedChildId: Int,
    val resizeChildId: Int,
    val clickThrough: Boolean = true,
) {
    CHATBOX_TABS(interfaceId = 751, fixedChildId = 68, resizeChildId = 19),
    CHAT_BOX_PANE(interfaceId = 752, fixedChildId = 192, resizeChildId = 73),

    ATTACK_TAB(interfaceId = 884, fixedChildId = 204, resizeChildId = 90),
    ACHIEVEMENTS_TAB(interfaceId = 1056, fixedChildId = 205, resizeChildId = 91),
    SKILLS_TAB(interfaceId = 320, fixedChildId = 206, resizeChildId = 92),
    QUEST_TAB(interfaceId = 190, fixedChildId = 207, resizeChildId = 93),
    INVENTORY_TAB(interfaceId = 679, fixedChildId = 208, resizeChildId = 94),
    EQUIPMENT_TAB(interfaceId = 387, fixedChildId = 209, resizeChildId = 95),
    PRAYER_TAB(interfaceId = 271, fixedChildId = 210, resizeChildId = 96),
    MAGIC_TAB(interfaceId = 192, fixedChildId = 211, resizeChildId = 97),

    /*
     * The Summoning "Follower Details" panel (interface 662) is NOT one of the sixteen numbered
     * sidebar tabs. It is gameframe *slot 95*, a panel with no tab button of its own, opened on
     * demand - exactly as the 2011 Knowledge Base describes it ("The Summoning interface can be
     * opened by selecting 'Follower details'"). Decoded from this cache on 2026-09-06:
     *
     *  - `disasm 8` (slot -> gameframe pane) maps slot 95 to 548:221 (fixed) / 746:107 (resizable).
     *    Slots 0..15 are the numbered tabs (548:204..219), 99 is Logout (548:222).
     *  - `disasm 2457`, the real `onOp` of 747:9 / 747:18 ("Follower Details"), returns
     *    immediately unless `IF_HASSUB(GOSUB(8, 95))` - i.e. unless a sub-interface is mounted
     *    here. Nothing else on the orb can open the panel.
     *  - `disasm 1364` (the gameframe rebuild) shows the orb's whole familiar-option layer
     *    747:8 only when that same `IF_HASSUB(GOSUB(8, 95))` holds *and* varbit 4280 is set.
     *
     * Slot 8 (548:212 / 746:98), where this used to be mounted, is the spare minigame tab:
     * its button 548:99 / 746:47 carries no baked op1 and its icon 548:107 is baked hidden,
     * and `disasm 1766` relabels it "Production" / "Microtutorial" for Stealing Creation and the
     * tutorial. Mounting 662 there produced a dead, iconless slot and left the orb's right-click
     * menu empty - the live symptom the owner reported.
     */
    SUMMONING_TAB(interfaceId = 662, fixedChildId = 221, resizeChildId = 107),
    FRIENDS_TAB(interfaceId = 550, fixedChildId = 213, resizeChildId = 99),
    FRIEND_CHAT_TAB(interfaceId = 1109, fixedChildId = 214, resizeChildId = 100),
    CLAN_CHAT_TAB(interfaceId = 1110, fixedChildId = 215, resizeChildId = 101),
    SETTINGS_TAB(interfaceId = 261, fixedChildId = 216, resizeChildId = 102),
    EMOTES_TAB(interfaceId = 464, fixedChildId = 217, resizeChildId = 103),
    MUSIC_TAB(interfaceId = 187, fixedChildId = 218, resizeChildId = 104),
    NOTES_TAB(interfaceId = 34, fixedChildId = 219, resizeChildId = 105),
    LOGOUT_TAB(interfaceId = 182, fixedChildId = 222, resizeChildId = 108),

    HP_ORB(interfaceId = 748, fixedChildId = 183, resizeChildId = 177),
    PRAYER_ORB(interfaceId = 749, fixedChildId = 185, resizeChildId = 178),
    ENERGY_ORB(interfaceId = 750, fixedChildId = 186, resizeChildId = 179),
    SUMMONING_ORB(interfaceId = 747, fixedChildId = 188, resizeChildId = 180),
    SPLIT_PM(interfaceId = 754, fixedChildId = 17, resizeChildId = 72),

    MULTI_ICON(interfaceId = 745, fixedChildId = 15, resizeChildId = 15),

    MAIN_SCREEN(
        interfaceId = -1,
        fixedChildId = 9,
        resizeChildId = 12,
        clickThrough = false,
    ),

    MAIN_SCREEN_OVERLAY(
        interfaceId = -1,
        fixedChildId = 8,
        resizeChildId = 9,
        clickThrough = true,
    ),

    // Note: this is used for interfaces such as the skill menu where it has a
    // background that should fill the entire game screen.
    MAIN_SCREEN_FULL(
        interfaceId = -1,
        fixedChildId = 9,
        resizeChildId = 11,
        clickThrough = false,
    ),

    TAB_AREA(interfaceId = -1, fixedChildId = 199, resizeChildId = 87),

    PVP_OVERLAY(interfaceId = -1, fixedChildId = 19, resizeChildId = 10, clickThrough = true),

    ;

    companion object {
        val values = enumValues<InterfaceDestination>()
    }
}

fun getDisplayComponentId(displayMode: DisplayMode) =
    when (displayMode) {
        DisplayMode.FIXED -> 548
        DisplayMode.RESIZABLE_NORMAL -> 746
        else -> throw RuntimeException("Unhandled display mode.")
    }

fun getChildId(
    pane: InterfaceDestination,
    displayMode: DisplayMode,
): Int =
    when (displayMode) {
        DisplayMode.FIXED -> pane.fixedChildId
        DisplayMode.RESIZABLE_NORMAL -> pane.resizeChildId
        else -> throw RuntimeException("Unhandled display mode.")
    }
