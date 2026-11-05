package gg.rsmod.plugins.content.areas.wilderness.npcs

/** N522 report section 9; compiled plugin, real cache definitions and actual Drop Viewer fractions. */
class WildernessElderChaosDruidTests : WildernessNpcContractTests(listOf(15102)) {
    @kotlin.test.Test fun `fleeing top damager is the pull candidate and attackers distant players and anchored players are excluded`() {
        val f = WildernessNpcTestSupport.Fixture()
        val n = f.npc(15102)
        fun player(x: Int, damage: Int, attacking: Boolean): gg.rsmod.game.model.entity.Player {
            val p = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            io.mockk.every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
            io.mockk.every { p.isOnline } returns true
            io.mockk.every { p.isDead() } returns false
            io.mockk.every { p.tile } returns gg.rsmod.game.model.Tile(x, 3800)
            if (attacking) p.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR] = java.lang.ref.WeakReference(n)
            n.damageMap.add(p, damage)
            return p
        }
        val fleeing = player(3219, 100, false)
        val attacking = player(3201, 200, true)
        val outside = player(3221, 1000, false)
        val lower = player(3202, 50, false)
        kotlin.test.assertSame(fleeing, WildernessNpcMechanics.pullCandidate(n, listOf(lower, attacking, outside, fleeing)) { false })
        kotlin.test.assertSame(lower, WildernessNpcMechanics.pullCandidate(n, listOf(lower, attacking, outside, fleeing)) { it === fleeing })
        kotlin.test.assertFalse(f.player.timers.has(gg.rsmod.game.model.timer.TELEBLOCK_TIMER), "unknown NPC duration/frequency is never replaced with player TB defaults")
    }

    @kotlin.test.Test fun `herb amounts have the sourced 15 20 15 5 distribution rather than uniform quantities`() {
        val f = WildernessNpcTestSupport.Fixture()
        val view = f.view(15102)
        val guam = view.filter { it.item == gg.rsmod.plugins.api.cfg.Items.GRIMY_GUAM }.associate { it.quantity to it.normal }
        listOf(1 to 15, 2 to 20, 3 to 15, 4 to 5).forEach { (amount, weight) ->
            kotlin.test.assertEquals(weight / (129.0 * 4), guam.getValue(amount.toString()), 1e-12)
        }
    }
}
