package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/** Inline red, the only red this revision's chatbox has - see [BeastOfBurden.release]. */
private const val RECOVERY_RED = "<col=ff0000>"

/**
 * Familiar item storage: the nine beasts of burden and the twenty-two foragers.
 *
 * Capacities are the ledger's, not this file's - the beast-of-burden keys carry the sourced
 * 3/6/9/12/7/7/18/7/30 and every forager carries the sourced 30, with
 * [SummoningLedger.validate] failing the server on any disagreement. The two contracts differ in
 * direction only: a beast of burden accepts deposits, a forager is withdraw-only.
 *
 * Reuses the existing generic [Player.containers]/[ContainerKey]/`register_container_key`
 * persistence mechanism ([gg.rsmod.game.service.serializer.json.JsonPlayerSerializer] already
 * saves/loads any registered container generically) rather than adding bespoke fields to
 * [Player] - the same pattern [gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY] already
 * establishes for a plugin-owned secondary container.
 *
 * No graphical deposit/withdraw interface is built here: real RS's BoB interface component
 * layout is a specific 667-era cache interface this session has not located or verified an ID
 * for, and the work order explicitly forbids guessing interface/component IDs. Deposit is
 * instead exposed the same real, non-graphical way the old Death's Office recovery was
 * exposes death-recovery: using an inventory item on the familiar deposits it (matching real
 * RS's own "drag item onto your pack animal" mechanic - wired in `familiar.plugin.kts`), and
 * the familiar's existing Renew/Dismiss/Cancel interact menu gains a "Withdraw-all" option.
 */
object BeastOfBurden {
    /** Novite 667 BeastOfBurden.store, sent when the familiar's inventory has no room (was an invented line). */
    const val NO_SPACE_MESSAGE = "Not enough space in your familiar inventory."

    /**
     * [withdrawOnly] separates the two carrying contracts the knowledge base describes. A beast
     * of burden takes items the player hands it; a forager "will find certain items from time to
     * time, and can carry up to 30. You are only able to 'Withdraw' items from these familiars."
     */
    data class Storage(val key: ContainerKey, val essenceOnly: Boolean = false, val withdrawOnly: Boolean = false)

