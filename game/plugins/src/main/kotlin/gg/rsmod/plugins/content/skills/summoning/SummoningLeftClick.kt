package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.syncVarp

/**
 * "Select left-click option" - the real interface-880 mechanism, re-derived from this cache on
 * 2026-09-06 and recorded in `C:\RSPS\RSPS_SUMMONING_2011_EVIDENCE.md`.
 *
 * How the authentic flow actually works:
 *
 *  1. 747:7 carries the real op `op10 = 'Select left-click option'` and opens 880.
 *  2. 880's eight rows each carry the real op `op1 = 'Select'` as a plain `IF_BUTTON1`, i.e. the
 *     click goes to the *server*; there is no `onOp` script on them. The server answers by writing
 *     880's preview varbit 6455 (varp 1494), which 880:3's `onVarTransmit` picks up
 *     (`varpTriggers=[1494]` -> script 2672 -> `GOSUB(2674)`). `disasm 2674` paints sprite 2035 on
 *     the selected row and 2034 on the other seven, so the highlight is drawn by the interface
 *     itself. Nothing is committed yet.
 *  3. 880:21 carries the real op `op1 = 'Confirm Selection'`. That is genuinely part of the 2011
 *     interface - the two-step select-then-confirm flow is authentic; only the previous
 *     implementation's chatbox narration around it ("Highlighted: X. Click Confirm Selection to
 *     apply it.") was invented, and it is gone. Confirming writes the real varbit 6454 (varp 1493).
 *  4. 747:7's own `onVarTransmit` (`varpTriggers=[1493]`) then runs script 751, whose tail is
 *     `GOSUB_WITH_PARAMS(2671)`. `disasm 2671` is the real switch: per case it shows the chosen
 *     action's direct left-click component on 747 and hides that action's twin in the orb's
 *     right-click submenu, and in the *else* branch of the very same case it does the opposite -
 *     hides the direct component and **shows** the submenu twin.
 *
 * Two concrete defects in the previous implementation, both fixed here:
 *
 *  - The varbit values were off by one. Decoding 2671's branch targets against 747's real op
 *    labels proves value **0** is Follower Details (`BRANCH_IF_FALSE` -> shows 48955410 = 747:18,
 *    whose op1 is 'Follower Details'), not 1; value 1 has no case at all and is Special move.
 *    The old table started Follower Details at 1 and treated Special move as "no value", so
 *    picking Follower Details configured a value the client draws nothing for, and picking
 *    Special move configured Follower Details.
 *  - It also replayed 2671's `IF_SETHIDE` pairs server-side with the same hidden flag for the
 *    direct component and its submenu twin. That is the opposite of what 2671 does to the twin,
 *    so it hid every unselected action from the orb's right-click menu - leaving the menu with
 *    only the one option that was already on left-click. The server now writes the varp and
 *    nothing else; the client's own script does the redraw, which is both authentic and the only
 *    way to keep the two halves consistent.
 */
enum class LeftClickAction(
    /** The real varbit-6454 value for this action. `null` for Special move, which script 2671 has
     *  no case for: selecting it hides all seven direct components and leaves the orb's own
     *  permanently-separate "Spell, Cast" button (747:24/25) as the only left-click target. */
    val varbitValue: Int?,
    /** The paired (graphic, text) "Select" row component ids on interface 880, proved by
     *  `disasm 2674`'s argument -> component mapping. */
    val selectRow: Pair<Int, Int>,
    val label: String,
) {
    FOLLOWER_DETAILS(0, 7 to 8, "Follower Details"),
    SPECIAL_MOVE(1, 9 to 10, "Special move"),
    ATTACK(2, 11 to 12, "Attack"),
    CALL(3, 13 to 14, "Call follower"),
    DISMISS(4, 15 to 16, "Dismiss follower"),
    TAKE_BOB(5, 17 to 18, "Take BoB"),
    RENEW(6, 19 to 20, "Renew familiar"),
    INTERACT(7, 25 to 26, "Interact"),
    ;

    companion object {
        /** 880's eight rows, in the same top-to-bottom order as the interface itself. */
        val ORDERED = arrayOf(FOLLOWER_DETAILS, SPECIAL_MOVE, ATTACK, CALL, DISMISS, TAKE_BOB, RENEW, INTERACT)

        fun byVarbitValue(value: Int): LeftClickAction? = ORDERED.firstOrNull { it.varbitValue == value }
    }
}

/** varbit 6454 (varp 1493 bits 0..3) - the persistent "configured left-click action". */
private const val ACTIVE_ACTION_VARBIT = 6454

/** The varp varbit 6454 lives in; 747:7 listens for it (`varpTriggers=[1493]`). */
private const val LEFT_CLICK_VARP = 1493

/** varbit 6455 (varp 1494 bits 0..3) - interface 880's own preview, live while the dialog is open. */
private const val PREVIEW_ACTION_VARBIT = 6455

/**
 * The configured action. A never-configured account reads 0, which is authentically Follower
 * Details - the same thing the client draws for a fresh account, so no invented default is
 * needed and none is applied.
 */
fun Player.leftClickAction(): LeftClickAction =
    LeftClickAction.byVarbitValue(getVarbit(ACTIVE_ACTION_VARBIT)) ?: LeftClickAction.FOLLOWER_DETAILS

/** Live-previews a row selection on 880 (step 2 above). */
fun Player.setPendingLeftClickAction(action: LeftClickAction) {
    setVarbit(PREVIEW_ACTION_VARBIT, action.varbitValue ?: 0)
}

fun Player.pendingLeftClickAction(): LeftClickAction =
    LeftClickAction.byVarbitValue(getVarbit(PREVIEW_ACTION_VARBIT)) ?: leftClickAction()

/**
 * Commits the previewed selection (step 3 above). Writing the varp is the entire server side of
 * this: script 751 -> 2671 performs the redraw on the client, exactly as it does for a real 2011
 * server. There is deliberately no chat feedback - the real interface gives none.
 */
fun Player.confirmLeftClickAction() {
    val action = pendingLeftClickAction()
    setVarbit(ACTIVE_ACTION_VARBIT, action.varbitValue ?: 1)
    // Script 2671 shows every op6 twin it is not making the left-click, including the ones this
    // familiar has no handler for, so the per-familiar gating has to be re-applied on top of it.
    SummoningUi.invalidate(this)
}

/**
 * Re-arms the client's own redraw. The `IF_SETHIDE` state script 2671 applies is live client
 * state, not persisted state, so after a fresh login (or any other point at which 747 is rebuilt)
 * varp 1493 has to be re-transmitted for 747:7's `onVarTransmit` to fire script 751 again.
 * [gg.rsmod.plugins.api.ext.syncVarp] rewrites the current value, which
 * [gg.rsmod.game.model.varp.VarpSet.setState] always marks dirty, so the transmit happens even
 * though the value is unchanged.
 */
fun Player.applyLeftClickAction() {
    syncVarp(LEFT_CLICK_VARP)
    SummoningUi.invalidate(this)
}
