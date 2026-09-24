package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.util.Misc
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The clan screens besides Clan Settings (1096), with this 667 cache's own component ids (decoded 2026-09-24; the Novite donor's
 * ClansManager is the behaviour reference, its component ids belong to another 1096/1107 build):
 *
 * - Clan Details (1107, tab button 1110:76): title 173, owner 35, home world 36, size 37, forum thread 38, motto 94, keywords 95,
 *   national flag graphic 199 (enum 3721), motif symbols 102 / 112 (enum 3686), game / clan time 204 / 203. The cache has no scripts
 *   filling it, so the server writes every field; the "Planted by" vexillum section (96) and the loading overlay (151) are hidden.
 * - Invitation (1095): opened by clicking the invite chat line (OPPLAYER9); Accept (36) is a pause button, Decline (46), Cancel (89)
 *   and Close (12) are buttons.
 * - National flag (1089, from 1096:371 "Select Flag"): script 4322 lists the flags as children of 1089:35 (slot = flag, enum 3721 /
 *   3722), Save & Quit is 1089:30.
 * - Motif Designer (1105, from 1096:125 "Edit motif"): scripts 4386 / 4387 list the symbols under 1105:66 (top) and 1105:63 (bottom),
 *   slot + 1 = enum 3686 key, shown from varbits 9086 / 9087; the four colour boxes (35, 80, 92, 104) open the HSL picker 1106
 *   (varp 2347 = the colour being edited; its Accept sends RESUME_P_HSLDIALOG), colours shown from varps 2094..2097; Done is 123.
 */
object ClanScreens {
    const val DETAILS = 1107
    const val INVITE = 1095
    const val FLAGS = 1089
    const val MOTIF = 1105
    const val HSL_PICKER = 1106

    const val INVITE_ACCEPT = 36
    val INVITE_REFUSE = listOf(46, 89, 12)

    const val FLAG_LIST = 35
    const val FLAG_SAVE = 30
    const val FLAG_CLOSE = 12
    const val FLAG_COUNT = 242

    const val MOTIF_TOP_LIST = 66
    const val MOTIF_BOTTOM_LIST = 63
    const val MOTIF_DONE = 123
    const val MOTIF_CLOSE = 151
    /** Colour boxes in varp order (2094 symbol top, 2095 symbol bottom, 2096 primary, 2097 secondary). */
    val MOTIF_COLOUR_BUTTONS = listOf(35, 80, 92, 104)
    const val MOTIF_SYMBOLS = 114

    private const val FLAG_SPRITES_ENUM = 3721
    private const val MOTIF_SPRITES_ENUM = 3686
    private const val MOTIF_TOP_VARBIT = 9086
    private const val MOTIF_BOTTOM_VARBIT = 9087
    private const val MOTIF_COLOUR_VARP = 2094
    private const val HSL_EDIT_VARP = 2347
    private const val OP1 = 2

    private val FLAG_SELECTION = AttributeKey<Int>()
    private val MOTIF_PART = AttributeKey<Int>()

    private fun enumInt(player: Player, enum: Int, key: Int): Int =
        (player.world.definitions.get(EnumDef::class.java, enum).values[key] as? Int) ?: -1

    // ---- Clan Details ------------------------------------------------------------------------------------------------------------

    fun openDetails(player: Player) {
        val clan = Clans.clanOfPlayer(player) ?: return player.message("You're not in a clan.")
        if (player.getInterfaceAt(InterfaceDestination.MAIN_SCREEN) != -1) return player.message("Please close the interface you have open first.")
        player.openInterface(DETAILS, InterfaceDestination.MAIN_SCREEN)
        player.setComponentHidden(DETAILS, 151, true)
        player.setComponentHidden(DETAILS, 96, true)
        player.setComponentText(DETAILS, 173, clan.name)
        player.setComponentText(DETAILS, 35, Clans.ownerOf(clan)?.let(Misc::formatForDisplay) ?: "")
        player.setComponentText(DETAILS, 36, if (clan.worldId > 0) clan.worldId.toString() else "None")
        player.setComponentText(DETAILS, 37, clan.members.size.toString())
        player.setComponentText(DETAILS, 38, clan.threadId ?: "None")
        player.setComponentText(DETAILS, 94, clan.motto ?: "")
        player.setComponentText(DETAILS, 95, clan.keywords ?: "")
        player.setComponentText(DETAILS, 5, if (clan.recruiting) "This clan is recruiting." else "")
        player.setComponentSprite(DETAILS, 199, enumInt(player, FLAG_SPRITES_ENUM, clan.flag))
        player.setComponentSprite(DETAILS, 102, if (clan.motifTop > 0) enumInt(player, MOTIF_SPRITES_ENUM, clan.motifTop) else -1)
        player.setComponentSprite(DETAILS, 112, if (clan.motifBottom > 0) enumInt(player, MOTIF_SPRITES_ENUM, clan.motifBottom) else -1)
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        player.setComponentText(DETAILS, 204, "%02d:%02d".format(now.hour, now.minute))
        val clanTime = now.plusMinutes(clan.timeZone.toLong())
        player.setComponentText(DETAILS, 203, "%02d:%02d".format(clanTime.hour, clanTime.minute))
    }

