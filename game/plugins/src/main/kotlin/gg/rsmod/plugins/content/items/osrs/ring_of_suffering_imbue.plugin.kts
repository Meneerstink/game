package gg.rsmod.plugins.content.items.osrs

/**
 * A11: use a Ring of suffering or Ring of suffering (r) on Perdu (GE home) to imbue it for the 78 Store's price, charges and recoil
 * setting kept (rules, donor and owner decisions in [RingOfSufferingImbue]). Perdu's other use-item bindings (Trouver unlock, broken
 * item repair) are in mechanics/trouver/trouver.plugin.kts; these ring ids are in neither list.
 */
RingOfSufferingImbue.IMBUABLE.forEach { ringId ->
    on_item_on_npc(item = ringId, npc = Npcs.PERDU) {
        val slot = player.getInteractingItemSlot()
        player.queue { RingOfSufferingImbue.dialogue(this, slot, ringId) }
    }
}
