package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.items.armor.DeadmanGear
import gg.rsmod.plugins.content.items.osrs.ZurielsStaff
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService
import gg.rsmod.plugins.content.mechanics.exchange.OfferType
import gg.rsmod.plugins.content.skills.summoning.BeastOfBurden
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.*

/** N457, owner 2026-10-30: delete all 667 corrupt weapons/armour without replacement. No cache writes or player saves. */
class CorruptRemovedItemsTests {
    @get:Rule val temporary = TemporaryFolder(File("build").apply { mkdirs() })

    // Native 667 constants include the whole 13908..13990 family, including notes and poisoned javelins.
    private val constants = Items::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }
        .associate { it.name to it.getInt(null) }
    private val family = constants.filterValues { it in 13908..13990 }
    private val notes = family.filterKeys { it.endsWith("_NOTED") }.values.toSet()
    private val normal = listOf(
        Items.VESTAS_LONGSWORD, Items.VESTAS_CHAINBODY, Items.STATIUSS_WARHAMMER, Items.ZURIELS_STAFF,
        Items.MORRIGANS_JAVELIN, Items.MORRIGANS_JAVELIN_P, Items.MORRIGANS_JAVELIN_P_13881, Items.MORRIGANS_JAVELIN_P_13882,
        Items.MORRIGANS_THROWING_AXE, Items.DRAGON_DAGGER, Items.DRAGON_MED_HELM, Items.BOW_OF_FAERDHINEN_C,
        Items.DRAGON_CHAINBODY, Items.DRAGON_CHAINBODY_3140,
    )
    private val defs = mockk<DefinitionSet>()
    private val itemDefs = (family.values + normal + DeadmanGear.CONVERT.keys + DeadmanGear.NORMAL).associateWith { id ->
        ItemDef(id).apply {
            name = constants.entries.first { it.value == id }.key
            tradeable = true
            grandExchange = true
            if (id in notes) {
                noteTemplateId = 799
                noteLinkId = id - 1
                stacks = true
            }
        }
    }

    init {
        every { defs.get(ItemDef::class.java, any()) } answers { itemDefs.getValue(secondArg()) }
        every { defs.getNullable(ItemDef::class.java, any()) } answers { itemDefs[secondArg<Int>()] }
    }

    @Test
    fun `the entire native family is refused including notes but normal neighbours stay`() {
        assertEquals((13908..13990).toSet(), family.values.toSet(), "complete native item constants")
        assertEquals(57, RemovedItems.CORRUPT_IDS.size)
        assertEquals(family.values.toSet() - notes, RemovedItems.CORRUPT_IDS)
        family.values.forEach { id ->
            assertTrue(RemovedItems.isUnobtainable(itemDefs.getValue(id)), "$id unobtainable")
            assertFalse(GrandExchangeInterface.exchangeable(itemDefs.getValue(id)), "$id GE refused")
            assertNull(DeadmanGear.target(id, defs), "$id must never become normal")
        }
        normal.forEach { id ->
            assertFalse(RemovedItems.isUnobtainable(itemDefs.getValue(id)), "$id must stay")
            assertNull(DeadmanGear.target(id, defs))
        }
        assertTrue(RemovedItems.CORRUPT_IDS.none { it in DeadmanGear.CONVERT || it in DeadmanGear.NORMAL })
    }

    @Test
    fun `login deletes corrupt and noted stacks from every container without adding to normal stacks`() {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val player = mockk<Client>(relaxed = true)
        every { player.world } returns world
        every { player.loginUsername } returns "n457-test"
        every { world.getService(GrandExchangeService::class.java) } returns null
        val keys = listOf(INVENTORY_KEY, EQUIPMENT_KEY, BANK_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY,
            BeastOfBurden.WAR_TORTOISE_KEY, RANDOM_EVENT_GIFT_KEY, gg.rsmod.plugins.content.mechanics.pvp.LootingBag.KEY)
        val containers = keys.associateWith { ItemContainer(defs, it) }
        every { player.inventory } returns containers.getValue(INVENTORY_KEY)
        every { player.equipment } returns containers.getValue(EQUIPMENT_KEY)
        every { player.bank } returns containers.getValue(BANK_KEY)
        every { player.gravestone } returns containers.getValue(GRAVESTONE_KEY)
        every { player.deathRecovery } returns containers.getValue(DEATH_RECOVERY_KEY)
        every { player.containers } returns HashMap(containers)
        containers.values.forEach { c ->
            c[0] = Item(Items.CORRUPT_DRAGON_DAGGER, 2)
            c[1] = Item(Items.CORRUPT_VESTAS_CHAINBODY_DEG, 2)
            c[2] = Item(Items.CORRUPT_VESTAS_CHAINBODY_NOTED, 2)
            c[3] = Item(Items.VESTAS_CHAINBODY, 2)
            c[4] = Item(Items.DRAGON_DAGGER, 2)
        }
        // The actual login order: migration must leave corrupt copies intact for the purge.
        assertEquals(0, DeadmanGear.migrate(player))
        assertEquals(containers.size * 3, RemovedItems.purge(player))
        containers.values.forEach { c ->
            (0..2).forEach { assertNull(c[it], "${c.key.name}: corrupt stack $it") }
            assertEquals(Items.VESTAS_CHAINBODY to 2, c[3]!!.let { it.id to it.amount })
            assertEquals(Items.DRAGON_DAGGER to 2, c[4]!!.let { it.id to it.amount })
            assertTrue(c.dirty)
        }
        assertEquals(0, RemovedItems.purge(player), "second login")
    }

    @Test
    fun `login withdraws only this player's corrupt GE offers with coins retained`() {
        val ge = GrandExchangeService(temporary.newFile("offers.json"))
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        every { world.getService(GrandExchangeService::class.java) } returns ge
        val player = mockk<Client>(relaxed = true)
        every { player.world } returns world
        every { player.loginUsername } returns "n457-test"
        val empty = ItemContainer(defs, INVENTORY_KEY)
        every { player.inventory } returns empty
        every { player.equipment } returns empty
        every { player.bank } returns empty
        every { player.gravestone } returns empty
        every { player.deathRecovery } returns empty
        every { player.containers } returns hashMapOf(INVENTORY_KEY to empty)
        val sell = ge.submit("n457-test", OfferType.SELL, Items.CORRUPT_DRAGON_DAGGER, 1_000, 2, slot = 0)!!.first
        val buy = ge.submit("n457-test", OfferType.BUY, Items.CORRUPT_VESTAS_CHAINBODY, 500, 2, slot = 1)!!.first
        val noted = ge.submit("n457-test", OfferType.SELL, Items.CORRUPT_VESTAS_CHAINBODY_NOTED, 500, 2, slot = 2)!!.first
        val keep = ge.submit("n457-test", OfferType.SELL, Items.DRAGON_DAGGER, 2_000, 2, slot = 3)!!.first
        val other = ge.submit("other-test", OfferType.SELL, Items.CORRUPT_DRAGON_DAGGER, 2_000, 2, slot = 0)!!.first
        assertEquals(3, RemovedItems.purge(player))
        assertNull(ge.offer(sell.id))
        assertNull(ge.offer(noted.id))
        assertEquals(1_000L, ge.offer(buy.id)!!.collectableCoins)
        assertEquals(0, ge.offer(buy.id)!!.collectableItems)
        assertEquals(Items.DRAGON_DAGGER, ge.offer(keep.id)!!.itemId)
        assertNotNull(ge.offer(other.id), "another player's offers are purged on their own login")
    }

    @Test
    fun `removed weapon specials fail safely and no corrupt projectile or staff passive remains`() {
        val player = mockk<Client>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        every { player.equipment } returns equipment
        RemovedItems.CORRUPT_IDS.forEach { id ->
            equipment[3] = Item(id)
            assertEquals(SpecialAttacks.Outcome.NOT_USED, SpecialAttacks.perform(player, null, world), "$id special")
            assertFalse(SpecialAttacks.hasSpecialAttack(id))
            assertFalse(ZurielsStaff.wielding(player))
            assertTrue(RangedProjectile.values.none { id in it.items })
        }
        assertTrue(Items.MORRIGANS_JAVELIN in RangedProjectile.MORRIGANS_JAVELIN.items)
        assertTrue(Items.ZURIELS_STAFF in ZurielsStaff.STAVES)
        // Source guard covers real script registrations as well as shared combat code.
        val content = File("src/main/kotlin/gg/rsmod/plugins/content")
        val banned = Regex("""Items\.(CORRUPT_(?:DRAGON|VESTAS|STATIUS|ZURIEL|MORRIGAN)\w*|C_(?:DRAGON|VESTAS|STATIUS|MORRIGAN)\w*)""")
        val offenders = content.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "kts") && it.name != "RemovedItems.kt" }
            .filter { f -> f.readLines().any { !it.trimStart().startsWith("//") && banned.containsMatchIn(it) } }.map { it.path }.toList()
        assertTrue(offenders.isEmpty(), "corrupt behaviour/source left: $offenders")
        assertFalse(File(content, "items/armor/CorruptArmorCharges.kt").exists())
    }
}
