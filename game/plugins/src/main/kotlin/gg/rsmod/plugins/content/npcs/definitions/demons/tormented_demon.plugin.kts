package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.scripts.impl.TormentedDemonCombatScript
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.VoidDropTables
import java.io.File

/**
 * Tormented demons, every revision-667 cache id 8349-8369 (7 demons x 3 prayer variants).
 *
 * RCV-012 B7 (owner decisions 2026-09-13): drops are Void's guthix_temple tables ([TormentedDemonDrops], replacing the
 * invented table); the attack style rotates on Void's timer ([TormentedDemonCombatScript.switchStyle]); attack speed 4 and
 * respawn 21 follow Void. Combat mechanics live in [TormentedDemonCombatScript].
 */
on_world_init {
    TormentedDemonDrops.register(VoidDropTables.load(File(TormentedDemonDrops.PATH)))
}

TormentedDemonDrops.NPC_IDS.forEach { id ->
    on_npc_death(id) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    }
    on_npc_spawn(npc = id) {
        TormentedDemonCombatScript.onSpawn(npc)
    }
}

on_timer(TormentedDemonCombatScript.STYLE_TIMER) {
    if (npc.id in TormentedDemonCombatScript.ids) {
        TormentedDemonCombatScript.switchStyle(npc)
    }
}

// RCV-012 B7: one definition for every cache demon id 8349-8369 (was 8349 only). HP 3260 (x10 DSL unit, real 326):
// Novite unpackedCombatDefinitionsList (all ids) and Void guthix_temple.npcs.toml agree; the old 1200 gave 120 HP.
// Levels from Void guthix_temple.npcs.toml (att/str/mage/range 255, def 150; Novite has no level data).
// Owner decision 5 ("you choose"): Void attack speed 4 and respawn 21.
TormentedDemonCombatScript.ids.forEach { demonId ->
    set_combat_def(npc = demonId) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.SLASH
            respawnDelay = 21
        }
        stats {
            hitpoints = 3260
            attack = 255
            strength = 255
            defence = 150
            magic = 255
            ranged = 255
        }
        bonuses {
            defenceStab = 70
            defenceSlash = 70
            defenceCrush = 70
            defenceMagic = 70
            defenceRanged = 70
        }
        anims {
            attack = 10922
            block = 10923
            death = TormentedDemonCombatScript.DEATH_ANIM
        }
        aggro {
            radius = 8
        }
    }
}
