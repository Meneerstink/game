package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.summoning.Pets

/**
 * CONSTRUCTION-99 deel 6 (2026-11-26): the menagerie (OSRS Menagerie, outdoors: level 37, 30,000 coins, one per house). Pet houses,
 * pet feeders and habitats are built from [PohFurnitureData] (Darkan ids, OSRS Wiki rows); this object does what they are for.
 *
 * - Pet house "Store" (Darkan `PetHouse`, rev-667 interfaces 879 + 878, inv 540): pets from the inventory go into the house and come
 *   back out with "Withdraw". The stored pets are one container on the player ([KEY]), so the shared ownership check
 *   ([gg.rsmod.plugins.content.mechanics.ownership.Possessions]) sees them: a pet in the menagerie still counts for the duplicate
 *   rule and for Perdu, as OSRS ("in their bank or in the POH").
 * - While the owner is home (not in building mode) every stored pet roams the menagerie (OSRS; Darkan `HouseNPCs.spawnPets`). Those
 *   pets belong to nobody: every pet option checks [Pets.current], so they can't be picked up, shooed or morphed.
 * - "Upgrade" on a pet house builds the next house for the OSRS "(Upgrade)" row ([UPGRADES]); on a feeder for the next feeder's
 *   materials. "Feed-all" is the feeder's flavour (pets have no hunger here).
 * - A pet house (or the menagerie) can't be removed while pets are in it.
 */
object PohMenagerie {
    const val WINDOW = 879
    const val WINDOW_GRID = 13
    const val WINDOW_TITLE = 10
    const val WINDOW_CLOSE = 11
    const val SIDE = 878
    const val SIDE_GRID = 0
    const val INV = 540
    private const val INVENTORY_INV = 93
    private const val COLUMNS = 12
    private const val ROWS = 6

    /** IF_SETEVENTS: op1 (Withdraw / Store) + op10 (Examine). */
    private const val EVENTS = 0x402

    /** Darkan `PetHouse`: 72 slots (the 12x6 grid of 879). */
    val KEY = ContainerKey("menagerie", capacity = COLUMNS * ROWS, stackType = ContainerStackType.NO_STACK)

    val PET_HOUSES = setOf("OAK_PET_HOUSE", "TEAK_PET_HOUSE", "MAHOGANY_PET_HOUSE", "CONSECRATED_PET_HOUSE", "DESECRATED_PET_HOUSE", "NATURAL_PET_HOUSE")
    private val HOUSE_IDS = PET_HOUSES.mapNotNull { PohFurniture.DECOS[it]?.objs?.first() }.toSet()
    private val FEEDER_IDS = listOf("OAK_PET_FEEDER", "TEAK_PET_FEEDER", "MAHOGANY_PET_FEEDER").mapNotNull { PohFurniture.DECOS[it]?.objs?.first() }.toSet()

    /** OSRS Wiki "(Upgrade)" rows: the next pet house built on the old one. */
    val UPGRADES: Map<String, PohBuild.Piece> =
        mapOf(
            "TEAK_PET_HOUSE" to PohBuild.Piece(44829, "Teak pet house", 48, 360.0, listOf(8780 to 4)),
            "MAHOGANY_PET_HOUSE" to PohBuild.Piece(44830, "Mahogany pet house", 59, 560.0, listOf(8782 to 4)),
            "CONSECRATED_PET_HOUSE" to PohBuild.Piece(44831, "Consecrated pet house", 70, 1560.0, listOf(8782 to 4, 8788 to 1)),
            "DESECRATED_PET_HOUSE" to PohBuild.Piece(44832, "Desecrated pet house", 81, 160.0, listOf(8782 to 1, 3420 to 1)),
            "NATURAL_PET_HOUSE" to PohBuild.Piece(44833, "Natural pet house", 92, 158.0, listOf(8782 to 1, 1929 to 2, 6034 to 3)),
        )

    private val OPEN_ATTR = AttributeKey<Boolean>()
    private val ROAMING_ATTR = AttributeKey<List<Npc>>()

    fun handles(id: Int): Boolean = id in HOUSE_IDS || id in FEEDER_IDS

    fun container(player: Player): ItemContainer = player.containers.getOrPut(KEY) { ItemContainer(player.world.definitions, KEY) }

