package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.ownership.Possessions
import mu.KLogging

/**
 * N446 / V13: OSRS Wiki "Locator orb", "Inert locator orb" and Dragon Slayer II's Morytania key route.
 * The owner removes the quest gate, retaining the craft -> Ava -> free lost replacement route.
 * Import contract and source limitations: docs/bugs/N446-locator-orb.md. No death/Perdu exception: K01 quest-item rules apply.
 */
object LocatorOrb : KLogging() {
    // Reserved for Claude's import: OSRS 22079 / 22081. Never use those upstream ids in the 667 cache.
    const val INERT = 24617
    const val ACTIVE = 24618
    const val CRAFTING_LEVEL = 62
    const val DAMAGE = 10
    val IDS = setOf(INERT, ACTIVE)

    val ATTUNED = AttributeKey<Boolean>(persistenceKey = "locator_orb_attuned")
    val WARNING_ACCEPTED = AttributeKey<Boolean>(persistenceKey = "locator_orb_warning_accepted")

    /** Fail closed until the matching definitions are imported; a missing cache must not consume materials or break boot. */
    fun available(definitions: DefinitionSet): Boolean =
        definitions.getNullable(ItemDef::class.java, INERT)?.name == "Inert locator orb" &&
            definitions.getNullable(ItemDef::class.java, ACTIVE)?.let {
                it.name == "Locator orb" && it.inventoryMenu.any { option -> option.equals("Feel", true) }
            } == true

    private fun ready(player: Player): Boolean {
        if (available(player.world.definitions)) return true
        logger.warn { "PROBLEM N446 locator orb definitions missing/mismatched: expected inert=$INERT active=$ACTIVE" }
        player.message("Ava's locator orbs are temporarily unavailable.")
        return false
    }

    /** Use a pipe on a cut dragonstone, as in the Wiki quest guide; molten glass remains on the normal glassblowing route. */
    fun craft(player: Player): Boolean {
        if (!ready(player)) return false
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < CRAFTING_LEVEL) {
            player.message("You need a Crafting level of $CRAFTING_LEVEL to make an inert locator orb.")
            return false
        }
        val inv = player.inventory
        if (!inv.contains(Items.GLASSBLOWING_PIPE) || !inv.contains(Items.DRAGONSTONE) || inv.getItemCount(Items.MOLTEN_GLASS) < 2) {
            player.message("You need a glassblowing pipe, two molten glass and a cut dragonstone.")
            return false
        }
        // No suspension between validation and mutation. Replace the gemstone in its own slot: works in a full inventory too.
        val slot = inv.getItemIndex(Items.DRAGONSTONE, skipAttrItems = false)
        if (!inv.remove(Items.MOLTEN_GLASS, 2, assureFullRemoval = true).hasSucceeded()) return false
        inv[slot] = Item(INERT)
        player.animate(Anims.GLASSBLOW)
        player.message("You make an inert locator orb. Take it to Ava on the top floor of Draynor Manor.")
        return true
    }

    fun activate(player: Player): Boolean {
        if (!ready(player)) return false
        val slot = player.inventory.getItemIndex(INERT, skipAttrItems = false)
        if (slot < 0) return false
        player.inventory[slot] = Item(ACTIVE)
        player.attr[ATTUNED] = true
        player.message("Ava adjusts your inert locator orb and gives it back, ready to use.")
        return true
    }

    /** Free replacements require the first crafted orb and absence from ALL possessions (including death storage and BoB). */
    fun replace(player: Player): Boolean {
        if (!ready(player)) return false
        Possessions.whereOwned(player, IDS)?.let {
            player.message("You already have a locator orb in $it.")
            return false
        }
        if (player.attr[ATTUNED] != true) {
            player.message("Make an inert locator orb with a glassblowing pipe, two molten glass and a cut dragonstone, then bring it to Ava.")
            return false
        }
        if (!player.inventory.add(ACTIVE, assureFullInsertion = true).hasSucceeded()) {
            player.message("You need an empty inventory slot for the locator orb.")
            return false
        }
        player.message("Ava gives you another locator orb, free of charge.")
        return true
    }

    fun damage(hitpoints: Int): Int = minOf(DAMAGE, (hitpoints - 1).coerceAtLeast(0))

    fun feel(player: Player): Boolean {
        if (!player.inventory.contains(ACTIVE) || player.attr[WARNING_ACCEPTED] != true || player.isDead()) return false
        // Recompute at resolution, not at click time: spam clicks / an earlier combat hit must never let the orb kill.
        val hit = player.hit(damage(player.getCurrentLifepoints()))
        hit.setCancelIf {
            hit.hitmarks.single().damage = damage(player.getCurrentLifepoints())
            player.isDead()
        }
        player.message("The locator orb is unstable and hurts you as you use it.")
        return true
    }
}
