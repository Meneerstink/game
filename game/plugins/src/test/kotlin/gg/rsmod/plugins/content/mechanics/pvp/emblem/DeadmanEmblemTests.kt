package gg.rsmod.plugins.content.mechanics.pvp.emblem

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.mechanics.death.DeathContainerSource
import gg.rsmod.plugins.content.mechanics.death.DeathContext
import gg.rsmod.plugins.content.mechanics.death.DeathExecutor
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskResult
import gg.rsmod.plugins.content.mechanics.death.DeathResolutionResult
import gg.rsmod.plugins.content.mechanics.death.DeathSlotItem
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider
import gg.rsmod.plugins.content.mechanics.pvp.BEST_KILLSTREAK_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill.Verdict
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Deadman emblems (owner 2026-09-25): tier table, PvM drop maths, the central valid-PK rules, and the PvP death transfer
 * through the real [DeathExecutor] (removal, upgrade, exact-tier transfer, one-emblem conflicts, no dupes).
 */
class DeadmanEmblemTests {
    // ---------------------------------------------------------------- tiers and values

    @Test
    fun `six tiers with their own items and strongly rising values just under 2x`() {
        assertEquals(6, DeadmanEmblem.EMBLEM_IDS.distinct().size)
        for (tier in 1..6) {
            assertEquals(tier, DeadmanEmblem.tierOf(DeadmanEmblem.emblemId(tier)))
            assertTrue(DeadmanEmblem.isLamp(DeadmanEmblem.lampId(tier)))
            assertFalse(DeadmanEmblem.isEmblem(DeadmanEmblem.lampId(tier)))
        }
        for (tier in 2..6) {
            val points = DeadmanEmblem.points(tier).toDouble() / DeadmanEmblem.points(tier - 1)
            val xp = DeadmanEmblem.lampXp(tier).toDouble() / DeadmanEmblem.lampXp(tier - 1)
            assertTrue(points in 1.7..1.99, "tier $tier points ratio $points")
            assertTrue(xp in 1.7..1.99, "tier $tier xp ratio $xp")
        }
    }

    @Test
    fun `every Deadman lamp is an experience lamp worth its tier`() {
        for (tier in 1..6) {
            val reward = gg.rsmod.plugins.content.items.lamps.ExperienceLamps.REWARDS[DeadmanEmblem.lampId(tier)]
            assertTrue(reward is gg.rsmod.plugins.content.items.lamps.ExperienceLamps.Reward.Fixed)
            assertEquals(DeadmanEmblem.lampXp(tier), reward.xp)
        }
    }

    // ---------------------------------------------------------------- PvM

    @Test
    fun `only 150 plus HP npcs can drop and the Wilderness adds 10 percent`() {
        assertEquals(0.0, DeadmanEmblem.dropChance(1_490, wilderness = true))
        val base = DeadmanEmblem.dropChance(1_500, wilderness = false)
        assertEquals(150.0 / 40_000, base, 1e-12)
        assertEquals(base * 1.10, DeadmanEmblem.dropChance(1_500, wilderness = true), 1e-12)
        assertEquals(DeadmanEmblem.MAX_CHANCE, DeadmanEmblem.dropChance(100_000, wilderness = false), 1e-12)
        // About one emblem per 3-4 hours: at ~3 HP of damage per second a player kills ~40k HP in 3.7 hours.
        val hours = DeadmanEmblem.HITPOINTS_PER_EMBLEM / 3.0 / 3600
        assertTrue(hours in 3.0..4.0, "$hours h")
    }

    // ---------------------------------------------------------------- valid PK rules

