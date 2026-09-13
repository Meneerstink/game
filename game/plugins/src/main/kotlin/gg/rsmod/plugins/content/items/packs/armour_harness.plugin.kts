package gg.rsmod.plugins.content.items.packs

/** RCV-010: "Unpack" on every armour harness (see [ArmourHarness]). */
ArmourHarness.values().forEach { harness ->
    on_item_option(item = harness.id, option = "Unpack") {
        ArmourHarness.unpack(player, player.getInteractingItemSlot())
    }
}
