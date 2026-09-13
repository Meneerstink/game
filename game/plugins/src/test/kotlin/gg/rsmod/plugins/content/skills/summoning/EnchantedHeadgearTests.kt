package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RCV-010 A5: every enchanted-headgear row against the 667 cache, and the enchant/store/spend lifecycle. */
class EnchantedHeadgearTests {
    private val sent = mutableListOf<String>()

    private fun player(summoning: Int = 99): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        skills.setBaseLevel(Skills.SUMMONING, summoning)
        skills.setCurrentLevel(Skills.SUMMONING, summoning)
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.skills } returns skills
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.write(*varargAll<Message> { (it as? MessageGameMessage)?.let { m -> sent.add(m.message) }; true }) } just Runs
        return player
    }

    private fun name(id: Int) = DEFINITIONS.get(ItemDef::class.java, id).name

    @Test
    fun `every headgear row uses the 667 cache's base, enchanted and charged items`() {
        val offenders = EnchantedHeadgear.rows.flatMap { row ->
            buildList {
                if (name(row.charged) != name(row.base) + " (charged)") add("charged ${row.charged} '${name(row.charged)}' for '${name(row.base)}'")
                if (row.enchanted != row.base && name(row.enchanted) != name(row.base) + " (e)") add("enchanted ${row.enchanted} '${name(row.enchanted)}' for '${name(row.base)}'")
            }
        }
        assertTrue(offenders.isEmpty(), "headgear rows not matching cache names:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `pikkupstix offers Enchant and charged helms offer their cache options`() {
        val enchanters = listOf(Npcs.PIKKUPSTIX, Npcs.PIKKUPSTIX_6971, Npcs.PIKKUPSTIX_7952, Npcs.PIKKUPSTIX_7953)
            .filter { id -> DEFINITIONS.get(NpcDef::class.java, id).options.any { it.equals("Enchant", ignoreCase = true) } }
        println("EnchantedHeadgearTests: pikkupstix with Enchant=$enchanters; charged menus=" +
            EnchantedHeadgear.rows.map { name(it.charged) + " inv=" + DEFINITIONS.get(ItemDef::class.java, it.charged).inventoryMenu.filterNotNull() +
                " worn=" + DEFINITIONS.get(ItemDef::class.java, it.charged).equipmentMenu.filterNotNull() })
        assertTrue(enchanters.isNotEmpty(), "no Pikkupstix id carries the Enchant option")
        assertTrue(EnchantedHeadgear.combatScrollIds.isNotEmpty())
    }

    @Test
    fun `enchant, level gate, disenchant and charged refusal follow Void`() {
        val low = player(summoning = 29)
        low.inventory[0] = Item(Items.RUNE_FULL_HELM)
        EnchantedHeadgear.enchant(low, Items.RUNE_FULL_HELM, 0)
        assertEquals(Items.RUNE_FULL_HELM, low.inventory[0]!!.id)
        assertEquals("You need a Summoning level of 30 to enchant that helmet.", sent.last())

        EnchantedHeadgear.rows.forEach { row ->
            val p = player()
            p.inventory[0] = Item(row.base)
            EnchantedHeadgear.enchant(p, row.base, 0)
            assertEquals(row.enchanted, p.inventory[0]!!.id, name(row.base))
            if (row.enchanted != row.base) {
                EnchantedHeadgear.enchant(p, row.enchanted, 0)
                assertEquals(row.base, p.inventory[0]!!.id, "disenchant ${name(row.base)}")
            }
            p.inventory[0] = Item(row.charged)
            EnchantedHeadgear.enchant(p, row.charged, 0)
            assertEquals(row.charged, p.inventory[0]!!.id)
            assertEquals("You need to remove the scrolls before I can work on that helmet.", sent.last())
        }
    }

    @Test
    fun `scrolls fill to capacity, refuse other types, and a worn helm supplies and empties`() {
        val combat = EnchantedHeadgear.combatScrollIds.first()
        val other = EnchantedHeadgear.combatScrollIds.first { it != combat }
        val nonCombat = SummoningSpecialMoves.bindings.first { it.target == FamiliarSpecialTarget.INSTANT }.scroll.scroll
        val row = EnchantedHeadgear.rows.first { it.base == Items.ANTLERS }
        val p = player()
        p.inventory[0] = Item(row.enchanted)
        p.inventory[1] = Item(nonCombat, 5)
        EnchantedHeadgear.store(p, nonCombat, 0)
        assertEquals("Only combat scrolls can be stored in headgear.", sent.last())

        p.inventory[2] = Item(combat, 50)
        EnchantedHeadgear.store(p, combat, 0)
        assertEquals(row.charged, p.inventory[0]!!.id)
        assertEquals(row.capacity, p.attr[EnchantedHeadgear.COUNT_ATTR])
        assertEquals(50 - row.capacity, p.inventory.getItemCount(combat))
        EnchantedHeadgear.store(p, combat, 0)
        assertEquals("The helmet is full.", sent.last())

        p.inventory[3] = Item(other, 1)
        p.attr[EnchantedHeadgear.COUNT_ATTR] = 1
        EnchantedHeadgear.store(p, other, 0)
        assertTrue(sent.last().startsWith("This helmet already holds"))

        p.equipment[EquipmentType.HEAD.id] = Item(row.charged)
        p.inventory[0] = null
        p.attr[EnchantedHeadgear.COUNT_ATTR] = 2
        assertEquals(combat, EnchantedHeadgear.wornScroll(p))
        EnchantedHeadgear.spendWornScroll(p)
        assertEquals(row.charged, p.equipment[EquipmentType.HEAD.id]!!.id)
        EnchantedHeadgear.spendWornScroll(p)
        assertEquals(row.enchanted, p.equipment[EquipmentType.HEAD.id]!!.id)
        assertNull(EnchantedHeadgear.wornScroll(p))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
