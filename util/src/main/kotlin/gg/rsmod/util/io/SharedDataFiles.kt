package gg.rsmod.util.io

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * World-wide registries that live next to the player saves (friends chat settings, clans). A headless conformance server
 * (`-Drsps.conformance=<domain>`, tools\conformance\Run-Conformance.ps1) runs against the owner's `data` folder and "saves nothing",
 * so it must never write these either: until 2026-10-01 a `conform systems social` run founded test clans in the real
 * `data/clans.txt` and cleared the owner's friends chat prefix in `data/friends_chat.json`. In conformance mode the file is a
 * private copy in the temp folder, seeded from the real one so the suite still sees realistic data.
 *
 * 2026-10-01 (ROOT_CAUSES #109, whole class): the breach harness (`-Drsps.breachConformance`, `-Drsps.breachSim`) is a headless test
 * server too, and the Deadman emblem / valid-kill state, the emblem log and the website player count were written straight into
 * `data` - a `conform deadman death` run left its bots in `data/deadman_emblem_pairs.txt`. Every test server isolates, and every
 * world-state writer goes through [of].
 *
 * 2026-10-02 (owner): the agent's live test server (`-Drsps.testServer=true`, tools\live\Start-TestServer.ps1, its own ports
 * and temp folder) runs beside the owner's server with real clients; it isolates the same way and never writes a player save.
 */
object SharedDataFiles {
    /** The system properties that mark a test server (headless harnesses and the agent's live test server). */
    val TEST_SERVER_PROPERTIES = listOf("rsps.conformance", "rsps.breachConformance", "rsps.breachSim", "rsps.testServer")

    fun testServer(): Boolean = TEST_SERVER_PROPERTIES.any { !System.getProperty(it).isNullOrBlank() }

    private val originParents = mutableMapOf<String, File>()
    private val scratchParents = mutableMapOf<Pair<String, String>, File>()

    private fun scratchRoot(): File {
        val temporary = File(System.getProperty("java.io.tmpdir")).canonicalFile
        val root = File(temporary, "rsps-conformance-data").canonicalFile
        check(root != temporary && root.startsWith(temporary)) { "PROBLEM test scratch root escapes temporary directory" }
        return root
    }

    /** Preserve the existing JVM signature for already compiled callers. */
    fun of(path: String): File = of(path, createIfMissing = true)

    /** A missing diagnostic/config file may need its normal writer, rather than an empty placeholder. */
    @Synchronized
    fun of(path: String, createIfMissing: Boolean): File {
        val real = File(path)
        if (!testServer()) return real
        val origin = real.canonicalFile
        val parent = checkNotNull(origin.parentFile) { "PROBLEM test output has no parent" }
        val identity = if (File.separatorChar == '\\') parent.path.lowercase(Locale.ROOT) else parent.path
        val root = scratchRoot()
        val privateParent = scratchParents.getOrPut(root.path to identity) {
            val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            File(root, digest)
        }
        val scratch = File(privateParent, origin.name).canonicalFile
        check(scratch != root && scratch.startsWith(root)) { "PROBLEM test output escapes scratch directory" }
        originParents[checkNotNull(scratch.parentFile).path] = parent
        if (!scratch.exists()) {
            if (origin.isFile || createIfMissing) {
                scratch.parentFile?.mkdirs()
                if (origin.isFile) origin.copyTo(scratch, overwrite = false) else scratch.writeText("")
            }
        }
        return scratch
    }

    /**
     * The file [name] next to [file] (the GE keeps its prices, guide index and history beside `offers.json`). On a test
     * server [file] is a scratch copy, so the sibling is seeded from the real file beside the real original the first time
     * (2026-10-02: a test server's GE started without prices and history). Live, it is simply the file next to [file].
     */
    @Synchronized
    fun sibling(file: File, name: String): File {
        val sibling = File(file.parentFile ?: File("."), name)
        if (!testServer()) return sibling
        val root = scratchRoot()
        val parent = (file.parentFile ?: File(".")).absoluteFile.normalize()
        // Injected non-scratch files retain their existing behaviour in test fixtures.
        if (!parent.startsWith(root)) return sibling
        check(name == File(name).name && name != "." && name != "..") { "PROBLEM scratch sibling must be a filename" }
        val resolved = sibling.canonicalFile
        check(resolved != root && resolved.startsWith(root)) { "PROBLEM test sibling escapes scratch directory" }
        if (resolved.exists()) return resolved
        val origin = originParents[parent.canonicalPath] ?: return resolved
        val real = File(origin, name)
        if (real.isFile) {
            resolved.parentFile?.mkdirs()
            real.copyTo(resolved, overwrite = false)
        }
        return resolved
    }
}
