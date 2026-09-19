package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.teleports.RuneFreeTeleportRequirements
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.prepareForTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell

/** Player-owned house - see [PlayerHouse]. */

// House teleport tab: same break sequence as the other teletabs (teleport_tab.plugin.kts); the private
// room is allocated on arrival so the allocator's empty-map scan can never release it mid-teleport.
on_item_option(item = Items.TELEPORT_TO_HOUSE, option = "break") {
    val self = player
    self.canTeleport(TeleportType.MODERN) {
        self.queue(TaskPriority.STRONG) {
            if (!self.inventory.contains(Items.TELEPORT_TO_HOUSE)) {
                return@queue
            }
            self.inventory.remove(item = Items.TELEPORT_TO_HOUSE)
            self.prepareForTeleport()
            self.lock = LockState.FULL_WITH_DAMAGE_IMMUNITY
            self.animate(id = Anims.USE_TELETAB_1, delay = 16)
            self.playSound(Sfx.POH_TABLET_BREAK_TELEPORT, delay = 15)
            wait(cycles = 3)
            self.graphic(Gfx.TAB_TELEPORT)
            self.animate(id = Anims.USE_TELETAB_2)
            wait(cycles = 2)
            self.animate(id = Anims.RESET)
            self.unlock()
            if (!enterHouse(self)) {
                self.inventory.add(Items.TELEPORT_TO_HOUSE)
            }
        }
    }
}

// Standard spellbook "Teleport to House" (level 40, 30 Magic XP - OSRS Wiki "Teleport to House"), cast like the other
// teleport spells (teleport_spells.plugin.kts: rune-free requirements, MODERN animation/graphics); the house is
// allocated on arrival.
on_magic_spell_button("Teleport to House") { metadata ->
    val requirements = RuneFreeTeleportRequirements.nonRuneRequirements(metadata.runes)
    if (!MagicSpells.canCast(player, metadata.lvl, requirements)) {
        return@on_magic_spell_button
    }
    val self = player
    self.canTeleport(TeleportType.MODERN) {
        MagicSpells.removeRunes(self, requirements, metadata.sprite)
        self.addXp(Skills.MAGIC, 30.0, checkBrawlingGloves = true)
        self.lock = LockState.FULL_WITH_DAMAGE_IMMUNITY
        self.queue(TaskPriority.STRONG) {
            val type = TeleportType.MODERN
            self.prepareForTeleport()
            self.animate(type.animation)
            type.graphic?.let { self.graphic(it) }
            wait(type.teleportDelay)
            enterHouse(self)
            type.endAnimation?.let { self.animate(it) }
            type.endGraphic?.let { self.graphic(it) }
            wait(2)
            self.animate(Anims.RESET)
            self.unlock()
        }
    }
}

fun enterHouse(player: Player): Boolean {
    val arrival = PlayerHouse.build(player)
    if (arrival == null) {
        player.message("Your house can't be reached right now. Try again in a moment.")
        return false
    }
    player.moveTo(arrival)
    return true
}

// Rimmington house portal: walks into the same private house.
on_obj_option(obj = PlayerHouse.RIMMINGTON_PORTAL, option = "enter") {
    enterHouse(player)
}

// Exit portal.
on_obj_option(obj = PlayerHouse.PORTAL, option = "enter") {
    player.moveTo(PlayerHouse.EXIT_TILE)
}

// Rejuvenation pool (OSRS Wiki "Ornate rejuvenation pool"): restores hitpoints, prayer, run energy and special
// attack, raises reduced stats back to their base level (boosts are kept) and cures poison and venom. The 667
// ornamental fountain carries name "Rejuvenation pool" and option "Drink" via PohObjectOptionTool.
on_obj_option(obj = PlayerHouse.POOL, option = "drink") {
    drinkFromPool(player)
}

// Slot 5 is the fountain's former "Remove" option: a client that has not yet downloaded the updated loc definition
// still shows that option, and it must drink too rather than do nothing.
on_obj_option(obj = PlayerHouse.POOL, option = 5) {
    drinkFromPool(player)
}

