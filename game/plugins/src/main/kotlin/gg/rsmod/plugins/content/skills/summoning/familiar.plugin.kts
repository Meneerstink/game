package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.getInteractingNpc
import gg.rsmod.plugins.api.ext.getInteractingPlayer
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface

/**
 * R07.1: wires [Familiar] to the real, verified cache options - "Summon" on the pouch
 * (confirmed live this session: `SPIRIT_WOLF_POUCH` really has "Summon" at inventory slot 3,
 * not guessed) and "Interact" on the summoned npc (confirmed the same way - familiars don't
 * have a player-facing "Dismiss" option in the cache, "Interact" is the real one).
 *
 * `on_item_option`/`on_npc_option` throw at plugin-load if the option string isn't really on
 * that specific def - only 2 of the ~50 pouches in the roster were individually checked this
 * session, so every entry's real options are checked here before binding, skipping (and
 * counting, logged) any that don't match rather than risking one unverified entry crashing the
 * whole boot for the entire roster.
 */
val familiarTickTimer = TimerKey()

/**
 * The familiar's real "Interact" option: the ordinary NPC chatbox, with the familiar's own
 * chathead, running one of its sourced conversations (see [SummoningDialogueData]). This replaces
 * the previous fabricated Renew/Deposit/Withdraw/Dismiss option menu, which had no basis in the
 * cache - `runNpcDefProbeTool` shows a familiar's real options are only
 * `[Interact, , Withdraw|Store, , ]`, and Renew/Dismiss live on the tab and orb instead.
 *
 * A familiar with no sourced transcript falls through to its other real cache option rather than
 * to invented dialogue: carriers open the Familiar Inventory (their "Store"/"Withdraw" option),
 * and anything else does nothing, which is honest about the data gap instead of papering over it
 * with a game message.
 */
suspend fun QueueTask.familiarDialogue(
    player: Player,
    npcId: Int,
) {
    val pouch = SummoningPouchData.values.firstOrNull { it.npc == npcId }
    val conversations = pouch?.let { SummoningDialogueData.conversationsFor(it) }.orEmpty()
    if (pouch == null || conversations.isEmpty()) {
        if (BeastOfBurden.isCarrierNpc(npcId)) {
            FamiliarInventory.open(player)
        }
        return
    }
    /*
     * Comprehension, straight out of the 2011 Knowledge Base ("Summoning - The Basics"):
     *
     *   "To understand your familiars, you need a Summoning level 10 points higher than you do to
     *    summon it - for example, to understand a magpie, which requires a Summoning level of 47 to
     *    summon, you will need a Summoning level of 57. Obviously, you can never understand a
     *    familiar with a Summoning level of 91, as you will not be able to boost your level above
     *    100 ... In addition, abyssal creatures are strange, unnatural beasts, and will only speak
     *    in what seems to be gibberish."
     *
     * The boosted level is the one that counts (the article's own reasoning is about boosting), so
     * this reads the current level, not the base one. Failing the check does not silence the
     * familiar - it removes the parenthetical translation and leaves the noise, which is what the
     * player is meant to hear.
     */
    val understands =
        !pouch.name.startsWith("ABYSSAL_") &&
            player.skills.getCurrentLevel(Skills.SUMMONING) >= pouch.level + 10
    conversations.random().forEach { line ->
        when {
            line.speaker == SummoningDialogueData.Speaker.PLAYER -> chatPlayer(line.speech)
            line.translation.isEmpty() || !understands -> chatNpc(line.speech, npc = npcId)
            else -> chatNpc(line.speech, line.translation, npc = npcId)
        }
    }
}

/**
 * R07.7: authentic dismiss confirmation - real RS asks before dismissing a familiar (losing it
 * forfeits the remaining life/points, an irreversible action worth guarding against misclicks).
 * Reuses the same [options] dialog primitive as the interact menu above rather than inventing a
 * new confirmation mechanism.
 */
suspend fun QueueTask.confirmDismiss(player: Player) {
    if (Familiar.current(player) == null) {
        return
    }
    if (options("Yes", "No", title = "Dismiss your familiar?") == 1) {
        Familiar.dismiss(player)
    }
}

