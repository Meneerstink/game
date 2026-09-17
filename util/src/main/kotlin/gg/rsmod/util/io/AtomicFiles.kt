package gg.rsmod.util.io

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Crash-safe file replacement for persistence that must never be left half-written: player saves,
 * the Grand Exchange book, clan/ban registries. The content is written to a sibling `.tmp` file and
 * then moved over the target in one step, so a JVM crash, native OOM or disk error mid-write leaves
 * the previous complete version in place instead of a truncated file.
 */
object AtomicFiles {
    fun writeText(
        file: File,
        text: String,
    ) = write(file.toPath()) { it.write(text) }

    fun write(
        path: Path,
        block: (java.io.BufferedWriter) -> Unit,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.newBufferedWriter(temp).use(block)
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
