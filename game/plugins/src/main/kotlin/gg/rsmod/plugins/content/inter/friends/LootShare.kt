package gg.rsmod.plugins.content.inter.friends

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.social.FriendsChatRank
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarbit

/**
 * LootShare and CoinShare of the 2011 friends chat ("clan chat" in the 667 client), as the 667 Void donor builds them
 * (content/social/clan/LootShare.kt, content/entity/death/NPCDeath.kt shareLoot/shareCoin, clan.varbits.toml):
 *
 * - The Friends Chat tab LootShare button toggles it after 2 minutes (varbit 4072 "loading", 4071 "active"; RuneScape Wiki "LootShare":
 *   "LootShare requires 2 minutes to activate"). The channel owner's LootShare rank decides who may (1108; -1 = No-one).
 * - A drop from an npc killed in a multi-combat area by a player whose LootShare is active is shared among the channel members within
 *   16 tiles with LootShare active: each tradeable item goes to one of them, chosen at random weighted by LootShare points (a
 *   member's points grow by the value of every item somebody else receives - "bad luck mitigation" - and shrink by what they receive).
 *   Untradeable items stay with the killer. With CoinShare on, an item worth over 100,000 coins is split as coins instead.
 * - CoinShare (1108 component 33, the owner's setting, varbit 4466) reaches the members 30 seconds later (varbit 4465).
 */
object LootShare {
    const val ACTIVE_VARBIT = 4071
    const val LOADING_VARBIT = 4072
    const val COIN_SHARE_VARBIT = 4465
    const val COIN_SHARE_SETTING_VARBIT = 4466

    const val TOGGLE_TICKS = 200 // 2 minutes
    const val COIN_SHARE_TICKS = 50 // 30 seconds
    const val RANGE = 16
    const val COIN_SHARE_MINIMUM = 100_000

    val POINTS = AttributeKey<Int>("loot_share_potential")

    private val RANK_NAMES = mapOf(0 to "friend", 1 to "recruit", 2 to "corporal", 3 to "sergeant", 4 to "lieutenant", 5 to "captain", 6 to "general", 7 to "owner")

    fun isActive(player: Player): Boolean = player.getVarbit(ACTIVE_VARBIT) == 1

    /** Leaving the channel (or logging in fresh) switches LootShare and CoinShare off (Novite 667 FriendChatsManager.disableLootShare). */
    fun disable(player: Player, quiet: Boolean = false) {
        val wasOn = isActive(player) || player.getVarbit(LOADING_VARBIT) == 1
        player.setVarbit(LOADING_VARBIT, 0)
        player.setVarbit(ACTIVE_VARBIT, 0)
        player.setVarbit(COIN_SHARE_VARBIT, 0)
        if (wasOn && !quiet && player.isOnline) player.message("LootShare is no longer active.")
    }

    /** The LootShare button on the Friends Chat tab. */
    fun toggle(player: Player) {
        val chat = player.world.friendsChat
        val channel = chat.channelOf(player) ?: return
        val lootRank = chat.settingsOf(channel.owner)?.lootShareRank ?: FriendsChatRank.GUEST
        if (lootRank == FriendsChatRank.GUEST) {
            player.message("LootShare is disabled by the clan owner.")
            return
        }
        if (channel.rankOf(player) < lootRank) {
            player.message("Only ${RANK_NAMES[lootRank] ?: "owner"}s can share loot.")
            return
        }
        if (player.getVarbit(LOADING_VARBIT) == 1) return
        player.setVarbit(LOADING_VARBIT, 1)
        val active = isActive(player)
        player.message("You will ${if (active) "stop sharing" else "be able to share"} loot in 2 minutes.")
        player.world.queue {
            wait(TOGGLE_TICKS)
            // Left the channel (or relogged) meanwhile: disable() already cleared the pending toggle.
            if (!player.isOnline || player.getVarbit(LOADING_VARBIT) != 1 || chat.channelOf(player) == null) return@queue
            player.setVarbit(LOADING_VARBIT, 0)
            val now = !isActive(player)
            player.setVarbit(ACTIVE_VARBIT, if (now) 1 else 0)
            val coinShare = chat.channelOf(player)?.let { chat.settingsOf(it.owner)?.coinShare } == true
            player.setVarbit(COIN_SHARE_VARBIT, if (coinShare) 1 else 0)
            player.message(if (now) "LootShare is now active. The CoinShare option is ${if (coinShare) "on" else "off"}." else "LootShare is no longer active.")
        }
    }

