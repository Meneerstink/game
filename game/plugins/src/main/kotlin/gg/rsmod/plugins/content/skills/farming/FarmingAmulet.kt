package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.farmingManager
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.skills.farming.data.Patch
import gg.rsmod.plugins.content.skills.farming.data.SeedType
import gg.rsmod.plugins.content.skills.farming.logic.PatchState

/** RS Wiki Amulet of farming: one player-wide binding, allotments/flowers only, one charge per status report. */
object FarmingAmulet {
    val BOUND_PATCH = AttributeKey<Int>("farming_amulet_patch")
    val ids = (1..8).map { Items.AMULET_OF_FARMING_1 + (it - 1) * 2 }

    fun canBind(patch: Patch): Boolean = patch.seedTypes.any { it == SeedType.Allotment || it == SeedType.Flower }

    fun bind(player: Player, patch: Patch) {
        if (!canBind(patch)) {
            player.message("You can only bind this amulet to an allotment or flower patch.")
            return
        }
        player.attr[BOUND_PATCH] = patch.id
        player.message("You bind the amulet to this ${patch.patchName}.")
    }

    fun bound(player: Player): Patch? = player.attr[BOUND_PATCH]?.let { Patch.byPatchId(it) }?.takeIf { canBind(it) }

    fun status(state: PatchState): String = when {
        state.isDead -> "Your patch has died."
        state.isDiseased -> "Your patch is diseased."
        state.isPlantFullyGrown -> "Your patch is fully grown."
        state.isEmpty || state.isWeedy -> "Your patch is empty."
        else -> "Your patch is growing."
    }

    fun useCharge(container: ItemContainer, slot: Int, held: Item): Boolean {
        if (held.id !in ids || held.amount != 1 || container[slot] !== held) return false
        container[slot] = if (held.id == Items.AMULET_OF_FARMING_1) null else Item(held.id - 2).copyAttr(held)
        return true
    }

    fun report(player: Player, container: ItemContainer, slot: Int, held: Item, patch: Patch) {
        // A rebind during dialogue must not consume a charge for a different patch.
        if (bound(player) != patch || !useCharge(container, slot, held)) return
        player.message(status(player.farmingManager().getPatchManager(patch).state))
        if (held.id == Items.AMULET_OF_FARMING_1) player.message("Your amulet of farming crumbles to dust.")
    }
}
