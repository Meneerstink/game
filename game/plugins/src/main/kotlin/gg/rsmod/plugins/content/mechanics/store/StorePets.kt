package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.content.skills.summoning.Pets
import java.io.File

/**
 * The OSRS pets in the stores (owner 2026-09-30: "make 3 tiers of pets ... spread these pets and sell them in the donator store loyalty
 * store and deadmanstore ... some pets need to be only dropped by their boss or monster variant").
 *
 * Owner answers 2026-09-30: tier 1 (most unique) in the Donator Shop, tier 2 in the Deadman Shop, tier 3 (most common) in the Loyalty
 * Shop; a pet whose boss exists on this server is drop-only and never sold. Tiers and boss drops live in `data/cfg/pets/pet_tiers.tsv`.
 * One entry per pet: its first (base) variant - the other variants are reached with Metamorphosis ([Pets.metamorph]).
 *
 * [PRICES] are the agent's proposal in line with the existing stock (Donator prestige weapons 1,000; Deadman heavy ballista 1,000;
 * Loyalty baby dragons 15,000), for the owner to adjust.
 */
object StorePets {
    const val CATEGORY = "OSRS pets"

    val SHOP_BY_TIER =
        mapOf(1 to StoreCatalogue.Shop.DONATOR, 2 to StoreCatalogue.Shop.DEADMAN, 3 to StoreCatalogue.Shop.LOYALTY)

    val PRICES =
        mapOf(StoreCatalogue.Shop.DONATOR to 1_000, StoreCatalogue.Shop.DEADMAN to 1_000, StoreCatalogue.Shop.LOYALTY to 15_000)

    /** (pet, shop, base item) for every sold pet; empty until `data/cfg/pets/pets.tsv` has been generated. */
    fun stock(
        table: List<Pets.Variant>,
        tiers: List<Pets.PetInfo>,
    ): List<Triple<String, StoreCatalogue.Shop, Int>> {
        val base = table.groupBy { it.pet }.mapValues { it.value.first().item }
        return tiers.filterNot { it.dropOnly }.mapNotNull { info ->
            val item = base[info.pet] ?: return@mapNotNull null
            Triple(info.pet, SHOP_BY_TIER.getValue(info.tier), item)
        }
    }

    /** Every variant item of every drop-only pet: never for sale. */
    fun dropOnlyItems(
        tableFile: File = File(Pets.TABLE_PATH),
        tiersFile: File = File(Pets.TIERS_PATH),
    ): Set<Int> {
        if (!tableFile.exists() || !tiersFile.exists()) return emptySet()
        val dropOnly = Pets.parseTiers(tiersFile.readLines()).filter { it.dropOnly }.map { it.pet }.toSet()
        return Pets.parseTable(tableFile.readLines()).filter { it.pet in dropOnly }.map { it.item }.toSet()
    }

    fun entries(
        tableFile: File = File(Pets.TABLE_PATH),
        tiersFile: File = File(Pets.TIERS_PATH),
    ): List<StoreCatalogue.Entry> {
        if (!tableFile.exists() || !tiersFile.exists()) return emptyList()
        return stock(Pets.parseTable(tableFile.readLines()), Pets.parseTiers(tiersFile.readLines())).map { (_, shop, item) ->
            StoreCatalogue.itemEntry(
                shop, item, PRICES.getValue(shop), CATEGORY,
                tier = if (shop == StoreCatalogue.Shop.DONATOR) StoreCatalogue.Tier.PRESTIGE else StoreCatalogue.Tier.NONE,
            )
        }
    }
}
