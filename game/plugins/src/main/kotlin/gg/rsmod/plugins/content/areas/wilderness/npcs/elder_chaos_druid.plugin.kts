package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 9.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 6607, doelvariant.
set_combat_def(npc = Npcs.ELDER_CHAOS_DRUID_OSRS) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.MAGIC
        respawnDelay = 25
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        alwaysAggro() // report: Elder Chaos druids never become tolerant
    }
    stats {
        hitpoints = 1500
        attack = 98
        strength = 98
        defence = 65
        magic = 110
        ranged = 1
    }
    bonuses {
        attackBonus = 0
        strengthBonus = 0
        attackMagic = 0
        magicDamageBonus = 0
        attackRanged = 0
        rangedStrengthBonus = 0
        defenceStab = 0
        defenceSlash = 0
        defenceCrush = 0
        defenceMagic = 0
        defenceRanged = 0
    }
    slayer { experience = 150.0 }
    anims {
        attack = 711
        block = 424
        death = 836
    }
}

val elderchaosdruidDrops = DropTableFactory.build {
    guaranteed {
        obj(Items.BONES, quantity = 1)
    }
    main {
        total(129)
        obj(Items.MITHRIL_BOLTS, 8..28, slots = if (WildernessNpcDrops.observatory(player)) 6 else 7)
        obj(Items.LAW_RUNE, quantity = 6, slots = 7)
        obj(Items.AIR_RUNE, quantity = 56, slots = 5)
        obj(Items.BODY_RUNE, quantity = 19, slots = 5)
        obj(Items.CHAOS_RUNE, quantity = 7, slots = 5)
        obj(Items.EARTH_RUNE, quantity = 19, slots = 5)
        obj(Items.MIND_RUNE, quantity = 22, slots = 5)
        obj(Items.NATURE_RUNE, quantity = 12, slots = 1)
        obj(Items.COINS_995, quantity = 80, slots = 7)
        obj(Items.COINS_995, quantity = 250, slots = 6)
        obj(Items.VIAL_OF_WATER, quantity = 4, slots = 10)
        obj(Items.STEEL_LONGSWORD, quantity = 1, slots = 5)
        nothing(2) // Missing item: Dark fishing bait; source quantity 10-24, chance 2/129.
        obj(Items.SNAPE_GRASS, quantity = 4, slots = 1)
        if (WildernessNpcDrops.observatory(player)) obj(Items.UNHOLY_MOULD, slots = 1)
        table(WildernessNpcDrops.druidHerbs, slots = 55)
        table(WildernessNpcDrops.rare, slots = 1, label = "Rare drop table")
        table(WildernessNpcDrops.gems, slots = 1, label = "Gem drop table")
        table(WildernessNpcDrops.druidRobes, slots = 1)
    }
    table("ensouled_chaos_druid_head") {
        total(20)
        nothing(1) // Missing item: Ensouled chaos druid head; source quantity 1, chance 1/20.
        nothing(19)
    }
    table("clue_scroll_hard") {
        val denominator = WildernessNpcDrops.clueDenominator(player, 128)
        total(denominator)
        obj(Items.CLUE_SCROLL_HARD, quantity = 1, slots = 1)
        nothing(denominator - 1)
    }
}
DropTableFactory.register(elderchaosdruidDrops, Npcs.ELDER_CHAOS_DRUID_OSRS)

on_npc_death(Npcs.ELDER_CHAOS_DRUID_OSRS) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
on_npc_spawn(15102) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
on_npc_combat(*intArrayOf(15102)) { npc.queue { WildernessNpcMechanics.druidCombat(this) } }
