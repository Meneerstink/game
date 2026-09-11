package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.attr.DISRUPTION_SHIELD_ATTR
import gg.rsmod.game.model.attr.MAGIC_IMBUE_ATTR
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.plugins.content.skills.summoning.FAMILIAR_LIFETIME_TIMER
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.SpellMetadata
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.magic.SpellbookSwap
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.skills.cooking.CookingData
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData

/**
 * Lunar spellbook (interface 430).
 *
 * Levels and runes come from the production cache (SpellbookData, decoded from the interface
 * component hooks). Animations, graphics, sounds and mechanics are sourced from the 2009scape
 * LunarListeners/HealSpell/VengeanceSpell/MagicImbueSpell handlers (same-era ids) and the 2011
 * RuneScape Wiki for experience values.
 *
 * Out of scope / not implemented here: NPC Contact (needs per-NPC dialogue routing), Borrowed
 * Power, Tune Bane Ore, Repair Rune Pouch, Fertile Soil/Cure Plant/Remote Farm
 * (farming), Boost/Stat Restore Potion Share. Those buttons currently do nothing.
 */

/** Group teleports (Tele Group X) ask each nearby player before moving them. */
private val GROUP_TELEPORTS =
    mapOf(
        "Tele Group Moonclan" to Triple(Tile(2111, 3916, 0), 67.0, "Moonclan Island"),
        "Tele Group Waterbirth" to Triple(Tile(2527, 3739, 0), 72.0, "Waterbirth Island"),
        "Tele Group Barbarian" to Triple(Tile(2544, 3572, 0), 77.0, "the Barbarian Outpost"),
        "Tele Group Khazard" to Triple(Tile(2656, 3157, 0), 81.0, "Port Khazard"),
        "Tele Group Fishing Guild" to Triple(Tile(2611, 3393, 0), 90.0, "the Fishing Guild"),
        "Tele Group Catherby" to Triple(Tile(2804, 3433, 0), 93.0, "Catherby"),
        "Tele Group Ice Plateau" to Triple(Tile(2972, 3873, 0), 99.0, "the Ice Plateau"),
        "Tele Group Trollheim" to Triple(Tile(2831, 3677, 0), 93.0, "Trollheim"),
    )

GROUP_TELEPORTS.forEach { (name, data) ->
    on_magic_spell_button(name) { metadata ->
        val (tile, xp, place) = data
        if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes) || !player.canTeleport(TeleportType.LUNAR)) {
            return@on_magic_spell_button
        }
        MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
        player.addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
        nearbyPlayers(player, radius = 1).forEach { other ->
            other.queue {
                other.message("${player.username} is attempting to teleport you to $place.")
                if (options("Accept the teleport.", "Decline.", title = "${player.username} wishes to teleport you to $place.") == 1 &&
                    other.canTeleport(TeleportType.LUNAR)
                ) {
                    other.teleport(tile, TeleportType.LUNAR)
                }
            }
        }
        player.teleport(tile, TeleportType.LUNAR)
    }
}

/** Players (other than [player]) standing within [radius] tiles. */
fun nearbyPlayers(
    player: Player,
    radius: Int,
): List<Player> {
    val result = mutableListOf<Player>()
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val tile = player.tile.transform(x, z)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
            chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { other ->
                if (other !== player && other.isOnline && other !in result) {
                    result.add(other)
                }
            }
        }
    }
    return result
}

/** Common preflight: level + runes, then consume runes and award [xp]. Returns false when refused. */
fun Player.castLunar(
    metadata: SpellMetadata,
    xp: Double,
    animation: Int,
    graphic: Int,
    height: Int = 0,
    sound: Int = -1,
): Boolean {
    if (!MagicSpells.canCast(this, metadata.lvl, metadata.runes)) {
        return false
    }
    MagicSpells.removeRunes(this, metadata.runes, metadata.sprite)
    if (animation > -1) animate(animation)
    if (graphic > -1) graphic(graphic, height)
    if (sound > -1) playSound(sound)
    addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
    return true
}

fun spell(data: SpellbookData): SpellMetadata {
    if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
    return MagicSpells.getMetadata(data.uniqueId)!!
}

/*
 * Vengeance family.
 */
