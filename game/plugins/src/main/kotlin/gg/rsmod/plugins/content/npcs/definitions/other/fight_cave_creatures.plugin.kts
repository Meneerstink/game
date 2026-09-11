package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.combat.StyleType

/**
 * TzHaar Fight Cave creatures. Stats and animation ids from the Void donor's
 * tzhaar_fight_cave npcs/combat/anims data (634 cache ids, unchanged in 667). Fight Cave npcs
 * never respawn and drop nothing; death delays follow the animation lengths.
 */
data class CaveCreature(
    val ids: IntArray,
    val hitpoints: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
    val style: StyleType,
    val speed: Int,
    val attackAnim: Int,
    val blockAnim: Int,
    val deathAnim: Int,
)

listOf(
    CaveCreature(intArrayOf(Npcs.TZKIH_2734, Npcs.TZKIH_2735), 100, 20, 30, 15, 30, 1, StyleType.STAB, 4, 9232, 9231, 9230),
    CaveCreature(intArrayOf(Npcs.TZKEK_2736, Npcs.TZKEK_2737), 200, 40, 60, 30, 60, 1, StyleType.CRUSH, 4, 9233, 9235, 9234),
    CaveCreature(intArrayOf(Npcs.TZKEK_2738), 100, 20, 30, 15, 30, 1, StyleType.CRUSH, 4, 9233, 9235, 9234),
    CaveCreature(intArrayOf(Npcs.TOKXIL_2739, Npcs.TOKXIL_2740), 400, 80, 120, 60, 120, 1, StyleType.CRUSH, 4, 9245, 9242, 9239),
    CaveCreature(intArrayOf(Npcs.YTMEJKOT, Npcs.YTMEJKOT_2742), 800, 160, 240, 120, 240, 1, StyleType.CRUSH, 4, 9246, 9248, 9247),
    CaveCreature(intArrayOf(Npcs.KETZEK, Npcs.KETZEK_2744), 1600, 320, 480, 240, 480, 240, StyleType.STAB, 4, 9265, 9268, 9269),
    CaveCreature(intArrayOf(Npcs.TZTOKJAD), 2500, 640, 960, 480, 960, 480, StyleType.STAB, 8, 9277, 9278, 9279),
    CaveCreature(intArrayOf(Npcs.YTHURKOT), 600, 140, 100, 60, 120, 1, StyleType.CRUSH, 4, 9252, 9253, 9257),
).forEach { creature ->
    creature.ids.forEach { id ->
        set_combat_def(npc = id) {
            configs {
                attackSpeed = creature.speed
                attackStyle = creature.style
                respawnDelay = 0
            }
            stats {
                hitpoints = creature.hitpoints
                attack = creature.attack
                strength = creature.strength
                defence = creature.defence
                ranged = creature.ranged
                magic = creature.magic
            }
            anims {
                attack = creature.attackAnim
                block = creature.blockAnim
                death = creature.deathAnim
            }
            aggro {
                radius = 20
            }
        }
    }
}
