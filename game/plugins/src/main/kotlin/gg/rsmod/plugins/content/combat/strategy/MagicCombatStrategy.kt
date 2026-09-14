package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.MIASMIC_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.MIASMIC_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import kotlin.math.floor

/**
 * @author Tom <rspsmods@gmail.com>
 *
 * Ancient Magicks / curse-spell extension (2026-09-10): multi-target Burst/Barrage casting in
 * multi-way areas, secondary [SpellEffect]s (freeze, poison, blood heal, shadow drain, miasmic
 * slowdown, stat drains, teleblock) and weapon-locked spells (Iban Blast, Magic Dart, Miasmic).
 * Mechanics sourced from the 2009scape spell handlers and the 2011 RuneScape Wiki.
 */
object MagicCombatStrategy : CombatStrategy {
    /** Undead npc names Crumble Undead may be cast on (2011 wiki: skeletons, zombies, ghosts, shades and their variants). */
    private val UNDEAD_NAMES = listOf("skeleton", "zombie", "ghost", "shade", "ghast", "revenant", "mummy", "zogre", "banshee", "ankou", "crawling hand", "aberrant spectre", "undead", "skeletal", "spectre", "wight", "zombified")

    override fun getAttackRange(pawn: Pawn): Int = 10

    override fun canAttack(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        if (pawn is Player) {
            val spell = pawn.attr[Combat.CASTING_SPELL]!!
            if (spell.requiredWeapons.isNotEmpty()) {
                val weapon = pawn.getEquipment(EquipmentType.WEAPON)
                if (weapon == null || weapon.id !in spell.requiredWeapons) {
                    pawn.message(spell.requiredWeaponMessage)
                    pawn.setVarp(Combat.SELECTED_AUTOCAST_VARP, 0)
                    pawn.attr.remove(Combat.CASTING_SPELL)
                    return false
                }
            }
            if (spell == CombatSpell.CRUMBLE_UNDEAD && !isUndead(target)) {
                pawn.message("This spell only affects skeletons, zombies, ghosts and shades.")
                pawn.setVarp(Combat.SELECTED_AUTOCAST_VARP, 0)
                pawn.attr.remove(Combat.CASTING_SPELL)
                return false
            }
            if (spell.effect == SpellEffect.Teleblock) {
                if (target !is Player) {
                    pawn.message("You can only cast this spell on other players.")
                    pawn.attr.remove(Combat.CASTING_SPELL)
                    return false
                }
                if (target.timers.has(TELEBLOCK_TIMER)) {
                    pawn.message("This player is already affected by this spell.")
                    pawn.attr.remove(Combat.CASTING_SPELL)
                    return false
                }
            }
            val requirements = MagicSpells.getMetadata(spell.uniqueId)
            if (requirements != null && !MagicSpells.canCast(pawn, requirements.lvl, requirements.runes, spellId = spell.uniqueId)) {
                return false
            }
        }
        return true
    }

    fun isUndead(target: Pawn): Boolean {
        if (target !is Npc) return false
        val name = target.def.name.lowercase()
        return UNDEAD_NAMES.any { name.contains(it) }
    }

    fun convertToRotation(value: Int): Int {
        return when (value) {
            0 -> 1
            1 -> 0
            2 -> 7
            3 -> 2
            4 -> 6
            5 -> 3
            6 -> 4
            7 -> 5
            else -> throw IllegalArgumentException("Invalid value for direction: $value")
        }
    }

    fun combinedDirection(
        attackDirection: Direction,
        targetDirection: Direction,
    ): Int {
        val lookupTable =
            arrayOf(
                intArrayOf(1, 2, 4, 0, 7, 3, 5, 6), // Player Facing NORTH_WEST
                intArrayOf(0, 1, 2, 3, 4, 5, 6, 7), // Player Facing NORTH
                intArrayOf(3, 0, 1, 5, 2, 6, 7, 4), // Player Facing NORTH_EAST
                intArrayOf(2, 4, 7, 1, 6, 0, 3, 5), // Player Facing WEST
                intArrayOf(5, 3, 0, 6, 1, 7, 4, 2), // Player Facing EAST
                intArrayOf(4, 7, 6, 2, 5, 1, 0, 3), // Player Facing SOUTH_WEST
                intArrayOf(7, 6, 5, 4, 3, 2, 1, 0), // Player Facing SOUTH
                intArrayOf(6, 5, 3, 7, 0, 4, 2, 1), // Player Facing SOUTH_EAST
            )
        return lookupTable[targetDirection.orientationValue][attackDirection.orientationValue]
    }

