package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LOYALTY_POINTS
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits

/**
 * The single catalogue of the three monetization shops (owner night run 2026-09-19, batches 4 and 5). Every shop screen, purchase,
 * preview and validation reads this table; nothing is listed per shop anywhere else.
 *
 * Owner rules encoded here:
 *  - three separate, account-bound currencies that are attributes on the account, never items (so they cannot be traded, dropped,
 *    lost on death, noted or duplicated through bank/trade/relog): Donator Points (webshop only), Deadman Points (Deadman/PvP only)
 *    and Loyalty Points (play time, daily reward, events);
 *  - no item in more than one exclusive shop; Elder chaos, Dagon'hai and Heavy ballista kits only in the Deadman Shop;
 *  - Donator sells kits only (never a complete ornamented item without its base), no noted / broken / loaded / empty variants;
 *  - kit entries are derived from [OsrsOrnamentKits] (the same table that drives attach, dismantle/revert and PvP death), so a kit's
 *    preview results, base requirement and dismantle policy can never drift away from the item routes.
 *
 * Prices are PROVISIONAL owner-balance values (the owner gave tiers, not prices) and live only in [TIER_PRICE] / the explicit
 * entries below.
 */
object StoreCatalogue {
    enum class Currency(
        val singular: String,
        val plural: String,
        val attr: AttributeKey<Int>,
    ) {
        DONATOR("Donator Point", "Donator Points", AttributeKey(persistenceKey = "donator_points")),
        DEADMAN("Deadman Point", "Deadman Points", AttributeKey(persistenceKey = "deadman_points")),
        LOYALTY("Loyalty Point", "Loyalty Points", LOYALTY_POINTS),
    }

    enum class Shop(
        val title: String,
        val currency: Currency,
    ) {
        DONATOR("Donator Shop", Currency.DONATOR),
        DEADMAN("Deadman Shop", Currency.DEADMAN),
        LOYALTY("Loyalty Shop", Currency.LOYALTY),
    }

    enum class Tier(val label: String) {
        BASIC("Basic"),
        PREMIUM("Premium"),
        ELITE("Elite"),
        PRESTIGE("Prestige"),
        NONE(""),
    }

    enum class Kind {
        /** An ornament / colour kit, paint, mix or upgrade kit: bought alone, attached by the player to the base item. */
        KIT,

        /** A complete item sold as-is (cosmetics, pets, Deadman rewards). */
        ITEM,

        /** Exchanges the required base item for the result in one step (Deadman defender (t)). */
        UPGRADE,

        /**
         * A look-only account unlock (max cape "Customise", [gg.rsmod.plugins.content.items.osrs.MaxCapeLooks]); no item is given.
         * Owner 2026-09-19: sold for Loyalty Points and for Donator Points, so the same unlock may appear in both shops.
         */
        UNLOCK,
    }

    enum class TradeablePolicy(val label: String) {
        /**
         * The bought item keeps its own item-definition tradeability (OSRS kits are tradeable). Owner decision 2026-09-19: Donator
         * kits stay tradeable (donors may sell them for gold); Deadman kits are the untradeable OSRS Bounty Hunter kits.
         */
        ITEM_DEFINITION("tradeable as in OSRS"),
        UNTRADEABLE("untradeable"),
    }

    enum class DeathPolicy(val label: String) {
        /** Normal death rules of the item (Items Kept on Death, PvP ornament conversion in OsrsOrnamentKits). */
        ITEM_DEFINITION("normal death rules"),
    }

    enum class DismantlePolicy(val label: String) {
        RETURNS_KIT("dismantle returns the kit"),
        KIT_CONSUMED("the kit is used up"),
        NONE("-"),
    }

