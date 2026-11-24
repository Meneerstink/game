package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.skills.summoning.SummoningTestCache
import gg.rsmod.plugins.content.mechanics.hud.TimerExpiry
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.lang.ref.WeakReference
import kotlin.coroutines.startCoroutine
import kotlin.test.*

class MageArenaIIGuardianTests {
    @Test fun `dying followers keep owner and location reserved and stale death cannot release a replacement`() {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        var index = 0
        every { world.spawn(any<Npc>()) } answers { firstArg<Npc>().index = ++index; true }
        every { world.remove(any<Npc>()) } answers { firstArg<Npc>().index = -1 }
        fun player() = mockk<Player>(relaxed = true).also {
            every { it.world } returns world
            every { it.attr } returns AttributeMap()
        }
        val owner = player(); val other = player()
        val location = MageArenaIITracking.Location(20_000, Tile(3200, 3900))
        val god = MageArenaII.God.SARADOMIN
        mockkObject(MageArenaAssets)
        try {
            every { MageArenaAssets.npc(any()) } answers { localNpc(firstArg()) }
            every { MageArenaAssets.sequence(any()) } returns null
            val old = MageArenaIIEncounter.summon(owner, god, location)!!
            old.setCurrentLifepoints(0)
            MageArenaIIEncounter.dismissAll(owner) // rotation/logout during the death animation
            assertSame(old, MageArenaIIEncounter.active(owner, god))
            assertNull(MageArenaIIEncounter.summon(owner, god, location))
            assertNull(MageArenaIIEncounter.summon(other, god, location))
            MageArenaIIEncounter.died(old)
            old.index = -1 // NpcDeathAction removes it after its hook
            val replacement = MageArenaIIEncounter.summon(owner, god, location)!!
            MageArenaIIEncounter.died(old) // stale callback
            assertSame(replacement, MageArenaIIEncounter.active(owner, god))
            assertNull(MageArenaIIEncounter.summon(other, god, location))
            assertNull(MageArenaIIEncounter.summon(owner, god, location))
        } finally {
            MageArenaIIEncounter.dismissAll(owner)
            MageArenaIIEncounter.dismissAll(other)
            unmockkObject(MageArenaAssets)
        }
    }

