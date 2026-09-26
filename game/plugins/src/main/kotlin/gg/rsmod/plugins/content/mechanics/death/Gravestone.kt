package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.GRAVESTONE_BLESSED_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_REPAIRED_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TICKS_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem

/** What a PvM death did with the lost items (the plugin turns this into the OSRS chat messages). */
data class GraveDeposit(
    /** Items of the old gravestone that went to Death's Office first ("Some items have been moved to Death's Office ..."). */
    val movedFromOldGrave: Boolean,
    /** The new items joined an existing gravestone ("Some of your items have been added to your previous gravestone."). */
    val addedToPrevious: Boolean,
    /** Items that fit neither the gravestone nor Death's Office; the caller drops them as the player's own ground items. */
    val overflow: List<Item>,
)

/** The outcome of taking items from a gravestone. */
sealed class GraveTakeOutcome {
    object NothingThere : GraveTakeOutcome()

    object NoInventorySpace : GraveTakeOutcome()


    data class Taken(val items: List<Item>) : GraveTakeOutcome()
}

/**
 * The player's gravestone (OSRS "Grave", 120 slots, [Player.gravestone]). Pure item and timer rules; the world object
 * (the private grave npc) is [GravestoneWorld]'s job.
 *
 * OSRS rules implemented (OSRS Wiki "Grave", "Death Changes" news post 25 June 2020):
 *  - a PvM death puts the lost items in a gravestone where the player died (outside an instance - the caller passes the
 *    tile); dying again adds the new items to the existing gravestone at its original location, except a PvM death in 20+
 *    Wilderness, which moves it ("Existing gravestones will now only move to the new death location if the location is in
 *    20+ Wilderness");
 *  - before the new items go in, the old gravestone sends its resources (unpowered orbs, pure essence, bones, ores, planks)
 *    and every unstackable item beyond 28 of one kind to Death's Office;
 *  - the 15-minute timer (1500 ticks) is refreshed whenever the contents change, and only runs while the player is logged
 *    in, not idle for more than 10 seconds and not looking at the gravestone ([tick]);
 *  - when it runs out the gravestone collapses and its items go to Death's Office (5 % fee per item worth 100,000+).
 * Owner decisions 2026-09-26: taking items from the gravestone is free (no fee, no Unlock); the gravestone is visible to every
 * player, only the owner can loot it, and others can Bless / Repair it once each (RS 2009 rules, [bless], [repair]).
 * Owner decisions 2026-09-26: food and potions stay in the gravestone on a repeat death (OSRS drops them under it), and a
 * gravestone whose items do not all fit in a full Death's Office keeps standing until there is room.
 */
object Gravestone {
    fun exists(player: Player): Boolean = player.attr[GRAVESTONE_TILE_ATTR] != null

    fun tile(player: Player): Tile? = player.attr[GRAVESTONE_TILE_ATTR]?.let { Tile.from30BitHash(it) }

    fun ticksLeft(player: Player): Int = player.attr[GRAVESTONE_TICKS_ATTR] ?: 0

