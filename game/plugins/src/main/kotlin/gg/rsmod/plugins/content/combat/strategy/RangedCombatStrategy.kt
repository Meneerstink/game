package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.combat.XpMode
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import gg.rsmod.plugins.content.combat.strategy.ranged.Chinchompas
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Javelins
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Knives
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.BowType
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows
import gg.rsmod.plugins.content.combat.strategy.ranged.weapon.CrossbowType
import gg.rsmod.plugins.content.items.osrs.Blowpipe
import gg.rsmod.plugins.content.items.osrs.BlowpipeCombat
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver
import gg.rsmod.plugins.content.mechanics.weapons.HandCannon

/**
 * @author Tom <rspsmods@gmail.com>
 */
object RangedCombatStrategy : CombatStrategy {
    private const val DEFAULT_ATTACK_RANGE = 7

    /** OSRS Wiki "Aquanite hopper": "crossbows are given an 11% chance to fire a second shot". */
    private const val AQUANITE_SECOND_SHOT_CHANCE = 0.11

    private const val MAX_ATTACK_RANGE = 10

    override fun getAttackRange(pawn: Pawn): Int {
        if (pawn is Player) {
            val weapon = pawn.getEquipment(EquipmentType.WEAPON)
            val attackStyle = CombatConfigs.getAttackStyle(pawn)

            var range =
                when (weapon?.id) {
                    Items.BLACK_SALAMANDER -> 1 // TODO ADD ALL SALAMANDERS
                    in Darts.DARTS -> 3
                    Items.SLING, Items.KAYLES_SLING -> 2
                    in Knives.KNIVES -> 4
                    in Javelins.JAVELINS, Items.COMP_OGRE_BOW -> 5
                    Items.DORGESHUUN_CBOW -> 6
                    // OSRS Wiki "Hunters' sunlight crossbow": 8 tiles (10 on longrange).
                    Items.HUNTERS_SUNLIGHT_CROSSBOW -> 8
                    Items.SEERCULL -> 8
                    // OSRS Wiki "Heavy ballista": attack range 9 (10 on longrange).
                    // OSRS-IMPORT bows (wiki item pages): Craw's/Webweaver 9, Venator 6, Scorching 10, Tonalztics 6 (7 charged).
                    Items.CRAWS_BOW_U, Items.CRAWS_BOW, Items.WEBWEAVER_BOW_U, Items.WEBWEAVER_BOW -> 9
                    Items.VENATOR_BOW, Items.VENATOR_BOW_UNCHARGED, Items.TONALZTICS_OF_RALOS_UNCHARGED -> 6
                    Items.TONALZTICS_OF_RALOS -> 7
                    Items.SCORCHING_BOW -> 10
                    in Bows.LONG_BOWS, Items.CHINCHOMPA_10033, Items.RED_CHINCHOMPA_10034, Items.BLACK_CHINCHOMPA, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR -> 9
                    // S4, 2026-09-03: OSRS Wiki "Twisted bow" - "attack range of 10 tiles ...
                    // matching the maximum range in the game", also matches A4's own sourced
                    // param 13 = 10 read from the pinned upstream item def.
                    Items.TWISTED_BOW, in Bows.CRYSTAL_BOWS -> 10
                    else -> DEFAULT_ATTACK_RANGE
                }

            if (attackStyle == WeaponStyle.LONG_RANGE) {
                range += if (weapon?.id == Items.SLING) 1 else 2
                if (range > 10) range = 10
            }

            return Math.min(MAX_ATTACK_RANGE, range)
        }
        return DEFAULT_ATTACK_RANGE
    }

