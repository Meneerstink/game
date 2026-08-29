package gg.rsmod.game.service.serializer.json

import com.google.gson.Gson
import com.google.gson.JsonParseException
import mu.KLogging
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Durable on-disk storage for JSON player saves.
 *
 * A save is first written and validated in the save directory. The previous
 * valid primary save is retained as a backup before the temporary file
 * atomically replaces it. This keeps interrupted writes from destroying the
 * last usable account state.
 */
class JsonPlayerSaveStore(
    root: Path,
    private val gson: Gson,
) {
    private val root = root.toAbsolutePath().normalize()

    init {
        Files.createDirectories(this.root)
    }

    fun exists(username: String): Boolean = Files.exists(primaryPath(username)) || Files.exists(backupPath(username))

    fun read(username: String): JsonPlayerSaveData {
        val primary = primaryPath(username)
        val backup = backupPath(username)

        return try {
            readValid(primary)
        } catch (primaryFailure: Exception) {
            if (primaryFailure is UnsupportedPlayerSaveSchemaException) {
                throw primaryFailure
            }
            if (!Files.exists(backup)) {
                throw primaryFailure
            }
            try {
                readValid(backup).also {
                    logger.warn { "Recovered player save from backup: ${primary.fileName}" }
                }
            } catch (backupFailure: Exception) {
                backupFailure.addSuppressed(primaryFailure)
                throw backupFailure
            }
        }
    }

    fun write(username: String, data: JsonPlayerSaveData) {
        val primary = primaryPath(username)
        val backup = backupPath(username)
        val temporary = Files.createTempFile(root, ".${safeName(username)}.", ".tmp")

        try {
            writeAndSync(temporary, data.copy(schemaVersion = JsonPlayerSaveData.CURRENT_SCHEMA_VERSION))
            readValid(temporary)

            if (Files.exists(primary) && isValid(primary)) {
                val backupTemporary = Files.createTempFile(root, ".${safeName(username)}.", ".bak.tmp")
                try {
                    Files.copy(primary, backupTemporary, StandardCopyOption.REPLACE_EXISTING)
                    moveReplacing(backupTemporary, backup)
                } finally {
                    Files.deleteIfExists(backupTemporary)
                }
            }

            moveReplacing(temporary, primary)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    internal fun primaryPath(username: String): Path = resolveAccountPath(safeName(username))

    internal fun backupPath(username: String): Path = resolveAccountPath("${safeName(username)}.bak")

    private fun readValid(file: Path): JsonPlayerSaveData {
        if (!Files.isRegularFile(file)) {
            throw JsonParseException("Player save does not exist: ${file.fileName}")
        }
        val data = Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
            gson.fromJson(reader, JsonPlayerSaveData::class.java)
                ?: throw JsonParseException("Player save is empty: ${file.fileName}")
        }
        return migrate(data)
    }

    private fun migrate(data: JsonPlayerSaveData): JsonPlayerSaveData =
        when (data.schemaVersion) {
            JsonPlayerSaveData.LEGACY_SCHEMA_VERSION ->
                data.copy(schemaVersion = JsonPlayerSaveData.CURRENT_SCHEMA_VERSION)
            JsonPlayerSaveData.CURRENT_SCHEMA_VERSION -> data
            else -> throw UnsupportedPlayerSaveSchemaException(data.schemaVersion)
        }

    private fun isValid(file: Path): Boolean =
        try {
            readValid(file)
            true
        } catch (_: Exception) {
            false
        }

    private fun writeAndSync(file: Path, data: JsonPlayerSaveData) {
        FileChannel.open(
            file,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { channel ->
            val json = gson.toJson(data)
            val bytes = StandardCharsets.UTF_8.encode(json)
            while (bytes.hasRemaining()) {
                channel.write(bytes)
            }
            channel.force(true)
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun safeName(username: String): String {
        val normalized = username.lowercase()
        require(USERNAME_PATTERN.matches(normalized)) { "Unsafe player save name." }
        return normalized
    }

    private fun resolveAccountPath(fileName: String): Path {
        val resolved = root.resolve(fileName).normalize()
        require(resolved.parent == root) { "Player save path escaped the save directory." }
        return resolved
    }

    companion object : KLogging() {
        private val USERNAME_PATTERN = Regex("^(?=.{1,12}$)[a-z0-9_]+(?: [a-z0-9_]+)*$")
    }
}

private class UnsupportedPlayerSaveSchemaException(version: Int) :
    JsonParseException("Unsupported player save schema: $version")