    data class Entry(
        val shop: Shop,
        val purchaseItem: Int,
        /** Items shown in the big preview, in carousel order (the ornamented results for a kit, the item itself otherwise). */
        val previewItems: List<Int>,
        /** Base item per preview item (same order); empty when nothing is required. */
        val requiredBaseItems: List<Int>,
        /** What the player ends up with per preview item (same order as [previewItems]). */
        val resultItems: List<Int>,
        val price: Int,
        val stock: Int = UNLIMITED,
        val category: String,
        val tier: Tier = Tier.NONE,
        val kind: Kind,
        val tradeablePolicy: TradeablePolicy = TradeablePolicy.ITEM_DEFINITION,
        val deathPolicy: DeathPolicy = DeathPolicy.ITEM_DEFINITION,
        val dismantlePolicy: DismantlePolicy = DismantlePolicy.NONE,
    ) {
        val currency: Currency get() = shop.currency
        val cosmeticOnly: Boolean get() = kind == Kind.KIT
    }

    const val UNLIMITED = Int.MAX_VALUE

    /** PROVISIONAL prices per Donator tier (owner gave the tiers only). */
    val TIER_PRICE = mapOf(Tier.BASIC to 100, Tier.PREMIUM to 250, Tier.ELITE to 500, Tier.PRESTIGE to 1_000)

    private data class KitRoute(val result: Int, val base: Int, val dismantle: DismantlePolicy)

    /** Every attach route of every kit (returned and consumed kits), from the shared ornament table. */
    private val kitRoutes: Map<Int, List<KitRoute>> by lazy {
        val routes = LinkedHashMap<Int, MutableList<KitRoute>>()
        OsrsOrnamentKits.ALL.forEach { routes.getOrPut(it.kit) { mutableListOf() } += KitRoute(it.ornamented, it.base, DismantlePolicy.RETURNS_KIT) }
        OsrsOrnamentKits.CONSUMED.forEach {
            routes.getOrPut(it.kit) { mutableListOf() } +=
                KitRoute(it.ornamented, it.base, if (it.returnsKit) DismantlePolicy.RETURNS_KIT else DismantlePolicy.KIT_CONSUMED)
        }
        // Masori crafting kit (osrs_max_capes.plugin.kts): Ava's assembler -> Masori assembler, Assembler max cape -> Masori assembler max cape.
        routes.getOrPut(Items.MASORI_CRAFTING_KIT) { mutableListOf() } += listOf(
            KitRoute(Items.MASORI_ASSEMBLER, Items.AVAS_ASSEMBLER, DismantlePolicy.KIT_CONSUMED),
            KitRoute(Items.MASORI_ASSEMBLER_MAX_CAPE, Items.ASSEMBLER_MAX_CAPE, DismantlePolicy.KIT_CONSUMED),
        )
        // Ward upgrade kit (CombinationData + Revert in osrs_magegear.plugin.kts): "It can be reverted, but the kit will not be returned".
        routes.getOrPut(Items.WARD_UPGRADE_KIT) { mutableListOf() } += listOf(
            KitRoute(Items.MALEDICTION_WARD_OR, Items.MALEDICTION_WARD, DismantlePolicy.KIT_CONSUMED),
            KitRoute(Items.ODIUM_WARD_OR, Items.ODIUM_WARD, DismantlePolicy.KIT_CONSUMED),
        )
        routes
    }

    fun kitEntry(
        shop: Shop,
        kit: Int,
        category: String,
        tier: Tier = Tier.NONE,
        price: Int = TIER_PRICE[tier] ?: error("kit $kit needs a price"),
        tradeable: TradeablePolicy = TradeablePolicy.ITEM_DEFINITION,
    ): Entry {
        val routes = kitRoutes[kit] ?: error("kit $kit has no attach route in OsrsOrnamentKits / the Masori routes")
        return Entry(
            shop = shop,
            purchaseItem = kit,
            previewItems = routes.map { it.result },
            requiredBaseItems = routes.map { it.base },
            resultItems = routes.map { it.result },
            price = price,
            category = category,
            tier = tier,
            kind = Kind.KIT,
            tradeablePolicy = tradeable,
            dismantlePolicy = routes.first().dismantle,
        )
    }

