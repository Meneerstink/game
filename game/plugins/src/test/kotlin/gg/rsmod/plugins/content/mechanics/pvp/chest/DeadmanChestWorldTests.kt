package gg.rsmod.plugins.content.mechanics.pvp.chest

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachIds
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.skills.summoning.Pets
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N101 on the REAL 667 map and the real engine: spawn a chest, open it as a player would (Loot -> 100 ticks singles / 50 multi), every
 * interrupt, every refusal at start and at completion, first opener only, kill chest (forced roll) incl. the kinds of kill that must
 * never count, the chest's Zemouregal (emblem only, gone with the chest). The cache locs/npc are not imported yet, so the ids point at
 * stand-in objects (the breach loc / boss spawn / Zemouregal 14466): the chest logic does not care which model it shows.
 */
class DeadmanChestWorldTests {
    private val clients = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()
    private val written = HashMap<Client, MutableList<gg.rsmod.game.message.Message>>()
    private var cycle = 100_000

    @Before
    fun setUp() {
        DeadmanChestIds.closedLoc = BreachIds.BREACH_LOC
        DeadmanChestIds.openLoc = BreachIds.BOSS_SPAWN_LOC
        DeadmanChestIds.guardianNpc = gg.rsmod.plugins.api.cfg.Npcs.ZEMOUREGAL
        DeadmanChestIds.spawnLoc = 0
        DeadmanChestIds.spawnTicks = 0
        DeadmanChestWorld.reset()
        DeadmanChestWorld.cycleOverride = cycle
        // The skull value needs the GE guide prices (not loaded in this engine); one coin = one coin of risk, below is exact.
        DeadmanChest.riskOf = { p -> p.inventory.getItemCount(Items.COINS_995).toLong() }
        gg.rsmod.plugins.content.mechanics.pvp.BossAreas.init(world)
    }

    @After
    fun cleanUp() {
        DeadmanChestWorld.chests.toList().forEach { DeadmanChestWorld.remove(world, it, "test") }
        DeadmanChestWorld.cycleOverride = null
        DeadmanChest.riskOf = { p -> gg.rsmod.plugins.content.mechanics.pvp.RiskSkull.calculateRiskedValue(p) }
        DeadmanChestIds.closedLoc = 0
        DeadmanChestIds.openLoc = 0
        DeadmanChestIds.guardianNpc = 0
        clients.forEach { world.unregister(it) }
        clients.clear()
        npcs.forEach {
            world.remove(it)
            DeadmanBreach.live.remove(it)
        }
        npcs.clear()
    }

    // ------------------------------------------------------------------------------------------------------- opening

    @Test
    fun `all 71 roster pets reach their owner through follower, backpack, bank and private ground on the real map`() {
        val dir = File("../../data/cfg/pets")
        Pets.load(File(dir, "pets.tsv"), File(dir, "pet_tiers.tsv"))
        val expected = Pets.parseTiers(File(dir, "pet_tiers.tsv").readLines())
        assertEquals(71, expected.size)
        val at = freeTile(false)
        for ((index, info) in expected.withIndex()) {
            val base = Pets.parseTable(File(dir, "pets.tsv").readLines()).first { it.pet == info.pet }.item
            for (destination in listOf("follower", "backpack", "bank", "ground")) {
                val p = player("pet${index}${destination.take(1)}", at)
                var ground: GroundItem? = null
                try {
                    if (destination != "follower") {
                        val other = expected.first { it.pet != info.pet }.pet
                        Pets.award(p, other)
                        assertNotNull("precondition: other pet follows", Pets.current(p))
                    }
                    if (destination == "bank" || destination == "ground") {
                        for (slot in 0 until p.inventory.capacity) p.inventory[slot] = gg.rsmod.game.model.item.Item(Items.SHARK)
                    }
                    if (destination == "ground") {
                        for (slot in 0 until p.bank.capacity) p.bank[slot] = gg.rsmod.game.model.item.Item(Items.SHARK)
                    }
                    val available = DeadmanChestLoot.pets().filterNot { Pets.owns(p, it.first) }
                    val petIndex = available.indexOfFirst { it.first == info.pet }
                    assertTrue("${info.pet} must be reachable ($destination)", petIndex >= 0)
                    val draw = DeadmanChestLoot.FIXED.sumOf { it.weight } + petIndex * DeadmanChestLoot.PET_WEIGHT
                    val reward = DeadmanChest.giveReward(p, object : java.util.Random() {
                        override fun nextInt(bound: Int): Int {
                            assertTrue(draw in 0 until bound)
                            return draw
                        }
                    })
                    assertTrue(reward is DeadmanChestLoot.Reward.Pet)
                    assertEquals(info.pet, (reward as DeadmanChestLoot.Reward.Pet).pet)
                    assertEquals(base, reward.item)
                    when (destination) {
                        "follower" -> {
                            assertEquals(base, Pets.itemOf(p))
                            assertNotNull(Pets.current(p))
                            // Real follower lifecycle: disconnect/restore and recovery after a teleport retain the reward.
                            Pets.disconnect(p)
                            assertNull(Pets.current(p))
                            Pets.restoreOnLogin(p)
                            assertEquals(base, Pets.itemOf(p))
                            p.tile = beside(at)
                            assertTrue(Pets.call(p))
                            assertTrue(Pets.owns(p, info.pet))
                        }
                        "backpack" -> assertEquals(1, p.inventory.getItemCount(base))
                        "bank" -> assertEquals(1, p.bank.getItemCount(base))
                        "ground" -> {
                            ground = world.getGroundItem { it.item == base && it.isOwnedBy(p) }
                            assertNotNull("${info.pet}: private ground fallback", ground)
                            assertEquals(1, ground!!.amount)
                            assertFalse(ground!!.isPublic())
                            assertEquals(0, p.inventory.getItemCount(base) + p.bank.getItemCount(base))
                        }
                    }
                    for (npc in info.dropNpcs) assertEquals(info, Pets.dropFor(npc))
                } finally {
                    ground?.let { world.remove(it) }
                    Pets.disconnect(p)
                    world.unregister(p)
                    clients.remove(p)
                    written.remove(p)
                }
            }
        }
    }

