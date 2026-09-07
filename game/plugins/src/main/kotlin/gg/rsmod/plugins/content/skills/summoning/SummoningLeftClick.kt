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
 *  1. 747:7 carries the real op `op10 = 'Select left-click option'` and opens interface 880.
 *  2. 880's rows each carry the real op `op1 = 'Select'` as a plain `IF_BUTTON1`, i.e. the click
 *     goes to the *server*; there is no `onOp` script on them. The server answers by writing 880's
 *     preview varbit 6455 (varp 1494), which 880:3's `onVarTransmit` picks up
 *     (`varpTriggers=[1494]` -> script 2672 -> `GOSUB(2674)`). `disasm 2674` paints sprite 2035 on
 *     the selected row and 2034 on the other seven, so the highlight is drawn by the interface
 *     itself. Nothing is committed yet.
 *  3. 880:21 carries the real op `op1 = 'Confirm Selection'`. Confirming writes the real varbit
 *     6454 (varp 1493).
 *  4. 747:7's own `onVarTransmit` (`varpTriggers=[1493]`) then runs script 751, whose tail is
 *     `GOSUB_WITH_PARAMS(2671)`. `disasm 2671` shows the chosen action's direct left-click
 *     component on 747 and hides that action's twin in the right-click submenu, and does the
 *     opposite for every action it is not making the left-click.
 *
 * ## What the owner's 2026-09-07 retest changed
 *
 * The selector used to offer all eight actions the cache bakes. Requirement H6 reduces the orb to
 * six - Special Move, Attack, Call, Dismiss, Take BoB, Renew - so "Follower Details" (varbit value
 * 0, now on the Skills tab instead) and "Interact" (value 7, a duplicate of the familiar's own npc
 * option) are no longer offered. Requirement H7 further restricts the list to the actions the
 * **currently summoned** familiar can actually perform, which is why [selectableActions] takes a
 * player rather than being a constant.
 *
 * The action set, the varbit values and the interface-880 row components all live on
 * [FamiliarAction] now, so the selector, the orb gating and the server handlers read one
 * declaration instead of three parallel ones.
 *
 * A consequence worth being explicit about: a never-configured account reads varbit 6454 = 0,
 * which used to mean Follower Details and is no longer an orb action at all. [leftClickAction]
 * therefore reports [DEFAULT_ACTION] for value 0, and [migrateRemovedDefault] rewrites the varbit
 * once on login so client and server agree. That is a consequence of H1/H6, not invented RS
 * behaviour, and it is the only value this file substitutes.
 */

/** varbit 6454 (varp 1493 bits 0..3) - the persistent "configured left-click action". */
private const val ACTIVE_ACTION_VARBIT = 6454

/** The varp varbit 6454 lives in; 747:7 listens for it (`varpTriggers=[1493]`). */
private const val LEFT_CLICK_VARP = 1493

/** varbit 6455 (varp 1494 bits 0..3) - interface 880's own preview, live while the dialog is open. */
private const val PREVIEW_ACTION_VARBIT = 6455

/**
 * The varbit-6454 values the cache bakes for the two actions H1/H6 removed from the orb:
 * 0 = Follower Details, 7 = Interact. Kept named rather than inline so the migration below reads
 * as "the removed ones" instead of as two magic numbers.
 */
private val REMOVED_ACTION_VALUES = setOf(0, 7)

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
 * script 2671 and the server agree about which twin should be visible; without it an untouched
 * account would keep asking the client to show 747:18 ("Follower Details"), which the orb gating
 * then immediately hides, leaving the orb with no left-click at all.
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

/** Live-previews a row selection on 880 (step 2 above). */
fun Player.setPendingLeftClickAction(action: FamiliarAction) {
    setVarbit(PREVIEW_ACTION_VARBIT, action.leftClickValue)
}

fun Player.pendingLeftClickAction(): FamiliarAction =
    FamiliarAction.byLeftClickValue(getVarbit(PREVIEW_ACTION_VARBIT)) ?: leftClickAction()

/**
 * Commits the previewed selection (step 3 above). Writing the varp is the entire server side of
 * this: script 751 -> 2671 performs the redraw on the client, exactly as it does for a real 2011
 * server. There is deliberately no chat feedback - the real interface gives none.
 */
fun Player.confirmLeftClickAction() {
    setVarbit(ACTIVE_ACTION_VARBIT, pendingLeftClickAction().leftClickValue)
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
