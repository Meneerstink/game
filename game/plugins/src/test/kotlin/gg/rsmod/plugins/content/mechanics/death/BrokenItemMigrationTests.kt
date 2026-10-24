package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrokenItemMigrationTests {
    @Test
    fun `an old marked broken item becomes the OSRS broken item and repairs back at the same price`() {
        val old = Item(Items.VOID_MELEE_HELM).also { it.attr[ItemAttribute.BROKEN] = UntradeableDeathProtection.STATE_BROKEN }
        val now = BrokenItemMigration.converted(old)!!
        assertEquals(Items.VOID_MELEE_HELM_BROKEN, now.id)
        assertEquals(0, UntradeableDeathProtection.state(now), "the hidden mark is gone")
        assertEquals(Items.VOID_MELEE_HELM, UntradeableDeathProtection.repaired(now).id)
    }

    @Test
    fun `a whole item and an item without an imported broken form are left alone`() {
        assertNull(BrokenItemMigration.converted(Item(Items.VOID_MELEE_HELM)))
        val marked = Item(Items.GLOVES_7462).also { it.attr[ItemAttribute.BROKEN] = UntradeableDeathProtection.STATE_BROKEN }
        assertNull(BrokenItemMigration.converted(marked))
    }
}
