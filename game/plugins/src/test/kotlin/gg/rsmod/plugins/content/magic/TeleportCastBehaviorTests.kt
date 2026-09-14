package gg.rsmod.plugins.content.magic

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import java.lang.ref.WeakReference
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.magic.teleports.RuneFreeTeleportRequirements
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Closes the Package-2 "prove it at actual cast-behavior level" gap: unlike
 * [gg.rsmod.plugins.content.magic.teleports.RuneFreeTeleportRequirementsTests]
 * (which only exercises the filtering function in isolation), these tests
 * drive the real [MagicSpells.canCast]/[MagicSpells.removeRunes] entry points
 * that `teleport_spells.plugin.kts` itself calls, against real
 * [SpellbookData] fixtures and real [ItemContainer]/[SkillSet] state.
 */
class TeleportCastBehaviorTests {
    @Test
    fun `representative teleport succeeds and consumes nothing with sufficient level and empty inventory`() {
        val player = newPlayer(magicLevel = 30)
        val requirements = RuneFreeTeleportRequirements.nonRuneRequirements(SpellbookData.VARROCK_TELEPORT.runes)

        assertTrue(requirements.isEmpty(), "Varrock Teleport's runes are all real runes and must be filtered to none")
        assertTrue(MagicSpells.canCast(player, SpellbookData.VARROCK_TELEPORT.level, requirements))

        MagicSpells.removeRunes(player, requirements, SpellbookData.VARROCK_TELEPORT.uniqueId)

        assertEquals(0, player.inventory.getItemCount(Items.FIRE_RUNE))
        assertEquals(0, player.inventory.getItemCount(Items.AIR_RUNE))
        assertEquals(0, player.inventory.getItemCount(Items.LAW_RUNE))
    }

    @Test
    fun `representative combat spell still requires and still consumes its runes`() {
        val player = newPlayer(magicLevel = SpellbookData.WIND_STRIKE.level)
        player.inventory.add(Items.AIR_RUNE, 1)
        player.inventory.add(Items.MIND_RUNE, 1)

        assertTrue(MagicSpells.canCast(player, SpellbookData.WIND_STRIKE.level, SpellbookData.WIND_STRIKE.runes))

        MagicSpells.removeRunes(player, SpellbookData.WIND_STRIKE.runes, SpellbookData.WIND_STRIKE.uniqueId)

        assertEquals(0, player.inventory.getItemCount(Items.AIR_RUNE), "combat spells were never made rune-free")
        assertEquals(0, player.inventory.getItemCount(Items.MIND_RUNE))
    }

    @Test
    fun `combat spell cast is blocked when its runes are missing`() {
        val player = newPlayer(magicLevel = SpellbookData.WIND_STRIKE.level)
        player.inventory.add(Items.AIR_RUNE, 1)
        // Mind rune deliberately withheld.

        assertFalse(MagicSpells.canCast(player, SpellbookData.WIND_STRIKE.level, SpellbookData.WIND_STRIKE.runes))
    }

    @Test
    fun `teleport cast is still blocked below the spell's magic level despite having zero rune requirements`() {
        val player = newPlayer(magicLevel = SpellbookData.VARROCK_TELEPORT.level - 1)
        val requirements = RuneFreeTeleportRequirements.nonRuneRequirements(SpellbookData.VARROCK_TELEPORT.runes)

        assertFalse(
            MagicSpells.canCast(player, SpellbookData.VARROCK_TELEPORT.level, requirements),
            "the rune-free mechanism must not have loosened the Magic-level gate",
        )
    }

