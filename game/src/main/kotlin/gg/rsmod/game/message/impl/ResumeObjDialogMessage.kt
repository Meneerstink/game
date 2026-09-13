package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Revision-667 `RESUME_P_OBJDIALOG` (client prot 13, 2 bytes): the item picked in the chatbox item search, sent by
 * the client's own CS2 handler as `p2(objId)`. The Grand Exchange "Choose Item" search is its caller.
 */
class ResumeObjDialogMessage(
    val item: Int,
) : Message
