package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.focusTab
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentAnim
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setVarc
import gg.rsmod.plugins.api.ext.setVarbit

/**
 * The single capability model behind BOTH Summoning surfaces: the Follower Details panel
 * (interface 662, gameframe slot 95) and the Summoning orb's option layer (interface 747:8).
 *
 * The rule this file exists to enforce is that an option is never visible without a handler
 * behind it. Everything the two surfaces offer is derived here from the active familiar, so the
 * panel and the orb can never disagree with each other or with the server.
 *
 * ## What the cache does on its own, and what it does not
 *
 * The client is not passive here, and this object is deliberately layered on top of the cache's
 * own scripts rather than fighting them:
 *
 * * `disasm 2671` (reached from 747:7's `onVarTransmit`, varp 1493) owns the left-click pairing:
 *   for each of the seven orb actions it shows the `op1` twin and hides the `op6` twin, or the
 *   other way round, according to varbit 6454. That is why [refreshOrb] re-runs 2671 rather than
 *   recomputing the pairing: the cache's own logic stays authoritative and this object only ever
 *   *removes* what does not apply afterwards.
 * * `disasm 751` (662:1, varps 1174/1160) draws the familiar model, the name line and the
 *   familiar-vs-pet layer switch. It has no branch that clears the model: when varp 448 is -1 the
 *   npc lookup `ENUM(1320, var448)` returns the enum's own default and the script skips its
 *   `IF_SETNPCMODEL` entirely, leaving the previous familiar rendered. That is the "stale Steel
 *   titan after dismiss" fault, and it can only be fixed from the server - see [refreshPanel].
 * * `disasm 1364` (the gameframe rebuild) shows 747:8 only when varbit 4280 is set *and*
 *   something is mounted on gameframe slot 95. Both hold permanently on this server, so the
 *   per-familiar gating of the orb has to happen here instead.
 *
 * ## Capabilities
 *
 * Nothing in here is a per-npc special case. Every decision is delegated to
 * [FamiliarCapabilityTable], the one place that derives what a familiar can do from the sourced
 * tables, so the panel, the orb, the npc options and the left-click selector cannot disagree.
 */
object SummoningUi {
    /** Interface 662 - Follower Details, mounted on gameframe slot 95. */
    const val PANEL = 662

    /** Interface 747 - the Summoning orb. */
    const val ORB = 747

    // --- 662 -----------------------------------------------------------------------------------
    /** The model box (`box=5,26 178x114`); its only child is 662:1, the familiar model. */
    private const val PANEL_MODEL_LAYER = 4

    /** The familiar model itself, driven by clientscript 751 off varp 1174. */
    private const val PANEL_MODEL = 1

    /** The name line under the model; 751 writes it with `IF_SETTEXT`. */
    private const val PANEL_NAME = 54

    /** The scroll counter under the special-move button (`onInvTransmit` script 769, inv 93). */
    private const val PANEL_SCROLL_COUNT = 66

    /** The attack button's own layer - holds 662:61..65, the target widget included. */
    private const val PANEL_ATTACK_LAYER = 60

    /*
     * Each of the four buttons is a clickable LAYER plus a separate icon GRAPHIC, and the icon is
     * the layer's *sibling*, not its child (67/68, 69/70 under 662:72; 49/50, 51/52 under 662:75).
     * Hiding the layer alone therefore leaves the icon painted with nothing behind it - live
     * testing on 2026-09-07 showed exactly that: a Unicorn stallion's panel had no Take BoB button
     * but still drew the Take BoB icon. Both halves are hidden together.
     */

    /** "Take BoB", and its icon. */
    private val PANEL_TAKE_BOB = intArrayOf(67, 68)

    /** "Renew Familiar", and its icon. */
    private val PANEL_RENEW = intArrayOf(69, 70)

    /** "Call Follower", and its icon. */
    private val PANEL_CALL = intArrayOf(49, 50)

    /** "Dismiss Familiar" / op2 "Dismiss Now", and its icon. */
    private val PANEL_DISMISS = intArrayOf(51, 52)

    /** The pet variant of the right-hand column (size/hunger); never used by a familiar. */
    private const val PANEL_PET_LAYER = 71

    /** The familiar variant of the right-hand column (time remaining / Summoning points). */
    private const val PANEL_FAMILIAR_LAYER = 72

    /** The special-move description overlay, shown by 657/658 while the button is hovered. */
    private const val PANEL_SPECIAL_INFO = 73

