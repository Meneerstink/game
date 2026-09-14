package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService

private val BOOTHS = BankObjects.BOOTHS
private val BANK_CHESTS_USE = BankObjects.CHESTS_USE
private val BANK_CHESTS_BANK = BankObjects.CHESTS_BANK

/**
 * Every bank object that really carries a `Collect` option, taken from the cache rather than
 * listed by hand.
 *
 * `on_obj_option` resolves the option index from the definition and refuses to bind an option the
 * object does not have, which would stop the server booting; asking the definitions directly means
 * this keeps working if the bank object set changes. In the current cache this is all 18 booths and
 * counters (option 3) plus the Grand Exchange bank chest 20607 (option 2).
 */
private val COLLECTORS =
    BankObjects.ALL.filter { obj ->
        world.definitions
            .get(ObjectDef::class.java, obj)
            .options
            .any { it?.lowercase() == "collect" }
    }

BOOTHS.forEach { booth ->

    on_obj_option(obj = booth, option = "use") {
        player.openBank()
    }

    on_obj_option(obj = booth, option = "use-quickly") {
        player.openBank()
    }
}

/*
 * RCV-012.B16: `Collect` opens the Grand Exchange collection box (Novite ObjectHandler "counter" ->
 * ExchangeManagement.openCollectionBox; native interface 109, GrandExchangeInterface). The instant
 * payout it used before the box existed stays on the ::ge_collect command (GrandExchangeCollection).
 */
COLLECTORS.forEach { collector ->

    on_obj_option(obj = collector, option = "collect") {
        if (player.world.getService(GrandExchangeService::class.java) == null) {
            player.message("The Grand Exchange is unavailable right now.")
            return@on_obj_option
        }
        GrandExchangeInterface.openCollectionBox(player)
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
