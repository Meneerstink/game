package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.content.scrolls.*
import kotlin.random.Random

/** Wiki Mimic/The Mimic: opt-in, one held Mimic, first-encounter dry protection. */
object MimicCaskets {
    val ASKED = AttributeKey<Boolean>(persistenceKey = "mimic_caskets_asked")
    val RECEIVED = AttributeKey<Boolean>(persistenceKey = "mimic_casket_received")

    fun supported(tier: ClueScrollTier) = tier == ClueScrollTier.ELITE || tier == ClueScrollTier.MASTER
    // Wiki Mimic explicitly checks inventory; dropped Mimics may be juggled and picked up together.
    fun held(player: Player) = player.inventory.any { it?.id == ClueItems.local(23184) }

    fun chance(player: Player, tier: ClueScrollTier): Double {
        if (!supported(tier) || player.attr[StrangeCasket.MIMIC_CHALLENGES] != true || held(player)) return 0.0
        val threshold = if (tier == ClueScrollTier.ELITE) 25 else 10
        if (player.attr[RECEIVED] != true && ClueCompletions.get(player, tier) >= threshold - 1) return 1.0
        return 1.0 / if (tier == ClueScrollTier.ELITE) 35 else 15
    }

    fun selected(player: Player, tier: ClueScrollTier, random: Random = Random.Default) = random.nextDouble() < chance(player, tier)

    /** Exact source object is required after any confirmation dialogue. No partial conversion. */
    fun convert(player: Player, slot: Int, source: Item, tier: ClueScrollTier,
                encounterReady: Boolean = MimicEncounter.ready(player.world)): Boolean {
        if (!encounterReady || !supported(tier) || source.id != tier.casketId || source.amount < 1 ||
            player.inventory[slot] !== source || held(player)) return false
        val mimic = ClueItems.local(23184) ?: return false
        val staged = ItemContainer(player.inventory)
        staged[slot] = if (source.amount == 1) null else Item(source, source.amount - 1)
        if (!staged.addPreservingAttr(MimicChallenge.create(mimic, tier), assureFullInsertion = true).hasSucceeded()) return false
        for (i in 0 until staged.capacity) {
            val old = player.inventory[i]; val next = staged[i]
            if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) player.inventory[i] = next
        }
        player.attr[RECEIVED] = true
        return true
    }

    fun rewards(player: Player, source: Item, random: Random = Random.Default): ClueCasketRewards.Plan? {
        val tier = MimicChallenge.tier(source) ?: return null
        if (!MimicChallenge.defeated(source) || MimicChallenge.active(source) || MimicChallenge.remaining(source) <= 0) return null
        val catalogue = MimicRewards.current
        if (catalogue.missing(tier) { osrs -> ClueItems.local(osrs)?.takeIf {
                player.world.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, it) != null
            } }.isNotEmpty()) {
            println("PROBLEM CLUE Mimic bonus assets not mapped; casket retained")
            return null
        }
        val bonus = catalogue.roll(tier, MimicChallenge.failures(source), random)
        val extra = bonus.map { Item(ClueItems.local(it.osrs)!!, it.amount) }.toMutableList()
        val case = ClueContainers.cases.single { it.tier == null }
        if (!ClueContainers.opened(player, case)) extra += Item(ClueItems.local(case.osrs) ?: return null)
        val plan = ClueCasketRewards.planForPlayer(player, tier, random, MimicChallenge.remaining(source), extra) ?: return null
        return plan.copy(megaRare = plan.megaRare + bonus.filter { it.rare }.map { ClueItems.local(it.osrs)!! })
    }
}
