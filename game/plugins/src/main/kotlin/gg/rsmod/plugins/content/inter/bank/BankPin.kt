package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setVarc
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The bank PIN: setting one, changing it, deleting it, and being asked for it before the bank
 * opens.
 *
 * Cache contract (`./gradlew :game:runInterfaceHookProbeTool --args="../data/cache layout 13"`):
 * interface 13 is the PIN keypad. Components 6-15 are the ten digit buttons, each a `pauseButton`
 * (`events=0x000001`) baked into the cache, so a click arrives as [ResumePauseButtonMessage] with no
 * `IfSetEvents` needed. Component 25 is `Exit` (`op1=IF_BUTTON1`, also baked), component 26 carries
 * "Please enter your FOUR DIGIT PIN using the buttons below." and component 27 the deletion notice.
 *
 * The entered PIN is recorded as the sequence of *button components* rather than as digits. The
 * client sends no digit value - only which button was pressed - and the keypad's digit sprites are
 * static in the cache, so a button always shows the same digit and the two forms are equivalent. The
 * one thing this cannot do is print the PIN back to the player, which nothing should ever do anyway.
 *
 * The PIN is stored as a salted SHA-256 of that sequence, so a player save never carries a PIN that
 * could be read out of it and tried on another account.
 *
 * NOT IMPLEMENTED, and deliberately so: interface 14 ("Bank PIN Settings") and the recovery delay it
 * offers. Its three action buttons are labelled by clientscript 4146 from varc 98, and which label
 * lands on which button in which state is not established from the cache; binding them on a guess
 * would be worse than the dialogue used here, which cannot mislabel itself. Recovery delay - the
 * "your PIN will be deleted in N days" flow behind varp 563 - needs a real timer and an owner
 * decision about its length, and is recorded as pending rather than approximated.
 */
object BankPin {
    const val PIN_INTERFACE_ID = 13

    /** The PIN itself, salted and hashed; both halves persist with the player. */
    val PIN_HASH = AttributeKey<String>(persistenceKey = "bank_pin_hash")
    val PIN_SALT = AttributeKey<String>(persistenceKey = "bank_pin_salt")

    /**
     * Set once the PIN has been given correctly, and deliberately not persisted: it lasts for the
     * login session, which is what makes the PIN worth having.
     */
    private val VERIFIED = AttributeKey<Boolean>()

    private val DIGIT_COMPONENTS = 6..15
    private const val EXIT_COMPONENT = 25
    private const val PROMPT_COMPONENT = 26
    private const val NOTICE_COMPONENT = 27

    /** varc 98 is the PIN state clientscript 4146 switches on: 0 = no PIN, 3 = a PIN is set. */
    private const val STATE_VARC = 98
    private const val STATE_NO_PIN = 0
    private const val STATE_HAS_PIN = 3

    private const val PIN_LENGTH = 4
    private const val MAX_ATTEMPTS = 3

    fun isSet(player: Player): Boolean = !player.attr[PIN_HASH].isNullOrEmpty()

    /** True when the bank must not open until the player has given their PIN. */
    fun required(player: Player): Boolean = isSet(player) && player.attr[VERIFIED] != true

    /**
     * Asks for the PIN and runs [onVerified] once it is given correctly.
     *
     * Getting it wrong [MAX_ATTEMPTS] times, or closing the keypad, simply leaves the bank shut -
     * there is nothing to punish here, since the only thing behind the PIN is the player's own bank.
     */
    fun request(
        player: Player,
        onVerified: (Player) -> Unit,
    ) {
        player.queue(TaskPriority.STRONG) {
            var attempts = 0
            while (attempts < MAX_ATTEMPTS) {
                val entered = enterPin(player, "Please enter your bank PIN.") ?: return@queue
                if (matches(player, entered)) {
                    player.attr[VERIFIED] = true
                    onVerified(player)
                    return@queue
                }
                attempts++
                if (attempts < MAX_ATTEMPTS) {
                    player.message("That PIN is incorrect. You have ${MAX_ATTEMPTS - attempts} attempts left.")
                }
            }
            player.message("Too many incorrect PINs. Try again later.")
        }
    }

    /** The `Set a Bank PIN` button on the bank interface: set, change or delete the PIN. */
    fun manage(player: Player) {
        player.queue(TaskPriority.STRONG) {
            managePin(player)
            /*
             * Both the dialogue and the keypad take the main screen away from the bank, and this is
             * only ever reached from the bank's own button, so put the bank back afterwards.
             */
            Bank.open(player)
        }
    }

