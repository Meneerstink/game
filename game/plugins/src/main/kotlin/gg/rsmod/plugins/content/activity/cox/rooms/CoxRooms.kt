package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.activity.cox.CoxRoom as RoomType
import java.util.concurrent.ConcurrentHashMap

/**
 * The rooms of every running raid (kept outside CoxRaid so the raid class stays patchable - skill rsps-raid-import 'veld toevoegen').
 * The tsps donor loads a room's contents when the raid begins ('The raid has started: spawn what the room holds'); rooms without an
 * encounter class (start/end/farming for now) get nothing.
 */
object CoxRooms {
    private val byRaid = ConcurrentHashMap<Long, List<CoxRoom>>()
    /** Which room owns an npc (attack rules, test commands). */
    val owner = ConcurrentHashMap<Npc, CoxRoom>()

    private val factories: Map<RoomType, (CoxRaid, Int, CoxLaidRoom) -> CoxRoom> = mapOf(
        RoomType.DARK_ALTAR_ROOM to ::MysticsRoom,
        RoomType.LIZARDMEN_SHAMAN to ::ShamansRoom,
        RoomType.SMALL_SCAVENGER_RUNT to ::ScavengersRoom,
        RoomType.LARGE_SCAVENGER_BEAST to ::ScavengersRoom,
        RoomType.GUARDIANS to ::GuardiansRoom,
        RoomType.TEKTON to ::TektonRoom,
        RoomType.VANGUARD to ::VanguardsRoom,
        RoomType.VASA_NISTIRIO to ::VasaRoom,
        RoomType.MUTTADILES to ::MuttadilesRoom,
    )

    fun rooms(raid: CoxRaid): List<CoxRoom> = byRaid[raid.id] ?: emptyList()

    fun load(raid: CoxRaid) {
        if (byRaid.containsKey(raid.id)) return
        val rooms = raid.layout.floors.flatMapIndexed { f, floor -> floor.mapNotNull { laid -> factories[laid.room]?.invoke(raid, f, laid) } }
        byRaid[raid.id] = rooms
        rooms.forEach { room ->
            runCatching { room.load() }.onFailure { CoxRaidLog.logger.error(it) { "CoX room ${room.laid.room} load in raid ${raid.id}" } }
        }
    }

    fun tick(raid: CoxRaid) {
        rooms(raid).forEach { room ->
            runCatching { room.cycle() }.onFailure {
                CoxRaidLog.logger.error(it) { "CoX room ${room.laid.room} tick in raid ${raid.id} - room closed" }
                room.close()
            }
        }
    }

    fun unload(raid: CoxRaid) {
        byRaid.remove(raid.id)?.forEach { it.close() }
    }

    /** Prayer blocks (Vasa's special, the Olm's Lightning): username -> (until cycle, refusal line). Read by ActivityRestrictions PRAYER. */
    private val prayerBlocks = ConcurrentHashMap<String, Pair<Int, String>>()

    fun blockPrayers(p: gg.rsmod.game.model.entity.Player, untilCycle: Int, message: String?) {
        if (message == null || untilCycle <= p.world.currentCycle) prayerBlocks.remove(p.username.lowercase())
        else prayerBlocks[p.username.lowercase()] = untilCycle to message
    }

    fun prayerRefusal(p: gg.rsmod.game.model.entity.Player): String? {
        val (until, msg) = prayerBlocks[p.username.lowercase()] ?: return null
        if (p.world.currentCycle >= until) { prayerBlocks.remove(p.username.lowercase()); return null }
        return msg
    }

    /** The room [npc] belongs to. */
    fun of(npc: Npc): CoxRoom? = owner[npc]

    /** Local ids of every npc a room drives itself (the generic auto-retaliate loop is switched off for them). */
    val DRIVEN_OSRS: List<Int> = listOf(7561, 7562, 7563, 7564, 7565, 7566, 7567, 7568, 7525, 7526, 7527, 7528, 7529, 7540, 7541, 7542, 7543, 7544, 7545, 7548, 7549, 7569, 7570, 7571, 7572, 7573, 7574, 7575, 7604, 7605, 7606)

}

object CoxRaidLog : mu.KLogging()
