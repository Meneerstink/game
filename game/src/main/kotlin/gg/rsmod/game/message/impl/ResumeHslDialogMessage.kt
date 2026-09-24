package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/** ClientProt RESUME_P_HSLDIALOG (22): the colour chosen in the HSL colour picker (interface 1106), a 16-bit HSL value. */
class ResumeHslDialogMessage(
    val hsl: Int,
) : Message
