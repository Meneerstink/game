package gg.rsmod.plugins.content.skills.runecrafting

import gg.rsmod.plugins.content.quests.finishedQuest
import gg.rsmod.plugins.content.quests.impl.RuneMysteries

private val enterOption = "Enter"

/*
 * OSRS Wiki "Runecraft cape": "When worn, allows access to any runic altar without the use of talismans or tiaras" (the plain max
 * cape too). The ruins show "Enter" through the same per-altar varbit a tiara sets, so the cape opens every altar's varbit while it is
 * worn; taking it off leaves only the altar of a worn tiara open.
 */
/** Altars reached through Mysterious Ruins (their ruins transform by a varbit; 0 = no varbit). */
val RUIN_ALTARS = Altar.values.filter { it.ruins != null && it.varbit != 0 }

fun openAllRuins(player: Player) = RUIN_ALTARS.forEach { player.setVarbit(it.varbit, 1) }

fun closeRuinsExceptTiara(player: Player) {
    val head = player.getEquipment(EquipmentType.HEAD)?.id
    RUIN_ALTARS.forEach { player.setVarbit(it.varbit, if (it.tiara != null && it.tiara == head) 1 else 0) }
}

fun syncRuins(player: Player) {
    if (gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.RUNECRAFTING)) {
        openAllRuins(player)
    } else {
        closeRuinsExceptTiara(player)
    }
}

// Slot hooks (they run after the change, and a cape swapped for another cape only fires the equip hook).
on_equip_to_slot(EquipmentType.CAPE.id) { syncRuins(player) }
on_unequip_from_slot(EquipmentType.CAPE.id) { syncRuins(player) }
on_login { syncRuins(player) }

Altar.values.forEach { altar ->

    /**
     * Handle each Mysterious Ruins object for the Runecrafting altar
     */
    altar.ruins?.forEach { ruin ->

        // Allow using the talisman on the ruins to enter the altar
        altar.talisman?.let { talisman ->
            on_item_on_obj(obj = ruin, item = talisman) {
                if (!player.finishedQuest(RuneMysteries)) {
                    player.message("You need to complete Rune Mysteries before you can enter here.")
                    return@on_item_on_obj
                }
                altar.entrance?.let { player.moveTo(it) }
            }
        }

        // If the object has the 'enter' option, we should check that the varbit is set for the player before teleporting them to the altar
        if (if_obj_has_option(obj = ruin, option = enterOption)) {
            on_obj_option(obj = ruin, option = enterOption) {
                if (!player.finishedQuest(RuneMysteries)) {
                    player.message("You need to complete Rune Mysteries before you can enter here.")
                    return@on_obj_option
                }
                if (player.getVarbit(altar.varbit) == 1) {
                    altar.entrance?.let { player.moveTo(it) }
                }
            }
        }
    }

    /**
     * Handle the enabling of the Mysterious Ruins varbit when equipping
     * the respective tiara
     */
    val tiara = altar.tiara
    if (tiara != null) {
        on_item_equip(item = tiara) {
            player.setVarbit(altar.varbit, 1)
        }
    }

    /**
     * Handle the disabling of the Mysterious Ruins varbit when removing
     * the respective tiara - unless a Runecraft cape perk keeps every altar open.
     */
    if (tiara != null) {
        on_item_unequip(item = tiara) {
            val cape = gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.RUNECRAFTING)
            player.setVarbit(altar.varbit, if (cape) 1 else 0)
        }
    }

    /**
     * Handle the crafting action
     */
    on_obj_option(obj = altar.altar, option = altar.option) {
        player.queue {
            RunecraftAction.craftRune(this, altar.rune)
        }
    }

    /**
     * Handle the exit portal for the altar
     * Added a restriction to the Law Altar exit portal to prevent players from smuggling items to Entrana.
     */
    val exitPortal = altar.exitPortal
    val exit = altar.exit
    if (exitPortal != null && exit != null) {
        on_obj_option(obj = exitPortal, option = "enter") {
            if (player.hasEntranaRestrictedEquipment() && exitPortal == Objs.PORTAL_2472)
                {
                    player.message(
                        "You cannot exit through this portal as you are carrying equipment that is restricted on Entrana.",
                    )
                } else {
                player.moveTo(exit)
            }
        }
    }

    /**
     * Handle the locate option on a talisman
     */
    altar.talisman?.let {
        on_item_option(item = it, option = "locate") {

            // The tile of the ruins
            val tile = altar.exit!!
            val pos = player.tile

            // The direction of the altar
            val direction: String =
                when {
                    pos.z > tile.z && pos.x - 1 > tile.x -> "south-west"
                    pos.x < tile.x && pos.z > tile.z -> "south-east"
                    pos.x > tile.x + 1 && pos.z < tile.z -> "north-west"
                    pos.x < tile.x && pos.z < tile.z -> "north-east"
                    pos.z < tile.z -> "north"
                    pos.z > tile.z -> "south"
                    pos.x < tile.x + 1 -> "east"
                    pos.x > tile.x + 1 -> "west"
                    else -> "unknown"
                }

            player.message("The talisman pulls towards the $direction.")
        }
    }
}
