package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanInventory
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig

/** DMM2 V2: cash/tabs remain one-time; reclaimable equipment NEVER contains money or tablets. */
object DeadmanStarterPack {
    // 1 = eligible, 2 = paid/already opened under the old implementation. Missing = not migrated.
    val INTRO_REWARD = AttributeKey<Int>("dmm2_starter_intro_reward")
    val CHRONICLE_CHARGES = AttributeKey<Int>("chronicle_charges")

    /** Actual LOCAL ids, installed by Claude after the cache contract. No source-id fallback. */
    data class Ids(val sword: Int, val bow: Int, val staff: Int, val tuna: Int, val chronicle: Int,
                   val chest: Int, val legs: Int, val cape: Int) {
        init {
            val ids = listOf(sword, bow, staff, tuna, chronicle, chest, legs, cape)
            require(ids.all { it > 0 } && ids.distinct().size == ids.size)
        }
        fun contents(): List<Item> = listOf(
            Item(sword), Item(bow).putAttr(ItemAttribute.CHARGES, 2000),
            Item(staff).putAttr(ItemAttribute.CHARGES, 500), Item(tuna, 12),
            Item(chronicle), Item(chest), Item(legs), Item(cape),
            Item(Items.DRAMEN_STAFF), Item(Items.DEADMANS_SKULL),
        )
    }

    @Volatile var ids: Ids? = null
        private set
    fun install(contract: Ids) { ids = contract }

    enum class Result { OK, ALREADY_GIVEN, ALREADY_OWNED, NOT_READY, NO_PACK, NO_SPACE }

    /** Call before intro/reclaim/open. Legacy unopened packs retain the old reward exactly once. */
    fun migrate(player: Player) {
        if (player.attr.has(INTRO_REWARD)) return
        val oldGrant = player.attr[NewPlayerStart.STARTER_PACK_GIVEN] == true
        val ownsPack = ownsPack(player)
        player.attr[INTRO_REWARD] = when {
            oldGrant -> if (ownsPack) 1 else 2
            player.attr[NEW_ACCOUNT_ATTR] == true -> 1
            else -> 2 // Missing historical flags do not prove an unpaid cash reward.
        }
        player.persistNow()
    }

    private fun ownsPack(player: Player): Boolean = player.containers.values.any {
        it.contains(NewPlayerConfig.current.starterChestItem)
    }

    /** Separate one-time introduction reward, atomic with its marker, inventory then bank fallback. */
    fun grantIntroductionReward(player: Player): Result {
        migrate(player)
        if (player.attr[INTRO_REWARD] == 2) return Result.ALREADY_GIVEN
        val inventory = ItemContainer(player.inventory)
        val bank = ItemContainer(player.bank)
        val rewards = listOf(Item(Items.COINS_995, NewPlayerStart.STARTER_CASH), Item(Items.TELEPORT_TO_HOUSE, 100))
        for (reward in rewards) {
            if (!DeadmanInventory.add(inventory, reward) && !DeadmanInventory.add(bank, reward)) return Result.NO_SPACE
        }
        DeadmanInventory.publish(inventory, player.inventory)
        DeadmanInventory.publish(bank, player.bank)
        player.attr[INTRO_REWARD] = 2
        player.persistNow()
        return Result.OK
    }

    /** Tutors may call repeatedly; an unopened pack anywhere on the account prevents duplicate grants. */
    fun reclaim(player: Player): Result {
        migrate(player) // Must precede creating a new pack: never mistake it for a legacy unpaid pack.
        if (ownsPack(player)) return Result.ALREADY_OWNED
        val pack = Item(NewPlayerConfig.current.starterChestItem)
        val inventory = ItemContainer(player.inventory)
        val bank = ItemContainer(player.bank)
        if (!DeadmanInventory.add(inventory, pack) && !DeadmanInventory.add(bank, pack)) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        DeadmanInventory.publish(bank, player.bank)
        player.persistNow()
        return Result.OK
    }

    fun open(player: Player): Result {
        val contract = ids ?: return Result.NOT_READY
        migrate(player)
        val pack = NewPlayerConfig.current.starterChestItem
        val staged = ItemContainer(player.inventory)
        if (!staged.contains(pack)) return Result.NO_PACK
        // Wiki requires 11 free inventory spaces, regardless of existing stack merges.
        if (staged.freeSlotCount < 11) return Result.NO_SPACE
        if (!staged.remove(pack, assureFullRemoval = true).hasSucceeded()) return Result.NO_PACK
        if (!contract.contents().all { DeadmanInventory.add(staged, it) }) return Result.NO_SPACE
        // PROJECT48 SIGILS: "A random sigil can also be claimed every 24 hours as part of a starter pack" (wiki Deadman: Armageddon).
        // Added to the same staged inventory, so the sigil and its 24-hour mark are given together or not at all.
        val rolled = gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilDrops.rollStarterSigil(player)
        val sigil = rolled?.takeIf { DeadmanInventory.add(staged, Item(it.unattunedId)) }
        DeadmanInventory.publish(staged, player.inventory)
        if (sigil != null) {
            gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilDrops.markStarterSigil(player)
            player.message("Your starter pack also holds the ${sigil.label}. Attune it at a bank.")
        } else if (rolled != null) {
            // No room left beside the pack's own items: nothing is marked, so the next starter pack holds a sigil.
            player.message("You had no room for the sigil of this starter pack. Your next starter pack will hold one.")
        }
        // Chronicle rev 15366319: charges survive destruction/replacement; account-owned, capped at 1,000.
        player.attr[CHRONICLE_CHARGES] = ((player.attr[CHRONICLE_CHARGES] ?: 0).coerceIn(0, 1000) + 10).coerceAtMost(1000)
        player.persistNow()
        // An outstanding legacy pack's one-time reward is paid separately; full containers remain retryable.
        grantIntroductionReward(player)
        return Result.OK
    }

    fun isWeapon(id: Int?): Boolean = ids?.let { id == it.sword || id == it.bow || id == it.staff } == true
    fun isBow(id: Int?): Boolean = ids?.bow?.let { it == id } == true
    fun isStaff(id: Int?): Boolean = ids?.staff?.let { it == id } == true

    /** A charge is spent per actual attack, including a miss. Empty weapons are removed, never recharged. */
    fun charges(item: Item): Int = (item.getAttr(ItemAttribute.CHARGES) ?: 0).coerceAtLeast(0)
    fun afterAttack(item: Item): Item? {
        require(isBow(item.id) || isStaff(item.id))
        return if (charges(item) <= 1) null else Item(item).putAttr(ItemAttribute.CHARGES, charges(item) - 1)
    }
}
