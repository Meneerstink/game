package gg.rsmod.plugins.content.skills.mining

val rockValues = RockType.values
val rockObjects = RockType.objects
val depletedRockSet =
    rockObjects
        .map { id ->
            world.definitions.get(ObjectDef::class.java, id).depleted
        }.toSet()

rockObjects.forEach { rock ->
    on_obj_option(obj = rock, option = 1) {
        val obj = player.getInteractingGameObj()
        val rockType = rockValues.find { obj.id in it.objectIds } ?: return@on_obj_option
        player.queue {
            Mining.mineRock(this, obj, rockType)
        }
    }
}
depletedRockSet.forEach { depletedRock ->
    on_obj_option(obj = depletedRock, option = 1) {
        player.animate(Anims.RESET)
        player.playSound(Sfx.PROSPECT)
        player.filterableMessage("There is currently no ore available in this rock.")
    }
}

// K12 N430: the worn Bracelet of clay's "Check" (dead before, census 2026-10-30).
on_equipment_option(item = Items.BRACELET_OF_CLAY, option = "Check") {
    player.message(BraceletOfClay.checkMessage(player))
}
