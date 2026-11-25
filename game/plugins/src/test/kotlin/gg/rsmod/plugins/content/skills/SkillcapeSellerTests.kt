package gg.rsmod.plugins.content.skills

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertSame
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/** K03 N410 part 1: every OSRS Cape of Accomplishment seller sells its cape, and the cape follows the 667 skill ids. */
class SkillcapeSellerTests {
    @Test
    fun `each cape requires a real 99 and pays once for the matching cape and hood`() {
        Skillcapes.values().forEach { cape ->
            val f = SkillcapeFixture()
            f.skills.setBaseLevel(cape.ordinal, 98)
            f.skills.setCurrentLevel(cape.ordinal, 100)
            f.inventory[0] = Item(Items.COINS_995, SkillcapeSeller.PRICE)
            assertFalse(Skills.purchaseSkillcape(f.player, cape), "boosted 98 $cape")
            assertEquals(SkillcapeSeller.PRICE, f.inventory.getItemCount(Items.COINS_995))
            f.skills.setBaseLevel(cape.ordinal, 99)
            f.skills.setCurrentLevel(cape.ordinal, 40)
            assertTrue(Skills.purchaseSkillcape(f.player, cape), "drained real 99 $cape")
            assertEquals(1, f.inventory.getItemCount(cape.untrimmedCape))
            assertEquals(1, f.inventory.getItemCount(cape.hood))
            assertEquals(0, f.inventory.getItemCount(Items.COINS_995))
            assertFalse(Skills.purchaseSkillcape(f.player, cape), "second click without payment $cape")
            assertEquals(1, f.inventory.getItemCount(cape.untrimmedCape))
        }
    }

    @Test
    fun `full inventory and insufficient coins leave every original item untouched`() {
        val f = SkillcapeFixture()
        val cape = Skillcapes.ATTACK
        f.skills.setBaseLevel(Skills.ATTACK, 99)
        f.fillThrough(27)
        val coins = Item(Items.COINS_995, SkillcapeSeller.PRICE)
        f.inventory[0] = coins
        assertFalse(Skills.purchaseSkillcape(f.player, cape))
        assertSame(coins, f.inventory[0])
        f.inventory[27] = null
        assertFalse(Skills.purchaseSkillcape(f.player, cape))
        assertSame(coins, f.inventory[0])
        f.inventory[26] = null
        coins.amount--
        assertFalse(Skills.purchaseSkillcape(f.player, cape))
        assertEquals(SkillcapeSeller.PRICE - 1, coins.amount)
        assertEquals(0, f.inventory.getItemCount(cape.hood))
        coins.amount++
        val unrelated = f.inventory[1]
        assertTrue(Skills.purchaseSkillcape(f.player, cape))
        assertSame(unrelated, f.inventory[1])
    }

    @Test
    fun `second real 99 buys trimmed cape and trims previous owned capes permanently`() {
        val f = SkillcapeFixture()
        f.skills.setBaseLevel(Skills.ATTACK, 99)
        f.skills.setBaseLevel(Skills.COOKING, 99)
        f.inventory[0] = Item(Items.COINS_995, SkillcapeSeller.PRICE * 2)
        f.bank[4] = Item(Items.ATTACK_CAPE)
        assertTrue(Skills.purchaseSkillcape(f.player, Skillcapes.COOKING))
        assertEquals(Items.ATTACK_CAPE_T, f.bank[4]?.id)
        assertEquals(1, f.inventory.getItemCount(Items.COOKING_CAPE_T))
        assertTrue(Skills.purchaseSkillcape(f.player, Skillcapes.COOKING))
        assertEquals(2, f.inventory.getItemCount(Items.COOKING_CAPE_T))
        assertEquals(2, f.inventory.getItemCount(Items.COOKING_HOOD))
        assertEquals(0, f.inventory.getItemCount(Items.COINS_995))
        f.skills.setBaseLevel(Skills.COOKING, 98)
        SkillcapeTrim.trimAll(f.player)
        assertEquals(Items.ATTACK_CAPE_T, f.bank[4]?.id)
    }
    @Test
    fun `the sellers that had no cape dialogue sell their skill's cape`() {
        val sold = SkillcapeSeller.SELLERS.values.toSet()
        listOf(Skillcapes.RANGED, Skillcapes.AGILITY, Skillcapes.THIEVING, Skillcapes.SLAYER, Skillcapes.RUNECRAFTING, Skillcapes.HUNTER, Skillcapes.SUMMONING)
            .forEach { assertTrue(it in sold, "$it has no seller") }
        assertEquals(Skillcapes.RUNECRAFTING, SkillcapeSeller.SELLERS[Npcs.AUBURY])
        assertEquals(Skillcapes.SLAYER, SkillcapeSeller.SELLERS[Npcs.DURADEL_8466])
        assertEquals(99_000, SkillcapeSeller.PRICE)
    }

    @Test
    fun `the cape enum follows the skill ids`() {
        assertEquals(Skills.ATTACK, SkillcapeSeller.skill(Skillcapes.ATTACK))
        assertEquals(Skills.RANGED, SkillcapeSeller.skill(Skillcapes.RANGED))
        assertEquals(Skills.AGILITY, SkillcapeSeller.skill(Skillcapes.AGILITY))
        assertEquals(Skills.RUNECRAFTING, SkillcapeSeller.skill(Skillcapes.RUNECRAFTING))
        assertEquals(Skills.SUMMONING, SkillcapeSeller.skill(Skillcapes.SUMMONING))
    }

    @Test
    fun `N410 - every one of the 24 skillcapes can be bought from a master, never only from Max`() {
        val sources =
            java.io.File("src/main/kotlin/gg/rsmod/plugins/content").walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".kts") || it.name.endsWith(".kt")) }
                .map { it.readText() }
                .filter { "purchaseSkillcape(" in it || "SkillcapeSeller.sell(" in it }
                .toList()
        val missing =
            Skillcapes.values().filter { cape ->
                cape !in SkillcapeSeller.SELLERS.values && sources.none { "Skillcapes.${cape.name}" in it }
            }
        assertTrue(missing.isEmpty(), "no master sells: $missing")
        assertEquals(Skillcapes.CONSTRUCTION, SkillcapeSeller.SELLERS[Npcs.ESTATE_AGENT])
    }
}
