package gg.rsmod.plugins.content.magic

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.CLIENT_SPELLBOOK_VARBIT
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Audit S-02: a spell of another spellbook (a modified client sending an Ancient Magicks component
 * with spell-on-player/npc while on the standard book) is refused before anything is cast or paid.
 */
class SpellbookCastGuardTests {
    private fun newPlayer(): Player {
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

    private fun giveRunes(
        player: Player,
        spell: CombatSpell,
    ) {
        MagicSpells.getMetadata(spell.uniqueId)!!.runes.forEach { player.inventory.add(it.id, it.amount) }
    }

    @Test
    fun `spellbook interfaces map to their books`() {
        assertEquals(Spellbook.STANDARD, MagicSpells.spellbookOf(192))
        assertEquals(Spellbook.ANCIENT, MagicSpells.spellbookOf(193))
        assertEquals(Spellbook.LUNAR, MagicSpells.spellbookOf(430))
        assertNull(MagicSpells.spellbookOf(662), "the Summoning panel is no spellbook")
    }

    @Test
    fun `an ancient spell from the standard book is refused and costs nothing`() {
        val player = newPlayer()
        giveRunes(player, CombatSpell.ICE_BARRAGE)
        val runesBefore = player.inventory.toMap()
        val metadata = MagicSpells.getMetadata(CombatSpell.ICE_BARRAGE.uniqueId)!!

        assertEquals(MagicSpells.WRONG_SPELLBOOK, MagicSpells.castProblem(player, metadata.lvl, metadata.runes, CombatSpell.ICE_BARRAGE.uniqueId))

        player.attr[Combat.CASTING_SPELL] = CombatSpell.ICE_BARRAGE
        assertFalse(MagicCombatStrategy.canAttack(player, mockk(relaxed = true)))
        assertEquals(runesBefore, player.inventory.toMap(), "no runes were used")
    }

    @Test
    fun `the same spell on the ancient book passes the spellbook check`() {
        val player = newPlayer()
        player.setSpellbook(Spellbook.ANCIENT)
        giveRunes(player, CombatSpell.ICE_BARRAGE)
        val metadata = MagicSpells.getMetadata(CombatSpell.ICE_BARRAGE.uniqueId)!!
        assertNotEquals(MagicSpells.WRONG_SPELLBOOK, MagicSpells.castProblem(player, metadata.lvl, metadata.runes, CombatSpell.ICE_BARRAGE.uniqueId))
    }

    @Test
    fun `a standard spell from the ancient book is refused`() {
        val player = newPlayer()
        player.setSpellbook(Spellbook.ANCIENT)
        val metadata = MagicSpells.getMetadata(CombatSpell.FIRE_BOLT.uniqueId)!!
        assertEquals(MagicSpells.WRONG_SPELLBOOK, MagicSpells.castProblem(player, metadata.lvl, metadata.runes, CombatSpell.FIRE_BOLT.uniqueId))
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
