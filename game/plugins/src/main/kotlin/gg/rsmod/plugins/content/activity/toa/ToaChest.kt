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
    /** Normal/Expert completions apart (OSRS: Icthlarin's shrouds at 100/500/1,000/1,500/2,000 Normal/Expert completions, lobby cape chest
     *  46080 'Search' - its exact claim rules are not in the sources yet, so the chest itself is still open). */
    val COMPLETIONS_NORMAL_EXPERT = AttributeKey<Int>(persistenceKey = "toa_completions_normal_expert")

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
        if (result.purpleWinner != null) glowPurple(raid)
        present.forEach { player ->
            player.attr[COMPLETIONS] = (player.attr[COMPLETIONS] ?: 0) + 1
            if (raid.settings.raidLevel >= gg.rsmod.plugins.content.activity.toa.core.ToaMode.NORMAL.raidLevel) {
                player.attr[COMPLETIONS_NORMAL_EXPERT] = (player.attr[COMPLETIONS_NORMAL_EXPERT] ?: 0) + 1
            }
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

    /**
     * Wiki ToA Rewards: "If a unique item is rolled, the flames around Osmumten's sarcophagus will glow purple rather than white".
     * The burial chamber's sarcophagus 46220, barrier 46221 and tile glow 46222 (cache2686 square 14672, 3678-3682 x 5140-5141) are
     * multilocs on varbit 14373 -> the *_closed / *_closed_rare variants (44825/44826, 44938/44939, 44996/44997, loc.sym). The whole party
     * sees the same flames, so the parents are swapped for their rare variant in this raid's instance. ONZEKER: the opening animation
     * ('a short animation will play where it is opened') has no sequence id in the sources - not played.
     */
    private fun glowPurple(raid: ToaRaid) {
        val rare = mapOf(46220 to 44826, 46221 to 44939, 46222 to 44997)
            .mapNotNull { (parent, variant) -> ToaIds.loc(parent)?.let { p -> ToaIds.loc(variant)?.let { p to it } } }.toMap()
        if (rare.isEmpty()) return
        for (x in 3678..3682) for (z in 5140..5141) {
            val tile = ToaMap.instance(raid.map, gg.rsmod.game.model.Tile(x, z))
            raid.world.chunks.getOrCreate(tile)
                .getEntities<gg.rsmod.game.model.entity.GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                .filter { it.id in rare }.toList()
                .forEach { obj ->
                    raid.world.remove(obj)
                    raid.world.spawn(gg.rsmod.game.model.entity.DynamicObject(obj, rare.getValue(obj.id)))
                }
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
