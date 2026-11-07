package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.Route
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.model.region.ChunkSet
import io.mockk.*

/** Independent OSRS2686 source geometry. Does NOT prove the imported local cache or the client render/collision. */
internal class ToaPuzzleSourceCollision(name: String, private val base: Tile) {
    private val world = mockk<World>(relaxed = true)
    private val definitions = mockk<DefinitionSet>(relaxed = true)
    val defs = hashMapOf<Int, ObjectDef>()
    private val statics = mutableListOf<GameObject>()
    private val terrain = mutableListOf<Tile>()
    lateinit var collision: CollisionManager
        private set

    init {
        every { world.definitions } returns definitions
        every { world.collision } answers { collision }
        every { definitions.get(ObjectDef::class.java, any()) } answers { defs.getValue(secondArg()) }
        val rows = checkNotNull(javaClass.getResourceAsStream("/toa/$name-collision.tsv"))
            .bufferedReader().use { it.readLines() }.filterNot { it.startsWith("#") || it.isBlank() }
        rows.forEach { row ->
            val fields = row.split('\t')
            val v = fields.drop(1).map(String::toInt)
            when (fields[0]) {
                "def" -> defs[v[0]] = ObjectDef(v[0]).also {
                    it.width = v[1]; it.length = v[2]; it.blockwalk = v[3]; it.blockrange = v[4] == 1
                }
                "loc" -> statics += DynamicObject(v[0], v[3], v[4], base.transform(v[1], v[2]))
                "terrain" -> for (z in v[1]..v[2]) terrain += base.transform(v[0], z)
            }
        }
        refresh()
    }

    /** Dynamic objects replace only their layer, just as World.spawn does. Inputs remain in OSRS source space. */
    fun refresh(dynamic: Collection<GameObject> = emptyList(), removed: Set<Pair<Tile, Int>> = emptySet()) {
        fun layer(type: Int) = when (type) { in 0..3 -> 0; in 4..8 -> 1; 22 -> 3; else -> 2 }
        collision = CollisionManager(ChunkSet(world))
        val replacements = removed + dynamic.map { it.tile to layer(it.type) }
        (statics.filterNot { (it.tile to layer(it.type)) in replacements } + dynamic).forEach {
            collision.applyCollision(definitions, it, CollisionUpdate.Type.ADD)
        }
        val blocked = CollisionUpdate.Builder().apply {
            setType(CollisionUpdate.Type.ADD)
            terrain.forEach { putTile(it, false, *Direction.NESW) }
            //Tiles outside this captured64x64 square are unknown, not an open route around its walls.
            for (offset in -1..64) {
                putTile(base.transform(offset, -1), false, *Direction.NESW)
                putTile(base.transform(offset, 64), false, *Direction.NESW)
                putTile(base.transform(-1, offset), false, *Direction.NESW)
                putTile(base.transform(64, offset), false, *Direction.NESW)
            }
        }.build()
        collision.applyUpdate(blocked)
        //Exhaustive layouts rebuild thousands of loc flags; only their collision matters, not retained mock invocation logs.
        clearMocks(world, definitions, answers = false)
    }

    fun route(from: Tile, to: Tile, width: Int = 1, length: Int = 1, reach: Int? = null,
              oldRadius: Boolean = false, stepOn: Boolean = false, avoid: Set<Tile> = emptySet()): Route =
        BFSPathFindingStrategy(collision, playerTiles = avoid,
            maxDistance = if (oldRadius) 20 else BFSPathFindingStrategy.PLAYER_MAX_DISTANCE,
            maxNodes = BFSPathFindingStrategy.PLAYER_MAX_NODES).calculateRoute(
            PathRequest.Builder().setPoints(from, to).setSourceSize(1, 1)
                .setTargetSize(if (stepOn) 0 else width, if (stepOn) 0 else length)
                .setProjectilePath(reach != null).apply {
                    if (reach != null) setTouchRadius(reach)
                    if (stepOn) setTouchRadius(0) else { clipDiagonalTiles(); clipOverlapTiles() }
                }.clipPathNodes(node = true, link = true).build())
}
