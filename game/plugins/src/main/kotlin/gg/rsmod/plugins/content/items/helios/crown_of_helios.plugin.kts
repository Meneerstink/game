package gg.rsmod.plugins.content.items.helios

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.impl.LogoutFullMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.NO_CLIP_ATTR
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.bits.INFINITE_VARS_STORAGE
import gg.rsmod.game.model.bits.InfiniteVarsType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.service.world.BannedPlayers
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.content.areas.godwars.GodWars
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon
import gg.rsmod.plugins.content.cmd.TestSpawnRegistry
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom

/**
 * Crown of Helios dev-tool menu ("Dev Tools" + "AV Tester" in [crownMenu]) - admin-only debug
 * instrumentation layered on top of the crown's original combat/teleport/toggle menu. Built to let
 * the owner isolate missing/incorrect Summoning and Curses animation/GFX/sound without guessing IDs:
 * everything here plays real IDs against a real target and reuses the same primitives the existing
 * `::anim`/`::gfx`/`::sound`/`::objsearch`/`::itemsearch`/`::npc`/`::obj`/`TestSpawnRegistry` admin
 * commands already use (see `commands.plugin.kts`), rather than re-deriving them.
 */

/**
 * Crown of Helios (item 22327) - the staff "yellow partyhat". See [CrownOfHelios] for the combat
 * side; this script is the spawn commands, the right-click menu and the admin utilities on it.
 *
 * Commands (ADMIN_POWER): `::helios` / `::teleport` spawn the crown, `::crown` opens its menu.
 * Inventory options (baked into the cloned cache definition): Wear, Command, Teleport.
 */

val CROWN = CrownOfHelios.ITEM

fun Player.crownMessage(text: String) = message("<col=E5B80B>Crown of Helios:</col> $text", type = ChatMessageType.CONSOLE)

fun Player.spawnCrown() {
    if (inventory.contains(CROWN) || equipment.contains(CROWN)) {
        crownMessage("You already carry the crown. Use <col=42C66C>::crown</col> or right-click it for its menu.")
        return
    }
    val result = inventory.add(CROWN, 1, assureFullInsertion = false)
    if (result.completed == 0) {
        crownMessage("No free inventory space for the crown.")
        return
    }
    crownMessage("A blazing golden crown appears in your pack. Wear it, then right-click <col=42C66C>Command</col>.")
}

fun Player.statusLine(): String {
    val worn = if (equipment.contains(CROWN)) "worn" else "not worn"
    return "$worn - mode <col=42C66C>${CrownOfHelios.mode(this).label}</col>, power <col=42C66C>${CrownOfHelios.power(this).label}</col>"
}

fun onOff(enabled: Boolean) = if (enabled) "<col=178000>on</col>" else "<col=42C66C>off</col>"

suspend fun QueueTask.combatModeMenu() {
    val choice = options("Melee", "Ranged", "Magic", "Back", title = "Combat mode")
    val mode =
        when (choice) {
            1 -> CrownOfHelios.Mode.MELEE
            2 -> CrownOfHelios.Mode.RANGED
            3 -> CrownOfHelios.Mode.MAGIC
            else -> return crownMenu()
        }
    player.attr[CrownOfHelios.MODE_ATTR] = mode
    player.crownMessage("Combat mode set to <col=42C66C>${mode.label}</col> (1 tick, 10 tiles, always hits).")
}

suspend fun QueueTask.powerMenu() {
    val powers = CrownOfHelios.Power.values()
    val choice = options(*powers.map { it.label }.toTypedArray(), "Back", title = "Hit power")
    val power = powers.getOrNull(choice - 1) ?: return crownMenu()
    player.attr[CrownOfHelios.POWER_ATTR] = power
    player.crownMessage("Hit power set to <col=42C66C>${power.label}</col>.")
}

fun Player.rememberRecent(
    label: String,
    tile: Tile,
) {
    val list = attr[CrownOfHelios.RECENT_ATTR] ?: mutableListOf<CrownOfHelios.SavedLocation>().also { attr[CrownOfHelios.RECENT_ATTR] = it }
    list.removeAll { it.label == label }
    list.add(0, CrownOfHelios.SavedLocation(label, tile))
    if (list.size > 8) list.subList(8, list.size).clear()
}

/**
 * IMPORTANT: the game's `options()` dialog only has real client interfaces for up to 5 total
 * entries (224 + 2*n interface family - confirmed working at n=5 by the pre-existing
 * `togglesMenu`/`powerMenu`, confirmed BROKEN above that: an 8-entry call here resolved to an
 * unrelated real interface and showed garbage, screenshotted by the owner as `cro.png`). Every
 * `options(...)` call anywhere in this file must pass 5 strings or fewer (title excluded) - use
 * a nested submenu or the 3-per-page pattern below ([bossDestinationPicker] etc) instead of adding
 * a 6th+ entry to any existing list.
 */
