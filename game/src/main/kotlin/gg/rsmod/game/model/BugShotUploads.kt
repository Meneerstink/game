package gg.rsmod.game.model

import mu.KLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando (bewijs bij de melding)"; Keurmeester
 * round 1 FAIL: the PNG stayed on the reporter's PC - friends play from other PCs and the server moves to a VPS).
 *
 * Donor: RuneLite ImageCapture.uploadScreenshot (the client sends its own screenshot to a server after the event; RuneLite
 * posts it to Imgur over HTTP). Here the server is ours and the reporter is already connected to it, so the PNG comes over
 * the game connection the player is logged in with (port 50015 - the one port every friend and the VPS already reach; a new
 * HTTP port would need a router forward and a login of its own):
 * - the `bug` command calls [expect]: this player may now send ONE screenshot for THAT report (its stem);
 * - the 78 client sends the PNG in pieces (opcode [OPCODE], var-short, at most [MAX_CHUNK] data bytes each - its own write
 *   buffer is 1350 bytes); every piece names its report stem and [receive] puts the pieces of each stem together: in order,
 *   one size, at most [MAX_BYTES];
 * - complete + a real PNG of a sane size -> written as bugs\<report stem>.png next to the report (never over another file),
 *   the report gets the line "Screenshot received: ..." and the reporter's client is told through the notify callback of
 *   [expect]: SAVED only after the file is on disk, REFUSED with the reason for every refusal.
 * Round 3 (Keurmeester FAIL round 2: two reports 15 s apart lost BOTH screenshots - the newer report replaced the open
 * request while the client was still sending the older PNG, and the pieces carried no report id): every report keeps its own
 * open upload (up to [MAX_OPEN] per player; the client sends them one after the other), a piece is matched by its stem, and
 * the rest of an upload that was already closed is dropped silently (one log line when it closes, not one per piece).
 * The file name is the server's own report stem: nothing the client sends ever becomes a path - a stem that is not one of
 * the player's open reports is refused. Refusals are logged as `[bugshot] PROBLEM`. Tests: BugShotUploadsTests.
 */
object BugShotUploads : KLogging() {
    /** ClientProt RSPS_BUGSHOT_CHUNK in the 78 client (var-short). */
    const val OPCODE = 94

    /** A 1920x1080 game frame as PNG is about 2-3 MB; anything above is not a screenshot. */
    const val MAX_BYTES = 6 * 1024 * 1024

    /** Data bytes of one piece; the client sends 1250 (RspsBugShot.CHUNK) - its write buffer cannot send more than 1350 per packet. */
    const val MAX_CHUNK = 1350

    /** Longest stem a piece may name (RspsBugShot.STEM: bug-yyyyMMdd-HHmmss-nnn- + at most 20 letters = 44). */
    const val MAX_STEM = 48

    /**
     * The 78 client's upload pace (RspsBugShot: one piece per 85 ms), kept at half of the connection's read limit
     * (ClientChannelInitializer.CHANNEL_READ_LIMIT) so the player's own packets are never read late.
     */
    const val CLIENT_RATE = 16 * 1024

    /**
     * An open upload expires when the player sent no piece at all (of any report) for this long since it was asked for:
     * [MAX_BYTES] at [CLIENT_RATE] takes ~6.5 minutes, a normal 0.4-2 MB screenshot 25 s - 2 minutes. A report waiting behind
     * another upload stays open while that one is still sending.
     */
    const val REQUEST_TTL_MS = 10 * 60 * 1000L

    /** Screenshot requests per player per hour (each report still gets its .txt; above this only the client keeps the PNG). */
    const val MAX_PER_HOUR = 20

    /** Open uploads per player; a newer report beyond this closes the OLDEST one (the client keeps its own copy). */
    const val MAX_OPEN = 5

    const val MAX_SIDE = 8192

    /** Never fill the disk with screenshots (AGENTS.md 5: keep room free). */
    @Volatile
    var minFreeBytes: Long = 1L shl 30

