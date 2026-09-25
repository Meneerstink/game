package gg.rsmod.game.service.login

import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.serializer.PlayerLoadResult
import gg.rsmod.game.service.world.WorldVerificationService
import gg.rsmod.net.codec.login.LoginResponse
import gg.rsmod.net.codec.login.LoginResultType
import gg.rsmod.util.io.IsaacRandom
import io.netty.channel.ChannelFutureListener
import mu.KLogging

/**
 * A worker for the [LoginService] that is responsible for handling the most
 * recent, non-handled [LoginServiceRequest] from its [boss].
 *
 * @author Tom <rspsmods@gmail.com>
 */
class LoginWorker(
    private val boss: LoginService,
    private val verificationService: WorldVerificationService,
) : Runnable {
    override fun run() {
        while (true) {
            val request = boss.requests.take()
            // Audit S-03: the account is claimed before its save is read and stays claimed until
            // the logout save has been written (Client.handleLogout), so a second session can never
            // read a save that is about to be overwritten.
            val account = AccountSessionRegistry.normalize(request.login.username)
            var claimed = false
            var handedToGameThread = false
            try {
                val world = request.world

                // Audit S-06: an account that had too many wrong passwords is locked for a while.
                if (boss.throttle.isAccountLocked(account)) {
                    LoginService.reject(request.login.channel, LoginResultType.MAX_ATTEMPTS)
                    logger.info("User '{}' login denied: account temporarily locked after failed attempts.", request.login.username)
                    continue
                }
                if (!boss.sessions.tryClaim(account)) {
                    LoginService.reject(request.login.channel, LoginResultType.ALREADY_ONLINE)
                    logger.info("User '{}' login denied: account is online or still logging out.", request.login.username)
                    continue
                }
                claimed = true

                val client = Client.fromRequest(world, request.login)
                val loadResult: PlayerLoadResult = boss.serializer.loadClientData(client, request.login)

                if (loadResult == PlayerLoadResult.LOAD_ACCOUNT || loadResult == PlayerLoadResult.NEW_ACCOUNT) {
                    if (AccountSessionRegistry.normalize(client.loginUsername) != account) {
                        // The save names a different account than the file it was read from; the claim
                        // would not protect the account that actually logs in.
                        LoginService.reject(request.login.channel, LoginResultType.COULD_NOT_COMPLETE_LOGIN)
                        logger.error("User '{}' login denied: save belongs to '{}'.", request.login.username, client.loginUsername)
                        continue
                    }
                    boss.throttle.recordSuccess(account)

                    val decodeRandom = IsaacRandom(request.login.xteaKeys)
                    val encodeRandom =
                        IsaacRandom(
                            IntArray(request.login.xteaKeys.size) {
                                request.login.xteaKeys[it] +
                                    50
                            },
                        )

                    val gameService = world.getService(GameService::class.java)
                    if (gameService == null) {
                        LoginService.reject(request.login.channel, LoginResultType.COULD_NOT_COMPLETE_LOGIN)
                        continue
                    }
                    handedToGameThread = true
                    gameService.submitGameThreadJob {
                        var registered = false
                        try {
                            val interceptedLoginResult =
                                verificationService.interceptLoginResult(
                                    world,
                                    client.uid,
                                    client.username,
                                    client.loginUsername,
                                )
                            val loginResult: LoginResultType =
                                interceptedLoginResult ?: if (client.register()) {
                                    registered = true
                                    LoginResultType.LOGGED_IN
                                } else {
                                    LoginResultType.COULD_NOT_COMPLETE_LOGIN
                                }
                            if (loginResult == LoginResultType.LOGGED_IN) {
                                client.channel.write(
                                    LoginResponse(
                                        index = client.index,
                                        privilege = client.privilege.id,
                                        result = loginResult,
                                    ),
                                )
                                boss.successfulLogin(client, world, encodeRandom, decodeRandom)
                            } else {
                                request.login.channel
                                    .writeAndFlush(loginResult)
                                    .addListener(ChannelFutureListener.CLOSE)
                                logger.info("User '{}' login denied with code {}.", client.username, loginResult)
                            }
                        } finally {
                            // Registered players keep the claim until Client.handleLogout has saved them.
                            if (!registered) {
                                boss.sessions.release(account)
                            }
                        }
                    }
                } else {
                    if (loadResult == PlayerLoadResult.INVALID_CREDENTIALS) {
                        boss.throttle.recordFailure(account)
                    }
                    val errorCode =
                        when (loadResult) {
                            PlayerLoadResult.INVALID_CREDENTIALS -> LoginResultType.INVALID_CREDENTIALS
                            PlayerLoadResult.INVALID_RECONNECTION -> LoginResultType.BAD_SESSION_ID
                            PlayerLoadResult.MALFORMED -> LoginResultType.ACCOUNT_LOCKED
                            // The client has no "password too weak" message; the login screen's own
                            // "invalid username or password" is the closest one.
                            PlayerLoadResult.INVALID_NEW_PASSWORD -> LoginResultType.INVALID_CREDENTIALS
                            PlayerLoadResult.REGISTRATION_LIMIT -> LoginResultType.MAX_ATTEMPTS
                            else -> LoginResultType.COULD_NOT_COMPLETE_LOGIN
                        }
                    request.login.channel
                        .writeAndFlush(errorCode)
                        .addListener(ChannelFutureListener.CLOSE)
                    logger.info(
                        "User '{}' login denied with code {} and channel {}.",
                        client.username,
                        loadResult,
                        client.channel,
                    )
                }
            } catch (e: Exception) {
                logger.error("Error when handling request from ${request.login.channel}.", e)
                request.login.channel.close()
            } finally {
                if (claimed && !handedToGameThread) {
                    boss.sessions.release(account)
                }
            }
        }
    }

    companion object : KLogging()
}
