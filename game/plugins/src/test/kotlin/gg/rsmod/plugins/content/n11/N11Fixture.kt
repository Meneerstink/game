package gg.rsmod.plugins.content.n11

import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.plugin.PluginRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.URLClassLoader

/** Cache-free inventories; compiled script tests still run the production handlers and queues. */
internal class N11Fixture : AutoCloseable {
    val definitions = mockk<DefinitionSet>()
    val defs = mutableMapOf<Int, ItemDef>()
    val world = mockk<World>(relaxed = true)
    val player = mockk<Player>(relaxed = true)
    val attrs = AttributeMap()
    val timers = TimerMap()
    val skills = mockk<SkillSet>(relaxed = true)
    val inventory = container()
    val equipment = container()
    val queues = PawnQueueTaskSet()
    val repo = PluginRepository(world)
    private val loader = URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader)

    init {
        every { definitions.get(ItemDef::class.java, any()) } answers { def(secondArg()) }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { def(secondArg()) }
        every { player.world } returns world
        every { world.definitions } returns definitions
        every { world.plugins } returns repo
        every { world.random(any<IntRange>()) } returns 0
        every { player.attr } returns attrs
        every { player.timers } returns timers
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.skills } returns skills
        every { player.tile } returns Tile(3200, 3200)
        every { player.lock } returns gg.rsmod.game.model.LockState.NONE
        every { skills.getMaxLevel(any()) } returns 99
        every { skills.getCurrentLevel(any()) } returns 50
        every { player.executePlugin<Unit>(any()) } answers { firstArg<Plugin.() -> Unit>().invoke(Plugin(player)) }
        every { player.queue(any(), any(), any()) } answers {
            queues.queue(player, Dispatchers.Unconfined, firstArg(), thirdArg(), persistent = secondArg())
        }
        every { player.lockingQueue(any(), any(), any()) } answers {
            queues.queue(player, Dispatchers.Unconfined, firstArg(), thirdArg())
        }
        File("src/test/resources/n11-item-menus.tsv").readLines().filter { !it.startsWith("#") }.forEach { row ->
            val c = row.split('\t')
            val definition = def(c[0].toInt())
            c[1].split('|').forEachIndexed { i, option -> definition.inventoryMenu[i] = option.ifEmpty { null } }
            c[2].split('|').forEachIndexed { i, option -> definition.equipmentMenu[i] = option.ifEmpty { null } }
        }
        select(0)
    }

    fun container() = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
    fun def(id: Int): ItemDef = defs.getOrPut(id) { ItemDef(id).apply { name = "item $id" } }
    fun menu(id: Int, vararg options: String) { options.forEachIndexed { i, name -> def(id).inventoryMenu[i] = name } }
    fun select(slot: Int) { attrs[INTERACTING_ITEM_SLOT] = slot; attrs[INTERACTING_SLOT_ATTR] = slot }
    fun load(name: String): Any =
        loader.loadClass("gg.rsmod.plugins.content.$name")
            .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
            .newInstance(repo, world, mockk<Server>(relaxed = true))

    fun loadConsumables() {
        val rows = gg.rsmod.plugins.content.items.food.ConsumableTable.read(File("../../data/cfg/consumables.tsv"))
        rows.forEach { row ->
            // Respect the census menu positions for ids already present in the fixture.
            if (def(row.id).inventoryMenu.none { it == if (row.drink) "Drink" else "Eat" }) {
                menu(row.id, if (row.drink) "Drink" else "Eat")
            }
        }
        val script = load("items.food.Consumables_plugin")
        // The server resolves its data directory at process cwd; tests inject the same parser's checked-in table.
        script.javaClass.getDeclaredField("CONSUMABLES").apply { isAccessible = true }.set(script, rows)
        every { world.executePlugin(any(), any()) } answers {
            secondArg<Plugin.() -> Unit>().invoke(Plugin(firstArg()))
        }
        repo.executeWorldInit(world)
    }
    override fun close() { queues.terminateTasks(); loader.close() }
}
