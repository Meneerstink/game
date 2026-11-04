package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.region.ChunkSet
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.*
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.quests.tab.CollectionLogPages
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildernessBossFanaticTests : WildernessBossContractTests(Npcs.CHAOS_FANATIC, 225, listOf(1, 1, 220, 200, 1), 2, 15, "Chaos Fanatic", 1000) {
    @Test fun `only the last engaged player is selected for a ground hit including a familiar credited to that player`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(Npcs.CHAOS_FANATIC)
        val other = mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        npc.attr[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR] = java.lang.ref.WeakReference(other)
        assertEquals(other, ChaosFanaticCombat.explosionVictim(npc, f.player))
        val familiar = f.npc(1)
        familiar.attr[gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR] = java.lang.ref.WeakReference(f.player)
        npc.attr[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR] = java.lang.ref.WeakReference(familiar)
        assertEquals(f.player, ChaosFanaticCombat.explosionVictim(npc, other))
    }
    @Test fun `exactly twelve red attacks follow an explosion and the thirteenth may be special`() {
        var left = ChaosFanaticCombat.cooldownAfter(0, ChaosFanaticCombat.Attack.EXPLOSION)
        repeat(12) {
            val attack = ChaosFanaticCombat.choose(left, 14)
            assertEquals(ChaosFanaticCombat.Attack.RED, attack)
            left = ChaosFanaticCombat.cooldownAfter(left, attack)
        }
        assertEquals(0, left)
        assertEquals(ChaosFanaticCombat.Attack.DISARM, ChaosFanaticCombat.choose(left, 14))
    }
}

class WildernessBossArchaeologistTests : WildernessBossContractTests(Npcs.CRAZY_ARCHAEOLOGIST, 225, listOf(160, 90, 240, 1, 180), 3, 15, "Crazy archaeologist", null) {
    @Test fun `explosions cannot damage a player who teleported upstairs or outside their 3x3`() {
        val centre = Tile(2977, 3702)
        assertTrue(CrazyArchaeologistCombat.inBlast(centre, centre.transform(1, -1)))
        assertFalse(CrazyArchaeologistCombat.inBlast(centre, Tile(centre.x, centre.z, 1)))
        assertFalse(CrazyArchaeologistCombat.inBlast(centre, centre.transform(2, 0)))
    }
}

class WildernessBossCallistoTests : WildernessBossContractTests(OldWildernessBosses.CALLISTO, 255, listOf(350, 370, 440, 1, 1), 4, 50, "Callisto", 1500) {
    @Test fun `roar travels four tiles on free ground and stops before solid scenery including a diagonal corner`() {
        val f = WildernessBossTestSupport.Fixture()
        val collision = CollisionManager(ChunkSet(mockk<World>(relaxed = true)))
        every { f.world.collision } returns collision
        val npc = f.npc(OldWildernessBosses.CALLISTO, Tile(3200, 3800))
        val start = Tile(3205, 3802)
        assertEquals(start.transform(4, 0), OldWildernessBosses.pushDestination(npc, start))
        val obstacle = start.transform(2, 0)
        val update = CollisionUpdate.Builder().apply {
            setType(CollisionUpdate.Type.ADD)
            putTile(obstacle, false, *Direction.values().filter { it != Direction.NONE }.toTypedArray())
        }.build()
        collision.applyUpdate(update)
        assertEquals(start.transform(1, 0), OldWildernessBosses.pushDestination(npc, start), "never enters the blocked arrival tile")
        assertEquals(obstacle.transform(-1, 0), OldWildernessBosses.pushDestination(npc, obstacle.transform(-1, 0)), "a wall immediately behind the player")
        val diagonal = Tile(3205, 3805)
        collision.applyUpdate(CollisionUpdate.Builder().apply {
            setType(CollisionUpdate.Type.ADD)
            putTile(diagonal.transform(1, 0), false, *Direction.values().filter { it != Direction.NONE }.toTypedArray())
        }.build())
        assertEquals(diagonal, OldWildernessBosses.pushDestination(npc, diagonal), "no diagonal cut through a corner")
    }