    val THORNY_SNAIL_KEY = ContainerKey("bob_thorny_snail", capacity = 3, stackType = ContainerStackType.NORMAL)
    val SPIRIT_KALPHITE_KEY = ContainerKey("bob_spirit_kalphite", capacity = 6, stackType = ContainerStackType.NORMAL)
    val BULL_ANT_KEY = ContainerKey("bob_bull_ant", capacity = 9, stackType = ContainerStackType.NORMAL)
    val SPIRIT_TERRORBIRD_KEY = ContainerKey("bob_spirit_terrorbird", capacity = 12, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_PARASITE_KEY = ContainerKey("bob_abyssal_parasite", capacity = 7, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_LURKER_KEY = ContainerKey("bob_abyssal_lurker", capacity = 12, stackType = ContainerStackType.NORMAL)
    val WAR_TORTOISE_KEY = ContainerKey("bob_war_tortoise", capacity = 18, stackType = ContainerStackType.NORMAL)
    val ABYSSAL_TITAN_KEY = ContainerKey("bob_abyssal_titan", capacity = 20, stackType = ContainerStackType.NORMAL)
    val PACK_YAK_KEY = ContainerKey("bob_pack_yak", capacity = 30, stackType = ContainerStackType.NORMAL)

    private val beastOfBurdenStorage = mapOf(
        SummoningPouchData.THORNY_SNAIL to Storage(THORNY_SNAIL_KEY),
        SummoningPouchData.SPIRIT_KALPHITE to Storage(SPIRIT_KALPHITE_KEY),
        SummoningPouchData.BULL_ANT to Storage(BULL_ANT_KEY),
        SummoningPouchData.SPIRIT_TERRORBIRD to Storage(SPIRIT_TERRORBIRD_KEY),
        SummoningPouchData.ABYSSAL_PARASITE to Storage(ABYSSAL_PARASITE_KEY, essenceOnly = true),
        SummoningPouchData.ABYSSAL_LURKER to Storage(ABYSSAL_LURKER_KEY, essenceOnly = true),
        SummoningPouchData.WAR_TORTOISE to Storage(WAR_TORTOISE_KEY),
        SummoningPouchData.ABYSSAL_TITAN to Storage(ABYSSAL_TITAN_KEY, essenceOnly = true),
        SummoningPouchData.PACK_YAK to Storage(PACK_YAK_KEY),
    )

    /**
     * Every forager's own store, one per familiar, built from the ledger rather than listed by
     * hand: the capacity is the sourced 30 [SummoningCatalogue] carries for all of them, so a
     * forager can never end up with a store the ledger disagrees with.
     *
     * This is also where the albino rat's Cheese Feast cheese goes. The knowledge base classes
     * the rat as a forager that "stores cheese after scroll use", so it needs no special-case
     * container of its own - it is a forager whose forage happens to come from a scroll.
     */
    private val foragerStorage =
        SummoningCatalogue
            .inCategory(FamiliarCategory.FORAGER)
            .associate { entry ->
                entry.pouch to
                    Storage(
                        ContainerKey(
                            "forager_${entry.pouch.name.lowercase()}",
                            capacity = entry.inventory.capacity,
                            stackType = ContainerStackType.NORMAL,
                        ),
                        withdrawOnly = true,
                    )
            }

    private val storageByPouch = beastOfBurdenStorage + foragerStorage

    /**
     * N423: the pack of a pet with a bound burden trinket, one per trinket size (6/9/12/18/30, the familiar it is named after).
     * Only the pet that is out carries anything; a bigger trinket moves the pack along ([moveUpgradedCargo]).
     */
    val PET_KEYS: Map<BurdenTrinkets.Tier, ContainerKey> =
        BurdenTrinkets.Tier.values().associateWith { ContainerKey("bob_pet_${it.slots}", capacity = it.slots, stackType = ContainerStackType.NORMAL) }

    val allKeys = storageByPouch.values.map { it.key } + PET_KEYS.values

    /** N503 D17: the largest pack a pet can have (the Pack yak trinket), so every pet's own pack fits any follower pack. */
    const val PET_PACK_CAPACITY = 30

    /**
     * N503 D17 (owner 2026-11-01: "upgraded pet oppakken: items blijven in de pet"): every pet has its own pack, keyed by the pet's
     * base item (one per pet, not per trinket size like [PET_KEYS]). While the pet is out its items are in the follower pack of its
     * trinket size; Pick-up, dismiss, Shoo and a monster death put them in here ([stowPetCargo]), and the pet coming out again
     * takes them back ([unstowPetCargo]). Registered for every pet by `pets.plugin.kts`, so they are saved with the account and
     * counted as owned (Possessions).
     */
    fun petPackKey(baseItem: Int): ContainerKey = ContainerKey("$PET_PACK_PREFIX$baseItem", capacity = PET_PACK_CAPACITY, stackType = ContainerStackType.NORMAL)

    const val PET_PACK_PREFIX = "bob_petpack_"

    /** The follower pack (its trinket size) and the own pack of the pet with item [petItem]; null for a plain pet. */
    private fun petPacks(
        player: Player,
        petItem: Int?,
    ): Pair<ContainerKey, ContainerKey>? {
        val pet = petItem?.let { Pets.variantOfItem(it)?.pet } ?: return null
        val tier = BurdenTrinkets.upgradeOf(player, pet) ?: return null
        val own = Pets.baseItem(pet)?.let { petPackKey(it) } ?: return null
        return PET_KEYS.getValue(tier) to own
    }

    /** Moves every stack of [from] into [to] (attributes kept); what does not fit stays. Returns the number of stacks moved whole. */
    private fun moveAll(
        from: ItemContainer,
        to: ItemContainer,
    ): Int {
        var moved = 0
        for (slot in 0 until from.capacity) {
            val item = from[slot] ?: continue
            val added = to.addPreservingAttr(item, assureFullInsertion = false).completed
            from[slot] = if (added >= item.amount) null else Item(item, item.amount - added)
            if (added >= item.amount) moved++
        }
        return moved
    }

    /**
     * D17: the pet [petItem] (the one out) leaves - its follower pack goes into its own pack. Works without the npc (a login that
     * puts the pet in the backpack). Returns how many stacks it keeps.
     */
    fun stowPetCargo(
        player: Player,
        petItem: Int? = Pets.itemOf(player),
    ): Int {
        val (follower, own) = petPacks(player, petItem) ?: return 0
        val held = player.containers[follower] ?: return 0
        if (held.rawItems.none { it != null }) return 0
        val moved = moveAll(held, container(player, own))
        synchronise(player)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "pet pack stowed: $moved stacks into ${own.name} for ${player.username}" }
        return moved
    }

    /** D17: the upgraded pet that just came out takes its own pack back into its follower pack. Returns the stacks left behind. */
    fun unstowPetCargo(player: Player): Int {
        if (Familiar.current(player) != null) return 0
        val (follower, own) = petPacks(player, Pets.itemOf(player)) ?: return 0
        val stored = player.containers[own] ?: return 0
        if (stored.rawItems.none { it != null }) return 0
        moveAll(stored, container(player, follower))
        val left = stored.rawItems.count { it != null }
        if (left > 0) player.message("Your pet can't carry everything it has stored: $left ${if (left == 1) "item stays" else "items stay"} with it until it has room.")
        synchronise(player)
        return left
    }

    /** D17: how many stacks [pet]'s own pack holds (0 when the pet has none). */
    fun petPackCount(
        player: Player,
        pet: String,
    ): Int {
        val base = Pets.baseItem(pet) ?: return 0
        return player.containers[petPackKey(base)]?.rawItems?.count { it != null } ?: 0
    }

    /**
     * D19 (owner 2026-11-01: "melding bij inloggen"): how many pets carry items - each pet's own pack, plus the follower pack of the
     * upgraded pet that is out.
     */
    fun petsCarrying(player: Player): Int {
        val stowed = player.containers.entries.count { (key, held) -> key.name.startsWith(PET_PACK_PREFIX) && held.rawItems.any { it != null } }
        val out = if (isUpgradedPetOut(player) && storedCount(player) > 0) 1 else 0
        return stowed + out
    }

    /** D19: the login line, or null when no pet carries anything. */
    fun petsCarryingMessage(player: Player): String? =
        when (val n = petsCarrying(player)) {
            0 -> null
            1 -> "1 of your pets still carries items."
            else -> "$n of your pets still carry items."
        }

    /**
     * N503 D16 (owner 2026-11-01, OSRS): a beast of burden that dies while its owner lives drops what it carried on the ground where
     * it died - the owner's own ground items (the normal drop times), not Death's Office.
     */
    fun dropAtFamiliar(
        player: Player,
        tile: gg.rsmod.game.model.Tile,
    ): Int {
        val key = activeKey(player) ?: return 0
        val held = container(player, key)
        var dropped = 0
        for (slot in 0 until held.capacity) {
            val item = held[slot] ?: continue
            player.world.spawn(gg.rsmod.game.model.entity.GroundItem(Item(item), tile, player))
            held[slot] = null
            dropped++
        }
        if (dropped > 0) {
            player.message("$RECOVERY_RED Your familiar's items have fallen to the ground where it died.")
            synchronise(player)
        }
        return dropped
    }

    /** The carrying contract for [pouch], for [SummoningLedger] to check against the ledger. */
    fun storageFor(pouch: SummoningPouchData): Storage? = storageByPouch[pouch]

    private fun storage(player: Player): Storage? {
        val npc = Familiar.current(player) ?: return petStorage(player)
        val pouch = SummoningPouchData.values.firstOrNull { it.npc == npc.id } ?: return null
        return storageByPouch[pouch]
    }

    /** N423: the upgraded pet that is out carries like the beast of burden its trinket is named after. */
    private fun petStorage(player: Player): Storage? {
        if (Pets.current(player) == null) return null
        val tier = BurdenTrinkets.followerTier(player) ?: return null
        return Storage(PET_KEYS.getValue(tier))
    }

    /** True while an upgraded pet (a bound burden trinket) is following - it carries like a beast of burden. */
    fun isUpgradedPetOut(player: Player): Boolean = Familiar.current(player) == null && petStorage(player) != null

    /**
     * N423: a bigger trinket bound to the pet that is out - the pack moves into the bigger pack (it always fits). Called by
     * [BurdenTrinkets.bind] before the binding changes.
     */
    fun moveUpgradedCargo(
        player: Player,
        from: BurdenTrinkets.Tier,
        to: BurdenTrinkets.Tier,
    ) {
        if (from == to) return
        val old = container(player, PET_KEYS.getValue(from))
        val new = container(player, PET_KEYS.getValue(to))
        for (slot in 0 until old.capacity) {
            val item = old[slot] ?: continue
            val added = new.addPreservingAttr(item, assureFullInsertion = false).completed
            old[slot] = if (added >= item.amount) null else Item(item, item.amount - added)
        }
        synchronise(player)
    }

    /**
     * A pet pack left behind with no upgraded pet to carry it (the trinket or the pet gone while logged out): moved to
     * Death's Office like a dismissed familiar's cargo, so nothing is ever stranded. Run at login.
     */
    fun rescueStrandedPetCargo(player: Player) {
        val carrying = petStorage(player)?.key
        PET_KEYS.values.filter { it != carrying }.forEach { key ->
            val held = player.containers[key] ?: return@forEach
            if (held.rawItems.none { it != null }) return@forEach
            moveToDeathsOffice(player, key)
        }
    }
    /** True if [npcId] is a beast of burden - a familiar the player can hand items to. */
    fun isBobNpc(npcId: Int): Boolean = beastOfBurdenStorage.keys.any { it.npc == npcId }

    /**
     * True if [npcId] carries items at all, beast of burden or forager. The "Take Beast of Burden
     * items" button covers both: "If you have a beast of burden or a forager out, you can click
     * this button to transfer any items they are carrying to your own inventory."
     */
    fun isCarrierNpc(npcId: Int): Boolean = storageByPouch.keys.any { it.npc == npcId }

    /** True if [npcId] only lets the player take items out - every forager. */
    fun isWithdrawOnlyNpc(npcId: Int): Boolean =
        storageByPouch.entries.any { (pouch, storage) -> pouch.npc == npcId && storage.withdrawOnly }

    /** True while the familiar out is one of the abyssal ones that carry rune / pure essence only. */
    fun isEssenceOnly(player: Player): Boolean = storage(player)?.essenceOnly == true

    /** The active familiar inventory key, if the summoned familiar can carry items. */
    fun activeKey(player: Player): ContainerKey? = storage(player)?.key

    /**
     * The live container behind the active familiar, or null when nothing that carries items is
     * out. [FamiliarInventory] renders this directly rather than copying it, so the window and the
     * server can never disagree about what the familiar is holding.
     */
    fun activeContainer(player: Player): ItemContainer? = activeKey(player)?.let { container(player, it) }

    /**
     * Every mutation below funnels through here so the graphical window (when open) and the
     * player's backpack are both re-sent in the same breath as the change - the immediate
     * client/server synchronisation the Beast of Burden acceptance criteria require.
     */
    private fun synchronise(player: Player) {
        if (FamiliarInventory.isOpen(player)) {
            FamiliarInventory.refresh(player)
        }
    }

    private fun container(
        player: Player,
        key: ContainerKey,
    ): ItemContainer = player.containers.getOrPut(key) { ItemContainer(player.world.definitions, key) }

    /**
     * Deposits [item] from [player]'s inventory into the active BoB container, best-effort
     * (whatever fits). Returns the amount actually moved; 0 (with a message) if no BoB
     * familiar is out or nothing fit.
     */
    fun deposit(
        player: Player,
        item: Item,
    ): Int {
        if (item.amount <= 0) return 0
        val storage = storage(player) ?: run {
            player.message("You need an active Beast of Burden familiar out to store items with it.")
            return 0
        }
        if (storage.withdrawOnly) {
            player.message("You can only withdraw items from this familiar.")
            return 0
        }
        // N423: an upgraded pet is named as one.
        val who = if (Familiar.current(player) == null) "pet" else "familiar"
        if (gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.isEmblem(item.id)) {
            // One emblem per player, held in the inventory or bank only (owner 2026-09-25).
            player.message("Your $who can't carry a Deadman emblem.")
            return 0
        }
        // N423: a burden trinket never goes into a pack (it is what makes the pet carry). N433 (owner 2026-10-30: "je kunt nu pets
        // storen in een beast of burden dit hoort niet"): nor does a pet, in any familiar's or upgraded pet's pack.
        if (BurdenTrinkets.isTrinket(item.id) || Pets.isPetItem(item.id)) {
            player.message("Your $who can't carry that item.")
            return 0
        }
        // Audit X-01: loot keys are PvP risk by design; they may never be parked in a familiar.
        if (gg.rsmod.plugins.content.mechanics.pvp.LootKeys.isKey(item.id)) {
            player.message("Your $who can't carry that item.")
            return 0
        }
        val key = storage.key
        val essence = item.id == Items.RUNE_ESSENCE || item.id == Items.PURE_ESSENCE
        if (storage.essenceOnly != essence) {
            player.message("Your $who can't carry that item.")
            return 0
        }
        // Item-on-familiar actions can retain an item reference while queued. Recheck
        // ownership here, and never credit more cargo than the inventory can pay for.
        val requested = minOf(item.amount, player.inventory.getItemCount(item.id))
        if (requested <= 0) return 0
        val held = container(player, key)
        // Audit E-06: keep charges/stored contents (item attributes) on the way in.
        val transaction = held.addPreservingAttr(Item(item, requested), assureFullInsertion = false)
        if (transaction.completed <= 0) {
            player.message(NO_SPACE_MESSAGE)
            return 0
        }
        // Novite 667 BeastOfBurden.store: a stack that only partly fits stores what fits and says so.
        if (transaction.completed < requested) player.message(NO_SPACE_MESSAGE)
        val removed = player.inventory.remove(item.id, transaction.completed, assureFullRemoval = true).completed
        if (removed < transaction.completed) {
            held.remove(item.id, transaction.completed - removed, assureFullRemoval = true)
        }
        synchronise(player)
        if (removed > 0) gg.rsmod.plugins.content.mechanics.ItemMoveSounds.putIn(player)
        return removed
    }

    /**
     * Adds [item] directly into the active familiar's storage without taking it from the
     * player's inventory first - for specials that generate items in the familiar itself
     * (e.g. Cheese Feast) rather than depositing something the player already carries.
     * Returns the amount actually added.
     */
    fun grant(player: Player, item: Item): Int {
        val key = activeKey(player) ?: return 0
        val added = container(player, key).add(item.id, item.amount, assureFullInsertion = false).completed
        synchronise(player)
        return added
    }

    /**
     * How many items the active familiar is holding, counting stack sizes.
     *
     * Needed to tell the three "Take BoB" outcomes apart (owner requirement H9): an empty store, a
     * full inventory, and a partial withdrawal all end with [withdrawAll] returning a number that
     * on its own cannot distinguish "nothing to take" from "nowhere to put it".
     */
    fun storedCount(player: Player): Int {
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        return (0 until container.capacity).sumOf { container[it]?.amount ?: 0 }
    }

    /** Withdraws everything from the active BoB container into the inventory, best-effort. */
    fun withdrawAll(player: Player): Int {
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        var withdrawn = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            val transaction = player.inventory.addPreservingAttr(item, assureFullInsertion = false)
            if (transaction.completed <= 0) continue
            container[slot] = if (transaction.completed == item.amount) null else Item(item, item.amount - transaction.completed)
            withdrawn += transaction.completed
        }
        synchronise(player)
        if (withdrawn > 0) takenOut(player)
        return withdrawn
    }