    override fun attack(
        pawn: Pawn,
        target: Pawn,
    ) {
        val world = pawn.world

        val spell = pawn.attr[Combat.CASTING_SPELL] ?: return
        pawn.stopMovement()
        spell.castGfx?.let { gfx -> pawn.graphic(gfx) }
        var animation = spell.castAnimation[0]
        if (pawn is Player && pawn.hasWeaponType(WeaponType.STAFF)) {
            animation = spell.castAnimation[1]
        }
        if (pawn is Npc) {
            animation = spell.castAnimation.getOrNull(2) ?: spell.castAnimation[0]
        }
        pawn.animate(animation)

        if (pawn is Player) {
            MagicSpells
                .getMetadata(spell.uniqueId)
                ?.let { requirement -> MagicSpells.removeRunes(pawn, requirement.runes, spellId = spell.uniqueId) }
            // Charged tomes use one charge per qualifying combat cast (Tomes).
            gg.rsmod.plugins.content.items.osrs.Tomes.afterCast(pawn, spell)
        }

        val targets = collectTargets(pawn, target, spell)
        targets.forEach { victim -> castOn(pawn, victim, spell, primary = victim === target) }
    }

    /**
     * Burst/Barrage hit every attackable pawn within one tile of the primary target when both the
     * caster and the primary target stand in a multi-way area. Everything else is single-target.
     */
    private fun collectTargets(
        pawn: Pawn,
        target: Pawn,
        spell: CombatSpell,
    ): List<Pawn> {
        val world = pawn.world
        if (!spell.multiTarget || !pawn.tile.isMulti(world) || !target.tile.isMulti(world)) {
            return listOf(target)
        }
        val result = mutableListOf<Pawn>(target)
        for (x in -1..1) {
            for (z in -1..1) {
                val tile = target.tile.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                val candidates =
                    if (target is Player) {
                        chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT)
                    } else {
                        chunk.getEntities<Npc>(tile, EntityType.NPC)
                    }
                candidates.forEach { other ->
                    if (other !== target && other !== pawn && other !in result && canSplash(pawn, other)) {
                        result.add(other)
                    }
                }
            }
        }
        return result.take(9)
    }

    /** Silent attackability check for secondary multi-target victims. */
    private fun canSplash(
        pawn: Pawn,
        other: Pawn,
    ): Boolean {
        if (other.isDead() || other.invisible || !other.tile.isMulti(pawn.world)) {
            return false
        }
        return when (other) {
            is Npc -> other.isSpawned() && other.def.isAttackable() && other.combatDef.lifepoints != -1 && other.getCurrentLifepoints() > 0
            is Player -> {
                if (!other.isOnline || !other.lock.canBeAttacked()) {
                    return false
                }
                if (pawn is Player) {
                    if (!AreaState.canPlayersFight(pawn, other)) return false
                    val wildLvl = pawn.tile.getWildernessLevel()
                    if (wildLvl > 0 && other.combatLevel !in Combat.getValidCombatLvlRange(pawn, wildLvl)) return false
                }
                true
            }
            else -> false
        }
    }

    private fun castOn(
        pawn: Pawn,
        target: Pawn,
        spell: CombatSpell,
        primary: Boolean,
    ) {
        val world = pawn.world
        val attackDirection = Direction.calculateAttackDirection(target.tile, pawn.tile)
        val targetDirection = target.faceDirection
        val impactDirection: Int = combinedDirection(attackDirection, targetDirection)
        val impactGfxRotation: Int = convertToRotation(impactDirection)

        val hitDelay: Int
        if (spell.projectile > -1) {
            val projectile = pawn.createProjectile(target, gfx = spell.projectile, type = ProjectileType.MAGIC)
            world.spawn(projectile)
            if (spell.secondProjectile > -1) {
                world.spawn(pawn.createProjectile(target, gfx = spell.secondProjectile, type = ProjectileType.MAGIC))
            }
            if (spell.thirdProjectile > -1) {
                world.spawn(pawn.createProjectile(target, gfx = spell.thirdProjectile, type = ProjectileType.MAGIC))
            }
            hitDelay = getHitDelay(pawn.getCentreTile(), target.getCentreTile())
            spell.impactGfx?.let { gfx ->
                target.graphic(Graphic(gfx.id, gfx.height, projectile.lifespan, impactGfxRotation))
            }
        } else {
            hitDelay = if (spell.fixedHitDelay > -1) spell.fixedHitDelay else getHitDelay(pawn.getCentreTile(), target.getCentreTile())
            spell.impactGfx?.let { gfx ->
                target.graphic(Graphic(gfx.id, gfx.height, hitDelay * 30, impactGfxRotation))
            }
        }

        val formula = MagicCombatFormula
        val accuracy = formula.getAccuracy(pawn, target)
        val landHit = accuracy >= world.randomDouble()

        if (!spell.damaging) {
            // Effect-only spell: no damage hit is shown; the effect lands on a successful roll,
            // otherwise the target only shows the splash graphic.
            if (landHit) {
                target.graphic(Graphic(spell.impactGfx?.id ?: 85, spell.impactGfx?.height ?: 96, hitDelay * 30))
                world.queue {
                    wait(hitDelay)
                    applyEffect(pawn, target, spell, 0)
                }
            } else {
                target.graphic(Graphic(85, 96, hitDelay * 30))
                if (pawn is Player) pawn.message("The spell has no effect.")
            }
            if (pawn is Player && landHit) {
                pawn.addXp(Skills.MAGIC, spell.experience, checkBrawlingGloves = true)
            }
            return
        }

        val maxHit = formula.getMaxHit(pawn, target)
        val pawnHit =
            pawn.dealHit(
                target = target,
                maxHit = maxHit,
                landHit = landHit,
                delay = hitDelay,
                hitType = HitType.MAGIC,
            )
        val damage = pawnHit.hit.hitmarks.sumOf { it.damage }
        if (landHit) {
            pawnHit.hit.addAction { applyEffect(pawn, target, spell, damage) }
            // Trident of the Swamp: manually cast combat spells also roll its 25 % venom while it holds a charge.
            if (pawn is Player) {
                pawnHit.hit.addAction { gg.rsmod.plugins.content.items.osrs.PoweredStaves.rollVenom(pawn, target) }
                // Toxic staff of the dead: 25 % venom for spells cast while the charged staff is wielded.
                pawnHit.hit.addAction { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.rollVenom(pawn, target) }
            }
        } else {
            spell.impactGfx?.let { target.graphic(Graphic(85, 96, hitDelay * 30)) }
        }

        if (damage >= 0 && pawn.entityType.isPlayer) {
            addCombatXp(pawn as Player, target, damage, spell, primary)
        }
    }

    /** Applies the spell's secondary effect once its hit lands. [damage] is in lifepoints (x10). */
    private fun applyEffect(
        pawn: Pawn,
        target: Pawn,
        spell: CombatSpell,
        damage: Int,
    ) {
        if (target.isDead()) return
        when (val effect = spell.effect) {
            is SpellEffect.Freeze -> {
                val frozen = target.freeze(effect.ticks) { if (target is Player) target.message("You have been frozen.") }
                if (!frozen && spell == CombatSpell.ICE_BARRAGE) {
                    // Already frozen / immune: barrage shows the frozen-orb graphic instead.
                    target.graphic(Graphic(1677, 96))
                }
            }
            is SpellEffect.Poison -> if (damage > 0) Poison.poison(target, effect.damage)
            is SpellEffect.BloodHeal -> {
                val heal = damage / 4
                if (heal > 0) {
                    when (pawn) {
                        is Player -> pawn.heal(heal)
                        is Npc -> pawn.setCurrentLifepoints(minOf(pawn.getCurrentLifepoints() + heal, pawn.getMaximumLifepoints()))
                    }
                }
            }
            is SpellEffect.ShadowDrain -> drainSkill(target, Skills.ATTACK, 10)
            is SpellEffect.StatDrain -> drainSkill(target, effect.skill, effect.percent, gg.rsmod.plugins.content.items.osrs.Tomes.drainBoost(pawn, spell))
            is SpellEffect.Miasmic -> {
                if (!target.timers.has(MIASMIC_IMMUNITY_TIMER)) {
                    target.timers[MIASMIC_TIMER] = effect.ticks
                    target.timers[MIASMIC_IMMUNITY_TIMER] = effect.ticks + 15
                    if (target is Player) target.message("You feel slowed down.")
                }
            }
            is SpellEffect.Teleblock -> {
                if (target is Player && !target.timers.has(TELEBLOCK_TIMER)) {
                    val protectedFromMagic =
                        Prayers.isActive(target, Prayer.PROTECT_FROM_MAGIC) || AncientCurses.isCurseActive(target, AncientCurse.DEFLECT_MAGIC)
                    target.timers[TELEBLOCK_TIMER] = if (protectedFromMagic) 250 else 500
                    target.message("You have been teleblocked!")
                    if (pawn is Player) pawn.message("You have teleblocked ${target.username}.")
                }
            }
            null -> {}
        }
    }

    /**
     * Curse-style drains lower the target's current level by [percent] of its base level, but never
     * below what a previous cast of the same strength already reached (2011 wiki behaviour).
     */
    private fun drainSkill(
        target: Pawn,
        skill: Int,
        percent: Int,
        /** Tome of Water: stat-draining curses are 50 % more effective (drain floored). */
        boost: Double = 1.0,
    ) {
        val base: Int
        val current: Int
        val index: Int
        when (target) {
            is Player -> {
                index = skill
                base = target.skills.getMaxLevel(index)
                current = target.skills.getCurrentLevel(index)
            }
            is Npc -> {
                index = when (skill) {
                    Skills.ATTACK -> NpcSkills.ATTACK
                    Skills.STRENGTH -> NpcSkills.STRENGTH
                    Skills.DEFENCE -> NpcSkills.DEFENCE
                    else -> return
                }
                base = target.stats.getMaxLevel(index)
                current = target.stats.getCurrentLevel(index)
            }
            else -> return
        }
        val amount = (base * percent * boost / 100.0).toInt()
        val floor = base - amount
        if (current <= floor) {
            return
        }
        val drain = minOf(amount, current - floor).coerceAtLeast(1)
        val cap = -(base - floor).coerceAtLeast(1)
        when (target) {
            is Player -> target.skills.alterCurrentLevel(index, -drain, capValue = cap)
            is Npc -> target.stats.alterCurrentLevel(index, -drain, capValue = cap)
            else -> {}
        }
    }

    fun getHitDelay(
        start: Tile,
        target: Tile,
    ): Int {
        val distance = start.getDistance(target)
        return 2 + floor((1.0 + distance) / 3.0).toInt()
    }

    private fun addCombatXp(
        player: Player,
        target: Pawn,
        damage: Int,
        spell: CombatSpell,
        primary: Boolean,
    ) {
        val modDamage = if (target.entityType.isNpc) target.getCurrentLifepoints().coerceAtMost(damage) else damage
        val multiplier = if (target is Npc) Combat.getNpcXpMultiplier(target) else 1.0
        // Base spell experience is awarded once per cast; every victim adds its damage experience.
        val baseXp = if (primary) spell.experience else 0.0
        val experience = baseXp + (modDamage * 0.2) * multiplier
        val sharedExperience = baseXp + (modDamage * 0.133) * multiplier
        val hitpointsExperience = (modDamage * 0.133) * multiplier
        val defenceExperience = (modDamage * 0.1) * multiplier
        var bonusRate: Double
        val defensive = player.getVarp(Combat.DEFENSIVE_CAST_VARP) > 0
        if (defensive) {
            bonusRate = player.addXp(Skills.MAGIC, sharedExperience, checkBrawlingGloves = true)
            player.addXp(Skills.DEFENCE, defenceExperience * bonusRate)
        } else {
            bonusRate = player.addXp(Skills.MAGIC, experience, checkBrawlingGloves = true)
        }

        player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
    }
}
