package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.closeMainInterface
import java.lang.ref.WeakReference

/** Server-side ports for Claude's imported UI. No widget root/component number is guessed here. */
object Dmm2Interactions {
    interface SafeUi {
        fun open(player: Player, items: List<Item?>)
        fun visible(player: Player): Boolean
        fun refresh(player: Player, items: List<Item?>)
    }
    interface RewardUi {
        fun open(player: Player, store: NigelRewardStore)
        fun visible(player: Player): Boolean
        fun refresh(player: Player, store: NigelRewardStore)
    }
    @Volatile var safeUi: SafeUi? = null
    @Volatile var rewardUi: RewardUi? = null
    private val SAFE_NPC = AttributeKey<WeakReference<Npc>>(resetOnDeath = true)
    private val REWARD_NPC = AttributeKey<WeakReference<Npc>>(resetOnDeath = true)
    fun near(player: Player, npc: Npc): Boolean = npc.isSpawned() && !player.isDead() && npc.tile.height == player.tile.height &&
        player.tile.isWithinRadius(npc.tile, 2)
    fun isFinancial(npc: Npc): Boolean = npc.id in listOfNotNull(Dmm2Ids.npc(6516), Dmm2Ids.npc(6485))
    fun isNigel(npc: Npc): Boolean = npc.id in listOfNotNull(Dmm2Ids.npc(8391), Dmm2Ids.npc(8390))
    fun openSafe(player: Player, npc: Npc): Boolean {
        closeSafe(player)
        val ui = safeUi ?: return false
        player.closeMainInterface()
        if (!isFinancial(npc) || !near(player, npc) || SafeDepositBox.beginAccess(player) != SafeDepositBox.Status.OK) return false
        player.attr[SAFE_NPC] = WeakReference(npc)
        ui.open(player, SafeDepositBox.contents(player) ?: return false)
        return true
    }
    private fun safeSession(player: Player): Boolean = player.attr[SAFE_NPC]?.get()?.let {
        isFinancial(it) && near(player, it) && safeUi?.visible(player) == true
    } == true
    fun closeSafe(player: Player) { player.attr.remove(SAFE_NPC); SafeDepositBox.endAccess(player) }
    fun deposit(player: Player, slot: Int, expectedId: Int, amount: Int): SafeDepositBox.Transfer {
        if (!safeSession(player)) { closeSafe(player); return SafeDepositBox.Transfer(SafeDepositBox.Status.NOT_OPEN) }
        return SafeDepositBox.deposit(player, slot, expectedId, amount).also { SafeDepositBox.contents(player)?.let { safeUi?.refresh(player, it) } }
    }
    fun depositWorn(player: Player, slot: Int, expectedId: Int, amount: Int): SafeDepositBox.Transfer {
        if (!safeSession(player)) { closeSafe(player); return SafeDepositBox.Transfer(SafeDepositBox.Status.NOT_OPEN) }
        return SafeDepositBox.depositWorn(player, slot, expectedId, amount).also { SafeDepositBox.contents(player)?.let { safeUi?.refresh(player, it) } }
    }
    fun withdraw(player: Player, slot: Int, expectedId: Int): SafeDepositBox.Transfer {
        if (!safeSession(player)) { closeSafe(player); return SafeDepositBox.Transfer(SafeDepositBox.Status.NOT_OPEN) }
        return SafeDepositBox.withdraw(player, slot, expectedId).also { SafeDepositBox.contents(player)?.let { safeUi?.refresh(player, it) } }
    }
    fun openRewards(player: Player, npc: Npc): Boolean {
        player.attr.remove(REWARD_NPC)
        val ui = rewardUi ?: return false
        val store = NigelRewardStore.active ?: return false
        if (!isNigel(npc) || !near(player, npc)) return false
        player.closeMainInterface()
        player.attr[REWARD_NPC] = WeakReference(npc); ui.open(player, store)
        return true
    }
    fun closeRewards(player: Player) { player.attr.remove(REWARD_NPC) }
    private fun store(player: Player): NigelRewardStore? {
        val npc = player.attr[REWARD_NPC]?.get() ?: return null
        if (!isNigel(npc) || !near(player, npc) || rewardUi?.visible(player) != true) {
            closeRewards(player); return null
        }
        return NigelRewardStore.active
    }
    fun buy(player: Player, reward: NigelRewardStore.Reward, amount: Int): NigelRewardStore.Result {
        val store = store(player) ?: return NigelRewardStore.Result.NOT_READY
        return store.buy(player, reward, amount).also { rewardUi?.refresh(player, store) }
    }
    fun sell(player: Player, slot: Int, expectedId: Int, amount: Int): NigelRewardStore.Result {
        val store = store(player) ?: return NigelRewardStore.Result.NOT_READY
        return store.sellBack(player, slot, expectedId, amount).also { rewardUi?.refresh(player, store) }
    }
}
