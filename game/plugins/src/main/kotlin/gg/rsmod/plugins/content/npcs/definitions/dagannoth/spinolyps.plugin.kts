package gg.rsmod.plugins.content.npcs.definitions.dagannoth

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.scripts.impl.SpinolypCombatScript
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * Spinolyps (Waterbirth Island dungeon, 2892 / 2894 / 2896).
 *
 * Void waterbirth_island.npcs.toml: 100 lifepoints, att/str/def 10, ranged 100, aggressive with a hunt
 * range of 10, respawn 10 ticks, bones drop. Anims 2868 / 2869 / 2866 (Void anims + Novite combat
 * definitions). Attack speed 4 from the Novite/OSRS-sourced bulk row. Attack logic: [SpinolypCombatScript].
 */
val table = DropTableFactory

val spinolypDrops =
    table.build {
        guaranteed {
            obj(Items.BONES)
        }
    }

SpinolypCombatScript.ids.forEach { id ->
    table.register(spinolypDrops, id)

    on_npc_death(id) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        table.getDrop(world, killer, npc.id, npc.tile)
    }

    set_combat_def(npc = id) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.RANGED
            respawnDelay = 10
            deathDelay = 1
        }
        stats {
            hitpoints = 1000
            attack = 10
            strength = 10
            defence = 10
            magic = 1
            ranged = 100
        }
        anims {
            attack = 2868
            block = 2869
            death = 2866
        }
        aggro {
            radius = 10
        }
    }
}
