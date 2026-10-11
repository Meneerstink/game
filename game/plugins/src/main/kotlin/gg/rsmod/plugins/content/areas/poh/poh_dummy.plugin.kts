package gg.rsmod.plugins.content.areas.poh

/*
 * The ornate combat dummy's combat definitions (owner 2026-10-10: "is dit dezelfde dummy kwa werking net als die van osrs").
 *
 * Stats are the OSRS npc definitions' own (OsrsNpcImportTool dropped them as "stat 3 = 1000, stat 5 = 500": hitpoints 1000,
 * Magic 500 - the Magic level the twisted bow and other target-Magic weapons scale on; every other level 1). The dummy never
 * fights back and never dies ([PlayerHouse.spawnDummy]); every attack lands for the attacker's max hit (PawnExt.dealHit),
 * gives no experience and always loses its ammunition (OSRS Wiki "Combat dummy").
 *
 * What each variant counts as (OSRS Wiki "Ornate undead combat dummy"), as far as this server models it:
 * - undead (10507): undead and demon (salve amulet, demonbane weapons); every variant counts as the player's Slayer task
 *   ("functions as any slayer task", TargetModifiers.isCurrentSlayerTask);
 * - wilderness (10508): a Wilderness npc for the revenant weapons (PlayerHouse.isWildernessDummy);
 * - kalphite (10509): kalphite (keris);
 * - dragon (10512): draconic (dragonbane weapons);
 * - kurask (10510) and vampyre (10511): this server has no leaf-bladed-only or vampyre rules, so they behave as the
 *   standard dummy (8598).
 */
PlayerHouse.Dummy.values().forEach { variant ->
    set_combat_def(variant.npc) {
        configs {
            attackSpeed = 4
            respawnDelay = 0
        }
        species {
            when (variant) {
                PlayerHouse.Dummy.UNDEAD -> {
                    +NpcSpecies.UNDEAD
                    +NpcSpecies.DEMON
                }
                PlayerHouse.Dummy.KALPHITE -> +NpcSpecies.KALPHITE
                PlayerHouse.Dummy.DRAGON -> +NpcSpecies.DRAGON
                else -> {}
            }
        }
        stats {
            hitpoints = 10_000
            attack = 1
            strength = 1
            defence = 1
            magic = 500
            ranged = 1
        }
        // The OSRS dummies have no sequences at all (the import found none); the dummy never dies, so -1 is never played.
        anims {
            attack = -1
            block = -1
            death = -1
        }
    }
}
