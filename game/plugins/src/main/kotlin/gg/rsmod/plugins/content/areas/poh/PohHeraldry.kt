package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Gender
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus

/**
 * CONSTRUCTION-99 deel 7 (heraldry). Sources: OSRS Wiki "Heraldic crest", "Sir Renitee", "Pluming stand", "Shield easel",
 * "Banner easel", "Heraldic helmet (Construction)", "Heraldic kiteshield (Construction)", "Banner (Construction)" (fetched 2026-11-26);
 * Darkan `HouseHeraldry` / `SirRenitee` (rev 727) for the crest order, the decoration loc ids and Sir Renitee's lines.
 *
 * - The family crest: Sir Renitee (White Knights' Castle, Falador, first floor) assigns a random crest the first time a player with
 *   Construction 16 (boostable) asks - free; changing it costs 5,000 coins (Money: 500,000), some crests have a requirement.
 * - The crest pieces ([CREST_PIECES]: oak/teak/gilded wall decorations, round/square/kite shields) need a crest to build and show
 *   the owner's crest (one loc per crest, [PohFurniture.Deco.obj]); a changed crest shows the next time the house is built.
 * - The workshop's heraldry space: the pluming stand paints steel/rune full helms (Crafting 38), the shield easel also steel/rune
 *   kiteshields (Crafting 43), the banner easel also makes banners from a plank and a bolt of cloth (Crafting 48). No experience
 *   (none on the wiki). The adamant versions are OSRS-only (not in the 667 cache). Painted items are untradeable (items.yml) and
 *   turn back into the unpainted item on an unprotected PvP death ([paintedBase], PvpDeathBreakables).
 */
object PohHeraldry {
    enum class Crest(
        val label: String,
        val description: String,
        val cost: Int,
        val requirement: String?,
        val allowed: (Player) -> Boolean,
    ) {
        ARRAV("Arrav", "Shield of Arrav symbol", 5_000, "Shield of Arrav", { quest(it, "Shield of Arrav") }),
        ASGARNIA("Asgarnia", "Symbol of Asgarnia", 5_000, null, { true }),
        DORGESHUUN("Dorgeshuun", "Dorgeshuun brooch", 5_000, "The Lost Tribe", { quest(it, "The Lost Tribe") }),
        DRAGON("Dragon", "Dragon", 5_000, "Dragon Slayer", { quest(it, "Dragon Slayer") }),
        FAIRY("Fairy", "Fairy", 5_000, "Lost City", { quest(it, "Lost City") }),
        GUTHIX("Guthix", "Symbol of Guthix", 5_000, "70 Prayer", { it.skills.getMaxLevel(Skills.PRAYER) >= 70 }),
        HAM("HAM", "Symbol of the HAM cult", 5_000, null, { true }),
        HORSE("Horse", "Horse", 5_000, "a toy horsey", { p -> TOY_HORSEYS.any { p.inventory.contains(it) } }),
        JOGRE("Jogre", "Jungle Ogre", 5_000, null, { true }),
        KANDARIN("Kandarin", "Symbol of Kandarin", 5_000, null, { true }),
        MISTHALIN("Misthalin", "Symbol of Misthalin", 5_000, null, { true }),
        MONEY("Money", "Money", 500_000, null, { true }),
        SARADOMIN("Saradomin", "Symbol of Saradomin", 5_000, "70 Prayer", { it.skills.getMaxLevel(Skills.PRAYER) >= 70 }),
        SKULL("Skull", "Skull", 5_000, "a skull", { it.skullIcon != -1 }),
        VARROCK("Varrock", "Symbol of Varrock", 5_000, null, { true }),
        ZAMORAK("Zamorak", "Symbol of Zamorak", 5_000, "70 Prayer", { it.skills.getMaxLevel(Skills.PRAYER) >= 70 }),
    }

    private val TOY_HORSEYS = intArrayOf(Items.TOY_HORSEY, Items.TOY_HORSEY_2522, Items.TOY_HORSEY_2524, Items.TOY_HORSEY_2526)

    /** Deco keys whose 16 locs are one per crest (Crest order). */
    val CREST_PIECES = setOf("OAK_DECORATION", "TEAK_DECORATION", "GILDED_DECORATION", "ROUND_SHIELD", "SQUARE_SHIELD", "KITE_SHIELD")

