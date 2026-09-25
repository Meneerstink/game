package gg.rsmod.game.service.login

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Audit S-06: login rate limits.
 *
 * - [allowAttempt]: at most [attemptsPerIp] login attempts per IP address per [attemptWindowMs]
 *   (every attempt costs an Argon2 hash of 64 MiB on a login worker).
 * - [recordFailure] / [isAccountLocked]: [failuresBeforeLock] wrong passwords for one account
 *   within [failureWindowMs] lock that account for [lockMs], whichever IPs they came from.
 * - [allowRegistration]: at most [registrationsPerIp] new accounts per IP per [registrationWindowMs]
 *   (every unknown name used to create a save on disk).
 *
 * Thread-safe. [clock] is injectable for tests.
 */
class LoginThrottle(
    private val attemptsPerIp: Int = 10,
    private val attemptWindowMs: Long = 60_000L,
    private val failuresBeforeLock: Int = 5,
    private val failureWindowMs: Long = 5 * 60_000L,
    private val lockMs: Long = 5 * 60_000L,
    private val registrationsPerIp: Int = 5,
    private val registrationWindowMs: Long = 60 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val attempts = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val failures = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val lockedUntil = ConcurrentHashMap<String, Long>()
    private val registrations = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** Counts one login attempt from [ip]; false when the IP is over its limit (the attempt is then not counted). */
    fun allowAttempt(ip: String): Boolean = take(attempts, ip, attemptsPerIp, attemptWindowMs)

    /** Counts one new account from [ip]; false when the IP is over its limit. */
    fun allowRegistration(ip: String): Boolean = take(registrations, ip, registrationsPerIp, registrationWindowMs)

    fun isAccountLocked(account: String): Boolean {
        val key = AccountSessionRegistry.normalize(account)
        val until = lockedUntil[key] ?: return false
        if (clock() < until) {
            return true
        }
        lockedUntil.remove(key, until)
        return false
    }

    /** A wrong password for [account]; locks the account once [failuresBeforeLock] is reached. */
    fun recordFailure(account: String) {
        val key = AccountSessionRegistry.normalize(account)
        val now = clock()
        val list = failures.computeIfAbsent(key) { ArrayDeque() }
        synchronized(list) {
            prune(list, now, failureWindowMs)
            list.addLast(now)
            if (list.size >= failuresBeforeLock) {
                lockedUntil[key] = now + lockMs
                list.clear()
            }
        }
    }

    /** A correct password: forget the earlier failures of [account]. */
    fun recordSuccess(account: String) {
        failures.remove(AccountSessionRegistry.normalize(account))
    }

    /** Drops bookkeeping that can no longer matter, so the maps do not grow without bound. */
    fun purge() {
        val now = clock()
        purge(attempts, now, attemptWindowMs)
        purge(failures, now, failureWindowMs)
        purge(registrations, now, registrationWindowMs)
        lockedUntil.entries.removeIf { now >= it.value }
    }

    private fun take(
        map: ConcurrentHashMap<String, ArrayDeque<Long>>,
        key: String,
        limit: Int,
        windowMs: Long,
    ): Boolean {
        val now = clock()
        val list = map.computeIfAbsent(key) { ArrayDeque() }
        synchronized(list) {
            prune(list, now, windowMs)
            if (list.size >= limit) {
                return false
            }
            list.addLast(now)
            return true
        }
    }

    private fun prune(
        list: ArrayDeque<Long>,
        now: Long,
        windowMs: Long,
    ) {
        while (list.isNotEmpty() && now - list.peekFirst() >= windowMs) {
            list.pollFirst()
        }
    }

    private fun purge(
        map: ConcurrentHashMap<String, ArrayDeque<Long>>,
        now: Long,
        windowMs: Long,
    ) {
        map.entries.removeIf { (_, list) ->
            synchronized(list) {
                prune(list, now, windowMs)
                list.isEmpty()
            }
        }
    }
}
