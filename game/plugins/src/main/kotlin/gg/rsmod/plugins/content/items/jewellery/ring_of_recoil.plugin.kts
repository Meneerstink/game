package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.plugins.content.mechanics.combatresponse.RingOfRecoil

// K12 N11: the worn ring's "Check" was a dead option (census 2026-10-30) - the damage it can still reflect.
on_equipment_option(item = Items.RING_OF_RECOIL, option = "Check") {
    player.message(RingOfRecoil.checkMessage(player))
}
