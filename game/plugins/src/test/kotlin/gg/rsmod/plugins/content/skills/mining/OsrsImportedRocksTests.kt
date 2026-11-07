package gg.rsmod.plugins.content.skills.mining

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** N462: the Necropolis OSRS rocks deplete to the Jagex-named ROCKS1/ROCKS2 (RuneLite gameval ObjectID); Enakh rocks never deplete. */
class OsrsImportedRocksTests {
    @Test fun `rock1 variants deplete to ROCKS1 and rock2 variants to ROCKS2`() {
        listOf(11362, 11364, 11366, 11374).forEach { assertEquals(11390, OsrsImportedRocks.depletedOsrs(it), "rock $it") }
        listOf(11363, 11365, 11367, 11375).forEach { assertEquals(11391, OsrsImportedRocks.depletedOsrs(it), "rock $it") }
    }

    @Test fun `enakh sandstone and granite never deplete and keep their rock types`() {
        assertNull(OsrsImportedRocks.depletedOsrs(11386)); assertNull(OsrsImportedRocks.depletedOsrs(11387))
        assertEquals(RockType.SANDSTONE, OsrsImportedRocks.TYPES[11386]); assertEquals(RockType.GRANITE, OsrsImportedRocks.TYPES[11387])
        assertEquals(RockType.ADAMANTITE, OsrsImportedRocks.TYPES[11374])
    }
}