    /** The special-move button; its clickable child is created at runtime by script 606. */
    const val PANEL_SPECIAL = 74

    // --- 747 -----------------------------------------------------------------------------------
    /** The whole familiar-option layer. Hidden outright when there is no familiar. */
    private const val ORB_FAMILIAR_LAYER = 8

    /** The special-move button; like 662:74 its clickable child is created by script 606. */
    const val ORB_SPECIAL = 17

    /**
     * The cache's own left-click pairing script, bound to 747:7 `onVarTransmit` on varp 1493.
     * Re-running it restores every op1/op6 twin to the state varbit 6454 asks for.
     */
    private const val LEFT_CLICK_PAIRING_SCRIPT = 2671

    /**
     * Read by clientscript 608 immediately before it puts `op1 = "Cast"` on the special-move
     * button. With the varc clear the button keeps only its target verb, so clicking it starts
     * target selection instead of firing; with it set the button fires on the spot. That is
     * exactly the instant-versus-targeted split [FamiliarSpecialTarget] already models, so the
     * server drives the varc from the active familiar's binding.
     */
    private const val SPECIAL_IS_INSTANT_VARC = 1436

    /**
     * The Summoning orb's own icon, 747:3. The cache bakes sprite [ORB_SPRITE_INACTIVE] here and
     * clientscript 751 overwrites it with [ORB_SPRITE_ACTIVE] on its has-familiar path - and has
     * no branch anywhere that puts the baked value back. That is why the orb stayed lit after a
     * dismiss (owner requirement H3): nothing in the client was ever going to un-light it.
     */
    private const val ORB_ICON = 3

    /** Recovered from the cache: the value 747:3 is baked with, i.e. the authentic inactive orb. */
    private const val ORB_SPRITE_INACTIVE = 1244

    /** The value clientscript 751 writes to 747:3 when a familiar is out. */
    private const val ORB_SPRITE_ACTIVE = 1802

    /**
     * Brings the Follower Details panel up in the sidebar region it is mounted on, and takes it
     * away again.
     *
     * ## Where the panel lives, and why it is not a tab
     *
     * Interface 662 is mounted on **gameframe slot 95** (`548:221` fixed / `746:107` resizable).
     * Probing the resizable gameframe shows 746:107 sharing parent 89 and the box `0,0 190x261`
     * with every numbered sidebar tab, including the Skills tab at 746:92 - so slot 95 renders in
     * exactly the same place a tab does: the sidebar panel region directly beneath the tab row.
     * That region is what the owner means by the empty block underneath the Skills area, and it is
     * where the authentic 2011 reference shots put the panel.
     *
     * The point of slot 95 is that, unlike slots 0..15, **it has no tab button of its own**. This
     * server mounts it for the whole session so the owner's spare-tab entry can focus it even when
     * the panel is empty. A familiar becoming active focuses it; dismissing one only blanks the
     * mounted panel and leaves the entry available.
     *
     * A stale note used to sit here claiming the owner had rejected arming the spare tab slot
     * (`548:99` / `746:47`) as a Follower Details button. That was true of an earlier round and is
     * no longer the standing instruction: the owner has since asked, repeatedly and with a marked
     * client screenshot, for Follower Details to live in exactly that empty tab and to stay visible
     * there even with no familiar out. [FollowerDetailsTab] implements that and is armed
     * unconditionally at login and on every window-mode change; this object is only responsible for
     * what the slot *contains*, which is a real familiar or a genuinely blank panel.
     *
     * [hidePanel] is retained for callers that explicitly want to leave the panel, but the
     * familiar lifecycle does not call it: the owner's requirement is that the spare-tab entry
     * remains visible with an empty panel when no familiar is out.
     */
    fun showPanel(player: Player) {
        player.focusTab(Tabs.SUMMONING)
    }

    fun hidePanel(player: Player) {
        player.focusTab(Tabs.INVENTORY)
    }

    /**
     * Mounts interface 662 on slot 95 for the session and focuses it only when a familiar is out.
     *
     * Mounting is deliberately unconditional: the spare-tab entry must open a genuinely empty
     * Follower Details panel when the player has no familiar, rather than sending a focus request
     * to an unmounted slot. The panel contents are blanked by [refreshPanel].
     */
    fun restorePanel(player: Player) {
        player.openInterface(InterfaceDestination.SUMMONING_TAB)
        if (Familiar.current(player) != null) showPanel(player)
    }

    /** Whether this familiar can be ordered to attack - i.e. it has real, sourced combat data. */
    fun canFight(npcId: Int): Boolean = FamiliarCapabilityTable.forNpc(npcId)?.canFight == true

