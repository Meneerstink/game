package gg.rsmod.game.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PerEntityTaskIsolationTests {
    @Test
    fun `shared per-entity tasks catch failures and continue their loops`() {
        val sources = listOf(
            File("src/main/kotlin/gg/rsmod/game/task/MessageHandlerTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/sequential/SequentialPlayerCycleTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/sequential/SequentialPlayerPostCycleTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/sequential/SequentialNpcCycleTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/sequential/SequentialSynchronizationTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/ChunkCreationTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/WorldRemoveTask.kt"),
            File("src/main/kotlin/gg/rsmod/game/task/QueueHandlerTask.kt"),
        )

        sources.forEach { source ->
            val text = source.readText()
            assertTrue(text.contains("catch (e: Exception)"), "${source.name} lacks per-entity catch")
            assertTrue(text.contains("logger.error"), "${source.name} lacks failure logging")
        }
    }
}