    /** Withdraws [amount] of whatever occupies [slot] in the active BoB container, best-effort. */
    fun withdraw(
        player: Player,
        slot: Int,
        amount: Int,
    ): Int {
        if (amount <= 0) return 0
        val key = activeKey(player) ?: return 0
        val container = container(player, key)
        if (slot !in 0 until container.capacity) return 0
        val item = container[slot] ?: return 0
        val take = minOf(amount, item.amount)
        val transaction = player.inventory.addPreservingAttr(Item(item, take), assureFullInsertion = false)
        if (transaction.completed <= 0) return 0
        container[slot] = if (transaction.completed == item.amount) null else Item(item, item.amount - transaction.completed)
        synchronise(player)
        takenOut(player)
        return transaction.completed
    }

    /**
     * Taking items out of a beast of burden: the take sound (ItemMoveSounds) and the bury-bones reach-down animation 827 -
     * owner 2026-10-06 (bug 025058, OWNER_QUESTIONS Q8: "bob animation is the bury bones animation the same"); no donor
     * (Void, Novite, 2009scape, Darkan) animates it.
     */
    fun takenOut(player: Player) {
        player.animate(gg.rsmod.plugins.api.cfg.Anims.REACH_DOWN)
        gg.rsmod.plugins.content.mechanics.ItemMoveSounds.takeOut(player)
    }