/**
 * Teleport flows END after a successful teleport instead of re-opening a menu: `Pawn.teleport`
 * starts a [TaskPriority.STRONG] queue, which terminates this dialog task. A menu opened after
 * that point belongs to a task that is no longer in the queue, so no click/keypress can ever
 * resume it - the client sits on "Please wait..." (the owner-reported Back/dropdown hang).
 * `options()` now also refuses to open on a terminated task, but the flows return explicitly so
 * the behaviour does not depend on that guard alone.
 *
 * Per owner instruction (2026-09-11): "Player teleports" and "Search (spellbook locations)" were
 * removed from this menu; the sourced boss locations are kept as a direct entry.
 */
suspend fun QueueTask.teleportMenu() {
    when (
        options(
            "Home",
            "Boss locations",
            "Recent / Favorites",
            "Back",
            title = "Teleport",
        )
    ) {
        1 -> {
            player.rememberRecent("Home", world.gameContext.home)
            player.crownMessage("Teleporting home.")
            player.teleport(world.gameContext.home, TeleportType.MODERN)
        }
        2 -> return bossDestinationPicker()
        3 -> return recentFavoritesMenu()
        4 -> return crownMenu()
    }
}

suspend fun QueueTask.recentFavoritesMenu() {
    when (options("Recent", "Favorites", "Back", title = "Recent / Favorites")) {
        1 -> teleportRecentMenu()
        2 -> teleportFavoritesMenu()
        else -> teleportMenu()
    }
}

/**
 * Boss teleport destinations, all real/sourced - never a guessed coordinate:
 * - GWD chambers (Bandos/Armadyl/Saradomin/Zamorak/Zaros-Nex): [GodWars.God.chamberEntry],
 *   the same tile the dungeon's own door/kill-count logic uses.
 * - Barrows: [Barrows.Brother.AHRIM]'s surface mound tile, the same `hillX/hillZ`-derived tile
 *   the barrows script itself moves a player to when it puts them back on the surface.
 * - King Black Dragon / Kalphite Queen: their real bulk NPC spawn tile
 *   (`content/areas/spawns/spawns_9033.plugin.kts` x=2270,z=4701 / `spawns_13972.plugin.kts`
 *   x=3484,z=9491 - re-read 2026-09-12).
 */
data class BossDestination(val label: String, val tile: Tile)

val BOSS_DESTINATIONS: List<BossDestination> =
    GodWars.God.values().map { BossDestination(it.displayName, it.chamberEntry) } +
        listOf(
            BossDestination("Barrows", Tile(Barrows.Brother.AHRIM.hillX.first + 1, Barrows.Brother.AHRIM.hillZ.first + 1, 0)),
            BossDestination("King Black Dragon", Tile(2270, 4701, 0)),
            BossDestination("Kalphite Queen", Tile(3484, 9491, 0)),
        )

/** 3 destinations per page + "More..."/"Back" - never more than 5 options (see the note above). */
suspend fun QueueTask.bossDestinationPicker(page: Int = 0) {
    val pageSize = 3
    val start = page * pageSize
    val pageItems = BOSS_DESTINATIONS.drop(start).take(pageSize)
    val hasMore = start + pageSize < BOSS_DESTINATIONS.size
    val labels = pageItems.map { it.label }.toMutableList()
    if (hasMore) labels.add("More...")
    labels.add("Back")
    val choice = options(*labels.toTypedArray(), title = "Boss ${start + 1}-${start + pageItems.size} of ${BOSS_DESTINATIONS.size}")
    when {
        choice in 1..pageItems.size -> {
            val dest = pageItems[choice - 1]
            player.rememberRecent(dest.label, dest.tile)
            player.crownMessage("Teleporting to <col=42C66C>${dest.label}</col>.")
            player.teleport(dest.tile, TeleportType.MODERN)
        }
        hasMore && choice == pageItems.size + 1 -> bossDestinationPicker(page + 1)
        choice == labels.size -> teleportMenu()
    }
}

suspend fun QueueTask.teleportRecentMenu(page: Int = 0) {
    val recent = player.attr[CrownOfHelios.RECENT_ATTR].orEmpty()
    if (recent.isEmpty()) {
        player.crownMessage("No recent Crown teleports yet.")
        return recentFavoritesMenu()
    }
    val pageSize = 3
    val start = page * pageSize
    val pageItems = recent.drop(start).take(pageSize)
    val hasMore = start + pageSize < recent.size
    val labels = pageItems.map { it.label }.toMutableList()
    if (hasMore) labels.add("More...")
    labels.add("Back")
    val choice = options(*labels.toTypedArray(), title = "Recent teleports")
    when {
        choice in 1..pageItems.size -> {
            val dest = pageItems[choice - 1]
            player.crownMessage("Teleporting to <col=42C66C>${dest.label}</col>.")
            player.teleport(dest.tile, TeleportType.MODERN)
        }
        hasMore && choice == pageItems.size + 1 -> teleportRecentMenu(page + 1)
        choice == labels.size -> recentFavoritesMenu()
    }
}

suspend fun QueueTask.teleportFavoritesMenu() {
    when (options("Go to a favorite", "Save my current location", "Back", title = "Favorites")) {
        1 -> favoritesGotoMenu()
        2 -> {
            val label = inputString("Name this location")
            if (label.isNotBlank()) {
                val favorites =
                    player.attr[CrownOfHelios.FAVORITES_ATTR]
                        ?: mutableListOf<CrownOfHelios.SavedLocation>().also { player.attr[CrownOfHelios.FAVORITES_ATTR] = it }
                favorites.add(CrownOfHelios.SavedLocation(label, player.tile))
                player.crownMessage("Saved <col=42C66C>$label</col> to favorites.")
            }
            teleportFavoritesMenu()
        }
        else -> recentFavoritesMenu()
    }
}