    override fun canAttack(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        if (pawn is Player) {
            val weapon = pawn.getEquipment(EquipmentType.WEAPON)
            val ammo = pawn.getEquipment(EquipmentType.AMMO)

            if (weapon?.id == Items.TOXIC_BLOWPIPE && !Blowpipe.canFire(weapon)) {
                pawn.message(if (Blowpipe.scales(weapon) <= 0) Blowpipe.NO_SCALES_MESSAGE else Blowpipe.NO_DARTS_MESSAGE)
                pawn.resetFacePawn()
                return false
            }

            // Craw's bow / Webweaver bow: "It has to be charged with revenant ether to be fired" (RevenantBows).
            if (weapon != null && weapon.id in gg.rsmod.plugins.content.items.osrs.RevenantBows.ALL &&
                !gg.rsmod.plugins.content.items.osrs.RevenantBows.canFire(weapon)
            ) {
                pawn.message(gg.rsmod.plugins.content.items.osrs.RevenantBows.NO_ETHER_MESSAGE)
                pawn.resetFacePawn()
                return false
            }

            // Ammo slot first, then a worn Dizana's quiver's stored ammo (RangedAmmo).
            val fired = RangedAmmo.fired(pawn)
            val crossbow = CrossbowType.values.firstOrNull { it.item == weapon?.id }
            if (crossbow != null && fired == null) {
                val message =
                    if (ammo !=
                        null
                    ) {
                        "You can't use that ammo with your crossbow."
                    } else {
                        "There is no ammo left in your quiver."
                    }
                pawn.message(message)
                pawn.resetFacePawn()
                return false
            }

            val bow = BowType.values.firstOrNull { it.item == weapon?.id }
            if (bow != null && bow.ammo.isNotEmpty()) {
                if (fired == null) {
                    val message =
                        if (ammo !=
                            null
                        ) {
                            "You can't use that ammo with your bow."
                        } else {
                            "There is no ammo left in your quiver."
                        }
                    pawn.message(message)
                    pawn.resetFacePawn()
                    return false
                }
            }
        }
        return true
    }

