package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AGGRESSOR
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.VENOM_TIMER
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.CombatConfigs.getCombatClass
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.mechanics.combatresponse.DamageResponse
import gg.rsmod.plugins.content.mechanics.lifesteal.GuthanLifesteal
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.statdrain.AhrimBlightedAura
import gg.rsmod.plugins.content.mechanics.statdrain.KarilAgilityDrain
import gg.rsmod.plugins.content.mechanics.statdrain.ToragEnergyDrain
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Redemption
import gg.rsmod.plugins.content.mechanics.prayer.Smite
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import java.lang.ref.WeakReference
import kotlin.random.Random

/**
 * @author Tom <rspsmods@gmail.com>
 */

/** Default `minHit` of every [dealHit] overload: the formula-driven hit with no caller-set minimum. */
const val DEFAULT_MIN_HIT = 0.1

/**
 * OSRS damage roll (OSRS Wiki "Damage per second/Melee", "/Ranged", "/Magic"): a successful hit deals a uniformly
 * random whole number from 0 up to and including the floored max hit, and a successful hit that rolls 0 is changed
 * to 1 (the pages' average "Max hit/2 + 1/(Max hit+1)"). A caller-set minimum (special attacks, scripted damage)
 * becomes the lowest whole damage instead, so an exact hit (`minHit = maxHit - 0.1`) stays exact.
 */
fun rollDamage(
    minHit: Double,
    maxHit: Double,
    random: Random = Random.Default,
): Int {
    val high = kotlin.math.floor(maxHit + 1e-9).toInt()
    if (high <= 0) return 0
    if (minHit <= DEFAULT_MIN_HIT) return maxOf(1, random.nextInt(0, high + 1))
    val low = kotlin.math.ceil(minHit - 1e-9).toInt().coerceIn(0, high)
    return random.nextInt(low, high + 1)
}

fun Pawn.isAttacking(): Boolean = attr[COMBAT_TARGET_FOCUS_ATTR]?.get() != null

fun Pawn.isBeingAttacked(): Boolean = timers.has(ACTIVE_COMBAT_TIMER)

fun Pawn.getCombatTarget(): Pawn? = attr[COMBAT_TARGET_FOCUS_ATTR]?.get()

fun Pawn.setCombatTarget(target: Pawn) {
    attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(target)
}

fun Pawn.clearActiveCombatTimer() {
    timers.remove(ACTIVE_COMBAT_TIMER)
}

fun Pawn.getAggressor(): Pawn? = attr[AGGRESSOR]?.get()

fun Pawn.getLastHit(): Pawn? = attr[LAST_HIT_ATTR]?.get()

fun Pawn.getLastHitBy(): Pawn? = attr[LAST_HIT_BY_ATTR]?.get()

fun Pawn.removeCombatTarget() = attr.remove(COMBAT_TARGET_FOCUS_ATTR)

fun Pawn.canEngageCombat(target: Pawn): Boolean = Combat.canEngage(this, target)

fun Pawn.canAttack(
    target: Pawn,
    combatClass: CombatClass,
): Boolean = Combat.canAttack(this, target, combatClass)

fun Pawn.isAttackDelayReady(): Boolean = Combat.isAttackDelayReady(this)

fun Pawn.combatRaycast(
    target: Pawn,
    distance: Int,
    projectile: Boolean,
): Boolean = Combat.raycast(this, target, distance, projectile)

fun Pawn.isPoisoned(): Boolean = timers.has(POISON_TIMER)

fun Pawn.isEnvenomed(): Boolean = timers.has(VENOM_TIMER)

suspend fun Pawn.canAttackMelee(
    it: QueueTask,
    target: Pawn,
    moveIfNeeded: Boolean,
): Boolean =
    Combat.areBordering(
        tile.x,
        tile.z,
        getSize(),
        getSize(),
        target.tile.x,
        target.tile.z,
        target.getSize(),
        target.getSize(),
    ) ||
        moveIfNeeded &&
        moveToAttackRange(it, target, distance = 0, projectile = false)

fun Pawn.dealHit(
    target: Pawn,
    formula: CombatFormula,
    minHit: Double = 0.1,
    delay: Int,
    onHit: (PawnHit) -> Unit = {
    },
): PawnHit {
    val accuracy = formula.getAccuracy(this, target)
    val maxHit = formula.getMaxHit(this, target)
    val landHit = accuracy >= world.randomDouble()
    return dealHit(target, minHit, maxHit, landHit, delay, onHit, HitType.REGULAR_HIT)
}

