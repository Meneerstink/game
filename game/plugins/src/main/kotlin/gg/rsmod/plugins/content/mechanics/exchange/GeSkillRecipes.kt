package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.cooking.CookingData
import gg.rsmod.plugins.content.skills.crafting.gems.GemData
import gg.rsmod.plugins.content.skills.crafting.glassblowing.GlassData
import gg.rsmod.plugins.content.skills.crafting.jewellery.JewelleryData
import gg.rsmod.plugins.content.skills.crafting.leather.LeatherData
import gg.rsmod.plugins.content.skills.crafting.pottery.PotteryData
import gg.rsmod.plugins.content.skills.crafting.silver.SilverData
import gg.rsmod.plugins.content.skills.crafting.spinning.SpinningData
import gg.rsmod.plugins.content.skills.crafting.tanning.TanningData
import gg.rsmod.plugins.content.skills.crafting.weaponry.BattlestaffData
import gg.rsmod.plugins.content.skills.crafting.weaving.WeavingData
import gg.rsmod.plugins.content.skills.fletching.arrows.ArrowData
import gg.rsmod.plugins.content.skills.fletching.bolttips.BoltTipData
import gg.rsmod.plugins.content.skills.fletching.crossbows.CrossbowData
import gg.rsmod.plugins.content.skills.fletching.feathering.FeatheringData
import gg.rsmod.plugins.content.skills.fletching.javelins.JavelinData
import gg.rsmod.plugins.content.skills.fletching.stringing.StringData
import gg.rsmod.plugins.content.skills.fletching.tippedbolts.TippedBoltData
import gg.rsmod.plugins.content.skills.fletching.whittling.LogData
import gg.rsmod.plugins.content.skills.herblore.HerbData
import gg.rsmod.plugins.content.skills.herblore.mixing.PotionData
import gg.rsmod.plugins.content.skills.smithing.data.BarProducts
import gg.rsmod.plugins.content.skills.smithing.data.SmeltingData

/**
 * HUIS-SKILL-WINST (owner 2026-11-26 'Allebei'): the house buys and sells without limit at a fixed price, so buying the
 * ingredients of a skill product from the house and selling the product back was an endless, riskless gold tap (Onyx amulet
 * (u) + ball of wool 1.63M -> Onyx amulet 2.56M). The owner chose: the house never pays more for a skill product than its
 * ingredients cost in the house, for every production skill.
 *
 * Every recipe here is a [GeHouseLoops.Route] with only the make rule: the house bid for [GeHouseLoops.Route.amount] products
 * is at most the house ask of the consumed ingredients. Tools (chisel, needle, moulds, hammer, knife, glassblowing pipe) are
 * not consumed and do not count. Ingredients a recipe only sometimes uses (thread 1 in 5, the Sustenance sigil secondary) and
 * by-products (the bucket of molten glass) are left out: that lowers the bound, the safe side for the house. A failed roll
 * (burnt food, a crushed gem, iron ore) costs the player the ingredients, so the bound is never above the real cost either.
 * A recipe with an ingredient the house does not sell bounds nothing ([GeHouseLoops.bound]).
 *
 * Not bounded (owner 2026-11-26 'RC niet meenemen'): Runecrafting - the house sells pure essence for ~2 gp, so the bound would
 * make every rune (drops included) worth ~2 gp to the house, while the profit is ordinary skilling (a walk per trip). Farming
 * and Summoning are not here either: produce takes growing time and disease risk, pouches need charms the house does not sell.
 *
 * The data come from the skills' own recipe tables, so a recipe added there is bounded as well. The few recipes that live only
 * in a plugin script are listed by hand at the bottom ([scriptRecipes]); keep them in step with those scripts.
 */
