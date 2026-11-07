package gg.rsmod.plugins.content.skills.mining

import gg.rsmod.plugins.content.activity.toa.ToaIds

/*
 * N462: mining the OSRS rocks imported with the Necropolis ([OsrsImportedRocks]) with the normal [Mining.mineRock]; the empty rock
 * (OSRS ROCKS1/ROCKS2, imported by OsrsLocImportTool 'necropolis-rocks') is set as each rock's depleted form, so the rock empties and
 * respawns exactly like the rev-667 rocks (Mining.kt reads ObjectDef.depleted).
 */
OsrsImportedRocks.TYPES.forEach { (osrs, type) ->
    val local = ToaIds.loc(osrs) ?: return@forEach
    if (local in RockType.objects) return@forEach // a rev-667 id already bound by mining.plugin.kts
    OsrsImportedRocks.depletedOsrs(osrs)?.let(ToaIds::loc)?.let { empty ->
        world.definitions.get(ObjectDef::class.java, local).depleted = empty
    }
    on_obj_option(obj = local, option = 1) {
        val obj = player.getInteractingGameObj()
        player.queue { Mining.mineRock(this, obj, type) }
    }
}
OsrsImportedRocks.depletedLocal.forEach { empty ->
    on_obj_option(obj = empty, option = 1) {
        player.animate(Anims.RESET)
        player.playSound(Sfx.PROSPECT)
        player.filterableMessage("There is currently no ore available in this rock.")
    }
}
