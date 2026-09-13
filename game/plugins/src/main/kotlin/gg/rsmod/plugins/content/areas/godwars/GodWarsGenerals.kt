package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.attack.NpcAttacks

/**
 * The four God Wars Dungeon generals' boss mechanics.
 *
 * RCV-005 root cause: the generals had hand-written attack scripts (Matrix-era ids, placeholder melee
 * animations, no per-attack gfx/sounds for most styles). Their attacks now come from the shared data-driven
 * model ([NpcAttacks], Void `bandos/saradomin/zamorak/armadyl.combat.toml`): Graardor melee + ranged volley on
 * the whole chamber, Zilyana melee + lightning on the whole chamber, K'ril melee/poison melee/slam through
 * Protect from Melee (prayer drain, message, shout) + magic, Kree'arra melee + ranged/magic on the whole chamber.
 *
 * What stays here are the mechanics the data cannot express, ported from Void:
 * - K'ril `protect_melee_zamorak` condition and slam cooldown (`zamorak/KrilTsutsaroth.kt`);
 * - Kree'arra's stun on `ranged_teleport` / `magic_teleport` (`armadyl/KreeArra.kt`);
 * - the generals' battle shouts (force-chat lines and sound ids from the original 2012 Matrix scripts kept at
 *   `C:\RSPS\import-source\donors\matrix-data\matrix-scripts\`; Void has no shout data), one swing in five.
 */
object GodWarsGenerals {
    /** Void `gwd_last_slam` clock: K'ril cannot slam through protection again until it expires. */
    val GWD_LAST_SLAM = TimerKey()

    /** Void armadyl.anims/gfx/sounds.toml `kree_arra_stun`. */
    private const val KREE_ARRA_STUN_ANIM = 848
    private const val KREE_ARRA_STUN_GFX = 981
    private const val KREE_ARRA_STUN_SOUND = 3201

    private val shouts: Map<String, List<Pair<String, Int>>> =
        mapOf(
            // Novite 667 npc/combat/impl/GeneralGraardorCombat.java:25-66 (every line has its sound).
            "general_graardor" to
                listOf(
                    "Death to our enemies!" to 3219,
                    "Brargh!" to 3209,
                    "Break their bones!" to 3221,
                    "For the glory of Bandos!" to 3205,
                    "Split their skulls!" to 3229,
                    "We feast on the bones of our enemies tonight!" to 3206,
                    "CHAAARGE!" to 3220,
                    "Crush them underfoot!" to 3224,
                    "All glory to Bandos!" to 3205,
                    "GRAAAAAAAAAR!" to 3207,
                    "FOR THE GLORY OF THE BIG HIGH WAR GOD!" to 3228,
                ),
            "commander_zilyana" to
                listOf(
                    "Death to the enemies of the light!" to 3247,
                    "Slay the evil ones!" to 3242,
                    "Saradomin lend me strength!" to 3263,
                    "By the power of Saradomin!" to 3262,
                    "May Saradomin be my sword." to 3251,
                    "Good will always triumph!" to 3260,
                    "Forward! Our allies are with us!" to 3245,
                    "Saradomin is with us!" to 3266,
                    "In the name of Saradomin!" to 3250,
                    "Attack! Find the Godsword!" to 3258,
                ),
            // Novite 667 npc/combat/impl/KrilTsutsaroth.java:26-59 (every line has its sound; the old table had most -1
            // and a wrong 3229, which is a Graardor line).
            "kril_tsutsaroth" to
                listOf(
                    "Attack them, you dogs!" to 3278,
                    "Forward!" to 3276,
                    "Death to Saradomin's dogs!" to 3277,
                    "Kill them, you cowards!" to 3290,
                    "The Dark One will have their souls!" to 3280,
                    "Zamorak curse them!" to 3270,
                    "Rend them limb from limb!" to 3273,
                    "No retreat!" to 3276,
                    "Flay them all!" to 3286,
                ),
        )

    fun installAttackHooks() {
        shouts.forEach { (combatDef, lines) ->
            NpcAttacks.onSwing(combatDef) { npc, _, attack ->
                // An attack with its own `say` (K'ril's slam) already shouted.
                if (attack.say.isEmpty() && npc.world.random(4) == 0) {
                    val (line, sound) = lines.random()
                    shout(npc, line, sound)
                }
            }
        }

        NpcAttacks.condition("protect_melee_zamorak") { _, target ->
            target is Player && target.isProtectedFrom(CombatClass.MELEE) && !target.timers.has(GWD_LAST_SLAM)
        }
        listOf("melee_slam", "melee_slam_poison").forEach { attack ->
            NpcAttacks.onAttack("kril_tsutsaroth", attack) { npc, target ->
                target.timers[GWD_LAST_SLAM] = npc.world.random(6..10)
            }
        }

        listOf("ranged_teleport", "magic_teleport").forEach { attack ->
            NpcAttacks.onAttack("kree_arra", attack) { npc, target -> knockBack(npc, target) }
        }
    }

    fun shout(
        npc: Npc,
        line: String,
        sound: Int = -1,
    ) {
        npc.forceChat(line)
        if (sound > -1) {
            npc.world.spawn(AreaSound(tile = npc.tile, id = sound, radius = 10, volume = 1))
        }
    }

    /** Void `KreeArra.knockBack`: away from Kree'arra, or along one axis of a blocked diagonal. */
    private fun knockBack(
        npc: Npc,
        target: Pawn,
    ) {
        if (stun(target, Direction.between(npc.tile, target.tile))) return
        val dx = Integer.signum(target.tile.x - npc.tile.x)
        val dz = Integer.signum(target.tile.z - npc.tile.z)
        if (dx != 0 && dz != 0) {
            if (stun(target, Direction.between(target.tile, target.tile.transform(dx, 0)))) return
            stun(target, Direction.between(target.tile, target.tile.transform(0, dz)))
        }
    }

    private fun stun(
        target: Pawn,
        direction: Direction,
    ): Boolean {
        if (!target.world.collision.canTraverse(tile = target.tile, direction = direction, projectile = false, water = false)) {
            return false
        }
        target.graphic(KREE_ARRA_STUN_GFX, delay = 100)
        target.animate(KREE_ARRA_STUN_ANIM, delay = 100)
        (target as? Player)?.playSound(KREE_ARRA_STUN_SOUND)
        return true
    }
}