/**
 * "Take BoB" - identical on all three surfaces it is offered from (the follower panel's button, the
 * orb, and the Familiar Inventory window), so it is written once here rather than three times.
 *
 * Two rules it enforces:
 *
 *  * **Capability.** The familiar must actually carry items. This is re-checked server-side even
 *    though the option is hidden for non-carriers, so a forged or stale click after a dismiss,
 *    expiry or familiar swap cannot act on a familiar that has no store.
 *  * **Feedback on the empty case** (owner requirement H9). Withdrawing nothing used to be
 *    silent, which is indistinguishable from a broken button. It now says so.
 */
fun takeBeastOfBurdenItems(player: Player) {
    if (!FamiliarCapabilityTable.activeSupports(player, FamiliarAction.TAKE_BOB)) {
        player.message("Your familiar cannot carry items for you.")
        return
    }
    val stored = BeastOfBurden.storedCount(player)
    if (stored == 0) {
        player.message("Your familiar isn't carrying anything.")
        return
    }
    val withdrawn = BeastOfBurden.withdrawAll(player)
    when {
        withdrawn == 0 -> player.message("You don't have enough inventory space to take those items.")
        withdrawn < stored ->
            player.message("You take $withdrawn item(s) from your familiar; you have no room for the rest.")
        else -> player.message("You withdraw $withdrawn item(s) from your familiar.")
    }
}

/**
 * R07.7: authentic dismiss confirmation - real RS asks before dismissing a familiar (losing it
 * forfeits the remaining life/points, an irreversible action worth guarding against misclicks).
 *
 * Renew gets the same treatment for the same reason (owner requirement H12): renewing consumes a
 * second pouch, which is destroyed, and the old implementation consumed it on the first click with
 * no way back. The confirmation is asked **before** anything is taken - [Familiar.renew] is only
 * reached once the player has said yes.
 */
suspend fun QueueTask.confirmRenew(player: Player) {
    if (!FamiliarCapabilityTable.activeSupports(player, FamiliarAction.RENEW)) {
        return
    }
    if (options("Yes", "No", title = "Renew your familiar?") == 1) {
        Familiar.renew(player)
    }
}

var boundSummon = 0
var skippedSummon = 0
SummoningPouchData.values().forEach { data ->
    val def = world.definitions.get(ItemDef::class.java, data.pouch)
    if (def.inventoryMenu.any { it?.lowercase() == "summon" }) {
        on_item_option(item = data.pouch, option = "summon") {
            if (Familiar.summon(player, data)) {
                player.applyLeftClickAction()
            }
        }
        boundSummon++
    } else {
        skippedSummon++
    }
}

var boundInteract = 0
var skippedInteract = 0
val familiarNpcIds = SummoningPouchData.values().map { it.npc }.distinct().toIntArray()

// Register native combat definitions only for rows whose animations and death data are sourced.
SummoningCombatDefinitions.executableCombatValues.forEach { definition ->
    listOfNotNull(definition.pouch.npc, definition.combatNpc).distinct().forEach { npcId ->
        set_combat_def(npcId) {
            configs {
                attackSpeed = definition.attackSpeed
                respawnDelay = 0
                attackStyle = when (definition.style) {
                    FamiliarAttackStyle.MELEE -> StyleType.CRUSH
                    FamiliarAttackStyle.RANGED -> StyleType.RANGED
                    FamiliarAttackStyle.MAGIC -> StyleType.MAGIC
                    FamiliarAttackStyle.NONE -> StyleType.NONE
                }
            }
            stats {
                hitpoints = definition.hitpoints
                attack = definition.attack
                strength = definition.strength
                defence = definition.defence
                ranged = definition.ranged
                magic = definition.magic
            }
            bonuses {
                attackStab = definition.attack
                attackSlash = definition.attack
                attackCrush = definition.attack
                attackMagic = definition.magic
                attackRanged = definition.ranged
                attackBonus = definition.attack
                strengthBonus = definition.strength
                rangedStrengthBonus = definition.maxHit
                magicDamageBonus = definition.maxHit / 10
            }
            anims {
                attack = definition.attackAnimation
                block = definition.blockAnimation
                death = definition.deathAnimation
            }
        }
    }
}

