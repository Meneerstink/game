package gg.rsmod.plugins.content.combat.scripts

import AberrantSpectreCombatScript
import gg.rsmod.plugins.content.areas.wilderness.Revenants
import gg.rsmod.plugins.content.combat.scripts.impl.*

/**
 * We can use this file to bind the combat scripts for the npcs.
 * Keeps them all in one place.
 * @author Kevin Senez <ksenez94@gmail.com>
 *
 * RCV-005 root cause: bosses whose hand-written script only chose and executed an attack (with placeholder
 * animations and no gfx, projectiles or sounds) are no longer bound here. They fight through the shared
 * data-driven attack model (NpcAttacks, Void `*.combat.toml`) in the generic combat cycle, and keep their real
 * mechanics as NpcAttacks hooks: Kalphite Queen, King Black Dragon, chromatic dragons, Dagannoth Kings and the
 * Chaos Elemental (confusion/madness, see chaos_elemental_attacks.plugin.kts).
 */

/**
 * Sets the [on_npc_combat] for Abyssal demons
 */
on_npc_combat(*AbyssalDemonCombatScript.ids) {
    npc.queue {
        AbyssalDemonCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Aberrant Spectres
 */
on_npc_combat(*AberrantSpectreCombatScript.ids) {
    npc.queue {
        AberrantSpectreCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Highwaymen
 */
on_npc_combat(*HighwaymanCombatScript.ids) {
    npc.queue {
        HighwaymanCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Cockatrices
 */
on_npc_combat(*CockatriceCombatScript.ids) {
    npc.queue {
        CockatriceCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Banshees
 */
on_npc_combat(*BansheeCombatScript.ids) {
    npc.queue {
        BansheeCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Bloodvelds
 */
on_npc_combat(*BloodveldCombatScript.ids) {
    npc.queue {
        BloodveldCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Canifis Citizens
 */
on_npc_combat(*CanifisCitizensCombatScript.ids) {
    npc.queue {
        CanifisCitizensCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Revenants
 */
on_npc_combat(*Revenants.ids) {
    npc.queue {
        Revenants.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Rock Crabs
 */
on_npc_combat(*RockCrabsCombatScript.ids) {
    npc.queue {
        RockCrabsCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for the Corporeal Beast
 */
on_npc_combat(*CorporealBeastCombatScript.ids) {
    npc.queue {
        CorporealBeastCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Tormented demons
 */
on_npc_combat(*TormentedDemonCombatScript.ids) {
    npc.queue {
        TormentedDemonCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for the Giant Mole
 */
on_npc_combat(*GiantMoleCombatScript.ids) {
    npc.queue {
        GiantMoleCombatScript.handleSpecialCombat(this)
    }
}

/*
 * Metallic dragons (bronze/iron/steel/mithril), skeletal wyverns and spinolyps: no script binding - they use
 * the shared data-driven attacks (Void metal_dragons / skeletal_wyvern / waterbirth_island combat tomls:
 * melee, close and long-range dragonfire, mithril magic/range, wyvern bite/tail whip/icy breath/ranged,
 * spinolyp water shot with impact poison). The mithril `ranged_only` condition is ported in
 * npc_attack_conditions.plugin.kts.
 */

/*
 * Frost dragons: no script binding - Void frost_dragon sections via the shared model, with the style/orb
 * mechanics in frost_dragon_attacks.plugin.kts.
 */

/**
 * Sets the [on_npc_combat] for the WildyWyrm
 */
on_npc_combat(*WildyWyrmCombatScript.ids) {
    npc.queue {
        WildyWyrmCombatScript.handleSpecialCombat(this)
    }
}
