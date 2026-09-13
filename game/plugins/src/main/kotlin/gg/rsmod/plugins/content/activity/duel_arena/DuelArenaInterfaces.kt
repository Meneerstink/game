package gg.rsmod.plugins.content.activity.duel_arena

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.INTERFACE_INV_INIT_BIG
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.INVENTORY_INTERFACE_KEY
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.removeOption
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.sendItemContainer
import gg.rsmod.plugins.api.ext.sendItemContainerOther
import gg.rsmod.plugins.api.ext.sendOption
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarcString
import gg.rsmod.plugins.api.ext.setVarp
import java.lang.ref.WeakReference

/**
 * RCV-010 C2-a: native revision-667 Duel Arena screens. Every interface, component, container key, script, varp and
 * message below is Novite 667 `controlers/impl/DuelArena.java`, `DuelControler.java`, `DuelRules.java`,
 * `ButtonHandler` (interface 640) and `DefaultGameEncoder` (message type 101); the rule components themselves are the
 * cache-verified ids in [DuelRule]/[DuelEquipLock].
 */
object DuelArenaInterfaces {
    const val CHALLENGE_SCREEN = 640
    const val STAKE_RULES = 631
    const val FRIENDLY_RULES = 637
    const val STAKE_INVENTORY = 628
    const val STAKE_CONFIRM = 626
    const val FRIENDLY_CONFIRM = 639
    const val SPOILS = 634
    const val LOBBY_OVERLAY = 638

    const val STAKE_CONTAINER_KEY = 134
    const val SPOILS_CONTAINER_KEY = 136
    const val TARGET_NAME_VARCSTR = 274
    const val RULES_VARP = 286
    const val CHALLENGE_TYPE_VARP = 283
    const val CHALLENGE_FRIENDLY = 67108864
    const val CHALLENGE_STAKE = 134217728
    const val SPOILS_SCRIPT = 149

    const val STAKE_ACCEPT = 46
    /** 667 cache 631:51 op1 'Decline' (Novite's 107 is not a 631 button). */
    const val STAKE_DECLINE = 51
    const val STAKE_REMOVE_COMPONENT = 47
    const val FRIENDLY_ACCEPT = 21
    /** 667 cache 637:22 op1 'Decline' (Novite's 86 is not a 637 button). */
    const val FRIENDLY_DECLINE = 22
    const val STAKE_CONFIRM_ACCEPT = 43
    const val FRIENDLY_CONFIRM_ACCEPT = 25
    /** 667 cache 626:45 / 639:27 op1 'Decline' on the confirmation screens. */
    const val STAKE_CONFIRM_DECLINE = 45
    const val FRIENDLY_CONFIRM_DECLINE = 27
    /** 667 cache 634:35 is the spoils item layer (Novite used 28); 634:17 op1 'Claim'. */
    const val SPOILS_ITEMS = 35
    const val SPOILS_CLAIM = 17
    val CHALLENGE_FRIENDLY_BUTTONS = intArrayOf(18, 22)
    val CHALLENGE_STAKE_BUTTONS = intArrayOf(19, 21)
    const val CHALLENGE_SEND = 20

    /** Novite `sendUnlockIComponentOptionSlots(..., 0, 1, 2, 3, 4, 5)`: `2 << slot` for each option. */
    val OPTIONS_0_TO_5 = (0..5).fold(0) { hash, slot -> hash or (2 shl slot) }

    val CHALLENGE_TARGET_ATTR = AttributeKey<WeakReference<Player>>()
    val CHALLENGE_FRIENDLY_ATTR = AttributeKey<Boolean>()
    val CHALLENGED_BY_ATTR = AttributeKey<WeakReference<Player>>()
    val CHALLENGED_FRIENDLY_ATTR = AttributeKey<Boolean>()
    val IN_LOBBY_ATTR = AttributeKey<Boolean>()

    /** Novite `DuelControler.isAtDuelArena`: `inArea(3341, 3265, 3387, 3281)`. */
    fun inLobby(tile: Tile): Boolean = tile.height == 0 && tile.x in 3341..3387 && tile.z in 3265..3281

    /** Novite `DuelRules` index of every rule (the varp 286 bit order). */
    fun noviteRuleIndex(rule: DuelRule): Int =
        when (rule) {
            DuelRule.NO_RANGED -> 0
            DuelRule.NO_MELEE -> 1
            DuelRule.NO_MAGIC -> 2
            DuelRule.NO_DRINKS -> 3
            DuelRule.NO_FOOD -> 4
            DuelRule.NO_PRAYER -> 5
            DuelRule.OBSTACLES -> 6
            DuelRule.NO_FORFEIT -> 7
            DuelRule.FUN_WEAPONS -> 8
            DuelRule.NO_SPECIAL_ATTACKS -> 9
            DuelRule.ENABLE_SUMMONING -> 24
            DuelRule.NO_MOVEMENT -> 25
        }

    /** Equipment locks are Novite rule `10 + equipment slot`. */
    fun noviteRuleIndex(lock: DuelEquipLock): Int = 10 + lock.slot.id

