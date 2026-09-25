package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.SpellMetadata

if (!MagicSpells.isLoaded()) {
    MagicSpells.loadSpellRequirements()
}

MagicSpells.getCombatSpells().forEach { entry ->
    val requirement = entry.value

    on_spell_on_npc(requirement.interfaceId, requirement.component) {
        castCombatSpellOnPawn(player, player.getInteractingNpc(), requirement)
    }

    on_spell_on_player(requirement.interfaceId, requirement.component) {
        castCombatSpellOnPawn(player, player.getInteractingPlayer(), requirement)
    }
}

fun castCombatSpellOnPawn(
    player: Player,
    pawn: Pawn,
    spellMetadata: SpellMetadata,
) {
    val combatSpell = CombatSpell.values.firstOrNull { spell -> spell.uniqueId == spellMetadata.sprite }
    if (combatSpell != null) {
        // Manual cast: one cast through the shared engine; the saved autocast choice is untouched.
        gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(player, combatSpell, pawn)
        player.attack(pawn)
    } else {
        /*
         * The spell is not defined in [CombatSpell].
         */
        // Audit S-12: debug output only for staff with the dev power.
        if (world.devContext.debugMagicSpells && world.privileges.isEligible(player.privilege, gg.rsmod.game.model.priv.Privilege.DEV_POWER)) {
            player.message("Undefined combat spell: [spellId=${spellMetadata.sprite}, name=${spellMetadata.name}]")
        }
    }
}
