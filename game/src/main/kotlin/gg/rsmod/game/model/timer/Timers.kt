package gg.rsmod.game.model.timer

/**
 * A decoupled file that holds TimerKeys that require read-access from our
 * game module. Any timer keys that can be stored on the plugin classes themselves,
 * should do so. When storing them in a class, remember the TimerKey must be
 * a singleton, meaning it should only have a single state.
 *
 * @author Tom <rspsmods@gmail.com>
 */

/**
 * A timer for npcs to reset their pawn face attribute.
 */
internal val RESET_PAWN_FACING_TIMER = TimerKey()

/**
 * A timer for removing a skull icon. Has a persistence key so the remaining
 * duration survives logout/reconnect and is cleared on death, since a death
 * already resolves the skull's item-risk consequence.
 *
 * Audit D-04: `tickOffline = false` - the skull only counts down while the player is online
 * (OSRS behaviour), so logging out for five minutes no longer sheds it. Saves written before
 * this change still carry the old `tickOffline = true` key; `pvp_skull.plugin.kts` migrates
 * that legacy key onto this one at login ([LEGACY_SKULL_ICON_DURATION_TIMER]).
 */
val SKULL_ICON_DURATION_TIMER =
    TimerKey(persistenceKey = "skull_icon_duration", tickOffline = false, resetOnDeath = true, removeOnZero = true)

/**
 * Audit D-04: the pre-fix shape of [SKULL_ICON_DURATION_TIMER] (`tickOffline = true`). A persisted
 * timer is rebuilt from its saved flags, so a save written before the fix deserialises into this key,
 * not the live one. Only read (and removed) by the login migration in `pvp_skull.plugin.kts`.
 */
val LEGACY_SKULL_ICON_DURATION_TIMER =
    TimerKey(persistenceKey = "skull_icon_duration", tickOffline = true, resetOnDeath = true, removeOnZero = true)

/**
 * Window during which a player who was just attacked can attack back without
 * being treated as the unprovoked initiator for PK-skull purposes. Refreshed
 * on every hit received, so it stays open for the duration of an active
 * fight. Session-local only - does not need to survive reconnect.
 */
val PVP_AGGRESSOR_WINDOW_TIMER = TimerKey(tickOffline = false, resetOnDeath = true)

/**
 * R14.23/R14.24: remaining beginner-PvP-protection budget, in game cycles. Granted once (60
 * minutes = 6000 cycles) at true first login. `tickOffline = false` is the exact mechanism
 * R14.24/R14.27 ask for: it only counts down while the player is actually online/active, and
 * is untouched while offline - the same as [SKULL_ICON_DURATION_TIMER] (Audit D-04: the skull
 * no longer counts down offline either). `removeOnZero = true` means the timer disappears entirely once
 * exhausted, so "has this timer" doubles as "is currently protected" with no separate expiry
 * flag needed. Not reset on death (R14.24: "do not grant anew on ... death").
 */
val NEW_PLAYER_PROTECTION_TIMER =
    TimerKey(persistenceKey = "new_player_protection", tickOffline = false, resetOnDeath = false, removeOnZero = true)

/**
 * Timer key set when a pawn is attacked either in PvP or in PvM.
 */
val ACTIVE_COMBAT_TIMER = TimerKey()

/**
 * Deadman PvP guards plan (owner-approved 2026-09-16): "instant teleport if not hit by a player
 * or NPC in the last 7 seconds" for an unskulled player. Deliberately separate from
 * [ACTIVE_COMBAT_TIMER] (10.2s, used for the unrelated combat-escape/logout lock and other
 * existing consumers) so retuning this gate's exact duration can never change those other
 * features' behaviour. Set on the target whenever a hit lands, same event as
 * [ACTIVE_COMBAT_TIMER].
 */
val TELEPORT_COMBAT_TIMER = TimerKey()

/**
 * Session-local Deadman logout hold. Audit X-10: combat arms it for every hit a player receives
 * (bosses, poison and venom included - at least 16 ticks after the latest hit), and the network
 * logout path refreshes it when a skulled player disconnects, so an X-log can never finish before
 * a pending hit has landed.
 */