    /** Whether "Take BoB" / the Familiar Inventory apply - beasts of burden and foragers. */
    fun carries(npcId: Int): Boolean = FamiliarCapabilityTable.forNpc(npcId)?.carries == true

    /**
     * The familiar the two surfaces are currently drawn for, so the ~15 `IF_SETHIDE` packets this
     * object writes are sent when the familiar actually changes rather than on every game cycle -
     * [Familiar.updateHud] runs once per cycle per online player. Deliberately transient: after a
     * relog the client has been rebuilt and the gating genuinely has to be re-sent.
     */
    private val RENDERED_FAMILIAR = AttributeKey<Int>()

    /**
     * The familiar the *panel* was last brought up for.
     *
     * Deliberately separate from [RENDERED_FAMILIAR], which [invalidate] clears to force a redraw
     * of the gating. Those are two different questions - "does the client need the hides again"
     * and "has the player's familiar actually changed" - and answering the second with the first
     * makes every unrelated invalidation steal the sidebar.
     */
    private val SETTLED_FAMILIAR = AttributeKey<Int>()

    private const val NO_FAMILIAR = -1

    /**
     * Marks the two surfaces as needing a redraw. The redraw itself happens on the next game
     * cycle, in [settle].
     *
     * ## Why this is deferred rather than immediate
     *
     * The first attempt applied the gating inline and it did not stick: a Steel titan still
     * offered "Take BoB". The cause is a packet-ordering difference, not the gating logic.
     * `setComponentHidden` writes its message to the channel there and then, but a varp written
     * through [gg.rsmod.game.model.varp.VarpSet] is only *marked dirty* and is flushed at the end
     * of the cycle. So within one cycle the client receives the hides first and the varps second,
     * and the varps re-run the cache's own hook scripts - 2671 off varp 1493, 751 off varps
     * 1174/1160 - which put back exactly what had just been hidden.
     *
     * Deferring by one cycle puts the hides after the previous cycle's varps and before the next
     * ones, which is the only ordering in which they survive. It also lands after the client has
     * finished rebuilding its gameframe on login, which has the same effect for the same reason.
     */
    fun invalidate(player: Player) {
        player.attr.remove(RENDERED_FAMILIAR)
    }

    /**
     * How many cycles may pass before the gating is re-sent even though nothing the server knows
     * about has changed. 50 cycles is ~30 seconds.
     *
     * ## Why a periodic re-send is needed at all
     *
     * Everything this object writes - `IF_SETHIDE`, `IF_SETEVENTS`, `IF_SETGRAPHIC`, the varc that
     * chooses instant-versus-targeted - is **live client state, not persisted state**. Any time the
     * client rebuilds a component it comes back with its baked flags: no ops, the baked sprite,
     * visible. The server has no packet that tells it a rebuild happened.
     *
     * A change-only refresh therefore fails open: once the client has silently reverted a
     * component, the server's own "already rendered this familiar" bookkeeping says there is
     * nothing to do and the surface stays broken until the familiar changes or the player relogs.
     * That is the shape of every "it worked and then stopped working" report against these two
     * surfaces - a special-move button that stops responding mid-fight, a Take BoB that reappears
     * on a familiar that cannot carry.
     *
     * Re-sending on a slow heartbeat makes the surfaces self-healing without turning ~15 packets
     * per player into a per-cycle cost: the steady state is one refresh every 30 seconds, and a
     * real change still refreshes immediately.
     */
    private const val RESEND_INTERVAL_CYCLES = 50

    /** Cycle count at the last full refresh, for [RESEND_INTERVAL_CYCLES]. */
    private val RENDERED_ON_CYCLE = AttributeKey<Int>()