    @Test
    fun `the chest opens after exactly 100 ticks in singles and 50 in multi, the first opener gets one reward and the chest is gone after the open state`() {
        for (multi in listOf(false, true)) {
            val tile = freeTile(multi)
            val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
            assertEquals("chest tile is multi=$multi", multi, tile.isMulti(world))
            val p = player("opener$multi", beside(tile))
            giveRisk(p)
            val before = snapshotOf(p)
            DeadmanChestWorld.beginOpen(p, chest.tile)
            val needed = if (multi) 50 else 100
            repeat(needed - 1) { tickChest() }
            assertFalse("not open before the last tick ($multi)", chest.state.looted)
            tickChest()
            assertTrue("open after $needed ticks (multi=$multi) ${messages(p)}", chest.state.looted)
            assertEquals(p.username.lowercase(), chest.state.opener)
            assertTrue("a reward arrived", snapshotOf(p) != before || DeadmanChestLoot.pets().isNotEmpty())
            repeat(DeadmanChestConfig.OPEN_STATE_TICKS + 1) { tickChest() }
            assertTrue("chest removed after the open state", DeadmanChestWorld.chests.isEmpty())
            world.unregister(p)
            clients.remove(p)
        }
    }

    @Test
    fun `every interrupt resets the opening - eating, equipping, dropping, a hit, an attack, walking away, logging out`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        val interrupts: List<Pair<String, (Client) -> Unit>> =
            listOf(
                "eat / use item (inventory change)" to { p -> p.inventory.add(Items.SHARK, 1) },
                "equip (equipment change)" to { p -> p.equipment[3] = gg.rsmod.game.model.item.Item(Items.ABYSSAL_WHIP, 1) },
                "drop (inventory change)" to { p -> p.inventory.remove(Items.COINS_995, 10) },
                "hit taken / dealt (combat timer re-armed)" to { p -> p.timers[ACTIVE_COMBAT_TIMER] = 17 },
                "walking away" to { p -> p.tile = Tile(p.tile.x + 3, p.tile.z, p.tile.height) },
                "logout" to { p -> world.unregister(p) },
            )
        for ((name, action) in interrupts) {
            val p = player("int${name.hashCode()}", beside(tile))
            giveRisk(p)
            DeadmanChestWorld.beginOpen(p, chest.tile)
            repeat(40) { tickChest() }
            assertEquals("$name: running", 40, DeadmanChestWorld.channelElapsed(p))
            action(p)
            tickChest()
            assertNull("$name: the opening was stopped and progress is gone", DeadmanChestWorld.channelElapsed(p))
            assertFalse("$name: chest still closed", chest.state.looted)
            if (name != "logout") {
                world.unregister(p)
                clients.remove(p)
            }
        }
    }

    @Test
    fun `refused at the start - risk below 100000, beginner protection, kill grace, standing on the chest, too far away`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        val poor = player("poor", beside(tile))
        poor.inventory.add(Items.COINS_995, 50_000)
        DeadmanChestWorld.beginOpen(poor, chest.tile)
        assertEquals(DeadmanChest.MSG_RISK, messages(poor).last())
        assertNull(DeadmanChestWorld.channelElapsed(poor))

        val newbie = player("newbie", beside(tile))
        giveRisk(newbie)
        newbie.attr[NEW_ACCOUNT_ATTR] = true
        BeginnerProtection.grantOnFirstLogin(newbie)
        assertTrue("precondition: protected", BeginnerProtection.isProtected(newbie))
        DeadmanChestWorld.beginOpen(newbie, chest.tile)
        assertEquals(DeadmanChest.MSG_PROTECTED, messages(newbie).last())

        val graced = player("graced", beside(tile))
        giveRisk(graced)
        KillGrace.grant(graced)
        DeadmanChestWorld.beginOpen(graced, chest.tile)
        assertEquals(DeadmanChest.MSG_PROTECTED, messages(graced).last())

        val on = player("onchest", tile)
        giveRisk(on)
        DeadmanChestWorld.beginOpen(on, chest.tile)
        assertEquals(DeadmanChest.MSG_ON_CHEST, messages(on).last())

        val far = player("far", Tile(tile.x + 5, tile.z, tile.height))
        giveRisk(far)
        DeadmanChestWorld.beginOpen(far, chest.tile)
        assertEquals(DeadmanChest.MSG_TOO_FAR, messages(far).last())
        assertEquals(0, DeadmanChestWorld.channelCount())
    }

    @Test
    fun `risk lost during the opening (not through the inventory) is caught at completion`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        val p = player("dropper", beside(tile))
        giveRisk(p)
        DeadmanChestWorld.beginOpen(p, chest.tile)
        repeat(99) { tickChest() }
        // A path that is not an inventory action (a charge/degrade system) emptied the pockets, the snapshot hash follows - so the
        // refusal comes from the SAME requirement check on the last tick.
        p.inventory.removeAll()
        tickChest()
        assertFalse(chest.state.looted)
        assertEquals(DeadmanChest.MSG_RISK, messages(p).last())
    }

    @Test
    fun `two players finishing on the same tick - one reward, the other is told the chest was already looted`() {
        val tile = freeTile(true)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        val a = player("first", beside(tile))
        val b = player("second", Tile(beside(tile).x, beside(tile).z + 1, tile.height).takeIf { it != tile } ?: beside(tile))
        giveRisk(a)
        giveRisk(b)
        val bBefore = snapshotOf(b)
        DeadmanChestWorld.beginOpen(a, chest.tile)
        DeadmanChestWorld.beginOpen(b, chest.tile)
        repeat(50) { tickChest() }
        assertTrue(chest.state.looted)
        assertEquals("first", chest.state.opener)
        assertEquals("the loser gets nothing", bBefore, snapshotOf(b))
        assertTrue(messages(b).any { it == DeadmanChest.MSG_LOOTED })
    }

    @Test
    fun `an unlooted chest expires, a new timed chest replaces the old one, and a player standing on the spot is moved beside it`() {
        val tile = freeTile(false)
        val standing = player("standing", tile)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        assertTrue("moved off the chest", standing.tile != tile && standing.tile.getDistance(tile) <= 4)
        val second = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("other", tile))!!
        assertFalse("the old timed chest is gone", DeadmanChestWorld.chests.contains(chest))
        assertEquals(1, DeadmanChestWorld.chests.size)
        cycle += DeadmanChestConfig.TIMED_INTERVAL_TICKS + 1
        DeadmanChestWorld.cycleOverride = cycle
        DeadmanChestWorld.tick(world)
        assertTrue("expired", DeadmanChestWorld.chests.isEmpty())
        assertNotNull(second)
    }

    @Test
    fun `without the cache data nothing spawns - the feature is off, no invisible chest`() {
        DeadmanChestIds.closedLoc = 0
        assertNull(DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("x", freeTile(false))))
        assertTrue(DeadmanChestWorld.chests.isEmpty())
    }

    @Test
    fun `the wiki spots that survive the 667 map and the safe zones are real chest places`() {
        val all = DeadmanChestWorld.spots(world)
        println("CHEST-SPOTS kept=" + all.joinToString { "${it.name}@${it.tile.x},${it.tile.z}" })
        assertTrue("at least 4 of the 10 wiki spots must stay usable and dangerous: ${all.map { it.name }}", all.size >= 4)
        all.forEach { assertTrue("${it.name} fits", DeadmanChestWorld.fits(world, it.tile)) }
    }

    // ------------------------------------------------------------------------------------------------------- kill chests

    @Test
    fun `a forced roll after a 30 HP kill spawns a silent chest, a Zemouregal summon, a safe-zone kill and a weak kill never do, and the cooldown holds`() {
        val killer = player("killer", beside(freeTile(false)))
        val tile = freeTile(false)
        // Breach monsters (not summon, not test) DO count (OSRS wiki trivia); their combat defs come from the breach script bound below.
        // (a boss npc would turn its whole region into a boss area, where the chest moves to the lair entrance: pick a plain one)
        val plainId =
            BreachMonsters.BY_ID.filterValues { !it.summon }.keys.first { id ->
                val probe = DeadmanBreach.spawnMonster(world, id, tile, 8)
                val plain = !gg.rsmod.plugins.content.mechanics.pvp.BossNpcs.isBoss(probe) && probe.combatDef.lifepoints >= DeadmanChestConfig.MIN_LIFEPOINTS
                world.remove(probe)
                DeadmanBreach.live.remove(probe)
                plain
            }
        val boss = DeadmanBreach.spawnMonster(world, plainId, tile, 8).also { npcs += it }
        assertTrue("30+ HP: ${boss.combatDef.lifepoints}", boss.combatDef.lifepoints >= DeadmanChestConfig.MIN_LIFEPOINTS)

        val summon = DeadmanBreach.spawnMonster(world, 14471, tile, 8).also { npcs += it }
        assertTrue("the summon is recognised as a breach summon", DeadmanChest.killFactsOf(killer, summon).breachSummonOrTest)
        assertNull(DeadmanChestWorld.onNpcKilled(world, killer, summon) { 0 })

        val test = DeadmanBreach.spawnMonster(world, plainId, tile, 8, test = true).also { npcs += it }
        assertNull("breachtest npcs", DeadmanChestWorld.onNpcKilled(world, killer, test) { 0 })

        val safe = DeadmanBreach.spawnMonster(world, plainId, SAFE_TILE, 8).also { npcs += it }
        assertNull("kill in a guarded city", DeadmanChestWorld.onNpcKilled(world, killer, safe) { 0 })
        val man = npcAt(gg.rsmod.plugins.api.cfg.Npcs.MOUNTAIN_TROLL_1108, tile)
        man.combatDef = man.combatDef.copy(lifepoints = 29)
        assertNull("a weak npc", DeadmanChestWorld.onNpcKilled(world, killer, man) { 0 })
        assertNull("the roll must hit", DeadmanChestWorld.onNpcKilled(world, killer, boss) { 1 })
        assertTrue(DeadmanChestWorld.chests.isEmpty())

        val chest = DeadmanChestWorld.onNpcKilled(world, killer, boss) { 0 }
        assertNotNull("forced roll = chest ${DeadmanChest.killFactsOf(killer, boss)} spot=${DeadmanChestWorld.killSpot(world, killer, boss.tile)} at=${boss.tile} fits=${DeadmanChestWorld.fits(world, boss.tile)} usable=${DeadmanBreach.usable(world, boss.tile)} boss=${gg.rsmod.plugins.content.mechanics.pvp.BossAreas.isBossArea(world, boss.tile)} danger=${gg.rsmod.plugins.content.mechanics.pvp.AreaState.isDangerous(boss.tile)} enabled=${DeadmanChestWorld.enabled(world)} limiter=${DeadmanChestWorld.limiter.activeCount()} keys=${DeadmanChestWorld.limiterKeys(killer)}", chest)
        assertEquals(false, chest!!.timed)
        assertTrue("a kill chest is silent", messages(killer).none { it.contains("Deadman") })
        val second = DeadmanBreach.spawnMonster(world, plainId, tile, 8).also { npcs += it }
        assertNull("one per killer / cooldown", DeadmanChestWorld.onNpcKilled(world, killer, second) { 0 })
        // The plugin hook is bound: the real npc-killed path reaches the same rules (cooldown still holds, nothing new).
        world.plugins.executeNpcKilled(killer, second)
        assertEquals(1, DeadmanChestWorld.chests.size)
    }

    @Test
    fun `the chest Zemouregal is no breach npc, never rolls a chest, drops only a tier 5 Archaic emblem for the top damager and goes with the chest`() {
        DeadmanChestIds.guardianNpc = 14466
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("test spot", tile))!!
        val g = chest.guardian!!
        assertNotNull("guardian spawned", g)
        npcs += g
        assertFalse("not a breach npc (no breach loot / points / close)", DeadmanBreach.isBreachNpc(g))
        assertTrue(DeadmanChestWorld.isGuardian(g))
        assertFalse("his kill never counts", DeadmanChest.killCounts(DeadmanChest.killFactsOf(player("slayer", beside(tile)), g)))
        val slayer = clients.last()
        g.damageMap.add(slayer, 100)
        val emblem = DeadmanChestWorld.onGuardianDeath(world, g)
        assertNotNull("the top damager gets the emblem", emblem)
        assertEquals(BreachIds.ARCHAIC_EMBLEM_TIER_5, emblem!!.item)
        assertEquals(1, emblem.amount)
        DeadmanChestWorld.remove(world, chest, "test")
        assertFalse("guardian removed with the chest", world.npcs.contains(g))
    }

    // ------------------------------------------------------------------------------------------------------- helpers

    @Test
    fun `guardian has the real chest combat definition and the Zemouregal attack row`() {
        val tile = freeTile(true)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("combat", tile))!!
        val guardian = chest.guardian!!
        assertEquals("DSL 5000 lifepoints becomes 500 engine HP", 500, guardian.combatDef.lifepoints)
        assertEquals(7, guardian.combatDef.attackSpeed)
        assertFalse(guardian.respawns)
        assertEquals("breach_zemouregal", NpcAttacks.rowFor(guardian.id)!!.combatDef)
        assertFalse(DeadmanBreach.isBreachNpc(guardian))
    }

    @Test
    fun `the real 4 by 4 footprint refuses all inner tiles and accepts its entire perimeter, bodies move outside it`() {
        val def = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, BreachIds.BREACH_LOC)
        val oldWidth = def.width
        val oldLength = def.length
        def.width = 4
        def.length = 4
        var chest: DeadmanChestWorld.Chest? = null
        try {
            val tile = freeTile(false)
            val p = player("footprint", Tile(tile.x + 3, tile.z + 3, tile.height))
            giveRisk(p)
            chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("4x4", tile))!!
            assertFalse("a body anywhere inside is moved outside before placement", DeadmanChestWorld.inside(world, tile, p.tile))
            for (dx in -1..4) for (dz in -1..4) {
                p.tile = Tile(tile.x + dx, tile.z + dz, tile.height)
                val refusal = DeadmanChest.refusal(DeadmanChest.factsOf(p, tile))
                if (dx in 0..3 && dz in 0..3) assertEquals("inside $dx,$dz", DeadmanChest.MSG_ON_CHEST, refusal)
                else assertNull("perimeter $dx,$dz is adjacent", refusal)
            }
        } finally {
            chest?.let { DeadmanChestWorld.remove(world, it, "footprint test") }
            def.width = oldWidth
            def.length = oldLength
        }
    }

    @Test
    fun `appearance animation finishes before Loot is offered, then the clicked action can start a fresh channel`() {
        DeadmanChestIds.spawnLoc = BreachIds.BOSS_SPAWN_LOC
        DeadmanChestIds.spawnTicks = 3
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("appearing", tile))!!
        val p = player("appearance", beside(tile))
        giveRisk(p)
        DeadmanChestWorld.beginOpen(p, tile)
        assertNull(DeadmanChestWorld.channelElapsed(p))
        repeat(3) { tickChest() }
        assertEquals(DeadmanChestIds.closedLoc, chest.obj.id)
        DeadmanChestWorld.beginOpen(p, tile)
        repeat(3) { tickChest() }
        p.fullInterruption(interactions = true, queue = true)
        DeadmanChestWorld.beginOpen(p, tile)
        tickChest()
        assertEquals("the second Loot click starts a new attempt", 1, DeadmanChestWorld.channelElapsed(p))
    }

    @Test
    fun `each missing cache component disables the whole chest feature`() {
        val spot = DeadmanChestWorld.Spot("partial cache", freeTile(false))
        for (missing in listOf("closed", "open", "guardian")) {
            DeadmanChestIds.closedLoc = if (missing == "closed") 0 else BreachIds.BREACH_LOC
            DeadmanChestIds.openLoc = if (missing == "open") 0 else BreachIds.BOSS_SPAWN_LOC
            DeadmanChestIds.guardianNpc = if (missing == "guardian") 0 else 14466
            assertNull("missing $missing must not spawn a partial chest", DeadmanChestWorld.spawnTimed(world, spot))
        }
        DeadmanChestIds.guardianNpc = Int.MAX_VALUE
        assertFalse("a configured but absent NPC definition is also incomplete", DeadmanChestWorld.enabled(world))
    }

    @Test
    fun `real engine NPC health uses the 29 and 30 HP boundary after the DSL unit conversion`() {
        val tile = freeTile(false)
        val killer = player("hpboundary", beside(tile))
        val npc = npcAt(gg.rsmod.plugins.api.cfg.Npcs.MOUNTAIN_TROLL_1108, tile)
        npc.combatDef = npc.combatDef.copy(lifepoints = 29)
        assertFalse(DeadmanChest.killCounts(DeadmanChest.killFactsOf(killer, npc)))
        npc.combatDef = npc.combatDef.copy(lifepoints = 30)
        assertTrue("30 engine HP is eligible; it must not require 300 HP", DeadmanChest.killCounts(DeadmanChest.killFactsOf(killer, npc)))
    }

    @Test
    fun `a real incoming swing interrupts even when the combat timer was already at 17 and auto retaliation is off`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("repeated combat", tile))!!
        val p = player("repeatedhit", beside(tile))
        giveRisk(p)
        p.timers[ACTIVE_COMBAT_TIMER] = 17
        DeadmanChestWorld.beginOpen(p, tile)
        repeat(3) { tickChest() }
        assertEquals(3, DeadmanChestWorld.channelElapsed(p))
        gg.rsmod.plugins.content.combat.Combat.postAttack(chest.guardian!!, p)
        assertEquals("the timer value alone cannot identify this new swing", 17, p.timers[ACTIVE_COMBAT_TIMER])
        tickChest()
        assertNull("a new combat action resets progress even without a timer increase", DeadmanChestWorld.channelElapsed(p))
    }

    @Test
    fun `real inventory Check-charge and Rub then cancel interrupt without changing any item`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("actions", tile))!!
        val p = player("itemactions", beside(tile))
        giveRisk(p)
        p.inventory.add(Items.CAMULET)
        p.inventory.add(Items.RING_OF_DUELLING_8)
        p.interfaces.open(548, 197, 679)
        val handler = gg.rsmod.game.message.handler.IfButton1Handler()
        for ((item, label) in listOf(Items.CAMULET to "Check-charge", Items.RING_OF_DUELLING_8 to "Rub")) {
            val option = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).inventoryMenu.indexOfFirst { it.equals(label, true) }
            assertTrue("cache option $label for $item", option >= 0)
            val opcodes = listOf(61, 64, 4, 18, 10)
            DeadmanChestWorld.beginOpen(p, tile)
            repeat(3) { tickChest() }
            val before = snapshotOf(p)
            handler.handle(p, world, gg.rsmod.game.message.impl.IfButtonMessage(679 shl 16, option, p.inventory.getItemIndex(item, skipAttrItems = false), item, opcodes[option]))
            assertEquals("$label did not mutate an item", before, snapshotOf(p))
            tickChest()
            assertNull("$label action must reset the channel", DeadmanChestWorld.channelElapsed(p))
            if (label == "Check-charge") {
                assertTrue("real charge handler ran", messages(p).any { it.contains("Your Camulet has") })
            } else {
                // Drive the real Rub dialogue and choose Nowhere, without changing an item or teleporting.
                val queues = gg.rsmod.game.model.entity.Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as gg.rsmod.game.model.queue.QueueTaskSet
                queues.cycle()
                assertTrue("real Rub dialogue opened", p.interfaces.optionsOpen)
                queues.submitReturnValue(gg.rsmod.game.message.impl.ResumePauseButtonMessage(interfaceId = 234, component = 6, button = 6))
                queues.cycle()
                assertEquals("cancel Rub did not consume a charge", before, snapshotOf(p))
            }
        }
        assertFalse(chest.state.looted)
    }

    @Test
    fun `guardian summons belong only to their creator and late summons are removed after loot and guardian death`() {
        DeadmanChestIds.guardianNpc = 14466
        for (looted in listOf(false, true)) {
            val tile = freeTile(true)
            val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("summons", tile))!!
            val guardian = chest.guardian!!
            val target = player("target$looted", beside(tile))
            giveRisk(target)
            val unrelated = DeadmanBreach.spawnMonster(world, 14471, target.tile, 8).also { npcs += it }
            val otherGuardian = npcAt(14466, Tile(guardian.tile.x + 1, guardian.tile.z, guardian.tile.height))
            repeat(5) { assertTrue(NpcAttacks.attackWith(otherGuardian, target, "magic")) }
            val others = DeadmanBreach.live.filter { it !== unrelated }.toSet()
            assertTrue("real other-guardian swing created a summon", others.isNotEmpty())
            tickChest() // old distance-based code incorrectly claims both unrelated summons
            if (looted) {
                DeadmanChestWorld.beginOpen(target, tile)
                repeat(50) { tickChest() }
                assertTrue(chest.state.looted)
            }
            val before = DeadmanBreach.live.toSet()
            repeat(5) { assertTrue(NpcAttacks.attackWith(guardian, target, "magic")) }
            val own = DeadmanBreach.live.filter { it !in before }
            assertTrue("real guardian swing created a late summon", own.isNotEmpty())
            guardian.setCurrentLifepoints(0)
            world.remove(guardian) // removal must still find his ownership after death, without another tracking tick
            DeadmanChestWorld.remove(world, chest, "test ownership")
            assertTrue("unrelated breach summon survives", world.npcs.contains(unrelated))
            others.forEach { assertTrue("another guardian's summon survives", world.npcs.contains(it)) }
            own.forEach { assertFalse("own late summon removed after loot=$looted and death", world.npcs.contains(it)) }
            (others + own).forEach { world.remove(it); DeadmanBreach.live.remove(it) }
            world.remove(otherGuardian)
            world.remove(unrelated)
            DeadmanBreach.live.remove(unrelated)
            world.unregister(target)
            clients.remove(target)
        }
    }

    @Test
    fun `kill spawn fallback must be reachable by the killer`() {
        val tile = freeTile(false)
        val killer = player("unreachable", Tile(tile.x + 100, tile.z, tile.height))
        assertNull("a walkable death tile across a wall or disconnected from the killer is not enough", DeadmanChestWorld.killSpot(world, killer, tile))
    }

    @Test
    fun `world breach boss kills can spawn a chest without treating an ordinary field as a boss cave`() {
        val tile = freeTile(false)
        val killer = player("breachbosskill", beside(tile))
        val id = BreachMonsters.BY_ID.values.first { it.name.equals("General Graardor", true) }.id
        val boss = DeadmanBreach.spawnMonster(world, id, tile, 8).also { npcs += it }
        assertTrue(gg.rsmod.plugins.content.mechanics.pvp.BossNpcs.isBoss(boss))
        assertTrue("the generic teleport classifier includes dynamically spawned bosses", gg.rsmod.plugins.content.mechanics.pvp.BossAreas.isBossArea(world, tile))
        assertNotNull("an ordinary breach battlefield is not a fixed boss lair", DeadmanChestWorld.onNpcKilled(world, killer, boss) { 0 })
    }

    @Test
    fun `delayed damage landing interrupts a channel started after the projectile was launched`() {
        val tile = freeTile(false)
        val chest = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("pending hit", tile))!!
        val p = player("delayedhit", beside(tile))
        giveRisk(p)
        val hit = gg.rsmod.game.model.Hit.Builder().addHit(damage = 1, type = 0).setDamageDelay(1).build()
        hit.source = chest.guardian
        p.addHit(hit)
        DeadmanChestWorld.beginOpen(p, tile)
        p.hitsCycle()
        tickChest()
        assertEquals("a queued hit has not landed yet", 1, DeadmanChestWorld.channelElapsed(p))
        p.hitsCycle()
        assertEquals(98, p.getCurrentLifepoints())
        tickChest()
        assertNull("landing damage resets the new channel", DeadmanChestWorld.channelElapsed(p))
    }

    @Test
    fun `deep boss regions use their explicit reachable exit rather than an unrelated nearby timed spot`() {
        val root = com.google.gson.Gson().fromJson(File("../../data/cfg/deadman/chest_spots.json").readText(), com.google.gson.JsonObject::class.java)
        val def = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, BreachIds.BREACH_LOC)
        val oldWidth = def.width
        val oldLength = def.length
        def.width = 4
        def.length = 4
        try {
        for (entry in root["boss_exits"].asJsonArray) {
            val config = entry.asJsonObject
            val a = config["tile"].asJsonArray
            val entrance = Tile(a[0].asInt, a[1].asInt, a[2].asInt)
            for (region in config["regions"].asJsonArray.map { it.asInt }) {
                // A synthetic deep kill anywhere in the mapped region; the real map still validates the EXIT and its path.
                val death = Tile((region shr 8) * 64 + 32, (region and 255) * 64 + 32, 0)
                val boss = npcAt(gg.rsmod.plugins.api.cfg.Npcs.GENERAL_GRAARDOR, death)
                val killer = player("exit$region", death)
                val tile = DeadmanChestWorld.killSpot(world, killer, death)
                assertNotNull("${config["name"]}: region $region must map to an exit", tile)
                assertEquals(entrance.height, tile!!.height)
                assertTrue("the real 4x4 chest fits outside the lair near its source exit: $tile vs $entrance", tile.getDistance(entrance) <= 16)
                assertTrue("exit ground is usable and dangerous", DeadmanChestWorld.fits(world, tile))
                assertTrue("exit is connected to the source arrival tile", tile == entrance || tile in DeadmanChestWorld.reachable(world, entrance, 16))
                world.remove(boss)
                world.unregister(killer)
                clients.remove(killer)
            }
        }
        } finally {
            def.width = oldWidth
            def.length = oldLength
        }
    }

    @Test
    fun `timed and kill chests enforce spacing in either spawn order`() {
        val tile = freeTile(false)
        val killer = player("spacing", beside(tile))
        val id = BreachMonsters.BY_ID.filterValues { !it.summon }.keys.first { id ->
            val probe = DeadmanBreach.spawnMonster(world, id, tile, 8)
            val plain = !gg.rsmod.plugins.content.mechanics.pvp.BossNpcs.isBoss(probe) && probe.combatDef.lifepoints >= 300
            world.remove(probe)
            DeadmanBreach.live.remove(probe)
            plain
        }
        val npc = DeadmanBreach.spawnMonster(world, id, tile, 8).also { npcs += it }
        val timed = DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("first", tile))!!
        npc.tile = beside(tile)
        assertNull("kill spawn within 15 of timed chest", DeadmanChestWorld.onNpcKilled(world, killer, npc) { 0 })
        DeadmanChestWorld.remove(world, timed, "test")
        npc.tile = tile
        val kill = DeadmanChestWorld.onNpcKilled(world, killer, npc) { 0 }!!
        assertNull("timed spawn overlapping active kill chest", DeadmanChestWorld.spawnTimed(world, DeadmanChestWorld.Spot("second", tile)))
        assertEquals(listOf(kill), DeadmanChestWorld.chests.toList())
    }

    private fun tickChest() {
        cycle++
        DeadmanChestWorld.cycleOverride = cycle
        DeadmanChestWorld.tick(world)
    }

    private fun giveRisk(p: Client) {
        p.inventory.add(Items.COINS_995, 5_000_000)
    }

    private fun snapshotOf(p: Client): String =
        (p.inventory.filterNotNull() + p.bank.filterNotNull()).joinToString { "${it.id}x${it.amount}" } + "|follower=" + gg.rsmod.plugins.content.skills.summoning.Pets.hasFollower(p)

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<gg.rsmod.game.message.impl.MessageGameMessage>().map { it.message }

    private fun beside(t: Tile) = Tile(t.x + 1, t.z, t.height)

    /** A tile on the real map where a chest may stand (usable + dangerous); [multi] picks multi-combat or single-way ground. */
    private fun freeTile(multi: Boolean): Tile {
        for (x in 2990..3250) for (z in 3525..3720) {
            val t = Tile(x, z, 0)
            val free = DeadmanChestWorld.fits(world, t) && DeadmanChestWorld.fits(world, Tile(x + 1, z, 0)) && DeadmanChestWorld.fits(world, Tile(x + 1, z + 1, 0))
            if (free && !gg.rsmod.plugins.content.mechanics.pvp.BossAreas.isBossArea(world, t) && t.isMulti(world) == multi && DeadmanChestWorld.chests.none { it.tile.getDistance(t) < 20 }) return t
        }
        fail("no free ${if (multi) "multi" else "single"} wilderness tile found")
        error("unreachable")
    }

    private fun npcAt(
        id: Int,
        at: Tile,
    ): Npc {
        val npc = Npc(id, Tile(at), world)
        assertTrue(world.spawn(npc))
        npcs += npc
        return npc
    }

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        world.collision.isClipped(at)
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.uid = gg.rsmod.game.model.PlayerUID(name)
        client.tile = at
        val out = mutableListOf<gg.rsmod.game.message.Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<gg.rsmod.game.message.Message>() }
        client.gameSystem = system
        client.setCurrentLifepoints(99)
        written[client] = out
        assertTrue("registered $name", world.register(client))
        clients += client
        return client
    }

    companion object {
        /** Lumbridge: a guarded city, never a chest spot. */
        val SAFE_TILE = Tile(3222, 3218, 0)

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            every { ctx.home } returns Tile(3164, 3495, 0)
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            // The breach script reads NpcAttacks.DEFAULT_PATH relative to the working directory (the game root on the server, game\plugins
            // in a test run): stage the real file there for the run and remove it again afterwards.
            val staged = File(NpcAttacks.DEFAULT_PATH)
            if (!staged.exists()) {
                staged.parentFile.mkdirs()
                File("../../data/cfg/npcs/npc-attacks.json").copyTo(staged)
                Runtime.getRuntime().addShutdownHook(Thread { staged.delete() })
            }
            gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.load(File("../../data/cfg/areas/dmm-multicombat-zones.json"))
            // Same for the breach spots and the chest spots (both read from ./data/cfg/deadman relative to the working directory).
            for (name in listOf("breach-locations.json", "chest_spots.json")) {
                val stagedSpots = File("./data/cfg/deadman/$name")
                if (!stagedSpots.exists()) {
                    stagedSpots.parentFile.mkdirs()
                    File("../../data/cfg/deadman/$name").copyTo(stagedSpots)
                    Runtime.getRuntime().addShutdownHook(Thread { stagedSpots.delete() })
                }
            }
            bindScript(world, "gg.rsmod.plugins.content.mechanics.pvp.breach.Deadman_breach_plugin")
            // The stand-in has no options; the real clickable OSRS variant 60248 has Loot.
            world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, BreachIds.BREACH_LOC).options[0] = "Loot"
            // Legacy timing/rules cases use a one-tile stand-in; the separate real-size test covers ALL 4x4 tiles/perimeter.
            world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, BreachIds.BREACH_LOC).width = 1
            world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, BreachIds.BREACH_LOC).length = 1
            DeadmanChestIds.guardianNpc = gg.rsmod.plugins.api.cfg.Npcs.ZEMOUREGAL
            bindScript(world, "gg.rsmod.plugins.content.mechanics.pvp.chest.Deadman_chest_plugin")
            bindScript(world, "gg.rsmod.plugins.content.items.Teleport_items_plugin")
            bindScript(world, "gg.rsmod.plugins.content.items.jewellery.Ring_of_dueling_plugin")
            world
        }

        private fun bindScript(
            world: World,
            script: String,
        ) {
            DeadmanChestIds.closedLoc = BreachIds.BREACH_LOC
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), DeadmanChestWorldTests::class.java.classLoader)
            val compiled = File(classes, script.replace('.', '/') + ".class")
            if (!compiled.exists()) fail("${compiled.path} is missing: the scripts compile did not run first")
            loader.loadClass(script)
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
        }
    }
}
