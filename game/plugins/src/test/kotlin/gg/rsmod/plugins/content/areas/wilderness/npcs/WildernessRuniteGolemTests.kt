package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 10; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessRuniteGolemTests : WildernessNpcContractTests(listOf(15103)) {
    @kotlin.test.Test fun `golem death makes public invulnerable rocks once and no fabricated kill loot`() {
        val f = WildernessNpcTestSupport.Fixture()
        val golem = f.npc(15103)
        val rocks = WildernessNpcMechanics.golemDied(golem)!!
        kotlin.test.assertEquals(15104, rocks.id)
        kotlin.test.assertNull(rocks.owner, "everyone may mine, not only the killer")
        kotlin.test.assertFalse(rocks.respawns)
        kotlin.test.assertNull(WildernessNpcMechanics.golemDied(golem))
        val staleHit = WildernessNpcTestSupport.hit(30,30)
        rocks.hitModifier!!(staleHit)
        kotlin.test.assertEquals(listOf(0,0),staleHit.hitmarks.map { it.damage })
        val armourHit = WildernessNpcTestSupport.hit(0,3,16)
        WildernessNpcMechanics.golemArmour(armourHit)
        kotlin.test.assertEquals(listOf(0,0,12),armourHit.hitmarks.map { it.damage })
        kotlin.test.assertTrue(f.view(15103).isEmpty())
    }

    @kotlin.test.Test fun `Mining 85 usable pickaxe and active rocks are required for every player`() {
        val f = WildernessNpcTestSupport.Fixture()
        val rocks = f.npc(15104)
        rocks.attr[WildernessNpcMechanics.RESOURCE_ACTIVE] = true
        io.mockk.every { rocks.getFrontFacingTile(f.player, any()) } returns f.player.tile
        io.mockk.every { f.player.skills.getMaxLevel(gg.rsmod.plugins.api.Skills.MINING) } returns 85
        io.mockk.every { f.player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.MINING) } returns 85
        io.mockk.every { f.player.inventory.contains(any<Int>()) } returns false
        io.mockk.every { f.player.equipment.contains(any<Int>()) } returns false
        kotlin.test.assertFalse(WildernessNpcMechanics.canMine(rocks, f.player))
        io.mockk.every { f.player.inventory.contains(gg.rsmod.plugins.api.cfg.Items.RUNE_PICKAXE) } returns true
        kotlin.test.assertTrue(WildernessNpcMechanics.canMine(rocks, f.player))
        io.mockk.every { f.player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.MINING) } returns 84
        kotlin.test.assertFalse(WildernessNpcMechanics.canMine(rocks, f.player))
        io.mockk.every { f.player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.MINING) } returns 85
        io.mockk.every { f.player.inventory.isFull } returns true
        kotlin.test.assertFalse(WildernessNpcMechanics.canMine(rocks, f.player))
    }
}
