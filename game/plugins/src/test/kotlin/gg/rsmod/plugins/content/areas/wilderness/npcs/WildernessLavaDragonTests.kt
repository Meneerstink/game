package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.plugins.api.ext.getVarp

/** N522 report section 6; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessLavaDragonTests : WildernessNpcContractTests(listOf(15098)) {
    @kotlin.test.Test fun `firebreath is one of six rolls and uses the chromatic shield potion and prayer matrix`() {
        kotlin.test.assertEquals(1, (0..5).count(WildernessNpcMechanics::chooseDragonfire))
        val f = WildernessNpcTestSupport.Fixture()
        // The real magic-accuracy path reads prayer varbits; the shared NPC-only fixture does not otherwise need them.
        val cache = com.displee.cache.CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: "../../data/cache")
        try { WildernessNpcTestSupport.defs.load(cache, gg.rsmod.game.fs.def.VarbitDef::class.java) } finally { cache.close() }
        val p = f.player
        val npc = f.npc(15098)
        val formula = gg.rsmod.plugins.content.combat.formula.DragonfireFormula()
        io.mockk.every { f.world.randomDouble() } returns 0.0 // dragon wins the magic accuracy roll
        kotlin.test.assertEquals(50.0, formula.getMaxHit(npc, p))
        io.mockk.every { p.equipment[gg.rsmod.plugins.api.EquipmentType.SHIELD.id] } returns gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.ANTIDRAGON_SHIELD)
        io.mockk.every { p.equipment.hasAt(gg.rsmod.plugins.api.EquipmentType.SHIELD.id, gg.rsmod.plugins.api.cfg.Items.ANTIDRAGON_SHIELD) } returns true
        kotlin.test.assertEquals(5.0, formula.getMaxHit(npc, p))
        p.timers[gg.rsmod.game.model.timer.ANTIFIRE_TIMER] = 600
        kotlin.test.assertEquals(0.0, formula.getMaxHit(npc, p))
        p.timers.remove(gg.rsmod.game.model.timer.ANTIFIRE_TIMER)
        io.mockk.every { p.equipment[gg.rsmod.plugins.api.EquipmentType.SHIELD.id] } returns null
        io.mockk.every { p.equipment.hasAt(gg.rsmod.plugins.api.EquipmentType.SHIELD.id, gg.rsmod.plugins.api.cfg.Items.ANTIDRAGON_SHIELD) } returns false
        p.timers[gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER] = 300
        kotlin.test.assertEquals(0.0, formula.getMaxHit(npc, p))
    }

    @kotlin.test.Test fun `herb entrance really rolls two types independently and the MM2 slot has one reward`() {
        val f = WildernessNpcTestSupport.Fixture()
        val herbs = gg.rsmod.plugins.content.drops.DropTableFactory.tables(f.player, 15098)!!
            .flatMap { it.entries.toList() }.mapNotNull { it.drop as? gg.rsmod.plugins.content.drops.DropEntry.TableDrop }
            .single { it.table.entries.firstOrNull()?.drop is gg.rsmod.plugins.content.drops.DropEntry.MultiDrop }.table
        kotlin.test.assertEquals(121, herbs.entries.size)
        kotlin.test.assertTrue(herbs.entries.all { (it.drop as gg.rsmod.plugins.content.drops.DropEntry.MultiDrop).items.size == 2 })
        kotlin.test.assertTrue(herbs.entries.any { (it.drop as gg.rsmod.plugins.content.drops.DropEntry.MultiDrop).items.let { pair -> pair[0].id != pair[1].id } })
        io.mockk.every { f.player.getVarp(1913) } returns 0
        val before = f.view(15098)
        kotlin.test.assertEquals(7.0 / 128, before.single { it.item == gg.rsmod.plugins.api.cfg.Items.COINS_995 && it.quantity == "2960" }.normal)
        kotlin.test.assertTrue(before.none { it.item == gg.rsmod.plugins.api.cfg.Items.DRAGON_JAVELIN_TIPS })
    }
}
