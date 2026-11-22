package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.playSound

/*
 * PROJECT48 O10 (run 4): dead inventory options of obtainable items (census 22-11, work\claude-scripts\run4\o10_class.tsv),
 * each with its OSRS Wiki source. Readable transcripts live in items\books\ReadableDocuments.kt.
 *  - Bloom (wiki "Bloom", "Silver sickle (b)", "Rotting log", "Small bush"): 1-6 Prayer points, not at 0 Prayer; every rotting log,
 *    rotting branch and small bush next to the player blooms; Pick (branch: Take-cutting, wiki infobox) gives 1 fungus/stem/pear and the loc returns to its rotting form.
 *    Cache ids = OSRS gameval LOG/BRANCH/PEARTREE_DRUIDICSPIRIT(2) 3508-3513, anim DRUIDICSPIRIT_HUMAN_BLOOM 1100,
 *    spotanims 263 (loc) / 264 (player), sounds CAST_BLOOM 1493 and BLOOM_MUSHROOM/BRANCH/PEARS 1491/1489/1492.
 *    The 667 Ivandis flail (13117-13146) is not ported: its anim (OSRS 8008) differs in this cache.
 *  - Druid pouch Fill (wiki "Druid pouch"): three blossomed items, pears before stems before fungi; charges 3/2/1 each;
 *    "You need at least three blossomed items to add something to the druid pouch." Ghasts do not use the charges yet (BUGLIJST GHAST-POUCH).
 *  - Poison karambwan Eat (wiki): "You eat the Karambwan." + "It tastes ... painful."; a 5 poison hitsplat only at 6+ Hitpoints, no poison.
 *  - Cave nightshade Eat (wiki): the player says "Ahhhh! What have I done!", after Continue 15 damage (floor(HP/2) under 20 HP), never lethal.
 *  - Sealed message / Haemalchemy volume 1 Read (wiki): their one fixed line. Beacon ring Summon: the post-quest line (wiki).
 */

val DRUID_POUCH_CHARGES = AttributeKey<Int>("druid_pouch_charges")

val BLOOMS = mapOf(Objs.ROTTING_LOG to Objs.FUNGI_ON_LOG, Objs.ROTTING_BRANCH to Objs.BUDDING_BRANCH, Objs.A_SMALL_BUSH to Objs.A_GOLDEN_PEAR_BUSH)
val BLOOM_SOUND = mapOf(Objs.ROTTING_LOG to Sfx.BLOOM_MUSHROOM, Objs.ROTTING_BRANCH to Sfx.BLOOM_BRANCH, Objs.A_SMALL_BUSH to Sfx.BLOOM_PEARS)
val PICKS = mapOf(
    Objs.FUNGI_ON_LOG to Triple("pick", Items.MORT_MYRE_FUNGUS, Objs.ROTTING_LOG),
    Objs.BUDDING_BRANCH to Triple("take-cutting", Items.MORT_MYRE_STEM, Objs.ROTTING_BRANCH),
    Objs.A_GOLDEN_PEAR_BUSH to Triple("pick", Items.MORT_MYRE_PEAR, Objs.A_SMALL_BUSH),
)

fun bloom(p: gg.rsmod.game.model.entity.Player) {
    if (p.skills.getCurrentLevel(Skills.PRAYER) <= 0) {
        p.message("You need to recharge your Prayer points at an altar.") // no OSRS source for this line (BUGLIJST P48-TEKST)
        return
    }
    p.animate(Anims.HUMANOID_1100)
    p.graphic(Gfx.GFX_264)
    p.playSound(Sfx.CAST_BLOOM)
    // setCurrentLevel, not decrementCurrentLevel: alterCurrentLevel never goes below 1, and Bloom can drain Prayer to 0 (wiki).
    p.skills.setCurrentLevel(Skills.PRAYER, maxOf(0, p.skills.getCurrentLevel(Skills.PRAYER) - world.random(1..6)))
    val found = mutableListOf<GameObject>()
    for (dx in -1..1) for (dz in -1..1) {
        if (dx == 0 && dz == 0) continue
        val t = p.tile.transform(dx, dz)
        world.chunks.getOrCreate(t)
            .getEntities<GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
            .filterTo(found) { it.id in BLOOMS }
    }
    found.forEach { obj ->
        world.spawn(DynamicObject(BLOOMS.getValue(obj.id), obj.type, obj.rot, obj.tile))
        world.spawn(TileGraphic(obj.tile, Gfx.GFX_263, 0))
        p.playSound(BLOOM_SOUND.getValue(obj.id))
    }
}

