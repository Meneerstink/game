package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface

/*
 * N462 (owner 2026-11-07 'dat hele gebied correct integreren net zoals osrs'): Cam the Bank Camel in the Jaltevas pyramid lobby.
 * In OSRS the camel is SCENERY, not an npc: cache2686 square 13454 places toa_bank_camel_parent 46223 at 3352,9119 (plane 0), a multiloc
 * on varbit 14446 -> toa_bank_camel 46075 "Bank Camel" / toa_bank_camel_named 46231 "Cam the Camel" (both ops Bank, Collect, model 46371;
 * loc.sym). Wiki Bank Camel: options "Bank, Collect" (map pin x:3352,y:9119). The 667 import keeps only the parent (no varbit
 * transform), so the server places the default variant 46075 on the parent's spot - the same as toa_necropolis.plugin.kts does for the
 * pyramid entrance. 'Collect' = the Grand Exchange collection box like every Bank+Collect banker (banker_collect.plugin.kts).
 * ONZEKER: which varbit 14446 value shows 'Cam the Camel' (46231) - the default 46075 is used.
 */
val CAMEL_TILE = Tile(3352, 9119, 0)

on_world_init {
    val camel = ToaIds.loc(46075) ?: return@on_world_init
    val parentId = ToaIds.loc(46223) ?: return@on_world_init
    // shape 11 (diagonal interactable, rot 3) - found by id, not by layer
    val parent = world.chunks.getOrCreate(CAMEL_TILE)
        .getEntities<gg.rsmod.game.model.entity.GameObject>(CAMEL_TILE, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
        .firstOrNull { it.id == parentId } ?: return@on_world_init
    world.remove(parent)
    world.spawn(DynamicObject(camel, parent.type, parent.rot, CAMEL_TILE))
}

listOfNotNull(ToaIds.loc(46075), ToaIds.loc(46231)).forEach { camel ->
    on_obj_option(obj = camel, option = "bank") { player.openBank() }
    on_obj_option(obj = camel, option = "collect") { GrandExchangeInterface.openCollectionBox(player) }
}
