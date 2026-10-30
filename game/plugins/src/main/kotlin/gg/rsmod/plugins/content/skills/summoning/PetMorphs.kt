package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.skills.runecrafting.Rune

/**
 * OSRS pet metamorphoses (OSRS Wiki "Metamorphosis" and each pet's page, read 2026-09-30): which variants a player has unlocked, the
 * items that change a pet (used on the follower, or on the pet item where OSRS allows it), and the rift guardian's altar colours.
 *
 * - Metamorphosis cycles through the player's unlocked variants only; the first variant (the one a boss drops / a shop sells) is always
 *   unlocked, and so is every variant OSRS unlocks by default ("{{NA}}" on the wiki: Kalphite Princess, Pet Smoke Devil, dark core,
 *   snakeling, Venenatis, Callisto, Vet'ion, Baby chinchompa, Jal-Nib-Rek, Noon, Ikkle Hydra, Lil' Creator, Bran / Ric).
 * - A variant whose OSRS unlock route does not exist here (spirit flakes, metamorphic / sanguine dust, egg sacs, parasitic egg, raid
 *   remnants, charged ice, coagulated venom, dark / calcified acorns, the Gauntlet, TzHaar-Ket-Rak, Guardians of the Rift, the wrath
 *   altar, ores / logs / seeds this cache has no item for) is unlocked by default: CUSTOM_SERVER_OVERRIDE, else it could never be had.
 */
object PetMorphs {
    /** OSRS item ids of the variants a player unlocked, comma separated. */
    val UNLOCKED_ATTR = AttributeKey<String>(persistenceKey = "osrs_pet_morphs")

    /** 1 while the rift guardian's colour is locked (OSRS "Locking"). */
    val RIFT_LOCK_ATTR = AttributeKey<Int>(persistenceKey = "osrs_rift_guardian_locked")

    /** Fish fed to Gull so far (OSRS: 50 raw or cooked fish unlock Gulliver). */
    val GULL_FED_ATTR = AttributeKey<Int>(persistenceKey = "osrs_gull_fed")

    const val MSG_RIFT_UNLOCK = "You have unlocked a new metamorphosis for your rift guardian."

    /**
     * An item that changes a pet: used on it, [target] (OSRS item id of the variant) replaces the pet and is unlocked. [count] items
     * are needed and consumed when [consumed]; [followerOnly]: OSRS allows it only while the pet follows (Phoenix).
     */
    data class Use(val item: Int, val target: Int, val count: Int = 1, val consumed: Boolean = false, val followerOnly: Boolean = false)

    /** Per pet (roster name): the items that change it. Wiki per pet; 667 item ids from Items.kt. */
    val USES: Map<String, List<Use>> =
        mapOf(
            // Baby Mole: "mole claws used on it to transform into a baby mole-rat; mole skin will return it" (recipe: consumed).
            "Baby Mole" to listOf(Use(Items.MOLE_CLAW, 25613, consumed = true), Use(Items.MOLE_SKIN, 12646, consumed = true)),
            // Rock golem: "using the desired ore ... while it is in the inventory or following"; a plain rock reverts it.
            "Rock golem" to
                listOf(
                    Use(Items.ROCK_1480, 13321), Use(Items.TIN_ORE, 21187), Use(Items.COPPER_ORE, 21188), Use(Items.IRON_ORE, 21189),
                    Use(Items.BLURITE_ORE, 21190), Use(Items.SILVER_ORE, 21191), Use(Items.COAL, 21192), Use(Items.GOLD_ORE, 21193),
                    Use(Items.MITHRIL_ORE, 21194), Use(Items.GRANITE_5KG, 21195), Use(Items.ADAMANTITE_ORE, 21196),
                    Use(Items.RUNITE_ORE, 21197), Use(Items.ELEMENTAL_ORE, 21359), Use(Items.DAEYALT_ORE, 21360),
                ),
            // Beaver: "change the colour freely at any time by using the corresponding log on it. The log will be consumed." (the
            // funky shaped log that unlocks this does not exist here: CUSTOM, granted).
            "Beaver" to
                listOf(
                    Use(Items.LOGS, 13322, consumed = true), Use(Items.OAK_LOGS, 28229, consumed = true),
                    Use(Items.WILLOW_LOGS, 28230, consumed = true), Use(Items.TEAK_LOGS, 28235, consumed = true),
                    Use(Items.MAPLE_LOGS, 28231, consumed = true), Use(Items.MAHOGANY_LOGS, 28236, consumed = true),
                    Use(Items.ARCTIC_PINE_LOGS, 28237, consumed = true), Use(Items.YEW_LOGS, 28232, consumed = true),
                    Use(Items.MAGIC_LOGS, 28233, consumed = true),
                ),
            // Tangleroot: "using various seeds on it. This will consume the seed. To revert ... an acorn".
            "Tangleroot" to
                listOf(
                    Use(Items.ACORN, 20661, consumed = true), Use(Items.GUAM_SEED, 24559, consumed = true),
                    Use(Items.WHITE_LILY_SEED, 24561, consumed = true),
                ),
            // Rocky: redberries -> Red, poison ivy berries -> Ziggy, white berries revert.
            "Rocky" to listOf(Use(Items.REDBERRIES, 24847), Use(Items.POISON_IVY_BERRIES, 24849), Use(Items.WHITE_BERRIES, 20663)),
            // Phoenix: "using 250 of the desired gnomish firelighters on it while it is following ... unlocks that colour permanently".
            "Phoenix" to
                listOf(
                    Use(Items.BLUE_FIRELIGHTER, 24484, 250, consumed = true, followerOnly = true),
                    Use(Items.GREEN_FIRELIGHTER, 24483, 250, consumed = true, followerOnly = true),
                    Use(Items.PURPLE_FIRELIGHTER, 24486, 250, consumed = true, followerOnly = true),
                    Use(Items.WHITE_FIRELIGHTER, 24485, 250, consumed = true, followerOnly = true),
                ),
        )

