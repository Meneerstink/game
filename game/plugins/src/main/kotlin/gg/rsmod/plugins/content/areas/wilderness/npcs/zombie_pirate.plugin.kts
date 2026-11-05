package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness

/** N522 part 5. Source: team/dossiers/N522-NPCS-ONDERZOEK.txt, section 4.
 * HP uses the DSL's x10 unit. Animations: task-card imports/667 human roles.
 * Unresolved source fields are recorded in N522.txt, never guessed here.
 */

// OSRS 13489, Level 22.
set_combat_def(npc = Npcs.ZOMBIE_PIRATE_OSRS) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        // SOURCE GAP: report gives no pirate respawn. Disable automatic respawn until sourced.
        respawnDelay = 0
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 300
        attack = 20
        strength = 10
        defence = 20
        magic = 1
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
    species { +NpcSpecies.UNDEAD }
    slayer { experience = 30.0 }
    anims {
        attack = 15698
        block = 15697
        death = 15699
    }
}
ElementalWeakness.idOverrides[Npcs.ZOMBIE_PIRATE_OSRS] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 50)

// OSRS 13490, Level 28.
set_combat_def(npc = Npcs.ZOMBIE_PIRATE_OSRS_28_A) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        // SOURCE GAP: report gives no pirate respawn. Disable automatic respawn until sourced.
        respawnDelay = 0
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 350
        attack = 30
        strength = 15
        defence = 20
        magic = 1
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
    species { +NpcSpecies.UNDEAD }
    slayer { experience = 35.0 }
    anims {
        attack = 15698
        block = 15697
        death = 15699
    }
}
ElementalWeakness.idOverrides[Npcs.ZOMBIE_PIRATE_OSRS_28_A] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 50)

// OSRS 13491, Level 28.
set_combat_def(npc = Npcs.ZOMBIE_PIRATE_OSRS_28_B) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        // SOURCE GAP: report gives no pirate respawn. Disable automatic respawn until sourced.
        respawnDelay = 0
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 350
        attack = 30
        strength = 15
        defence = 20
        magic = 1
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
    species { +NpcSpecies.UNDEAD }
    slayer { experience = 35.0 }
    anims {
        attack = 15698
        block = 15697
        death = 15699
    }
}
ElementalWeakness.idOverrides[Npcs.ZOMBIE_PIRATE_OSRS_28_B] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 50)

// OSRS 13492, Level 34.
set_combat_def(npc = Npcs.ZOMBIE_PIRATE_OSRS_34_A) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        // SOURCE GAP: report gives no pirate respawn. Disable automatic respawn until sourced.
        respawnDelay = 0
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 400
        attack = 40
        strength = 20
        defence = 20
        magic = 1
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
    species { +NpcSpecies.UNDEAD }
    slayer { experience = 40.0 }
    anims {
        attack = 15698
        block = 15697
        death = 15699
    }
}
ElementalWeakness.idOverrides[Npcs.ZOMBIE_PIRATE_OSRS_34_A] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 50)

// OSRS 13493, Level 34.
set_combat_def(npc = Npcs.ZOMBIE_PIRATE_OSRS_34_B) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.CRUSH
        // SOURCE GAP: report gives no pirate respawn. Disable automatic respawn until sourced.
        respawnDelay = 0
    }
    aggro {
        radius = 8 // existing server search radius; exact OSRS radius is not in the report
        aggroTimer = 1000 // shared server 10-minute tolerance policy
    }
    stats {
        hitpoints = 400
        attack = 40
        strength = 20
        defence = 20
        magic = 1
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
    species { +NpcSpecies.UNDEAD }
    slayer { experience = 40.0 }
    anims {
        attack = 15698
        block = 15697
        death = 15699
    }
}
ElementalWeakness.idOverrides[Npcs.ZOMBIE_PIRATE_OSRS_34_B] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 50)

