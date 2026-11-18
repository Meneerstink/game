package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX
import gg.rsmod.plugins.content.npcs.bulk.BulkNpcCombatDefs
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.*

class ClueGuardianCombatDataTests {
    private fun definition(id: Int): gg.rsmod.game.model.combat.NpcCombatDef {
        val rows=File(RspsPaths.data("cfg/npcs/combat-defs.json")).reader().use {
            Gson().fromJson(it,Array<BulkNpcCombatDefs.Row>::class.java).filter { row -> row.id==id }
        }
        assertEquals(1,rows.size)
        val defs=mockk<DefinitionSet>()
        every { defs.getCount(AnimDef::class.java) } returns 65536
        every { defs.getNullable(AnimDef::class.java,any()) } answers { AnimDef(secondArg()) }
        every { defs.getNullable(NpcDef::class.java,any()) } answers { NpcDef(secondArg()) }
        return BulkNpcCombatDefs.build(rows,defs).defs.getValue(id)
    }

    @Test fun `Wilderness double agent uses the source zero strength bonus and eighty real hitpoints`() {
        // Pinned Double agent Wiki version1/source1777; local5144 cache identity.
        val actual=definition(5144)
        assertEquals(80,actual.lifepoints)
        assertEquals(listOf(80,43,24,24,1),actual.stats)
        assertEquals(0,actual.bonuses[NPC_STRENGTH_BONUS_INDEX])
        assertEquals(4,actual.attackSpeed)
    }

    @Test fun `non Wilderness double agent has forty source strength bonus rather than inherited forty four`() {
        // Pinned Double agent Wiki version2/source1778; local5145 cache identity.
        val actual=definition(5145)
        assertEquals(120,actual.lifepoints)
        assertEquals(listOf(100,80,80,80,1),actual.stats)
        assertEquals(40,actual.bonuses[NPC_STRENGTH_BONUS_INDEX])
        assertTrue(actual.poisonDamage <= 0)
    }

    @Test fun `Saradomin wizard melee has the source six poison and forty strength bonus`() {
        // Pinned Saradomin wizard/source2955. This does not accept its unscripted magic/prayer switching.
        val actual=definition(1264)
        assertEquals(120,actual.lifepoints)
        assertEquals(listOf(100,80,80,80,1),actual.stats)
        assertEquals(40,actual.bonuses[NPC_STRENGTH_BONUS_INDEX])
        assertEquals(6,actual.poisonDamage)
        assertEquals(4,actual.attackSpeed)
    }
}
