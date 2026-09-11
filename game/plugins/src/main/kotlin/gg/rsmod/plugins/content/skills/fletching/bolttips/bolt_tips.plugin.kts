package gg.rsmod.plugins.content.skills.fletching.bolttips

val boltTipData = BoltTipData.values
val byGem = BoltTipData.byGem

boltTipData.forEach { data ->
    on_item_on_item(item1 = Items.CHISEL, item2 = data.gem) {
        if (player.inventory.getItemCount(data.gem) == 1) {
            cutBoltTip(player, data.gem, 1)
            return@on_item_on_item
        }
        player.queue {
            produceItemBox(
                data.gem,
                option = SkillDialogueOption.CUT,
                title = "Choose how many you wish to cut,<br>then click on the item to begin.",
                logic = ::cutBoltTip,
            )
        }
    }
}

fun cutBoltTip(
    player: Player,
    item: Int,
    amount: Int,
) {
    val data = byGem[item] ?: return
    player.queue(TaskPriority.WEAK) { BoltTipAction.cut(this, data, amount) }
}