object GeSkillRecipes {
    fun routes(): List<GeHouseLoops.Route> {
        val out = mutableListOf<GeHouseLoops.Route>()

        fun recipe(
            kind: String,
            product: Int,
            amount: Int,
            vararg parts: Pair<Int, Int>,
        ) {
            if (product <= 0 || amount <= 0 || parts.isEmpty()) return
            if (parts.any { (id, n) -> id <= 0 || n <= 0 || id == product }) return
            val map = mutableMapOf<Int, Int>()
            parts.forEach { (id, n) -> map.merge(id, n, Int::plus) }
            out += GeHouseLoops.Route(product, map, breaks = false, makes = true, amount = amount, kind = kind)
        }

        // Crafting.
        for (gem in GemData.values()) recipe("crafting:gem", gem.cut, 1, gem.uncut to 1)
        for (glass in GlassData.values) recipe("crafting:glass", glass.id, 1, Items.MOLTEN_GLASS to 1)
        recipe("crafting:glass", Items.MOLTEN_GLASS, 1, Items.SODA_ASH to 1, Items.BUCKET_OF_SAND to 1)
        for (jewel in JewelleryData.values()) {
            for (item in jewel.products) {
                if (jewel.gemRequired > 0) {
                    recipe("crafting:jewellery", item.resultItem, 1, Items.GOLD_BAR to 1, jewel.gemRequired to 1)
                } else {
                    recipe("crafting:jewellery", item.resultItem, 1, Items.GOLD_BAR to 1)
                }
            }
        }
        for (leather in LeatherData.values) {
            for (item in leather.products) recipe("crafting:leather", item.resultItem, 1, leather.raw to item.amountRequired)
        }
        for (pottery in PotteryData.values) {
            for (item in pottery.products) {
                recipe("crafting:pottery", item.unfired, 1, Items.SOFT_CLAY to if (pottery.urn) 2 else 1)
                if (item.fired > 0) recipe("crafting:pottery", item.fired, 1, item.unfired to 1)
                val rune = pottery.runeRequired
                if (rune != null && item.fired > 0) recipe("crafting:pottery", item.fired + 2, 1, item.fired to 1, rune to 1)
            }
        }
        for (silver in SilverData.values) {
            val parts = listOf(Items.SILVER_BAR to 1) + (silver.extraItems ?: emptyList()).map { it to 1 }
            recipe("crafting:silver", silver.resultItem.id, silver.resultItem.amount, *parts.toTypedArray())
        }
        for (spinning in SpinningData.values) {
            for (raw in spinning.raw) recipe("crafting:spinning", spinning.product, 1, raw to 1)
        }
        for (tan in TanningData.values) recipe("crafting:tanning", tan.tannedId, 1, tan.rawItemId to 1, Items.COINS_995 to tan.price)
        for (weave in WeavingData.values) recipe("crafting:weaving", weave.resultItem, 1, weave.resourceItem.id to weave.resourceItem.amount)
        for (staff in BattlestaffData.values) recipe("crafting:battlestaff", staff.resultItem, 1, Items.BATTLESTAFF to 1, staff.orbId to 1)

        // Fletching.
        for (log in LogData.values) {
            for (item in log.products) recipe("fletching:whittle", item.product, item.amount, log.raw to 1)
        }
        for (bow in StringData.values) {
            for (item in bow.products) recipe("fletching:string", item.product, item.amount, bow.bow_u to 1, Items.BOW_STRING to 1)
        }
        for (crossbow in CrossbowData.values) {
            recipe("fletching:crossbow", crossbow.unstrung, 1, crossbow.stock to 1, crossbow.limbs to 1)
            recipe("fletching:crossbow", crossbow.strung, 1, crossbow.unstrung to 1, Items.CROSSBOW_STRING to 1)
        }
        for (feather in FeatheringData.values()) {
            recipe("fletching:feather", feather.product, 1, feather.raw to 1, Items.FEATHER to feather.feathersRequired)
        }
        for (arrow in ArrowData.values()) recipe("fletching:arrow", arrow.product, 1, arrow.tips to 1, Items.HEADLESS_ARROW to 1)
        for (tip in BoltTipData.values) recipe("fletching:bolttip", tip.tip, 1, tip.gem to 1)
        for (javelin in JavelinData.values) recipe("fletching:javelin", javelin.product, 1, javelin.shaft to 1, javelin.tip to 1)
        for (bolt in TippedBoltData.values) recipe("fletching:tipped", bolt.product, 1, bolt.plainBolt to 1, bolt.tip to 1)

        // Herblore.
        for (herb in HerbData.values()) {
            recipe("herblore:clean", herb.clean, 1, herb.grimy to 1)
            recipe("herblore:unf", herb.unf, 1, herb.clean to 1, Items.VIAL_OF_WATER to 1)
            recipe("herblore:unf", herb.unf, 1, herb.grimy to 1, Items.VIAL_OF_WATER to 1)
        }
        for (potion in PotionData.values()) {
            recipe("herblore:potion", potion.product, 1, potion.primary to 1, potion.secondary to 1)
            potion.secondaryAlt?.let { recipe("herblore:potion", potion.product, 1, potion.primary to 1, it to 1) }
        }

        // Smithing.
        for (bar in SmeltingData.values) {
            if (bar.secondaryCount > 0) {
                recipe("smithing:smelt", bar.product, 1, bar.primaryOre to 1, bar.secondaryOre to bar.secondaryCount)
            } else {
                recipe("smithing:smelt", bar.product, 1, bar.primaryOre to 1)
            }
        }
        for (product in BarProducts.values()) {
            recipe(
                "smithing:smith",
                product.result,
                product.smithingType.producedAmount,
                product.barType.item to product.smithingType.barRequirement,
            )
        }

        // Cooking.
        for (food in CookingData.values) recipe("cooking", food.cooked, 1, food.raw to 1)

        scriptRecipes(::recipe)
        return out
    }

