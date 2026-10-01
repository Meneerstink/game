package gg.rsmod.game.model.skill

import gg.rsmod.game.model.entity.Player

/**
 * One hook in front of every experience gain ([Player.addXp]): content can take the experience a player would earn and
 * hand it to someone else. Used by the Assist System (plugins `mechanics/assist/AssistSystem`), where the assister
 * "receives all skill experience" for actions done with a borrowed level (RuneScape Wiki "Assist System").
 * The handler returns true when it took the experience; the player then gains nothing. It receives the call's own flags, so a
 * redirected gain is awarded exactly as the original call would have been.
 */
object XpRedirect {
    @Volatile
    var handler: ((player: Player, skill: Int, xp: Double, modifiers: Boolean, disableBonusExperience: Boolean) -> Boolean)? = null
}
