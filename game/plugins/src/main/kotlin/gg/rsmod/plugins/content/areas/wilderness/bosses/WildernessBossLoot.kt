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
        val recipients = recipients(npc)
        for (share in recipients) {
            val p = share.player
            if (p !== killer) p.incrementNpcKillCount(npc.id)
            KillCounts.onNpcKilled(p, npc.id)
            val chance = Pets.dropChance(npc.id, DropTableFactory.killRate(p)) * share.fraction
            if (npc.world.randomDouble() < chance) {
                val pet = Pets.dropFor(npc.id)?.pet ?: continue
                Pets.award(p, pet, source = DropSources.forNpc(p, npc.id)?.key)
            }
        }
    }

    /** One global unique, assigned by relative damage. Regular rolls are personal and scaled in quantity. */
    fun drop(npc: Npc) {
        val shares = recipients(npc)
        if (shares.isEmpty()) return
        val unique = DropTableFactory.rollMainReplacement(shares.first().player, npc.id, DropTableFactory.killRate(shares.first().player))
        val uniqueOwner = uniqueRecipient(shares.map { it.damage }, npc.world.randomDouble())
        shares.forEachIndexed { i, share ->
            // A non-recipient still gets their regular roll. Never roll (and then discard) a second unique.
            val loot = DropTableFactory.getDrop(share.player, npc.id, rate = DropTableFactory.killRate(share.player),
                replacementLoot = if (i == uniqueOwner) unique else emptyList()).orEmpty()
            val items = loot.map { item ->
                when (item.id) {
                    Items.BIG_BONES -> Item(if (i == 0) Items.BIG_BONES else Items.BONES)
                    in UNIQUES -> item
                    else -> Item(item.id, quantity(item.amount, share.fraction))
                }
            }.toMutableList()
            DropTableFactory.giveDrop(npc.world, share.player, npc.id, npc.tile, items, allowLootShare = false)
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
