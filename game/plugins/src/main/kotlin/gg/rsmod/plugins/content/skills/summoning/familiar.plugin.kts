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
    /*
     * Owner failures F4/F5: the familiar's chatbox model behaviour was wrong.
     *
     * All 78 familiars do have a chathead model in this cache (`FamiliarChatheadTests`), so the
     * portrait itself was never the problem - what was wrong is what it was being animated with.
     * `chatNpc` defaults to [FacialExpression.HAPPY_TALKING], and every named expression in that
     * enum is a `97xx`/`98xx` **humanoid** facial-expression sequence. A Spirit wolf's head has no
     * frames for a human expression rig, so the portrait was being driven by a sequence built for
     * a different model entirely.
     *
     * The correct sequence is Void's own shared familiar-details route: the familiar selector from
     * varbit 4282 keys cache enum 1276, or enum 1275 with 50 subtracted for values above 50. That is
     * the same route clientscript 751 uses for interface 662, and it supplies chathead sequences
     * rather than world BAS animations. Phoenix is absent from Void's selector map, so it stays on
     * [FacialExpression.NONE] instead of receiving a guessed substitute.
     */
    // `chatNpc`/`chatPlayer` default to `wrap = false`, i.e. exactly one dialogue line however
    // long the text is. The sourced Knowledge Base conversation lines routinely run well past what
    // that one line fits, and with no wrapping the overflow was simply clipped by the interface
    // rather than continued on a further line - the "text doesn't get overridden" report this
    // round. `wrap = true` is the same fix every other long NPC dialogue in this codebase already
    // uses (see e.g. town_crier.plugin.kts): it splits the text with `TextWrapping.wrap` first and
    // picks the dialogue interface with enough lines for the result.
    val chatheadAnimation = SummoningUi.resolveChatheadAnimation(player, npcId)
    conversations.random().forEach { line ->
        when {
            line.speaker == SummoningDialogueData.Speaker.PLAYER -> chatPlayer(line.speech, wrap = true)
            line.translation.isEmpty() || !understands ->
                chatNpc(
                    line.speech,
                    npc = npcId,
                    facialExpression = FacialExpression.NONE,
                    animationOverride = chatheadAnimation,
                    wrap = true,
                )
            else ->
                chatNpc(
                    line.speech,
                    line.translation,
                    npc = npcId,
                    facialExpression = FacialExpression.NONE,
                    animationOverride = chatheadAnimation,
                    wrap = true,
                )
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
                // The sourced familiar ledger stores lifepoints on the historical x10 scale, same
                // as every other hand-written NpcCombatDsl call site - NpcCombatDsl.stats{} itself
                // is the single shared runtime boundary that converts to the 1:1 hitpoints unit.
                // (Previously this line ALSO divided by 10 before handing the value to stats{},
                // which then divided by 10 again - every familiar's live HP was 10x too low, e.g.
                // a familiar with a sourced/real 150 HP was published with 15, and anything with
                // real HP below 10 was floored to 1. Caught by NpcCombatDsl's new require() guard,
                // which throws on this file's already-divided values because they are frequently
                // not themselves multiples of ten.)
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
                magicDamageBonus = definition.maxHit
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
    // H6 removed varbit-6454 values 0 and 7 from the orb; an account still holding one of them
    // would otherwise have no left-click action at all. Runs before applyLeftClickAction so the
    // varp transmitted below already carries the corrected value.
    player.migrateRemovedDefault()
    player.applyLeftClickAction()
    // The client is freshly rebuilt at this point and holds none of the IF_SETHIDE state the
    // server last sent, so the panel/orb gating has to be re-sent unconditionally.
    Familiar.redrawInterfaces(player)
    // RCV-010 A4: the same restore runs after every gameframe top-level rebuild (GameframeRebuild), e.g. closing the
    // world map, not only on login and window-mode switches.
    gg.rsmod.plugins.api.ext.GameframeRebuild.register("summoning") { p ->
        Familiar.redrawInterfaces(p)
        SummoningUi.restorePanel(p)
        FollowerDetailsTab.install(p)
    }
    // Owner requirement (2026-09-09): the Follower Details tab is a permanent fixture, armed the
    // same way regardless of whether a familiar is currently out - see FollowerDetailsTab.
    // Mount its empty-or-populated panel as well; focusTab(95) cannot open an unmounted slot.
    SummoningUi.restorePanel(player)
    FollowerDetailsTab.install(player)
    // ...and again a few cycles in. The gameframe is still being assembled while login runs, and
    // every component an interface (re)builds comes back with its baked hidden flag, which would
    // silently undo the first pass. Cheap, one-shot, and it makes the login path behave like the
    // mid-session path rather than being a special case.
    player.queue {
        wait(5)
        Familiar.redrawInterfaces(player)
        SummoningUi.restorePanel(player)
        FollowerDetailsTab.install(player)
    }
    // CUSTOM_SERVER_OVERRIDE (see BeastOfBurden.release): a familiar's cargo is rescued into
    // Death's Domain instead of being lost or floored, so the player is reminded on every login
    // for as long as anything is still waiting there.
    val waiting = BeastOfBurden.deathsDomainCount(player)
    if (waiting > 0) {
        player.message("<col=ff0000>You have items stored at Death's Domain.")
    }
}

// Owner requirement (2026-09-09): the Follower Details tab button, in both layout modes - see
// FollowerDetailsTab. Always opens the panel slot; what that slot currently shows (a familiar, or
// genuinely empty) is SummoningUi.refreshPanel's job, not this handler's.
FollowerDetailsTab.clickTargets.forEach { (pane, button) ->
    on_button(pane, button) {
        FollowerDetailsTab.open(player)
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
 * Owner requirements: "Follower Details" (747:9 / 747:18) and "Interact" (747:15 / 747:26) are
 * not orb actions and have **no bindings here at all**.
 *
 * Follower Details has no entry point that is clicked at all any more - not on the orb, not as a
 * tab, not as a tab button. Its panel is mounted on gameframe slot 95 and simply appears in the
 * sidebar while a familiar is active; see [SummoningUi.showPanel]. Interact was a duplicate: the
 * familiar's own npc option already opens the same conversation, and it is bound above.
 *
 * Both components are hidden on every orb refresh by [SummoningUi.refreshOrb], because the cache's
 * own script 2671 shows them again whenever the left-click varp is transmitted. Leaving them
 * unbound as well means that even if a client did surface one, it would do nothing.
 */

/** The most rows the game's own "Select an Option" chatbox dialogue can draw (interface 234). */
val MAX_DIALOGUE_OPTIONS = 5

on_button(747, 7) { // "Select left-click option" - op10 on 747:7, an IF_BUTTON10 with no onOp
    /*
     * 747:7 has an `onVarTransmit` hook and no `onOp`, so the client does not open anything
     * itself: op10 is delivered to the server and the server decides what to show.
     *
     * ## Why interface 880 is not used, even though it is real cache content
     *
     * 880 is genuine: eight `op1='Select'` rows and a `Confirm Selection` button. It was tried
     * twice - first as a modal across the game view, then in the sidebar panel region its own
     * `190x261` root asks for - and the owner rejected it both times as a "fake custom
     * radio-button GUI" that "must be REMOVED completely".
     *
     * The rejection is not really about where it was drawn. 880's eight rows are **baked**, and
     * two of them are "Follower details" and "Interact", which requirement G4 says must never be
     * offered. A server cannot delete a baked row, so the previous attempt could only refuse the
     * click afterwards - the player still saw, and could still click, two entries that do
     * nothing, plus rows for capabilities their familiar does not have. An interface that always
     * advertises the wrong options is the fault, and no placement fixes it.
     *
     * So the choice is made through the game's own "Select an Option" chatbox dialogue instead -
     * stock content (interfaces 228..234 mounted on 752:13, the same dialogue every shop and
     * quest uses), not a Summoning-specific window - and its rows come from
     * [Player.selectableActions], so it offers exactly the actions the *current* familiar can
     * perform and nothing else.
     */
    player.queue { chooseLeftClickAction(player) }
}

/**
 * Asks which action a plain left-click on the orb should perform, and stores the answer.
 *
 * The chatbox dialogue holds at most five rows, and a fully-capable familiar - a Pack yak, say,
 * which fights, carries and has a special move - has all six. The overflow is paged rather than
 * truncated, because silently dropping the sixth action would be the same class of fault as
 * offering two that do not exist.
 */
suspend fun QueueTask.chooseLeftClickAction(player: Player) {
    val available = player.selectableActions()
    if (available.isEmpty()) {
        return
    }
    var offset = 0
    while (true) {
        val remaining = available.drop(offset)
        val paged = remaining.size > MAX_DIALOGUE_OPTIONS
        val shown = if (paged) remaining.take(MAX_DIALOGUE_OPTIONS - 1) else remaining
        val labels = shown.map { it.label } + if (paged) listOf("More options...") else emptyList()
        val choice = options(*labels.toTypedArray(), title = "Select left-click option")
        if (choice < 1 || choice > labels.size) {
            return
        }
        if (paged && choice == labels.size) {
            offset += shown.size
            continue
        }
        val action = shown[choice - 1]
        player.setLeftClickAction(action)
        player.message("Left-click on your Summoning orb will now <col=255>${action.label}</col>.")
        return
    }

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
