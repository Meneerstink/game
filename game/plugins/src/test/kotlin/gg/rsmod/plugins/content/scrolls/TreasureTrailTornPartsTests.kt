package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import io.mockk.*
import kotlin.test.*

class TreasureTrailTornPartsTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    private val gear = mutableMapOf<Int, ItemDef>()
    private val partIds = listOf(20002, 20003, 20004)

    companion object {
        private val step = TreasureTrailThreePartStep(TreasureTrailStepCatalogue.current.rows.filter { it.tornPart })
        private val single = object : ClueStep {
            override val tier = ClueScrollTier.MASTER
            override val hint = "single-action test fixture"
            override fun onAttempt(player: Player) = false
        }
        init { ClueScrollManager.registerStep(step); ClueScrollManager.registerStep(single) }
    }

    private fun account(): Player = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { p.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { p.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        p.inventory[0] = Item(20000)
        ClueScrollManager.prepare(p, p.inventory[0]!!, ClueScrollTier.MASTER)
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
    }

    @BeforeTest fun setup() {
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { gear[secondArg()] ?: ItemDef(secondArg()).also { it.stacks = false } }
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(19835) } returns 20000
        every { ClueItems.local(19836) } returns 20001
        TreasureTrailTornParts.SOURCE_IDS.zip(partIds).forEach { (source, local) -> every { ClueItems.local(source) } returns local }
    }
    @AfterTest fun cleanup() { unmockkObject(ClueItems) }

    private fun item(id: Int, name: String, slot: BonusSlot, bonus: Int) {
        gear[id] = ItemDef(id).also { it.name = name; it.bonuses[slot.id] = bonus }
    }

    @Test fun `Abbot checks actual negative gear bonus and Robin excludes blowpipe contribution`() {
        val p = account()
        val abbot = step.components.indexOfFirst { it.key.endsWith("Anger Abbot Langley") }
        val robin = step.components.indexOfFirst { it.key.endsWith("Robin wishes to see your finest ranged equipment") }
        assertFalse(step.qualifies(p, abbot))
        item(7001, "Ancient staff", BonusSlot.PRAYER_BONUS, -1)
        p.equipment[EquipmentType.WEAPON.id] = Item(7001)
        assertTrue(step.qualifies(p, abbot))
        item(7002, "Prayer test helm", BonusSlot.PRAYER_BONUS, 1)
        p.equipment[EquipmentType.HEAD.id] = Item(7002)
        assertFalse(step.qualifies(p, abbot))
        repeat(p.equipment.capacity) { p.equipment[it] = null }
        item(7003, "Toxic blowpipe", BonusSlot.ATTACK_RANGED, 200)
        item(7004, "Ranged test body", BonusSlot.ATTACK_RANGED, 150)
        item(7005, "Ranged test cape", BonusSlot.ATTACK_RANGED, 30)
        p.equipment[EquipmentType.WEAPON.id] = Item(7003)
        p.equipment[EquipmentType.CHEST.id] = Item(7004)
        assertFalse(step.qualifies(p, robin))
        p.equipment[EquipmentType.CAPE.id] = Item(7005)
        assertFalse(step.qualifies(p, robin)) // Transcript rejects wearing a blowpipe even with180 from other gear.
        p.equipment[EquipmentType.WEAPON.id] = null
        assertTrue(step.qualifies(p, robin))
    }

    @Test fun `full inventory and a second account cannot duplicate a component or advance a trail`() {
        val first = account(); val second = account()
        val dig = step.components.indexOfFirst { it.action == TreasureTrailStepCatalogue.Action.DIG }
        (1..27).forEach { first.inventory[it] = Item(995) }
        assertEquals(TreasureTrailTornParts.Obtain.NO_SPACE, TreasureTrailTornParts.obtain(first, step, dig))
        assertEquals(0, ClueScrollManager.progress(first, ClueScrollTier.MASTER))
        first.inventory[1] = null
        assertEquals(TreasureTrailTornParts.Obtain.GIVEN, TreasureTrailTornParts.obtain(first, step, dig))
        assertEquals(TreasureTrailTornParts.Obtain.ALREADY_HELD, TreasureTrailTornParts.obtain(first, step, dig))
        assertEquals(TreasureTrailTornParts.Obtain.GIVEN, TreasureTrailTornParts.obtain(second, step, dig))
        assertEquals(1, first.inventory.getItemCount(partIds[dig]))
        assertEquals(1, second.inventory.getItemCount(partIds[dig]))
        assertEquals(0, ClueScrollManager.progress(second, ClueScrollTier.MASTER))
        assertTrue(partIds.all(ClueDeathItems::contains))
    }

    @Test fun `juggled physical parts combine once and missing parts or stale clicks spend nothing`() {
        val p = account()
        p.inventory[1] = Item(partIds[0]); p.inventory[2] = Item(partIds[1])
        val click = p.inventory[1]!!
        assertEquals(TreasureTrailTornParts.Combine.MISSING_PARTS, TreasureTrailTornParts.combine(p, click))
        assertEquals(2, partIds.sumOf { p.inventory.getItemCount(it) })
        p.inventory[3] = Item(partIds[2])
        // No clue-specific token is attached to parts; parts from a different
        // three-part clue remain valid, as the source explicitly permits.
        assertTrue(p.inventory[3]!!.attr.isEmpty())
        assertEquals(TreasureTrailTornParts.Combine.COMBINED, TreasureTrailTornParts.combine(p, click))
        assertEquals(1, ClueScrollManager.progress(p, ClueScrollTier.MASTER))
        assertEquals(0, partIds.sumOf { p.inventory.getItemCount(it) })
        assertEquals(TreasureTrailTornParts.Combine.STALE, TreasureTrailTornParts.combine(p, click))
        assertEquals(1, ClueScrollManager.progress(p, ClueScrollTier.MASTER))
    }

    @Test fun `combining against an unrelated master discards parts without progressing that clue`() {
        val p = account()
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, single.persistenceId)
        partIds.forEachIndexed { i, id -> p.inventory[i+1] = Item(id) }
        assertEquals(TreasureTrailTornParts.Combine.DISCARDED_INCOMPATIBLE, TreasureTrailTornParts.combine(p, p.inventory[1]!!))
        assertTrue(partIds.all { !p.inventory.contains(it) })
        assertEquals(0, ClueScrollManager.progress(p, ClueScrollTier.MASTER))
        assertEquals(single.persistenceId, p.inventory[0]!!.getAttr(ItemAttribute.CLUE_STEP_KEY))
    }
}
