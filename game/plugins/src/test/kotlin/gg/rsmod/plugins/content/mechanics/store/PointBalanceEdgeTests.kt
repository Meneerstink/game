package gg.rsmod.plugins.content.mechanics.store

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LOYALTY_POINTS
import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.attr.SLAYER_POINTS
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.DAILY_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.game.task.QueueHandlerTask
import gg.rsmod.game.task.WorldRemoveTask
import gg.rsmod.game.task.sequential.SequentialNpcCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask
import gg.rsmod.game.task.sequential.SequentialSynchronizationTask
import gg.rsmod.plugins.content.activity.pest_control.PEST_POINTS
import gg.rsmod.plugins.content.daily.DailyObjectives
import gg.rsmod.plugins.content.mechanics.pvp.BEST_KILLSTREAK_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.Killstreaks
import gg.rsmod.plugins.content.mechanics.pvp.PK_POINTS_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N106 (owner 2026-10-17, DMM-PAKKET section 6): Loyalty / Donator / Deadman Points - and every other persistent point balance of
 * the same kind (PK, Slayer, Breach, Pest Control, Penguin points) - never go negative and never wrap past 2,147,483,647: every
 * add stops at the maximum, every take is refused when the balance does not hold it.
 *
 * The owner's own account sits at 2,146,808,247 / 2,147,457,397 / 2,147,448,047 (after `maxpoints`); before this fix the next
 * +50 play-time Loyalty, +500 daily, +10 Deadman per PvP kill or an emblem cash-out wrapped his balance to about -2.147 billion
 * (a laptop save already shows loyalty_points -2,147,483,549).
 *
 * Donor: Void VariableStore.inc/dec (Donors/void/engine/.../client/variable/VariableStore.kt:24-34) - one inc(max) / dec(min)
 * pair for every counter; ours sums in Long first (Void's Int sum still wraps).
 *
 * Every writer is driven the way the game drives it: the real timers ticked by the real player cycle, the real admin commands
 * through [PluginRepository.executeCommand], the PvP kill reward, the emblem cash-out at Skully, the daily objective, the slayer
 * task completion. The last test is a source guard: no balance is written outside the one helper.
 */
class PointBalanceEdgeTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()
    private val written = HashMap<Client, MutableList<Message>>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        npcs.forEach { world.remove(it) }
        players.clear()
        npcs.clear()
    }

    // ------------------------------------------------------------------------------------------- the owner's case: Loyalty adds

    @Test
    fun `owner case - his Loyalty 2,147,457,397 after the real play-time rewards (526 x 50) stops at 2,147,483,647`() {
        // 2,147,483,647 - 2,147,457,397 = 26,250 = 525 rewards of 50; the 526th crossed the maximum and wrapped (old code).
        val player = player("n106owner")
        player.attr[LOYALTY] = OWNER_EDGE
        repeat(526) { assertTrue(repo.executeTimer(player, LOYALTY_PLAYTIME_TIMER)) }
        assertEquals("Loyalty after 526 play-time rewards from the owner's balance", MAX, player.attr[LOYALTY])
        assertTrue("never negative on the way", (player.attr[LOYALTY] ?: 0) >= 0)
    }

    @Test
    fun `the 30-minute play-time Loyalty reward just under the maximum stops at 2,147,483,647`() {
        val player = player("n106time")
        player.attr[LOYALTY] = NEAR
        player.timers[LOYALTY_PLAYTIME_TIMER] = 1
        tick(3)
        assertTrue("the play-time timer fired: ${messages(player)}", messages(player).any { it.contains("for your time online") || it.contains("at the maximum") })
        assertEquals("Loyalty after +50 at MAX-7", MAX, player.attr[LOYALTY])
        // And again at the maximum: it stays there.
        player.timers[LOYALTY_PLAYTIME_TIMER] = 1
        tick(3)
        assertEquals("Loyalty after a second +50 at the maximum", MAX, player.attr[LOYALTY])
    }

    @Test
    fun `the daily 500 Loyalty reward at the edge stops at the maximum and the message reads the real balance`() {
        val player = player("n106daily")
        player.attr[LOYALTY] = NEAR
        player.timers[DAILY_TIMER] = 1
        tick(3)
        assertEquals("Loyalty after the daily +500: ${messages(player)}", MAX, player.attr[LOYALTY])
        assertTrue(messages(player).toString(), messages(player).any { it == "You now have a total of 2,147,483,647 loyalty points." })
    }

    @Test
    fun `the admin addloyalty command with Int MAX_VALUE on a player with points stops at the maximum`() {
        val admin = player("n106admin", admin = true)
        val target = player("n106target")
        target.attr[LOYALTY] = 100
        assertTrue(repo.executeCommand(admin, "addloyalty", arrayOf("n106target", "2147483647")))
        assertEquals("100 + 2,147,483,647 Loyalty", MAX, target.attr[LOYALTY])
    }

    @Test
    fun `a daily objective reward and every engine add (drill demon, slayer) stop at the maximum`() {
        val player = player("n106obj")
        player.attr[LOYALTY] = NEAR
        val npc = Npc(1, HOME, world)
        val objective = DailyObjectives.OBJECTIVES[0]
        repeat(objective.target) { DailyObjectives.onKill(player, npc) }
        assertTrue("the objective completed: ${messages(player)}", messages(player).any { it.startsWith("Daily objective complete") })
        assertEquals("Loyalty after the +${objective.reward} objective reward", MAX, player.attr[LOYALTY])

        player.attr[LOYALTY] = NEAR
        player.addLoyalty(30) // drill demon random event reward
        assertEquals("Player.addLoyalty at the edge", MAX, player.attr[LOYALTY])

        player.attr[SLAYER_POINTS] = NEAR
        player.addSlayerPoints(100) // slayer task completion (SlayerExt.handleDecrease)
        assertEquals("Player.addSlayerPoints at the edge", MAX, player.attr[SLAYER_POINTS])
    }

    @Test
    fun `the admin addslayerpoints command with Int MAX_VALUE stops at the maximum`() {
        val admin = player("n106admin2", admin = true)
        val target = player("n106slay")
        target.attr[SLAYER_POINTS] = 5
        assertTrue(repo.executeCommand(admin, "addslayerpoints", arrayOf("n106slay", "2147483647")))
        assertEquals(MAX, target.attr[SLAYER_POINTS])
    }

    // ------------------------------------------------------------------------------------------------ Deadman / PK adds

    @Test
    fun `owner case - a valid PvP kill at the edge pays Deadman and PK points up to the maximum, never negative`() {
        val killer = player("n106killer")
        val victim = player("n106victim")
        killer.attr[DEADMAN] = OWNER_EDGE_DEADMAN
        killer.attr[PK_POINTS_ATTR] = MAX - 1
        killer.attr[BEST_KILLSTREAK_ATTR] = 1_000 // no leaderboard write from the test
        Killstreaks.onJudgedKill(killer, victim, ValidPkKill.Verdict.VALID)
        assertTrue("the kill was paid: ${messages(killer)}", messages(killer).any { it.contains("Deadman Points") })
        assertEquals("Deadman Points after +${Killstreaks.DEADMAN_POINTS_PER_KILL} at 2,147,483,640", MAX, killer.attr[DEADMAN])
        assertEquals("PK points after a kill at MAX-1", MAX, killer.attr[PK_POINTS_ATTR])
    }

    @Test
    fun `owner case - cashing a tier 6 Deadman emblem in at Skully at the edge stops at the maximum`() {
        val player = player("n106emblem")
        player.attr[DEADMAN] = NEAR
        player.inventory.add(Item(DeadmanEmblem.emblemId(6), 1))
        assertTrue(DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.POINTS))
        assertEquals("Deadman Points after +${DeadmanEmblem.points(6)} emblem points", MAX, player.attr[DEADMAN])
    }

    @Test
    fun `givepoints for every 78 Store currency and Breach saturates at the maximum and floors at zero`() {
        val admin = player("n106give", admin = true)
        val target = player("n106getter")
        for (currency in StoreCatalogue.Currency.values()) {
            target.attr[currency.attr] = OWNER_EDGE
            assertTrue(repo.executeCommand(admin, "givepoints", arrayOf("n106getter", currency.name.lowercase(), "1000000")))
            assertEquals("${currency.name} +1,000,000 at the edge", MAX, target.attr[currency.attr])
            assertTrue(repo.executeCommand(admin, "givepoints", arrayOf("n106getter", currency.name.lowercase(), "-2147483647")))
            assertTrue(repo.executeCommand(admin, "givepoints", arrayOf("n106getter", currency.name.lowercase(), "-5")))
            assertEquals("${currency.name} taken below zero", 0, target.attr[currency.attr])
        }
        target.attr[BreachPoints.BALANCE] = OWNER_EDGE
        assertTrue(repo.executeCommand(admin, "givepoints", arrayOf("n106getter", "breach", "1000000")))
        assertEquals("Breach +1,000,000 at the edge", MAX, target.attr[BreachPoints.BALANCE])
    }

    // ------------------------------------------------------------------------------------------------------------ takes

    @Test
    fun `a take larger than the balance is refused - the balance never goes below zero`() {
        val player = player("n106take")
        player.attr[LOYALTY] = 100
        player.subtractLoyalty(MAX)
        assertEquals("Loyalty 100 - 2,147,483,647 is refused", 100, player.attr[LOYALTY])
        player.subtractLoyalty(100)
        assertEquals("Loyalty 100 - 100", 0, player.attr[LOYALTY])
        player.attr[SLAYER_POINTS] = 30
        player.subtractSlayerPoints(400)
        assertEquals("Slayer 30 - 400 is refused", 30, player.attr[SLAYER_POINTS])
        player.subtractSlayerPoints(30)
        assertEquals("Slayer 30 - 30", 0, player.attr[SLAYER_POINTS])
        // A balance that is negative in memory (it can only come from a save that wrapped before N106; the login repair below
        // fixes those saves): no take succeeds on it and no add leaves it negative.
        player.attr[LOYALTY] = -2_147_483_549
        assertTrue("a wrapped balance cannot pay", !player.subtractLoyalty(1))
        assertEquals("a negative balance + 50 is never negative", 50, player.addLoyalty(50))
    }

    // ------------------------------------------------------------------------------- one-time repair of wrapped saves at login

    @Test
    fun `login repair - a save whose balance wrapped before N106 (sous, -2,147,483,549) becomes 2,147,483,647 once, and so does every other negative`() {
        withSerializer { serializer, dir ->
            val before = player("n106wrapped")
            before.currentXteaKeys = intArrayOf(1, 2, 3, 4)
            before.passwordHash = "x"
            before.attr[LOYALTY] = -2_147_483_549 // the laptop save "sous": MAX + 100 wrapped
            before.attr[DEADMAN] = -2_000_000_001
            before.attr[StoreCatalogue.Currency.DONATOR.attr] = -1_999_999_999 // old build: maxpoints then addloyalty 147483650
            before.attr[SLAYER_POINTS] = -5 // old build: MAX + 2147483643
            before.attr[PK_POINTS_ATTR] = Int.MIN_VALUE
            before.attr[BreachPoints.BALANCE] = 100 // healthy: untouched
            assertTrue("saved", serializer.saveClientData(before))
            val json = dir.resolve("n106wrapped").toFile().readText()
            assertTrue("the wrapped value is really in the save: $json", json.contains("\"loyalty_points\": -2147483549"))
            world.unregister(before)
            players.remove(before)

            val after = load(serializer, "n106wrapped")
            assertEquals("Loyalty wrapped -> maximum", MAX, after.attr[LOYALTY])
            assertEquals("Deadman wrapped -> maximum", MAX, after.attr[DEADMAN])
            assertEquals("Donator -1,999,999,999 (a wrap by an admin add) -> maximum", MAX, after.attr[StoreCatalogue.Currency.DONATOR.attr])
            assertEquals("Slayer -5 (a wrap by MAX + 2147483643) -> maximum", MAX, after.attr[SLAYER_POINTS])
            assertEquals("PK Int.MIN_VALUE -> maximum", MAX, after.attr[PK_POINTS_ATTR])
            assertEquals("a healthy Breach balance is untouched", 100, after.attr[BreachPoints.BALANCE])
            assertEquals("a currency the account never had stays absent", null, after.attr[PEST_POINTS])
            assertEquals("the first +50 play-time reward after the repair stops at the maximum", MAX, after.addLoyalty(50))
            assertEquals("the repair marker is saved on the account", 1, after.attr[PointBalance.REPAIR_MARKER])

            // Second login: the marker is in the save, nothing is repaired again and a real balance is kept.
            after.attr[LOYALTY] = 123
            assertTrue("saved again", serializer.saveClientData(after))
            world.unregister(after)
            players.remove(after)
            val again = load(serializer, "n106wrapped")
            assertEquals("a repaired account keeps its balance on the next login", 123, again.attr[LOYALTY])
            assertEquals("the repair is idempotent", emptyList<PointBalance.Repair>(), PointBalance.repairWrapped(again.attr))
        }
    }

    @Test
    fun `login repair - the repair runs before any login hook, so the first reward after login on a wrapped save is the maximum`() {
        withSerializer { serializer, _ ->
            val before = player("n106hook")
            before.currentXteaKeys = intArrayOf(1, 2, 3, 4)
            before.passwordHash = "x"
            before.attr[LOYALTY] = -2_147_483_549
            assertTrue("saved", serializer.saveClientData(before))
            world.unregister(before)
            players.remove(before)
            val after = load(serializer, "n106hook")
            // The daily reward (+500) is the first thing that touches the balance when the day rolls over at login.
            after.timers[DAILY_TIMER] = 1
            tick(2)
            assertEquals("daily +500 on a repaired wrapped save", MAX, after.attr[LOYALTY])
            assertTrue("the chat line reads the real balance: ${messages(after)}", messages(after).any { it.contains("total of 2,147,483,647 loyalty points") })
        }
    }

    @Test
    fun `login repair - every balance key used in the sources is in the repair list`() {
        val keys =
            StoreCatalogue.Currency.values().map { it.attr } +
                listOf(LOYALTY, SLAYER_POINTS, PK_POINTS_ATTR, BreachPoints.BALANCE, PEST_POINTS, gg.rsmod.plugins.content.activity.penguin_hide_and_seek.PenguinPoints)
        keys.forEach { assertTrue("${it.persistenceKey} is repaired at login", it.persistenceKey in PointBalance.PERSISTENCE_KEYS) }
        assertEquals("no stray key in the list", keys.map { it.persistenceKey }.toSet(), PointBalance.PERSISTENCE_KEYS.toSet())
    }

    @Test
    fun `login repair - every negative size is repaired, there is no threshold (a wrap lands on any negative value)`() {
        val attrs = gg.rsmod.game.model.attr.AttributeMap()
        attrs[LOYALTY] = -1
        attrs[DEADMAN] = -1_999_999_999
        attrs[SLAYER_POINTS] = -2_000_000_000
        attrs[PK_POINTS_ATTR] = Int.MIN_VALUE
        val repairs = PointBalance.repairWrapped(attrs).associate { it.key to it.new }
        assertEquals(setOf("loyalty_points", "deadman_points", "slayer_points", "pk_points"), repairs.keys)
        repairs.values.forEach { assertEquals(MAX, it) }
        assertEquals(MAX, attrs[LOYALTY])
        assertEquals(MAX, attrs[DEADMAN])
    }

    @Test
    fun `login repair - the admin wrap from the dossier maxpoints then addloyalty 147483650 (-1,999,999,999) is restored to the maximum`() {
        val attrs = gg.rsmod.game.model.attr.AttributeMap()
        attrs[LOYALTY] = (MAX.toLong() + 147_483_650L).toInt() // what the old build stored
        assertEquals(-1_999_999_999, attrs[LOYALTY])
        PointBalance.repairWrapped(attrs)
        assertEquals(MAX, attrs[LOYALTY])
    }

    @Test
    fun `a capped play-time reward says the Loyalty Points are at the maximum instead of the full amount`() {
        val player = player("n106cap")
        player.attr[LOYALTY] = MAX
        player.timers[LOYALTY_PLAYTIME_TIMER] = 1
        tick(2)
        assertEquals(MAX, player.attr[LOYALTY])
        assertTrue("at the maximum: ${messages(player)}", messages(player).any { it.contains("at the maximum") })
        assertTrue("not 'awarded 50': ${messages(player)}", messages(player).none { it.contains("awarded 50 Loyalty") })
    }

    @Test
    fun `a 78 Store purchase at the maximum balance debits exactly the price`() {
        val player = player("n106store")
        val entry = StoreCatalogue.ENTRIES.first { it.currency == StoreCatalogue.Currency.LOYALTY && it.kind == StoreCatalogue.Kind.ITEM }
        player.attr[LOYALTY] = MAX
        StoreUi.open(player, entry.shop)
        val index = StoreUi.entries(player).indexOf(entry)
        assertTrue("entry listed", index >= 0)
        StoreUi.turnPage(player, index / StoreUi.slotCount(player))
        StoreUi.select(player, index % StoreUi.slotCount(player))
        StoreUi.buy(player)
        StoreUi.buy(player)
        assertEquals("one item ${entry.purchaseItem} bought: ${messages(player)}", (MAX.toLong() - entry.price).toInt(), player.attr[LOYALTY])
    }

    // ------------------------------------------------------------------------------------------------------------- relog

    @Test
    fun `relog - every balance at the maximum is saved and loaded as 2,147,483,647 and an add after relog still stops there`() {
        val keys = StoreCatalogue.Currency.values().map { it.attr } + listOf(BreachPoints.BALANCE, PK_POINTS_ATTR, SLAYER_POINTS)
        val dir = java.nio.file.Files.createTempDirectory("n106-saves")
        try {
            val serializer = gg.rsmod.game.service.serializer.json.JsonPlayerSerializer()
            serializer.initSerializer(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to dir.toString())))
            val before = player("n106relog")
            before.currentXteaKeys = intArrayOf(1, 2, 3, 4)
            before.passwordHash = "x"
            keys.forEach { before.attr[it] = MAX }
            assertTrue("saved", serializer.saveClientData(before))
            val json = dir.resolve("n106relog").toFile().readText()
            keys.forEach { assertTrue("${it.persistenceKey} written as 2147483647", json.contains("\"${it.persistenceKey}\": 2147483647")) }
            world.unregister(before)
            players.remove(before)

            val after = Client(EmbeddedChannel(), world)
            after.loginUsername = "n106relog"
            val request =
                gg.rsmod.net.codec.login.LoginRequest(
                    mockk(relaxed = true), "n106relog", "", 667, intArrayOf(1, 2, 3, 4), false, 0, "", 765, 503, reconnecting = true,
                )
            assertEquals(gg.rsmod.game.service.serializer.PlayerLoadResult.LOAD_ACCOUNT, serializer.loadClientData(after, request))
            keys.forEach { assertEquals("${it.persistenceKey} after relog", MAX, after.attr[it]) }
            assertEquals("+50 play-time Loyalty after relog", MAX, after.addLoyalty(50))
            assertEquals("+10 Deadman after relog", MAX, after.attr.let { gg.rsmod.game.model.attr.PointBalance.add(it, DEADMAN, 10) })
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ------------------------------------------------------------------------------------------------------- source guard

    /**
     * The class rule (ROOT_CAUSES #4): a point balance is never written with its own arithmetic. Every write of a balance key
     * (the three 78 Store currencies through `<currency>.attr`, LOYALTY_POINTS, SLAYER_POINTS, PK_POINTS_ATTR, Breach BALANCE,
     * PEST_POINTS, PenguinPoints, PointCurrency's balanceAttr) in the engine and the plugins goes through
     * gg.rsmod.game.model.attr.PointBalance.
     */
    @Test
    fun `source guard - no point balance is assigned outside PointBalance`() {
        val roots = listOf(File("src/main/kotlin"), File("../src/main/kotlin"))
        roots.forEach { assertTrue("source root ${it.absolutePath}", it.isDirectory) }
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name != "PointBalance.kt" }.forEach { file ->
                val lines = file.readLines().map { it.substringBefore("//") }
                // A local alias of a balance key (`val deadman = Currency.DEADMAN.attr`) is a balance key too.
                val aliases = lines.mapNotNull { ALIAS.find(it)?.takeIf { m -> BALANCE_KEY.matches(m.groupValues[2].trim()) }?.groupValues?.get(1) }.toSet()
                fun isBalance(key: String) = BALANCE_KEY.matches(key.trim()) || key.trim() in aliases
                lines.forEachIndexed { i, code ->
                    (ASSIGN.findAll(code) + PUT.findAll(code)).forEach { m ->
                        if (isBalance(m.groupValues[1])) offenders += "${file.path}:${i + 1}: ${code.trim()}"
                    }
                }
            }
        }
        assertTrue("point balances written outside PointBalance (${offenders.size}):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    // ------------------------------------------------------------------------------------------------------------ helpers

    private fun player(
        name: String,
        admin: Boolean = false,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = HOME
        client.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION, 99)
        client.setCurrentLifepoints(99)
        if (admin) client.privilege = Privilege(2, 2, "admin", setOf(Privilege.ADMIN_POWER, Privilege.MOD_POWER, Privilege.DEV_POWER))
        val out = mutableListOf<Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        written[client] = out
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    private fun withSerializer(block: (gg.rsmod.game.service.serializer.json.JsonPlayerSerializer, java.nio.file.Path) -> Unit) {
        val dir = java.nio.file.Files.createTempDirectory("n106-saves")
        try {
            val serializer = gg.rsmod.game.service.serializer.json.JsonPlayerSerializer()
            serializer.initSerializer(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to dir.toString())))
            block(serializer, dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun load(
        serializer: gg.rsmod.game.service.serializer.json.JsonPlayerSerializer,
        name: String,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        val request =
            gg.rsmod.net.codec.login.LoginRequest(
                mockk(relaxed = true), name, "", 667, intArrayOf(1, 2, 3, 4), false, 0, "", 765, 503, reconnecting = true,
            )
        assertEquals(gg.rsmod.game.service.serializer.PlayerLoadResult.LOAD_ACCOUNT, serializer.loadClientData(client, request))
        client.currentXteaKeys = intArrayOf(1, 2, 3, 4)
        val out = mutableListOf<Message>()
        written[client] = out
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        const val MAX = Int.MAX_VALUE

        /** The owner's Donator Points (DMM-PAKKET section 6), used as the edge for every balance. */
        const val OWNER_EDGE = 2_147_457_397

        /** 7 under the maximum: one kill (+10) crosses it. */
        const val OWNER_EDGE_DEADMAN = 2_147_483_640

        /** 7 under the maximum: every reward crosses it. */
        const val NEAR = MAX - 7

        val HOME = Tile(3158, 3497)
        val LOYALTY = StoreCatalogue.Currency.LOYALTY.attr
        val DEADMAN = StoreCatalogue.Currency.DEADMAN.attr

        /** store.plugin.kts: `TimerKey(persistenceKey = "loyalty_playtime", tickOffline = false)` (a data class: equal by value). */
        val LOYALTY_PLAYTIME_TIMER = TimerKey(persistenceKey = "loyalty_playtime", tickOffline = false)

        /** `<x>.attr[<key>] = ...` (not `==`). */
        private val ASSIGN = Regex("""attr\[([^\[\]]+)\]\s*=(?!=)""")

        /** `attr.put(<key>, ...)`. */
        private val PUT = Regex("""attr\.put\(\s*([^,()]+),""")

        /** `val <name> = <key>` - a local alias of a key. */
        private val ALIAS = Regex("""\bval\s+(\w+)\s*=\s*([\w!.]+)\s*$""")

        /** Every point balance key as it is written in the sources (`<currency>.attr` needs its owner: `currency.attr`, `it.attr`). */
        private val BALANCE_KEY =
            Regex(
                """(?:[\w.]*\.)?(LOYALTY_POINTS|SLAYER_POINTS|PK_POINTS_ATTR|BALANCE|PEST_POINTS|PenguinPoints|balanceAttr)|[\w!.]+\.attr""",
            )

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.mechanics.store.Store_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/store.plugin.kts",
                "gg.rsmod.plugins.content.daily.Daily_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/daily/daily.plugin.kts",
                "gg.rsmod.plugins.content.cmd.Commands_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/cmd/commands.plugin.kts",
            )

        private val service = mockk<GameService>(relaxed = true)

        private val tasks =
            listOf(
                QueueHandlerTask(),
                SequentialPlayerCycleTask(),
                WorldRemoveTask(),
                SequentialNpcCycleTask(),
                SequentialSynchronizationTask(),
                SequentialPlayerPostCycleTask(),
            )

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            every { ctx.home } returns Tile(3222, 3218)
            every { ctx.cycleTime } returns 600
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            // The relog test loads a save, which looks the privilege up (Server.kt loads them from game.yml).
            world.privileges.load(
                ServerProperties().loadMap(
                    mapOf("privileges" to arrayListOf(linkedMapOf("id" to 0, "name" to "player", "powers" to listOf<String>()))),
                ),
            )
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
            bindScripts(world)
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }

        private val repo: PluginRepository by lazy { world.plugins }

        private fun bindScripts(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), PointBalanceEdgeTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
        }
    }
}