val DEADMAN_LOGOUT_TIMER = TimerKey(resetOnDeath = true)

/**
 * Timer key used to force a player disconnect, usually used so that if a
 * player's channel has been inactive (disconnected) for X amount of time,
 * we disconnect them so that they can play again.
 */
val FORCE_DISCONNECTION_TIMER = TimerKey()

/**
 * Timer key set when frozen. Audit C-13: cleared on death, so a fresh freeze/stun never carries over the respawn.
 */
val FROZEN_TIMER = TimerKey(resetOnDeath = true)

/**
 * Timer key set alongside [FROZEN_TIMER] (P9-followup further-foundations pass, 2026-09-02)
 * for a longer duration than the freeze itself, so it naturally outlives it: the real freeze
 * immunity mechanic grants a fixed grace period *after* a freeze wears off during which the
 * pawn can't be frozen again (sourced from the OSRS wiki's "Freeze" article: 5 ticks for both
 * Curse-book binds (Bind/Snare/Entangle) and Ice spells, the two freeze sources that existed
 * in this server's ~2011/rev-667 era — Arceuus' Grasp spells and their separate 2-tick
 * immunity are 2018+ content and not applicable here). See `Pawn.freeze()`. Audit C-13: cleared on
 * death together with [FROZEN_TIMER].
 */
val FREEZE_IMMUNITY_TIMER = TimerKey(resetOnDeath = true)

/**
 * Timer key set when stunned. Audit C-13: cleared on death, so a fresh freeze/stun never carries over the respawn.
 */
val STUN_TIMER = TimerKey(resetOnDeath = true)

/**
 * Timer key for poison ticks.
 */
val POISON_TIMER = TimerKey(persistenceKey = "poison", tickOffline = false, resetOnDeath = true)

/**
 * Timer key for poison immunity ticks.
 */
val POISON_IMMUNITY = TimerKey(persistenceKey = "poison_immunity", tickOffline = false, resetOnDeath = false)

/**
 * Timer key for venom ticks. Mutually exclusive with [POISON_TIMER] - a pawn can be
 * poisoned or envenomed, never both at once.
 */
val VENOM_TIMER = TimerKey(persistenceKey = "venom", tickOffline = false, resetOnDeath = true)

/**
 * Timer key for venom immunity ticks (granted by a full anti-venom cure).
 */
val VENOM_IMMUNITY = TimerKey(persistenceKey = "venom_immunity", tickOffline = false, resetOnDeath = false)

/**
 * Vengeance's recast cooldown - 50 ticks (30 real-world seconds), sourced from the OSRS
 * Wiki "Vengeance" page. Gates re-casting the spell, not the active reflect buff itself
 * (that lives in [gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR] and is consumed on the
 * next hit taken instead of expiring on a timer). resetOnDeath follows the same convention
 * already used for poison/venom's timers in this file - not independently sourced.
 */
val VENGEANCE_COOLDOWN = TimerKey(persistenceKey = "vengeance_cooldown", tickOffline = false, resetOnDeath = true)

/**
 * Timer key for regular antifire potion protection ticking down (6 minutes / 600 ticks,
 * sourced from the OSRS Wiki "Dragonfire" page). Was declared but never wired to anything
 * until the 2026-09-02 autonomous dragonfire pass - see [SUPER_ANTIFIRE_TIMER] for the
 * stronger tier, which grants full immunity on its own where this one only reduces damage.
 */
val ANTIFIRE_TIMER = TimerKey()

/**
 * Timer key for super antifire potion protection ticking down (3 minutes / 300 ticks,
 * sourced from the OSRS Wiki "Dragonfire" page). Kept separate from [ANTIFIRE_TIMER]
 * rather than reusing it with a "tier" attribute because the two grant different
 * protection outright (super alone is full immunity; regular alone only cuts damage by
 * 30%) - [gg.rsmod.plugins.content.combat.formula.DragonfireFormula] needs to distinguish
 * them, not just know "some antifire is active".
 */
val SUPER_ANTIFIRE_TIMER = TimerKey()

/**
 * Timer key for the delay in between a pawn's attack.
 */
val ATTACK_DELAY = TimerKey()

