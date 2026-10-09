package gg.rsmod.plugins.api

import gg.rsmod.game.model.HitmarkGroup

/**
 * The server's own hit types. [id] is the internal type every combat rule, trace and conformance suite reads; the client
 * draws the OSRS hitsplat [group] (owner 2026-10-09: OSRS hitsplats, lit / tint / max per viewer - see [HitmarkGroup]).
 * The revision-667 combat-style icons, critical and soak splats are gone: every damage type shares OSRS regular damage.
 *
 * @author Tom <rspsmods@gmail.com>
 */
enum class HitType(
    val id: Int,
    val group: HitmarkGroup = HitmarkGroup.REGULAR_DAMAGE,
) {
    BLOCK(id = 8),
    REGULAR_HIT(id = 3),
    MELEE(id = 0),
    RANGE(id = 1),
    MAGIC(id = 2),

    /** Ring of recoil, vengeance and other reflected damage: untinted for every viewer since 2020 (OSRS Wiki "Hitsplat"). */
    REFLECTED(id = 4, group = HitmarkGroup.UNTINTED_DAMAGE),
    CANNON(id = 13),
    ABSORB(id = 5),
    POISON(id = 6, group = HitmarkGroup.POISON),
    DISEASE(id = 7, group = HitmarkGroup.DISEASE),
    HEAL(id = 9, group = HitmarkGroup.HEAL),
    CRIT_MELEE(id = 10),
    CRIT_RANGE(id = 11),
    CRIT_MAGIC(id = 12),
    VENOM(id = 14, group = HitmarkGroup.VENOM),
    BURN(id = 28, group = HitmarkGroup.BURN),
    ;

    companion object {
        private val byId = values().associateBy { it.id }

        /** The OSRS hitsplat group of internal hit type [id] (regular damage for an unknown id). */
        fun groupOf(id: Int): HitmarkGroup = byId[id]?.group ?: HitmarkGroup.REGULAR_DAMAGE
    }
}