    /** The crest a piece shows when its owner has none (a crest piece cannot be built without one): Varrock, Darkan's default. */
    val DEFAULT_CREST = Crest.VARROCK.ordinal

    /** Crest ordinal + 1 (absent = none assigned yet). */
    private val CREST_ATTR = AttributeKey<Int>(persistenceKey = "poh_heraldic_crest")

    fun crest(player: Player): Crest? = player.attr[CREST_ATTR]?.let { Crest.values().getOrNull(it - 1) }

    fun crestIndex(player: Player): Int = crest(player)?.ordinal ?: DEFAULT_CREST

    fun setCrest(
        player: Player,
        crest: Crest,
    ) {
        player.attr[CREST_ATTR] = crest.ordinal + 1
    }

    private fun quest(
        player: Player,
        name: String,
    ): Boolean = QuestList.ENTRIES.none { it.name == name } || QuestStatus.isCompleted(player, name)

    const val NO_CREST = "You must speak to the chief herald of Falador before you can make heraldic items."

    /** A painted item family: the unpainted item, the first painted id (16 ids, step 2: the noted form sits between), Crafting. */
    enum class Painted(val base: Int, val first: Int, val crafting: Int, val label: String, val shield: Boolean) {
        STEEL_HELM(Items.STEEL_FULL_HELM, 8682, 38, "Steel heraldic helm", false),
        RUNE_HELM(Items.RUNE_FULL_HELM, 8464, 38, "Rune heraldic helm", false),
        STEEL_KITE(Items.STEEL_KITESHIELD, 8746, 43, "Steel kiteshield", true),
        RUNE_KITE(Items.RUNE_KITESHIELD, 8714, 43, "Rune kiteshield", true),
        ;

        fun painted(crest: Crest): Int = first + crest.ordinal * 2
    }

    const val BANNER_FIRST = 8650
    const val BANNER_CRAFTING = 48

    const val PLUMING_STAND = 13716
    const val SHIELD_EASEL = 13717
    const val BANNER_EASEL = 13718
    val STANDS = setOf(PLUMING_STAND, SHIELD_EASEL, BANNER_EASEL)

    private const val PAINT_ANIM = 4109

    /** Every painted helm/kiteshield -> its unpainted item (PvP death). Banners have no unpainted form. */
    val PAINTED_TO_BASE: Map<Int, Int> = Painted.values().flatMap { p -> Crest.values().map { p.painted(it) to p.base } }.toMap()

    fun paintedBase(itemId: Int): Int? = PAINTED_TO_BASE[itemId]

    val BANNERS: Set<Int> = Crest.values().map { BANNER_FIRST + it.ordinal * 2 }.toSet()

