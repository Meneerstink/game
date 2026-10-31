package gg.rsmod.plugins.content.skills.prayer.scattering


val ashData = AshData.values
val definitions = AshData.ashDefinitions
val ashes = ashData.map { it.ash }.toTypedArray()

ashes.forEach { ash ->
    if (!world.plugins.hasItemOption(ash, 1)) on_item_option(ash, option = "scatter") {
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot]?.takeIf { it.id == ash } ?: return@on_item_option
        player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {

            player.animate(Anims.SCATTER_ASHES)
            player.graphic(definitions[ash]!!.gfx)
            wait(2)

            if (player.inventory[slot] === held && player.inventory.remove(item = ash, beginSlot = slot).hasSucceeded()) {
                player.filterableMessage("You scatter the ashes in the wind.")
                player.addXp(Skills.PRAYER, definitions[ash]!!.experience, checkBrawlingGloves = true)
            }
        }
    }
}
