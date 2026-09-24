package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * ClientProt OPPLAYER9 (43): the ninth player op. In the 667 client the chatbox sends it when a clan-invite line ("X is inviting
 * you to join their clan.", chat type 117) is clicked, with the inviter's player index (Novite 667 PlayerOptionPacket option 9).
 */
class OpPlayer9Message(
    val index: Int,
) : Message
