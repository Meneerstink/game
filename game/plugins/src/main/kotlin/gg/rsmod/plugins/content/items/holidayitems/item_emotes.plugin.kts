package gg.rsmod.plugins.content.items.holidayitems

import gg.rsmod.game.model.attr.AttributeKey

/*
 * N11 (2026-10-31, item-option census): three toys sold in the stores did nothing - "Fly" on the Toy kite (inventory and worn),
 * "Dance" on the Rubber chicken (inventory and worn) and "Emote" on the worn Reindeer hat. 667 Void donor ItemEmotes.kt:
 * emote_fly_kite 8990, emote_chicken_dance 1835, emote_reindeer 5059 with gfx 859 + 263 (emotes.anims/gfx.toml); like Void, a
 * second click while the emote still plays is refused.
 */

val EMOTE_UNTIL = AttributeKey<Int>()

/** Plays [anim] (and [gfx]) unless the previous item emote is still running ([ticks] long). */
fun gg.rsmod.game.model.entity.Player.itemEmote(anim: Int, ticks: Int, vararg gfx: Int) {
    if ((attr[EMOTE_UNTIL] ?: 0) > world.currentCycle) {
        message("Please wait till you've finished performing your current emote.")
        return
    }
    attr[EMOTE_UNTIL] = world.currentCycle + ticks
    animate(anim)
    gfx.forEach { graphic(it) }
}

on_item_option(item = Items.TOY_KITE, option = "fly") { player.itemEmote(8990, ticks = 8) }
on_equipment_option(item = Items.TOY_KITE, option = "fly") { player.itemEmote(8990, ticks = 8) }

on_item_option(item = Items.RUBBER_CHICKEN, option = "dance") { player.itemEmote(1835, ticks = 8) }
on_equipment_option(item = Items.RUBBER_CHICKEN, option = "dance") { player.itemEmote(1835, ticks = 8) }

on_equipment_option(item = Items.REINDEER_HAT, option = "emote") { player.itemEmote(5059, ticks = 6, 859, 263) }
