package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.scripts.impl.GlacorCombatScript
import gg.rsmod.plugins.content.combat.scripts.impl.GlacyteCombatScript
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Glacor and glacytes (Glacor Cave, Nov 2011). Stats/anims: Matrix 718 via the Novite donor
 * (5,000 life points, 6-tick attacks, anims 9955/9962/9961). Drops: 2011 wiki table
 * (Ragefire/Steadfast/Glaiven boots, shard of Armadyl, charms and runes).
 */
val GLACOR = Npcs.GLACOR
val table = DropTableFactory

val glacorDrops = table.build {
    guaranteed { obj(Items.BIG_BONES) }
    main {
        total(512)
        obj(Items.RAGEFIRE_BOOTS, slots = 1)
        obj(Items.STEADFAST_BOOTS, slots = 1)
        obj(Items.GLAIVEN_BOOTS, slots = 1)
        obj(Items.SHARDS_OF_ARMADYL, slots = 2)
        obj(Items.CRIMSON_CHARM, quantityRange = 1..3, slots = 60)
        obj(Items.BLUE_CHARM, quantityRange = 1..2, slots = 30)
        obj(Items.GREEN_CHARM, quantityRange = 1..3, slots = 40)
        obj(Items.COINS_995, quantityRange = 2000..10000, slots = 80)
        obj(Items.BLOOD_RUNE, quantityRange = 30..60, slots = 30)
        obj(Items.SOUL_RUNE, quantityRange = 20..40, slots = 30)
        obj(Items.LAW_RUNE, quantityRange = 30..60, slots = 30)
        obj(Items.RUNITE_ORE, quantityRange = 1..3, slots = 25)
        obj(Items.MAGIC_LOGS, quantityRange = 20..40, slots = 25)
        obj(Items.UNCUT_DRAGONSTONE, slots = 10)
        obj(Items.WHITE_BERRIES, quantityRange = 5..10, slots = 20)
        table(Rare.rareTable, slots = 20)
        nothing(67)
    }
}
table.register(glacorDrops, GLACOR)

on_npc_death(GLACOR) {
    GlacorCombatScript.reset(npc)
    table.getDrop(world, npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death, npc.id, npc.tile)
}

on_npc_spawn(GLACOR) {
    GlacorCombatScript.reset(npc)
}

GlacorCombatScript.GLACYTE_IDS.forEach { id ->
    on_npc_death(id) {
        GlacorCombatScript.onGlacyteDeath(npc)
    }
}

set_combat_def(npc = GLACOR) {
    configs {
        attackSpeed = 6
        attackStyle = StyleType.CRUSH
        respawnDelay = 60
    }
    stats {
        hitpoints = 5000
        attack = 448
        strength = 285
        defence = 448
        magic = 300
        ranged = 300
    }
    bonuses {
        defenceStab = 80
        defenceSlash = 80
        defenceCrush = 80
        defenceMagic = 40
        defenceRanged = 80
    }
    anims {
        attack = 9955
        block = 9962
        death = 9961
    }
    aggro {
        radius = 0
    }
}

GlacorCombatScript.GLACYTE_IDS.forEach { id ->
    set_combat_def(npc = id) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.CRUSH
            respawnDelay = 0 // glacytes never respawn (engine: respawns = respawnDelay > 0); -1 fails NpcCombatBuilder check
        }
        stats {
            hitpoints = 1000
            attack = 150
            strength = 150
            defence = 150
            magic = 1
            ranged = 1
        }
        anims {
            attack = 9955
            block = 9962
            death = 9961
        }
        aggro {
            // RuneScape Wiki 2012 revisions: unstable and sapping glacytes "aggressive = Yes", enduring glacyte "aggressive = No".
            radius = if (id == Npcs.ENDURING_GLACYTE) 0 else 8
        }
    }
}

on_npc_combat(*GlacorCombatScript.ids) {
    npc.queue { GlacorCombatScript.handleSpecialCombat(this) }
}

on_npc_combat(*GlacyteCombatScript.ids) {
    npc.queue { GlacyteCombatScript.handleSpecialCombat(this) }
}
