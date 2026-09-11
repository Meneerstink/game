package gg.rsmod.plugins.content.npcs.definitions.dagannoth

import gg.rsmod.game.model.combat.StyleType

/**
 * Dagannoth Kings. Stats: OSRS/2007-era values matched by name and combat level (bulk table);
 * animations and attack timings from the Matrix 718 NPCCombatDefinitions via the Novite donor
 * (Supreme 2855/2854/2856 7 ticks, Prime 2854/2852/2856 4 ticks, Rex 2853/2854/2856 4 ticks).
 * Drops come from the bulk drop table (berserker/warrior/archer/seers rings, dragon axe,
 * mud battlestaff, seercull).
 */
data class King(val id: Int, val style: StyleType, val speed: Int, val attackAnim: Int, val blockAnim: Int, val attack: Int, val strength: Int, val defence: Int, val ranged: Int, val magic: Int)

listOf(
    King(Npcs.DAGANNOTH_SUPREME, StyleType.RANGED, 7, 2855, 2854, attack = 255, strength = 255, defence = 128, ranged = 255, magic = 255),
    King(Npcs.DAGANNOTH_PRIME, StyleType.MAGIC, 4, 2854, 2852, attack = 255, strength = 255, defence = 255, ranged = 1, magic = 255),
    King(Npcs.DAGANNOTH_REX, StyleType.SLASH, 4, 2853, 2854, attack = 255, strength = 255, defence = 255, ranged = 255, magic = 1),
).forEach { king ->
    set_combat_def(npc = king.id) {
        configs {
            attackSpeed = king.speed
            attackStyle = king.style
            respawnDelay = 60
        }
        stats {
            hitpoints = 2550
            attack = king.attack
            strength = king.strength
            defence = king.defence
            ranged = king.ranged
            magic = king.magic
        }
        bonuses {
            // Combat triangle (2011): each king is near-immune to two styles.
            defenceStab = if (king.id == Npcs.DAGANNOTH_SUPREME) 0 else 255
            defenceSlash = if (king.id == Npcs.DAGANNOTH_SUPREME) 0 else 255
            defenceCrush = if (king.id == Npcs.DAGANNOTH_SUPREME) 0 else 255
            defenceRanged = if (king.id == Npcs.DAGANNOTH_PRIME) 0 else 255
            defenceMagic = if (king.id == Npcs.DAGANNOTH_REX) 0 else 255
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