    @Test fun `old Callisto is immune to magic damage but vulnerability may still land and melee is capped at fifty`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(OldWildernessBosses.CALLISTO)
        assertEquals(0, OldWildernessBosses.modifyIncomingDamage(npc, 99, HitType.MAGIC))
        assertEquals(30, OldWildernessBosses.modifyIncomingDamage(npc, 30, HitType.MELEE))
        assertEquals(50, OldWildernessBosses.CALLISTO_MELEE_MAX)
        assertEquals(60, OldWildernessBosses.CALLISTO_MAX)
    }
}

class WildernessBossVetionTests : WildernessBossContractTests(OldWildernessBosses.VETION, 255, listOf(430, 430, 395, 300, 1), 4, Int.MAX_VALUE, "Vetion", 1500) {
    @Test fun `damage immunity lasts only while at least one live spawned hound remains`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(OldWildernessBosses.VETION)
        val dog = f.npc(OldWildernessBosses.HELLHOUND)
        OldWildernessBosses.dogs(npc) += dog
        assertEquals(0, OldWildernessBosses.modifyIncomingDamage(npc, 30))
        every { dog.isDead() } returns true
        assertEquals(30, OldWildernessBosses.modifyIncomingDamage(npc, 30))
        assertEquals(null, OldWildernessBosses.rewardNpc(OldWildernessBosses.VETION))
        assertEquals(OldWildernessBosses.VETION, OldWildernessBosses.rewardNpc(OldWildernessBosses.VETION_REBORN))
        assertEquals(45, OldWildernessBosses.REBORN_MAX)
        assertEquals(6, OldWildernessBosses.EARTHQUAKE_RANGE)
    }

    @Test fun `both players keep their phase one damage credit when Reborn is created`() {
        val f = WildernessBossTestSupport.Fixture()
        val other = mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        f.players.entries[1] = f.player
        f.players.entries[2] = other
        val npc = f.npc(OldWildernessBosses.VETION)
        npc.damageMap.add(f.player, 200)
        npc.damageMap.add(other, 55)
        every { f.world.spawn(any<gg.rsmod.game.model.entity.Npc>()) } returns true
        val next = OldWildernessBosses.nextPhase(npc, OldWildernessBosses.VETION_REBORN)!!
        assertEquals(200, next.damageMap.getDamageFrom(f.player))
        assertEquals(55, next.damageMap.getDamageFrom(other))
        assertEquals(f.player, next.damageMap.getMostDamage())
        assertEquals(500, OldWildernessBosses.REBORN_TIMEOUT_TICKS)
    }
}

class WildernessBossScorpiaTests : WildernessBossContractTests(Npcs.SCORPIA, 200, listOf(250, 150, 180, 1, 1), 4, 14, "Scorpia", 2000) {
    @Test fun `guardian reach is measured from the footprint and excludes another floor`() {
        val origin = Tile(3234, 10340)
        assertEquals(3, ScorpiaCombat.distanceToArea(origin.transform(7, 0), origin, 5))
        assertEquals(4, ScorpiaCombat.distanceToArea(origin.transform(8, 0), origin, 5))
        assertEquals(Int.MAX_VALUE, ScorpiaCombat.distanceToArea(Tile(origin.x, origin.z, 1), origin, 5))
    }
}

