package gg.rsmod.plugins.content.drops

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message

/**
 * DROP-MELDINGEN (owner 24-11 'ja', advice: threshold 10,000 coins, untradeable line always on): the OSRS chat lines of the settings
 * "Loot drop notifications" and "Untradeable loot notifications" for npc drops, in red. Source: DMM video HIisFATzF8c 06:52 -
 * "Valuable drop: 2 x Rune kiteshield (82,148 coins)" (the value is the whole stack) and "Untradeable drop: Sigil of the formidable
 * fighter". The OSRS default threshold is not on the wiki (Settings only gives the 500M maximum): the owner's choice applies.
 */
object DropNotifications {
    const val VALUE_THRESHOLD = 10_000L
    private const val RED = "ef1020"

    fun notify(player: Player, item: Item) {
        val world = player.world
        val raw = world.definitions.getNullable(ItemDef::class.java, item.id) ?: return
        // Fixronde 26-11 B-02: a noted drop (Spindel's 60 x Unicorn horn) has a nameless note def - name it (and judge it) by its item.
        val def = raw.takeIf { it.noted && it.noteLinkId > 0 }?.let { world.definitions.getNullable(ItemDef::class.java, it.noteLinkId) } ?: raw
        val name = (if (item.amount > 1) "${item.amount} x " else "") + def.name
        val untradeable = !def.tradeable || gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigils.isSigilItem(item.id)
        if (untradeable) {
            player.message("<col=$RED>Untradeable drop: $name</col>")
            return
        }
        val value = gg.rsmod.plugins.content.mechanics.exchange.GePrice.stack(world, item)
        if (value >= VALUE_THRESHOLD) player.message("<col=$RED>Valuable drop: $name (${String.format(java.util.Locale.ENGLISH, "%,d", value)} coins)</col>")
    }
}