    /** Deposits every permitted inventory item into the active familiar, best-effort. */
    fun depositAll(player: Player): Int {
        val storage = storage(player)
        if (storage == null || storage.withdrawOnly) {
            player.message("You need an active Beast of Burden familiar out to store items with it.")
            return 0
        }
        var deposited = 0
        for (slot in 0 until player.inventory.capacity) {
            val item = player.inventory[slot] ?: continue
            deposited += deposit(player, item)
        }
        synchronise(player)
        return deposited
    }
    /**
     * CUSTOM_SERVER_OVERRIDE - deliberately **not** authentic 2011 behaviour.
     *
     * In 2011 a beast of burden's cargo was simply lost when the familiar went away (the "drop it
     * on the floor" rule is the 22 August 2016 ninja strike, and dropping it here would in any
     * case put it on the ground for anyone to take). The owner's standing design decision for
     * this server is that the cargo is never lost and never floored: it moves into the player's
     * own Death's Office recovery storage instead, and the player is told so in red.
     *
     * Safety properties, in order of importance:
     *
     * * **No duplication and no loss.** Each slot is inserted first and only cleared once the
     *   insert has actually completed; a slot that cannot be inserted is left exactly where it is
     *   rather than being deleted. `assureFullInsertion = false` plus the completed-amount check
     *   means a partially inserted stack leaves the remainder behind instead of vanishing.
     * * **Persistence.** [Player.deathRecovery] is a saved container
     *   ([gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY]), so the cargo survives logout and
     *   a server restart.
     * * **Office fee.** Death charges his normal office fee for it (owner 2026-09-26, death rework);
     *   on the owner's PvM death the cargo goes to the gravestone instead (death.plugin.kts).
     *
     * Called by every path that takes a familiar away while it still holds items - dismiss,
     * expiry, death, replacement and the owner's own death. Logout deliberately does not call it:
     * the same familiar and the same items come back on login.
     */
    fun release(player: Player) {
        moveToDeathsOffice(player)
    }

