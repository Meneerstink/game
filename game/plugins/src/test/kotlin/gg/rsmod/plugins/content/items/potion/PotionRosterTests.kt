package gg.rsmod.plugins.content.items.potion

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.herblore.mixing.PotionData
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RCV-010 A3 (owner live 2026-09-13: Overload "item unhandled", decanting does nothing).
 *
 * Drink effects are deliberately enumerated by [Potion]. Decant and Empty are instead enumerated from the
 * real 667 cache, so those container interactions do not disappear when a drink effect is parked or removed.
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
        Blocked("OWNER_REMOVED: extreme 125-stat/ranged, overload, prayer renewal and recover special") { it.id in RemovedPotions.itemIds },
        Blocked("SEPARATE HANDLER: Deadman blighted overload (mechanics/pvp/breach/blighted_overload.plugin.kts)") { it.id in gg.rsmod.plugins.content.mechanics.pvp.breach.BlightedOverload.DOSES },
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
    fun `owner removed potion families have no drink row or herblore recipe`() {
        assertEquals(28, RemovedPotions.doseItemIds.size)
        assertEquals(32, RemovedPotions.itemIds.size)
        assertTrue(Potion.values().none { it.item in RemovedPotions.itemIds })
        assertTrue(PotionData.values().none { it.product in RemovedPotions.recipeProducts })

        val magic = player()
        PotionType.EXTREME_MAGIC.apply(magic)
        assertEquals(106, magic.skills.getCurrentLevel(Skills.MAGIC), "Extreme magic was not part of the removal request")
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
    fun `every implemented potion consumes on the shared three tick food and potion lock`() {
        Potion.values().forEach { potion ->
            val p = player()
            p.inventory[0] = Item(potion.item)

            Potions.drinkAt(p, potion, 0)

            assertEquals(3, p.timers[POTION_DELAY], "potion delay for ${potion.name}")
            assertEquals(3, p.timers[FOOD_DELAY], "food delay for ${potion.name}")
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
        val families = PotionDecanting.cacheFamilies(DEFINITIONS)
        assertTrue(families.size > PotionDecanting.families.size, "decanting is still limited to implemented drink effects")
        assertTrue(families.flatMap { it.toList() }.none { it in RemovedPotions.itemIds })
        families.forEach { family ->
            fun id(doses: Int) = family[4 - doses]
            for (a in 1..4) for (b in 1..4) {
                val p = player()
                p.inventory[0] = Item(id(a))
                p.inventory[1] = Item(id(b))
                val handled = PotionDecanting.decant(p, 0, 1, families)
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
                assertTrue(PotionDecanting.decant(p, 1, 0, families))
                if (d == 1) {
                    assertEquals(Items.VIAL, p.inventory[0]!!.id)
                    assertEquals(id(1), p.inventory[1]!!.id)
                } else {
                    assertEquals(id(d - d / 2), p.inventory[0]!!.id, "${family[0]} split $d kept")
                    assertEquals(id(d / 2), p.inventory[1]!!.id, "${family[0]} split $d poured")
                }
            }
        }
        val pairs = PotionDecanting.bindingPairs(families)
        assertEquals(pairs.size, pairs.map { minOf(it.first, it.second) to maxOf(it.first, it.second) }.toSet().size, "duplicate bindings")

        val emptyable = PotionDecanting.emptyableDoseItems(DEFINITIONS)
        assertTrue(emptyable.isNotEmpty())
        assertTrue(emptyable.none { it in RemovedPotions.itemIds })
        emptyable.forEach { item ->
            val p = player()
            p.inventory[0] = Item(item)
            val name = DEFINITIONS.get(ItemDef::class.java, item).name
            assertTrue(PotionDecanting.empty(p, 0, forcedContainer = Items.VIAL), name)
            assertEquals(Items.VIAL, p.inventory[0]!!.id, name)
        }
        println("PotionRosterTests: decantFamilies=${families.size} emptyableDoseItems=${emptyable.size}")
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
