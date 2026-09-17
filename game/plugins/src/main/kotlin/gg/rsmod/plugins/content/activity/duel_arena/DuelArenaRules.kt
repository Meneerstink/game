package gg.rsmod.plugins.content.activity.duel_arena

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.MOVEMENT_RESTRICTION_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction
import gg.rsmod.plugins.content.skills.summoning.Familiar

/**
 * RCV-010 C2: Duel Arena rule enforcement and match lifecycle. Source: Novite 667
 * `controlers/impl/DuelArena.java` + `DuelRules.java` (rule semantics, validation, countdown, placement, payout and
 * every message, kept verbatim including its spelling).
 */
object DuelArenaRules {
    /** Novite `FUN_WEAPONS = { new Item(4566) }` (rubber chicken). */
    const val FUN_WEAPON = 4566

    val CAN_FIGHT_ATTR = AttributeKey<Boolean>()

    const val NO_MOVEMENT_MESSAGE = "You cannot move during this duel!"

    fun fighting(player: Player): DuelArenaMatch? = player.getDuelMatch()?.takeIf { it.stage == DuelStage.FIGHTING }

    /** [gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions] rule for players in a duel. */
    fun activityRefusal(player: Player, action: RestrictedAction): String? {
        val match = fighting(player) ?: return null
        val rules = match.rules
        return when (action) {
            RestrictedAction.EAT -> "You cannot eat during this duel.".takeIf { DuelRule.NO_FOOD in rules }
            RestrictedAction.DRINK -> "You cannot drink during this duel.".takeIf { DuelRule.NO_DRINKS in rules }
            RestrictedAction.PRAYER -> "You can't use prayers in this duel.".takeIf { DuelRule.NO_PRAYER in rules }
            RestrictedAction.SPECIAL_ATTACK -> "You can't use special attacks in this duel.".takeIf { DuelRule.NO_SPECIAL_ATTACKS in rules }
            RestrictedAction.TELEPORT -> "A magical force prevents you from teleporting from the arena."
            RestrictedAction.SUMMON -> "Summoning has been disabled during this duel!".takeIf { DuelRule.ENABLE_SUMMONING !in rules }
        }
    }

    /** Novite `keepCombating`. */
    fun attackRefusal(attacker: Player, target: Pawn, style: CombatClass, weaponId: Int): String? {
        val match = fighting(attacker) ?: return null
        if (attacker.attr[CAN_FIGHT_ATTR] != true) return "The duel hasn't started yet."
        if (target !== match.other(attacker)) {
            return "You may only attack your target, you can find your target by following the hint icon on your map."
        }
        val rules = match.rules
        return when {
            style == CombatClass.MAGIC && DuelRule.NO_MAGIC in rules -> "You cannot use Magic in this duel!"
            style == CombatClass.RANGED && DuelRule.NO_RANGED in rules -> "You cannot use Range in this duel!"
            style == CombatClass.MELEE && DuelRule.NO_MELEE in rules -> "You cannot use Melee in this duel!"
            DuelRule.FUN_WEAPONS in rules && weaponId != FUN_WEAPON -> "You can only use fun weapons in this duel!"
            else -> null
        }
    }

    /** Novite `canEquip`: a locked slot cannot be equipped during the fight. */
    fun equipRefusal(player: Player, itemId: Int): String? {
        val match = fighting(player) ?: return null
        val def = player.world.definitions.get(ItemDef::class.java, itemId)
        if (match.lockedSlots.none { it.slot.id == def.equipSlot }) return null
        return "You can't equip ${def.name.lowercase()} during this duel."
    }

    /** Novite rule buttons 63/64: No Movement and Obstacles exclude each other. Returns the messages to send. */
    fun toggleRule(match: DuelArenaMatch, rule: DuelRule): List<String> {
        if (rule in match.rules) match.rules.remove(rule) else match.rules.add(rule)
        val messages = mutableListOf<String>()
        if (rule == DuelRule.NO_MOVEMENT && DuelRule.OBSTACLES in match.rules) {
            match.rules.remove(DuelRule.OBSTACLES)
            messages += "You can't have movement without obstacles."
        } else if (rule == DuelRule.OBSTACLES && DuelRule.NO_MOVEMENT in match.rules) {
            match.rules.remove(DuelRule.NO_MOVEMENT)
            messages += "You can't have obstacles without movement."
        }
        match.resetAccepted()
        return messages
    }

