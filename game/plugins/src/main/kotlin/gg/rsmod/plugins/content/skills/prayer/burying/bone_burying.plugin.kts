package gg.rsmod.plugins.content.skills.prayer.burying


val boneData = BoneData.values
val definitions = BoneData.boneDefinitions
val bones = boneData.map { it.bone }.toTypedArray()

bones.forEach { bone ->
    on_item_option(bone, option = "bury") {
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot] ?: return@on_item_option
        if (held.id != bone) return@on_item_option
        player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {

            // send the message, animate and play the sound
            player.filterableMessage("You dig a hole in the ground...")
            player.animate(Anims.REACH_DOWN)
            player.playSound(2738)

            // wait 2 ticks for the animation to play
            wait(2)

            // remove the bones, and add experience
            if (player.inventory[slot] === held && player.inventory.remove(item = bone, beginSlot = slot).hasSucceeded()) {
                player.filterableMessage("You bury the bones.")
                player.addXp(Skills.PRAYER, definitions[bone]!!.experience, checkBrawlingGloves = true)
            }
        }
    }
}