suspend fun QueueTask.favoritesGotoMenu(page: Int = 0) {
    val favorites = player.attr[CrownOfHelios.FAVORITES_ATTR].orEmpty()
    if (favorites.isEmpty()) {
        player.crownMessage("No favorites saved yet.")
        return teleportFavoritesMenu()
    }
    val pageSize = 3
    val start = page * pageSize
    val pageItems = favorites.drop(start).take(pageSize)
    val hasMore = start + pageSize < favorites.size
    val labels = pageItems.map { it.label }.toMutableList()
    if (hasMore) labels.add("More...")
    labels.add("Back")
    val choice = options(*labels.toTypedArray(), title = "Favorites")
    when {
        choice in 1..pageItems.size -> {
            val dest = pageItems[choice - 1]
            player.crownMessage("Teleporting to favorite <col=42C66C>${dest.label}</col>.")
            player.teleport(dest.tile, TeleportType.MODERN)
        }
        hasMore && choice == pageItems.size + 1 -> favoritesGotoMenu(page + 1)
        choice == labels.size -> teleportFavoritesMenu()
    }
}

suspend fun QueueTask.togglesMenu() {
    val infHp = player.hasStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.HP)
    val infRun = player.hasStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.RUN)
    val infPray = player.hasStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.PRAY)
    val noClip = player.attr[NO_CLIP_ATTR] ?: false
    val choice =
        options(
            "God mode (infinite HP): ${onOff(infHp)}",
            "Infinite run: ${onOff(infRun)}",
            "Infinite prayer: ${onOff(infPray)}",
            "Invisible: ${onOff(player.invisible)}",
            "No-clip: ${onOff(noClip)}",
            title = "Admin toggles",
        )
    when (choice) {
        1 -> {
            player.toggleStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.HP)
            player.crownMessage("God mode ${onOff(!infHp)}.")
        }
        2 -> {
            player.toggleStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.RUN)
            player.crownMessage("Infinite run ${onOff(!infRun)}.")
        }
        3 -> {
            player.toggleStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.PRAY)
            player.crownMessage("Infinite prayer ${onOff(!infPray)}.")
        }
        4 -> {
            player.invisible = !player.invisible
            player.crownMessage("Invisible ${onOff(player.invisible)}.")
        }
        5 -> {
            player.attr[NO_CLIP_ATTR] = !noClip
            player.crownMessage("No-clip ${onOff(!noClip)}.")
        }
    }
}

fun Player.logAction(text: String) {
    val log = attr[CrownOfHelios.LAST_ACTIONS_ATTR] ?: mutableListOf<String>().also { attr[CrownOfHelios.LAST_ACTIONS_ATTR] = it }
    log.add(0, text)
    if (log.size > 15) log.subList(15, log.size).clear()
}

fun Player.giveCrownItem(itemId: Int, amount: Int) {
    if (amount <= 0) {
        crownMessage("Amount must be greater than zero.")
        return
    }
    val def = world.definitions.getNullable(ItemDef::class.java, itemId)
    if (def == null) {
        crownMessage("Item $itemId does not exist in the cache.")
        return
    }
    val result = inventory.add(itemId, amount, assureFullInsertion = true)
    if (!result.hasSucceeded()) {
        crownMessage("Not enough free inventory space for $amount x ${def.name}; nothing was added.")
        return
    }
    crownMessage("Added $amount x ${def.name}.")
    logAction("Gave item ${def.name} ($itemId) x$amount")
}

suspend fun QueueTask.searchAndGiveCrownItem() {
    val query = inputString("Search item name").trim().lowercase()
    if (query.isBlank()) return itemToolsMenu()
    val matches =
        (0 until world.definitions.getCount(ItemDef::class.java))
            .mapNotNull { id -> world.definitions.getNullable(ItemDef::class.java, id)?.let { id to it } }
            .filter { (_, def) -> def.name.lowercase().contains(query) }
            .take(4)
    if (matches.isEmpty()) {
        player.crownMessage("No cached item matches '$query'.")
        return itemToolsMenu()
    }
    val labels = matches.map { it.second.name }.toMutableList()
    labels.add("Back")
    val choice = options(*labels.toTypedArray(), title = "Give item")
    if (choice in 1..matches.size) {
        val amount = inputInt("Amount")
        if (amount > 0) player.giveCrownItem(matches[choice - 1].first, amount)
    }
    itemToolsMenu()
}

suspend fun QueueTask.itemToolsMenu() {
    when (options("Overload (4)", "Super attack (4)", "Super strength (4)", "Search & give", "Back", title = "Item tools")) {
        1 -> {
            player.giveCrownItem(Items.OVERLOAD_4, inputInt("How many Overload (4)?"))
            itemToolsMenu()
        }
        2 -> {
            player.giveCrownItem(Items.SUPER_ATTACK_4, inputInt("How many Super attack (4)?"))
            itemToolsMenu()
        }
        3 -> {
            player.giveCrownItem(Items.SUPER_STRENGTH_4, inputInt("How many Super strength (4)?"))
            itemToolsMenu()
        }
        4 -> searchAndGiveCrownItem()
        else -> devToolsMenu()
    }
}

