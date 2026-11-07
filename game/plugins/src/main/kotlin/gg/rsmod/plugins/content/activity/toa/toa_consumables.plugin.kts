package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.api.ext.getInteractingItemSlot

/* N462 ToA raid supplies: one option per dose (ToaConsumables). */
ToaConsumables.Kind.values().forEach { kind ->
    ToaConsumables.osrsDoses(kind).forEach { osrs ->
        ToaIds.item(osrs)?.let { local ->
            on_item_option(item = local, option = kind.option) {
                ToaConsumables.use(player, kind, local, player.getInteractingItemSlot())
            }
        }
    }
}

on_timer(ToaConsumables.SILK_TIMER) { ToaConsumables.silkTick(player) }
on_timer(ToaConsumables.SCARAB_TIMER) { ToaConsumables.scarabTick(player) }
on_timer(ToaConsumables.SALTS_TIMER) { ToaConsumables.saltsTick(player) }
