package gg.rsmod.plugins.content.combat.strategy.ranged

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment

/**
 * OSRS Wiki "Ava's device" (page + raw wikitext, fetched 2026-09-14).
 *
 * Ammunition retrieval per shot: attractor 60 % saved / 20 % dropped / 20 % broken, accumulator 72 % / 8 % / 20 %,
 * assembler 80 % / 0 % / 20 % ("will never drop any ammo on the ground").
 *
 * "The ammunition recovery effect does not work if the player is wearing metallic torso armour (most melee armour,
 * Ahrim's robetop, etc.) due to the metal causing interference with the magnet." [INTERFERING_TORSOS] is the page's
 * complete list of interfering body items, matched by item name so every id carrying that name is covered. Degraded
 * Barrows bodies carry a trailing charge number in their name ("Dharok's platebody 100"); that number is ignored because
 * it is the same armour. Names absent from this cache simply never match.
 */
object AvasDevices {
    val INTERFERING_TORSOS: Set<String> =
        setOf(
            "adamant chainbody", "adamant platebody", "adamant platebody (g)", "adamant platebody (t)", "ahrim's robetop",
            "black chainbody", "black platebody", "black platebody (g)", "black platebody (t)",
            "decorative armour (gold platebody)", "decorative armour (red platebody)", "decorative armour (white platebody)",
            "dharok's platebody", "dragon chainbody", "dragon platebody", "elite black platebody", "gilded platebody",
            "guthan's platebody", "guthix platebody", "iron chainbody", "iron platebody", "justiciar chestguard",
            "mithril chainbody", "mithril platebody", "rune chainbody", "rune platebody", "rune platebody (g)",
            "rune platebody (t)", "saradomin platebody", "steel platebody", "statius's platebody (bh)", "torag's platebody",
            "verac's brassard", "zamorak platebody",
        )

    private val DEGRADE_SUFFIX = Regex(" (0|25|50|75|100)$")

    fun interferes(torsoName: String?): Boolean =
        torsoName != null && DEGRADE_SUFFIX.replace(torsoName.lowercase(), "") in INTERFERING_TORSOS

    fun interferes(player: Player): Boolean {
        val body = player.getEquipment(EquipmentType.CHEST) ?: return false
        return interferes(player.world.definitions.get(ItemDef::class.java, body.id).name)
    }
}
