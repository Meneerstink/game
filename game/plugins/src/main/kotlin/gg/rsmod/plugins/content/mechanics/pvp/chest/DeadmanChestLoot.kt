package gg.rsmod.plugins.content.mechanics.pvp.chest

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.util.Random

/**
 * Every number of the Deadman's chest in ONE place (owner 2026-10-17, dossier N101: "all chances PROVISIONAL in one config place; the
 * owner approves them in the N110 batch"). OSRS gives no kill chance, no lifetime and no loot rarity ("Unknown" on the wiki), so none
 * of these is a donor value; they are the Bouwer's proposal and never a blocker.
 */
object DeadmanChestConfig {
    /** OSRS wiki "Deadman's chest (scenery)": 60 seconds uninterrupted in singles, 30 seconds in multi (0.6 s ticks). */
    const val OPEN_TICKS_SINGLES = 100
    const val OPEN_TICKS_MULTI = 50

    /** OSRS: at least 30 HP. NpcCombatDsl.stats divides raw lifepoints by ten; Npc.combatDef uses ENGINE HP, not raw JSON units. */
    const val MIN_LIFEPOINTS = 30

    /** Owner 2026-10-17: the minimum risked value (OSRS permanent world 250k, owner 100k). */
    const val MIN_RISK = 100_000L

    /** Timed chest: every 30 minutes (owner: like Annihilation). */
    const val TIMED_INTERVAL_TICKS = 3000

    /** PROVISIONAL: 1 in 250 per counting kill. */
    const val KILL_CHANCE = 250

    /** PROVISIONAL anti-farm caps. */
    const val KILL_COOLDOWN_TICKS = 1000
    const val MAX_KILL_CHESTS = 3
    const val MIN_CHEST_DISTANCE = 15

    /** PROVISIONAL lifetimes: an unlooted kill chest lasts 5 minutes; a looted chest shows its open state for 10 seconds. */
    const val KILL_CHEST_TICKS = 500
    const val OPEN_STATE_TICKS = 17

    /** N100 drop-rate brackets: default NO boost on anything of the chest (dossier gap 4, VRAGEN 34 b). */
    const val BRACKET_BOOST = false

    /** Breach monsters roll the chest list on top of their own table: PROVISIONAL 1 in 400 per eligible player. */
    const val BREACH_EXTRA_CHANCE = 400
}

/**
 * The ONE Deadman's chest loot list (owner 2026-10-17). Three consumers read it so they can never drift: the chest reward roll, the
 * shop filter (StoreCatalogue sells none of [FIXED_IDS], StorePets sells no pet) and the Grand Exchange house (`houseQuote` is null
 * for [FIXED_IDS]: "komen alleen nog uit de chest en breaches").
 *
 * Ids are the NORMAL pieces the shop sold (dossier gap 6), never (deg)/corrupt: DeadmanGear.CONVERT maps those to these.
 * Weights are PROVISIONAL (N110 a).
 */
object DeadmanChestLoot {
    class Entry(
        val item: Int,
        val amount: Int,
        val weight: Int,
    )

    /** Morrigan's javelin / throwing axe are ONE stack of 100 (as BreachLoot.RARE), never noted. */
    val FIXED: List<Entry> =
        listOf(
            // Ancient Warriors, 17 pieces (Vesta, Statius, Morrigan, Zuriel).
            Entry(Items.VESTAS_CHAINBODY, 1, 10), Entry(Items.VESTAS_PLATESKIRT, 1, 10),
            Entry(Items.VESTAS_LONGSWORD, 1, 6), Entry(Items.VESTAS_SPEAR, 1, 6),
            Entry(Items.STATIUSS_FULL_HELM, 1, 10), Entry(Items.STATIUSS_PLATEBODY, 1, 10),
            Entry(Items.STATIUSS_PLATELEGS, 1, 10), Entry(Items.STATIUSS_WARHAMMER, 1, 6),
            Entry(Items.MORRIGANS_COIF, 1, 10), Entry(Items.MORRIGANS_LEATHER_BODY, 1, 10),
            Entry(Items.MORRIGANS_LEATHER_CHAPS, 1, 10),
            Entry(Items.MORRIGANS_JAVELIN, 100, 10), Entry(Items.MORRIGANS_THROWING_AXE, 100, 10),
            Entry(Items.ZURIELS_HOOD, 1, 10), Entry(Items.ZURIELS_ROBE_TOP, 1, 10),
            Entry(Items.ZURIELS_ROBE_BOTTOM, 1, 10), Entry(Items.ZURIELS_STAFF, 1, 6),
            // Trinket of fairies (owner 10-17 overrules the 09-19 "no trinkets" exclusion for this one trinket).
            Entry(Items.TRINKET_OF_FAIRIES, 1, 8),
            // Moved out of the Deadman Shop.
            Entry(Items.ELDER_CHAOS_HOOD, 1, 8), Entry(Items.ELDER_CHAOS_TOP, 1, 8), Entry(Items.ELDER_CHAOS_ROBE, 1, 8),
            Entry(Items.HEAVY_BALLISTA, 1, 3),
            Entry(Items.VOIDWAKER_BLADE, 1, 2), Entry(Items.VOIDWAKER_GEM, 1, 2), Entry(Items.VOIDWAKER_HILT, 1, 2),
        )

