package gg.rsmod.plugins.content.areas.varrock

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.bracket.TheDigSite

/**
 * PROJECT48 run 4 O17 (The Dig Site unlock): the Varrock Museum specimen cleaning activity. Shared state for
 * varrock_museum_cleaning.plugin.kts and the ruby-necklace enchant (Lvl-3 Enchant + Enchant ruby tablet).
 *
 * Source: OSRS Wiki "Clean necklace": showing the clean necklace to a Varrock Museum archaeologist teaches the player to enchant ruby
 * necklaces into digsite pendants; before that, Lvl-3 Enchant / the ruby tablet on a ruby necklace gives the message
 * "You have not yet discovered the secret to enchanting ruby necklaces." Ruby amulets are not affected.
 */
object VarrockMuseum {
    // Same persisted identification state as the existing archaeologist dialogue (order must remain unchanged).
    val CASE_TOLD = AttributeKey<Int>(persistenceKey = "museum_case_told")
    const val KUDOS_VARP = 1010 // native interface 532:1 script64 / varp trigger1010

    data class Exhibit(val item: Int, val number: Int, val root: Int, val filled: Int,
        val varbit: Int, val toldBit: Int, val tile: Tile, val model: Int)

    // OSRS Wiki Kudos / five artefact pages; ids, varbits, tiles and inventory meshes from the native 667 cache.
    val EXHIBITS = listOf(
        Exhibit(Items.OLD_SYMBOL, 36, 12137, 24539, 3646, 0, Tile(3262, 3448), 25511),
        Exhibit(Items.ANCIENT_SYMBOL, 37, 12138, 24540, 3645, 1, Tile(3262, 3450), 25504),
        Exhibit(Items.OLD_COIN, 45, 15484, 24549, 3648, 2, Tile(3259, 3448), 25509),
        Exhibit(Items.ANCIENT_COIN, 44, 12234, 24548, 3647, 3, Tile(3259, 3450), 25510),
        Exhibit(Items.POTTERY, 22, 12139, 24542, 3649, 4, Tile(3259, 3452), 25508),
    )

    fun displayed(player: Player, item: Int): Boolean = EXHIBITS.firstOrNull { it.item == item }
        ?.let { player.getVarbit(it.varbit) != 0 } == true

    fun exhibit(obj: GameObject): Exhibit? = EXHIBITS.firstOrNull {
        obj.tile == it.tile && obj.id in setOf(it.root, it.filled, if (it.item == Items.POTTERY) 24543 else 24550)
    }

    /** Synchronous consume + native persistent completion bit + Kudos: no delayed reward to claim twice. */
    fun place(player: Player, obj: GameObject, slot: Int, expected: Item): Boolean {
        val target = exhibit(obj) ?: return false
        if (!TheDigSite.isFinished(player) || expected.id != target.item || player.inventory[slot] !== expected ||
            displayed(player, target.item) || (player.attr[CASE_TOLD] ?: 0) and (1 shl target.toldBit) == 0) return false
        if (!player.inventory.remove(expected.id, 1, beginSlot = slot).hasSucceeded()) return false
        // Wiki artefact pages: all copies of the four unique finds are taken, including banked copies.
        // Pottery remains a common find and only one piece is handed in.
        if (target.item != Items.POTTERY) {
            player.inventory.remove(target.item, player.inventory.getItemCount(target.item))
            player.bank.remove(target.item, player.bank.getItemCount(target.item))
        }
        player.setVarbit(target.varbit, 1)
        player.setVarp(KUDOS_VARP, player.getVarp(KUDOS_VARP) + 10)
        player.playSound(Sfx.VM_GAIN_KUDOS)
        return true
    }

    /** Native Kudos parchment, factual labels/counts only; cleaning displays award 10 Kudos, no guaranteed lamp. */
    fun showKudos(player: Player) {
        player.openInterface(600, InterfaceDestination.MAIN_SCREEN)
        player.setComponentText(600, 13, "Museum Kudos")
        player.setComponentText(600, 14, "Museum Kudos: ${player.getVarp(KUDOS_VARP)}<br>" +
            EXHIBITS.sortedBy { it.number }.joinToString("<br>") {
                val name = player.world.definitions.get(ItemDef::class.java, it.item).name
                "${it.number}: $name (${if (displayed(player, it.item)) 10 else 0})"
            })
        for (component in 17..21) player.setComponentHidden(600, component, true)
    }

    fun study(player: Player, obj: GameObject) {
        val target = exhibit(obj) ?: return
        player.openInterface(528, InterfaceDestination.MAIN_SCREEN)
        val visible = target.item == Items.POTTERY || displayed(player, target.item)
        player.setComponentText(528, 2, if (visible) player.world.definitions.get(ItemDef::class.java, target.item).name else "")
        player.setComponentText(528, 15, "Display case")
        player.setComponentText(528, 16, target.number.toString())
        for (component in 3..14) player.setComponentHidden(528, component, component != 3 || !visible)
        if (visible) player.setComponentModel(528, 3, target.model)
    }

    /** Set when an archaeologist has studied the clean necklace (OSRS: the pendant spell is learnt). */
    val RUBY_PENDANT_LEARNT = AttributeKey<Boolean>(persistenceKey = "museum_ruby_pendant")

    const val NOT_LEARNT_MESSAGE = "You have not yet discovered the secret to enchanting ruby necklaces."

    fun learnt(player: Player): Boolean = player.attr[RUBY_PENDANT_LEARNT] == true

    /** True when [item] may be enchanted now; sends the OSRS refusal for a ruby necklace before the necklace was shown. */
    fun mayEnchant(player: Player, item: Int): Boolean {
        if (item != Items.RUBY_NECKLACE || learnt(player)) return true
        player.message(NOT_LEARNT_MESSAGE)
        return false
    }
}