class WildernessBossVenenatisTests : WildernessBossContractTests(VenenatisCombat.NPC, 850, listOf(300, 200, 321, 300, 350), 4, 10, "Venenatis", 1500) {
    @Test fun `a group kill counts each contributor once and a global unique does not discard the other player's main loot`() {
        val f = WildernessBossTestSupport.Fixture()
        val other = mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        val otherCounts = mutableMapOf<Int, Int>()
        every { other.world } returns f.world
        every { other.attr } returns AttributeMap()
        every { other.combatLevel } returns 126
        every { other.getNpcKillCount(any()) } answers { otherCounts[firstArg()] ?: 0 }
        every { other.incrementNpcKillCount(any(), any()) } answers {
            val id = firstArg<Int>()
            otherCounts[id] = (otherCounts[id] ?: 0) + secondArg<Int>()
        }
        f.players.entries[1] = f.player
        f.players.entries[2] = other
        val npc = f.npc(VenenatisCombat.NPC)
        npc.damageMap.add(f.player, 75)
        npc.damageMap.add(other, 25)
        f.counts[npc.id] = 1 // NpcDeathAction counts the top damager before broadcasting the kill.
        every { f.world.randomDouble() } returns 0.9 // no pet; unique belongs to the second player
        WildernessBossLoot.onKilled(npc, f.player)
        assertEquals(1, f.counts[npc.id])
        assertEquals(1, otherCounts[npc.id])
        val drops = gg.rsmod.plugins.content.drops.DropTableFactory
        val oldRandom = drops.prng
        val received = mutableMapOf<gg.rsmod.game.model.entity.Player, List<gg.rsmod.game.model.item.Item>>()
        mockkObject(drops)
        try {
            drops.prng = mockk<java.security.SecureRandom>().also { rng ->
                every { rng.nextInt(any<Int>()) } returns 0
                every { rng.nextDouble() } returns 0.0
            }
            every { drops.rollMainReplacement(any(), any(), any()) } returns listOf(gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.VOIDWAKER_GEM))
            every { drops.giveDrop(any(), any(), any(), any(), any(), any(), any()) } answers {
                received[secondArg()] = arg(4)
            }
            WildernessBossLoot.drop(npc)
            assertEquals(2, received.size)
            assertTrue(received.getValue(f.player).any { it.id == gg.rsmod.plugins.api.cfg.Items.BIG_BONES })
            assertTrue(received.getValue(f.player).any { it.id == gg.rsmod.plugins.api.cfg.Items.MYSTIC_AIR_STAFF_NOTED }, "the losing unique recipient still gets a main roll")
            assertTrue(received.getValue(other).any { it.id == gg.rsmod.plugins.api.cfg.Items.BONES })
            assertEquals(1, received.values.flatten().count { it.id in WildernessBossLoot.UNIQUES })
            assertTrue(received.getValue(other).any { it.id == gg.rsmod.plugins.api.cfg.Items.VOIDWAKER_GEM })
        } finally {
            drops.prng = oldRandom
            unmockkObject(drops)
        }
    }

    @Test fun `a curved bone is rolled only after a long bone failed and the viewer shows the effective chance`() {
        val f = WildernessBossTestSupport.Fixture()
        val drops = gg.rsmod.plugins.content.drops.DropTableFactory
        val oldRandom = drops.prng
        val rng = mockk<java.security.SecureRandom>()
        try {
            drops.prng = rng
            every { rng.nextInt(any<Int>()) } returns 0
            val withLong = drops.getDrop(f.player, VenenatisCombat.NPC)!!
            assertTrue(withLong.any { it.id == gg.rsmod.plugins.api.cfg.Items.LONG_BONE })
            assertFalse(withLong.any { it.id == gg.rsmod.plugins.api.cfg.Items.CURVED_BONE })
            every { rng.nextInt(400) } returns 1
            val withCurved = drops.getDrop(f.player, VenenatisCombat.NPC)!!
            assertFalse(withCurved.any { it.id == gg.rsmod.plugins.api.cfg.Items.LONG_BONE })
            assertTrue(withCurved.any { it.id == gg.rsmod.plugins.api.cfg.Items.CURVED_BONE })
            val source = DropSources.forNpc(f.player, VenenatisCombat.NPC)!!
            val curved = DropSources.view(f.player, source, 1.0).lines.single { it.item == gg.rsmod.plugins.api.cfg.Items.CURVED_BONE }
            assertEquals((399.0 / 400) / 5000, curved.normal, 1e-12)
        } finally { drops.prng = oldRandom }
    }
    @Test fun `the damaging web covers exactly seven by five tiles and never another floor`() {
        val centre = VenenatisCombat.VENENATIS_ARENA.middle
        assertEquals(35, (-5..5).sumOf { x -> (-5..5).count { z -> VenenatisCombat.inDamagingWeb(centre, centre.transform(x, z)) } })
        assertFalse(VenenatisCombat.inDamagingWeb(centre, Tile(centre.x, centre.z, 0)))
    }
    @Test fun `group loot selects ten contributors and a unique has only one damage weighted recipient`() {
        val people = (1..11).map { mockk<gg.rsmod.game.model.entity.Player>() to it }
        val shares = WildernessBossLoot.shares(people)
        assertEquals(10, shares.size)
        assertEquals(11.0 / 66, shares.first().fraction, 1e-12)
        assertEquals(25, WildernessBossLoot.quantity(100, 0.01))
        assertEquals(75, WildernessBossLoot.quantity(100, 0.75))
        assertEquals(0, WildernessBossLoot.uniqueRecipient(listOf(75, 25), 0.749))
        assertEquals(1, WildernessBossLoot.uniqueRecipient(listOf(75, 25), 0.75))
        assertEquals(1, WildernessBossLoot.uniqueRecipient(listOf(75, 25), 0.999999))
    }
}

