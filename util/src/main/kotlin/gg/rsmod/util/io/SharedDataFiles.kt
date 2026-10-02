package gg.rsmod.util.io

import java.io.File

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

    private fun scratchRoot() = File(System.getProperty("java.io.tmpdir"), "rsps-conformance-data")

    fun of(path: String): File {
        val real = File(path)
        if (!testServer()) return real
        val scratch = File(scratchRoot(), real.path.replace(File.separatorChar, '/'))
        if (!scratch.exists()) {
            scratch.parentFile?.mkdirs()
            if (real.isFile) real.copyTo(scratch, overwrite = true) else scratch.writeText("")
        }
        return scratch
    }

    /**
     * The file [name] next to [file] (the GE keeps its prices, guide index and history beside `offers.json`). On a test
     * server [file] is a scratch copy, so the sibling is seeded from the real file beside the real original the first time
     * (2026-10-02: a test server's GE started without prices and history). Live, it is simply the file next to [file].
     */
    fun sibling(file: File, name: String): File {
        val sibling = File(file.parentFile ?: File("."), name)
        if (!testServer() || sibling.exists()) return sibling
        val root = scratchRoot().absoluteFile
        if (!sibling.absoluteFile.startsWith(root)) return sibling
        val real = File(sibling.absoluteFile.relativeTo(root).path)
        if (real.isFile) {
            sibling.parentFile?.mkdirs()
            real.copyTo(sibling, overwrite = false)
        }
        return sibling
    }
}
