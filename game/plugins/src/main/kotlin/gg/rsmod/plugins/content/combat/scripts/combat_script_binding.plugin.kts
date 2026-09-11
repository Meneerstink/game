package gg.rsmod.plugins.content.combat.scripts

import AberrantSpectreCombatScript
import gg.rsmod.plugins.content.areas.wilderness.Revenants
import gg.rsmod.plugins.content.combat.scripts.impl.*

/**
 * We can use this file to bind the combat scripts for the npcs.
 * Keeps them all in one place.
 * @author Kevin Senez <ksenez94@gmail.com>
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
 * Sets the [on_npc_combat] for Regular Dragons
 */
on_npc_combat(*DragonCombatScript.ids) {
    npc.queue {
        DragonCombatScript.handleSpecialCombat(this)
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
 * Sets the [on_npc_combat] for KBD
 */
on_npc_combat(*KingBlackDragonCombatScript.ids) {
    npc.queue {
        KingBlackDragonCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Kalphite Queen (both forms)
 */
on_npc_combat(*KalphiteQueenCombatScript.ids) {
    npc.queue {
        KalphiteQueenCombatScript.handleSpecialCombat(this)
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
 * Sets the [on_npc_combat] for the Chaos Elemental
 */
on_npc_combat(*ChaosElementalCombatScript.ids) {
    npc.queue {
        ChaosElementalCombatScript.handleSpecialCombat(this)
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

/**
 * Sets the [on_npc_combat] for the Dagannoth Kings
 */
on_npc_combat(*DagannothKingsCombatScript.ids) {
    npc.queue {
        DagannothKingsCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for metallic dragons (bronze, iron, steel, mithril)
 */
on_npc_combat(*MetalDragonCombatScript.ids) {
    npc.queue {
        MetalDragonCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Frost dragons
 */
on_npc_combat(*FrostDragonCombatScript.ids) {
    npc.queue {
        FrostDragonCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Skeletal wyverns
 */
on_npc_combat(*SkeletalWyvernCombatScript.ids) {
    npc.queue {
        SkeletalWyvernCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for Spinolyps
 */
on_npc_combat(*SpinolypCombatScript.ids) {
    npc.queue {
        SpinolypCombatScript.handleSpecialCombat(this)
    }
}

/**
 * Sets the [on_npc_combat] for the WildyWyrm
 */
on_npc_combat(*WildyWyrmCombatScript.ids) {
    npc.queue {
        WildyWyrmCombatScript.handleSpecialCombat(this)
    }
}
