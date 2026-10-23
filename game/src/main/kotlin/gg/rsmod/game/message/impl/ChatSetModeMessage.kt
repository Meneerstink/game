package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * N239: the 2011 client's CHAT_SETMODE (client opcode 23, one byte): the channel the next MESSAGE_PUBLIC lines belong to - 0 public,
 * 1 friends chat, 2 clan chat, 3 guest clan chat. The chat input script 4730 strips the "/", "//" or "///" a player types, sends
 * this mode, the line, and the player's default mode again.
 */
data class ChatSetModeMessage(val mode: Int) : Message
