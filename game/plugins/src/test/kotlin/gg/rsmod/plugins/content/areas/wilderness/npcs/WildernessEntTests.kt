package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 7; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessEntTests : WildernessNpcContractTests(listOf(15099)) {
    @kotlin.test.Test fun `the actual size2 footprint permits both adjacent edges and rejects a two tile harvesting gap`() {
        val f = WildernessNpcTestSupport.Fixture()
        val trunk = f.npc(15100)
        trunk.attr[WildernessNpcMechanics.RESOURCE_ACTIVE] = true
        trunk.attr[WildernessNpcMechanics.TRUNK_EXPIRES] = 101
        trunk.attr[WildernessNpcMechanics.TRUNK_KILLER] = f.player.username
        io.mockk.every { trunk.getSize() } returns 2
        for (x in listOf(3199,3200,3201,3202)) {
            io.mockk.every { f.player.tile } returns gg.rsmod.game.model.Tile(x,3800)
            kotlin.test.assertTrue(WildernessNpcMechanics.canChop(trunk,f.player),"adjacent x=$x")
        }
        for (x in listOf(3198,3203)) {
            io.mockk.every { f.player.tile } returns gg.rsmod.game.model.Tile(x,3800)
            kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk,f.player),"two-tile gap x=$x")
        }
    }
    @kotlin.test.Test fun `only the killer can chop and movement logout expiry and attempt limits stop harvesting`() {
        val f = WildernessNpcTestSupport.Fixture()
        val trunk = f.npc(15100)
        io.mockk.every { trunk.getFrontFacingTile(f.player, any()) } returns gg.rsmod.game.model.Tile(3200, 3800)
        trunk.attr[WildernessNpcMechanics.RESOURCE_ACTIVE] = true
        trunk.attr[WildernessNpcMechanics.TRUNK_EXPIRES] = 101
        trunk.attr[WildernessNpcMechanics.CHOP_ATTEMPTS] = 0
        trunk.attr[WildernessNpcMechanics.TRUNK_KILLER] = f.player.username
        kotlin.test.assertTrue(WildernessNpcMechanics.canChop(trunk, f.player))
        val other = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk, other))
        io.mockk.every { f.player.isOnline } returns false
        kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk, f.player))
        io.mockk.every { f.player.isOnline } returns true
        io.mockk.every { f.world.currentCycle } returns 101
        kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk, f.player))
        io.mockk.every { f.world.currentCycle } returns 100
        trunk.attr[WildernessNpcMechanics.CHOP_ATTEMPTS] = 25
        kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk, f.player))
        trunk.attr[WildernessNpcMechanics.CHOP_ATTEMPTS] = 0
        io.mockk.every { f.player.tile } returns gg.rsmod.game.model.Tile(3300, 3900)
        kotlin.test.assertFalse(WildernessNpcMechanics.canChop(trunk, f.player))
    }

    @kotlin.test.Test fun `death creates one public trunk and world queue removes it before one fresh Ent respawns`() {
        val f = WildernessNpcTestSupport.Fixture()
        val ent = f.npc(15099)
        val lifecycle = io.mockk.slot<suspend gg.rsmod.game.model.queue.QueueTask.(kotlinx.coroutines.CoroutineScope) -> Unit>()
        io.mockk.every { f.world.queue(capture(lifecycle)) } returns Unit
        val spawned = mutableListOf<gg.rsmod.game.model.entity.Npc>()
        val removed = mutableListOf<gg.rsmod.game.model.entity.Npc>()
        io.mockk.every { f.world.spawn(capture(spawned)) } returns true
        io.mockk.every { f.world.remove(capture(removed)) } returns Unit
        val trunk = WildernessNpcMechanics.entDied(ent)!!
        kotlin.test.assertEquals(15100, trunk.id)
        kotlin.test.assertNull(trunk.owner, "ownership limits harvesting, not visibility")
        kotlin.test.assertNull(WildernessNpcMechanics.entDied(ent), "repeat death hook cannot create duplicate resources")
        kotlin.test.assertEquals(1, spawned.size)
        val queues = gg.rsmod.game.model.queue.impl.WorldQueueTaskSet()
        queues.queue(f.world, kotlinx.coroutines.Dispatchers.Unconfined, gg.rsmod.game.model.queue.TaskPriority.STANDARD, lifecycle.captured)
        repeat(100) { queues.cycle() }
        kotlin.test.assertTrue(removed.isEmpty())
        queues.cycle()
        kotlin.test.assertEquals(listOf(trunk), removed)
        kotlin.test.assertFalse(trunk.attr[WildernessNpcMechanics.RESOURCE_ACTIVE]!!)
        repeat(14) { queues.cycle() }
        kotlin.test.assertEquals(1, spawned.size)
        queues.cycle()
        kotlin.test.assertEquals(listOf(15100,15099), spawned.map { it.id })
    }

    @kotlin.test.Test fun `log tiers and noted quantities use sourced calculator and unresolved boundaries have no invented result`() {
        val axe = gg.rsmod.plugins.content.skills.woodcutting.AxeType.DRAGON
        kotlin.test.assertEquals(gg.rsmod.plugins.api.cfg.Items.MAGIC_LOGS_NOTED, WildernessNpcMechanics.logForRoll(99,99,axe,100))
        kotlin.test.assertEquals(gg.rsmod.plugins.api.cfg.Items.OAK_LOGS_NOTED, WildernessNpcMechanics.logForRoll(99,99,axe,15))
        kotlin.test.assertNull(WildernessNpcMechanics.logForRoll(45,45,axe,50))
        kotlin.test.assertNull(WildernessNpcMechanics.logForRoll(99,100,axe,50), "boosted/drained calculator ambiguity remains open")
        kotlin.test.assertEquals(91.0/256, WildernessNpcMechanics.chopSuccess(99,false))
        kotlin.test.assertEquals(103.0/256, WildernessNpcMechanics.chopSuccess(99,true))
    }
}
