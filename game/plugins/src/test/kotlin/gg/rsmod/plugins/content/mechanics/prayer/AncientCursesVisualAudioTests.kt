package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Sfx
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Q-030: animation/graphic/projectile/activation-chance data for every Sap/Leech/Deflect curse,
 * PROVEN from Novite's rev-667 `Player.java` `handleIngoingHit`/`sendDeath` (see [AncientCurse]
 * and [AncientCurses] class KDocs for the exact source method), plus the wiring that actually
 * fires that data in [AncientCurses.applySapLeech]/[AncientCurses.onIncomingHit]. Enumerates the
 * whole curse set rather than one exemplar (R8): every Sap/Leech curse and every combat Deflect
 * must carry real sourced ids, and Deflect Summoning's known, honestly-recorded gap must stay a
 * gap rather than being silently "fixed" with a guess.
 */
class AncientCursesVisualAudioTests {
    private val sapCurses = listOf(AncientCurse.SAP_WARRIOR, AncientCurse.SAP_RANGER, AncientCurse.SAP_MAGE, AncientCurse.SAP_SPIRIT)
    private val leechCurses =
        listOf(
            AncientCurse.LEECH_ATTACK, AncientCurse.LEECH_RANGED, AncientCurse.LEECH_MAGIC, AncientCurse.LEECH_DEFENCE,
            AncientCurse.LEECH_STRENGTH, AncientCurse.LEECH_ENERGY, AncientCurse.LEECH_SPECIAL_ATTACK,
        )
    private val combatDeflects = listOf(AncientCurse.DEFLECT_MELEE, AncientCurse.DEFLECT_MISSILES, AncientCurse.DEFLECT_MAGIC)

    @Test
    fun `every Sap curse has a real cast animation, cast graphic, projectile and target graphic`() {
        sapCurses.forEach { curse ->
            assertNotNull("$curse missing castAnimation", curse.castAnimation)
            assertEquals("$curse cast animation", 12569, curse.castAnimation)
            assertNotNull("$curse missing castGraphic", curse.castGraphic)
            assertNotNull("$curse missing projectileGraphic", curse.projectileGraphic)
            assertNotNull("$curse missing targetGraphic", curse.targetGraphic)
        }
        // Each Sap's own ids must be distinct from the others' (real per-curse art, not one reused id).
        assertEquals(sapCurses.size, sapCurses.map { it.castGraphic }.distinct().size)
        assertEquals(sapCurses.size, sapCurses.map { it.projectileGraphic }.distinct().size)
        assertEquals(sapCurses.size, sapCurses.map { it.targetGraphic }.distinct().size)
    }

    @Test
    fun `every Leech curse has a real cast animation, projectile and target graphic, and only Energy and Special have a caster graphic`() {
        leechCurses.forEach { curse ->
            assertNotNull("$curse missing castAnimation", curse.castAnimation)
            assertEquals("$curse cast animation", 12575, curse.castAnimation)
            assertNotNull("$curse missing projectileGraphic", curse.projectileGraphic)
            assertNotNull("$curse missing targetGraphic", curse.targetGraphic)
        }
        assertEquals(leechCurses.size, leechCurses.map { it.projectileGraphic }.distinct().size)
        assertEquals(leechCurses.size, leechCurses.map { it.targetGraphic }.distinct().size)
        assertEquals(leechCurses.size, leechCurses.count { it.secondaryTargetGraphic == null })
        assertEquals(2252, AncientCurse.LEECH_ENERGY.projectileGraphic)
        assertEquals(2256, AncientCurse.LEECH_SPECIAL_ATTACK.projectileGraphic)
        // Cache layout (caster gfx / projectile / impact) + Divergent 667: 2251/2255 belong to
        // Leech Energy / Leech Special Attack; the slot before every other Leech projectile is absent.
        assertEquals(2251, AncientCurse.LEECH_ENERGY.castGraphic)
        assertEquals(2255, AncientCurse.LEECH_SPECIAL_ATTACK.castGraphic)
        leechCurses.filter { it != AncientCurse.LEECH_ENERGY && it != AncientCurse.LEECH_SPECIAL_ATTACK }.forEach { curse ->
            assertNull("$curse has no caster graphic in any source or in the cache layout", curse.castGraphic)
        }
    }