fun Pawn.avLabel(): String =
    when (this) {
        is Player -> username
        is Npc -> "$name (npc $id)"
        else -> "target"
    }

/** Nearest NPC within [radius] tiles, for AV Tester / Nearby without typing an NPC index. */
fun Player.nearestNpc(radius: Int = 15): Npc? {
    var nearest: Npc? = null
    var nearestDist = Int.MAX_VALUE
    world.npcs.forEach { npc ->
        if (npc.tile.isWithinRadius(tile, radius)) {
            val dist = npc.tile.getDistance(tile)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = npc
            }
        }
    }
    return nearest
}

enum class AvKind(val label: String) {
    ANIMATION("Animation"),
    GFX("GFX"),
    SOUND("Sound"),
    PROJECTILE("Projectile"),
}

/** AV Tester: pick a kind, enter a real cache ID, play it on a real target. No client needed. */
suspend fun QueueTask.avTesterMenu() {
    when (options("Animation", "GFX", "Sound", "Projectile", "Back", title = "AV Tester")) {
        1 -> avTest(AvKind.ANIMATION)
        2 -> avTest(AvKind.GFX)
        3 -> avTest(AvKind.SOUND)
        4 -> avTest(AvKind.PROJECTILE)
        else -> crownMenu()
    }
}

suspend fun QueueTask.avTest(kind: AvKind) {
    when (options("Enter ${kind.label} ID", "Browse ${kind.label} IDs (preview)", "Back", title = "AV Tester: ${kind.label}")) {
        1 -> Unit
        2 -> return avBrowse(kind)
        else -> return avTesterMenu()
    }
    val id = inputInt("Enter ${kind.label} ID")
    if (id < 0) {
        player.crownMessage("Cancelled.")
        return avTesterMenu()
    }
    if (kind == AvKind.SOUND) {
        player.playSound(id)
        player.logAction("Sound $id")
        player.crownMessage("Playing sound <col=42C66C>$id</col> (you hear it).")
        return avTesterMenu()
    }
    val targetChoice = options("On myself", "On my current target", "On nearest NPC", "Cancel", title = "Play ${kind.label} $id on")
    val target: Pawn? =
        when (targetChoice) {
            1 -> player
            2 -> player.getCombatTarget()
            3 -> player.nearestNpc()
            else -> null
        }
    if (target == null) {
        player.crownMessage("No valid target for that choice (no current target / no nearby NPC).")
        return avTesterMenu()
    }
    when (kind) {
        AvKind.ANIMATION -> {
            target.animate(id)
            player.logAction("Animation $id on ${target.avLabel()}")
            player.crownMessage("Playing animation <col=42C66C>$id</col> on ${target.avLabel()}.")
        }
        AvKind.GFX -> {
            target.graphic(id, 0)
            player.logAction("GFX $id on ${target.avLabel()}")
            player.crownMessage("Playing GFX <col=42C66C>$id</col> on ${target.avLabel()}.")
        }
        AvKind.PROJECTILE -> {
            val style = options("Arrow arc", "Bolt arc", "Magic arc", title = "Projectile style")
            val type =
                when (style) {
                    1 -> ProjectileType.ARROW
                    2 -> ProjectileType.BOLT
                    else -> ProjectileType.MAGIC
                }
            world.spawn(player.createProjectile(target, id, type))
            player.logAction("Projectile $id -> ${target.avLabel()}")
            player.crownMessage("Firing projectile <col=42C66C>$id</col> at ${target.avLabel()}.")
        }
        AvKind.SOUND -> Unit // handled above
    }
    avTesterMenu()
}

/**
 * RCV-010 D1: browse and preview real cache ids instead of typing raw ids blind. Every step plays the id on the
 * chosen target and only lands on ids that exist in this cache (see [CrownAvBrowser]).
 */
