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

/*
 * Owner 2026-09-24 ("controleer alle bankers ... alle banks in onze rsps! pest control bank"): the hand list above missed real bank
 * booths - the Void Knights' Outpost booth 14369 and Port Phasmatys' 5276 answered "Nothing interesting happens" (unhandled-actions
 * log). Every other bank object is now served from its cache definition: a loc named "Bank booth" / "Bank chest" / "Bank counter"
 * (or a "Counter" that advertises "Use-quickly") opens the bank on Use / Use-quickly / Bank and the collection box on Collect. It is
 * an object FALLBACK - consulted only when no explicit handler is bound - so it can never double-bind or override a special bank.
 */
val BANK_OBJECT_NAMES = setOf("bank booth", "bank chest", "bank counter")
val BANK_OPEN_OPTIONS = setOf("use", "use-quickly", "bank")

world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    val options = def.options.map { it?.lowercase() }
    val name = def.name.lowercase()
    if (name !in BANK_OBJECT_NAMES && !(name == "counter" && "use-quickly" in options)) return@bindObjectFallback false
    when (options.getOrNull(opt - 1)) {
        in BANK_OPEN_OPTIONS -> {
            player.openBank()
            true
        }
        "collect" -> {
            GrandExchangeInterface.openCollectionBox(player)
            true
        }
        else -> false
    }
}

/* The "Closed chest" pieces standing in the Burgh de Rott (12768) and Port Phasmatys (5272) banks open and close like every chest. */
listOf(Objs.CLOSED_CHEST_12768 to Objs.OPEN_CHEST_12769, Objs.CLOSED_CHEST_5272 to Objs.OPEN_CHEST_5273).forEach { (closed, open) ->
    on_obj_option(closed, "Open") {
        val chest = player.getInteractingGameObj()
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(Anims.REACH_FORWARD)
            wait(2)
            world.spawn(DynamicObject(open, chest.type, chest.rot, chest.tile))
        }
    }
    on_obj_option(open, "Close") {
        val chest = player.getInteractingGameObj()
        player.lockingQueue(lockState = LockState.FULL) {
            player.animate(Anims.REACH_FORWARD)
            wait(2)
            world.spawn(DynamicObject(closed, chest.type, chest.rot, chest.tile))
        }
    }
    on_obj_option(open, "Search") {
        player.message("You search the chest and find nothing.")
    }
}
