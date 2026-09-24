package gg.rsmod.plugins.content.magic.standard

import gg.rsmod.game.model.attr.GOD_SPELL_CHARGE_ATTR
import gg.rsmod.game.model.timer.GOD_SPELL_CHARGE_TIMER
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.SpellMetadata
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Standard spellbook utility spells that were still unbound after the Ancient/Lunar import:
 * Bones to Bananas/Peaches, the three Teleother spells, Charge and Enchant Crossbow Bolt.
 *
 * Levels/runes come from the cache-decoded SpellbookData; animations/graphics from the
 * 2009scape modern handlers (ChargeSpell 811/6, TeleotherSpells 1818/343 and 342 on the target,
 * BonesToBananas 722/141) and experience from the 2011 wiki. The Enchant Crossbow Bolt screen
 * is interface 432; its ten bolt buttons and their level texts were decoded from the cache
 * (layout: 14 opal, 29 sapphire, 18 jade, 22 pearl, 32 emerald, 26 topaz, 35 ruby, 38 diamond,
 * 41 dragonstone, 44 onyx).
 *
 * Charge Water/Earth/Fire/Air Orb stay unbound: the client's spell-on-object packet is not
 * mapped in packets.yml, so there is no server hook to receive them yet.
 */

fun spell(data: SpellbookData): SpellMetadata {
    if (!MagicSpells.isLoaded()) MagicSpells.loadSpellRequirements()
    return MagicSpells.getMetadata(data.uniqueId)!!
}

fun bonesTo(
    player: Player,
    metadata: SpellMetadata,
    produce: Int,
    xp: Double,
) {
    if (gg.rsmod.plugins.content.magic.BonesToFruit.refuseWithoutBones(player)) return
    if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes)) return
    MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
    gg.rsmod.plugins.content.magic.BonesToFruit.convert(player, produce)
    player.addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
}

on_magic_spell_button("Bones to Bananas") { metadata -> bonesTo(player, metadata, Items.BANANA, 25.0) }
on_magic_spell_button("Bones to Peaches") { metadata -> bonesTo(player, metadata, Items.PEACH, 35.5) }

/*
 * Teleother: the target gets an accept/decline prompt (2011 behaviour; there is no Accept Aid
 * toggle in this server yet, so the prompt itself is the consent).
 */
private val TELEOTHER =
    mapOf(
        SpellbookData.TELEOTHER_LUMBRIDGE to Triple(Tile(3222, 3217, 0), 84.0, "Lumbridge"),
        SpellbookData.TELEOTHER_FALADOR to Triple(Tile(2965, 3378, 0), 92.0, "Falador"),
        SpellbookData.TELEOTHER_CAMELOT to Triple(Tile(2758, 3478, 0), 100.0, "Camelot"),
    )

TELEOTHER.forEach { (data, dest) ->
    on_spell_on_player(192, data.component) {
        val target = player.getInteractingPlayer()
        val metadata = spell(data)
        val (tile, xp, place) = dest
        if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes)) return@on_spell_on_player
        // Deadman PvP guards plan (2026-09-16): the old pre-flight target.canTeleport(...) check
        // here (before the accept prompt even showed) already had the pre-existing side effect
        // of starting a skulled target's countdown prematurely, before they had even accepted -
        // removed; the real gate below (after consent, at the actual teleport attempt) is the
        // correct, sufficient one and avoids double-triggering SevenSecondAction.
        MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
        player.animate(1818)
        player.graphic(343, 96)
        player.playSound(Sfx.TELE_OTHER_CAST)
        player.addXp(Skills.MAGIC, xp, checkBrawlingGloves = true)
        target.queue {
            target.message("${player.username} is attempting to teleport you to $place.")
            if (options("Accept the teleport.", "Decline.", title = "${player.username} wishes to teleport you to $place.") == 1) {
                target.canTeleport(TeleportType.MODERN) {
                    target.graphic(342, 0)
                    target.teleport(tile, TeleportType.MODERN)
                }
            } else {
                player.message("${target.username} declined the teleport.")
            }
        }
    }
}