    /** Weight of one pet: PROVISIONAL, every tier-roster species equal, including those with boss drops. */
    const val PET_WEIGHT = 5

    /** Pets are untradeable and read from [pets] at load; the fixed items are what the shop filter and the GE house exclude. */
    val FIXED_IDS: Set<Int> = FIXED.map { it.item }.toSet()

    /** The 17 Ancient Warriors pieces the owner named (count guard). */
    val ANCIENT_WARRIORS: Set<Int> =
        setOf(
            Items.VESTAS_CHAINBODY, Items.VESTAS_PLATESKIRT, Items.VESTAS_LONGSWORD, Items.VESTAS_SPEAR,
            Items.STATIUSS_FULL_HELM, Items.STATIUSS_PLATEBODY, Items.STATIUSS_PLATELEGS, Items.STATIUSS_WARHAMMER,
            Items.MORRIGANS_COIF, Items.MORRIGANS_LEATHER_BODY, Items.MORRIGANS_LEATHER_CHAPS, Items.MORRIGANS_JAVELIN,
            Items.MORRIGANS_THROWING_AXE, Items.ZURIELS_HOOD, Items.ZURIELS_ROBE_TOP, Items.ZURIELS_ROBE_BOTTOM, Items.ZURIELS_STAFF,
        )

    /**
     * A pet reward: (pet name, first/base variant item), using the former shop's base-variant selection.
     * Council Opus+Sol 2026-10-20: ALL pet_tiers species enter the chest, independently of their existing boss drops.
     * The shop-only dropOnly filter must not restrict this roster. BreachLoot shares it and delivers plain pet items
     * through its existing private ground-loot path (including duplicates); the chest uses Pets.award and excludes owned pets.
     */
    fun pets(
        table: List<Pets.Variant> = Pets.variants,
        tiers: List<Pets.PetInfo> = Pets.infos.values.toList(),
    ): List<Pair<String, Int>> {
        val base = table.groupBy { it.pet }.mapValues { it.value.first().item }
        return tiers.filter { it.pet !in OWN_BOSS_ONLY }.mapNotNull { info -> base[info.pet]?.let { info.pet to it } }
    }

    /**
     * N291 (owner 2026-10-24): the God Wars pets come ONLY from their own boss - never from the chest or a breach (whose extra
     * roll is this list). Every other species stays in.
     */
    val OWN_BOSS_ONLY = setOf("Pet General Graardor", "Pet K'ril Tsutsaroth", "Pet Kree'arra", "Pet Zilyana")

    sealed class Reward {
        class Item(val item: Int, val amount: Int) : Reward()

        class Pet(val pet: String, val item: Int) : Reward()
    }

    /**
     * The chest's one reward: a weighted pick over [FIXED] and the pets. [ownsPet] excludes pets the opener already owns (Pets.award
     * gives nothing for a duplicate, which would waste the one reward - dossier gap 5, ADAPTED).
     */
    fun roll(
        random: Random,
        pets: List<Pair<String, Int>> = pets(),
        ownsPet: (String) -> Boolean = { false },
    ): Reward {
        val available = pets.filterNot { ownsPet(it.first) }
        val total = FIXED.sumOf { it.weight } + available.size * PET_WEIGHT
        var r = random.nextInt(total)
        for (e in FIXED) {
            r -= e.weight
            if (r < 0) return Reward.Item(e.item, e.amount)
        }
        for (p in available) {
            r -= PET_WEIGHT
            if (r < 0) return Reward.Pet(p.first, p.second)
        }
        val last = FIXED.last()
        return Reward.Item(last.item, last.amount)
    }
}
