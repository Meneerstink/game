package gg.rsmod.plugins.content.objs

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner priority #1 / audit 2026-10-04 section 5: the Abyss outer ring is crossed through the multiloc parents 7142-7153 the map
 * places (the old handlers bound the never-sent child ids). Every Void position has a placed parent whose transforms include an
 * obstacle, and every landing is open inner-ring ground.
 */
class AbyssObstaclesMapTests {
    private val world get() = RealMapWorld.world

    @Test
    fun `every Void obstacle position holds a placed multiloc parent and lands on open ground`() {
        val kinds = setOf(7158, 7161, 7164, 7165, 7168, 7154)
        AbyssObstacles.POSITIONS.forEach { (tile, tiles) ->
            val t = Tile(tile.x, tile.z, 0)
            val parent = world.chunks.get(t, true)!!.getEntities<GameObject>(t, EntityType.STATIC_OBJECT).firstOrNull { it.id in 7142..7153 }
            assertTrue("parent placed at $t", parent != null)
            val def = world.definitions.get(ObjectDef::class.java, parent!!.id)
            assertTrue("${parent.id} at $t transforms into an obstacle: ${def.transforms?.toList()}", def.transforms?.any { it in kinds } == true)
            assertTrue("landing ${tiles.second} open", Landing.pocket(world.collision, tiles.second).size >= Landing.OPEN_GROUND)
        }
    }
}
