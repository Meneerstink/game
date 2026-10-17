package gg.rsmod.game.model.skill

import gg.rsmod.game.model.entity.Player

/**
 * The ONE experience-rate rule (N92, owner 2026-10-17 "VERVANGEN: alleen de Annihilation-rate telt"): [Player.addXp]
 * multiplies every gain that applies its modifiers by [of] - nothing else (the 2011 level curve x1 -> x5 and the
 * time-played bonus XP are gone for normal XP). The plugins install [factor] at load from their Annihilation tier table
 * (`mechanics/pvp/annihilation/AnnihilationTier`: combat 10x/15x/15x/20x per combat bracket, skilling 10x), the same way the
 * Void donor reads one `BonusExperience.multiplier` for every gain. Without plugins (engine unit tests) a gain is exact.
 * A call with `modifiers = false` (the AFK area's own fixed numbers, conformance bots) stays exact.
 */
object XpRates {
    @Volatile
    var factor: ((player: Player, skill: Int) -> Double)? = null

    /** The factor [player] gains [skill] experience at; 1.0 without an installed [factor] or for a nonsense value. */
    fun of(
        player: Player,
        skill: Int,
    ): Double {
        val rate = factor?.invoke(player, skill) ?: return 1.0
        return if (rate > 0.0 && rate.isFinite()) rate else 1.0
    }
}
