package gg.rsmod.plugins.content.combat

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.CLIENT_SPELLBOOK_VARBIT
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.magic.MagicSpells
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N27 / N55 / N73 / N75 (owner 10-16): a cast shows ONE graphic on arrival - the impact on a hit, only the splash on a miss.
 * N74 (owner 10-16): Bind / Snare / Entangle on a held or freeze-immune target is refused before the cast (OSRS Wiki Snare).
 */
class MagicArrivalGraphicTests {
    @Test
    fun `a landed effect spell shows its impact once and a miss shows only the splash`() {
        for (spell in listOf(CombatSpell.TELEPORT_BLOCK, CombatSpell.ENTANGLE, CombatSpell.SNARE, CombatSpell.BIND, CombatSpell.WIND_STRIKE, CombatSpell.FIRE_SURGE)) {
            val impact = spell.impactGfx!!
            assertEquals(Graphic(impact.id, impact.height, 60, 3), MagicCombatStrategy.arrivalGraphic(spell, landHit = true, delay = 60, rotation = 3), "$spell hit")
            assertEquals(Graphic(MagicCombatStrategy.SPLASH_GFX, 96, 60), MagicCombatStrategy.arrivalGraphic(spell, landHit = false, delay = 60, rotation = 3), "$spell miss")
        }
    }

    @Test
    fun `the cast sends the arrival graphic from one place only`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        val castOn = source.substringAfter("private fun castOn(").substringBefore("private fun applyEffect(")
        assertEquals(1, Regex("""target\.graphic\(""").findAll(castOn).count(), "castOn: one target.graphic call (the arrival graphic)")
        assertFalse("Graphic(85" in castOn, "no hard-coded splash next to the impact")
    }

    @Test
    fun `a bind on a held or immune target is refused before the cast and costs nothing`() {
        assertEquals("Your target is already held by a magical force.", MagicCombatStrategy.bindRefusal(target(FROZEN_TIMER)))
        assertEquals("The target is currently immune to that spell.", MagicCombatStrategy.bindRefusal(target(FREEZE_IMMUNITY_TIMER)))
        assertNull(MagicCombatStrategy.bindRefusal(target(null)))

        for (spell in MagicCombatStrategy.BIND_SPELLS) {
            val player = caster()
            MagicSpells.getMetadata(spell.uniqueId)!!.runes.forEach { player.inventory.add(it.id, it.amount) }
            val runesBefore = player.inventory.toMap()
            player.attr[Combat.CASTING_SPELL] = spell
            assertFalse(MagicCombatStrategy.canAttack(player, target(FROZEN_TIMER)), "$spell on a frozen target")
            assertEquals(runesBefore, player.inventory.toMap(), "$spell: no runes used")
            assertNull(player.attr[Combat.CASTING_SPELL], "$spell: the manual cast is dropped, no cast animation follows")
        }
    }

    @Test
    fun `N39 and N96 - Deadman Tele Block lasts 2,30 or 1,15 with Protect from Magic and says so in the OSRS words`() {
        assertEquals(250, MagicCombatStrategy.teleblockTicks(protectedFromMagic = false))
        assertEquals(125, MagicCombatStrategy.teleblockTicks(protectedFromMagic = true))
        assertEquals(
            "<col=4f006f>A Tele Block spell has been cast on you by Zezima. It will expire in 2 minutes, 30 seconds.</col>",
            MagicCombatStrategy.teleblockMessage("Zezima", 250),
        )
        assertEquals(
            "<col=4f006f>A Tele Block spell has been cast on you by Zezima. It will expire in 1 minute, 15 seconds.</col>",
            MagicCombatStrategy.teleblockMessage("Zezima", 125),
        )
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertFalse("You have been teleblocked!" in source)
    }

    @Test
    fun `N86 - Snare and Entangle deal their OSRS damage, Bind none`() {
        assertEquals(2, CombatSpell.SNARE.maxHit)
        assertEquals(5, CombatSpell.ENTANGLE.maxHit)
        assertTrue(CombatSpell.SNARE.damaging && CombatSpell.ENTANGLE.damaging)
        assertEquals(0, CombatSpell.BIND.maxHit)
        assertFalse(CombatSpell.BIND.damaging)
        assertTrue(CombatSpell.SNARE.effect is gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect.Freeze, "the hit still binds")
    }

    private fun target(timer: gg.rsmod.game.model.timer.TimerKey?): Player {
        val target = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        if (timer != null) timers[timer] = 5
        every { target.timers } returns timers
        return target
    }

    private fun caster(): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.varps } returns VarpSet(VARP_IDS)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3200, 3200)
        val skills = SkillSet(maxSkills = Skills.MAGIC + 1)
        skills.setBaseLevel(Skills.MAGIC, 99)
        skills.setCurrentLevel(Skills.MAGIC, 99)
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var VARP_IDS: Set<Int>

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
            val varbitVarps = listOf(Varbits.SPELLBOOK, CLIENT_SPELLBOOK_VARBIT, MagicSpells.INF_RUNES_VARBIT).map { DEFINITIONS.get(VarbitDef::class.java, it).varp }
            VARP_IDS = (varbitVarps + listOf(Combat.SELECTED_AUTOCAST_VARP)).toSet()
        }
    }
}
