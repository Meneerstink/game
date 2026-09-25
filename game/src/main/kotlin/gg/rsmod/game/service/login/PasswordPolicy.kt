package gg.rsmod.game.service.login

/**
 * Audit S-04/S-06/S-08: the one password rule for new accounts, `::changepass` and the recovery
 * page: 5 to 20 letters and digits, and not the account name. Existing passwords that predate the
 * rule keep working; it is only applied when a password is chosen.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 5
    const val MAX_LENGTH = 20
    const val RULE_MESSAGE = "Passwords are 5 to 20 letters and numbers."
    const val SAME_AS_NAME_MESSAGE = "Your password can't be your username."

    private val ALLOWED = Regex("^[A-Za-z0-9]{$MIN_LENGTH,$MAX_LENGTH}$")

    /** Why [password] may not be chosen for [username], or null when it may. */
    fun problem(
        username: String,
        password: String,
    ): String? {
        if (!ALLOWED.matches(password)) {
            return RULE_MESSAGE
        }
        val name = username.trim()
        if (password.equals(name, ignoreCase = true) || password.equals(name.replace(" ", ""), ignoreCase = true)) {
            return SAME_AS_NAME_MESSAGE
        }
        return null
    }

    fun isAcceptable(
        username: String,
        password: String,
    ): Boolean = problem(username, password) == null
}
