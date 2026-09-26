package gg.rsmod.plugins.content.newplayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.tools.importer.AfkBasementMapTool
import gg.rsmod.game.tools.importer.ModernRegionProbeTool
import gg.rsmod.game.tools.importer.Rev667Loc
import gg.rsmod.game.tools.importer.Rev667LocCodec
import gg.rsmod.game.tools.importer.Rev667LocType
import gg.rsmod.game.tools.importer.Rev667TileCodec
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Skilling Basement "Guild Cellar" (owner 2026-09-26): the 15 stations on the real map, training through level-ups,
 * the exact level-55 cap, sessions (one station at a time, no double ticks, no XP after leaving or logging out), the
 * safe zone, the Survival Expert's one-time talks and the hall entrance with its own dungeon icon.
 */
class SkillingBasementTests {
    private class Account(var tile: Tile = AfkArea.BASEMENT_ARRIVAL) {
        val attributes = AttributeMap()
        val skills = SkillSet(25)
        val messages = mutableListOf<String>()
        val xpCalls = mutableListOf<Triple<Double, Boolean, Boolean>>()
        var online = true
        var loggingOut = false
        val player = mockk<Player>(relaxed = true)

        init {
            val world = mockk<World>(relaxed = true)
            every { world.definitions } returns DEFINITIONS
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.skills } returns skills
            every { player.tile } answers { tile }
            every { player.isOnline } answers { online }
            every { player.isLogoutPending } answers { loggingOut }
            every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
            every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
            every { player.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers {
                (firstArg<Array<*>>().firstOrNull() as? gg.rsmod.game.message.impl.MessageGameMessage)?.let { messages += it.message }
            }
            // The engine's addXp with modifiers off adds exactly the amount given (Player.addXp: modifier 1.0, bonus 1.0).
            every { player.addXp(any(), any(), any(), any()) } answers {
                val skill = firstArg<Int>()
                xpCalls += Triple(secondArg(), thirdArg(), arg(3))
                skills.setBaseXp(skill, skills.getCurrentXp(skill) + secondArg<Double>())
            }
        }
    }

    // ------------------------------------------------------------------------------------------ stations

    @Test
    fun `exactly the 15 skills have a station, each a unique loc copy with its own option`() {
        val expected = setOf(
            Skills.WOODCUTTING, Skills.FISHING, Skills.MINING, Skills.FARMING, Skills.HUNTER, Skills.COOKING, Skills.FIREMAKING,
            Skills.SMITHING, Skills.CRAFTING, Skills.FLETCHING, Skills.HERBLORE, Skills.CONSTRUCTION, Skills.RUNECRAFTING,
            Skills.AGILITY, Skills.THIEVING,
        )
        assertEquals(expected, AfkArea.TRAINABLE)
        assertEquals(expected, AfkArea.STATIONS.map { it.skill }.toSet())
        val locStations = AfkArea.STATIONS.filter { !it.npc }
        assertEquals(AfkBasementMapTool.STATIONS.size, locStations.size)
        AfkBasementMapTool.STATIONS.forEachIndexed { index, station ->
            val server = locStations[index]
            assertEquals(AfkBasementMapTool.FIRST_STATION_LOC + index, server.loc)
            assertEquals(SKILL_BY_NAME.getValue(station.skill), server.skill, station.skill)
            assertEquals(station.option, server.option, station.skill)
            val def = DEFINITIONS.get(ObjectDef::class.java, server.loc)
            assertEquals(station.option, def.options[0], "loc ${server.loc} first option")
            def.options.drop(1).forEach { assertTrue(it.isNullOrEmpty() || it == "Hidden", "loc ${server.loc} shows only its station option: ${def.options.toList()}") }
        }
        val spot = DEFINITIONS.get(NpcDef::class.java, AfkArea.FISHING_SPOT)
        assertEquals("Fishing spot", spot.name)
        assertEquals(listOf("Net", "Bait"), AfkArea.STATIONS.filter { it.npc }.map { it.option })
        AfkArea.STATIONS.filter { it.npc }.forEach { assertTrue(it.option in spot.options.filterNotNull(), "${it.option} on ${spot.options.toList()}") }
        AfkArea.STATIONS.forEach { assertTrue(it.animation > 0, "station ${it.loc} ${it.option} has an animation") }
        val talk = DEFINITIONS.get(NpcDef::class.java, AfkArea.SURVIVAL_EXPERT).options.filterNotNull().map { it.lowercase() }
        assertTrue("talk-to" in talk)
    }

