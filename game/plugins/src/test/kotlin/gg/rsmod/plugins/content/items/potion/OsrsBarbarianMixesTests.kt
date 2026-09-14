package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every 667 barbarian mix against the OSRS Wiki Barbarian Training "Heals" table and its base potion (whole roster, 23 mixes). */
class OsrsBarbarianMixesTests {
    /** 2-dose id, 1-dose id, base potion type, heal (wiki table). */
    private val wiki =
        listOf(
            Triple(Items.ATTACK_MIX_2 to Items.ATTACK_MIX_1, PotionType.ATTACK, 3),
            Triple(Items.ANTIPOISON_MIX_2 to Items.ANTIPOISON_MIX_1, PotionType.ANTIPOISON, 3),
            Triple(Items.STRENGTH_MIX_2 to Items.STRENGTH_MIX_1, PotionType.STRENGTH, 3),
            Triple(Items.RESTORE_MIX_2 to Items.RESTORE_MIX_1, PotionType.RESTORE, 3),
            Triple(Items.ENERGY_MIX_2 to Items.ENERGY_MIX_1, PotionType.ENERGY, 3),
            Triple(Items.DEFENCE_MIX_2 to Items.DEFENCE_MIX_1, PotionType.DEFENCE, 6),
            Triple(Items.AGILITY_MIX_2 to Items.AGILITY_MIX_1, PotionType.AGILITY, 6),
            Triple(Items.COMBAT_MIX_2 to Items.COMBAT_MIX_1, PotionType.COMBAT, 3),
            Triple(Items.PRAYER_MIX_2 to Items.PRAYER_MIX_1, PotionType.PRAYER, 6),
            Triple(Items.SUPER_ATTACK_MIX_2 to Items.SUPER_ATTACK_MIX_1, PotionType.SUPER_ATTACK, 6),
            Triple(Items.ANTIP_SUPERMIX_2 to Items.ANTIP_SUPERMIX_1, PotionType.SUPER_ANTIPOISON, 6),
            Triple(Items.FISHING_MIX_2 to Items.FISHING_MIX_1, PotionType.FISHING, 6),
            Triple(Items.SUPER_ENERGY_MIX_2 to Items.SUPER_ENERGY_MIX_1, PotionType.SUPER_ENERGY, 6),
            Triple(Items.HUNTING_MIX_2 to Items.HUNTING_MIX_1, PotionType.HUNTER, 6),
            Triple(Items.SUPER_STRENGTH_MIX_2 to Items.SUPER_STRENGTH_MIX_1, PotionType.SUPER_STRENGTH, 6),
            Triple(Items.MAGIC_ESSENCE_MIX_2 to Items.MAGIC_ESSENCE_MIX_1, PotionType.MAGIC_ESSENCE, 6),
            Triple(Items.SUPER_RESTORE_MIX_2 to Items.SUPER_RESTORE_MIX_1, PotionType.SUPER_RESTORE, 6),
            Triple(Items.SUPER_DEFENCE_MIX_2 to Items.SUPER_DEFENCE_MIX_1, PotionType.SUPER_DEFENCE, 6),
            Triple(Items.ANTIDOTE_MIX_2 to Items.ANTIDOTE_MIX_1, PotionType.ANTIPOISON_PLUS, 6),
            Triple(Items.ANTIFIRE_MIX_2 to Items.ANTIFIRE_MIX_1, PotionType.ANTIFIRE, 6),
            Triple(Items.RANGING_MIX_2 to Items.RANGING_MIX_1, PotionType.RANGING, 6),
            Triple(Items.MAGIC_MIX_2 to Items.MAGIC_MIX_1, PotionType.MAGIC, 6),
            Triple(Items.ZAMORAK_MIX_2 to Items.ZAMORAK_MIX_1, PotionType.ZAMORAK_BREW, 6),
        )

    @Test
    fun `every 667 mix drinks two doses of its base potion with the wiki heal`() {
        assertEquals(23, wiki.size)
        wiki.forEach { (ids, type, heal) ->
            val two = Potion.values().single { it.item == ids.first }
            val one = Potion.values().single { it.item == ids.second }
            assertEquals(listOf(ids.second, Items.VIAL), listOf(two.replacement, one.replacement), "$two")
            assertEquals(listOf(type, type), listOf(two.potionType, one.potionType), "$two")
            assertEquals(listOf(heal, heal), listOf(two.mixHeal, one.mixHeal), "$two")
        }
        val mixes = Potion.values().filter { it.mixHeal > 0 }.map { it.item }.toSet()
        assertEquals(wiki.flatMap { listOf(it.first.first, it.first.second) }.toSet(), mixes, "no other potion carries a mix heal")
        assertTrue(Potion.values().none { it.item == Items.RELICYMS_MIX_2 || it.item == Items.RELICYMS_MIX_1 }, "Relicym's mix stays BLOCKED")
    }

    @Test
    fun `drinking a mix applies the base effect then heals with the lumpy message`() {
        assertEquals("You drink the lumpy potion", BarbarianMixes.MESSAGE)
        val drink = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/Potions.kt").readText()
        val apply = drink.indexOf("potion.potionType.apply(player)")
        val heal = drink.indexOf("player.heal(potion.mixHeal)")
        assertTrue(apply in 0 until heal && "player.filterableMessage(BarbarianMixes.MESSAGE)" in drink)
    }
}
