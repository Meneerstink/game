package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.magic.SpellType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuneFreeTeleportRequirementsTests {
 @Test
 fun `spellbook teleports retain no rune requirements`() {
 SpellbookData.values()
 .filter { it.spellType == SpellType.TELEPORT_SPELL_TYPE }
 .forEach { spell ->
 val retained = RuneFreeTeleportRequirements.nonRuneRequirements(spell.runes)
 assertTrue(retained.none { RuneFreeTeleportRequirements.isRune(it.id) }, spell.spellName)
 }
 }

 @Test
 fun `ape atoll teleport still requires its banana`() {
 val retained =
 RuneFreeTeleportRequirements.nonRuneRequirements(
 SpellbookData.APE_ATOLL_TELEPORT.runes,
 )

 assertEquals(1, retained.size)
 assertEquals(Items.BANANA, retained.single().id)
 assertEquals(1, retained.single().amount)
 }

 @Test
 fun `only runes are removed from mixed requirements`() {
 val requirements =
 listOf(
 Item(Items.AIR_RUNE, 3),
 Item(Items.LAW_RUNE, 1),
 Item(Items.BANANA, 1),
 )

 val retained = RuneFreeTeleportRequirements.nonRuneRequirements(requirements)

 assertEquals(1, retained.size)
 assertEquals(Items.BANANA, retained.single().id)
 assertEquals(1, retained.single().amount)
 assertFalse(RuneFreeTeleportRequirements.isRune(Items.BANANA))
 }
}
