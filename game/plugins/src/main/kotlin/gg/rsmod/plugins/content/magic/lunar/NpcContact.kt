package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentNpcHead
import gg.rsmod.plugins.api.ext.setComponentText
import java.lang.ref.WeakReference

/**
 * N72 "Astral Contact" (the OSRS name of the Lunar spell NPC Contact, 430:26). Owner 2026-11-01 14:1x: make it work with the npcs
 * that exist here, the rest later. Donor: Void `content/skill/magic/book/lunar/NPCContact.kt` + `npc_contact.ifaces.toml`
 * (interface 429, head/name component per contact, cast anim 4413 lunar_cast_charge, gfx 728 height 130, sound 3618, 63 xp, the
 * contact window two ticks after the cast). A chosen contact talks through the npc's own Talk-to handler, with the real npc in the
 * world as the dialogue partner (Void `talkWith(npc)`), so the slayer masters and the Dark mage keep their one dialogue.
 */
object NpcContact {
    const val INTERFACE = 429
    const val XP = 63.0
    const val ANIMATION = 4413
    const val GRAPHIC = 728
    const val GRAPHIC_HEIGHT = 130
    const val SOUND = 3618

    /** A contact: its window components (head + name), the npc ids it may be spawned as, and its name for the "too busy" line. */
    class Contact(val name: String, val head: Int, val label: Int, val npcIds: List<Int>)

    /** Void's contactable list on our npcs; Lapalok answers as Duradel (Void), Sumona is spawned as her wrapper 7779. */
    val CONTACTS =
        listOf(
            Contact("Turael", 13, 41, listOf(Npcs.TURAEL)),
            Contact("Mazchna", 18, 44, listOf(Npcs.MAZCHNA)),
            Contact("Vannaka", 28, 46, listOf(Npcs.VANNAKA)),
            Contact("Chaeldar", 29, 48, listOf(Npcs.CHAELDAR)),
            Contact("Sumona", 27, 42, listOf(7779, Npcs.SUMONA)),
            Contact("Duradel", 23, 45, listOf(Npcs.DURADEL_8466)),
            Contact("Lapalok", 25, 36, listOf(Npcs.DURADEL_8466)),
            Contact("Dark mage", 30, 47, listOf(Npcs.DARK_MAGE_2262)),
        )

    /** Void's hidden contacts (no content behind them here yet): Honest Jimmy, Bert, Advisor Ghrim, Spria, Lanthus, Achtryn, Cyrisus, Larry. */
    val HIDDEN = listOf(10, 38, 11, 39, 12, 40, 15, 37, 17, 43, 21, 22, 31, 32, 33, 49)
    const val RANDOM_HEAD = 35
    const val RANDOM_LABEL = 51
    const val CLOSE = 52

    fun byComponent(component: Int): Contact? = CONTACTS.firstOrNull { component == it.head || component == it.label }

    fun open(player: Player) {
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        HIDDEN.forEach { player.setComponentHidden(INTERFACE, it, true) }
        // Void: "The cache reuses Murphy's slot; replace him with the Abyss's dark mage".
        player.setComponentNpcHead(INTERFACE, 30, Npcs.DARK_MAGE_2262)
        player.setComponentText(INTERFACE, 47, "Dark mage")
    }

    /** The npc option index (1-based) of Talk-to on [npcId], or -1. */
    fun talkOption(player: Player, npcId: Int): Int {
        val def = player.world.definitions.getNullable(NpcDef::class.java, npcId) ?: return -1
        val i = def.options.indexOfFirst { it.equals("talk-to", ignoreCase = true) }
        return if (i < 0) -1 else i + 1
    }

    fun contact(player: Player, contact: Contact) {
        player.closeInterface(INTERFACE)
        val npc = contact.npcIds.firstNotNullOfOrNull { id -> player.world.npcs.firstOrNull { it.id == id } }
        val option = npc?.let { talkOption(player, it.id) } ?: -1
        if (npc == null || option < 0) {
            player.message("${contact.name} is too busy to talk right now.")
            return
        }
        player.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
        if (!player.world.plugins.executeNpc(player, npc.id, option)) player.message("${contact.name} is too busy to talk right now.")
    }
}
