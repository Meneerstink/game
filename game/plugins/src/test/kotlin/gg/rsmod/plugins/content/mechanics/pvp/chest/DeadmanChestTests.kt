package gg.rsmod.plugins.content.mechanics.pvp.chest

import com.displee.cache.CacheLibrary
import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.armor.DeadmanGear
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StorePets
import gg.rsmod.plugins.content.skills.summoning.Pets
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N101 (owner 2026-10-17): the Deadman's chest. CLASS = "one owner list, many consumers": the chest list [DeadmanChestLoot] is the ONLY
 * source of the 17 Ancient Warriors pieces, the Trinket of fairies, Elder chaos, Heavy ballista, the Voidwaker parts and every OSRS
 * pet - no shop, no Grand Exchange house quote, no store-pet entry may sell what the chest gives. Plus every rule of the chest (dossier
 * gaps 1-6): requirements, the 100 / 50 tick channel and every interrupt, first opener, kill predicate, anti-farm limiter, loot variants.
 */
class DeadmanChestTests {
    private fun facts(
        inInstance: Boolean = false,
        risk: Long = 150_000,
        beginner: Boolean = false,
        grace: Boolean = false,
        onChest: Boolean = false,
        adjacent: Boolean = true,
    ) = DeadmanChest.Facts(inInstance, risk, beginner, grace, onChest, adjacent)

    private fun kill(
        lifepoints: Int = 30,
        summonOrTest: Boolean = false,
        guardian: Boolean = false,
        pet: Boolean = false,
        instance: Boolean = false,
        minigame: Boolean = false,
        dangerous: Boolean = true,
        respawns: Boolean = true,
    ) = DeadmanChest.KillFacts(lifepoints, summonOrTest, guardian, pet, instance, minigame, dangerous, respawns)

    // ------------------------------------------------------------------------------------------------ the class: nothing of the list is sold

    @Test
    fun `class - no shop sells anything the chest gives, the Deadman Shop included`() {
        val sold = StoreCatalogue.ENTRIES.flatMap { listOf(it.purchaseItem) + it.resultItems }
        assertEquals(emptyList(), sold.filter { it in DeadmanChestLoot.FIXED_IDS }.distinct(), "chest items on sale")
        assertEquals(emptyList(), StoreCatalogue.violations())
    }

    @Test
    fun `class - all 71 owner roster species enter the chest, no pet is sold and existing boss drops stay`() {
        assertTrue(Pets.load(TABLE, TIERS) > 0)
        assertEquals(emptyList(), StorePets.entries(TABLE, TIERS).map { it.purchaseItem }, "pets still sold")
        val expected = Pets.parseTiers(TIERS.readLines())
        assertEquals(71, expected.size, "Council Opus+Sol 2026-10-20 19:37: all pet_tiers species")
        assertEquals(mapOf(1 to 24, 2 to 25, 3 to 22), expected.groupingBy { it.tier }.eachCount())
        val pets = DeadmanChestLoot.pets()
        assertEquals(expected.map { it.pet }.toSet(), pets.map { it.first }.toSet(), "complete independent owner roster")
        assertEquals(71, pets.size, "exactly one base item per species, including multi-variant pets")
        for (info in expected) {
            val base = Pets.parseTable(TABLE.readLines()).first { it.pet == info.pet }.item
            assertEquals(base, pets.single { it.first == info.pet }.second, info.pet)
            for (npc in info.dropNpcs) assertEquals(info, Pets.dropFor(npc), "existing boss drop npc=$npc")
        }
        val variantItems = Pets.variants.map { it.item }.toSet()
        assertEquals(emptyList(), StoreCatalogue.ENTRIES.filter { e ->
            e.purchaseItem in variantItems || e.resultItems.any { it in variantItems }
        }.map { it.purchaseItem }, "no base or metamorphosis variant sold")
    }

    @Test
    fun `class - the Grand Exchange house neither sells nor buys a chest item, every other tradeable item is still quoted`() {
        val service = geService()
        val failures = DeadmanChestLoot.FIXED_IDS.filter { service.houseQuote(it) != null }
        assertEquals(emptyList(), failures, "the house still deals in these chest items")
        assertNotNull(service.houseQuote(Items.STEEL_TITAN_POUCH), "owner 09-20 'every item buyable' must hold for everything else")
    }

