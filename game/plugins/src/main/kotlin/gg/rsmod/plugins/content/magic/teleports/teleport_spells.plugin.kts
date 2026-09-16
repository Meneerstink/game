package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.collision.ObjectType
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
    val endTile = findValidTile(spell)
    teleport(spell.type, endTile, spell.xp, data)
}

fun Player.findValidTile(spell: TeleportSpell): Tile {
    var tile = spell.endArea.randomTile
    while (world.getObject(tile, ObjectType.INTERACTABLE) != null) {
        tile = spell.endArea.randomTile
    }
    return tile
}

fun Player.teleport(
    type: TeleportType,
    endTile: Tile,
    xp: Double,
    data: SpellMetadata,
) {
    val itemRequirements = RuneFreeTeleportRequirements.nonRuneRequirements(data.runes)
 if (!MagicSpells.canCast(this, data.lvl, itemRequirements)) {
        return
    }

    // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
    // player's 7-second countdown complete this whole action automatically, instead of needing
    // one extra click (the one-arg overload's fallback behaviour).
    canTeleport(type) {
        MagicSpells.removeRunes(this, itemRequirements, data.sprite)
        teleport(endTile, type)
        addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
        world.spawn(AreaSound(tile, SOUNDAREA_ID, SOUNDAREA_RADIUS, SOUNDAREA_VOLUME))
    }
}
