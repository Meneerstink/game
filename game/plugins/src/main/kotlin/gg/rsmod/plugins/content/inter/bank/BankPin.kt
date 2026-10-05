package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.messageBox
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarc
import gg.rsmod.plugins.api.ext.setVarcString
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
 * Bank PIN Settings (owner 2026-10-04: "PIN settings have no interface, only the chatbox"; "a wrong PIN plays no sound"): the
 * mechanism is Void `BankPin` / `BankPinSettings` (set takes effect at once, a change waits the recovery delay of 3 or 7 days,
 * escalating lockout after wrong entries, sounds 1040 pending / 1042 cancel / 2274 success / 2277 wrong), drawn on the 667
 * interface 14 as Novite `BankPinManager` drives it. 667 contract (clientscript 4146 = 14:1 onLoad / onVarcTransmit 98 /
 * onVarcstrTransmit 344): varc 98 & 3 = 0 "No PIN set" (14:18 Set a PIN, 14:19 Change recovery delay), 1 "You have a PIN"
 * (18 Change your PIN, 19 Delete your PIN, 20 Change recovery delay), 3 "PIN coming soon" (18 Cancel the PIN); bit 10 = 7 days
 * instead of 3; bit 31 = the confirm view (14:31, question 14:32, 33 yes / 34 its text, 35 no / 36 its text); varcstr 344 = the
 * message. 14:18-20 / 33 / 35 are pause buttons, 14:11 is Close (IF_BUTTON1). The 667 keypad (interface 13) has no "I don't know
 * it" button, so Void's forgotten-PIN deletion has no entry point here.
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

    const val SETTINGS_INTERFACE_ID = 14
    const val SETTINGS_CLOSE = 11
    private const val SETTINGS_MESSAGE_VARCSTR = 344
    private const val CONFIRM_VIEW = -1
    private const val STATE_PENDING = 3
    private const val LONG_RECOVERY_BIT = 1 shl 10

    const val SOUND_PENDING = 1040
    const val SOUND_CANCEL = 1042
    const val SOUND_SUCCESS = 2274
    const val SOUND_WRONG = 2277

    /** A changed PIN waiting for the recovery delay (Void `bank_pin_pending`), salted like [PIN_HASH]. */
    val PENDING_HASH = AttributeKey<String>(persistenceKey = "bank_pin_pending_hash")
    val PENDING_SALT = AttributeKey<String>(persistenceKey = "bank_pin_pending_salt")

    /** A PIN deletion waiting for the recovery delay (Void `bank_pin_delete`). */
    val DELETE_PENDING = AttributeKey<Boolean>(persistenceKey = "bank_pin_delete")

    /** Epoch second the pending change or deletion takes effect (Void clock `bank_pin_change`). */
    val CHANGE_AT = AttributeKey<Int>(persistenceKey = "bank_pin_change_at")

    /** 7 days recovery instead of 3 (Void `bank_pin_long_recovery`). */
    val LONG_RECOVERY = AttributeKey<Boolean>(persistenceKey = "bank_pin_long_recovery")
    val TRIES = AttributeKey<Int>(persistenceKey = "bank_pin_tries")
    val LOCKOUT_UNTIL = AttributeKey<Int>(persistenceKey = "bank_pin_lockout_until")

    /** What the settings' Yes button confirms: "set" or "delete" (Void `bank_pin_confirm`). */
    private val CONFIRM = AttributeKey<String>()

    private fun now(): Int = (System.currentTimeMillis() / 1000).toInt()

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

    /**
     * The fourth digit: the 667 client sends it as a pause button on [PIN_INTERFACE_ID] component digit + 6 (13:6..15), not as
     * a 759 click (Novite `finishPin(componentId - 6)`; seen in the agent's client 2026-10-04: three 759 clicks, then
     * RESUME_PAUSEBUTTON 13:10 for the digit 4). True when it was a keypad digit.
     */
    fun pressPauseDigit(
        player: Player,
        interfaceId: Int,
        component: Int,
    ): Boolean {
        if (interfaceId != PIN_INTERFACE_ID || component !in 6..15) return false
        val input = player.attr[INPUT] ?: return false
        input.press(component - 6)
        return true
    }

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
     * Void `verifyBankAccess`: asks for the PIN once and runs [onVerified] when it is right. A wrong PIN plays the wrong sound and
     * counts towards the escalating lockout ([lockoutSeconds]); the bank simply stays shut.
     */
    fun request(
        player: Player,
        onVerified: (Player) -> Unit,
    ) {
        checkPending(player)
        if (!required(player)) {
            onVerified(player)
            return
        }
        player.queue(TaskPriority.STRONG) {
            if (isLockedOut(player)) {
                lockoutStatement(player)
                return@queue
            }
            val entered = enterPin(player, "Bank PIN", "Please enter your PIN using the buttons below.") ?: return@queue
            if (!matches(player, entered)) {
                wrongPin(player)
                return@queue
            }
            player.attr[VERIFIED] = true
            player.attr[TRIES] = 0
            player.playSound(SOUND_SUCCESS)
            player.message("You have correctly entered your PIN.")
            onVerified(player)
        }
    }

    /** Void `wrongPin`: sound, message, and from the second wrong entry a lockout. */
    private suspend fun QueueTask.wrongPin(player: Player) {
        player.playSound(SOUND_WRONG)
        player.message("The PIN you entered is incorrect.")
        val tries = (player.attr[TRIES] ?: 0) + 1
        player.attr[TRIES] = tries
        if (tries >= 2) {
            player.attr[LOCKOUT_UNTIL] = now() + lockoutSeconds(tries)
            lockoutStatement(player)
        }
    }

    /** Void `BankPin.lockoutSeconds`: escalating retry cooldown. */
    fun lockoutSeconds(tries: Int): Int =
        when {
            tries <= 2 -> 10
            tries == 3 -> 15
            else -> 1000
        }

    fun isLockedOut(player: Player): Boolean = (player.attr[LOCKOUT_UNTIL] ?: 0) > now()

    private suspend fun QueueTask.lockoutStatement(player: Player) {
        val seconds = (player.attr[LOCKOUT_UNTIL] ?: 0) - now()
        if (seconds <= 60) {
            messageBox("You will be able to access your bank in less than 1 minute.")
        } else {
            messageBox("You will be able to access your bank pin in ${(seconds + 59) / 60} minutes.")
        }
    }

    fun recoveryDays(player: Player): Int = if (player.attr[LONG_RECOVERY] == true) 7 else 3

    fun isChangePending(player: Player): Boolean = !player.attr[PENDING_HASH].isNullOrEmpty()

    fun isDeletePending(player: Player): Boolean = player.attr[DELETE_PENDING] == true

    fun pendingDays(player: Player): Int {
        val remaining = (player.attr[CHANGE_AT] ?: 0) - now()
        return if (remaining <= 0) 0 else (remaining + 86399) / 86400
    }

    /** Void `checkPending`: a pending change or deletion takes effect once its recovery delay has passed. */
    fun checkPending(player: Player) {
        if ((isChangePending(player) || isDeletePending(player)) && (player.attr[CHANGE_AT] ?: 0) <= now()) {
            if (isDeletePending(player)) {
                player.attr.remove(PIN_HASH)
                player.attr.remove(PIN_SALT)
            } else {
                player.attr[PIN_HASH] = player.attr[PENDING_HASH]!!
                player.attr[PIN_SALT] = player.attr[PENDING_SALT]!!
                player.attr[VERIFIED] = false
            }
            clearPending(player)
        }
    }

    fun clearPending(player: Player) {
        player.attr.remove(PENDING_HASH)
        player.attr.remove(PENDING_SALT)
        player.attr.remove(DELETE_PENDING)
        player.attr.remove(CHANGE_AT)
    }

    /** Void `setPending`: a changed PIN waits the recovery delay; the old PIN stays active until then. */
    fun setPending(
        player: Player,
        digits: List<Int>,
    ) {
        val salt = newSalt()
        player.attr[PENDING_SALT] = salt
        player.attr[PENDING_HASH] = hash(digits, salt)
        player.attr[CHANGE_AT] = now() + recoveryDays(player) * 86400
    }

    /** Void `isEasyGuess`: all the same digit, or a run of adjacent digits. */
    fun isEasyGuess(digits: List<Int>): Boolean =
        digits.all { it == digits[0] } || (0 until digits.size - 1).all { kotlin.math.abs(digits[it + 1] - digits[it]) == 1 }

    /** The `Set a Bank PIN` button on the bank (and the bankers' PIN option): the Bank PIN Settings screen. */
    fun manage(player: Player) {
        openSettings(player)
    }

    /** Void `BankPinSettings` opened: pending changes applied, the default message, the pending sound. */
    fun openSettings(
        player: Player,
        message: String? = null,
    ) {
        checkPending(player)
        refreshSettings(player, message ?: defaultMessage(player))
        player.playSound(SOUND_PENDING)
    }

    private fun refreshSettings(
        player: Player,
        message: String,
    ) {
        player.attr.remove(CONFIRM)
        mountSettings(player)
        player.setVarcString(SETTINGS_MESSAGE_VARCSTR, message)
        player.setVarc(STATE_VARC, settingsState(player))
    }

    /**
     * (Re)opens interface 14. The 667 client takes no further pause button after one was clicked until the server opens or closes a
     * sub-interface (MiniMenu PAUSE_BUTTON sets InterfaceManager.dialog; only IF_OPENSUB / IF_CLOSESUB clear it), so every
     * settings click answers with the screen again - Novite `openSettingsScreen` / `showConfirmSetPin` do the same.
     */
    private fun mountSettings(player: Player) {
        player.openInterface(interfaceId = SETTINGS_INTERFACE_ID, dest = InterfaceDestination.MAIN_SCREEN)
    }

    /** varc 98 for interface 14 (clientscript 4146). */
    fun settingsState(player: Player): Int {
        val base =
            when {
                isChangePending(player) || isDeletePending(player) -> STATE_PENDING
                isSet(player) -> 1
                else -> STATE_NO_PIN
            }
        return base or if (player.attr[LONG_RECOVERY] == true) LONG_RECOVERY_BIT else 0
    }

    private fun defaultMessage(player: Player): String =
        when {
            isDeletePending(player) ->
                "You have requested that your PIN be deleted. This will take effect in another ${daysText(pendingDays(player))}.<br><br>" +
                    "Your PIN stays active until then. To cancel, use the button on the left."
            isChangePending(player) ->
                "You have requested that your PIN be changed. This will take effect in another ${daysText(pendingDays(player))}.<br><br>" +
                    "Your old PIN stays active until then. To cancel, use the button on the left."
            else ->
                "Customers are reminded that they should NEVER tell anyone their Bank PINs or passwords, nor should they ever enter " +
                    "their PINs on any website form.<br><br>Have you read the PIN guide on the website?"
        }

    private fun daysText(days: Int) = "$days day${if (days == 1) "" else "s"}"

    /** Void `BankPinSettings.confirm`: the yes / no view. */
    private fun confirm(
        player: Player,
        action: String,
        question: String,
        yes: String,
        no: String,
    ) {
        mountSettings(player)
        player.setComponentText(SETTINGS_INTERFACE_ID, 32, question)
        player.setComponentText(SETTINGS_INTERFACE_ID, 34, yes)
        player.setComponentText(SETTINGS_INTERFACE_ID, 36, no)
        player.setVarc(STATE_VARC, CONFIRM_VIEW)
        player.attr[CONFIRM] = action
        player.playSound(SOUND_PENDING)
    }

    /** A pause button on interface 14 (bank.plugin.kts). True when it was one of the settings buttons. */
    fun pressSettingsButton(
        player: Player,
        interfaceId: Int,
        component: Int,
    ): Boolean {
        if (interfaceId != SETTINGS_INTERFACE_ID || !player.isInterfaceVisible(SETTINGS_INTERFACE_ID)) return false
        val pending = isChangePending(player) || isDeletePending(player)
        when (component) {
            18 ->
                when {
                    pending -> {
                        val deletion = isDeletePending(player)
                        clearPending(player)
                        player.playSound(SOUND_CANCEL)
                        refreshSettings(
                            player,
                            if (deletion) {
                                "The PIN deletion has been cancelled.<br><br>Your PIN remains active."
                            } else {
                                "The PIN change has been cancelled.<br><br>Your old PIN remains active."
                            },
                        )
                    }
                    isSet(player) -> player.queue(TaskPriority.STRONG) { changePin(player) }
                    else ->
                        confirm(
                            player, "set", "Do you really wish to set a PIN on your bank account?",
                            "Yes, I really want a Bank PIN. I will never forget it!", "No, I might forget it!",
                        )
                }
            19 ->
                if (isSet(player) && !pending) {
                    confirm(
                        player, "delete", "Do you really wish to delete your Bank PIN?",
                        "Yes, I don't need a PIN anymore.", "No thanks, I'd rather keep the extra security.",
                    )
                } else if (!isSet(player)) {
                    toggleRecovery(player)
                }
            20 -> if (isSet(player) && !pending) toggleRecovery(player)
            33 ->
                when (player.attr[CONFIRM]) {
                    "set" -> player.queue(TaskPriority.STRONG) { setPinFromSettings(player) }
                    "delete" -> player.queue(TaskPriority.STRONG) { deletePinFromSettings(player) }
                }
            35 -> refreshSettings(player, "No changes made.")
            else -> return false
        }
        return true
    }

    private fun toggleRecovery(player: Player) {
        player.attr[LONG_RECOVERY] = player.attr[LONG_RECOVERY] != true
        refreshSettings(
            player,
            "Your recovery delay has now been set to ${recoveryDays(player)} days.<br><br>You would have to wait this long to delete " +
                "your PIN if you forgot it. But you ${if (isSet(player)) "do have one." else "haven't got one..."}",
        )
    }

    /** Void `choosePin`: enter twice, refuse an easy guess or a mismatch (settings reopened with the reason). */
    private suspend fun QueueTask.choosePin(player: Player): List<Int>? {
        val first = enterPin(player, "Set new PIN", "Please choose a new FOUR DIGIT PIN using the buttons below.") ?: return reopen(player)
        val second = enterPin(player, "Confirm new PIN", "Now please enter that number again!") ?: return reopen(player)
        if (isEasyGuess(first)) {
            openSettings(player, "That number wouldn't be very hard to guess. Please try something different!")
            return null
        }
        if (first != second) {
            openSettings(player, "Those numbers did not match.<br><br>Your PIN has not been set; please try again if you wish to set a new PIN.")
            return null
        }
        return first
    }

    private fun reopen(player: Player): List<Int>? {
        openSettings(player)
        return null
    }

    private suspend fun QueueTask.setPinFromSettings(player: Player) {
        val pin = choosePin(player) ?: return
        store(player, pin)
        refreshState(player)
        openSettings(player, "A PIN has been set on your bank account.<br><br>You will be asked for it next time you visit the bank.")
    }

    private suspend fun QueueTask.changePin(player: Player) {
        if (!confirmCurrentPin(player)) return
        val pin = choosePin(player) ?: return
        if (matches(player, pin)) {
            player.message("Your current PIN matches your new one.")
            openSettings(player)
            return
        }
        setPending(player, pin)
        player.message("Your PIN change will take effect in ${recoveryDays(player)} days.")
        openSettings(player)
    }

    private suspend fun QueueTask.deletePinFromSettings(player: Player) {
        if (player.attr[VERIFIED] != true && !confirmCurrentPin(player)) return
        clear(player)
        clearPending(player)
        refreshState(player)
        player.playSound(SOUND_CANCEL)
        openSettings(player, "Your Bank PIN has now been deleted.<br><br>This means that there is no PIN protection on your bank account.")
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

    /** The current PIN before a change or a deletion (Void: wrong -> [wrongPin], locked out -> the statement). */
    private suspend fun QueueTask.confirmCurrentPin(player: Player): Boolean {
        if (isLockedOut(player)) {
            player.closeInterface(SETTINGS_INTERFACE_ID)
            lockoutStatement(player)
            return false
        }
        val entered = enterPin(player, "Bank PIN", "Please enter your PIN using the buttons below.") ?: run {
            openSettings(player)
            return false
        }
        if (!matches(player, entered)) {
            wrongPin(player)
            return false
        }
        player.attr[VERIFIED] = true
        player.attr[TRIES] = 0
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
        // Owner 2026-09-30 ("Finally, the FOURTH digit" and setup does not complete): varps normally go out with the end-of-cycle
        // varp flush, AFTER the script below has already drawn the keypad. The first keypad starts at stage 0 anyway, but the
        // second one ("Now please enter that number again.") opens right after the fourth click left the client's stage at 3
        // (script 1110 caps it there), so it was drawn as "Finally, the FOURTH digit." with four '*' markers and every click
        // after that showed the wrong digit number. The reset stage and keypad varp are sent now, before script 1271 reads them.
        sendVarpNow(player, KEYPAD_VARP)
        sendVarpNow(player, player.world.definitions.get(gg.rsmod.game.fs.def.VarbitDef::class.java, STAGE_VARBIT).varp)
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

    /** Writes varp [id]'s current server value to the client immediately (ahead of the scripts written after it). */
    private fun sendVarpNow(
        player: Player,
        id: Int,
    ) {
        val state = player.varps.getState(id)
        player.write(
            if (state in -Byte.MAX_VALUE..Byte.MAX_VALUE) {
                gg.rsmod.game.message.impl.VarpSmallMessage(id, state)
            } else {
                gg.rsmod.game.message.impl.VarpLargeMessage(id, state)
            },
        )
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
