package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.World

/**
 * OSRS Wiki Barrows brother pages (raw wikitext 2026-09-14), the npc set effects:
 * - Dharok "Wretched Strength": "increases his max hit by 1% for each Hitpoint he is missing" (57 at 1 hitpoint).
 * - Verac "Defiler": "25% chance per attack to ignore Defence and armour bonuses"; through Protect from Melee the set effect "lowers
 *   his max hit from 23 to 15". The ignored defence is applied as a landed hit ("negates on average 75% of his attacks").
 * - Guthan "Infestation": "25% chance per successful hit to heal him for whatever damage is dealt".
 * - Torag "Corruption": "25% chance per successful hit to lower the player's energy by 20% of the current amount".
 * - Karil "Tainted Shot": "25% chance per successful hit to lower the player's Agility by 20%" and "his set effect will activate
 *   regardless if the hit would have been successful" through Protect from Missiles.
 * - Ahrim "Blighted Aura": "20% chance to lower the player's Strength stat by 5 for each successful hit", which still activates through
 *   Protect from Magic.
 * SOURCE_GAP: Karil's 20% rounding (floored, as other stat drains); whether Torag/Guthan trigger through Protect from Melee (the npc melee
 * roll fails, so they do not).
 */
object BarrowsSetEffects {
    const val AHRIM = "ahrim_the_blighted"
    const val DHAROK = "dharok_the_wretched"
    const val GUTHAN = "guthan_the_infested"
    const val KARIL = "karil_the_tainted"
    const val TORAG = "torag_the_corrupted"
    const val VERAC = "verac_the_defiled"

    val COMBAT_DEFS = setOf(AHRIM, DHAROK, GUTHAN, KARIL, TORAG, VERAC)

    const val SET_EFFECT_CHANCE_PERCENT = 25
    const val AHRIM_CHANCE_PERCENT = 20
    const val VERAC_PROTECTED_MAX_HIT = 15.0
    const val AHRIM_STRENGTH_DRAIN = 5

    fun dharokMaxHit(
        baseMaxHit: Double,
        currentHitpoints: Int,
        maxHitpoints: Int,
    ): Double = baseMaxHit * (1.0 + (maxHitpoints - currentHitpoints).coerceAtLeast(0) / 100.0)

    fun toragEnergyAfter(energy: Double): Double = energy - energy * 0.2

    fun karilAgilityDrain(currentLevel: Int): Int = (currentLevel * 0.2).toInt()

    fun rolls(
        world: World,
        percent: Int,
    ): Boolean = world.random(99) < percent
}
