package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.model.attr.DEATH_TUTORIAL_ATTR
import gg.rsmod.game.model.attr.GRAVESTONE_ANGEL_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.doubleItemMessageBox
import gg.rsmod.plugins.api.ext.focusTab
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.messageBox
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.mechanics.death.DeathPayment
import gg.rsmod.plugins.content.mechanics.death.DeathsDomainConfig
import gg.rsmod.plugins.content.mechanics.death.OfficeInterface
import gg.rsmod.game.model.attr.DEATH_COFFER_ATTR

/**
 * Death's dialogue - the OSRS Wiki "Transcript:Death (NPC)", line for line: the standard dialogue, the first-death tutorial
 * ("Upon losing any items while dying for the first time"), talking to him again during and after the tutorial, and trying
 * to leave early. The tutorial's picture boxes use the OSRS pictures ("Gravestone" / "Death's Coffer" unobtainable items,
 * the watch and 100 coins). The Ultimate Ironman branches do not apply (this server has no ironman modes).
 */
object DeathDialogue {
    // ---- tutorial progress (DEATH_TUTORIAL_ATTR bits) ----
    /** In the office for the first-death explanation; the portal is closed until [TOPICS_DONE]. */
    const val IN_TUTORIAL = 1
    const val EXPLAINED = 2
    const val FEE_TOPIC = 4
    const val TIME_TOPIC = 8
    const val KEPT_TOPIC = 16
    const val TOPICS_DONE = 32

    /** Set when the player first leaves the office after the explanation: it is never given again. */
    const val COMPLETE = 64

    private const val ALL_TOPICS = FEE_TOPIC or TIME_TOPIC or KEPT_TOPIC

    fun flags(player: Player): Int = player.attr[DEATH_TUTORIAL_ATTR] ?: 0

    fun has(player: Player, flag: Int): Boolean = flags(player) and flag != 0

    fun set(player: Player, flag: Int) {
        player.attr[DEATH_TUTORIAL_ATTR] = flags(player) or flag
    }

    fun inTutorial(player: Player): Boolean = has(player, IN_TUTORIAL)

    /** The portal may be used: no tutorial running, or every topic covered. */
    fun mayLeave(player: Player): Boolean = !inTutorial(player) || has(player, TOPICS_DONE)

    /** OSRS (Death Changes news post): "the first time you die you'll be taken to Death's Office". */
    fun needsTutorial(player: Player): Boolean = !has(player, COMPLETE)

    /** Leaving through the portal after the explanation ends it for good. */
    fun leave(player: Player) {
        if (inTutorial(player)) {
            player.attr[DEATH_TUTORIAL_ATTR] = (flags(player) and IN_TUTORIAL.inv()) or COMPLETE
        }
    }

    /** The Worn Equipment tab (the root-package `Tabs.EQUIPMENT`, gameframe tab 5). */
    private const val WORN_EQUIPMENT_TAB = 5

    private val red = "<col=9f0000>"
    private const val END = "</col>"

    // ---- Items used as the tutorial's pictures (imported by OsrsItemImportTool batch "deaths-office-pictures") ----
    private val GRAVE_PICTURE get() = DeathsOfficeIds.GRAVESTONE_PICTURE
    private val COFFER_PICTURE get() = DeathsOfficeIds.COFFER_PICTURE

    private suspend fun QueueTask.death(vararg lines: String) = chatNpc(*lines, npc = DeathsOfficeArea.DEATH, facialExpression = FacialExpression.NORMAL)

    private suspend fun QueueTask.me(vararg lines: String) = chatPlayer(*lines, facialExpression = FacialExpression.NORMAL)

    /** "Talk-to" Death. */
    suspend fun QueueTask.talk() {
        when {
            inTutorial(player) && has(player, TOPICS_DONE) -> {
                death("Would you like me to remind you about gravestones?")
                tutorialOptions()
            }
            inTutorial(player) && has(player, EXPLAINED) -> {
                death("I believe I was explaining gravestones to you. We still have some more topics to cover.")
                tutorialOptions()
            }
            inTutorial(player) -> firstDeath()
            else -> standard()
        }
    }

