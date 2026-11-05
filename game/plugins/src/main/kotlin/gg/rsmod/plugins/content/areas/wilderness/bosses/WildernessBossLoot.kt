package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.quests.tab.KillCounts
import gg.rsmod.plugins.content.skills.summoning.Pets
import kotlin.math.max

/** Current Venenatis loot rules, Wiki 15361982 / Mod Ash 1623969908886315009 and 1623369509154263055. */
object WildernessBossLoot {
    data class Share(val player: Player, val damage: Int, val fraction: Double)
    private val SHARES = AttributeKey<List<Share>>()
    val UNIQUES = setOf(Items.FANGS_OF_VENENATIS, Items.DRAGON_2H_SWORD, Items.DRAGON_PICKAXE, Items.VOIDWAKER_GEM, Items.TREASONOUS_RING)

    data class Contract(val rewardNpc: Int, val uniques: Set<Int>, val topBones: Int = Items.BIG_BONES, val otherBones: Int = Items.BONES)
    private val contracts = mapOf(
        VenenatisCombat.NPC to Contract(VenenatisCombat.NPC, UNIQUES),
        OldWildernessBosses.CALLISTO to Contract(OldWildernessBosses.CALLISTO,
            setOf(Items.CLAWS_OF_CALLISTO, Items.DRAGON_2H_SWORD, Items.DRAGON_PICKAXE, Items.VOIDWAKER_HILT, Items.TYRANNICAL_RING)),
        OldWildernessBosses.VETION_REBORN to Contract(OldWildernessBosses.VETION,
            setOf(Items.SKULL_OF_VETION, Items.DRAGON_2H_SWORD, Items.DRAGON_PICKAXE, Items.VOIDWAKER_BLADE, Items.RING_OF_THE_GODS)),
    )

    fun contract(npcId: Int): Contract? = contracts[npcId]
    fun manages(npcId: Int): Boolean = npcId in contracts

    fun quantity(amount: Int, contribution: Double): Int = max(1, (amount * max(0.25, contribution)).toInt())

    /** Select at most ten recipients, but use ALL damage in the contribution denominator. */
    fun shares(damage: List<Pair<Player, Int>>): List<Share> {
        val positive = damage.filter { it.second > 0 }.sortedByDescending { it.second }
        val total = positive.sumOf { it.second.toLong() }
        if (total == 0L) return emptyList()
        return positive.take(10).map { Share(it.first, it.second, it.second.toDouble() / total) }
    }

    fun recipients(npc: Npc): List<Share> = npc.attr[SHARES] ?: run {
        val damage = ArrayList<Pair<Player, Int>>()
        npc.world.players.forEach { damage += it to npc.damageMap.getDamageFrom(it) }
        shares(damage).also { npc.attr[SHARES] = it }
    }

    /** Called by the one engine kill hook: the engine has counted the top damager already. */
    fun onKilled(npc: Npc, killer: Player) {
        val contract = contract(npc.id) ?: return
        val recipients = recipients(npc)
        for (share in recipients) {
            val p = share.player
            if (p !== killer) p.incrementNpcKillCount(npc.id)
            KillCounts.onNpcKilled(p, npc.id)
            val chance = Pets.dropChance(contract.rewardNpc, DropTableFactory.killRate(p)) * share.fraction
            if (npc.world.randomDouble() < chance) {
                val pet = Pets.dropFor(contract.rewardNpc)?.pet ?: continue
                Pets.award(p, pet, source = DropSources.forNpc(p, contract.rewardNpc)?.key)
            }
        }
    }

    /** One global unique, assigned by relative damage. Regular rolls are personal and scaled in quantity. */
    fun drop(npc: Npc) {
        val contract = contract(npc.id) ?: return
        val shares = recipients(npc)
        if (shares.isEmpty()) return
        val unique = DropTableFactory.rollMainReplacement(shares.first().player, contract.rewardNpc, DropTableFactory.killRate(shares.first().player))
        val uniqueOwner = uniqueRecipient(shares.map { it.damage }, npc.world.randomDouble())
        shares.forEachIndexed { i, share ->
            // A non-recipient still gets their regular roll. Never roll (and then discard) a second unique.
            val loot = DropTableFactory.getDrop(share.player, contract.rewardNpc, rate = DropTableFactory.killRate(share.player),
                replacementLoot = if (i == uniqueOwner) unique else emptyList()).orEmpty()
            val items = loot.map { item ->
                when (item.id) {
                    contract.topBones -> Item(if (i == 0) contract.topBones else contract.otherBones)
                    in contract.uniques -> item
                    else -> Item(item.id, quantity(item.amount, share.fraction))
                }
            }.toMutableList()
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) {
                "drop GROUP table ${contract.rewardNpc} for ${share.player.username} share=${"%.2f".format(share.fraction)} at ${npc.tile.x},${npc.tile.z}: " +
                    items.joinToString(",") { "${it.id}x${it.amount}" }.ifEmpty { "nothing" }
            }
            DropTableFactory.giveDrop(npc.world, share.player, contract.rewardNpc, npc.tile, items, allowLootShare = false)
        }
    }

    fun uniqueRecipient(damage: List<Int>, roll: Double): Int {
        val total = damage.sumOf { it.toLong() }
        var remaining = roll * total
        damage.forEachIndexed { i, value ->
            remaining -= value
            if (remaining < 0) return i
        }
        return damage.lastIndex
    }
}