    @Test
    fun `ape atoll teleport still requires and consumes its banana while staying rune-free`() {
        val player = newPlayer(magicLevel = SpellbookData.APE_ATOLL_TELEPORT.level)
        val requirements = RuneFreeTeleportRequirements.nonRuneRequirements(SpellbookData.APE_ATOLL_TELEPORT.runes)
        assertEquals(listOf(Items.BANANA), requirements.map { it.id })

        // Without the banana, the cast must still fail.
        assertFalse(MagicSpells.canCast(player, SpellbookData.APE_ATOLL_TELEPORT.level, requirements))

        player.inventory.add(Items.BANANA, 1)
        assertTrue(MagicSpells.canCast(player, SpellbookData.APE_ATOLL_TELEPORT.level, requirements))

        MagicSpells.removeRunes(player, requirements, SpellbookData.APE_ATOLL_TELEPORT.uniqueId)

        assertEquals(0, player.inventory.getItemCount(Items.BANANA), "the banana is a real ingredient, not a rune")
        assertEquals(0, player.inventory.getItemCount(Items.FIRE_RUNE))
        assertEquals(0, player.inventory.getItemCount(Items.WATER_RUNE))
        assertEquals(0, player.inventory.getItemCount(Items.LAW_RUNE))
    }

    @Test
    fun `wilderness teleport restriction still blocks a deep-wilderness teleport regardless of the rune-free mechanism`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        // Region 12092 (x=47, z=60 in region coordinates) is deep wilderness;
        // wilderness level (3900 - 3520) / 8 + 1 = 48, far past MODERN's
        // default wildLvlRestriction of 20. canTeleport() never sees the
        // rune-free item list at all - it is called independently of it.
        every { player.tile } returns Tile(3040, 3900)

        assertFalse(player.canTeleport(TeleportType.MODERN))
    }

    @Test
    fun `wilderness teleport restriction still allows a shallow-wilderness teleport within the limit`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        // Region 12087, wilderness level (3576 - 3520) / 8 + 1 = 8, within
        // MODERN's default wildLvlRestriction of 20.
        every { player.tile } returns Tile(3040, 3576)

        assertTrue(player.canTeleport(TeleportType.MODERN))
    }

    @Test
    fun `ordinary teleport is allowed during player combat unless teleblocked or above the wilderness limit`() {
        // Novite's ten-second combat wait is in HomeTeleport.process, not ordinary teleport checks.
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576) // shallow wilderness, otherwise allowed
        val timers = TimerMap()
        timers[ACTIVE_COMBAT_TIMER] = 17
        every { player.timers } returns timers
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))

        assertTrue(player.canTeleport(TeleportType.MODERN))
    }

    @Test
    fun `an active combat timer from an npc hit does not block teleporting, as in OSRS`() {
        // RCV-005 owner retest 2026-09-13: teleporting out of NPC combat must work.
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[ACTIVE_COMBAT_TIMER] = 17
        every { player.timers } returns timers
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Npc>(relaxed = true))

        assertTrue(player.canTeleport(TeleportType.MODERN))
    }

    @Test
    fun `teleporting is allowed again once the active combat timer has cleared`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        every { player.timers } returns TimerMap()

        assertTrue(player.canTeleport(TeleportType.MODERN))
    }

    private fun newPlayer(magicLevel: Int): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.varps } returns VARPS
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val skills = SkillSet(maxSkills = Skills.MAGIC + 1)
        skills.setBaseLevel(Skills.MAGIC, magicLevel)
        skills.setCurrentLevel(Skills.MAGIC, magicLevel)
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        // Real VarpSet covering the two varps MagicSpells/canCast's failure
        // path touches: the varp backing INF_RUNES_VARBIT (so getVarbit()
        // resolves through the real VarbitDef, exercising the actual
        // cast-behavior code path rather than a stub) and
        // Combat.SELECTED_AUTOCAST_VARP, which the insufficient-level branch
        // resets directly.
        private lateinit var VARPS: VarpSet

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)

            val infRunesVarp = DEFINITIONS.get(VarbitDef::class.java, MagicSpells.INF_RUNES_VARBIT).varp
            VARPS = VarpSet(setOf(infRunesVarp, Combat.SELECTED_AUTOCAST_VARP))
        }
    }
}
