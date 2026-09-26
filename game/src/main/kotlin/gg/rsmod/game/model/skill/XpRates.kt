package gg.rsmod.game.model.skill

/**
 * Server-wide XP rate set from `data/cfg/new_player.yml` (`xp.normal_rate`, loaded by the plugins' NewPlayerConfig).
 *
 * [Player.addXp][gg.rsmod.game.model.entity.Player.addXp] multiplies a gain by [normal] only when it applies its
 * modifiers, i.e. on top of the level curve and the time-played bonus XP; a call with `modifiers = false` stays exact.
 * 1.0 (the default) changes nothing.
 */
object XpRates {
    @Volatile
    var normal: Double = 1.0
        set(value) {
            require(value > 0.0 && value.isFinite()) { "xp rate must be a positive number, was $value" }
            field = value
        }
}