    /** Novite `DuelRules.setConfigs`: bit `16 << index` per active rule, plus 5 for rule 7 and 6 for rule 25. */
    fun rulesVarp(match: DuelArenaMatch): Int {
        val active = match.rules.map { noviteRuleIndex(it) } + match.lockedSlots.map { noviteRuleIndex(it) }
        return active.distinct().sumOf { index ->
            (16 shl index) + when (index) {
                7 -> 5
                25 -> 6
                else -> 0
            }
        }
    }

    fun ruleForComponent(interfaceId: Int, component: Int): DuelRule? =
        DuelRule.values().firstOrNull { (if (interfaceId == STAKE_RULES) it.id631 else it.id637) == component }

    fun lockForComponent(interfaceId: Int, component: Int): DuelEquipLock? =
        DuelEquipLock.values().firstOrNull { (if (interfaceId == STAKE_RULES) it.id631 else it.id637) == component }

    fun rulesInterface(match: DuelArenaMatch) = if (match.friendly) FRIENDLY_RULES else STAKE_RULES

    fun confirmInterface(match: DuelArenaMatch) = if (match.friendly) FRIENDLY_CONFIRM else STAKE_CONFIRM

    /** Lobby entry/exit: the "Challenge" player option and the lobby overlay (Novite `DuelControler.start/remove`). */
    fun refreshLobby(player: Player) {
        val inside = inLobby(player.tile)
        val was = player.attr[IN_LOBBY_ATTR] == true
        if (inside == was) return
        player.attr[IN_LOBBY_ATTR] = inside
        if (inside) {
            player.sendOption("Challenge", 1)
            player.openInterface(interfaceId = LOBBY_OVERLAY, dest = InterfaceDestination.PVP_OVERLAY)
        } else {
            player.removeOption(1)
            player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        }
    }

    /** Novite `canPlayerOption1` without a pending challenge: open the friendly/stake choice (640). */
    fun openChallengeScreen(player: Player, target: Player) {
        player.attr[CHALLENGE_TARGET_ATTR] = WeakReference(target)
        player.attr[CHALLENGE_FRIENDLY_ATTR] = true
        player.openInterface(CHALLENGE_SCREEN, InterfaceDestination.MAIN_SCREEN)
        player.setVarp(CHALLENGE_TYPE_VARP, CHALLENGE_FRIENDLY)
    }

    fun chooseChallengeType(player: Player, friendly: Boolean) {
        player.attr[CHALLENGE_FRIENDLY_ATTR] = friendly
        player.setVarp(CHALLENGE_TYPE_VARP, if (friendly) CHALLENGE_FRIENDLY else CHALLENGE_STAKE)
    }

    /** Novite `DuelControler.challenge`. Returns true when the request was sent. */
    fun sendChallenge(player: Player): Boolean {
        player.closeInterface(CHALLENGE_SCREEN)
        val friendly = player.attr[CHALLENGE_FRIENDLY_ATTR] ?: return false
        val target = player.attr[CHALLENGE_TARGET_ATTR]?.get()
        player.attr.remove(CHALLENGE_FRIENDLY_ATTR)
        player.attr.remove(CHALLENGE_TARGET_ATTR)
        if (target == null || !target.isOnline || !target.tile.isWithinRadius(player.tile, 14) || !inLobby(target.tile)) {
            player.message("Unable to find ${target?.let { it.username } ?: "your target"}")
            return false
        }
        target.attr[CHALLENGED_BY_ATTR] = WeakReference(player)
        target.attr[CHALLENGED_FRIENDLY_ATTR] = friendly
        player.message("Sending ${target.username} a request...")
        target.message("wishes to duel with you(${if (friendly) "friendly" else "stake"}).", ChatMessageType.DUEL_REQ, player.username)
        return true
    }

    /** Opens the rules (and, for a staked duel, stake) screens for [player] (Novite `openDuelScreen`/`sendOptions`). */
    fun openRules(match: DuelArenaMatch, player: Player) {
        val other = match.other(player)
        val rulesInterface = rulesInterface(match)
        if (!match.friendly) {
            player.openInterface(STAKE_INVENTORY, InterfaceDestination.TAB_AREA)
            player.setInterfaceEvents(interfaceId = STAKE_INVENTORY, component = 0, range = 0..27, setting = OPTIONS_0_TO_5)
            player.runClientScript(INTERFACE_INV_INIT_BIG, (STAKE_INVENTORY shl 16) or 0, INVENTORY_INTERFACE_KEY, 4, 7, 0, -1,
                "Stake 1", "Stake 5", "Stake 10", "Stake All", "Stake X", "Examine")
            player.setInterfaceEvents(interfaceId = STAKE_RULES, component = STAKE_REMOVE_COMPONENT, range = 0..27, setting = OPTIONS_0_TO_5)
            player.runClientScript(INTERFACE_INV_INIT_BIG, (STAKE_RULES shl 16) or 0, 120, 4, 7, 0, -1,
                "Remove 1", "Remove 5", "Remove 10", "Remove All", "Remove X", "Examine")
        }
        player.setVarcString(TARGET_NAME_VARCSTR, " " + other.username)
        player.setComponentText(rulesInterface, if (match.friendly) 18 else 40, "${other.combatLevel}")
        player.setVarp(RULES_VARP, rulesVarp(match))
        player.openInterface(rulesInterface, InterfaceDestination.MAIN_SCREEN)
        refreshStakes(match)
        refreshStatus(match, player)
    }

