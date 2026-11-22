package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.plugins.content.areas.wilderness.FountainOfRune.Kind
import gg.rsmod.plugins.content.mechanics.deadman.items.DmmItems

/** O26 PROJECT48 23-11: Fountain of Rune - map placement + recharging; rules and sources in [FountainOfRune]. */

on_world_init {
    FountainOfRune.REMOVED_667.forEach { (id, tile) ->
        world.getObject(tile, ObjectType.INTERACTABLE)?.takeIf { it.id == id }?.let { world.remove(it) }
    }
    world.spawn(DynamicObject(FountainOfRune.FOUNTAIN, 10, 0, FountainOfRune.TILE))
    world.spawn(DynamicObject(FountainOfRune.ENERGY, 10, 0, FountainOfRune.ENERGY_TILE))
}

fun allowed(player: Player, kind: Kind): Boolean =
    when (kind) {
        Kind.GLORY -> gg.rsmod.plugins.content.quests.foundation.bracket.HeroesQuest.isFinished(player)
        Kind.SKILLS_OR_COMBAT -> gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "Legends' Quest")
        Kind.WEALTH -> true
    }

/** The unnoted id of [id] when it is a note the fountain takes (only skills necklaces and combat bracelets may be noted). */
fun chargeable(player: Player, id: Int): Pair<Int, Boolean>? {
    FountainOfRune.CHARGES[id]?.let { return id to false }
    val def = player.world.definitions.getNullable(ItemDef::class.java, id) ?: return null
    if (!def.noted) return null
    val unnoted = def.noteLinkId
    val entry = FountainOfRune.CHARGES[unnoted] ?: return null
    return if (entry.second == Kind.SKILLS_OR_COMBAT) unnoted to true else null
}

fun notedOf(player: Player, id: Int): Int = player.world.definitions.get(ItemDef::class.java, id).noteLinkId

fun recharge(player: Player) {
    var charged = 0
    var eternal = false
    val roll = { kind: Kind, result: Int ->
        if (kind == Kind.GLORY && player.world.random(FountainOfRune.ETERNAL_CHANCE - 1) == 0) {
            eternal = true
            DmmItems.AMULET_OF_ETERNAL_GLORY
        } else result
    }
    for (slot in 0 until player.inventory.capacity) {
        val item = player.inventory[slot] ?: continue
        val (base, noted) = chargeable(player, item.id) ?: continue
        val (result, kind) = FountainOfRune.CHARGES.getValue(base)
        if (!allowed(player, kind)) continue
        if (noted) {
            val amount = item.amount
            if (player.inventory.remove(item.id, amount, beginSlot = slot).hasSucceeded()) {
                player.inventory.add(notedOf(player, result), amount, beginSlot = slot)
                charged += amount
            }
        } else {
            player.inventory[slot] = Item(roll(kind, result))
            charged++
        }
    }
    for (slot in 0 until player.equipment.capacity) {
        val item = player.equipment[slot] ?: continue
        val (result, kind) = FountainOfRune.CHARGES[item.id] ?: continue
        if (!allowed(player, kind)) continue
        player.equipment[slot] = Item(roll(kind, result))
        charged++
    }
    when {
        charged == 0 -> player.message("Nothing interesting happens.")
        eternal -> player.message("The power of the fountain is transferred into an amulet of eternal glory. It will now have unlimited charges.")
        else -> player.message("You feel a power emanating from the fountain as it recharges your jewellery.")
    }
}

// Every piece and every full one (using a full glory still charges the rest), plus the notes of skills necklaces / combat bracelets.
val unnoted = (FountainOfRune.CHARGES.keys + FountainOfRune.CHARGES.values.map { it.first }).distinct()
val notesTaken = (FountainOfRune.CHARGES.filterValues { it.second == Kind.SKILLS_OR_COMBAT }.keys + listOf(FountainOfRune.SKILLS_6, FountainOfRune.COMBAT_6))
    .mapNotNull { world.definitions.getNullable(ItemDef::class.java, it)?.noteLinkId?.takeIf { n -> n > 0 } }
(unnoted + notesTaken).distinct().forEach { id -> on_item_on_obj(FountainOfRune.FOUNTAIN, id) { recharge(player) } }