/**
 * Timer key for delay in between drinking potions.
 */
val POTION_DELAY = TimerKey()

/**
 * Timer key for delay in between eating food.
 */
val FOOD_DELAY = TimerKey()

/**
 * Timer key for delay in between eating "combo" food.
 */
val COMBO_FOOD_DELAY = TimerKey()

/**
 * Timer key for lighting a log while firemaking
 */
val LAST_LOG_LIT = TimerKey()

/**
 * Timer key for stat restore
 */
var STAT_RESTORE = TimerKey("stat_restoration", tickOffline = false, resetOnDeath = false)

/**
 * Timer key for time spent logged in
 */
val TIME_ONLINE =
    TimerKey(persistenceKey = "time_online", tickOffline = false, resetOnDeath = false, tickForward = true)

/**
 * Timer key for anti cheat random event trigger
 */
val ANTI_CHEAT_TIMER =
    TimerKey(
        persistenceKey = "anti_cheat",
        tickOffline = false,
        resetOnDeath = false,
        tickForward = false,
        removeOnZero = true,
    )

/**
 * Timer key for bonus experience time elapsed
 */
val BONUS_EXPERIENCE_TIME_ELAPSED = TimerKey()

/**
 * Timer key for saving the individual player
 */
val SAVE_TIMER = TimerKey()

/**
 * Timer key for dailies
 */
val DAILY_TIMER = TimerKey(persistenceKey = "dailies", tickOffline = true, resetOnDeath = false, removeOnZero = false)

/**
 * The timer to tick if a player is in an area that requires a light source
 */
val DARK_ZONE_TIMER = TimerKey()

/**
 * The timer used to trigger a visual update of the prayer points in the client after login
 */
val PRAYER_INITIALIZATION_TIMER = TimerKey()

/**
 * A timer that will run if the player is slotted for logout
 */
val LOGOUT_TIMER = TimerKey()

/**
 * Timer that raises special attack by 10 each time
 */
val SPECIAL_ATTACK_TIMER = TimerKey()

/**
 *  Timer that removes Spear Wall special effect
 */
val SPEAR_WALL_TIMER = TimerKey()

/**
 *  Timer that removes Hamstring special effect
 */
val HAMSTRING_TIMER = TimerKey()

/**
 *  Timer that counts down for bleed damage for Phantom Strike
 */
val PHANTOM_STRIKE_TIMER = TimerKey()

/**
 * Timer key set while a pawn is slowed by a Miasmic spell (Ancient Magicks): attack delay is
 * doubled for the duration. Set alongside [MIASMIC_IMMUNITY_TIMER].
 */
val MIASMIC_TIMER = TimerKey()

/**
 * Timer key that outlives [MIASMIC_TIMER]; while active the pawn can't be slowed again.
 */
val MIASMIC_IMMUNITY_TIMER = TimerKey()

/**
 * Timer key set by Teleport Block. While active every teleport method is refused with
 * "A magical force has stopped you from teleporting." Persisted and ticked offline so logging
 * out doesn't clear it.
 */
val TELEBLOCK_TIMER = TimerKey(persistenceKey = "teleblock", tickOffline = true, resetOnDeath = true)

/**
 * Staff of light special (Power of Light): while active, melee damage taken has a 50% chance to
 * be halved. One minute (100 ticks).
 */
val STAFF_OF_LIGHT_TIMER = TimerKey()

/**
 * Toxic staff of the dead: "the staff will immediately use 10 scales when the player enters combat and will use another
 * 10 if the player is still in combat after a minute has passed" (OSRS Wiki). Runs for one minute (100 ticks) after each
 * 10-scale charge use.
 */
val TOXIC_STAFF_SCALE_TIMER = TimerKey()

/**
 * Smoke ancient sceptre: "reducing a poisoned target's healing by 20% for 6 seconds after taking damage from smoke spells" (OSRS Wiki).
 */
val SMOKE_SCEPTRE_HEAL_REDUCTION_TIMER = TimerKey()

/**
 * Standard-book Charge spell duration (7 minutes = 700 cycles).
 */
val GOD_SPELL_CHARGE_TIMER = TimerKey()
