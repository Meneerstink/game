package gg.rsmod.plugins.content.skills.construction.mahogany

import gg.rsmod.game.message.impl.LocAddChangeMessage
import gg.rsmod.game.message.impl.UpdateZonePartialFollowsMessage
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LOC_ALIAS_ATTR
import gg.rsmod.game.model.attr.locAliasKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C7): Mahogany Homes (OSRS Wiki "Mahogany Homes", "Mahogany Homes/Mechanics", "Carpenter points",
 * "Mahogany Homes Reward Shop", "Plank sack", "Supply crate (Mahogany Homes)", "Carpenter's outfit").
 *
 * - Contracts from Amy (Falador; she alone gives the FIRST one), Marlo (Varrock) and Ellie (East Ardougne): Beginner 1 / Novice 20 /
 *   Adept 50 / Expert 70 Construction, planks / oak / teak / mahogany. The home and its layout are drawn from the OSRS layouts
 *   ([MahoganyHomesData.LAYOUTS], every row of the Wiki mechanics tables). VERVANGING: Hosidius (Angelo, Barbara, Leela, Mariah) is
 *   Kourend = parked, so contracts go to the 9 Falador / Varrock / Ardougne homes.
 * - In the home: furniture to Remove (then Build on the space) or to Repair, per the layout. OSRS shows this per player through the
 *   contract varbits 10554-10561 on multilocs; the 667 loc type stores multiloc variants as u16 and our imported variants are > 65535,
 *   so the world keeps the finished furniture (variant 0) and the contractor's view is sent per player (LocAddChange + the engine's
 *   [LOC_ALIAS_ATTR], as CoX 'Reload'). Experience per material: build 22.5 / 48 / 72 / 112, repair 127.5 / 160 / 190 / 240.
 * - Talk to the homeowner when done: 500 / 1,250 / 2,250 / 2,750 experience and 2 / 3 / 4 / 5 carpenter points.
 * - Rewards (Amy "Rewards"): the shop ([SHOP]); the carpenter's outfit gives 0.4 / 0.8 / 0.6 / 0.2 % (2.5 % as a set) on ALL
 *   Construction experience ([outfitBonus], through the experience rate); Amy's saw is a saw that can be wielded; the plank sack holds
 *   28 planks of any kind, used straight from the sack (here and in the house); a supply crate is one roll of the OSRS table.
 */
object MahoganyHomes {
    const val HELMET = 25698
    const val SHIRT = 25699
    const val TROUSERS = 25700
    const val BOOTS = 25701
    const val AMYS_SAW = 25702
    const val PLANK_SACK = 25703
    const val SUPPLY_CRATE = 25704
    const val SHOP = "Mahogany Homes"

    val TIER_NAMES = listOf("Beginner", "Novice", "Adept", "Expert")
    val LEVELS = intArrayOf(1, 20, 50, 70)
    val PLANKS = intArrayOf(Items.PLANK, Items.OAK_PLANK, Items.TEAK_PLANK, Items.MAHOGANY_PLANK)
    private val POINTS = intArrayOf(2, 3, 4, 5)
    private val COMPLETION_XP = doubleArrayOf(500.0, 1250.0, 2250.0, 2750.0)
    private val BUILD_XP = doubleArrayOf(22.5, 48.0, 72.0, 112.0)
    private val REPAIR_XP = doubleArrayOf(127.5, 160.0, 190.0, 240.0)

    /** State values of a spot (the OSRS varbit): 0 / 2 finished, 1 to repair, 3 old furniture to remove, 4 empty space to build on. */
    private const val DONE = 2
    private const val REPAIR = 1
    private const val REMOVE = 3
    private const val SPACE = 4

    private const val BUILD_ANIM = 3676
    private const val REMOVE_ANIM = 3685

