package gg.rsmod.plugins.content.items.drop

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.item.Item

/**
 * N215 (owner 2026-10-23: "er moet een drop warning komen bij untradeables net als osrs"). OSRS asks for a confirmation before a
 * valuable drop (OSRS Wiki "Drop": an item worth 30,000 coins or more, 2014 update; Settings > Warnings "Drop item warning"). Here
 * every untradeable item asks too - on this server a dropped untradeable is seen only by its owner and is gone when it despawns.
 */
object DropWarning {
    /** OSRS: a drop of an item (stack) worth this much or more asks first. */
    const val VALUE_THRESHOLD = 30_000L

    fun needsWarning(
        world: World,
        item: Item,
        value: (Int) -> Int = { gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit(world, it) },
    ): Boolean {
        val def = world.definitions.getNullable(ItemDef::class.java, item.id) ?: return false
        if (!def.tradeable && !def.noted) return true
        return value(item.id).toLong() * item.amount >= VALUE_THRESHOLD
    }

    fun question(name: String): String = "Are you sure you want to drop your <col=7f0000>$name</col>?"
}
