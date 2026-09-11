package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * WildyWyrm's drop table. Combat already exists (`WildyWyrmCombatScript.kt`, spawned in
 * `areas/wilderness/wildywyrm.plugin.kts`) but had no drop table anywhere - real gap recorded
 * under Q-042, closed here.
 *
 * Source: Novite `data/json/drops.json`, entry `"name": "WildyWyrm"` (line ~71386) - the only
 * donor with any WildyWyrm loot data at all (Void has none; grepped case-insensitive for
 * "wildywyrm"/"wilderness.*wyrm" across the whole Void tree, no hits). Eight items, each an
 * independent `CUSTOM_RARE` roll. `CUSTOM_RARE`'s exact figure comes from Novite's own
 * `utility/game/npc/drops/Drop.java` `Chance` enum: `CUSTOM_RARE(0.75)` - a flat 0.75% per item,
 * not a shared weighted table. Modelled here as eight independently-named tables inside one
 * registration (this codebase's `getDrop` sums every non-guaranteed table registered for an npc
 * id - the same "roll every named table" technique `chaos_elemental_drops.plugin.kts` already
 * uses for its secondary/minor/elite_clue split - so N independent named tables reproduce N
 * independent rolls; a table registration is one-per-npc-id, so all eight must live in a single
 * `DropTableFactory.build` block, not eight separate `register` calls). Each table is
 * `total(10000)`/`slots=75` - exactly 0.75% (75 in 10,000), not an invented approximation.
 *
 * All eight item ids verified present and correctly named in this project's own generated
 * `Items.kt` (Statius's and Corrupt Statius's full 4-piece sets: platebody, platelegs, full helm,
 * warhammer) - the classic "Ancient Warriors' equipment" drop mechanic, native to this cache.
 */
val WILDYWYRM = Npcs.WILDYWYRM

val CUSTOM_RARE_SLOTS = 75
val CUSTOM_RARE_TOTAL = 10000

val wildyWyrmDrops =
    DropTableFactory.build {
        table("statiuss_platebody") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.STATIUSS_PLATEBODY, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("statiuss_platelegs") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.STATIUSS_PLATELEGS, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("statiuss_full_helm") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.STATIUSS_FULL_HELM, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("statiuss_warhammer") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.STATIUSS_WARHAMMER, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("corrupt_statiuss_platebody") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.CORRUPT_STATIUSS_PLATEBODY, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("corrupt_statiuss_platelegs") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.CORRUPT_STATIUSS_PLATELEGS, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("corrupt_statiuss_full_helm") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.CORRUPT_STATIUSS_FULL_HELM, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
        table("corrupt_statiuss_warhammer") {
            total(CUSTOM_RARE_TOTAL)
            obj(Items.CORRUPT_STATIUSS_WARHAMMER, slots = CUSTOM_RARE_SLOTS)
            nothing(CUSTOM_RARE_TOTAL - CUSTOM_RARE_SLOTS)
        }
    }

DropTableFactory.register(wildyWyrmDrops, WILDYWYRM)

on_npc_death(WILDYWYRM) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
