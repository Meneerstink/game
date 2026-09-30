package gg.rsmod.util.io

import java.io.File

/**
 * World-wide registries that live next to the player saves (friends chat settings, clans). A headless conformance server
 * (`-Drsps.conformance=<domain>`, tools\conformance\Run-Conformance.ps1) runs against the owner's `data` folder and "saves nothing",
 * so it must never write these either: until 2026-10-01 a `conform systems social` run founded test clans in the real
 * `data/clans.txt` and cleared the owner's friends chat prefix in `data/friends_chat.json`. In conformance mode the file is a
 * private copy in the temp folder, seeded from the real one so the suite still sees realistic data.
 */
object SharedDataFiles {
    fun of(path: String): File {
        val real = File(path)
        if (System.getProperty("rsps.conformance").isNullOrBlank()) return real
        val scratch = File(System.getProperty("java.io.tmpdir"), "rsps-conformance-data/" + real.path.replace(File.separatorChar, '/'))
        if (!scratch.exists()) {
            scratch.parentFile?.mkdirs()
            if (real.isFile) real.copyTo(scratch, overwrite = true) else scratch.writeText("")
        }
        return scratch
    }
}
