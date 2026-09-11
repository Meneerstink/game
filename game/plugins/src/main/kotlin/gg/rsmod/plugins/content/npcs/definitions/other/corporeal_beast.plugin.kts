package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.scripts.impl.CorporealBeastCombatScript
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems

/**
 * Corporeal Beast (8133) and Dark energy core (8127) definitions.
 *
 * Stats: Void corporeal_beasts_lair.npcs.toml (2,000 lifepoints, att/str 320, def 310, magic 350,
 * ranged 150, attack bonus 50, respawn 50 ticks, poison immune). Attack speed 4 and death delay from
 * the Novite combat definitions (8133: 10057 / 10386 / 10385, delay 4, death delay 3).
 * Drops: Void corporeal_beasts_lair.drops.toml (secondary roll 513, tertiary clue roll 512, charm
 * roll 1000) with the guaranteed big bones kept; item names resolved to their 667 ids.
 * The core's combat script and the beast's regeneration/core mechanics live in
 * [CorporealBeastCombatScript].
 */
val CORP = Npcs.CORPOREAL_BEAST
val CORE = CorporealBeastCombatScript.DARK_ENERGY_CORE
val table = DropTableFactory

val sigils =
    table.build {
        main {
            total(5)
            obj(Items.SPECTRAL_SIGIL, slots = 2)
            obj(Items.ARCANE_SIGIL, slots = 1)
            obj(Items.ELYSIAN_SIGIL, slots = 1)
            obj(Items.DIVINE_SIGIL, slots = 1)
        }
    }

val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        main {
            total(513)
            obj(Items.MYSTIC_AIR_STAFF, slots = 10)
            obj(Items.MYSTIC_WATER_STAFF, slots = 10)
            obj(Items.MYSTIC_EARTH_STAFF, slots = 10)
            obj(Items.MYSTIC_FIRE_STAFF, slots = 10)
            obj(Items.SPIRIT_SHIELD, slots = 8)
            obj(Items.HOLY_ELIXIR, slots = 3)
            obj(Items.ADAMANT_ARROW, quantity = 750, slots = 16)
            obj(Items.RUNITE_BOLTS, quantity = 250, slots = 25)
            obj(Items.CANNONBALL, quantity = 2000, slots = 17)
            obj(Items.MYSTIC_ROBE_TOP, slots = 18)
            obj(Items.MYSTIC_ROBE_BOTTOM, quantityRange = 1..2, slots = 18)
            obj(Items.REGEN_BRACELET, slots = 19)
            obj(Items.ONYX_BOLTS_E, quantity = 175, slots = 20)
            obj(Items.PURE_ESSENCE_NOTED, quantity = 2500, slots = 21)
            obj(Items.LAW_RUNE, quantity = 250, slots = 17)
            obj(Items.COSMIC_RUNE, quantity = 500, slots = 17)
            obj(Items.DEATH_RUNE, quantity = 300, slots = 17)
            obj(Items.SOUL_RUNE, quantity = 250, slots = 32)
            obj(Items.ADAMANTITE_ORE_NOTED, quantity = 125, slots = 17)
            obj(Items.RUNITE_ORE_NOTED, quantity = 20, slots = 12)
            obj(Items.ADAMANT_BAR_NOTED, quantity = 35, slots = 18)
            obj(Items.TEAK_PLANK_NOTED, quantity = 100, slots = 10)
            obj(Items.MAHOGANY_LOGS_NOTED, quantity = 150, slots = 12)
            obj(Items.MAGIC_LOGS_NOTED, quantity = 75, slots = 12)
            obj(Items.RAW_SHARK_NOTED, quantity = 70, slots = 21)
            obj(Items.WHITE_BERRIES_NOTED, quantity = 120, slots = 15)
            obj(Items.DESERT_GOAT_HORN_NOTED, quantity = 120, slots = 15)
            obj(Items.WATERMELON_SEED, quantity = 24, slots = 15)
            obj(Items.RANARR_SEED, quantityRange = 10..15, slots = 5)
            obj(Items.TUNA_POTATO, quantity = 30, slots = 21)
            obj(Items.GREEN_DRAGONHIDE_NOTED, quantity = 100, slots = 18)
            obj(Items.ANTIPOISON_4_NOTED_5953, quantity = 40, slots = 10)
            obj(Items.COINS_995, quantityRange = 20000..50000, slots = 10)
            obj(Items.CRYSTAL_KEY, slots = 1)
            table(sigils, slots = 1)
            table(Gems.gemTable, slots = 12)
        }

        table("tertiary") {
            total(512)
            obj(Items.CLUE_SCROLL_HARD, slots = 2)
            obj(Items.CLUE_SCROLL_ELITE, slots = 2)
            nothing(508)
        }

        table("charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 13, slots = 239)
            obj(Items.GREEN_CHARM, quantity = 13, slots = 119)
            obj(Items.CRIMSON_CHARM, quantity = 13, slots = 239)
            obj(Items.BLUE_CHARM, quantity = 13, slots = 382)
            nothing(21)
        }
    }

val coreDrops =
    table.build {
        guaranteed {
            obj(Items.ASHES)
        }
    }

table.register(drops, CORP)
table.register(coreDrops, CORE)

on_npc_spawn(npc = CORP) {
    CorporealBeastCombatScript.startRegeneration(npc)
}

on_npc_death(CORP) {
    CorporealBeastCombatScript.onBeastDeath(npc)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    table.getDrop(world, killer, npc.id, npc.tile)
}

on_npc_death(CORE) {
    CorporealBeastCombatScript.onCoreDeath(npc)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    table.getDrop(world, killer, npc.id, npc.tile)
}

set_combat_def(npc = CORP) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 50
        deathDelay = 3
        poisonImmune = true
    }
    stats {
        hitpoints = 20000
        attack = 320
        strength = 320
        defence = 310
        magic = 350
        ranged = 150
    }
    bonuses {
        attackBonus = 50
    }
    anims {
        attack = 10057
        block = 10386
        death = 10385
    }
    aggro {
        radius = 15
    }
}

set_combat_def(npc = CORE) {
    configs {
        attackSpeed = 2
        attackStyle = StyleType.MAGIC
        respawnDelay = 0
        deathDelay = 1
    }
    stats {
        hitpoints = 250
        attack = 1
        strength = 1
        defence = 20
        magic = 1
        ranged = 1
    }
    bonuses {
        defenceStab = 10
        defenceSlash = 10
        defenceCrush = 10
        defenceMagic = -5
        defenceRanged = 10
    }
    anims {
        death = 10391
    }
}