val pirateDrops = DropTableFactory.build {
    guaranteed {
        obj(WildernessNpcDrops.pirateItem(player, Items.BONES), quantity = 1)
    }
    main {
        val medium = WildernessNpcDrops.mediumDiary(player)
        total(if (medium) 378 else 1260)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLOOD_RUNE), quantityRange = 30..60, slots = 4)
        obj(WildernessNpcDrops.pirateItem(player, Items.DEATH_RUNE), quantityRange = 30..90, slots = 4)
        obj(WildernessNpcDrops.pirateItem(player, Items.CHAOS_RUNE), quantityRange = 30..90, slots = 4)
        obj(WildernessNpcDrops.pirateItem(player, Items.MIND_RUNE), quantityRange = 30..90, slots = 4)
        obj(WildernessNpcDrops.pirateItem(player, Items.BATTLESTAFF), quantityRange = 1..3, slots = 8)
        obj(WildernessNpcDrops.pirateItem(player, Items.ADAMANT_PLATEBODY), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_MED_HELM), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_WARHAMMER), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_BATTLEAXE), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_LONGSWORD), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_SWORD), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.RUNE_MACE), quantity = 1, slots = 6)
        obj(WildernessNpcDrops.pirateItem(player, Items.DRAGON_DAGGER), quantity = 1, slots = 1)
        obj(WildernessNpcDrops.pirateItem(player, Items.DRAGON_LONGSWORD), quantity = 1, slots = 1)
        obj(WildernessNpcDrops.pirateItem(player, Items.DRAGON_SCIMITAR), quantity = 1, slots = 1)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLIGHTED_ANCIENT_ICE_SACK), quantityRange = 10..30, slots = 12)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLIGHTED_ANGLERFISH_NOTED), quantityRange = 5..15, slots = 12)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLIGHTED_MANTA_RAY_NOTED), quantityRange = 5..15, slots = 12)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLIGHTED_KARAMBWAN_NOTED), quantityRange = 5..15, slots = 12)
        obj(WildernessNpcDrops.pirateItem(player, Items.BLIGHTED_SUPER_RESTORE4_NOTED), quantityRange = 1..3, slots = 12)
        nothing(if (medium) 189 else 1071)
        obj(WildernessNpcDrops.pirateItem(player, Items.COINS_995), quantityRange = 1000..8000, slots = 12)
        nothing(16) // Missing item: Zombie pirate key; source quantity 1, chance 16/1260.
        obj(WildernessNpcDrops.pirateItem(player, Items.ADAMANT_SEEDS), quantityRange = 5..10, slots = 8)
        obj(WildernessNpcDrops.pirateItem(player, Items.CANNONBALL), quantityRange = 20..100, slots = 12)
        obj(WildernessNpcDrops.pirateItem(player, Items.GOLD_ORE_NOTED), quantityRange = 5..15, slots = 12)
    }
    table("zombie_champion_scroll") {
        total(5000)
        nothing(1) // Missing item: Zombie champion scroll; source quantity 1, chance 1/5000.
        nothing(4999)
    }
}
DropTableFactory.register(pirateDrops, Npcs.ZOMBIE_PIRATE_OSRS, Npcs.ZOMBIE_PIRATE_OSRS_28_A, Npcs.ZOMBIE_PIRATE_OSRS_28_B, Npcs.ZOMBIE_PIRATE_OSRS_34_A, Npcs.ZOMBIE_PIRATE_OSRS_34_B)

on_npc_death(Npcs.ZOMBIE_PIRATE_OSRS, Npcs.ZOMBIE_PIRATE_OSRS_28_A, Npcs.ZOMBIE_PIRATE_OSRS_28_B, Npcs.ZOMBIE_PIRATE_OSRS_34_A, Npcs.ZOMBIE_PIRATE_OSRS_34_B) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
// N522 lifecycle wiring
listOf(15091, 15092, 15093, 15094, 15095).forEach { id ->
    on_npc_spawn(id) {
    val spawned = npc
    // Global spawn installs default aggro AFTER local hooks; override on the following cycle.
    spawned.world.queue { wait(1); if (spawned.isSpawned() && !spawned.isDead()) WildernessNpcMechanics.setupAggro(spawned) }
}
}
