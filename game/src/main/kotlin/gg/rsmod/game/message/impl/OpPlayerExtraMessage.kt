package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/** ClientProt OPPLAYER1 and OPPLAYER5-10 (player menu slots 1 and 5-10, e.g. "Challenge" and "Req Assist"). */
data class OpPlayerExtraMessage(val option: Int, val index: Int) : Message