package gg.rsmod.plugins.api

/**
 * Head skull ids sent in the appearance block.
 *
 * Owner 2026-09-26 (exactly like OSRS Deadman: Annihilation): every player shows a risk-coloured skull
 * ([DMM_VERY_HIGH_RISK]..[DMM_VERY_LOW_RISK], OSRS headicons_pk frames 25..21, dark eyes); a really skulled player shows
 * the YELLOW-EYED variant of the same tier ([DMM_VERY_HIGH_RISK_SKULLED]..[DMM_VERY_LOW_RISK_SKULLED], frames 32..28). A
 * yellow-eyed id is its tier id plus [SKULLED_FLAG]; the appearance block sends that flag as bit 7 of the pk-icon byte and
 * the client's PlayerEntity/DeadmanIcons decode it. Whether a player IS skulled is never read from this icon - that is
 * always [gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled].
 *
 * @author Tom <rspsmods@gmail.com>
 */
enum class SkullIcon(
    val id: Int,
) {
    NONE(id = -1),
    WHITE(id = 0),
    RED(id = 1),
    VERY_HIGH_RISK(id = 2),
    HIGH_RISK(id = 3),
    MEDIUM_RISK(id = 4),
    LOW_RISK(id = 5),
    NO_BOUNTY(id = 6),
    VERY_LOW_RISK(id = 7),
    DMM_VERY_HIGH_RISK(id = 8),
    DMM_HIGH_RISK(id = 9),
    DMM_MEDIUM_RISK(id = 10),
    DMM_LOW_RISK(id = 11),
    DMM_VERY_LOW_RISK(id = 12),
    DMM_VERY_HIGH_RISK_SKULLED(id = 24),
    DMM_HIGH_RISK_SKULLED(id = 25),
    DMM_MEDIUM_RISK_SKULLED(id = 26),
    DMM_LOW_RISK_SKULLED(id = 27),
    DMM_VERY_LOW_RISK_SKULLED(id = 28),
    ;

    /** The yellow-eyed variant of a Deadman tier (itself for anything else). */
    fun skulled(): SkullIcon = if (id in 8..12) forId(id + SKULLED_FLAG) ?: this else this

    /** The dark-eyed tier of a yellow-eyed icon (itself for anything else). */
    fun tier(): SkullIcon = if (id in 24..28) forId(id - SKULLED_FLAG) ?: this else this

    companion object {
        const val SKULLED_FLAG = 16

        fun forId(id: Int): SkullIcon? = values().firstOrNull { it.id == id }
    }
}