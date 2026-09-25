package gg.rsmod.game.service.recovery

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.login.LoginService
import gg.rsmod.util.ServerProperties
import mu.KLogging
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs [AccountRecovery] and its web page (OSRS style: the password is reset on a website, not in the client).
 *
 * game.yml service properties (all optional; without `smtp-host` the page and `setemail` say recovery is not switched on):
 *   smtp-host, smtp-port (465), smtp-user, smtp-password, mail-from, http-port (50078), http-bind (127.0.0.1), public-url,
 *   saves-path (./data/saves/)
 * Put the page behind HTTPS (reverse proxy) before opening it to the internet: it carries passwords.
 *
 * Audit S-08: the page binds to 127.0.0.1 by default (only a local HTTPS reverse proxy can reach it; set `http-bind` to change
 * that), requests are served by a small thread pool and mails are sent in the background, so one slow SMTP server or Argon2
 * hash no longer blocks every other visitor.
 */
class AccountRecoveryService : Service {
    private var http: HttpServer? = null

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        val host = serviceProperties.getOrDefault("smtp-host", "")
        val mailer =
            if (host.isBlank()) {
                null
            } else {
                SmtpMailer(
                    host,
                    serviceProperties.getOrDefault("smtp-port", 465),
                    serviceProperties.getOrDefault("smtp-user", ""),
                    serviceProperties.getOrDefault("smtp-password", ""),
                    serviceProperties.getOrDefault("mail-from", serviceProperties.getOrDefault("smtp-user", "")),
                )
            }
        val port = serviceProperties.getOrDefault("http-port", 50078)
        val bind = serviceProperties.getOrDefault("http-bind", "127.0.0.1")
        val recovery =
            AccountRecovery(
                mailer?.let { m -> { to: String, subject: String, body: String -> m.send(to, subject, body) } },
                Paths.get(serviceProperties.getOrDefault("saves-path", "./data/saves/")),
                serviceProperties.getOrDefault("public-url", "http://localhost:$port/recover"),
                Executors.newSingleThreadExecutor { r -> Thread(r, "account-recovery-mail").apply { isDaemon = true } },
            )
        AccountRecovery.instance = recovery
        if (mailer == null) {
            logger.info { "Account recovery: no smtp-host configured - e-mail recovery is switched off." }
            return
        }
        val address = InetSocketAddress(bind, port)
        val behindLocalProxy = address.address?.isLoopbackAddress == true
        http =
            HttpServer.create(address, 0).apply {
                executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "account-recovery-http").apply { isDaemon = true } }
                createContext("/recover") { exchange -> handle(exchange, recovery, world, behindLocalProxy) }
                start()
            }
        logger.info { "Account recovery page listening on $bind:$port." }
    }

    private fun form(exchange: HttpExchange): Map<String, String> {
        if (exchange.requestMethod != "POST") return emptyMap()
        val body = exchange.requestBody.readNBytes(4096).toString(Charsets.UTF_8)
        return body.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else URLDecoder.decode(pair.substring(0, i), "UTF-8") to URLDecoder.decode(pair.substring(i + 1), "UTF-8")
        }.toMap()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /**
     * The visitor's address. Behind a local reverse proxy every request comes from 127.0.0.1, so the last
     * `X-Forwarded-For` entry (the one the proxy itself appended) is used when the page only listens on loopback.
     */
    private fun clientIp(
        exchange: HttpExchange,
        trustForwardedFor: Boolean,
    ): String {
        val direct = exchange.remoteAddress?.address?.hostAddress ?: "unknown"
        if (!trustForwardedFor) return direct
        val forwarded = exchange.requestHeaders.getFirst("X-Forwarded-For") ?: return direct
        return forwarded.split(',').map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: direct
    }

    /** Audit S-08: whether [name] is logged in, asked on the game thread (the player list is not thread-safe). Unknown = online. */
    private fun isOnline(
        world: World,
        name: String,
    ): Boolean {
        val game = world.getService(GameService::class.java) ?: return world.getPlayerForName(name) != null
        val answer = CompletableFuture<Boolean>()
        game.submitGameThreadJob { answer.complete(world.getPlayerForName(name) != null) }
        return runCatching { answer.get(5, TimeUnit.SECONDS) }.getOrDefault(true)
    }

    /** Audit S-08: holds the account's login slot (see LoginService.sessions) while the recovery page rewrites its save. */
    private fun lockAccount(
        world: World,
        name: String,
    ): AutoCloseable? {
        val sessions = world.getService(LoginService::class.java)?.sessions ?: return AutoCloseable {}
        if (!sessions.tryClaim(name)) return null
        return AutoCloseable { sessions.release(name) }
    }

    private fun handle(
        exchange: HttpExchange,
        recovery: AccountRecovery,
        world: World,
        trustForwardedFor: Boolean,
    ) {
        val result =
            runCatching {
                val f = form(exchange)
                val ip = clientIp(exchange, trustForwardedFor)
                when (f["step"]) {
                    "request" -> recovery.requestReset(f["username"].orEmpty(), ip)
                    "reset" ->
                        recovery.resetPassword(
                            { name -> isOnline(world, name) },
                            f["username"].orEmpty(),
                            f["code"].orEmpty(),
                            f["password"].orEmpty(),
                            ip,
                            { name -> lockAccount(world, name) },
                        )
                    else -> ""
                }
            }.getOrElse { "Something went wrong. Please try again." }
        val page =
            """
            <!doctype html><html><head><meta charset="utf-8"><title>78 - Recover your password</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font-family:Verdana,sans-serif;background:#2b2419;color:#ff981f;max-width:420px;margin:40px auto;padding:0 16px}
            input,button{width:100%;padding:8px;margin:4px 0 12px;box-sizing:border-box}p.msg{color:#fff}</style></head><body>
            <h1>Recover your password</h1>
            ${if (result.isNotEmpty()) "<p class=\"msg\">${esc(result)}</p>" else ""}
            <h2>1. Get a reset code</h2>
            <form method="post"><input type="hidden" name="step" value="request">
            <label>Username<input name="username" maxlength="12" required></label><button>Send code to my e-mail</button></form>
            <h2>2. Choose a new password</h2>
            <form method="post"><input type="hidden" name="step" value="reset">
            <label>Username<input name="username" maxlength="12" required></label>
            <label>Code from the e-mail<input name="code" maxlength="8" required></label>
            <label>New password (5-20 letters and numbers)<input name="password" type="password" maxlength="20" required></label>
            <button>Change password</button></form></body></html>
            """.trimIndent()
        val bytes = page.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {}

    override fun bindNet(
        server: Server,
        world: World,
    ) {}

    override fun terminate(
        server: Server,
        world: World,
    ) {
        http?.stop(0)
    }

    companion object : KLogging()
}