suspend fun QueueTask.avBrowse(kind: AvKind) {
    val source =
        when (kind) {
            AvKind.ANIMATION -> CrownAvBrowser.AvSource.ANIMATION
            AvKind.SOUND -> CrownAvBrowser.AvSource.SYNTH_SOUND
            AvKind.GFX, AvKind.PROJECTILE -> CrownAvBrowser.AvSource.SPOT_ANIM
        }
    val ids = CrownAvBrowser.ids(world, source)
    val start = inputInt("Browse ${kind.label} IDs starting from")
    if (start < 0) {
        player.crownMessage("Cancelled.")
        return avTesterMenu()
    }
    var id = CrownAvBrowser.step(ids, start, 0) ?: run {
        player.crownMessage("No ${kind.label} ID at or after $start in this cache.")
        return avTesterMenu()
    }
    var target: Pawn = player
    var projectileType = ProjectileType.MAGIC
    if (kind != AvKind.SOUND) {
        val chosen: Pawn? =
            when (options("On myself", "On my current target", "On nearest NPC", "Cancel", title = "Preview ${kind.label} on")) {
                1 -> player
                2 -> player.getCombatTarget()
                3 -> player.nearestNpc()
                else -> null
            }
        if (chosen == null) {
            player.crownMessage("No valid target for that choice (no current target / no nearby NPC).")
            return avTesterMenu()
        }
        target = chosen
        if (kind == AvKind.PROJECTILE) {
            projectileType =
                when (options("Arrow arc", "Bolt arc", "Magic arc", title = "Projectile style")) {
                    1 -> ProjectileType.ARROW
                    2 -> ProjectileType.BOLT
                    else -> ProjectileType.MAGIC
                }
        }
    }
    while (true) {
        when (kind) {
            AvKind.ANIMATION -> target.animate(id)
            AvKind.GFX -> target.graphic(id, 0)
            AvKind.SOUND -> player.playSound(id)
            AvKind.PROJECTILE -> world.spawn(player.createProjectile(target, id, projectileType))
        }
        player.logAction("Browse ${kind.label} $id")
        val delta =
            when (options("Next", "Previous", "Forward 10", "Back 10", "Stop", title = "${kind.label} $id (${ids.size} in cache)")) {
                1 -> 1
                2 -> -1
                3 -> 10
                4 -> -10
                else -> return avTesterMenu()
            }
        val next = CrownAvBrowser.step(ids, id, delta)
        if (next == null) {
            player.crownMessage("No further ${kind.label} IDs in that direction.")
        } else {
            id = next
        }
    }
}

suspend fun QueueTask.devToolsMenu() {
    when (
        options(
            "Item tools",
            "Spawn / test sandbox",
            "Inspect (nearby NPCs / target dummy)",
            "State tools (player state / snapshot / inspector / log)",
            "Back",
            title = "Dev Tools",
        )
    ) {
        1 -> itemToolsMenu()
        2 -> sandboxMenu()
        3 -> inspectMenu()
        4 -> stateToolsMenu()
        else -> moreMenu()
    }
}

suspend fun QueueTask.inspectMenu() {
    when (options("Nearby NPCs", "Target dummy (HP / freeze)", "Back", title = "Inspect")) {
        1 -> nearbyMenu()
        2 -> targetDummyMenu()
        else -> devToolsMenu()
    }
}

suspend fun QueueTask.stateToolsMenu() {
    when (
        options(
            "Player state (cure / unskull / unfreeze)",
            "Snapshot / restore my state",
            "Interface & Entity Inspector (debug flags)",
            "Last actions log",
            "Back",
            title = "State Tools",
        )
    ) {
        1 -> playerStateMenu()
        2 -> snapshotMenu()
        3 -> debugFlagsMenu()
        4 -> lastActionsMenu()
        else -> devToolsMenu()
    }
}

/**
 * Target Dummy: pick a real pawn (your combat target, nearest NPC, or yourself) and control its
 * HP/freeze directly - for watching a curse drain, a familiar special or a hit land without the
 * target fighting back or dying under you mid-test.
 */
suspend fun QueueTask.targetDummyMenu() {
    val targetChoice = options("My current target", "Nearest NPC", "Myself", "Cancel", title = "Target Dummy: pick target")
    val target: Pawn? =
        when (targetChoice) {
            1 -> player.getCombatTarget()
            2 -> player.nearestNpc()
            3 -> player
            else -> null
        }
    if (target == null) {
        player.crownMessage("No valid target (no current target / no nearby NPC).")
        return inspectMenu()
    }
    when (
        options(
            "Heal to full",
            "Set HP",
            "Freeze (100 cycles)",
            "Remove freeze",
            "Back",
            title = "Target Dummy: ${target.avLabel()}",
        )
    ) {
        1 -> {
            target.setCurrentLifepoints(target.getMaximumLifepoints())
            player.logAction("Healed ${target.avLabel()} to full")
            player.crownMessage("${target.avLabel()} healed to full (${target.getMaximumLifepoints()}).")
        }
        2 -> {
            val hp = inputInt("Set HP to (max ${target.getMaximumLifepoints()})")
            if (hp in 0..target.getMaximumLifepoints()) {
                target.setCurrentLifepoints(hp)
                player.logAction("Set ${target.avLabel()} HP to $hp")
                player.crownMessage("${target.avLabel()} HP set to <col=42C66C>$hp</col>.")
            } else {
                player.crownMessage("Invalid HP.")
            }
        }
        3 -> {
            target.freeze(100)
            player.logAction("Froze ${target.avLabel()}")
            player.crownMessage("${target.avLabel()} frozen.")
        }
        4 -> {
            target.timers.remove(FROZEN_TIMER)
            player.logAction("Unfroze ${target.avLabel()}")
            player.crownMessage("${target.avLabel()} unfrozen.")
        }
        else -> return inspectMenu()
    }
    targetDummyMenu()
}

/**
 * Position/HP/run/skill-level snapshot only - never inventory/equipment (see
 * [CrownOfHelios.Snapshot]), so this can never duplicate or lose real items.
 */
