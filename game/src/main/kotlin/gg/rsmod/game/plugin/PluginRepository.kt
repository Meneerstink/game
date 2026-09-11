package gg.rsmod.game.plugin

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import gg.rsmod.game.Server
import gg.rsmod.game.event.Event
import gg.rsmod.game.model.SimplePolygonArea
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.COMMAND_ARGS_ATTR
import gg.rsmod.game.model.attr.COMMAND_ATTR
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import io.github.classgraph.ClassGraph
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import mu.KLogging
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A repository that is responsible for storing and executing plugins, as well
 * as making sure no plugin collides.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class PluginRepository(
    val world: World,
) {
    /**
     * The total amount of plugins.
     */
    private var pluginCount = 0

    /**
     * Plugins that get executed when the world is initialised.
     */
    private val worldInitPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * Plugins that get executed once every plugin in [worldInitPlugins] has finished.
     *
     * Gap-filling fallbacks - "bind this only where nobody has written real behaviour" - have to
     * observe the complete set of bindings to be correct. [worldInitPlugins] run in plugin
     * discovery order, so a fallback registered from an ordinary world-init block races every
     * other world-init block: it either misses bindings it should have deferred to, or, since
     * [bindObject]/[bindNpc]/[bindItem] all throw on a duplicate, prevents the server from booting
     * at all. Registering the fallback here instead makes that deterministic.
     */
    private val lateWorldInitPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * The plugin that will executed when changing display modes.
     */
    private var windowStatusPlugin: (Plugin.() -> Unit)? = null

    /**
     * The plugin that will be executed when the core module wants
     * close the main modal the player has opened.
     *
     * This is used for things such as the [gg.rsmod.game.message.impl.MoveGameClickMessage].
     */
    private var closeModalPlugin: (Plugin.() -> Unit)? = null

    /**
     * This plugin is used to check if a player has a menu opened and any
     * [gg.rsmod.game.model.queue.QueueTask] with a [gg.rsmod.game.model.queue.TaskPriority.STANDARD]
     * priority should wait before executing.
     */
    private var isMenuOpenedPlugin: (Plugin.() -> Boolean)? = null

    /**
     * A list of plugins that will be executed upon login.
     */
    private val loginPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be executed upon logout.
     */
    private val logoutPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be executed upon an [gg.rsmod.game.model.entity.Npc]
     * being spawned into the world. Use sparingly.
     */
    private val globalNpcSpawnPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be executed upon an [gg.rsmod.game.model.entity.Npc]
     * with a specific id being spawned into the world. Use sparingly per npc.
     *
     * Note: any npc added to this map <strong>will</strong> still invoke the
     * [globalNpcSpawnPlugins] plugin.
     */
    private val npcSpawnPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * The plugin that will handle initiating combat.
     */
    private var combatPlugin: (Plugin.() -> Unit)? = null

    /**
     * The plugin that will handle on death.
     */
    private var slayerLogic: (Plugin.() -> Unit)? = null

    /**
     * A map of plugins that contain custom combat plugins for specific npcs.
     */
    private val npcCombatPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins that will handle spells on npcs depending on the interface
     * hash of the spell.
     */
    private val spellOnNpcPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains plugins that should be executed when the [TimerKey]
     * hits a value of [0] time left.
     */
    private val timerPlugins = hashMapOf<TimerKey, Plugin.() -> Unit>()

    /**
     * A map that contains plugins that should be executed when an interface
     * is opened.
     */
    private val interfaceOpenPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains plugins that should be executed when an interface
     * is closed.
     */
    private val interfaceClosePlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains command plugins. The pair has the privilege power
     * required to use the command on the left, and the plugin on the right.
     *
     * The privilege power left value can be set to null, which means anyone
     * can use the command.
     */
    private val commandPlugins = hashMapOf<String, Pair<String?, Plugin.() -> Unit>>()

    /**
     * A map of button click plugins. The key is a shifted value of the parent
     * and child id.
     */
    private val buttonPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of equipment option plugins.
     */
    private val equipmentOptionPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins that contain plugins that should execute when equipping
     * items from a certain equipment slot.
     */
    private val equipSlotPlugins: Multimap<Int, Plugin.() -> Unit> = HashMultimap.create()

    /**
     * A map of plugins that contain plugins that should execute when un-equipping
     * items from a certain equipment slot.
     */
    private val unequipSlotPlugins: Multimap<Int, Plugin.() -> Unit> = HashMultimap.create()

    /**
     * Audit finding 9 remainder: [EquipAction.equip] silently moves whatever is already
     * equipped in a conflicting slot into the player's real inventory when a new item is worn
     * over it (a "gear replacement" swap), with no requirement hook at all - unlike equipping a
     * new item ([equipItemRequirementPlugins]) or the explicit Remove button (guarded manually
     * per call site), nothing could stop that swap. Multimap (like [unequipSlotPlugins], not a
     * single-bind map like [equipItemRequirementPlugins]) so more than one plugin can gate the
     * same slot; all must agree before the swap proceeds.
     */
    private val canUnequipSlotPlugins: Multimap<Int, Plugin.() -> Boolean> = HashMultimap.create()

    /**
     * A map of plugins that can stop an item from being equipped.
     */
    private val equipItemRequirementPlugins = Int2ObjectOpenHashMap<Plugin.() -> Boolean>()

    /**
     * A map of plugins that are executed when a player equips an item.
     */
    private val equipItemPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins that are executed when a player un-equips an item.
     */
    private val unequipItemPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A plugin that executes when a player levels up a skill.
     */
    private var skillLevelUps = mutableListOf<(Plugin.() -> Unit)>()

    /**
     * A plugin that executes when a player experience goes up in a skill.
     */
    private var skillExperienceUps = mutableListOf<(Plugin.() -> Unit)>()

    private val componentItemSwapPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    private val componentToComponentItemSwapPlugins = Long2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains any plugin that will be executed upon entering a new
     * region. The key is the region id and the value is a list of plugins
     * that will execute upon entering the region.
     */
    private val enterRegionPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * A map that contains any plugin that will be executed upon leaving a region.
     * The key is the region id and the value is a list of plugins that will execute
     * upon leaving the region.
     */
    private val exitRegionPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * A map that contains any plugin that will be executed upon entering a new
     * [gg.rsmod.game.model.region.Chunk]. The key is the chunk id which can be
     * calculated via [gg.rsmod.game.model.region.ChunkCoords.hashCode].
     */
    private val enterChunkPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * A map that contains any plugin that will be executed upon entering a new
     * [gg.rsmod.game.model.SimplePolygonArea]. The key is the area hash, which can be
     * calculated via [gg.rsmod.game.model.SimplePolygonArea.hashCode].
     */
    private val enterSimplePolygonAreaPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * A map that contains any plugins that will be executed when a song ends on the client
     */
    private val songEndPlugins = mutableListOf<(Plugin.() -> Unit)>()

    /**
     * A map that contains any plugin that will be executed when leaving a
     * [gg.rsmod.game.model.region.Chunk]. The key is the chunk id which can be
     * calculated via [gg.rsmod.game.model.region.ChunkCoords.hashCode].
     */
    private val exitChunkPlugins = Int2ObjectOpenHashMap<MutableList<Plugin.() -> Unit>>()

    /**
     * A map that contains items and any associated menu-click and its respective
     * plugin logic, if any (would not be in the map if it doesn't have a plugin).
     */
    private val itemPlugins = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Plugin.() -> Unit>>()

    /**
     * A map that contains ground items and any associated menu-click and its respective
     * plugin logic, if any (would not be in the map if it doesn't have a plugin).
     */
    private val groundItemPlugins = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Plugin.() -> Unit>>()

    /**
     * A map that contains Boolean functions that will return false when a ground
     * item can not be picked up.
     */
    private val groundItemPickupConditions = Int2ObjectOpenHashMap<Plugin.() -> Boolean>()

    /**
     * A map of plugins that check if an item with the associated key, can be
     * dropped on the floor.
     */
    private val canDropItemPlugins = Int2ObjectOpenHashMap<Plugin.() -> Boolean>()

    /**
     * A map that contains objects and any associated menu-click and its respective
     * plugin logic, if any (would not be in the map if it doesn't have a plugin).
     */
    private val objectPlugins = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Plugin.() -> Unit>>()

    /**
     * A map that contains items and any objects that they may be used on, and it's
     * respective plugin logic.
     */
    private val itemOnObjectPlugins = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Plugin.() -> Unit>>()

    /**
     * A map that contains all objects that should have their
     * respective plugin logic called when any item is used on them.
     */
    private val anyItemOnObjectPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains item on item plugins.
     *
     * Key: (itemId1 << 16) | itemId2
     * Value: plugin
     */
    private val itemOnItemPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains item on ground item plugins.
     *
     * Key: (invItem << 16) | groundItem
     * Value: plugin
     */
    private val itemOnGroundItemPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains magic spell on item plugins.
     *
     * Key: (fromComponentHash << 32) | toComponentHash
     * Value: plugin
     */
    private val spellOnItemPlugins = Long2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains magic spell on item plugins.
     *
     * Key: (fromComponentHash << 32) | toComponentHash
     * Value: plugin
     */
    private val spellOnGroundItemPlugins = Long2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins that will handle spells on players depending on the interface
     * hash of the spell.
     */
    private val spellOnPlayerPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains npcs and any associated menu-click and its respective
     * plugin logic, if any (would not be in the map if it doesn't have a plugin).
     */
    private val npcPlugins = Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Plugin.() -> Unit>>()

    /**
     * A map of plugins for item on npc logic.
     *
     * Key: (item << 16) | npcId
     * Value: plugin
     */
    private val itemOnNpcPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins for any item on npc logic.
     *
     * Key: npcId
     * Value: plugin
     */
    private val anyItemOnNpcPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins for item on player logic.
     *
     * Key: (item << 16)
     * Value: plugin
     */
    private val itemOnPlayerPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map that contains npc ids as the key and their interaction distance as
     * the value. If map does not contain an npc, it will have the default interaction
     */
    private val npcInteractionDistancePlugins = Int2IntOpenHashMap()

    /**
     * A map that contains object ids as the key and their interaction distance as
     * the value. If map does not contain an object, it will have the default interaction
     */
    private val objInteractionDistancePlugins = Int2IntOpenHashMap()

    /**
     * A list of plugins that will be invoked when a ground item is picked up
     * by a player.
     */
    private val globalGroundItemPickUp = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when a player hits 0 hp.
     */
    private val playerPreDeathPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when a player dies and is teleported
     * back to the respawn location (after death animation played out).
     */
    private val playerDeathPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A map of plugins that are invoked when a player interaction option is executed
     */
    private val playerOptionPlugins = hashMapOf<String, Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when an npc hits 0 hp.
     */
    private val npcPreDeathPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when an npc dies
     * and is de-registered from the world.
     */
    private val npcDeathPlugins = Int2ObjectOpenHashMap<Plugin.() -> Unit>()

    /**
     * A map of plugins that occur when an [Event] is triggered.
     */
    private val eventPlugins = Object2ObjectOpenHashMap<Class<out Event>, MutableList<Plugin.(Event) -> Unit>>()

    /**
     * A list of plugins that will be invoked when a player adds another player to
     * their friend list
     */
    private val addFriendPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when a player deletes another player from
     * their friend list
     */
    private val deleteFriendPlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when a player adds another player to
     * their ignore list
     */
    private val addIgnorePlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * A list of plugins that will be invoked when a player deletes another player from
     * their ignore list
     */
    private val deleteIgnorePlugins = mutableListOf<Plugin.() -> Unit>()

    /**
     * The int value is calculated via [gg.rsmod.game.model.region.ChunkCoords.hashCode].
     */
    internal val multiCombatChunks = IntOpenHashSet()

    /**
     * The int value is calculated via [gg.rsmod.game.model.Tile.regionId].
     */
    internal val multiCombatRegions = IntOpenHashSet()

    /** Marks [region] as multi-combat at runtime (used for dynamically allocated instances). */
    fun addMultiCombatRegion(region: Int) {
        multiCombatRegions.add(region)
    }

    /**
     * Temporarily holds all npc spawns set from plugins for this [PluginRepository].
     * This is then passed onto the [World] and is cleared.
     */
    internal val npcSpawns = mutableListOf<Npc>()

    /**
     * Temporarily holds all object spawns set from plugins for this [PluginRepository].
     * This is then passed onto the [World] and is cleared.
     */
    internal val objSpawns = mutableListOf<DynamicObject>()

    /**
     * Temporarily holds all ground item spawns set from plugins for this
     * [PluginRepository].
     * This is then passed onto the [World] and is cleared.
     */
    internal val itemSpawns = mutableListOf<GroundItem>()

    /**
     * A map of [NpcCombatDef]s that have been set by [KotlinPlugin]s.
     */
    internal val npcCombatDefs = Int2ObjectOpenHashMap<NpcCombatDef>()

    /**
     * Read-only view of every registered [NpcCombatDef], keyed by npc id. Exists so plugin-side
     * audits/diagnostics can inspect combat data without being able to mutate the live map.
     */
    fun allNpcCombatDefs(): Map<Int, NpcCombatDef> = npcCombatDefs

    /**
     * Data-sourced combat definitions that only take effect for npc ids no hand-written
     * definition claims. See [applyFallbackNpcCombatDefs].
     */
    internal val fallbackNpcCombatDefs = Int2ObjectOpenHashMap<NpcCombatDef>()

    fun bindNpcCombatDefFallback(
        npc: Int,
        def: NpcCombatDef,
    ) {
        check(!fallbackNpcCombatDefs.containsKey(npc)) { "Fallback npc combat definition has been previously set: $npc" }
        fallbackNpcCombatDefs[npc] = def
    }

    /**
     * Holds all valid shops set from plugins for this [PluginRepository].
     */
    internal val shops = Object2ObjectOpenHashMap<String, Shop>()

    /**
     * A list of [Service]s that have been requested for loading by a [KotlinPlugin].
     */
    internal val services = mutableListOf<Service>()

    /**
     * A list of [SimplePolygonArea]s that have been set for plugins.
     */
    internal val simplePolygonAreas = mutableListOf<SimplePolygonArea>()

    /**
     * Holds all container keys set from plugins for this [PluginRepository].
     */
    val containerKeys =
        ObjectOpenHashSet<ContainerKey>().apply {
            add(INVENTORY_KEY)
            add(EQUIPMENT_KEY)
            add(BANK_KEY)
            add(RANDOM_EVENT_GIFT_KEY)
            add(DEATH_RECOVERY_KEY)
        }

    /**
     * Initiates and populates all our plugins.
     */
    fun init(
        server: Server,
        world: World,
        jarPluginsDirectory: String,
    ) {
        loadPlugins(server, jarPluginsDirectory)
        applyFallbackNpcCombatDefs()
        loadServices(server, world)
        spawnEntities()
    }

    /**
     * Merge every [fallbackNpcCombatDefs] entry whose npc id has no hand-written
     * [KotlinPlugin.set_combat_def] into [npcCombatDefs]. Runs after *all* plugin scripts have
     * loaded (script discovery order is unspecified, so a bulk data table cannot know at its own
     * load time which ids a hand-written definition will still claim) and before [spawnEntities],
     * because [World.spawn] copies the combat def onto each npc at spawn time.
     */
    private fun applyFallbackNpcCombatDefs() {
        if (fallbackNpcCombatDefs.isEmpty()) {
            return
        }
        var applied = 0
        var overridden = 0
        fallbackNpcCombatDefs.forEach { (npc, def) ->
            if (npcCombatDefs.containsKey(npc)) {
                overridden++
            } else {
                npcCombatDefs[npc] = def
                applied++
            }
        }
        logger.info(
            "Npc combat defs: applied {} bulk fallback definitions ({} ids kept their hand-written definition).",
            applied,
            overridden,
        )
        fallbackNpcCombatDefs.clear()
    }

    /**
     * Locate and load all [KotlinPlugin]s.
     */
    private fun loadPlugins(
        server: Server,
        jarPluginsDirectory: String,
    ) {
        scanPackageForPlugins(server, world)
        scanJarDirectoryForPlugins(server, world, Paths.get(jarPluginsDirectory))
    }

    /**
     * Scan our local package to find any and all [KotlinPlugin]s.
     */
    private fun scanPackageForPlugins(
        server: Server,
        world: World,
    ) {
        ClassGraph().enableAllInfo().whitelistModules().scan().use { result ->
            val plugins = result.getSubclasses(KotlinPlugin::class.java.name).directOnly()
            plugins.forEach { p ->
                val pluginClass = p.loadClass(KotlinPlugin::class.java)
                val constructor =
                    pluginClass.getConstructor(
                        PluginRepository::class.java,
                        World::class.java,
                        Server::class.java,
                    )
                constructor.newInstance(this, world, server)
            }
        }
    }

    /**
     * Scan directory for any JAR file which may contain plugins.
     */
    private fun scanJarDirectoryForPlugins(
        server: Server,
        world: World,
        directory: Path,
    ) {
        if (Files.exists(directory)) {
            Files.walk(directory).forEach { path ->
                if (!path.fileName.toString().endsWith(".jar")) {
                    return@forEach
                }
                scanJarForPlugins(server, world, path)
            }
        }
    }

    /**
     * Scan JAR located in [path] for any and all valid [KotlinPlugin]s and
     * initialise them.
     */
    private fun scanJarForPlugins(
        server: Server,
        world: World,
        path: Path,
    ) {
        val urls = arrayOf(path.toFile().toURI().toURL())
        val classLoader = URLClassLoader(urls, PluginRepository::class.java.classLoader)

        ClassGraph().ignoreParentClassLoaders().addClassLoader(classLoader).enableAllInfo().scan().use { result ->
            val plugins = result.getSubclasses(KotlinPlugin::class.java.name).directOnly()
            plugins.forEach { p ->
                val pluginClass = p.loadClass(KotlinPlugin::class.java)
                val constructor =
                    pluginClass.getConstructor(
                        PluginRepository::class.java,
                        World::class.java,
                        Server::class.java,
                    )
                constructor.newInstance(this, world, server)
            }
        }
    }

    /**
     * Load and initialise [Service]s given to us by [KotlinPlugin]s.
     */
    private fun loadServices(
        server: Server,
        world: World,
    ) {
        services.forEach { service ->
            service.init(server, world, ServerProperties())
            world.services.add(service)
        }

        services.forEach { service ->
            service.postLoad(server, world)
        }
    }

    /**
     * Spawn any and all [gg.rsmod.game.model.entity.Entity]s given to us by
     * [KotlinPlugin]s.
     */
    private fun spawnEntities() {
        npcSpawns.forEach { npc -> world.spawn(npc) }
        objSpawns.forEach { obj -> world.spawn(obj) }
        itemSpawns.forEach { item -> world.spawn(item) }
    }

    /**
     * Gracefully terminate this repository.
     */
    fun terminate() {
        npcSpawns.forEach { npc ->
            if (npc.isSpawned()) {
                world.remove(npc)
            }
        }

        objSpawns.forEach { obj ->
            if (obj.isSpawned(world)) {
                world.remove(obj)
            }
        }

        itemSpawns.forEach { item ->
            if (item.isSpawned(world)) {
                world.remove(item)
            }
        }

        world.services.removeAll(services)
    }

    /**
     * Get the total amount of plugins loaded from the plugins path.
     */
    fun getPluginCount(): Int = pluginCount

    fun getNpcInteractionDistance(npc: Int): Int? = npcInteractionDistancePlugins.getOrDefault(npc, null)

    fun getObjInteractionDistance(obj: Int): Int? = objInteractionDistancePlugins.getOrDefault(obj, null)

    fun bindWorldInit(plugin: Plugin.() -> Unit) {
        worldInitPlugins.add(plugin)
    }

    fun bindLateWorldInit(plugin: Plugin.() -> Unit) {
        lateWorldInitPlugins.add(plugin)
    }

    fun executeWorldInit(world: World) {
        worldInitPlugins.forEach { logic -> world.executePlugin(world, logic) }
        lateWorldInitPlugins.forEach { logic -> world.executePlugin(world, logic) }
    }

    fun bindSlayerLogic(plugin: Plugin.() -> Unit) {
        if (slayerLogic != null) {
            if (rejectDuplicateBinding("Slayer logic is already bound")) return
        }
        slayerLogic = plugin
    }

    fun executeSlayerLogic(pawn: Pawn) {
        if (slayerLogic != null) {
            pawn.executePlugin(slayerLogic!!)
        }
    }

    fun bindCombat(plugin: Plugin.() -> Unit) {
        if (combatPlugin != null) {
            if (rejectDuplicateBinding("Combat plugin is already bound")) return
        }
        combatPlugin = plugin
    }

    fun executeCombat(pawn: Pawn) {
        if (combatPlugin != null) {
            pawn.executePlugin(combatPlugin!!)
        }
    }

    fun bindNpcCombat(
        npc: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (npcCombatPlugins.containsKey(npc)) {
            if (rejectDuplicateBinding("Npc is already bound to a combat plugin: $npc")) return
        }
        npcCombatPlugins[npc] = plugin
        pluginCount++
    }

    fun hasNpcCombatPlugin(npc: Int): Boolean = npcCombatPlugins.containsKey(npc)

    fun executeNpcCombat(n: Npc): Boolean {
        val plugin = npcCombatPlugins[n.id] ?: return false
        n.executePlugin(plugin)
        return true
    }

    /**
     * Audit finding 13 (R14.26): a beginner-protected player must not participate in a
     * Breach (or any other designated lucrative Wilderness activity) at all - not just be
     * excluded from the reward roll. [Pawn.attack] is the single choke point every combat
     * initiator (a player's Attack click, an aggressive npc's own AI, spell/ranged
     * auto-attack) routes through, so gating there blocks it in both directions with one
     * check. A plain predicate list rather than a [Plugin]-wrapped, id-keyed map like
     * [npcCombatPlugins] since this has to see both pawns involved, not one id, and the
     * check itself needs no messaging/coroutine context - only [BeginnerProtection] and
     * [WildernessBreach] (both content-layer) know what "protected"/"Breach npc" mean.
     */
    private val canAttackPlugins = mutableListOf<(attacker: Pawn, target: Pawn) -> Boolean>()

    fun bindCanAttack(plugin: (attacker: Pawn, target: Pawn) -> Boolean) {
        canAttackPlugins.add(plugin)
        pluginCount++
    }

    fun canAttack(
        attacker: Pawn,
        target: Pawn,
    ): Boolean = canAttackPlugins.all { it(attacker, target) }

    fun bindPlayerPreDeath(plugin: Plugin.() -> Unit) {
        playerPreDeathPlugins.add(plugin)
    }

    fun executePlayerPreDeath(p: Player) {
        playerPreDeathPlugins.forEach { plugin -> p.executePlugin(plugin) }
    }

    fun bindPlayerOption(
        option: String,
        plugin: Plugin.() -> Unit,
    ) {
        playerOptionPlugins[option] = plugin
    }

    fun executePlayerOption(
        player: Player,
        option: String,
    ): Boolean {
        val logic = playerOptionPlugins[option] ?: return false
        player.executePlugin(logic)
        return true
    }

    fun bindPlayerDeath(plugin: Plugin.() -> Unit) {
        playerDeathPlugins.add(plugin)
    }

    fun executePlayerDeath(p: Player) {
        playerDeathPlugins.forEach { plugin -> p.executePlugin(plugin) }
    }

    fun bindNpcPreDeath(
        npc: Int,
        plugin: Plugin.() -> Unit,
    ) {
        npcPreDeathPlugins[npc] = plugin
    }

    fun executeNpcPreDeath(npc: Npc) {
        npcPreDeathPlugins[npc.id]?.let { plugin ->
            npc.executePlugin(plugin)
        }
    }

    fun bindNpcDeath(
        npc: Int,
        plugin: Plugin.() -> Unit,
    ) {
        npcDeathPlugins[npc] = plugin
    }

    fun executeNpcDeath(npc: Npc) {
        npcDeathPlugins[npc.id]?.let { plugin ->
            npc.executePlugin(plugin)
        }
    }

    /**
     * A list of listeners invoked whenever ANY npc is killed by a player, regardless of npc id.
     * Unlike [npcDeathPlugins] (one bound plugin per npc id, silently overwritten on collision),
     * this is a plain list - any number of independent features (e.g. daily objectives) can
     * subscribe without risk of clobbering an npc-specific `on_npc_death` handler bound
     * elsewhere.
     */
    private val npcKilledListeners = ObjectArrayList<(Player, Npc) -> Unit>()

    fun bindNpcKilled(listener: (Player, Npc) -> Unit) {
        npcKilledListeners.add(listener)
        pluginCount++
    }

    fun executeNpcKilled(
        killer: Player,
        npc: Npc,
    ) {
        npcKilledListeners.forEach { it(killer, npc) }
    }

    fun bindSpellOnPlayer(
        parent: Int,
        child: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (parent shl 16) or child
        if (spellOnPlayerPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Spell is already bound to a plugin: [$parent, $child]")) return
        }
        spellOnPlayerPlugins[hash] = plugin
        pluginCount++
    }

    fun executeSpellOnPlayer(
        p: Player,
        parent: Int,
        child: Int,
    ): Boolean {
        val hash = (parent shl 16) or child
        val plugin = spellOnPlayerPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindSpellOnNpc(
        parent: Int,
        child: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (parent shl 16) or child
        if (spellOnNpcPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Spell is already bound to a plugin: [$parent, $child]")) return
        }
        spellOnNpcPlugins[hash] = plugin
        pluginCount++
    }

    fun executeSpellOnNpc(
        p: Player,
        parent: Int,
        child: Int,
    ): Boolean {
        val hash = (parent shl 16) or child
        val plugin = spellOnNpcPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindWindowStatus(plugin: Plugin.() -> Unit) {
        if (windowStatusPlugin != null) {
            if (rejectDuplicateBinding("Window status is already bound to a plugin")) return
        }
        windowStatusPlugin = plugin
    }

    fun executeWindowStatus(p: Player) {
        if (windowStatusPlugin != null) {
            p.executePlugin(windowStatusPlugin!!)
        } else {
            logger.warn { "Window status is not bound to a plugin." }
        }
    }

    fun bindModalClose(plugin: Plugin.() -> Unit) {
        if (closeModalPlugin != null) {
            if (rejectDuplicateBinding("Modal close is already bound to a plugin")) return
        }
        closeModalPlugin = plugin
    }

    fun executeModalClose(p: Player) {
        if (closeModalPlugin != null) {
            p.executePlugin(closeModalPlugin!!)
        } else {
            logger.warn { "Modal close is not bound to a plugin." }
        }
    }

    fun setMenuOpenedCheck(plugin: Plugin.() -> Boolean) {
        if (isMenuOpenedPlugin != null) {
            if (rejectDuplicateBinding("\"Menu Opened\" is already bound to a plugin")) return
        }
        isMenuOpenedPlugin = plugin
    }

    fun isMenuOpened(p: Player): Boolean =
        if (isMenuOpenedPlugin !=
            null
        ) {
            p.executePlugin(isMenuOpenedPlugin!!)
        } else {
            false
        }

    fun <T : Event> bindEvent(
        event: Class<T>,
        plugin: Plugin.(Event) -> Unit,
    ) {
        val plugins = eventPlugins[event]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            val newList = ObjectArrayList<Plugin.(Event) -> Unit>(1)
            newList.add(plugin)
            eventPlugins[event] = newList
        }

        pluginCount++
    }

    fun <T : Event> executeEvent(
        p: Pawn,
        event: T,
    ) {
        eventPlugins[event::class.java]?.forEach { plugin ->
            p.executePlugin {
                plugin.invoke(this, event)
            }
        }
    }

    fun bindLogin(plugin: Plugin.() -> Unit) {
        loginPlugins.add(plugin)
        pluginCount++
    }

    fun executeLogin(p: Player) {
        loginPlugins.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindLogout(plugin: Plugin.() -> Unit) {
        logoutPlugins.add(plugin)
        pluginCount++
    }

    fun executeLogout(p: Player) {
        logoutPlugins.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindComponentItemSwap(
        interfaceId: Int,
        component: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (interfaceId shl 16) or component
        componentItemSwapPlugins[hash] = plugin
    }

    fun executeComponentItemSwap(
        p: Player,
        interfaceId: Int,
        component: Int,
    ): Boolean {
        val hash = (interfaceId shl 16) or component
        val plugin = componentItemSwapPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindComponentToComponentItemSwap(
        srcInterfaceId: Int,
        srcComponent: Int,
        dstInterfaceId: Int,
        dstComponent: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val srcHash = (srcInterfaceId shl 16) or srcComponent
        val dstHash = (dstInterfaceId shl 16) or dstComponent
        val combinedHash = ((srcHash shl 32) or dstHash).toLong()
        componentToComponentItemSwapPlugins[combinedHash] = plugin
    }

    fun executeComponentToComponentItemSwap(
        p: Player,
        srcInterfaceId: Int,
        srcComponent: Int,
        dstInterfaceId: Int,
        dstComponent: Int,
    ): Boolean {
        val srcHash = (srcInterfaceId shl 16) or srcComponent
        val dstHash = (dstInterfaceId shl 16) or dstComponent
        val combinedHash = ((srcHash shl 32) or dstHash).toLong()
        val plugin = componentToComponentItemSwapPlugins[combinedHash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindGlobalNpcSpawn(plugin: Plugin.() -> Unit) {
        globalNpcSpawnPlugins.add(plugin)
        pluginCount++
    }

    fun bindNpcSpawn(
        npc: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = npcSpawnPlugins[npc]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            npcSpawnPlugins[npc] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun executeNpcSpawn(n: Npc) {
        val customPlugins = npcSpawnPlugins[n.id]
        if (customPlugins != null && customPlugins.isNotEmpty()) {
            customPlugins.forEach { logic -> n.executePlugin(logic) }
        }
        globalNpcSpawnPlugins.forEach { logic -> n.executePlugin(logic) }
    }

    fun bindTimer(
        key: TimerKey,
        plugin: Plugin.() -> Unit,
    ) {
        if (timerPlugins.containsKey(key)) {
            if (rejectDuplicateBinding("Timer key is already bound to a plugin: $key")) return
        }
        timerPlugins[key] = plugin
        pluginCount++
    }

    fun executeTimer(
        pawn: Pawn,
        key: TimerKey,
    ): Boolean {
        val plugin = timerPlugins[key]
        if (plugin != null) {
            pawn.executePlugin(plugin)
            return true
        }
        return false
    }

    fun executeWorldTimer(
        world: World,
        key: TimerKey,
    ): Boolean {
        val plugin = timerPlugins[key]
        if (plugin != null) {
            world.executePlugin(world, plugin)
            return true
        }
        return false
    }

    fun bindInterfaceOpen(
        interfaceId: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (interfaceOpenPlugins.containsKey(interfaceId)) {
            if (rejectDuplicateBinding("Component id is already bound to a plugin: $interfaceId")) return
        }
        interfaceOpenPlugins[interfaceId] = plugin
        pluginCount++
    }

    fun executeInterfaceOpen(
        p: Player,
        interfaceId: Int,
    ): Boolean {
        val plugin = interfaceOpenPlugins[interfaceId]
        if (plugin != null) {
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindInterfaceClose(
        interfaceId: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (interfaceClosePlugins.containsKey(interfaceId)) {
            if (rejectDuplicateBinding("Component id is already bound to a plugin: $interfaceId")) return
        }
        interfaceClosePlugins[interfaceId] = plugin
        pluginCount++
    }

    fun executeInterfaceClose(
        p: Player,
        interfaceId: Int,
    ): Boolean {
        val plugin = interfaceClosePlugins[interfaceId]
        if (plugin != null) {
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindCommand(
        command: String,
        powerRequired: String? = null,
        plugin: Plugin.() -> Unit,
    ) {
        val cmd = command.lowercase()
        if (commandPlugins.containsKey(cmd)) {
            if (rejectDuplicateBinding("Command is already bound to a plugin: $cmd")) return
        }
        commandPlugins[cmd] = Pair(powerRequired, plugin)
        pluginCount++
    }

    fun executeCommand(
        p: Player,
        command: String,
        args: Array<String>? = null,
    ): Boolean {
        val commandPair = commandPlugins[command]
        if (commandPair != null) {
            val powerRequired = commandPair.first
            val plugin = commandPair.second

            if (powerRequired != null && !p.privilege.powers.contains(powerRequired.lowercase())) {
                return false
            }

            p.attr.put(COMMAND_ATTR, command)
            if (args != null) {
                p.attr.put(COMMAND_ARGS_ATTR, args)
            } else {
                p.attr.put(COMMAND_ARGS_ATTR, emptyArray())
            }
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindButton(
        parent: Int,
        child: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (parent shl 16) or child
        if (buttonPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Button hash already bound to a plugin: [parent=$parent, child=$child]")) return
        }
        buttonPlugins[hash] = plugin
        pluginCount++
    }

    fun executeButton(
        p: Player,
        parent: Int,
        child: Int,
    ): Boolean {
        val hash = (parent shl 16) or child
        val plugin = buttonPlugins[hash]
        if (plugin != null) {
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindEquipmentOption(
        item: Int,
        option: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (item shl 16) or option
        if (equipmentOptionPlugins.containsKey(hash)) {
            logger.error(RuntimeException("Button hash already bound to a plugin: [item=$item, opt=$option]")) {}
            return
        }
        equipmentOptionPlugins[hash] = plugin
        pluginCount++
    }

    fun executeEquipmentOption(
        p: Player,
        item: Int,
        option: Int,
    ): Boolean {
        val hash = (item shl 16) or option
        val plugin = equipmentOptionPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindEquipSlot(
        equipSlot: Int,
        plugin: Plugin.() -> Unit,
    ) {
        equipSlotPlugins.put(equipSlot, plugin)
        pluginCount++
    }

    fun executeEquipSlot(
        p: Player,
        equipSlot: Int,
    ): Boolean {
        val plugin = equipSlotPlugins[equipSlot]
        if (plugin != null) {
            plugin.forEach { logic -> p.executePlugin(logic) }
            return true
        }
        return false
    }

    fun bindUnequipSlot(
        equipSlot: Int,
        plugin: Plugin.() -> Unit,
    ) {
        unequipSlotPlugins.put(equipSlot, plugin)
        pluginCount++
    }

    fun bindCanUnequipSlot(
        equipSlot: Int,
        plugin: Plugin.() -> Boolean,
    ) {
        canUnequipSlotPlugins.put(equipSlot, plugin)
        pluginCount++
    }

    /**
     * Returns false if any plugin bound to [equipSlot] refuses the un-equip/replace - checked
     * by [EquipAction] before an item currently in that slot is moved to inventory, whether via
     * the explicit Remove action or an equip that swaps it out. True (allowed) if none block it.
     */
    fun canUnequipSlot(
        p: Player,
        equipSlot: Int,
    ): Boolean {
        val plugins = canUnequipSlotPlugins[equipSlot]
        return plugins.all { logic -> p.executePlugin(logic) }
    }

    fun executeUnequipSlot(
        p: Player,
        equipSlot: Int,
    ): Boolean {
        val plugin = unequipSlotPlugins[equipSlot]
        if (plugin != null) {
            plugin.forEach { logic -> p.executePlugin(logic) }
            return true
        }
        return false
    }

    fun bindEquipItemRequirement(
        item: Int,
        plugin: Plugin.() -> Boolean,
    ) {
        if (equipItemRequirementPlugins.containsKey(item)) {
            if (rejectDuplicateBinding("Equip item requirement already bound to a plugin: [item=$item]")) return
        }
        equipItemRequirementPlugins[item] = plugin
        pluginCount++
    }

    fun executeEquipItemRequirement(
        p: Player,
        item: Int,
    ): Boolean {
        val plugin = equipItemRequirementPlugins[item]
        if (plugin != null) {
            /*
             * Plugin returns true if the item can be equipped, false if it
             * should block the item from being equipped.
             */
            return p.executePlugin(plugin)
        }
        /*
         * Should always be able to wear items by default.
         */
        return true
    }

    fun bindEquipItem(
        item: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (equipItemPlugins.containsKey(item)) {
            if (rejectDuplicateBinding("Equip item already bound to a plugin: [item=$item]")) return
        }
        equipItemPlugins[item] = plugin
        pluginCount++
    }

    fun executeEquipItem(
        p: Player,
        item: Int,
    ): Boolean {
        val plugin = equipItemPlugins[item]
        if (plugin != null) {
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindUnequipItem(
        item: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (unequipItemPlugins.containsKey(item)) {
            if (rejectDuplicateBinding("Unequip item already bound to a plugin: [item=$item]")) return
        }
        unequipItemPlugins[item] = plugin
        pluginCount++
    }

    fun executeUnequipItem(
        p: Player,
        item: Int,
    ): Boolean {
        val plugin = unequipItemPlugins[item]
        if (plugin != null) {
            p.executePlugin(plugin)
            return true
        }
        return false
    }

    fun bindSkillLevelUp(plugin: Plugin.() -> Unit) {
        skillLevelUps.add(plugin)
    }

    fun bindSkillExperienceUp(plugin: Plugin.() -> Unit) {
        skillExperienceUps.add(plugin)
    }

    fun executeSkillLevelUp(p: Player) {
        skillLevelUps.forEach { p.executePlugin(it) }
    }

    fun executeSkillExperienceUp(p: Player) {
        skillExperienceUps.forEach { p.executePlugin(it) }
    }

    fun bindRegionEnter(
        regionId: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = enterRegionPlugins[regionId]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            enterRegionPlugins[regionId] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun executeRegionEnter(
        p: Player,
        regionId: Int,
    ) {
        enterRegionPlugins[regionId]?.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindRegionExit(
        regionId: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = exitRegionPlugins[regionId]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            exitRegionPlugins[regionId] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun executeRegionExit(
        p: Player,
        regionId: Int,
    ) {
        exitRegionPlugins[regionId]?.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindChunkEnter(
        chunkHash: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = enterChunkPlugins[chunkHash]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            enterChunkPlugins[chunkHash] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun bindSimplePolygonAreaEnter(
        area: SimplePolygonArea,
        plugin: Plugin.() -> Unit,
    ) {
        val areaHash = area.hashCode()
        val plugins = enterSimplePolygonAreaPlugins[areaHash]
        simplePolygonAreas.add(area)
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            enterSimplePolygonAreaPlugins[areaHash] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun executeSimplePolygonAreaEnter(
        p: Player,
        areaHash: Int,
    ) {
        enterSimplePolygonAreaPlugins[areaHash]?.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindSoundSongEnd(plugin: Plugin.() -> Unit) {
        songEndPlugins.add(plugin)
    }

    fun executeSoundSongEnd(p: Player) {
        songEndPlugins.forEach { logic -> p.executePlugin(logic) }
    }

    fun executeChunkEnter(
        p: Player,
        chunkHash: Int,
    ) {
        enterChunkPlugins[chunkHash]?.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindChunkExit(
        chunkHash: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = exitChunkPlugins[chunkHash]
        if (plugins != null) {
            plugins.add(plugin)
        } else {
            exitChunkPlugins[chunkHash] = arrayListOf(plugin)
        }
        pluginCount++
    }

    fun executeChunkExit(
        p: Player,
        chunkHash: Int,
    ) {
        exitChunkPlugins[chunkHash]?.forEach { logic -> p.executePlugin(logic) }
    }

    fun bindItem(
        id: Int,
        opt: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val optMap = itemPlugins[id] ?: Int2ObjectOpenHashMap(1)
        if (optMap.containsKey(opt)) {
            if (rejectDuplicateBinding("Item is already bound to a plugin: $id [opt=$opt]")) return
        }
        optMap[opt] = plugin
        itemPlugins[id] = optMap
        pluginCount++
    }

    fun executeItem(
        p: Player,
        id: Int,
        opt: Int,
    ): Boolean {
        val optMap = itemPlugins[id] ?: return false
        val logic = optMap[opt] ?: return false
        p.executePlugin(logic)
        return true
    }

    fun bindGroundItem(
        id: Int,
        opt: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val optMap = groundItemPlugins[id] ?: Int2ObjectOpenHashMap(1)
        if (optMap.containsKey(opt)) {
            if (rejectDuplicateBinding("Ground item is already bound to a plugin: $id [opt=$opt]")) return
        }
        optMap[opt] = plugin
        groundItemPlugins[id] = optMap
        pluginCount++
    }

    fun executeGroundItem(
        p: Player,
        id: Int,
        opt: Int,
    ): Boolean {
        val optMap = groundItemPlugins[id] ?: return false
        val logic = optMap[opt] ?: return false
        p.executePlugin(logic)
        return true
    }

    fun setGroundItemPickupCondition(
        item: Int,
        plugin: Plugin.() -> Boolean,
    ) {
        if (groundItemPickupConditions.containsKey(item)) {
            if (rejectDuplicateBinding("Ground item pick-up condition already set: $item")) return
        }
        groundItemPickupConditions[item] = plugin
        pluginCount++
    }

    fun canPickupGroundItem(
        p: Player,
        item: Int,
    ): Boolean {
        val plugin = groundItemPickupConditions[item] ?: return true
        return p.executePlugin(plugin)
    }

    fun bindCanItemDrop(
        item: Int,
        plugin: Plugin.() -> Boolean,
    ) {
        if (canDropItemPlugins.containsKey(item)) {
            if (rejectDuplicateBinding("Item already bound to a 'can-drop' plugin: $item")) return
        }
        canDropItemPlugins[item] = plugin
    }

    fun canDropItem(
        p: Player,
        item: Int,
    ): Boolean {
        val plugin = canDropItemPlugins[item]
        if (plugin != null) {
            return p.executePlugin(plugin)
        }
        return true
    }

    fun bindItemOnObject(
        obj: Int,
        item: Int,
        lineOfSightDistance: Int = -1,
        plugin: Plugin.() -> Unit,
    ) {
        val plugins = itemOnObjectPlugins[item] ?: Int2ObjectOpenHashMap(1)
        if (plugins.containsKey(obj)) {
            if (rejectDuplicateBinding("Item is already bound to an object plugin: $item [obj=$obj]")) return
        }

        if (lineOfSightDistance != -1) {
            objInteractionDistancePlugins[obj] = lineOfSightDistance
        }

        plugins[obj] = plugin
        itemOnObjectPlugins[item] = plugins
        pluginCount++
    }

    fun bindAnyItemOnObject(
        obj: Int,
        lineOfSightDistance: Int = -1,
        plugin: Plugin.() -> Unit,
    ) {
        if (anyItemOnObjectPlugins.containsKey(obj)) {
            if (rejectDuplicateBinding("Object is already bound to a plugin: [obj=$obj]")) return
        }

        if (lineOfSightDistance != -1) {
            objInteractionDistancePlugins[obj] = lineOfSightDistance
        }

        anyItemOnObjectPlugins[obj] = plugin
        pluginCount++
    }

    fun executeItemOnObject(
        p: Player,
        obj: Int,
        item: Int,
    ): Boolean {
        val logic = itemOnObjectPlugins[item]?.get(obj) ?: anyItemOnObjectPlugins[obj] ?: return false
        p.executePlugin(logic)
        return true
    }

    fun bindItemOnItem(
        item1: Int,
        item2: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val max = Math.max(item1, item2)
        val min = Math.min(item1, item2)

        val hash = (max shl 16) or min

        if (itemOnItemPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Item on Item pair is already bound to a plugin: [item1=$item1, item2=$item2]")) return
        }

        itemOnItemPlugins[hash] = plugin
        pluginCount++
    }

    fun executeItemOnItem(
        p: Player,
        item1: Int,
        item2: Int,
    ): Boolean {
        val max = Math.max(item1, item2)
        val min = Math.min(item1, item2)

        val hash = (max shl 16) or min
        val plugin = itemOnItemPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindItemOnGroundItem(
        invItem: Int,
        groundItem: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (invItem shl 16) or groundItem
        if (itemOnGroundItemPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Item on Item pair is already bound to a plugin: [inv_item=$invItem, ground_item=$groundItem]")) return
        }
        itemOnGroundItemPlugins[hash] = plugin
        pluginCount++
    }

    fun executeItemOnGroundItem(
        p: Player,
        invItem: Int,
        groundItem: Int,
    ): Boolean {
        val hash = (invItem shl 16) or groundItem
        val plugin = itemOnGroundItemPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindSpellOnItem(
        fromComponentHash: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash: Long = (fromComponentHash.toLong() shl 32)
        if (spellOnItemPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Spell on item already bound to a plugin: from=[${fromComponentHash shr 16}, ${fromComponentHash or 0xFFFF}]")) return
        }
        spellOnItemPlugins[hash] = plugin
        pluginCount++
    }

    fun bindSpellOnGroundItem(
        fromComponentHash: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash: Long = (fromComponentHash.toLong() shl 32)
        if (spellOnGroundItemPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Spell on ground item already bound to a plugin: from=[${fromComponentHash shr 16}, ${fromComponentHash or 0xFFFF}]")) return
        }
        spellOnGroundItemPlugins[hash] = plugin
        pluginCount++
    }

    fun executeSpellOnItem(
        p: Player,
        fromComponentHash: Int,
    ): Boolean {
        val hash: Long = (fromComponentHash.toLong() shl 32)
        val plugin = spellOnItemPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun executeSpellOnGroundItem(
        p: Player,
        fromComponentHash: Int,
    ): Boolean {
        val hash: Long = (fromComponentHash.toLong() shl 32)
        val plugin = spellOnGroundItemPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindObject(
        obj: Int,
        opt: Int,
        lineOfSightDistance: Int = -1,
        plugin: Plugin.() -> Unit,
    ) {
        val optMap = objectPlugins[obj] ?: Int2ObjectOpenHashMap(1)
        if (optMap.containsKey(opt)) {
            if (rejectDuplicateBinding("Object is already bound to a plugin: $obj [opt=$opt]")) return
        }

        if (lineOfSightDistance != -1) {
            objInteractionDistancePlugins[obj] = lineOfSightDistance
        }

        optMap[opt] = plugin
        objectPlugins[obj] = optMap
        pluginCount++
    }

    fun executeObject(
        p: Player,
        id: Int,
        opt: Int,
    ): Boolean {
        val optMap = objectPlugins[id] ?: return false
        val logic = optMap[opt] ?: return false
        p.executePlugin(logic)
        return true
    }

    fun bindNpc(
        npc: Int,
        opt: Int,
        lineOfSightDistance: Int = -1,
        plugin: Plugin.() -> Unit,
    ) {
        val optMap = npcPlugins[npc] ?: Int2ObjectOpenHashMap(1)
        if (optMap.containsKey(opt)) {
            if (rejectDuplicateBinding("Npc is already bound to a plugin: $npc [opt=$opt]")) return
        }

        if (lineOfSightDistance != -1) {
            npcInteractionDistancePlugins[npc] = lineOfSightDistance
        }

        optMap[opt] = plugin
        npcPlugins[npc] = optMap
        pluginCount++
    }

    fun executeNpc(
        p: Player,
        id: Int,
        opt: Int,
    ): Boolean {
        val optMap = npcPlugins[id] ?: return false
        val logic = optMap[opt] ?: return false
        p.executePlugin(logic)
        return true
    }

    /**
     * The 1-based option slots (matching [gg.rsmod.game.fs.def.NpcDef.options] index + 1)
     * that actually have a bound plugin for [npc]. Used by [gg.rsmod.game.model.npc.NpcCensus]
     * to report which advertised options (Talk-to, Trade, ...) are real vs dead menu entries.
     */
    fun boundNpcOptions(npc: Int): Set<Int> = npcPlugins[npc]?.keys ?: emptySet()
    /**
     * The 1-based option slots that have a bound handler for an object definition.
     * This lets object audits distinguish advertised cache options from executable routes.
     */
    fun boundObjectOptions(obj: Int): Set<Int> = objectPlugins[obj]?.keys?.toSet() ?: emptySet()

    /**
     * Whether any item-on-object route is registered for an object, including generic
     * any-item handlers.
     */
    fun hasItemOnObjectHandler(obj: Int): Boolean =
        anyItemOnObjectPlugins.containsKey(obj) || itemOnObjectPlugins.values.any { it.containsKey(obj) }

    fun bindItemOnNpc(
        npc: Int,
        item: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (item shl 16) or npc
        if (itemOnNpcPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Item on npc is already bound to a plugin: npc=$npc, item=$item")) return
        }
        itemOnNpcPlugins[hash] = plugin
        pluginCount++
    }

    fun bindAnyItemOnNpc(
        npc: Int,
        plugin: Plugin.() -> Unit,
    ) {
        if (anyItemOnNpcPlugins.containsKey(npc)) {
            if (rejectDuplicateBinding("Any item on npc is already bound to a plugin: npc=$npc")) return
        }
        anyItemOnNpcPlugins[npc] = plugin
        pluginCount++
    }

    fun bindItemOnPlayer(
        item: Int,
        plugin: Plugin.() -> Unit,
    ) {
        val hash = (item shl 16)
        if (itemOnPlayerPlugins.containsKey(hash)) {
            if (rejectDuplicateBinding("Item on player is already bound to a plugin: item=$item")) return
        }
        itemOnPlayerPlugins[hash] = plugin
        pluginCount++
    }

    fun executeItemOnNpc(
        p: Player,
        npc: Int,
        item: Int,
    ): Boolean {
        val hash = (item shl 16) or npc
        val plugin = itemOnNpcPlugins[hash] ?: anyItemOnNpcPlugins[npc] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun executeItemOnPlayer(
        p: Player,
        item: Int,
    ): Boolean {
        val hash = (item shl 16)
        val plugin = itemOnPlayerPlugins[hash] ?: return false
        p.executePlugin(plugin)
        return true
    }

    fun bindGlobalGroundItemPickUp(plugin: Plugin.() -> Unit) {
        globalGroundItemPickUp.add(plugin)
    }

    fun executeGlobalGroundItemPickUp(p: Player) {
        globalGroundItemPickUp.forEach { plugin ->
            p.executePlugin(plugin)
        }
    }

    fun bindAddFriend(plugin: Plugin.() -> Unit) {
        addFriendPlugins.add(plugin)
    }

    fun executeAddFriend(p: Player) {
        addFriendPlugins.forEach { plugin ->
            p.executePlugin(plugin)
        }
    }

    fun bindDeleteFriend(plugin: Plugin.() -> Unit) {
        deleteFriendPlugins.add(plugin)
    }

    fun executeDeleteFriend(p: Player) {
        deleteFriendPlugins.forEach { plugin ->
            p.executePlugin(plugin)
        }
    }

    fun bindAddIgnore(plugin: Plugin.() -> Unit) {
        addIgnorePlugins.add(plugin)
    }

    fun executeAddIgnore(p: Player) {
        addIgnorePlugins.forEach { plugin ->
            p.executePlugin(plugin)
        }
    }

    fun bindDeleteIgnore(plugin: Plugin.() -> Unit) {
        deleteIgnorePlugins.add(plugin)
    }

    fun executeDeleteIgnore(p: Player) {
        deleteIgnorePlugins.forEach { plugin ->
            p.executePlugin(plugin)
        }
    }

    /**
     * Every bind* collision funnels through here. Normally a duplicate binding is a boot-blocking
     * error (throws). Booting with `-Drsmod.tolerateDuplicateBinds=true` instead logs every
     * conflict - tagged DUPLICATE-BIND, with the plugin script that caused it - and returns true
     * so the caller keeps the FIRST binding and skips the new one. That turns "one crash per
     * restart" into a single audit boot that lists all conflicts at once. Never run production
     * with the property set: the skipped bindings are content bugs that must be fixed in source.
     */
    private fun rejectDuplicateBinding(message: String): Boolean {
        val culprit = Thread.currentThread().stackTrace.firstOrNull { it.className.startsWith("gg.rsmod.plugins.") }
        val detail = if (culprit != null) "$message <- ${culprit.className} (${culprit.fileName}:${culprit.lineNumber})" else message
        if (TOLERATE_DUPLICATE_BINDS) {
            logger.error("DUPLICATE-BIND: $detail")
            return true
        }
        logger.error(detail)
        throw IllegalStateException(detail)
    }

    companion object : KLogging() {
        private val TOLERATE_DUPLICATE_BINDS = System.getProperty("rsmod.tolerateDuplicateBinds") == "true"
    }
}
