package gg.rsmod.game.model.entity

import com.google.common.base.MoreObjects
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.sync.block.UpdateBlockType

/**
 * @author Tom <rspsmods@gmail.com>
 */
class Npc private constructor(
    val id: Int,
    world: World,
    /**
     * The tile this npc respawns on and roams around. Mutable so an npc that is legitimately
     * re-posted at runtime (a Deadman guard teleported onto an intruder, then left to patrol where
     * the fight ended) roams and returns to its new post instead of the tile it was first created on.
     */
    var spawnTile: Tile,
) : Pawn(world) {
    /**
     * Route with the same breadth-first path finder players use instead of the axis-aligned
     * [gg.rsmod.game.model.path.strategy.SimplePathFindingStrategy] every npc gets by default.
     * The simple strategy walks straight at its target and stops at the first wall, which is why
     * an npc "forgets" a target standing behind a fence or counter; npcs that must genuinely
     * chase a target around obstacles (the Deadman guards) opt in here.
     */
    var smartPathfinding = false

    constructor(id: Int, tile: Tile, world: World) : this(id, world, spawnTile = Tile(tile)) {
        this.tile = tile
    }

    constructor(owner: Player, id: Int, tile: Tile, world: World) : this(id, world, spawnTile = Tile(tile)) {
        this.tile = tile
        this.owner = owner
    }

    /**
     * This flag indicates whether or not this npc's AI should be processed.
     *
     * As there's a small chance that most npcs will be in the viewport of a real
     * player, we don't really need to process a lot of the logic that comes with
     * the AI.
     */
    private var active = false

    /**
     * The owner of an npc will be the only [Player] who can view this npc.
     * If the owner is no longer online, this npc will be removed from the world.
     *
     * @see [gg.rsmod.game.task.WorldRemoveTask]
     */
    var owner: Player? = null

    /**
     * R07.2: [owner] alone makes an npc PRIVATE (only the owner can see it - see
     * [gg.rsmod.game.sync.task.NpcSynchronizationTask.shouldAdd]), which is correct for
     * instance-scoped/personal spawns but wrong for a real Summoning familiar, which real RS
     * shows to every nearby player. Setting this to true keeps [owner]'s existing
     * logout-cleanup behaviour ([gg.rsmod.game.task.WorldRemoveTask]) while overriding the
     * visibility restriction, so a familiar is visible to and targetable by everyone permitted
     * to see it, not just its owner.
     */
    var publicOwner: Boolean = false

    /**
     * Lets this npc step onto a tile another [Pawn] is standing on.
     *
     * [gg.rsmod.game.model.MovementQueue.cycle] normally reacts to that by clearing an npc's whole
     * movement queue and refusing to move at all for the cycle, which is right for ordinary
     * wandering/chasing npcs but wrong for a Summoning familiar: a familiar walks in its owner's
     * footsteps a single tile behind, so it constantly targets tiles that another player or npc
     * happens to be standing on, and every one of those cycles it would simply stop dead and fall
     * another tile behind. Real familiars are not blocked by other creatures - only by scenery.
     * Wall/scenery collision is unaffected; that is the separate `canTraverse` check above.
     */
    var ignoresEntityCollision: Boolean = false

    /**
     * This flag indicates whether or not this npc will respawn after death.
     *
     * [World.setNpcDefaults] recomputes this from the npc's [NpcCombatDef] every time it is
     * (re)spawned, which clobbers a value set on a freshly-constructed npc before it is passed to
     * [World.spawn]. Callers that need to force a one-off, non-respawning spawn (e.g. Nex, Barrows
     * brothers) must set [respawnOverride] instead - see its kdoc.
     */
    var respawns = false

    /**
     * Forces [respawns] to a fixed value on every [World.setNpcDefaults] call instead of letting
     * it be derived from the npc's [NpcCombatDef.respawnDelay]. Set this - not [respawns] directly
     * - before calling [World.spawn] on a freshly-constructed npc that must not respawn (or must
     * always respawn) regardless of what its combat def says.
     */
    var respawnOverride: Boolean? = null

    /**
     * The radius from [spawnTile], in tiles, which the npc can randomly walk.
     */
    var walkRadius = 0

    /**
     * The radius from [spawnTile], in tiles, which the npc can randomly walk.
     */
    var followRadius = 0

    /**
     * The current hitpoints the npc has.
     */
    private var hitpoints = 10

    /**
     * The [NpcCombatDef] assigned to our npc. This can change at any point to
     * another combat definition, for example if we want to transmogify the npc,
     * it may want to use a different [NpcCombatDef].
     */
    var combatDef: NpcCombatDef = NpcCombatDef.DEFAULT

    /**
     * The [CombatClass] the npc will use on its next attack.
     */
    var combatClass = CombatClass.MELEE

    /**
     * The [WeaponStyle] the npc will use on its next attack.
     */
    var weaponStyle = WeaponStyle.CONTROLLED

    /**
     * The [Stats] for this npc.
     */
    var stats = Stats(world.gameContext.npcStatCount)

    /**
     * Check if the npc will be aggressive towards the parameter player.
     */
    var aggroCheck: ((Npc, Player) -> Boolean)? = null

    /**
     * Shows this npc at [level] instead of the combat level baked into the client's own
     * `NPCType`, for every player who can see it.
     *
     * This is the only lever there is. In this revision the combat level a client draws beside an
     * npc's name comes from its cached `NPCType` (config opcode 95), and the server never sends
     * npc definitions - so an npc whose cache entry says 0 shows no level at all no matter what
     * the server believes about it. The one exception is the `COMBAT_LEVEL` extended-info block,
     * which `NPCList` applies over the cached value; that is what this writes.
     *
     * Pass [UpdateBlockBuffer.CACHE_COMBAT_LEVEL] to hand the decision back to the cache.
     */
    fun setCombatLevel(level: Int) {
        blockBuffer.combatLevel = level
        addBlock(UpdateBlockType.COMBAT_LEVEL)
    }

    /**
     * Gets the [NpcDef] corresponding to our [id].
     */
    val def: NpcDef = world.definitions.get(NpcDef::class.java, id)

    init {
        // An npc whose cache NPCType carries no combat level (opcode 95 absent, e.g. every OSRS
        // import such as Skully/Perdu/Mandrith) decodes as -1 in the client's NPCType too, and the
        // client's MiniMenu only suppresses the "(level N)" suffix for exactly 0 - so such an npc
        // showed a nonsense level. Publish 0 through the COMBAT_LEVEL block (sent with every add
        // update, see NpcUpdateBlockSegment) so the client shows no level, as the cache intends.
        if (def.combatLevel < 0) {
            blockBuffer.combatLevel = 0
        }
    }

    /**
     * A name given to this one npc by a world edit (null: none). Setting it also sends it to the clients.
     */
    var nameOverride: String? = null
        set(value) {
            field = value
            refreshName()
        }

    /**
     * Getter property for our npc name: this npc's own override, then its type's override, then the cache name.
     */
    val name: String
        get() = nameOverride ?: typeNames[id] ?: def.name

    /** Re-sends [name] to every client that sees this npc (the cache name when the overrides are gone). */
    fun refreshName() {
        addBlock(UpdateBlockType.NAME)
    }

    /**
     * If the npc is a "static" npc, meaning
     * they should not face the player on interaction
     */
    var static = false

    /**
     * Getter property for a set of any species that our npc may be categorised
     * as.
     */
    val species: Set<Any>
        get() = combatDef.species

    override val entityType: EntityType = EntityType.NPC

    override fun isRunning(): Boolean = false

    override fun getSize(): Int = world.definitions.get(NpcDef::class.java, id).size

    override fun getCurrentLifepoints(): Int = hitpoints

    override fun getMaximumLifepoints(): Int = combatDef.lifepoints

    public fun nearestTile(
        otherTile: Tile
    ): Tile {
        val nearestX = otherTile.x.coerceIn(tile.x..tile.x + getSize())
        val nearestZ = otherTile.z.coerceIn(tile.z..tile.z + getSize())

        return Tile(nearestX, nearestZ, tile.height)
    }

    override fun setCurrentLifepoints(level: Int) {
        this.hitpoints = level
    }

    override fun addBlock(block: UpdateBlockType) {
        val bits = world.npcUpdateBlocks.updateBlocks[block]!!
        blockBuffer.addBit(bits.bit)
    }

    override fun hasBlock(block: UpdateBlockType): Boolean {
        val bits = world.npcUpdateBlocks.updateBlocks[block]!!
        return blockBuffer.hasBit(bits.bit)
    }

    override fun cycle() {
        if (timers.isNotEmpty) {
            timerCycle()
        }
        hitsCycle()
        if (attr.has(FACING_PAWN_ATTR) && !attr.has(COMBAT_TARGET_FOCUS_ATTR) && attr[gg.rsmod.game.model.attr.HOLD_FACING_ATTR] != true) {
            val target = attr[FACING_PAWN_ATTR]?.get() ?: return
            if (!tile.isWithinRadius(target.tile, 1)) {
                resetFacePawn()
            }
        }
    }

    /**
     * This method will get the "visually correct" npc id for this npc from
     * [player]'s view point.
     *
     * Npcs can change their appearance for each player depending on their
     * [NpcDef.transforms] and [NpcDef.varp]/[NpcDef.varbit].
     */
    fun getTransform(player: Player): Int {
        val transforms = def.transforms ?: return id

        if (def.varbit != -1) {
            val varbitDef = world.definitions.get(VarbitDef::class.java, def.varbit)
            val state = player.varps.getBit(varbitDef.varp, varbitDef.startBit, varbitDef.endBit)
            return resolveTransformId(id, transforms, state)
        }

        if (def.varp != -1) {
            val state = player.varps.getState(def.varp)
            return resolveTransformId(id, transforms, state)
        }

        return id
    }

    /**
     * @see [Npc.active]
     */
    fun setActive(active: Boolean) {
        this.active = active
    }

    /**
     * @see [Npc.active]
     */
    fun isActive(): Boolean = active

    /**
     * Verifies if the npc is currently spawned in the world.
     */
    fun isSpawned(): Boolean = index > 0

    override fun toString(): String =
        MoreObjects
            .toStringHelper(
                this,
            ).add("id", id)
            .add("name", name)
            .add("index", index)
            .add("active", active)
            .toString()

    companion object {
        internal const val RESET_PAWN_FACE_DELAY = 25

        /** Names given to every npc of a type by a world edit; callers refresh the live npcs of that type. */
        val typeNames = java.util.concurrent.ConcurrentHashMap<Int, String>()
    }

    /**
     * @param nStats the max amount of stats an npc has.
     */
    class Stats(
        val nStats: Int,
    ) {
        private val currentLevels = Array(nStats) { 1 }

        private val maxLevels = Array(nStats) { 1 }

        fun getCurrentLevel(skill: Int): Int = currentLevels[skill]

        fun getMaxLevel(skill: Int): Int = maxLevels[skill]

        fun setCurrentLevel(
            skill: Int,
            level: Int,
        ) {
            currentLevels[skill] = level
        }

        fun setMaxLevel(
            skill: Int,
            level: Int,
        ) {
            maxLevels[skill] = level
        }

        /**
         * Alters the current level of the skill by adding [value] onto it.
         *
         * @param skill the skill level to alter.
         *
         * @param value the value which to add onto the current skill level.
         * This value can be negative to decrement the level.
         *
         * @param capValue the amount of levels which can be surpass the max
         * level in the skill. For example, if this value is set to [3] on a
         * skill that has is [99], that means that the level can be altered
         * from [99] to [102].
         */
        fun alterCurrentLevel(
            skill: Int,
            value: Int,
            capValue: Int = 0,
        ) {
            check(capValue == 0 || capValue < 0 && value < 0 || capValue > 0 && value >= 0) {
                "Cap value and alter value must always be the same signum (+ or -)."
            }
            val altered =
                when {
                    capValue > 0 -> Math.min(getCurrentLevel(skill) + value, getMaxLevel(skill) + capValue)
                    capValue < 0 -> Math.max(getCurrentLevel(skill) + value, getMaxLevel(skill) + capValue)
                    else -> Math.min(getMaxLevel(skill), getCurrentLevel(skill) + value)
                }
            val newLevel = Math.max(0, altered)
            val curLevel = getCurrentLevel(skill)

            if (newLevel != curLevel) {
                setCurrentLevel(skill = skill, level = newLevel)
            }
        }

        /**
         * Decrease the level of [skill].
         *
         * @param skill the skill level to alter.
         *
         * @param value the amount of levels which to decrease from [skill], as a
         * positive number.
         *
         * @param capped if true, the [skill] level cannot decrease further than
         * [getMaxLevel] - [value].
         */
        fun decrementCurrentLevel(
            skill: Int,
            value: Int,
            capped: Boolean,
        ) = alterCurrentLevel(skill, -value, if (capped) -value else 0)

        /**
         * Increase the level of [skill].
         *
         * @param skill the skill level to alter.
         *
         * @param value the amount of levels which to increase from [skill], as a
         * positive number.
         *
         * @param capped if true, the [skill] level cannot increase further than
         * [getMaxLevel].
         */
        fun incrementCurrentLevel(
            skill: Int,
            value: Int,
            capped: Boolean,
        ) = alterCurrentLevel(skill, value, if (capped) 0 else value)

        companion object {
            /**
             * The default count of stats for npcs.
             */
            const val DEFAULT_NPC_STAT_COUNT = 5
        }
    }
}