suspend fun QueueTask.snapshotMenu() {
    when (
        options(
            "Save snapshot (position / HP / run / skill levels)",
            "Restore last snapshot",
            "Back",
            title = "Snapshot / Restore",
        )
    ) {
        1 -> {
            val levels = IntArray(player.skills.maxSkills) { player.skills.getCurrentLevel(it) }
            player.attr[CrownOfHelios.SNAPSHOT_ATTR] =
                CrownOfHelios.Snapshot(player.tile, player.getCurrentLifepoints(), player.runEnergy, levels)
            player.logAction("Saved player-state snapshot")
            player.crownMessage("Snapshot saved.")
        }
        2 -> {
            val snap = player.attr[CrownOfHelios.SNAPSHOT_ATTR]
            if (snap == null) {
                player.crownMessage("No snapshot saved yet.")
            } else {
                player.moveTo(snap.tile)
                player.setCurrentLifepoints(snap.hp)
                player.runEnergy = snap.runEnergy
                AttackTab.setEnergy(player, snap.runEnergy.toInt())
                for (i in snap.levels.indices) {
                    player.skills.setCurrentLevel(i, snap.levels[i])
                }
                player.logAction("Restored player-state snapshot")
                player.crownMessage("Snapshot restored.")
            }
        }
        else -> return stateToolsMenu()
    }
    snapshotMenu()
}

/**
 * Interface Inspector / part of Entity Inspector: these six flags already existed as boot-time
 * console-verbosity switches ([world.devContext]) wired into real handlers - button clicks
 * (component/option/slot/item), object actions (id/rot/transform), examines, unhandled item/
 * spell actions and path-interaction choke points. This just exposes a live on/off toggle for
 * them instead of editing config and restarting. They are server-wide, not admin-scoped: every
 * player's matching console output turns on/off together, which is fine on a private test server
 * but worth knowing before flipping one with other players online.
 */
suspend fun QueueTask.debugFlagsMenu() {
    when (options("Interaction flags (buttons/items/objects)", "Content flags (examines/spells/pathing)", "Back", title = "Interface / Entity Inspector")) {
        1 -> flagsInteractionMenu()
        2 -> flagsContentMenu()
        else -> stateToolsMenu()
    }
}

fun Player.debugFlagsUpdated() {
    crownMessage("Debug flag updated - matching console output now prints to every player's client (server-wide).")
}

suspend fun QueueTask.flagsInteractionMenu() {
    val ctx = world.devContext
    when (
        options(
            "Buttons / interfaces: ${onOff(ctx.debugButtons)}",
            "Item actions: ${onOff(ctx.debugItemActions)}",
            "Objects: ${onOff(ctx.debugObjects)}",
            "Back",
            title = "Interaction flags",
        )
    ) {
        1 -> {
            ctx.debugButtons = !ctx.debugButtons
            player.debugFlagsUpdated()
            flagsInteractionMenu()
        }
        2 -> {
            ctx.debugItemActions = !ctx.debugItemActions
            player.debugFlagsUpdated()
            flagsInteractionMenu()
        }
        3 -> {
            ctx.debugObjects = !ctx.debugObjects
            player.debugFlagsUpdated()
            flagsInteractionMenu()
        }
        else -> debugFlagsMenu()
    }
}

suspend fun QueueTask.flagsContentMenu() {
    val ctx = world.devContext
    when (
        options(
            "Examines: ${onOff(ctx.debugExamines)}",
            "Magic spells: ${onOff(ctx.debugMagicSpells)}",
            "Pathing interactions: ${onOff(ctx.debugInteractions)}",
            "Back",
            title = "Content flags",
        )
    ) {
        1 -> {
            ctx.debugExamines = !ctx.debugExamines
            player.debugFlagsUpdated()
            flagsContentMenu()
        }
        2 -> {
            ctx.debugMagicSpells = !ctx.debugMagicSpells
            player.debugFlagsUpdated()
            flagsContentMenu()
        }
        3 -> {
            ctx.debugInteractions = !ctx.debugInteractions
            player.debugFlagsUpdated()
            flagsContentMenu()
        }
        else -> debugFlagsMenu()
    }
}

suspend fun QueueTask.cacheSearchMenu() {
    when (options("Items", "NPCs", "Objects", "Back", title = "Cache Search")) {
        1 -> cacheSearchItems()
        2 -> cacheSearchNpcs()
        3 -> cacheSearchObjects()
        else -> devToolsMenu()
    }
}

suspend fun QueueTask.cacheSearchItems() {
    val query = inputString("Search item name").lowercase()
    if (query.isBlank()) return cacheSearchMenu()
    val count = world.definitions.getCount(ItemDef::class.java)
    val matches = (0 until count).mapNotNull { id -> world.definitions.getNullable(ItemDef::class.java, id)?.let { id to it } }
        .filter { it.second.name.lowercase().contains(query) }
    if (matches.isEmpty()) {
        player.crownMessage("No items match \"$query\".")
    } else {
        player.crownMessage("Found ${matches.size} item(s) (showing up to 15):")
        matches.take(15).forEach { (id, def) -> player.message("Item $id: \"${def.name}\"", type = ChatMessageType.CONSOLE) }
    }
    cacheSearchMenu()
}

