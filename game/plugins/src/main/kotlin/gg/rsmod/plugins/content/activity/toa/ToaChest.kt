package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message
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
    /** OSRS keeps a count per mode ("Tombs of Amascut", "...: Entry Mode", "...: Expert Mode" - RuneLite ChatCommandsPlugin). */
    val COMPLETIONS_BY_MODE = gg.rsmod.plugins.content.activity.toa.core.ToaMode.values()
        .associateWith { AttributeKey<Int>(persistenceKey = "toa_completions_" + it.name.lowercase()) }

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

    /** The remnant [osrs] was used on the pet: every form it unlocks is in the player's unlocked metamorphoses. */
    fun remnantUsed(player: Player, osrs: Int): Boolean {
        val forms = gg.rsmod.plugins.content.skills.summoning.PetMorphs.TOA_REMNANTS[osrs] ?: return false
        val unlocked = player.attr[gg.rsmod.plugins.content.skills.summoning.PetMorphs.UNLOCKED_ATTR].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
        return forms.all { it in unlocked }
    }

    /** A room is cleared: every raid member's damage on each of the room's npcs (dead or alive, any form) becomes room points. */
    fun creditRoom(raid: ToaRaid, npcs: List<Npc>) {
        // Per account, also for a member who logged out (ultrareview #4: the damage of a member offline at the clear was lost).
        val members = raid.remaining
        npcs.forEach { npc ->
            val source = ToaPointSource.of(ToaIds.osrsNpcOf(npc.id) ?: -1)
            npc.damageMap.damageByPlayerUid().forEach { (uid, damage) ->
                if (uid in members && damage > 0) raid.points.damage(uid, damage, source)
            }
        }
    }

    /** Raid token -> members whose completion still has to be counted (they were logged out when the Wardens died). */
    private val completionsDue = hashMapOf<String, MutableSet<PlayerUID>>()

    private fun countCompletion(raid: ToaRaid, player: Player) {
        player.attr[COMPLETIONS] = (player.attr[COMPLETIONS] ?: 0) + 1
        if (raid.settings.raidLevel >= gg.rsmod.plugins.content.activity.toa.core.ToaMode.NORMAL.raidLevel) {
            player.attr[COMPLETIONS_NORMAL_EXPERT] = (player.attr[COMPLETIONS_NORMAL_EXPERT] ?: 0) + 1
        }
        val key = COMPLETIONS_BY_MODE.getValue(gg.rsmod.plugins.content.activity.toa.core.ToaMode.of(raid.settings.raidLevel))
        val count = (player.attr[key] ?: 0) + 1
        player.attr[key] = count
        player.message(ToaChallengeMessages.count(raid.settings.raidLevel, count))
    }

    fun completionOnReconnect(raid: ToaRaid, player: Player) {
        if (completionsDue[raid.rewardToken]?.remove(player.uid) == true) countCompletion(raid, player)
    }

    /** The Wardens died: roll the whole chest once and park every present member's share for the escrow. */
    fun fill(raid: ToaRaid) {
        val online = raid.players()
        // Every member still in the raid gets a share - a member logged out at the kill too (ultrareview #4); he opens the chest after
        // his reconnect, which puts him in the burial chamber. Leavers (no longer in `remaining`) only count for the party points.
        val present = raid.memberObjects()
        val lootPlayers = raid.roster.map { uid ->
            val player = present.firstOrNull { it.uid == uid }
            ToaLootPlayer(
                uid,
                raid.points.rewardPoints(uid),
                player?.attr?.get(COMPLETIONS) ?: 0,
                player?.attr?.get(HAD_THREAD) == true,
                player?.let(::ownedJewels) ?: emptySet(),
                // A remnant already used on Tumeken's guardian counts as owned (its forms are unlocked), else the same remnant came again.
                owns = { osrs -> player != null && (ToaLootItems.local(osrs)?.let { holds(player, it) } == true || remnantUsed(player, osrs)) },
            )
        }
        val result = ToaLoot.roll(lootPlayers, present.map { it.uid }.toSet(), raid.settings.raidLevel, Random.Default, raid.points.deathless)
        if (result.purpleWinner != null) glowPurple(raid)
        present.forEach { player ->
            if (player in online) countCompletion(raid, player) else completionsDue.getOrPut(raid.rewardToken) { mutableSetOf() } += player.uid
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
            if (player in online) ToaRewards.open(player, raid)
        }
    }

    /** Wiki ToA Rewards: a rolled unique makes the flames around Osmumten's sarcophagus glow purple ([ToaScene.vault]). */
    private fun glowPurple(raid: ToaRaid) = raid.vaultPurple()

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
