package gg.rsmod.plugins.content.npcs.definitions.dragons

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems

/** Bronze dragon - already spawned (see areas/spawns/spawns_12630) but had no combat def. */
val BRONZE_DRAGON = Npcs.BRONZE_DRAGON
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.DRAGON_BONES)
        }

        main {
            total(400)
            obj(Items.COINS_995, quantityRange = 100..900, slots = 150)
            obj(Items.RUNITE_ORE, slots = 20)
            obj(Items.ADAMANT_BAR, slots = 40)
            obj(Items.MITHRIL_ORE, quantity = 5, slots = 40)
            obj(Items.LAW_RUNE, quantity = 12, slots = 40)
            table(Gems.gemTable, slots = 20)
            nothing(90)
        }
    }

table.register(drops, BRONZE_DRAGON)

on_npc_death(BRONZE_DRAGON) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = BRONZE_DRAGON) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.STAB
        respawnDelay = 30
    }
    stats {
        hitpoints = 60
        attack = 25
        strength = 25
        defence = 30
        magic = 1
        ranged = 1
    }
    bonuses {
        defenceStab = 10
        defenceSlash = 15
        defenceCrush = 15
        defenceMagic = 10
        defenceRanged = 10
    }
    anims {
        // metal dragon melee 13158. Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 13158
        block = 13163
        death = 13159
    }
    aggro {
        radius = 3
    }
}
