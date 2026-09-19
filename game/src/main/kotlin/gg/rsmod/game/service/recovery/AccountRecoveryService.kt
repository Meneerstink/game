package gg.rsmod.game.service.recovery

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import mu.KLogging
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.concurrent.Executors

/**
 * Runs [AccountRecovery] and its web page (OSRS style: the password is reset on a website, not in the client).
 *
 * game.yml service properties (all optional; without `smtp-host` the page and `setemail` say recovery is not switched on):
 *   smtp-host, smtp-port (465), smtp-user, smtp-password, mail-from, http-port (50078), public-url, saves-path (./data/saves/)
 * Put the page behind HTTPS (reverse proxy) before opening it to the internet: it carries passwords.
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
        val recovery =
            AccountRecovery(
                mailer?.let { m -> { to: String, subject: String, body: String -> m.send(to, subject, body) } },
                Paths.get(serviceProperties.getOrDefault("saves-path", "./data/saves/")),
                serviceProperties.getOrDefault("public-url", "http://localhost:$port/recover"),
            )
        AccountRecovery.instance = recovery
        if (mailer == null) {
            logger.info { "Account recovery: no smtp-host configured - e-mail recovery is switched off." }
            return
        }
        http =
            HttpServer.create(InetSocketAddress(port), 0).apply {
                executor = Executors.newSingleThreadExecutor { r -> Thread(r, "account-recovery-http").apply { isDaemon = true } }
                createContext("/recover") { exchange -> handle(exchange, recovery, world) }
                start()
            }
        logger.info { "Account recovery page listening on port $port." }
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

    private fun handle(
        exchange: HttpExchange,
        recovery: AccountRecovery,
        world: World,
    ) {
        val result =
            runCatching {
                val f = form(exchange)
                when (f["step"]) {
                    "request" -> recovery.requestReset(f["username"].orEmpty())
                    "reset" -> recovery.resetPassword({ name -> world.getPlayerForName(name) != null }, f["username"].orEmpty(), f["code"].orEmpty(), f["password"].orEmpty())
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