    /** Novite `DuelRules.canAccept`. */
    fun acceptRefusal(match: DuelArenaMatch, player: Player): String? {
        val rules = match.rules
        if (DuelRule.NO_RANGED in rules && DuelRule.NO_MELEE in rules && DuelRule.NO_MAGIC in rules) {
            return "You have to be able to use atleast one combat style in a duel."
        }
        if (DuelRule.ENABLE_SUMMONING !in rules && (Familiar.current(player) != null || Familiar.current(match.other(player)) != null)) {
            return "Summoning has been disabled during this duel!"
        }
        var count = 0
        match.lockedSlots.forEach { lock ->
            val worn = player.equipment[lock.slot.id] ?: return@forEach
            val stackableInPack = lock == DuelEquipLock.AMMO &&
                player.world.definitions.get(ItemDef::class.java, worn.id).stackable && player.inventory.contains(worn.id)
            if (!stackableInPack) count++
        }
        var freeSlots = player.inventory.freeSlotCount - count
        if (freeSlots < 0) return "You do not have enough inventory space to remove all the equipment."
        freeSlots -= match.stakeOf(player).rawItems.count { it != null }
        if (freeSlots < 0) return "You do not have enough room in your inventory for this stake."
        return null
    }

    fun forfeitRefusal(match: DuelArenaMatch): String? =
        when {
            match.stage != DuelStage.FIGHTING -> "You're not fighting yet."
            DuelRule.NO_FORFEIT in match.rules -> "Forfeiting is disabled for this duel."
            else -> null
        }

    /** Novite `battleTeleport`: a free tile within 7 of the arena centre; with No Movement the opponent is adjacent. */
    fun battleTiles(match: DuelArenaMatch, centre: Tile): Pair<Tile, Tile> {
        val world = match.challenger.world
        val first = world.findRandomTileAround(centre, radius = 7) ?: centre
        val noMovement = DuelRule.NO_MOVEMENT in match.rules
        val second = (0 until 10).asSequence()
            .mapNotNull { world.findRandomTileAround(if (noMovement) first else centre, radius = if (noMovement) 1 else 7) }
            .firstOrNull { !it.sameAs(first) } ?: centre
        return first to second
    }

    fun onFightStart(match: DuelArenaMatch) {
        listOf(match.challenger, match.opponent).forEach { player ->
            player.attr[CAN_FIGHT_ATTR] = false
            if (DuelRule.NO_MOVEMENT in match.rules) player.attr[MOVEMENT_RESTRICTION_ATTR] = NO_MOVEMENT_MESSAGE
            player.message("Your battle will begin shortly.")
        }
    }

    fun onFightEnd(player: Player) {
        player.attr.remove(CAN_FIGHT_ATTR)
        player.attr.remove(MOVEMENT_RESTRICTION_ATTR)
    }

    /** Winner receives both stakes; anything that does not fit is dropped under the winner (Novite `addDroppable`). */
    fun payout(match: DuelArenaMatch, winner: Player) {
        listOf(match.challengerStake, match.opponentStake).forEach { stake ->
            stake.rawItems.filterNotNull().forEach { item -> giveOrDrop(winner, item) }
            stake.removeAll()
        }
    }

    /**
     * Hands [item] to [player], dropping under them whatever the inventory has no room for. Every
     * path that gives stake back (payout, decline, logout while configuring) must use this: a plain
     * `inventory.add` silently destroys the stake when the inventory filled up in the meantime.
     */
    fun giveOrDrop(player: Player, item: Item) {
        val result = player.inventory.add(item.id, item.amount, assureFullInsertion = false)
        val left = item.amount - result.completed
        if (left > 0) player.world.spawn(GroundItem(item.id, left, player.tile, player))
    }

    fun lostMessage(winner: Player) = "Oh dear, it seems you have lost to ${winner.username}."

    fun wonMessage(loser: Player) = "Congradulations! You easily defeated ${loser.username}."

    fun isLocked(slot: EquipmentType, match: DuelArenaMatch) = match.lockedSlots.any { it.slot == slot }
}
