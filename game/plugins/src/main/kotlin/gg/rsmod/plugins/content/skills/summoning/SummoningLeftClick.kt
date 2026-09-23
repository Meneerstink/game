package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.syncVarp

/**
 * "Select left-click option" - which of the orb's actions a plain left-click performs.
 *
 * ## How the cache's own mechanism works
 *
 * Re-derived from this cache on 2026-09-06 and recorded in
 * `C:\RSPS\RSPS_SUMMONING_2011_EVIDENCE.md`:
 *
 *  1. 747:7 carries the real op `op10 = 'Select left-click option'`, an `IF_BUTTON10` with no
 *     `onOp` script, so the click is delivered to the *server* and the server decides what to show.
 *  2. Whatever asked the question, the answer is stored in the real varbit 6454 (varp 1493).
 *  3. 747:7's own `onVarTransmit` (`varpTriggers=[1493]`) then runs script 751, whose tail is
 *     `GOSUB_WITH_PARAMS(2671)`. `disasm 2671` shows the chosen action's direct left-click
 *     component on 747 and hides that action's twin in the right-click submenu, and does the
 *     opposite for every action it is not making the left-click.
 *
 * So the varbit *is* the mechanism: writing 6454 is the entire server side of configuring the
 * orb, and the client's own script performs the redraw exactly as it would for a real 2011 server.
 *
 * ## Why the cache's own selector interface is not used
 *
 * The cache also bakes interface **880** for step 1 - eight `op1='Select'` rows and a
 * `Confirm Selection` button - and 747:7 opened it on a real 2011 server. It is not used here.
 * The owner rejected it twice by hand ("the fake custom radio-button GUI ... must be REMOVED
 * completely"), and the reason it cannot be salvaged is structural rather than cosmetic: its
 * eight rows are **baked**, two of them are "Follower details" and "Interact" - which requirement
 * G4 forbids outright - and the rest are shown whether or not the current familiar has the
 * capability. A server can refuse the click afterwards but cannot remove a row, so the interface
 * permanently advertises options that do not exist.
 *
 * The question is asked through the game's own "Select an Option" chatbox dialogue instead (see
 * `familiar.plugin.kts`), built from [selectableActions]. Nothing about the stored value changes:
 * the varbit, its values and the client redraw are all still the cache's own.
 *
 * ## What the owner's retests changed
 *
 * The orb exposes six actions - Special Move, Follower Details, Call, Dismiss, Take BoB and
 * Renew - while generic Attack is kept off the orb. [selectableActions] further restricts the list
 * to the actions the **currently summoned**
 * familiar can actually perform, which is why it takes a player rather than being a constant.
 *
 * A never-configured account reads varbit 6454 = 0, which is the cache's Follower Details value
 * and remains valid. Only the removed Interact value is migrated to [DEFAULT_ACTION].
 */

/** varbit 6454 (varp 1493 bits 0..3) - the persistent "configured left-click action". */
private const val ACTIVE_ACTION_VARBIT = 6454

/** The varp varbit 6454 lives in; 747:7 listens for it (`varpTriggers=[1493]`). */
private const val LEFT_CLICK_VARP = 1493

/**
 * The cache value removed from this orb is 7 = Interact. Follower Details (0) remains an orb
 * entrypoint and is also available from the custom skill-tab button.
 */
private val REMOVED_ACTION_VALUES = setOf(7)

/**
 * What a left-click does when the stored choice is one of the removed actions. Call Follower is
 * the least destructive of the six that remain: it has no cost, no confirmation and no target.
 */
private val DEFAULT_ACTION = FamiliarAction.CALL

/** The configured action, with the two removed values folded onto [DEFAULT_ACTION]. */
fun Player.leftClickAction(): FamiliarAction =
    FamiliarAction.byLeftClickValue(getVarbit(ACTIVE_ACTION_VARBIT)) ?: DEFAULT_ACTION

/**
 * Rewrites a stored choice that is no longer an orb action. Run once on login so the client's own
 * script 2671 and the server agree about which twin should be visible.
 */
fun Player.migrateRemovedDefault() {
    if (getVarbit(ACTIVE_ACTION_VARBIT) in REMOVED_ACTION_VALUES) {
        setVarbit(ACTIVE_ACTION_VARBIT, DEFAULT_ACTION.leftClickValue)
    }
}

/**
 * The rows "Select left-click option" should offer right now: the six allowed actions, filtered to
 * the ones the summoned familiar supports (H7). With no familiar out, all six are offered - the
 * choice is a persistent preference and configuring it without a familiar is legitimate.
 */
fun Player.selectableActions(): List<FamiliarAction> {
    val capabilities = FamiliarCapabilityTable.active(this) ?: return FamiliarAction.ORDERED
    return FamiliarAction.ORDERED.filter { capabilities.supports(it) }
}

/**
 * Stores the chosen action. Writing the varbit is the entire server side of this: script
 * 751 -> 2671 performs the redraw on the client, exactly as it does for a real 2011 server.
 */
fun Player.setLeftClickAction(action: FamiliarAction) {
    setVarbit(ACTIVE_ACTION_VARBIT, action.leftClickValue)
    // Script 2671 shows every op6 twin it is not making the left-click, including the ones this
    // familiar has no handler for, so the per-familiar gating has to be re-applied on top of it.
    SummoningUi.invalidate(this)
}

/**
 * Drops a stored choice the *current* familiar cannot perform, falling back to [DEFAULT_ACTION].
 *
 * Requirement G4: "stale selection must reset safely when familiar changes". Without this, a
 * player who set the orb's left-click to Take BoB with a Pack yak out, then summoned a Steel
 * titan, would be left with an orb whose single left-click does nothing at all - the server-side
 * capability guard correctly refuses it, and the refusal is silent because the option should
 * never have been reachable.
 *
 * Called from [SummoningUi.settle], i.e. whenever the active familiar changes, so it also covers
 * dismiss (no familiar -> every action is selectable again, nothing is reset) and login.
 */
fun Player.resetStaleLeftClickAction() {
    val capabilities = FamiliarCapabilityTable.active(this) ?: return
    if (!capabilities.supports(leftClickAction())) {
        setVarbit(ACTIVE_ACTION_VARBIT, DEFAULT_ACTION.leftClickValue)
    }
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
