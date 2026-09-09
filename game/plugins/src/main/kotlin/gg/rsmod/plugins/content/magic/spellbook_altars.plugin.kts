package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.priv.Privilege

/**
 * Spellbook switching altars.
 *
 * Ancient altar (Jaldraocht pyramid, object 6552) toggles Standard <-> Ancient Magicks, the
 * Astral altar (Lunar Isle, object 17010) toggles Standard <-> Lunar. Object ids sourced from the
 * 2009scape MagicAltarListener. The Ferox home altar menu (prayer_altar.plugin.kts) offers the same
 * switch from home, and ::spellbook <standard|ancient|lunar> exists for testing.
 */

on_obj_options(Objs.ALTAR_6552, options = arrayOf("pray-at", "pray")) {
    player.queue { Spellbooks.toggle(player, Spellbook.ANCIENT) }
}

on_obj_options(Objs.ALTAR_17010, options = arrayOf("pray-at", "pray")) {
    player.queue { Spellbooks.toggle(player, Spellbook.LUNAR) }
}

on_command("spellbook", Privilege.ADMIN_POWER) {
    val arg = player.getCommandArgs().firstOrNull()?.lowercase() ?: "standard"
    val book =
        when (arg) {
            "ancient", "ancients" -> Spellbook.ANCIENT
            "lunar", "lunars" -> Spellbook.LUNAR
            else -> Spellbook.STANDARD
        }
    Spellbooks.select(player, book)
}
