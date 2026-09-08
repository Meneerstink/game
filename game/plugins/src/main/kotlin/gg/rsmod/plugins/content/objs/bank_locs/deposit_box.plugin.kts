package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.content.inter.bank.openDepositBox

/**
 * @author Alycia <https://github.com/alycii>
 *
 * Every bank deposit box in the cache, not a hand-picked pair.
 *
 * Only objects 2045 and 2132 used to be bound, so the other fourteen - including the ones actually
 * placed around Edgeville and most of the members' world - answered with `Nothing interesting
 * happens`. The full set is `./gradlew :game:runObjectDefProbeTool --args="<cache> name deposit
 * box"` -> 16 objects (2045, 2132, 2133, 6836, 9398, 15985, 20228, 24995, 25937, 26969, 32924,
 * 32930, 32931, 34755, 36788, 39830), every one of them carrying `Deposit` as option 1.
 *
 * The name predicate is deliberately narrower than "has a Deposit option": that wider query also
 * returns log piles, smelters, the Coin Collector, a food chute and the Trade wagon, none of which
 * are banking objects.
 *
 * Not bound here: object 39830's second option, `Deposit-all`. It is the only deposit box with one,
 * and it means "deposit the whole inventory without opening the screen", which is separate content.
 */

world.definitions.getAllKeys(ObjectDef::class.java).forEach { obj ->
    val def = world.definitions.get(ObjectDef::class.java, obj)
    if (!def.name.contains("deposit box", ignoreCase = true)) {
        return@forEach
    }
    if (def.options.none { it.equals("Deposit", ignoreCase = true) }) {
        return@forEach
    }
    on_obj_option(obj = obj, option = "Deposit") {
        player.openDepositBox()
    }
}
