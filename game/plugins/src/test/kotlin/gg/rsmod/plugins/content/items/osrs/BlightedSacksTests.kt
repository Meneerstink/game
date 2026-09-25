package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.magic.SpellbookData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT Blighted sacks: which spells each sack powers, where, and that the rune check uses them. */
class BlightedSacksTests {
    @Test
    fun `each sack powers exactly the spells on its wiki page`() {
        assertEquals(
            setOf(SpellbookData.ICE_RUSH, SpellbookData.ICE_BURST, SpellbookData.ICE_BLITZ, SpellbookData.ICE_BARRAGE).map { it.uniqueId }.toSet(),
            BlightedSacks.Sack.ANCIENT_ICE.spells,
        )
        assertEquals(setOf(SpellbookData.BIND, SpellbookData.SNARE, SpellbookData.ENTANGLE).map { it.uniqueId }.toSet(), BlightedSacks.Sack.ENTANGLE.spells)
        assertEquals(setOf(SpellbookData.TELEPORT_BLOCK.uniqueId), BlightedSacks.Sack.TELEPORT_SPELL.spells)
        assertEquals(setOf(SpellbookData.VENGEANCE, SpellbookData.VENGEANCE_OTHER).map { it.uniqueId }.toSet(), BlightedSacks.Sack.VENGEANCE.spells)
        assertFalse(BlightedSacks.Sack.TELEPORT_SPELL.wildernessOnly, "owner 2026-09-22: every sack works everywhere outside safe zones")
        assertFalse(BlightedSacks.Sack.ENTANGLE.wildernessOnly)
        assertFalse(BlightedSacks.Sack.ANCIENT_ICE.wildernessOnly)
        assertFalse(BlightedSacks.Sack.VENGEANCE.wildernessOnly)
        assertEquals(BlightedSacks.Sack.ENTANGLE, BlightedSacks.sackFor(SpellbookData.SNARE.uniqueId))
        assertNull(BlightedSacks.sackFor(SpellbookData.FIRE_BLAST.uniqueId), "other spells keep their runes")
        assertNull(BlightedSacks.sackFor(-1))
        val items = BlightedSacks.Sack.values().map { it.item }
        assertEquals(listOf(Items.BLIGHTED_ANCIENT_ICE_SACK, Items.BLIGHTED_ENTANGLE_SACK, Items.BLIGHTED_TELEPORT_SPELL_SACK, Items.BLIGHTED_VENGEANCE_SACK), items)
    }

    @Test
    fun `the shared rune check and every caller of the sacked spells pass the spell id`() {
        val spells = File("src/main/kotlin/gg/rsmod/plugins/content/magic/MagicSpells.kt").readText()
        assertTrue("BlightedSacks.usable(p, spellId)" in spells, "canCast")
        assertTrue("BlightedSacks.consume(p, spellId)" in spells, "removeRunes")
        assertTrue("BlightedSacks.safeZoneRefusal(p, spellId)" in spells, "a sack in a safe zone names the real reason")
        assertTrue(
            "BlightedSacks.syncClient(player)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/DeadmanHud.kt").readText(),
            "the spellbook varc follows the safe zones every cycle",
        )
        assertTrue(
            "is missing combat definitions" !in File("src/main/kotlin/gg/rsmod/plugins/content/combat/Combat.kt").readText(),
            "players never see internal npc ids",
        )
        assertTrue(
            "MagicSpells.canCast(pawn, requirements.lvl, requirements.runes, spellId = spell.uniqueId)" in
                File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText(),
        )
        assertTrue("MagicSpells.removeRunes(pawn, requirement.runes, spellId = spell.uniqueId)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText())
        val lunar = File("src/main/kotlin/gg/rsmod/plugins/content/magic/lunar/lunar_spells.plugin.kts").readText()
        assertTrue("MagicSpells.canCast(this, metadata.lvl, metadata.runes, spellId = metadata.sprite)" in lunar)
        assertTrue("MagicSpells.removeRunes(this, metadata.runes, metadata.sprite)" in lunar)
        // Autocast and manual casts share MagicCombatStrategy.canAttack, which passes the spell id (sacks cover autocast Ice spells).
        assertTrue("canCast(pawn, requirements.lvl, requirements.runes, spellId = spell.uniqueId)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText(), "ice autocast")
    }
}
