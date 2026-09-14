package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.mechanics.run.RunEnergy
import gg.rsmod.plugins.content.skills.summoning.Familiar
import kotlin.math.floor

enum class PotionType(
    val alteredSkills: IntArray = intArrayOf(-1),
    val alterStrategy: Array<String> = emptyArray(),
    val message: String = "",
) {
    STRENGTH(alteredSkills = intArrayOf(Skills.STRENGTH), alterStrategy = arrayOf("r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SUPER_STRENGTH(alteredSkills = intArrayOf(Skills.STRENGTH), alterStrategy = arrayOf("s")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    ATTACK(alteredSkills = intArrayOf(Skills.ATTACK), alterStrategy = arrayOf("r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SUPER_ATTACK(alteredSkills = intArrayOf(Skills.ATTACK), alterStrategy = arrayOf("s")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    DEFENCE(alteredSkills = intArrayOf(Skills.DEFENCE), alterStrategy = arrayOf("r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SUPER_DEFENCE(alteredSkills = intArrayOf(Skills.DEFENCE), alterStrategy = arrayOf("s")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    MAGIC(alteredSkills = intArrayOf(Skills.MAGIC), alterStrategy = arrayOf("r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    RANGING(alteredSkills = intArrayOf(Skills.RANGED), alterStrategy = arrayOf("r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    FISHING(alteredSkills = intArrayOf(Skills.FISHING), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    AGILITY(alteredSkills = intArrayOf(Skills.AGILITY), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    ANTIPOISON {
        override fun apply(p: Player) {
            // Real rule (OSRS Wiki, "Venom"): any antipoison converts venom to regular
            // poison at the same damage instead of curing it - a second dose is needed to
            // clear the resulting poison. Only when the player isn't envenomed does this
            // dose act as a normal poison cure + immunity grant.
            if (Venom.downgradeToPoison(p)) {
                return
            }
            // RCV-010 A3: 90 seconds = 150 ticks (Void `antiPoison(90, SECONDS)`; Novite 86 000 ms).
            // The previous 1500 ticks was 15 minutes - ten times the 2011 duration.
            cureAndImmunise(p, PotionEffects.ANTIPOISON_IMMUNITY_TICKS)
        }
    },
    SUPER_ANTIPOISON {
        override fun apply(p: Player) {
            if (Venom.downgradeToPoison(p)) {
                return
            }
            // 6 minutes = 600 ticks (Void `antiPoison(6)` minutes; Novite 346 000 ms); was 6000.
            cureAndImmunise(p, PotionEffects.SUPER_ANTIPOISON_IMMUNITY_TICKS)
        }
    },
    ANTIFIRE {
        override fun apply(p: Player) {
            // Real duration (OSRS Wiki "Dragonfire"): 6 minutes / 600 ticks. Drinking a
            // regular antifire while a stronger super antifire is still active would
            // downgrade the protection, so leave the super timer alone if it's running.
            if (!p.timers.has(SUPER_ANTIFIRE_TIMER)) {
                p.timers[ANTIFIRE_TIMER] = 600
            }
        }
    },
    SUPER_ANTIFIRE {
        override fun apply(p: Player) {
            // Real duration (OSRS Wiki "Dragonfire"): 3 minutes / 300 ticks.
            p.timers[SUPER_ANTIFIRE_TIMER] = 300
        }
    },
    HUNTER(alteredSkills = intArrayOf(Skills.HUNTER), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    CRAFTING(alteredSkills = intArrayOf(Skills.CRAFTING), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    FLETCHING(alteredSkills = intArrayOf(Skills.FLETCHING), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    COMBAT(alteredSkills = intArrayOf(Skills.ATTACK, Skills.STRENGTH), alterStrategy = arrayOf("r", "r")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    RESTORE(
        alteredSkills = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC),
        alterStrategy = arrayOf("restore", "restore", "restore", "restore", "restore"),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SUPER_RESTORE(
        alteredSkills =
            intArrayOf(
                Skills.ATTACK,
                Skills.STRENGTH,
                Skills.DEFENCE,
                Skills.RANGED,
                Skills.MAGIC,
                Skills.PRAYER,
                Skills.COOKING,
                Skills.WOODCUTTING,
                Skills.FLETCHING,
                Skills.FISHING,
                Skills.FIREMAKING,
                Skills.CRAFTING,
                Skills.SMITHING,
                Skills.MINING,
                Skills.HERBLORE,
                Skills.AGILITY,
                Skills.THIEVING,
                Skills.SLAYER,
                Skills.FARMING,
                Skills.RUNECRAFTING,
                Skills.HUNTER,
                Skills.CONSTRUCTION,
                Skills.SUMMONING,
                Skills.DUNGEONEERING,
            ),
        alterStrategy =
            arrayOf(
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
                "s_restore",
            ),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    /**
     * One dose restores 25% of the player's maximum Summoning points plus 7,
     * and 15 points of the separate special-move pool.
     */
    SUMMONING {
        override fun apply(p: Player) {
            Familiar.restorePoints(p, Familiar.maxPoints(p) / 4 + 7)
            Familiar.restoreSpecialPoints(p, 15)
        }
    },
    PRAYER(alteredSkills = intArrayOf(Skills.PRAYER), alterStrategy = arrayOf("prayer")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SUPER_PRAYER(alteredSkills = intArrayOf(Skills.PRAYER), alterStrategy = arrayOf("s_prayer")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    SARADOMIN_BREW(
        alteredSkills =
            intArrayOf(
                Skills.CONSTITUTION,
                Skills.DEFENCE,
                Skills.ATTACK,
                Skills.STRENGTH,
                Skills.RANGED,
                Skills.MAGIC,
            ),
        alterStrategy =
            arrayOf(
                "brewHealth",
                "brewDef",
                "brewDrain",
                "brewDrain",
                "brewDrain",
                "brewDrain",
            ),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    BEER(
        alteredSkills = intArrayOf(Skills.CONSTITUTION, Skills.STRENGTH, Skills.ATTACK),
        alterStrategy = arrayOf("beerHealth", "beerStrength", "beerDrain"),
        message = "You drink the beer. You feel slightly reinvigorated... and slightly dizzy.",
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    JUG_OF_WINE(
        alteredSkills = intArrayOf(Skills.CONSTITUTION, Skills.ATTACK),
        alterStrategy = arrayOf("wineHealth", "wineDrain"),
        message = "You drink the wine. You feel slightly tipsy.",
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    HALF_FULL_WINE_JUG(
        alteredSkills = intArrayOf(Skills.CONSTITUTION, Skills.ATTACK),
        alterStrategy = arrayOf("wineHealth", "halfWineDrain"),
        message = "You drink the wine. You feel slightly tipsy.",
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    ASGARNIAN_ALE(
        alteredSkills = intArrayOf(Skills.CONSTITUTION, Skills.ATTACK, Skills.STRENGTH),
        alterStrategy = arrayOf("r", "mindBombDrain", "r"),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    WIZARDS_MIND_BOMB(
        alteredSkills = intArrayOf(Skills.CONSTITUTION, Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.MAGIC),
        alterStrategy = arrayOf("r", "mindBombDrain", "mindBombDrain", "mindBombDrain", "r"),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    DWARVEN_STOUT(
        alteredSkills =
            intArrayOf(
                Skills.CONSTITUTION,
                Skills.MINING,
                Skills.SMITHING,
                Skills.ATTACK,
                Skills.STRENGTH,
                Skills.DEFENCE,
            ),
        alterStrategy =
            arrayOf(
                "beerHealth",
                "dwarvenBoost",
                "dwarvenBoost",
                "dwarvenDrain",
                "dwarvenDrain",
                "dwarvenDrain",
            ),
    ) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },
    ENERGY {
        override fun apply(p: Player) {
            RunEnergy.renew(p, 20.0)
        }
    },

    // ---- RCV-010 A3: drinkable 667 potions that had no handler ("Unhandled item action") ----

    /** Novite 667 `Pots.SUPER_ENERGY`: +40 run energy. */
    SUPER_ENERGY {
        override fun apply(p: Player) {
            RunEnergy.renew(p, 40.0)
        }
    },

    /** Void 2011 `ZamorakBrew`/`PotionEffects` on its x10 unit, converted to 1:1 lifepoints. */
    ZAMORAK_BREW {
        override fun canDrink(p: Player): Boolean {
            if (p.getCurrentLifepoints() - PotionEffects.zamorakBrewDamage(p) < 0) {
                p.message("You need more hitpoints in order to survive the effects of the zamorak brew.")
                return false
            }
            return true
        }

        override fun apply(p: Player) {
            val damage = PotionEffects.zamorakBrewDamage(p)
            PotionEffects.boostCapped(p, Skills.ATTACK, 2 + floor(p.skills.getMaxLevel(Skills.ATTACK) * 0.20).toInt())
            PotionEffects.boostCapped(p, Skills.STRENGTH, 2 + floor(p.skills.getMaxLevel(Skills.STRENGTH) * 0.12).toInt())
            p.skills.alterCurrentLevel(Skills.DEFENCE, -(2 + floor(p.skills.getMaxLevel(Skills.DEFENCE) * 0.10).toInt()), -124)
            p.hit(damage)
        }
    },
    EXTREME_ATTACK {
        override fun canDrink(p: Player) = PotionEffects.notInWilderness(p)

        override fun apply(p: Player) = PotionEffects.applyExtreme(p, Skills.ATTACK)
    },
    EXTREME_STRENGTH {
        override fun canDrink(p: Player) = PotionEffects.notInWilderness(p)

        override fun apply(p: Player) = PotionEffects.applyExtreme(p, Skills.STRENGTH)
    },
    EXTREME_DEFENCE {
        override fun canDrink(p: Player) = PotionEffects.notInWilderness(p)

        override fun apply(p: Player) = PotionEffects.applyExtreme(p, Skills.DEFENCE)
    },
    EXTREME_MAGIC {
        override fun canDrink(p: Player) = PotionEffects.notInWilderness(p)

        override fun apply(p: Player) = PotionEffects.applyExtreme(p, Skills.MAGIC)
    },
    EXTREME_RANGING {
        override fun canDrink(p: Player) = PotionEffects.notInWilderness(p)

        override fun apply(p: Player) = PotionEffects.applyExtreme(p, Skills.RANGED)
    },
    OVERLOAD {
        override fun canDrink(p: Player) = PotionEffects.canDrinkOverload(p)

        override fun apply(p: Player) = PotionEffects.startOverload(p)
    },
    PRAYER_RENEWAL {
        override fun apply(p: Player) = PotionEffects.startPrayerRenewal(p)
    },
    RECOVER_SPECIAL {
        override fun canDrink(p: Player) = PotionEffects.canDrinkRecoverSpecial(p)

        override fun apply(p: Player) = PotionEffects.recoverSpecial(p)
    },

    /** Void 2011 `sanfew_serum`: super-antipoison cure/immunity plus a super restore. */
    SANFEW_SERUM {
        override fun apply(p: Player) {
            SUPER_ANTIPOISON.apply(p)
            SUPER_RESTORE.apply(p)
        }
    },

    /** Void 2011 `antipoison+`: 9 minutes. */
    ANTIPOISON_PLUS {
        override fun apply(p: Player) {
            if (Venom.downgradeToPoison(p)) return
            cureAndImmunise(p, PotionEffects.ANTIPOISON_PLUS_IMMUNITY_TICKS)
        }
    },

    /** Void 2011 `antipoison++`: 12 minutes. */
    ANTIPOISON_PLUS_PLUS {
        override fun apply(p: Player) {
            if (Venom.downgradeToPoison(p)) return
            cureAndImmunise(p, PotionEffects.ANTIPOISON_PLUS_PLUS_IMMUNITY_TICKS)
            // OSRS-IMPORT potions-venom ADJACENT FIX: "one dose of antidote++ will provide immunity to venom for 18-36 seconds" (PotionEffects).
            p.timers[gg.rsmod.game.model.timer.VENOM_IMMUNITY] = PotionEffects.ANTIDOTE_PLUS_PLUS_VENOM_IMMUNITY_TICKS
        }
    },

    /** Void 2011 `magic_essence`: +3 Magic. */
    MAGIC_ESSENCE(alteredSkills = intArrayOf(Skills.MAGIC), alterStrategy = arrayOf("r_skill")) {
        override fun apply(p: Player) {
            applyBoost(p, alteredSkills, alterStrategy)
        }
    },

    // OSRS-IMPORT potions-combat (DivinePotions).
    SUPER_COMBAT {
        override fun apply(p: Player) = DivinePotions.boost(p, intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE))
    },
    BASTION {
        override fun apply(p: Player) = DivinePotions.boost(p, intArrayOf(Skills.RANGED, Skills.DEFENCE))
    },
    BATTLEMAGE {
        override fun apply(p: Player) = DivinePotions.boost(p, intArrayOf(Skills.MAGIC, Skills.DEFENCE))
    },
    DIVINE_SUPER_COMBAT {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE))
    },
    DIVINE_SUPER_ATTACK {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.ATTACK))
    },
    DIVINE_SUPER_STRENGTH {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.STRENGTH))
    },
    DIVINE_SUPER_DEFENCE {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.DEFENCE))
    },
    DIVINE_RANGING {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.RANGED))
    },
    DIVINE_MAGIC {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.MAGIC))
    },
    DIVINE_BASTION {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.RANGED, Skills.DEFENCE))
    },
    DIVINE_BATTLEMAGE {
        override fun canDrink(p: Player) = DivinePotions.canDrinkDivine(p)

        override fun apply(p: Player) = DivinePotions.drinkDivine(p, intArrayOf(Skills.MAGIC, Skills.DEFENCE))
    },

    // OSRS-IMPORT potions-venom: "instantly cures venom and poison" with the immunity windows in PotionEffects.
    ANTI_VENOM {
        override fun apply(p: Player) = cureVenomAndPoison(p, PotionEffects.ANTI_VENOM_POISON_IMMUNITY_TICKS, PotionEffects.ANTI_VENOM_VENOM_IMMUNITY_TICKS)
    },
    ANTI_VENOM_PLUS {
        override fun apply(p: Player) =
            cureVenomAndPoison(p, PotionEffects.ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS, PotionEffects.ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS)
    },
    EXTENDED_ANTI_VENOM_PLUS {
        override fun apply(p: Player) =
            cureVenomAndPoison(p, PotionEffects.EXTENDED_ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS, PotionEffects.EXTENDED_ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS)
    }, ;

    protected fun cureVenomAndPoison(
        p: Player,
        poisonImmunityTicks: Int,
        venomImmunityTicks: Int,
    ) {
        Venom.cure(p, venomImmunityTicks)
        cureAndImmunise(p, poisonImmunityTicks)
        p.timers[gg.rsmod.game.model.timer.VENOM_IMMUNITY] = venomImmunityTicks
    }

    abstract fun apply(p: Player)

    /** Pre-drink gate; a refusal consumes nothing (Novite 667 `Effects.canDrink`). */
    open fun canDrink(p: Player): Boolean = true

    protected fun cureAndImmunise(
        p: Player,
        ticks: Int,
    ) {
        Poison.cure(p)
        p.timers[POISON_IMMUNITY] = ticks
    }

    fun applyBoost(
        p: Player,
        alteredSkills: IntArray,
        alterStrategy: Array<String>,
    ) {
        alteredSkills.forEachIndexed { index, i ->
            val cap = boostCap(p.skills.getMaxLevel(i), alterStrategy[index])
            val boost =
                boostQuantity(
                    p.skills.getMaxLevel(i).toDouble(),
                    alterStrategy[index],
                )
            // RCV-010 A2: Constitution used to fall through to alterCurrentLevel as well, so the heal
            // was applied twice (once to the lifepoint varbit, once more to the skill level). Only the
            // Saradomin brew is a sourced over-maximum heal (Novite `heal(15% + 20, 15%)` on x10); every
            // other drink heals up to the maximum - capValue is an allowance ABOVE the maximum.
            when (i) {
                Skills.CONSTITUTION -> p.heal(boost, if (alterStrategy[index] == "brewHealth") cap else 0)
                Skills.PRAYER -> p.restorePrayer(boost, cap)
                else -> p.skills.alterCurrentLevel(i, boost, cap)
            }
        }
    }

    private fun boostQuantity(
        currentLevel: Double,
        boostStrategy: String,
    ): Int {
        var boost = 0
        when (boostStrategy) {
            "r" -> boost = floor(currentLevel / 10).toInt() + 3
            "s" -> boost = floor(15 * (currentLevel / 100)).toInt() + 5
            "restore" -> boost = floor((currentLevel * 3) / 10).toInt() + 10
            "s_restore" -> boost = floor(currentLevel / 4).toInt() + 8
            "prayer" -> boost = floor(currentLevel / 4).toInt() + 7
            "s_prayer" -> boost = floor((currentLevel / 100) * 35).toInt() + 7
            "r_skill" -> boost = 3
            "mindBombDrain" -> boost = -(floor(currentLevel * 0.03) + 1).toInt()
            // "mindBombBoost" -> boost = floor(currentLevel * 0.02).toInt() + if (currentLevel >= 50) 3 else 1
            "brewHealth" -> boost = floor(15 * (currentLevel / 100)).toInt() + 2
            "brewDef" -> boost = floor(currentLevel / 5).toInt() + 2
            "brewDrain" ->
                boost =
                    if ((currentLevel - (floor(currentLevel / 10).toInt() - 2)) < 1) {
                        currentLevel.toInt() - 1
                    } else {
                        -(floor(currentLevel / 10).toInt() - 2)
                    }
            "dwarvenBoost" -> boost = 1
            "dwarvenDrain" -> boost = -2
            "beerHealth" -> boost = 1
            "beerStrength" -> boost = (floor(currentLevel * 0.04)).toInt()
            "beerDrain" -> boost = -(floor(currentLevel * 0.07)).toInt()
            "wineHealth" -> boost = 11
            "wineDrain" -> boost = -2
            "halfWineDrain" -> boost = -1
        }
        return boost
    }

    private fun boostCap(
        currentLevel: Int,
        boostStrategy: String,
    ): Int {
        var cap = 0
        when (boostStrategy) {
            "r" -> cap = boostQuantity(currentLevel.toDouble(), boostStrategy)
            "s" -> cap = boostQuantity(currentLevel.toDouble(), boostStrategy)
            "brewHealth" -> cap = boostQuantity(currentLevel.toDouble(), boostStrategy)
            "brewDef" -> cap = boostQuantity(currentLevel.toDouble(), boostStrategy)
            "brewDrain" ->
                cap =
                    if (currentLevel == 1) {
                        0
                    } else {
                        -124
                    }
            // RCV-010 A3: skill potions boost ABOVE the base level (Void `levels.boost(skill, 3)`); cap 0
            // meant "restore to base only", so fishing/agility/hunter/crafting/fletching did nothing at full level.
            "r_skill" -> cap = 3
            "beerStrength" -> cap = (currentLevel * 0.04).toInt()
            "dwarvenBoost" -> cap = 1
            "dwarvenDrain" ->
                cap =
                    if (currentLevel == 1) {
                        0
                    } else {
                        -124
                    }
        }
        return cap
    }
}
