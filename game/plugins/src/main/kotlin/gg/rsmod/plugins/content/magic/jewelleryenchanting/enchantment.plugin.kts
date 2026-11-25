package gg.rsmod.plugins.content.magic.jewelleryenchanting

// One payment/slot/cooldown path for both the original spell packets and the Jewellery submenu.
JewelleryEnchantment.SPELLS.forEachIndexed { i, spell ->
    fun begin(player: Player) {
        val slot = player.getInteractingItemSlot()
        player.queue(TaskPriority.STRONG) {
            with(JewelleryEnchantment) { enchant(spell, slot) }
        }
    }
    on_spell_on_item(spell.interfaceId, spell.component) { begin(player) }
    on_spell_on_item(JewelleryEnchantment.INTERFACE, 3 + i) { begin(player) }
}
on_button(192, 101) { JewelleryEnchantment.open(player) }
on_button(JewelleryEnchantment.INTERFACE, JewelleryEnchantment.BACK) { JewelleryEnchantment.back(player) }
