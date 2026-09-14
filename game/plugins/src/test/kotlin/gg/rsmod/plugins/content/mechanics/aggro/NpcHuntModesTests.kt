package gg.rsmod.plugins.content.mechanics.aggro

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RCV-012.B14 roster: the committed hunt-mode table carries Void's player hunt modes, and the shared rule applies each mode's checks
 * (Void `Hunting.canHunt`) instead of one level cap for every aggressive npc.
 */
class NpcHuntModesTests {
    private val table = NpcHuntModes.load(File("../../data/cfg/npcs/hunt-modes.json"))

    @Test
    fun `table carries the Void player hunt modes and their flags`() {
        val aggressive = table.modes.getValue("aggressive")
        assertEquals(NpcHuntModes.Mode("line_of_sight", checkNotTooStrong = false, checkNotCombat = true, checkNotCombatSelf = true, checkNotBusy = false), aggressive)
        val cowardly = table.modes.getValue("cowardly")
        assertEquals(NpcHuntModes.Mode("line_of_sight", checkNotTooStrong = true, checkNotCombat = true, checkNotCombatSelf = true, checkNotBusy = true), cowardly)
        assertEquals("line_of_walk", table.modes.getValue("guarding").checkVisual)
        assertTrue(table.npcs.isNotEmpty())
        table.npcs.forEach { assertTrue(it.mode in table.modes, "npc ${it.id} uses unknown mode ${it.mode}") }
        table.npcs.forEach { assertTrue(it.range > 0, "npc ${it.id} range") }
        assertEquals(table.npcs.size, table.npcs.map { it.id }.distinct().size, "one entry per npc id")
    }

    @Test
    fun `Trollheim thrower trolls hunt without the level cap and mountain trolls keep it`() {
        (1101..1105).plus(1130..1134).forEach { id ->
            assertEquals("aggressive", table.modeName(id), "thrower troll $id")
            val mode = table.mode(id)!!
            assertTrue(NpcHuntModes.allows(mode, playerCombatLevel = 138, npcCombatLevel = 68, playerUnderAttack = false, playerInMulti = false, playerMenuOpen = false, visible = true))
        }
        val mountainTroll = table.mode(1106)!!
        assertEquals("cowardly", table.modeName(1106))
        assertFalse(NpcHuntModes.allows(mountainTroll, playerCombatLevel = 139, npcCombatLevel = 69, playerUnderAttack = false, playerInMulti = false, playerMenuOpen = false, visible = true))
        assertTrue(NpcHuntModes.allows(mountainTroll, playerCombatLevel = 138, npcCombatLevel = 69, playerUnderAttack = false, playerInMulti = false, playerMenuOpen = false, visible = true))
    }

    @Test
    fun `each mode flag gates exactly its own check`() {
        val all = NpcHuntModes.Mode("line_of_sight", checkNotTooStrong = true, checkNotCombat = true, checkNotCombatSelf = true, checkNotBusy = true)
        val none = NpcHuntModes.Mode("none", checkNotTooStrong = false, checkNotCombat = false, checkNotCombatSelf = false, checkNotBusy = false)
        fun allows(mode: NpcHuntModes.Mode, level: Int = 50, underAttack: Boolean = false, multi: Boolean = false, menu: Boolean = false, visible: Boolean = true) =
            NpcHuntModes.allows(mode, level, 25, underAttack, multi, menu, visible)
        assertTrue(allows(all))
        assertFalse(allows(all, level = 51), "too strong: combat > npc * 2")
        assertFalse(allows(all, underAttack = true), "under attack in single-way combat")
        assertTrue(allows(all, underAttack = true, multi = true), "under attack is allowed in multi-combat")
        assertFalse(allows(all, menu = true), "busy with an open menu")
        assertFalse(allows(all, visible = false), "not visible")
        assertTrue(allows(none, level = 138, underAttack = true, menu = true, visible = false), "no checks at all")
    }

    @Test
    fun `the aggression plugin applies the table only to npcs that are aggressive in both sources`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/aggro/npc_aggro.plugin.kts").readText()
        assertTrue("NpcHuntModes.table.mode(n.id)" in plugin)
        assertTrue("NpcHuntModes.allows(" in plugin)
        assertTrue("if (npc.combatDef.aggressiveRadius <= 0) 0 else NpcHuntModes.table.range(npc.id) ?: npc.combatDef.aggressiveRadius" in plugin)
    }
}
