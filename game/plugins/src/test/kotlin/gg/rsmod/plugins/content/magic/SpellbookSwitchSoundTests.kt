package gg.rsmod.plugins.content.magic

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
 * A24 (owner 2026-10-10: "altar of occult in poh maakt geen geluid"). Every spellbook altar - the POH Altar of the Occult,
 * the Ancient altar 6552, the Astral altar 17010, the Ferox / gilded altar menus, the Deadman skull and the GE unlock
 * npcs - switches through the one shared route [Spellbooks.select]. Our port of the 2009scape MagicAltarListener
 * (`swapSpellBook`: `playAudio(player, Sounds.PRAYER_RECHARGE_2674)` then `animate(player, 645)`) took the animation and
 * messages but dropped the sound (ROOT_CAUSES #177), so every altar switch was silent. Neither cache plays it: 667 seq 645
 * and OSRS seq 7303 (occult loc animation) have no frame sounds, so the server must send it.
 *
 * Class: every successful switch to every book (from every other book, and the altar's toggle back) sends sound 2674 exactly
 * once; a refused switch (quest gate, Magic level) stays silent; no route switches the book outside [Spellbooks.select]
 * except the Spellbook Swap spell (own Void route), the scripted intro npcs and test-only setup (conformance, sweeps).
 */
class SpellbookSwitchSoundTests {
    private class Account {
        val attributes = AttributeMap()
        val skills = SkillSet(25)
        val varps = VarpSet((0..MAX_VARP).toSet())
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val sounds = ArrayList<Int>()

        init {
            every { world.definitions } returns DEFINITIONS
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.varps } returns varps
            every { player.skills } returns skills
            every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
            every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
            every { player.write(*varargAll<Message> { m -> (m as? SynthSoundMessage)?.let { sounds += it.sound }; true }) } just Runs
            skills.setBaseLevel(Skills.MAGIC, 99)
            attributes[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] = true
            attributes[UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] = true
        }
    }

    @Test
    fun `the switch sound is the 2009scape altar sound 2674`() {
        assertEquals(2674, Sfx.PRAYER_RECHARGE)
        assertEquals(645, Spellbooks.SWITCH_ANIM)
    }

    @Test
    fun `every successful switch to every book plays the altar sound once`() {
        for (from in Spellbook.values) {
            for (to in Spellbook.values) {
                if (from == to) continue
                val account = Account()
                account.player.setSpellbook(from)
                assertTrue(Spellbooks.select(account.player, to), "$from -> $to")
                assertEquals(to, account.player.getSpellbook(), "$from -> $to")
                assertEquals(listOf(Sfx.PRAYER_RECHARGE), account.sounds, "$from -> $to must sound once")
            }
        }
    }

    @Test
    fun `the altar toggle plays the sound both ways`() {
        for (book in listOf(Spellbook.ANCIENT, Spellbook.LUNAR)) {
            val account = Account()
            Spellbooks.toggle(account.player, book)
            assertEquals(book, account.player.getSpellbook())
            Spellbooks.toggle(account.player, book)
            assertEquals(Spellbook.STANDARD, account.player.getSpellbook())
            assertEquals(listOf(Sfx.PRAYER_RECHARGE, Sfx.PRAYER_RECHARGE), account.sounds, "$book altar there and back")
        }
    }

    @Test
    fun `a refused switch stays silent`() {
        for (book in listOf(Spellbook.ANCIENT, Spellbook.LUNAR)) {
            val noQuest = Account()
            noQuest.attributes.remove(UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED)
            noQuest.attributes.remove(UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED)
            assertFalse(Spellbooks.select(noQuest.player, book), "$book without the quest")
            assertTrue(noQuest.sounds.isEmpty(), "$book without the quest: ${noQuest.sounds}")

            val lowMagic = Account()
            lowMagic.skills.setBaseLevel(Skills.MAGIC, Spellbooks.requiredLevel(book) - 1)
            assertFalse(Spellbooks.select(lowMagic.player, book), "$book under its Magic level")
            assertTrue(lowMagic.sounds.isEmpty(), "$book under its Magic level: ${lowMagic.sounds}")
            assertNotEquals(book, lowMagic.player.getSpellbook())
        }
    }

    @Test
    fun `no altar or npc route switches the book outside Spellbooks select`() {
        val allowed = setOf("Spellbooks.kt", "SpellbookSwap.kt", "IntroGuardScene.kt", "PlayerExt.kt")
        val offenders =
            File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" || it.extension == "kts" }
                .filter { it.name !in allowed }
                // conformance suites and sweeps (content/cmd) set a bot's book as test setup, not as a player route
                .filter { !it.invariantSeparatorsPath.contains("/content/cmd/") }
                .filter { f -> f.readText().let { it.contains("switchSpellbook(") || it.contains("setSpellbook(") } }
                .map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "spellbook switched without Spellbooks.select (no sound, no gates): $offenders")
    }

    companion object {
        private const val MAX_VARP = 8000
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