/* Charge: for 7 minutes the god spells hit up to 30 instead of 20 while wearing the matching cape. */
on_magic_spell_button("Charge") { metadata ->
    if (player.timers.has(GOD_SPELL_CHARGE_TIMER)) {
        player.message("You already have a charged spell active.")
        return@on_magic_spell_button
    }
    val capes = listOf(Items.SARADOMIN_CAPE, Items.GUTHIX_CAPE, Items.ZAMORAK_CAPE)
    if (player.getEquipment(EquipmentType.CAPE)?.id !in capes) {
        player.message("You need to be wearing a god cape to cast this spell.")
        return@on_magic_spell_button
    }
    if (!MagicSpells.canCast(player, metadata.lvl, metadata.runes)) return@on_magic_spell_button
    MagicSpells.removeRunes(player, metadata.runes, metadata.sprite)
    player.animate(811)
    player.graphic(308, 96)
    player.playSound(Sfx.CHARGE)
    player.timers[GOD_SPELL_CHARGE_TIMER] = 700
    player.attr[GOD_SPELL_CHARGE_ATTR] = true
    player.addXp(Skills.MAGIC, 180.0, checkBrawlingGloves = true)
    player.message("You feel charged with magic power.")
}

on_timer(GOD_SPELL_CHARGE_TIMER) {
    player.attr.remove(GOD_SPELL_CHARGE_ATTR)
    player.message("Your magical charge fades away.")
}

/*
 * Enchant Crossbow Bolt (interface 432). Each cast enchants 10 bolts; runes are per cast.
 *
 * OSRS-IMPORT 2026-09-16 (OSRS Wiki "Enchant Crossbow Bolt", raw wikitext): each of the 10 gem spells "produces"
 * both the plain enchanted bolt ("Opal bolts (e)") and the OSRS-imported gem-tipped dragon bolt ("Opal dragon
 * bolts (e)") - the same spell/level/runes/XP enchant either, depending on which the player is carrying.
 * SOURCE_GAP (ADAPTED, not guessed): the page does not say which stack the spell targets when a player holds
 * both kinds at once - the normal-tier bolt is checked first (unchanged, already-tested priority), falling back
 * to the dragon-tier bolt only when the player has fewer than 10 of the normal kind. Dragonstone dragon bolts
 * (e) 22530 exists as a target item even though the plain Dragonstone dragon bolts cannot currently be fletched
 * here (no "Dragonstone bolt tips" item in this cache) - a player who otherwise obtains the unenchanted stack
 * can still enchant it.
 */
data class BoltEnchant(
    val component: Int,
    val bolt: Int,
    val enchanted: Int,
    val level: Int,
    val xp: Double,
    val runes: List<Item>,
    val dragonBolt: Int? = null,
    val dragonEnchanted: Int? = null,
)

