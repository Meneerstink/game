package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.inter.bank.Bank
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity

/**
 * The demon butler (owner 2026-10-10 night run: free - no wage - called like OSRS; fetches planks from the bank, takes items to the
 * bank and un-notes planks). Ported from Darkan (`ServantNPC.requestType`, `ServantHouseD`, `ItemOnServantD`, `HouseServants`
 * DEMON_BUTLER: 26 items a trip, a 12-tick trip, `HouseConstants.BANKABLE_ITEMS`) with Darkan's lines; no fee, no sawmill, no
 * cooking (the house has no kitchen).
 *
 * He waits in the dining room; the dining room's bell-pull calls him to the owner (OSRS; the rev-667 House Options panel has no
 * "Call Servant" button). A trip takes him out of the house for [TRIP_TICKS]: deposits leave the inventory at once (the bank's
 * own [Bank.deposit], so the Deadman deposit rules apply), fetched items arrive when he returns, un-noted planks too - and if the
 * owner logs out before that, the un-noted planks go to the bank instead, so nothing is lost or doubled.
 *
 * Deadman (handoff 9b): he is a bank, so [BankSecurity.denyBank] refuses a skulled player exactly like a bank in a guarded area.
 */
object PohServant {
    const val NPC = Npcs.DEMON_BUTLER
    const val CAPACITY = 26
    const val TRIP_TICKS = 12

    /** Darkan `HouseConstants.BANKABLE_ITEMS`. */
    val FETCHABLE =
        listOf(
            Items.PLANK, Items.OAK_PLANK, Items.TEAK_PLANK, Items.MAHOGANY_PLANK, Items.SOFT_CLAY, Items.LIMESTONE_BRICK,
            Items.STEEL_BAR, Items.BOLT_OF_CLOTH, Items.GOLD_LEAF, Items.MARBLE_BLOCK, Items.MAGIC_STONE,
        )

    /** The planks he un-notes (OSRS: a servant un-notes planks used on him). */
    val PLANKS = setOf(Items.PLANK, Items.OAK_PLANK, Items.TEAK_PLANK, Items.MAHOGANY_PLANK)

    enum class Kind { FETCH, DEPOSIT, UNNOTE }

    data class Trip(val kind: Kind, val item: Int, val amount: Int)

    private val SERVANT_ATTR = AttributeKey<Npc>()
    val TRIP_ATTR = AttributeKey<Trip>()
    val TRIP_TIMER = TimerKey()
    /** The last fetch request, item shl 8 or amount (a plain number: a persisted Pair does not survive the save, 2026-10-10). */
    private val LAST_FETCH_ATTR = AttributeKey<Int>(persistenceKey = "poh_servant_last_fetch_v2")

    private fun lastFetch(player: Player): Pair<Int, Int>? = player.attr[LAST_FETCH_ATTR]?.let { (it ushr 8) to (it and 0xFF) }

    private fun owns(
        player: Player,
        npc: Npc,
    ): Boolean = player.attr[SERVANT_ATTR] === npc

    fun servantOf(player: Player): Npc? =
        player.attr[SERVANT_ATTR]?.takeIf { it.isSpawned() && player.world.instanceAllocator.getMap(it.tile)?.area?.contains(player.tile) == true }

    /** Spawned with the dummy once the owner is inside the house it was built for. */
    fun spawn(player: Player) {
        val (map, tile) = player.attr[PlayerHouse.PENDING_SERVANT_ATTR] ?: return
        player.attr.remove(PlayerHouse.PENDING_SERVANT_ATTR)
        if (!map.area.contains(player.tile) || player.world.instanceAllocator.getMap(tile) !== map) return
        // Away on an errand (the house was reloaded meanwhile): he appears when he comes back (returnFromTrip).
        if (player.attr.has(TRIP_ATTR)) return
        val npc = Npc(NPC, tile, player.world)
        npc.walkRadius = 0
        player.world.spawn(npc)
        player.attr[SERVANT_ATTR] = npc
    }

