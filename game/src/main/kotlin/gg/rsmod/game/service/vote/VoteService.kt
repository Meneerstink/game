package gg.rsmod.game.service.vote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import mu.KLogging
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors

/**
 * Owner 2026-10-10 voting system: the page the toplists call back ("postback" / "callback" URL) when someone voted. Each site is set
 * up with `https://<host>/vote/<secret>/<site id>?<its username parameter>=` ([VoteSites], `data/cfg/votes.yml`); a vote for an
 * existing account goes into the [VoteLedger] (1 per site per 12 hours) and the game hands the reward out ([VoteLedger.onVote]).
 *
 * game.yml service properties: `secret` (required - without it voting stays off; a long random word that only the toplists know,
 * so nobody can call the page to give himself coins), http-port (50079), http-bind (127.0.0.1: put it behind the VPS's HTTPS
 * reverse proxy like the recovery page; 0.0.0.0 to open it directly), ledger-path (./data/votes/ledger.json), saves-path
 * (./data/saves/).
 */
class VoteService : Service {
    private var http: HttpServer? = null

    /** Bound to loopback = behind the VPS's reverse proxy, which appends the caller's address to X-Forwarded-For. */
    private var behindLocalProxy = false

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        // Like AccountRecoveryService (ROOT_CAUSES #109): a test server never takes the live server's port or gives real coins.
        if (gg.rsmod.util.io.SharedDataFiles.testServer()) {
            logger.info { "Voting: off on a test server." }
            return
        }
        val ledger = VoteLedger(Paths.get(serviceProperties.getOrDefault("ledger-path", "./data/votes/ledger.json")))
        VoteLedger.instance = ledger
        val secret = serviceProperties.getOrDefault("secret", "")
        if (secret.length < 12) {
            logger.info { "Voting: no secret (12+ characters) in game.yml - the vote callback page is off." }
            return
        }
        val saves = Paths.get(serviceProperties.getOrDefault("saves-path", "./data/saves/"))
        val port = serviceProperties.getOrDefault("http-port", 50079)
        val bind = serviceProperties.getOrDefault("http-bind", "127.0.0.1")
        val address = InetSocketAddress(bind, port)
        behindLocalProxy = address.address?.isLoopbackAddress == true
        http =
            HttpServer.create(address, 0).apply {
                executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "vote-http").apply { isDaemon = true } }
                createContext("/vote/") { exchange -> handle(exchange, world, ledger, secret, saves) }
                start()
            }
        logger.info { "Vote callback page listening on $bind:$port." }
    }

    private fun params(exchange: HttpExchange): Map<String, String> {
        val query = exchange.requestURI.rawQuery.orEmpty()
        val body = if (exchange.requestMethod == "POST") exchange.requestBody.readNBytes(4096).toString(Charsets.UTF_8) else ""
        return (query.split('&') + body.split('&')).mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else runCatching { URLDecoder.decode(pair.substring(0, i), "UTF-8") to URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
    }

    /** The caller's address (the proxy's own X-Forwarded-For entry when behind a local proxy, as AccountRecoveryService). */
    private fun clientIp(exchange: HttpExchange): String {
        val direct = exchange.remoteAddress?.address?.hostAddress ?: ""
        if (!behindLocalProxy) return direct
        val forwarded = exchange.requestHeaders.getFirst("X-Forwarded-For") ?: return direct
        return forwarded.split(',').map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: direct
    }

    /** Whether the caller is [host] (looked up on every call: TopG may move monitor.topg.org). */
    private fun fromHost(
        exchange: HttpExchange,
        host: String,
    ): Boolean {
        val ip = clientIp(exchange)
        return runCatching { InetAddress.getAllByName(host).any { it.hostAddress == ip } }.getOrDefault(false)
    }

    /** The account's save file exists (a vote for a name nobody plays is ignored, so the ledger cannot be filled with junk). */
    private fun accountExists(
        saves: Path,
        username: String,
    ): Boolean {
        val forms = setOf(username.lowercase(), username.lowercase().replace(' ', '_'), username.lowercase().replace('_', ' '))
        return runCatching { Files.list(saves).use { files -> files.anyMatch { it.fileName.toString().lowercase() in forms } } }.getOrDefault(false)
    }

    private fun handle(
        exchange: HttpExchange,
        world: World,
        ledger: VoteLedger,
        secret: String,
        saves: Path,
    ) {
        val parts = exchange.requestURI.path.trim('/').split('/')
        // /vote/<secret>/<site>
        val site = VoteSites.load().firstOrNull { parts.size == 3 && it.id == parts[2] }
        if (parts.size != 3 || parts[1] != secret || site == null) {
            reply(exchange, 404, "Not found")
            return
        }
        val p = params(exchange)
        val refusal =
            when {
                site.siteSecret.isNotEmpty() && p[site.secretParam] != site.siteSecret -> "wrong ${site.secretParam}"
                site.allowedHost.isNotEmpty() && !fromHost(exchange, site.allowedHost) -> "not from ${site.allowedHost}"
                else -> null
            }
        if (refusal != null) {
            logger.warn { "Vote from ${site.id} refused: $refusal (${exchange.remoteAddress?.address?.hostAddress})" }
            reply(exchange, 403, "Forbidden")
            return
        }
        val username = p[site.usernameParam].orEmpty().trim().replace('_', ' ')
        // rsps-list calls back for refused duplicates too, with voted=0.
        if (site.successParam.isNotEmpty() && p[site.successParam] != "1") {
            logger.info { "Vote from ${site.id} for '$username' not counted: ${site.successParam}=${p[site.successParam]}" }
            reply(exchange, 200, "OK")
            return
        }
        val result =
            when {
                !username.matches(VoteLedger.NAME) -> VoteLedger.Result.BAD_NAME
                !accountExists(saves, username) -> VoteLedger.Result.BAD_NAME
                else -> ledger.record(site.id, username)
            }
        logger.info { "Vote from ${site.id} for '$username': $result" }
        if (result == VoteLedger.Result.ACCEPTED) {
            val game = world.getService(GameService::class.java)
            val deliver = { VoteLedger.onVote?.invoke(username) ?: Unit }
            if (game != null) game.submitGameThreadJob { deliver() } else deliver()
        }
        reply(exchange, 200, "OK")
    }

    private fun reply(
        exchange: HttpExchange,
        code: Int,
        text: String,
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
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