private val BOLT_ENCHANTS =
    listOf(
        BoltEnchant(14, Items.OPAL_BOLTS, Items.OPAL_BOLTS_E, 4, 9.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.AIR_RUNE, 2)), Items.OPAL_DRAGON_BOLTS, Items.OPAL_DRAGON_BOLTS_E),
        BoltEnchant(29, Items.SAPPHIRE_BOLTS, Items.SAPPHIRE_BOLTS_E, 7, 17.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.WATER_RUNE, 1), Item(Items.MIND_RUNE, 1)), Items.SAPPHIRE_DRAGON_BOLTS, Items.SAPPHIRE_DRAGON_BOLTS_E),
        BoltEnchant(18, Items.JADE_BOLTS, Items.JADE_BOLTS_E, 14, 19.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.EARTH_RUNE, 2)), Items.JADE_DRAGON_BOLTS, Items.JADE_DRAGON_BOLTS_E),
        BoltEnchant(22, Items.PEARL_BOLTS, Items.PEARL_BOLTS_E, 24, 29.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.WATER_RUNE, 2)), Items.PEARL_DRAGON_BOLTS, Items.PEARL_DRAGON_BOLTS_E),
        BoltEnchant(32, Items.EMERALD_BOLTS, Items.EMERALD_BOLTS_E, 27, 37.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.AIR_RUNE, 3), Item(Items.NATURE_RUNE, 1)), Items.EMERALD_DRAGON_BOLTS, Items.EMERALD_DRAGON_BOLTS_E),
        BoltEnchant(26, Items.TOPAZ_BOLTS, Items.TOPAZ_BOLTS_E, 29, 33.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.FIRE_RUNE, 2)), Items.TOPAZ_DRAGON_BOLTS, Items.TOPAZ_DRAGON_BOLTS_E),
        BoltEnchant(35, Items.RUBY_BOLTS, Items.RUBY_BOLTS_E, 49, 59.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.FIRE_RUNE, 5), Item(Items.BLOOD_RUNE, 1)), Items.RUBY_DRAGON_BOLTS, Items.RUBY_DRAGON_BOLTS_E),
        BoltEnchant(38, Items.DIAMOND_BOLTS, Items.DIAMOND_BOLTS_E, 57, 67.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.EARTH_RUNE, 10), Item(Items.LAW_RUNE, 2)), Items.DIAMOND_DRAGON_BOLTS, Items.DIAMOND_DRAGON_BOLTS_E),
        BoltEnchant(41, Items.DRAGON_BOLTS, Items.DRAGON_BOLTS_E, 68, 78.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.EARTH_RUNE, 15), Item(Items.SOUL_RUNE, 1)), Items.DRAGONSTONE_DRAGON_BOLTS, Items.DRAGONSTONE_DRAGON_BOLTS_E),
        BoltEnchant(44, Items.ONYX_BOLTS, Items.ONYX_BOLTS_E, 87, 97.0, listOf(Item(Items.COSMIC_RUNE, 1), Item(Items.FIRE_RUNE, 20), Item(Items.DEATH_RUNE, 1)), Items.ONYX_DRAGON_BOLTS, Items.ONYX_DRAGON_BOLTS_E),
    )

on_magic_spell_button("Enchant Crossbow Bolt") {
    player.openInterface(432, InterfaceDestination.MAIN_SCREEN)
}

on_button(432, 12) {
    player.closeInterface(432)
}

BOLT_ENCHANTS.forEach { enchant ->
    on_button(432, enchant.component) {
        val casts =
            when (player.getInteractingOption()) {
                2 -> 5
                3 -> 10
                else -> 1
            }
        player.closeInterface(432)
        player.queue {
            repeat(casts) {
                if (player.skills.getCurrentLevel(Skills.MAGIC) < enchant.level) {
                    player.message("You need a Magic level of ${enchant.level} to enchant those bolts.")
                    return@queue
                }
                // Normal-tier bolts are checked first (unchanged, already-tested priority); the OSRS-imported
                // gem-tipped dragon bolt of the same tier is only targeted when the player has fewer than 10
                // normal bolts - see this block's class doc for why (SOURCE_GAP on the real selection order).
                val useDragon = player.inventory.getItemCount(enchant.bolt) < 10 && enchant.dragonBolt != null && player.inventory.getItemCount(enchant.dragonBolt) >= 10
                val bolt = if (useDragon) enchant.dragonBolt!! else enchant.bolt
                val enchanted = if (useDragon) enchant.dragonEnchanted!! else enchant.enchanted
                if (player.inventory.getItemCount(bolt) < 10) {
                    player.message("You need at least 10 bolts to cast this spell.")
                    return@queue
                }
                if (!MagicSpells.canCast(player, enchant.level, enchant.runes)) return@queue
                MagicSpells.removeRunes(player, enchant.runes, SpellbookData.ENCHANT_CROSSBOW_BOLT.uniqueId)
                player.animate(4462)
                player.graphic(759, 96)
                if (player.inventory.remove(bolt, 10).hasSucceeded()) {
                    player.inventory.add(enchanted, 10)
                    player.addXp(Skills.MAGIC, enchant.xp, checkBrawlingGloves = true)
                }
                wait(2)
            }
        }
    }
}
