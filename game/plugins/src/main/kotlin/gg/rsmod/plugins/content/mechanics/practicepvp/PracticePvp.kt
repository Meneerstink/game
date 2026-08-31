package gg.rsmod.plugins.content.mechanics.practicepvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import java.lang.ref.WeakReference

/**
 * Safe Practice PvP (PROJECT_PLAN SS15): no real-item loss, no economic reward, free
 * temporary presets.
 *
 * ponytail/security tradeoff, flagged prominently for the owner (see IMPLEMENTATION_STATUS.md
 * FUTURE FINETUNING): rather than building a full stash/restore of a player's real
 * inventory+equipment (a real item-loss/dupe risk to get exactly right under time pressure),
 * entry *requires* both the inventory and equipment to already be empty - there is never
 * anything real to protect. Presets are intentionally mid-tier (not BIS) so that even the
 * unaddressed gap below is low economic impact:
 *
 * Temp items are NOT flagged untradeable at the item-definition level (can't add new items
 * to a fixed cache), so [isHoldingTempGear] plus a guard at each real entry point
 * (`Bank.open`/`Bank.openDepositBox`, the "Trade with" option, `Player.openShop`) is the fix
 * instead - refuses cleanly with a message, before anything opens, so no item or point is ever
 * at risk (nothing is removed/spent by the refusal itself). The `::ge_sell`/`::ge_buy` commands
 * (grand_exchange.plugin.kts) are also guarded the same way - the doc note that used to be here
 * ("GE is not guarded, nothing reachable to leak through") was wrong: the command route was
 * already a real, reachable entry point that could sell free temp gear for real GP. Cleanup
 * (death/leave/logout, all three wired below) remains the primary mechanism; these guards close
 * the mid-match window cleanup alone didn't cover.
 */
object PracticePvp {
    private val IN_MATCH_ATTR = AttributeKey<Boolean>()
    private val PARTNER_ATTR = AttributeKey<WeakReference<Player>>()
    private val GRANTED_SLOTS_ATTR = AttributeKey<MutableList<Int>>()

    private val queue = ArrayList<Player>()

    enum class Preset(
        val label: String,
        val weapon: Int,
        val shield: Int?,
        val body: Int,
        val legs: Int,
        val helm: Int,
    ) {
        MELEE("Melee", Items.ADAMANT_SCIMITAR, Items.ADAMANT_KITESHIELD, Items.ADAMANT_PLATEBODY, Items.ADAMANT_PLATELEGS, Items.ADAMANT_FULL_HELM),
        RANGE("Ranged", Items.ADAMANT_CROSSBOW, Items.ADAMANT_KITESHIELD, Items.STUDDED_BODY, Items.STUDDED_CHAPS, Items.COIF),
        MAGE("Magic", Items.STAFF_OF_AIR, null, Items.MYSTIC_ROBE_TOP, Items.MYSTIC_ROBE_BOTTOM, Items.WIZARD_HAT),
    }

    fun queueUp(
        player: Player,
        preset: Preset,
    ) {
        if (player.attr[IN_MATCH_ATTR] == true) {
            player.filterableMessage("You're already in a Practice PvP match.")
            return
        }
        if (!player.inventory.isEmpty || !player.equipment.isEmpty) {
            player.filterableMessage("Bank your items first - Practice PvP requires an empty inventory and equipment.")
            return
        }
        queue.removeIf { it == player || !it.isOnline }
        val opponent = queue.firstOrNull()
        if (opponent == null) {
            queue.add(player)
            player.filterableMessage("Queued for Practice PvP. Waiting for an opponent...")
            grant(player, preset)
            return
        }
        queue.remove(opponent)
        grant(player, preset)
        startMatch(player, opponent)
    }

    private fun grant(
        player: Player,
        preset: Preset,
    ) {
        val slots = mutableListOf<Int>()
        fun give(item: Int, slot: EquipmentType) {
            player.equipment[slot.id] = Item(item)
            slots.add(slot.id)
        }
        give(preset.weapon, EquipmentType.WEAPON)
        preset.shield?.let { give(it, EquipmentType.SHIELD) }
        give(preset.body, EquipmentType.CHEST)
        give(preset.legs, EquipmentType.LEGS)
        give(preset.helm, EquipmentType.HEAD)
        player.attr[GRANTED_SLOTS_ATTR] = slots
        player.refreshBonuses()
        player.filterableMessage("You are equipped with the ${preset.label} preset.")
    }

    private fun startMatch(
        a: Player,
        b: Player,
    ) {
        a.attr[IN_MATCH_ATTR] = true
        b.attr[IN_MATCH_ATTR] = true
        a.attr[PARTNER_ATTR] = WeakReference(b)
        b.attr[PARTNER_ATTR] = WeakReference(a)
        b.teleportTo(gg.rsmod.game.model.Tile(a.tile))
        a.filterableMessage("Your Practice PvP match against ${b.username} has begun. Fight!")
        b.filterableMessage("Your Practice PvP match against ${a.username} has begun. Fight!")
    }

    fun areMatched(
        a: Player,
        b: Player,
    ): Boolean = a.attr[IN_MATCH_ATTR] == true && a.attr[PARTNER_ATTR]?.get() == b

    /** True while [player] is holding granted temp preset gear - the actual leak surface,
     * not just [IN_MATCH_ATTR] (queued-but-not-yet-matched players already hold temp gear too,
     * per [grant] being called before [startMatch]). */
    fun isHoldingTempGear(player: Player): Boolean = player.attr[GRANTED_SLOTS_ATTR] != null

    /** Call on death, manual leave, and logout - always safe/idempotent. */
    fun cleanup(player: Player) {
        val slots = player.attr[GRANTED_SLOTS_ATTR]
        if (slots != null) {
            slots.forEach { slot -> player.equipment[slot] = null }
            player.attr.remove(GRANTED_SLOTS_ATTR)
            player.refreshBonuses()
        }
        val partner = player.attr[PARTNER_ATTR]?.get()
        player.attr.remove(IN_MATCH_ATTR)
        player.attr.remove(PARTNER_ATTR)
        queue.remove(player)
        if (partner != null && partner.attr[IN_MATCH_ATTR] == true) {
            partner.filterableMessage("Your Practice PvP opponent has left. Match over.")
            cleanup(partner)
        }
    }
}