    @Test
    fun `N115 - the house neither sells nor buys what the 78 Store sells, except the Donator gear that also drops in PvM`() {
        val service = geService()
        val storeOnly = StoreCatalogue.ENTRIES.filter { it.shop != StoreCatalogue.Shop.DEADMAN && !(it.shop == StoreCatalogue.Shop.DONATOR && it.category in StoreCatalogue.DONATOR_PVM_GEAR) }
        val failures = storeOnly.flatMap { listOf(it.purchaseItem) + it.resultItems }.filter { it > 0 && service.houseQuote(it) != null }
        assertEquals(emptyList(), failures, "the house still deals in these store items")
        assertNull(service.houseQuote(Items.BERSERKER_RING_I), "VRAGEN 53: ~10,000 coins in the house")
        assertNotNull(service.houseQuote(Items.AMULET_OF_TORTURE), "Donator gear that also drops in PvM stays in the house")
        assertNotNull(service.houseQuote(Items.MASORI_BODY))
    }

    // ------------------------------------------------------------------------------------------------ the list itself

    @Test
    fun `the list holds the 17 Ancient Warriors pieces, the trinket, Elder chaos, Heavy ballista and the Voidwaker parts as the NORMAL ids`() {
        assertEquals(17, DeadmanChestLoot.ANCIENT_WARRIORS.size)
        assertTrue(DeadmanChestLoot.FIXED_IDS.containsAll(DeadmanChestLoot.ANCIENT_WARRIORS))
        assertTrue(DeadmanChestLoot.FIXED_IDS.containsAll(listOf(Items.TRINKET_OF_FAIRIES, Items.ELDER_CHAOS_HOOD, Items.ELDER_CHAOS_TOP, Items.ELDER_CHAOS_ROBE, Items.HEAVY_BALLISTA, Items.VOIDWAKER_BLADE, Items.VOIDWAKER_GEM, Items.VOIDWAKER_HILT)))
        assertEquals(DeadmanChestLoot.FIXED.size, DeadmanChestLoot.FIXED_IDS.size, "no id twice")
        assertEquals(emptyList(), DeadmanChestLoot.FIXED_IDS.filter { it in DeadmanGear.CONVERT.keys }, "(deg) / corrupt ids")
    }

    @Test
    fun `Morrigan javelins and throwing axes are one stack of 100, never noted`() {
        listOf(Items.MORRIGANS_JAVELIN, Items.MORRIGANS_THROWING_AXE).forEach { id ->
            val entry = DeadmanChestLoot.FIXED.single { it.item == id }
            assertEquals(100, entry.amount)
            assertTrue(DEFINITIONS.get(ItemDef::class.java, id).stackable, "item $id stackable")
        }
        assertEquals(emptyList(), DeadmanChestLoot.FIXED.filter { it.item !in listOf(Items.MORRIGANS_JAVELIN, Items.MORRIGANS_THROWING_AXE) && it.amount != 1 })
    }

    @Test
    fun `every weight of the list is reachable by the roll and a pet the opener owns is never picked`() {
        Pets.load(TABLE, TIERS)
        val pets = DeadmanChestLoot.pets()
        val total = DeadmanChestLoot.FIXED.sumOf { it.weight } + pets.size * DeadmanChestLoot.PET_WEIGHT
        val seenItems = HashSet<Int>()
        val seenPets = HashSet<String>()
        for (r in 0 until total) {
            when (val reward = DeadmanChestLoot.roll(fixed(r), pets)) {
                is DeadmanChestLoot.Reward.Item -> seenItems += reward.item
                is DeadmanChestLoot.Reward.Pet -> seenPets += reward.pet
            }
        }
        assertEquals(DeadmanChestLoot.FIXED_IDS, seenItems)
        assertEquals(pets.map { it.first }.toSet(), seenPets)
        for (owned in pets.map { it.first }) {
            val availableTotal = DeadmanChestLoot.FIXED.sumOf { it.weight } + (pets.size - 1) * DeadmanChestLoot.PET_WEIGHT
            for (r in 0 until availableTotal) {
                val reward = DeadmanChestLoot.roll(fixed(r), pets, ownsPet = { it == owned })
                if (reward is DeadmanChestLoot.Reward.Pet) assertTrue(reward.pet != owned, "an owned pet was picked: $owned")
            }
        }
        repeat(DeadmanChestLoot.FIXED.sumOf { it.weight }) { r ->
            assertTrue(DeadmanChestLoot.roll(fixed(r), pets, ownsPet = { true }) is DeadmanChestLoot.Reward.Item)
        }
    }

