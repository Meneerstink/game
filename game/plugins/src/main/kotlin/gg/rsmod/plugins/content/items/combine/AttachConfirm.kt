package gg.rsmod.plugins.content.items.combine

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.ext.confirmItemAction
import gg.rsmod.plugins.content.items.ItemActionGuard

/**
 * N444 (owner 2026-10-30: "basilik jaw op een neitiznot maakt geen geluid geeft ook geen melding van are you sure"): attaching a part
 * asks "Are you sure" in the same item GUI as taking it apart (ItemActionGuard, owner 2026-09-18: every Dismantle / Disassemble /
 * Revert asks) and as the ornament kits (CombinationAction). The rule is the mirror of the guard's: an attach route asks when its
 * result can be taken apart again - the result's own inventory menu in the cache has Dismantle, Disassemble or Revert - so the
 * player is asked on both ways of the same change (Neitiznot faceguard, Elidinis' ward (f), slayer helmet ...). An attach that
 * cannot be undone keeps OSRS's own behaviour.
 *
 * Every item-on-item attach route registers itself with [route] next to its `on_item_on_item` (the guard test
 * `AttachConfirmTests` fails when one calls [ItemAttachSounds.attached] without it); [install] puts one interceptor in front of
 * every item-on-item route ([PluginRepository.itemOnItemInterceptor]).
 */
object AttachConfirm {
    data class Route(val part: Int, val target: Int, val result: Int, val ready: (Player) -> Boolean)

    private val routes = HashMap<Long, Route>()

    private fun key(
        a: Int,
        b: Int,
    ): Long = (maxOf(a, b).toLong() shl 32) or minOf(a, b).toLong()

    /**
     * Item [part] used on item [target] makes [result] (either way round). [ready] repeats the route's own requirements (levels,
     * runes, the other parts): only a player who can really attach is asked - one who cannot gets the route's own refusal at once.
     */
    fun route(
        part: Int,
        target: Int,
        result: Int,
        ready: (Player) -> Boolean = { true },
    ) {
        routes[key(part, target)] = Route(part, target, result, ready)
    }

    fun routeOf(
        item1: Int,
        item2: Int,
    ): Route? = routes[key(item1, item2)]

    /** The result's inventory menu has an option that takes it apart again (ItemActionGuard's options, Uncharge excepted). */
    fun undoable(def: ItemDef?): Boolean =
        def?.inventoryMenu?.any { it != null && it.lowercase() in ItemActionGuard.CONFIRM_OPTIONS && it.lowercase() != "uncharge" } == true

    fun question(partName: String): String = "Are you sure you want to attach the ${partName.lowercase()} to this item?"

    fun detail(partName: String): String = "The ${partName.lowercase()} will be attached to this item."

    fun install(repository: PluginRepository) {
        repository.itemOnItemInterceptor = interceptor@{ player, item1, item2, proceed ->
            val route = routeOf(item1, item2) ?: return@interceptor false
            val defs = player.world.definitions
            if (!undoable(defs.getNullable(ItemDef::class.java, route.result)) || !route.ready(player)) return@interceptor false
            val partName = defs.getNullable(ItemDef::class.java, route.part)?.name ?: return@interceptor false
            val slot = player.attr[INTERACTING_ITEM_SLOT]
            val otherSlot = player.attr[OTHER_ITEM_SLOT_ATTR]
            player.queue {
                if (!confirmItemAction(route.target, question(partName), detail(partName))) return@queue
                // Both parts must still be in the backpack (one may have been moved or dropped while the GUI was open).
                if (!player.inventory.contains(route.part) || !player.inventory.contains(route.target)) return@queue
                if (slot != null) player.attr[INTERACTING_ITEM_SLOT] = slot
                if (otherSlot != null) player.attr[OTHER_ITEM_SLOT_ATTR] = otherSlot
                proceed()
            }
            true
        }
    }
}
