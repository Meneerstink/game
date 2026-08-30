package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.plugins.content.inter.bank.openBank

private val BOOTHS = BankObjects.BOOTHS
private val BANK_CHESTS_USE = BankObjects.CHESTS_USE
private val BANK_CHESTS_BANK = BankObjects.CHESTS_BANK

BOOTHS.forEach { booth ->

    on_obj_option(obj = booth, option = "use") {
        player.openBank()
    }

    on_obj_option(obj = booth, option = "use-quickly") {
        player.openBank()
    }
}

BANK_CHESTS_USE.forEach { chest ->
    on_obj_option(obj = chest, "use") {
        player.openBank()
    }
}

BANK_CHESTS_BANK.forEach { chest ->
    on_obj_option(obj = chest, "bank") {
        player.openBank()
    }
}
