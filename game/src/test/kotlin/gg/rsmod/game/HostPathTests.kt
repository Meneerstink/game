package gg.rsmod.game

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * T17 (2026-10-16): the same build runs on the laptop and on the VPS (owner 2026-10-10); the VPS passes `-Drsps.root` /
 * `-Drsps.data` ([RspsPaths]) and has no `C:\RSPS`. On the VPS every logout threw ExceptionInInitializerError (LootKeyChest ->
 * LootKeyInterfaceImportTool -> OsrsItemImportTool.<clinit> -> OsrsPetRoster.load "C:\RSPS\...\osrs_pets.tsv") and the player
 * stayed online. A smoke run on the laptop could not catch it (C:\RSPS exists there), so these tests play the Linux host:
 *
 *  - [noImporterClassInitialiserOpensAFileOrAPathOutsideRspsPaths]: a child JVM started like the VPS world
 *    (`-Drsps.root=<tmp>`, working dir <tmp>) refuses every laptop-workspace file access and initialises EVERY class of
 *    `gg.rsmod.game.tools.importer` (the whole class of offline tool objects that plugin code reaches as layout holders):
 *    no initialiser may fail, open a file, or hold a laptop path in a static field.
 *  - [noDriveLetterPathLiteralInServerSources]: no `"C:/..."` / `"C:\\..."` string literal in the server's main sources
 *    (game, plugins, net, util); the one allowed default is in RspsPaths.kt (donor: Void game.properties `storage.data`
 *    with every path derived from it; Darkan Settings.java relative to the working dir).
 */
class HostPathTests {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun noImporterClassInitialiserOpensAFileOrAPathOutsideRspsPaths() {
        val root = tmp.newFolder("vps-world")
        File(root, "data").mkdirs()
        val java = File(System.getProperty("java.home"), "bin/java").path
        val classPath = System.getProperty("java.class.path")
        assertTrue("test class path must hold the game classes: $classPath", classPath.contains("classes"))
        val process =
            ProcessBuilder(
                java, "-Xmx1g", "-Drsps.root=${root.path}", "-Drsps.data=${File(root, "data").path}",
                "-cp", classPath, HostPathProbe::class.java.name,
            ).directory(root).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }
        assertTrue("probe did not finish in 5 minutes", process.waitFor(5, TimeUnit.MINUTES))
        reader.join(10_000)
        val lines = output.lines()
        println(output)
        val classes = lines.firstOrNull { it.startsWith("CLASSES ") }?.removePrefix("CLASSES ")?.trim()?.toIntOrNull() ?: 0
        assertTrue("probe found only $classes importer classes (exit ${process.exitValue()}):\n$output", classes >= 100)
        val problems = lines.filter { it.startsWith("ERROR ") || it.startsWith("ACCESS ") || it.startsWith("FIELD ") }
        assertTrue(
            "importer class initialisers must not open files or keep laptop paths on a host without C:\\RSPS " +
                "(${problems.size} problem(s)):\n" + problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun noDriveLetterPathLiteralInServerSources() {
        val module = File(".").absoluteFile
        val roots = listOf("src/main", "plugins/src/main", "../net/src/main", "../util/src/main").map { File(module, it) }
        assertTrue("source roots not found from ${module.path}", roots.take(2).all { it.isDirectory })
        val literal = Regex("\"[A-Za-z]:(/|\\\\\\\\)")
        val hits = mutableListOf<String>()
        for (dir in roots.filter { it.isDirectory }) {
            dir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "kts" || it.extension == "java") }
                .filter { it.name != "RspsPaths.kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { i, line ->
                        val code = line.trim()
                        if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) return@forEachIndexed
                        if (literal.containsMatchIn(code.substringBefore(" // "))) hits += "${file.relativeTo(module).path}:${i + 1}: $code"
                    }
                }
        }
        assertTrue(
            "laptop path literals in runtime sources - use RspsPaths.root(..)/RspsPaths.data(..) (${hits.size}):\n" +
                hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }
}
