package gg.rsmod.plugins.content.skills

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D5 (owner 10-11): Max sells every 667 skillcape except Dungeoneering for 99,000 with its hood, only with a real 99, and capes
 * are trimmed automatically once the player has two 99s (OSRS Wiki "Cape of Accomplishment": in inventory, bank, worn).
 */
class SkillcapeTrimTests {
    private val definitions = mockk<DefinitionSet>(relaxed = true)

    private fun player(vararg ninetyNines: Int): Player {
        val skills = SkillSet(25)
        ninetyNines.forEach { skills.setBaseLevel(it, 99) }
        val p = mockk<Player>(relaxed = true)
        every { p.skills } returns skills
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        return p
    }

    @Test
    fun `the second 99 trims every owned cape - inventory, worn and banked`() {
        val p = player(Skills.ATTACK, Skills.COOKING)
        p.inventory[0] = Item(Items.ATTACK_CAPE)
        p.equipment[1] = Item(Items.COOKING_CAPE)
        p.bank[5] = Item(Items.SUMMONING_CAPE)
        assertEquals(3, SkillcapeTrim.trimAll(p))
        assertEquals(Items.ATTACK_CAPE_T, p.inventory[0]!!.id)
        assertEquals(Items.COOKING_CAPE_T, p.equipment[1]!!.id)
        assertEquals(Items.SUMMONING_CAPE_T, p.bank[5]!!.id)
    }

    @Test
    fun `one 99 keeps the cape untrimmed, and Dungeoneering never counts`() {
        val one = player(Skills.ATTACK)
        one.inventory[0] = Item(Items.ATTACK_CAPE)
        assertEquals(0, SkillcapeTrim.trimAll(one))
        assertEquals(Items.ATTACK_CAPE, one.inventory[0]!!.id)

        val withDgn = player(Skills.ATTACK, Skills.DUNGEONEERING)
        withDgn.inventory[0] = Item(Items.ATTACK_CAPE)
        assertFalse(Skills.hasTwo99s(withDgn), "DGN: Dungeoneering is removed")
        assertEquals(Items.ATTACK_CAPE, withDgn.inventory[0]!!.id)
    }

    @Test
    fun `the cape enum follows the skill ids, so the shop's 99 rule asks the right skill`() {
        assertEquals(Skills.ATTACK, Skillcapes.ATTACK.ordinal)
        assertEquals(Skills.CONSTITUTION, Skillcapes.CONSTITUTION.ordinal)
        assertEquals(Skills.RUNECRAFTING, Skillcapes.RUNECRAFTING.ordinal)
        assertEquals(Skills.HUNTER, Skillcapes.HUNTER.ordinal)
        assertEquals(Skills.CONSTRUCTION, Skillcapes.CONSTRUCTION.ordinal)
        assertEquals(Skills.SUMMONING, Skillcapes.SUMMONING.ordinal)
        assertEquals(24, SkillcapeTrim.TRIMMED.size, "every cape but Dungeoneering")
    }

    @Test
    fun `Max's shop stocks the 24 capes at 99,000 with hood, 99 rule and trim`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/max_cape.plugin.kts").readText()
        assertTrue("val SKILLCAPE_PRICE = 99_000" in plugin)
        assertTrue(Skillcapes.values().none { it.name == "DUNGEONEERING" }, "DGN: no Dungeoneering cape is sold")
        assertTrue("p.skills.getMaxLevel(cape.ordinal) >= 99" in plugin, "a real 99, not a boosted level")
        assertTrue("bonusItems = listOf(cape.hood)" in plugin && "SkillcapeTrim.trimAll(p)" in plugin)
        assertTrue("items[i + 1] = gg.rsmod.game.model.shop.ShopItem(cape.untrimmedCape, 10, sellPrice = SKILLCAPE_PRICE)" in plugin)
    }
}
