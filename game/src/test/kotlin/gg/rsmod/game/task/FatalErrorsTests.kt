package gg.rsmod.game.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit T-01: which throwables the game-thread safety nets absorb. Only a fatal JVM error escapes; a
 * plugin's TODO(), a runaway recursion or a class-initialisation failure is logged and the loop goes on.
 */
class FatalErrorsTests {
    @Test
    fun `plugin failures are not fatal`() {
        assertFalse(NotImplementedError("TODO()").isFatalGameError())
        assertFalse(StackOverflowError().isFatalGameError())
        assertFalse(ExceptionInInitializerError("static init").isFatalGameError())
        assertFalse(IllegalStateException().isFatalGameError())
    }

    @Test
    fun `JVM resource errors are fatal`() {
        assertTrue(OutOfMemoryError().isFatalGameError())
        assertTrue(InternalError().isFatalGameError())
        assertFailsWith<OutOfMemoryError> { OutOfMemoryError().rethrowIfFatal() }
    }

    @Test
    fun `a task throwing an Error does not stop the next task or the next cycle`() {
        // The shape of GameService.cycle's task loop.
        val ran = mutableListOf<String>()
        val tasks = listOf<() -> Unit>(
            { throw StackOverflowError() },
            { ran += "after-error" },
            { throw NotImplementedError() },
            { ran += "last" },
        )
        repeat(2) {
            tasks.forEach { task ->
                try {
                    task()
                } catch (e: Throwable) {
                    e.rethrowIfFatal()
                }
            }
        }
        assertEquals(listOf("after-error", "last", "after-error", "last"), ran)
    }
}