val executableCombatNpcIds = SummoningCombatDefinitions.executableCombatValues
    .flatMap { listOfNotNull(it.pouch.npc, it.combatNpc) }
    .distinct()
    .toIntArray()

on_npc_combat(*executableCombatNpcIds) {
    npc.queue { FamiliarCombat.handleCombat(this) }
}

familiarNpcIds.forEach { npc ->
    val def = world.definitions.get(NpcDef::class.java, npc)
    if (def.options.any { it?.lowercase() == "interact" }) {
        on_npc_option(npc = npc, option = "interact") {
            if (Familiar.current(player)?.id != npc) {
                return@on_npc_option
            }
            player.queue { familiarDialogue(player, npc) }
        }
        boundInteract++
    } else {
        skippedInteract++
    }
}

// The familiar's real third cache option: "Store" on a beast of burden, "Withdraw" on a forager
// (`runNpcDefProbeTool 6815` -> `OPTIONS=[Interact, , Store, , ]`, `6796`/`6817`/`6991` ->
// `[Interact, , Withdraw, , ]`). Both open the same real Familiar Inventory window - see
// [FamiliarInventory] - which is what makes the difference visible: a beast of burden gets a
// Store-enabled backpack panel next to the grid, a forager a read-only one. Bound only where the
// cache actually carries the option, using the same skip-and-count idiom as Interact above.
var boundStore = 0
var skippedStore = 0
familiarNpcIds.filter { BeastOfBurden.isCarrierNpc(it) }.forEach { npc ->
    val def = world.definitions.get(NpcDef::class.java, npc)
    val option = def.options.firstOrNull { it?.lowercase() == "store" || it?.lowercase() == "withdraw" }
    if (option != null) {
        on_npc_option(npc = npc, option = option.lowercase()) {
            if (Familiar.current(player)?.id != npc) {
                return@on_npc_option
            }
            FamiliarInventory.open(player)
        }
        boundStore++
    } else {
        skippedStore++
    }
}

/*
 * 2026-09-07 owner human retest: "Cure Unicorn stallion currently has no effect and no chatbox
 * feedback." That option is not a one-off. A census of all 78 familiar npc definitions in the
 * production cache (`runNpcDefProbeTool`, kept at summoning_refs/familiar_npcdefs.txt) shows the
 * complete set of option strings a familiar can carry is:
 *
 *   Interact (78)  Withdraw (20)  Store (9)  Drain (7, the -atrice family)
 *   Cure (Unicorn stallion)   Burrow (Desert wyrm)     Cannon (Barker toad)
 *   Special (Dreadfowl)       Fireball (Forge regent)  Drown (Karamthulhu overlord)
 *   Ash-blast (Phoenix)       Flames (Smoke devil)     Strike (Void torcher)
 *
 * Every one of those thirteen non-carry options is the same thing wearing the familiar's own
 * name: a second entry point to that familiar's special move, next to the follower panel's
 * button and the orb's "Cast <move>". So they are bound once, generically, to the same
 * [SummoningSpecialMoves] dispatch - there are no per-npc ability implementations here, and no
 * option is bound that the cache does not really carry.
 */
val carryOptions = setOf("interact", "store", "withdraw")
var boundAbility = 0
familiarNpcIds.forEach { npcId ->
    val def = world.definitions.get(NpcDef::class.java, npcId)
    def.options.filterNotNull().map { it.lowercase() }.filter { it.isNotBlank() && it !in carryOptions }
        .distinct()
        .forEach { option ->
            on_npc_option(npc = npcId, option = option) {
                if (Familiar.current(player)?.id != npcId) {
                    return@on_npc_option
                }
                SummoningSpecialMoves.castFromFamiliarOption(player)
            }
            boundAbility++
        }
}

// R07.3: depositing into a Beast of Burden familiar - real RS mechanic is using an
// inventory item on your pack animal. Bound only for the 3 real BoB npc ids, verified via
// BeastOfBurden.isBobNpc rather than assumed for the whole familiar roster.
BeastOfBurden.allKeys.forEach { register_container_key(it) }
familiarNpcIds.filter { BeastOfBurden.isBobNpc(it) }.forEach { npc ->
    on_any_item_on_npc(npc) {
        if (Familiar.current(player)?.id != npc) {
            return@on_any_item_on_npc
        }
        val item = player.getInteractingItem()
        BeastOfBurden.deposit(player, item)
    }
}