    /**
     * Applies the gating if anything has changed since it was last applied, or if it is simply due
     * a re-send. Driven once per cycle from [Familiar.tick]; see [invalidate] for why it is not
     * done inline, and [RESEND_INTERVAL_CYCLES] for why "unchanged" is not the same as "nothing to
     * do".
     */
    fun settle(player: Player) {
        val npcId = Familiar.current(player)?.id ?: NO_FAMILIAR
        val now = player.world.currentCycle
        val due = now - (player.attr[RENDERED_ON_CYCLE] ?: Int.MIN_VALUE) >= RESEND_INTERVAL_CYCLES
        if (player.attr[RENDERED_FAMILIAR] == npcId && !due) {
            return
        }
        player.attr[RENDERED_FAMILIAR] = npcId
        player.attr[RENDERED_ON_CYCLE] = now
        val active = npcId.takeIf { it != NO_FAMILIAR }

        /*
         * Whether the *familiar itself* changed, which is a different question from whether the
         * gating needs re-sending. [invalidate] is called for several reasons that have nothing to
         * do with the familiar - configuring the orb's left-click, for one - and it works by
         * clearing [RENDERED_FAMILIAR], so testing that attribute would report a change every
         * time. Doing the two things below on that signal would yank the sidebar onto the
         * Summoning panel while the player was looking at their inventory.
         */
        val previous = player.attr[SETTLED_FAMILIAR] ?: NO_FAMILIAR
        if (previous != npcId) {
            player.attr[SETTLED_FAMILIAR] = npcId
            // A familiar-state varp can make the client rebuild the gameframe. That rebuild
            // restores the cache's non-clickable spare-tab defaults, so re-arm both the tab
            // graphic and its overlaid icon after the state transition (LR-03/LR-04).
            FollowerDetailsTab.install(player)
            // G4: a left-click the new familiar cannot perform is dropped rather than left to
            // fail silently on the next click. Done before the orb is redrawn so the redraw
            // already reflects the corrected value.
            player.resetStaleLeftClickAction()
            // G1: the panel is the familiar's own surface and has no click entry point of any
            // kind, so a familiar appearing has to bring it up. Doing it here rather than only in
            // Familiar.summon covers every other way a familiar can start existing - login with
            // one already out, a reconnect, a renew that swapped the npc - which is what left the
            // owner with no Follower Details panel to look at.
            if (active != null) showPanel(player)
        }
        refreshPanel(player, active)
        refreshOrb(player, active)
        refreshSpecialMode(player)
        // The special-move button's own IF_SETEVENTS has exactly the same "live client state"
        // problem, and is what stops a Unicorn's Healing Aura responding mid-fight (H11), so it is
        // re-armed on the same heartbeat rather than only when the familiar changes.
        SummoningSpecialMoves.refreshOrbButton(player, force = true)
        // ...and the panel's special-move name/description/cost, which are varcs and a varbit and
        // are just as much client state as the events mask.
        SummoningSpecialMoves.refreshPanelText(player, force = true)
    }

    /**
     * Resolves the familiar chathead sequence through Void's varbit-4282 / enum-1275-or-1276 route.
     * Shared by interface 662 and familiar dialogue so neither surface can drift back to a world
     * BAS animation, which is a different model rig.
     */
    fun resolveChatheadAnimation(
        player: Player,
        npcId: Int,
    ): Int? = SummoningChatheadAnimations.resolve(player, npcId)

    private fun refreshPanelAnimation(
        player: Player,
        npcId: Int,
    ) {
        val pouch = SummoningPouchData.values.firstOrNull { it.npc == npcId }
        val selector = pouch?.let { SummoningChatheadAnimations.selector(it) }
        // Void's map variable sends -1 for an unmapped familiar (Phoenix); do the same so a prior
        // familiar's selector cannot survive in client state even though the component is cleared.
        player.setVarbit(SummoningChatheadAnimations.SELECTOR_VARBIT, selector ?: -1)
        player.setComponentAnim(PANEL, PANEL_MODEL, resolveChatheadAnimation(player, npcId) ?: -1)
    }