/**
 * Sends the dealHit method while allowing the setting of [HitType]
 * @author Kevin Senez <ksenez94@gmail.com>
 */
fun Pawn.dealHit(
    target: Pawn,
    formula: CombatFormula,
    minHit: Double = 0.1,
    delay: Int,
    type: HitType,
    onHit: (PawnHit) -> Unit = {},
): PawnHit {
    val accuracy = formula.getAccuracy(this, target)
    val maxHit = formula.getMaxHit(this, target)
    val landHit = accuracy >= world.randomDouble()
    return dealHit(target, minHit, maxHit, landHit, delay, onHit, type)
}

/**
 * Deals a hit to a target Pawn from this Pawn.
 *
 * @param target The target Pawn to deal the hit to.
 * @param maxHit The maximum possible hit damage.
 * @param landHit Whether the hit should successfully land on the target.
 * @param delay The delay before the hit is executed.
 * @param onHit An optional lambda that will be executed when the hit is successful.
 * @param hitType The type of hit being performed.
 * @return A PawnHit object containing information about the hit.
 */
fun Pawn.dealHit(
    target: Pawn,
    minHit: Double = 0.1,
    maxHit: Double,
    landHit: Boolean,
    delay: Int,
    onHit: (PawnHit) -> Unit = {},
    hitType: HitType,
    bonusDamage: Int = 0,
    applyDeflectProtection: Boolean = true,
): PawnHit {
    // Calculate the 1:1 real damage, applying a random factor.
    // Combat formulas and hitpoints use the same 1:1 real-damage unit. Keep the hitmark value
    // identical to the rolled damage so server state, hitbars and client hit splats agree.
    var damage = if (landHit) (rollDamage(minHit, maxHit) + bonusDamage).toDouble() else 0.0
    // Power of Death (staff of the dead, toxic staff, staff of light): halves melee damage taken; ends on damage without the staff.
    if (target is Player) {
        damage = gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.modifyIncomingDamage(target, hitType, damage.toInt()).toDouble()
    }
    // Tormented demons: fire shield and style prayer (see TormentedDemonCombatScript).
    // RCV-012 B7: misses reach the rule too - both donors count every hit, a miss included, toward the prayer switch.
    if (target is Npc && target.id in gg.rsmod.plugins.content.combat.scripts.impl.TormentedDemonCombatScript.ids && damage >= 0) {
        val weapon = (this as? Player)?.equipment?.get(3)?.id ?: -1
        val style = when (hitType) {
            HitType.RANGE -> CombatClass.RANGED
            HitType.MAGIC -> CombatClass.MAGIC
            else -> CombatClass.MELEE
        }
        damage = gg.rsmod.plugins.content.combat.scripts.impl.TormentedDemonCombatScript.modifyIncomingDamage(target, this, style, damage.toInt(), weapon).toDouble()
    }
    // Corporeal Beast: melee and ranged damage is halved unless dealt with a Corpbane weapon on the stab style (OSRS Wiki).
    if (target is Npc && target.id == gg.rsmod.plugins.api.cfg.Npcs.CORPOREAL_BEAST && damage > 0) {
        damage = gg.rsmod.plugins.content.combat.scripts.impl.CorporealBeastCombatScript.modifyIncomingDamage(this, hitType, damage.toInt()).toDouble()
    }
    // Deadman breach monsters Porazdir, Justiciar Zachariah and Derwen: "completely immune to melee and ranged attacks".
    if (target is Npc && damage > 0) {
        damage = gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.modifyIncomingDamage(target, hitType, damage.toInt()).toDouble()
    }
    // Preserve the hit's declared style and the damage before Deflect reduces it to zero.
    // Reading getCombatClass again on impact can use a different style after a weapon switch.
    val curseHitStyle = when (hitType) {
        HitType.MELEE -> CombatClass.MELEE
        HitType.RANGE -> CombatClass.RANGED
        HitType.MAGIC -> CombatClass.MAGIC
        else -> getCombatClass(this)
    }
    val deflectDamage = damage.toInt()
    if (applyDeflectProtection) {
        damage = AncientCurses.deflectDamageTaken(this, target, curseHitStyle, deflectDamage).toDouble()
    }
    var type = hitType.id
    var executeHit = landHit
    val dmg = damage.toInt()

    // Handles critical hit markers for Npc targets
    if (damage >= (maxHit * 0.90) && target is Npc) {
        type += 10
    }

    // Handles death blow condition for Npc targets
    if (target is Npc) {
        if (target.combatDef.deathBlowLifepoints > -1) {
            val deathBlowLifepoints = target.combatDef.deathBlowLifepoints
            // If the damage caused the target to have less than 50 health
            if (dmg >= target.getCurrentLifepoints() && target.getCurrentLifepoints() - dmg < deathBlowLifepoints) {
                // Limit the damage to leave the target at 50 health
                if (target.getCurrentLifepoints() > deathBlowLifepoints) {
                    damage = (target.getCurrentLifepoints() - deathBlowLifepoints).toDouble()
                } else {
                    executeHit = false
                }
            }
        }
    }

    // Create the hit object with the calculated damage, type, and delay
    val hit =
        if (executeHit) {
            target.hit(damage = damage.toInt(), type = type, delay = delay)
        } else {
            target.hit(damage = 0, type = HitType.BLOCK, delay = delay)
        }

    val pawnHit = PawnHit(hit, executeHit)

    // Audit X-10: a player with a hit on its way cannot finish an x-log before it lands, and stays held 16 ticks after impact.
    if (target is Player) {
        Combat.holdLogout(target, delay + Combat.LOGOUT_HOLD_TICKS)
        hit.addAction { Combat.holdLogout(target) }
    }

    // Deadman skull (owner 2026-09-18, MAJOR): the attacker is skulled when this hitsplat registers
    // on the other player - the first hit action, so it runs the cycle the hitmark is written and
    // never for a cancelled hit. Every melee/ranged/magic/special route deals through here.
    if (this is Player && target is Player) {
        val attacker = this
        hit.addAction { PvpSkull.onHitRegistered(attacker, target) }
    }

    // Special attacks give the normal combat experience for their damage (SpecialAttackXp, OSRS Wiki "Combat").
    if (this is Player && executeHit) {
        gg.rsmod.plugins.content.combat.specialattack.SpecialAttackXp.award(this, target, hit.hitmarks.sumOf { it.damage }, hitType)
    }

    // Amulet of blood fury: every successful melee hit (specials and multi-hits included) uses a charge and may heal (BloodFury).
    if (this is Player && hitType == HitType.MELEE && executeHit) {
        val attacker = this
        hit.addAction { gg.rsmod.plugins.content.items.osrs.BloodFury.onMeleeHit(attacker, hit.hitmarks.sumOf { it.damage }) }
    }

    // Poisoned weapons and ammunition: 1/4 melee, 1/8 ranged on a landed non-zero hit (WeaponPoison, OSRS Wiki "Poison").
    if (this is Player) {
        gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.onPlayerHit(this, target, pawnHit, hitType)
        // Spirit scorpion Venom Shot: a charged owner's next damaging ranged hit poisons its target (Void FamiliarBoostSpecials).
        if (hitType == HitType.RANGE && executeHit && damage > 0) {
            val attacker = this
            hit.addAction { gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves.consumeVenomShot(attacker, target) }
        }
    }

    // Crystal armour: "One charge is depleted for each successful hit that is received from combat" - monster hits only,
    // nothing when a protection prayer negated the damage (CrystalEquipment).
    if (target is Player && this is Npc && executeHit) {
        hit.addAction {
            if (hit.hitmarks.sumOf { it.damage } > 0) gg.rsmod.plugins.content.items.osrs.CrystalEquipment.onHitReceived(target)
        }
    }

    if (target is Npc && target.id == gg.rsmod.plugins.api.cfg.Npcs.CORPOREAL_BEAST && executeHit) {
        hit.addAction {
            gg.rsmod.plugins.content.combat.scripts.impl.CorporealBeastCombatScript.onBeastDamaged(target, this@dealHit, hit.hitmarks.sumOf { it.damage })
        }
    }

    // Re-check PvP safety when a delayed hit lands. This closes the boundary
    // window for projectiles/spells fired before either player entered home.
    val creditedPlayer = attr[DAMAGE_CREDIT_ATTR]?.get() as? Player
    hit.setCancelIf {
        isDead() ||
            disruptionShieldAbsorbs(this, target, damage.toInt()) ||
            when {
                this is Player && target is Player -> !AreaState.canPlayersFight(this, target)
                creditedPlayer != null && target is Player -> !AreaState.canPlayersFight(creditedPlayer, target)
                else -> false
            }
    }

    // Animate the target blocking the hit (if not a melee hit)
    hit.addAction {
        val pawn = this@dealHit
        if (getCombatClass(pawn) != CombatClass.MELEE) {
            val blockAnimation = CombatConfigs.getBlockAnimation(target)
            target.animate(blockAnimation, priority = false)
        }
    }

    // RCV-005 shared NPC combat audio (NpcCombatAudio): every strategy and every scripted npc attack
    // deals its damage through here, so this is the one dispatch point. Void Attack.kt plays the
    // attack sound when the npc attacks; Block.kt plays the npc defend sound through the attacker.
    // A data-driven attack (NpcAttacks) already played its own section sounds this tick.
    if (this is Npc && !gg.rsmod.plugins.content.combat.attack.NpcAttacks.isDataAttackThisCycle(this)) {
        gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.onAttack(this, target)
    }
    if (target is Npc) {
        hit.addAction {
            gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.onDefend(this@dealHit, target)
        }
    }

    // Execute the provided onHit lambda
    hit.addAction { onHit(pawnHit) }

    // Apply post-damage effects
    hit.addAction {
        val pawn = this@dealHit
        Combat.postDamage(pawn, target)
    }

    // Update the damage map for the target
    if (landHit) {
        hit.addAction {
            val pawn = this@dealHit
            val credited = pawn.attr[DAMAGE_CREDIT_ATTR]?.get() ?: pawn
            val dealt = hit.hitmarks.sumOf { it.damage }
            target.damageMap.add(credited, dealt)
            gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(target, credited, dealt)
        }
    }

    // Capture the special's identity now: its hit action may execute after the special context ends.
    val deflectAttackToken = attr[AncientCurses.DEFLECT_ATTACK_TOKEN_ATTR]
    // BATCH 2: Ancient Curses' Sap/Leech/Soul Split trigger once per landed hit - this is the
    // single point every combat style (melee/ranged/magic) routes through, so it only needs
    // wiring here rather than in each *CombatFormula.kt.
    if (landHit) {
        hit.addAction {
            val pawn = this@dealHit
            val totalDamage = hit.hitmarks.sumOf { it.damage }
            AncientCurses.onDamageDealt(pawn, target, totalDamage, curseHitStyle)
            // P6 (2026-09-02): reflect/recoil/vengeance now routed through one deterministic
            // dispatcher instead of calling AncientCurses.onIncomingHit directly - Deflect
            // curse is still evaluated first inside it, unchanged, just moved up a level so
            // it shares an order with Vengeance/Ring of recoil. See DamageResponse.kt.
            AncientCurses.withDeflectAttackToken(deflectAttackToken) {
                DamageResponse.onIncomingHit(pawn, target, curseHitStyle, totalDamage, deflectDamage)
            }
            // Lifesteal further-foundations pass (2026-09-02): Guthan's Infestation set effect,
            // an attacker-side "on damage dealt" effect like Sap/Leech above it. See
            // GuthanLifesteal.kt for the sourcing note.
            GuthanLifesteal.onDamageDealt(pawn, getCombatClass(pawn), totalDamage)
            // Stat-drain further-foundations pass (2026-09-02): Ahrim's Blighted Aura set
            // effect (the "Barrows" entry of the master plan's "Stat drain" item; BGS's own
            // drain is wired separately in its own special-attack plugin.kts, and DWH is
            // blocked - absent from this cache). See AhrimBlightedAura.kt for the sourcing note.
            AhrimBlightedAura.onDamageDealt(pawn, target, getCombatClass(pawn))
            // Barrows set-effect audit 2026-09-17b: the player-worn Torag/Karil set effects were entirely
            // missing (only the NPC brothers' own attacks used them) - see ToragEnergyDrain.kt /
            // KarilAgilityDrain.kt for sourcing, both reusing BarrowsSetEffects' shared formula.
            ToragEnergyDrain.onDamageDealt(pawn, target, getCombatClass(pawn))
            KarilAgilityDrain.onDamageDealt(pawn, target, getCombatClass(pawn))
            // Prayer subsystem batch: Smite's prayer-drain effect (see Smite.kt for sourcing) -
            // same once-per-landed-hit dispatcher as the effects above it.
            Smite.onDamageDealt(pawn, target, totalDamage)
            // Prayer subsystem batch 39: Redemption's auto-heal effect (see Redemption.kt for
            // sourcing) - target-side, triggers on the victim rather than the attacker.
            Redemption.onDamageDealt(target, totalDamage)
        }
    }

    return pawnHit
}