    fun stored(player: Player): Int = player.containers[KEY]?.occupiedSlotCount ?: 0

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        when (option) {
            "store" -> if (obj.id in HOUSE_IDS) open(player) else return false
            "upgrade" -> player.queue { PohConstruct.upgradePiece(this, obj) }
            "feed-all" -> player.message("You fill the feeder. Your pets will never go hungry.")
            else -> return false
        }
        return true
    }

    fun isOpen(player: Player): Boolean = player.attr.getOrDefault(OPEN_ATTR, false)

    fun open(player: Player) {
        if (!PlayerHouse.ownsHouseAt(player)) {
            player.message("This isn't your pet house.")
            return
        }
        player.attr[OPEN_ATTR] = true
        player.openInterface(WINDOW, InterfaceDestination.MAIN_SCREEN)
        player.openInterface(SIDE, InterfaceDestination.TAB_AREA)
        player.setComponentText(WINDOW, WINDOW_TITLE, "Pet house")
        player.runClientScript(150, (WINDOW shl 16) or WINDOW_GRID, INV, COLUMNS, ROWS, 0, "Withdraw")
        player.setEvents(WINDOW, WINDOW_GRID, to = COLUMNS * ROWS, setting = EVENTS)
        player.runClientScript(150, (SIDE shl 16) or SIDE_GRID, INVENTORY_INV, 4, 7, 0, "Store")
        player.setEvents(SIDE, SIDE_GRID, to = player.inventory.capacity, setting = EVENTS)
        refresh(player)
    }

    fun refresh(player: Player) {
        player.sendItemContainer(INV, container(player))
        player.inventory.dirty = true
    }

    fun close(player: Player) {
        if (!isOpen(player)) return
        player.closeInterface(WINDOW)
        onClosed(player)
    }

    /** The side panel goes back to the backpack however the window closed (Familiar inventory does the same). */
    fun onClosed(player: Player) {
        if (!isOpen(player)) return
        player.attr[OPEN_ATTR] = false
        player.closeInterface(InterfaceDestination.TAB_AREA)
        player.openInterface(InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
    }

    /** "Store" on an inventory slot: a pet goes into the house. */
    fun store(
        player: Player,
        slot: Int,
    ) {
        if (!isOpen(player) || !PlayerHouse.ownsHouseAt(player)) return
        val item = player.inventory[slot] ?: return
        if (!Pets.isPetItem(item.id)) {
            player.message("Only pets can live in your pet house.")
            return
        }
        val house = container(player)
        if (!house.hasSpace) {
            player.message("Your pet house is full.")
            return
        }
        if (player.inventory.remove(item.id, 1, beginSlot = slot).hasSucceeded()) {
            house.add(item.id, 1)
            refreshPets(player)
        }
        refresh(player)
    }

    /** "Withdraw" on a house slot: the pet back into the inventory. */
    fun withdraw(
        player: Player,
        slot: Int,
    ) {
        if (!isOpen(player) || !PlayerHouse.ownsHouseAt(player)) return
        val house = container(player)
        val item = house[slot] ?: return
        if (!player.inventory.hasSpace) {
            player.message("You don't have enough inventory space.")
            return
        }
        if (house.remove(item.id, 1, beginSlot = slot).hasSucceeded()) {
            player.inventory.add(item.id, 1, assureFullInsertion = true)
            house.shift()
            refreshPets(player)
        }
        refresh(player)
    }

    /**
     * The stored pets that roam the menagerie: removed and placed again whenever the house is built or the pets change. Only while
     * the owner stands in their own house outside building mode and the menagerie exists.
     */
    fun refreshPets(player: Player) {
        val world = player.world
        player.attr[ROAMING_ATTR]?.forEach { if (it.isSpawned()) world.remove(it) }
        player.attr.remove(ROAMING_ATTR)
        if (!PlayerHouse.ownsHouseAt(player) || PlayerHouse.isBuildMode(player)) return
        val room = PohRooms.rooms(player).firstOrNull { it.type == PohRooms.RoomType.MENAGERIE } ?: return
        val area = PlayerHouse.houseAreaOf(player) ?: return
        val corner = PohRooms.cornerOf(area.bottomLeftX, area.bottomLeftZ, room.x, room.z, room.plane)
        val pets = player.containers[KEY]?.rawItems?.filterNotNull() ?: return
        val spawned = mutableListOf<Npc>()
        pets.forEachIndexed { i, item ->
            val npcId = Pets.variantOfItem(item.id)?.npc ?: return@forEachIndexed
            val start = corner.transform(2 + (i * 3) % 4, 2 + (i * 5) % 4)
            val tile = gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(world, start, radius = 2) ?: return@forEachIndexed
            val npc = Npc(npcId, tile, world)
            npc.walkRadius = 3
            world.spawn(npc)
            spawned += npc
        }
        player.attr[ROAMING_ATTR] = spawned
    }
}