    /** Resource items an old gravestone hands to Death on a repeat death (OSRS Wiki "Grave", "Resources"). */
    fun isResource(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean {
        val def = definitions.getNullable(ItemDef::class.java, itemId) ?: return false
        val name = (if (def.noted) definitions.getNullable(ItemDef::class.java, def.noteLinkId)?.name else def.name)?.lowercase() ?: return false
        return name == "unpowered orb" || name == "pure essence" || name.endsWith("bones") || name.endsWith(" ore") ||
            name == "coal" || name == "clay" || name == "plank" || name.endsWith(" plank")
    }

    /**
     * Carries out a PvM death's item loss: [items] (already removed from the player, attributes kept) go into the gravestone
     * at [deathTile] (or the existing one). Everything that does not fit goes to Death's Office; what fits nowhere is
     * returned in [GraveDeposit.overflow]. Nothing is deleted.
     */
    fun deposit(
        player: Player,
        items: List<Item>,
        deathTile: Tile,
        moveExisting: Boolean,
        config: DeathsDomainConfig = DeathsDomainConfig.current,
    ): GraveDeposit {
        val definitions = player.world.definitions
        val grave = player.gravestone
        val hadGrave = exists(player) && !grave.isEmpty
        var movedFromOld = false
        if (hadGrave) {
            movedFromOld = sendSurplusToOffice(player, config)
        }
        if (!hadGrave || moveExisting) {
            player.attr[GRAVESTONE_TILE_ATTR] = deathTile.as30BitInteger
        }
        if (!hadGrave) {
            // A new gravestone can be blessed and repaired again.
            player.attr.remove(GRAVESTONE_BLESSED_ATTR)
            player.attr.remove(GRAVESTONE_REPAIRED_ATTR)
        }
        val overflow = mutableListOf<Item>()
        var added = false
        for (item in items) {
            if (item.amount <= 0) continue
            val stored = Item(item.id, item.amount).copyAttr(item).removeAttr(ItemAttribute.DEATH_FEE_FREE)
            val inGrave = DeathStorage.insert(definitions, grave, stored)
            if (inGrave > 0) added = true
            var left = stored.amount - inGrave
            if (left > 0) {
                left -= DeathsOffice.store(player, Item(stored, left))
            }
            if (left > 0) overflow += Item(stored, left)
        }
        if (grave.isEmpty) {
            clear(player)
        } else if (added) {
            // The timer is refreshed to 15 minutes, but never shortened: time added by Bless/Repair on this gravestone stays.
            player.attr[GRAVESTONE_TICKS_ATTR] = maxOf(config.graveDurationTicks, if (hadGrave) ticksLeft(player) else 0)
        }
        return GraveDeposit(movedFromOldGrave = movedFromOld, addedToPrevious = hadGrave && added, overflow = overflow)
    }

    /** Resources and unstackables beyond [DeathsDomainConfig.graveUnstackableKeep] per kind leave the old grave for Death. */
    private fun sendSurplusToOffice(
        player: Player,
        config: DeathsDomainConfig,
    ): Boolean {
        val definitions = player.world.definitions
        val grave = player.gravestone
        var moved = false
        val unstackableSeen = HashMap<Int, Int>()
        for (slot in 0 until grave.capacity) {
            val item = grave[slot] ?: continue
            val surplus =
                when {
                    isResource(definitions, item.id) -> item.amount
                    !DeathStorage.stackable(definitions, item.id) -> {
                        val seen = unstackableSeen.merge(item.id, item.amount, Int::plus)!!
                        (seen - config.graveUnstackableKeep).coerceIn(0, item.amount)
                    }
                    else -> 0
                }
            if (surplus <= 0) continue
            val movedHere = minOf(surplus, DeathsOffice.room(player, item))
            if (movedHere <= 0) continue
            val taken = DeathStorage.takeFromSlot(grave, slot, movedHere) ?: continue
            val stored = DeathsOffice.store(player, taken, free = DeathFees.isFeeFree(taken))
            if (stored < taken.amount) DeathStorage.insert(definitions, grave, Item(taken, taken.amount - stored))
            if (stored > 0) moved = true
        }
        return moved
    }

    /**
     * One game tick of the gravestone timer. [active] is false while the player is idle for more than 10 seconds, has the
     * gravestone window open or is in Death's Office (the first-death tutorial holds the timer: "it will wait until you
     * leave this place").
     * @return true when the gravestone collapsed this tick.
     */
    fun tick(
        player: Player,
        active: Boolean,
    ): Boolean {
        if (!exists(player)) return false
        if (player.gravestone.isEmpty) {
            clear(player)
            return true
        }
        val left = ticksLeft(player)
        if (left > 0) {
            if (!active) return false
            player.attr[GRAVESTONE_TICKS_ATTR] = left - 1
            if (left - 1 > 0) return false
        }
        return collapse(player)
    }

    /**
     * Sends everything in the gravestone to Death's Office (paid items stay fee-free). Owner 2026-09-26: if Death's Office
     * is full, the rest stays in the gravestone, which keeps standing until there is room.
     * @return true when the gravestone is gone.
     */
    fun collapse(player: Player): Boolean {
        val grave = player.gravestone
        for (slot in 0 until grave.capacity) {
            val item = grave[slot] ?: continue
            val room = DeathsOffice.room(player, item)
            if (room <= 0) continue
            val taken = DeathStorage.takeFromSlot(grave, slot, room) ?: continue
            val stored = DeathsOffice.store(player, taken, free = DeathFees.isFeeFree(taken))
            if (stored < taken.amount) DeathStorage.insert(player.world.definitions, grave, Item(taken, taken.amount - stored))
        }
        if (grave.isEmpty) {
            clear(player)
            return true
        }
        player.attr[GRAVESTONE_TICKS_ATTR] = 0
        return false
    }

    fun clear(player: Player) {
        player.attr.remove(GRAVESTONE_TILE_ATTR)
        player.attr.remove(GRAVESTONE_TICKS_ATTR)
        player.attr.remove(GRAVESTONE_BLESSED_ATTR)
        player.attr.remove(GRAVESTONE_REPAIRED_ATTR)
    }

    /** "Take" one stack (as much as fits in the inventory). Owner 2026-09-26: taking items from the gravestone is free. */
    fun take(
        player: Player,
        slot: Int,
    ): GraveTakeOutcome {
        if (slot !in 0 until player.gravestone.capacity) return GraveTakeOutcome.NothingThere
        val item = player.gravestone[slot] ?: return GraveTakeOutcome.NothingThere
        if (DeadmanEmblem.isEmblem(item.id)) {
            // A Deadman emblem comes back under the one-emblem rule (keep the best, cash the other in).
            player.gravestone[slot] = null
            afterChange(player)
            repeat(item.amount) { DeadmanEmblem.receive(player, DeadmanEmblem.tierOf(item.id), DeadmanEmblem.Source.RECLAIM, "gravestone") }
            return GraveTakeOutcome.Taken(listOf(Item(item.id, item.amount)))
        }
        val back = DeathsOffice.handedBack(item)
        val fits = DeathsOffice.inventoryRoom(player, back, back.amount)
        if (fits <= 0) return GraveTakeOutcome.NoInventorySpace
        val taken = DeathStorage.takeFromSlot(player.gravestone, slot, fits) ?: return GraveTakeOutcome.NothingThere
        val handed = DeathsOffice.handedBack(taken)
        val added = player.inventory.addPreservingAttr(handed, assureFullInsertion = false).completed
        if (added < handed.amount) DeathStorage.insert(player.world.definitions, player.gravestone, Item(taken, handed.amount - added))
        afterChange(player)
        return GraveTakeOutcome.Taken(listOf(Item(handed.id, added)))
    }

    /** "Take-All": OSRS "When collecting items from the take-all section in the gravestone menu, equipable items are now prioritised". */
    fun takeAll(player: Player): GraveTakeOutcome {
        val definitions = player.world.definitions
        val slots =
            (0 until player.gravestone.capacity).filter { player.gravestone[it] != null }
                .sortedByDescending { slot -> definitions.getNullable(ItemDef::class.java, player.gravestone[slot]!!.id)?.equipSlot?.let { it >= 0 } == true }
        if (slots.isEmpty()) return GraveTakeOutcome.NothingThere
        val taken = mutableListOf<Item>()
        var blocked = false
        for (slot in slots) {
            when (val outcome = take(player, slot)) {
                is GraveTakeOutcome.Taken -> taken += outcome.items
                GraveTakeOutcome.NoInventorySpace -> blocked = true
                else -> {}
            }
        }
        if (taken.isEmpty()) return if (blocked) GraveTakeOutcome.NoInventorySpace else GraveTakeOutcome.NothingThere
        return GraveTakeOutcome.Taken(taken)
    }

    /** Result of blessing or repairing a gravestone (RS 2009, 2009scape `GraveController`). */
    sealed class PrayOutcome {
        object AlreadyDone : PrayOutcome()

        object OwnGrave : PrayOutcome()

        data class LevelTooLow(val level: Int) : PrayOutcome()

        object NoPrayerPoints : PrayOutcome()

        data class Done(val minutes: Int) : PrayOutcome()
    }

    const val BLESS_LEVEL = 70
    const val BLESS_MAX_MINUTES = 60
    const val REPAIR_LEVEL = 2
    const val REPAIR_MAX_MINUTES = 5

    /** Ticks per minute (0.6 s ticks). */
    private const val TICKS_PER_MINUTE = 100

    /**
     * Bless [owner]'s gravestone (RS 2009): only another player, Prayer 70, once per gravestone; adds min(60, prayer points
     * - 10) minutes and costs that many prayer points. The caller plays animation 645 and the prayer recharge sound.
     */
    fun bless(
        blesser: Player,
        owner: Player,
    ): PrayOutcome {
        if (blesser === owner) return PrayOutcome.OwnGrave
        if (owner.attr[GRAVESTONE_BLESSED_ATTR] == true) return PrayOutcome.AlreadyDone
        if (blesser.skills.getMaxLevel(PRAYER_SKILL) < BLESS_LEVEL) return PrayOutcome.LevelTooLow(BLESS_LEVEL)
        val minutes = minOf(BLESS_MAX_MINUTES, blesser.getCurrentPrayerPoints() - 10)
        if (minutes <= 0) return PrayOutcome.NoPrayerPoints
        blesser.alterPrayerPoints(-minutes)
        owner.attr[GRAVESTONE_BLESSED_ATTR] = true
        addMinutes(owner, minutes)
        return PrayOutcome.Done(minutes)
    }

    /** Repair a gravestone (RS 2009): Prayer 2, once per gravestone; adds min(5, prayer points) minutes for that many points. */
    fun repair(
        repairer: Player,
        owner: Player,
    ): PrayOutcome {
        if (owner.attr[GRAVESTONE_REPAIRED_ATTR] == true) return PrayOutcome.AlreadyDone
        if (repairer.skills.getMaxLevel(PRAYER_SKILL) < REPAIR_LEVEL) return PrayOutcome.LevelTooLow(REPAIR_LEVEL)
        val minutes = minOf(REPAIR_MAX_MINUTES, repairer.getCurrentPrayerPoints())
        if (minutes <= 0) return PrayOutcome.NoPrayerPoints
        repairer.alterPrayerPoints(-minutes)
        owner.attr[GRAVESTONE_REPAIRED_ATTR] = true
        addMinutes(owner, minutes)
        return PrayOutcome.Done(minutes)
    }

    /** Extra time lives in [GRAVESTONE_TICKS_ATTR], so it survives logout like the rest of the timer. */
    private fun addMinutes(
        owner: Player,
        minutes: Int,
    ) {
        owner.attr[GRAVESTONE_TICKS_ATTR] = ticksLeft(owner) + minutes * TICKS_PER_MINUTE
    }

    private const val PRAYER_SKILL = 5
    /** A gravestone emptied by taking its items disappears. */
    fun afterChange(player: Player) {
        if (player.gravestone.isEmpty) clear(player)
    }
}