    // ---- first death ----

    suspend fun QueueTask.firstDeath() {
        death(
            "So, mortal... you have died, as all mortals do. As you have suffered the loss of some items on this occasion, " +
                "I have brought you here to explain for you how that works.",
        )
        me("I lost items? Can I get them back?")
        death("Let me explain... Across most of the land of Gielinor, when you die, some of the items you're carrying are dropped.")
        explainGravestones()
        set(player, EXPLAINED)
        tutorialOptions()
    }

    private suspend fun QueueTask.explainGravestones() {
        itemMessageBox("A gravestone appears on the ground ${red}near where you died$END. You can go there and retrieve your items by clicking on it.", GRAVE_PICTURE)
        itemMessageBox("Your gravestone would be marked with an arrow to help to spot it. A grave icon appears on the ${red}world map$END too, if your grave is in a mapped area.", GRAVE_PICTURE)
        // Owner 2026-09-26: the gravestone is free; others can bless or repair it (RS 2009).
        itemMessageBox(
            "Taking your items back from a gravestone is ${red}free of charge$END. Only you can loot it, but other players can see it - " +
                "a kind soul may even ${red}bless or repair$END it to make it last longer.",
            GRAVE_PICTURE,
        )
        itemMessageBox("A gravestone does not last forever. After ${red}about 15 minutes$END, a gravestone will collapse. Anything in it will be sent here, to me.", GRAVE_PICTURE)
        death(
            "I will look after such items, ${red}without a time limit$END. However, I do charge more for my services. " +
                "It would be cheaper to retrieve them from the gravestone directly, if you can reach it in time.",
        )
    }

    private fun topic(label: String, done: Boolean): String = if (done) "<str>$label</str>" else label

    private suspend fun QueueTask.tutorialOptions() {
        while (true) {
            val choice =
                options(
                    topic("Tell me about gravestones again.", true),
                    topic("How do I pay your fee?", has(player, FEE_TOPIC)),
                    topic("How long do I have to return to my gravestone?", has(player, TIME_TOPIC)),
                    topic("How do I know what will happen to my items when I die?", has(player, KEPT_TOPIC)),
                    "I think I'm done here.",
                )
            when (choice) {
                1 -> {
                    me("Tell me about gravestones again.")
                    death("Gladly... Across most of the land of Gielinor, when you die, some of the items you're carrying are dropped.")
                    explainGravestones()
                }
                2 -> {
                    me("How do I pay your fee?")
                    death("Your gravestone costs you nothing. Only what reaches me costs a little: cash is always acceptable, and I can take it from your bank too.")
                    death(
                        "Besides that, take a look at my Coffer here. You can bring your unwanted possessions, and sacrifice them into my Coffer. " +
                            "I'll count their value against any future fees I charge you.",
                    )
                    itemMessageBox(
                        "Death's Coffer won't accept items worth less than ${red}10,000 coins per item$END - Death doesn't want cheap junk! " +
                            "But more valuable items can be sacrificed to pay down future reclamation fees.",
                        COFFER_PICTURE,
                    )
                    set(player, FEE_TOPIC)
                }
                3 -> {
                    me("How long do I have to return to my gravestone?")
                    itemMessageBox(
                        "A gravestone lasts for ${red}at least 15 minutes$END before it collapses. However, that timer ${red}pauses if you become inactive$END, " +
                            "in case you have lost your connection to the world.",
                        GRAVE_PICTURE,
                    )
                    doubleItemMessageBox("A timer ${red}on your head-up display$END shows the remaining time before your gravestone will collapse.", GRAVE_PICTURE, Items.WATCH)
                    set(player, TIME_TOPIC)
                }
                4 -> {
                    me("How do I know what will happen to my items when I die?")
                    death("There is a menu to predict these things for you. Take a look at your Worn Items side-panel...")
                    // The worn equipment tab opens (tab 5 of this gameframe).
                    player.focusTab(WORN_EQUIPMENT_TAB)
                    messageBox("The ${red}Items Kept on Death$END menu tells you roughly what your items will do when you next die.")
                    set(player, KEPT_TOPIC)
                }
                5 -> {
                    me("I think I'm done here.")
                    if (flags(player) and ALL_TOPICS != ALL_TOPICS) {
                        death("I'm afraid we have more topics to cover before you leave. Speak to me when you are ready to continue.")
                    } else {
                        death("Very well. Once you pass through my portal, you will have 15 minutes to reach your gravestone.")
                        set(player, TOPICS_DONE)
                    }
                    return
                }
                else -> return
            }
        }
    }

