package gg.rsmod.game.model

/**
 * Weapon item id -> body-animation set (BASType id) that replaces the item's cache param 644 in the player appearance block.
 *
 * Used for imported OSRS weapons whose stand / walk / run sequences are their own (`OsrsBasImportTool`); content plugins
 * register them at load, the appearance block reads them.
 */
object RenderAnimations {
    private val byWeapon = HashMap<Int, Int>()

    fun register(
        basId: Int,
        vararg weapons: Int,
    ) = weapons.forEach { byWeapon[it] = basId }

    fun forWeapon(itemId: Int): Int? = byWeapon[itemId]
}