on_world_init {
    SummoningFamiliarDefinitions.validate()
    SummoningCombatDefinitions.validate()
    SummoningSpecialMoves.validate()
    SummoningLedger.validate()
    // H13: needs the loaded cache, so it cannot live in SummoningLedger.validate().
    SummoningLedger.validateRenderData(world)
    println(
        "R07.1 familiar: bound Summon on $boundSummon/${boundSummon + skippedSummon} pouches, " +
            "Interact on $boundInteract/${boundInteract + skippedInteract} familiar npcs, " +
            "$boundAbility unique familiar ability options, " +
            "Store on $boundStore/${boundStore + skippedStore} BoB npcs " +
            "(skipped entries didn't have that exact real cache option - not guessed).",
    )
}

on_login {
    player.timers[familiarTickTimer] = 1
    // Without varbit 4280 the client hides the orb's whole familiar-option layer (747:8), so this
    // has to be armed before anything else here writes Summoning state - see Familiar.unlockInterface.
    Familiar.unlockInterface(player)
    // R07.7: real RS mechanic - a familiar survives logout, its lifetime timer just pauses
    // (tickOffline = false) and resumes with the same time/points left on login, it does not
    // get dismissed. See Familiar.disconnect/restoreOnLogin.
    Familiar.restoreOnLogin(player)
    // Unconditional: varps persist, so an account that logged out with a familiar and then lost it
    // (expiry while offline, a cleared save) would otherwise log back in with varp 448/1174 still
    // naming the old familiar and the tab drawing a follower that does not exist.
    Familiar.updateHud(player)
    // H1: the Follower Details entry point is a sidebar tab now, and the spare tab slot is baked
    // with no op and a hidden icon, so it has to be armed after every gameframe build.
    FollowerDetailsTab.install(player)
    // H6 removed varbit-6454 values 0 and 7 from the orb; an account still holding one of them
    // would otherwise have no left-click action at all. Runs before applyLeftClickAction so the
    // varp transmitted below already carries the corrected value.
    player.migrateRemovedDefault()
    player.applyLeftClickAction()
    // The client is freshly rebuilt at this point and holds none of the IF_SETHIDE state the
    // server last sent, so the panel/orb gating has to be re-sent unconditionally.
    Familiar.redrawInterfaces(player)
    // ...and again a few cycles in. The gameframe is still being assembled while login runs, and
    // every component an interface (re)builds comes back with its baked hidden flag, which would
    // silently undo the first pass. Cheap, one-shot, and it makes the login path behave like the
    // mid-session path rather than being a special case.
    player.queue {
        wait(5)
        Familiar.redrawInterfaces(player)
    }
    // CUSTOM_SERVER_OVERRIDE (see BeastOfBurden.release): a familiar's cargo is rescued into
    // Death's Domain instead of being lost or floored, so the player is reminded on every login
    // for as long as anything is still waiting there.
    val waiting = BeastOfBurden.deathsDomainCount(player)
    if (waiting > 0) {
        player.message("<col=ff0000>You have items stored at Death's Domain.")
    }
}

on_timer(familiarTickTimer) {
    Familiar.tick(player)
    player.timers[familiarTickTimer] = 1
}

on_logout {
    Familiar.disconnect(player)
}

// Owner death, wired at the same real, existing per-player death hook death.plugin.kts uses for
// DeathResolver/DeathExecutor - kept in this file so the summoning package owns its own lifecycle
// rule instead of touching death.plugin.kts.
//
// This used to call Familiar.dismiss, which drops the beast of burden's cargo on the floor. That
// is the *modern* rule, introduced by the 22 August 2016 ninja strike ("A beast of burden's
// inventory is now dropped to the floor when a player dies"); before it, the cargo was simply
// lost. Familiar.ownerDeath implements this revision's behaviour and documents the switch back.
on_player_pre_death {
    Familiar.ownerDeath(player)
}
// A familiar can also be killed independently of its owner. Release BoB cargo
// at the familiar's death tile and clear the owner's live familiar state.
on_npc_pre_death(*familiarNpcIds) {
    Familiar.onDeath(npc)
}

