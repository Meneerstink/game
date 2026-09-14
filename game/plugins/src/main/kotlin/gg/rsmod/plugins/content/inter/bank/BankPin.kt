package gg.rsmod.plugins.content.inter.bank

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
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarc
import gg.rsmod.plugins.api.ext.setVarp
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The bank PIN: setting one, changing it, deleting it, and being asked for it before the bank
 * opens.
 *
 * Cache contract (RCV-012.B17, `InterfaceHookProbeTool data\cache layout|interface 13|759`, `script 1271|1110|696`):
 * - Interface 13 is the keypad frame. Components 6-15 are empty 64x64 layers under the layer 13:5 (no sprite, text or op); the
 *   visible digit buttons are interface 759, mounted into 13:5 - Novite `BankPinManager.showEnterPin`:
 *   `sendInterface(true, 13, 5, 759)`. The old keypad opened 13 alone and waited for pause buttons on those empty layers, so the
 *   player saw no digits and nothing could be entered (owner live: "bank PINs do not work").
 * - Interface 759 has ten digit cells; the clickable child of cell k (k = 0..9) is component 4 * (k + 1) (`Select digit`,
 *   op1 = IF_BUTTON1), so the digit is `component / 4 - 1` (Novite `BankPinListener`: `buttonId / 4 - 1`). Each click runs client
 *   script 1110, which advances varbit 1010 (varp 563 bits 0-2, the digit stage, capped at 3), plays sound 1041 and re-runs script
 *   1271; script 1271 draws "First click the FIRST digit." .. "Finally, the FOURTH digit." / "Please wait...", the four entry
 *   markers on 13:1-4 ("?" before, "*" after each digit), and moves the ten cells to shuffled positions - the digit on a cell
 *   stays the same, only its place changes.
 * - 13:25 is `Exit` (op1 = IF_BUTTON1, client script 696 prints "Cancelled." and plays sound 1042); 13:26 carries the prompt and
 *   13:27 the title line.
 * - Opening the keypad resets the client state as Novite does: varp 163 = 0, varc 98 = 0, varc 199 = -1, 13:24 events 0,
 *   varbit 1010 = 0, then script 1271 with argument 1.
 *
 * The PIN is stored as a salted SHA-256 of its four digits, so a player save never carries a PIN that could be read out of it and
 * tried on another account. (Before RCV-012.B17 the hash covered keypad component ids; no PIN could ever be entered through that
 * keypad, so no stored PIN uses the old form.)
 *
 * NOT IMPLEMENTED, and deliberately so: interface 14 ("Bank PIN Settings") and the recovery delay it offers - the dialogue used here
 * cannot mislabel itself, and the delay needs an owner decision about its length.
 */
object BankPin {
    const val PIN_INTERFACE_ID = 13

    /** Interface 759: the ten digit cells, mounted into [PIN_INTERFACE_ID] component [KEYPAD_SLOT]. */
    const val DIGITS_INTERFACE_ID = 759
    const val KEYPAD_SLOT = 5
    const val EXIT_COMPONENT = 25

    /** The clickable child of each 759 digit cell: 4, 8, .., 40. */
    val DIGIT_COMPONENTS = (1..10).map { it * 4 }

    /** The PIN itself, salted and hashed; both halves persist with the player. */
    val PIN_HASH = AttributeKey<String>(persistenceKey = "bank_pin_hash")
    val PIN_SALT = AttributeKey<String>(persistenceKey = "bank_pin_salt")

    /**
     * Set once the PIN has been given correctly, and deliberately not persisted: it lasts for the
     * login session, which is what makes the PIN worth having.
     */
    private val VERIFIED = AttributeKey<Boolean>()

    /** Presses waiting to be read by the keypad task; kept per player and never persisted. */
    private val INPUT = AttributeKey<KeypadInput>()

    private const val PROMPT_COMPONENT = 26
    private const val TITLE_COMPONENT = 27
    private const val EVENTS_COMPONENT = 24

    /** varc 98 is the PIN state clientscript 4146 switches on: 0 = no PIN, 3 = a PIN is set. */
    private const val STATE_VARC = 98
    private const val STATE_NO_PIN = 0
    private const val STATE_HAS_PIN = 3

