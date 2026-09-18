package gg.rsmod.plugins.content.items

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.ext.confirmItemAction
import gg.rsmod.plugins.api.ext.itemMessageBox

/**
 * Owner 2026-09-18 (binding): "every uncharge item needs to give a warning ... or every disassemble", "when disassembling any
 * item for example slayer helm it needs to give a warning gui are you sure - not only the slayer helmet but all items you can
 * disassemble", and "when checking a weapon (Webweaver or any other) it should show a GUI ... same GUI as the Dragonfire ward
 * Inspect".
 *
 * Root cause: every item option handler decided on its own whether to confirm and how to report, so most printed a plain chat
 * line and ran destructive actions without asking. This guard sits once in front of every bound inventory and worn-equipment
 * option ([PluginRepository.itemOptionInterceptor]) and applies both rules to every item, now and in the future, by the
 * option's name in the item definition:
 *
 * - [CONFIRM_OPTIONS] (Uncharge / Dismantle / Disassemble / Revert): an "Are you sure" item GUI (the destroy-item interface 94
 *   with the item's picture and name) first; the action only runs on Yes and when the item is still in the same place.
 *   Handlers that already ask their own item-specific question are registered in [selfConfirming] and keep their wording.
 * - [STATUS_OPTIONS] (Check / Check-charges / Inspect): the handler's chat lines are collected while it runs and shown in the
 *   item dialogue (interface 519, item picture + text) exactly like the Dragonfire ward Inspect.
 */
object ItemActionGuard {
    val CONFIRM_OPTIONS = setOf("uncharge", "dismantle", "disassemble", "revert")
    val STATUS_OPTIONS = setOf("check", "check-charges", "check charges", "inspect")

    /** Chat lines collected while a status option runs (read by `Player.message`). */
    val STATUS_CAPTURE = AttributeKey<MutableList<String>>()

    private val selfConfirming = HashSet<Long>()

    private fun key(
        itemId: Int,
        option: String,
    ): Long = (itemId.toLong() shl 32) or option.lowercase().hashCode().toLong().and(0xFFFFFFFFL)

    /** [option] on [itemIds] already asks its own confirmation question; the shared one is skipped. */
    fun selfConfirming(
        option: String,
        vararg itemIds: Int,
    ) {
        itemIds.forEach { selfConfirming += key(it, option) }
    }

    fun isSelfConfirming(
        itemId: Int,
        option: String,
    ): Boolean = key(itemId, option) in selfConfirming

    fun optionName(
        player: Player,
        itemId: Int,
        option: Int,
        worn: Boolean,
    ): String? {
        val def = player.world.definitions.getNullable(ItemDef::class.java, itemId) ?: return null
        val menu = if (worn) def.equipmentMenu else def.inventoryMenu
        return menu.getOrNull(option - 1)?.lowercase()
    }

    /** Detail line of the confirmation GUI: what the option does, never a mechanic the item does not have. */
    fun detail(option: String): String =
        when (option) {
            "uncharge" -> "This will remove the charges from this item."
            "revert" -> "This will revert this item to its original form."
            else -> "This will take this item apart."
        }

    fun question(option: String): String = "Are you sure you want to $option this item?"

    fun install(repository: PluginRepository) {
        repository.itemOptionInterceptor = interceptor@{ player, itemId, option, worn, proceed ->
            val name = optionName(player, itemId, option, worn) ?: return@interceptor false
            when {
                name in CONFIRM_OPTIONS && !isSelfConfirming(itemId, name) -> {
                    confirmThenRun(player, itemId, name, worn, proceed)
                    true
                }
                name in STATUS_OPTIONS -> {
                    runCapturingStatus(player, itemId, proceed)
                    true
                }
                else -> false
            }
        }
    }

    private fun confirmThenRun(
        player: Player,
        itemId: Int,
        option: String,
        worn: Boolean,
        proceed: () -> Unit,
    ) {
        val slot = player.attr[INTERACTING_ITEM_SLOT]
        player.queue {
            if (!confirmItemAction(itemId, question(option), detail(option))) return@queue
            // The item must still be where it was clicked (it may have been moved while the GUI was open).
            if (worn) {
                if (player.equipment.getItemCount(itemId) <= 0) return@queue
            } else {
                if (slot == null || player.inventory[slot]?.id != itemId) return@queue
                player.attr[INTERACTING_ITEM_SLOT] = slot
            }
            proceed()
        }
    }

    private fun runCapturingStatus(
        player: Player,
        itemId: Int,
        proceed: () -> Unit,
    ) {
        val lines = mutableListOf<String>()
        player.attr[STATUS_CAPTURE] = lines
        try {
            proceed()
        } finally {
            player.attr.remove(STATUS_CAPTURE)
        }
        if (lines.isEmpty()) return
        val text = lines.joinToString("<br>")
        player.queue { itemMessageBox(text, item = itemId) }
    }
}