/*
 * R07.4/R07.5: interface 662 button wiring - components 49/51/65/67/69 are the real,
 * verified-live component ids from the R07.5 evidence trail (OWNER_TASK_STATUS.md). Every
 * handler re-checks Familiar.current(player) first - anti-forgery/stale-click protection so a
 * forged or leftover click after dismiss/expiry/relog can't act on nothing.
 */
on_button(662, 49) { // "Call familiar" (portrait button)
    Familiar.call(player)
}

/*
 * 662:51 carries two real ops in the cache: `ops=[1:'Dismiss Familiar', 2:'Dismiss Now']`. Op 2 is
 * the deliberate "skip the confirmation" shortcut, so it must not re-ask; only op 1 confirms.
 * IF_BUTTON2 arrives as opcode 64, the same mapping the bank's container handlers already use.
 */
on_button(662, 51) {
    if (player.getInteractingOpcode() == 64) {
        Familiar.dismiss(player)
    } else {
        player.queue { confirmDismiss(player) }
    }
}

on_button(662, 69) { // "Renew Familiar"
    player.queue { confirmRenew(player) }
}

on_button(662, 67) { // "Take Beast of Burden items" - capability-gated, see takeBeastOfBurdenItems
    takeBeastOfBurdenItems(player)
}

// Interface-target packets for the follower-details attack action.
on_spell_on_npc(662, 65) {
    FamiliarCombat.commandAttack(player, player.getInteractingNpc())
}
on_spell_on_player(662, 65) {
    FamiliarCombat.commandAttack(player, player.getInteractingPlayer())
}

// Fixed/resizable Summoning-orb attack target actions.
arrayOf(14, 23).forEach { component ->
    on_spell_on_npc(747, component) {
        FamiliarCombat.commandAttack(player, player.getInteractingNpc())
    }
    on_spell_on_player(747, component) {
        FamiliarCombat.commandAttack(player, player.getInteractingPlayer())
    }
}

on_button(662, 65) { // "Order your familiar to attack a target"
    val npc = Familiar.current(player)
    if (npc == null) {
        return@on_button
    }
    // R07.3b update: 72/73 fighting familiars now have real, sourced combat data wired via
    // set_combat_def above and FamiliarCombat - only Albino Rat (SummoningCombatDefinitions.
    // blockedCombatValues) still lacks a sourced attack/death animation. commandAttack (bound to
    // the spell-on-npc/spell-on-player packets, not this button) rejects that one honestly.
    player.message("Select a target for your familiar.")
}

/*
 * R07.7: Summoning orb (interface 747, the minimap globe's right-click submenu) - components
 * verified live this session via a raw cache component-text scan. 747 has two parallel component
 * sets for the same 7 actions depending on client layout mode (fixed-mode ids 9-15, resize-mode
 * ids 18-26 - see the arrayOf pairs below), so each real action is bound once across both ids
 * with Array<Int>.
 *
 * "Follower Details" (9/18) and "Interact" (15/26) are deliberately left unbound - their real
 * server-side effect wasn't sourced this session (662 is a permanently-docked sidebar tab, not
 * something proven to need an explicit server "open" call; "Interact" has no clear distinct
 * meaning from the npc's own interact option). Left honestly unimplemented rather than guessed.
 */
on_button(747, arrayOf(10, 19)) { // "Call Follower"
    if (FamiliarCapabilityTable.activeSupports(player, FamiliarAction.CALL)) {
        Familiar.call(player)
    }
}

on_button(747, arrayOf(11, 20)) { // "Dismiss"
    if (FamiliarCapabilityTable.activeSupports(player, FamiliarAction.DISMISS)) {
        player.queue { confirmDismiss(player) }
    }
}

on_button(747, arrayOf(12, 21)) { // "Take BoB"
    takeBeastOfBurdenItems(player)
}

on_button(747, arrayOf(13, 22)) { // "Renew Familiar"
    player.queue { confirmRenew(player) }
}

on_button(747, arrayOf(14, 23)) { // "Attack" - see R07.3b note on 662's Attack button above.
    if (FamiliarCapabilityTable.activeSupports(player, FamiliarAction.ATTACK)) {
        player.message("Select a target for your familiar.")
    }
}

