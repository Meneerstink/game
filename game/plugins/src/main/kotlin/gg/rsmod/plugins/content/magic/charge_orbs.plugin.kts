package gg.rsmod.plugins.content.magic

/**
 * Charge Water / Earth / Fire / Air Orb on the elemental obelisks (spell-on-object, OpLocTHandler; owner 2026-09-18 P0).
 * Levels and runes: SpellbookData (30 elemental + 3 cosmic + 1 unpowered orb). XP 66 / 70 / 73 / 76 (OSRS Wiki spell pages,
 * read 2026-09-18). Look/sound (Void donor OrbCharging + spellbook.*.toml): animation 726, graphic 149 water / 150 air /
 * 151 earth, fire 152 (Void lists 151 twice; 149-152 are one four-colour family on sequence 688 in the 667 cache - ADAPTED),
 * sounds 118 / 116 / 115 / 117. One charge every 6 ticks until the orbs or runes run out (Void weakQueue 6).
 * Obelisks (667 cache names): Water 2151, Air 2152, Fire 2153, Earth 29415.
 */
data class OrbSpell(val spell: SpellbookData, val obelisk: Int, val orb: Int, val xp: Double, val gfx: Int, val sound: Int, val element: String)

val ORB_SPELLS =
    listOf(
        OrbSpell(SpellbookData.CHARGE_WATER_ORB, 2151, Items.WATER_ORB, 66.0, 149, 118, "water"),
        OrbSpell(SpellbookData.CHARGE_EARTH_ORB, 29415, Items.EARTH_ORB, 70.0, 151, 115, "earth"),
        OrbSpell(SpellbookData.CHARGE_FIRE_ORB, 2153, Items.FIRE_ORB, 73.0, 152, 117, "fire"),
        OrbSpell(SpellbookData.CHARGE_AIR_ORB, 2152, Items.AIR_ORB, 76.0, 150, 116, "air"),
    )

val CHARGE_ORB_ANIM = 726
val CHARGE_ORB_INTERVAL = 6

ORB_SPELLS.forEach { orbSpell ->
    on_spell_on_obj(parent = orbSpell.spell.interfaceId, child = orbSpell.spell.component, obj = orbSpell.obelisk) {
        chargeOrbs(player, orbSpell)
    }
    // Any other object: the spell's own refusal (Void OrbCharging wording).
    on_spell_on_obj(parent = orbSpell.spell.interfaceId, child = orbSpell.spell.component) {
        val article = if (orbSpell.element.first() in "aeiou") "an" else "a"
        player.message("This spell needs to be cast on $article ${orbSpell.element} obelisk.")
    }
}

fun chargeOrbs(
    player: Player,
    orbSpell: OrbSpell,
) {
    player.queue {
        while (true) {
            if (!MagicSpells.canCast(player, orbSpell.spell.level, orbSpell.spell.runes, spellId = orbSpell.spell.uniqueId)) return@queue
            MagicSpells.removeRunes(player, orbSpell.spell.runes, spellId = orbSpell.spell.uniqueId)
            player.inventory.add(orbSpell.orb, 1)
            player.animate(CHARGE_ORB_ANIM)
            player.graphic(orbSpell.gfx, 50)
            player.playSound(orbSpell.sound)
            player.addXp(Skills.MAGIC, orbSpell.xp)
            wait(CHARGE_ORB_INTERVAL)
        }
    }
}