    @Test
    fun `every combat Deflect has a real reflect animation and graphic, Deflect Summoning does not`() {
        combatDeflects.forEach { curse ->
            assertNotNull("$curse missing reflectAnimation", curse.reflectAnimation)
            assertEquals("$curse reflect animation", 12573, curse.reflectAnimation)
            assertNotNull("$curse missing reflectGraphic", curse.reflectGraphic)
        }
        assertEquals(combatDeflects.size, combatDeflects.map { it.reflectGraphic }.distinct().size)
        // Documented gap, not a silent omission: Novite has no server-side implementation of a
        // Deflect Summoning reflect effect (no incoming-Summoning-damage hook exists to source it
        // from), so this stays null rather than guessing an id.
        assertNull(AncientCurse.DEFLECT_SUMMONING.reflectAnimation)
        assertNull(AncientCurse.DEFLECT_SUMMONING.reflectGraphic)
    }

    @Test
    fun `Sap and Leech per-curse activation chances match Novite's real per-hit rolls, not the old shared 25 percent guess`() {
        // getRandom(4)==0 -> 1-in-5, getRandom(7)==0 -> 1-in-8, getRandom(10)==0 -> 1-in-11
        // (Utils.getRandom(n) returns a uniform value in 0..n inclusive - confirmed from source).
        assertEquals(20.0, AncientCurse.SAP_WARRIOR.activationChancePercent, 0.0001)
        assertEquals(20.0, AncientCurse.SAP_RANGER.activationChancePercent, 0.0001)
        assertEquals(20.0, AncientCurse.SAP_MAGE.activationChancePercent, 0.0001)
        assertEquals(100.0 / 11.0, AncientCurse.SAP_SPIRIT.activationChancePercent, 0.0001)
        assertEquals(12.5, AncientCurse.LEECH_ATTACK.activationChancePercent, 0.0001)
        assertEquals(12.5, AncientCurse.LEECH_RANGED.activationChancePercent, 0.0001)
        assertEquals(12.5, AncientCurse.LEECH_MAGIC.activationChancePercent, 0.0001)
        assertEquals(12.5, AncientCurse.LEECH_STRENGTH.activationChancePercent, 0.0001)
        assertEquals(100.0 / 11.0, AncientCurse.LEECH_DEFENCE.activationChancePercent, 0.0001)
        assertEquals(100.0 / 11.0, AncientCurse.LEECH_ENERGY.activationChancePercent, 0.0001)
        assertEquals(100.0 / 11.0, AncientCurse.LEECH_SPECIAL_ATTACK.activationChancePercent, 0.0001)
    }

