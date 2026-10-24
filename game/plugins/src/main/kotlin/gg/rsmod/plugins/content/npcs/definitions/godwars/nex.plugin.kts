package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Nex, behind the Ancient Prison's Frozen Door. The fight itself (phases, minions, arena entry)
 * lives in areas/godwars/nex; this file holds her definition, drops and the minion definitions.
 * Stats: Matrix 718 NPCCombatDefinitions via the Novite donor (30,000 life points, 5-tick attacks).
 */
val NEX = Npcs.NEX
val NexMinionIds = intArrayOf(Npcs.FUMUS, Npcs.UMBRA, Npcs.CRUOR, Npcs.GLACIES)
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.COINS_995, quantityRange = 5000..25000)
        }

        main {
            // 2011 Nex drop table (RS wiki, Jan 2011 release): unique Torva/Zaryte at 1/384 per piece
            // (Darkan data/npcs/drops/nex.json: each unique its own 1/384 roll), ancient ceremonial robes,
            // and a supplies table otherwise.
            // Owner 2026-10-06: "remove pernix and virtus completely from our rsps" - their six 10-slot rows
            // are gone. total(3840) stays the roll denominator, so every remaining row keeps its exact rate
            // and the freed 60 slots are part of the declared-but-unfilled "nothing" tail.
            total(3840)
            obj(Items.TORVA_FULL_HELM, slots = 10)
            obj(Items.TORVA_PLATEBODY, slots = 10)
            obj(Items.TORVA_PLATELEGS, slots = 10)
            // Owner 2026-10-24 (N293): the Zaryte bow is gone from the game - no drop, no Drop Viewer line, no log slot.
            // Its 10 slots joined the unfilled tail, so every other row keeps its rate.
            // Owner 2026-10-24 (N269): the Zaryte crossbow drops whole, at the OSRS Nihil horn's 1/258 (15 / 3,840 = 1/256),
            // taken from the "nothing" tail so every other row keeps its rate.
            obj(Items.ZARYTE_CROSSBOW, slots = 15)
            obj(Items.ANCIENT_CEREMONIAL_MASK, slots = 40)
            obj(Items.ANCIENT_CEREMONIAL_TOP, slots = 40)
            obj(Items.ANCIENT_CEREMONIAL_LEGS, slots = 40)
            obj(Items.ANCIENT_CEREMONIAL_GLOVES, slots = 40)
            obj(Items.ANCIENT_CEREMONIAL_BOOTS, slots = 40)
            obj(Items.ONYX_BOLTS_E, quantityRange = 100..150, slots = 300)
            obj(Items.SOUL_RUNE, quantityRange = 150..200, slots = 300)
            obj(Items.BLOOD_RUNE, quantityRange = 100..200, slots = 300)
            obj(Items.DEATH_RUNE, quantityRange = 150..250, slots = 300)
            obj(Items.MAGIC_LOGS, quantityRange = 50..100, slots = 250)
            obj(Items.RUNITE_ORE, quantityRange = 10..20, slots = 250)
            obj(Items.SHARK, quantityRange = 10..20, slots = 300)
            obj(Items.GRIMY_TORSTOL, quantityRange = 5..10, slots = 250)
            obj(Items.MAGIC_SEED, quantityRange = 1..3, slots = 150)
            obj(Items.CRYSTAL_KEY, quantityRange = 1..2, slots = 150)
            obj(Items.AIR_BATTLESTAFF, quantityRange = 5..10, slots = 100)
            obj(Items.WATER_BATTLESTAFF, quantityRange = 5..10, slots = 100)
            table(Rare.rareTable, slots = 200)
            nothing(485)
        }
    }

table.register(drops, NEX)

on_npc_death(NEX) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

set_combat_def(npc = NEX) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.MAGIC
        respawnDelay = 200
    }
    // C4: Darkan data/npcs/combatdefs/uncategorized/nex.json combatLevels (ATTACK 315, STRENGTH 260, DEFENSE 260, MAGE 350,
    // RANGE 350); the defence bonuses below are not in that file and stay as they were.
    stats {
        hitpoints = 30000
        attack = 315
        strength = 260
        defence = 260
        magic = 350
        ranged = 350
    }
    bonuses {
        defenceStab = 120
        defenceSlash = 120
        defenceCrush = 120
        defenceMagic = 120
        defenceRanged = 120
    }
    anims {
        // Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 6354
        block = 6983
        death = 6951
    }
    aggro {
        radius = 15
    }
}


/**
 * Fumus, Umbra, Cruor and Glacies. Lifepoints/stats from the Matrix 718 definitions (Novite donor);
 * the minions share Nex's model rig and therefore her animation set.
 */
NexMinionIds.forEach { id ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = 5
            attackStyle = StyleType.MAGIC
            respawnDelay = 150
        }
        stats {
            hitpoints = 6000
            attack = 147
            strength = 155
            defence = 147
            magic = 147
            ranged = 1
        }
        bonuses {
            defenceStab = 60
            defenceSlash = 60
            defenceCrush = 60
            defenceMagic = 60
            defenceRanged = 60
        }
        anims {
            attack = 6986
            block = 6983
            death = 6951
        }
        aggro {
            radius = 0
        }
    }
}
