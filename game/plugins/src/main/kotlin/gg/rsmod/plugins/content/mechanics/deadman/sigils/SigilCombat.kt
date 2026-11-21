package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.HitType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula

/**
 * PROJECT48 SIGILS - the one file the combat code calls (a single line per hook, not 65). The hooks sit in `Pawn.dealHit`
 * (content/combat/PawnExt.kt): [accuracy] and [maxHit] where the formula is rolled, [modifyDamage] on the rolled damage (the attacker's
 * damage sigils AND the target's damage-reduction sigils) and [afterHit] once the hit exists (heals, curses, recoil). Every function
 * returns its input unchanged when no sigil applies, so a player without sigils pays only the [any] check.
 * The per-sigil effects are added below, one block per sigil row (S01..S65), each commented with its wiki sentence.
 */
object SigilCombat {
    enum class Style { MELEE, RANGED, MAGIC, OTHER }

    /** Whether any sigil is attuned at all (the cheap early exit of every hook). */
    fun any(player: Player): Boolean = Sigils.attuned(player).isNotEmpty()

    fun styleOf(formula: CombatFormula): Style =
        when (formula) {
            is MeleeCombatFormula -> Style.MELEE
            is RangedCombatFormula -> Style.RANGED
            is MagicCombatFormula -> Style.MAGIC
            else -> Style.OTHER
        }

    fun styleOf(type: HitType): Style =
        when (type) {
            // The CRIT_ types are the same styles (boss scripts - ToA, the old Wilderness bosses - send them for their big hits).
            HitType.MELEE, HitType.CRIT_MELEE -> Style.MELEE
            HitType.RANGE, HitType.CRIT_RANGE -> Style.RANGED
            HitType.MAGIC, HitType.CRIT_MAGIC -> Style.MAGIC
            else -> Style.OTHER
        }

