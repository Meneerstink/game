package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import kotlin.math.floor

/**
 * N522 nachtrun deel 3 (Krystilia, rapport 2): Larran's key and Larran's small / big chest.
 *
 * - Larran's key (OSRS 23490, imported as [KEY]): dropped by monsters killed on a Krystilia task. OSRS Wiki "Larran's key" (2026-11-05):
 *   1 in floor(0.3 x (80 - L)^2) + 100 for combat level L 1-80, 1 in floor(-5/27 x L) + 115 for 81-350, 1 in 50 above 350; Slayer monsters
 *   (a Slayer level requirement) +20 %. (The +15 % of the OSRS Wilderness Slayer Cave and superior monsters do not exist here.)
 * - Larran's small chest (OSRS 33343, north-east of the Chaos Temple, 3281,3659) and big chest (OSRS 34829, the ship west of the Pirates'
 *   Hideout, 3018,3955 plane 1): Unlock with a key. Loot: OSRS Wiki "Larran's small chest" / "Larran's big chest" (2026-11-05), main table
 *   out of 60; the big chest pre-rolls each Dagon'hai piece 1/256; 3/60 (1/20) the fish table, whose fish follow the player's Fishing level
 *   (Module:Slayer chest fish chart + Module:Skilling success chart interp: sequential rolls manta, turtle, shark, monkfish, swordfish, tuna,
 *   else lobster; manta / turtle / shark roll a sub-table fish 2/8, raw shark 3/8, shark lure 3/8; big = brimstone quantities x1.5).
 * - Not in this game (left as nothing, every other rate stays OSRS): Celastrus / Dragonfruit tree / Redwood tree seed, Shark lure.
 */
object LarransChests {
    const val KEY = 24638 // OSRS 23490 (OsrsItemImportTool batch larran, tx-20261105-005933)
    const val SMALL = 63857 // OSRS 33343 Larran's small chest (closed)
    const val SMALL_OPEN = 63858 // OSRS 34828
    const val BIG = 63859 // OSRS 34829 Larran's big chest (closed)
    const val BIG_OPEN = 63860 // OSRS 34830

    val SMALL_TILE = Tile(3281, 3659, 0)
    /**
     * OSRS Wiki LocLine 3018,3955 plane 1, on the ship west of the Pirates' Hideout. The rev-667 ship there is scenery: every deck tile
     * on plane 1 is blocked (clip out=NESW in=NESW) and the open plane-1 tiles float beside the hull (testclient 2026-11-05), so nobody can
     * reach a chest on it. The big chest is therefore NOT placed until the OSRS ship (walkable deck) is imported - BUGLIJST N569.
     */
    val BIG_TILE = Tile(3018, 3955, 1)
    const val BIG_PLACED = false

    /** The chance of a key for one task kill of an npc of combat level [level] (Wiki formula, x1.2 for Slayer monsters). */
    fun keyChance(level: Int, slayerMonster: Boolean): Double {
        val l = level.coerceAtLeast(1)
        val denominator = when {
            l <= 80 -> floor(0.3 * (80 - l) * (80 - l)) + 100
            l <= 350 -> floor(-5.0 / 27.0 * l) + 115
            else -> 50.0
        }
        return (1.0 / denominator) * if (slayerMonster) 1.2 else 1.0
    }

    /** Called once per task kill (SlayerExt.handleDecrease, before the count goes down - so the task's last kill counts too). */
    fun rollKey(player: Player, npc: Npc) {
        // A failing drop must never break the task count it rides on (the same rule as DropTableFactory.getDrop).
        try {
            val chance = keyChance(npc.def.combatLevel, npc.combatDef.slayerReq > 1)
            if (player.world.randomDouble() >= chance) return
            gg.rsmod.plugins.content.drops.DropTableFactory.giveDrop(player.world, player, npc.id, npc.tile, listOf(Item(KEY, 1)), allowLootShare = false)
        } catch (e: Exception) {
            gg.rsmod.game.model.Trace.crash("larran key", e)
        }
    }

    data class Row(val item: Int, val min: Int, val max: Int, val weight: Int)

    /** Item null = an OSRS row of an item this game does not have. Weights out of 60; [FISH] marks the 3/60 fish table. */
    const val FISH = -2

    val SMALL_TABLE =
        listOf(
            Row(Items.UNCUT_DIAMOND_NOTED, 15, 25, 5), Row(Items.UNCUT_RUBY_NOTED, 20, 30, 5), Row(Items.COAL_NOTED, 282, 480, 5),
            Row(Items.COINS_995, 40_534, 114_792, 3), Row(Items.GOLD_ORE_NOTED, 81, 179, 4), Row(Items.DRAGON_ARROWTIPS, 41, 182, 4),
            Row(Items.IRON_ORE_NOTED, 300, 449, 3), Row(Items.RUNE_FULL_HELM_NOTED, 1, 3, 3), Row(Items.RUNE_PLATEBODY_NOTED, 1, 2, 3),
            Row(Items.RUNE_PLATELEGS_NOTED, 1, 2, 3), Row(FISH, 0, 0, 3), Row(Items.RUNITE_ORE_NOTED, 5, 10, 2),
            Row(Items.STEEL_BAR_NOTED, 253, 450, 2), Row(Items.MAGIC_LOGS_NOTED, 80, 120, 2), Row(Items.DRAGON_DART_TIP, 31, 149, 2),
            Row(Items.PALM_TREE_SEED, 1, 3, 1), Row(Items.MAGIC_SEED, 1, 2, 1), Row(-1, 1, 3, 1), Row(-1, 1, 3, 1), Row(-1, 1, 1, 1),
            Row(Items.TORSTOL_SEED, 2, 4, 1), Row(Items.SNAPDRAGON_SEED, 2, 4, 1), Row(Items.RANARR_SEED, 2, 4, 1),
            Row(Items.PURE_ESSENCE_NOTED, 3041, 5989, 3),
        )

