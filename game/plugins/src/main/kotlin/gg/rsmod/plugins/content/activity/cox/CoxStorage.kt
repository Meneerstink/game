package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CoX storage units. OSRS Wiki (Tiny storage unit; Storage unit; Chambers of Xeric): a tiny unit holds 25 personal items; units are upgraded for
 * the raid with mallignum root planks and a hammer (or dragon warhammer): small (Construction 30) / medium (60) / large (90) / massive (99), two
 * planks and 150 XP a tier, 100 points a tier; personal / shared space 30/250, 60/500, 90/1,000, 120/1,500; items left in a private storage are
 * taken out at the storage unit by the bank chest on the mountain (30107, 'Private'), which also holds the ancient chest's unclaimed rewards.
 * tsps Storage.ChambersOfXeric.js (code help; ONZEKER): the shared storage takes raid items only (cache enum 1666 ranges), kindling can't be
 * stored (Wiki Kindling), raid-only items left in a private storage are gone once outside, building is done 2 ticks later (anim 3676).
 * ONZEKER (no source): the refusal lines for building/shared (plank count, hammer, level, shared not built).
 * UI: the ported 271 / 550 with server-filled slots (OsrsInterfacePortTool 'cox'), the side panel = 667 1125 + CS2 150 (as Dizana's quiver).
 */
object CoxStorage {
    data class Tier(val osrsLoc: Int, val level: Int, val personal: Int, val shared: Int)
    val TIERS = listOf(Tier(29769, 0, 25, 0), Tier(29770, 30, 30, 250), Tier(29779, 60, 60, 500), Tier(29780, 90, 90, 1000), Tier(37978, 99, 120, 1500))
    const val MOUNT_UNIT = 30107
    const val PLANK = 21036
    const val KINDLING = 20799
    const val SIDE = 1125
    const val PRIVATE_SLOTS = 24
    const val SHARED_SLOTS = 21
    const val PRIVATE_SHOWN = 120
    const val SHARED_SHOWN = 250
    const val TO_SHARED = 5
    const val TO_PRIVATE = 5
    const val PRIVATE_CLOSE = 23
    const val SHARED_CLOSE = 20
    val HAMMERS = setOf(2347, 13576)
    /** tsps 'cache enum 1666': the items the shared storage takes (OSRS ids). */
    private val SHARED_RANGES = listOf(307..307, 590..590, 952..952, 1265..1265, 1267..1267, 1349..1349, 1351..1351, 1523..1523, 2347..2347,
        5341..5341, 5343..5343, 10010..10010, 20800..20801, 20853..20853, 20855..20868, 20870..20883, 20901..20996, 21036..21036)

    /** Private storage per account: "localId:amount,..." (persists; the mountain's unit hands it out). */
    val PRIVATE = AttributeKey<String>(persistenceKey = "cox_private_storage")

    /**
     * Fixronde 26-11 (COX-OPSLAG-RAIDITEMS): the raid the raid-only items in the private storage were stored in. Before, they were only
     * cleared at the mountain's unit, so overloads stored in one raid were there again at the start of the next. Raid ids restart at 1
     * with the server, hence the server-run stamp in the tag.
     */
    val PRIVATE_RAID = AttributeKey<String>(persistenceKey = "cox_private_raid")
    private val RUN = System.currentTimeMillis()
    private fun tag(raidId: Long) = "$RUN:$raidId"

    private data class View(val shared: Boolean, val size: Int, val mount: Boolean, val raidId: Long)
    private val views = ConcurrentHashMap<String, View>()
    private val shared = ConcurrentHashMap<Long, MutableList<IntArray>>()
    private val tiers = ConcurrentHashMap<Long, Int>()

    private fun key(p: Player) = p.username.lowercase()

    fun privateItems(p: Player): MutableList<IntArray> =
        p.attr[PRIVATE].orEmpty().split(',').filter { it.contains(':') }.map { e -> e.split(':').let { intArrayOf(it[0].toInt(), it[1].toInt()) } }.toMutableList()

    private fun savePrivate(p: Player, items: List<IntArray>) { p.attr[PRIVATE] = items.joinToString(",") { "${it[0]}:${it[1]}" } }

    private fun stackable(p: Player, id: Int) = p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).let { it.stackable || it.noted }

    private fun osrsOf(local: Int): Int? = SHARED_RANGES.flatMap { it.toList() }.firstOrNull { (CoxIds.item(it) ?: it) == local }
    private fun sharedTakes(local: Int) = osrsOf(local) != null

    /** Raid-only items (CoxRaids.RAID_ONLY_OSRS) can't leave the raid: gone from the private storage once outside (tsps). */
    fun clearRaidItems(p: Player) {
        val raidOnly = CoxRaids.raidOnlyLocal()
        val items = privateItems(p)
        if (items.removeAll { it[0] in raidOnly }) savePrivate(p, items)
        p.attr.remove(PRIVATE_RAID)
    }

    fun tier(raid: CoxRaid) = tiers[raid.id] ?: 0

    /** The ancient chest's unclaimed rewards into the private storage (Wiki 'Storage unit'); no size limit for them. */
    fun addUnclaimed(p: Player, items: List<Pair<Int, Int>>) {
        val list = privateItems(p)
        items.forEach { (id, n) -> add(list, Int.MAX_VALUE, id, n, stackable(p, id)) }
        savePrivate(p, list)
    }

    // ------------------------------------------------------------------ opening

    fun openPrivate(p: Player, mount: Boolean) {
        val iface = CoxInterfaces.of(271) ?: return
        val raid = CoxRaids.raidAt(p.tile)
        if (mount) clearRaidItems(p) else if (raid == null) return
        else if (p.attr[PRIVATE_RAID] != tag(raid.id)) clearRaidItems(p)   // raid-only items of an earlier raid
        val size = if (mount) PRIVATE_SHOWN else TIERS[tier(raid!!)].personal
        views[key(p)] = View(false, size, mount, raid?.id ?: -1)
        p.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        p.setInterfaceEvents(iface, PRIVATE_CLOSE, -1..-1, 0x2)
        p.setInterfaceEvents(iface, TO_SHARED, -1..-1, 0x2)
        p.setComponentHidden(iface, TO_SHARED, mount)
        for (s in 0 until PRIVATE_SHOWN) p.setInterfaceEvents(iface, PRIVATE_SLOTS + s, -1..-1, EVENTS)
        openSide(p, mount)
        refresh(p)
    }

    fun openShared(p: Player) {
        val iface = CoxInterfaces.of(550) ?: return
        val raid = CoxRaids.raidAt(p.tile) ?: return
        val size = TIERS[tier(raid)].shared
        if (size <= 0) { p.message("The shared storage needs the unit to be upgraded first."); return }
        views[key(p)] = View(true, size, false, raid.id)
        p.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        p.setInterfaceEvents(iface, SHARED_CLOSE, -1..-1, 0x2)
        p.setInterfaceEvents(iface, TO_PRIVATE, -1..-1, 0x2)
        for (s in 0 until SHARED_SHOWN) p.setInterfaceEvents(iface, SHARED_SLOTS + s, -1..-1, EVENTS)
        openSide(p, false)
        refresh(p)
    }

    private const val EVENTS = 0x2 or 0x4 or 0x8 or 0x10 or 0x20

    private fun openSide(p: Player, mount: Boolean) {
        p.openInterface(SIDE, InterfaceDestination.INVENTORY_TAB)
        if (mount) return
        p.unlockIComponentOptionSlots(SIDE, 0, 0, 27, 0, 1, 2, 3, 4)
        p.runClientScript(150, SIDE shl 16, 93, 4, 7, 0, -1, "Store-1", "Store-5", "Store-10", "Store-All", "Store-X")
    }

    fun close(p: Player) {
        views.remove(key(p)) ?: return
        p.closeInterface(SIDE)
        p.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        p.inventory.dirty = true
    }

    fun refresh(p: Player) {
        val v = views[key(p)] ?: return
        val iface = CoxInterfaces.of(if (v.shared) 550 else 271) ?: return
        val first = if (v.shared) SHARED_SLOTS else PRIVATE_SLOTS
        val shown = if (v.shared) SHARED_SHOWN else PRIVATE_SHOWN
        val list: List<IntArray> = if (v.shared) shared[v.raidId].orEmpty() else privateItems(p)
        for (s in 0 until shown) {
            val e = list.getOrNull(s)
            if (e == null) p.setComponentHidden(iface, first + s, true)
            else { p.setComponentItem(iface, first + s, e[0], e[1]); p.setComponentHidden(iface, first + s, false) }
        }
        p.inventory.dirty = true
    }

    // ------------------------------------------------------------------ moving items

    private fun add(list: MutableList<IntArray>, size: Int, id: Int, amount: Int, stack: Boolean): Int {
        if (stack) {
            list.firstOrNull { it[0] == id }?.let { it[1] += amount; return amount }
            if (list.size >= size) return 0
            list += intArrayOf(id, amount); return amount
        }
        val n = minOf(amount, size - list.size).coerceAtLeast(0)
        repeat(n) { list += intArrayOf(id, 1) }
        return n
    }

    /** From the side panel: [slot] of the inventory, [amount] (Int.MAX_VALUE = all). */
    fun deposit(p: Player, slot: Int, amount: Int) {
        val v = views[key(p)] ?: return
        if (v.mount) { p.message("You can't store items now."); return }
        val item = p.inventory[slot] ?: return
        val id = item.id
        if (id == CoxIds.item(KINDLING)) { p.message("You can't store kindling."); return }
        if (v.shared && !sharedTakes(id)) { p.message("The shared storage unit cannot hold that item."); return }
        val list = if (v.shared) shared.getOrPut(v.raidId) { mutableListOf() } else privateItems(p)
        val want = minOf(amount, p.inventory.getItemCount(id))
        val stored = add(list, v.size, id, want, stackable(p, id))
        if (stored <= 0) { p.message(if (v.shared) "The shared storage is full." else "Not enough space in your private storage."); return }
        p.inventory.remove(id, stored)
        if (!v.shared) { savePrivate(p, list); if (id in CoxRaids.raidOnlyLocal()) p.attr[PRIVATE_RAID] = tag(v.raidId) }
        if (v.shared) p.world.players.entries.filterNotNull().filter { views[key(it)]?.let { o -> o.shared && o.raidId == v.raidId } == true }.forEach { refresh(it) }
        else refresh(p)
    }

    /** From a storage slot: withdraw [amount] of that slot's item. */
    fun withdraw(p: Player, slot: Int, amount: Int) {
        val v = views[key(p)] ?: return
        val list = if (v.shared) shared[v.raidId] ?: return else privateItems(p)
        val id = list.getOrNull(slot)?.get(0) ?: return
        val have = list.filter { it[0] == id }.sumOf { it[1] }
        val stack = stackable(p, id)
        val room = if (stack) (if (p.inventory.contains(id) || p.inventory.freeSlotCount > 0) Int.MAX_VALUE else 0) else p.inventory.freeSlotCount
        val want = minOf(amount, have, room)
        if (want <= 0) { p.message("Not enough space in your inventory."); return }
        var left = want
        val it = list.listIterator(list.size)
        while (it.hasPrevious() && left > 0) {
            val e = it.previous()
            if (e[0] != id) continue
            val out = minOf(e[1], left); e[1] -= out; left -= out
            if (e[1] <= 0) it.remove()
        }
        p.inventory.add(id, want)
        if (!v.shared) savePrivate(p, list)
        if (v.shared) p.world.players.entries.filterNotNull().filter { views[key(it)]?.let { o -> o.shared && o.raidId == v.raidId } == true }.forEach { refresh(it) }
        else refresh(p)
    }

    fun click(p: Player, shared: Boolean, component: Int, option: Int) {
        val first = if (shared) SHARED_SLOTS else PRIVATE_SLOTS
        val close = if (shared) SHARED_CLOSE else PRIVATE_CLOSE
        when (component) {
            close -> { p.closeInterface(CoxInterfaces.of(if (shared) 550 else 271) ?: return); close(p); return }
            TO_SHARED -> { if (shared) openPrivate(p, false) else openShared(p); return }
        }
        val slot = component - first
        if (slot !in 0 until (if (shared) SHARED_SHOWN else PRIVATE_SHOWN)) return
        amount(p, option) { withdraw(p, slot, it) }
    }

    fun sideClick(p: Player, slot: Int, option: Int) = amount(p, option) { deposit(p, slot, it) }

    fun sideOpen(p: Player): Boolean = views[key(p)]?.mount == false

    private fun amount(p: Player, option: Int, act: (Int) -> Unit) {
        when (option) {
            1 -> act(1); 2 -> act(5); 3 -> act(10); 4 -> act(Int.MAX_VALUE)
            // ROOT_CAUSES 92 (quiver): an amount asked with a window open needs a WEAK task.
            5 -> p.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) { val n = inputInt("Enter amount:"); if (n > 0) act(n) }
        }
    }

    // ------------------------------------------------------------------ building

    /** 'Build' on a unit: the next tier (or straight to a higher one paying the tiers between), planks + hammer, Construction level. */
    fun build(p: Player) {
        val raid = CoxRaids.raidAt(p.tile) ?: return
        val now = tier(raid)
        if (now >= TIERS.lastIndex) return
        val con = p.skills.getCurrentLevel(Skills.CONSTRUCTION)
        val target = TIERS.indices.lastOrNull { it > now && TIERS[it].level <= con } ?: run {
            p.message("You need a Construction level of at least ${TIERS[now + 1].level} to build that."); return
        }
        val planks = 2 * (target - now)
        val plank = CoxIds.item(PLANK) ?: return
        if (HAMMERS.none { p.inventory.contains(it) || p.equipment.contains(it) }) { p.message("You need a hammer to build this."); return }
        if (p.inventory.getItemCount(plank) < planks) { p.message("You need $planks mallignum root planks to build that."); return }
        p.animate(3676)
        p.queue {
            wait(2)
            if (tier(raid) != now || p.inventory.getItemCount(plank) < planks) return@queue
            p.inventory.remove(plank, planks)
            tiers[raid.id] = target
            p.addXp(Skills.CONSTRUCTION, 150.0 * (target - now))
            raid.addPoints(p.username, 100 * (target - now))
            swapUnits(raid, TIERS[now].osrsLoc, TIERS[target].osrsLoc)
        }
    }

    private fun swapUnits(raid: CoxRaid, from: Int, to: Int) {
        val a = CoxIds.loc(from) ?: return
        val b = CoxIds.loc(to) ?: return
        raid.layout.floors.forEachIndexed { f, floor -> floor.forEach { CoxRaids.replaceInRoom(raid, f, it, a, b) } }
    }

    fun forget(raid: CoxRaid) { shared.remove(raid.id); tiers.remove(raid.id) }
}
