package gg.rsmod.plugins.content.items.armor

import gg.rsmod.plugins.api.cfg.Items

/**
 * Barrows (and Akrisae's) equipment degradation.
 *
 * 2011 RuneScape Wiki, "Barrows equipment": a piece lasts 15 hours of combat before it fully
 * degrades, in four visible steps (100 -> 75 -> 50 -> 25 -> 0). Only time spent attacking or
 * being attacked counts, so every stage is 3 hours 45 minutes of combat = 22,500 game cycles.
 * The pristine item (no suffix) becomes the "100" item on its first combat cycle, exactly like
 * the existing corrupt/PvP armour model in [CorruptArmorCharges]; the remaining cycles of the
 * current stage live in [ItemAttribute.CHARGES] on the item, so they survive banking, death
 * and logout with the item.
 *
 * Every id is derived from the production cache constants: each Barrows piece has its base id
 * followed by five consecutive degraded ids (100/75/50/25/0), see [BarrowsPiece].
 */
object BarrowsDegradation {
    /** 15 hours / 4 stages = 3h45m of combat per stage, in 600ms game cycles. Ticked by [EquipmentDegradation]. */
    const val STAGE_CYCLES = 22_500
}

/**
 * One Barrows piece and its six ids: base, then 100/75/50/25/0 (consecutive in the cache).
 * Repair costs are the 2011 wiki Bob prices for a fully degraded piece (helm 60k, body 90k,
 * legs 80k, weapon 100k), scaled by the missing stages.
 */
enum class BarrowsPiece(
    val baseId: Int,
    val firstDegradedId: Int,
    val displayName: String,
    val fullRepairCost: Int,
) {
    AHRIMS_HOOD(Items.AHRIMS_HOOD, Items.AHRIMS_HOOD_100, "Ahrim's hood", 60_000),
    AHRIMS_STAFF(Items.AHRIMS_STAFF, Items.AHRIMS_STAFF_100, "Ahrim's staff", 100_000),
    AHRIMS_ROBE_TOP(Items.AHRIMS_ROBE_TOP, Items.AHRIMS_ROBE_TOP_100, "Ahrim's robe top", 90_000),
    AHRIMS_ROBE_SKIRT(Items.AHRIMS_ROBE_SKIRT, Items.AHRIMS_ROBE_SKIRT_100, "Ahrim's robe skirt", 80_000),
    DHAROKS_HELM(Items.DHAROKS_HELM, Items.DHAROKS_HELM_100, "Dharok's helm", 60_000),
    DHAROKS_GREATAXE(Items.DHAROKS_GREATAXE, Items.DHAROKS_GREATAXE_100, "Dharok's greataxe", 100_000),
    DHAROKS_PLATEBODY(Items.DHAROKS_PLATEBODY, Items.DHAROKS_PLATEBODY_100, "Dharok's platebody", 90_000),
    DHAROKS_PLATELEGS(Items.DHAROKS_PLATELEGS, Items.DHAROKS_PLATELEGS_100, "Dharok's platelegs", 80_000),
    GUTHANS_HELM(Items.GUTHANS_HELM, Items.GUTHANS_HELM_100, "Guthan's helm", 60_000),
    GUTHANS_WARSPEAR(Items.GUTHANS_WARSPEAR, Items.GUTHANS_WARSPEAR_100, "Guthan's warspear", 100_000),
    GUTHANS_PLATEBODY(Items.GUTHANS_PLATEBODY, Items.GUTHANS_PLATEBODY_100, "Guthan's platebody", 90_000),
    GUTHANS_CHAINSKIRT(Items.GUTHANS_CHAINSKIRT, Items.GUTHANS_CHAINSKIRT_100, "Guthan's chainskirt", 80_000),
    KARILS_COIF(Items.KARILS_COIF, Items.KARILS_COIF_100, "Karil's coif", 60_000),
    KARILS_CROSSBOW(Items.KARILS_CROSSBOW, Items.KARILS_CROSSBOW_100, "Karil's crossbow", 100_000),
    KARILS_TOP(Items.KARILS_TOP, Items.KARILS_TOP_100, "Karil's leathertop", 90_000),
    KARILS_SKIRT(Items.KARILS_SKIRT, Items.KARILS_SKIRT_100, "Karil's leatherskirt", 80_000),
    TORAGS_HELM(Items.TORAGS_HELM, Items.TORAGS_HELM_100, "Torag's helm", 60_000),
    TORAGS_HAMMERS(Items.TORAGS_HAMMERS, Items.TORAGS_HAMMERS_100, "Torag's hammers", 100_000),
    TORAGS_PLATEBODY(Items.TORAGS_PLATEBODY, Items.TORAGS_PLATEBODY_100, "Torag's platebody", 90_000),
    TORAGS_PLATELEGS(Items.TORAGS_PLATELEGS, Items.TORAGS_PLATELEGS_100, "Torag's platelegs", 80_000),
    VERACS_HELM(Items.VERACS_HELM, Items.VERACS_HELM_100, "Verac's helm", 60_000),
    VERACS_FLAIL(Items.VERACS_FLAIL, Items.VERACS_FLAIL_100, "Verac's flail", 100_000),
    VERACS_BRASSARD(Items.VERACS_BRASSARD, Items.VERACS_BRASSARD_100, "Verac's brassard", 90_000),
    VERACS_PLATESKIRT(Items.VERACS_PLATESKIRT, Items.VERACS_PLATESKIRT_100, "Verac's plateskirt", 80_000),
    AKRISAES_HOOD(Items.AKRISAES_HOOD, Items.AKRISAES_HOOD_100, "Akrisae's hood", 60_000),
    AKRISAES_WAR_MACE(Items.AKRISAES_WAR_MACE, Items.AKRISAES_WAR_MACE_100, "Akrisae's war mace", 100_000),
    AKRISAES_ROBE_TOP(Items.AKRISAES_ROBE_TOP, Items.AKRISAES_ROBE_TOP_100, "Akrisae's robe top", 90_000),
    AKRISAES_ROBE_SKIRT(Items.AKRISAES_ROBE_SKIRT, Items.AKRISAES_ROBE_SKIRT_100, "Akrisae's robe skirt", 80_000),
    ;

    /** All six ids in order: pristine, 100, 75, 50, 25, 0. */
    val ids: IntArray = intArrayOf(baseId, firstDegradedId, firstDegradedId + 1, firstDegradedId + 2, firstDegradedId + 3, firstDegradedId + 4)

    fun stageOf(id: Int): Int = ids.indexOf(id)

    fun idForStage(stage: Int): Int = ids[stage]

    /** Cost to restore [id] to pristine: one quarter of the full price per missing stage. */
    fun repairCost(id: Int): Int {
        val stage = stageOf(id)
        if (stage <= FULL) return 0
        return fullRepairCost * (stage - FULL) / 4
    }

    companion object {
        const val PRISTINE = 0
        const val FULL = 1
        const val BROKEN = 5

        val values = enumValues<BarrowsPiece>()
        private val byId: Map<Int, BarrowsPiece> = values.flatMap { piece -> piece.ids.map { it to piece } }.toMap()

        fun forId(id: Int): BarrowsPiece? = byId[id]
    }
}
