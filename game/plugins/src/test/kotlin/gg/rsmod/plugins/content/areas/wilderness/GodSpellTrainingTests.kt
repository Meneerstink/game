package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class GodSpellTrainingTests {
    @Test fun `untrained god spells allow arena PvP but never count it toward100 or allow an outside player`() {
        val a = Account()
        val target = mockk<Player>(relaxed = true)
        every { target.tile } returns MageArena.FOE_TILE
        GodSpellTraining.SPELLS.forEach { training ->
            assertFalse(GodSpellTraining.canCast(a.player, target, training.spell), "MA1 still required")
            a.attrs[COMPLETED_MAGE_ARENA] = true
            assertTrue(GodSpellTraining.canCast(a.player, target, training.spell))
            repeat(100) { GodSpellTraining.cast(a.player, target, training.spell) }
            assertEquals(0, GodSpellTraining.casts(a.player, training))
            every { target.tile } returns Tile(3200, 3900)
            assertFalse(GodSpellTraining.canCast(a.player, target, training.spell))
            every { target.tile } returns MageArena.FOE_TILE
            every { a.player.tile } returns Tile(3200, 3900)
            assertFalse(GodSpellTraining.canCast(a.player, target, training.spell))
            every { a.player.tile } returns MageArena.ARRIVAL
            a.attrs[COMPLETED_MAGE_ARENA] = false
        }
    }

    private class Account {
        val attrs = AttributeMap()
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val defs = mockk<DefinitionSet>()
        init {
            every { defs.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
            every { world.definitions } returns defs
            every { player.world } returns world
            every { player.attr } returns attrs
            every { player.tile } returns MageArena.ARRIVAL
        }
        fun mage(): Npc = Npc(GodSpellTraining.BATTLE_MAGES.first(), MageArena.FOE_TILE, world)
    }

    @Test fun `every spell independently unlocks on its hundredth actual cast including splashes`() {
        val a = Account(); a.attrs[COMPLETED_MAGE_ARENA] = true
        val outside = Npc(1, Tile(3200, 3200), a.world)
        GodSpellTraining.SPELLS.forEach { training ->
            repeat(99) { GodSpellTraining.cast(a.player, a.mage(), training.spell) }
            assertEquals(99, GodSpellTraining.casts(a.player, training))
            assertFalse(GodSpellTraining.canCast(a.player, outside, training.spell))
            // A cast is recorded at launch; deliberately no hit, accuracy roll, kill or damage event here.
            GodSpellTraining.cast(a.player, a.mage(), training.spell)
            assertEquals(100, GodSpellTraining.casts(a.player, training))
            assertTrue(GodSpellTraining.canCast(a.player, outside, training.spell))
            repeat(5) { GodSpellTraining.cast(a.player, a.mage(), training.spell) }
            assertEquals(100, GodSpellTraining.casts(a.player, training))
        }
        assertTrue(GodSpellTraining.mastered(a.player))
        assertFalse(GodSpellTraining.mastered(Account().player), "another account never shares training")
    }

    @Test fun `Kolodion players breach and a mage spawned outside the arena cannot train spells`() {
        val a = Account(); val spell = CombatSpell.SARADOMIN_STRIKE
        assertFalse(GodSpellTraining.canCast(a.player, a.mage(), spell), "MA1 must be earned first")
        a.attrs[COMPLETED_MAGE_ARENA] = true
        val bad = listOf(
            Npc(MageArena.FORMS.first().npc, MageArena.FOE_TILE, a.world),
            Npc(14446, MageArena.FOE_TILE, a.world),
            Npc(GodSpellTraining.BATTLE_MAGES.first(), Tile(3200, 3200), a.world),
            mockk<Player>(relaxed = true),
        )
        bad.forEach { target ->
            assertFalse(GodSpellTraining.canCast(a.player, target, spell))
            GodSpellTraining.cast(a.player, target, spell)
        }
        assertEquals(0, GodSpellTraining.casts(a.player, GodSpellTraining.SPELLS.first()))
        assertTrue(GodSpellTraining.canCast(a.player, a.mage(), spell))
        every { a.player.tile } returns Tile(3105, 3933, 1)
        assertFalse(GodSpellTraining.canCast(a.player, a.mage(), spell))
    }

    @Test fun `non god magic is unaffected and all transcript progress boundaries are retained`() {
        val a = Account()
        assertTrue(GodSpellTraining.canCast(a.player, a.mage(), CombatSpell.WIND_STRIKE))
        val training = GodSpellTraining.SPELLS.first()
        for ((count, text) in listOf(0 to "still need", 50 to "still need", 51 to "over half", 89 to "over half", 90 to "almost ready", 99 to "almost ready", 100 to "experienced enough")) {
            a.attrs[training.key] = count
            assertTrue(text in GodSpellTraining.progress(a.player, training), "boundary $count")
        }
    }
}