    private suspend fun QueueTask.managePin(player: Player) {
        if (!isSet(player)) {
            if (options("Set a bank PIN", "Cancel", title = "Bank PIN") != 1) {
                return
            }
            setNewPin(player)
            return
        }

        when (options("Change your PIN", "Delete your PIN", "Cancel", title = "Bank PIN")) {
            1 -> {
                if (confirmCurrentPin(player)) {
                    setNewPin(player)
                }
            }
            2 -> {
                if (confirmCurrentPin(player)) {
                    clear(player)
                    refreshState(player)
                    player.message("Your bank PIN has been deleted.")
                }
            }
        }
    }

    /** Forgets the PIN entirely - used when it is deleted, and by the tests. */
    fun clear(player: Player) {
        player.attr.remove(PIN_HASH)
        player.attr.remove(PIN_SALT)
        player.attr[VERIFIED] = true
    }

    /**
     * Stores [digits] as this player's PIN, under a fresh salt.
     *
     * The player counts as verified immediately afterwards: they have just proved they know it
     * twice over, and asking again in the same breath would only be theatre.
     */
    fun store(
        player: Player,
        digits: List<Int>,
    ) {
        val salt = newSalt()
        player.attr[PIN_SALT] = salt
        player.attr[PIN_HASH] = hash(digits, salt)
        player.attr[VERIFIED] = true
    }

    /** Tells the client whether a PIN exists, which is all varc 98 carries. */
    private fun refreshState(player: Player) {
        player.setVarc(STATE_VARC, if (isSet(player)) STATE_HAS_PIN else STATE_NO_PIN)
    }

    fun matches(
        player: Player,
        digits: List<Int>,
    ): Boolean {
        val stored = player.attr[PIN_HASH] ?: return false
        val salt = player.attr[PIN_SALT] ?: return false
        return constantTimeEquals(stored, hash(digits, salt))
    }

    /** The salted hash a PIN is stored as. Kept internal so the tests can pin the format down. */
    internal fun hash(
        digits: List<Int>,
        salt: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:${digits.joinToString(",")}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun QueueTask.setNewPin(player: Player) {
        val first = enterPin(player, "Choose a bank PIN.") ?: return
        val second = enterPin(player, "Enter the same PIN again to confirm it.") ?: return
        if (first != second) {
            player.message("Those PINs don't match. Your PIN has not been changed.")
            return
        }
        store(player, first)
        refreshState(player)
        player.message("Your bank PIN has been set. Don't tell it to anyone.")
    }

    private suspend fun QueueTask.confirmCurrentPin(player: Player): Boolean {
        val entered = enterPin(player, "Please enter your current bank PIN.") ?: return false
        if (!matches(player, entered)) {
            player.message("That PIN is incorrect.")
            return false
        }
        return true
    }

    /**
     * Opens the keypad and collects [PIN_LENGTH] presses, or null when the player exits, closes the
     * keypad or is interrupted.
     */
    private suspend fun QueueTask.enterPin(
        player: Player,
        prompt: String,
    ): List<Int>? {
        player.openInterface(interfaceId = PIN_INTERFACE_ID, dest = InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(PIN_INTERFACE_ID, PROMPT_COMPONENT, prompt)
        player.setComponentText(PIN_INTERFACE_ID, NOTICE_COMPONENT, "")
        player.setVarc(STATE_VARC, if (isSet(player)) STATE_HAS_PIN else STATE_NO_PIN)

        terminateAction = closeKeypad
        val digits = mutableListOf<Int>()
        while (digits.size < PIN_LENGTH) {
            waitReturnValue()
            val message = requestReturnValue as? ResumePauseButtonMessage
            if (message == null || message.interfaceId != PIN_INTERFACE_ID) {
                terminateAction!!(this)
                return null
            }
            when (message.component) {
                EXIT_COMPONENT -> {
                    terminateAction!!(this)
                    return null
                }
                in DIGIT_COMPONENTS -> digits += message.component
                /* Anything else on the keypad is decoration; ignore it and keep waiting. */
                else -> {}
            }
        }
        terminateAction!!(this)
        return digits
    }

    private val closeKeypad: QueueTask.() -> Unit = {
        player.closeInterface(PIN_INTERFACE_ID)
    }

    private fun newSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Compares the whole of both strings, so a wrong PIN cannot be found a character at a time. */
    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) {
            return false
        }
        var difference = 0
        for (i in a.indices) {
            difference = difference or (a[i].code xor b[i].code)
        }
        return difference == 0
    }
}
