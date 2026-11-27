package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.content.magic.*
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button

private val SOUNDAREA_ID = 200
private val SOUNDAREA_RADIUS = 10
private val SOUNDAREA_VOLUME = 1

TeleportSpell.values.forEach { teleport ->
    val spriteId = teleport.spriteId
    if (spriteId == null) {
        on_magic_spell_button(teleport.spellName) { metadata ->
            player.teleport(teleport, metadata)
        }
    } else {
        val metadata = MagicSpells.getMetadata(spriteId)!!
        on_button(metadata.interfaceId, metadata.component) {
            player.teleport(teleport, metadata)
        }
    }
}

fun Player.teleport(
    spell: TeleportSpell,
    data: SpellMetadata,
) {
    // ONTBREKEND 21 (OSRS Wiki "Ape Atoll Teleport (standard)"): unlocked by the Awowogei section of Recipe for Disaster (part 7;
    // QuestBrackets.rfdSubquests counts bracket-given and played parts). Accounts outside the brackets keep the Monkey Madness gate.
    if (spell == TeleportSpell.APE_ATOLL) {
        val bracket = gg.rsmod.plugins.content.quests.foundation.QuestBrackets.applies(this)
        val open = if (bracket) gg.rsmod.plugins.content.quests.foundation.QuestBrackets.rfdSubquests(this) >= AWOWOGEI_PART
        else gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(this, "Monkey Madness")
        if (!open) {
            message(if (bracket) "You need to free King Awowogei in Recipe for Disaster to use this teleport." else "You need to complete Monkey Madness to use this teleport.")
            return
        }
    }
    // PROJECT48 ONTGR (Plague City): the Ardougne Teleport spell is unlocked by the quest (OSRS Wiki "Ardougne Teleport").
    if (spell == TeleportSpell.ARDOUGNE && !gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(this, "Plague City")) {
        message("You need to complete Plague City to use this teleport.")
        return
    }
    // DIARIES-OSRS Kandarin hard (OSRS Wiki): "Camelot Teleport can now be used to teleport to Seers' Village bank instead" (the choice is
    // set at the Wedge; the 667 spellbook has no right-click option for it).
    val endTile = if (spell == TeleportSpell.CAMELOT && gg.rsmod.plugins.content.diaries.DiaryItems.camelotToSeers(this))
        gg.rsmod.game.model.collision.Landing.pick(world.collision, gg.rsmod.game.model.Area(2724, 3486, 2729, 3490), 0) else findValidTile(spell)
    teleport(spell.type, endTile, spell.xp, data)
}

/** A random tile of the spell's area on its walkable ground (Landing: not inside scenery, not in a closed-off pocket). */
fun Player.findValidTile(spell: TeleportSpell): Tile = gg.rsmod.game.model.collision.Landing.pick(world.collision, spell.endArea, spell.level)

/** Recipe for Disaster part 7 = Freeing King Awowogei (B111Rfd). */
val AWOWOGEI_PART = 7

fun Player.teleport(
    type: TeleportType,
    endTile: Tile,
    xp: Double,
    data: SpellMetadata,
) {
    // N72 (owner 2026-10-31 18:4x "alle teleports moeten nu gewoon runes kosten"): the spell's own runes and ingredients.
    // PROJECT48 S05 Sigil of Mobility: teleports do not consume runes and do not grant any XP.
    val mobility = gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigils.effective(this, gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigil.MOBILITY)
    val itemRequirements = if (mobility) emptyList() else data.runes
 if (!MagicSpells.canCast(this, data.lvl, itemRequirements)) {
        return
    }
    // N43 (LC16 2026-10-16): a spell from a Safe zone to a Dangerous spot opens the Dangerous-area warning first, like walking out
    // of the GE gate - before any rune is used (cancel = nothing lost); "Enter" casts the spell for real.
    val warning = gg.rsmod.plugins.content.mechanics.pvp.DangerWarning
    if (!isInterfaceVisible(warning.INTERFACE_ID) && warning.needsWarning(this, tile, endTile)) {
        val self = this
        queue(gg.rsmod.game.model.queue.TaskPriority.STRONG) {
            if (warning.confirm?.invoke(this, warning.BODY_TEXT, endTile) == true) {
                warning.allowNextCrossing(self)
                self.teleport(type, endTile, xp, data)
            }
        }
        return
    }

    // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
    // player's 7-second countdown complete this whole action automatically, instead of needing
    // one extra click (the one-arg overload's fallback behaviour).
    canTeleport(type) {
        // The countdown can outlive an inventory/equipment change. Re-check the complete
        // requirement set at execution time so a delayed callback cannot consume a rune or
        // ingredient that is no longer present.
        if (!MagicSpells.canCast(this, data.lvl, itemRequirements, data.sprite)) {
            return@canTeleport
        }
        MagicSpells.removeRunes(this, itemRequirements, data.sprite)
        teleport(endTile, type, startSound = false)
        if (!mobility) addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
        world.spawn(AreaSound(tile, SOUNDAREA_ID, SOUNDAREA_RADIUS, SOUNDAREA_VOLUME))
    }
}
