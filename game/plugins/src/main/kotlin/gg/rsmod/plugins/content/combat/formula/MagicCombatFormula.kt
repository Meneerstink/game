package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MagicCombatFormula : CombatFormula {

    /**
     * N69: the capes that let Charge power each god spell - OSRS Wiki DPS calculator (weirdgloop osrs-dps-calc BaseCalc
     * isChargeSpellApplicable: god cape, imbued god cape, (imbued) god max cape), plus the locked (l) variants we imported.
     * The cape belongs to the effect only; casting Charge needs no cape (OSRS Wiki Charge, Darkan Magic.java:237-248, Void Charge.kt).
     */
    val CHARGE_CAPES: Map<CombatSpell, Set<Int>> =
        mapOf(
            CombatSpell.SARADOMIN_STRIKE to
                setOf(Items.SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_L, Items.IMBUED_SARADOMIN_MAX_CAPE, Items.IMBUED_SARADOMIN_MAX_CAPE_L),
            CombatSpell.CLAWS_OF_GUTHIX to
                setOf(Items.GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_L, Items.IMBUED_GUTHIX_MAX_CAPE, Items.IMBUED_GUTHIX_MAX_CAPE_L),
            CombatSpell.FLAMES_OF_ZAMORAK to
                setOf(Items.ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_L, Items.IMBUED_ZAMORAK_MAX_CAPE, Items.IMBUED_ZAMORAK_MAX_CAPE_L),
        )

    fun chargeApplies(
        spell: CombatSpell,
        capeId: Int?,
    ): Boolean = capeId != null && CHARGE_CAPES[spell]?.contains(capeId) == true

    private val BOLT_SPELLS =
        enumSetOf(CombatSpell.WIND_BOLT, CombatSpell.WATER_BOLT, CombatSpell.EARTH_BOLT, CombatSpell.FIRE_BOLT)

    private val FIRE_SPELLS =
        enumSetOf(
            CombatSpell.FIRE_STRIKE,
            CombatSpell.FIRE_BOLT,
            CombatSpell.FIRE_BLAST,
            CombatSpell.FIRE_WAVE,
            CombatSpell.FIRE_SURGE,
        )

    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double {
        // Check if the target has the prayer protection and the attacker is not a player
        // Deadman guards (CityGuards) are exempt: "Protection prayers are ineffective against their damage."
        if ((target.isProtectedFrom(CombatClass.MAGIC) && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.deflects(target, CombatClass.MAGIC)) &&
            pawn !is Player &&
            !gg.rsmod.plugins.content.mechanics.pvp.CityGuards.bypassesProtectionPrayer(pawn)
        ) {
            return 0.0 // Hits will never land
        }
        return getUnprotectedAccuracy(pawn, target, specialAttackMultiplier)
    }

    /**
     * Accuracy roll without the protection-prayer short circuit, for npcs whose magic attacks are
     * only partially blocked by Protect from Magic (e.g. the Corporeal Beast).
     */
    fun getUnprotectedAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double = 1.0,
    ): Double {
        val attack = getAttackRoll(pawn, target, specialAttackMultiplier)
        val defence =
            if (target is Player) {
                getDefenceRoll(target)
            } else if (target is Npc) {
                getDefenceRoll(pawn, target)
            } else {
                throw IllegalArgumentException("Unhandled pawn.")
            }.let { roll ->
                // Brimstone ring (OSRS Wiki): "a 25% chance that the target's defensive roll will be reduced by 10% for that attack" -
                // every magical attack, players and NPCs alike.
                if (pawn is Player && pawn.hasEquipped(EquipmentType.RING, Items.BRIMSTONE_RING) && pawn.world.randomDouble() < 0.25) {
                    roll - roll / 10
                } else {
                    roll
                }
            }

        val accuracy: Double
        if (attack > defence) {
            accuracy = 1.0 - (defence + 2.0) / (2.0 * (attack + 1.0))
        } else {
            accuracy = attack / (2.0 * (defence + 1))
        }
        return accuracy
    }

    override fun getMaxHit(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        // Deadman guards: every guard kind (melee, ranged, mage) deals the same sourced consecutive-hit
        // ramp (CityGuards.rampedMaxHit) instead of its spell's base - the owner wants all guards on
        // identical stats, and the melee/ranged formulas already route through the same ramp.
        if (pawn is Npc && target is Player && gg.rsmod.plugins.content.mechanics.pvp.CityGuards.isGuard(pawn)) {
            return gg.rsmod.plugins.content.mechanics.pvp.CityGuards.rampedMaxHit(pawn, target).toDouble()
        }
        // Nightmare staff specials supply their own spell base; the autocast spell's effects then do not apply (NightmareStaves).
        val specialBase = pawn.attr[gg.rsmod.plugins.content.items.osrs.NightmareStaves.SPECIAL_BASE_MAX_HIT]
        val spell = if (specialBase != null) null else pawn.attr[Combat.CASTING_SPELL]
        // Powered staff built-in spell (PoweredStaves): max(1, ⌊current Magic/3⌋ + offset) replaces the spell base.
        var hit =
            specialBase?.toDouble()
                ?: spell?.let { s ->
                    // Players: OSRS level-scaled base (elemental tiers, Magic Dart); npcs cast the fixed base.
                    (if (pawn is Player) CombatSpell.baseMaxHit(s, pawn.skills.getCurrentLevel(Skills.MAGIC)) else s.maxHit).toDouble()
                }
                ?: (pawn as? Player)?.let { p ->
                    gg.rsmod.plugins.content.items.osrs.PoweredStaves.wielded(p)?.baseMaxHit(p.skills.getCurrentLevel(Skills.MAGIC))?.toDouble()
                }
                ?: 1.0
        if (pawn is Player) {
            if (pawn.hasEquipped(EquipmentType.GLOVES, Items.CHAOS_GAUNTLETS) &&
                spell != null &&
                spell in BOLT_SPELLS
            ) {
                hit += 3
            }

            // Charge spell: god spells hit up to 30 while charged and wearing the matching cape.
            if (spell != null && pawn.attr[gg.rsmod.game.model.attr.GOD_SPELL_CHARGE_ATTR] == true) {
                if (chargeApplies(spell, pawn.getEquipment(EquipmentType.CAPE)?.id)) {
                    hit += 10
                }
            }

            // OSRS Wiki "Maximum magic hit": ⌊⌊Base + Gauntlets + Charge⌋ × (1 + Visible bonuses + Void + Prayer)⌋ -
            // the percentages add up before multiplying. The player bonus slot holds tenths of a percent.
            val additive =
                pawn.getMagicDamageBonus() / 1000.0 + getEliteVoidMagicDamage(pawn) + getPrayerMagicDamage(pawn) +
                    gg.rsmod.plugins.content.items.osrs.SmokeStaves.magicDamageBonus(pawn, spell) +
                    gg.rsmod.plugins.content.items.osrs.VirtusRobes.ancientMagicksBonus(pawn, spell)
            val baseMax = Math.floor(hit)
            hit = Math.floor(baseMax * (1.0 + additive))
            // Imbued Slayer helmet (OSRS-audit 2026-09-17b, see TargetModifiers.IMBUED_SLAYER_HELMETS):
            // "a 15% boost to Magic accuracy and Magic damage" against the player's current Slayer task.
            if (TargetModifiers.hasImbuedSlayerHelmetTaskBoost(pawn, target)) {
                hit = Math.floor(hit * TargetModifiers.IMBUED_SLAYER_HELMET_BOOST)
            }
            // Revenant sceptres: "an additional 50% magic accuracy and damage boost ... when attacking any NPC in the Wilderness",
            // "also applied to any combat spells cast when it is equipped" (RevenantWeapons).
            if (pawn is Player && gg.rsmod.plugins.content.items.osrs.RevenantWeapons.wildernessBuff(pawn, target)) hit = Math.floor(hit * 3 / 2)
            // N522: Amulet of avarice, "an additional 20% increase in accuracy and damage when fighting revenants" (OSRS Wiki).
            if (pawn is Player && gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantGear.avariceBoost(pawn, target)) hit = Math.floor(hit * 6 / 5)
            if (pawn is Player && gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantGear.surgeBoost(pawn, target)) hit = Math.floor(hit * 23 / 20)
            // Elemental weakness: + ⌊Base Max × weakness %⌋, after the slayer/salve step (ElementalWeakness).
            hit += Math.floor(baseMax * ElementalWeakness.percent(target, spell) / 100.0)
            // Charged tomes: "stacking multiplicatively with Magic damage bonuses" (Tomes).
            hit = Math.floor(hit * gg.rsmod.plugins.content.items.osrs.Tomes.damageMultiplier(pawn, target, spell))
            // Dragon hunter wand: max hit x7/5 against draconic targets (wiki DPS calculator trackFactor [7, 5]).
            if (pawn.hasEquipped(EquipmentType.WEAPON, Items.DRAGON_HUNTER_WAND) && Draconic.isDraconic(target)) {
                hit = Math.floor(hit * 7 / 5)
            }
            // "Damage per second/Magic": against a player praying Protect from Magic, multiply by 6/10 - the same step
            // melee and ranged already take (owner audit 2026-09-22: magic was the only style that ignored it in PvP).
            // Deflect Magic is excluded here because AncientCurses.deflectDamageTaken applies its own 6/10, never both.
            if (target is Player && target.isProtectedFrom(CombatClass.MAGIC) && !AncientCurses.deflects(target, CombatClass.MAGIC)) {
                hit = Math.floor(hit * 0.6)
            }
        } else if (pawn is Npc) {
            val spell = pawn.attr[Combat.CASTING_SPELL]
            if (spell == null) {
                val a = getEffectiveAttackLevel(pawn)
                val b = 1.0 + (pawn.getMagicDamageBonus() / 100.0)

                var base = 0.5 + a * (b + 64.0) / 640.0
                hit = Math.floor(base)
            }
            else {
                val multiplier = 1.0 + (pawn.getMagicDamageBonus() / 100.0)
                hit *= multiplier
                hit = Math.floor(hit)
            }
        }

        hit *= getDamageDealMultiplier(pawn)
        hit = Math.floor(hit)

        return hit
    }

    private fun getAttackRoll(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double = 1.0,
    ): Int {
        val a =
            if (pawn is Player) {
                getEffectiveAttackLevel(pawn)
            } else if (pawn is Npc) {
                getEffectiveAttackLevel(pawn)
            } else {
                0.0
            }
        val b = getEquipmentAttackBonus(pawn)

        var maxRoll = a * (b + 64.0)
        if (pawn is Player) {
            maxRoll = applyAttackSpecials(pawn, target, maxRoll, specialAttackMultiplier)
            // Elemental weakness: +1 % magic accuracy per weakness point (ElementalWeakness).
            val weakness = ElementalWeakness.percent(target, pawn.attr[Combat.CASTING_SPELL])
            if (weakness > 0) maxRoll = Math.floor(maxRoll) + Math.floor(Math.floor(maxRoll) * weakness / 100.0)
        }
        return maxRoll.toInt()
    }

    /**
     * The npc magic defence roll. Public because [MeleeCombatFormula] shares it for the
     * MAGIC_MELEE attack style, which is rolled against magic defence whoever the target is -
     * previously only an npc-versus-player MAGIC_MELEE attack found this route, and an npc-versus-npc
     * one (a bloodveld, icefiend or pyrefiend retaliating against a summoned familiar) fell through
     * to the melee defence bonus, which has no MAGIC_MELEE case and threw IllegalStateException.
     *
     * [pawn] is unused; it is kept so the existing call sites read the same as the player overload.
     */
    fun getDefenceRoll(
        @Suppress("UNUSED_PARAMETER") pawn: Pawn,
        target: Npc,
    ): Int {
        // S2, 2026-09-03: this must read the TARGET npc's effective Defence level, not the
        // attacker's - `getEffectiveDefenceLevel(npc: Npc)` below existed but was never
        // called by anything before this fix, which is itself evidence the target-based
        // path was never wired in. See RSPS_DECISIONS.md.
        // "Damage per second/Magic": NPC magic defence roll = (9 + NPC Magic level) × (NPC magic defence + 64);
        // a monster's Defence level plays no part.
        val a = Math.floor(target.stats.getCurrentLevel(NpcSkills.MAGIC) * AncientCurses.drainMultiplier(target, Skills.MAGIC)) + 9.0
        val b = getEquipmentDefenceBonus(target)

        val maxRoll = a * (b + 64.0)
        return maxRoll.toInt()
    }

    public fun getDefenceRoll(target: Player): Int {
        var effectiveLvl = getEffectiveDefenceLevel(target)

        effectiveLvl *= 0.3
        effectiveLvl = Math.floor(effectiveLvl)

        var magicLvl = target.skills.getCurrentLevel(Skills.MAGIC).toDouble()
        magicLvl *= getPrayerMagicDefenceMultiplier(target) * AncientCurses.drainMultiplier(target, Skills.MAGIC)
        magicLvl = Math.floor(magicLvl)

        magicLvl *= 0.7
        magicLvl = Math.floor(magicLvl)

        val a = Math.floor(effectiveLvl + magicLvl).toInt()
        val b = getEquipmentDefenceBonus(target)

        val maxRoll = a * (b + 64.0)
        return maxRoll.toInt()
    }

    /** The spell whose effects apply: none while a Nightmare staff special supplies its own base ([NightmareStaves.SPECIAL_BASE_MAX_HIT]). */
    private fun castingSpell(player: Player): CombatSpell? =
        if (player.attr.has(gg.rsmod.plugins.content.items.osrs.NightmareStaves.SPECIAL_BASE_MAX_HIT)) null else player.attr[Combat.CASTING_SPELL]

    private fun applyAttackSpecials(
        player: Player,
        target: Pawn,
        base: Double,
        specialAttackMultiplier: Double = 1.0,
    ): Double {
        // The plain Salve amulet and black mask/Slayer helmet are melee-only; magic needs their imbued versions
        // ("Damage per second/Magic": 1.15 slayer helm (i) / salve (i)). Salve (i)/(ei) have no item ids anywhere
        // in this cache and stay unimplemented; the imbued Slayer helmet DOES exist (TargetModifiers.
        // IMBUED_SLAYER_HELMETS, fixed 2026-09-17b) and applies its 1.15 task-gated factor below.
        // Tome of Water: water spells +10 % (NPC) / +20 % (player), curse spells +20 % accuracy (Tomes).
        // Mystic smoke staff: +10 % additive magic accuracy for standard spells, before the tome factor (wiki DPS calculator order).
        var smoke = Math.floor(base * gg.rsmod.plugins.content.items.osrs.SmokeStaves.accuracyMultiplier(player, castingSpell(player)))
        if (TargetModifiers.hasImbuedSlayerHelmetTaskBoost(player, target)) {
            smoke = Math.floor(smoke * TargetModifiers.IMBUED_SLAYER_HELMET_BOOST)
        }
        if (gg.rsmod.plugins.content.items.osrs.RevenantWeapons.wildernessBuff(player, target)) smoke = Math.floor(smoke * 3 / 2)
        // N522: Amulet of avarice, "an additional 20% increase in accuracy and damage when fighting revenants" (OSRS Wiki).
        if (gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantGear.avariceBoost(player, target)) smoke = Math.floor(smoke * 6 / 5)
        if (gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantGear.surgeBoost(player, target)) smoke = Math.floor(smoke * 23 / 20)
        var roll = Math.floor(smoke * gg.rsmod.plugins.content.items.osrs.Tomes.accuracyMultiplier(player, target, castingSpell(player)))
        // Ice ancient sceptre: +10 % for ice spells on freezable, not frozen targets (AncientSceptres).
        roll = Math.floor(roll * gg.rsmod.plugins.content.items.osrs.AncientSceptres.iceAccuracyMultiplier(player, target, castingSpell(player)))
        // Audit I-08: Zuriel's staff, ice spells +10 % accuracy.
        roll = Math.floor(roll * gg.rsmod.plugins.content.items.osrs.ZurielsStaff.iceAccuracyMultiplier(player, castingSpell(player)))
        // Dragon hunter wand: attack roll x7/4 against draconic targets (wiki DPS calculator trackFactor [7, 4]).
        if (player.hasEquipped(EquipmentType.WEAPON, Items.DRAGON_HUNTER_WAND) && Draconic.isDraconic(target)) {
            roll = Math.floor(roll * 7 / 4)
        }
        // Special attack accuracy factor (Volatile Nightmare staff Immolate [3, 2] in the wiki DPS calculator), applied last.
        roll = Math.floor(roll * specialAttackMultiplier)
        return roll
    }

    private fun getEffectiveAttackLevel(player: Player): Double {
        var effectiveLevel = Math.floor(player.skills.getCurrentLevel(Skills.MAGIC) * getPrayerAttackMultiplier(player) * AncientCurses.drainMultiplier(player, Skills.MAGIC))

        // Owner decision 2026-09-14 (b) = wiki DPS calculator `getPlayerMaxMagicAttackRoll`: Accurate stance +2 (powered staves),
        // +9, then Void magic trunc(x * 29 / 20) after the stance bonus.
        effectiveLevel += 9.0 + gg.rsmod.plugins.content.items.osrs.PoweredStaves.stanceMagicBonus(player)

        if (VoidKnight.wearing(player, VoidKnight.MAGE_HELMS)) {
            effectiveLevel = (effectiveLevel.toInt() * 29 / 20).toDouble()
        }

        return Math.floor(effectiveLevel)
    }

    /** Void Knight equipment: elite magic void adds +5 % magic damage. */
    private fun getEliteVoidMagicDamage(player: Player): Double = if (VoidKnight.wearingElite(player, VoidKnight.MAGE_HELMS)) 0.05 else 0.0

    /** "Maximum magic hit": Mystic Lore +1 %, Mystic Might +2 %, Augury +4 % magic damage. */
    private fun getPrayerMagicDamage(player: Player): Double =
        when {
            // N463 Ruinous Powers: Ancient Will +3 %, Trinitas +2 %, Vaporise +8 % (N496) magic damage.
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.RUINOUS -> gg.rsmod.plugins.content.mechanics.prayer.RuinousPowers.magicDamage(player)
            Prayers.isActive(player, Prayer.MYSTIC_LORE) -> 0.01
            Prayers.isActive(player, Prayer.MYSTIC_MIGHT) -> 0.02
            Prayers.isActive(player, Prayer.AUGURY) -> 0.04
            // Owner 2026-10-09: Leech Magic hits like Augury (+4 % magic damage) on top of its +1 Magic level per proc.
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT &&
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isCurseActive(player, gg.rsmod.plugins.content.mechanics.prayer.AncientCurse.LEECH_MAGIC) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.LEECH_MAGIC_DAMAGE
            // N496 (owner 2026-10-31): Sap Mage = Mystic Might (+2 % magic damage); Sap and Leech Magic never run together.
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT &&
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isCurseActive(player, gg.rsmod.plugins.content.mechanics.prayer.AncientCurse.SAP_MAGE) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.SAP_MAGE_MAGIC_DAMAGE
            else -> 0.0
        }

    private fun getEffectiveDefenceLevel(player: Player): Double {
        var effectiveLevel =
            Math.floor(
                player.skills.getCurrentLevel(Skills.DEFENCE) * getPrayerDefenceMultiplier(player) * AncientCurses.drainMultiplier(player, Skills.DEFENCE) * gg.rsmod.plugins.content.items.osrs.AmuletOfTheDamned.toragDefenceMultiplier(player),
            )

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.DEFENSIVE -> 3.0
                WeaponStyle.CONTROLLED -> 1.0
                WeaponStyle.LONG_RANGE -> 3.0
                else -> 0.0
            }

        effectiveLevel += 8.0

        return Math.floor(effectiveLevel)
    }

    private fun getEffectiveAttackLevel(npc: Npc): Double {
        var effectiveLevel = Math.floor(npc.stats.getCurrentLevel(NpcSkills.MAGIC) * AncientCurses.drainMultiplier(npc, Skills.MAGIC))
        effectiveLevel += 9 // "Damage per second/Melee": the +8 constant plus "If you're calculating for: An NPC, +1"
        return effectiveLevel
    }

    private fun getEquipmentAttackBonus(pawn: Pawn): Double {
        return pawn.getBonus(BonusSlot.ATTACK_MAGIC).toDouble()
    }

    private fun getEquipmentDefenceBonus(target: Pawn): Double {
        return target.getBonus(BonusSlot.DEFENCE_MAGIC).toDouble()
    }

    private fun getPrayerAttackMultiplier(player: Player): Double =
        when {
            // N463 Ruinous Powers: Ancient Will 20 %, Trinitas 15 %, Vaporise 35 %, Intensify 40 % (N496).
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.RUINOUS -> gg.rsmod.plugins.content.mechanics.prayer.RuinousPowers.magicAttack(player)
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.curseBoostMultiplier(player, Skills.MAGIC)
            Prayers.isActive(player, Prayer.MYSTIC_WILL) -> 1.05
            Prayers.isActive(player, Prayer.MYSTIC_LORE) -> 1.10
            Prayers.isActive(player, Prayer.MYSTIC_MIGHT) -> 1.15
            Prayers.isActive(player, Prayer.AUGURY) -> 1.25
            else -> 1.0
        }

    /**
     * N487: the prayer multiplier on the Magic level of a DEFENDING player. The Normal Mystic prayers and Augury and the curses
     * keep counting (OSRS Augury: "magical attack and defence"); the Ruinous offence prayers do not - OSRS Wiki Ancient Will,
     * Trinitas, Vaporise and Intensify list "Magic defence 0%", they only raise accuracy.
     */
    internal fun getPrayerMagicDefenceMultiplier(player: Player): Double =
        if (gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.RUINOUS) {
            1.0
        } else {
            getPrayerAttackMultiplier(player)
        }

    private fun getPrayerDefenceMultiplier(player: Player): Double =
        when {
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.curseBoostMultiplier(player, Skills.DEFENCE)
            Prayers.isActive(player, Prayer.THICK_SKIN) -> 1.05
            Prayers.isActive(player, Prayer.ROCK_SKIN) -> 1.10
            Prayers.isActive(player, Prayer.STEEL_SKIN) -> 1.15
            Prayers.isActive(player, Prayer.CHIVALRY) -> 1.20
            Prayers.isActive(player, Prayer.PIETY) -> 1.25
            Prayers.isActive(player, Prayer.RIGOUR) -> 1.25
            Prayers.isActive(player, Prayer.AUGURY) -> 1.25
            else -> 1.0
        }

    private fun getDamageDealMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_DEAL_MULTIPLIER] ?: 1.0
}