    @Test
    fun `Wrath's death explosion uses the real 300 percent multiplier in 1 to 1 hitpoint units`() {
        // Novite: prayer level * 3.0 in x10 units (297 at 99) = level * 3 / 10 after the 1:1 migration.
        val player = mockk<Player>(relaxed = true)
        every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 99) }
        assertEquals(29, AncientCurses.wrathMaxDamage(player))

        val lowLevel = mockk<Player>(relaxed = true)
        every { lowLevel.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 50) }
        assertEquals(15, AncientCurses.wrathMaxDamage(lowLevel))
    }

    @Test
    fun `a landed Sap Warrior hit plays the real cast animation, graphic, projectile and target graphic`() {
        val fixture = RuntimeFixture()
        every { fixture.world.percentChance(any()) } returns true
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(fixture.player, AncientCurse.SAP_WARRIOR)

        val target = mockk<Npc>(relaxed = true)
        every { target.attr } returns AttributeMap()

        AncientCurses.onDamageDealt(fixture.player, target, damage = 50)

        verify { fixture.player.animate(12569) }
        verify { fixture.player.graphic(2214) }
        // Novite shows the impact one game tick after the projectile; the spotanim delay is counted
        // in 20 ms client cycles (client Static50.animationTick -> Animator.tick(1)), 30 per tick.
        verify { target.graphic(2216, delay = ONE_TICK_CLIENT_CYCLES) }
        verify { fixture.world.spawn(any<gg.rsmod.game.model.entity.Projectile>()) }
        // The Sap caster graphic's own sequence (12570 -> 8115) is the audio; nothing is server-sent.
        verify(exactly = 0) { fixture.player.write(match<gg.rsmod.game.message.Message> { it is SynthSoundMessage }) }
    }

    @Test
    fun `Soul Split sends the real projectile pair, target graphic and heal-drain effect`() {
        val fixture = RuntimeFixture()
        every { fixture.world.percentChance(any()) } returns true
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(fixture.player, AncientCurse.SOUL_SPLIT)

        val target = mockk<Player>(relaxed = true)
        every { target.attr } returns AttributeMap()
        every { target.world } returns fixture.world

        AncientCurses.onDamageDealt(fixture.player, target, damage = 100)

        verify { target.decreasePrayerPoints(20) }
        verify { target.graphic(2264, delay = ONE_TICK_CLIENT_CYCLES) }
        verify { fixture.world.spawn(any<gg.rsmod.game.model.entity.Projectile>()) }
        // Soul Split's 2263/2264 sequences are silent and no source maps 8112/8113/8119 to a phase.
        verify(exactly = 0) { fixture.player.write(match<gg.rsmod.game.message.Message> { it is SynthSoundMessage }) }
    }

    @Test
    fun `no curse activation sends a server sound and every explicit deactivation sends the lift sound`() {
        val offenders = mutableListOf<String>()
        AncientCurse.values().forEach { curse ->
            val fixture = RuntimeFixture()
            AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(fixture.player, curse)
            runCatching { verify(exactly = 0) { fixture.player.write(match<gg.rsmod.game.message.Message> { it is SynthSoundMessage }) } }
                .onFailure { offenders += "$curse: activation sent a server sound (the graphic sequence is the audio)" }
            AncientCurses.toggleCurse(fixture.player, curse)
            runCatching { verify(exactly = 1) { fixture.player.write(SynthSoundMessage(sound = LIFT_SOUND, loops = 1, delay = 0)) } }
                .onFailure { offenders += "$curse: explicit deactivation did not send 2663 exactly once" }
        }
        val fixture = RuntimeFixture()
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleTurmoil(fixture.player)
        runCatching { verify(exactly = 0) { fixture.player.write(match<gg.rsmod.game.message.Message> { it is SynthSoundMessage }) } }
            .onFailure { offenders += "Turmoil: activation sent a server sound" }
        AncientCurses.toggleTurmoil(fixture.player)
        runCatching { verify(exactly = 1) { fixture.player.write(SynthSoundMessage(sound = LIFT_SOUND, loops = 1, delay = 0)) } }
            .onFailure { offenders += "Turmoil: explicit deactivation did not send 2663 exactly once" }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every one of the eighteen ordinary curse slots sends its exact activation and deactivation messages`() {
        val offenders = mutableListOf<String>()
        AncientCurse.values.forEach { curse ->
            val fixture = RuntimeFixture()
            AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(fixture.player, curse)
            runCatching {
                verify(exactly = 1) {
                    fixture.player.write(
                        MessageGameMessage(
                            type = ChatMessageType.FILTERED.id,
                            message = "You activate ${curse.curseName}.",
                            username = null,
                        ),
                    )
                }
            }.onFailure { offenders += "$curse: missing activation message" }

            AncientCurses.toggleCurse(fixture.player, curse)
            runCatching {
                verify(exactly = 1) {
                    fixture.player.write(
                        MessageGameMessage(
                            type = ChatMessageType.FILTERED.id,
                            message = "You deactivate ${curse.curseName}.",
                            username = null,
                        ),
                    )
                }
            }.onFailure { offenders += "$curse: missing deactivation message" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    /** Novite's `closePrayers` switches a replaced prayer off silently. */
    @Test
    fun `replacing a conflicting curse is silent for every curse pair`() {
        val offenders = mutableListOf<String>()
        AncientCurse.values().forEach { first ->
            AncientCurse.values().filter { it != first && it.conflictsWith(first) }.forEach { second ->
                val fixture = RuntimeFixture()
                AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
                AncientCurses.toggleCurse(fixture.player, first)
                io.mockk.clearMocks(fixture.player, answers = false, recordedCalls = true, childMocks = false, verificationMarks = true, exclusionRules = false)
                AncientCurses.toggleCurse(fixture.player, second)
                runCatching {
                    verify(exactly = 0) { fixture.player.write(match<gg.rsmod.game.message.Message> { it is SynthSoundMessage }) }
                }.onFailure { offenders += "$first -> $second: replacing sent a server sound (activation is graphic-borne, replacement is silent)" }
            }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `source-proven curse toggles play activation visuals and quick activation stays visual-free`() {
        val fixture = RuntimeFixture()
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)

        assertEquals(12567, AncientCurses.PROTECT_ITEM_ACTIVATION_ANIMATION)
        assertEquals(2213, AncientCurses.PROTECT_ITEM_ACTIVATION_GRAPHIC)
        assertEquals(12589, AncientCurse.BERSERKER.activationAnimation)
        assertEquals(2266, AncientCurse.BERSERKER.activationGraphic)

        AncientCurses.toggleCurse(fixture.player, AncientCurse.BERSERKER)
        verify { fixture.player.animate(12589) }
        verify { fixture.player.graphic(2266) }

        AncientCurses.toggleTurmoil(fixture.player)
        verify { fixture.player.animate(12565) }
        verify { fixture.player.graphic(2226) }

        AncientCurses.toggleCurse(fixture.player, AncientCurse.BERSERKER)
        AncientCurses.toggleCurse(fixture.player, AncientCurse.BERSERKER, playActivationVisual = false)
        verify(exactly = 1) { fixture.player.animate(12589) }
        verify(exactly = 1) { fixture.player.graphic(2266) }
    }

    @Test
    fun `Deflect Melee reflect plays the real reflect animation and graphic on the defender`() {
        val fixture = RuntimeFixture()
        every { fixture.world.percentChance(any()) } returns true
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(fixture.player, AncientCurse.DEFLECT_MELEE)

        val attacker = mockk<Npc>(relaxed = true)

        AncientCurses.onIncomingHit(attacker, fixture.player, gg.rsmod.game.model.combat.CombatClass.MELEE, damage = 200)

        verify { fixture.player.animate(12573) }
        verify { fixture.player.graphic(2230) }
    }

    private companion object {
        /** Novite `Prayer.java:493/499`, Void `prayer.sounds.toml` `deactivate_prayer`. */
        const val LIFT_SOUND = 2663

        /** Client spotanim delay unit is one 20 ms cycle; a 600 ms game tick is 30 of them. */
        const val ONE_TICK_CLIENT_CYCLES = 30
    }

    /** Minimal reusable fixture mirroring [AncientCursesRuntimeTests.Fixture]'s varbit wiring. */
    private class RuntimeFixture {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)

        init {
            val definitions = mockk<gg.rsmod.game.fs.DefinitionSet>()
            every { definitions.get(gg.rsmod.game.fs.def.VarbitDef::class.java, any()) } answers {
                val id = secondArg<Int>()
                gg.rsmod.game.fs.def.VarbitDef(id).apply {
                    val prayer = Prayer.values.firstOrNull { it.varbit == id }
                    when {
                        id in 6820..6839 -> { varp = 1582; startBit = id - 6820 }
                        id in 6862..6881 -> { varp = 1587; startBit = id - 6862 }
                        prayer != null -> { varp = Prayers.ACTIVE_PRAYERS_VARP; startBit = prayer.slot }
                        else -> varp = id
                    }
                    endBit = startBit
                }
            }
            every { world.definitions } returns definitions
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { player.varps } returns gg.rsmod.game.model.varp.VarpSet((0..8000).toSet())
            every { player.varcs } returns MutableList(2000) { 0 }
            every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 99) }
            every { player.getCurrentPrayerPoints() } returns 99
            every { player.isOnline } returns true
            every { player.isDead() } returns false
            every { player.lock.canUsePrayer() } returns true
            player.attr[AncientCurses.UNLOCKED_ATTR] = true
        }
    }
}