class WildernessBossCollisionTests {
    @Test fun `a low wall permits projectiles but blocks each nearby boss melee route`() {
        val f = WildernessBossTestSupport.Fixture()
        val collision = CollisionManager(ChunkSet(mockk<World>(relaxed = true)))
        every { f.world.collision } returns collision
        val checks = listOf<Pair<Int, (gg.rsmod.game.model.entity.Npc, Tile) -> Boolean>>(
            OldWildernessBosses.CALLISTO to OldWildernessBosses::adjacent,
            OldWildernessBosses.VETION to OldWildernessBosses::vetionMeleeReach,
            VenenatisCombat.NPC to VenenatisCombat::adjacent,
            VenenatisCombat.SPINDEL to VenenatisCombat::adjacent,
            Npcs.CRAZY_ARCHAEOLOGIST to CrazyArchaeologistCombat::canKick,
        )
        checks.forEachIndexed { i, (id, canMelee) ->
            val npc = f.npc(id, Tile(3200 + 20 * i, 3800))
            val edge = npc.tile.transform(npc.getSize() - 1, 0)
            val player = edge.transform(1, 0)
            assertTrue(canMelee(npc, player), "$id has open melee reach")
            collision.applyUpdate(CollisionUpdate.Builder().apply {
                setType(CollisionUpdate.Type.ADD)
                putTile(edge, false, Direction.EAST)
            }.build())
            assertTrue(collision.raycast(edge, player, projectile = true), "a low wall does not block a projectile")
            assertFalse(canMelee(npc, player), "$id cannot use that projectile visibility for a melee hit through the wall")
        }
    }
}

class WildernessBossKillCountTests {
    @Test fun `boss notification covers ordinary boss pages including joint pages and suppresses minions and the queen's first form`() {
        val f = WildernessBossTestSupport.Fixture()
        val sources = gg.rsmod.plugins.content.quests.tab.DropSources
        val counts = gg.rsmod.plugins.content.quests.tab.KillCounts
        val pages = gg.rsmod.plugins.content.quests.tab.CollectionLogPages
        val bosses = listOf(
            Npcs.KING_BLACK_DRAGON to "King Black Dragon", Npcs.GENERAL_GRAARDOR to "General Graardor",
            Npcs.KREEARRA to "Kree'arra", Npcs.COMMANDER_ZILYANA to "Commander Zilyana",
            Npcs.KRIL_TSUTSAROTH to "K'ril Tsutsaroth", Npcs.CORPOREAL_BEAST to "Corporeal Beast",
            Npcs.DAGANNOTH_REX to "Dagannoth Rex", Npcs.DAGANNOTH_PRIME to "Dagannoth Prime",
            Npcs.DAGANNOTH_SUPREME to "Dagannoth Supreme", 2745 to "TzTok-Jad",
            Npcs.KALPHITE_QUEEN_1160 to "Kalphite Queen",
        ).map { (id, name) -> DropSources.Source("npc:$id", name, id,
            if (id == Npcs.KALPHITE_QUEEN_1160) intArrayOf(Npcs.KALPHITE_QUEEN, Npcs.KALPHITE_QUEEN_1159, id) else intArrayOf(id),
            100, DropSources.Group.BOSSES, DropSources.Kind.NPC, id) }
        mockkObject(sources)
        try {
            every { sources.all(f.player) } returns bosses
            pages.install(listOf(CollectionLogPages.Page("all-bosses", CollectionLogPages.Category.BOSSES, "Joint test page", bosses.map { it.key }, emptyList())))
            f.counts[Npcs.KALPHITE_QUEEN] = 999 // old first-phase counters must not inflate completed fights
            counts.onNpcKilled(f.player, Npcs.KALPHITE_QUEEN)
            counts.onNpcKilled(f.player, Npcs.KALPHITE_QUEEN_1159)
            counts.onNpcKilled(f.player, Npcs.SCORPIAS_GUARDIAN)
            assertTrue(f.messages.isEmpty())
            bosses.forEach { source ->
                f.counts[source.modelNpc] = 1234
                counts.onNpcKilled(f.player, source.modelNpc)
                assertEquals("Your ${source.name} kill count is: <col=ff0000>1,234</col>.", f.messages.last())
                assertEquals(1234, counts.get(f.player, source.key))
            }
            assertEquals(bosses.size, f.messages.size)
        } finally {
            unmockkObject(sources)
            pages.reset()
        }
    }
}