on_item_option(Items.SILVER_SICKLE_B, "bloom") { bloom(player) }
on_equipment_option(Items.SILVER_SICKLE_B, "bloom") { bloom(player) }

PICKS.forEach { (bloomed, pick) ->
    on_obj_option(obj = bloomed, option = pick.first) {
        val obj = player.getInteractingGameObj()
        if (player.inventory.isFull) {
            player.message("You don't have enough inventory space.")
            return@on_obj_option
        }
        player.animate(Anims.REACH_DOWN) // HUMAN_PICKUPFLOOR 827; the wiki names no pick anim
        player.inventory.add(pick.second)
        world.spawn(DynamicObject(pick.third, obj.type, obj.rot, obj.tile))
    }
}

val BLOSSOMED = listOf(Items.MORT_MYRE_PEAR to 3, Items.MORT_MYRE_STEM to 2, Items.MORT_MYRE_FUNGUS to 1)

fun fillPouch(p: gg.rsmod.game.model.entity.Player) {
    val picked = mutableListOf<Pair<Int, Int>>()
    for ((item, charges) in BLOSSOMED) {
        repeat(p.inventory.getItemCount(item)) { if (picked.size < 3) picked += item to charges }
    }
    if (picked.size < 3) {
        p.message("You need at least three blossomed items to add something to the druid pouch.")
        return
    }
    val pouch = if (p.inventory.contains(Items.DRUID_POUCH_2958)) Items.DRUID_POUCH_2958 else Items.DRUID_POUCH
    if (!p.inventory.contains(pouch)) return
    picked.forEach { p.inventory.remove(it.first) }
    p.attr[DRUID_POUCH_CHARGES] = (p.attr[DRUID_POUCH_CHARGES] ?: 0) + picked.sumOf { it.second }
    if (pouch == Items.DRUID_POUCH) {
        val slot = p.inventory.getItemIndex(Items.DRUID_POUCH, skipAttrItems = false)
        p.inventory.remove(Items.DRUID_POUCH, beginSlot = slot)
        p.inventory.add(Items.DRUID_POUCH_2958, beginSlot = slot)
    }
}

listOf(Items.DRUID_POUCH, Items.DRUID_POUCH_2958).forEach { pouch ->
    on_item_option(pouch, "fill") { fillPouch(player) }
    BLOSSOMED.forEach { (item, _) -> on_item_on_item(item, pouch) { fillPouch(player) } }
}

on_item_option(Items.POISON_KARAMBWAN, "eat") {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != Items.POISON_KARAMBWAN || player.timers.has(FOOD_DELAY)) return@on_item_option
    if (!player.inventory.remove(Items.POISON_KARAMBWAN, beginSlot = slot).hasSucceeded()) return@on_item_option
    player.timers[FOOD_DELAY] = 3
    player.animate(Anims.EAT_FOOD)
    player.playSound(Sfx.EAT)
    player.message("You eat the Karambwan.")
    player.message("It tastes ... painful.")
    if (player.skills.getCurrentLevel(Skills.CONSTITUTION) >= 6) player.hit(5, HitType.POISON)
}

on_item_option(Items.CAVE_NIGHTSHADE, "eat") {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != Items.CAVE_NIGHTSHADE) return@on_item_option
    if (!player.inventory.remove(Items.CAVE_NIGHTSHADE, beginSlot = slot).hasSucceeded()) return@on_item_option
    player.animate(Anims.EAT_FOOD)
    player.playSound(Sfx.EAT)
    player.queue {
        chatPlayer("Ahhhh! What have I done!")
        val hp = player.skills.getCurrentLevel(Skills.CONSTITUTION)
        val damage = minOf(if (hp >= 20) 15 else hp / 2, hp - 1)
        if (damage > 0) player.hit(damage)
    }
}

on_item_option(Items.SEALED_MESSAGE, "read") { player.message("The message is sealed and addressed to Veliaf. It would be rude of you to open it.") }
on_item_option(Items.HAEMALCHEMY, "read") { player.message("This book seems far too complex to you at the moment.") }

// Wiki "Beacon ring": after What Lies Below the ring no longer summons Zaff (the quest is not playable here, so always this line).
on_item_option(Items.BEACON_RING, "summon") { player.message("Zaff has removed the summon charges from the ring. It will not summon him again.") }