suspend fun Pawn.moveToAttackRange(
    it: QueueTask,
    target: Pawn,
    distance: Int,
    projectile: Boolean,
): Boolean = Combat.moveToAttackRange(it, this, target, distance, projectile)

fun Pawn.postAttackLogic(target: Pawn) = Combat.postAttack(this, target)

fun Pawn.createProjectile(
    srcTile: Tile,
    target: Tile,
    gfx: Int,
    type: ProjectileType,
    endHeight: Int = -1,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = srcTile, target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = type.startHeight, endHeight = if (endHeight != -1) endHeight else type.endHeight)
            .setSlope(angle = type.angle, steepness = type.steepness)
            .setTimes(delay = type.delay, lifespan = type.delay + Combat.getProjectileLifespan(this, target, type))

    return builder.build()
}

fun Pawn.createProjectile(
    target: Tile,
    gfx: Int,
    type: ProjectileType,
    endHeight: Int = -1,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = tile, target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = type.startHeight, endHeight = if (endHeight != -1) endHeight else type.endHeight)
            .setSlope(angle = type.angle, steepness = type.steepness)
            .setTimes(delay = type.delay, lifespan = type.delay + Combat.getProjectileLifespan(this, target, type))

    return builder.build()
}

fun Pawn.createProjectile(
    target: Pawn,
    gfx: Int,
    type: ProjectileType,
    endHeight: Int = -1,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = getFrontFacingTile(target), target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = type.startHeight, endHeight = if (endHeight != -1) endHeight else type.endHeight)
            .setSlope(angle = type.angle, steepness = type.steepness)
            .setTimes(delay = type.delay, lifespan = type.delay + Combat.getProjectileLifespan(this, target.tile, type))

    return builder.build()
}

