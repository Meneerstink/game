package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.skills.slayer.Krystilia
import gg.rsmod.game.model.combat.SlayerAssignment
import io.mockk.*
import kotlin.test.*

class ClueGuardianCombatTests {
    @AfterTest fun cleanup()=unmockkAll()
    @Test fun `candidate and partial mappings cannot enable source combat identities`() {
        val body=ClueCombatAssets.sources.joinToString("\n") { "seq\t$it\t${20000+it}" }
        assertFailsWith<IllegalArgumentException> { ClueCombatAssets.parse("# applied=false\n$body") }
        assertFailsWith<IllegalArgumentException> { ClueCombatAssets.parse("# applied=true\n"+body.lineSequence().drop(1).joinToString("\n")) }
        assertEquals(16,ClueCombatAssets.parse("# applied=true\n$body").size)
    }
    @Test fun `source attacks retain separate exact maxima and poison is not blindly applied on misses`() {
        val visual=ClueGuardianCombat.Visuals(1,2,3)
        for((id,max) in mapOf(6587 to 10,6588 to 13,7307 to 18,7308 to 23,7309 to 36,8635 to 18,8636 to 12,8637 to 16)) {
            val profile=ClueGuardianProfiles.all.single { it.source==id }
            val row=ClueGuardianCombat.row(profile,30000+id,visual)
            assertEquals(max*10,row.attacks.single().hits.single().max)
            assertEquals(0,row.attacks.single().poison) // Ancient melee poison uses the accurate-impact hook, including through prayer.
        }
        assertTrue(ClueGuardianCombat.row(ClueGuardianProfiles.all.single { it.source==7310 },30000,visual).attacks.single().hits.isEmpty())
    }
    @Test fun `Brassican rolls standard magical accuracy and explicitly bypasses protect and deflect`() {
        mockkObject(MagicCombatFormula)
        mockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        val world=mockk<World>();every { world.randomDouble() } returnsMany listOf(0.2,0.9)
        val npc=mockk<Npc>(relaxed=true);every { npc.world } returns world
        val target=mockk<Pawn>(relaxed=true)
        every { MagicCombatFormula.getUnprotectedAccuracy(npc,target,any()) } returns 0.75
        every { npc.dealHit(target,any<Double>(),16.0,any(),1,any(),HitType.MAGIC,0,false) } returns mockk<PawnHit>(relaxed=true)
        ClueGuardianCombat.brassican(npc,target);ClueGuardianCombat.brassican(npc,target)
        verify(exactly=1) { npc.dealHit(target,any<Double>(),16.0,true,1,any(),HitType.MAGIC,0,false) }
        verify(exactly=1) { npc.dealHit(target,any<Double>(),16.0,false,1,any(),HitType.MAGIC,0,false) }
        verify(exactly=0) { MagicCombatFormula.getAccuracy(any(),any(),any()) }
    }
    @Test fun `clue guard never counts as an aviansie Slayer kill`() {
        val task=Krystilia.TASKS.single { it.assignment==SlayerAssignment.AVIANSIE }
        assertTrue("aviansie" in task.names);assertFalse("armadylean guard" in task.names)
    }
}
