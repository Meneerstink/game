package gg.rsmod.plugins.content.npcs.definitions.dagannoth

import gg.rsmod.game.model.combat.StyleType

/**
 * Dagannoth Kings = OSRS Wiki infoboxes (raw wikitext 2026-09-14, "Dagannoth Rex" / "Dagannoth Prime" / "Dagannoth Supreme"):
 * hitpoints 255, attack speed 4, respawn 150 ticks, aggressive; att/str 255; def Rex 255 / Prime 255 / Supreme 128; mage Rex 0 /
 * Prime 255 / Supreme 255; range Rex 255 / Prime 0 / Supreme 255; all attack bonuses 0; defences Rex 255/255/255 magic 10 ranged 255,
 * Prime 255/255/255 magic 255 ranged 10, Supreme 10/10/10 magic 255 ranged 550. Max hits (26/50/30) live in npc-attacks.json.
 * ADAPTED: NpcCombatBuilder raises levels below 1 to 1 (wiki 0); 667 has one ranged defence (light/standard/heavy are equal);
 * elemental weakness (Earth 35%) has no 667 mechanic. Animations from the Novite donor (Supreme 2855/2854/2856, Prime 2854/2852/2856,
 * Rex 2853/2854/2856). Drops come from the bulk drop table.
 */
data class King(
    val id: Int, val style: StyleType, val attackAnim: Int, val blockAnim: Int, val defence: Int, val ranged: Int, val magic: Int,
    val defenceMelee: Int, val defenceMagic: Int, val defenceRanged: Int,
)

val KING_ATTACK_SPEED = 4
val KING_RESPAWN_TICKS = 150

listOf(
    King(Npcs.DAGANNOTH_SUPREME, StyleType.RANGED, 2855, 2854, defence = 128, ranged = 255, magic = 255, defenceMelee = 10, defenceMagic = 255, defenceRanged = 550),
    King(Npcs.DAGANNOTH_PRIME, StyleType.MAGIC, 2854, 2852, defence = 255, ranged = 0, magic = 255, defenceMelee = 255, defenceMagic = 255, defenceRanged = 10),
    King(Npcs.DAGANNOTH_REX, StyleType.SLASH, 2853, 2854, defence = 255, ranged = 255, magic = 0, defenceMelee = 255, defenceMagic = 10, defenceRanged = 255),
).forEach { king ->
    set_combat_def(npc = king.id) {
        configs {
            attackSpeed = KING_ATTACK_SPEED
            attackStyle = king.style
            respawnDelay = KING_RESPAWN_TICKS
        }
        stats {
            hitpoints = 2550
            attack = 255
            strength = 255
            defence = king.defence
            ranged = king.ranged
            magic = king.magic
        }
        bonuses {
            defenceStab = king.defenceMelee
            defenceSlash = king.defenceMelee
            defenceCrush = king.defenceMelee
            defenceMagic = king.defenceMagic
            defenceRanged = king.defenceRanged
        }
        anims {
            attack = king.attackAnim
            block = king.blockAnim
            death = 2856
        }
        aggro {
            radius = 8
        }
    }
}
