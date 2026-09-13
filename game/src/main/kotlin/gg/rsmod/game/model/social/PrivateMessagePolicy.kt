package gg.rsmod.game.model.social

import gg.rsmod.game.model.ChatFilterType

/**
 * RCV-010 D4: the one delivery rule for every private message (the shared `MessagePrivateHandler` path).
 *
 * Root cause: the handler delivered to `world.getPlayerForName(name)!!` with no checks — a message to an offline name
 * threw, and a player on the recipient's ignore list could still message them.
 *
 * Sources:
 * - Void `content/social/chat/Chat.kt` (`instruction<ChatPrivate>`) and `QuickChat.kt`: no delivery when the target is
 *   not online or `target.ignores(sender)`, and the sender is told "Unable to send message - player unavailable.";
 *   Void `Ignores.kt`: an admin sender is never ignored.
 * - Novite 667 `FriendsIgnores.sendMessage`: a sender whose own private status is Off is switched to On when sending.
 * Neither donor refuses delivery because of the recipient's private filter, so no such rule is invented here.
 */
object PrivateMessagePolicy {
    const val UNAVAILABLE = "Unable to send message - player unavailable."

    fun deliveryRefusal(
        targetOnline: Boolean,
        targetIgnoresSender: Boolean,
        senderIsAdmin: Boolean,
    ): String? =
        when {
            !targetOnline -> UNAVAILABLE
            targetIgnoresSender && !senderIsAdmin -> UNAVAILABLE
            else -> null
        }

    fun senderPrivateStatusAfterSending(current: ChatFilterType): ChatFilterType =
        if (current == ChatFilterType.OFF) ChatFilterType.ON else current
}
