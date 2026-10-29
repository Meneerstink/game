package gg.rsmod.plugins.content.mechanics.lightsource

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items

/**
 * @author Alycia <https://github.com/alycii>
 */
enum class LightSource(
    val raw: Int,
    val product: Int,
    val levelRequired: Int,
    val enclosed: Boolean,
    val interfaceId: Int,
) {
    UNLIT_TORCH(
        raw = Items.UNLIT_TORCH,
        product = Items.LIT_TORCH,
        levelRequired = 1,
        enclosed = false,
        interfaceId = 98,
    ),
    CANDLE(raw = Items.CANDLE, product = Items.LIT_CANDLE, levelRequired = 1, enclosed = false, interfaceId = 98),
    BLACK_CANDLE(
        raw = Items.BLACK_CANDLE,
        product = Items.LIT_BLACK_CANDLE,
        levelRequired = 1,
        enclosed = false,
        interfaceId = 98,
    ),
    // N419: the raw source is the filled, unlit item - 4527 is the empty lantern from glassblowing, 4535 the unfuelled oil lantern.
    CANDLE_LANTERN(
        raw = Items.CANDLE_LANTERN_4529,
        product = Items.CANDLE_LANTERN_4531,
        levelRequired = 4,
        enclosed = false,
        interfaceId = 98,
    ),
    CANDLE_LANTERN_4532(
        raw = Items.CANDLE_LANTERN_4532,
        product = Items.CANDLE_LANTERN_4534,
        levelRequired = 4,
        enclosed = false,
        interfaceId = 98,
    ),
    OIL_LAMP(
        raw = Items.OIL_LAMP,
        product = Items.OIL_LAMP_4524,
        levelRequired = 12,
        enclosed = false,
        interfaceId = 98,
    ),
    OIL_LANTERN(
        raw = Items.OIL_LANTERN_4537,
        product = Items.OIL_LANTERN_4539,
        levelRequired = 26,
        enclosed = false,
        interfaceId = 98,
    ),
    BULLSEYE_LANTERN(
        raw = Items.BULLSEYE_LANTERN_4548,
        product = Items.BULLSEYE_LANTERN_4550,
        levelRequired = 49,
        enclosed = false,
        interfaceId = 98,
    ),
    SAPPHIRE_LANTERN(
        raw = Items.SAPPHIRE_LANTERN_4701,
        product = Items.SAPPHIRE_LANTERN_4702,
        levelRequired = 49,
        enclosed = false,
        interfaceId = 98,
    ),
    EMERALD_LANTERN(
        raw = Items.EMERALD_LANTERN,
        product = Items.EMERALD_LANTERN_9065,
        levelRequired = 49,
        enclosed = false,
        interfaceId = 98,
    ),
    MINING_HELMET(
        raw = Items.MINING_HELMET_5014,
        product = Items.MINING_HELMET,
        levelRequired = 65,
        enclosed = false,
        interfaceId = 98,
    ),
    ;

    companion object {
        fun getActiveLightSource(player: Player): LightSource? {
            // K03 N410: the Firemaking cape (and the max cape) "Acts as an inextinguishable light source" (OSRS Wiki "Cape of
            // Accomplishment"); it lights the way like a lit torch (same overlay).
            if (gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.FIREMAKING)) {
                return UNLIT_TORCH
            }
            for (source in values()) {
                // N419: a worn light (the lit mining helmet) lights the way as well as a carried one.
                if (player.inventory.contains(source.product) || player.equipment.contains(source.product)) {
                    return source
                }
            }
            return null
        }
    }
}