class WildernessBossSpindelTests : WildernessBossContractTests(VenenatisCombat.SPINDEL, 515, listOf(200, 130, 225, 235, 286), 4, 9, "Spindel", 2800) {
    @Test fun `recoil and vengeance are not consumed or reflected by either spider or spiderling`() {
        val f = WildernessBossTestSupport.Fixture()
        val response = gg.rsmod.plugins.content.mechanics.combatresponse.DamageResponse
        val vengeance = gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
        val recoil = gg.rsmod.plugins.content.mechanics.combatresponse.RingOfRecoil
        mockkObject(vengeance, recoil)
        try {
            listOf(VenenatisCombat.NPC, VenenatisCombat.SPINDEL, VenenatisCombat.SPIDERLING, VenenatisCombat.SPINDEL_SPIDERLING).forEach {
                response.onIncomingHit(f.npc(it), f.player, gg.rsmod.game.model.combat.CombatClass.MELEE, 15)
            }
            verify(exactly = 0) { vengeance.onIncomingHit(any(), any(), any()) }
            verify(exactly = 0) { recoil.onIncomingHit(any(), any(), any()) }
        } finally { unmockkObject(vengeance, recoil) }
    }
    @Test fun `a spider boss and her own spiderlings are one attacker for the single-way rules`() {
        val f = WildernessBossTestSupport.Fixture()
        val spindel = f.npc(VenenatisCombat.SPINDEL)
        val other = f.npc(VenenatisCombat.SPINDEL)
        val mine = f.npc(VenenatisCombat.SPINDEL_SPIDERLING).also { it.attr[VenenatisCombat.OWNER] = spindel }
        val sister = f.npc(VenenatisCombat.SPINDEL_SPIDERLING).also { it.attr[VenenatisCombat.OWNER] = spindel }
        val stray = f.npc(VenenatisCombat.SPINDEL_SPIDERLING).also { it.attr[VenenatisCombat.OWNER] = other }
        val Combat = gg.rsmod.plugins.content.combat.Combat
        assertTrue(Combat.sameSide(mine, spindel))
        assertTrue(Combat.sameSide(spindel, mine))
        assertTrue(Combat.sameSide(mine, sister))
        assertFalse(Combat.sameSide(stray, spindel))
        assertFalse(Combat.sameSide(other, spindel))
        assertFalse(Combat.sameSide(f.npc(OldWildernessBosses.CALLISTO), f.npc(OldWildernessBosses.CALLISTO)))
        assertFalse(Combat.sameSide(f.player, spindel))
    }
    @Test fun `Spindel never exceeds ten spiderlings and her arena excludes the other boss and other floors`() {
        assertEquals(2, VenenatisCombat.spiderlingsToSummon(VenenatisCombat.SPINDEL, 8))
        assertEquals(1, VenenatisCombat.spiderlingsToSummon(VenenatisCombat.SPINDEL, 9))
        assertEquals(0, VenenatisCombat.spiderlingsToSummon(VenenatisCombat.SPINDEL, 10))
        val a = VenenatisCombat.SPINDEL_ARENA
        assertTrue(a.inChasm(a.spawn))
        assertFalse(a.inChasm(VenenatisCombat.VENENATIS_ARENA.spawn))
        assertFalse(a.inChasm(Tile(a.spawn.x, a.spawn.z, 1)))
    }
}

