package gg.rsmod.game.service.serializer

/**
 * Possible results when trying to decode player data.
 *
 * @author Tom <rspsmods@gmail.com>
 */
enum class PlayerLoadResult {
    /**
     * The account has never logged into the game before.
     */
    NEW_ACCOUNT,

    /**
     * The account has previously been made.
     */
    LOAD_ACCOUNT,

    /**
     * The credentials provided at login are incorrect.
     */
    INVALID_CREDENTIALS,

    /**
     * The log-in xteas did not match the previous session.
     */
    INVALID_RECONNECTION,

    /**
     * There was an error decoding the data.
     */
    MALFORMED,

    /**
     * Audit S-06: a new account was refused because its password does not meet
     * [gg.rsmod.game.service.login.PasswordPolicy]. No save is created.
     */
    INVALID_NEW_PASSWORD,

    /**
     * Audit S-06: a new account was refused because its address created too many accounts
     * recently. No save is created.
     */
    REGISTRATION_LIMIT,
}
