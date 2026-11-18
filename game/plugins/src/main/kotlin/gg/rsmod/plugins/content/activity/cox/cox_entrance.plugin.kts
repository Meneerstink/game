package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject

/*
 * CoX: the way to the Chambers of Xeric (dossier COX.txt TOEGANG; OSRS Wiki Chambers of Xeric 'Does not require a quest to access').
 * Fairy ring BLS (OSRS Wiki Fairy ring: "Kebos Lowlands: South of Mount Quidamortem", ring at 1295,3493, square 5174 imported in place by
 * OsrsRegionImportTool 'cox-entrance'). The ring there is OSRS loc 29495 FAIRYRING_MINORHUB - the same definition that came in without a
 * model, name or options at the Necropolis (toa_necropolis.plugin.kts, AKP), so it is replaced by the rev-667 'Fairy ring' 14142 (Use ->
 * dial, fairy_rings.plugin.kts) exactly like AKP.
 */
val BLS_RING = Tile(1295, 3493, 0)

on_world_init {
    CoxIds.loc(29495)?.let { broken -> world.getObject(BLS_RING, ObjectType.INTERACTABLE)?.takeIf { it.id == broken }?.let { world.remove(it) } }
    world.spawn(DynamicObject(14142, 22, 0, BLS_RING))
}