    /**
     * Same override as [release]; kept as a distinct entry point because [Familiar.ownerDeath]
     * used to destroy the cargo outright and the difference is worth keeping visible.
     */
    fun discard(
        player: Player,
        free: Boolean = false,
    ) {
        moveToDeathsOffice(player, free)
    }

    /**
     * Audit X-01: on a PvP death the familiar's cargo is unprotected loot like everything else the victim
     * carried. Removes and returns every stack (attributes kept) so the death plugin can hand it to the
     * killer through the same loot-key/ground-loot plan; [Familiar.ownerDeath] then finds nothing to rescue.
     */
    fun takeAllCargo(player: Player): List<Item> {
        val key = activeKey(player) ?: return emptyList()
        val held = container(player, key)
        val cargo = mutableListOf<Item>()
        for (slot in 0 until held.capacity) {
            val item = held[slot] ?: continue
            cargo += Item(item)
            held[slot] = null
        }
        if (cargo.isNotEmpty()) synchronise(player)
        return cargo
    }

    /** How many items are currently waiting in Death's Office. */
    fun deathsDomainCount(player: Player): Int =
        (0 until player.deathRecovery.capacity).count { player.deathRecovery[it] != null }

    /** @param free a safe death (safe minigame) costs nothing: the cargo is stored fee-free. */
    private fun moveToDeathsOffice(
        player: Player,
        free: Boolean = false,
    ) {
        moveToDeathsOffice(player, activeKey(player) ?: return, free)
    }

