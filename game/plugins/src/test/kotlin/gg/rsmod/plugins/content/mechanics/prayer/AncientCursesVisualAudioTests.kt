package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.message.impl.SynthSoundMessage
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
    fun `every Leech curse has a real cast animation, projectile and target graphic but no cast graphic`() {
        leechCurses.forEach { curse ->
            assertNotNull("$curse missing castAnimation", curse.castAnimation)
            assertEquals("$curse cast animation", 12575, curse.castAnimation)
            assertNull("$curse should not have a cast graphic (source has none for Leech)", curse.castGraphic)
            assertNotNull("$curse missing projectileGraphic", curse.projectileGraphic)
            assertNotNull("$curse missing targetGraphic", curse.targetGraphic)
        }
        assertEquals(leechCurses.size, leechCurses.map { it.projectileGraphic }.distinct().size)
        assertEquals(leechCurses.size, leechCurses.map { it.targetGraphic }.distinct().size)
        assertEquals(listOf(2233, 2237, 2241, 2245, 2249, 2253, 2257), leechCurses.map { it.secondaryTargetGraphic })
        assertEquals(2252, AncientCurse.LEECH_ENERGY.projectileGraphic)
        assertEquals(2256, AncientCurse.LEECH_SPECIAL_ATTACK.projectileGraphic)
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
    fun `Wrath's death explosion uses the real 300 percent multiplier, not the old 250 percent bug`() {
        val player = mockk<Player>(relaxed = true)
        every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 99) }
        assertEquals(297, AncientCurses.wrathMaxDamage(player))

        val lowLevel = mockk<Player>(relaxed = true)
        every { lowLevel.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 50) }
        assertEquals(150, AncientCurses.wrathMaxDamage(lowLevel))
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
        verify { target.graphic(2216, delay = 1) }
        verify { fixture.world.spawn(any<gg.rsmod.game.model.entity.Projectile>()) }
        verify { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_CAST_AND_FIRE, volume = 1, delay = 0)) }
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
        verify { target.graphic(2264, delay = 1) }
        verify { fixture.world.spawn(any<gg.rsmod.game.model.entity.Projectile>()) }
        verify { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_HIT, volume = 1, delay = 0)) }
    }

    @Test
    fun `curse activation and deactivation play the real curse book toggle sounds`() {
        val fixture = RuntimeFixture()
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)

        AncientCurses.toggleCurse(fixture.player, AncientCurse.SAP_WARRIOR)
        verify { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_ALL, volume = 1, delay = 0)) }

        AncientCurses.toggleCurse(fixture.player, AncientCurse.SAP_WARRIOR)
        verify { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_LIFT, volume = 1, delay = 0)) }

        AncientCurses.toggleTurmoil(fixture.player)
        verify(atLeast = 2) { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_ALL, volume = 1, delay = 0)) }
        AncientCurses.toggleTurmoil(fixture.player)
        verify(atLeast = 2) { fixture.player.write(SynthSoundMessage(sound = Sfx.CURSE_LIFT, volume = 1, delay = 0)) }
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
