package gg.rsmod.plugins.content.items.packs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * RCV-010: armour harnesses carry the cache inventory op "Unpack" (items 9666/9668/9670) but no handler existed; they
 * were only reachable through the Grand Exchange sets screen, which the 667 set roster (enum 1087) does not include.
 *
 * Contents and behaviour: Novite 667 `ArmourSets.Sets` (PROSELYTE_LG/SK, INITIATE_SET) and
 * `ArmourSetOpening.handleSetOpening` (needs `components - 1` free slots, removes the harness, adds each part once).
 */
enum class ArmourHarness(val id: Int, vararg val components: Int) {
    PROSELYTE_M(Items.PROSYTE_HARNESS_M, 9672, 9674, 9676),
    INITIATE_M(Items.INITIATE_HARNESS_M, 5574, 5575, 5576),
    PROSELYTE_F(Items.PROSYTE_HARNESS_F, 9672, 9674, 9678),
    ;

    companion object {
        fun forId(id: Int): ArmourHarness? = values().firstOrNull { it.id == id }

        /** Returns true when the harness was unpacked. */
        fun unpack(
            player: Player,
            slot: Int,
        ): Boolean {
            val item = player.inventory[slot] ?: return false
            val harness = forId(item.id) ?: return false
            val needed = harness.components.size - 1
            if (player.inventory.freeSlotCount < needed) {
                player.message("You need ${needed - player.inventory.freeSlotCount} more free inventory slots to open this armour set.")
                return false
            }
            if (!player.inventory.remove(item.id, 1, beginSlot = slot).hasSucceeded()) return false
            harness.components.forEach { player.inventory.add(it, 1) }
            return true
        }
    }
}
