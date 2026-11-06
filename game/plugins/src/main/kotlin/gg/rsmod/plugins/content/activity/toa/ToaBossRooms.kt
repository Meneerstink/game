package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.activity.toa.bosses.ToaBossNpcs
import gg.rsmod.plugins.content.activity.toa.bosses.ToaEncounter
import gg.rsmod.plugins.content.activity.toa.bosses.ToaEncounterContext

/**
 * N462 raid adapter: a boss room of the raid starts the boss card's encounter (ToaEncounter.definitions, registered by
 * toa_bosses.plugin.kts) inside the room's scope, so every npc of the fight counts for the room points and is removed with
 * the room, and the boss's death clears the room. A boss whose card is not active yet leaves its room uncleared (ToaRooms).
 */
object ToaBossRooms {
    class Spawn(val encounter: String, val tile: Tile)

    /**
     * OSRS spawn tile per boss room. Kephri 3551,5409 and Zebak 3921,5408 = wiki infobox maps (04/05-brontekst); the plane is
     * the boss entry's plane (ToaPath.bossEntry), where the party lands. Akkha and Ba-Ba have no infobox map (map = No):
     * ONZEKER T02 - Akkha's arena tile is the one the boss screenshots used, to be checked against OSRS in the test client.
     */
    val SPAWNS: Map<ToaPath, Spawn> = mapOf(
        ToaPath.HET to Spawn("akkha", Tile(3679, 5407, 1)),
        ToaPath.CRONDIS to Spawn("zebak", Tile(3921, 5408, ToaPath.CRONDIS.bossEntry.height)),
        ToaPath.SCABARAS to Spawn("kephri", Tile(3551, 5409, ToaPath.SCABARAS.bossEntry.height)),
    )

    fun register(): List<AutoCloseable> =
        SPAWNS.map { (path, spawn) -> ToaRooms.register(ToaRoomKey(path, true)) { raid -> Room(raid, spawn) } }

    /** The live raid view a boss card targets: the room's active players, the raid's level, children through the room scope. */
    class RaidContext(private val raid: ToaRaid, private val scope: ToaRoomScope) : ToaEncounterContext {
        override val players get() = raid.activePlayers()
        override val partySize: Int get() = raid.roster.size.coerceIn(1, 8)
        override val raidLevel: Int get() = raid.settings.raidLevel.coerceIn(0, 600)
        override val pathLevel = 0
        override val invocations = emptySet<String>()
        override fun spawnChild(npc: Npc): Boolean = scope.active && scope.spawn(npc)
    }

    class Room(private val raid: ToaRaid, private val spawn: Spawn) : ToaRoom {
        var boss: Npc? = null
            private set

        override fun start(run: ToaRoomRun) {
            val definition = ToaEncounter.definitions[spawn.encounter] ?: return
            val scope = raid.scope(run) ?: return
            val id = ToaBossNpcs.local(definition.osrsNpc) ?: return
            val npc = Npc(id, ToaMap.instance(raid.map, spawn.tile), raid.world)
            npc.respawnOverride = false
            val context = RaidContext(raid, scope)
            npc.attr[ToaEncounter.CONTEXT] = context
            if (!scope.spawn(npc)) return
            boss = npc
            ToaEncounter.attach(npc, context, definition.factory)
            // The boss's death clears this run once its death sequence has played (the death handler despawns it; clearing at the
            // first dead tick would close the scope and cut the animation). The generation token ignores a death after a wipe/retry.
            scope.queue {
                var died = false
                while (npc.isSpawned()) {
                    if (npc.isDead()) died = true
                    wait(1)
                }
                if (died) run.clear(raid.world.currentCycle)
            }
        }

        override fun clear(run: ToaRoomRun) { boss = null }
        override fun wipe(run: ToaRoomRun) { boss = null }
        override fun reset(run: ToaRoomRun) { boss = null }
    }
}
