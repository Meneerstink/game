package gg.rsmod.plugins.content.items

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.content.inter.attack.AttackTab

/*
 * O10-ZONDER-BRON (SIGILS-QUESTS-REST, 24-11): Preach on the six completed god books.
 * Sources: OSRS Wiki "Holy book" (options Wield, Preach, Drop; "Equipping a holy book allows the player to preach and perform
 * ceremonies ... by operating it ... costing 25% of the player's special attack energy"), "God book" ("Preaching consumes 25%
 * special attack energy") and the six transcripts Transcript:Holy book / Unholy book / Book of Balance / Book of War / Book of Law /
 * Book of Darkness ("Select a relevant passage": Wedding Ceremony, Last Rites, Blessings, Preach; the lines are overhead text).
 * No source: the delay between the overhead lines (LINE_DELAY) and the not-enough-energy line (the server's spec-weapon line).
 */

val LINE_DELAY = 3

val PASSAGES: Map<Int, List<List<String>>> = mapOf(
    Items.HOLY_BOOK to listOf(
        listOf("In the name of Saradomin,", "Protector of us all,", "I now join you in the eyes of Saradomin."),
        listOf("Thy cause was false, thy skills did lack;", "See you in Lumbridge when you get back."),
        listOf("Go in peace in the name of Saradomin;", "May his glory shine upon you like the sun."),
        listOf("The currency of goodness is honour;", "It retains its value through scarcity.", "This is Saradomin's wisdom."),
    ),
    Items.UNHOLY_BOOK to listOf(
        listOf("Two great warriors, joined by hand,", "to spread destruction across the land.", "In Zamorak's name, now two are one."),
        listOf("The weak deserve to die,", "So the strong may flourish.", "This is the creed of Zamorak."),
        listOf("May your bloodthirst never be sated,", "and may all your battles be glorious.", "Zamorak bring you strength."),
        listOf("There is no opinion that cannot be proven true...", "by crushing those who choose to disagree with it.", "Zamorak give me strength!"),
    ),
    Items.BOOK_OF_BALANCE to listOf(
        listOf("Light and dark, day and night,", "Balance arises from contrast.", "I unify thee in the name of Guthix."),
        listOf("Thy death was not in vain,", "for it brought some balance to the world.", "May Guthix bring you rest."),
        listOf("May you walk the path, and never fall,", "for Guthix walks beside thee on thy journey.", "May Guthix bring you peace."),
        listOf("The trees, the earth, the sky, the waters;", "All play their part upon this land.", "May Guthix bring you balance."),
    ),
    Items.BOOK_OF_WAR to listOf(
        listOf("Big High War God want great warriors.", "Because you can make more...", "I bind you in Big High War God name."),
        listOf("You not worthy of Big High War God;", "You die too easy."),
        listOf("Big High War God make you strong...", "so you smash enemies."),
        listOf("War is best, peace is for weak.", "If you not worthy of Big High War God...", "you get made dead soon."),
    ),
    Items.BOOK_OF_LAW to listOf(
        listOf("As ye vow to be at peace with each other...", "and to uphold high values of morality and friendship...", "I now pronounce you united in the law of Armadyl."),
        listOf("Thou didst fight true...", "but the foe was too great.", "May thy return be as swift as the flight of Armadyl."),
        listOf("For thy task is lawful...", "May the blessing of Armadyl be upon thee."),
        listOf("Peace shall bring thee wisdom;", "Wisdom shall bring thee peace.", "This is the law of Armadyl."),
    ),
    Items.BOOK_OF_DARKNESS to listOf(
        listOf("Ye faithful and loyal to the Great Lord...", "May ye together succeed in your deeds.", "Ye are now joined by the greatest power."),
        listOf("Thy faith faltered, no power could save thee.", "Like the Great Lord, one day you shall rise again."),
        listOf("By day or night, in defeat or victory...", "the power of the Great Lord be with thee."),
        listOf("Follower of the Great Lord be relieved:", "One day your loyalty will be rewarded.", "Power to the Great Lord!"),
    ),
)

fun preach(p: gg.rsmod.game.model.entity.Player, book: Int) {
    val passages = PASSAGES.getValue(book)
    p.queue {
        val choice = options("Wedding Ceremony", "Last Rites", "Blessings", "Preach", title = "Select a relevant passage")
        val lines = passages.getOrNull(choice - 1) ?: return@queue
        // The book must still be carried or worn when the passage is picked (dropped/banked during the dialogue = nothing).
        if (!p.inventory.contains(book) && !p.equipment.contains(book)) return@queue
        if (!AttackTab.canAffordSpecial(p, 25)) {
            p.message("You don't have enough power left.")
            return@queue
        }
        AttackTab.spendSpecial(p, 25)
        lines.forEachIndexed { i, line ->
            if (i > 0) wait(LINE_DELAY)
            p.forceChat(line)
        }
    }
}

PASSAGES.keys.forEach { book ->
    // Bound only where the cache has the option (a missing option is a boot error).
    val def = world.definitions.get(ItemDef::class.java, book)
    if (def.inventoryMenu.any { it.equals("Preach", ignoreCase = true) }) on_item_option(book, option = "preach") { preach(player, book) }
    if (def.equipmentMenu.any { it.equals("Preach", ignoreCase = true) }) on_equipment_option(item = book, option = "preach") { preach(player, book) }
    // N537 stap 3a (census 25-11): the worn Book of Darkness (OSRS import) names this option "Read" and it did nothing; it is the
    // same passage choice as the other worn god books' Preach.
    if (def.equipmentMenu.any { it.equals("Read", ignoreCase = true) }) on_equipment_option(item = book, option = "read") { preach(player, book) }
}
