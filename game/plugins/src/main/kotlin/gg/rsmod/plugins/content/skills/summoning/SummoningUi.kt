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

    /**
     * N355 (owner 2026-10-24: "de preview van de follower heeft nog de vierkant keer 3"): the three nested rectangles 662:0 / 2 / 3
     * (8,28 173x111; 14,31 161x105; 23,36 143x95) behind the model box draw as three squares around the preview - they were always hidden.
     * Owner 2026-10-29 (photo of his Spirit wolf's Follower Details): with a familiar the client shows 2 and 3 again - a dark inner
     * square - while a pet kept the flat lighter box; "zoals familiar": shown for every active follower, hidden only when empty.
     */
    private val PANEL_BACKDROP_RECTS = listOf(2, 3)

    /** The name line under the model; 751 writes it with `IF_SETTEXT`. */
    private const val PANEL_NAME = 54

    /** Room for the follower's name on its bar (662:54) in the bold font; a wider name is drawn in p11 (N384). */
    private const val NAME_BAR_WIDTH = 160

    /** The name text box itself (662:54 is 165 px wide): wider than this in p11 the client wraps the name under the bar. */
    private const val NAME_TEXT_WIDTH = 165

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

    /** The icon of [PANEL_RENEW] (662:70): the cache's renew pouch, or (N423) the backpack for an upgraded pet's Store. */
    private const val PANEL_RENEW_ICON = 70
    private const val RENEW_SPRITE = 1747

    /** The gameframe's inventory-tab backpack (clientscript 1766 sets it on 548:142 / 746:27): an upgraded pet's Store. */
    private const val STORE_SPRITE = 1821

    /** Clientscript 5396 (SummoningInterfaceScriptTool point 6): `(op, tooltip)` of 662:69. */
    private const val STORE_LABEL_SCRIPT = 5396

    /**
     * The familiar-only parts of the familiar column 72 (N423: shown again for an upgraded pet for its Take BoB / Store row): the
     * special-move bar 17, Summoning points 18/41, time left 42/43 and the scroll graphics 55-59. The pet column 71 keeps its own
     * growth / hunger icons in the same place.
     */
    private val FAMILIAR_ONLY_IN_72 = listOf(17, 18, 41, 42, 43, 55, 56, 57, 58, 59)

    /** "Call Follower", and its icon. */
    private val PANEL_CALL = intArrayOf(49, 50)

    /** "Dismiss Familiar" / op2 "Dismiss Now", and its icon. */
    private val PANEL_DISMISS = intArrayOf(51, 52)

    /** The pet variant of the right-hand column (size/hunger); never used by a familiar. */
    private const val PANEL_PET_LAYER = 71

    /**
     * The pet column's two values, drawn by clientscript 753 (662:71/45 `varpTriggers=[1175]`): varbit 4285 (varp 1175
     * bits 1..8) -> 662:45/46 "growth", varbit 4286 (bits 9..16) -> 662:48 "hunger", each as "<n>%" - or "NA" when the
     * value is exactly 101.
     */
    private const val PET_GROWTH_VARBIT = 4285
    private const val PET_HUNGER_VARBIT = 4286
    private const val PET_VALUE_NOT_APPLICABLE = 101

    /** The text fields script 753 writes "NA" / "<n>%" into (IF_SETTEXT 662:45, 662:46, 662:48); the icons are other components. */
    private val PET_VALUE_TEXTS = listOf(45, 46, 48)

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
        // Tab 8's page is slot 95 (SummoningInterfaceScriptTool point 3): this shows 662 with the Follower Details tab lit.
        player.focusTab(Tabs.BLANK_TAB)
    }

    fun hidePanel(player: Player) {
        player.focusTab(Tabs.INVENTORY)
    }

    /**
     * Mounts interface 662 on slot 95 for the session. It never focuses the panel: [settle] does that once, when a
     * follower appears (summon, login with one out, a renew that swapped the npc).
     *
     * Mounting is deliberately unconditional: the spare-tab entry must open a genuinely empty
     * Follower Details panel when the player has no familiar, rather than sending a focus request
     * to an unmounted slot. The panel contents are blanked by [refreshPanel].
     *
     * Owner 2026-10-04 032111 ("interfaces keep hanging ... sometimes it doesn't open"): this used to focus tab 8 whenever a
     * follower was out, and it runs on every gameframe rebuild - every bank, shop, GE, price checker, equipment stats or BoB
     * mount and close (GameframeRebuild.rearm), now and again 3 ticks later. CS2 71 stores the focused tab in VARC 168 even
     * while a side inventory hides the tabs, and 1364 reopens VARC 168 when the side inventory closes, so closing any of
     * those screens showed the Summoning panel instead of the player's tab, and a tab clicked within 3 ticks of a close was
     * switched away again.
     */
    fun restorePanel(player: Player) {
        if (!panelMounted(player)) {
            player.openInterface(InterfaceDestination.SUMMONING_TAB)
            // A fresh mount comes up with the cache's baked flags; redraw the gating on the next cycle.
            invalidate(player)
        }
    }

    /**
     * Whether 662 already sits on slot 95 of the current gameframe. Owner 2026-09-30 ("Summoning-tab knippert / verdwijnt"):
     * re-sending `IF_OPENSUB` for a mounted 662 makes the client close it - which wipes its server events
     * (`InterfaceManager.closeSubInterface` -> `resetServerActiveProperties`), fires the gameframe's sub-change hook and
     * rebuilds 662 with its baked flags - and open it again. The login, window-mode, gameframe-rebuild and every
     * side-inventory mount (bank, shop, GE: [gg.rsmod.plugins.api.ext.GameframeRebuild.rearm]) called this, so the panel
     * was torn down up to seven times in six seconds after login and twice on every bank open.
     */
    fun panelMounted(player: Player): Boolean {
        val mode = player.interfaces.displayMode
        val parent = gg.rsmod.plugins.api.getDisplayComponentId(mode)
        val child = gg.rsmod.plugins.api.getChildId(InterfaceDestination.SUMMONING_TAB, mode)
        return player.interfaces.getInterfaceAt(parent, child) == PANEL
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
        // An OSRS pet ([Pets]) is drawn on the same two surfaces when it is the follower (Novite's rev-667 pets open 662 as well).
        val npcId = Familiar.current(player)?.id ?: Pets.current(player)?.id ?: NO_FAMILIAR
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
        } else {
            // B4 (owner, reported again after #105 and #135: "summoning tab disappears on login with a pet/familiar, on teleport,
            // ..."): the tab button and icon are live client state exactly like the gating below - every rebuild of the tab strip
            // the server is not told about brings them back with the cache's baked "hidden, no op" flags. They were re-armed only on
            // a familiar change, login, window-mode switch and the known gameframe rebuilds; now on every full pass as well
            // (invalidate, and the RESEND_INTERVAL_CYCLES heartbeat), so whatever reset path hid the tab, it comes back by itself.
            FollowerDetailsTab.install(player)
        }
        refreshPanel(player, active)
        refreshOrb(player, active)
        refreshDismissLabels(player, active)
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
    ): Int? =
        SummoningChatheadAnimations.resolve(player, npcId)
            // K02: an OSRS pet's head moves in Follower Details exactly as in its chat (PetChatheads), or stays still.
            ?: PetChatheads.panelAnimation(npcId)
            // N257: a 667 pet's creature head with the cache's creature pose (it was left unposed or deformed).
            ?: gg.rsmod.plugins.api.ext.CreatureChathead.TALK.takeIf { gg.rsmod.plugins.api.ext.CreatureChathead.isCreaturePet(npcId) }

    private fun refreshPanelAnimation(
        player: Player,
        npcId: Int,
    ) {
        val pouch = SummoningPouchData.values.firstOrNull { it.npc == npcId }
        val selector = pouch?.let { SummoningChatheadAnimations.selector(it) }
        // Void's map variable sends -1 for an unmapped familiar (Phoenix) and for every pet, which the 7-bit varbit holds as 127;
        // a prior familiar's selector cannot survive in client state. N425: clientscript 751 (patched, cache) leaves the head's
        // animation alone for exactly this value, so the server's animation below is the only one a pet's head gets.
        player.setVarbit(SummoningChatheadAnimations.SELECTOR_VARBIT, selector ?: SummoningChatheadAnimations.NO_SELECTOR)
        val anim = resolveChatheadAnimation(player, npcId) ?: -1
        player.setComponentAnim(PANEL, PANEL_MODEL, anim)
        // N397: clientscript 751 (run by the client on the var transmits above, after this tick's packets) sets 662:1's animation
        // from enum 1275/1276 by the selector varbit - for a pet (selector -1) a creature pose (8374, measured on the Callisto cub)
        // that replaced the head's own movement or twisted a still OSRS head; the Ikkle Hydra's head lay flat. Sent again one tick
        // later, after the script has run - for a pet also "no animation".
        if (anim != -1 || isPetFollower(npcId)) {
            // Twice: the script's run is not always in the very next client cycle (6 of 380 pets kept 751's pose after one resend).
            player.world.queue {
                repeat(2) {
                    wait(1)
                    if (Familiar.current(player)?.id == npcId || Pets.current(player)?.id == npcId) player.setComponentAnim(PANEL, PANEL_MODEL, anim)
                }
            }
        }
        // A4: 662 stays mounted, so its head keeps whatever camera/position it was last sent - send both for every follower
        // (the cache values for every familiar and every pet whose head fits; NpcChatheadFit for the oversized OSRS heads).
        gg.rsmod.plugins.api.ext.NpcChatheadFit.send(player, gg.rsmod.plugins.api.ext.NpcChatheadFit.PANEL, PANEL, PANEL_MODEL, npcId, resetPosition = true)
    }

    private fun refreshPanel(
        player: Player,
        npcId: Int?,
    ) {
        val active = npcId != null
        // A pet has a name, a model, Call and Dismiss - no time / points column, no renew, scrolls, special move or Beast of Burden.
        val pet = npcId != null && isPetFollower(npcId)
        // The right-hand column is the cache's own choice: script 751 shows the pet layer 71 (growth / hunger) whenever
        // enum 1283 has no scroll for varp 448 - every pet - and the familiar layer 72 otherwise. Hiding 71 for a pet only
        // fought 751 (it re-shows 71 on every varp 1174 transmit) and left growth/hunger at varbits 4285/4286 = 0, drawn as
        // "0%" (owner 2026-09-30). An OSRS pet neither grows nor gets hungry, so both carry script 753's own "NA" value.
        player.setComponentHidden(PANEL, PANEL_PET_LAYER, !pet)
        if (pet) {
            player.setVarbit(PET_GROWTH_VARBIT, PET_VALUE_NOT_APPLICABLE)
            player.setVarbit(PET_HUNGER_VARBIT, PET_VALUE_NOT_APPLICABLE)
        }
        // N428 (owner 2026-10-30: "alleen die tekst 2 keer NA weg, niet de iconen"): script 753's text fields hidden for a pet, the
        // growth and hunger icons stay.
        PET_VALUE_TEXTS.forEach { player.setComponentHidden(PANEL, it, pet) }
        // N423: an upgraded pet carries like a beast of burden - the familiar column comes back for its Take BoB / Store row only.
        val upgraded = pet && BeastOfBurden.isUpgradedPetOut(player)
        player.setComponentHidden(PANEL, PANEL_MODEL_LAYER, !active)
        PANEL_BACKDROP_RECTS.forEach { player.setComponentHidden(PANEL, it, !active) }
        player.setComponentHidden(PANEL, PANEL_FAMILIAR_LAYER, !active || (pet && !upgraded))
        FAMILIAR_ONLY_IN_72.forEach { player.setComponentHidden(PANEL, it, upgraded) }
        player.setComponentHidden(PANEL, PANEL_SCROLL_COUNT, !active || pet)
        PANEL_CALL.forEach { player.setComponentHidden(PANEL, it, !active) }
        PANEL_DISMISS.forEach { player.setComponentHidden(PANEL, it, !active) }
        PANEL_RENEW.forEach { player.setComponentHidden(PANEL, it, !active || (pet && !upgraded)) }
        if (upgraded) {
            player.setComponentSprite(PANEL, PANEL_RENEW_ICON, STORE_SPRITE)
            player.runClientScript(STORE_LABEL_SCRIPT, STORE_OP, STORE_TOOLTIP)
        } else if (active) {
            player.setComponentSprite(PANEL, PANEL_RENEW_ICON, RENEW_SPRITE)
            player.runClientScript(STORE_LABEL_SCRIPT, FamiliarAction.RENEW.label, RENEW_TOOLTIP)
        }

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
        player.setComponentHidden(PANEL, PANEL_SPECIAL_INFO, pet)
        // N384 (owner 2026-10-24: "Tumeken's damaged guardian" ran off the name bar): script 751 writes the name in the bar's bold
        // font; a name wider than the bar is drawn in the small font instead (the client's RspsUi font hook).
        // N427 (owner 2026-10-30: "waarom staat er overal Pet voor?"): the follower's own name, as OSRS names the creature at your
        // side - "Zilyana Jr.", "Kraken", "Kitten" - not its item ("Pet Zilyana", "Pet kitten").
        val ui = gg.rsmod.plugins.content.quests.tab.N108Ui
        // N423: "Upgraded Tumeken's Damaged Guardian" is wider than the bar's text box (662:54, 165 px) even in the small font and
        // wrapped onto a second line under the bar; a name that fits neither font ends in "..." (the UI's overflow rule, fitP11).
        val name = player.world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, npcId!!)?.name.orEmpty()
            .let { if (ui.p11(player.world, it) > NAME_TEXT_WIDTH) ui.fitP11(player.world, it, NAME_TEXT_WIDTH) else it }
        // Clientscript 751 (patched, cache - SummoningInterfaceScriptTool point 5) writes a known familiar's enum name over this and
        // leaves every other follower's bar to the server: before, it wrote the pet ITEM's name here on every transmit.
        player.setComponentText(PANEL, PANEL_NAME, name)
        gg.rsmod.plugins.content.quests.tab.N108Ui.fonts(
            player,
            PANEL,
            listOf(PANEL_NAME to if (gg.rsmod.plugins.content.quests.tab.N108Ui.b12(player.world, name) > NAME_BAR_WIDTH) "p11_full" else "b12_full"),
        )
        // The same capability record the orb reads, so the two surfaces cannot disagree.
        val capabilities = FamiliarCapabilityTable.forNpc(npcId!!)
        val carries = capabilities?.supports(FamiliarAction.TAKE_BOB) == true || upgraded
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
        val supports: ((FamiliarAction) -> Boolean)? =
            when {
                capabilities != null -> { action -> capabilities.supports(action) }
                npcId != null && Pets.isPetNpc(npcId) -> Pets.orbActions(player).let { actions -> { action -> action in actions } }
                else -> null
            }
        if (supports == null) {
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
            if (!supports(action)) {
                action.orbComponents.forEach { player.setComponentHidden(ORB, it, true) }
            }
        }
        player.setComponentSprite(ORB, ORB_ICON, ORB_SPRITE_ACTIVE)
    }

    /**
     * Clientscript 5378 (SummoningInterfaceScriptTool): `(orb, panel op1, panel op2)` relabels the follower's dismiss entry
     * on the orb (747:11 op6 / 747:20 op1) and the panel (662:51 op1 / op2). The labels are baked "Dismiss" / "Dismiss
     * Familiar" / "Dismiss Now"; an OSRS pet is picked up instead (owner 2026-09-30: "osrs Pets moeten ook pickup functie
     * hebben in de summoning orb ipv dismiss"; OSRS offers only Pick-up on a pet), so its entry reads "Pick-up" and the
     * "Dismiss Now" shortcut is emptied (an empty label is no menu entry: `InterfaceManager.getOp`). Re-sent with the rest
     * of the gating because a rebuilt component comes back with its baked labels.
     */
    private const val DISMISS_LABEL_SCRIPT = 5378
    const val PET_PICK_UP = "Pick-up"

    /** N423: 662:69 for an upgraded pet (clientscript 5396), and the cache's own words for a familiar. */
    const val STORE_OP = "Store"
    private const val STORE_TOOLTIP = "Store items"
    private const val RENEW_TOOLTIP = "Renew familiar"

    private fun refreshDismissLabels(
        player: Player,
        npcId: Int?,
    ) {
        if (npcId != null && isPetFollower(npcId)) {
            player.runClientScript(DISMISS_LABEL_SCRIPT, PET_PICK_UP, PET_PICK_UP, "")
        } else {
            player.runClientScript(DISMISS_LABEL_SCRIPT, FamiliarAction.DISMISS.label, "Dismiss Familiar", "Dismiss Now")
        }
    }

    private fun isPetFollower(npcId: Int): Boolean = FamiliarCapabilityTable.forNpc(npcId) == null && Pets.isPetNpc(npcId)

    /** The word an orb action shows for the current follower (the left-click chooser uses the same words as the menu). */
    fun labelOf(
        player: Player,
        action: FamiliarAction,
    ): String {
        val npcId = Familiar.current(player)?.id ?: Pets.current(player)?.id
        return if (action == FamiliarAction.DISMISS && npcId != null && isPetFollower(npcId)) PET_PICK_UP else action.label
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