suspend fun QueueTask.cacheSearchNpcs() {
    val query = inputString("Search NPC name").lowercase()
    if (query.isBlank()) return cacheSearchMenu()
    val allNpcDefs = world.definitions.getAll(NpcDef::class.java) as Map<Int, NpcDef>
    val matches = allNpcDefs.filter { (_, def) -> def.name.lowercase().contains(query) }
    if (matches.isEmpty()) {
        player.crownMessage("No NPCs match \"$query\".")
    } else {
        player.crownMessage("Found ${matches.size} NPC(s) (showing up to 15):")
        matches.entries.take(15).forEach { (id, def) ->
            player.message("NPC $id: \"${def.name}\" combatLvl=${def.combatLevel}", type = ChatMessageType.CONSOLE)
        }
    }
    cacheSearchMenu()
}

suspend fun QueueTask.cacheSearchObjects() {
    val query = inputString("Search object name").lowercase()
    if (query.isBlank()) return cacheSearchMenu()
    val allObjDefs = world.definitions.getAll(ObjectDef::class.java) as Map<Int, ObjectDef>
    val matches = allObjDefs.filter { (_, def) -> def.name.lowercase().contains(query) }
    if (matches.isEmpty()) {
        player.crownMessage("No objects match \"$query\".")
    } else {
        player.crownMessage("Found ${matches.size} object(s) (showing up to 15):")
        matches.entries.take(15).forEach { (id, def) ->
            player.message("Obj $id: \"${def.name}\"", type = ChatMessageType.CONSOLE)
        }
    }
    cacheSearchMenu()
}

suspend fun QueueTask.sandboxMenu() {
    when (
        options(
            "Spawn NPC here",
            "Spawn object here",
            "Give myself an item",
            "Clear my test spawns",
            "Back",
            title = "Spawn / Test Sandbox",
        )
    ) {
        1 -> {
            val id = inputInt("NPC id to spawn")
            if (id >= 0) {
                val npc = Npc(id, player.tile, world)
                world.spawn(npc)
                TestSpawnRegistry.trackNpc(npc)
                player.logAction("Spawned npc $id")
                player.crownMessage("Spawned npc <col=42C66C>$id</col>.")
            }
            sandboxMenu()
        }
        2 -> {
            val id = inputInt("Object id to spawn")
            if (id >= 0) {
                val obj = DynamicObject(id, 10, 0, player.tile)
                world.spawn(obj)
                TestSpawnRegistry.trackObject(obj)
                player.logAction("Spawned object $id")
                player.crownMessage("Spawned object <col=42C66C>$id</col> (type 10, rot 0).")
            }
            sandboxMenu()
        }
        3 -> {
            val id = inputInt("Item id to give")
            if (id >= 0) {
                val amount = inputInt("Amount").let { if (it > 0) it else 1 }
                val result = player.inventory.add(id, amount, assureFullInsertion = false)
                player.logAction("Gave item $id x$amount")
                player.crownMessage("Gave item <col=42C66C>$id</col> (added ${result.completed}).")
            }
            sandboxMenu()
        }
        4 -> {
            val (npcsCleared, objsCleared) = TestSpawnRegistry.clear(world)
            player.logAction("Cleared test spawns ($npcsCleared npcs, $objsCleared objects)")
            player.crownMessage("Cleared <col=42C66C>$npcsCleared</col> test npc(s) and <col=42C66C>$objsCleared</col> test object(s).")
            sandboxMenu()
        }
        else -> devToolsMenu()
    }
}

suspend fun QueueTask.nearbyMenu() {
    val radius = 15
    val nearby = mutableListOf<Npc>()
    world.npcs.forEach { npc -> if (npc.tile.isWithinRadius(player.tile, radius)) nearby.add(npc) }
    if (nearby.isEmpty()) {
        player.crownMessage("No NPCs within $radius tiles.")
    } else {
        val sorted = nearby.sortedBy { it.tile.getDistance(player.tile) }
        player.crownMessage("${sorted.size} NPC(s) within $radius tiles (showing up to 20):")
        sorted.take(20).forEach { npc ->
            val def = world.definitions.getNullable(NpcDef::class.java, npc.id)
            player.message(
                "NPC ${npc.id} \"${npc.name}\" combatLvl=${def?.combatLevel ?: -1} tile=${npc.tile} region=${npc.tile.regionId}",
                type = ChatMessageType.CONSOLE,
            )
        }
    }
    inspectMenu()
}

suspend fun QueueTask.playerStateMenu() {
    when (
        options(
            "Restore all (HP/prayer/run/stats)",
            "Cure poison / venom",
            "Clear skull",
            "Remove freeze",
            "Back",
            title = "Player State",
        )
    ) {
        1 -> {
            player.healAndRestore()
            playerStateMenu()
        }
        2 -> {
            Poison.cure(player)
            Venom.cure(player, 0)
            player.logAction("Cured poison/venom")
            player.crownMessage("Poison/venom cured.")
            playerStateMenu()
        }
        3 -> {
            player.setSkullIcon(SkullIcon.NONE)
            player.timers.remove(SKULL_ICON_DURATION_TIMER)
            player.logAction("Cleared skull")
            player.crownMessage("Skull cleared.")
            playerStateMenu()
        }
        4 -> {
            player.timers.remove(FROZEN_TIMER)
            player.logAction("Removed freeze")
            player.crownMessage("Freeze removed.")
            playerStateMenu()
        }
        else -> stateToolsMenu()
    }
}