    fun itemEntry(
        shop: Shop,
        item: Int,
        price: Int,
        category: String,
        tradeable: TradeablePolicy = TradeablePolicy.ITEM_DEFINITION,
    ) = Entry(shop, item, listOf(item), emptyList(), listOf(item), price, category = category, kind = Kind.ITEM, tradeablePolicy = tradeable)

    fun upgradeEntry(
        shop: Shop,
        base: Int,
        result: Int,
        price: Int,
        category: String,
    ) = Entry(shop, result, listOf(result), listOf(base), listOf(result), price, category = category, kind = Kind.UPGRADE)

    /** Max cape look unlock: preview = the variant cape, requirement = its real component (MaxCapes.VARIANTS). */
    fun lookUnlockEntry(
        shop: Shop,
        variant: gg.rsmod.plugins.content.items.osrs.MaxCapes.Variant,
        price: Int,
    ) = Entry(
        shop, variant.cape, listOf(variant.cape), listOf(variant.component), listOf(variant.cape), price,
        category = "Max cape looks", tier = if (shop == Shop.DONATOR) Tier.PREMIUM else Tier.NONE, kind = Kind.UNLOCK,
    )

    val ENTRIES: List<Entry> by lazy { build() }

    fun entries(shop: Shop): List<Entry> = ENTRIES.filter { it.shop == shop }

