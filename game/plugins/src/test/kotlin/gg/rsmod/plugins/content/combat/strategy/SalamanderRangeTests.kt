package gg.rsmod.plugins.content.combat.strategy

import java.io.File
import java.nio.file.Paths
import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.SeqSoundProbeTool
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo

/**
 * The four revision-667 salamander weapons share the salamander ranged weapon type.
 * Their ranged path must therefore use the close-range salamander movement rule rather
 * than falling through to the generic seven-tile default.
 */
class SalamanderRangeTests {
    @Test
    fun `all sourced salamander weapons use the close range branch`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        val branch = source.substringAfter("when (weapon?.id) {").substringBefore("in Darts.DARTS")
        assertTrue("Items.ORANGE_SALAMANDER" in branch)
        assertTrue("Items.RED_SALAMANDER" in branch)
        assertTrue("Items.BLACK_SALAMANDER" in branch)
        assertTrue("Items.SWAMP_LIZARD" in branch)
        assertTrue("-> 1" in branch)
    }

    @Test
    fun `salamanders accept swamp tar as direct ammo`() {
        val weapons = listOf(
            Items.ORANGE_SALAMANDER,
            Items.RED_SALAMANDER,
            Items.BLACK_SALAMANDER,
            Items.SWAMP_LIZARD,
        )
        weapons.forEach { weapon ->
            assertTrue(RangedAmmo.isSalamander(weapon))
            assertContentEquals(arrayOf(Items.SWAMP_TAR), RangedAmmo.validAmmo(weapon))
        }
        assertTrue(!RangedAmmo.isSalamander(Items.YEW_SHORTBOW))
    }

    @Test
    fun `local cache binds salamander spotanim to sourced attack sequence`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val data = assertNotNull(library.data(21, 953 ushr 8, 953 and 0xff), "spotanim 953")
            val buf = io.netty.buffer.Unpooled.wrappedBuffer(data)
            var sequence = -1
            while (buf.isReadable) {
                when (val opcode = buf.readUnsignedByte().toInt()) {
                    0 -> break
                    1, 4, 5, 6, 15 -> buf.skipBytes(2)
                    2 -> sequence = buf.readUnsignedShort()
                    7, 8, 14 -> buf.skipBytes(1)
                    9, 10, 11, 12, 13 -> Unit
                    16 -> buf.skipBytes(4)
                    40, 41 -> buf.skipBytes(buf.readUnsignedByte().toInt() * 4)
                    else -> error("unknown spotanim 953 opcode $opcode")
                }
            }
            // The local 667 cache's spotanim 953 owns sequence 5264; the donor's player attack
            // animation is the separate, independently present sequence 5247.
            assertEquals(5264, sequence)
            assertEquals(20, SeqSoundProbeTool.seq(library, 5264)?.frameCount)
            assertEquals(15, SeqSoundProbeTool.seq(library, 5247)?.frameCount)
        } finally {
            library.close()
        }
    }

    @Test
    fun `salamander presentation branches use sourced cache ids`() {
        val combatConfigs = File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText()
        val melee = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MeleeCombatStrategy.kt").readText()
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        val magic = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("Anims.HUMANOID_5247" in combatConfigs)
        assertTrue("Gfx.GFX_953" in ranged)
        assertTrue("RangedCombatStrategy.attack(pawn, target)" in melee)
        assertTrue("1" in magic.substringAfter("override fun getAttackRange"))
        assertTrue("RangedCombatStrategy.canAttack(pawn, target)" in magic)
        assertTrue("RangedCombatStrategy.attack(pawn, target)" in magic)
    }
}
