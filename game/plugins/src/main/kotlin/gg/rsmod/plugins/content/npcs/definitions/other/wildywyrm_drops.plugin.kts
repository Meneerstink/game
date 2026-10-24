package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * WildyWyrm's drop table. Combat: `WildyWyrmCombatScript.kt`, spawned in `areas/wilderness/wildywyrm.plugin.kts`.
 *
 * Owner 2026-10-24 (N270): no Statius's equipment from the WildyWyrm any more (the Novite donor's eight 0.75 % rolls; the Ancient
 * Warriors' gear comes only from the Deadman's chest and the breaches) - it drops ordinary boss loot like the Glacor instead: big
 * bones every kill, then charms, coins, runes, ore, logs, a dragonstone and the Rare drop table (the Glacor's commons, glacor.plugin.kts).
 */
val WILDYWYRM = Npcs.WILDYWYRM

val wildyWyrmDrops =
    DropTableFactory.build {
        guaranteed { obj(Items.BIG_BONES) }
        main {
            total(512)
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
            table(gg.rsmod.plugins.content.drops.global.Rare.rareTable, slots = 20)
            nothing(132)
        }
    }

DropTableFactory.register(wildyWyrmDrops, WILDYWYRM)

on_npc_death(WILDYWYRM) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