    @Test
    fun `valid-kill rules are checked in order - abuse first, then repeat victim, then risk`() {
        assertEquals(Verdict.NO_KILLER, ValidPkKill.judge(false, false, false, false, false, 5_000_000))
        assertEquals(Verdict.SELF, ValidPkKill.judge(true, true, false, false, false, 5_000_000))
        assertEquals(Verdict.STAFF, ValidPkKill.judge(true, false, true, true, true, 0))
        assertEquals(Verdict.SAME_ADDRESS, ValidPkKill.judge(true, false, false, true, true, 0))
        assertEquals(Verdict.REPEAT_VICTIM, ValidPkKill.judge(true, false, false, false, true, 5_000_000))
        assertEquals(Verdict.LOW_RISK, ValidPkKill.judge(true, false, false, false, false, 999_999))
        assertEquals(Verdict.VALID, ValidPkKill.judge(true, false, false, false, false, 1_000_000))
        assertTrue(Verdict.SAME_ADDRESS.abuse && Verdict.STAFF.abuse && !Verdict.LOW_RISK.abuse && !Verdict.REPEAT_VICTIM.abuse)
        assertEquals(ValidPkKill.pairKey("Bob", "alice"), ValidPkKill.pairKey("Alice", "bob"), "the cooldown is per pair, either direction")
        // Audit D-07: the daily cap and the fed-kill rule come after the cooldowns; neither is abuse.
        assertEquals(Verdict.DAILY_CAP, ValidPkKill.judge(true, false, false, false, false, 5_000_000, dailyCapReached = true, fedKill = true))
        assertEquals(Verdict.LOW_RISK, ValidPkKill.judge(true, false, false, false, false, 999_999, fedKill = true))
        assertEquals(Verdict.FED_KILL, ValidPkKill.judge(true, false, false, false, false, 1_000_000, fedKill = true))
        assertFalse(Verdict.DAILY_CAP.abuse || Verdict.FED_KILL.abuse || Verdict.DAILY_CAP.valid || Verdict.FED_KILL.valid)
    }

    @Test
    fun `Audit D-07 - the same machine id is the same person whatever the IP, loopback included`() {
        val a = client("uuid_a", "ABC-123", "10.0.0.1")
        val b = client("uuid_b", "ABC-123", "10.0.0.2")
        assertTrue(ValidPkKill.sameAddress(a, b), "same machine, different IP")
        val c = client("uuid_c", "ABC-123", "127.0.0.1")
        val d = client("uuid_d", "ABC-123", "127.0.0.1")
        assertTrue(ValidPkKill.sameAddress(c, d), "loopback with the same machine id is the same person")
        val e = client("uuid_e", "", "127.0.0.1")
        val f = client("uuid_f", "", "127.0.0.1")
        assertFalse(ValidPkKill.sameAddress(e, f), "loopback without a machine id stays the owner's local two-client test")
        val g = client("uuid_g", "", "10.0.0.9")
        val h = client("uuid_h", "", "10.0.0.9")
        assertTrue(ValidPkKill.sameAddress(g, h), "same remote IP")
        assertFalse(ValidPkKill.sameAddress(a, g), "a blank machine id never matches")
    }

    @Test
    fun `Audit D-07 - one victim counts once per hour whoever the killer is`() {
        val (world, first, victim) = fight("rv_killer1", "rv_victim")
        victim.inventory[0] = Item(COINS, 2_000_000)
        assertEquals(Verdict.VALID, DeadmanEmblem.onPvpDeath(world, victim, first, emptyList(), 2_000_000).verdict)
        val second = newPlayer("rv_killer2", world)
        hitBack(second, victim)
        assertEquals(Verdict.REPEAT_VICTIM, ValidPkKill.evaluate(second, victim, 2_000_000).verdict)
    }