    /** Hit chance (0..1) of [attacker] against [target] after the sigils of the attacker. */
    fun accuracy(
        attacker: Pawn,
        target: Pawn,
        accuracy: Double,
        formula: CombatFormula,
    ): Double {
        if (attacker !is Player || !any(attacker)) return accuracy
        var factor = 1.0
        // S02 Deft Strikes (+20% vs non-player enemies), S24 Precision (+50% vs all monsters), S37 Meticulousness (+75% vs NPCs, the
        // higher-tier variant of Deft Strikes): they do not stack, the strongest one counts.
        if (target is Npc) {
            if (Sigils.effective(attacker, Sigil.DEFT_STRIKES)) factor = maxOf(factor, 1.20)
            if (Sigils.effective(attacker, Sigil.PRECISION)) factor = maxOf(factor, 1.50)
            if (Sigils.effective(attacker, Sigil.METICULOUSNESS)) factor = maxOf(factor, 1.75)
        }
        val style = styleOf(formula)
        val special = attacker.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] == true
        // S56 Finality: the next special attack inside the window cannot miss.
        if (special && Sigils.windowOpen(attacker, Sigil.FINALITY)) return 1.0
        // S44 Binding: +75% magic accuracy on the next binding spell inside the window.
        if (style == Style.MAGIC && Sigils.windowOpen(attacker, Sigil.BINDING) && castingBindingSpell(attacker)) {
            factor *= 1.75
            Sigils.consumeWindow(attacker, Sigil.BINDING)
        }
        // S57 Rampage: alternating combat styles build a count (max 3); each count is +3% accuracy and damage.
        factor *= 1.0 + 0.03 * rampageCount(attacker, style)
        // S20 Barrows: +40% accuracy with Barrows weaponry against players and NPCs.
        if (Sigils.effective(attacker, Sigil.BARROWS) && wieldsBarrowsWeapon(attacker)) factor *= 1.40
        // S29 Adroit: +1% accuracy for each hitpoint the player is missing (players and NPCs).
        if (Sigils.effective(attacker, Sigil.THE_ADROIT)) {
            val missing = (attacker.getMaximumLifepoints() - attacker.getCurrentLifepoints()).coerceAtLeast(0)
            factor *= 1.0 + missing / 100.0
        }
        return if (factor == 1.0) accuracy else scaleRoll(accuracy, factor)
    }

    /**
     * The hit chance after the attack roll is multiplied by [factor] (OSRS: chance = 1 - (D+2)/(2(A+1)) when A > D, else A/(2(D+1))).
     * Only the chance is known here, so the roll ratio is rebuilt from it: exact for the large rolls of real fights.
     */
    fun scaleRoll(
        chance: Double,
        factor: Double,
    ): Double {
        val p = chance.coerceIn(0.0, 1.0)
        if (p <= 0.0) return 0.0
        return if (p > 0.5) {
            1.0 - (1.0 - p) / factor
        } else if (p * factor <= 0.5) {
            p * factor
        } else {
            1.0 - 1.0 / (4.0 * p * factor)
        }.coerceIn(0.0, 1.0)
    }

    /** Extra attack-bonus points of the attuned sigils for [slot] (added where the formula reads the equipment attack bonus). */
    fun attackBonus(
        pawn: Pawn,
        slot: BonusSlot,
    ): Int {
        if (pawn !is Player || !any(pawn)) return 0
        var bonus = 0
        when (slot) {
            // S15 Meticulous Mage: magic accuracy +40.
            BonusSlot.ATTACK_MAGIC -> if (Sigils.effective(pawn, Sigil.THE_METICULOUS_MAGE)) bonus += 40
            // S17 Rigorous Ranger: ranged accuracy +20.
            BonusSlot.ATTACK_RANGED -> if (Sigils.effective(pawn, Sigil.THE_RIGOROUS_RANGER)) bonus += 20
            // S51 Formidable Fighter: melee accuracy +30 in stab, slash and crush.
            BonusSlot.ATTACK_STAB, BonusSlot.ATTACK_SLASH, BonusSlot.ATTACK_CRUSH ->
                if (Sigils.effective(pawn, Sigil.THE_FORMIDABLE_FIGHTER)) bonus += 30
            else -> {}
        }
        return bonus
    }

    /** Extra defence-bonus points of the attuned sigils for [slot]: S23 Fortification +50, S40 Rampart +100 (no stacking). */
    fun defenceBonus(
        pawn: Pawn,
        slot: BonusSlot,
    ): Int {
        if (pawn !is Player || !any(pawn)) return 0
        return when (slot) {
            BonusSlot.DEFENCE_STAB, BonusSlot.DEFENCE_SLASH, BonusSlot.DEFENCE_CRUSH, BonusSlot.DEFENCE_RANGED ->
                if (Sigils.effective(pawn, Sigil.THE_RAMPART)) 100 else if (Sigils.effective(pawn, Sigil.FORTIFICATION)) 50 else 0
            else -> 0
        }
    }

    /** Max hit of [attacker] against [target] after the sigils of the attacker. */
    fun maxHit(
        attacker: Pawn,
        target: Pawn,
        maxHit: Double,
        formula: CombatFormula,
    ): Double {
        if (attacker !is Player || !any(attacker)) return maxHit
        return maxHit
    }

    /** The rolled [damage] of a hit after the sigils of the attacker (damage dealt) and of the target (damage taken). */
    fun modifyDamage(
        attacker: Pawn,
        target: Pawn,
        damage: Double,
        type: HitType,
        landed: Boolean,
    ): Double {
        var result = damage
        if (attacker is Player && any(attacker)) result = dealt(attacker, target, result, type, landed)
        if (target is Player && any(target)) result = taken(target, attacker, result, type)
        return result
    }

    private fun dealt(
        attacker: Player,
        target: Pawn,
        damage: Double,
        type: HitType,
        landed: Boolean,
    ): Double {
        if (!landed) return damage
        var result = damage
        // S01 Consistency: every attack does +1 damage to the target.
        if (Sigils.effective(attacker, Sigil.CONSISTENCY)) result += 1.0
        // S51 Formidable Fighter: "Upon a successful hit, you have a 30% chance for your next hit to deal +10 damage" (see [formidable]).
        result += formidable(attacker, type, result)
        // S57 Rampage: +3% damage per count. "The damage bonus effect does not stack with the Sigil of Aggression": with Aggression
        // attuned only Aggression's 10% counts (Rampage keeps its accuracy).
        if (Sigils.effective(attacker, Sigil.RAMPAGE) && !Sigils.effective(attacker, Sigil.AGGRESSION)) {
            result = Math.round(result * (1.0 + 0.03 * (attacker.attr[RAMPAGE_COUNT] ?: 0))).toDouble()
        }
        // S36 Aggression: all attacks deal 10% more damage (a Rampage bonus does not stack with it).
        if (Sigils.effective(attacker, Sigil.AGGRESSION)) result = Math.round(result * 1.10).toDouble()
        return result
    }

    private val FORMIDABLE_NEXT_HIT = gg.rsmod.game.model.attr.AttributeKey<Boolean>()
    const val FORMIDABLE_BONUS = 10.0
    private const val FORMIDABLE_CHANCE = 0.30

    /**
     * S51 Formidable Fighter (wiki): "Upon a successful hit, you have a 30% chance for your next hit to deal +10 damage. The chance to
     * deal +10 damage on your next hit does not apply to special attacks." Returns the bonus of THIS landed hit (10 when the previous
     * successful hit armed it) and rolls the chance for the next one. A successful hit = a landed hit that deals damage. The sigil is a
     * melee sigil (its other half is melee accuracy), so only melee hits arm and spend it; a special attack never rolls the chance,
     * and it leaves an armed bonus waiting for the next normal hit.
     */
    private fun formidable(
        attacker: Player,
        type: HitType,
        damage: Double,
    ): Double {
        if (styleOf(type) != Style.MELEE || !Sigils.effective(attacker, Sigil.THE_FORMIDABLE_FIGHTER)) return 0.0
        if (attacker.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] == true) return 0.0
        if (damage <= 0.0) return 0.0
        val bonus = if (attacker.attr[FORMIDABLE_NEXT_HIT] == true) FORMIDABLE_BONUS else 0.0
        val armed = attacker.world.randomDouble() < FORMIDABLE_CHANCE
        attacker.attr[FORMIDABLE_NEXT_HIT] = armed
        if (armed) SigilFx.proc(attacker, Sigil.THE_FORMIDABLE_FIGHTER)
        return bonus
    }

    private fun taken(
        target: Player,
        attacker: Pawn,
        damage: Double,
        type: HitType,
    ): Double {
        if (damage <= 0.0) return damage
        var result = damage
        if (attacker is Npc) result = fromMonster(target, result)
        // S38 Pious Protection: 35% less damage of a style while the matching protection prayer is on, 5% more while a protection prayer
        // of another style is on (on top of what the prayer itself does).
        if (Sigils.effective(target, Sigil.PIOUS_PROTECTION)) {
            val cls = when (styleOf(type)) {
                Style.MELEE -> gg.rsmod.game.model.combat.CombatClass.MELEE
                Style.RANGED -> gg.rsmod.game.model.combat.CombatClass.RANGED
                Style.MAGIC -> gg.rsmod.game.model.combat.CombatClass.MAGIC
                else -> null
            }
            if (cls != null) {
                val right = target.isProtectedFrom(cls)
                val wrong = !right && gg.rsmod.game.model.combat.CombatClass.values().any { it != cls && target.isProtectedFrom(it) }
                if (right) result = Math.floor(result * 0.65) else if (wrong) result = Math.floor(result * 1.05)
            }
        }
        // S36 Aggression: you take 5% more damage from all sources.
        if (Sigils.effective(target, Sigil.AGGRESSION)) result = Math.round(result * 1.05).toDouble()
        return result
    }

    /** S39 Titanium (-60%) and S07 Resistance (-25%): "all attacks from monsters"; they do not stack (Titanium wins). */
    private fun fromMonster(
        target: Player,
        damage: Double,
    ): Double =
        when {
            Sigils.effective(target, Sigil.TITANIUM) -> Math.floor(damage * 0.40)
            Sigils.effective(target, Sigil.RESISTANCE) -> Math.floor(damage * 0.75)
            else -> damage
        }

    /** The hit types that are an attack (not poison, venom, disease, burn, reflected damage or a heal). */
    private val ATTACK_TYPES =
        setOf(HitType.REGULAR_HIT, HitType.MELEE, HitType.RANGE, HitType.MAGIC, HitType.CRIT_MELEE, HitType.CRIT_RANGE, HitType.CRIT_MAGIC)

    /**
     * The damage of a hit that does NOT come through `Pawn.dealHit`: a boss mechanic (fire rain, waves, explosions, a share of the
     * hitpoints ...) dealt with `Pawn.hit(damage, type, source = npc)`. Without this the monster sigils only worked on standard attacks.
     * Titanium / Resistance: "all attacks from monsters" ([source] is an Npc). Aggression: "you take 5% more damage from all sources"
     * ([source] is any pawn). `dealHit` uses the other `hit` overload (no source), so nothing is counted twice.
     */
    fun directHit(
        target: Pawn,
        source: Pawn?,
        damage: Int,
        type: HitType,
    ): Int {
        if (damage <= 0 || source == null || source === target || target !is Player || type !in ATTACK_TYPES || !any(target)) return damage
        if (SHARE_OF_DEALT_HIT.get()) return damage
        var result = damage.toDouble()
        if (source is Npc) result = fromMonster(target, result)
        if (Sigils.effective(target, Sigil.AGGRESSION)) result = Math.round(result * 1.05).toDouble()
        return result.toInt()
    }

    private val SHARE_OF_DEALT_HIT: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    /**
     * Deals a hit whose damage is a SHARE of a hit that already went through `dealHit` (Karil's second hit = half of the first, the
     * Twinflame staff's second spell = 40% of the first). The sigils are already inside that first hit, so [directHit] leaves this one
     * alone - otherwise Aggression's 5% would be counted on top of itself.
     */
    fun <T> shareOfDealtHit(block: () -> T): T {
        val before = SHARE_OF_DEALT_HIT.get()
        SHARE_OF_DEALT_HIT.set(true)
        try {
            return block()
        } finally {
            SHARE_OF_DEALT_HIT.set(before)
        }
    }

    /** After the hit is created: heals, curses and recoil of the attacker's and the target's sigils. */
    fun afterHit(
        attacker: Pawn,
        target: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
        if (attacker is Player && any(attacker)) attackerAfterHit(attacker, target, hit, type, landed)
        if (target is Player && any(target)) targetAfterHit(target, attacker, hit, type, landed)
    }

    private fun attackerAfterHit(
        attacker: Player,
        target: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
        // S56 Finality: the special that just fired used up the window.
        if (attacker.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] == true) Sigils.consumeWindow(attacker, Sigil.FINALITY)
        if (!landed) return
        // S54 Ruthless Ranger / S52 Menacing Mage: damage over time.
        SigilDots.onLanded(attacker, target, type)
        // S27 Restoration: on a successful hit restore 10% of the damage dealt (rounded up) to hitpoints - players and NPCs, not special
        // attacks (multi-target attacks are not told apart from single hits here: see the dossier).
        if (Sigils.effective(attacker, Sigil.RESTORATION) &&
            attacker.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] != true &&
            !multiTargetAttack(attacker, target, type)
        ) {
            hit.addAction {
                val dealt = hitmarks.sumOf { it.damage }
                if (dealt > 0) attacker.heal(Math.ceil(dealt / 10.0).toInt())
            }
        }
    }

    private fun targetAfterHit(
        target: Player,
        attacker: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
        // S53 Porcupine: while the window is open every hit taken recoils 4 damage to the attacker - 2 in the 3-50 and 51-70 brackets.
        if (landed && Sigils.windowOpen(target, Sigil.THE_PORCUPINE) && attacker !== target && attacker.isAlive()) {
            attacker.hit(porcupineRecoil(target), HitType.REFLECTED, source = target)
        }
    }

    /**
     * The wiki's values "on level 3-50 / 51-70 worlds": OSRS runs one world per bracket; here the bracket is the player's own
     * ([gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.of]: by combat level, never below the highest bracket he reached).
     */
    private fun bracketOf(player: Player) = gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.of(player)

    /** S53 Porcupine: "recoil 4 damage ... On level 3-50 and 51-70 worlds: ... recoil 2 damage". */
    fun porcupineRecoil(player: Player): Int =
        if (bracketOf(player).ordinal < gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket.B71_90.ordinal) 2 else 4

    /** S52 Menacing Mage: "The curse deals 18 damage (12 damage in the level 3-50 combat bracket)". */
    fun curseDamage(player: Player): Int = if (bracketOf(player) == gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket.B3_50) 12 else 18

    /** S54 Ruthless Ranger: "The effect deals 15 damage (10 damage in the level 3-50 combat bracket)". */
    fun crippleDamage(player: Player): Int = if (bracketOf(player) == gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket.B3_50) 10 else 15

    private val RAMPAGE_COUNT = gg.rsmod.game.model.attr.AttributeKey<Int>()
    private val RAMPAGE_STYLE = gg.rsmod.game.model.attr.AttributeKey<Style>()
    private val RAMPAGE_CYCLE = gg.rsmod.game.model.attr.AttributeKey<Int>()
    private const val RAMPAGE_RESET_TICKS = 10

    /** Updates and returns the rampage count for an attack of [style] (S57): +1 on a changed style (max 3), reset on the same style or after 6 s idle. */
    private fun rampageCount(
        attacker: Player,
        style: Style,
    ): Int {
        if (!Sigils.effective(attacker, Sigil.RAMPAGE)) return 0
        val now = attacker.world.currentCycle
        // One attack may ask its hit chance more than once (a two-hit special, a second target of a multi-target attack): the same
        // style in the same tick is the same attack and leaves the count as it is.
        if (attacker.attr[RAMPAGE_CYCLE] == now && attacker.attr[RAMPAGE_STYLE] == style) return attacker.attr[RAMPAGE_COUNT] ?: 0
        val last = attacker.attr[RAMPAGE_STYLE]
        val idle = now - (attacker.attr[RAMPAGE_CYCLE] ?: Int.MIN_VALUE / 2) > RAMPAGE_RESET_TICKS
        var count = if (idle) 0 else attacker.attr[RAMPAGE_COUNT] ?: 0
        count = if (last != null && last != style && !idle) minOf(3, count + 1) else if (last == style) 0 else count
        if (count > (attacker.attr[RAMPAGE_COUNT] ?: 0)) SigilFx.rampage(attacker, count)
        attacker.attr[RAMPAGE_STYLE] = style
        attacker.attr[RAMPAGE_COUNT] = count
        attacker.attr[RAMPAGE_CYCLE] = now
        return count
    }

    /** The combat spell of the attack that is being dealt now, if it is a spell. */
    private fun currentSpell(player: Player): gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell? =
        ((player.attr[gg.rsmod.plugins.content.combat.Combat.CASTING_SPELL] as Any?)
            ?: (player.attr[gg.rsmod.plugins.content.combat.Combat.SPELL_OF_THIS_ATTACK] as Any?)) as? gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

    /**
     * S27 Restoration "excludes attacks on multiple targets": the two attacks of this server that hit more than one target are a
     * Burst / Barrage spell and a thrown chinchompa, both only where they really spread - attacker and target in a multi-way area
     * (the same rule as MagicCombatStrategy.collectTargets and the chinchompa splash).
     */
    private fun multiTargetAttack(
        attacker: Player,
        target: Pawn,
        type: HitType,
    ): Boolean {
        val spreads =
            when (styleOf(type)) {
                Style.MAGIC -> currentSpell(attacker)?.multiTarget == true
                Style.RANGED ->
                    gg.rsmod.plugins.content.combat.strategy.ranged.Chinchompas.isChinchompa(attacker.getEquipment(EquipmentType.WEAPON)?.id)
                else -> false
            }
        return spreads && attacker.tile.isMulti(attacker.world) && target.tile.isMulti(attacker.world)
    }

    /** S17 Rigorous Ranger: "a 50% chance of saving ammunition when using Ranged weapons, except when throwing Chinchompas". */
    fun savesAmmo(player: Player): Boolean {
        if (!Sigils.effective(player, Sigil.THE_RIGOROUS_RANGER)) return false
        if (gg.rsmod.plugins.content.combat.strategy.ranged.Chinchompas.isChinchompa(player.getEquipment(EquipmentType.WEAPON)?.id)) return false
        return player.world.randomDouble() < 0.50
    }

    /** S15 Meticulous Mage: "a 50% chance of saving runes or staff charges when using Magic". */
    fun savesRunes(player: Player): Boolean = Sigils.effective(player, Sigil.THE_METICULOUS_MAGE) && player.world.randomDouble() < 0.50

    private fun castingBindingSpell(player: Player): Boolean {
        val spell = currentSpell(player)
        return spell == gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.BIND ||
            spell == gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.SNARE ||
            spell == gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ENTANGLE
    }

    /** S47: the energy a special costs; 10 less (when it costs more than 10) while the Specialised Strikes window is open. */
    fun specialCost(
        player: Player,
        cost: Int,
    ): Int = if (cost > 10 && Sigils.windowOpen(player, Sigil.SPECIALISED_STRIKES)) cost - 10 else cost

    /** The window of a one-shot sigil was used up by the special attack that just fired. */
    fun windowUsed(player: Player) {
        Sigils.consumeWindow(player, Sigil.SPECIALISED_STRIKES)
    }

    private val BARROWS_WEAPONS = listOf("dharok's greataxe", "verac's flail", "torag's hammers", "guthan's warspear", "karil's crossbow", "ahrim's staff")

    private fun wieldsBarrowsWeapon(player: Player): Boolean {
        val id = player.getEquipment(EquipmentType.WEAPON)?.id ?: return false
        val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name.lowercase()
        return BARROWS_WEAPONS.any { name.startsWith(it) }
    }

    /** Weapons that the Gunslinger does not speed up (wiki: the Crystal bow, Bow of Faerdhinen and Morrigan's throwing axe). */
    private fun gunslingerExcluded(
        pawn: Player,
        weaponId: Int?,
    ): Boolean {
        if (weaponId == null) return false
        val name = pawn.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, weaponId).name.lowercase()
        return name.contains("crystal bow") || name.contains("faerdhinen") || name.contains("morrigan")
    }

    /** S35 Swashbuckler (melee) and S30 Gunslinger (ranged): one tick faster for a base attack speed above 4. */
    fun baseSpeed(
        pawn: Pawn,
        weaponId: Int?,
        speed: Int,
        combatClass: gg.rsmod.game.model.combat.CombatClass?,
    ): Int {
        if (pawn !is Player || speed <= 4 || !any(pawn)) return speed
        return when (combatClass) {
            gg.rsmod.game.model.combat.CombatClass.MELEE -> if (Sigils.effective(pawn, Sigil.THE_SWASHBUCKLER)) speed - 1 else speed
            gg.rsmod.game.model.combat.CombatClass.RANGED ->
                if (Sigils.effective(pawn, Sigil.THE_GUNSLINGER) && !gunslingerExcluded(pawn, weaponId)) speed - 1 else speed
            else -> speed
        }
    }

    /** S19 Arcane Swiftness: one tick faster for a magic weapon with a base attack speed above 4. */
    fun magicSpeed(
        pawn: Pawn,
        speed: Int,
    ): Int = if (pawn is Player && speed > 4 && Sigils.effective(pawn, Sigil.ARCANE_SWIFTNESS)) speed - 1 else speed

    /** S34 Lightbearer sigil: ticks between two 10% special-energy restores (25 = 15 s, 17 = 10 s with the ring), or null without the sigil. */
    fun specRegenTicks(
        player: Player,
        wearingRing: Boolean,
    ): Int? = if (Sigils.effective(player, Sigil.THE_LIGHTBEARER)) (if (wearingRing) 17 else 25) else null

    /** True when [pawn] is an npc (sigils such as Deft Strikes only work against non-player enemies). */
    fun isNpc(pawn: Pawn): Boolean = pawn is Npc
}
