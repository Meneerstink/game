package gg.rsmod.game.message.handler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item PKT / N2: ClientProt OPLOCT (42) is one packet for "Use item -> object" and "Cast -> object". Only the inventory
 * (679) is an item use; every spell or familiar source must reach the spell-on-object plugin (Void
 * InterfaceOnObjectOptionHandler: item present -> interactItemOn, otherwise interactOn).
 */
class OpLocTRoutingTests {
    @Test
    fun `an inventory item used on an object is an item use`() {
        assertTrue(OpLocTHandler.isItemUse(679))
    }

    @Test
    fun `every spellbook and the familiar special sources are casts`() {
        // Modern 192, Ancient 193, Lunar 430 spellbooks; Summoning orb 747 and familiar panel 662 (special moves on objects).
        listOf(192, 193, 430, 747, 662).forEach { parent ->
            assertFalse("interface $parent must cast on the object, not look for an inventory item", OpLocTHandler.isItemUse(parent))
        }
    }
}