    private fun refreshPanel(
        player: Player,
        npcId: Int?,
    ) {
        val active = npcId != null
        // The pet column is never the familiar column; 751 only hides it on the familiar path,
        // and that path is skipped entirely once varp 448 goes to -1.
        player.setComponentHidden(PANEL, PANEL_PET_LAYER, true)
        player.setComponentHidden(PANEL, PANEL_MODEL_LAYER, !active)
        player.setComponentHidden(PANEL, PANEL_FAMILIAR_LAYER, !active)
        player.setComponentHidden(PANEL, PANEL_SCROLL_COUNT, !active)
        PANEL_CALL.forEach { player.setComponentHidden(PANEL, it, !active) }
        PANEL_DISMISS.forEach { player.setComponentHidden(PANEL, it, !active) }
        PANEL_RENEW.forEach { player.setComponentHidden(PANEL, it, !active) }

        if (!active) {
            // Script 751 leaves the last familiar's model and name in place, so clear both by
            // hand. Hiding the layer is what actually removes the model from the panel; blanking
            // the name stops a dismissed familiar's title surviving the next redraw.
            player.setComponentText(PANEL, PANEL_NAME, "")
            player.setComponentHidden(PANEL, PANEL_MODEL, true)
            PANEL_TAKE_BOB.forEach { player.setComponentHidden(PANEL, it, true) }
            player.setComponentHidden(PANEL, PANEL_ATTACK_LAYER, true)
            player.setComponentHidden(PANEL, PANEL_SPECIAL, true)
            player.setComponentHidden(PANEL, PANEL_SPECIAL_INFO, true)
            return
        }

        player.setComponentHidden(PANEL, PANEL_MODEL, false)
        player.setComponentHidden(PANEL, PANEL_SPECIAL_INFO, false)
        // The same capability record the orb reads, so the two surfaces cannot disagree.
        val capabilities = FamiliarCapabilityTable.forNpc(npcId!!)
        val carries = capabilities?.supports(FamiliarAction.TAKE_BOB) == true
        PANEL_TAKE_BOB.forEach { player.setComponentHidden(PANEL, it, !carries) }
        player.setComponentHidden(PANEL, PANEL_ATTACK_LAYER, capabilities?.supports(FamiliarAction.ATTACK) != true)
        player.setComponentHidden(PANEL, PANEL_SPECIAL, capabilities?.supports(FamiliarAction.SPECIAL_MOVE) != true)
        refreshPanelAnimation(player, npcId)
    }

    /**
     * Draws the orb for the familiar that is out, or makes it inert when none is.
     *
     * Two owner requirements shape this and neither is negotiable from the cache's side:
     *
     * * **H6/H1** - "Follower Details" and "Interact" are removed outright. They are hidden on
     *   every pass, familiar or not, because clientscript 2671 shows them again whenever the
     *   left-click varp is transmitted.
     * * **H3** - with no familiar the orb must not look active. Script 751 lights 747:3 with
     *   sprite [ORB_SPRITE_ACTIVE] and never puts the baked [ORB_SPRITE_INACTIVE] back, so the
     *   server has to.
     */
    private fun refreshOrb(
        player: Player,
        npcId: Int?,
    ) {
        val capabilities = npcId?.let { FamiliarCapabilityTable.forNpc(it) }
        if (capabilities == null) {
            /*
             * Without a familiar the orb's authentic menu is "Select left-click option" and
             * nothing else; every entry under 747:8 would be an option with no subject.
             *
             * Hiding 747:8 alone does not do it. Live testing on 2026-09-07 showed the menu still
             * listing Follower Details / Attack / Interact / Renew / Take BoB / Dismiss / Call
             * with the layer hidden: this client collects menu entries from a component's own
             * hidden flag, not from its ancestors'. So every option component is hidden
             * individually, and 747:8 with them.
             */
            FamiliarAction.ALL_ORB_COMPONENTS.forEach { player.setComponentHidden(ORB, it, true) }
            player.setComponentHidden(ORB, ORB_FAMILIAR_LAYER, true)
            player.setComponentSprite(ORB, ORB_ICON, ORB_SPRITE_INACTIVE)
            return
        }
        player.setComponentHidden(ORB, ORB_FAMILIAR_LAYER, false)
        // Hand the op1/op6 pairing back to the cache before removing anything, so a familiar that
        // regains a capability gets its entry back without this object having to reimplement 2671.
        player.runClientScript(LEFT_CLICK_PAIRING_SCRIPT)

        // Then subtract, unconditionally and by capability. Order matters: 2671 has just re-shown
        // everything it owns, including the two removed actions and any action this familiar
        // cannot perform.
        FamiliarAction.REMOVED_ORB_COMPONENTS.forEach { player.setComponentHidden(ORB, it, true) }
        FamiliarAction.ORDERED.forEach { action ->
            if (!capabilities.supports(action)) {
                action.orbComponents.forEach { player.setComponentHidden(ORB, it, true) }
            }
        }
        player.setComponentSprite(ORB, ORB_ICON, ORB_SPRITE_ACTIVE)
    }

    /**
     * Tells the client whether the special-move button should fire on click or start target
     * selection. See [SPECIAL_IS_INSTANT_VARC].
     */
    fun refreshSpecialMode(player: Player) {
        val instant = SummoningSpecialMoves.resolveBinding(player)?.target == FamiliarSpecialTarget.INSTANT
        player.setVarc(SPECIAL_IS_INSTANT_VARC, if (instant) 1 else 0)
    }

    private fun hasSpecial(player: Player): Boolean = SummoningSpecialMoves.resolveBinding(player) != null
}
