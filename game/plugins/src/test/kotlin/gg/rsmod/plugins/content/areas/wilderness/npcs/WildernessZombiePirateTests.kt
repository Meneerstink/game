package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 4; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessZombiePirateTests : WildernessNpcContractTests(listOf(15091, 15092, 15093, 15094, 15095)) {
    @kotlin.test.Test fun `changing Avarice refreshes the real view and item search without leaking to the next player`() {
        val f = WildernessNpcTestSupport.Fixture()
        val drops = gg.rsmod.plugins.content.quests.tab.DropSources
        val source = drops.forNpc(f.player,15091)!!
        val regular = f.view(15091)
        kotlin.test.assertTrue(regular.any { it.item == gg.rsmod.plugins.api.cfg.Items.BONES })
        val original = drops.itemChances(drops.view(f.player,source,1.0))
        val index = gg.rsmod.plugins.content.quests.tab.DropSources.ItemIndex(
            original.keys.associateWith { drops.itemName(f.world,it) },
            original.mapValues { listOf(Triple(source,it.value.first,it.value.second)) },
        )
        io.mockk.every { f.player.equipment[gg.rsmod.plugins.api.EquipmentType.AMULET.id] } returns gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.AMULET_OF_AVARICE)
        val worn = f.view(15091)
        kotlin.test.assertTrue(worn.any { it.item == gg.rsmod.plugins.api.cfg.Items.BONES_NOTED })
        kotlin.test.assertTrue(worn.none { it.item == gg.rsmod.plugins.api.cfg.Items.BONES })
        val refreshed = drops.personalWildernessIndex(f.player,index,1.0,listOf(source))
        kotlin.test.assertTrue(gg.rsmod.plugins.api.cfg.Items.BONES_NOTED in refreshed.names)
        kotlin.test.assertFalse(gg.rsmod.plugins.api.cfg.Items.BONES in refreshed.names)
        val nextPlayer = WildernessNpcTestSupport.Fixture()
        kotlin.test.assertTrue(nextPlayer.view(15091).any { it.item == gg.rsmod.plugins.api.cfg.Items.BONES })
    }
    @kotlin.test.Test fun `avarice notes only noteable drops and already noted supplies stay noted`() {
        val f = WildernessNpcTestSupport.Fixture()
        io.mockk.every { f.player.equipment[gg.rsmod.plugins.api.EquipmentType.AMULET.id] } returns gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.AMULET_OF_AVARICE)
        val view = f.view(15091)
        kotlin.test.assertTrue(view.any { it.item == gg.rsmod.plugins.api.cfg.Items.BONES_NOTED && it.normal == 1.0 })
        kotlin.test.assertTrue(view.none { it.item == gg.rsmod.plugins.api.cfg.Items.BONES })
        kotlin.test.assertTrue(view.any { it.item == gg.rsmod.plugins.api.cfg.Items.BLIGHTED_ANGLERFISH_NOTED && it.quantity == "5-15" })
        kotlin.test.assertEquals(4.0 / 1260,view.single { it.item == gg.rsmod.plugins.api.cfg.Items.BLOOD_RUNE }.normal,1e-12)
    }
}