/*
 * R08 correction: the previous per-binding wiring here bound `on_button`/`on_spell_on_npc`/
 * `on_spell_on_item` at 662/747 component ids like 77, 129, 161, 197... none of which exist -
 * interface 662 only has real components 0-75 and 747 only 0-26 (confirmed this session with a
 * decoder ported from this revision's real client source, see SummoningSpecialMoves.kt's R08
 * doc comment). Every one of those 34 old bindings was dead. The cache's real special-move
 * trigger is a single component - 747:25 ("Spell, Cast", resize-mode only) - used the same
 * spell-cast way as the existing on_spell_on_npc(662, 65) attack binding above: click it to
 * self-cast an INSTANT special, or click it then a target for an NPC/INVENTORY_ITEM special.
 * Which scroll it means is resolved server-side from the player's active familiar
 * (SummoningSpecialMoves.resolveBinding) rather than from any per-scroll id, because no such
 * ids exist in this cache. 662 has no equivalent trigger.
 */
/*
 * 2026-09-07 owner human retest: "Special Move from the main Follower Details GUI does NOTHING."
 * That was true, and 747:25 alone could never have fixed it. The real special-move buttons on
 * both surfaces are *dynamic* components:
 *
 *   `disasm 211` (bound to 662:74 and 747:17 `onLoad`/`onVarTransmit`/`onVarcstrTransmit`, varp
 *   448 / varcstr 205) calls script 606, which does `CC_CREATE` under **662:74** and **747:17**,
 *   and script 608, which gives that child `opbase = "<col=00ff00>" + varcstr 204` (the familiar's
 *   own move name - this is where "Cast Venom Shot" in the orb menu comes from), a target verb of
 *   "Cast", and `op1 = "Cast"` only when varc 1436 is set.
 *
 * A click on a dynamic child is addressed to its parent, with the child index in `slot` (see
 * IfButton1Decoder/IfButton1Handler), so the three parents below are the complete set of real
 * triggers: 662:74 is the follower panel's button, 747:17 the orb's, and 747:25 the orb's baked
 * "Spell"/"Cast" twin. 662 had no binding at all before this, which is exactly the reported fault.
 *
 * Whether the click fires on the spot or starts target selection is not decided here: the server
 * drives varc 1436 from the active familiar's own binding (SummoningUi.refreshSpecialMode), so
 * INSTANT specials get an op1 and targeted ones only get the verb.
 */
arrayOf(
    SummoningUi.PANEL to SummoningUi.PANEL_SPECIAL,
    SummoningUi.ORB to SummoningUi.ORB_SPECIAL,
    SummoningUi.ORB to 25,
).forEach { (parent, component) ->
    on_button(parent, component) {
        SummoningSpecialMoves.castInstant(player)
    }
    on_spell_on_player(parent, component) {
        SummoningSpecialMoves.castOnPlayer(player, player.getInteractingPlayer())
    }
    on_spell_on_npc(parent, component) {
        SummoningSpecialMoves.castOnNpc(player, player.getInteractingNpc())
    }
    on_spell_on_item(parent, component) {
        SummoningSpecialMoves.castOnInventoryItem(player, player.getInteractingItemSlot())
    }
}

/*
 * Owner requirements H1 and H6: "Follower Details" (747:9 / 747:18) and "Interact" (747:15 /
 * 747:26) are no longer orb actions and have **no bindings here at all**.
 *
 * Follower Details moved to the sidebar tab strip - see [FollowerDetailsTab] for why it had to
 * become a real tab button rather than a menu entry. Interact was a duplicate: the familiar's own
 * npc option already opens the same conversation, and it is bound above.
 *
 * Both components are hidden on every orb refresh by [SummoningUi.refreshOrb], because the cache's
 * own script 2671 shows them again whenever the left-click varp is transmitted. Leaving them
 * unbound as well means that even if a client did surface one, it would do nothing.
 */
on_button(FollowerDetailsTab.buttons[0].first, FollowerDetailsTab.buttons[0].second) {
    FollowerDetailsTab.open(player)
}
on_button(FollowerDetailsTab.buttons[1].first, FollowerDetailsTab.buttons[1].second) {
    FollowerDetailsTab.open(player)
}

