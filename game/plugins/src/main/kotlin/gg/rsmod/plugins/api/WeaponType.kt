package gg.rsmod.plugins.api

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class WeaponType(
    val id: Int,
) {
    NONE(id = 0),
    STAFF(id = 1),
    AXE(id = 2),
    SCEPTRE(id = 3),
    PICKAXE(id = 4),
    DAGGER(id = 5),
    LONG_SWORD(id = 6),
    TWO_HANDED(id = 7),
    MACE(id = 8),
    CLAWS(id = 9),
    HAMMER(id = 10),
    WHIP(id = 11),
    HAMMER_EXTRA(id = 12),
    THROWN_EXTRA(id = 13),
    SPEAR(id = 14),
    HALBERD(id = 15),
    BOW(id = 16),
    CROSSBOW(id = 17),
    THROWN(id = 18),
    CHINCHOMPA(id = 19),
    FIXED_DEVICE(id = 20),
    SALAMANDER(id = 21),
    SCYTHE(id = 22),
    FLAIL(id = 23),
    SLING(id = 24),

    /**
     * Client style set 26 (item param 686): Jab / Swipe / Fend - the 667 Staff of light, and the imported OSRS bladed staves cloned
     * from it (Staff of the dead, Toxic staff of the dead, Staff of Balance). Without this entry the server read them as unarmed.
     */
    BLADED_STAFF(id = 26),
}
