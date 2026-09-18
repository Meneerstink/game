package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Quick chat (owner 2026-09-18: "quick message ... werkt niet"). The rev-667 client sends a quick-chat phrase as its
 * phrase id (g2) followed by the phrase's encoded filler values; the server never needs to understand the fillers, it only
 * relays [payload] byte-for-byte to the recipients, whose client decodes it with the same phrase definition.
 *
 * Client -> server: ClientProt MESSAGE_QUICKCHAT_PUBLIC (30: p1 channel, then payload; 0 = public, 1 = friends chat,
 * 2/3 = clan channels) and MESSAGE_QUICKCHAT_PRIVATE (79: pjstr recipient, then payload).
 */
class MessageQuickChatPublicMessage(val channel: Int, val payload: ByteArray) : Message

class MessageQuickChatPrivateMessage(val username: String, val payload: ByteArray) : Message

/** A server packet whose whole body is prebuilt; each subclass is bound to its own opcode in `packets.yml`. */
sealed class RawPayloadMessage(val body: ByteArray) : Message

/** ServerProt MESSAGE_PUBLIC (91) with the 0x8000 quick-chat flag: g2 slot, g2 flags, g1 rank, payload. */
class QuickChatPublicOutMessage(body: ByteArray) : RawPayloadMessage(body)

/** ServerProt MESSAGE_QUICKCHAT_FRIENDCHAT (20): g1 0, name, g8 channel, g2+g3 id, g1 rank, payload. */
class QuickChatFriendChannelOutMessage(body: ByteArray) : RawPayloadMessage(body)

/** ServerProt MESSAGE_QUICKCHAT_PRIVATE (42): g1 0, name, g2+g3 id, g1 rank, payload. */
class QuickChatPrivateOutMessage(body: ByteArray) : RawPayloadMessage(body)

/** ServerProt MESSAGE_QUICKCHAT_PRIVATE_ECHO (97): recipient name, payload. */
class QuickChatPrivateEchoOutMessage(body: ByteArray) : RawPayloadMessage(body)
