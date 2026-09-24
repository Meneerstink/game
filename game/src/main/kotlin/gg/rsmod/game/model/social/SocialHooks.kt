package gg.rsmod.game.model.social

import gg.rsmod.game.model.entity.Player

/**
 * Entry points the net layer calls for the clan system, which lives in the plugins module (content/mechanics/clan). Null until the
 * clan plugin installs them.
 */
class SocialHooks {
    /** A "//" chat line; true when it was handled (the sender is in a clan channel or was told why not). */
    var clanTalk: ((Player, String) -> Boolean)? = null

    /** A "///" chat line into the clan channel the player listens to as a guest. */
    var clanGuestTalk: ((Player, String) -> Boolean)? = null

    /** A quick-chat phrase into the clan channel (payload exactly as the client sent it). */
    var clanQuickChat: ((Player, ByteArray) -> Boolean)? = null

    /** ClientProt CLANCHANNEL_KICKUSER. */
    var clanKick: ((Player, Boolean, String) -> Unit)? = null

    /** (observer, other): whether other is in observer's clan - the player-update CLANMATE block (minimap clan dot). */
    var isClanmate: ((Player, Player) -> Boolean)? = null

    /** ClientProt AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL: permanently ban a guest (by display name) of the own clan channel. */
    var clanBanFromChannel: ((Player, String) -> Unit)? = null
}
