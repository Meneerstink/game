package gg.rsmod.plugins.content.skills

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import gg.rsmod.plugins.content.skills.mining.Mining
import gg.rsmod.plugins.content.skills.mining.RockType
import gg.rsmod.plugins.content.skills.summoning.SkillingPets
import gg.rsmod.plugins.content.scrolls.ClueSkilling
import gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilSkilling
import gg.rsmod.plugins.content.n11.N11Fixture
import gg.rsmod.plugins.content.items.osrs.Blowpipe
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.OTHER_ITEM_ATTR
import java.lang.ref.WeakReference
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.items.osrs.AvasAssembler
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.MaxCapes
import gg.rsmod.plugins.content.items.potion.StaminaPotions
import gg.rsmod.plugins.content.mechanics.lightsource.LightSource
import gg.rsmod.plugins.content.skills.herblore.HerbData
import gg.rsmod.plugins.content.skills.herblore.mixing.CreateUnfinishedPotionAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.clearMocks
import kotlin.test.*

internal class SkillcapeFixture {
    val definitions = mockk<DefinitionSet>()
    val player = mockk<Player>(relaxed = true)
    val world = mockk<World>(relaxed = true)
    val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
    val equipment = ItemContainer(definitions, 14, ContainerStackType.NORMAL)
    val bank = ItemContainer(definitions, 800, ContainerStackType.STACK)
    val skills = SkillSet(25)
    val attrs = AttributeMap()
    val timers = TimerMap()
    var energy = 0.0

    init {
        every { definitions.get(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).apply { stacks = id == Items.COINS_995 }
        }
        every { player.inventory } returns inventory
        every { player.world } returns world
        every { world.definitions } returns definitions
        every { player.tile } returns Tile(3200, 3200)
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.skills } returns skills
        every { player.attr } returns attrs
        every { player.timers } returns timers
        every { player.runEnergy } answers { energy }
        every { player.runEnergy = any() } answers { energy = firstArg() }
    }

    fun wear(item: Int) { equipment[EquipmentType.CAPE.id] = Item(item) }
    fun fillThrough(last: Int) { for (slot in 0..last) inventory[slot] = Item(Items.KNIFE) }
}

