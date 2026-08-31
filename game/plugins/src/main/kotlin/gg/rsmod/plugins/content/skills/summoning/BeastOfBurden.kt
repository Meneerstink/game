package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message

/**
 * R07.3 (Beast of Burden storage): extra-inventory-space container for the 3 named BoB
 * familiars - Pack Yak 30 / War Tortoise 18 / Spirit Terrorbird 12 slots, exact capacities
 * given by the work order itself, not guessed.
 *
 * Reuses the existing generic [Player.containers]/[ContainerKey]/`register_container_key`
 * persistence mechanism ([gg.rsmod.game.service.serializer.json.JsonPlayerSerializer] already
 * saves/loads any registered container generically) rather than adding bespoke fields to
 * [Player] - the same pattern [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY] already
 * establishes for a plugin-owned secondary container.
 *
 * No graphical deposit/withdraw interface is built here: real RS's BoB interface component
 * layout is a specific 667-era cache interface this session has not located or verified an ID
 * for, and the work order explicitly forbids guessing interface/component IDs. Deposit is
 * instead exposed the same real, non-graphical way as [gg.rsmod.plugins.content.mechanics.death.DeathRecoveryService]
 * exposes death-recovery: using an inventory item on the familiar deposits it (matching real
 * RS's own "drag item onto your pack animal" mechanic - wired in `familiar.plugin.kts`), and
 * the familiar's existing Renew/Dismiss/Cancel interact menu gains a "Withdraw-all" option.
 */
object BeastOfBurden {
    val PACK_YAK_KEY = ContainerKey("bob_pack_yak", capacity = 30, stackType = ContainerStackType.NORMAL)
    val WAR_TORTOISE_KEY = ContainerKey("bob_war_tortoise", capacity = 18, stackType = ContainerStackType.NORMAL)
    val SPIRIT_TERRORBIRD_KEY = ContainerKey("bob_spirit_terrorbird", capacity = 12, stackType = ContainerStackType.NORMAL)

    private val keysByPouch =
        mapOf(
            SummoningPouchData.PACK_YAK to PACK_YAK_KEY,
            SummoningPouchData.WAR_TORTOISE to WAR_TORTOISE_KEY,
            SummoningPouchData.SPIRIT_TERRORBIRD to SPIRIT_TERRORBIRD_KEY,
        )

    val allKeys = keysByPouch.values.toList()

    /** True if [npcId] is one of the 3 real BoB familiars' summoned npc id. */
    fun isBobNpc(npcId: Int): Boolean = keysByPouch.keys.any { it.npc == npcId }

    /** The active BoB container key for [player]'s currently-summoned familiar, if any. */
    fun activeKey(player: Player): ContainerKey? {
        val npc = Familiar.current(player) ?: return null
        val data = SummoningPouchData.values.firstOrNull { it.npc == npc.id } ?: return null
        return keysByPouch[data]
    }

    private fun container(
        player: Player,
        key: ContainerKey,
    ): ItemContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }

    /**
     * Deposits [item] from [player]'s inventory into the active BoB container, best-effort
     * (whatever fits). Returns the amount actually moved; 0 (with a message) if no BoB
     * familiar is out or nothing fit.
     */
    fun deposit(
        player: Player,
        item: Item,
    ): Int {
        val key = activeKey(player)
        if (key == null) {
            player.message("You need an active Beast of Burden familiar out to store items with it.")
            return 0
        }
        val transaction = container(player, key).add(item.id, item.amount, assureFullInsertion = false)
        if (transaction.completed <= 0) {
            player.message("Your familiar can't carry any more of that.")
            return 0
        }
        player.inventory.remove(Item(item.id, transaction.completed), assureFullRemoval = true)
        return transaction.completed
    }

    /** Withdraws everything from the active BoB container into the inventory, best-effort. */
    fun withdrawAll(player: Player): Int {
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        var withdrawn = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            val transaction = player.inventory.add(item.id, item.amount, assureFullInsertion = false)
            if (transaction.completed <= 0) continue
            container[slot] = if (transaction.completed == item.amount) null else Item(item.id, item.amount - transaction.completed)
            withdrawn += transaction.completed
        }
        return withdrawn
    }
}
