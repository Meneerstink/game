package gg.rsmod.plugins.content.skills.fletching.tippedbolts

val tippedBoltData = TippedBoltData.values
val byProduct = TippedBoltData.byProduct

tippedBoltData.forEach { data ->
    on_item_on_item(item1 = data.tip, item2 = data.plainBolt) {
        player.queue {
            produceItemBox(
                data.product,
                option = SkillDialogueOption.MAKE,
                title = "Choose how many you wish to make, then<br>click on the chosen item to begin.",
                logic = ::attachTip,
            )
        }
    }
}

fun attachTip(
    player: Player,
    item: Int,
    amount: Int,
) {
    val data = byProduct[item] ?: return
    player.queue(TaskPriority.WEAK) { TippedBoltAction.attach(this, data, amount) }
}
