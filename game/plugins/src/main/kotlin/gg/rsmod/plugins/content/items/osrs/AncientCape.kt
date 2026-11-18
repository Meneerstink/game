package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.wilderness.MageArenaAssets
import gg.rsmod.plugins.content.areas.wilderness.MageArenaII
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import gg.rsmod.plugins.content.mechanics.trouver.TrouverLockable

/** OSRS Wiki Ancient fabric, Imbued ancient cape, Imbued ancient max cape and Transcript:Ancient fabric. */
object AncientCape {
    class Assets(val item: (Int) -> Int?, val variant: (String) -> Int? = MageArenaAssets::variant) {
        val fabric get() = item(34641)
        val cape get() = item(34633)
        val broken get() = item(34635)
        val locked get() = item(34637)
        val max get() = item(34625)
        val hood get() = item(34627)
        val maxBroken get() = item(34629)
        val lockedBroken get() = variant("ancient-cape-locked-broken")
        val lockedMangled get() = variant("ancient-cape-locked-mangled")
    }
    val assets = Assets(MageArenaAssets::item)
    val GOD_CAPES = setOf(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_ZAMORAK_CAPE)
    val MAX_HOODS = mapOf(
        Items.IMBUED_SARADOMIN_MAX_CAPE to Items.IMBUED_SARADOMIN_MAX_HOOD,
        Items.IMBUED_GUTHIX_MAX_CAPE to Items.IMBUED_GUTHIX_MAX_HOOD,
        Items.IMBUED_ZAMORAK_MAX_CAPE to Items.IMBUED_ZAMORAK_MAX_HOOD,
    )

    enum class Result { CONVERTED, NOT_COMPLETED, INVENTORY_FULL, MISSING_ITEMS, MISSING_HOOD, ASSETS_MISSING }

    /** Task-authorized alternative acquisition: earned MA2 replaces Pete's unlock; repeat free fabric remains OSRS-like. */
    fun fabric(player: Player, assets: Assets = this.assets): Result {
        if (!MageArenaII.completed(player)) return Result.NOT_COMPLETED
        val fabric = assets.fabric ?: return Result.ASSETS_MISSING
        return if (player.inventory.add(fabric).hasSucceeded()) Result.CONVERTED else Result.INVENTORY_FULL
    }

    /** Irreversible cosmetic conversion. No god credit, magic damage bonus or arbitrary fee is created. */
    fun convert(player: Player, capeSlot: Int, assets: Assets = this.assets): Result {
        val fabric = assets.fabric ?: return Result.ASSETS_MISSING
        val inventory = player.inventory
        if (capeSlot !in inventory.rawItems.indices) return Result.MISSING_ITEMS
        val old = inventory[capeSlot]?.id ?: return Result.MISSING_ITEMS
        val requiredHood = MAX_HOODS[old]
        if (old !in GOD_CAPES && requiredHood == null) return Result.MISSING_ITEMS
        val output = (if (requiredHood != null) assets.max else assets.cape) ?: return Result.ASSETS_MISSING
        val outputHood = if (requiredHood != null) assets.hood ?: return Result.ASSETS_MISSING else null
        val fabricSlot = inventory.getItemIndex(fabric, skipAttrItems = false)
        if (fabricSlot == -1) return Result.MISSING_ITEMS
        val hoodSlot = requiredHood?.let { inventory.getItemIndex(it, skipAttrItems = false) }
        if (hoodSlot == -1) return Result.MISSING_HOOD
        // No suspensions after validation. Inputs occupy distinct slots and output replaces those slots, even when full.
        inventory[capeSlot] = Item(output)
        inventory[fabricSlot] = null
        if (hoodSlot != null) inventory[hoodSlot] = Item(outputHood!!)
        return Result.CONVERTED
    }

    fun maxVariant(assets: Assets = this.assets): MaxCapes.Variant? {
        val cape = assets.cape ?: return null
        val max = assets.max ?: return null
        val hood = assets.hood ?: return null
        return MaxCapes.Variant(cape, max, hood)
    }

    /** Same terms as the imbued god capes (PvpDeathBreakables): no coins out of nothing for the killer (owner V3), Perdu repair 96k / 99k. */
    fun breakables(assets: Assets = this.assets): List<PvpDeathBreakables.Breakable> = listOfNotNull(
        assets.cape?.let { cape -> assets.broken?.let { PvpDeathBreakables.Breakable(cape, it, killerCoins = 0, repairCost = 96_000) } },
        assets.max?.let { cape -> assets.maxBroken?.let { PvpDeathBreakables.Breakable(cape, it, killerCoins = 0, repairCost = 99_000) } },
    )

    fun lockables(assets: Assets = this.assets): List<TrouverLockable> = listOfNotNull(
        assets.cape?.let { cape -> assets.locked?.let locked@{ locked ->
            val broken = assets.lockedBroken ?: return@locked null
            val mangled = assets.lockedMangled ?: return@locked null
            TrouverLockable(cape, locked, broken, mangled)
        } },
    )
}
