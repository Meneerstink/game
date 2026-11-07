package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.queue.impl.WorldQueueTaskSet
import gg.rsmod.game.sync.block.UpdateBlockSet
import gg.rsmod.game.sync.block.UpdateBlockStructure
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.content.activity.toa.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers

/** Runs the real adapters and world queues. Synthetic IDs/definitions isolate mechanics; this is NOT cache/import evidence. */
internal class ToaPuzzleFixture(count: Int, val path: ToaPath, factory: (ToaRaid) -> ToaRoom) : AutoCloseable {
    val world = mockk<World>(relaxed = true)
    val definitions = mockk<DefinitionSet>(relaxed = true)
    val queues = WorldQueueTaskSet()
    val entities = linkedSetOf<Entity>()
    val tiles = hashMapOf<PlayerUID, Tile>()
    val dead = hashSetOf<PlayerUID>()
    var cycle = 0
    private var nextNpcIndex = 1
    val map = mockk<InstancedMap>(relaxed = true)
    val players: List<Player>
    val raid: ToaRaid
    private val registration: AutoCloseable

    init {
        mockkObject(ToaIds, ToaRecovery)
        every { ToaIds.npc(any()) } answers { firstArg<Int>() + 50000 }
        every { ToaIds.item(any()) } answers { firstArg<Int>() + 50000 }
        every { ToaIds.loc(any()) } answers { firstArg<Int>() + 50000 }
        every { ToaIds.seq(any()) } returns null
        every { ToaIds.npcSeq(any(), any()) } returns null
        every { ToaIds.spot(any()) } returns null
        every { ToaIds.osrsNpcOf(any()) } answers { firstArg<Int>() - 50000 }
        every { ToaRecovery.challenge(any()) } just Runs
        every { ToaRecovery.full(any()) } just Runs
        every { world.gameContext.npcStatCount } returns 5
        val blocks = UpdateBlockSet().also { set ->
            UpdateBlockType.values().forEachIndexed { index, block -> set.updateBlocks[block] = UpdateBlockStructure(1 shl index, emptyList()) }
        }
        every { world.npcUpdateBlocks } returns blocks
        every { world.definitions } returns definitions
        every { definitions.get(NpcDef::class.java, any()) } answers {
            NpcDef(secondArg()).also { it.size = if (secondArg<Int>() - 50000 in 11700..11704) 5 else 1 }
        }
        every { definitions.get(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).also { it.stacks = secondArg<Int>() == 50000 + 27297 }
        }
        every { definitions.get(ObjectDef::class.java, any()) } answers { ObjectDef(secondArg()) }
        every { world.currentCycle } answers { cycle }
        every { world.queue(any()) } answers { queues.queue(world, Dispatchers.Unconfined, TaskPriority.STANDARD, firstArg()) }
        every { world.locLayer(any()) } answers { when (firstArg<Int>()) { in 0..3 -> 0; in 4..8 -> 1; 22 -> 3; else -> 2 } }
        every { world.spawn(any<Npc>()) } answers { firstArg<Npc>().also { it.index = nextNpcIndex++; entities += it }; true }
        every { world.remove(any<Npc>()) } answers { firstArg<Npc>().also { entities -= it; it.index = -1 }; Unit }
        every { world.spawn(any<GameObject>()) } answers { entities += firstArg<GameObject>(); Unit }
        every { world.remove(any<GameObject>()) } answers { entities -= firstArg<GameObject>(); Unit }
        every { world.spawn(any<GroundItem>()) } answers { entities += firstArg<GroundItem>(); Unit }
        every { world.remove(any<GroundItem>()) } answers { entities -= firstArg<GroundItem>(); Unit }
        every { world.isSpawned(any<GameObject>()) } answers { firstArg<GameObject>() in entities }
        every { world.isSpawned(any<GroundItem>()) } answers { firstArg<GroundItem>() in entities }
        val area = Area(6400, 0, 6848, 448)
        every { map.area } returns area
        every { map.sourceTile(any()) } answers {
            val tile = firstArg<Tile>()
            if (!area.contains(tile)) null else Tile(tile.x - 6400 + ToaMap.SOURCE_BASE_X + ToaMap.CACHE_OFFSET_X,
                tile.z + ToaMap.SOURCE_BASE_Z + ToaMap.CACHE_OFFSET_Z, tile.height)
        }
        players = (1..count).map { number ->
            val player = mockk<Player>(relaxed = true)
            val uid = PlayerUID(number)
            tiles[uid] = ToaMap.instance(map, path.challengeEntry)
            every { player.uid } returns uid
            every { player.index } returns number
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { player.tile } answers { tiles.getValue(uid) }
            every { player.isDead() } answers { uid in dead }
            every { player.isOnline } returns true
            every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
            every { player.teleportTo(any()) } answers { tiles[uid] = firstArg(); Unit }
            player
        }
        every { world.players } returns PawnList(arrayOfNulls<Player>(10).also { slots -> players.forEachIndexed { i, p -> slots[i + 1] = p } })
        registration = ToaRooms.replaceForTest(ToaRoomKey(path, false), factory)
        raid = ToaRaid(1, players.map { it.uid }, ToaRaidSettings(), map, world)
        players.forEach { check(raid.enterChallenge(it, path)) }
    }

    fun advance(count: Int = 1) { repeat(count) { cycle++; queues.cycle() } }
    fun at(player: Player, tile: Tile) { tiles[player.uid] = ToaMap.instance(map, tile) }
    fun obj(osrs: Int, tile: Tile) = DynamicObject(osrs + 50000, 10, 0, ToaMap.instance(map, tile))
    override fun close() {
        raid.close(); registration.close()
        unmockkObject(ToaIds, ToaRecovery)
    }
}