    /** Recipes that only live in a plugin script (no shared data table): kept in step with those scripts by hand. */
    private fun scriptRecipes(recipe: (String, Int, Int, Array<out Pair<Int, Int>>) -> Unit) {
        fun r(
            kind: String,
            product: Int,
            amount: Int,
            vararg parts: Pair<Int, Int>,
        ) = recipe(kind, product, amount, parts)
        // fletching/crossbows/zaryte_crossbow.plugin.kts
        r("fletching:zaryte", Items.ZARYTE_CROSSBOW, 1, Items.ARMADYL_CROSSBOW to 1, Items.NIHIL_HORN to 1, Items.NIHIL_SHARD to 250)
        // fletching/ballista/heavy_ballista.plugin.kts
        r("fletching:ballista", Items.INCOMPLETE_HEAVY_BALLISTA, 1, Items.BALLISTA_LIMBS to 1, Items.HEAVY_FRAME to 1)
        r("fletching:ballista", Items.UNSTRUNG_HEAVY_BALLISTA, 1, Items.INCOMPLETE_HEAVY_BALLISTA to 1, Items.BALLISTA_SPRING to 1)
        r("fletching:ballista", Items.HEAVY_BALLISTA, 1, Items.UNSTRUNG_HEAVY_BALLISTA to 1, Items.MONKEY_TAIL to 1)
        // fletching/antlerbolts/antler_bolts.plugin.kts (BOLTS_PER_ANTLER = 12)
        r("fletching:antler", Items.SUNLIGHT_ANTLER_BOLTS, 12, Items.SUNLIGHT_ANTLER to 1)
        r("fletching:antler", Items.MOONLIGHT_ANTLER_BOLTS, 12, Items.MOONLIGHT_ANTLER to 1)
        // areas/varrock/sawmill_operator.plugin.kts (SAWMILL_PLANKS): no experience, an instant conversion for a fee.
        r("sawmill", Items.PLANK, 1, Items.LOGS to 1, Items.COINS_995 to 100)
        r("sawmill", Items.OAK_PLANK, 1, Items.OAK_LOGS to 1, Items.COINS_995 to 250)
        r("sawmill", Items.TEAK_PLANK, 1, Items.TEAK_LOGS to 1, Items.COINS_995 to 500)
        r("sawmill", Items.MAHOGANY_PLANK, 1, Items.MAHOGANY_LOGS to 1, Items.COINS_995 to 1500)
    }
}
