package gg.rsmod.plugins.content.mechanics.lfg

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class LfgGroup(
    val id: Int,
    val activity: String,
    val leader: String,
    val members: MutableList<String> = mutableListOf(),
)

/**
 * R14.19: voluntary group-finding for PvM/Wilderness/minigames via the home board -
 * list/join/leave/cleanup. In-memory only (session-scoped; not meant to survive a restart) -
 * same "flat registry, no DB" choice already made for
 * [gg.rsmod.plugins.content.mechanics.clan.Clans] at this player-count target.
 */
object Lfg {
    val GROUP_ATTR = AttributeKey<Int>() // current group id - not persisted, voluntary/session-only

    private val groups = ConcurrentHashMap<Int, LfgGroup>()
    private val nextId = AtomicInteger(1)

    fun open(): List<LfgGroup> = groups.values.sortedBy { it.id }

    fun myGroup(player: Player): LfgGroup? = player.attr[GROUP_ATTR]?.let { groups[it] }

    fun create(
        player: Player,
        activity: String,
    ): LfgGroup? {
        if (myGroup(player) != null) {
            player.filterableMessage("You're already in a group. Leave it first.")
            return null
        }
        val id = nextId.getAndIncrement()
        val group = LfgGroup(id, activity, player.username, mutableListOf(player.username))
        groups[id] = group
        player.attr[GROUP_ATTR] = id
        player.filterableMessage("You started a group looking for '$activity'.")
        return group
    }

    fun join(
        player: Player,
        group: LfgGroup,
    ) {
        if (myGroup(player) != null) {
            player.filterableMessage("You're already in a group. Leave it first.")
            return
        }
        group.members.add(player.username)
        player.attr[GROUP_ATTR] = group.id
        player.filterableMessage("You joined the group for '${group.activity}' (led by ${group.leader}).")
    }

    /** Also called on logout so a disconnected member's slot doesn't linger forever. */
    fun leave(player: Player) {
        val group = myGroup(player) ?: return
        group.members.remove(player.username)
        player.attr.remove(GROUP_ATTR)
        player.filterableMessage("You left the group for '${group.activity}'.")
        if (group.members.isEmpty()) {
            groups.remove(group.id)
        }
    }
}