    /** "tier|home|layout|varbit=state,..." */
    val CONTRACT = AttributeKey<String>(persistenceKey = "mahogany_contract")
    val CARPENTER_POINTS = AttributeKey<Int>(persistenceKey = "carpenter_points")
    val CONTRACTS_DONE = AttributeKey<Int>(persistenceKey = "mahogany_contracts")
    val LAST_TIER = AttributeKey<Int>(persistenceKey = "mahogany_last_tier")
    private val SYNC_KEY = AttributeKey<Long>()
    private val RESEND = AttributeKey<Int>()
    val SYNC_TIMER = TimerKey()

    class Spot(val varbit: Int, val tile: Tile, val type: Int, val rot: Int, val variants: IntArray) {
        val base get() = variants[0]
    }

    class Home(val name: String, val npc: Int, val city: String, val npcTile: Tile, val where: String, val spots: List<Spot>) {
        fun spot(varbit: Int) = spots.first { it.varbit == varbit }
    }

    class Entry(val varbit: Int, val count: Int, val steel: Boolean, val repair: Boolean)

    class Layout(val tier: Int, val home: Int, val entries: List<Entry>)

    class Contractor(val name: String, val npc: Int, val tile: Tile)

    private fun tile(s: String) = s.split(',').map(String::toInt).let { Tile(it[0], it[1], it[2]) }

    val HOMES: List<Home> =
        MahoganyHomesData.HOMES.map { line ->
            val p = line.split('|')
            val spots =
                p[5].split(';').map { s ->
                    val f = s.split(':')
                    Spot(f[0].toInt(), Tile(f[1].toInt(), f[2].toInt(), f[3].toInt()), f[4].toInt(), f[5].toInt(), f[6].split('/').map(String::toInt).toIntArray())
                }
            Home(p[0], p[1].toInt(), p[2], tile(p[3]), p[4], spots)
        }

    val CONTRACTORS: List<Contractor> = MahoganyHomesData.CONTRACTORS.map { it.split('|').let { p -> Contractor(p[0], p[1].toInt(), tile(p[2])) } }

    val LAYOUTS: List<Layout> =
        MahoganyHomesData.LAYOUTS.map { line ->
            val p = line.split('|')
            Layout(
                p[0].toInt(),
                HOMES.indexOfFirst { it.name == p[1] },
                p[2].split(',').map { e -> e.split(':').let { Entry(it[0].toInt(), it[1].toInt(), it[2] == "S", it[3] == "1") } },
            )
        }

    private val SPOT_AT: Map<Tile, Pair<Int, Spot>> = HOMES.flatMapIndexed { i, h -> h.spots.map { it.tile to (i to it) } }.toMap()

    val AMY get() = CONTRACTORS.first { it.name == "Amy" }

    class Contract(val tier: Int, val home: Int, val layout: Int, val states: MutableMap<Int, Int>) {
        val homeDef get() = HOMES[home]
        val layoutDef get() = LAYOUTS[layout]

        fun finished() = states.values.all { it == DONE || it == 0 }

        fun encode() = "$tier|$home|$layout|" + states.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    fun contract(player: Player): Contract? =
        player.attr[CONTRACT]?.takeIf { it.isNotEmpty() }?.let { s ->
            runCatching {
                val p = s.split('|')
                Contract(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].split(',').associate { e -> e.split('=').let { it[0].toInt() to it[1].toInt() } }.toMutableMap())
            }.getOrNull()
        }

    private fun save(
        player: Player,
        c: Contract?,
    ) {
        if (c == null) player.attr.remove(CONTRACT) else player.attr[CONTRACT] = c.encode()
    }

    // ---------------------------------------------------------------- world

    private fun layer(type: Int) =
        when (type) {
            in 0..3 -> 0
            in 4..8 -> 1
            22 -> 3
            else -> 2
        }

