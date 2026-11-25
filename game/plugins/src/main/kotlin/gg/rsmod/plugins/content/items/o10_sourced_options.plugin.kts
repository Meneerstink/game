package gg.rsmod.plugins.content.items

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.EquipmentType

/*
 * O10-ZONDER-BRON (SIGILS-QUESTS-REST, 24-11): dead inventory options whose text was found in the OSRS Wiki transcripts in a second
 * search (the first run 4 search looked only at the item pages). Every line below is copied from the named transcript.
 *  - Stone tablet (The Dig Site) Read: Transcript:Stone tablet (The Dig Site).
 *  - Map part Study (Lozar 1535, Melzar 1536, Thalzar 1537): Transcript:Dragon Slayer I "Studying ... map part".
 *  - Monkey dentures Listen: Transcript:Monkey dentures (two English lines with an M'speak amulet worn; Monkey Madness II is not in
 *    this game) and "Monkey language" (the phrase generator, used for the Listen option without the amulet).
 *  - Doll of Iban Search: Transcript:Underground Pass "Searching the doll". Our Underground Pass gives the doll after Kalrag with
 *    his blood on it (B5170UndergroundPass), so only the blood line follows.
 *  - Pigeon cage Open: Transcript:Biohazard "Opening pigeon cage" - the line when the bird feed was not thrown (our Biohazard has no
 *    pigeon step).
 *  - Zadimus corpse Bury: Transcript:Shilo Village "Burying the Zadimus corpse in the wrong place" (our Shilo Village takes the
 *    corpse to Trufitus, there is no burial place); the corpse stays.
 */

fun boundWhereCached(item: Int, option: String, action: (gg.rsmod.game.model.entity.Player) -> Unit) {
    // Bound only where the cache has the option (a missing option is a boot error).
    val def = world.definitions.get(ItemDef::class.java, item)
    if (def.inventoryMenu.any { it.equals(option, ignoreCase = true) }) on_item_option(item, option = option) { action(player) }
}

boundWhereCached(Items.STONE_TABLET, "read") { p ->
    p.queue { chatPlayer("It says: Tremble mortal, before the altar of our dread lord Zaros.") }
}

val MAP_STUDY = mapOf(
    Items.MAP_PART_1536 to "This is a piece of map that you found in Melzar's Maze. You will need to join it to the other two map pieces before you can see the route to Crandor.",
    Items.MAP_PART_1537 to "This is a piece of map that you found in a secret chest in the Dwarven Mine. You will need to join it to the other two map pieces before you can see the route to Crandor.",
    Items.MAP_PART to "This is a piece of map that you got from Wormbrain, the goblin thief. You will need to join it to the other two map pieces before you can see the route to Crandor.",
)
MAP_STUDY.forEach { (part, text) -> boundWhereCached(part, "study") { p -> p.queue { itemMessageBox(text, part) } } }

val MONKEY_PHRASES = listOf("Ah Ah!", "Ah Uh Ah!", "Ah!", "Ook Ah Ook!", "Ook Ah Uh!", "Ook Ook!", "Ook!", "Ook.")

/** Wiki "Monkey language": random phrases joined with a space after each, until the line holds 8 or more spaces. */
fun monkeyLine(): String {
    val sb = StringBuilder()
    while (sb.count { it == ' ' } < 8) sb.append(MONKEY_PHRASES.random()).append(' ')
    return sb.toString().trimEnd()
}

boundWhereCached(Items.MONKEY_DENTURES, "listen") { p ->
    val understands = p.equipment[EquipmentType.AMULET.id]?.id == Items.MSPEAK_AMULET
    val said = if (understands) listOf("'Floss me! Floss me!'", "'Help - I need toothpaste!'").random() else monkeyLine()
    p.queue { itemMessageBox("The monkey dentures say: <col=000080>$said</col>", Items.MONKEY_DENTURES) }
}

