package gg.rsmod.plugins.content.items.potion

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.OVERLOAD_REFRESHES_ATTR
import gg.rsmod.game.model.attr.PRAYER_RENEWAL_TICKS_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.OVERLOAD_TIMER
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.RECOVER_SPECIAL_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RCV-010 A3 (owner live 2026-09-13: Overload "item unhandled", decanting does nothing).
 *
 * Shared path: every potion is a [Potion] row -> `potionDrinking.plugin.kts` binds "Drink" from that table,
 * and [PotionDecanting] derives its families from the same table. The roster is enumerated from the real 667
 * cache so a drinkable potion without a row is named here.
 */
class PotionRosterTests {
    private val sent = mutableListOf<String>()

    private fun player(lifepoints: Int = 99): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
            skills.setBaseLevel(skill, 99)
            skills.setCurrentLevel(skill, 99)
        }
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.skills } returns skills
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.tile } returns Tile(3222, 3218, 0)
        every { player.getCurrentLifepoints() } returns lifepoints
        every { player.getCurrentPrayerPoints() } returns 0
        every { player.getMaximumPrayerPoints() } returns 99
        every { player.write(*varargAll<Message> { (it as? MessageGameMessage)?.let { m -> sent.add(m.message) }; true }) } just Runs
        return player
    }

    /**
     * Drinkable dose potions in the 667 cache that are deliberately not handled in this batch, with the
     * exact reason. Anything else without a [Potion] row fails the roster test by name.
     */
    private class Blocked(val reason: String, val matches: (ItemDef) -> Boolean)

    private fun named(vararg prefixes: String): (ItemDef) -> Boolean =
        { def -> prefixes.any { def.name.startsWith(it, ignoreCase = true) } }

    private val blocked = listOf(
        Blocked("PARKED minigame: Castle Wars potions 18715-18738") { it.id in 18715..18738 },
        Blocked("PARKED minigame: Stealing Creation (5)-dose potions 14207-14275") { it.id in 14207..14275 },
        Blocked("PARKED quest item: Goblin potion (Land of the Goblins)", named("Goblin potion")),
        Blocked("SEPARATE BATCH A3-b: Barbarian Herblore two-dose mixes (potion effect + roe/caviar heal)") { it.name.matches(Regex(""".* mix \([12]\)""", RegexOption.IGNORE_CASE)) || it.name.startsWith("Anti-p supermix", true) },
        Blocked("SEPARATE BATCH A3-c: brewed keg/mature ales (food/drink table, Void MatureAle)", named("Asgarnian ale", "Mind bomb", "Dwarven stout", "Greenman's ale", "Dragon bitter", "Moonlight mead", "Axeman's folly", "Chef's delight", "Slayer's respite", "Cider")),
        Blocked("SOURCE_BLOCKED: Herblore Habitat juju/scentless/god potions have no donor effect", named("Juju", "Scentless potion", "Saradomin's blessing", "Guthix's gift", "Zamorak's favour")),
        Blocked("BLOCKED: Relicym's balm cures disease; the disease mechanic does not exist server-side", named("Relicym's balm")),
        Blocked("SOURCE_CONFLICT: Guthix rest empty container (Void data: empty = vial, excess = empty_cup)", named("Guthix rest")),
    )

    @Test
    fun `every drinkable dose potion in the 667 cache has a drink row`() {
        val handled = Potion.values().map { it.item }.toSet()
        val dose = Regex(""".*\((\d)\)$""")
        val drinkable = DEFINITIONS.getAll<ItemDef>(ItemDef::class.java).values.filterIsInstance<ItemDef>()
            .filter { !it.noted && it.inventoryMenu.any { option -> option.equals("Drink", ignoreCase = true) } && dose.matches(it.name) }
        assertTrue(drinkable.size > 100, "cache roster too small: ${drinkable.size}")
        val missing = drinkable
            .filter { it.id !in handled && blocked.none { entry -> entry.matches(it) } }
            .map { "${it.id} ${it.name}" }
        println("PotionRosterTests: drinkable=${drinkable.size} handled=${drinkable.count { it.id in handled }} " +
            blocked.joinToString("; ") { entry -> "${entry.reason}=${drinkable.count { it.id !in handled && entry.matches(it) }}" })
        assertTrue(missing.isEmpty(), "drinkable 667 potions without a drink row (${missing.size}):\n" + missing.joinToString("\n"))
    }

    @Test
    fun `every potion row is a real drinkable cache item`() {
        val offenders = Potion.values().filter { potion ->
            val def = DEFINITIONS.getNullable(ItemDef::class.java, potion.item)
            def == null || def.inventoryMenu.none { it.equals("Drink", ignoreCase = true) }
        }.map { "${it.name} ${it.item}" }
        assertTrue(offenders.isEmpty(), "potion rows that are not drinkable cache items:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `extreme potions and overload use the Novite 667 formulas`() {
        val expected = mapOf(Skills.ATTACK to 125, Skills.STRENGTH to 125, Skills.DEFENCE to 125, Skills.MAGIC to 106, Skills.RANGED to 122)
        listOf(PotionType.EXTREME_ATTACK to Skills.ATTACK, PotionType.EXTREME_STRENGTH to Skills.STRENGTH, PotionType.EXTREME_DEFENCE to Skills.DEFENCE,
            PotionType.EXTREME_MAGIC to Skills.MAGIC, PotionType.EXTREME_RANGING to Skills.RANGED).forEach { (type, skill) ->
            val p = player()
            assertTrue(type.canDrink(p))
            type.apply(p)
            assertEquals(expected[skill], p.skills.getCurrentLevel(skill), type.name)
        }
        val p = player()
        PotionType.OVERLOAD.apply(p)
        expected.forEach { (skill, level) -> assertEquals(level, p.skills.getCurrentLevel(skill), "overload skill $skill") }
    }

    @Test
    fun `overload refuses while active and at 50 life points or less, then runs twenty refreshes and restores`() {
        val low = player(lifepoints = 50)
        assertFalse(PotionType.OVERLOAD.canDrink(low))
        assertTrue(sent.last().contains("more than 500 life points"))

        val p = player()
        assertTrue(PotionType.OVERLOAD.canDrink(p))
        PotionType.OVERLOAD.apply(p)
        assertEquals(PotionEffects.OVERLOAD_REFRESH_TICKS, p.timers[OVERLOAD_TIMER])
        assertFalse(PotionType.OVERLOAD.canDrink(p))
        assertEquals("You may only use this potion every five minutes.", sent.last())

        var ticks = 0
        while (p.attr.has(OVERLOAD_REFRESHES_ATTR)) {
            PotionEffects.tickOverload(p)
            ticks++
        }
        assertEquals(PotionEffects.OVERLOAD_REFRESHES, ticks)
        PotionEffects.OVERLOAD_SKILLS.forEach { assertEquals(99, p.skills.getCurrentLevel(it)) }
        verify(exactly = 1) { p.alterLifepoints(PotionEffects.OVERLOAD_END_HEAL, 0) }
        assertEquals(PotionEffects.OVERLOAD_END_MESSAGE, sent.last())
    }

    @Test
    fun `prayer renewal restores one real point per ten ticks and warns and ends with the Novite messages`() {
        val p = player()
        PotionType.PRAYER_RENEWAL.apply(p)
        while (p.attr.has(PRAYER_RENEWAL_TICKS_ATTR)) PotionEffects.tickPrayerRenewal(p)
        verify(exactly = 50) { p.alterPrayerPoints(1, 0) }
        assertEquals(1, sent.count { it == PotionEffects.RENEWAL_WARNING_MESSAGE })
        assertEquals(PotionEffects.RENEWAL_END_MESSAGE, sent.last())
    }

    @Test
    fun `recover special restores 25 percent and refuses for 30 seconds`() {
        mockkObject(AttackTab)
        try {
            val p = player()
            every { AttackTab.getEnergy(p) } returns 60
            every { AttackTab.setEnergy(p, any()) } just Runs
            assertTrue(PotionType.RECOVER_SPECIAL.canDrink(p))
            PotionType.RECOVER_SPECIAL.apply(p)
            verify { AttackTab.setEnergy(p, 85) }
            assertEquals(PotionEffects.RECOVER_SPECIAL_COOLDOWN_TICKS, p.timers[RECOVER_SPECIAL_TIMER])
            assertFalse(PotionType.RECOVER_SPECIAL.canDrink(p))
            assertEquals("You may only use this pot every 30 seconds.", sent.last())
        } finally {
            unmockkObject(AttackTab)
        }
    }

    @Test
    fun `zamorak brew uses the Void 2011 boosts and refuses nothing at full health`() {
        val p = player()
        assertTrue(PotionType.ZAMORAK_BREW.canDrink(p))
        assertEquals(11, PotionEffects.zamorakBrewDamage(p))
        PotionType.ZAMORAK_BREW.apply(p)
        assertEquals(120, p.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(112, p.skills.getCurrentLevel(Skills.STRENGTH))
        assertEquals(88, p.skills.getCurrentLevel(Skills.DEFENCE))
    }

    @Test
    fun `antipoison immunity durations follow the 2011 donors`() {
        mapOf(
            PotionType.ANTIPOISON to 150,
            PotionType.SUPER_ANTIPOISON to 600,
            PotionType.ANTIPOISON_PLUS to 900,
            PotionType.ANTIPOISON_PLUS_PLUS to 1200,
            PotionType.SANFEW_SERUM to 600,
        ).forEach { (type, ticks) ->
            val p = player()
            type.apply(p)
            assertEquals(ticks, p.timers[POISON_IMMUNITY], type.name)
        }
    }

    @Test
    fun `flat skill potions boost three levels above the base level`() {
        mapOf(
            PotionType.FISHING to Skills.FISHING,
            PotionType.AGILITY to Skills.AGILITY,
            PotionType.HUNTER to Skills.HUNTER,
            PotionType.CRAFTING to Skills.CRAFTING,
            PotionType.FLETCHING to Skills.FLETCHING,
            PotionType.MAGIC_ESSENCE to Skills.MAGIC,
        ).forEach { (type, skill) ->
            val p = player()
            type.apply(p)
            assertEquals(102, p.skills.getCurrentLevel(skill), type.name)
        }
    }

    @Test
    fun `decanting pours, splits and refuses correctly for every potion family`() {
        val families = PotionDecanting.families
        // Derived, not a magic number: every four-dose row in the table starts exactly one family.
        val fourDoseRows = Potion.values().filter { DEFINITIONS.get(ItemDef::class.java, it.item).name.endsWith("(4)") }
            .filterNot { row -> Potion.values().any { it.replacement == row.item } }
        val unfamilied = fourDoseRows.filter { row -> families.none { it[0] == row.item } }.map { it.name }
        assertTrue(unfamilied.isEmpty(), "four-dose potions without a decanting family: $unfamilied")
        assertEquals(fourDoseRows.size, families.size)
        families.forEach { family ->
            fun id(doses: Int) = family[4 - doses]
            for (a in 1..4) for (b in 1..4) {
                val p = player()
                p.inventory[0] = Item(id(a))
                p.inventory[1] = Item(id(b))
                val handled = PotionDecanting.decant(p, 0, 1)
                if (b == 4) {
                    assertFalse(handled, "${family[0]} $a on full $b")
                    assertEquals(id(a), p.inventory[0]!!.id)
                    continue
                }
                val total = a + b
                assertEquals(id(minOf(4, total)), p.inventory[1]!!.id, "${family[0]} $a on $b target")
                assertEquals(if (total > 4) id(total - 4) else Items.VIAL, p.inventory[0]!!.id, "${family[0]} $a on $b source")
            }
            for (d in 1..4) {
                val p = player()
                p.inventory[0] = Item(id(d))
                p.inventory[1] = Item(Items.VIAL)
                assertTrue(PotionDecanting.decant(p, 1, 0))
                if (d == 1) {
                    assertEquals(Items.VIAL, p.inventory[0]!!.id)
                    assertEquals(id(1), p.inventory[1]!!.id)
                } else {
                    assertEquals(id(d - d / 2), p.inventory[0]!!.id, "${family[0]} split $d kept")
                    assertEquals(id(d / 2), p.inventory[1]!!.id, "${family[0]} split $d poured")
                }
            }
        }
        val pairs = PotionDecanting.bindingPairs()
        assertEquals(pairs.size, pairs.map { minOf(it.first, it.second) to maxOf(it.first, it.second) }.toSet().size, "duplicate bindings")
    }

    @Test
    fun `overload and renewal presentation ids exist in the 667 cache`() {
        assertNotNull(DEFINITIONS.getNullable(AnimDef::class.java, PotionEffects.OVERLOAD_ANIMATION))
        assertNotNull(DEFINITIONS.getNullable(SpotAnimDef::class.java, PotionEffects.OVERLOAD_GRAPHIC))
        assertNotNull(DEFINITIONS.getNullable(SpotAnimDef::class.java, PotionEffects.PRAYER_RENEWAL_GRAPHIC))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
        }
    }
}
