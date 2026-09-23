/**
 * Owner-only item census. It cross-references every cached inventory and worn
 * option with the registered item handlers and records engine-owned options
 * separately in the CSV.
 */
import gg.rsmod.game.model.priv.Privilege

on_command("item_inventory", Privilege.OWNER_POWER) {
    val summary = gg.rsmod.game.model.item.ItemCensus.writeCsv(world)
    player.message(summary, type = ChatMessageType.CONSOLE)
}
