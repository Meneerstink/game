package gg.rsmod.game.service.serializer.json

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import gg.rsmod.game.model.Gender
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPlayerSaveStoreTests {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun `writes and reads current schema`() {
        val store = createStore()
        val expected = saveData(displayName = "First save")

        store.write(USERNAME, expected)

        val actual = store.read(USERNAME)
        assertEquals(JsonPlayerSaveData.CURRENT_SCHEMA_VERSION, actual.schemaVersion)
        assertEquals(expected.copy(schemaVersion = JsonPlayerSaveData.CURRENT_SCHEMA_VERSION), actual)
    }

    @Test
    fun `retains previous valid save as backup`() {
        val store = createStore()
        val first = saveData(displayName = "First save")
        val second = saveData(displayName = "Second save")

        store.write(USERNAME, first)
        store.write(USERNAME, second)

        assertTrue(Files.exists(store.backupPath(USERNAME)))
        assertEquals("Second save", store.read(USERNAME).displayName)
    }

    @Test
    fun `recovers from backup when primary is corrupt`() {
        val store = createStore()
        val first = saveData(displayName = "Recover me")
        val second = saveData(displayName = "Latest")

        store.write(USERNAME, first)
        store.write(USERNAME, second)
        Files.write(store.primaryPath(USERNAME), "{broken".toByteArray(StandardCharsets.UTF_8))

        assertEquals("Recover me", store.read(USERNAME).displayName)
    }

    @Test
    fun `migrates legacy unversioned save`() {
        val store = createStore()
        val legacy = gson.toJsonTree(saveData(displayName = "Legacy")).asJsonObject
        legacy.remove("schemaVersion")
        Files.write(store.primaryPath(USERNAME), gson.toJson(legacy).toByteArray(StandardCharsets.UTF_8))

        val migrated = store.read(USERNAME)

        assertEquals(JsonPlayerSaveData.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("Legacy", migrated.displayName)
    }

    @Test
    fun `rejects unsupported future schema`() {
        val store = createStore()
        val future = saveData(displayName = "Future").copy(schemaVersion = 99)
        Files.write(store.primaryPath(USERNAME), gson.toJson(future).toByteArray(StandardCharsets.UTF_8))

        assertFailsWith<JsonParseException> {
            store.read(USERNAME)
        }
    }

    @Test
    fun `rejects path traversal account names`() {
        val store = createStore()

        assertFailsWith<IllegalArgumentException> {
            store.exists("../outside")
        }
    }

    private fun createStore(): JsonPlayerSaveStore =
        JsonPlayerSaveStore(temporaryFolder.newFolder("saves").toPath(), gson)

    private fun saveData(displayName: String): JsonPlayerSaveData =
        JsonPlayerSaveData(
            schemaVersion = JsonPlayerSaveData.CURRENT_SCHEMA_VERSION,
            username = USERNAME,
            displayName = displayName,
            passwordHash = "hash",
            displayMode = 0,
            privilege = 0,
            runEnergy = 100.0,
            x = 3205,
            z = 3240,
            height = 0,
            previousXteas = intArrayOf(1, 2, 3, 4),
            appearance =
                JsonPlayerSerializer.PersistentAppearance(
                    gender = Gender.MALE.id,
                    looks = intArrayOf(0, 1, 2),
                    colors = intArrayOf(0, 1, 2),
                ),
            attributes = emptyMap(),
            timers = emptyList(),
            skills = emptyList(),
            itemContainers = emptyList(),
            varps = emptyList(),
            friends = mutableListOf(),
            ignoredPlayers = mutableListOf(),
            privateFilterSetting = 0,
            publicFilterSetting = 0,
            tradeFilterSetting = 0,
        )

    companion object {
        private const val USERNAME = "audit user"
    }
}
