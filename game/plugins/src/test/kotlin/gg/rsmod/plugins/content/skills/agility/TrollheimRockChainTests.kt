package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-09-30 (Trollheim, "bugged in this exact position"): the donor-sourced Trollheim chain rocks landed on a single walled-in
 * tile on one side each in this cache. [chainLanding] keeps a donor landing only where it is usable, else crosses the rock by this
 * cache's collision; checked for every placement of the chain in this cache and every side a player can approach it from.
 */
class TrollheimRockChainTests {
    private val world get() = RealMapWorld.world

    private val chain = setOf(3803, 3804, 9303, 9304, 9305, 9306)

    private fun placements(): List<GameObject> =
        (2840..2930).flatMap { x -> (3640..3710).map { z -> Tile(x, z) } }.flatMap { t ->
            world.chunks.get(t, true)!!.getEntities<GameObject>(t, EntityType.STATIC_OBJECT).filter { it.id in chain }
        }

    /** The donor offset sourced_shortcuts.plugin.kts uses for [obj] approached from [from]. */
    private fun donor(
        obj: GameObject,
        from: Tile,
    ): Tile? {
        val (x, z) = obj.tile.x to obj.tile.z
        return when (obj.id) {
            9303 -> Tile(x + if (from.x >= x) -2 else 2, z)
            9304 -> Tile(x, z + if (from.z >= z) -2 else 2)
            3803, 3804 -> Tile(x + if (from.x >= x) -1 else 1, z)
            9305 -> Tile(2895, 3674)
            else -> null
        }
    }

    @Test
    fun `the old donor landings of this cache's chain rocks are walled-in single tiles`() {
        // Measured 2026-10-01: the four landings the owner's climbs could end on.
        listOf(Tile(2857, 3663), Tile(2873, 3662), Tile(2877, 3671), Tile(2895, 3674)).forEach {
            assertEquals("$it", 1, Landing.pocket(world.collision, it).size)
        }
    }

    @Test
    fun `every chain rock lands on usable ground from every side it is approached from`() {
        val rocks = placements()
        assertTrue("chain rocks found: ${rocks.map { "${it.id}@${it.tile.x},${it.tile.z}" }}", rocks.size >= 5)
        val bad = mutableListOf<String>()
        for (rock in rocks) {
            val donorRock = rock.id != 9306
            for (dx in -2..2) for (dz in -2..2) {
                val from = Tile(rock.tile.x + dx, rock.tile.z + dz)
                // Only tiles a player can stand on in open ground, beside the rock.
                if (Landing.pocket(world.collision, from).size < Landing.OPEN_GROUND || !isUsableLanding(world, from)) continue
                val landing = if (donorRock) chainLanding(world, rock, from, donor(rock, from)!!) else crossingLanding(world, rock, from)
                if (landing == null) continue // the climb then refuses ("You can't climb the rocks from here."), the player stays put
                if (!isUsableLanding(world, landing)) bad += "${rock.id}@${rock.tile.x},${rock.tile.z} from ${from.x},${from.z} -> ${landing.x},${landing.z}"
            }
        }
        assertTrue("climbs that land where the player cannot move: $bad", bad.isEmpty())
    }
}
