package gg.rsmod.plugins.content.items.combine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Heavy ballista 3-step assembly (OSRS Wiki "Heavy ballista", fetched 2026-09-16):
 * Ballista limbs + Heavy frame -> Incomplete heavy ballista (30 XP) -> + Ballista spring ->
 * Unstrung heavy ballista (30 XP) -> + Monkey tail -> Heavy ballista (600 XP), 72 Fletching
 * throughout, 660 XP total. Unlike the godsword/ornament-kit families this route is hand-written
 * `on_item_on_item` wiring rather than a `CombinationData` row (the plugin needs three distinct
 * intermediate results from the same two ingredients across steps, which the shared single-result
 * combination table cannot express), so it is guarded here at plugin-source level the same way
 * `GodswordAssemblyTests` guards its own hand-written binding order.
 */
class HeavyBallistaAssemblyTests {
    private val plugin =
        File("src/main/kotlin/gg/rsmod/plugins/content/skills/fletching/ballista/heavy_ballista.plugin.kts").readText()

    @Test
    fun `plugin file exists and requires 72 Fletching for all three steps`() {
        assertTrue("LEVEL_REQUIRED = 72" in plugin)
        val requirementChecks = Regex("getCurrentLevel\\(Skills\\.FLETCHING\\) < LEVEL_REQUIRED").findAll(plugin).count()
        assertTrue("expected a level gate on each of the 3 steps, found $requirementChecks", requirementChecks == 3)
    }

    @Test
    fun `the three steps chain in the real OSRS order with the real XP amounts`() {
        val stepOne = "on_item_on_item(item1 = Items.BALLISTA_LIMBS, item2 = Items.HEAVY_FRAME)"
        val stepTwo = "on_item_on_item(item1 = Items.INCOMPLETE_HEAVY_BALLISTA, item2 = Items.BALLISTA_SPRING)"
        val stepThree = "on_item_on_item(item1 = Items.UNSTRUNG_HEAVY_BALLISTA, item2 = Items.MONKEY_TAIL)"
        listOf(stepOne, stepTwo, stepThree).forEach { assertTrue("missing step: $it", it in plugin) }
        assertTrue(plugin.indexOf(stepOne) < plugin.indexOf(stepTwo))
        assertTrue(plugin.indexOf(stepTwo) < plugin.indexOf(stepThree))

        assertTrue("step 1 must grant Incomplete heavy ballista", "Item(Items.INCOMPLETE_HEAVY_BALLISTA, 1)" in plugin)
        assertTrue("step 2 must grant Unstrung heavy ballista", "Item(Items.UNSTRUNG_HEAVY_BALLISTA, 1)" in plugin)
        assertTrue("step 3 must grant Heavy ballista", "Item(Items.HEAVY_BALLISTA, 1)" in plugin)

        assertTrue("step 1 XP", "addXp(Skills.FLETCHING, 30.0)" in plugin)
        assertTrue("step 3 XP", "addXp(Skills.FLETCHING, 600.0)" in plugin)
        val thirtyXpCount = Regex("addXp\\(Skills\\.FLETCHING, 30\\.0\\)").findAll(plugin).count()
        assertTrue("expected two 30 XP grants (steps 1 and 2), found $thirtyXpCount", thirtyXpCount == 2)
    }

    @Test
    fun `no step is a single 4-item combine`() {
        // The owner's live-tested bug this plugin replaced: a single CombinationData row crafting
        // all four parts (heavy frame + limbs + spring + monkey tail) at once. The real route never
        // consumes more than 2 items at a time (each on_item_on_item call takes only item1/item2).
        val combinationDataFile =
            File("src/main/kotlin/gg/rsmod/plugins/content/items/combine/CombinationData.kt").readText()
        assertTrue(
            "the old single-combine HEAVY_BALLISTA entry must stay removed from CombinationData (HEAVY_BALLISTA_OR, the " +
                "unrelated ornament-kit row, is expected to remain)",
            Regex("""\bHEAVY_BALLISTA\(""").containsMatchIn(combinationDataFile).not(),
        )
    }
}
