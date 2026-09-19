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
    /**
     * Every item that works as Ava's assembler: the assembler and its (l), the Masori assembler ("a cosmetic variant of Ava's
     * assembler") and the assembler max capes ("it only acts as a cosmetic upgrade to Ava's assembler"), each with its (l).
     */
    val ASSEMBLERS: Set<Int> =
        setOf(
            gg.rsmod.plugins.api.cfg.Items.AVAS_ASSEMBLER, gg.rsmod.plugins.api.cfg.Items.AVAS_ASSEMBLER_L,
            gg.rsmod.plugins.api.cfg.Items.MASORI_ASSEMBLER, gg.rsmod.plugins.api.cfg.Items.MASORI_ASSEMBLER_L,
            gg.rsmod.plugins.api.cfg.Items.ASSEMBLER_MAX_CAPE, gg.rsmod.plugins.api.cfg.Items.ASSEMBLER_MAX_CAPE_L,
            gg.rsmod.plugins.api.cfg.Items.MASORI_ASSEMBLER_MAX_CAPE, gg.rsmod.plugins.api.cfg.Items.MASORI_ASSEMBLER_MAX_CAPE_L,
        )

    /** Ava's accumulator and the Accumulator max cape (OSRS Wiki "Accumulator max cape": it keeps the accumulator's effect). */
    val ACCUMULATORS: Set<Int> =
        setOf(gg.rsmod.plugins.api.cfg.Items.AVAS_ACCUMULATOR, gg.rsmod.plugins.api.cfg.Items.ACCUMULATOR_MAX_CAPE)

    /** Every item carrying the OSRS "Commune" junk-gathering option: the assembler family and the Accumulator max cape. */
    val COMMUNE_DEVICES: Set<Int> get() = ASSEMBLERS + gg.rsmod.plugins.api.cfg.Items.ACCUMULATOR_MAX_CAPE

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

    /** What happens to one piece of recoverable ammunition after a shot. */
    enum class AmmoOutcome { BROKEN, DROPPED, RECOVERED }

    /**
     * The single ammunition-retrieval rule for every shot that uses recoverable ammunition - normal attacks, the dark bow's
     * second arrow, ranged special attacks and thrown specials (OSRS Wiki "Ava's device": the devices recover "arrows, bolts,
     * darts, javelins, throwing knives, thrownaxes, and toktz-xil-ul"). [chance] is uniform in 0..99: 0..19 always breaks
     * (20 %); the rest is dropped under the target unless a device saves it - attractor 60 / 20 / 20, accumulator 72 / 8 / 20,
     * assembler 80 recovered / 20 broken, never dropped. A quiver Ava upgraded gives its effect without the metal-torso rule
     * ("The interaction between Ava devices and metal torsos does not carry over").
     *
     * Owner 2026-09-18: before this every special-attack shot ignored the devices (always used up, 80 % dropped on the floor).
     */
    fun outcome(
        player: Player,
        chance: Int,
    ): AmmoOutcome {
        if (chance in 0..19) return AmmoOutcome.BROKEN
        val quiver = gg.rsmod.plugins.content.items.osrs.DizanasQuiver.wornAvaEffect(player)
        val cape = player.getEquipment(EquipmentType.CAPE)?.id
        val device = !interferes(player)
        val dropped =
            when {
                quiver == gg.rsmod.plugins.content.items.osrs.DizanasQuiver.AvaEffect.ASSEMBLER -> false
                quiver == gg.rsmod.plugins.content.items.osrs.DizanasQuiver.AvaEffect.ACCUMULATOR -> chance in 20..27
                device && cape == gg.rsmod.plugins.api.cfg.Items.AVAS_ATTRACTOR -> chance in 20..39
                device && cape in ACCUMULATORS -> chance in 20..27
                device && cape in ASSEMBLERS -> false
                else -> true
            }
        return if (dropped) AmmoOutcome.DROPPED else AmmoOutcome.RECOVERED
    }
}