    private fun build(): List<Entry> {
        val d = Shop.DONATOR
        val m = Shop.DEADMAN
        val l = Shop.LOYALTY
        val list = mutableListOf<Entry>()

        // ---- Donator Shop: kits only, tiers from the owner list ----
        list += kitEntry(d, Items.DRAGON_BOOTS_ORNAMENT_KIT, "Dragon kits", Tier.BASIC)
        listOf(Items.RUNE_SCIMITAR_ORNAMENT_KIT_GUTHIX, Items.RUNE_SCIMITAR_ORNAMENT_KIT_SARADOMIN, Items.RUNE_SCIMITAR_ORNAMENT_KIT_ZAMORAK)
            .forEach { list += kitEntry(d, it, "Weapon kits", Tier.BASIC) }
        listOf(Items.BLUE_DARK_BOW_PAINT, Items.GREEN_DARK_BOW_PAINT, Items.YELLOW_DARK_BOW_PAINT, Items.WHITE_DARK_BOW_PAINT)
            .forEach { list += kitEntry(d, it, "Weapon kits", Tier.BASIC) }
        listOf(
            Items.DRAGON_CHAINBODY_ORNAMENT_KIT, Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT, Items.DRAGON_FULL_HELM_ORNAMENT_KIT,
            Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT, Items.DRAGON_KITESHIELD_ORNAMENT_KIT, Items.DRAGON_PLATEBODY_ORNAMENT_KIT,
            Items.DRAGON_SCIMITAR_ORNAMENT_KIT, Items.DRAGON_DEFENDER_ORNAMENT_KIT, Items.RUNE_DEFENDER_ORNAMENT_KIT,
            // The revision-667 (or) and (sp) kits, now that their attach/Split routes exist (night run 2026-09-19).
            Items.DRAGON_FULL_HELM_ORNAMENT_KIT_OR, Items.DRAGON_PLATEBODY_ORNAMENT_KIT_OR, Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_OR,
            Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_OR, Items.DRAGON_FULL_HELM_ORNAMENT_KIT_SP, Items.DRAGON_PLATEBODY_ORNAMENT_KIT_SP,
            Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_SP, Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_SP,
        ).forEach { list += kitEntry(d, it, "Dragon kits", Tier.PREMIUM) }
        listOf(Items.FROZEN_WHIP_MIX, Items.VOLCANIC_WHIP_MIX, Items.LAVA_STAFF_UPGRADE_KIT, Items.STEAM_STAFF_UPGRADE_KIT,
            Items.BERSERKER_NECKLACE_ORNAMENT_KIT, Items.TZHAAR_KET_OM_ORNAMENT_KIT)
            .forEach { list += kitEntry(d, it, "Weapon kits", Tier.PREMIUM) }
        listOf(Items.ARMADYL_GODSWORD_ORNAMENT_KIT, Items.BANDOS_GODSWORD_ORNAMENT_KIT, Items.SARADOMIN_GODSWORD_ORNAMENT_KIT,
            Items.ZAMORAK_GODSWORD_ORNAMENT_KIT)
            .forEach { list += kitEntry(d, it, "Godsword kits", Tier.ELITE) }
        listOf(Items.OCCULT_ORNAMENT_KIT, Items.ANGUISH_ORNAMENT_KIT, Items.TORTURE_ORNAMENT_KIT, Items.TORMENTED_ORNAMENT_KIT,
            Items.LIGHT_INFINITY_COLOUR_KIT, Items.DARK_INFINITY_COLOUR_KIT)
            .forEach { list += kitEntry(d, it, "Jewellery and robe kits", Tier.ELITE) }
        listOf(Items.FURY_ORNAMENT_KIT, Items.TWISTED_ANCESTRAL_COLOUR_KIT, Items.BLOWPIPE_ORNAMENT_KIT)
            .forEach { list += kitEntry(d, it, "Prestige kits", Tier.PRESTIGE) }

        // ---- Deadman Shop: PvP-earned; Elder chaos, Dagon'hai and Heavy ballista kits are exclusive here ----
        listOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.HEAVY_BALLISTA_ORNAMENT_KIT)
            .forEach { list += kitEntry(m, it, "Deadman kits", price = 750, tradeable = TradeablePolicy.UNTRADEABLE) }
        listOf(Items.VOID_KNIGHT_TOP to 250, Items.VOID_KNIGHT_ROBE to 250, Items.VOID_KNIGHT_MACE to 250, Items.VOID_KNIGHT_GLOVES to 150,
            Items.VOID_MELEE_HELM to 200, Items.VOID_RANGER_HELM to 200, Items.VOID_MAGE_HELM to 200, Items.VOID_KNIGHT_DEFLECTOR to 300)
            .forEach { (item, price) -> list += itemEntry(m, item, price, "Void knight") }
        listOf(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_ZAMORAK_CAPE)
            .forEach { list += itemEntry(m, it, 400, "Imbued items") }
        listOf(Items.RING_OF_THE_GODS_I, Items.TYRANNICAL_RING_I, Items.TREASONOUS_RING_I)
            .forEach { list += itemEntry(m, it, 600, "Imbued items") }
        list += itemEntry(m, Items.RUNE_POUCH, 500, "Utility")
        list += itemEntry(m, Items.DIVINE_RUNE_POUCH, 1_000, "Utility")
        list += upgradeEntry(m, Items.DRAGON_DEFENDER, Items.DRAGON_DEFENDER_T, 400, "Defenders")
        list += upgradeEntry(m, Items.RUNE_DEFENDER, Items.RUNE_DEFENDER_T, 250, "Defenders")

