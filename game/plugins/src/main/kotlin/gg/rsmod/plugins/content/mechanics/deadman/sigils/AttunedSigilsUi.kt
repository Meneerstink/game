package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool
import gg.rsmod.game.tools.importer.AttunedSigilsInterfaceImportTool.AS
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.quests.tab.N108Ui

/**
 * SIGIL-INFO-UI (owner design 2026-11-21, screens/SIGILS/mock/sigil_venster_v3.png): the "Attuned Sigils" window. Three columns, one
 * per attuned sigil in the order they were attuned: skull (tier x kind), name, type, tier, the wiki effect text ([SigilTexts]) and an
 * Unattune button with the rules of the item option (at a bank, not skulled); an empty slot shows the red cross and the wiki line.
 * Attuning stays on the item (owner 21-11). Layout and sprites: [AttunedSigilsInterfaceImportTool].
 */
object AttunedSigilsUi {
    const val INTERFACE_ID = AttunedSigilsInterfaceImportTool.INTERFACE_ID

    private const val EVENTS_OP1 = 0x2
    private const val GREY = "9a9083"
    private const val P11 = "p11_full"
    private const val P12 = "p12_full"
    const val EMPTY_TEXT = "Look out for sigils as you explore the world. You can attune 3 of them to give you various advantages."

    /**
     * The icon drawn on a sigil's skull: sigil number -> OSRS sprite 3407..3453. The cache does not link the icons to the sigils, so a
     * sigil gets one only once the owner has confirmed the picture (contact sheet screens/SIGILS/osrs-sprites); until then the skull
     * alone shows its tier and kind.
     */
    val ICONS: Map<Int, Int> = emptyMap()

    fun isOpen(player: Player) = player.interfaces.isVisible(INTERFACE_ID)

    fun open(player: Player) {
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INTERFACE_ID)
        val hits = mutableListOf(AS.CLOSE)
        for (i in 0 until AS.SLOTS) hits += AS.slot(i) + AS.S_BUTTON_HIT
        hits.forEach { player.setInterfaceEvents(INTERFACE_ID, it, -1..-1, EVENTS_OP1) }
        draw(player)
    }

    fun close(player: Player) {
        if (isOpen(player)) player.closeInterface(INTERFACE_ID)
    }

    /** The sigil in window slot [slot] (0-2), or null for an empty slot. */
    fun sigilAt(
        player: Player,
        slot: Int,
    ): Sigil? = Sigils.attuned(player).getOrNull(slot)

    /**
     * The Unattune button of [slot]: the same path and rules as the item's Unattune option ([Sigils.unattune]); the attuned sigil has
     * to be in the inventory (it turns into the unattuned item there).
     */
    fun unattune(
        player: Player,
        slot: Int,
        atBank: Boolean,
    ): Sigils.Result? {
        val sigil = sigilAt(player, slot) ?: return null
        val inv = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == sigil.attunedId }
        if (inv == null) {
            N108Ui.reset(player, INTERFACE_ID)
            return Sigils.Result.ITEM_CHANGED
        }
        val result = Sigils.unattune(player, inv, atBank)
        if (isOpen(player)) draw(player)
        return result
    }

    fun draw(player: Player) {
        val p = INTERFACE_ID
        val world = player.world
        var content = AS.SCROLL_H
        for (i in 0 until AS.SLOTS) {
            val base = AS.slot(i)
            val x = AS.inX(i)
            val sigil = sigilAt(player, i)
            val filled = sigil != null
            player.setComponentSprite(p, base + AS.S_COLUMN, AttunedSigilsInterfaceImportTool.column(sigil?.kind?.name))
            N108Ui.hidden(player, p, base + AS.S_TYPE, !filled)
            N108Ui.hidden(player, p, base + AS.S_TIER, !filled)
            N108Ui.hidden(player, p, base + AS.S_BUTTON, !filled)
            N108Ui.hidden(player, p, base + AS.S_BUTTON_TEXT, !filled)
            N108Ui.hidden(player, p, base + AS.S_BUTTON_HIT, !filled)
            N108Ui.hidden(player, p, base + AS.S_HINT, filled)
            val textY: Int
            val text: String
            if (sigil == null) {
                player.setComponentSprite(p, base + AS.S_SKULL, AttunedSigilsInterfaceImportTool.spriteId("cross"))
                N108Ui.size(player, p, base + AS.S_SKULL, AS.CROSS, AS.CROSS)
                N108Ui.pos(player, p, base + AS.S_SKULL, x + (AS.COL_W - AS.CROSS) / 2, AS.IN_CROSS_Y)
                N108Ui.hidden(player, p, base + AS.S_ICON, true)
                N108Ui.pos(player, p, base + AS.S_NAME, x + 4, AS.IN_EMPTY_NAME_Y)
                N108Ui.text(player, p, base + AS.S_NAME, "<col=$GREY>Empty slot</col>")
                textY = AS.IN_EMPTY_TEXT_Y
                text = "<col=$GREY>$EMPTY_TEXT</col>"
            } else {
                player.setComponentSprite(p, base + AS.S_SKULL, AttunedSigilsInterfaceImportTool.skull(sigil.kind.name, sigil.tier))
                N108Ui.size(player, p, base + AS.S_SKULL, AS.SKULL_W, AS.SKULL_H)
                N108Ui.pos(player, p, base + AS.S_SKULL, x + (AS.COL_W - AS.SKULL_W) / 2, AS.IN_SKULL_Y)
                val icon = AttunedSigilsInterfaceImportTool.icon(ICONS[sigil.number] ?: -1)
                N108Ui.hidden(player, p, base + AS.S_ICON, icon < 0)
                if (icon >= 0) player.setComponentSprite(p, base + AS.S_ICON, icon)
                val nameLines = N108Ui.lines(world, P12, sigil.label, AS.NAME_W).coerceIn(1, 2)
                N108Ui.pos(player, p, base + AS.S_NAME, x + 4, AS.IN_NAME_Y)
                N108Ui.text(player, p, base + AS.S_NAME, sigil.label)
                val typeY = AS.IN_NAME_Y + nameLines * AS.LINE_P12 + 5
                N108Ui.pos(player, p, base + AS.S_TYPE, x, typeY)
                N108Ui.text(player, p, base + AS.S_TYPE, "Type: ${sigil.kind.name.lowercase().replaceFirstChar { it.uppercase() }}")
                N108Ui.pos(player, p, base + AS.S_TIER, x, typeY + AS.LINE_P11)
                N108Ui.text(player, p, base + AS.S_TIER, "Tier: ${sigil.tier}")
                textY = typeY + 30
                text = SigilTexts.of(sigil)
            }
            val lines = N108Ui.lines(world, P11, text, AS.TEXT_W)
            N108Ui.pos(player, p, base + AS.S_EFFECT, x + 6, textY)
            N108Ui.size(player, p, base + AS.S_EFFECT, AS.TEXT_W, lines * AS.LINE_P11 + 2)
            N108Ui.text(player, p, base + AS.S_EFFECT, text)
            content = maxOf(content, textY + lines * AS.LINE_P11 + 6)
        }
        N108Ui.scroll(player, p, AS.SCROLL, AS.SCROLLBAR, content)
    }
}
