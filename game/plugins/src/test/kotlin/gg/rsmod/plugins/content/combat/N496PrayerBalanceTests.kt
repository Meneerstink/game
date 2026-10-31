package gg.rsmod.plugins.content.combat

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.RuinousPowers
import gg.rsmod.plugins.content.mechanics.prayer.RuinousPrayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N496 (owner 2026-10-31 22:4x "alle 11 bouwen"): the max hits through the real formulas, 99 + potion, best gear (melee +150
 * strength, aggressive; ranged +120 ranged strength, rapid). Owner rule: melee and ranged Ruinous = Normal +2 and Curses = Normal +1
 * (dossier N496, team/dossiers/N496-TOTAALOVERZICHT.html). Before: Piety 52 / Turmoil 56 / Decimate 54 and Rigour 42 / Leech
 * Ranged 39 / Annihilate 43.
 */
class N496PrayerBalanceTests {
    private enum class Book { PIETY, TURMOIL, DECIMATE, RIGOUR, LEECH_RANGED, LEECH_RANGED_FULL, ANNIHILATE }

    private fun maxHit(book: Book): Int {
        mockkObject(CombatConfigs, Prayers, AncientCurses, RuinousPowers)
        try {
            val melee = book in setOf(Book.PIETY, Book.TURMOIL, Book.DECIMATE)
            val player = mockk<Player>(relaxed = true)
            every { player.prayerIcon } returns PrayerIcon.NONE.id
            val skills =
                SkillSet(maxSkills = 7).apply {
                    setBaseLevel(Skills.STRENGTH, 99)
                    setCurrentLevel(Skills.STRENGTH, 118)
                    setBaseLevel(Skills.RANGED, 99)
                    setCurrentLevel(Skills.RANGED, if (book == Book.LEECH_RANGED_FULL) 117 else 112)
                }
            every { player.skills } returns skills
            val bonuses = IntArray(18)
            if (melee) bonuses[BonusSlot.STRENGTH_BONUS.id] = 150 else bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] = 120
            every { player.equipmentBonuses } returns bonuses
            every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
            every { player.attr } returns AttributeMap()
            every { CombatConfigs.getAttackStyle(player) } returns if (melee) WeaponStyle.AGGRESSIVE else WeaponStyle.RAPID
            every { Prayers.isActive(any(), any()) } returns false
            every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.NORMAL
            every { AncientCurses.isTurmoilActive(player) } returns false
            every { AncientCurses.isCurseActive(player, any()) } returns false
            when (book) {
                Book.PIETY -> every { Prayers.isActive(player, Prayer.PIETY) } returns true
                Book.RIGOUR -> every { Prayers.isActive(player, Prayer.RIGOUR) } returns true
                Book.TURMOIL -> {
                    every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.ANCIENT
                    every { AncientCurses.isTurmoilActive(player) } returns true
                    // Against a 99 opponent: 23 % + (99 x 4 / 100 = 3 levels -> 3 %) - AncientCurses2011Tests covers the snapshot.
                    every { AncientCurses.turmoilMultiplier(player, Skills.STRENGTH, any()) } returns
                        1.0 + (23 + (99 * AncientCurses.TURMOIL_TARGET_STRENGTH_PCT / 100) * 100 / 99) / 100.0
                }
                Book.LEECH_RANGED, Book.LEECH_RANGED_FULL -> {
                    every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.ANCIENT
                    every { AncientCurses.isCurseActive(player, any()) } answers { secondArg<AncientCurse>() == AncientCurse.LEECH_RANGED }
                }
                Book.DECIMATE, Book.ANNIHILATE -> {
                    every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.RUINOUS
                    val prayer = if (book == Book.DECIMATE) RuinousPrayer.DECIMATE else RuinousPrayer.ANNIHILATE
                    every { RuinousPowers.anyActive(player) } returns true
                    every { RuinousPowers.isActive(player, any()) } answers { secondArg<RuinousPrayer>() == prayer }
                }
            }
            val target = mockk<Player>(relaxed = true)
            every { target.prayerIcon } returns PrayerIcon.NONE.id
            every { target.attr } returns AttributeMap()
            return if (melee) {
                MeleeCombatFormula.getMaxHit(player, target, 1.0, 1.0).toInt()
            } else {
                RangedCombatFormula.getMaxHit(player, target, 1.0, 1.0).toInt()
            }
        } finally {
            unmockkObject(CombatConfigs, Prayers, AncientCurses, RuinousPowers)
        }
    }

    @Test
    fun `melee - Piety 52, Turmoil 53, Decimate 54`() {
        assertEquals(52, maxHit(Book.PIETY))
        assertEquals(53, maxHit(Book.TURMOIL))
        assertEquals(54, maxHit(Book.DECIMATE))
    }

    @Test
    fun `ranged - Rigour 42, Leech Ranged 42 then 43 with 5 leeched levels, Annihilate 44`() {
        assertEquals(42, maxHit(Book.RIGOUR))
        assertEquals(42, maxHit(Book.LEECH_RANGED))
        assertEquals(43, maxHit(Book.LEECH_RANGED_FULL))
        assertEquals(44, maxHit(Book.ANNIHILATE))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun load() {
            DEFINITIONS.load(CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString()), ItemDef::class.java)
        }
    }
}
