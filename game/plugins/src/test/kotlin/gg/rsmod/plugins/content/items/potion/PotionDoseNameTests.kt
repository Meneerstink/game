package gg.rsmod.plugins.content.items.potion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N11 (2026-10-31): the potion "Empty" rule bound every "(n)" item with an Empty option - "Potatoes (2)" became an empty vial and the
 * produce containers' own Empty crashed the boot (item 5462 bound twice). Fruit baskets, sacks and seaweed nets are never doses.
 */
class PotionDoseNameTests {
    @Test
    fun `real potions read as doses`() {
        listOf("Super attack (4)", "Prayer potion (1)", "Super combat potion(4)", "Anti-venom+(2)", "Olive oil (3)", "Menaphite remedy(1)")
            .forEach { assertTrue(PotionDecanting.isDoseName(it), it) }
    }

    @Test
    fun `produce containers and other names never read as doses`() {
        listOf(
            "Apples (2)", "Oranges (4)", "Strawberries (3)", "Bananas (2)", "Tomatoes (4)", "Potatoes (2)", "Onions (3)", "Cabbages (2)",
            "Seaweed net (1)", "Seaweed net (4)", "Cabbages (10)", "Vial", "Super attack",
        ).forEach { assertFalse(PotionDecanting.isDoseName(it), it) }
    }
}