    /** Gull: "Using 50 raw or cooked fish on Gull will unlock ... Gulliver, with a one-time unique instance of Gull transforming". */
    const val GULL = 31285
    const val GULLIVER = 31287
    const val GULL_FISH_NEEDED = 50

    /** Rift guardian colour per rune crafted (RuneLite gameval ItemID SKILLPETRUNECRAFTING_*); fire is the base variant. */
    val RIFT_BY_RUNE: Map<Rune, Int> =
        mapOf(
            Rune.FIRE to 20665, Rune.AIR to 20667, Rune.MIND to 20669, Rune.WATER to 20671, Rune.EARTH to 20673, Rune.BODY to 20675,
            Rune.COSMIC to 20677, Rune.CHAOS to 20679, Rune.NATURE to 20681, Rune.LAW to 20683, Rune.DEATH to 20685,
            Rune.ASTRAL to 20689, Rune.BLOOD to 20691,
        )
    const val RIFT_GUARDIAN = "Rift guardian"

    /** OSRS item ids of the variants that need an unlock (everything else is unlocked by default, see the class comment). */
    private val gated: Set<Int> by lazy {
        USES.values.flatten().map { it.target }.toSet() + GULLIVER + RIFT_BY_RUNE.values - Pets.variants.groupBy { it.pet }.values.map { it.first().osrsItem }.toSet()
    }

    private fun unlockedSet(player: Player): MutableSet<Int> =
        player.attr[UNLOCKED_ATTR].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toMutableSet()

    /**
     * Owner decision 2026-10-25 (HOOFDLIJST K02 BESLUITEN, N401 "rift guardian kleur veranderen lukt niet"): Metamorphosis offers every
     * form of the pet - also the ones OSRS unlocks with an item or a skill here (rift guardian colours, Gulliver, ores, logs, seeds,
     * berries, firelighters); those items still change the pet directly. Only forms of the same pet, never another pet.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isUnlocked(
        player: Player,
        variant: Pets.Variant,
    ): Boolean = true

    /** Unlocks [variant]; true when it was new. */
    fun unlock(
        player: Player,
        variant: Pets.Variant,
    ): Boolean {
        if (variant.osrsItem < 0 || variant.osrsItem !in gated) return false
        val set = unlockedSet(player)
        if (!set.add(variant.osrsItem)) return false
        player.attr[UNLOCKED_ATTR] = set.sorted().joinToString(",")
        return true
    }

    /** The unlocked variant after [item] in its pet's cycle (itself when nothing else is unlocked). */
    fun nextUnlocked(
        player: Player,
        item: Int,
    ): Pets.Variant? {
        val v = Pets.variantOfItem(item) ?: return null
        val all = Pets.variantsOf(v.pet)
        val start = all.indexOf(v)
        for (step in 1..all.size) {
            val next = all[(start + step) % all.size]
            if (next === v || isUnlocked(player, next)) return next
        }
        return v
    }

    private fun variantByOsrs(
        pet: String,
        osrsItem: Int,
    ): Pets.Variant? = Pets.variantsOf(pet).firstOrNull { it.osrsItem == osrsItem }

