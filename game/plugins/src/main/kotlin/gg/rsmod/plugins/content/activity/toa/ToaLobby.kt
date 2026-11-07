package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.quests.foundation.BeneathCursedSands
import java.util.WeakHashMap
import gg.rsmod.plugins.content.combat.removeCombatTarget
import mu.KLogging
import gg.rsmod.game.model.queue.QueueTask

/** World-thread registry, independent of Friends Chat membership. */
class ToaLobby private constructor() {
    private var sequence = 0L
    private val parties = linkedMapOf<Long, ToaParty>()
    private val membership = hashMapOf<PlayerUID, ToaParty>()
    private val raids = hashMapOf<PlayerUID, ToaRaid>()
    private val rewardConfirmations = hashMapOf<PlayerUID, Long>()
    private val rewardPrompts = hashMapOf<Pair<Long, PlayerUID>, QueueTask>()

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
        rewardConfirmations.remove(uid)
        rewardPrompts.keys.removeAll { it.second == uid }
        if (party.members.isEmpty()) parties.remove(party.id)
        return true
    }

    fun kick(actor: PlayerUID, uid: PlayerUID): Boolean {
        val party = membership[actor] ?: return false
        if (!party.kick(actor, uid)) return false
        membership.remove(uid)
        rewardConfirmations.remove(uid)
        rewardPrompts.keys.removeAll { it.second == uid }
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
                && (!ToaRewards.hasUnclaimed(member) || rewardConfirmations[uid] == party.id)
        } ?: return null
        val map = ToaMap.allocate(world)
        if (map == null) { party.allocationFailed(); return null }
        val raid = ToaRaid(party.id, roster, party.settings.copy(invocations = party.settings.invocations.toSet()), map, world)
        try { raid.prepare(roster.map { players.getValue(it) }) } catch (e: Exception) {
            raid.close()
            world.instanceAllocator.release(world, map)
            party.allocationFailed()
            logger.error("PROBLEM ToA instance preparation failed for party ${party.id}", e)
            return null
        }
        roster.forEach { uid ->
            if (ToaRewards.stored(players.getValue(uid)) != null) ToaRewards.discard(players.getValue(uid))
            rewardConfirmations.remove(uid)
            raids[uid] = raid
            membership.remove(uid)
            players.getValue(uid).teleportTo(ToaMap.instance(map, ToaMap.NEXUS))
        }
        parties.remove(party.id)
        return raid
    }

    fun requestStart(player: Player) {
        val party = party(player.uid) ?: create(player.uid) ?: return
        if (party.leader != player.uid || party.started) return
        val members = player.world.players.entries.filterNotNull().filter { it.uid in party.members }
        if (members.size != party.members.size || members.any { !ToaMap.inLobby(it.tile) || !canEnter(it) }) return
        val unconfirmed = members.filter { ToaRewards.hasUnclaimed(it) && rewardConfirmations[it.uid] != party.id }
        if (unconfirmed.isEmpty()) { start(player); return }
        unconfirmed.forEach { member ->
            val prompt = party.id to member.uid
            if (rewardPrompts[prompt]?.terminated != false) member.queue promptWork@ {
                if (rewardPrompts[prompt]?.terminated == false) return@promptWork
                rewardPrompts[prompt] = this
                try {
                    if (ToaUi.confirmRewards(this) && membership[member.uid] === party) {
                        rewardConfirmations[member.uid] = party.id
                        if (members.all { !ToaRewards.hasUnclaimed(it) || rewardConfirmations[it.uid] == party.id }) start(player)
                    }
                } finally { if (rewardPrompts[prompt] === this) rewardPrompts.remove(prompt) }
            }
        }
    }

    fun leaveRaid(player: Player, returnToLobby: Boolean = true) {
        val raid = raids.remove(player.uid) ?: return
        raid.remove(player.uid)
        player.clearHits(); player.removeCombatTarget()
        ToaUi.close(player)
        ToaSupplies.cleanup(player)
        if (returnToLobby) player.teleportTo(ToaMap.LOBBY)
        releaseIfEmpty(raid)
    }

    fun logout(player: Player) {
        val raid = raids[player.uid]
        if (raid == null) { leave(player.uid); return }
        raid.disconnect(player)
        // 21-supplies-dood: rejoin needs another online party member on the same world.
        releaseIfEmpty(raid)
    }

    fun login(player: Player) {
        val raid = raids[player.uid]
        if (raid == null) ToaSupplies.cleanup(player) else raid.reconnect(player)
    }

    fun cycle() {
        raids.values.toSet().forEach { raid ->
            // Engine teleports (spells, items, staff tools) also end membership; keep the requested destination.
            raid.world.players.entries.filterNotNull().filter {
                it.uid in raid.remaining && it.uid !in raid.offline && !raid.map.area.contains(it.tile)
            }.forEach { leaveRaid(it, returnToLobby = false) }
            if (raid.remaining.isNotEmpty()) {
                raid.cycle()
                if (raid.progress.failed) {
                    raid.players().filter { !it.isDead() }.forEach { leaveRaid(it) }
                }
                releaseIfEmpty(raid)
            }
        }
    }

    private fun releaseIfEmpty(raid: ToaRaid) {
        if (raid.closed) return
        val present = raid.world.players.any {
            it.uid in raid.remaining && it.uid !in raid.offline && it.isOnline && raid.map.area.contains(it.tile)
        }
        if (present) return
        raid.world.players.entries.filterNotNull().filter { it.uid in raid.remaining }.forEach { ToaSupplies.cleanup(it); ToaUi.close(it) }
        raid.remaining.toList().forEach { raids.remove(it) }
        raid.remaining.clear()
        raid.close()
        raid.world.instanceAllocator.release(raid.world, raid.map)
    }

    companion object : KLogging() {
        private val worlds = WeakHashMap<World, ToaLobby>()
        fun of(world: World): ToaLobby = worlds.getOrPut(world) { ToaLobby() }

        /** OSRS: the Tombs of Amascut need Beneath Cursed Sands (N462 short quest; owner E2: old completions kept by BeneathCursedSands.migrate). */
        fun canEnter(player: Player): Boolean = BeneathCursedSands.isFinished(player)
    }
}

