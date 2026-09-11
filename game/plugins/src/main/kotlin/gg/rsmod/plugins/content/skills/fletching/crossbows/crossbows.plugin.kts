package gg.rsmod.plugins.content.skills.fletching.crossbows

val crossbowData = CrossbowData.values
val byUnstrung = CrossbowData.byUnstrung
val byStrung = CrossbowData.byStrung

val crossbowAction = CrossbowAction(world.definitions)

crossbowData.forEach { data ->
    on_item_on_item(item1 = data.stock, item2 = data.limbs) {
        player.queue {
            produceItemBox(
                data.unstrung,
                option = SkillDialogueOption.MAKE,
                title = "Choose how many you wish to make, then<br>click on the chosen item to begin.",
                names = arrayOf("Crossbow (u)"),
                logic = ::assembleCrossbow,
            )
        }
    }

    on_item_on_item(item1 = data.unstrung, item2 = Items.CROSSBOW_STRING) {
        player.queue {
            produceItemBox(
                data.strung,
                option = SkillDialogueOption.MAKE,
                title = "Choose how many you wish to make, then<br>click on the chosen item to begin.",
                names = arrayOf(data.itemName),
                logic = ::stringCrossbow,
            )
        }
    }
}

fun assembleCrossbow(
    player: Player,
    item: Int,
    amount: Int,
) {
    val data = byUnstrung[item] ?: return
    player.queue(TaskPriority.WEAK) { crossbowAction.assemble(this, data, amount) }
}

fun stringCrossbow(
    player: Player,
    item: Int,
    amount: Int,
) {
    val data = byStrung[item] ?: return
    player.queue(TaskPriority.WEAK) { crossbowAction.string(this, data, amount) }
}