    /** Bell-pull "Ring": the butler comes to the owner (Darkan `ServantNPC.call`). */
    fun call(player: Player) {
        player.playSound(Sfx.BELL_PEAL)
        val npc = servantOf(player)
        if (npc == null) {
            player.message(if (player.attr.has(TRIP_ATTR)) "Your servant is away on an errand." else "You have no servant here.")
            return
        }
        val spot = gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(player.world, player.tile.transform(1, 0), radius = 2) ?: return
        npc.moveTo(spot)
        npc.facePawn(player)
    }

    private suspend fun QueueTask.say(
        npc: Npc,
        vararg lines: String,
    ) = chatNpc(*lines, npc = npc.id, facialExpression = FacialExpression.HAPPY_TALKING)

    /** Talk-to (Darkan `ServantHouseD`). */
    suspend fun talk(
        task: QueueTask,
        npc: Npc,
    ) {
        val player = task.player
        if (!owns(player, npc)) return
        task.say(npc, "I am at thy command, my master.")
        when (task.options("Go to the bank...", "Un-note planks...", "Nothing, thanks.")) {
            1 -> bankOptions(task, npc)
            2 -> task.say(npc, "Hand me thy noted planks and I shall return with the planks themselves.")
        }
    }

    private suspend fun bankOptions(
        task: QueueTask,
        npc: Npc,
    ) {
        when (task.options("Take something to the bank", "Bring something from the bank", "Cancel")) {
            1 -> task.say(npc, "Give any item to me and I shall take it swiftly to the bank where it will be safe from thieves and harm.")
            2 -> fetchMenu(task, npc)
        }
    }

    /** "Fetch-from-bank" and the bank menu's "Bring something from the bank": the last request first, then every fetchable item. */
    suspend fun fetchMenu(
        task: QueueTask,
        npc: Npc,
    ) {
        val player = task.player
        if (!owns(player, npc)) return
        val defs = player.world.definitions
        fun name(id: Int) = defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name
        val last = lastFetch(player)
        val entries = (listOfNotNull(last?.let { -1 }) + FETCHABLE)
        var start = 0
        while (true) {
            val rest = entries.drop(start)
            val page = if (rest.size <= 5) rest else rest.take(4)
            val labels = page.map { if (it == -1) "Fetch another ${last!!.second} x ${name(last.first).lowercase()}." else name(it) } +
                if (rest.size > 5) listOf("More...") else emptyList()
            val picked = task.options(*labels.toTypedArray(), title = "Bring something from the bank")
            if (rest.size > 5 && picked == 5) {
                start = if (start + 4 >= entries.size) 0 else start + 4
                continue
            }
            val choice = page.getOrNull(picked - 1) ?: return
            if (choice == -1) {
                request(task, npc, Kind.FETCH, last!!.first, last.second)
            } else {
                val amount = task.inputInt("How many would you like?")
                if (amount > 0) request(task, npc, Kind.FETCH, choice, amount)
            }
            return
        }
    }

    /** An item used on the butler: noted planks may be un-noted, anything else is taken to the bank (Darkan `ItemOnServantD`). */
    suspend fun itemOn(
        task: QueueTask,
        npc: Npc,
        item: Item,
    ) {
        val player = task.player
        if (!owns(player, npc)) return
        val def = item.getDef(player.world.definitions)
        val unnoted = item.toUnnoted(player.world.definitions).id
        if (def.noted && unnoted in PLANKS) {
            when (task.options("Un-note them.", "Take them to the bank.", "Cancel.")) {
                1 -> request(task, npc, Kind.UNNOTE, item.id, player.inventory.getItemCount(item.id))
                2 -> request(task, npc, Kind.DEPOSIT, item.id, player.inventory.getItemCount(item.id))
            }
            return
        }
        val held = player.inventory.getItemCount(item.id)
        val amount = if (held <= 1) 1 else task.inputInt("How many would you like me to take?")
        if (amount > 0) request(task, npc, Kind.DEPOSIT, item.id, minOf(amount, held))
    }