/** Explicit projectile timing for rev-667 donor routes that do not use a preset projectile type. */
fun Pawn.createProjectile(
    target: Pawn,
    gfx: Int,
    startHeight: Int,
    endHeight: Int,
    angle: Int,
    steepness: Int,
    delay: Int,
    lifespan: Int,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = getFrontFacingTile(target), target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight)
            .setSlope(angle = angle, steepness = steepness)
            .setTimes(delay = delay, lifespan = lifespan)
    return builder.build()
}

fun Pawn.createProjectile(
    target: Tile,
    gfx: Int,
    startHeight: Int,
    endHeight: Int = -1,
    angle: Int,
    steepness: Int,
    delay: Int,
    lifespan: Int,
): Projectile {
    val builder =
        Projectile
            .Builder()
            .setTiles(start = tile, target = target)
            .setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = if (endHeight != -1) endHeight else endHeight)
            .setSlope(angle = angle, steepness = steepness)
            .setTimes(delay = delay, lifespan = lifespan)
    return builder.build()
}

fun Pawn.poison(
    initialDamage: Int,
    onPoison: (() -> Unit)? = null,
) {
    if (!Poison.isImmune(this) && Poison.poison(this, initialDamage)) {
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.notePoisoner(this)
        Poison.setPoisonVarp(this, Poison.OrbState.POISON)
        onPoison?.invoke()
    }
}

fun Pawn.venom(onVenom: (() -> Unit)? = null) {
    if (Venom.envenom(this)) {
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.notePoisoner(this)
        onVenom?.invoke()
    }
}

/**
 * Lunar Disruption Shield: nullifies the next damaging hit a player takes from another player
 * (2011 wiki: "Nullify the next hit you receive from another player"). Consumed on use.
 */
private fun disruptionShieldAbsorbs(
    attacker: Pawn,
    target: Pawn,
    damage: Int,
): Boolean {
    if (damage <= 0 || attacker !is Player || target !is Player) return false
    if (target.attr[gg.rsmod.game.model.attr.DISRUPTION_SHIELD_ATTR] != true) return false
    target.attr.remove(gg.rsmod.game.model.attr.DISRUPTION_SHIELD_ATTR)
    target.graphic(1841)
    target.message("Your disruption shield absorbs the attack.")
    attacker.message("${target.username}'s disruption shield absorbs your attack.")
    return true
}
