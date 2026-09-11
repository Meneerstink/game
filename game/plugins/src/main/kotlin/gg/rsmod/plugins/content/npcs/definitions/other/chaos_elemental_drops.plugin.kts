package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Herbs
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * Q-041: Chaos Elemental's drop table. Combat behaviour already exists
 * (`ChaosElementalCombatScript.kt`, spawned in `spawns_12861.plugin.kts`) but had no drop table at
 * all - real gap. Sourced from Void's real production `data/area/wilderness/wilderness.drops.toml`
 * (`chaos_elemental_drop_table`/`_secondary`/`_minor`), which is `type = "all"`: the secondary
 * table, the minor table and an elite clue chance are each rolled independently and every hit is
 * kept. This factory rolls every named table registered for an npc and combines the results
 * (`DropTableBuilder.build()` returns all of `tables.values`, and `getDrop` sums every
 * non-"guaranteed" one) - the same "all" semantics, replicated with three distinctly-named
 * `table(...)` blocks in one registration rather than three separate registrations (which would
 * silently overwrite each other, since a table registration is one-per-npc-id).
 *
 * Disclosed departures from the source, not guesses:
 *  - `weapon_poison++` has no id in this 667 cache (a later-era item) - its 20/256 secondary
 *    slots become `nothing(20)` rather than a substituted item.
 *  - the secondary table's 14 listed entries only sum to 240/256; the remaining 16 are an
 *    explicit `nothing(16)`, the same "remainder is empty" convention this file's own sibling
 *    `king_black_dragon_level_276.plugin.kts` uses (`nothing(9614)`).
 *  - the minor table's `dragon_pickaxe` entry carries no explicit chance in the source; its
 *    10 listed chance=25 entries sum to 250/256, so it fills the remaining 6 as "whatever's left"
 *    rather than an invented number.
 *  - `herb_drop_table` has no exact same-named table in target; `Herbs.minorHerbTable` is the
 *    closest existing sourced equivalent (adapt, don't duplicate a near-identical table).
 */
val CHAOS_ELEMENTAL = Npcs.CHAOS_ELEMENTAL

val chaosElementalDrops =
    DropTableFactory.build {
        table("secondary") {
            total(256)
            obj(Items.DRAGON_DAGGER, quantity = 1, slots = 22)
            obj(Items.DRAGON_2H_SWORD, quantity = 1, slots = 2)
            obj(Items.AIR_RUNE, quantity = 500, slots = 20)
            obj(Items.BLOOD_RUNE, quantity = 75, slots = 20)
            obj(Items.CHAOS_RUNE, quantity = 250, slots = 20)
            obj(Items.DEATH_RUNE, quantity = 125, slots = 20)
            obj(Items.MITHRIL_DART, quantity = 300, slots = 20)
            obj(Items.RUNE_ARROW, quantity = 150, slots = 20)
            table(Herbs.minorHerbTable, slots = 8)
            obj(Items.STRANGE_FRUIT_NOTED, quantity = 10, slots = 26)
            obj(Items.ANTIPOISON_4, quantity = 1, slots = 20)
            nothing(20) // weapon_poison++ - no id in this cache era, see file header
            obj(Items.COINS_995, quantity = 7500, slots = 16)
            table(Rare.rareTable, slots = 6)
            nothing(16) // source's 240/256 remainder
        }
        table("minor") {
            total(256)
            obj(Items.ANCHOVY_PIZZA, quantity = 3, slots = 25)
            obj(Items.BABYDRAGON_BONES, quantity = 2, slots = 25)
            obj(Items.BAT_BONES, quantity = 5, slots = 25)
            obj(Items.BIG_BONES, quantity = 3, slots = 25)
            obj(Items.BONES, quantity = 4, slots = 25)
            obj(Items.DRAGON_BONES, quantity = 1, slots = 25)
            obj(Items.SUPER_ATTACK_4, quantity = 1, slots = 25)
            obj(Items.SUPER_DEFENCE_4, quantity = 1, slots = 25)
            obj(Items.SUPER_STRENGTH_4, quantity = 1, slots = 25)
            obj(Items.TUNA, quantity = 5, slots = 25)
            obj(Items.DRAGON_PICKAXE, quantity = 1, slots = 6) // source's 250/256 remainder
        }
        table("elite_clue") {
            total(300)
            obj(Items.CLUE_SCROLL_ELITE, quantity = 1, slots = 1)
            nothing(299)
        }
    }

DropTableFactory.register(chaosElementalDrops, CHAOS_ELEMENTAL)

on_npc_death(CHAOS_ELEMENTAL) {
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
}