    fun refreshStakes(match: DuelArenaMatch) {
        listOf(match.challenger, match.opponent).forEach { player ->
            val other = match.other(player)
            player.sendItemContainer(STAKE_CONTAINER_KEY, match.stakeOf(player))
            player.sendItemContainerOther(STAKE_CONTAINER_KEY, match.stakeOf(other))
            player.sendItemContainer(INVENTORY_INTERFACE_KEY, player.inventory)
            player.setVarp(RULES_VARP, rulesVarp(match))
        }
    }

    /** Novite `getAcceptMessage`. */
    fun statusText(match: DuelArenaMatch, player: Player): String =
        when {
            match.isAccepted(match.other(player)) -> "Other player has accepted."
            match.isAccepted(player) -> "Waiting for other player..."
            match.confirming -> "Please look over the agreements to the duel."
            else -> ""
        }

    fun refreshStatus(match: DuelArenaMatch, player: Player) {
        val (interfaceId, component) =
            if (match.confirming) {
                confirmInterface(match) to if (match.friendly) 23 else 35
            } else {
                rulesInterface(match) to if (match.friendly) 20 else 41
            }
        player.setComponentText(interfaceId, component, "<col=ff0000>" + statusText(match, player))
    }

    /** Novite `openConfirmationScreen`. */
    fun openConfirmation(match: DuelArenaMatch) {
        match.confirming = true
        match.resetAccepted()
        listOf(match.challenger, match.opponent).forEach { player ->
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
            player.openInterface(confirmInterface(match), InterfaceDestination.MAIN_SCREEN)
            if (!match.friendly) {
                player.setComponentText(STAKE_CONFIRM, 25, confirmationStakeText(match.stakeOf(player).rawItems.any { it != null }))
                player.setComponentText(STAKE_CONFIRM, 26, confirmationStakeText(match.stakeOf(match.other(player)).rawItems.any { it != null }))
            }
            refreshStatus(match, player)
        }
    }

    fun confirmationStakeText(hasStake: Boolean): String = if (hasStake) "" else "Absolutely nothing!"

    /** Staking from the 628 inventory screen, only while configuring the first screen of a staked duel. */
    fun addStake(match: DuelArenaMatch, player: Player, slot: Int, requested: Int): Boolean {
        if (match.stage != DuelStage.CONFIGURING || match.confirming || match.friendly || requested <= 0) return false
        val item = player.inventory[slot] ?: return false
        if (!player.world.definitions.get(ItemDef::class.java, item.id).tradeable) {
            player.message("That item cannot be staked!")
            return false
        }
        val count = minOf(requested, player.inventory.getItemCount(item.id))
        val removed = player.inventory.remove(item.id, count, assureFullRemoval = false, beginSlot = slot).completed
        if (removed <= 0) return false
        val added = match.stakeOf(player).add(item.id, removed).completed
        if (added < removed) player.inventory.add(item.id, removed - added)
        match.resetAccepted()
        return added > 0
    }

    fun removeStake(match: DuelArenaMatch, player: Player, slot: Int, requested: Int): Boolean {
        if (match.stage != DuelStage.CONFIGURING || match.confirming || requested <= 0) return false
        val stake = match.stakeOf(player)
        val item = stake[slot] ?: return false
        val count = minOf(requested, stake.getItemCount(item.id))
        val removed = stake.remove(item.id, count, assureFullRemoval = false, beginSlot = slot).completed
        if (removed <= 0) return false
        val added = player.inventory.add(item.id, removed).completed
        if (added < removed) stake.add(item.id, removed - added)
        match.resetAccepted()
        return added > 0
    }

    /** Novite `endDuel`: the victor sees the loser's stake on 634 (container 136, script 149). */
    fun showSpoils(winner: Player, loser: Player, spoils: gg.rsmod.game.model.container.ItemContainer) {
        winner.openInterface(SPOILS, InterfaceDestination.MAIN_SCREEN)
        winner.setInterfaceEvents(interfaceId = SPOILS, component = SPOILS_ITEMS, range = 0..28, setting = 1026)
        winner.runClientScript(SPOILS_SCRIPT, (SPOILS shl 16) or SPOILS_ITEMS, SPOILS_CONTAINER_KEY, 6, 6, 0, -1, "", "", "", "", "")
        winner.sendItemContainer(SPOILS_CONTAINER_KEY, spoils)
        winner.setVarcString(TARGET_NAME_VARCSTR, " " + loser.username)
        winner.setComponentText(SPOILS, 32, "${loser.combatLevel}")
    }
}
