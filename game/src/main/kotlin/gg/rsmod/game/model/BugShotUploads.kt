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
 * - the `bug` command calls [expect]: this player may now send ONE screenshot, for that report, within [REQUEST_TTL_MS];
 * - the 78 client sends the PNG in pieces (opcode [OPCODE], var-short, at most [MAX_CHUNK] bytes each - its own write buffer
 *   is 1350 bytes) and [receive] puts them together: in order, one size, at most [MAX_BYTES];
 * - complete + a real PNG of a sane size -> written as bugs\<report stem>.png next to the report (never over another file),
 *   and the report gets the line "Screenshot received: ...".
 * The file name is the server's own report stem: nothing the client sends ever becomes a path. No request, another player's
 * report, a second upload, a gap or a bad size: refused and logged as `[bugshot] PROBLEM`. Tests: BugShotUploadsTests.
 */
object BugShotUploads : KLogging() {
    /** ClientProt RSPS_BUGSHOT_CHUNK in the 78 client (var-short). */
    const val OPCODE = 94

    /** A 1920x1080 game frame as PNG is about 2-3 MB; anything above is not a screenshot. */
    const val MAX_BYTES = 6 * 1024 * 1024

    /** The client sends 1300-byte pieces (RspsBugShot.CHUNK); its write buffer cannot send more than 1350 bytes per packet. */
    const val MAX_CHUNK = 1350

    /**
     * The 78 client's upload pace (RspsBugShot: one 1300-byte piece per 80 ms), kept at half of the connection's read limit
     * (ClientChannelInitializer.CHANNEL_READ_LIMIT) so the player's own packets are never read late.
     */
    const val CLIENT_RATE = 16 * 1024

    /** [MAX_BYTES] at [CLIENT_RATE] takes ~6.5 minutes; a normal 0.4-2 MB screenshot 25 s - 2 minutes. */
    const val REQUEST_TTL_MS = 10 * 60 * 1000L

    /** Screenshot requests per player per hour (each report still gets its .txt; above this only the client keeps the PNG). */
    const val MAX_PER_HOUR = 20

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

    private class Upload(val report: File, val issuedAt: Long) {
        val stem: String = report.nameWithoutExtension
        var data: ByteArray? = null
        var received = 0
    }

    private val pending = ConcurrentHashMap<String, Upload>()
    private val requests = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val lastWarned = ConcurrentHashMap<String, Long>()

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun key(username: String) = username.lowercase()

    /**
     * The `bug` command (game thread): [username] may now upload the screenshot of [report]. False when the player already
     * asked [MAX_PER_HOUR] times this hour - then the client only keeps its own copy. A newer report replaces a request
     * that is still open (only the newest report of a player gets its picture).
     */
    fun expect(
        username: String,
        report: File,
        now: Long = System.currentTimeMillis(),
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
        pending[k] = Upload(report, now)
        return true
    }

    /** One piece from [username]'s client (network thread). */
    fun receive(
        username: String,
        total: Int,
        offset: Int,
        data: ByteArray,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val k = key(username)
        val up = pending[k] ?: return refuse(username, null, "no screenshot was asked for (no bug report open)", now)
        synchronized(up) {
            if (pending[k] !== up) {
                return refuse(username, null, "the request was replaced or closed", now)
            }
            if (now - up.issuedAt > REQUEST_TTL_MS) {
                return drop(k, up, username, "the request for ${up.stem} expired", now)
            }
            if (data.isEmpty() || data.size > MAX_CHUNK) {
                return drop(k, up, username, "a piece of ${data.size} bytes (1..$MAX_CHUNK)", now)
            }
            if (total < PNG_SIGNATURE.size || total > MAX_BYTES) {
                return drop(k, up, username, "a screenshot of $total bytes (max $MAX_BYTES)", now)
            }
            var buf = up.data
            if (buf == null) {
                if (offset != 0) {
                    return drop(k, up, username, "the first piece starts at $offset", now)
                }
                buf = ByteArray(total)
                up.data = buf
                logger.info { "[bugshot] receiving ${up.stem}.png from $username: $total bytes" }
            }
            if (total != buf.size) {
                return drop(k, up, username, "the size changed from ${buf.size} to $total", now)
            }
            if (offset != up.received) {
                return drop(k, up, username, "a piece at $offset, expected ${up.received}", now)
            }
            if (offset.toLong() + data.size > total) {
                return drop(k, up, username, "a piece past the end ($offset+${data.size} > $total)", now)
            }
            System.arraycopy(data, 0, buf, offset, data.size)
            up.received += data.size
            if (up.received < total) {
                return Result(Status.CHUNK, "${up.received}/$total")
            }
            pending.remove(k, up)
            val size = pngSize(buf) ?: return refuse(username, up.stem, "not a PNG", now)
            if (size.first !in 1..MAX_SIDE || size.second !in 1..MAX_SIDE) {
                return refuse(username, up.stem, "a PNG of ${size.first}x${size.second}", now)
            }
            val png = buf
            executor { store(up.report, png, size.first, size.second) }
            return Result(Status.COMPLETE, "${up.stem}.png ${size.first}x${size.second} $total bytes")
        }
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
        pending.clear()
        requests.clear()
        lastWarned.clear()
    }

    private fun drop(
        k: String,
        up: Upload,
        username: String,
        why: String,
        now: Long,
    ): Result {
        pending.remove(k, up)
        return refuse(username, up.stem, why, now)
    }

    private fun refuse(
        username: String,
        stem: String?,
        why: String,
        now: Long,
    ): Result {
        // one line per player per 10 s: a client that keeps sending after a refusal does not flood the log
        val k = key(username)
        val last = lastWarned[k]
        if (stem != null || last == null || now - last > 10_000L) {
            lastWarned[k] = now
            logger.warn { "[bugshot] PROBLEM refused a screenshot piece from $username${stem?.let { " ($it)" } ?: ""}: $why" }
        }
        return Result(Status.REFUSED, why)
    }
}