    /** Darkan `ServantNPC.requestType`, free of charge; the butler leaves at once and returns after [TRIP_TICKS]. */
    private suspend fun request(
        task: QueueTask,
        npc: Npc,
        kind: Kind,
        item: Int,
        requested: Int,
    ) {
        val player = task.player
        if (player.attr.has(TRIP_ATTR)) return
        if (BankSecurity.denyBank(player)) return
        if (kind == Kind.FETCH && player.bank.getItemCount(item) <= 0) {
            task.say(npc, "It appears you do not have this item in your bank.")
            return
        }
        if (requested > CAPACITY && kind == Kind.FETCH) {
            task.say(npc, "I'm sorry. I can only hold $CAPACITY items during a trip.")
            return
        }
        val amount = minOf(requested, CAPACITY)
        when (kind) {
            Kind.FETCH -> player.attr[LAST_FETCH_ATTR] = (item shl 8) or amount
            Kind.UNNOTE -> {
                val unnoted = Item(item).toUnnoted(player.world.definitions).id
                // Darkan requestType: an un-note trip carries no more than the free inventory slots.
                val take = minOf(amount, player.inventory.getItemCount(item), player.inventory.freeSlotCount)
                if (take <= 0) {
                    player.message("You don't have enough inventory space.")
                    return
                }
                if (take <= 0 || player.inventory.remove(item, take).completed != take) return
                player.attr[TRIP_ATTR] = Trip(kind, unnoted, take)
            }
            Kind.DEPOSIT -> {
                val slot = player.inventory.getItemIndex(item, skipAttrItems = false)
                if (slot < 0) return
                val before = player.inventory.getItemCount(item)
                Bank.deposit(player, player.inventory, slot, amount)
                if (player.inventory.getItemCount(item) == before) return
            }
        }
        if (kind == Kind.FETCH) player.attr[TRIP_ATTR] = Trip(kind, item, amount)
        if (kind == Kind.DEPOSIT) player.attr[TRIP_ATTR] = Trip(kind, item, amount)
        player.world.remove(npc)
        player.timers[TRIP_TIMER] = TRIP_TICKS
    }

    /** The butler is back: he brings what was asked and stands beside the owner again. */
    fun returnFromTrip(player: Player) {
        val trip = player.attr[TRIP_ATTR] ?: return
        player.attr.remove(TRIP_ATTR)
        // Back in the owner's house - the same one, or the one a building-mode toggle / rotation reloaded meanwhile.
        if (!PlayerHouse.ownsHouseAt(player)) {
            settleAway(player, trip)
            return
        }
        servantOf(player)?.let { player.world.remove(it) }
        val spot = gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(player.world, player.tile.transform(1, 0), radius = 2) ?: player.tile
        val npc = Npc(NPC, spot, player.world)
        npc.walkRadius = 0
        player.world.spawn(npc)
        player.attr[SERVANT_ATTR] = npc
        npc.facePawn(player)
        val line =
            when (trip.kind) {
                Kind.FETCH -> {
                    val slot = player.bank.getItemIndex(trip.item, skipAttrItems = false)
                    if (slot < 0 || BankSecurity.isBankBlocked(player) || Bank.withdraw(player, trip.item, trip.amount, slot, forceUnnoted = true) <= 0) {
                        "I could not bring thee those items, my master."
                    } else {
                        "I have returned with the items you asked me to retrieve."
                    }
                }
                Kind.UNNOTE -> {
                    val added = player.inventory.add(trip.item, trip.amount).completed
                    if (added < trip.amount) player.bank.add(trip.item, trip.amount - added)
                    "I have returned with the items you asked me to retrieve."
                }
                Kind.DEPOSIT -> "I have successfully deposited your items into your bank. No longer will the items be at risk from thieves."
            }
        player.queue { chatNpc(line, npc = npc.id, facialExpression = FacialExpression.HAPPY_TALKING) }
    }

    /** The owner left (or logged out) before the butler came back: un-noted planks go to the bank, a fetch never happens. */
    fun settleAway(
        player: Player,
        trip: Trip? = player.attr[TRIP_ATTR],
    ) {
        trip ?: return
        player.attr.remove(TRIP_ATTR)
        player.timers.remove(TRIP_TIMER)
        if (trip.kind == Kind.UNNOTE) player.bank.add(trip.item, trip.amount)
    }
}