    val BIG_TABLE =
        listOf(
            Row(Items.UNCUT_DIAMOND_NOTED, 35, 45, 5), Row(Items.UNCUT_RUBY_NOTED, 35, 45, 5), Row(Items.COAL_NOTED, 450, 650, 5),
            Row(Items.GOLD_ORE_NOTED, 150, 250, 4), Row(Items.DRAGON_ARROWTIPS, 100, 250, 4), Row(Items.COINS_995, 75_000, 175_000, 3),
            Row(Items.IRON_ORE_NOTED, 500, 650, 3), Row(Items.RUNE_FULL_HELM_NOTED, 3, 5, 3), Row(Items.RUNE_PLATEBODY_NOTED, 2, 3, 3),
            Row(Items.RUNE_PLATELEGS_NOTED, 2, 3, 3), Row(Items.PURE_ESSENCE_NOTED, 4500, 7500, 3), Row(FISH, 0, 0, 3),
            Row(Items.RUNITE_ORE_NOTED, 15, 20, 2), Row(Items.STEEL_BAR_NOTED, 350, 550, 2), Row(Items.MAGIC_LOGS_NOTED, 180, 220, 2),
            Row(Items.DRAGON_DART_TIP, 80, 200, 2), Row(Items.PALM_TREE_SEED, 3, 5, 1), Row(Items.MAGIC_SEED, 3, 4, 1), Row(-1, 3, 5, 1),
            Row(-1, 3, 5, 1), Row(-1, 1, 1, 1), Row(Items.TORSTOL_SEED, 4, 6, 1), Row(Items.SNAPDRAGON_SEED, 4, 6, 1),
            Row(Items.RANARR_SEED, 4, 6, 1),
        )

    /** Module:Skilling success chart p.interp: floor(low*(99-L)/98 + high*(L-1)/98 + 0.5) + 1, / 256, clamped to 0..1. */
    fun interp(low: Int, high: Int, level: Int): Double {
        val value = floor(low * (99.0 - level) / 98.0 + high * (level - 1.0) / 98.0 + 0.5) + 1
        return (value / 256.0).coerceIn(0.0, 1.0)
    }

    /** Quantities: Wiki "Larran's small chest" (manta ray "not listed" -> nothing) and "Larran's big chest" (brimstone x1.5). */
    enum class Fish(val item: Int, val small: IntRange?, val big: IntRange) {
        MANTA(Items.RAW_MANTA_RAY_NOTED, null, 120..240),
        TURTLE(Items.RAW_SEA_TURTLE_NOTED, 81..177, 120..300),
        SHARK(Items.RAW_SHARK_NOTED, 126..250, 120..375),
        MONKFISH(Items.RAW_MONKFISH_NOTED, 162..297, 150..450),
        SWORDFISH(Items.RAW_SWORDFISH_NOTED, 113..264, 150..450),
        TUNA(Items.RAW_TUNA_NOTED, 112..307, 150..525),
        LOBSTER(Items.RAW_LOBSTER_NOTED, 163..342, 150..525),
    }

    /** The fish roll for Fishing level [level]; [rnd] gives uniform 0..1 numbers, [sub] 0..7 for the manta / turtle / shark sub-table. */
    fun fish(level: Int, rnd: () -> Double, sub: () -> Int): Fish? {
        val order = listOf(
            Fish.MANTA to interp(-10, 20, level), Fish.TURTLE to interp(-10, 50, level), Fish.SHARK to interp(-60, 40, level),
            Fish.MONKFISH to interp(0, 170, level), Fish.SWORDFISH to interp(25, 200, level), Fish.TUNA to interp(160, 0, level),
        )
        for ((fish, chance) in order) {
            if (rnd() < chance) {
                if (fish == Fish.MANTA || fish == Fish.TURTLE || fish == Fish.SHARK) {
                    // fish 2/8, raw shark 3/8, shark lure 3/8 (not in this game: nothing)
                    val r = sub()
                    return when {
                        r < 2 -> fish
                        r < 5 -> Fish.SHARK
                        else -> null
                    }
                }
                return fish
            }
        }
        return Fish.LOBSTER
    }

    /** One opening of the [big] or small chest for [player]: the item list. */
    fun roll(player: Player, big: Boolean): List<Item> {
        val world = player.world
        val out = ArrayList<Item>()
        if (big) {
            listOf(Items.DAGONHAI_HAT, Items.DAGONHAI_ROBE_TOP, Items.DAGONHAI_ROBE_BOTTOM).forEach { if (world.random(255) == 0) out += Item(it) }
        }
        val table = if (big) BIG_TABLE else SMALL_TABLE
        var pick = world.random(table.sumOf { it.weight } - 1)
        val row = table.first { pick -= it.weight; pick < 0 }
        when (row.item) {
            -1 -> {}
            FISH -> fish(player.skills.getMaxLevel(Skills.FISHING), { world.randomDouble() }, { world.random(7) })?.let {
                val range = if (big) it.big else it.small
                if (range != null) out += Item(it.item, world.random(range))
            }
            else -> out += Item(row.item, world.random(row.min..row.max))
        }
        return out
    }
}
