package gg.rsmod.plugins.content.items

/*
 * N431 + N432 (owner 2026-10-30, V7): Iban's staff (OSRS: 120 casts, recharged at the well) and the gloves of silence (OSRS: 62
 * uses, repaired with dark kebbit fur) do NOT wear out here; their cache "Check" option only says so. CUSTOM wording. The shared
 * item guard (ItemActionGuard, STATUS_OPTIONS) shows the line in the item dialogue.
 */
val IBANS_STAFF_CHECK = "Your staff has unlimited charges here."
val GLOVES_OF_SILENCE_CHECK = "Your gloves of silence never wear out here."

on_item_option(item = Items.IBANS_STAFF, option = "check") {
    player.message(IBANS_STAFF_CHECK)
}

on_item_option(item = Items.GLOVES_OF_SILENCE, option = "check") {
    player.message(GLOVES_OF_SILENCE_CHECK)
}

on_equipment_option(item = Items.GLOVES_OF_SILENCE, option = "Check") {
    player.message(GLOVES_OF_SILENCE_CHECK)
}
