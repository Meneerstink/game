package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig

/** New-account rewards whose values are confirmed product requirements. */
object NewPlayerStart {
    /**
     * Owner 2026-10-10: 1m (was 50k) - the same as one vote. Owner 2026-10-22 (N179): it comes out of the Deadman starter pack
     * into the inventory, no longer into the bank at account creation.
     */
    const val STARTER_CASH = 1_000_000

    /** The Deadman starter pack has been handed out (saved: replaying the introduction never gives a second one). */
    val STARTER_PACK_GIVEN = AttributeKey<Boolean>(persistenceKey = "deadman_starter_pack_given")

    /**
     * Owner 2026-10-08: a new player starts with an empty inventory and gets only the Deadman starter pack (OSRS item 22330,
     * here [NewPlayerConfig.starterChestItem]) once the introduction is over - finished, skipped or interrupted.
     */
    fun grantStarterPack(player: Player) {
        if (player.attr[STARTER_PACK_GIVEN] == true) return
        val pack = NewPlayerConfig.current.starterChestItem
        if (!player.inventory.add(pack).hasSucceeded() && !player.bank.add(pack).hasSucceeded()) return
        player.attr[STARTER_PACK_GIVEN] = true
    }

    /** "Open": the pack becomes its contents ([NewPlayerConfig.starterChestContents]), all or nothing. */
    fun openStarterPack(player: Player) {
        val config = NewPlayerConfig.current
        if (!player.inventory.contains(config.starterChestItem)) return
        val slots =
            config.starterChestContents.sumOf { (item, amount) ->
                val def = player.world.definitions.get(ItemDef::class.java, item)
                if (def.stackable || def.noted) 1 else amount
            }
        if (player.inventory.freeSlotCount + 1 < slots) {
            player.message("You need $slots free inventory spaces to open this.")
            return
        }
        if (!player.inventory.remove(config.starterChestItem, assureFullRemoval = true).hasSucceeded()) return
        config.starterChestContents.forEach { (item, amount) -> player.inventory.add(item, amount) }
    }
}
