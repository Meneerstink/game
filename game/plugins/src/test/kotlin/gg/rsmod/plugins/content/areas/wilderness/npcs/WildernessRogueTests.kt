package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 5; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessRogueTests : WildernessNpcContractTests(listOf(15096, 15097)) {
    @kotlin.test.Test fun `high agility does not introduce the legacy lucky triple or quadruple into the OSRS Rogue`() {
        val f = WildernessNpcTestSupport.Fixture()
        io.mockk.every { f.player.skills.getCurrentLevel(any()) } returns 99
        io.mockk.every { f.world.random(7) } returns 1
        val logic = gg.rsmod.plugins.content.skills.thieving.pickpocketing.Pickpocketing
        val method = logic.javaClass.getDeclaredMethod("getMultiplier",gg.rsmod.game.model.entity.Player::class.java,
            gg.rsmod.plugins.content.skills.thieving.pickpocketing.PickpocketTarget::class.java).apply { isAccessible = true }
        kotlin.test.assertEquals(1,method.invoke(logic,f.player,gg.rsmod.plugins.content.skills.thieving.pickpocketing.PickpocketTarget.WildernessRogue))
        kotlin.test.assertEquals(4,method.invoke(logic,f.player,gg.rsmod.plugins.content.skills.thieving.pickpocketing.PickpocketTarget.Rogue), "existing target behaviour remains outside this task")
    }
    @kotlin.test.Test fun `only imported level15 has the source correct pickpocket table and Rocky entitlement`() {
        val target = gg.rsmod.plugins.content.skills.thieving.pickpocketing.PickpocketTarget.WildernessRogue
        kotlin.test.assertEquals(listOf(15096),target.objectIds)
        kotlin.test.assertEquals(32,target.level)
        kotlin.test.assertEquals(36.5,target.xp)
        kotlin.test.assertEquals(2..2,target.damage)
        kotlin.test.assertEquals(8,target.stunnedTicks)
        kotlin.test.assertEquals(257211,gg.rsmod.plugins.content.skills.summoning.SkillingPets.PICKPOCKET[target])
        val f = WildernessNpcTestSupport.Fixture()
        val b = gg.rsmod.plugins.content.drops.DropTableBuilder(f.player,java.security.SecureRandom()).apply(target.drops)
        val t = b.tables.getValue("main")
        kotlin.test.assertEquals(144,t.totalSlots)
        kotlin.test.assertEquals(144,t.entries.last().index)
        val coins = t.entries.first().drop as gg.rsmod.plugins.content.drops.DropEntry.ItemRangeDrop
        kotlin.test.assertEquals(123,t.entries.first().index)
        kotlin.test.assertEquals(25..40,coins.quantityRange)
    }
}
