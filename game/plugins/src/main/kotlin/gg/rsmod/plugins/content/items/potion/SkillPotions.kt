package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.mechanics.aid.AcceptAid
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.isMulti
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
 * dressing dose (none); release / apply messages (none sent); a message to the players a mix or moth is shared with (none sent).
 * Sharing (Accept Aid = varp 427 value 1, clientscript 1269): Hunter's mix - "in multicombat areas they apply the effect of the mix to the
 * drinker and up to three players adjacent to them (who have Accept Aid toggled on)"; Moonlight / Sunlight moth - "In a multi-combat zone,
 * a jarred moonlight moth may be used on another player to share the effect with them and up to three players within a 5x5 area of the
 * target. Players must have Accept Aid toggled on" ([shareMix], [mothRecipients]). BLOCKED: every recipe - raw hunter meats, pillar / elkhorn corals, haddock eye, crab / squid paste
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

    /** The hunter's mixes ([shareMix]). */
    val HUNTER_MIXES =
        setOf(
            PotionType.RUBY_HARVEST_MIX, PotionType.SAPPHIRE_GLACIALIS_MIX, PotionType.BLACK_WARLOCK_MIX, PotionType.SNOWY_KNIGHT_MIX,
            PotionType.MOONLIGHT_MOTH_MIX, PotionType.SUNLIGHT_MOTH_MIX,
        )
    const val MIX_SHARE_LIMIT = 3
    const val MOTH_SHARE_EXTRA = 3
    const val MOTH_SHARE_RADIUS = 2

    /** A hunter's mix drunk in multicombat also reaches up to three adjacent players with Accept Aid on. */
    fun shareMix(
        drinker: Player,
        type: PotionType,
    ) {
        if (type !in HUNTER_MIXES || !drinker.tile.isMulti(drinker.world)) return
        AcceptAid.nearbyAccepting(drinker.world, drinker.tile, radius = 1, exclude = drinker)
            .take(MIX_SHARE_LIMIT)
            .forEach { type.apply(it) }
    }

    /**
     * Who a jarred moth used on [target] reaches: nobody outside multicombat or when [target] refuses aid; else [target] and up to three
     * more Accept Aid players within the 5x5 around [target] ([user] excluded).
     */
    fun mothRecipients(
        user: Player,
        target: Player,
    ): List<Player> {
        if (!target.tile.isMulti(target.world) || !AcceptAid.accepts(target)) return emptyList()
        val others =
            AcceptAid.nearbyAccepting(target.world, target.tile, MOTH_SHARE_RADIUS, exclude = target)
                .filter { it !== user }
                .take(MOTH_SHARE_EXTRA)
        return listOf(target) + others
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