    /** "Attempting to leave before finishing the tutorial". */
    suspend fun QueueTask.notFinished() {
        death("I haven't finished talking to you yet, ${player.username}. Please don't worry about your gravestone - it will wait until you leave this place.")
    }

    // ---- standard dialogue ----

    suspend fun QueueTask.standard() {
        death("Hello mortal. Have you come to retrieve something you lost when you died?")
        mainOptions()
    }

    private suspend fun QueueTask.mainOptions() {
        while (true) {
            when (options("How does that work?", "What is this place?", "Yes, have you got anything for me?", "More options...")) {
                1 -> {
                    me("How does that work?")
                    death(
                        "When you die, you often drop things you were carrying. In most cases, a gravestone appears near where you died, " +
                            "and your items can be retrieved there.",
                    )
                    death(
                        "However, gravestones do not last for long. When your gravestone collapses, your items come here, to me. " +
                            "I can return them to you - though I charge a fee for my services.",
                    )
                    death(
                        "Aside from that, if you bring me your unwanted possessions, and sacrifice them into my Coffer here, " +
                            "I'll count their value against any future fees I charge you.",
                    )
                    death("I don't accept just any junk, but I'll try to give you a good price for anything decent.")
                    death("So do you need to retrieve something you lost when you died?")
                }
                2 -> {
                    me("What is this place?")
                    death(
                        "As you should be able to see, I am Death. The Grim Reaper. The Inexorable One. The Inescapable Grasp of Mortal Destiny. " +
                            "And this is my little office. Nice, isn't it?",
                    )
                    when (options("It's lovely.", "Your decorations leave a bit to be desired.")) {
                        1 -> {
                            me("It's lovely.")
                            death("Thank you. I feel the colour scheme suits my style. Now, can I help you retrieve something you lost when you died?")
                        }
                        2 -> {
                            me("Your decorations leave a bit to be desired.")
                            death(
                                "Sometimes it's hard to care for beauty when one's very existence is just seen as a curse upon the world. " +
                                    "Now, do you actually want to retrieve something from me?",
                            )
                        }
                    }
                    return
                }
                3 -> {
                    me("Yes, have you got anything for me?")
                    OfficeInterface.open(player)
                    return
                }
                4 -> if (!moreOptions()) return
                else -> return
            }
        }
    }

    /** @return true to go back to the first options ("Previous options..."). */
    private suspend fun QueueTask.moreOptions(): Boolean {
        when (options("Can I choose a different-looking gravestone?", "Can I change how I die?", "Can I change where I respawn?", "Not just now, thanks.", "Previous options...")) {
            1 -> {
                me("Can I choose a different-looking gravestone?")
                chooseGravestone()
                return false
            }
            2 -> {
                death("Er... I suppose so.")
                death("How would you like to... die?")
                // Historical transcript "Choosing how you would like to die": the only animation this server has is the default.
                if (options("Default.", title = "Animation to use for death.") == 1) {
                    death("You are already using that.")
                    death("Have you come to retrieve something you lost from a previous gravestone?")
                    return true
                }
                return false
            }
            3 -> {
                // Owner 2026-09-26: respawn points are bought and switched here (RespawnPoints).
                with(RespawnPoints) { respawnDialogue() }
                return false
            }
            4 -> {
                me("Not just now, thanks.")
                death("Really? There isn't much else for you to do here. But you know your business.")
                return false
            }
            5 -> return true
        }
        return false
    }

