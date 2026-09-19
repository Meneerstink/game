package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Client prot SEND_SNAPSHOT (opcode 80, var byte), sent by CS2 CHAT_SENDABUSEREPORT from the Report Abuse window (594):
 * offender name, rule index (the client already subtracts 1), moderator mute flag and an optional comment (max 80 chars).
 */
data class ReportAbuseMessage(
    val name: String,
    val rule: Int,
    val mute: Boolean,
    val comment: String,
) : Message
