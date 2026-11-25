package gg.rsmod.plugins.content.magic.jewelleryenchanting

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.varrock.VarrockMuseum
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions
import gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction

/** OSRS Wiki Jewellery Enchantments/Lvl-7 Enchant: seven item-target spells, 3 ticks manual,
 * 7 ticks automatic. Wiki Lvl-6/Lvl-7 GIFs show the same held-amulet cast; cache2686 names
 * HUMAN_ENCHANTAMULETLVL3=721 / ENCHANT_AMULET2_LVL6=452, sound143 (Wiki audio licence).
 * Other tiers retain their existing native visuals. No OSRS quest/diary gates are added.
 */
object JewelleryEnchantment {
    const val INTERFACE = 1251
    const val BACK = 2
    const val ITEM_TARGET_EVENTS = 0x010000 // exact baked events of native192:29/41/53/61/76/88, targetMask32.
    val SPELLS = listOf(SpellbookData.LVL_1_ENCHANT, SpellbookData.LVL_2_ENCHANT, SpellbookData.LVL_3_ENCHANT,
        SpellbookData.LVL_4_ENCHANT, SpellbookData.LVL_5_ENCHANT, SpellbookData.LVL_6_ENCHANT, SpellbookData.LVL_7_ENCHANT)
    private val CAST = TimerKey()
    private val XP = listOf(17.5, 37.0, 59.0, 67.0, 78.0, 97.0, 110.0)

    fun data(raw: Int, spell: SpellbookData): EnchantmentData? = EnchantmentData.values().firstOrNull { it.raw == raw && it.spell == spell }

    fun cast(player: Player, spell: SpellbookData, slot: Int, expectedRaw: Int): Boolean {
        if (player.isDead() || !player.lock.canItemInteract() || player.timers.has(CAST)) return false
        if (ActivityRestrictions.refuse(player, RestrictedAction.UTILITY_SPELL)) return false
        if (slot !in 0 until player.inventory.capacity) return false
        val item = player.inventory[slot] ?: return false
        if (item.id != expectedRaw || item.amount != 1) return false
        val product = data(item.id, spell)
        if (product == null || item.getDef(player.world.definitions).noted) {
            player.message("You can't use this spell on this item.")
            return false
        }
        if (spell == SpellbookData.LVL_7_ENCHANT &&
            item.getDef(player.world.definitions).name != product.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) {
            player.message("This jewellery's import is not available yet.")
            return false
        }
        if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
        val metadata = MagicSpells.getMetadata(spell.uniqueId) ?: return false
        if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes, metadata.sprite)) return false
        if (!VarrockMuseum.mayEnchant(player, item.id)) return false
        // No suspension between revalidation, payment and slot replacement. Full inventory is safe.
        player.timers[CAST] = 3
        MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
        player.inventory[slot] = Item(product.product)
        player.addXp(Skills.MAGIC, XP[SPELLS.indexOf(spell)], checkBrawlingGloves = true)
        val animation = when (spell) { SpellbookData.LVL_1_ENCHANT -> 719; SpellbookData.LVL_2_ENCHANT -> 720; else -> 721 }
        val graphic = when (spell) { SpellbookData.LVL_1_ENCHANT -> 114; SpellbookData.LVL_2_ENCHANT -> 115; SpellbookData.LVL_7_ENCHANT -> 452; else -> 116 }
        player.animate(animation)
        player.graphic(graphic, 92)
        player.playSound(product.sound)
        return true
    }

    suspend fun QueueTask.enchant(spell: SpellbookData, firstSlot: Int) {
        if (firstSlot !in 0 until player.inventory.capacity) return
        val raw = player.inventory[firstSlot]?.id ?: return
        var slot = firstSlot
        while (cast(player, spell, slot, raw)) {
            wait(7)
            slot = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == raw } ?: return
        }
    }

    fun open(player: Player) {
        if (player.getSpellbook() != gg.rsmod.plugins.api.Spellbook.STANDARD) return
        if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
        player.openInterface(INTERFACE, InterfaceDestination.MAGIC_TAB)
        player.focusTab(Tabs.SPELLBOOK)
        player.setInterfaceEvents(INTERFACE, BACK, -1..-1, 2)
        SPELLS.forEachIndexed { i, spell ->
            // Item targeting, no Autocast op. Requirements are always checked again by cast().
            player.setInterfaceEvents(INTERFACE, 3 + i, -1..-1, ITEM_TARGET_EVENTS)
            val available = MagicSpells.castProblem(player, spell.level, spell.runes, spell.uniqueId) == null
            player.setComponentSprite(INTERFACE, 3 + i, if (spell == SpellbookData.LVL_7_ENCHANT) (if (available) 9208 else 9209)
                else intArrayOf(18, 28, 39, 43, 50, 353)[i] + if (available) 0 else 50)
        }
    }

    fun back(player: Player) { player.openInterface(player.spellbookInterfaceId(), InterfaceDestination.MAGIC_TAB) }
}