fun drinkFromPool(player: Player) {
    if (!player.lock.canItemInteract()) {
        return
    }
    // Same drink animation and liquid sound as potions (Potions.kt); the OSRS pool animation is not in the 667 cache.
    player.animate(Anims.EAT_FOOD)
    player.playSound(Sfx.LIQUID)
    player.message("You drink from the pool and feel completely rejuvenated.")
    player.heal(player.getMaximumLifepoints())
    player.restorePrayer(player.getMaximumPrayerPoints())
    player.runEnergy = 100.0
    AttackTab.setEnergy(player, 100)
    for (skill in 0 until player.skills.maxSkills) {
        if (skill == Skills.CONSTITUTION || skill == Skills.PRAYER) continue
        val base = player.skills.getMaxLevel(skill)
        if (player.skills.getCurrentLevel(skill) < base) {
            player.skills.setCurrentLevel(skill, base)
        }
    }
    Poison.cure(player)
    Venom.cure(player, immunityTicks = 0, announce = false)
}

// Prayer altar (POH gilded altar 13199, option "Pray"); bone offering stays in prayer_altar.plugin.kts. After the
// recharge it offers the same prayer book / spellbook choice as the Ferox Enclave altar (prayer_altar.plugin.kts).
on_obj_option(obj = PlayerHouse.ALTAR, option = "pray") {
    player.queue {
        player.animate(Anims.ALTAR_PRAY)
        player.filterableMessage("You recharge your Prayer points.")
        player.playSound(Sfx.PRAYER_RECHARGE)
        Prayers.rechargePrayerPoints(player)
        when (options("Use Normal Prayers.", "Use Ancient Curses.", "Unlock Ancient Curses (50,000 coins).", "Change my spellbook.", "Keep my books.")) {
            1 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
            2 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
            3 -> AncientCurses.unlock(player)
            4 ->
                when (options("Standard spellbook.", "Ancient Magicks.", "Lunar spellbook.")) {
                    1 -> Spellbooks.select(player, Spellbook.STANDARD)
                    2 -> Spellbooks.select(player, Spellbook.ANCIENT)
                    3 -> Spellbooks.select(player, Spellbook.LUNAR)
                }
        }
    }
}

// Portal chamber: marble teleport portals, destinations as the matching standard teleport spells (TeleportSpell).
mapOf(
    PlayerHouse.VARROCK_PORTAL to TeleportSpell.VARROCK,
    PlayerHouse.FALADOR_PORTAL to TeleportSpell.FALADOR,
    PlayerHouse.CAMELOT_PORTAL to TeleportSpell.CAMELOT,
).forEach { (portal, spell) ->
    on_obj_option(obj = portal, option = "enter") {
        player.moveTo(spell.endArea.randomTile)
    }
}

// Combat dummy: appears once the owner is inside (see PlayerHouse.DUMMY_SPAWN_TIMER).
on_timer(PlayerHouse.DUMMY_SPAWN_TIMER) {
    PlayerHouse.spawnDummy(player)
}

// Mounted amulet of glory: the four glory destinations (amulet_of_glory.plugin.kts), never runs out of charges.
private val GLORY_DESTINATIONS =
    listOf(
        Tile(3086, 3503, 0), // Edgeville
        Tile(2917, 3175, 0), // Karamja
        Tile(3104, 3249, 0), // Draynor Village
        Tile(3293, 3162, 0), // Al Kharid
    )

on_obj_option(obj = PlayerHouse.GLORY, option = "rub") {
    player.queue {
        val choice = options("Edgeville.", "Karamja.", "Draynor Village.", "Al Kharid.", "Nowhere.")
        val destination = GLORY_DESTINATIONS.getOrNull(choice - 1) ?: return@queue
        player.canTeleport(TeleportType.JEWELRY) {
            world.spawn(AreaSound(player.tile, 200, 5, 20))
            player.teleport(destination, TeleportType.JEWELRY)
        }
    }
}

// Combat dummy: never dies.
on_timer(PlayerHouse.DUMMY_HEAL_TIMER) {
    npc.setCurrentLifepoints(npc.getMaximumLifepoints())
    npc.timers[PlayerHouse.DUMMY_HEAL_TIMER] = PlayerHouse.DUMMY_HEAL_TICKS
}
