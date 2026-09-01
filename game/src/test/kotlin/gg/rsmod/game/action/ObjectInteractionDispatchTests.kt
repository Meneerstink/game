package gg.rsmod.game.action

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectInteractionDispatchTests {
    @Test
    fun fallsBackToOriginalIdWhenTransformedIdIsUnhandled() {
        val calls = mutableListOf<Int>()

        val handled = executeWithObjectIdFallback(100, 200) { id ->
            calls += id
            id == 100
        }

        assertTrue(handled)
        assertEquals(listOf(200, 100), calls)
    }

    @Test
    fun transformedIdWinsAndSameIdIsNotExecutedTwice() {
        val transformedCalls = mutableListOf<Int>()
        val transformedHandled = executeWithObjectIdFallback(100, 200) { id ->
            transformedCalls += id
            id == 200
        }

        assertTrue(transformedHandled)
        assertEquals(listOf(200), transformedCalls)

        val sameIdCalls = mutableListOf<Int>()
        val sameIdHandled = executeWithObjectIdFallback(100, 100) { id ->
            sameIdCalls += id
            true
        }

        assertTrue(sameIdHandled)
        assertEquals(listOf(100), sameIdCalls)

        val sameIdUnhandled = executeWithObjectIdFallback(100, 100) { false }
        assertTrue(!sameIdUnhandled)
    }
}