package gg.rsmod.plugins.content.skills.fletching.javelins

val javelinData = JavelinData.values
val byProduct = JavelinData.byProduct

javelinData.forEach { data ->
    on_item_on_item(item1 = data.tip, item2 = data.shaft) {
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
    player.queue(TaskPriority.WEAK) { JavelinAction.attach(this, data, amount) }
}
