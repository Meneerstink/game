package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.MessagePublicMessage
import gg.rsmod.game.message.impl.PublicChatMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.util.Misc.formatForDisplay
import gg.rsmod.util.Misc.formatSentence

/**
 * Owner 2026-09-30: explicit chat-channel prefixes "/p " public, "/f " friends chat, "/c " clan chat, "/g " guest clan chat, next to
 * the 2011 client's own "/", "//" and "///". Each is rewritten to the one route that channel already has, so no channel can leak
 * into another; a prefix needs the space, so a friends-chat line such as "/fun" still goes to the friends chat.
 */
internal fun routeChannelPrefix(text: String): String {
    if (text.length < 3 || text[0] != '/' || text[2] != ' ') return text
    val rest = text.substring(3)
    return when (text[1].lowercaseChar()) {
        'p' -> rest
        'f' -> "/$rest"
        'c' -> "//$rest"
        'g' -> "///$rest"
        else -> text
    }
}

/**
 * N239 (owner 2026-10-23: "in een clan chat of friends chat lukt typen niet: tekst gaat naar publieke chat"): the 2011 client strips
 * the "/", "//" or "///" a player types and announces the channel with CHAT_SETMODE instead (clientscript 4730), so the line came
 * in without its prefix and went to public chat. A line sent in channel mode 1 / 2 / 3 gets the friends / clan / guest-clan prefix
 * back, so it takes the one route that channel has; a line that still carries a prefix (an older client) keeps it.
 */
internal fun routeChannelMode(
    text: String,
    mode: Int,
): String = if (mode in 1..3 && !text.startsWith("/") && !text.startsWith("::")) "/".repeat(mode) + text else text

/**
 * @author Tom <rspsmods@gmail.com>
 */
class MessagePublicHandler : MessageHandler<MessagePublicMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: MessagePublicMessage,
    ) {
        val decompressed = ByteArray(256)
        val huffman = world.huffman
        huffman.decompress(message.data, decompressed, message.length)

        val unpacked = routeChannelMode(routeChannelPrefix(String(decompressed, 0, message.length)), client.attr[CHAT_MODE_ATTR] ?: 0)

        if (unpacked.startsWith("::yell")) {
            client.writeMessage("To talk in the global chat, start your message in public chat with a period (.)")
            return
        }

        if (unpacked.startsWith("::commands")) {
            // Owner 2026-10-10: the staff command list (moderators and up); everyone else gets the console hint.
            if (!world.plugins.executeCommand(client, "commands")) {
                client.writeMessage("To use commands, press the (`) key on your keyboard.")
            }
            return
        }

        // Owner 2026-10-10 (staff tools): a muted player's typed chat - public, global, friends and clan chat - goes nowhere.
        if (gg.rsmod.game.model.priv.Punishments.refuseChat(client)) return

        /*
         * The client has no protocol of its own for friends-chat text: the chatbox sends everything
         * through MESSAGE_PUBLIC, and the leading '/' is what marks a line as belonging to the
         * channel instead of to the players standing nearby.
         */
        // "///" talks in the clan channel the player listens to as a guest.
        if (unpacked.startsWith("///")) {
            val player = client as Player
            val text = formatSentence(unpacked.substring(3))
            val hook = world.socialHooks.clanGuestTalk
            if (text.isNotBlank() && (hook == null || !hook(player, text))) {
                player.writeMessage("You are not in a guest clan channel.")
            }
            return
        }

        // "//" talks in the player's clan channel (the clan plugin's SocialHooks.clanTalk); a single "/" is the friends chat.
        if (unpacked.startsWith("//")) {
            val player = client as Player
            val text = formatSentence(unpacked.substring(2))
            val hook = world.socialHooks.clanTalk
            if (text.isNotBlank() && (hook == null || !hook(player, text))) {
                player.writeMessage("You are not in a clan channel.")
            }
            return
        }

        if (unpacked.startsWith("/")) {
            val player = client as Player
            val text = formatSentence(unpacked.substring(1))
            if (text.isNotBlank() && !world.friendsChat.talk(world, player, text)) {
                player.writeMessage("You are not in a friends chat channel.")
            }
            return
        }

        if (unpacked.startsWith(".")) {
            val player = client as Player
            val icon =
                when (player.privilege.id) {
                    1 -> "<img=0>"
                    2 -> "<img=1>"
                    else -> ""
                }
            world.players.forEach {
                val color =
                    when (it.interfaces.displayMode) {
                        DisplayMode.FIXED -> "0000ff"
                        DisplayMode.RESIZABLE_NORMAL -> "7fa9ff"
                        else -> "#fa9ff"
                    }
                it.writeMessage(
                    "[<col=d45b5b>Global</col>] $icon${formatForDisplay(player.username)}: <col=$color>${
                        formatSentence(
                            unpacked.substring(1),
                        )
                    }</col>",
                )
            }
            return
        }

        world.players.forEach {
            if (it.tile.isWithinRadius(client.tile, 30)) {
                it.write(
                    PublicChatMessage(
                        world,
                        formatSentence(unpacked),
                        client.index,
                        client.privilege.icon,
                        message.effect,
                    ),
                )
            }
        }

        world.getService(LoggerService::class.java, searchSubclasses = true)?.logPublicChat(client, unpacked)
    }
}
