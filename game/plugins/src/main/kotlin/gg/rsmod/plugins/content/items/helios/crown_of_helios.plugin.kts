package gg.rsmod.plugins.content.items.helios

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.NO_CLIP_ATTR
import gg.rsmod.game.model.bits.INFINITE_VARS_STORAGE
import gg.rsmod.game.model.bits.InfiniteVarsType
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.teleport

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

suspend fun QueueTask.teleportMenu() {
    when (options("Home", "To a player", "Bring a player to me", "Coordinates (x, z)", "Back", title = "Teleport")) {
        1 -> {
            player.teleport(world.gameContext.home, TeleportType.MODERN)
            player.crownMessage("Teleporting home.")
        }
        2 -> {
            val other = inputPlayer("Teleport to which player?")
            if (other == null) {
                player.crownMessage("That player is not online.")
            } else {
                player.teleport(other.tile, TeleportType.MODERN)
                player.crownMessage("Teleporting to <col=42C66C>${other.username}</col>.")
            }
        }
        3 -> {
            val other = inputPlayer("Bring which player here?")
            if (other == null) {
                player.crownMessage("That player is not online.")
            } else if (other == player) {
                player.crownMessage("You are already here.")
            } else {
                other.teleport(player.tile, TeleportType.MODERN)
                other.message("You have been summoned by ${player.username}.")
                player.crownMessage("Summoning <col=42C66C>${other.username}</col>.")
            }
        }
        4 -> {
            val x = inputInt("Enter x")
            val z = inputInt("Enter z")
            if (x <= 0 || z <= 0) {
                player.crownMessage("Cancelled.")
            } else {
                player.moveTo(Tile(x, z, player.tile.height))
                player.crownMessage("Moved to <col=42C66C>$x, $z</col>.")
            }
        }
        5 -> crownMenu()
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
            "Admin toggles",
            "Heal & restore",
            title = "Crown of Helios",
        )
    when (choice) {
        1 -> combatModeMenu()
        2 -> powerMenu()
        3 -> teleportMenu()
        4 -> togglesMenu()
        5 -> player.healAndRestore()
    }
}

fun Player.openCrownMenu() {
    if (!CrownOfHelios.isAdmin(this)) {
        message("The crown does not answer to you.")
        return
    }
    queue { crownMenu() }
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
        player.queue { teleportMenu() }
    }
}
