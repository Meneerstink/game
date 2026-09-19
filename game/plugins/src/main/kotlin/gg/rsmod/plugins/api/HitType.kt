package gg.rsmod.plugins.api

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class HitType(
    val id: Int,
) {
    BLOCK(id = 8),
    REGULAR_HIT(id = 3),
    MELEE(id = 0),
    RANGE(id = 1),
    MAGIC(id = 2),
    REFLECTED(id = 4),
    CANNON(id = 13),
    ABSORB(id = 5),
    POISON(id = 6),
    DISEASE(id = 7),
    HEAL(id = 9),
    CRIT_MELEE(id = 10),
    CRIT_RANGE(id = 11),
    CRIT_MAGIC(id = 12),

    /** Owner 2026-09-18 (OSRS venom): the OSRS venom splat - no cache entry; the client draws OSRS sprite 1632 on the poison type (VenomHitmarkType). */
    VENOM(id = 14),

    /** Owner 2026-09-19 (OSRS burn): the OSRS burn splat (OSRS hitsplat 74, sprite 4767) on the regular damage type; past the cache's 0-27. */
    BURN(id = 28),
}
