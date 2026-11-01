package gg.rsmod.plugins.content.skills

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.game.ItemMetadataService
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.items.osrs.MaxCapes
import gg.rsmod.plugins.content.items.osrs.MaxCapeLooks
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.BufferedReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real items.yml -> ItemMetadataService -> EquipAction, without a server or cache writes. */
class SkillcapeEquipTests {
    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val plugins = mockk<PluginRepository>(relaxed = true)
        every { p.world } returns world
        every { world.definitions } returns definitions
        every { world.plugins } returns plugins
        every { plugins.executeEquipItemRequirement(any(), any()) } returns true
        every { plugins.canUnequipSlot(any(), any()) } returns true
        // The same callbacks registered by skillcape_boost.plugin.kts, through the real swap/remove paths.
        every { plugins.executeEquipItem(any(), any()) } answers {
            SkillcapeBoost.equip(firstArg(), secondArg())
            true
        }
        every { plugins.executeUnequipItem(any(), any()) } answers {
            SkillcapeBoost.remove(firstArg(), secondArg())
            true
        }
        every { p.attr } returns AttributeMap()
        every { p.skills } returns SkillSet(25).also { skills ->
            (0..23).forEach { skills.setBaseLevel(it, 99); skills.setCurrentLevel(it, 99) }
        }
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        return p
    }

    @Test
    fun `every skill cape trimmed cape and hood requires its own real 99`() {
        assertEquals(24, Skillcapes.values().size)
        Skillcapes.values().forEach { cape ->
            val skill = SkillcapeSeller.skill(cape)
            listOf(cape.untrimmedCape, cape.trimmedCape, cape.hood).forEach { id ->
                assertEquals(mapOf(skill.toByte() to 99.toByte()), items.getValue(id).skillReqs!!.toMap(), "item $id")
                val p = player()
                p.skills.setBaseLevel(skill, 98)
                p.skills.setCurrentLevel(skill, 100)
                assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, EquipAction.equip(p, Item(id)), "item $id")
                assertTrue(p.equipment.isEmpty)
                p.skills.setBaseLevel(skill, 99)
                p.skills.setCurrentLevel(skill, 97)
                assertEquals(EquipAction.Result.SUCCESS, EquipAction.equip(p, Item(id)), "item $id")
                assertEquals(id, p.equipment[items.getValue(id).equipSlot]?.id)
                assertEquals(97, p.skills.getCurrentLevel(skill), "drained skill must not be restored")
            }
        }
    }

    @Test
    fun `every max cape hood locked and look variant requires all 24 skills but no Dungeoneering`() {
        assertEquals(28, maxItems.size)
        maxItems.forEach { id ->
            assertEquals((0..23).associate { it.toByte() to 99.toByte() }, items.getValue(id).skillReqs!!.toMap(), "item $id")
            val p = player()
            (0..23).forEach { skill ->
                p.skills.setBaseLevel(skill, 98)
                p.skills.setCurrentLevel(skill, 100)
                assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, EquipAction.equip(p, Item(id)), "item $id skill $skill")
                p.skills.setBaseLevel(skill, 99)
            }
            p.skills.setBaseLevel(Skills.DUNGEONEERING, 1)
            p.skills.setCurrentLevel(Skills.SUMMONING, 97)
            assertEquals(EquipAction.Result.SUCCESS, EquipAction.equip(p, Item(id)), "item $id")
            assertEquals(97, p.skills.getCurrentLevel(Skills.SUMMONING))
        }
    }

    @Test
    fun `Summoning refusal uses the existing message format without an out of bounds error`() {
        val p = player()
        p.skills.setBaseLevel(Skills.SUMMONING, 98)
        assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, EquipAction.equip(p, Item(Skillcapes.SUMMONING.hood)))
        assertEquals("summoning", EquipAction.SKILL_NAMES[Skills.SUMMONING])
    }

    @Test
    fun `a temporary drain keeps a worn cape and removing it never restores the drain`() {
        val p = player()
        assertEquals(EquipAction.Result.SUCCESS, EquipAction.equip(p, Item(Skillcapes.ATTACK.untrimmedCape)))
        assertEquals(100, p.skills.getCurrentLevel(Skills.ATTACK))
        p.skills.setCurrentLevel(Skills.ATTACK, 97)
        assertEquals(Skillcapes.ATTACK.untrimmedCape, p.equipment[1]?.id)
        assertFalse(SkillcapeBoost.apply(p, Skillcapes.ATTACK))
        assertEquals(EquipAction.Result.SUCCESS, EquipAction.unequip(p, 1))
        assertEquals(97, p.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `hood and cape cannot double boost in either order and only cape removal clears boost`() {
        listOf(false, true).forEach { hoodFirst ->
            val p = player()
            val cape = Skillcapes.CONSTRUCTION
            if (hoodFirst) EquipAction.equip(p, Item(cape.hood))
            assertEquals(99, p.skills.getCurrentLevel(Skills.CONSTRUCTION))
            EquipAction.equip(p, Item(cape.untrimmedCape))
            if (!hoodFirst) EquipAction.equip(p, Item(cape.hood))
            assertEquals(100, p.skills.getCurrentLevel(Skills.CONSTRUCTION))
            assertFalse(SkillcapeBoost.apply(p, cape))
            EquipAction.unequip(p, 0)
            assertEquals(100, p.skills.getCurrentLevel(Skills.CONSTRUCTION))
            EquipAction.unequip(p, 1)
            assertEquals(99, p.skills.getCurrentLevel(Skills.CONSTRUCTION))
        }
    }

    @Test
    fun `replacing a boosted cape removes its boost and applies the new cape boost`() {
        val p = player()
        EquipAction.equip(p, Item(Skillcapes.ATTACK.trimmedCape))
        EquipAction.equip(p, Item(Skillcapes.MINING.trimmedCape))
        assertEquals(99, p.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(100, p.skills.getCurrentLevel(Skills.MINING))
        EquipAction.unequip(p, 1)
        assertEquals(99, p.skills.getCurrentLevel(Skills.MINING))
    }

    @Test
    fun `removing cape preserves independent potion boosts and stronger boosts`() {
        listOf(false, true).forEach { capeBoost ->
            val p = player()
            if (!capeBoost) p.skills.setCurrentLevel(Skills.ATTACK, 100)
            EquipAction.equip(p, Item(Skillcapes.ATTACK.untrimmedCape))
            if (capeBoost) p.skills.setCurrentLevel(Skills.ATTACK, 118)
            EquipAction.unequip(p, 1)
            assertEquals(if (capeBoost) 118 else 100, p.skills.getCurrentLevel(Skills.ATTACK))
        }
    }

    @Test
    fun `max cape perks survive the equipment gate for plain variant and locked capes`() {
        (setOf(MaxCapes.MAX_CAPE) + MaxCapes.WEARABLE_VARIANTS).forEach { id ->
            val p = player()
            assertEquals(EquipAction.Result.SUCCESS, EquipAction.equip(p, Item(id)))
            assertTrue(SkillcapePerks.wearingAnyMaxCape(p))
            SkillcapePerks.VariantPerk.values().forEach { assertTrue(SkillcapePerks.worn(p, it)) }
            assertEquals(id == MaxCapes.MAX_CAPE, SkillcapePerks.worn(p, Skillcapes.ATTACK))
            assertEquals(99, p.skills.getCurrentLevel(Skills.ATTACK), "max cape never boosts")
        }
    }

    @Test
    fun `every selected max look retains the plain cape requirements and perks`() {
        MaxCapes.VARIANTS.forEach { variant ->
            val p = player()
            p.inventory[0] = Item(variant.component)
            assertTrue(MaxCapeLooks.unlock(p, variant.cape))
            MaxCapeLooks.select(p, variant.cape)
            p.skills.setBaseLevel(Skills.SUMMONING, 98)
            assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, EquipAction.equip(p, Item(MaxCapes.MAX_CAPE)))
            p.skills.setBaseLevel(Skills.SUMMONING, 99)
            assertEquals(EquipAction.Result.SUCCESS, EquipAction.equip(p, Item(MaxCapes.MAX_CAPE)))
            assertEquals(variant.cape, MaxCapeLooks.shownCape(p, MaxCapes.MAX_CAPE))
            Skillcapes.values().forEach { assertTrue(SkillcapePerks.worn(p, it)) }
        }
    }

    @Test
    fun `production plugin registers equip and removal hooks for capes only`() {
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/skills/skillcape_boost.plugin.kts").readText()
        assertTrue("listOf(cape.untrimmedCape, cape.trimmedCape)" in script)
        assertTrue("on_item_equip(item) { SkillcapeBoost.equip(player, item) }" in script)
        assertTrue("on_item_unequip(item) { SkillcapeBoost.remove(player, item) }" in script)
    }

    companion object {
        private val maxItems = setOf(MaxCapes.MAX_CAPE, MaxCapes.MAX_HOOD, 20751) +
            MaxCapes.WEARABLE_VARIANTS + MaxCapes.VARIANTS.map { it.hood }
        private val items = (maxItems + Skillcapes.values().flatMap { listOf(it.untrimmedCape, it.trimmedCape, it.hood) })
            .associateWith { ItemDef(it) }
        private val definitions = mockk<DefinitionSet>(relaxed = true)

        @BeforeClass
        @JvmStatic
        fun loadMetadata() {
            // Metadata loader walks cache indices; the fixture exposes only the cape definitions under test.
            val entries = items.values.toList()
            every { definitions.getCount(ItemDef::class.java) } returns entries.size
            every { definitions.get(ItemDef::class.java, any()) } answers {
                val id = secondArg<Int>()
                items[id] ?: entries[id]
            }
            val load = ItemMetadataService::class.java.getDeclaredMethod("load", DefinitionSet::class.java, BufferedReader::class.java)
            load.isAccessible = true
            File("../../data/cfg/items.yml").bufferedReader().use { load.invoke(ItemMetadataService(), definitions, it) }
        }
    }
}
