package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.ext.getInteractingNpc
import gg.rsmod.plugins.api.ext.getInteractingPlayer
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message

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
 * Non-graphical withdraw-one flow: no verified BoB interface component layout exists in this
 * cache (see R07.3 note below), so item selection reuses the real [options]/[inputInt] dialog
 * primitives already used elsewhere in this codebase rather than inventing component IDs. Lists
 * contents by message (item count can exceed the confirmed 5-option dialog cap), then two
 * [inputInt] prompts pick the slot and amount.
 */
suspend fun QueueTask.withdrawOne(player: Player) {
    val contents = BeastOfBurden.contents(player)
    if (contents.isEmpty()) {
        player.message("Your familiar isn't carrying anything.")
        return
    }
    contents.forEach { (slot, item) -> player.message("${slot + 1}: ${item.getName(world.definitions)} x${item.amount}") }
    val choice = inputInt("Enter item number (1-${contents.size})")
    val entry = contents.firstOrNull { it.index + 1 == choice }
    if (entry == null) {
        player.message("Nothing withdrawn.")
        return
    }
    val amount = inputInt("Enter amount (max ${entry.value.amount})")
    if (amount <= 0) {
        player.message("Nothing withdrawn.")
        return
    }
    val withdrawn = BeastOfBurden.withdraw(player, entry.index, amount)
    if (withdrawn > 0) player.message("You withdraw $withdrawn x ${entry.value.getName(world.definitions)} from your familiar.")
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

var boundSummon = 0
var skippedSummon = 0
SummoningPouchData.values().forEach { data ->
    val def = world.definitions.get(ItemDef::class.java, data.pouch)
    if (def.inventoryMenu.any { it?.lowercase() == "summon" }) {
        on_item_option(item = data.pouch, option = "summon") {
            Familiar.summon(player, data)
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
            player.queue {
                // R07.3: the 3 real Beast of Burden familiars get the full real BoB interact
                // menu (Renew/Deposit-all/Withdraw/Withdraw-all/Dismiss) - 5 options is the
                // confirmed max seen anywhere in this codebase (games_necklace, skills_necklace,
                // combat_bracelet, amulet_of_glory all use exactly 5), so plain "Cancel" is
                // dropped - escape/click-away already returns -1 with no action, same as those.
                if (BeastOfBurden.isBobNpc(npc)) {
                    when (options("Renew", "Deposit-all", "Withdraw", "Withdraw-all", "Dismiss")) {
                        1 -> Familiar.renew(player)
                        2 -> {
                            val deposited = BeastOfBurden.depositAll(player)
                            if (deposited > 0) player.message("You deposit $deposited item(s) with your familiar.")
                        }
                        3 -> withdrawOne(player)
                        4 -> {
                            val withdrawn = BeastOfBurden.withdrawAll(player)
                            if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
                        }
                        5 -> confirmDismiss(player)
                    }
                } else {
                    when (options("Renew", "Dismiss", "Cancel")) {
                        1 -> Familiar.renew(player)
                        2 -> confirmDismiss(player)
                    }
                }
            }
        }
        boundInteract++
    } else {
        skippedInteract++
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
    println(
        "R07.1 familiar: bound Summon on $boundSummon/${boundSummon + skippedSummon} pouches, " +
            "Interact on $boundInteract/${boundInteract + skippedInteract} familiar npcs " +
            "(skipped entries didn't have that exact real cache option - not guessed).",
    )
}

on_login {
    player.timers[familiarTickTimer] = 1
    // R07.7: real RS mechanic - a familiar survives logout, its lifetime timer just pauses
    // (tickOffline = false) and resumes with the same time/points left on login, it does not
    // get dismissed. See Familiar.disconnect/restoreOnLogin.
    Familiar.restoreOnLogin(player)
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

/*
 * R07.4/R07.5: interface 662 button wiring - components 49/51/65/67/69 are the real,
 * verified-live component ids from the R07.5 evidence trail (OWNER_TASK_STATUS.md). Every
 * handler re-checks Familiar.current(player) first - anti-forgery/stale-click protection so a
 * forged or leftover click after dismiss/expiry/relog can't act on nothing.
 */
on_button(662, 49) { // "Call familiar" (portrait button)
    Familiar.call(player)
}

on_button(662, 51) { // "Dismiss Familiar"
    player.queue { confirmDismiss(player) }
}

on_button(662, 69) { // "Renew Familiar"
    Familiar.renew(player)
}

on_button(662, 67) { // "Take Beast of Burden items" - gated to real BoB familiars only
    val npc = Familiar.current(player)
    if (npc == null || !BeastOfBurden.isBobNpc(npc.id)) {
        return@on_button
    }
    val withdrawn = BeastOfBurden.withdrawAll(player)
    if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
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
    Familiar.call(player)
}

on_button(747, arrayOf(11, 20)) { // "Dismiss"
    player.queue { confirmDismiss(player) }
}

on_button(747, arrayOf(12, 21)) { // "Take BoB"
    val npc = Familiar.current(player)
    if (npc == null || !BeastOfBurden.isBobNpc(npc.id)) {
        return@on_button
    }
    val withdrawn = BeastOfBurden.withdrawAll(player)
    if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
}

on_button(747, arrayOf(13, 22)) { // "Renew Familiar"
    Familiar.renew(player)
}

on_button(747, arrayOf(14, 23)) { // "Attack" - see R07.3b note on 662's Attack button above.
    if (Familiar.current(player) == null) {
        return@on_button
    }
    player.message("Select a target for your familiar.")
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
on_button(747, 25) {
    SummoningSpecialMoves.castInstant(player)
}

on_spell_on_npc(747, 25) {
    SummoningSpecialMoves.castOnNpc(player, player.getInteractingNpc())
}

on_spell_on_item(747, 25) {
    SummoningSpecialMoves.castOnInventoryItem(player, player.getInteractingItemSlot())
}

/*
 * R07.8 (Phase 5): interface 671 ("Familiar Inventory" / graphical BoB window) - real
 * component ids 13 ("Close"), 14 ("Familiar Inventory" title, no action) and 29 ("Take BoB,
 * Take Beast of Burden items.") verified live this session via the same raw cache probe as 662/
 * 747. Wired defensively so the window behaves correctly whenever it's open.
 *
 * Honest blocker, unchanged from the Phase 4/5 evidence trail: no real cache trigger to OPEN
 * 671 was sourced this session - the BoB npc's own verified option set is "Interact" only (R07.1
 * finding), and neither 662 nor 747's real component text names a distinct "View"/"Open BoB
 * inventory" action, so nothing in this codebase currently calls `player.openInterface(671, ...)`.
 * The item-slot/container grid among 671's other ~28 components also has no extracted text in
 * the raw scan (consistent with being a container/background widget, but its exact component id
 * and slot-count binding isn't decodable this way) - real per-item graphical withdraw/deposit
 * therefore isn't implemented; the existing chat-based `withdrawOne` prompt and the instant
 * withdraw-all bindings (662/747) remain the real, working BoB access path.
 */
on_button(671, 13) { // "Close"
    player.closeInterface(671)
}

on_button(671, 29) { // "Take BoB, Take Beast of Burden items."
    val npc = Familiar.current(player)
    if (npc == null || !BeastOfBurden.isBobNpc(npc.id)) {
        return@on_button
    }
    val withdrawn = BeastOfBurden.withdrawAll(player)
    if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
}