on_magic_spell_button("Vengeance") { metadata ->
    if (player.skills.getMaxLevel(Skills.DEFENCE) < 40) {
        player.message("You need a Defence level of 40 to cast this spell.")
        return@on_magic_spell_button
    }
    if (Vengeance.isOnCooldown(player)) {
        player.message("You can only cast vengeance spells every 30 seconds.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 112.0, animation = 4410, graphic = 726, height = 96, sound = Sfx.LUNAR_VENGENCE)) {
        Vengeance.activate(player)
    }
}

on_spell_on_player(430, SpellbookData.VENGEANCE_OTHER.component) {
    val target = player.getInteractingPlayer()
    val metadata = spell(SpellbookData.VENGEANCE_OTHER)
    if (Vengeance.isOnCooldown(target)) {
        player.message("That player has already cast vengeance recently.")
        return@on_spell_on_player
    }
    if (player.castLunar(metadata, xp = 108.0, animation = 4411, graphic = -1, sound = Sfx.LUNAR_VENGENCE)) {
        target.graphic(725, 96)
        Vengeance.activate(target)
        target.message("${player.username} has cast vengeance on you.")
    }
}

on_magic_spell_button("Vengeance Group") { metadata ->
    if (player.skills.getMaxLevel(Skills.DEFENCE) < 40) {
        player.message("You need a Defence level of 40 to cast this spell.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 95.0, animation = 4410, graphic = 726, height = 96, sound = Sfx.LUNAR_VENGENCE)) {
        (nearbyPlayers(player, radius = 1) + player).forEach { p ->
            if (!Vengeance.isOnCooldown(p)) {
                Vengeance.activate(p)
                p.graphic(725, 96)
                if (p !== player) p.message("${player.username} has cast vengeance on you.")
            }
        }
    }
}

/*
 * Cures.
 */
fun curePoison(p: Player): Boolean {
    var cured = false
    if (p.attr.has(POISON_TICKS_LEFT_ATTR)) {
        p.timers.remove(POISON_TIMER)
        p.attr.remove(POISON_TICKS_LEFT_ATTR)
        Poison.setPoisonVarp(p, Poison.OrbState.NONE)
        cured = true
    }
    if (Venom.cure(p, immunityTicks = 0)) cured = true
    return cured
}

on_magic_spell_button("Cure Me") { metadata ->
    if (!player.attr.has(POISON_TICKS_LEFT_ATTR)) {
        player.message("You are not poisoned.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 69.0, animation = 4411, graphic = 742, height = 90, sound = Sfx.LUNAR_CURE)) {
        curePoison(player)
        player.message("You have been cured of poison.")
    }
}

on_spell_on_player(430, SpellbookData.CURE_OTHER.component) {
    val target = player.getInteractingPlayer()
    val metadata = spell(SpellbookData.CURE_OTHER)
    if (!target.attr.has(POISON_TICKS_LEFT_ATTR)) {
        player.message("This player is not poisoned.")
        return@on_spell_on_player
    }
    if (player.castLunar(metadata, xp = 65.0, animation = 4411, graphic = -1, sound = Sfx.LUNAR_CURE_OTHER)) {
        target.graphic(736, 130)
        curePoison(target)
        target.message("${player.username} has cured you of poison.")
    }
}

on_magic_spell_button("Cure Group") { metadata ->
    if (player.castLunar(metadata, xp = 74.0, animation = 4409, graphic = 744, height = 130, sound = Sfx.LUNAR_CURE_GROUP)) {
        (nearbyPlayers(player, radius = 1) + player).forEach { p ->
            if (curePoison(p)) {
                p.graphic(744, 130)
                p.message(if (p === player) "You have been cured of poison." else "${player.username} has cured you of poison.")
            }
        }
    }
}

/*
 * Healing / energy transfer.
 */
on_spell_on_player(430, SpellbookData.HEAL_OTHER.component) {
    val target = player.getInteractingPlayer()
    val metadata = spell(SpellbookData.HEAL_OTHER)
    val transfer = (player.getCurrentLifepoints() * 0.75).toInt()
    if (player.getCurrentLifepoints() <= 10 || transfer <= 0) {
        player.message("You don't have enough life points to cast this spell.")
        return@on_spell_on_player
    }
    if (target.getCurrentLifepoints() >= target.getMaximumLifepoints()) {
        player.message("This player already has full life points.")
        return@on_spell_on_player
    }
    if (player.castLunar(metadata, xp = 101.0, animation = 4411, graphic = 738, height = 90, sound = Sfx.LUNAR_HEAL_OTHER)) {
        val healed = minOf(transfer, target.getMaximumLifepoints() - target.getCurrentLifepoints())
        player.hit(damage = healed, type = HitType.REGULAR_HIT)
        target.heal(healed)
        target.graphic(734, 90)
        target.message("${player.username} has transferred some of their life points to you.")
    }
}

on_magic_spell_button("Heal Group") { metadata ->
    val others = nearbyPlayers(player, radius = 1).filter { it.getCurrentLifepoints() < it.getMaximumLifepoints() }
    if (others.isEmpty()) {
        player.message("There is nobody nearby who needs healing.")
        return@on_magic_spell_button
    }
    val pool = (player.getCurrentLifepoints() * 0.75).toInt()
    if (player.getCurrentLifepoints() <= 10 || pool <= 0) {
        player.message("You don't have enough life points to cast this spell.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 124.0, animation = 1979, graphic = 734, height = 90, sound = Sfx.LUNAR_HEAL_GROUP)) {
        val share = pool / others.size
        var given = 0
        others.forEach { p ->
            val healed = minOf(share, p.getMaximumLifepoints() - p.getCurrentLifepoints())
            p.heal(healed)
            p.graphic(734, 90)
            p.message("${player.username} has transferred some of their life points to you.")
            given += healed
        }
        player.hit(damage = given, type = HitType.REGULAR_HIT)
    }
}

on_spell_on_player(430, SpellbookData.ENERGY_TRANSFER.component) {
    val target = player.getInteractingPlayer()
    val metadata = spell(SpellbookData.ENERGY_TRANSFER)
    val cost = player.getMaximumLifepoints() / 10
    if (player.getCurrentLifepoints() <= cost) {
        player.message("You don't have enough life points to cast this spell.")
        return@on_spell_on_player
    }
    if (AttackTab.getEnergy(player) < 100) {
        player.message("You need full special attack energy to cast this spell.")
        return@on_spell_on_player
    }
    if (player.castLunar(metadata, xp = 100.0, animation = 4411, graphic = 738, height = 90, sound = Sfx.LUNAR_ENERGY_TRANSFER)) {
        player.hit(damage = cost, type = HitType.REGULAR_HIT)
        AttackTab.setEnergy(player, 0)
        AttackTab.setEnergy(target, 100)
        target.runEnergy = 100.0
        target.sendRunEnergy(100)
        target.graphic(738, 90)
        target.message("${player.username} has transferred energy to you.")
    }
}

/*
 * Utility.
 */
private val HUMIDIFY =
    mapOf(
        Items.VIAL to Items.VIAL_OF_WATER,
        Items.BUCKET to Items.BUCKET_OF_WATER,
        Items.JUG to Items.JUG_OF_WATER,
        Items.BOWL to Items.BOWL_OF_WATER,
        Items.EMPTY_CUP to Items.CUP_OF_WATER,
        Items.KETTLE to Items.FULL_KETTLE,
        Items.WATERING_CAN to Items.WATERING_CAN_8,
        Items.CLAY to Items.SOFT_CLAY,
    )

on_magic_spell_button("Humidify") { metadata ->
    val fillable = player.inventory.rawItems.filterNotNull().filter { it.id in HUMIDIFY }
    if (fillable.isEmpty()) {
        player.message("You have nothing to humidify.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 65.0, animation = 6294, graphic = 1061, height = 20, sound = 3614)) {
        HUMIDIFY.forEach { (empty, full) ->
            val count = player.inventory.getItemCount(empty)
            if (count > 0 && player.inventory.remove(empty, count).hasSucceeded()) {
                player.inventory.add(full, count)
            }
        }
    }
}

on_magic_spell_button("Bake Pie") { metadata ->
    val pies = CookingData.values.filter { it.cooked.let { c -> world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, c).name.contains("pie", ignoreCase = true) } }
    val raw = player.inventory.rawItems.filterNotNull().firstOrNull { item -> pies.any { it.raw == item.id } }
    if (raw == null) {
        player.message("You don't have any uncooked pies to bake.")
        return@on_magic_spell_button
    }
    player.queue {
        while (true) {
            val next = player.inventory.rawItems.filterNotNull().firstOrNull { item -> pies.any { it.raw == item.id } } ?: break
            val data = pies.first { it.raw == next.id }
            if (player.skills.getCurrentLevel(Skills.COOKING) < data.levelRequirement) {
                player.message("You need a Cooking level of ${data.levelRequirement} to bake that pie.")
                break
            }
            if (!player.castLunar(metadata, xp = 60.0, animation = 4413, graphic = 746, height = 75, sound = Sfx.LUNAR_BAKE_PIE)) break
            if (player.inventory.remove(data.raw).hasSucceeded()) {
                player.inventory.add(data.cooked)
                player.addXp(Skills.COOKING, data.experience)
            }
            wait(5)
        }
    }
}

private val SUPERGLASS_SOURCES = listOf(Items.SODA_ASH, Items.SEAWEED)

on_magic_spell_button("Superglass Make") { metadata ->
    val sand = player.inventory.getItemCount(Items.BUCKET_OF_SAND)
    val ash = SUPERGLASS_SOURCES.sumOf { player.inventory.getItemCount(it) }
    val batches = minOf(sand, ash)
    if (batches == 0) {
        player.message("You need buckets of sand and soda ash or seaweed to cast this spell.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 78.0, animation = 4413, graphic = 729, height = 120, sound = Sfx.LUNAR_HEATGLASS)) {
        var remaining = batches
        player.inventory.remove(Items.BUCKET_OF_SAND, batches)
        player.inventory.add(Items.BUCKET, batches)
        for (source in SUPERGLASS_SOURCES) {
            val take = minOf(remaining, player.inventory.getItemCount(source))
            if (take > 0) {
                player.inventory.remove(source, take)
                remaining -= take
            }
        }
        // 2011 wiki: 1.3 glass per batch on average - each batch has a 30% chance of a bonus glass.
        var glass = batches
        repeat(batches) { if (world.random(100) < 30) glass++ }
        player.inventory.add(Items.MOLTEN_GLASS, glass)
        player.addXp(Skills.CRAFTING, 10.0 * batches)
    }
}

/** Log -> plank with the 2011 sawmill fee reduced by 30% (Plank Make): 70, 122, 245, 735 coins. */
private val PLANK_MAKE =
    mapOf(
        Items.LOGS to (Items.PLANK to 70),
        Items.OAK_LOGS to (Items.OAK_PLANK to 122),
        Items.TEAK_LOGS to (Items.TEAK_PLANK to 245),
        Items.MAHOGANY_LOGS to (Items.MAHOGANY_PLANK to 735),
    )

on_magic_spell_button("Plank Make") { metadata ->
    player.queue {
        while (true) {
            val log = player.inventory.rawItems.filterNotNull().firstOrNull { it.id in PLANK_MAKE } ?: break
            val (plank, fee) = PLANK_MAKE.getValue(log.id)
            if (player.inventory.getItemCount(Items.COINS_995) < fee) {
                player.message("You need $fee coins to make that plank.")
                break
            }
            if (!player.castLunar(metadata, xp = 90.0, animation = 6298, graphic = 1063, height = 120, sound = 3617)) break
            player.inventory.remove(Items.COINS_995, fee)
            if (player.inventory.remove(log.id).hasSucceeded()) {
                player.inventory.add(plank)
            }
            wait(3)
        }
    }
}

private val STRING_JEWELLERY =
    mapOf(
        Items.GOLD_AMULET to Items.GOLD_AMULET_1692,
        Items.SAPPHIRE_AMULET to Items.SAPPHIRE_AMULET_1694,
        Items.EMERALD_AMULET to Items.EMERALD_AMULET_1696,
        Items.RUBY_AMULET to Items.RUBY_AMULET_1698,
        Items.DIAMOND_AMULET to Items.DIAMOND_AMULET_1700,
        Items.DRAGONSTONE_AMMY to Items.DRAGONSTONE_AMMY_1702,
        Items.ONYX_AMULET_6579 to Items.ONYX_AMULET_6581,
        Items.UNSTRUNG_SYMBOL to Items.HOLY_SYMBOL,
        Items.UNSTRUNG_EMBLEM to Items.UNHOLY_SYMBOL,
    )

on_magic_spell_button("String Jewellery") { metadata ->
    player.queue {
        while (true) {
            val item = player.inventory.rawItems.filterNotNull().firstOrNull { it.id in STRING_JEWELLERY } ?: break
            if (!player.castLunar(metadata, xp = 83.0, animation = 4412, graphic = 730, height = 100, sound = Sfx.LUNAR_STRING_AMULET)) break
            if (player.inventory.remove(item.id).hasSucceeded()) {
                player.inventory.add(STRING_JEWELLERY.getValue(item.id))
                player.addXp(Skills.CRAFTING, 4.0)
            }
            wait(3)
        }
    }
}

private val MAKE_LEATHER =
    mapOf(
        Items.COWHIDE to Items.LEATHER,
        Items.SNAKE_HIDE to Items.SNAKESKIN,
        Items.GREEN_DRAGONHIDE to Items.GREEN_DRAGON_LEATHER,
        Items.BLUE_DRAGONHIDE to Items.BLUE_DRAGON_LEATHER,
        Items.RED_DRAGONHIDE to Items.RED_DRAGON_LEATHER,
        Items.BLACK_DRAGONHIDE to Items.BLACK_DRAGON_LEATHER,
    )

on_magic_spell_button("Make Leather") { metadata ->
    val hide = player.inventory.rawItems.filterNotNull().firstOrNull { it.id in MAKE_LEATHER }
    if (hide == null) {
        player.message("You have no hides to tan.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 83.0, animation = 4413, graphic = 733, height = 130, sound = Sfx.LUNAR_CAST)) {
        val count = minOf(5, player.inventory.getItemCount(hide.id))
        if (player.inventory.remove(hide.id, count).hasSucceeded()) {
            player.inventory.add(MAKE_LEATHER.getValue(hide.id), count)
        }
    }
}

on_magic_spell_button("Hunter Kit") { metadata ->
    if (player.inventory.isFull) {
        player.message("You don't have enough inventory space.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 71.0, animation = 6303, graphic = 1074, sound = 3615)) {
        player.inventory.add(Items.HUNTER_KIT)
    }
}

/** Magic Imbue: 12.6 seconds (21 ticks) of talisman-free combination runecrafting. */
val MAGIC_IMBUE_TIMER = TimerKey()

on_timer(MAGIC_IMBUE_TIMER) {
    player.attr.remove(MAGIC_IMBUE_ATTR)
    player.message("Your Magic Imbue charge has worn off.")
}

on_magic_spell_button("Magic Imbue") { metadata ->
    if (player.castLunar(metadata, xp = 86.0, animation = 722, graphic = 141, height = 96, sound = Sfx.LUNAR_EMBUE_RUNES)) {
        player.timers[MAGIC_IMBUE_TIMER] = 21
        player.attr[MAGIC_IMBUE_ATTR] = true
        player.message("You are charged to combine runes!")
    }
}

on_spell_on_npc(430, SpellbookData.MONSTER_EXAMINE.component) {
    val npc = player.getInteractingNpc()
    val metadata = spell(SpellbookData.MONSTER_EXAMINE)
    if (player.castLunar(metadata, xp = 61.0, animation = 6293, graphic = 1060, sound = 3620)) {
        val def = npc.combatDef
        player.message("This monster's combat level is ${npc.def.combatLevel}.")
        player.message("This monster has a maximum of ${def.lifepoints} life points.")
        player.message("This monster attacks every ${def.attackSpeed} ticks.")
    }
}

on_spell_on_player(430, SpellbookData.STAT_SPY.component) {
    val target = player.getInteractingPlayer()
    val metadata = spell(SpellbookData.STAT_SPY)
    if (player.castLunar(metadata, xp = 75.0, animation = 6293, graphic = 1060, sound = 3620)) {
        target.graphic(734, 0)
        val names = listOf("Attack", "Defence", "Strength", "Constitution", "Ranged", "Prayer", "Magic")
        val stats = names.mapIndexed { i, n -> "$n: ${target.skills.getCurrentLevel(i)}/${target.skills.getMaxLevel(i)}" }
        player.message("${target.username}'s combat stats: ${stats.joinToString(", ")}")
    }
}

/** Dream: sit and recover life points five times faster until moved or attacked. */
on_magic_spell_button("Dream") { metadata ->
    if (player.getCurrentLifepoints() >= player.getMaximumLifepoints()) {
        player.message("You have no need to dream - your life points are full.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 82.0, animation = 6295, graphic = -1, sound = 3619)) {
        player.queue {
            wait(2)
            player.graphic(1056)
            player.animate(6296)
            while (player.getCurrentLifepoints() < player.getMaximumLifepoints()) {
                wait(5)
                if (player.isDead() || player.hasMoveDestination() || player.isAttacking()) break
                player.heal(5)
                player.graphic(1056)
            }
            player.animate(6297)
        }
    }
}

/** Disruption Shield: nullifies the next hit from another player. */
on_magic_spell_button("Disruption Shield") { metadata ->
    if (player.attr[DISRUPTION_SHIELD_ATTR] == true) {
        player.message("You already have a disruption shield active.")
        return@on_magic_spell_button
    }
    if (player.castLunar(metadata, xp = 90.0, animation = 4410, graphic = 726, height = 96, sound = Sfx.LUNAR_CAST)) {
        player.attr[DISRUPTION_SHIELD_ATTR] = true
        player.message("You cast a disruption shield.")
    }
}

/** Spiritualise Food: use on food to heal the familiar and extend its timer (Livid Farm, 2011). */
on_spell_on_item(430, SpellbookData.SPIRITUALISE_FOOD.component) {
    val item = player.getInteractingItem()
    val metadata = spell(SpellbookData.SPIRITUALISE_FOOD)
    val food = gg.rsmod.plugins.content.items.food.Food.values.firstOrNull { it.item == item.id }
    if (food == null || food.heal <= 0) {
        player.message("You can only spiritualise ordinary food.")
        return@on_spell_on_item
    }
    val familiar = Familiar.current(player)
    if (familiar == null) {
        player.message("You need a familiar to cast this spell.")
        return@on_spell_on_item
    }
    if (player.castLunar(metadata, xp = 80.0, animation = 4413, graphic = 733, height = 130, sound = Sfx.LUNAR_CAST)) {
        if (player.inventory.remove(item.id, 1).hasSucceeded()) {
            val heal = food.heal * 10
            familiar.setCurrentLifepoints(minOf(familiar.getCurrentLifepoints() + heal, familiar.getMaximumLifepoints()))
            if (player.timers.has(FAMILIAR_LIFETIME_TIMER)) {
                player.timers[FAMILIAR_LIFETIME_TIMER] = player.timers[FAMILIAR_LIFETIME_TIMER] + 100
            }
            Familiar.updateHud(player)
            player.message("Your familiar looks healthier and its duration is extended.")
        }
    }
}

/*
 * Spellbook Swap: opens Ancient or Modern for one spell (or 2 minutes), bypassing the normal
 * level gate - see SpellbookSwap.kt for the revert mechanics.
 */
on_magic_spell_button("Spellbook Swap") { metadata ->
    player.queue {
        if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes)) {
            return@queue
        }
        val book =
            when (options("Ancient Magicks.", "Normal Magicks.", "Neither, thank you.", title = "Select a Spellbook")) {
                1 -> Spellbook.ANCIENT
                2 -> Spellbook.STANDARD
                else -> return@queue
            }
        MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
        player.addXp(Skills.MAGIC, 96.0, checkBrawlingGloves = true)
        SpellbookSwap.start(player, book)
        player.message("You have 2 minutes before your spellbook changes back to the Lunar spellbook!")
    }
}

on_timer(SpellbookSwap.TIMER) {
    SpellbookSwap.revert(player)
}

on_logout {
    SpellbookSwap.revert(player)
}

// Spells with no implementation yet (see file header) - bind so the click is at least acknowledged.
listOf("NPC Contact", "Fertile Soil", "Cure Plant", "Remote Farm", "Boost Potion Share", "Stat Restore Pot Share").forEach { name ->
    on_magic_spell_button(name) {
        player.message("This spell is not available yet.")
    }
}
