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
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.magic.teleports.RuneFreeTeleportRequirements
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
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
    fun `an unskulled player recently hit by another player is refused with the remaining-seconds message, no countdown`() {
        // Owner "deadmanmode vervijning" 2026-09-17 (OSRS Deadman wording): "If a unskulled player has
        // been attacked recently and attempts to teleport, they will instead receive the following
        // game message: You must be out of combat for another X seconds to teleport." - no interface.
        // TELEPORT_COMBAT_TIMER is the dedicated timer Combat.postAttack arms on every landed hit.
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576) // shallow wilderness, otherwise allowed
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER] = 12
        every { player.timers } returns timers
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))

        assertFalse(player.canTeleport(TeleportType.MODERN))
        assertFalse(timers.exists(gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.COUNTDOWN_TIMER), "no countdown interface for an unskulled player")
        assertEquals("You must be out of combat for another 7 seconds to teleport.", gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate.blockedMessage(player))
    }

    @Test
    fun `an unskulled player recently hit by an ordinary npc is refused the same way`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER] = 12
        every { player.timers } returns timers
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Npc>(relaxed = true))

        assertFalse(player.canTeleport(TeleportType.MODERN))
        assertFalse(timers.exists(gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.COUNTDOWN_TIMER), "no countdown interface for an unskulled player")
    }

    @Test
    fun `a skulled player gets the 7-second countdown interface even out of combat`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.SKULL_DURATION_CYCLES
        every { player.timers } returns timers

        assertFalse(player.canTeleport(TeleportType.MODERN))
        assertTrue(timers.exists(gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.COUNTDOWN_TIMER), "the countdown must be armed")
    }

    @Test
    fun `an unskulled player fighting a boss teleports instantly - met uitzondering van alle bosses`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER] = 12
        every { player.timers } returns timers
        val boss = mockk<Npc>(relaxed = true)
        every { boss.id } returns gg.rsmod.plugins.api.cfg.Npcs.KING_BLACK_DRAGON
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(boss)

        assertTrue(player.canTeleport(TeleportType.MODERN))
        assertFalse(timers.exists(gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.COUNTDOWN_TIMER))
    }

    @Test
    fun `teleporting is allowed again once the 7-second combat-recency timer has cleared`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        every { player.timers } returns TimerMap()

        assertTrue(player.canTeleport(TeleportType.MODERN))
    }

    @Test
    fun `a skulled player never gets an instant teleport, even out of combat - the 7-second countdown starts instead`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        // Skulled = the running PK skull timer (PvpSkull.isSkulled); the head icon is derived from it.
        every { player.timers } returns TimerMap().also { it[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] = 500 }

        assertFalse(player.canTeleport(TeleportType.MODERN), "must not teleport instantly while skulled")
    }

    @Test
    fun `the two-arg canTeleport overload runs onConfirmed immediately for an unskulled out-of-combat player`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        every { player.timers } returns TimerMap()
        var ran = false

        val result = player.canTeleport(TeleportType.MODERN) { ran = true }

        assertTrue(result)
        assertTrue(ran, "onConfirmed must run immediately when the gate already passes")
    }

    @Test
    fun `the two-arg canTeleport overload defers onConfirmed for a skulled player until the 7-second countdown finishes`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] = 500
        every { player.timers } returns timers
        var ran = false

        val result = player.canTeleport(TeleportType.MODERN) { ran = true }

        assertFalse(result, "must return false while the countdown is running")
        assertFalse(ran, "onConfirmed must not run yet - it is deferred, not skipped")
        assertTrue(
            timers.exists(gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.COUNTDOWN_TIMER),
            "the 7-second countdown must actually be armed",
        )

        gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.complete(player)

        assertTrue(ran, "onConfirmed must run automatically once the countdown completes - no extra click needed")
    }

    @Test
    fun `a Tele Block applied during the countdown prevents the delayed teleport`() {
        val player = newPlayer(magicLevel = 99)
        every { player.lock } returns LockState.NONE
        every { player.tile } returns Tile(3040, 3576)
        val timers = TimerMap()
        timers[gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER] = 500
        every { player.timers } returns timers
        var ran = false

        assertFalse(player.canTeleport(TeleportType.MODERN) { ran = true })
        timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 20
        gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.complete(player)

        assertFalse(ran, "completion must re-check Tele Block instead of trusting the seven-second-old decision")
    }

    @Test
    fun `delayed teleport callback rechecks the complete spell requirements`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/magic/teleports/teleport_spells.plugin.kts").readText()
        assertTrue(
            "MagicSpells.canCast(this, data.lvl, itemRequirements, data.sprite)" in source,
            "a seven-second teleport callback must revalidate non-rune ingredients before payment",
        )
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
        every { player.timers } returns TimerMap()
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