    private val WRITER =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "rsps-bugshot-writer").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }

    /** Where a finished PNG is written - the writer thread; tests run it inline. */
    @Volatile
    var executor: (Runnable) -> Unit = { WRITER.execute(it) }

    enum class Status { CHUNK, COMPLETE, REFUSED }

    data class Result(val status: Status, val detail: String)

    /** What the reporter's client is told about one report: SAVED after the PNG is on disk, else REFUSED with the reason. */
    enum class Outcome { SAVED, REFUSED }

    private class Upload(val report: File, val issuedAt: Long, val notify: (Outcome, String) -> Unit) {
        val stem: String = report.nameWithoutExtension
        var data: ByteArray? = null
        var received = 0
    }

    /** One player's open uploads by stem (oldest first), the time of the last piece and the stems closed lately. */
    private class Uploads {
        val open = LinkedHashMap<String, Upload>()
        var lastPieceAt = 0L
        val closed =
            object : LinkedHashMap<String, String>() {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 4 * MAX_OPEN
            }
    }

    private val players = ConcurrentHashMap<String, Uploads>()
    private val requests = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val lastWarned = ConcurrentHashMap<String, Long>()

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun key(username: String) = username.lowercase()

    /**
     * The `bug` command (game thread): [username] may now upload the screenshot of [report]. False when the player already
     * asked [MAX_PER_HOUR] times this hour - then the client only keeps its own copy. Earlier reports that are still open stay
     * open (round 3). [notify] hears once how this report ends (called on the network or the writer thread).
     */
    fun expect(
        username: String,
        report: File,
        now: Long = System.currentTimeMillis(),
        notify: (Outcome, String) -> Unit = { _, _ -> },
    ): Boolean {
        val k = key(username)
        val times = requests.computeIfAbsent(k) { ArrayDeque() }
        synchronized(times) {
            while (times.isNotEmpty() && now - times.peekFirst() >= 60 * 60 * 1000L) {
                times.pollFirst()
            }
            if (times.size >= MAX_PER_HOUR) {
                logger.warn { "[bugshot] PROBLEM $username asked ${times.size} screenshots this hour - ${report.name} gets none on the server" }
                return false
            }
            times.addLast(now)
        }
        val p = players.computeIfAbsent(k) { Uploads() }
        val up = Upload(report, now, notify)
        val evicted: Upload?
        synchronized(p) {
            p.closed.remove(up.stem)
            p.open[up.stem] = up
            evicted = if (p.open.size > MAX_OPEN) p.open.values.first() else null
            if (evicted != null) {
                close(p, evicted, "more than $MAX_OPEN reports were waiting for their screenshot")
            }
        }
        if (evicted != null) {
            logger.warn { "[bugshot] PROBLEM $username: ${evicted.stem} closed - more than $MAX_OPEN reports were waiting for a screenshot" }
            tell(evicted, Outcome.REFUSED, "more than $MAX_OPEN reports were waiting for their screenshot")
        }
        return true
    }

    /** One piece of report [stem]'s screenshot from [username]'s client (network thread). */
    fun receive(
        username: String,
        stem: String,
        total: Int,
        offset: Int,
        data: ByteArray,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val p = players[key(username)] ?: return refuse(username, null, "no screenshot was asked for (no bug report open)", now)
        val up: Upload
        val png: ByteArray
        val dims: Pair<Int, Int>
        synchronized(p) {
            up = p.open[stem] ?: run {
                // the rest of an upload that was already closed (refused, expired, replaced) still on its way: dropped quietly
                val closedWhy = p.closed[stem]
                return if (closedWhy != null) {
                    Result(Status.REFUSED, "closed earlier: $closedWhy")
                } else {
                    refuse(username, null, "no open bug report '${stem.take(MAX_STEM)}'", now)
                }
            }
            if (now - maxOf(up.issuedAt, p.lastPieceAt) > REQUEST_TTL_MS) {
                return drop(p, up, username, "the request expired", now)
            }
            p.lastPieceAt = now
            if (data.isEmpty() || data.size > MAX_CHUNK) {
                return drop(p, up, username, "a piece of ${data.size} bytes (1..$MAX_CHUNK)", now)
            }
            if (total < PNG_SIGNATURE.size || total > MAX_BYTES) {
                return drop(p, up, username, "a screenshot of $total bytes (max $MAX_BYTES)", now)
            }
            var buf = up.data
            if (buf == null) {
                if (offset != 0) {
                    return drop(p, up, username, "the first piece starts at $offset", now)
                }
                buf = ByteArray(total)
                up.data = buf
                logger.info { "[bugshot] receiving ${up.stem}.png from $username: $total bytes" }
            }
            if (total != buf.size) {
                return drop(p, up, username, "the size changed from ${buf.size} to $total", now)
            }
            if (offset != up.received) {
                return drop(p, up, username, "a piece at $offset, expected ${up.received}", now)
            }
            if (offset.toLong() + data.size > total) {
                return drop(p, up, username, "a piece past the end ($offset+${data.size} > $total)", now)
            }
            System.arraycopy(data, 0, buf, offset, data.size)
            up.received += data.size
            if (up.received < total) {
                return Result(Status.CHUNK, "${up.received}/$total")
            }
            val size = pngSize(buf) ?: return drop(p, up, username, "not a PNG", now)
            if (size.first !in 1..MAX_SIDE || size.second !in 1..MAX_SIDE) {
                return drop(p, up, username, "a PNG of ${size.first}x${size.second}", now)
            }
            close(p, up, "complete")
            png = buf
            dims = size
        }
        executor {
            val out = store(up.report, png, dims.first, dims.second)
            if (out != null) {
                tell(up, Outcome.SAVED, "${out.name} ${dims.first}x${dims.second} ${(png.size + 1023) / 1024} KB")
            } else {
                tell(up, Outcome.REFUSED, "the server could not save it")
            }
        }
        return Result(Status.COMPLETE, "${up.stem}.png ${dims.first}x${dims.second} ${png.size} bytes")
    }


    /**
     * Writes the PNG as <report stem>.png next to [report] (<stem>-shot2.png ... when that name is taken - never overwrites)
     * and adds "Screenshot received: ..." to the report. Null when the disk is too full.
     */
    fun store(
        report: File,
        png: ByteArray,
        width: Int,
        height: Int,
    ): File? {
        val dir = report.absoluteFile.parentFile
        try {
            dir.mkdirs()
            if (dir.usableSpace < minFreeBytes) {
                logger.warn { "[bugshot] PROBLEM ${report.name}: only ${dir.usableSpace / (1024 * 1024)} MB free in $dir - screenshot not saved" }
                return null
            }
            val stem = report.nameWithoutExtension
            var out = File(dir, "$stem.png")
            var n = 2
            while (out.exists()) {
                out = File(dir, "$stem-shot$n.png")
                n++
            }
            val part = File(dir, out.name + ".part")
            part.writeBytes(png)
            Files.move(part.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (report.isFile) {
                report.appendText(
                    "Screenshot received: ${out.name} (${width}x$height, ${(png.size + 1023) / 1024} KB, sent by the reporter's 78 client " +
                        "at ${LocalTime.now().format(CLOCK)})\n",
                )
            }
            logger.info { "[bugshot] saved ${out.path} ${width}x$height ${png.size} bytes" }
            return out
        } catch (e: Exception) {
            logger.warn { "[bugshot] PROBLEM ${report.name}: screenshot not saved ($e)" }
            return null
        }
    }

    /** Width and height from the PNG header (signature + IHDR), or null when [png] is not a PNG. */
    fun pngSize(png: ByteArray): Pair<Int, Int>? {
        if (png.size < 24) return null
        for (i in PNG_SIGNATURE.indices) {
            if (png[i] != PNG_SIGNATURE[i]) return null
        }
        if (String(png, 12, 4, Charsets.ISO_8859_1) != "IHDR") return null
        fun int(at: Int) = (png[at].toInt() and 0xFF shl 24) or (png[at + 1].toInt() and 0xFF shl 16) or (png[at + 2].toInt() and 0xFF shl 8) or (png[at + 3].toInt() and 0xFF)
        return int(16) to int(20)
    }


    /** Tests only: forget every request. */
    fun clear() {
        players.clear()
        requests.clear()
        lastWarned.clear()
    }

    /**
     * Round 4 (AUDIT T2-93: after a kick the client went on sending the old account's PNG over a new login): the player's
     * session ended (logout, kick, lost connection - plugin on_logout). His open uploads are closed quietly: the 78 client drops
     * them on any new connection too (RspsBugShot.checkSession), so no piece of them can come any more, and the partial data
     * is freed. Nobody is told (the player is gone); a finished upload already being written is not affected.
     */
    fun endSession(username: String) {
        val p = players[key(username)] ?: return
        val ended: List<String>
        synchronized(p) {
            ended = p.open.values.map { "${it.stem} ${it.received}/${it.data?.size ?: 0}" }
            for (up in p.open.values.toList()) close(p, up, "the player's session ended")
        }
        if (ended.isNotEmpty()) {
            logger.info { "[bugshot] $username's session ended - ${ended.size} screenshot upload(s) closed, not saved: ${ended.joinToString()}" }
        }
    }

    /** The stems of [username]'s open uploads, oldest first (tests). */
    fun openStems(username: String): List<String> = players[key(username)]?.let { p -> synchronized(p) { p.open.keys.toList() } } ?: emptyList()

    private fun close(
        p: Uploads,
        up: Upload,
        why: String,
    ) {
        p.open.remove(up.stem)
        p.closed[up.stem] = why
    }

    private fun tell(
        up: Upload,
        outcome: Outcome,
        detail: String,
    ) {
        try {
            up.notify(outcome, detail)
        } catch (e: Exception) {
            logger.warn { "[bugshot] PROBLEM ${up.stem}: the reporter could not be told ($e)" }
        }
    }

    /** Closes [up] after a bad piece (the rest of it is dropped quietly) and tells the reporter's client to stop sending it. */
    private fun drop(
        p: Uploads,
        up: Upload,
        username: String,
        why: String,
        now: Long,
    ): Result {
        close(p, up, why)
        tell(up, Outcome.REFUSED, why)
        return refuse(username, up.stem, why, now)
    }

    private fun refuse(
        username: String,
        stem: String?,
        why: String,
        now: Long,
    ): Result {
        // one line per closed upload; pieces of no open report at most one line per player per 10 s
        val k = key(username)
        val last = lastWarned[k]
        if (stem != null || last == null || now - last > 10_000L) {
            lastWarned[k] = now
            logger.warn { "[bugshot] PROBLEM refused a screenshot piece from $username${stem?.let { " ($it)" } ?: ""}: $why" }
        }
        return Result(Status.REFUSED, why)
    }
}