    private const val STAGE_VARBIT = 1010
    private const val KEYPAD_VARP = 163
    private const val KEYPAD_VARC = 199
    private const val KEYPAD_SCRIPT = 1271

    private const val PIN_LENGTH = 4
    private const val MAX_ATTEMPTS = 3

    /**
     * Keypad presses in click order. The button handlers only record presses here and the keypad task waits until one is
     * available (`QueueTask.wait { .. }`), so several clicks in one game cycle are all kept and no queue hand-off is needed.
     */
    class KeypadInput {
        private val digits = ArrayDeque<Int>()
        var exited = false
            private set

        val hasDigit: Boolean get() = digits.isNotEmpty()

        fun press(digit: Int) {
            digits.addLast(digit)
        }

        fun exit() {
            exited = true
        }

        fun takeDigit(): Int? = digits.removeFirstOrNull()
    }

    /** The digit behind interface 759 component [component], or null for anything that is not a digit button. */
    fun digitForComponent(component: Int): Int? = if (component in DIGIT_COMPONENTS) component / 4 - 1 else null

    /** A 759 digit click (bank.plugin.kts). */
    fun pressDigit(
        player: Player,
        component: Int,
    ) {
        val digit = digitForComponent(component) ?: return
        player.attr[INPUT]?.press(digit)
    }

    /** The keypad's Exit button (bank.plugin.kts). */
    fun pressExit(player: Player) {
        player.attr[INPUT]?.exit()
    }

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
                val entered = enterPin(player, "Bank PIN", "Please enter your bank PIN.") ?: return@queue
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
        val first = enterPin(player, "Set new PIN", "Please choose a new FOUR DIGIT PIN using the buttons below.") ?: return
        val second = enterPin(player, "Confirm new PIN", "Now please enter that number again.") ?: return
        if (first != second) {
            player.message("Those PINs don't match. Your PIN has not been changed.")
            return
        }
        store(player, first)
        refreshState(player)
        player.message("Your bank PIN has been set. Don't tell it to anyone.")
    }

    private suspend fun QueueTask.confirmCurrentPin(player: Player): Boolean {
        val entered = enterPin(player, "Bank PIN", "Please enter your current bank PIN.") ?: return false
        if (!matches(player, entered)) {
            player.message("That PIN is incorrect.")
            return false
        }
        return true
    }

    /**
     * Opens the keypad and collects [PIN_LENGTH] digits, or null when the player exits or the task is
     * interrupted (which closes the keypad through [terminateAction]).
     */
    private suspend fun QueueTask.enterPin(
        player: Player,
        title: String,
        prompt: String,
    ): List<Int>? {
        val input = KeypadInput()
        player.attr[INPUT] = input
        player.openInterface(interfaceId = PIN_INTERFACE_ID, dest = InterfaceDestination.MAIN_SCREEN)
        player.openInterface(parent = PIN_INTERFACE_ID, child = KEYPAD_SLOT, interfaceId = DIGITS_INTERFACE_ID, type = 1)
        player.setVarp(KEYPAD_VARP, 0)
        player.setVarc(STATE_VARC, 0)
        player.setVarc(KEYPAD_VARC, -1)
        player.setInterfaceEvents(interfaceId = PIN_INTERFACE_ID, component = EVENTS_COMPONENT, range = -1..-1, setting = 0)
        player.setComponentText(PIN_INTERFACE_ID, TITLE_COMPONENT, title)
        player.setComponentText(PIN_INTERFACE_ID, PROMPT_COMPONENT, prompt)
        player.setVarbit(STAGE_VARBIT, 0)
        player.runClientScript(KEYPAD_SCRIPT, 1)

        terminateAction = closeKeypad
        val digits = mutableListOf<Int>()
        while (digits.size < PIN_LENGTH) {
            wait { input.hasDigit || input.exited }
            if (input.exited) {
                terminateAction!!(this)
                return null
            }
            while (digits.size < PIN_LENGTH) {
                digits += input.takeDigit() ?: break
            }
        }
        terminateAction!!(this)
        return digits
    }

    private val closeKeypad: QueueTask.() -> Unit = {
        player.attr.remove(INPUT)
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