class SkillcapeFeaturesTests {
    @Test
    fun `cooking hood grants only the worn cooking guild exemption and no cooking skill perk`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(Items.COOKING_HOOD)
        assertFalse(SkillcapePerks.worn(f.player, SkillcapePerks.VariantPerk.COOKS_GUILD))
        f.equipment[EquipmentType.HEAD.id] = f.inventory[0]
        f.inventory[0] = null
        assertTrue(SkillcapePerks.worn(f.player, SkillcapePerks.VariantPerk.COOKS_GUILD))
        assertFalse(SkillcapePerks.worn(f.player, Skillcapes.COOKING), "hood must not prevent food from burning")
        assertFalse(SkillcapePerks.worn(f.player, SkillcapePerks.VariantPerk.CRAFTING_GUILD))
        f.equipment[EquipmentType.HEAD.id] = Item(Items.FIREMAKING_HOOD)
        assertFalse(SkillcapePerks.worn(f.player, SkillcapePerks.VariantPerk.COOKS_GUILD))
    }

    @Test
    fun `natural run recovery gains three percent only with worn agility or plain max cape`() {
        val f = SkillcapeFixture()
        every { f.player.lock } returns gg.rsmod.game.model.LockState.NONE
        every { f.player.isRunning() } returns false
        every { f.player.varps.getState(gg.rsmod.plugins.api.cfg.Varps.RUN_STATE) } returns 0
        f.skills.setBaseLevel(Skills.AGILITY, 99)
        f.energy = 50.0
        gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
        val normal = f.energy - 50.0
        assertTrue(normal > 0)
        for (id in listOf(Items.AGILITY_CAPE, Items.AGILITY_CAPE_T, MaxCapes.MAX_CAPE)) {
            f.wear(id)
            f.energy = 50.0
            gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
            assertEquals(normal * 1.03, f.energy - 50.0, 1e-10, "worn $id")
        }
        for (id in listOf(Items.AGILITY_HOOD) + MaxCapes.WEARABLE_VARIANTS) {
            f.wear(id)
            f.energy = 50.0
            gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
            assertEquals(normal, f.energy - 50.0, 1e-10, "no Agility perk on $id")
        }
        f.equipment[EquipmentType.CAPE.id] = null
        f.inventory[0] = Item(Items.AGILITY_CAPE)
        f.energy = 50.0
        gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
        assertEquals(normal, f.energy - 50.0, 1e-10, "carrying does not give the passive perk")
        every { f.player.varps.getState(gg.rsmod.plugins.api.cfg.Varps.RUN_STATE) } returns 3
        f.energy = 50.0
        gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
        val resting = f.energy
        f.wear(Items.AGILITY_CAPE_T)
        f.energy = 50.0
        gg.rsmod.plugins.content.mechanics.run.RunEnergy.drain(f.player)
        assertEquals(resting, f.energy, "the native resting recovery remains unchanged")
    }

    @Test
    fun `real cape attraction timer changes steel to mithril after upgrade and respects the shared off switch`() {
        N11Fixture().use { f ->
            f.load("items.osrs.Avas_assembler_plugin")
            f.equipment[EquipmentType.CAPE.id] = Item(Items.RANGING_CAPE)
            val field = f.repo.javaClass.getDeclaredField("timerPlugins").apply { isAccessible = true }
            val timer = (field.get(f.repo) as Map<*, *>).keys.single() as TimerKey
            assertTrue(f.repo.executeTimer(f.player, timer))
            assertEquals(1, f.inventory.getItemCount(Items.STEEL_ARROW))
            assertNull(f.equipment[EquipmentType.AMMO.id])
            f.attrs[RangingSkillcape.UPGRADED] = true
            every { f.player.tile } returns Tile(3204, 3200)
            assertTrue(f.repo.executeTimer(f.player, timer))
            assertEquals(1, f.inventory.getItemCount(Items.MITHRIL_ARROW))
            assertNull(f.equipment[EquipmentType.AMMO.id])
            f.attrs[AvasAssembler.GATHERING_STOPPED] = true
            every { f.player.tile } returns Tile(3208, 3200)
            f.repo.executeTimer(f.player, timer)
            assertEquals(1, f.inventory.getItemCount(Items.MITHRIL_ARROW))
        }
    }
    @Test
    fun `actual inventory and worn handlers work with the sourced menus and do not require absent menu slots`() {
        N11Fixture().use { f ->
            f.load("skills.Skillcape_features_plugin") // today's cache has no extra menu yet; must still boot safely.
            assertFalse(f.repo.hasItemOption(Items.FLETCHING_CAPE, 3))
        }
        N11Fixture().use { f ->
            for (cape in listOf(Skillcapes.FLETCHING, Skillcapes.HERBLORE)) {
                for (id in listOf(cape.untrimmedCape, cape.trimmedCape)) {
                    f.menu(id, "", "Wear", "Search")
                    f.def(id).equipmentMenu[0] = "Boost"
                    f.def(id).equipmentMenu[1] = "Search"
                }
            }
            f.load("skills.Skillcape_features_plugin")
            f.inventory[0] = Item(Items.FLETCHING_CAPE)
            assertTrue(f.repo.executeItem(f.player, Items.FLETCHING_CAPE, 3))
            assertEquals(1, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
            assertEquals(1, f.inventory.getItemCount(Items.BRONZE_CROSSBOW))
            f.equipment[EquipmentType.CAPE.id] = Item(Items.HERBLORE_CAPE_T)
            assertTrue(f.repo.executeEquipmentOption(f.player, Items.HERBLORE_CAPE_T, 2))
            assertEquals(1, f.inventory.getItemCount(Items.PESTLE_AND_MORTAR))
            val head = Item(Items.VORKATHS_HEAD)
            val cape = Item(Items.RANGING_CAPE)
            f.inventory[20] = head
            f.inventory[21] = cape
            f.attrs[INTERACTING_ITEM] = WeakReference(head)
            f.attrs[OTHER_ITEM_ATTR] = WeakReference(cape)
            assertTrue(f.repo.executeItemOnItem(f.player, head.id, cape.id))
            assertNull(f.inventory[20])
            assertTrue(RangingSkillcape.upgraded(f.player))
        }
    }
    @Test
    fun `Vorkath upgrade consumes one clicked head once and applies to future capes without changing stats`() {
        val f = SkillcapeFixture()
        val head = Item(Items.VORKATHS_HEAD)
        val cape = Item(Items.RANGING_CAPE)
        f.inventory[0] = head
        f.inventory[1] = cape
        assertFalse(RangingSkillcape.upgraded(f.player))
        assertTrue(RangingSkillcape.upgrade(f.player, cape, head))
        assertNull(f.inventory[0])
        assertSame(cape, f.inventory[1])
        assertTrue(RangingSkillcape.upgraded(f.player))
        assertNotNull(RangingSkillcape.UPGRADED.persistenceKey)
        val secondHead = Item(Items.VORKATHS_HEAD)
        f.inventory[0] = secondHead
        assertFalse(RangingSkillcape.upgrade(f.player, secondHead, cape))
        assertSame(secondHead, f.inventory[0])
        for (id in RangingSkillcape.CAPES) {
            f.wear(id)
            assertEquals(Items.AVAS_ASSEMBLER, RangingSkillcape.attractionDevice(f.player, id))
            for (roll in 0..99) {
                assertEquals(if (roll < 20) AvasDevices.AmmoOutcome.BROKEN else AvasDevices.AmmoOutcome.RECOVERED,
                    AvasDevices.outcome(f.player, roll), "$id roll $roll")
            }
        }
    }

    @Test
    fun `dropped or replaced clicked head or cape cannot upgrade a different copy`() {
        val f = SkillcapeFixture()
        val head = Item(Items.VORKATHS_HEAD)
        val cape = Item(Items.RANGING_CAPE_T)
        f.inventory[0] = Item(head)
        f.inventory[1] = cape
        assertFalse(RangingSkillcape.upgrade(f.player, head, cape))
        f.inventory[0] = head
        f.inventory[1] = Item(cape)
        assertFalse(RangingSkillcape.upgrade(f.player, head, cape))
        assertSame(head, f.inventory[0])
        assertFalse(RangingSkillcape.upgraded(f.player))
        for (id in RangingSkillcape.CAPES) {
            f.wear(id)
            assertEquals(Items.AVAS_ACCUMULATOR, RangingSkillcape.attractionDevice(f.player, id))
            assertEquals(8, (0..99).count { AvasDevices.outcome(f.player, it) == AvasDevices.AmmoOutcome.DROPPED })
        }
    }

    @Test
    fun `upgraded cape saves blowpipe darts at 80 percent and metal armour still blocks ammo retrieval`() {
        val f = SkillcapeFixture()
        val pipe = Blowpipe.chargeScales(Blowpipe.loadDarts(Item(Items.TOXIC_BLOWPIPE_EMPTY), Items.BRONZE_DART, 10).result, 10).result
        val id = Items.RANGING_CAPE
        assertTrue(Blowpipe.spendShot(pipe, 0.5, 0.75, RangingSkillcape.attractionDevice(f.player, id)).dartUsed)
        f.attrs[RangingSkillcape.UPGRADED] = true
        assertFalse(Blowpipe.spendShot(pipe, 0.5, 0.75, RangingSkillcape.attractionDevice(f.player, id)).dartUsed)
        assertTrue(Blowpipe.spendShot(pipe, 0.5, 0.80, RangingSkillcape.attractionDevice(f.player, id)).dartUsed)
        f.wear(id)
        f.equipment[EquipmentType.CHEST.id] = Item(Items.STEEL_PLATEBODY)
        every { f.definitions.get(ItemDef::class.java, Items.STEEL_PLATEBODY) } returns ItemDef(Items.STEEL_PLATEBODY).apply { name = "Steel platebody" }
        assertTrue(AvasDevices.interferes(f.player))
        assertEquals(AvasDevices.AmmoOutcome.DROPPED, AvasDevices.outcome(f.player, 50))
    }

    @Test
    fun `mining cape gives XP for the delivered extra ore only with one combined XP award`() {
        val f = SkillcapeFixture()
        val obj = mockk<GameObject>(relaxed = true)
        every { f.definitions.get(ObjectDef::class.java, any()) } answers { ObjectDef(secondArg()).apply { depleted = -1 } }
        every { f.world.random(any<Int>()) } returns 0
        every { f.world.randomDouble() } returns 0.0
        mockkObject(SigilSkilling, SkillingPets, ClueSkilling)
        try {
            every { SigilSkilling.give(f.player, any(), any()) } answers { f.inventory.add(secondArg<Int>(), thirdArg<Int>()) }
            every { SkillingPets.mining(any(), any()) } returns Unit
            every { ClueSkilling.mining(any(), any()) } returns Unit
            val success = Mining::class.java.getDeclaredMethod("onSuccess", Player::class.java, String::class.java,
                RockType::class.java, GameObject::class.java, RockType.Product::class.java).apply { isAccessible = true }
            f.wear(Items.MINING_CAPE_T)
            success.invoke(Mining, f.player, "iron ore", RockType.IRON, obj, null)
            assertEquals(2, f.inventory.getItemCount(Items.IRON_ORE))
            verify(exactly = 1) { f.player.addXp(Skills.MINING, RockType.IRON.experience * 2, any(), any()) }
            clearMocks(f.player, answers = false, recordedCalls = true)
            f.fillThrough(27)
            success.invoke(Mining, f.player, "iron ore", RockType.IRON, obj, null)
            assertEquals(0, f.inventory.getItemCount(Items.IRON_ORE))
            verify(exactly = 1) { f.player.addXp(Skills.MINING, RockType.IRON.experience, any(), any()) }
            clearMocks(f.player, answers = false, recordedCalls = true)
            f.inventory[27] = null
            success.invoke(Mining, f.player, "iron ore", RockType.IRON, obj, null)
            assertEquals(1, f.inventory.getItemCount(Items.IRON_ORE))
            verify(exactly = 1) { f.player.addXp(Skills.MINING, RockType.IRON.experience, any(), any()) }
        } finally {
            unmockkObject(SigilSkilling, SkillingPets, ClueSkilling)
        }
    }
    @Test
    fun `search gives both fletching tools three times with a shared account limit`() {
        val f = SkillcapeFixture()
        for (cape in listOf(Items.FLETCHING_CAPE, Items.FLETCHING_CAPE_T, MaxCapes.MAX_CAPE)) {
            f.wear(cape)
            assertTrue(SkillcapeFeatures.searchFletching(f.player))
        }
        assertEquals(3, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
        assertEquals(3, f.inventory.getItemCount(Items.BRONZE_CROSSBOW))
        assertFalse(SkillcapeFeatures.searchFletching(f.player))
        assertEquals(6, f.inventory.occupiedSlotCount)
    }

    @Test
    fun `full inventory and one free slot deliver no half pair and use no search charge`() {
        val f = SkillcapeFixture()
        f.wear(Items.FLETCHING_CAPE)
        f.fillThrough(27)
        assertFalse(SkillcapeFeatures.searchFletching(f.player))
        f.inventory[27] = null
        assertFalse(SkillcapeFeatures.searchFletching(f.player))
        assertEquals(0, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
        assertEquals(0, f.inventory.getItemCount(Items.MITH_GRAPPLE_9419))
        f.inventory[26] = null
        assertTrue(SkillcapeFeatures.searchFletching(f.player))
        assertEquals(28, f.inventory.occupiedSlotCount)
    }

    @Test
    fun `a carried fletching cape works and dropping it rejects stale use`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(Items.FLETCHING_CAPE)
        assertTrue(SkillcapeFeatures.searchFletching(f.player))
        f.inventory[0] = null
        assertFalse(SkillcapeFeatures.searchFletching(f.player))
        assertEquals(1, SkillcapePerks.usedToday(f.player, SkillcapePerks.DailyUse.FLETCHING_SEARCH))
    }

    @Test
    fun `herblore search is unlimited but cannot lose items to full inventory`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(Items.HERBLORE_CAPE_T)
        repeat(5) { assertTrue(SkillcapeFeatures.searchHerblore(f.player)) }
        assertEquals(5, f.inventory.getItemCount(Items.PESTLE_AND_MORTAR))
        for (slot in 6..27) f.inventory[slot] = Item(Items.KNIFE)
        assertFalse(SkillcapeFeatures.searchHerblore(f.player))
        assertEquals(5, f.inventory.getItemCount(Items.PESTLE_AND_MORTAR))
    }

    @Test
    fun `agility perk restores energy and arms exactly one minute once per account`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(Items.AGILITY_CAPE)
        assertTrue(SkillcapeFeatures.stamina(f.player))
        assertEquals(100.0, f.energy)
        assertEquals(100, f.timers[StaminaPotions.STAMINA_TIMER])
        f.energy = 20.0
        f.wear(MaxCapes.MAX_CAPE)
        assertFalse(SkillcapeFeatures.stamina(f.player))
        assertEquals(20.0, f.energy)
    }

    @Test
    fun `firemaking cape and plain max light the area carried or worn but variants and hoods do not`() {
        val f = SkillcapeFixture()
        for (id in listOf(Items.FIREMAKING_CAPE, Items.FIREMAKING_CAPE_T, MaxCapes.MAX_CAPE)) {
            f.inventory[0] = Item(id)
            assertNotNull(LightSource.getActiveLightSource(f.player), "carried $id")
            f.inventory[0] = null
            f.wear(id)
            assertNotNull(LightSource.getActiveLightSource(f.player), "worn $id")
            f.equipment[EquipmentType.CAPE.id] = null
        }
        for (id in listOf(Items.FIREMAKING_HOOD) + MaxCapes.WEARABLE_VARIANTS) {
            f.inventory[0] = Item(id)
            f.wear(id)
            assertNull(LightSource.getActiveLightSource(f.player), "no light perk on $id")
        }
    }

    @Test
    fun `grimy herb needs a worn cape each time and grants cleaning XP exactly once`() {
        val f = SkillcapeFixture()
        val herb = HerbData.GUAM
        f.skills.setBaseLevel(Skills.HERBLORE, 99)
        f.skills.setCurrentLevel(Skills.HERBLORE, 99)
        val action = CreateUnfinishedPotionAction()
        f.fillThrough(27)
        f.inventory[0] = Item(herb.grimy)
        f.inventory[1] = Item(Items.VIAL_OF_WATER)
        f.inventory[2] = Item(Items.HERBLORE_CAPE)
        assertFalse(action.mixOne(f.player, herb, useGrimy = true))
        assertEquals(herb.grimy, f.inventory[0]?.id)
        f.wear(Items.HERBLORE_CAPE_T)
        assertTrue(action.mixOne(f.player, herb, useGrimy = true))
        assertNull(f.inventory[0])
        assertEquals(herb.unf, f.inventory[1]?.id)
        assertFalse(action.mixOne(f.player, herb, useGrimy = true))
        verify(exactly = 1) { f.player.addXp(Skills.HERBLORE, herb.experience, any(), any()) }
        f.inventory[0] = Item(herb.grimy)
        f.inventory[1] = Item(Items.VIAL_OF_WATER)
        f.equipment[EquipmentType.CAPE.id] = null
        assertFalse(action.mixOne(f.player, herb, useGrimy = true))
    }

    @Test
    fun `clean herb mixing remains cape independent and gives no cleaning XP`() {
        val f = SkillcapeFixture()
        f.skills.setCurrentLevel(Skills.HERBLORE, 99)
        val herb = HerbData.RANARR
        f.inventory[0] = Item(herb.clean)
        f.inventory[1] = Item(Items.VIAL_OF_WATER)
        assertTrue(CreateUnfinishedPotionAction().mixOne(f.player, herb))
        assertEquals(herb.unf, f.inventory[1]?.id)
        verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
    }
}