    /** The CoinShare button of the owner's Friends Chat Setup: reaches every member of the live channel 30 seconds later. */
    fun toggleCoinShare(owner: Player) {
        val chat = owner.world.friendsChat
        val on = chat.settingsOf(owner.username)?.coinShare != true
        chat.setCoinShare(owner, on)
        owner.setVarbit(COIN_SHARE_SETTING_VARBIT, if (on) 1 else 0)
        owner.world.queue {
            wait(COIN_SHARE_TICKS)
            val channel = chat.channelOwnedBy(owner.username) ?: return@queue
            channel.members().forEach { member ->
                member.setVarbit(COIN_SHARE_VARBIT, if (on) 1 else 0)
                member.message("CoinShare has been switched ${if (on) "on" else "off"}.")
            }
        }
    }

    /** The members a drop at [tile] is shared among, or null when the kill is not shared. */
    fun sharers(killer: Player, tile: Tile): List<Player>? {
        if (!isActive(killer) || !tile.isMulti(killer.world)) return null
        val channel = killer.world.friendsChat.channelOf(killer) ?: return null
        val members = channel.members().filter { it.isOnline && isActive(it) && it.tile.height == tile.height && it.tile.isWithinRadius(tile, RANGE) }
        return members.takeIf { killer in it && it.size > 1 }
    }

    /** Spawns one npc drop for [members]; returns false when the item is not shared (the caller drops it normally). */
    fun share(world: World, killer: Player, members: List<Player>, item: Item, tile: Tile, spawn: (Item, Player) -> Unit): Boolean {
        val def = world.definitions.get(ItemDef::class.java, item.id)
        val coinShare = world.friendsChat.channelOf(killer)?.let { world.friendsChat.settingsOf(it.owner)?.coinShare } == true
        val value = def.cost.toLong() * item.amount
        if (coinShare && def.cost > COIN_SHARE_MINIMUM) {
            val split = (value / members.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            members.forEach { member ->
                world.spawn(GroundItem(Items.COINS_995, split, tile, member))
                member.message("You received $split gold as your split of this drop: ${item.amount} x ${def.name}.")
            }
            return true
        }
        val awardee = if (def.tradeable) weighted(world, members) ?: killer else killer
        members.filter { it !== awardee }.forEach { member ->
            member.message("${awardee.username} received: ${item.amount} ${plural(def.name, item.amount)}.")
            member.attr[POINTS] = ((member.attr[POINTS] ?: 0).toLong() + value).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            member.message("Your chance of receiving loot has improved.")
        }
        awardee.attr[POINTS] = ((awardee.attr[POINTS] ?: 0).toLong() - value).coerceAtLeast(0).toInt()
        spawn(item, awardee)
        awardee.message("You received: ${item.amount} ${plural(def.name, item.amount)}.")
        return true
    }

    private fun weighted(world: World, members: List<Player>): Player? {
        val total = members.sumOf { (it.attr[POINTS] ?: 0).toLong() }
        if (total <= 0) return null
        var roll = (world.randomDouble() * total).toLong()
        for (member in members) {
            roll -= member.attr[POINTS] ?: 0
            if (roll < 0) return member
        }
        return members.last()
    }

    private fun plural(name: String, amount: Int): String = if (amount != 1 && !name.endsWith("s")) "${name}s" else name
}