        // ---- Loyalty Shop: high-end rewards, prestige cosmetics, pets (plus the former Xuan "Loyalty Rewards" stock) ----
        listOf(Items.WARD_UPGRADE_KIT to 40_000, Items.MENAPHITE_ORNAMENT_KIT to 40_000, Items.MASORI_CRAFTING_KIT to 60_000,
            Items.ZALCANO_SHARD to 30_000, Items.HARMONISED_ORB to 75_000, Items.VOLATILE_ORB to 75_000, Items.ELDRITCH_ORB to 75_000,
            Items.DRAGON_PICKAXE_UPGRADE_KIT to 30_000)
            .forEach { (kit, price) -> list += kitEntry(l, kit, "High-end rewards", price = price) }
        listOf(Items.CROWN_OF_HELIOS to 25_000, Items.GUTHIX_HALO to 2_500, Items.SARADOMIN_HALO to 2_500, Items.ZAMORAK_HALO to 2_500,
            Items.ROYAL_CROWN to 15_000, Items.HERALD_CAPE to 7_500, Items.TOP_HAT to 5_000, Items.SNOWMAN_TOP_HAT to 5_000,
            Items.REINDEER_HAT to 500, Items.BUNNY_EARS to 5_000)
            .forEach { (item, price) -> list += itemEntry(l, item, price, "Prestige cosmetics") }
        listOf(Items.SANTA_COSTUME_TOP, Items.SANTA_COSTUME_LEGS, Items.SANTA_COSTUME_GLOVES, Items.SANTA_COSTUME_BOOTS,
            Items.MIME_MASK, Items.MIME_TOP, Items.MIME_LEGS, Items.MIME_GLOVES, Items.MIME_BOOTS,
            Items.GHOSTLY_HOOD, Items.GHOSTLY_ROBE, Items.GHOSTLY_ROBE_6108, Items.GHOSTLY_GLOVES, Items.GHOSTLY_BOOTS, Items.GHOSTLY_CLOAK,
            Items.JESTER_HAT, Items.JESTER_SCARF, Items.LEDERHOSEN_HAT, Items.LEDERHOSEN_TOP, Items.LEDERHOSEN_SHORTS)
            .forEach { list += itemEntry(l, it, 2_000, "Costumes") }
        listOf(Items.ZOMBIE_MASK, Items.SKELETON_MASK, Items.JACK_LANTERN_MASK, Items.FOX_MASK, Items.SHEEP_MASK, Items.PENGUIN_MASK,
            Items.BAT_MASK, Items.CAT_MASK, Items.WOLF_MASK)
            .forEach { list += itemEntry(l, it, 1_500, "Masks") }
        listOf(Items.GREEN_DRAGON_MASK, Items.BLUE_DRAGON_MASK, Items.RED_DRAGON_MASK, Items.BLACK_DRAGON_MASK, Items.FROST_DRAGON_MASK,
            Items.BRONZE_DRAGON_MASK, Items.IRON_DRAGON_MASK, Items.STEEL_DRAGON_MASK, Items.MITHRIL_DRAGON_MASK)
            .forEach { list += itemEntry(l, it, 3_000, "Masks") }
        // Holiday cosmetics = the former Xuan "Loyalty Rewards" stock at its old prices (moved here so there is one loyalty shop).
        listOf(Items.SANTA_HAT to 10_000, Items.RED_HWEEN_MASK to 7_000, Items.GREEN_HWEEN_MASK to 7_000, Items.BLUE_HWEEN_MASK to 7_000,
            Items.CHRISTMAS_CRACKER to 35_000, Items.DISK_OF_RETURNING to 10_000, Items.EASTER_EGG to 3_000, Items.PUMPKIN to 3_000,
            Items.ANGER_SWORD to 500, Items.ANGER_SPEAR to 500, Items.ANGER_MACE to 500, Items.ANGER_BATTLEAXE to 500,
            Items.RUBBER_CHICKEN to 500, Items.CHOCATRICE_CAPE to 500, Items.GRIM_REAPER_HOOD to 500, Items.WEB_CLOAK to 500)
            .forEach { (item, price) -> list += itemEntry(l, item, price, "Holiday cosmetics") }
        listOf(Items.PET_ROCK to 1_000, Items.HELLKITTEN to 10_000, Items.BABY_DRAGON to 15_000, Items.BABY_DRAGON_12472 to 15_000,
            Items.BABY_DRAGON_12474 to 15_000, Items.BABY_DRAGON_12476 to 15_000, Items.BABY_PENGUIN to 7_500, Items.BABY_RACCOON_12486 to 7_500,
            Items.BABY_GECKO to 7_500, Items.BABY_SQUIRREL to 7_500, Items.BABY_CHAMELEON to 7_500, Items.BABY_MONKEY to 7_500,
            Items.BABY_GIANT_CRAB_12500 to 7_500, Items.BABY_ICEFIEND to 10_000, Items.BABY_PLATYPUS to 7_500,
            Items.TERRIER_PUPPY_12512 to 5_000, Items.GREYHOUND_PUPPY_12514 to 5_000, Items.LABRADOR_PUPPY_12516 to 5_000,
            Items.DALMATIAN_PUPPY_12518 to 5_000, Items.SHEEPDOG_PUPPY_12520 to 5_000, Items.BULLDOG_PUPPY_12522 to 5_000)
            .forEach { (item, price) -> list += itemEntry(l, item, price, "Pets") }