    /** "Make-helmet" / "Use" on a heraldry stand. */
    suspend fun paint(
        task: QueueTask,
        stand: Int,
    ) {
        val player = task.player
        val crest = crest(player)
        if (crest == null) {
            task.messageBox(NO_CREST)
            return
        }
        if (!crest.allowed(player)) {
            task.messageBox("You need ${crest.requirement} to paint the ${crest.label} crest.")
            return
        }
        val families = Painted.values().filter { (!it.shield || stand != PLUMING_STAND) && player.inventory.contains(it.base) }
        val banner = stand == BANNER_EASEL
        if (families.isEmpty() && !banner) {
            task.messageBox(
                if (stand == PLUMING_STAND) "You need a steel or rune full helm to paint." else "You need a steel or rune full helm or kiteshield to paint.",
            )
            return
        }
        val labels = families.map { "${it.label} (${crest.label})" } + (if (banner) listOf("Banner (${crest.label})") else emptyList())
        val pick = task.options(*(labels + "Nothing").toTypedArray(), title = "Paint your crest on") - 1
        if (pick !in labels.indices) return
        val family = families.getOrNull(pick)
        val level = family?.crafting ?: BANNER_CRAFTING
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < level) {
            task.messageBox("You need a Crafting level of $level to make that.")
            return
        }
        if (family == null) {
            if (!player.inventory.contains(Items.PLANK) || !player.inventory.contains(Items.BOLT_OF_CLOTH)) {
                task.messageBox("You need a plank and a bolt of cloth to make a banner.")
                return
            }
            player.inventory.remove(Items.PLANK, 1)
            player.inventory.remove(Items.BOLT_OF_CLOTH, 1)
            player.animate(PAINT_ANIM)
            task.wait(3)
            player.inventory.add(BANNER_FIRST + crest.ordinal * 2, 1)
            player.message("You make a banner with your crest on it.")
            return
        }
        if (player.inventory.remove(family.base, 1).completed == 0) return
        player.animate(PAINT_ANIM)
        task.wait(3)
        player.inventory.add(family.painted(crest), 1)
        player.message("You paint your crest onto the ${if (family.shield) "kiteshield" else "helmet"}.")
    }

    // ----- Sir Renitee ---------------------------------------------------------------------------------------------------------

    private fun young(player: Player): String = if (player.appearance.gender == Gender.MALE) "man" else "woman"

    suspend fun talk(task: QueueTask) {
        val player = task.player
        task.chatNpc("Hmm? What's that, young ${young(player)}? What can I do for you?", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
        if (task.options("I don't know, what can you do for me?", "Nothing, thanks.") != 1) return
        task.chatPlayer("I don't know, what can you do for me?", facialExpression = FacialExpression.NORMAL)
        task.chatNpc(
            "Hmm, well, mmm, do you have a family crest? I keep track of every Gielinor family, you know, so I might be able to find yours.",
            npc = Npcs.SIR_RENITEE,
            facialExpression = FacialExpression.NORMAL,
        )
        task.chatNpc(
            "I'm also something of an, mmm, a painter. If you've met any important persons or visited any nice places I could paint them for you.",
            npc = Npcs.SIR_RENITEE,
            facialExpression = FacialExpression.NORMAL,
        )
        when (task.options("Can you see if I have a family crest?", "Can I buy a painting?", "Nothing, thanks.")) {
            1 -> familyCrest(task)
            2 -> painting(task)
        }
    }

    private suspend fun familyCrest(task: QueueTask) {
        val player = task.player
        task.chatPlayer("Can you see if I have a family crest?", facialExpression = FacialExpression.NORMAL)
        task.chatNpc("What is your name?", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
        task.chatPlayer("${player.username}.", facialExpression = FacialExpression.NORMAL)
        task.chatNpc("Mmm, ${player.username}, let me see...", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
        if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) < 16) {
            task.chatNpc(
                "First things first, young ${young(player)}! There is not much point in having a family crest if you cannot display it. " +
                    "You should train Construction until you can build a wall decoration in your dining room.",
                npc = Npcs.SIR_RENITEE,
                facialExpression = FacialExpression.NORMAL,
            )
            return
        }
        // The first crest: random and free (OSRS Wiki "Heraldic crest"), from the crests without a requirement or extra cost.
        val crest = crest(player) ?: Crest.values().filter { it.requirement == null && it != Crest.MONEY }.random().also { setCrest(player, it) }
        if (crest == Crest.VARROCK) {
            task.chatNpc(
                "Well, I don't think you have any noble blood, but I see that your ancestors came from Varrock, so you can use that city's crest.",
                npc = Npcs.SIR_RENITEE,
                facialExpression = FacialExpression.NORMAL,
            )
        } else {
            task.chatNpc("According to my records, your crest is the symbol of ${crest.label}.", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
        }
        if (task.options("I don't like that crest. Can I have a different one?", "Thanks!") != 1) {
            task.chatPlayer("Thanks!", facialExpression = FacialExpression.HAPPY)
            task.chatNpc("You're welcome, my ${if (player.appearance.gender == Gender.MALE) "boy" else "girl"}.", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.HAPPY)
            return
        }
        task.chatPlayer("I don't like that crest. Can I have a different one?", facialExpression = FacialExpression.NORMAL)
        task.chatNpc("Mmm, very well. Changing your crest will cost 5,000 coins.", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
        val choices = Crest.values().filter { it != crest }
        val labels = choices.map { c -> c.description + (if (c.cost != 5_000) " (${"%,d".format(c.cost)} coins)" else "") }
        val pick = task.options(*(labels + "Never mind").toTypedArray(), title = "Which crest would you like?") - 1
        val chosen = choices.getOrNull(pick)
        if (chosen == null) {
            task.chatPlayer("Never mind.", facialExpression = FacialExpression.NORMAL)
            task.chatNpc("Come back if you change your mind!", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.NORMAL)
            return
        }
        if (!chosen.allowed(player)) {
            task.chatNpc("I'm afraid you can't have that crest - you need ${chosen.requirement}.", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.SAD)
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < chosen.cost) {
            task.chatPlayer("I'll have to go get some.", facialExpression = FacialExpression.NORMAL)
            return
        }
        task.chatPlayer("I would like the ${chosen.label} crest.", facialExpression = FacialExpression.NORMAL)
        // Read again after the dialogue: the coins may have gone meanwhile.
        if (player.inventory.remove(Items.COINS_995, chosen.cost).completed < chosen.cost) return
        setCrest(player, chosen)
        task.chatNpc("The ${chosen.description}? Excellent choice!", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.HAPPY)
    }

    /** Sir Renitee's stock (OSRS Wiki "Sir Renitee" stock table, Darkan `MapAndPortrait`). */
    private class Painting(val item: Int, val name: String, val price: Int, val allowed: (Player) -> Boolean)

    private fun quests(vararg names: String): (Player) -> Boolean = { p -> names.all { quest(p, it) } }

    private val PORTRAITS =
        listOf(
            Painting(Items.ELENA_PORTRAIT, "Elena portrait", 1_000, quests("Plague City")),
            Painting(Items.ARTHUR_PORTRAIT, "Arthur portrait", 1_000, quests("Merlin's Crystal", "Holy Grail")),
            Painting(Items.MISC_PORTRAIT, "Misc. portrait", 1_000, quests("Throne of Miscellania")),
            Painting(Items.KELDAGRIM_PORTRAIT, "Keldagrim portrait", 1_000, quests("The Giant Dwarf")),
        )
    private val LANDSCAPES =
        listOf(
            // Sheep Shearer is not in the 667 quest list (QuestList); [quest] passes a quest the list does not have.
            Painting(Items.LUMBRIDGE_PAINTING, "Lumbridge painting", 2_000, quests("Cook's Assistant", "Rune Mysteries", "Sheep Shearer", "The Restless Ghost")),
            Painting(Items.DESERT_PAINTING, "Desert painting", 2_000, quests("Prince Ali Rescue", "The Tourist Trap", "The Feud", "The Golem")),
            Painting(Items.MORYTANIA_PAINTING, "Morytania painting", 2_000, quests("Ghosts Ahoy", "Shades of Mort'ton", "Creature of Fenkenstrain", "Haunted Mine")),
            Painting(Items.KARAMJA_PAINTING, "Karamja painting", 2_000, quests("Pirate's Treasure", "Shilo Village", "Tai Bwo Wannai Trio")),
            Painting(Items.ISAFDAR_PAINTING, "Isafdar painting", 2_000, quests("Roving Elves")),
        )
    private val MAPS =
        listOf(
            Painting(Items.SMALL_MAP, "Small map", 1_000, { QuestStatus.points(it) >= 51 }),
            Painting(Items.MEDIUM_MAP, "Medium map", 1_000, { QuestStatus.points(it) >= 101 }),
            Painting(Items.LARGE_MAP, "Large map", 1_000, { QuestStatus.points(it) >= 151 }),
        )

    private suspend fun painting(task: QueueTask) {
        val player = task.player
        task.chatPlayer("Can I buy a painting?", facialExpression = FacialExpression.NORMAL)
        val stock =
            when (task.options("A portrait, please.", "A landscape, please.", "A map, please.", "Never mind.", title = "What would you like?")) {
                1 -> PORTRAITS
                2 -> LANDSCAPES
                3 -> MAPS
                else -> return
            }
        val open = stock.filter { it.allowed(player) }
        if (open.isEmpty()) {
            task.chatNpc("I'm sorry, I can only let you have one if you've got some connection with it.", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.SAD)
            return
        }
        val pick = task.options(*(open.map { "${it.name} - ${"%,d".format(it.price)} coins" } + "Never mind").toTypedArray(), title = "Buy which?") - 1
        val painting = open.getOrNull(pick) ?: return
        if (player.inventory.getItemCount(Items.COINS_995) < painting.price) {
            task.chatPlayer("I don't have enough money for that.", facialExpression = FacialExpression.SAD)
            return
        }
        if (player.inventory.freeSlotCount == 0 && player.inventory.getItemCount(Items.COINS_995) != painting.price) {
            task.messageBox("You don't have enough inventory space.")
            return
        }
        if (player.inventory.remove(Items.COINS_995, painting.price).completed < painting.price) return
        player.inventory.add(painting.item, 1)
        task.chatNpc("Here you go, enjoy your ${painting.name.lowercase()}!", npc = Npcs.SIR_RENITEE, facialExpression = FacialExpression.HAPPY)
    }
}
