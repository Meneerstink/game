package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.foundation.BeneathCursedSands
import java.util.WeakHashMap

/** World-thread registry, independent of Friends Chat membership. */
class ToaLobby private constructor() {
    private var sequence = 0L
    private val parties = linkedMapOf<Long, ToaParty>()
    private val membership = hashMapOf<PlayerUID, ToaParty>()
    private val raids = hashMapOf<PlayerUID, ToaRaid>()

    fun party(uid: PlayerUID): ToaParty? = membership[uid]
    fun raid(uid: PlayerUID): ToaRaid? = raids[uid]
    fun create(uid: PlayerUID): ToaParty? {
        if (uid in membership || uid in raids) return null
        return ToaParty(++sequence, uid).also { parties[it.id] = it; membership[uid] = it }
    }

    fun join(uid: PlayerUID, leader: PlayerUID): Boolean {
        if (uid in membership || uid in raids) return false
        val party = membership[leader] ?: return false
        if (party.leader != leader || !party.join(uid)) return false
        membership[uid] = party
        return true
    }

    fun leave(uid: PlayerUID): Boolean {
        val party = membership[uid] ?: return false
        if (!party.leave(uid)) return false
        membership.remove(uid)
        if (party.members.isEmpty()) parties.remove(party.id)
        return true
    }

    fun kick(actor: PlayerUID, uid: PlayerUID): Boolean {
        val party = membership[actor] ?: return false
        if (!party.kick(actor, uid)) return false
        membership.remove(uid)
        return true
    }

    fun start(player: Player): ToaRaid? {
        val party = party(player.uid) ?: create(player.uid) ?: return null
        val world = player.world
        val players = world.players.entries.filterNotNull().associateBy { it.uid }
        val roster = party.start(player.uid) { uid ->
            val member = players[uid]
            member != null && member.isOnline && !member.isLogoutPending && !member.isDead() &&
                ToaMap.inLobby(member.tile) && canEnter(member) && raids[uid] == null
        } ?: return null
        val map = ToaMap.allocate(world)
        if (map == null) { party.allocationFailed(); return null }
        val raid = ToaRaid(party.id, roster, party.settings.copy(invocations = party.settings.invocations.toSet()), map, world)
        roster.forEach { uid ->
            raids[uid] = raid
            membership.remove(uid)
            players.getValue(uid).teleportTo(ToaMap.instance(map, ToaMap.NEXUS))
        }
        parties.remove(party.id)
        return raid
    }

    fun leaveRaid(player: Player) {
        val raid = raids.remove(player.uid) ?: return
        raid.remaining.remove(player.uid)
        player.teleportTo(ToaMap.LOBBY)
        if (raid.remaining.isEmpty() || player.world.players.none { it.uid in raid.remaining && raid.map.area.contains(it.tile) }) {
            raid.remaining.forEach { raids.remove(it) }
            raid.remaining.clear()
            raid.close()
            player.world.instanceAllocator.release(player.world, raid.map)
        }
    }

    fun logout(player: Player) {
        val raid = raids[player.uid]
        if (raid == null) { leave(player.uid); return }
        // 21-supplies-dood: rejoin needs another online party member on the same world.
        val others = player.world.players.any { it.uid != player.uid && it.uid in raid.remaining && it.isOnline }
        if (!others) {
            raid.remaining.forEach { raids.remove(it) }
            raid.remaining.clear()
            raid.close()
            player.world.instanceAllocator.release(player.world, raid.map)
        }
    }

    fun login(player: Player) {
        raids[player.uid]?.let { player.teleportTo(ToaMap.instance(it.map, ToaMap.NEXUS)) }
    }

    fun cycle() {
        raids.values.toSet().forEach { it.progress.cycle(it.world.currentCycle) }
    }

    companion object {
        private val worlds = WeakHashMap<World, ToaLobby>()
        fun of(world: World): ToaLobby = worlds.getOrPut(world) { ToaLobby() }

        /** OSRS: the Tombs of Amascut need Beneath Cursed Sands (N462 short quest; owner E2: old completions kept by BeneathCursedSands.migrate). */
        fun canEnter(player: Player): Boolean = BeneathCursedSands.isFinished(player)
    }
}

