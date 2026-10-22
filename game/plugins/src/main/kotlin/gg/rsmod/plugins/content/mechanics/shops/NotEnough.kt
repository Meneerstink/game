package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound

/**
 * N89 (owner 2026-10-22): "kopen zonder genoeg geld/punten: de melding komt zonder geluid; RuneScape speelt daar een geluid bij
 * (alle shops/stores, coins en punten)". One place for the line and the error sound, used by every shop and store refusal.
 */
fun Player.notEnough(line: String) {
    message(line)
    playSound(Sfx.ERRORSOUND)
}