suspend fun QueueTask.lastActionsMenu() {
    val log = player.attr[CrownOfHelios.LAST_ACTIONS_ATTR].orEmpty()
    if (log.isEmpty()) {
        player.crownMessage("No Crown actions logged yet this session.")
    } else {
        player.crownMessage("Last ${log.size} Crown action(s):")
        log.forEach { player.message(it, type = ChatMessageType.CONSOLE) }
    }
    stateToolsMenu()
}

fun Player.healAndRestore() {
    setCurrentLifepoints(getMaximumLifepoints())
    runEnergy = 100.0
    AttackTab.setEnergy(this, 100)
    for (i in 0 until skills.maxSkills) {
        skills.setCurrentLevel(i, skills.getMaxLevel(i))
    }
    crownMessage("Lifepoints, prayer, run energy, special attack and all stats restored.")
}

suspend fun QueueTask.crownMenu() {
    val choice =
        options(
            "Combat mode: ${CrownOfHelios.mode(player).label}",
            "Hit power: ${CrownOfHelios.power(player).label}",
            "Teleport",
            "Heal & restore",
            "More (toggles / AV Tester / Dev Tools / players)",
            title = "Crown of Helios",
        )
    when (choice) {
        1 -> combatModeMenu()
        2 -> powerMenu()
        3 -> teleportMenu()
        4 -> player.healAndRestore()
        5 -> moreMenu()
    }
}

suspend fun QueueTask.moreMenu() {
    when (
        options(
            "Admin toggles",
            "AV Tester",
            "Dev Tools",
            "Player management (kick / ban)",
            "Back",
            title = "Crown: more",
        )
    ) {
        1 -> togglesMenu()
        2 -> avTesterMenu()
        3 -> devToolsMenu()
        4 -> playerManagementMenu()
        else -> crownMenu()
    }
}

/** Kick reuses the same logout sequence as the existing `::kick` command. Ban persists to a
 * plain username list checked at login ([BannedPlayers]) - a real `ACCOUNT_BANNED` client
 * response, not a fake/local-only block - and also kicks the player immediately if online. */
suspend fun QueueTask.playerManagementMenu() {
    when (options("Kick a player", "Ban a player", "Unban a player", "Back", title = "Player Management")) {
        1 -> {
            val name = inputString("Kick which player? (username)")
            if (name.isNotBlank()) {
                val target = world.getPlayerForName(name.replace("_", " "))
                if (target == null) {
                    player.crownMessage("$name is not online.")
                } else {
                    target.requestLogout()
                    target.write(LogoutFullMessage())
                    target.channelClose()
                    player.logAction("Kicked $name")
                    player.crownMessage("Kicked <col=42C66C>$name</col>.")
                }
            }
            playerManagementMenu()
        }
        2 -> {
            val name = inputString("Ban which player? (username)")
            if (name.isNotBlank()) {
                val added = BannedPlayers.ban(name)
                val target = world.getPlayerForName(name.replace("_", " "))
                if (target != null) {
                    target.requestLogout()
                    target.write(LogoutFullMessage())
                    target.channelClose()
                }
                player.logAction("Banned $name")
                player.crownMessage(
                    if (added) "Banned <col=42C66C>$name</col> (kicked if they were online)." else "$name was already banned.",
                )
            }
            playerManagementMenu()
        }
        3 -> {
            val name = inputString("Unban which player? (username)")
            if (name.isNotBlank()) {
                val removed = BannedPlayers.unban(name)
                player.logAction("Unbanned $name")
                player.crownMessage(if (removed) "Unbanned <col=42C66C>$name</col>." else "$name was not banned.")
            }
            playerManagementMenu()
        }
        else -> moreMenu()
    }
}

fun Player.openCrownMenu() {
    if (!CrownOfHelios.isAdmin(this)) {
        message("The crown does not answer to you.")
        return
    }
    // STRONG: terminates any earlier (possibly still-waiting) Crown flow and skips the
    // main-screen "menu open" gate, so a second Command click can never stack a dead task in
    // front of the live one.
    queue(TaskPriority.STRONG) { crownMenu() }
}

on_command("helios", Privilege.ADMIN_POWER) { player.spawnCrown() }
on_command("teleport", Privilege.ADMIN_POWER) { player.spawnCrown() }
on_command("crown", Privilege.ADMIN_POWER) { player.openCrownMenu() }

can_equip_item(CROWN) {
    if (!CrownOfHelios.isAdmin(player)) {
        player.message("The crown does not answer to you.")
        false
    } else {
        true
    }
}

on_item_equip(CROWN) {
    player.crownMessage("The crown blazes to life: ${player.statusLine()}.")
}

on_item_unequip(CROWN) {
    player.crownMessage("The crown's fire fades; your normal combat returns.")
}

on_item_option(CROWN, "Command") { player.openCrownMenu() }

on_item_option(CROWN, "Teleport") {
    if (!CrownOfHelios.isAdmin(player)) {
        player.message("The crown does not answer to you.")
    } else {
        player.queue(TaskPriority.STRONG) { teleportMenu() }
    }
}