    private fun moveToDeathsOffice(
        player: Player,
        key: ContainerKey,
        free: Boolean = false,
    ) {
        val held = container(player, key)
        var moved = 0
        var stranded = 0
        for (slot in 0 until held.capacity) {
            val item = held[slot] ?: continue
            // Owner 2026-09-26 (death rework): familiar cargo is no longer fee-free - Death's normal office fee applies.
            val stored = gg.rsmod.plugins.content.mechanics.death.DeathsOffice.store(player, item, free = free)
            if (stored <= 0) {
                stranded++
                continue
            }
            held[slot] =
                if (stored == item.amount) {
                    null
                } else {
                    Item(item.id, item.amount - stored).copyAttr(item)
                }
            moved++
        }
        if (moved == 0) {
            return
        }
        // Red, per the owner's specification. This revision has no red chat *type*; every other
        // red line in this codebase is an inline colour tag on an ordinary game message.
        // N423: an upgraded pet's pack goes the same way, named as the pet's.
        val whose = if (key in PET_KEYS.values) "pet" else "familiar"
        player.message("$RECOVERY_RED Your $whose's items have been moved to Death's Office.")
        if (stranded > 0) {
            player.message(
                "$RECOVERY_RED Death's Office is full - $stranded of your $whose's items could not be stored.",
            )
        }
        synchronise(player)
    }

    /** Non-null (slot, item) pairs in the active BoB container, for building a withdraw-selection prompt. */
    fun contents(player: Player): List<IndexedValue<Item>> {
        val key = activeKey(player) ?: return emptyList()
        val container = container(player, key)
        return (0 until container.capacity).mapNotNull { slot -> container[slot]?.let { IndexedValue(slot, it) } }
    }
}