    override fun attack(
        pawn: Pawn,
        target: Pawn,
    ) {
        val world = pawn.world

        val animation = CombatConfigs.getAttackAnimation(pawn)

        /*
         * A list of actions that will be executed upon this hit dealing damage
         * to the [target].
         */
        var ammoDropAction: ((PawnHit).() -> Unit) = {}
        var boltAmmoId: Int? = null

        // The Toxic blowpipe fires its stored darts (charges on the item), never the weapon slot itself.
        val firedBlowpipe = pawn is Player && BlowpipeCombat.fire(pawn, target)

        if (pawn is Player && !firedBlowpipe) {
            /*
             * Get the [EquipmentType] for the ranged weapon you're using.
             */
            val ammoSlot =
                when {
                    pawn.hasWeaponType(WeaponType.THROWN) ||
                        pawn.hasWeaponType(WeaponType.CHINCHOMPA) ||
                        pawn.hasWeaponType(
                            WeaponType.SLING,
                        ) -> EquipmentType.WEAPON

                    else -> EquipmentType.AMMO
                }

            val fired = if (ammoSlot == EquipmentType.AMMO) RangedAmmo.fired(pawn) else null
            // The Tonalztics of Ralos is thrown but never used up ("effectively provides unlimited ammo").
            val ammo =
                if (ammoSlot == EquipmentType.AMMO) {
                    fired?.item
                } else {
                    pawn.getEquipment(ammoSlot)?.takeUnless { gg.rsmod.plugins.content.items.osrs.Tonalztics.isTonalztics(it.id) }
                }
            boltAmmoId = ammo?.id
            /*
             * Create a projectile based on ammo.
             */
            val ammoProjectile = if (ammo != null) RangedProjectile.values.firstOrNull { ammo.id in it.items } else null
            if (ammoProjectile != null) {
                val projectile = pawn.createProjectile(target, ammoProjectile.gfx, ammoProjectile.type)
                ammoProjectile.drawback?.let { drawback -> pawn.graphic(drawback) }
                ammoProjectile.impact?.let { impact -> target.graphic(impact.id, impact.height, projectile.lifespan) }
                world.spawn(projectile)
            } else if (gg.rsmod.plugins.content.items.osrs.Tonalztics.isTonalztics(pawn.getEquipment(EquipmentType.WEAPON)?.id)) {
                // ADAPTED_TO_667: the 667 rune thrownaxe projectile (OSRS Tonalztics graphics not imported).
                world.spawn(pawn.createProjectile(target, RangedProjectile.DRAGON_THROWNAXE.gfx, RangedProjectile.DRAGON_THROWNAXE.type))
            } else if (gg.rsmod.plugins.content.items.osrs.CrystalEquipment.isCrystalBow(pawn.getEquipment(EquipmentType.WEAPON)?.id) ||
                pawn.getEquipment(EquipmentType.WEAPON)?.id in gg.rsmod.plugins.content.items.osrs.RevenantBows.ALL
            ) {
                // Craw's / Webweaver bow shots: ADAPTED_TO_667 crystal bow arrow (OSRS WILD_CAVE_BOW_ARROW_* not imported).
                // Crystal bows fire their own arrow: Void donor arrows.gfx.toml special_arrow_shoot 250 (drawback, height 60) and
                // special_arrow 249 (projectile). Bow of Faerdhinen: OSRS SP_ATTACK_ARROW_LAUNCH/TRAVEL_FAERDHINEN imported (fxpilot),
                // same height as the crystal bow (ADAPTED).
                val bowfa = pawn.getEquipment(EquipmentType.WEAPON)?.id in
                    setOf(gg.rsmod.plugins.api.cfg.Items.BOW_OF_FAERDHINEN, gg.rsmod.plugins.api.cfg.Items.BOW_OF_FAERDHINEN_C)
                pawn.graphic(if (bowfa) gg.rsmod.plugins.content.items.osrs.OsrsGfx.FAERDHINEN_ARROW_LAUNCH else 250, 60)
                world.spawn(pawn.createProjectile(target, if (bowfa) gg.rsmod.plugins.content.items.osrs.OsrsGfx.FAERDHINEN_ARROW_TRAVEL else 249, ProjectileType.ARROW))
            }

            /*
             * Remove or drop ammo if applicable.
             */
            val ammoNeeded = if (ammoProjectile != null) ammoProjectile?.noAmmoNeeded() else true
            val breakOnImpact = if (ammoProjectile != null) ammoProjectile?.breakOnImpact() else false
            if (ammo != null) {
                val chance = world.random(99)
                val breakAmmo = chance in 0..19
                // OSRS Wiki "Ava's device": metallic torso armour stops the retrieval effect (AvasDevices).
                val device = !AvasDevices.interferes(pawn)
                val dropAmmo =
                    when {
                        // Attractor 60 % saved / 20 % dropped / 20 % broken (was 10 % dropped); accumulator 72 / 8 / 20;
                        // assembler 80 / 0 / 20 ("will never drop any ammo on the ground").
                        device && pawn.hasEquipped(EquipmentType.CAPE, Items.AVAS_ATTRACTOR) -> chance in 20..39
                        device && pawn.hasEquipped(EquipmentType.CAPE, Items.AVAS_ACCUMULATOR) -> chance in 20..27
                        device && pawn.hasEquipped(EquipmentType.CAPE, Items.AVAS_ASSEMBLER, Items.AVAS_ASSEMBLER_L) -> false
                        else -> !breakAmmo
                    }
                val amount = 1
                if (ammoNeeded == true) {
                    if (breakAmmo || dropAmmo) {
                        if (fired != null) RangedAmmo.consume(pawn, fired, amount) else pawn.equipment.remove(ammo.id, amount)
                    }
                    if (dropAmmo && breakOnImpact == false) {
                        ammoDropAction = { world.spawn(GroundItem(ammo.id, amount, target.tile, pawn)) }
                    }
                }
            }

            // Sounds for ranged weapons
            if (pawn.hasWeaponType(WeaponType.CROSSBOW)) pawn.playSound(Sfx.CROSSBOW) // crossbow sound
            if (pawn.hasWeaponType(WeaponType.BOW)) pawn.playSound(Sfx.SHORTBOW) // bow sound
            if (pawn.hasWeaponType(WeaponType.CHINCHOMPA)) pawn.playSound(Sfx.CHINCHOMPA_HIT) // chin sound
            if (pawn.hasWeaponType(WeaponType.THROWN)) pawn.playSound(Sfx.THROWN) // thrown item sound

            if (pawn.hasWeaponType(WeaponType.THROWN) || pawn.hasWeaponType(WeaponType.CHINCHOMPA)) {
                if (pawn.getEquipment(EquipmentType.WEAPON) == null) {
                    pawn.message("You do not have enough ammo left.")
                    pawn.attr[LAST_KNOWN_WEAPON_TYPE] = 0
                }
            }
        }
        if (pawn is Npc) {
            // Data-sourced ranged npcs (BulkNpcCombatDefs): the projectile/launch gfx live on the
            // combat def. Scripted npcs spawn their own projectiles and leave both at -1.
            if (pawn.combatDef.attackGfx > -1) {
                pawn.graphic(pawn.combatDef.attackGfx)
            }
            if (pawn.combatDef.attackProjectile > -1) {
                world.spawn(pawn.createProjectile(target, pawn.combatDef.attackProjectile, ProjectileType.ARROW))
            }
        }
        pawn.animate(animation)

        // P8, 2026-09-02: hand cannon explosion check, once per shot fired, before the
        // damage roll (matches the sourced "explosion is always checked on autoattacks"
        // rule) - see HandCannon.kt for the full sourcing note and its inferred numbers.
        if (pawn is Player && pawn.getEquipment(EquipmentType.WEAPON)?.id == Items.HAND_CANNON) {
            if (HandCannon.rollExplodes(world, HandCannon.firemakingLevel(pawn), isSpecialAttack = false)) {
                HandCannon.explode(pawn)
                return
            }
        }

        val formula = RangedCombatFormula
        // Chinchompas: attack roll x n/4 by fuse and distance to the target's closest tile (Chinchompas).
        val chinchompa = pawn is Player && Chinchompas.isChinchompa(pawn.getEquipment(EquipmentType.WEAPON)?.id)
        val fuseFactor =
            if (chinchompa) {
                val distance = Chinchompas.distanceToClosestTile(pawn.tile, target.tile, target.getSize())
                Chinchompas.accuracyNumerator((pawn as Player).getAttackStyle(), distance) / 4.0
            } else {
                1.0
            }
        val accuracy = formula.getAccuracy(pawn, target, fuseFactor)
        val maxHit = formula.getMaxHit(pawn, target)
        val landHit = accuracy >= world.randomDouble()
        val hitDelay =
            if (firedBlowpipe) {
                BlowpipeCombat.hitDelay(pawn.tile.getDistance(target.tile), special = false)
            } else {
                getHitDelay(pawn.getCentreTile(), target.tile.transform(target.getSize() / 2, target.getSize() / 2))
            }
        // Enchanted dragon bolts roll their effect on every normal crossbow shot (EnchantedBolts).
        val shot =
            if (pawn is Player) {
                EnchantedBolts.resolve(pawn, target, boltAmmoId, landHit, maxHit, EnchantedBolts.Special.NONE, world.randomDouble())
            } else {
                null
            }
        // Seeking arrows: "increase the player's minimum hit from 1 to 3 upon a successful hit" (capped at the max hit, SOURCE_GAP).
        val seekingMinimum =
            if (landHit && boltAmmoId in gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.SEEKING_ARROWS) {
                minOf(gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.SEEKING_MIN_HIT.toDouble(), maxHit)
            } else {
                null
            }
        val pawnHit =
            pawn.dealHit(
                target = target,
                minHit = shot?.minHit ?: seekingMinimum ?: DEFAULT_MIN_HIT,
                maxHit = shot?.maxHit ?: maxHit,
                landHit = shot?.landHit ?: landHit,
                delay = hitDelay,
                onHit = ammoDropAction,
                hitType = HitType.RANGE,
                bonusDamage = shot?.bonusDamage ?: 0,
            )
        val damage = pawnHit.hit.hitmarks.sumOf { it.damage }
        // Chinchompas: up to 11/12 targets (9/10 in PvP) in the 3x3 around the target; secondary targets hit exactly when the
        // primary target is hit, each with its own damage roll (Chinchompas).
        if (chinchompa) {
            val weaponId = (pawn as Player).getEquipment(EquipmentType.WEAPON)?.id
            val cap = Chinchompas.maxTargets(weaponId, pvp = target is Player) - 1
            gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.adjacentTargets(pawn as Player, target).take(cap).forEach { other ->
                val splash = pawn.dealHit(target = other, maxHit = formula.getMaxHit(pawn, other), landHit = landHit, delay = hitDelay, hitType = HitType.RANGE)
                val splashDamage = splash.hit.hitmarks.sumOf { it.damage }
                if (splashDamage > 0) addCombatXp(pawn, other, splashDamage)
            }
        }
        // Aquanite hopper: "crossbows are given an 11% chance to fire a second shot" with "33.3% reduced accuracy, 66.7% reduced damage,
        // and 66.7% reduced enchanted bolt proc chance", and no bolt effect when the first shot's already triggered (OSRS Wiki).
        // ADAPTED: no second projectile graphic; SOURCE_GAP: whether the second bolt is used up (it is not).
        if (pawn is Player && pawn.hasWeaponType(WeaponType.CROSSBOW) && pawn.getEquipment(EquipmentType.SHIELD)?.id == Items.AQUANITE_HOPPER &&
            world.randomDouble() < AQUANITE_SECOND_SHOT_CHANCE
        ) {
            val secondLand = formula.getAccuracy(pawn, target, 2.0 / 3.0) >= world.randomDouble()
            val secondMax = formula.getMaxHit(pawn, target, 1.0 / 3.0)
            val procRoll = if (shot?.bolt != null) 1.0 else world.randomDouble() * 3.0
            val second = EnchantedBolts.resolve(pawn, target, boltAmmoId, secondLand, secondMax, EnchantedBolts.Special.NONE, procRoll)
            val secondHit =
                pawn.dealHit(
                    target = target,
                    minHit = second.minHit,
                    maxHit = second.maxHit,
                    landHit = second.landHit,
                    delay = hitDelay,
                    hitType = HitType.RANGE,
                    bonusDamage = second.bonusDamage,
                )
            val secondDamage = secondHit.hit.hitmarks.sumOf { it.damage }
            second.bolt?.let { bolt -> secondHit.hit.addAction { EnchantedBolts.afterHit(bolt, pawn, target, secondDamage) } }
            if (secondDamage > 0) addCombatXp(pawn, target, secondDamage)
        }
        // Tonalztics of Ralos (charged): a second hit with its own accuracy and damage rolls.
        if (pawn is Player && gg.rsmod.plugins.content.items.osrs.Tonalztics.hits(pawn.getEquipment(EquipmentType.WEAPON)) == 2) {
            val second =
                pawn.dealHit(
                    target = target,
                    maxHit = maxHit,
                    landHit = formula.getAccuracy(pawn, target) >= world.randomDouble(),
                    delay = hitDelay,
                    hitType = HitType.RANGE,
                )
            val secondDamage = second.hit.hitmarks.sumOf { it.damage }
            if (secondDamage > 0) addCombatXp(pawn, target, secondDamage)
        }
        // Venator bow (charged, multicombat): up to two bounces, each rolling its own accuracy at 2/3 of the original max hit.
        if (pawn is Player && gg.rsmod.plugins.content.items.osrs.VenatorBow.active(pawn)) {
            var from: Pawn = target
            var delay = hitDelay
            repeat(gg.rsmod.plugins.content.items.osrs.VenatorBow.BOUNCES) {
                val options =
                    gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.adjacentTargets(
                        pawn,
                        from,
                        gg.rsmod.plugins.content.items.osrs.VenatorBow.BOUNCE_RADIUS,
                    )
                if (options.isEmpty()) return@repeat
                val next = options[world.random(options.size - 1)]
                // ADAPTED_TO_667: crystal bow arrow graphic for the ricochet (OSRS ARROW_VENATOR01_* not imported).
                world.spawn(from.createProjectile(next, 249, ProjectileType.ARROW))
                delay += getHitDelay(from.getCentreTile(), next.getCentreTile())
                val bounce =
                    pawn.dealHit(
                        target = next,
                        maxHit = gg.rsmod.plugins.content.items.osrs.VenatorBow.bounceMaxHit(maxHit),
                        landHit = formula.getAccuracy(pawn, next) >= world.randomDouble(),
                        delay = delay,
                        hitType = HitType.RANGE,
                    )
                val bounceDamage = bounce.hit.hitmarks.sumOf { it.damage }
                if (bounceDamage > 0) addCombatXp(pawn, next, bounceDamage)
                from = next
            }
        }
        if (pawn is Player) {
            gg.rsmod.plugins.content.items.osrs.RevenantBows.afterShot(pawn)
            gg.rsmod.plugins.content.items.osrs.VenatorBow.afterShot(pawn)
            gg.rsmod.plugins.content.items.osrs.Tonalztics.afterThrow(pawn)
        }
        if (firedBlowpipe) {
            pawnHit.hit.addAction { BlowpipeCombat.rollVenom(pawn as Player, target) }
        } else if (pawn is Player && DizanasQuiver.applies(pawn)) {
            // The shot gained Dizana's Sunfire (bonuses read above): one 1/3 charge roll.
            DizanasQuiver.afterShot(pawn)
        }
        // Bow of Faerdhinen: one charge per attack, hit or miss (CrystalEquipment).
        if (pawn is Player && !firedBlowpipe) {
            gg.rsmod.plugins.content.items.osrs.CrystalEquipment.afterBowShot(pawn)
        }
        val activated = shot?.bolt
        if (activated != null) {
            pawnHit.hit.addAction { EnchantedBolts.afterHit(activated, pawn as Player, target, damage) }
        }

        if (damage > 0 && pawn.entityType.isPlayer) {
            addCombatXp(pawn as Player, target, damage)
        }
    }

    fun getHitDelay(
        start: Tile,
        target: Tile,
    ): Int {
        val distance = start.getDistance(target)
        return 2 + (Math.floor((3.0 + distance) / 6.0)).toInt()
    }

    private fun addCombatXp(
        player: Player,
        target: Pawn,
        damage: Int,
    ) {
        val modDamage = if (target.entityType.isNpc) Math.min(target.getCurrentLifepoints(), damage) else damage
        val mode = CombatConfigs.getXpMode(player)
        val multiplier = if (target is Npc) Combat.getNpcXpMultiplier(target) else 1.0

        val hitpointsExperience = (modDamage * 0.133) * multiplier
        val combatExperience = (modDamage * 0.4) * multiplier
        val sharedExperience = (modDamage * 0.2) * multiplier
        var bonusRate = 1.0
        if (mode == XpMode.RANGED_XP) {
            bonusRate = player.addXp(Skills.RANGED, combatExperience, checkBrawlingGloves = true)
        } else if (mode == XpMode.SHARED_XP) {
            bonusRate = player.addXp(Skills.RANGED, sharedExperience, checkBrawlingGloves = true)
            player.addXp(Skills.DEFENCE, sharedExperience * bonusRate)
        }
        player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
    }
}
