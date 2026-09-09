package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * Corporeal Beast - very high HP, team-oriented boss.
 *
 * ponytail: the real "spirit shields drastically reduce damage dealt to it" mechanic is not
 * implemented (needs a verified incoming-damage-modifier hook this pass didn't confirm) -
 * see IMPLEMENTATION_STATUS.md FUTURE FINETUNING. Its very high HP already makes it
 * effectively a team encounter without that mechanic.
 */
val CORP = Npcs.CORPOREAL_BEAST
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        main {
            total(1500)
            obj(Items.COINS_995, quantityRange = 1000..8000, slots = 400)
            obj(Items.RUNITE_ORE, quantityRange = 2..5, slots = 100)
            obj(Items.SHARK, quantity = 10, slots = 100)

            // Sigils/shield chain - very rare
            obj(Items.SPIRIT_SHIELD, slots = 4)
            obj(Items.ARCANE_SIGIL, slots = 3)
            obj(Items.DIVINE_SIGIL, slots = 3)
            obj(Items.ELYSIAN_SIGIL, slots = 2)
            obj(Items.SPECTRAL_SIGIL, slots = 3)
            obj(Items.HOLY_ELIXIR, slots = 4)

            nothing(981)
        }
    }

table.register(drops, CORP)

on_npc_death(CORP) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = CORP) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 100
    }
    stats {
        hitpoints = 8000
        attack = 350
        strength = 400
        defence = 300
        magic = 300
        ranged = 300
    }
    bonuses {
        defenceStab = 100
        defenceSlash = 100
        defenceCrush = 100
        defenceMagic = 100
        defenceRanged = 100
    }
    anims {
        // melee 10057 (magic 10410, stomp 10496). Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 10057
        block = 10386
        death = 10385
    }
    aggro {
        radius = 15
    }
}