    @Test
    fun `the basement map holds every station and all of them are reachable from the stairs`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val maps = library.index(5)
            val tiles = Rev667TileCodec.decode(library.data(5, maps.archive(AfkBasementMapTool.MAP_NAME)!!.id, 0)!!)
            val locs = Rev667LocCodec.decode(library.data(5, maps.archive(AfkBasementMapTool.LOC_NAME)!!.id, 0)!!)
            fun placed(id: Int, lx: Int, lz: Int) = locs.any { it.id == id && it.localX == lx && it.localZ == lz && it.plane == 0 }
            AfkBasementMapTool.STATIONS.forEachIndexed { index, s ->
                val id = AfkBasementMapTool.FIRST_STATION_LOC + index
                assertTrue(placed(id, s.lx, s.lz), "${s.skill} loc $id at ${s.lx},${s.lz}")
                s.more.forEach { (x, z) -> assertTrue(placed(id, x, z), "${s.skill} loc $id at $x,$z") }
            }
            assertTrue(placed(AfkBasementMapTool.STAIRS_UP_LOC, AfkBasementMapTool.STAIRS_LX, AfkBasementMapTool.STAIRS_LZ), "stairs up")
            assertEquals(AfkArea.BASEMENT_STAIRS.x - AfkBasementMapTool.BASE_X, AfkBasementMapTool.STAIRS_LX)