on_button(747, 7) { // "Select left-click option" (op10 on 747:7, opens 880)
    // Real 2011 behaviour (see SummoningLeftClick.kt): 880's preview varbit (1494) starts the
    // dialog showing whatever is already the real active choice (1493), not blank/unset.
    player.setPendingLeftClickAction(player.leftClickAction())
    player.openInterface(880, InterfaceDestination.MAIN_SCREEN)
}

FamiliarAction.ORDERED.forEach { action ->
    val (graphic, text) = action.selectRow
    on_button(880, arrayOf(graphic, text)) {
        // H7: a row for an action this familiar cannot perform is not selectable. The rows are
        // baked into 880 and cannot be removed, so the guard is here as well as in the redraw.
        if (action !in player.selectableActions()) {
            return@on_button
        }
        // Real 2011 behaviour: selecting a row live-updates 880's own preview icon (component 3,
        // driven by the client's own onVarTransmit redraw off the varbit this writes) - the
        // interface shows the highlight itself, there is no chatbox feedback for this step.
        player.setPendingLeftClickAction(action)
    }
}

on_button(880, 21) { // "Confirm Selection"
    player.confirmLeftClickAction()
    player.closeInterface(880)
}

/*
 * Interface 671 - the real graphical "Familiar Inventory" window. The previous blocker note here
 * ("no real cache trigger to OPEN 671 was sourced", "the item-slot grid ... isn't decodable")
 * is resolved: the open trigger is the familiar's own third cache option (Store / Withdraw, wired
 * above) and the grid is 671:27, a 6x5 = 30-cell layer whose dimensions are provable from the
 * divider graphics' baked positions. See [FamiliarInventory] for the whole evidence trail.
 */
on_button(671, FamiliarInventory.CLOSE_COMPONENT) { // "Close"
    FamiliarInventory.close(player)
}

on_interface_close(671) {
    FamiliarInventory.onClosed(player)
}

on_button(671, FamiliarInventory.TAKE_BOB_COMPONENT) { // "Take BoB, Take Beast of Burden items."
    takeBeastOfBurdenItems(player)
}

/*
 * Withdrawing: a click on the familiar's own 6x5 grid. Opcode -> amount uses exactly the same
 * mapping the bank's container handlers already use in this codebase (61/64/4 = ops 1..3,
 * 91 = "-All", 81 = "-X"), so the meaning of each opcode is established by working code.
 */
on_button(671, FamiliarInventory.GRID_COMPONENT) p@{
    if (!FamiliarInventory.isOpen(player)) {
        return@p
    }
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val container = BeastOfBurden.activeContainer(player) ?: return@p
    if (slot !in 0 until container.capacity) {
        return@p
    }
    val item = container[slot] ?: return@p
    if (opcode == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }
    if (FamiliarInventory.isEnterAmount(opcode)) {
        player.queue(TaskPriority.WEAK) {
            val amount = inputInt("How many would you like to withdraw?")
            if (amount > 0) BeastOfBurden.withdraw(player, slot, amount)
        }
        return@p
    }
    val amount = FamiliarInventory.amountFor(opcode, item.amount)
    if (amount > 0) BeastOfBurden.withdraw(player, slot, amount)
}

/* Storing: a click on the backpack panel that sits alongside the window. */
on_button(665, FamiliarInventory.SIDE_COMPONENT) p@{
    if (!FamiliarInventory.isOpen(player)) {
        return@p
    }
    val slot = player.getInteractingSlot()
    val opcode = player.getInteractingOpcode()
    val item = player.inventory[slot] ?: return@p
    if (opcode == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }
    if (FamiliarInventory.isEnterAmount(opcode)) {
        player.queue(TaskPriority.WEAK) {
            val amount = inputInt("How many would you like to store?")
            if (amount > 0) BeastOfBurden.deposit(player, Item(item.id, minOf(amount, player.inventory.getItemCount(item.id))))
        }
        return@p
    }
    val available = player.inventory.getItemCount(item.id)
    val amount = FamiliarInventory.amountFor(opcode, available)
    if (amount > 0) BeastOfBurden.deposit(player, Item(item.id, minOf(amount, available)))
}
