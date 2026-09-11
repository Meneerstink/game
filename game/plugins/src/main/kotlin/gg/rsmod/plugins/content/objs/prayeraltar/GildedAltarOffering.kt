package gg.rsmod.plugins.content.objs.prayeraltar

import gg.rsmod.plugins.content.skills.prayer.burying.BoneData

/**
 * Gilded altar (POH) bone offering. Sourced from Novite's rev-667
 * `player/actions/prayer/AltarAction.java`: `bone.getExperience() * 3` - three times the same
 * bone's normal bury XP (see [BoneData], already used by this project's bone-burying feature).
 * Object id 13199 is cross-verified: Novite hardcodes it and this target's own generated
 * `Objs.ALTAR_13199` resolves to the same numeric id, a same-revision match rather than a guess.
 */
object GildedAltarOffering {
    const val XP_MULTIPLIER = 3.0

    /** Prayer XP granted for offering [bone], or null if [bone] is not a bone this altar accepts. */
    fun xpFor(bone: Int): Double? = BoneData.boneDefinitions[bone]?.let { it.experience * XP_MULTIPLIER }
}
