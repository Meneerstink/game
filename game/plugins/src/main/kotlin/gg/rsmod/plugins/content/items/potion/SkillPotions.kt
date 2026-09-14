package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.restorePrayer

/**
 * OSRS-IMPORT step 4 potions-skill (OSRS Wiki raw wikitext 2026-09-14):
 * - Super fishing / Super hunter potion: "provides a +6 boost to Fishing / Hunter".
 * - Hunter's mixes (two doses, "share a 3 tick (1.8 second) consumption cooldown with potions"): Ruby harvest mix Attack, Sapphire glacialis
 *   mix Defence, Black warlock mix Strength "by 4 + 15% of their ... level, rounded down"; Snowy knight mix "restore 8 Hitpoints per dose";
 *   Moonlight moth mix "restore 22 Prayer points per dose" (holy wrench does not apply); Sunlight moth mix "restore 6 + 20% of their reduced
 *   stats, as well as 8 Hitpoints, but not including Prayer".
 * - Moonlight / Sunlight moth items: "Release" gives the same effect as the mix and "The empty butterfly jar will remain in the inventory".
 * - Haemostatic dressing: "cures bleed when applied, healing 5 Hitpoints if bleeding was stopped"; "can be consumed even if the player is not
 *   bleeding" (this server has no OSRS bleed status, so applying it has no effect).
 * DERIVED: a finished mix leaves a vial (wiki Vial: "Upon finishing a potion, the empty vial is left"; the mixes carry Drink / Empty like
 * potions). SOURCE_GAP: the restore basis of "6 + 20%" (base level used, as for OSRS restore potions); the container left by the last
 * dressing dose (none); release / apply messages (none sent). BLOCKED: the multicombat sharing with players who have Accept Aid on (varp 427
 * on/off value not established from the cache); every recipe - raw hunter meats, pillar / elkhorn corals, haddock eye, crab / squid paste
 * and cotton yarn are absent in 667; Bottled dragonbreath (Mount Karuulm vent absent, and this server keeps no dragonfire shield charges).
 */
object SkillPotions {
    const val SUPER_SKILL_BOOST = 6
    const val MIX_HEAL = 8
    const val MOONLIGHT_PRAYER = 22

    fun hunterMixBoost(base: Int): Int = base * 15 / 100 + 4

    fun sunlightRestore(base: Int): Int = base * 20 / 100 + 6

    fun superSkill(
        p: Player,
        skill: Int,
    ) {
        p.skills.alterCurrentLevel(skill, SUPER_SKILL_BOOST, SUPER_SKILL_BOOST)
    }

    fun hunterBoost(
        p: Player,
        skill: Int,
    ) {
        val boost = hunterMixBoost(p.skills.getMaxLevel(skill))
        p.skills.alterCurrentLevel(skill, boost, boost)
    }

    fun moonlight(p: Player) {
        p.restorePrayer(MOONLIGHT_PRAYER)
    }

    fun sunlight(p: Player) {
        for (skill in 0 until p.skills.maxSkills) {
            if (skill == Skills.PRAYER || skill == Skills.CONSTITUTION) continue
            val base = p.skills.getMaxLevel(skill)
            val current = p.skills.getCurrentLevel(skill)
            if (current < base) p.skills.setCurrentLevel(skill, minOf(base, current + sunlightRestore(base)))
        }
        p.heal(MIX_HEAL)
    }
}