    @Test
    fun `every owner roster species is reachable through the breach extra roll at every bracket rate`() {
        Pets.load(TABLE, TIERS)
        val expected = Pets.parseTiers(TIERS.readLines()).map { it.pet }.toSet()
        val total = DeadmanChestLoot.FIXED.sumOf { it.weight } + expected.size * DeadmanChestLoot.PET_WEIGHT
        for (rate in listOf(1.0, 3.0, 4.0, 5.0)) {
            val seen = mutableSetOf<String>()
            for (draw in 0 until total) {
                val random = object : Random() {
                    override fun nextInt(bound: Int): Int = if (bound == total) draw else 0
                    override fun nextDouble(): Double = 0.0
                }
                BreachLoot.roll(random, rate).forEach { drop ->
                    Pets.variantOfItem(drop.item)?.let { variant ->
                        assertEquals(1, drop.amount)
                        assertEquals(Pets.baseItem(variant.pet), drop.item)
                        seen += variant.pet
                    }
                }
            }
            assertEquals(expected, seen, "breach rate=$rate must reach the independent whole roster")
        }
    }

    @Test
    fun `chest eligibility follows tiers while base selection follows first variant, independently of boss eligibility`() {
        val table = listOf(
            Pets.Variant("boss", "base", 1, 11), Pets.Variant("boss", "morph", 2, 12),
            Pets.Variant("other", "base", 3, 13), Pets.Variant("not in tiers", "base", 4, 14),
        )
        val tiers = listOf(Pets.PetInfo("other", 3, emptySet(), 0), Pets.PetInfo("boss", 1, setOf(100), 5000))
        assertEquals(listOf("other" to 3, "boss" to 1), DeadmanChestLoot.pets(table, tiers))
    }

    // ------------------------------------------------------------------------------------------------ requirements (start AND completion)

    @Test
    fun `refusal - instance, protection, immunity, risk below 100000, standing on the chest, not next to it`() {
        assertNull(DeadmanChest.refusal(facts()))
        assertEquals(DeadmanChest.MSG_INSTANCE, DeadmanChest.refusal(facts(inInstance = true)))
        assertEquals(DeadmanChest.MSG_PROTECTED, DeadmanChest.refusal(facts(beginner = true)))
        assertEquals(DeadmanChest.MSG_PROTECTED, DeadmanChest.refusal(facts(grace = true)))
        assertEquals(DeadmanChest.MSG_RISK, DeadmanChest.refusal(facts(risk = 99_999)))
        assertNull(DeadmanChest.refusal(facts(risk = 100_000)), "exactly 100,000 may open")
        assertEquals(DeadmanChest.MSG_ON_CHEST, DeadmanChest.refusal(facts(onChest = true)))
        assertEquals(DeadmanChest.MSG_TOO_FAR, DeadmanChest.refusal(facts(adjacent = false)))
    }

    @Test
    fun `the channel is 100 ticks in singles and 50 in multi, an interrupt or a failed requirement stops it for good`() {
        assertEquals(100, DeadmanChestConfig.OPEN_TICKS_SINGLES)
        assertEquals(50, DeadmanChestConfig.OPEN_TICKS_MULTI)
        for (needed in listOf(100, 50)) {
            val c = DeadmanChest.Channel(needed)
            repeat(needed - 1) { assertEquals(DeadmanChest.Channel.Result.RUNNING, c.tick(false, facts())) }
            assertEquals(DeadmanChest.Channel.Result.DONE, c.tick(false, facts()))
        }
        val hit = DeadmanChest.Channel(100)
        repeat(60) { hit.tick(false, facts()) }
        assertEquals(DeadmanChest.Channel.Result.STOPPED, hit.tick(true, facts()), "eat / hit / attack")
        assertEquals(0, hit.elapsed, "progress is lost, he must click again")
        assertEquals(DeadmanChest.Channel.Result.STOPPED, hit.tick(false, facts()))
        val dropped = DeadmanChest.Channel(100)
        repeat(99) { dropped.tick(false, facts()) }
        assertEquals(DeadmanChest.Channel.Result.STOPPED, dropped.tick(false, facts(risk = 50_000)), "risk dropped on the last tick: refused at completion")
    }

    @Test
    fun `two completions on the same tick give one reward - the first opener only`() {
        val state = DeadmanChest.State(Tile(3052, 3671, 0), timed = true, expiresAt = 3000)
        assertTrue(state.tryLoot("a"))
        assertFalse(state.tryLoot("b"))
        assertEquals("a", state.opener)
    }

