package gg.rsmod.plugins.content.inter.magic

import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.Combat.DEFENSIVE_CAST_VARP
import gg.rsmod.plugins.content.combat.Combat.SELECTED_AUTOCAST_VARP
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.magic.MagicSpells

val FILTER_COMBAT_SPELLS_VARBIT = 6459
val FILTER_TELEPORT_SPELLS_VARBIT = 6462
val FILTER_MISC_SPELLS_VARBIT = 6461
val FILTER_SKILL_SPELLS_VARBIT = 6460

CombatSpell.definitions.values.filter { it.autoCastId != -1 }.forEach { spell ->
    on_button(interfaceId = spell.interfaceId, component = spell.componentId) {
        if (player.getVarp(SELECTED_AUTOCAST_VARP) == spell.autoCastId) {
            player.attr.remove(Combat.CASTING_SPELL)
            player.setVarp(SELECTED_AUTOCAST_VARP, 0)
            return@on_button
        }

        // Powered staves "cannot be used to autocast spells" (OSRS Wiki "Powered staff"); message ADAPTED.
        if (gg.rsmod.plugins.content.items.osrs.PoweredStaves.wielded(player) != null) {
            player.message(gg.rsmod.plugins.content.items.osrs.PoweredStaves.NO_AUTOCAST_MESSAGE)
            player.setVarp(SELECTED_AUTOCAST_VARP, 0)
            return@on_button
        }

        val metadata = MagicSpells.getMetadata(spell.uniqueId)
        if (metadata != null && MagicSpells.canCast(player, metadata.lvl, metadata.runes, spellId = spell.uniqueId)) {
            player.attr[Combat.CASTING_SPELL] = spell
            player.setVarp(SELECTED_AUTOCAST_VARP, spell.autoCastId)
        } else {
            player.setVarp(SELECTED_AUTOCAST_VARP, 0)
        }
    }
}

// Defensive Casting toggle: 192:2 standard, 193:18 Ancient Magicks, 430:20 Lunar (component ids
// decoded from the production cache; all three share CS2 hook script 1128).
listOf(192 to 2, 193 to 18, 430 to 20).forEach { (interfaceId, component) ->
    on_button(interfaceId = interfaceId, component = component) {
        if (player.getVarp(DEFENSIVE_CAST_VARP) > 0) {
            player.setVarp(DEFENSIVE_CAST_VARP, 0)
            return@on_button
        }
        player.setVarp(DEFENSIVE_CAST_VARP, 256)
    }
}

on_button(interfaceId = 192, 7) {
    player.toggleVarbit(FILTER_COMBAT_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 9) {
    player.toggleVarbit(FILTER_TELEPORT_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 11) {
    player.toggleVarbit(FILTER_MISC_SPELLS_VARBIT)
}

on_button(interfaceId = 192, 13) {
    player.toggleVarbit(FILTER_SKILL_SPELLS_VARBIT)
}
