package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*

/**
 * Herblore in the raid (OSRS Wiki Chambers of Xeric 'Herblore'; Golpar, Buchu leaf, Noxifer): herbs clean at 47 / 52 / 60 for 1 / 2 / 3 XP;
 * a clean herb, its second ingredient and a water-filled gourd make a 4-dose potion of the best strength the level allows (golpar 47/59/70,
 * buchu 52/65/78; XP 6.5/10/13 and 13.5/20/26.5); overloads take noxifer and an elder, twisted and kodai potion of one strength (60/75/90,
 * XP 33.5/50/66.5) and come out no stronger than the level allows. Golpar + stinkhorn / cicely / endarkened juice = elder / twisted / kodai;
 * buchu + the same = revitalisation / prayer enhance / Xeric's aid. Code help: tsps Herblore.ChambersOfXeric.js.
 * Messages (tsps): 'You clean the <herb>.', 'You mix your <herb> together with your <a> into a potion perfectly!'. Anim 363, sounds 3923 / 2608.
 * LATER: the noxifer antipoison (its potions 25754-25765 are not imported).
 */
object CoxHerblore {
    data class Herb(val name: String, val grimy: Int, val clean: Int, val level: Int, val xp: Double)
    val GOLPAR = Herb("golpar", 20904, 20905, 47, 1.0)
    val BUCHU = Herb("buchu leaf", 20907, 20908, 52, 2.0)
    val NOXIFER = Herb("noxifer", 20901, 20902, 60, 3.0)
    val HERBS = listOf(GOLPAR, BUCHU, NOXIFER)

    const val WATER_GOURD = 20801
    const val STINKHORN = 20910
    const val ENDARKENED = 20911
    const val CICELY = 20912

    data class Mix(val name: String, val herb: Herb, val secondary: Int, val fourDose: IntArray, val levels: IntArray, val xp: DoubleArray)
    private val GOLPAR_LEVELS = intArrayOf(47, 59, 70)
    private val GOLPAR_XP = doubleArrayOf(6.5, 10.0, 13.0)
    private val BUCHU_LEVELS = intArrayOf(52, 65, 78)
    private val BUCHU_XP = doubleArrayOf(13.5, 20.0, 26.5)
    val MIXES = listOf(
        Mix("elder potion", GOLPAR, STINKHORN, intArrayOf(20916, 20920, 20924), GOLPAR_LEVELS, GOLPAR_XP),
        Mix("twisted potion", GOLPAR, CICELY, intArrayOf(20928, 20932, 20936), GOLPAR_LEVELS, GOLPAR_XP),
        Mix("kodai potion", GOLPAR, ENDARKENED, intArrayOf(20940, 20944, 20948), GOLPAR_LEVELS, GOLPAR_XP),
        Mix("revitalisation potion", BUCHU, STINKHORN, intArrayOf(20952, 20956, 20960), BUCHU_LEVELS, BUCHU_XP),
        Mix("prayer enhance potion", BUCHU, CICELY, intArrayOf(20964, 20968, 20972), BUCHU_LEVELS, BUCHU_XP),
        Mix("xeric's aid potion", BUCHU, ENDARKENED, intArrayOf(20976, 20980, 20984), BUCHU_LEVELS, BUCHU_XP),
    )
    private val OVERLOAD_OUT = intArrayOf(20988, 20992, 20996)
    private val OVERLOAD_LEVELS = intArrayOf(60, 75, 90)
    private val OVERLOAD_XP = doubleArrayOf(33.5, 50.0, 66.5)

    /** Every OSRS item that takes part in a mix (item-on-item bindings). */
    val INGREDIENTS: Set<Int> = setOf(WATER_GOURD, STINKHORN, ENDARKENED, CICELY) + HERBS.map { it.clean } +
        MIXES.take(3).flatMap { it.fourDose.toList() }

    private fun has(p: Player, osrs: Int) = CoxIds.item(osrs)?.let { p.inventory.contains(it) } ?: false
    private fun take(p: Player, osrs: Int) = CoxIds.item(osrs)?.let { p.inventory.remove(it, 1) }
    private fun tier(levels: IntArray, level: Int) = levels.indexOfLast { level >= it }

    fun clean(p: Player, herb: Herb) {
        val grimy = CoxIds.item(herb.grimy) ?: return
        val clean = CoxIds.item(herb.clean) ?: return
        if (p.skills.getCurrentLevel(Skills.HERBLORE) < herb.level) { p.message("You need a Herblore level of at least ${herb.level} to clean a ${herb.name}."); return }
        p.queue {
            while (p.inventory.contains(grimy)) {
                val slot = p.inventory.getItemIndex(grimy, skipAttrItems = false)
                if (slot < 0) break
                p.inventory.remove(grimy, 1, beginSlot = slot)
                p.inventory.add(clean, 1, beginSlot = slot)
                p.playSound(3923)
                p.addXp(Skills.HERBLORE, herb.xp)
                p.message("You clean the ${herb.name}.")
                wait(2)
            }
        }
    }

    /** Using two ingredients together: every potion they and the rest of the inventory make, one at a time. */
    fun mix(p: Player) {
        val level = p.skills.getCurrentLevel(Skills.HERBLORE)
        val recipe = MIXES.firstOrNull { m -> tier(m.levels, level) >= 0 && has(p, m.herb.clean) && has(p, m.secondary) && has(p, WATER_GOURD) }
        if (recipe != null) {
            val t = tier(recipe.levels, level)
            take(p, recipe.herb.clean); take(p, recipe.secondary); take(p, WATER_GOURD)
            CoxIds.item(recipe.fourDose[t])?.let { p.inventory.add(it, 1) }
            p.animate(363); p.playSound(2608)
            p.addXp(Skills.HERBLORE, recipe.xp[t])
            val second = p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, CoxIds.item(recipe.secondary) ?: return).name.lowercase()
            p.message("You mix your ${recipe.herb.name} together with your $second into a potion perfectly!")
            return
        }
        val cap = tier(OVERLOAD_LEVELS, level)
        if (cap >= 0 && has(p, NOXIFER.clean)) {
            for (set in 2 downTo 0) {
                val inputs = MIXES.take(3).map { it.fourDose[set] }
                if (!inputs.all { has(p, it) }) continue
                val t = minOf(set, cap)
                take(p, NOXIFER.clean); inputs.forEach { take(p, it) }
                CoxIds.item(OVERLOAD_OUT[t])?.let { p.inventory.add(it, 1) }
                p.animate(363); p.playSound(2608)
                p.addXp(Skills.HERBLORE, OVERLOAD_XP[t])
                p.message("You mix your noxifer together with your elder potion, twisted potion and kodai potion into a potion perfectly!")
                return
            }
        }
        p.message("Nothing interesting happens.")
    }
}
