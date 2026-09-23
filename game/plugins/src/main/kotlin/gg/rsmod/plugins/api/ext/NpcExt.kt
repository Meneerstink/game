package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.plugins.api.NpcSpecies

const val NPC_ATTACK_BONUS_INDEX = 10
const val NPC_STRENGTH_BONUS_INDEX = 11
const val NPC_RANGED_STRENGTH_BONUS_INDEX = 12
const val NPC_MAGIC_DAMAGE_BONUS_INDEX = 13

fun Npc.prepareAttack(
    combatClass: CombatClass,
    styleType: StyleType,
    weaponStyle: WeaponStyle,
) {
    this.combatClass = combatClass
    this.combatDef.attackStyleType = styleType
    this.weaponStyle = weaponStyle
}

fun Npc.createProjectile(
    target: Pawn,
    gfx: Int,
    startHeight: Int,
    endHeight: Int,
    delay: Int,
    angle: Int,
    lifespan: Int = -1,
    steepness: Int = -1,
): Projectile {
    val start = getFrontFacingTile(target)
    val builder =
        Projectile
            .Builder()
            .setTiles(start = start, target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight)
            .setSlope(
                angle = angle,
                steepness =
                    if (steepness ==
                        -1
                    ) {
                        Math.min(255, ((getSize() shr 1) + 1) * 32)
                    } else {
                        steepness
                    },
            ).setTimes(
                delay = delay,
                lifespan =
                    if (lifespan ==
                        -1
                    ) {
                        (delay + (world.collision.raycastTiles(start, target.getCentreTile()) * 5))
                    } else {
                        lifespan
                    },
            )

    return builder.build()
}

fun Npc.createProjectile(
    target: Tile,
    gfx: Int,
    startHeight: Int,
    endHeight: Int,
    delay: Int,
    angle: Int,
    lifespan: Int,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = getFrontFacingTile(target), target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight)
            .setSlope(angle = angle, steepness = Math.min(255, ((getSize() shr 1) + 1) * 32))
            .setTimes(delay = delay, lifespan = lifespan)

    return builder.build()
}

/**
 * Check if npc belongs to any of the species specified.
 *
 * @return true if [Npc.species] contains [species] or any value in [others].
 */
fun Npc.isSpecies(
    species: NpcSpecies,
    vararg others: NpcSpecies,
): Boolean = this.species.contains(species) || this.species.any { others.contains(it) }

fun Npc.getAttackBonus(): Int = equipmentBonuses[NPC_ATTACK_BONUS_INDEX]

fun Npc.getStrengthBonus(): Int = equipmentBonuses[NPC_STRENGTH_BONUS_INDEX]

fun Npc.getRangedStrengthBonus(): Int = equipmentBonuses[NPC_RANGED_STRENGTH_BONUS_INDEX]

fun Npc.getMagicDamageBonus(): Int = equipmentBonuses[NPC_MAGIC_DAMAGE_BONUS_INDEX]

/**
 * The [Player] credited with killing this npc, or `null` when no player damage was recorded for it.
 *
 * Npc death and drop scripts used to write `npc.damageMap.getMostDamage()!! as Player`, which throws
 * whenever an npc dies without a player attacker in its damage map (a scripted or environmental
 * kill, a kill by another npc, or an npc that dealt the most damage itself). The death hooks are
 * isolated, so the throw was swallowed and the npc silently dropped nothing and played no death
 * sound - live proof: `guard_level_21.plugin.kts` NullPointerException in the 2026-09-15 server log.
 * One shared accessor, so every script expresses "no player killer" the same way.
 */
fun Npc.killer(): Player? = damageMap.getMostDamage() as? Player
