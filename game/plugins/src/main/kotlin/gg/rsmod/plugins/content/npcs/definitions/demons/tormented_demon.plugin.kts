package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Tormented Demon (already spawned in the Forinthry Dungeon area, see areas/spawns).
 *
 * ponytail: the real "off-hand elemental weakness cycling" mechanic (needing an
 * elemental/Ice-Strykewyrm-style weapon to switch its weakness and avoid heavy retaliation
 * damage) is not implemented - it fights as a straightforward hard hitter instead. See
 * IMPLEMENTATION_STATUS.md.
 */
val TD = Npcs.TORMENTED_DEMON
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        main {
            total(1200)
            obj(Items.COINS_995, quantityRange = 500..4000, slots = 300)
            obj(Items.RUNITE_ORE, quantityRange = 1..3, slots = 80)
            obj(Items.DEATH_RUNE, quantity = 60, slots = 80)
            obj(Items.BLOOD_RUNE, quantity = 40, slots = 60)
            obj(Items.MAGIC_LOGS, quantity = 20, slots = 60)

            table(Gems.gemTable, slots = 60)
            table(Rare.rareTable, slots = 60)

            obj(Items.DRACONIC_VISAGE, slots = 2)

            nothing(558)
        }
    }

table.register(drops, TD)

on_npc_death(TD) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = TD) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.SLASH
        respawnDelay = 40
    }
    stats {
        hitpoints = 1200
        attack = 300
        strength = 300
        defence = 250
        magic = 300
        ranged = 1
    }
    bonuses {
        defenceStab = 70
        defenceSlash = 70
        defenceCrush = 70
        defenceMagic = 70
        defenceRanged = 70
    }
    anims {
        // melee 10922 (magic 10918 / ranged 10919 in the script). Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 10922
        block = 10923
        death = 10917
    }
    aggro {
        radius = 8
    }
}
