package gg.rsmod.game

import java.io.File
import java.io.FilePermission
import java.security.Permission
import java.util.jar.JarFile

/**
 * T17 (2026-10-16, VPS): child-JVM probe for [HostPathTests]. Runs the way the VPS does (`-Drsps.root=<dir>
 * -Drsps.data=<dir>/data`, working dir = that root, no `C:\RSPS` workspace) and plays a Linux host on this Windows laptop:
 * a security manager refuses every file access under the laptop workspace (`C:\RSPS` / `C:/RSPS`) that is not the JDK or the
 * class path, and records every other file access. Then it initialises every class of `gg.rsmod.game.tools.importer` - the
 * offline tool objects plugin code uses as layout/constant holders (LootKeyChest -> LootKeyInterfaceImportTool ->
 * OsrsItemImportTool.<clinit> -> OsrsPetRoster.load at every logout on the VPS 2026-10-16) - and reads their static path
 * fields. Output lines: `CLASSES <n>`, `ERROR <class> <throwable chain>`, `ACCESS <class> <action> <path>`,
 * `FIELD <class>.<field> = <value>`.
 */
object HostPathProbe {
    private const val PACKAGE = "gg/rsmod/game/tools/importer/"

    private val laptopRoots = listOf("c:\\rsps", "c:/rsps")

    @Volatile private var current: String = "-"

    private val out = mutableListOf<String>()

    private fun norm(path: String): String = path.replace('\\', '/').lowercase()

    @JvmStatic
    fun main(args: Array<String>) {
        val cwd = System.getProperty("user.dir")
        val allowed =
            (System.getProperty("java.class.path").split(File.pathSeparator) + System.getProperty("java.home"))
                .filter { it.isNotBlank() }
                .map { norm(File(it).absolutePath).trimEnd('/') } +
                // The JDK's own AWT start-up file (java.awt.Toolkit reads it when an image tool object is initialised).
                norm(File(System.getProperty("user.home"), ".accessibility.properties").absolutePath)
        val classes = importerClasses()
        val manager = Recorder(allowed, norm(cwd).trimEnd('/'))
        System.setSecurityManager(manager)
        for (name in classes) {
            current = name
            try {
                val type = Class.forName(name, true, HostPathProbe::class.java.classLoader)
                for (field in type.declaredFields) {
                    if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                    val value =
                        try {
                            field.isAccessible = true
                            field.get(null)
                        } catch (_: Throwable) {
                            null
                        } ?: continue
                    if (value !is CharSequence && value !is Collection<*> && value !is Map<*, *> && value !is File) continue
                    val text = value.toString()
                    if (laptopRoots.any { norm(text).contains(it) }) record("FIELD $name.${field.name} = ${text.take(160)}")
                }
            } catch (t: Throwable) {
                record("ERROR $name ${chain(t)}")
            }
        }
        current = "-"
        System.setSecurityManager(null)
        println("CLASSES ${classes.size}")
        synchronized(out) { out.forEach(::println) }
    }

    private fun record(line: String) {
        synchronized(out) { if (line !in out) out += line }
    }

    private fun chain(t: Throwable): String {
        val parts = mutableListOf<String>()
        var e: Throwable? = t
        while (e != null && parts.size < 6) {
            parts += "${e.javaClass.simpleName}(${e.message?.take(200) ?: ""})"
            e = e.cause
        }
        return parts.joinToString(" <- ")
    }

    private fun importerClasses(): List<String> {
        val names = sortedSetOf<String>()
        // Only the main classes (the build's own tool objects), not test classes that happen to share the package.
        val main = File(RspsPaths::class.java.protectionDomain.codeSource.location.toURI())
        for (file in listOf(main)) {
            if (file.isDirectory) {
                val dir = File(file, PACKAGE)
                if (!dir.isDirectory) continue
                dir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach {
                    names += it.relativeTo(file).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
                }
            } else if (file.isFile && file.name.endsWith(".jar")) {
                JarFile(file).use { jar ->
                    jar.entries().asSequence().filter { it.name.startsWith(PACKAGE) && it.name.endsWith(".class") }
                        .forEach { names += it.name.removeSuffix(".class").replace('/', '.') }
                }
            }
        }
        return names.toList()
    }

    private class Recorder(private val allowed: List<String>, private val cwd: String) : SecurityManager() {
        private val busy = ThreadLocal.withInitial { false }

        override fun checkPermission(perm: Permission) = check(perm)

        override fun checkPermission(
            perm: Permission,
            context: Any?,
        ) = check(perm)

        private fun check(perm: Permission) {
            if (perm !is FilePermission || current == "-" || busy.get()) return
            busy.set(true)
            try {
                val raw = norm(perm.name)
                val path = if (raw.length > 1 && raw[1] == ':' || raw.startsWith("/")) raw else "$cwd/${raw.removePrefix("./")}"
                if (allowed.any { path == it || path.startsWith("$it/") }) return
                val laptop = laptopRoots.any { path.startsWith(it) }
                record("ACCESS $current ${perm.actions} ${perm.name}${if (laptop) " (laptop path - refused)" else ""}")
                if (laptop) throw SecurityException("no such file on this host: ${perm.name}")
            } finally {
                busy.set(false)
            }
        }
    }
}
