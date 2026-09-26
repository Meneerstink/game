package gg.rsmod.plugins.content.items.helios

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.api.cfg.Items

/**
 * State and helpers behind the staff "crown" menu (`crown_of_helios.plugin.kts`). Owner 2026-09-26: the Crown of Helios
 * item (22327) itself is removed from the game - no combat override, no death protection; any copy is purged at login
 * ([gg.rsmod.plugins.content.mechanics.removed.RemovedItems]). Only the menu remains.
 */
object CrownOfHelios {
    /** The removed item, kept only so [gg.rsmod.plugins.content.mechanics.removed.RemovedItems] can purge old copies. */
    const val REMOVED_ITEM = Items.CROWN_OF_HELIOS

    /** Which max starter set the menu hands out. */
    enum class Mode(
        val label: String,
        val combatClass: CombatClass,
    ) {
        MELEE("Melee", CombatClass.MELEE),
        RANGED("Ranged", CombatClass.RANGED),
        MAGIC("Magic", CombatClass.MAGIC),
    }

    /** A named tile the crown's Teleport menu can jump back to (a favorite or a recent trip). */
    data class SavedLocation(val label: String, val tile: Tile)

    /** Admin-saved teleport shortcuts (Teleport > Favorites), kept for the session. */
    val FAVORITES_ATTR = AttributeKey<MutableList<SavedLocation>>()

    /** Auto-tracked last few Crown teleports (Teleport > Recent), newest first. */
    val RECENT_ATTR = AttributeKey<MutableList<SavedLocation>>()

    /** Ring buffer of the last Crown dev-tool actions (AV Tester plays, spawns, etc). */
    val LAST_ACTIONS_ATTR = AttributeKey<MutableList<String>>()

    /**
     * Player-state snapshot (Dev Tools > Snapshot/Restore) - deliberately position/HP/run/skill
     * levels only, never inventory or equipment: a bulk raw-slot restore of those risks an item
     * duplication bug, which this dev tool must never be able to cause.
     */
    data class Snapshot(
        val tile: Tile,
        val hp: Int,
        val runEnergy: Double,
        val levels: IntArray,
    )

    val SNAPSHOT_ATTR = AttributeKey<Snapshot>()

    fun isAdmin(player: Player): Boolean = player.world.privileges.isEligible(player.privilege, Privilege.ADMIN_POWER)

    fun isStaff(player: Player): Boolean = player.world.privileges.isEligible(player.privilege, Privilege.MOD_POWER)

    fun canUseCrown(player: Player): Boolean = isAdmin(player) || isStaff(player)

    /** Cache-backed max-style starter sets used by the admin Crown combat shortcuts. */
    fun maxSet(mode: Mode): IntArray =
        when (mode) {
            Mode.MELEE ->
                intArrayOf(
                    Items.BANDOS_CHESTPLATE,
                    Items.BANDOS_TASSETS,
                    Items.ABYSSAL_WHIP,
                    Items.DRAGON_DEFENDER,
                    Items.FIRE_CAPE,
                    Items.AMULET_OF_FURY,
                    Items.PRIMORDIAL_BOOTS,
                    Items.FEROCIOUS_GLOVES,
                    Items.BERSERKER_RING,
                )
            Mode.RANGED ->
                intArrayOf(
                    Items.ARMADYL_HELMET,
                    Items.ARMADYL_CHESTPLATE,
                    Items.ARMADYL_CHAINSKIRT,
                    Items.ZARYTE_BOW,
                    Items.FIRE_CAPE,
                    Items.AMULET_OF_FURY,
                    Items.RANGER_BOOTS,
                )
            Mode.MAGIC ->
                intArrayOf(
                    Items.ANCESTRAL_HAT,
                    Items.ANCESTRAL_ROBE_TOP,
                    Items.ANCESTRAL_ROBE_BOTTOM,
                    Items.STAFF_OF_LIGHT,
                    Items.MAGES_BOOK,
                    Items.FIRE_CAPE,
                    Items.OCCULT_NECKLACE,
                )
        }
}