    @Test fun `despawn retains boss teleblock while death clears only its own cast`() {
        val world = mockk<World>(relaxed = true)
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
        every { world.definitions } returns definitions
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.spawn(any<Npc>()) } returns true
        val owner = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        every { owner.world } returns world
        every { owner.attr } returns AttributeMap()
        every { owner.timers } returns timers
        mockkObject(MageArenaAssets)
        try {
            every { MageArenaAssets.npc(any()) } answers { localNpc(firstArg()) }
            every { MageArenaAssets.sequence(any()) } returns null
            val actor = MageArenaIIEncounter.summon(owner, MageArenaII.God.SARADOMIN,
                MageArenaIITracking.Location(11_000, Tile(3200, 3900)))!!
            val state = MageArenaIIEncounter.stateOf(actor)!!
            TimerExpiry.teleblock(owner, 200, source = state)
            MageArenaIIEncounter.dismiss(actor)
            assertEquals(200, timers[TELEBLOCK_TIMER], "fleeing must not cure boss TB")
            assertEquals(150, timers[TimerExpiry.TELEBLOCK_WARNING_TIMER])
            MageArenaIIEncounter.died(actor)
            assertFalse(timers.has(TELEBLOCK_TIMER))
            TimerExpiry.teleblock(owner, 200, source = state)
            timers.remove(TELEBLOCK_TIMER)
            TimerExpiry.teleblock(owner, 500)
            MageArenaIIEncounter.died(actor)
            assertEquals(500, timers[TELEBLOCK_TIMER], "a stale death must not cure a PKer's new TB")
        } finally {
            MageArenaIIEncounter.dismissAll(owner)
            unmockkObject(MageArenaAssets)
        }
    }

    @Test fun `MA2 rolls can hit and miss under Protect Deflect and Ruinous without changing generic NPC protection`() {
        val target = mockk<Player>(relaxed = true)
        every { target.attr } returns AttributeMap()
        every { target.skills } returns SkillSet(25).apply {
            setBaseLevel(Skills.DEFENCE, 75); setBaseLevel(Skills.MAGIC, 75)
        }
        every { target.equipmentBonuses } returns IntArray(18)
        every { target.equipment } returns ItemContainer(DefinitionSet(), EQUIPMENT_KEY)
        mockkObject(Prayers)
        try {
            every { Prayers.isActive(any(), any()) } returns false
            MageArenaIIGuardians.ALL.forEach { guardian ->
                val actor = npc(localNpc(guardian.upstream)!!)
                actor.combatDef = MageArenaIIGuardians.definition(guardian) { it + 60_000 }!!
                actor.combatDef.stats.forEachIndexed { index, level -> actor.stats.setMaxLevel(index, level) }
                actor.combatDef.bonuses.forEachIndexed { index, bonus -> actor.equipmentBonuses[index] = bonus }
                for ((style, icons) in listOf(
                    CombatClass.MAGIC to listOf(PrayerIcon.PROTECT_FROM_MAGIC, PrayerIcon.DEFLECT_MAGIC, PrayerIcon.RUINOUS_DAMPEN_MAGIC),
                    CombatClass.MELEE to listOf(PrayerIcon.PROTECT_FROM_MELEE, PrayerIcon.DEFLECT_MELEE, PrayerIcon.RUINOUS_DAMPEN_MELEE))) {
                    icons.forEach { icon ->
                        every { target.prayerIcon } returns icon.id
                        val chance = MageArenaIIEncounter.accuracy(actor, target, style)
                        assertTrue(chance > 0.0 && chance < 1.0, "$guardian $icon must allow hits and misses")
                    }
                }
                every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MAGIC.id
                assertEquals(0.0, MagicCombatFormula.getAccuracy(actor, target), "generic formula stays unchanged")
                every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MELEE.id
                assertEquals(0.0, MeleeCombatFormula.getAccuracy(actor, target), "generic formula stays unchanged")
            }
        } finally { unmockkObject(Prayers) }
    }

    @Test fun `symbol summoned guardians install the existing impact filter including delayed typeless damage`() {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.spawn(any<Npc>()) } returns true
        val owner = mockk<Player>(relaxed = true)
        every { owner.world } returns world
        every { owner.attr } returns AttributeMap()
        mockkObject(MageArenaAssets)
        try {
            every { MageArenaAssets.npc(any()) } answers { localNpc(firstArg()) }
            every { MageArenaAssets.sequence(any()) } returns null
            MageArenaIIGuardians.ALL.forEachIndexed { index, guardian ->
                val actor = MageArenaIIEncounter.summon(owner, guardian.god,
                    MageArenaIITracking.Location(10_000 + index, Tile(3200, 3900)))!!
                val filter = assertNotNull(actor.hitModifier, "real summon must install the impact rule")
                val delayedSpecial = Hit.Builder().addHit(25, HitType.REGULAR_HIT.id).build()
                val matching = Hit.Builder().addHit(30, HitType.MAGIC.id).build().apply {
                    sourceCombatClass = CombatClass.MAGIC
                    sourceSpellId = guardian.spell.uniqueId
                }
                val recoil = Hit.Builder().addHit(4, HitType.REFLECTED.id).build()
                filter(delayedSpecial); filter(matching); filter(recoil)
                assertEquals(0, delayedSpecial.hitmarks.single().damage)
                assertEquals(30, matching.hitmarks.single().damage)
                assertEquals(4, recoil.hitmarks.single().damage)
            }
        } finally {
            MageArenaIIEncounter.dismissAll(owner)
            unmockkObject(MageArenaAssets)
        }
    }

    @Test fun `unreachable target suspends after one route attempt instead of retrying within the tick`() {
        val actor = mockk<Npc>(relaxed = true)
        val target = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { actor.attr } returns attrs
        attrs[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(target)
        val task = QueueTask(actor, TaskPriority.STANDARD)
        var attempts = 0
        mockkObject(Combat)
        try {
            every { Combat.isAttackDelayReady(actor) } returns true
            every { Combat.canEngage(actor, target) } returns true
            coEvery { Combat.moveToAttackRange(task, actor, target, 8, true) } answers {
                attempts++
                // Bound the old broken implementation too: the test must fail, never hang the test worker.
                if (attempts > 1) attrs.remove(COMBAT_TARGET_FOCUS_ATTR)
                false
            }
            val run: suspend () -> Unit = { MageArenaIICombatScript.handleSpecialCombat(task) }
            run.startCoroutine(task)
            assertEquals(1, attempts)
            assertTrue(task.suspended(), "failed path must yield a game tick even with no attack delay")
        } finally {
            task.terminate()
            unmockkObject(Combat)
        }
    }

    private val localNpc: (Int) -> Int? = { 50_000 + it }
    private fun npc(id: Int): Npc {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()).apply { name = "MA2 guardian" } }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        return Npc(id, Tile(3200, 3900), world).apply { stats = Npc.Stats(5) }
    }

    @Test fun `regular stats use real320HP Derwen80Magic and calculated XP bonuses exactly once`() {
        MageArenaIIGuardians.ALL.forEach { guardian ->
            val def = MageArenaIIGuardians.definition(guardian) { it + 60_000 }!!
            assertEquals(320, def.lifepoints)
            assertEquals(6, def.attackSpeed)
            assertEquals(0, def.respawnDelay)
            assertTrue(def.waitFullDeathAnimation && def.poisonImmunity && def.venomImmunity)
            assertEquals(-60, def.bonuses[BonusSlot.DEFENCE_MAGIC.id])
            assertEquals(listOf(200, 200, 200, 200), listOf(BonusSlot.DEFENCE_STAB, BonusSlot.DEFENCE_SLASH,
                BonusSlot.DEFENCE_CRUSH, BonusSlot.DEFENCE_RANGED).map { def.bonuses[it.id] })
            val actor = npc(localNpc(guardian.upstream)!!)
            actor.combatDef = def
            // World.setNpcDefaults copies bonuses as well as stats; assigning combatDef alone does not.
            def.bonuses.forEachIndexed { index, bonus -> actor.equipmentBonuses[index] = bonus }
            def.stats.forEachIndexed { index, level -> actor.stats.setMaxLevel(index, level) }
            assertEquals(1.0 + guardian.xpBonusPercent / 100.0, Combat.getNpcXpMultiplier(actor), 1e-9,
                "the shared formula must not apply the Wiki XP bonus twice")
            if (guardian.god == MageArenaII.God.ZAMORAK) {
                assertEquals(SlayerAssignment.BLACK_DEMON, def.slayerAssignment)
                assertEquals(376.0, def.slayerXp)
                assertTrue(NpcSpecies.DEMON in def.species)
            } else assertNull(def.slayerAssignment)
        }
        val derwen = MageArenaIIGuardians.ALL.single { it.god == MageArenaII.God.GUTHIX }
        assertEquals(80, derwen.magic)
        assertEquals(180, derwen.magicBonus)
    }

    @Test fun `missing bindings never substitute OSRS ids or Breach actors`() {
        for (id in listOf(14446, 14447, 14448, 7858, 7859, 7860)) {
            assertNull(MageArenaIIGuardians.forNpc(id, localNpc))
            assertNull(MageArenaIIGuardians.forNpc(id) { null })
        }
        MageArenaIIGuardians.ALL.forEach { guardian ->
            assertSame(guardian, MageArenaIIGuardians.forNpc(localNpc(guardian.upstream)!!, localNpc))
            assertNull(MageArenaIIGuardians.definition(guardian) { null })
        }
    }

    @Test fun `launch gate blocks every wrong god nonmagic and built in spell but leaves Breach damage alone`() {
        mockkObject(MageArenaAssets)
        try {
            every { MageArenaAssets.npc(any()) } answers { localNpc(firstArg()) }
            MageArenaIIGuardians.ALL.forEach { guardian ->
                val actor = npc(localNpc(guardian.upstream)!!)
                for (spell in listOf(null, CombatSpell.WIND_STRIKE) + MageArenaIIGuardians.ALL.map { it.spell }) {
                    for (type in listOf(HitType.MAGIC, HitType.CRIT_MAGIC, HitType.MELEE, HitType.RANGE)) {
                        val expected = if (spell == guardian.spell && type in listOf(HitType.MAGIC, HitType.CRIT_MAGIC)) 30 else 0
                        assertEquals(expected, MageArenaIIGuardians.modifyIncomingDamage(actor, type, 30, spell?.uniqueId))
                    }
                }
            }
            for (id in listOf(14446, 14447, 14448)) {
                assertEquals(30, MageArenaIIGuardians.modifyIncomingDamage(npc(id), HitType.MELEE, 30, null))
            }
        } finally { unmockkObject(MageArenaAssets) }
    }

    @Test fun `in flight hit retains its original spell and cannot become a god spell after a later cast`() {
        MageArenaIIGuardians.ALL.forEach { guardian ->
            val matching = Hit.Builder().addHit(30, HitType.MAGIC.id).build().apply {
                sourceCombatClass = CombatClass.MAGIC
                sourceSpellId = guardian.spell.uniqueId
            }
            val wrong = Hit.Builder().addHit(30, HitType.MAGIC.id).build().apply {
                sourceCombatClass = CombatClass.MAGIC
                sourceSpellId = CombatSpell.WIND_STRIKE.uniqueId
            }
            MageArenaIIGuardians.modifyHit(guardian, wrong)
            MageArenaIIGuardians.modifyHit(guardian, matching)
            assertEquals(0, wrong.hitmarks.single().damage)
            assertEquals(30, matching.hitmarks.single().damage)
            // A powered-staff hit has no spell id, irrespective of a previously selected god spell.
            matching.sourceSpellId = null
            MageArenaIIGuardians.modifyHit(guardian, matching)
            assertEquals(0, matching.hitmarks.single().damage)
        }
    }

    @Test fun `sourced recoil exception does not let an untyped or mixed hit bypass spell immunity`() {
        MageArenaIIGuardians.ALL.forEach { guardian ->
            val hit = Hit.Builder().addHit(4, HitType.REFLECTED.id).addHit(30, HitType.MAGIC.id).build()
            MageArenaIIGuardians.modifyHit(guardian, hit)
            assertEquals(listOf(4, 0), hit.hitmarks.map { it.damage })
            val mixed = Hit.Builder().addHit(30, HitType.MAGIC.id).addHit(20, HitType.MELEE.id).build().apply {
                sourceCombatClass = CombatClass.MAGIC
                sourceSpellId = guardian.spell.uniqueId
            }
            MageArenaIIGuardians.modifyHit(guardian, mixed)
            assertEquals(listOf(30, 0), mixed.hitmarks.map { it.damage })
        }
    }
}