    /** [item] used on the follower ([onFollower]) or on the pet item in [slot]; true when the item meant something to this pet. */
    fun useItem(
        player: Player,
        petItem: Int,
        item: Int,
        onFollower: Boolean,
        slot: Int = -1,
    ): Boolean {
        val pet = Pets.variantOfItem(petItem)?.pet ?: return false
        if (pet == "Gull (pet)" && onFollower && isFish(player, item)) return feedGull(player, item)
        val use = USES[pet]?.firstOrNull { it.item == item } ?: return false
        if (use.followerOnly && !onFollower) {
            player.message("Your pet needs to be following you for that.")
            return true
        }
        val target = variantByOsrs(pet, use.target) ?: return false
        if (target.item == petItem) return true
        if (player.inventory.getItemCount(item) < use.count) {
            player.message("You need ${use.count} of those to do that.")
            return true
        }
        if (use.consumed && !player.inventory.remove(item, use.count, assureFullRemoval = true).hasSucceeded()) return true
        unlock(player, target)
        if (onFollower) {
            Pets.morphFollower(player, target)
        } else if (slot >= 0 && player.inventory[slot]?.id == petItem) {
            // N436: the new form keeps the pet's "Upgraded" item while a trinket is bound.
            player.inventory[slot] = Item(Pets.shownItem(player, target), 1)
        }
        return true
    }

    private fun isFish(
        player: Player,
        item: Int,
    ): Boolean {
        if (item in COOKED_FISH) return true
        // The fishing table also holds its junk (casket, oyster, seaweed, boots ...): only its "Raw ..." items are fish.
        return item in RAW_FISH && player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name.lowercase().startsWith("raw ")
    }

    /** One fish per use (OSRS 2025-12-10: Gull no longer eats whole stacks); the 50th unlocks Gulliver and turns Gull into it once. */
    private fun feedGull(
        player: Player,
        item: Int,
    ): Boolean {
        if (!player.inventory.remove(item, 1, assureFullRemoval = true).hasSucceeded()) return true
        val fed = (player.attr[GULL_FED_ATTR] ?: 0) + 1
        player.attr[GULL_FED_ATTR] = fed
        val gulliver = variantByOsrs("Gull (pet)", GULLIVER) ?: return true
        if (fed >= GULL_FISH_NEEDED && unlock(player, gulliver)) Pets.morphFollower(player, gulliver)
        return true
    }

    /** Runes crafted at an altar with the rift guardian following: it takes that altar's colour (unless locked) and unlocks it. */
    fun onRunecraft(
        player: Player,
        rune: Rune,
    ) {
        val out = Pets.itemOf(player)?.let { Pets.variantOfItem(it) } ?: return
        if (out.pet != RIFT_GUARDIAN || Pets.current(player) == null) return
        if ((player.attr[RIFT_LOCK_ATTR] ?: 0) == 1) return
        val target = variantByOsrs(RIFT_GUARDIAN, RIFT_BY_RUNE[rune] ?: return) ?: return
        if (unlock(player, target)) player.message(MSG_RIFT_UNLOCK)
        Pets.morphFollower(player, target)
    }

    /** OSRS "Locking": keeps the rift guardian's colour when runes are crafted. The toggle's wording is not sourced (CUSTOM). */
    fun toggleRiftLock(player: Player) {
        val locked = (player.attr[RIFT_LOCK_ATTR] ?: 0) == 1
        player.attr[RIFT_LOCK_ATTR] = if (locked) 0 else 1
        player.message(if (locked) "Your rift guardian will now change colour at the altars." else "Your rift guardian's colour is now locked.")
    }

    /** The rift guardian a runecrafting pet roll gives: the colour of the altar used (OSRS Wiki Rift guardian). */
    fun riftGuardianFor(rune: Rune): Int? = RIFT_BY_RUNE[rune]?.let { variantByOsrs(RIFT_GUARDIAN, it) }?.item

    /** Raw and cooked fish: Gull eats either. */
    private val RAW_FISH: Set<Int> by lazy { gg.rsmod.plugins.content.skills.fishing.Fish.values().map { it.id }.toSet() }
    private val COOKED_FISH: Set<Int> =
        setOf(
            Items.SHRIMPS, Items.SARDINE, Items.HERRING, Items.ANCHOVIES, Items.MACKEREL, Items.COD, Items.BASS, Items.TROUT, Items.PIKE,
            Items.SALMON, Items.TUNA, Items.SWORDFISH, Items.LOBSTER, Items.SHARK, Items.MONKFISH, Items.COOKED_KARAMBWAN, Items.RAINBOW_FISH,
            Items.CAVE_EEL, Items.LAVA_EEL, Items.CAVEFISH, Items.CRAYFISH,
        )
}
