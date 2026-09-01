package gg.rsmod.game.model.entity

import kotlin.test.Test
import kotlin.test.assertEquals

class TransformResolutionTests {
    @Test
    fun missingTransformsKeepOriginalId() {
        assertEquals(42, resolveTransformId(42, null, 5))
        assertEquals(42, resolveTransformId(42, emptyArray(), 5))
    }

    @Test
    fun statesAreClampedToAvailableTransformEntries() {
        val transforms = arrayOf(100, 200, -1)

        assertEquals(100, resolveTransformId(42, transforms, -1))
        assertEquals(200, resolveTransformId(42, transforms, 1))
        assertEquals(-1, resolveTransformId(42, transforms, 99))
    }
}