    @Test
    fun `Audit D-07 - a fed kill does not count and a hit long ago is not fighting back`() {
        val (_, killer, victim) = fight("fed_killer", "fed_victim")
        every { killer.damageMap } returns gg.rsmod.game.model.combat.DamageMap()
        assertEquals(Verdict.FED_KILL, ValidPkKill.evaluate(killer, victim, 5_000_000).verdict)
        hitBack(killer, victim)
        val later = System.currentTimeMillis() + ValidPkKill.FED_KILL_WINDOW_MS + 1
        assertEquals(Verdict.FED_KILL, ValidPkKill.evaluate(killer, victim, 5_000_000, later).verdict)
        assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 5_000_000).verdict)
    }

    @Test
    fun `Audit D-07 - value the killer handed the victim is not the victim's risk`() {
        val (_, killer, victim) = fight("gift_killer", "gift_victim")
        ValidPkKill.noteGift("GIFT_KILLER", "gift_victim", 1_500_000)
        assertEquals(Verdict.LOW_RISK, ValidPkKill.evaluate(killer, victim, 2_000_000).verdict, "2M risked, 1.5M of it from the killer")
        assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 2_600_000).verdict)
        val tomorrow = System.currentTimeMillis() + ValidPkKill.GIFT_WINDOW_MS + 1
        assertEquals(0L, ValidPkKill.giftedRecently("gift_killer", "gift_victim", tomorrow))
    }

    @Test
    fun `Audit D-07 - a killer scores at most the daily cap and the cooldowns are written to disk`() {
        val world = fight("cap_killer", "cap_victim_0").world
        val killer = newPlayer("cap_killer", world)
        repeat(ValidPkKill.DAILY_VALID_KILL_CAP) { i ->
            val victim = newPlayer("cap_victim_$i", world)
            hitBack(killer, victim)
            assertEquals(Verdict.VALID, ValidPkKill.evaluate(killer, victim, 2_000_000).verdict, "kill ${i + 1}")
            ValidPkKill.record(killer, victim)
        }
        val one = newPlayer("cap_victim_extra", world)
        hitBack(killer, one)
        assertEquals(Verdict.DAILY_CAP, ValidPkKill.evaluate(killer, one, 2_000_000).verdict)
        val state = java.io.File(ValidPkKill.dataDir, "deadman_emblem_pairs.txt").readText()
        assertTrue("victim:cap_victim_0=" in state, "the per-victim cooldown survives a restart")
        val daily = java.io.File(ValidPkKill.dataDir, "deadman_pk_daily.txt").readText()
        assertTrue(daily.startsWith("cap_killer=") || "\ncap_killer=" in daily, "the daily count survives a restart")
    }

    @Test
    fun `Audit D-08 and D-06 - points and the kill grace only follow a valid kill`() {
        val (world, killer, victim) = fight("pts_killer", "pts_victim")
        assertEquals(Verdict.LOW_RISK, DeadmanEmblem.onPvpDeath(world, victim, killer, emptyList(), 0).verdict)
        assertEquals(null, killer.attr[StoreCatalogue.Currency.DEADMAN.attr], "a naked victim pays no Deadman Points")
        assertEquals(null, killer.attr[gg.rsmod.plugins.content.mechanics.pvp.CURRENT_KILLSTREAK_ATTR], "nor raises the streak")
        assertFalse(gg.rsmod.plugins.content.mechanics.pvp.KillGrace.isProtected(killer), "nor earns the grace")

        val (world2, killer2, victim2) = fight("pts_killer2", "pts_victim2")
        assertEquals(Verdict.VALID, DeadmanEmblem.onPvpDeath(world2, victim2, killer2, emptyList(), 1_000_000).verdict)
        assertEquals(KILL_POINTS, killer2.attr[StoreCatalogue.Currency.DEADMAN.attr])
        assertEquals(1, killer2.attr[gg.rsmod.plugins.content.mechanics.pvp.CURRENT_KILLSTREAK_ATTR])
        assertTrue(gg.rsmod.plugins.content.mechanics.pvp.KillGrace.isProtected(killer2))
    }

    @Test
    fun `protect item and an unskulled death never keep the emblem`() {
        val emblem = DeadmanEmblem.emblemId(4)
        val risk =
            DeathItemRiskCalculator.calculate(
                inventory = arrayOf(Item(emblem, 1), Item(4151, 1)),
                equipment = arrayOfNulls(14),
                itemProtectionActive = true,
                valueProvider = ItemRiskValueProvider { if (it == emblem) Long.MAX_VALUE else 1L },
                alwaysLost = { DeadmanEmblem.isEmblem(it) },
            )
        assertTrue(risk.lost.any { it.item.id == emblem })
        assertTrue(risk.protected.none { it.item.id == emblem })
    }

    // ---------------------------------------------------------------- PvP death transfer

    @Test
    fun `valid kill upgrades the carried emblem and takes the victim's exact tier`() {
        val (world, killer, victim) = fight("up_killer", "up_victim")
        killer.inventory[3] = Item(DeadmanEmblem.emblemId(2), 1)
        victim.inventory[0] = Item(DeadmanEmblem.emblemId(5), 1)
        victim.inventory[1] = Item(COINS, 1_000_000)

        die(world, victim, killer)

        assertEquals(0, DeadmanEmblem.holdings(victim).size, "the victim always loses it")
        // Killer: own T2 -> T3 (valid kill), then the victim's T5 arrives: keep T5, T3 is cashed in.
        assertEquals(listOf(5), DeadmanEmblem.holdings(killer).map { it.tier })
        assertEquals(DeadmanEmblem.points(3) + KILL_POINTS, killer.attr[StoreCatalogue.Currency.DEADMAN.attr])
        assertEquals(DeadmanEmblem.emblemId(5), killer.inventory[3]?.id, "the better emblem takes the old one's slot")
    }

    @Test
    fun `a low-risk kill still hands over the exact tier but upgrades nothing`() {
        val (world, killer, victim) = fight("low_killer", "low_victim")
        killer.inventory[0] = Item(DeadmanEmblem.emblemId(1), 1)
        victim.inventory[0] = Item(DeadmanEmblem.emblemId(1), 1)
        victim.inventory[1] = Item(COINS, 999_999)

        die(world, victim, killer)

        // Own T1 stays T1 (no upgrade); the incoming T1 is equal, so it is cashed in.
        assertEquals(listOf(1), DeadmanEmblem.holdings(killer).map { it.tier })
        // Audit D-08 (deliberate change): a low-risk kill no longer pays the killstreak's Deadman Points.
        assertEquals(DeadmanEmblem.points(1), killer.attr[StoreCatalogue.Currency.DEADMAN.attr])
    }

    @Test
    fun `the same pair scores once per hour in either direction`() {
        val (world, a, b) = fight("pair_a", "pair_b")
        a.inventory[0] = Item(DeadmanEmblem.emblemId(3), 1)
        b.inventory[0] = Item(COINS, 2_000_000)
        die(world, b, a)
        assertEquals(listOf(4), DeadmanEmblem.holdings(a).map { it.tier })

        val (world2, b2, a2) = fight("pair_b", "pair_a")
        b2.inventory[0] = Item(DeadmanEmblem.emblemId(1), 1)
        a2.inventory[0] = Item(COINS, 2_000_000)
        die(world2, a2, b2)
        assertEquals(listOf(1), DeadmanEmblem.holdings(b2).map { it.tier }, "revenge kill inside the window: no upgrade")
    }

    @Test
    fun `tier 6 cannot grow and a banked emblem does not upgrade`() {
        val (world, killer, victim) = fight("max_killer", "max_victim")
        killer.bank[0] = Item(DeadmanEmblem.emblemId(6), 1)
        victim.inventory[0] = Item(COINS, 3_000_000)
        die(world, victim, killer)
        assertEquals(listOf(6), DeadmanEmblem.holdings(killer).map { it.tier })
        assertTrue(DeadmanEmblem.holdings(killer).single().inBank)
    }

    @Test
    fun `a full inventory sends a won emblem to the bank, never the floor`() {
        val (world, killer, victim) = fight("full_killer", "full_victim")
        for (slot in 0 until killer.inventory.capacity) killer.inventory[slot] = Item(4151, 1)
        victim.inventory[0] = Item(DeadmanEmblem.emblemId(4), 1)
        die(world, victim, killer)
        val held = DeadmanEmblem.holdings(killer).single()
        assertEquals(4, held.tier)
        assertTrue(held.inBank)
    }

    @Test
    fun `an offline killer gets the emblem on his next login and a repeated death execution moves nothing twice`() {
        val (world, killer, victim) = fight("off_killer", "off_victim")
        every { killer.isOnline } returns false
        victim.inventory[0] = Item(DeadmanEmblem.emblemId(2), 1)
        val result = die(world, victim, killer)
        assertFalse(DeathExecutor.execute(world, result))
        assertEquals(listOf(2), DeadmanEmblem.Ledger.pendingFor("off_killer"))
        assertEquals(0, DeadmanEmblem.holdings(killer).size)

        every { killer.isOnline } returns true
        DeadmanEmblem.deliverPending(killer)
        assertEquals(listOf(2), DeadmanEmblem.holdings(killer).map { it.tier })
        assertEquals(emptyList(), DeadmanEmblem.Ledger.pendingFor("off_killer"))
    }

    @Test
    fun `cash-out pays points or swaps the emblem for the tier's lamp in place`() {
        val player = newPlayer("cash")
        player.inventory[5] = Item(DeadmanEmblem.emblemId(4), 1)
        assertTrue(DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.LAMP))
        assertEquals(DeadmanEmblem.lampId(4), player.inventory[5]?.id)
        player.bank[0] = Item(DeadmanEmblem.emblemId(2), 1)
        assertTrue(DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.POINTS))
        assertEquals(DeadmanEmblem.points(2), player.attr[StoreCatalogue.Currency.DEADMAN.attr])
        assertEquals(0, DeadmanEmblem.holdings(player).size)
        assertFalse(DeadmanEmblem.cashOut(player, DeadmanEmblem.CashOut.POINTS))
    }

    @Test
    fun `receiving never leaves two emblems`() {
        val player = newPlayer("recv")
        player.bank[0] = Item(DeadmanEmblem.emblemId(3), 1)
        assertEquals(5, DeadmanEmblem.receive(player, 5, DeadmanEmblem.Source.ADMIN_CREATE))
        assertEquals(listOf(5), DeadmanEmblem.holdings(player).map { it.tier })
        assertFalse(DeadmanEmblem.holdings(player).single().inBank, "a better emblem lands in the inventory")
        assertEquals(5, DeadmanEmblem.receive(player, 2, DeadmanEmblem.Source.ADMIN_CREATE))
        assertEquals(1, DeadmanEmblem.holdings(player).size)
        assertEquals(DeadmanEmblem.points(3) + DeadmanEmblem.points(2), player.attr[StoreCatalogue.Currency.DEADMAN.attr])
    }

    // ---------------------------------------------------------------- fixtures

    private data class Fight(val world: World, val killer: Player, val victim: Player)

    private fun fight(killerName: String, victimName: String): Fight {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns emptySet()
        val killer = newPlayer(killerName, world)
        val victim = newPlayer(victimName, world)
        killer.attr[BEST_KILLSTREAK_ATTR] = 99
        killer.attr[LootKeys.ENABLED] = false
        // Audit D-07: a kill only counts when the victim fought back (no fed kills).
        hitBack(killer, victim)
        return Fight(world, killer, victim)
    }

    /** [victim] lands a real hit on [killer] now (the killer's damage map, as combat records it). */
    private fun hitBack(killer: Player, victim: Player) {
        val map = gg.rsmod.game.model.combat.DamageMap()
        map.add(victim, 5)
        every { killer.damageMap } returns map
    }

    /** A connected client with machine id [uuid] from [ip]. */
    private fun client(name: String, uuid: String, ip: String): gg.rsmod.game.model.entity.Client {
        val client = mockk<gg.rsmod.game.model.entity.Client>(relaxed = true)
        val channel = mockk<io.netty.channel.Channel>(relaxed = true)
        every { channel.remoteAddress() } returns java.net.InetSocketAddress(java.net.InetAddress.getByName(ip), 43594)
        every { client.channel } returns channel
        every { client.uuid } returns uuid
        every { client.username } returns name
        return client
    }

    /** Kills [victim] (everything in the inventory lost) through the real death executor. */
    private fun die(world: World, victim: Player, killer: Player): DeathResolutionResult {
        val lost = (0 until victim.inventory.capacity).mapNotNull { slot -> victim.inventory[slot]?.let { DeathSlotItem(DeathContainerSource.INVENTORY, slot, it) } }
        val result = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost))
        assertTrue(DeathExecutor.execute(world, result))
        return result
    }

    @Test
    fun `an emblem in the gravestone counts as owned and comes back under the one-emblem rule`() {
        val player = newPlayer("graveowner")
        player.gravestone[0] = Item(DeadmanEmblem.emblemId(2), 1)
        player.attr[gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR] = Tile(3200, 3200, 0).as30BitInteger
        assertEquals(2, DeadmanEmblem.ownedTier(player), "an emblem waiting in the gravestone is still owned")
        // A better emblem drops meanwhile: the one in the gravestone is cashed in, never a second emblem.
        DeadmanEmblem.receive(player, 3, DeadmanEmblem.Source.DROP)
        assertEquals(0, player.gravestone.getItemCount(DeadmanEmblem.emblemId(2)))
        assertEquals(1, player.inventory.getItemCount(DeadmanEmblem.emblemId(3)))
        assertEquals(DeadmanEmblem.points(2), player.attr[StoreCatalogue.Currency.DEADMAN.attr])

        // Reclaiming a worse emblem from the gravestone cashes it in instead of adding a second one.
        player.gravestone[0] = Item(DeadmanEmblem.emblemId(1), 1)
        assertTrue(gg.rsmod.plugins.content.mechanics.death.Gravestone.take(player, 0) is gg.rsmod.plugins.content.mechanics.death.GraveTakeOutcome.Taken)
        assertEquals(1, DeadmanEmblem.holdings(player).size)
        assertEquals(3, DeadmanEmblem.ownedTier(player))
        assertEquals(DeadmanEmblem.points(2) + DeadmanEmblem.points(1), player.attr[StoreCatalogue.Currency.DEADMAN.attr])
    }

    private fun newPlayer(name: String, world: World = mockk(relaxed = true)): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.username } returns name
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3200, 3700, 0)
        every { player.isOnline } returns true
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        return player
    }

    companion object {
        private const val COINS = 995

        /** Deadman Points the existing Killstreaks reward pays for a first kill of a pair (unchanged by emblems). */
        private const val KILL_POINTS = 10
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            ValidPkKill.dataDir = Files.createTempDirectory("emblem-test").toFile()
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
