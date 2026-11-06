package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.activity.toa.core.ToaLoot
import gg.rsmod.plugins.content.activity.toa.core.ToaLootItems
import gg.rsmod.plugins.content.activity.toa.core.ToaLootPlayer
import gg.rsmod.plugins.content.activity.toa.core.ToaPointSource
import kotlin.random.Random

/**
 * N462: the raid's points and its chest. Points: at a room clear every member's damage on each of the room's npcs (DamageMap) goes to the
 * room points with the OSRS multiplier of its source (ToaPointSource); a cleared room moves them to the totals (ToaRaid). The chest is
 * rolled ONCE per raid at the Wardens' death (ToaLoot, OSRS chest formulas) and handed to ToaRewards' escrow through [ToaRewards.roller],
 * so the chest, the niche and a reconnect all read the same roll.
 */
object ToaChest {
    /** Raid completions per account (the Thread of Elidinis and jewel chances scale with it). */
    val COMPLETIONS = AttributeKey<Int>(persistenceKey = "toa_completions")
    val HAD_THREAD = AttributeKey<Boolean>(persistenceKey = "toa_had_thread")

    /** A jewel counts as owned while the jewel or its Keris partisan is held (Chest wiki: ownership check, not the collection log). */
    private val PARTISAN_OF_JEWEL = mapOf(27283 to 25981, 27285 to 27287, 27289 to 27291, 30893 to 30891)

    private val pending = hashMapOf<Pair<String, PlayerUID>, List<Item>>()

    init {
        ToaRewards.roller = ToaRewardRoller { context -> pending.remove(context.raidToken to context.player).orEmpty() }
        // Collection log: the items as they are taken from the chest / niche (N462 page 'toa').
        val previous = ToaRewards.claimed
        ToaRewards.claimed = { player, items ->
            gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, gg.rsmod.plugins.content.quests.tab.CollectionLogPages.TOA, items.map { it.id to it.amount })
            previous(player, items)
        }
    }

    fun install() = Unit

    /** A room is cleared: every raid member's damage on each of the room's npcs (dead or alive, any form) becomes room points. */
    fun creditRoom(raid: ToaRaid, npcs: List<Npc>) {
        npcs.forEach { npc ->
            val source = ToaPointSource.of(ToaIds.osrsNpcOf(npc.id) ?: -1)
            raid.players().forEach { player ->
                val damage = npc.damageMap.getDamageFrom(player)
                if (damage > 0) raid.points.damage(player.uid, damage, source)
            }
        }
    }

    /** The Wardens died: roll the whole chest once and park every present member's share for the escrow. */
    fun fill(raid: ToaRaid) {
        val present = raid.players()
        val lootPlayers = raid.roster.map { uid ->
            val player = present.firstOrNull { it.uid == uid }
            ToaLootPlayer(
                uid,
                raid.points.rewardPoints(uid),
                player?.attr?.get(COMPLETIONS) ?: 0,
                player?.attr?.get(HAD_THREAD) == true,
                player?.let(::ownedJewels) ?: emptySet(),
                owns = { osrs -> player != null && ToaLootItems.local(osrs)?.let { holds(player, it) } == true },
            )
        }
        val result = ToaLoot.roll(lootPlayers, present.map { it.uid }.toSet(), raid.settings.raidLevel, Random.Default, raid.points.deathless)
        present.forEach { player ->
            player.attr[COMPLETIONS] = (player.attr[COMPLETIONS] ?: 0) + 1
            val drops = result.drops[player.uid].orEmpty()
            if (drops.any { it.osrsItem == ToaLoot.THREAD_OF_ELIDINIS }) player.attr[HAD_THREAD] = true
            pending[raid.rewardToken to player.uid] = drops.mapNotNull { drop ->
                val local = ToaLootItems.local(drop.osrsItem)
                if (local == null) {
                    System.err.println("[toa] chest: OSRS item ${drop.osrsItem} is not imported - left out")
                    null
                } else {
                    Item(if (drop.noted) noted(player, local) else local, drop.amount)
                }
            }
            ToaRewards.open(player, raid)
        }
    }

    private fun noted(player: Player, id: Int): Int {
        val def = player.world.definitions.getNullable(ItemDef::class.java, id) ?: return id
        return if (!def.noted && def.noteLinkId > 0) def.noteLinkId else id
    }

    private fun ownedJewels(player: Player): Set<Int> = ToaLoot.JEWELS.filter { jewel ->
        listOfNotNull(ToaLootItems.local(jewel), PARTISAN_OF_JEWEL[jewel]?.let(ToaLootItems::local)).any { id ->
            holds(player, id)
        }
    }.toSet()

    private fun holds(player: Player, id: Int): Boolean =
        player.inventory.contains(id) || player.bank.contains(id) || player.equipment.contains(id)
}