        // Max cape look unlocks (owner 2026-09-19): earnable with Loyalty Points and buyable with Donator Points.
        gg.rsmod.plugins.content.items.osrs.MaxCapes.VARIANTS.forEach { variant ->
            list += lookUnlockEntry(l, variant, 20_000)
            list += lookUnlockEntry(d, variant, TIER_PRICE.getValue(Tier.PREMIUM))
        }
        return list
    }

    /**
     * Catalogue rules the owner fixed; returns one line per violation (empty = valid). [definitions] lets the rules check names
     * (no noted / broken / loaded / empty variants).
     */
    fun violations(definitions: DefinitionSet? = null): List<String> {
        val problems = mutableListOf<String>()
        // Look unlocks are account unlocks, not items: the same unlock may be sold for Loyalty and Donator Points.
        val shopsByItem = ENTRIES.filter { it.kind != Kind.UNLOCK }.groupBy { it.purchaseItem }.mapValues { (_, e) -> e.map { it.shop }.toSet() }
        shopsByItem.filter { it.value.size > 1 }.forEach { (item, shops) -> problems += "item $item is sold in several shops: $shops" }
        ENTRIES.groupBy { it.shop to it.purchaseItem }.filter { it.value.size > 1 }.forEach { problems += "duplicate entry ${it.key}" }
        val deadmanOnly = setOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.HEAVY_BALLISTA_ORNAMENT_KIT)
        ENTRIES.filter { it.purchaseItem in deadmanOnly && it.shop != Shop.DEADMAN }.forEach { problems += "${it.purchaseItem} must be Deadman-only" }
        ENTRIES.filter { it.shop == Shop.DONATOR && it.kind != Kind.KIT && it.kind != Kind.UNLOCK }.forEach { problems += "Donator entry ${it.purchaseItem} is not a kit or look unlock" }
        ENTRIES.filter { it.price <= 0 }.forEach { problems += "entry ${it.purchaseItem} has no price" }
        ENTRIES.filter { it.previewItems.isEmpty() || it.previewItems.size != it.resultItems.size }.forEach { problems += "entry ${it.purchaseItem} preview/result mismatch" }
        ENTRIES.filter { it.kind != Kind.ITEM && it.requiredBaseItems.size != it.previewItems.size }.forEach { problems += "entry ${it.purchaseItem} needs a base item per result" }
        val forbidden = setOf(Items.MAX_CAPE, Items.DIZANAS_MAX_CAPE, Items.AVAS_ASSEMBLER, Items.NIGHTMARE_STAFF)
        ENTRIES.filter { it.shop == Shop.DONATOR && it.kind != Kind.UNLOCK && it.purchaseItem in forbidden }.forEach { problems += "Donator must not sell ${it.purchaseItem}" }
        if (definitions != null) {
            ENTRIES.forEach { e ->
                val def = definitions.getNullable(ItemDef::class.java, e.purchaseItem)
                if (def == null) {
                    problems += "entry ${e.purchaseItem} has no item definition"
                    return@forEach
                }
                val name = def.name.lowercase()
                if (def.noted) problems += "entry ${e.purchaseItem} ($name) is noted"
                if (listOf("(broken)", "broken", "(loaded)", "(empty)", "(uncharged)").any { it in name } && e.kind != Kind.KIT) {
                    problems += "entry ${e.purchaseItem} ($name) is a broken/loaded/empty variant"
                }
            }
        }
        return problems
    }
}