class WildernessBossMaledictusTests {
    @Test fun `Maledictus has his own boss source count and a shared Revenants log instead of counting two dragon kills`() {
        val f = WildernessBossTestSupport.Fixture()
        val sources = gg.rsmod.plugins.content.quests.tab.DropSources
        val pages = gg.rsmod.plugins.content.quests.tab.CollectionLogPages
        val source = sources.forNpc(f.player, RevenantMaledictus.NPC)!!
        assertEquals("npc:${RevenantMaledictus.NPC}", source.key)
        assertEquals(gg.rsmod.plugins.content.quests.tab.DropSources.Group.BOSSES, source.group)
        val view = sources.view(f.player, source, 1.0)
        for (tier in 1..2) assertTrue(view.lines.any { it.item == RevenantCaveRevenants.emblem(tier) && it.normal >= 0.5 })
        val page = pages.byKey(f.player, "revenants")!!
        assertTrue(source.key in page.sources)
        assertTrue(RevenantCaveRevenants.emblem(1) in page.items)
        f.counts[RevenantMaledictus.NPC] = 1
        gg.rsmod.plugins.content.quests.tab.KillCounts.onNpcKilled(f.player, RevenantMaledictus.NPC)
        assertEquals(listOf("Your Revenant maledictus kill count is: <col=ff0000>1</col>."), f.messages)
        assertEquals(0.19, sources.twice(0.1), 1e-12)
    }

    @Test fun `ice prevents attacking for six ticks even if auto retaliation overwrites the ordinary attack delay`() {
        val f = WildernessBossTestSupport.Fixture()
        val timers = f.player.timers
        timers[RevenantMaledictus.ICE_ATTACK_LOCK] = RevenantMaledictus.FREEZE_TICKS
        timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
        assertFalse(gg.rsmod.plugins.content.combat.Combat.isAttackDelayReady(f.player))
        timers.remove(RevenantMaledictus.ICE_ATTACK_LOCK)
        assertTrue(gg.rsmod.plugins.content.combat.Combat.isAttackDelayReady(f.player))
        assertEquals(6, RevenantMaledictus.FREEZE_TICKS)
    }
    @Test fun `Maledictus has his Wiki stats speed and immunities and halves protected damage rather than blocking it`() {
        val f = WildernessBossTestSupport.Fixture()
        val d = f.repo.allNpcCombatDefs().getValue(RevenantMaledictus.NPC)
        assertEquals(1250, d.lifepoints)
        assertEquals(5, d.attackSpeed)
        assertEquals(Int.MAX_VALUE, d.aggressiveTimer)
        assertTrue(d.poisonImmunity && d.venomImmunity)
        (0..30).forEach { damage ->
            assertEquals(damage, RevenantMaledictus.protectedDamage(damage, false))
            assertEquals(damage / 2, RevenantMaledictus.protectedDamage(damage, true))
        }
        assertEquals(4500, RevenantMaledictus.COOLDOWN_TICKS)
    }

    @Test fun `a delayed special belongs to exactly one life and cannot hit after removal death or respawn`() {
        val f = WildernessBossTestSupport.Fixture()
        val n = f.npc(RevenantMaledictus.NPC)
        val token = WildernessBossLife.token(n)
        assertTrue(WildernessBossLife.active(n, token))
        every { n.isDead() } returns true
        assertFalse(WildernessBossLife.active(n, token))
        every { n.isDead() } returns false
        n.attr.clear() // the real NpcDeathAction resets attributes before respawning the same object
        WildernessBossLife.token(n)
        assertFalse(WildernessBossLife.active(n, token))
        val fresh = WildernessBossLife.token(n)
        every { n.isSpawned() } returns false
        assertFalse(WildernessBossLife.active(n, fresh))
    }
}