    // ------------------------------------------------------------------------------------------------ kill chests

    @Test
    fun `a kill counts only with 30 engine Hitpoints and none of the exclusions`() {
        assertFalse(DeadmanChest.killCounts(kill(lifepoints = 29)))
        assertTrue(DeadmanChest.killCounts(kill(lifepoints = 30)))
        assertFalse(DeadmanChest.killCounts(kill(summonOrTest = true)), "Zemouregal summons / breachtest npcs")
        assertFalse(DeadmanChest.killCounts(kill(guardian = true)), "the chest's own Zemouregal")
        assertFalse(DeadmanChest.killCounts(kill(pet = true)), "pets and familiars")
        assertFalse(DeadmanChest.killCounts(kill(instance = true)))
        assertFalse(DeadmanChest.killCounts(kill(minigame = true)))
        assertFalse(DeadmanChest.killCounts(kill(dangerous = false)), "safe zone")
        assertFalse(DeadmanChest.killCounts(kill(respawns = false)), "intro / admin npcs")
    }

    @Test
    fun `kill limiter - one per account or address per cooldown, one per killer, world cap and distance`() {
        val l = DeadmanChest.KillLimiter()
        val a = Tile(3000, 3600, 0)
        assertTrue(l.allowed(listOf("acct:a", "ip:1"), "a", a, 0))
        l.spawned(listOf("acct:a", "ip:1"), "a", a, 0)
        assertFalse(l.allowed(listOf("acct:b", "ip:1"), "b", Tile(3100, 3600, 0), 10), "alt on the same address shares the cooldown")
        assertFalse(l.allowed(listOf("acct:a", "ip:1"), "a", Tile(3100, 3600, 0), 5_000), "one active chest per killer")
        assertFalse(l.allowed(listOf("acct:c"), "c", Tile(3010, 3600, 0), 10), "within 15 tiles of another chest")
        assertTrue(l.allowed(listOf("acct:c"), "c", Tile(3016, 3600, 0), 10))
        l.spawned(listOf("acct:c"), "c", Tile(3100, 3600, 0), 10)
        l.spawned(listOf("acct:d"), "d", Tile(3200, 3600, 0), 10)
        assertEquals(DeadmanChestConfig.MAX_KILL_CHESTS, l.activeCount())
        assertFalse(l.allowed(listOf("acct:e"), "e", Tile(3300, 3600, 0), 10), "world cap")
        l.removed(a)
        assertTrue(l.allowed(listOf("acct:e"), "e", Tile(3300, 3600, 0), 10))
        assertTrue(l.allowed(listOf("acct:a", "ip:1"), "a", a, DeadmanChestConfig.KILL_COOLDOWN_TICKS + 1), "cooldown over")
    }

    @Test
    fun `defaults - no bracket boost, breach monsters roll the chest list on top and the fairies trinket is no longer excluded`() {
        assertFalse(DeadmanChestConfig.BRACKET_BOOST)
        assertFalse(Items.TRINKET_OF_FAIRIES in BreachLoot.EXCLUDED_TRINKETS)
        assertTrue(Items.TRINKET_OF_AVARICE in BreachLoot.EXCLUDED_TRINKETS && Items.TRINKET_OF_UNDEAD in BreachLoot.EXCLUDED_TRINKETS)
        // nextInt(BREACH_EXTRA_CHANCE) == 0 is the extra chest-list roll: with every draw forced to 0 the first list entry comes out.
        val drops = BreachLoot.roll(fixed(0))
        val first = DeadmanChestLoot.FIXED.first()
        assertTrue(drops.any { it.item == first.item && it.amount == first.amount }, "extra chest roll: $drops")
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private fun fixed(value: Int) =
        object : Random() {
            override fun nextInt(bound: Int): Int = value % bound

            override fun nextInt(): Int = value

            override fun nextDouble(): Double = 0.0
        }

    private fun geService(): GrandExchangeService {
        val service = GrandExchangeService(File(Files.createTempDirectory("ge").toFile(), "offers.json"))
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val properties =
            ServerProperties().loadMap(
                mapOf("buy-limits" to Paths.get("..", "..", "data", "cfg", "ge", "buy-limits.json").toString(), "async-writes" to false),
            )
        service.init(mockk<Server>(relaxed = true), world, properties)
        return service
    }

    companion object {
        private val TABLE = File("../../data/cfg/pets/pets.tsv")
        private val TIERS = File("../../data/cfg/pets/pet_tiers.tsv")
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
            var id = -1
            File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