    // ---- Invitation --------------------------------------------------------------------------------------------------------------

    /** The invite chat line was clicked: the invitation screen, then Accept joins (Novite ClanInvite dialogue). */
    fun openInvite(player: Player, inviter: Player) {
        if (player.attr[Clans.CLAN_ATTR] != null) return
        if (player.getInterfaceAt(InterfaceDestination.MAIN_SCREEN) != -1) return player.message("Please close the interface you have open first.")
        val clan = Clans.takeInvite(player, inviter) ?: return
        player.queue {
            Clans.sendPreviewSettings(player, clan)
            player.openInterface(INVITE, InterfaceDestination.MAIN_SCREEN)
            player.setComponentText(INVITE, 2, "You have been invited to join ${clan.name} by ${Misc.formatForDisplay(inviter.username)}.")
            player.setComponentHidden(INVITE, 94, true)
            terminateAction = {
                player.closeInterface(INVITE)
                Clans.restoreListenedSettings(player)
            }
            waitReturnValue()
            val msg = requestReturnValue as? gg.rsmod.game.message.impl.ResumePauseButtonMessage
            terminateAction = null
            player.closeInterface(INVITE)
            Clans.restoreListenedSettings(player)
            if (msg != null && msg.interfaceId == INVITE && msg.component == INVITE_ACCEPT) Clans.acceptInvite(player, clan.name)
        }
    }

    /** Decline / Cancel / Close: ends the waiting invitation task (its terminate action closes the screen and restores settings). */
    fun refuseInvite(player: Player) {
        player.closeInterface(INVITE)
        player.interruptQueues()
        Clans.restoreListenedSettings(player)
    }

    // ---- National flag -----------------------------------------------------------------------------------------------------------

    fun openFlags(player: Player) {
        val clan = Clans.clanOfPlayer(player) ?: return
        if (Clans.rankOfPlayer(player) < ClanRank.ADMIN.value) return player.message("You need to be an admin or above to change the clan settings.")
        player.attr[FLAG_SELECTION] = clan.flag
        player.openInterface(FLAGS, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(FLAGS, FLAG_LIST, 0 until FLAG_COUNT, OP1)
    }

    fun selectFlag(player: Player, slot: Int) {
        if (slot in 0 until FLAG_COUNT) player.attr[FLAG_SELECTION] = slot
    }

    fun saveFlag(player: Player) {
        val flag = player.attr[FLAG_SELECTION]
        player.attr.remove(FLAG_SELECTION)
        if (flag != null) Clans.editSettings(player) { this.flag = flag }
        backToSettings(player, FLAGS)
    }

    fun backToSettings(player: Player, from: Int) {
        player.closeInterface(from)
        ClanSettingsInterface.open(player)
    }

    // ---- Motif Designer ----------------------------------------------------------------------------------------------------------

    fun openMotif(player: Player) {
        val clan = Clans.clanOfPlayer(player) ?: return
        if (Clans.rankOfPlayer(player) < ClanRank.ADMIN.value) return player.message("You need to be an admin or above to change the clan settings.")
        player.openInterface(MOTIF, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(MOTIF, MOTIF_TOP_LIST, 0 until MOTIF_SYMBOLS, OP1)
        player.setInterfaceEvents(MOTIF, MOTIF_BOTTOM_LIST, 0 until MOTIF_SYMBOLS, OP1)
        sendMotif(player, clan)
    }

    private fun sendMotif(player: Player, clan: Clan) {
        clan.motifColours.forEachIndexed { i, colour -> player.setVarp(MOTIF_COLOUR_VARP + i, colour) }
        player.setVarbit(MOTIF_TOP_VARBIT, clan.motifTop)
        player.setVarbit(MOTIF_BOTTOM_VARBIT, clan.motifBottom)
    }

    fun selectSymbol(player: Player, top: Boolean, slot: Int) {
        if (slot !in 0 until MOTIF_SYMBOLS) return
        if (!Clans.editSettings(player) { if (top) motifTop = slot + 1 else motifBottom = slot + 1 }) return
        Clans.clanOfPlayer(player)?.let { sendMotif(player, it) }
    }

    fun editColour(player: Player, part: Int) {
        val clan = Clans.clanOfPlayer(player) ?: return
        if (Clans.rankOfPlayer(player) < ClanRank.ADMIN.value) return
        player.attr[MOTIF_PART] = part
        player.setVarp(HSL_EDIT_VARP, clan.motifColours[part])
        player.openInterface(HSL_PICKER, InterfaceDestination.MAIN_SCREEN)
    }

    /** RESUME_P_HSLDIALOG: the picked colour goes into the part being edited; the designer comes back. */
    fun colourChosen(player: Player, hsl: Int) {
        val part = player.attr[MOTIF_PART] ?: return
        player.attr.remove(MOTIF_PART)
        Clans.editSettings(player) { motifColours[part] = hsl and 0xFFFF }
        player.closeInterface(HSL_PICKER)
        openMotif(player)
    }
}
