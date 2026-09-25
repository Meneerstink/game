package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx

/**
 * Melee swing sounds of the imported OSRS weapons that have neither sound frames in their attack sequence nor an
 * `attack_audio` of their own (owner 2026-09-25: "some osrs ported weapons have no sound").
 *
 * OSRS sends a weapon's swing sound from the server per attack type. Source: the xrsps OSRS server weapon table
 * (`server/gamemodes/vanilla/data/weapons.ts`: `getDefaultHitSounds` per combat category, per-weapon `hitSounds`, and
 * `getHitSound`'s fallback order when a category has no sound for the attack type). The weapon's category is its OSRS Wiki
 * combat category. Every id is below the ~3800 point where the OSRS and 667 sound tables diverge, and each carries the same
 * Jagex name in this cache's `Sfx` table (hacksword_*, baxe_*, stabsword_*, staff_*, warhammer_crush).
 */
object OsrsWeaponSounds {
    class Category(
        val stab: Int? = null,
        val slash: Int? = null,
        val crush: Int? = null,
    ) {
        /** xrsps `getHitSound` fallback order. */
        fun of(style: StyleType): Int? =
            when (style) {
                StyleType.STAB -> stab ?: slash ?: crush
                StyleType.CRUSH -> crush ?: slash ?: stab
                else -> slash ?: stab ?: crush
            }
    }

    val SWORD = Category(stab = Sfx.HACKSWORD_STAB, slash = Sfx.HACKSWORD_SLASH, crush = Sfx.HACKSWORD_CRUSH)
    val AXE = Category(slash = Sfx.BAXE_SLASH, crush = Sfx.BAXE_CRUSH)
    val PICKAXE = Category(stab = Sfx.HACKSWORD_STAB, crush = Sfx.HACKSWORD_CRUSH)
    val SPEAR = Category(stab = Sfx.STABSWORD_STAB, slash = Sfx.STABSWORD_SLASH, crush = Sfx.STABSWORD_CRUSH)
    val STAFF = Category(stab = Sfx.STAFF_STAB, crush = Sfx.STAFF_CRUSH)
    val BLUNT = Category(crush = Sfx.WARHAMMER_CRUSH)
    val CLAW = Category(stab = Sfx.HACKSWORD_STAB, slash = Sfx.HACKSWORD_SLASH)

    private val byWeapon = mutableMapOf<Int, Category>()

    private fun register(
        category: Category,
        vararg items: Int,
    ) = items.forEach { byWeapon[it] = category }

    init {
        // OSRS Wiki "Slash sword" category.
        register(SWORD, Items.ARCLIGHT, Items.ARCLIGHT_INACTIVE, Items.EMBERLIGHT)
        register(AXE, Items.THIRDAGE_AXE, Items.GILDED_AXE)
        register(PICKAXE, Items.THIRDAGE_PICKAXE, Items.GILDED_PICKAXE, Items.DRAGON_PICKAXE_OR, Items.DRAGON_PICKAXE_OR_UPGRADED)
        register(SPEAR, Items.DRAGON_HUNTER_LANCE, Items.GILDED_SPEAR, Items.GILDED_HASTA)
        // Bladed staves (xrsps: "Bladed staff" -> staff sounds), staves and wands (xrsps: Kodai / 3rd age wand -> staff sounds). The
        // powered staves only melee here because this server gives them staff styles; their bash uses the staff sounds (ADAPTED).
        register(
            STAFF,
            Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD, Items.STAFF_OF_BALANCE,
            Items.KODAI_WAND, Items.THIRDAGE_WAND, Items.DRAGON_HUNTER_WAND,
            Items.TRIDENT_OF_THE_SEAS, Items.TRIDENT_OF_THE_SEAS_FULL, Items.UNCHARGED_TRIDENT, Items.TRIDENT_OF_THE_SWAMP,
            Items.UNCHARGED_TOXIC_TRIDENT, Items.TRIDENT_OF_THE_SEAS_E, Items.UNCHARGED_TRIDENT_E, Items.TRIDENT_OF_THE_SWAMP_E,
            Items.UNCHARGED_TOXIC_TRIDENT_E, Items.SANGUINESTI_STAFF, Items.SANGUINESTI_STAFF_UNCHARGED,
        )
        // OSRS Wiki "Blunt" category (xrsps generated header "Blunt" -> warhammer_crush).
        register(BLUNT, Items.GILDED_SPADE, Items.DRAGON_CANE)
        register(CLAW, Items.BURNING_CLAWS)
    }

    /** Every weapon this table gives a swing sound (guard: `OsrsWeaponSoundsTests`). */
    val COVERED: Set<Int> get() = byWeapon.keys

    /** The swing sound of [weaponId] for an attack of [style], or null when the table does not cover the weapon. */
    fun melee(
        weaponId: Int,
        style: StyleType,
    ): Int? = byWeapon[weaponId]?.of(style)
}