boundWhereCached(Items.DOLL_OF_IBAN, "search") { p ->
    p.message("You carefully search the doll...")
    p.message("Blood has been smeared onto the doll.")
}

boundWhereCached(Items.PIGEON_CAGE, "open") { p -> p.message("The pigeons don't want to leave.") }

boundWhereCached(Items.ZADIMUS_CORPSE, "bury") { p ->
    p.message("You feel an unearthly compunction to bury this corpse!")
    p.queue {
        messageBox("You hear a ghostly wailing sound coming from the corpse and a whispering voice says,'Let me rest in a sacred place and assist you I will'")
    }
}

/*
 * Second wiki round 24-11 20:0x:
 *  - Shaman robe Search: Transcript:Watchtower "Searching the shaman robe" (three boxes). The shamans count as killed once Watchtower
 *    is completed (the quest has no server steps here; QuestStatus is the only state); cyan crystal = 2382 (wiki Crystal, version 3).
 *  - Silvthrill rod Inspect/Look-at: Transcript:In Aid of the Myreque "Inspecting the rod" (7637 unenchanted, 7638 enchanted).
 */
val SHAMANS_CRYSTAL = Items.CRYSTAL_2382

boundWhereCached(Items.SHAMAN_ROBE, "search") { p ->
    p.queue {
        when {
            !gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(p, "Watchtower") ->
                itemMessageBox("You search the robe, but find nothing. Maybe you need to defeat the shamans first.", Items.SHAMAN_ROBE)
            p.inventory.contains(SHAMANS_CRYSTAL) || p.bank.contains(SHAMANS_CRYSTAL) ->
                doubleItemMessageBox("You search the robe, but find nothing. You already have the shamans' crystal.", Items.SHAMAN_ROBE, SHAMANS_CRYSTAL)
            p.inventory.add(SHAMANS_CRYSTAL, 1).hasSucceeded() ->
                doubleItemMessageBox("You search the robe and find a crystal!", Items.SHAMAN_ROBE, SHAMANS_CRYSTAL)
            else -> p.message("You don't have enough inventory space.")
        }
    }
}

val ROD_INSPECT = mapOf(
    Items.SILVTHRILL_ROD_7637 to "This rod looks very similar to the rod you saw fused to the top of the coffin. However, this rod lacks both mortal and divine energies that the original gave off.",
    Items.SILVTHRILL_ROD_7638 to "This rod looks very similar to the rod you saw fused to the top of the coffin. However, this rod lacks the divine energies that the original gave off.",
)
ROD_INSPECT.forEach { (rod, text) ->
    listOf("inspect", "look-at").forEach { op -> boundWhereCached(rod, op) { p -> p.queue { itemMessageBox(text, rod) } } }
}

// Address form Wiki rev15186886 / King's Ransom transcript rev15389571; the form is already in native 667 interface 586.
boundWhereCached(Items.ADDRESS_FORM, "read") { p -> p.openInterface(586, InterfaceDestination.MAIN_SCREEN) }
on_button(interfaceId = 586, component = 6) { player.closeInterface(InterfaceDestination.MAIN_SCREEN) }

// Ogre bow Wiki rev15183145: only the pre-quest message is sourced. A post-quest
// kill counter/message needs its own source and is deliberately not fabricated.
fun checkOgrePrequest(p: gg.rsmod.game.model.entity.Player) {
    if (!gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(p, "Big Chompy Bird Hunting")) {
        p.message("You've scratched up no kills yet! You've got to complete the quest first!")
    }
}
listOf(Items.OGRE_BOW, Items.COMP_OGRE_BOW).forEach { bow ->
    boundWhereCached(bow, "check kills") { p -> checkOgrePrequest(p) }
    val def = world.definitions.get(ItemDef::class.java, bow)
    if (def.equipmentMenu.any { it.equals("check kills", ignoreCase = true) }) {
        on_equipment_option(item = bow, option = "check kills") { checkOgrePrequest(player) }
    }
}