    /** Boot: the finished furniture of every home spot (variant 0) in place of whatever the rev-667 map had on that tile and layer. */
    fun spawnWorld(world: World) {
        HOMES.forEach { home ->
            home.spots.forEach { spot ->
                world.chunks.get(spot.tile, createIfNeeded = true)
                    ?.getEntities<GameObject>(spot.tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                    ?.filter { layer(it.type) == layer(spot.type) }
                    ?.forEach { world.remove(it) }
                world.spawn(DynamicObject(spot.base, spot.type, spot.rot, spot.tile))
            }
        }
    }

    fun spotAt(tile: Tile): Pair<Int, Spot>? = SPOT_AT[tile]

    // ---------------------------------------------------------------- per-player view

    private fun shown(
        spot: Spot,
        state: Int,
    ): Int? = if (state == 0) null else spot.variants.getOrNull(state)?.takeIf { it >= 0 && it != spot.base }

    /** Sends [player] the contract view of the home (or the plain furniture after the contract) and keeps the engine alias in step. */
    fun sync(
        player: Player,
        home: Int? = contract(player)?.home,
        states: Map<Int, Int>? = contract(player)?.states,
    ) {
        if (home == null) return
        val aliases = player.attr[LOC_ALIAS_ATTR] ?: HashMap<Long, Int>().also { player.attr[LOC_ALIAS_ATTR] = it }
        HOMES[home].spots.forEach { spot ->
            val key = locAliasKey(spot.tile, spot.base)
            val show = shown(spot, states?.get(spot.varbit) ?: 0)
            if (show == null) aliases.remove(key) else aliases[key] = show
            send(player, spot, show ?: spot.base)
        }
    }

    private fun send(
        player: Player,
        spot: Spot,
        id: Int,
    ) {
        val base = player.lastKnownRegionBase ?: return
        if (!player.isOnline || !player.tile.isWithinRadius(spot.tile, 48)) return
        val local = base.toLocal(spot.tile)
        if (local.x !in 0 until 104 || local.z !in 0 until 104) return
        val settings = (spot.type shl 2) or (spot.rot and 3)
        val hash = (spot.tile.chunkOffsetX shl 4) or (spot.tile.chunkOffsetZ and 0x7)
        player.write(UpdateZonePartialFollowsMessage(local.x shr 3, local.z shr 3, spot.tile.height))
        player.write(LocAddChangeMessage(id, settings, hash))
    }

    /** Every 2 ticks while a contract runs: after a map rebuild (new region base) the client shows the world's furniture again. */
    fun tick(player: Player) {
        val c = contract(player) ?: return
        val home = c.homeDef
        if (home.spots.none { player.tile.isWithinRadius(it.tile, 48) }) {
            player.attr.remove(SYNC_KEY)
        } else {
            val base = player.lastKnownRegionBase
            val key = if (base == null) -1L else (base.x.toLong() shl 20) or base.z.toLong()
            if (player.attr[SYNC_KEY] != key) {
                player.attr[SYNC_KEY] = key
                player.attr[RESEND] = 2
            }
            val left = player.attr[RESEND] ?: 0
            if (left > 0) {
                player.attr[RESEND] = left - 1
                sync(player, c.home, c.states)
            }
        }
        player.timers[SYNC_TIMER] = 2
    }

    // ---------------------------------------------------------------- contracts

    fun planksName(tier: Int) = listOf("planks", "oak planks", "teak planks", "mahogany planks")[tier]

    fun tiersFor(player: Player) = LEVELS.indices.filter { player.skills.getCurrentLevel(Skills.CONSTRUCTION) >= LEVELS[it] }

    /** A new contract of [tier] for [player] (the caller checked there is none); returns the home. */
    fun start(
        player: Player,
        tier: Int,
    ): Home {
        val previous = player.attr[CONTRACT]
        val choices = LAYOUTS.indices.filter { LAYOUTS[it].tier == tier }
        val pick = choices.random()
        val layout = LAYOUTS[pick]
        val states = layout.entries.associate { it.varbit to if (it.repair) REPAIR else REMOVE }.toMutableMap()
        val c = Contract(tier, layout.home, pick, states)
        save(player, c)
        player.attr[LAST_TIER] = tier
        if (previous == null) player.attr.remove(SYNC_KEY)
        player.attr[RESEND] = 2
        player.timers[SYNC_TIMER] = 1
        sync(player, c.home, c.states)
        return c.homeDef
    }

    fun describe(c: Contract) = "${c.homeDef.name} ${c.homeDef.where}"

    /** Talk-to a homeowner: finish the contract when every task is done. Returns true when it handled the talk. */
    suspend fun ownerTalk(
        task: QueueTask,
        homeIndex: Int,
    ) {
        val player = task.player
        val c = contract(player)
        if (c == null || c.home != homeIndex) {
            task.chatNpc("Hello there! I'm afraid I'm not expecting anyone from Mahogany Homes right now.")
            return
        }
        if (!c.finished()) {
            task.chatNpc("Oh, hello! The work isn't finished yet - please carry on, I'll be right here.")
            return
        }
        val tier = c.tier
        save(player, null)
        sync(player, homeIndex, null)
        player.attr[CARPENTER_POINTS] = (player.attr[CARPENTER_POINTS] ?: 0) + POINTS[tier]
        player.attr[CONTRACTS_DONE] = (player.attr[CONTRACTS_DONE] ?: 0) + 1
        player.addXp(Skills.CONSTRUCTION, COMPLETION_XP[tier])
        task.chatNpc("Thank you so much, it looks wonderful! I'll let Mahogany Homes know you did a great job.")
        player.message(
            "You have completed your contract and been awarded ${POINTS[tier]} carpenter points. " +
                "You now have ${player.attr[CARPENTER_POINTS]} carpenter points.",
        )
    }

    // ---------------------------------------------------------------- furniture

    suspend fun act(
        task: QueueTask,
        obj: GameObject,
        option: String,
    ) {
        val player = task.player
        val (homeIndex, spot) = spotAt(obj.tile) ?: return
        val c = contract(player)
        val state = c?.takeIf { it.home == homeIndex }?.states?.get(spot.varbit)
        if (c == null || state == null) {
            player.message("You have no reason to touch this - it isn't part of your contract.")
            return
        }
        val entry = c.layoutDef.entries.first { it.varbit == spot.varbit }
        when {
            option == "remove" && state == REMOVE -> {
                player.lock = LockState.DELAY_ACTIONS
                player.animate(REMOVE_ANIM)
                task.wait(2)
                c.states[spot.varbit] = SPACE
                player.message("You remove the old furniture.")
            }
            option == "build" && state == SPACE -> {
                if (!materials(task, c, entry, building = true)) return
                player.lock = LockState.DELAY_ACTIONS
                player.animate(BUILD_ANIM)
                task.wait(3)
                take(player, c, entry)
                player.addXp(Skills.CONSTRUCTION, entry.count * BUILD_XP[c.tier])
                c.states[spot.varbit] = DONE
                player.message("You build the new furniture.")
            }
            option == "repair" && state == REPAIR -> {
                if (!materials(task, c, entry, building = false)) return
                player.lock = LockState.DELAY_ACTIONS
                player.animate(BUILD_ANIM)
                task.wait(3)
                take(player, c, entry)
                player.addXp(Skills.CONSTRUCTION, entry.count * REPAIR_XP[c.tier])
                c.states[spot.varbit] = DONE
                player.message("You repair the furniture.")
            }
            else -> {
                player.unlock()
                return
            }
        }
        player.unlock()
        save(player, c)
        sync(player, c.home, c.states)
        if (c.finished()) {
            player.message("You have completed all the work. Speak to ${c.homeDef.name} to finish the contract.")
        }
    }

    private suspend fun materials(
        task: QueueTask,
        c: Contract,
        entry: Entry,
        building: Boolean,
    ): Boolean {
        val player = task.player
        if (!player.inventory.contains(Items.HAMMER) || (!entry.steel && !hasSaw(player))) {
            task.messageBox(if (entry.steel) "You need a hammer to do that." else "You need a hammer and a saw to do that.")
            return false
        }
        val have = if (entry.steel) player.inventory.getItemCount(Items.STEEL_BAR) else planks(player, PLANKS[c.tier])
        if (have < entry.count) {
            val what = if (entry.steel) "steel bar" + (if (entry.count == 1) "" else "s") else planksName(c.tier).let { if (entry.count == 1) it.removeSuffix("s") else it }
            task.messageBox("You need ${entry.count} $what to ${if (building) "build" else "repair"} this.")
            return false
        }
        return true
    }

    private fun take(
        player: Player,
        c: Contract,
        entry: Entry,
    ) {
        if (entry.steel) player.inventory.remove(Items.STEEL_BAR, entry.count) else takePlanks(player, PLANKS[c.tier], entry.count)
    }

    // ---------------------------------------------------------------- tools, outfit, plank sack

    fun hasSaw(player: Player) =
        player.inventory.contains(Items.SAW) || player.inventory.contains(Items.CRYSTAL_SAW) || player.inventory.contains(AMYS_SAW) ||
            player.equipment.contains(AMYS_SAW)

    /** OSRS Wiki "Crystal saw": in the inventory, furniture that needs a hammer and saw can be built up to 3 levels higher. */
    fun crystalSawBoost(player: Player) = if (player.inventory.contains(Items.CRYSTAL_SAW)) 3 else 0

    /** The carpenter's outfit: 0.4 / 0.8 / 0.6 / 0.2 %, 2.5 % as a full set, on every Construction gain. */
    fun outfitBonus(
        player: Player,
        skill: Int,
    ): Double {
        if (skill != Skills.CONSTRUCTION) return 1.0
        val e = player.equipment
        val worn = listOf(HELMET, SHIRT, TROUSERS, BOOTS).map { e.contains(it) }
        if (worn.all { it }) return 1.025
        return 1.0 + listOf(0.004, 0.008, 0.006, 0.002).filterIndexed { i, _ -> worn[i] }.sum()
    }

    private fun sackSlot(player: Player) = player.inventory.getItemIndex(PLANK_SACK, skipAttrItems = false)

    /** The sack's planks, packed 5 bits per kind (plank, oak, teak, mahogany) in its CHARGES - at most 28 in all. */
    fun sackContents(item: Item?): IntArray {
        val packed = item?.getAttr(ItemAttribute.CHARGES) ?: 0
        return IntArray(4) { (packed shr (5 * it)) and 31 }
    }

    private fun setSack(
        player: Player,
        slot: Int,
        counts: IntArray,
    ) {
        val item = Item(PLANK_SACK)
        val packed = counts.foldIndexed(0) { i, acc, n -> acc or (n.coerceIn(0, 28) shl (5 * i)) }
        if (packed != 0) item.putAttr(ItemAttribute.CHARGES, packed)
        player.inventory[slot] = item
    }

    fun planks(
        player: Player,
        plank: Int,
    ): Int {
        val i = PLANKS.indexOf(plank)
        val inSack = if (i < 0) 0 else sackSlot(player).takeIf { it >= 0 }?.let { sackContents(player.inventory[it])[i] } ?: 0
        return player.inventory.getItemCount(plank) + inSack
    }

    /** Takes [count] of [plank]: the inventory first, then the sack. */
    fun takePlanks(
        player: Player,
        plank: Int,
        count: Int,
    ) {
        val fromInv = minOf(count, player.inventory.getItemCount(plank))
        if (fromInv > 0) player.inventory.remove(plank, fromInv)
        var left = count - fromInv
        val i = PLANKS.indexOf(plank)
        val slot = sackSlot(player)
        if (left > 0 && i >= 0 && slot >= 0) {
            val c = sackContents(player.inventory[slot])
            val t = minOf(left, c[i])
            c[i] -= t
            left -= t
            setSack(player, slot, c)
        }
    }

    fun fillSack(player: Player) {
        val slot = sackSlot(player).takeIf { it >= 0 } ?: return
        val c = sackContents(player.inventory[slot])
        var room = 28 - c.sum()
        if (room <= 0) {
            player.message("Your sack is full.")
            return
        }
        var added = 0
        PLANKS.forEachIndexed { i, plank ->
            val n = minOf(room, player.inventory.getItemCount(plank))
            if (n > 0) {
                player.inventory.remove(plank, n)
                c[i] += n
                room -= n
                added += n
            }
        }
        if (added == 0) {
            player.message("You don't have any planks to put in the sack.")
            return
        }
        setSack(player, sackSlot(player), c)
        check(player)
    }

    fun emptySack(player: Player) {
        val slot = sackSlot(player).takeIf { it >= 0 } ?: return
        val c = sackContents(player.inventory[slot])
        if (c.sum() == 0) {
            player.message("Your sack is empty.")
            return
        }
        PLANKS.forEachIndexed { i, plank ->
            val n = minOf(c[i], player.inventory.freeSlotCount)
            if (n > 0 && player.inventory.add(plank, n).hasSucceeded()) c[i] -= n
        }
        setSack(player, sackSlot(player), c)
        if (c.sum() > 0) player.message("You don't have enough inventory space to empty the whole sack.")
        check(player)
    }

    fun check(player: Player) {
        val c = sackContents(sackSlot(player).takeIf { it >= 0 }?.let { player.inventory[it] })
        if (c.sum() == 0) {
            player.message("Your sack is empty.")
            return
        }
        player.message(
            "Your sack contains: " +
                listOf("plank", "oak plank", "teak plank", "mahogany plank").mapIndexedNotNull { i, n -> if (c[i] == 0) null else "${c[i]} x $n" }
                    .joinToString(", ") + ".",
        )
    }

    // ---------------------------------------------------------------- supply crate

    /** One roll (OSRS Wiki drop table, 144ths): item, min, max, weight. All noted. */
    private val CRATE =
        listOf(
            listOf(Items.BOLT_OF_CLOTH, 9, 9, 5), listOf(Items.BOLT_OF_CLOTH, 10, 10, 1),
            listOf(Items.LIMESTONE_BRICK, 9, 9, 5), listOf(Items.LIMESTONE_BRICK, 10, 10, 1),
            listOf(Items.MAHOGANY_PLANK, 6, 6, 15), listOf(Items.MAHOGANY_PLANK, 7, 7, 3),
            listOf(Items.OAK_PLANK, 28, 28, 10), listOf(Items.OAK_PLANK, 29, 29, 15), listOf(Items.OAK_PLANK, 30, 30, 5),
            listOf(Items.SOFT_CLAY, 45, 45, 5), listOf(Items.SOFT_CLAY, 46, 46, 10), listOf(Items.SOFT_CLAY, 47, 47, 10),
            listOf(Items.SOFT_CLAY, 48, 48, 5),
            listOf(Items.STEEL_BAR, 23, 23, 5), listOf(Items.STEEL_BAR, 24, 24, 20), listOf(Items.STEEL_BAR, 25, 25, 5),
            listOf(Items.TEAK_PLANK, 15, 15, 20), listOf(Items.TEAK_PLANK, 16, 16, 4),
        )

    /** Opens one crate from [slot]; false when there is no room for the loot. */
    fun openCrate(
        player: Player,
        slot: Int,
    ): Boolean {
        var roll = (0 until CRATE.sumOf { it[3] }).random()
        val row = CRATE.first { r -> (roll < r[3]).also { roll -= r[3] } }
        val noted = Item(row[0]).toNoted(player.world.definitions).id
        if (player.inventory.getItemCount(noted) == 0 && player.inventory.freeSlotCount == 0 && player.inventory[slot]?.amount != 1) {
            player.message("You need some free inventory space to open the crate.")
            return false
        }
        player.inventory.remove(SUPPLY_CRATE, 1, beginSlot = slot)
        player.inventory.add(noted, row[1])
        return true
    }
}
