package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.CURRENT_SHOP_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopCurrency
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openShop
import gg.rsmod.plugins.content.mechanics.shops.ShopPageGroups

/**
 * N382 (owner 2026-10-24: "Perdu Lost Property moet het OSRS Trade-winkelvenster zijn, niet een chatmenu"): Perdu's Trade opens the
 * shop window with this player's own lost items ([PerduReclaim.paidOffers]: the untradeables a PvP death took and the OSRS Lost
 * Property items he has earned and lost), each at its price under the cell. Buying one pays carried coins first, then the bank
 * ([PerduReclaim.claim], OSRS Wiki "Perdu"), hands it over and takes the row away. Perdu buys nothing. More than one window of
 * items ([PAGE_SIZE]) gets the Prev / Next page row. Lost pets are free and handed back when talked to ([PerduReclaim.returnPets]).
 */
object PerduShop {
    const val NAME = "Perdu's Lost Property"

    /** Rows per window: the most the shop window draws ([Shop.CLIENT_VISIBLE_SLOTS]). */
    const val PAGE_SIZE = Shop.CLIENT_VISIBLE_SLOTS

    /** The offers behind the rows of the open window (transient). */
    private val SHOWN = AttributeKey<List<PerduReclaim.Offer>>()
    private val PAGE = AttributeKey<Int>()

    init {
        ShopPageGroups.registerDynamic(
            NAME,
            object : ShopPageGroups.DynamicPages {
                override fun label(player: Player): String? {
                    val pages = pages(player)
                    return if (pages <= 1) null else "Page ${(player.attr[PAGE] ?: 0) + 1} / $pages"
                }

                override fun turn(
                    player: Player,
                    delta: Int,
                ) {
                    val to = ((player.attr[PAGE] ?: 0) + delta).coerceIn(0, pages(player) - 1)
                    if (to != (player.attr[PAGE] ?: 0)) open(player, to)
                }
            },
        )
    }

    private fun pages(player: Player): Int = (PerduReclaim.paidOffers(player).size + PAGE_SIZE - 1) / PAGE_SIZE

    /** Opens the window on [page]; false when Perdu has nothing of [player]'s to sell. */
    fun open(
        player: Player,
        page: Int = 0,
    ): Boolean {
        PerduLostProperty.observe(player)
        val all = PerduReclaim.paidOffers(player)
        if (all.isEmpty()) return false
        val shown = page.coerceIn(0, (all.size - 1) / PAGE_SIZE)
        val shop = Shop(NAME, StockType.NORMAL, PurchasePolicy.BUY_NONE, Currency, arrayOfNulls<ShopItem>(PAGE_SIZE), emptyArray(), false)
        player.attr[PAGE] = shown
        fill(player, shop, all)
        player.openShop(shop)
        return true
    }

    /** Puts the offers of the current page into [shop]'s rows; an emptied row stays empty. */
    private fun fill(
        player: Player,
        shop: Shop,
        all: List<PerduReclaim.Offer>,
    ) {
        val page = (player.attr[PAGE] ?: 0).coerceIn(0, maxOf(0, (all.size - 1) / PAGE_SIZE))
        val rows = all.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        player.attr[SHOWN] = rows
        for (i in shop.items.indices) {
            val offer = rows.getOrNull(i)
            shop.items[i] = offer?.let { ShopItem(it.itemId, it.amount, sellPrice = it.price.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        }
        player.shopDirty = true
    }

    private fun name(
        world: World,
        id: Int,
    ): String = world.definitions.getNullable(ItemDef::class.java, id)?.name ?: "Item $id"

    /** Perdu's coins: the prices of [PerduReclaim]; nothing is sold to him. */
    object Currency : ShopCurrency {
        override val currencyItem: Int = Items.COINS_995

        override fun onSellValueMessage(
            p: Player,
            shopItem: ShopItem,
            freeItem: Boolean,
        ) {
            p.message("${name(p.world, shopItem.item)}: Perdu will give it back for ${PerduReclaim.priceText(priceOf(p.world, shopItem).toLong())}.")
        }

        override fun onBuyValueMessage(
            p: Player,
            shop: Shop,
            item: Int,
        ) {
            p.message("Perdu doesn't buy items - he only returns things you've lost.")
        }

        override fun getSellPrice(
            world: World,
            item: Int,
        ): Int = 0

        override fun getBuyPrice(
            stock: Int,
            world: World,
            item: Int,
        ): Int = 0

        override fun sellToPlayer(
            p: Player,
            shop: Shop,
            slot: Int,
            amt: Int,
        ) {
            val offer = p.attr[SHOWN]?.getOrNull(slot) ?: return
            if (shop.items.getOrNull(slot)?.item != offer.itemId) return
            // One lost item per click: a row is one thing Perdu kept (a stack of runes is one row).
            val refusal = PerduReclaim.claim(p, offer)
            if (refusal != null) {
                p.message(refusal)
                return
            }
            p.message("Perdu hands you your ${name(p.world, offer.itemId).replaceFirstChar { it.lowercase() }}.")
            val rest = PerduReclaim.paidOffers(p)
            if (rest.isEmpty()) {
                p.closeInterface(620)
                p.message("Perdu has nothing else of yours.")
                return
            }
            if (p.attr[CURRENT_SHOP_ATTR] === shop) {
                fill(p, shop, rest)
                ShopPageGroups.render(p, shop)
            }
        }

        override fun giveToPlayer(
            p: Player,
            shop: Shop,
            slot: Int,
            amt: Int,
        ) {}

        override fun buyFromPlayer(
            p: Player,
            shop: Shop,
            slot: Int,
            amt: Int,
        ) {
            p.message("Perdu doesn't buy items - he only returns things you've lost.")
        }
    }
}
