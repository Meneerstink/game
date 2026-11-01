package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.plugins.content.skills.farming.constants.CompostState
import gg.rsmod.plugins.content.skills.farming.logic.PatchState

/**
 * N72 deel C (ChatGPT-B rapport N72-DEELC-ONDERZOEK.txt: "Cure Plant / Fertile Soil: SERVER stub; object-target ontbreekt"):
 * the two Lunar spells cast ON a farming patch (430:55 Cure Plant, 430:24 Fertile Soil - spell-on-object, Darkan LunarSpellbook
 * onButtonClick-on-object 24/55). Until now they answered "This spell is not available yet." This object decides, per patch
 * state, the refusal or the effect; lunar_spells.plugin.kts pays the runes and plays the cast.
 *
 * Values (OSRS Wiki, read 2026-11-01): Cure Plant 60 Magic + 91.5 Farming XP, only a diseased patch, messages "It's growing just
 * fine." / "That's not diseased." (fully grown) / "It says 'Cure' not 'Resurrect'..." (dead) / "The weeds are healthy enough
 * already." / "There's nothing there to cure." / "Umm... this spell won't cure that!" / bin "Bins don't often get diseased.".
 * Fertile Soil 87 Magic + 18 Farming XP, supercompost on a patch "before or after planting, at any growth stage", messages
 * "Composting it isn't going to make it any bigger." (fully grown) / "No, that would be silly." (bin) / "Um... I don't want to
 * fertilize that!". Ultracompost (ash covered tome) does not exist here. Look: Void CurePlant/FertileSoil (anim 4432 + gfx 748
 * on the caster / anim 4413 + gfx 724 on the patch, sound 2891 lunar_fertilize).
 */
object LunarFarming {
    const val CURE_PLANT_MAGIC_XP = 60.0
    const val CURE_PLANT_FARMING_XP = 91.5
    const val FERTILE_SOIL_MAGIC_XP = 87.0
    const val FERTILE_SOIL_FARMING_XP = 18.0

    const val CURE_ANIM = 4432
    const val CURE_GFX = 748
    const val FERTILE_ANIM = 4413
    const val FERTILE_GFX = 724
    const val SOUND = 2891

    /** What the spells need to know about a patch (a [PatchState] read once; plain values so the rules are testable). */
    data class View(
        val planted: Boolean,
        val weedy: Boolean,
        val dead: Boolean,
        val diseased: Boolean,
        val plantFullyGrown: Boolean,
        val compost: CompostState,
        val patchName: String,
    ) {
        companion object {
            fun of(state: PatchState) =
                View(
                    planted = state.seed != null,
                    weedy = state.isWeedy,
                    dead = state.isDead,
                    diseased = state.isDiseased,
                    plantFullyGrown = state.isPlantFullyGrown,
                    compost = state.compostState,
                    patchName = state.patch.patchName,
                )
        }
    }

    const val CURE_NOT_A_PATCH = "Umm... this spell won't cure that!"
    const val CURE_BIN = "Bins don't often get diseased."
    const val CURE_DEAD =
        "It says 'Cure' not 'Resurrect'. Although death may arise from disease, it is not in itself a disease and hence cannot be cured. So there."
    const val FERTILE_NOT_A_PATCH = "Um... I don't want to fertilize that!"
    const val FERTILE_BIN = "No, that would be silly."
    const val FERTILE_FULLY_GROWN = "Composting it isn't going to make it any bigger."

    /** Why Cure Plant cannot be cast on [patch] (null = it can); [bin] = the target is a compost bin. */
    fun cureRefusal(patch: View?, bin: Boolean = false): String? =
        when {
            bin -> CURE_BIN
            patch == null -> CURE_NOT_A_PATCH
            patch.dead -> CURE_DEAD
            patch.diseased -> null
            !patch.planted && patch.weedy -> "The weeds are healthy enough already."
            !patch.planted -> "There's nothing there to cure."
            patch.plantFullyGrown -> "That's not diseased."
            else -> "It's growing just fine."
        }

    /** Why Fertile Soil cannot be cast on [patch] (null = it can). */
    fun fertileRefusal(patch: View?, bin: Boolean = false): String? =
        when {
            bin -> FERTILE_BIN
            patch == null -> FERTILE_NOT_A_PATCH
            !patch.planted && patch.weedy -> "This patch needs weeding first."
            patch.dead -> "This patch needs to be cleared first."
            patch.plantFullyGrown -> FERTILE_FULLY_GROWN
            patch.compost != CompostState.None ->
                "This ${patch.patchName} has already been treated with ${patch.compost.displayName}."
            else -> null
        }

    /** The patch gets supercompost; a crop already in it gains the harvest lives supercompost gives at planting. */
    fun fertilise(state: PatchState) {
        val seed = state.seed
        state.compost(CompostState.SuperCompost)
        if (seed != null && !seed.seedType.harvest.fixedLives && !seed.seedType.harvest.livesReplenish) {
            state.setLives(state.livesLeft + CompostState.SuperCompost.lives)
        }
    }

    private val CompostState.displayName get() = if (this == CompostState.SuperCompost) "supercompost" else "compost"
}