            // Walkable room tiles: floor not blocked, not under a blocking loc, not the Survival Expert's tile.
            val blocked = HashSet<Pair<Int, Int>>()
            for (lx in 0 until 64) for (lz in 0 until 64) if (tiles.tiles[0][lx][lz].flags and 1 != 0) blocked += lx to lz
            blocked += (AfkBasementMapTool.EXPERT_LX to AfkBasementMapTool.EXPERT_LZ)
            locs.filter { it.plane == 0 && it.type in 10..11 }.forEach { loc ->
                val def = DEFINITIONS.get(ObjectDef::class.java, loc.id)
                if (def.blockwalk == 0) return@forEach
                footprint(loc, def).forEach { blocked += it }
            }
            val start = (AfkArea.BASEMENT_ARRIVAL.x - AfkBasementMapTool.BASE_X) to (AfkArea.BASEMENT_ARRIVAL.z - AfkBasementMapTool.BASE_Z)
            assertFalse(start in blocked, "arrival tile is free")
            val reached = HashSet<Pair<Int, Int>>().apply { add(start) }
            val todo = ArrayDeque(listOf(start))
            while (todo.isNotEmpty()) {
                val (x, z) = todo.removeFirst()
                for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val n = (x + dx) to (z + dz)
                    if (n.first in AfkBasementMapTool.ROOM_X && n.second in AfkBasementMapTool.ROOM_Z && n !in blocked && reached.add(n)) todo += n
                }
            }
            val stationIds = AfkBasementMapTool.FIRST_STATION_LOC until AfkBasementMapTool.FIRST_STATION_LOC + AfkBasementMapTool.STATIONS.size
            locs.filter { it.id in stationIds }.forEach { loc ->
                val fp = footprint(loc, DEFINITIONS.get(ObjectDef::class.java, loc.id))
                val sides = fp.flatMap { (x, z) -> listOf((x + 1) to z, (x - 1) to z, x to (z + 1), x to (z - 1)) }.filter { it !in fp }
                assertTrue(sides.any { it in reached }, "station ${loc.id} at ${loc.localX},${loc.localZ} can be reached")
            }
            // The stairs up can be used: one of the sides its access mask leaves open (rotated with the loc) borders a tile
            // players reach. Loc 54 only opens its south side; placed unturned against the south wall it said
            // "I can't reach that!" (owner 2026-09-26).
            val stairs = locs.single { it.id == AfkBasementMapTool.STAIRS_UP_LOC }
            val stairsDef = DEFINITIONS.get(ObjectDef::class.java, stairs.id)
            val mask = stairsDef.clipMask and 0xF
            val r = stairs.rotation
            val rotated = if (r == 0) mask else ((mask shl r) and 0xF) or (mask ushr (4 - r))
            val fp = footprint(stairs, stairsDef)
            val xs = fp.map { it.first }
            val zs = fp.map { it.second }
            val openSides =
                buildList {
                    if (rotated and 1 == 0) xs.distinct().forEach { add(it to zs.maxOf { it } + 1) } // north
                    if (rotated and 2 == 0) zs.distinct().forEach { add(xs.maxOf { it } + 1 to it) } // east
                    if (rotated and 4 == 0) xs.distinct().forEach { add(it to zs.minOf { it } - 1) } // south
                    if (rotated and 8 == 0) zs.distinct().forEach { add(xs.minOf { it } - 1 to it) } // west
                }
            assertTrue(openSides.any { it in reached }, "the stairs up can be used from a reachable tile (open sides $openSides)")
            assertTrue(start in openSides, "players arrive on a tile the stairs can be used from")
            val spot = AfkBasementMapTool.FISHING_LX to AfkBasementMapTool.FISHING_LZ
            assertTrue(reached.any { (x, z) -> maxOf(Math.abs(x - spot.first), Math.abs(z - spot.second)) <= 2 }, "the fishing spot is within two tiles of the bank")
            assertTrue(reached.size > 250, "the room is mostly walkable (${reached.size} tiles)")
            // Every loc stands inside the room (nothing in a wall), except the walls and rock backing themselves.
            val shell = setOf(AfkBasementMapTool.WALL, AfkBasementMapTool.ROCK, AfkBasementMapTool.ROCK_CORNER)
            locs.filter { it.id !in shell }.forEach { assertTrue(it.localX in AfkBasementMapTool.ROOM_X && it.localZ in AfkBasementMapTool.ROOM_Z, "loc ${it.id} inside the room") }
        } finally {
            library.close()
        }
    }

    @Test
    fun `every wall has a face outwards too, and the pond has grass under its water`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val maps = library.index(5)
            val tiles = Rev667TileCodec.decode(library.data(5, maps.archive(AfkBasementMapTool.MAP_NAME)!!.id, 0)!!)
            val locs = Rev667LocCodec.decode(library.data(5, maps.archive(AfkBasementMapTool.LOC_NAME)!!.id, 0)!!)
            fun wall(lx: Int, lz: Int, rotation: Int) = locs.any { it.id == AfkBasementMapTool.WALL && it.type == 0 && it.localX == lx && it.localZ == lz && it.rotation == rotation }
            val x = AfkBasementMapTool.ROOM_X
            val z = AfkBasementMapTool.ROOM_Z
            // The cellar wall only faces the room; without these the south and east sides were see-through from the camera.
            x.forEach { lx ->
                assertTrue(wall(lx, z.first, 3), "south edge $lx faces out")
                assertTrue(wall(lx, z.last, 1), "north edge $lx faces out")
                assertTrue(wall(lx, z.first - 1, 1), "south wall $lx faces in")
            }
            z.forEach { lz ->
                assertTrue(wall(x.first, lz, 0), "west edge $lz faces out")
                assertTrue(wall(x.last, lz, 2), "east edge $lz faces out")
            }
            // Water without an underlay drew as a white square.
            for (lx in AfkBasementMapTool.POND_X) for (lz in AfkBasementMapTool.POND_Z) {
                val t = tiles.tiles[0][lx][lz]
                assertEquals(AfkBasementMapTool.FLOOR_WATER, t.overlayId)
                assertEquals(AfkBasementMapTool.UNDERLAY_GRASS, t.underlayId, "pond $lx,$lz")
            }
            // No dark cellar pillars on the carpet any more.
            assertFalse(locs.any { it.id == 32903 }, "the black cellar pillars are gone")
        } finally {
            library.close()
        }
    }

    @Test
    fun `the hall entrance stands on its dark opening, so the steps down are visible`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val maps = library.index(5)
            val tiles = Rev667TileCodec.decode(library.data(5, maps.archive("m49_54")!!.id, 0)!!)
            for (dx in 0..1) for (dz in 0..1) {
                val t = tiles.tiles[0][AfkArea.HALL_STAIRS.x - 49 * 64 + dx][AfkArea.HALL_STAIRS.z - 54 * 64 + dz]
                assertEquals(AfkBasementMapTool.ENTRANCE_FLOOR, t.overlayId, "entrance tile +$dx,+$dz")
                assertEquals(0, t.underlayId)
            }
            // RoyalHallMapTool rebuilds this floor from the pristine map: it cuts the opening too.
            val hallTool = File("../src/main/kotlin/gg/rsmod/game/tools/importer/RoyalHallMapTool.kt").readText()
            assertTrue("AfkBasementMapTool.cutEntrance(tiles, rx, rz)" in hallTool)
            assertTrue(hallTool.indexOf("AfkBasementMapTool.cutEntrance(") < hallTool.indexOf("val updatedMap = Rev667TileCodec.encode(tiles)"))
        } finally {
            library.close()
        }
    }

    @Test
    fun `basement messages are plain English`() {
        assertEquals(
            "Your Fishing is already level 55, the highest level you can train here. Train it further out in the world.",
            AfkArea.alreadyCappedMessage("Fishing", 55),
        )
        assertEquals("Your Fishing has reached level 55, the highest level you can train here.", AfkArea.capReachedMessage("Fishing", 55))
        assertEquals("Fishing", AfkArea.joinNames(listOf("Fishing")))
        assertEquals("Fishing and Mining", AfkArea.joinNames(listOf("Fishing", "Mining")))
        assertEquals("Cooking, Fishing and Mining", AfkArea.joinNames(listOf("Cooking", "Fishing", "Mining")))
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/newplayer/afk_area.plugin.kts").readText()
        assertFalse("train it out in the world now" in script)
        // Talk-to is a conversation with questions, not the welcome speech again.
        listOf("What is this place?", "How does the training work?", "Where do I find each skill?", "Goodbye.").forEach { assertTrue(it in script, it) }
        // Every welcome line fits one dialogue box (the old ones were two long lines per box).
        (AfkArea.introLines(55) + AfkArea.WHERE_LINES + AfkArea.capLines("Construction", 55)).forEach { assertTrue(it.length <= 150, it) }
    }

    @Test
    fun `the hall entrance is its own loc with the dungeon icon, placed once where the old staircase stood`() {
        val library = CacheLibrary(CACHE_PATH)
        try {
            val bytes = library.data(16, AfkArea.HALL_STAIRS_DOWN ushr 8, AfkArea.HALL_STAIRS_DOWN and 0xFF)!!
            val type = Rev667LocType.decode(AfkArea.HALL_STAIRS_DOWN, bytes)
            assertEquals(AfkBasementMapTool.DUNGEON_MAP_ELEMENT, type.mapElement)
            assertEquals("Climb-down", type.options[0])
            val keys = ModernRegionProbeTool.loadKeys(File("../../data/xteas/xteas.json"))
            val maps = library.index(5)
            val archive = maps.archive("l49_54", keys[(49 shl 8) or 54])!!
            val locs = Rev667LocCodec.decode(archive.file(0)!!.data!!)
            val entrances = locs.filter { it.id == AfkArea.HALL_STAIRS_DOWN }
            assertEquals(1, entrances.size)
            assertEquals(AfkArea.HALL_STAIRS.x - 49 * 64, entrances[0].localX)
            assertEquals(AfkArea.HALL_STAIRS.z - 54 * 64, entrances[0].localZ)
            assertEquals(2, locs.count { it.id == AfkBasementMapTool.STANDING_TORCH && it.plane == 0 && (it.localX to it.localZ) in AfkBasementMapTool.ENTRANCE_TORCHES.map { (x, z) -> (x - 49 * 64) to (z - 54 * 64) } })
            val decor = File("../../data/cfg/home_decor.txt").readLines()
            assertFalse(decor.any { it.startsWith("obj 62780 3160 3487") }, "the marble staircase is gone")
            assertEquals("Climb-up", DEFINITIONS.get(ObjectDef::class.java, AfkArea.BASEMENT_STAIRS_UP).options[0])
        } finally {
            library.close()
        }
    }

    // ------------------------------------------------------------------------------------------ training

    @Test
    fun `training carries on through every level-up with only a chat line in the basement`() {
        val account = Account()
        val config = NewPlayerConfig.DEFAULTS
        var levelUps = 0
        var level = account.skills.getMaxLevel(Skills.FISHING)
        while (AfkArea.train(account.player, Skills.FISHING, config)) {
            val now = account.skills.getMaxLevel(Skills.FISHING)
            if (now > level) {
                levelUps++
                assertTrue(AfkArea.quietLevelUp(account.player, Skills.FISHING, "Fishing"), "handled quietly in the basement")
                level = now
            }
        }
        assertEquals(54, levelUps + 1, "levels 2..54 passed while training went on, 55 ends it")
        assertEquals("Your Fishing level is now 54.", account.messages.last())
        assertTrue(account.messages.all { it.startsWith("Your Fishing level is now ") })
        // Outside the basement the normal level-up (window, jingle, fireworks) runs: quietLevelUp does not take it.
        val outside = Account(Tile(3164, 3490, 0))
        assertFalse(AfkArea.quietLevelUp(outside.player, Skills.FISHING, "Fishing"))
        assertTrue(outside.messages.isEmpty())
        // level_up.plugin.kts asks the basement first, before any window, jingle or firework.
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/skills/level_up.plugin.kts").readText()
        val quiet = script.indexOf("AfkArea.quietLevelUp(")
        assertTrue(quiet > 0)
        listOf("LEVEL_UP_FIREWORKS", "playJingle", "levelUpMessageBox", "FLASHING_ICON_VARBITS", "interruptQueues").forEach { assertTrue(script.indexOf(it) > quiet, "$it comes after the basement check") }
    }

    @Test
    fun `the cap is exactly level 55 even with large rates, and a level 55 skill cannot train`() {
        val cap = SkillSet.getXpForLevel(55)
        assertEquals(166636.0, Math.floor(cap))
        listOf(NewPlayerConfig.DEFAULTS, NewPlayerConfig.DEFAULTS.copy(afkRate = 7.3, afkXpPerTick = 999.0), NewPlayerConfig.DEFAULTS.copy(afkRate = 0.37)).forEach { config ->
            val account = Account()
            while (AfkArea.train(account.player, Skills.MINING, config)) Unit
            assertEquals(cap, account.skills.getCurrentXp(Skills.MINING))
            assertEquals(55, account.skills.getMaxLevel(Skills.MINING))
            assertFalse(AfkArea.train(account.player, Skills.MINING, config))
            assertEquals(cap, account.skills.getCurrentXp(Skills.MINING))
            // Every tick is raw XP: no level curve, bonus XP or xp.normal_rate can push it over the cap.
            assertTrue(account.xpCalls.all { (_, modifiers, noBonus) -> !modifiers && noBonus })
        }
        val high = Account()
        high.skills.setBaseLevel(Skills.COOKING, 70)
        assertTrue(AfkArea.atCap(high.player, Skills.COOKING))
        assertEquals(0.0, AfkArea.tickXp(high.player, Skills.COOKING))
        val justBelow = Account()
        justBelow.skills.setBaseXp(Skills.AGILITY, cap - 5.0)
        assertTrue(AfkArea.train(justBelow.player, Skills.AGILITY).not())
        assertEquals(cap, justBelow.skills.getCurrentXp(Skills.AGILITY))
    }

    @Test
    fun `one station at a time, no double ticks and nothing after leaving or logging out`() {
        val account = Account()
        val first = AfkArea.newSession(account.player)
        assertTrue(AfkArea.mayTrain(account.player, first))
        val second = AfkArea.newSession(account.player)
        assertFalse(AfkArea.mayTrain(account.player, first), "clicking a second station ends the first")
        assertTrue(AfkArea.mayTrain(account.player, second))
        account.loggingOut = true
        assertFalse(AfkArea.mayTrain(account.player, second), "logging out")
        account.loggingOut = false
        account.online = false
        assertFalse(AfkArea.mayTrain(account.player, second), "disconnected")
        account.online = true
        account.tile = AfkArea.HALL_ARRIVAL
        assertFalse(AfkArea.mayTrain(account.player, second), "left the basement")
        account.tile = AfkArea.BASEMENT_ARRIVAL
        AfkArea.endSession(account.player)
        assertFalse(AfkArea.mayTrain(account.player, second), "stopped")
        // Two players on the same station train independently.
        val a = Account()
        val b = Account()
        val sa = AfkArea.newSession(a.player)
        val sb = AfkArea.newSession(b.player)
        repeat(10) {
            assertTrue(AfkArea.mayTrain(a.player, sa) && AfkArea.mayTrain(b.player, sb))
            AfkArea.train(a.player, Skills.THIEVING)
            AfkArea.train(b.player, Skills.THIEVING)
        }
        assertEquals(a.skills.getCurrentXp(Skills.THIEVING), b.skills.getCurrentXp(Skills.THIEVING))
        assertEquals(10 * NewPlayerConfig.current.afkXpPerTick * NewPlayerConfig.current.afkRate, a.skills.getCurrentXp(Skills.THIEVING))
        // The training script checks the session, the tile, items and interfaces every tick.
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/newplayer/afk_area.plugin.kts").readText()
        listOf("AfkArea.mayTrain(player, session)", "player.tile != startTile", "itemsFingerprint(player) != items", "getModal() != -1", "isLocked()").forEach { assertTrue(it in script, it) }
    }

    // ------------------------------------------------------------------------------------------ safe zone and expert

    @Test
    fun `the whole basement is a safe zone and nothing outside the room is`() {
        for (x in AfkArea.MIN_X..AfkArea.MAX_X) for (z in AfkArea.MIN_Z..AfkArea.MAX_Z) assertTrue(GuardedZones.contains(Tile(x, z, 0)), "$x,$z")
        assertTrue(AfkArea.contains(AfkArea.BASEMENT_ARRIVAL))
        assertTrue(AfkArea.contains(AfkArea.SURVIVAL_EXPERT_TILE))
        assertTrue(AfkArea.contains(AfkArea.FISHING_SPOT_TILE))
        assertFalse(AfkArea.contains(Tile(AfkArea.MAX_X + 1, AfkArea.MIN_Z, 0)))
        assertFalse(AfkArea.contains(Tile(AfkArea.MIN_X, AfkArea.MIN_Z, 1)))
    }

    @Test
    fun `the Survival Expert welcomes once and congratulates once per capped skill`() {
        val account = Account()
        assertTrue(AfkArea.markIntro(account.player))
        assertFalse(AfkArea.markIntro(account.player))
        assertTrue(AfkArea.markCapped(account.player, Skills.MINING))
        assertFalse(AfkArea.markCapped(account.player, Skills.MINING))
        assertTrue(AfkArea.markCapped(account.player, Skills.FISHING))
        assertTrue(AfkArea.INTRO_SEEN.persistenceKey != null && AfkArea.CAPPED_SKILLS.persistenceKey != null, "saved on the account")
        val intro = AfkArea.introLines(55).joinToString(" ")
        listOf("level 55", "safe zone", "as long as you like", "chat message", "Walk away").forEach { assertTrue(it in intro, it) }
        val capped = AfkArea.capLines("Mining", 55).joinToString(" ")
        listOf("Mining", "level 55", "normal RuneScape way", "other skills").forEach { assertTrue(it in capped, it) }
    }

    @Test
    fun `new_player yml holds the basement rates`() {
        val text = File("../../data/cfg/new_player.yml").readText()
        assertTrue("OWNER" in text.substringAfter("afk_area:").substringBefore("summoning_kit:"), "the owner marker on the XP rate")
        val loaded = NewPlayerConfig.load(File("../../data/cfg/new_player.yml"))
        assertEquals(55, loaded.afkLevelCap)
    }

    companion object {
        private val SKILL_BY_NAME =
            mapOf(
                "Cooking" to Skills.COOKING, "Firemaking" to Skills.FIREMAKING, "Fishing" to Skills.FISHING, "Herblore" to Skills.HERBLORE,
                "Woodcutting" to Skills.WOODCUTTING, "Fletching" to Skills.FLETCHING, "Crafting" to Skills.CRAFTING, "Smithing" to Skills.SMITHING,
                "Mining" to Skills.MINING, "Construction" to Skills.CONSTRUCTION, "Runecrafting" to Skills.RUNECRAFTING, "Thieving" to Skills.THIEVING,
                "Agility" to Skills.AGILITY, "Farming" to Skills.FARMING, "Hunter" to Skills.HUNTER,
            )
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        private fun footprint(loc: Rev667Loc, def: ObjectDef): Set<Pair<Int, Int>> {
            val w = if (loc.rotation and 1 == 1) def.length else def.width
            val d = if (loc.rotation and 1 == 1) def.width else def.length
            return (loc.localX until loc.localX + w).flatMap { x -> (loc.localZ until loc.localZ + d).map { z -> x to z } }.toSet()
        }

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