    private suspend fun QueueTask.chooseGravestone() {
        while (true) {
            val angelCost = DeathsDomainConfig.current.angelCost
            // The transcript: "a choice between the basic or angel gravestone, which are free and 200k coins respectively".
            val choice = options("Basic gravestone (free).", "Angel of Death gravestone (${String.format(java.util.Locale.US, "%,d", angelCost)} coins).", title = "Select a gravestone")
            val angel = player.attr[GRAVESTONE_ANGEL_ATTR] == true
            when (choice) {
                1 ->
                    if (angel) {
                        death("Do you really want to switch to the basic gravestone? I'm not offering to give back the fee you paid earlier.")
                        if (options("Yes, I want the basic gravestone.", "Actually, never mind.") == 1) {
                            me("Yes, I want the basic gravestone.")
                            player.attr[GRAVESTONE_ANGEL_ATTR] = false
                            GravestoneWorld.respawn(player)
                            messageBox("Your gravestone is now the basic type.")
                        } else {
                            me("Actually, never mind.")
                        }
                        return
                    } else {
                        death("You've already got the basic gravestone.")
                        if (options("Can I choose a different-looking gravestone?", "Actually, never mind.") == 1) {
                            me("Can I choose a different-looking gravestone?")
                            continue
                        }
                        me("Actually, never mind.")
                        return
                    }
                2 -> {
                    // "I can take coins from your inventory, or from my Coffer."
                    val carried = player.inventory.getItemCount(Items.COINS_995).toLong()
                    val coffer = DeathPayment.coffer(player).toLong()
                    if (carried + coffer < angelCost) {
                        death("You haven't got ${String.format(java.util.Locale.US, "%,d", angelCost)} coins to pay for that. I can take coins from your inventory, or from my Coffer.")
                        if (options("Can I choose a different-looking gravestone?", "Actually, never mind.") == 1) {
                            me("Can I choose a different-looking gravestone?")
                            continue
                        }
                        me("Actually, never mind.")
                        return
                    }
                    death(
                        "Most people won't see your grave, by the way. It appears to you, but it aims to be invisible to everyone else. " +
                            "Are you sure you want to buy a cosmetic override for it?",
                    )
                    if (options("Yes, I understand other people won't often see it.", "No thanks, it's not worth it.") == 1) {
                        me("Yes, I understand other people won't often see it.")
                        if (payAngel(player, angelCost.toLong())) {
                            player.attr[GRAVESTONE_ANGEL_ATTR] = true
                            GravestoneWorld.respawn(player)
                            messageBox("Your gravestone is now the Angel of Death.")
                        }
                    } else {
                        me("No thanks, it's not worth it.")
                    }
                    death("It is not for me to judge how people spend their money.")
                    return
                }
                else -> return
            }
        }
    }

    /** Coins from the inventory first, then the coffer ("I can take coins from your inventory, or from my Coffer"). */
    private fun payAngel(
        player: Player,
        cost: Long,
    ): Boolean {
        val carried = player.inventory.getItemCount(Items.COINS_995).toLong()
        val coffer = DeathPayment.coffer(player).toLong()
        if (carried + coffer < cost) return false
        val fromInventory = minOf(carried, cost).toInt()
        if (fromInventory > 0) player.inventory.remove(Item(Items.COINS_995, fromInventory), assureFullRemoval = true)
        val fromCoffer = cost - fromInventory
        if (fromCoffer > 0) player.attr[DEATH_COFFER_ATTR] = (coffer - fromCoffer).toInt()
        return true
    }
}
