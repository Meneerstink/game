package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/** Keeps authentic non-rune ingredients while making spellbook teleports rune-free. */
object RuneFreeTeleportRequirements {
 private val runeIds =
 setOf(
 Items.FIRE_RUNE,
 Items.WATER_RUNE,
 Items.AIR_RUNE,
 Items.EARTH_RUNE,
 Items.MIND_RUNE,
 Items.BODY_RUNE,
 Items.DEATH_RUNE,
 Items.NATURE_RUNE,
 Items.CHAOS_RUNE,
 Items.LAW_RUNE,
 Items.COSMIC_RUNE,
 Items.BLOOD_RUNE,
 Items.SOUL_RUNE,
 Items.STEAM_RUNE,
 Items.MIST_RUNE,
 Items.DUST_RUNE,
 Items.SMOKE_RUNE,
 Items.MUD_RUNE,
 Items.LAVA_RUNE,
 Items.ASTRAL_RUNE,
 )

 fun isRune(itemId: Int): Boolean = itemId in runeIds

 fun nonRuneRequirements(requirements: List<Item>): List<Item> =
 requirements.filterNot { isRune(it.id) }
}
