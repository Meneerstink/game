package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentAnim
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setVarc

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
     * Applies the gating if anything has changed since it was last applied. Driven once per cycle
     * from [Familiar.tick]; see [invalidate] for why it is not done inline.
     */
    fun settle(player: Player) {
        val npcId = Familiar.current(player)?.id ?: NO_FAMILIAR
        if (player.attr[RENDERED_FAMILIAR] == npcId) {
            return
        }
        player.attr[RENDERED_FAMILIAR] = npcId
        val active = npcId.takeIf { it != NO_FAMILIAR }
        refreshPanel(player, active)
        refreshOrb(player, active)
        refreshSpecialMode(player)
    }

    /**
     * The familiar's own idle sequence, sent to the panel's model (662:1).
     *
     * Clientscript 751 animates that model with `ENUM(1276, varbit 4282)`, and varbit 4282 is the
     * **pet** growth stage - so every familiar was being drawn with the same pet idle. The real
     * per-familiar sequence is reachable from the cache: [gg.rsmod.game.fs.def.NpcDef.basId]
     * (NPCType opcode 127) keys [gg.rsmod.game.fs.def.BasDef], whose `ready` (or weighted
     * `readyAnimations` pool) is the animation the npc idles with in the world.
     *
     * All 78 familiars resolve to a real sequence this way - see
     * `C:\RSPS\summoning_refs\familiar_bastypes.txt`. Nothing is substituted or guessed: a
     * familiar whose set carried no idle at all would be left alone rather than given someone
     * else's animation.
     */
    private fun refreshPanelAnimation(
        player: Player,
        npcId: Int,
    ) {
        val basId = player.world.definitions.get(NpcDef::class.java, npcId).basId
        if (basId == -1) {
            return
        }
        val idle = player.world.definitions.get(BasDef::class.java, basId).idleAnimation()
        if (idle != -1) {
            player.setComponentAnim(PANEL, PANEL_MODEL, idle)
        }
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
