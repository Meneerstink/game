package gg.rsmod.plugins.content.mechanics.pvp.annihilation

import gg.rsmod.game.model.skill.XpRates
import gg.rsmod.plugins.api.ext.calculateAndSetCombatLevel

/*
 * N92 + N100: the Annihilation tier (AnnihilationTier) is the engine's one experience rate. Player.addXp reads it through
 * XpRates for every gain with modifiers on (skilling, combat, familiars, lamps, quest XP); the drop roll reads it in
 * DropTableFactory. The red rate line is sent from the level-up route (skills/level_up.plugin.kts).
 */

XpRates.factor = { player, skill -> AnnihilationTier.xpRate(player, skill) }

on_login {
    // The combat level is known before the tier is recorded (runetek5's login computes it too; doing it here keeps the
    // order of the login scripts irrelevant).
    player.calculateAndSetCombatLevel()
    AnnihilationTier.recordAtLogin(player)
}
