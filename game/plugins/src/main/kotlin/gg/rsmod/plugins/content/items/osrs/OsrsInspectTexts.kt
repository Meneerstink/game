package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.message

/**
 * N557 (owner 2026-11-02 'Inspect basilisk jaw geeft verkeerde tekst' - an example of the whole class): the OSRS answer to "Inspect" on
 * every OSRS-ported material that has the option. Source per line: the OSRS Wiki item transcript ('Transcript:<item>', `tbox` = the
 * item message box with the item's picture) or, where the wiki has no transcript, the item page's own quote of the chatbox message
 * ("When inspected, a message in the chatbox states ..."). Items without a sourced text keep the examine fallback in
 * osrs_census_options.plugin.kts (Bottled dragonbreath (unpowered), Tormented synapse - its lines depend on quests this world lacks).
 */
object OsrsInspectTexts {
    /** OSRS: the basilisk jaw story heard from Olaf the Bard ("The player can now create Neitiznot faceguards."). */
    val BASILISK_STORY = AttributeKey<Boolean>("basilisk_jaw_story")

    private sealed class Answer {
        /** Transcript `tbox`: the item message box. */
        class Box(val text: String, val playerLine: String? = null) : Answer()

        /** Wiki page quote: a chatbox message. */
        class Chat(val text: String) : Answer()
    }

    private const val QUARTZ =
        "It's a strange chunk of quartz filled with an ancient magical power. You might be able to combine it with something else."
    private const val VESTIGE = "It's an ancient vestige filled with power. You might be able to combine it with something else."
    private const val OLD_ICON = "It's an icon from an old ring. With the help of a Fremennik, you might be able to combine it with something else."
    private const val POWER_ICON = "It's an ancient icon filled with power. You might be able to use it to craft a powerful ring."

    private val FIXED: Map<Int, Answer> =
        mapOf(
            Items.AVERNIC_DEFENDER_HILT to
                Answer.Box("You raise the hilt, inspecting each section carefully. It looks as though it could combine with a powerful parrying dagger."),
            Items.HYDRA_LEATHER to Answer.Box("This leather looks pretty tough to work with... Maybe the dragonkin had a way."),
            Items.VORKATHS_HEAD to Answer.Box("Perhaps Ava would like to see this?"),
            Items.VOIDWAKER_HILT to
                Answer.Chat("A sturdy hilt. It's part of what looks to be a powerful weapon. Perhaps someone in Ferox Enclave would know what it is?"),
            Items.VOIDWAKER_BLADE to
                Answer.Chat(
                    "A sharp blade. It looks to be the deadliest part of a powerful weapon. Perhaps someone in Ferox Enclave would know what it is?",
                ),
            Items.VOIDWAKER_GEM to
                Answer.Chat(
                    "A magical gem. It looks like it could be fitted into a powerful weapon. Perhaps someone in Ferox Enclave would know what it is?",
                ),
            Items.KODAI_INSIGNIA to
                Answer.Chat("You sense a dark magic emanating from the insignia. It looks like this could be attached to a wand."),
            Items.MENAPHITE_ORNAMENT_KIT to Answer.Box("A magical dye. It looks like it could fit a fortified version of Elidinis' Ward."),
            Items.MAGUS_VESTIGE to Answer.Box(VESTIGE),
            Items.VENATOR_VESTIGE to Answer.Box(VESTIGE),
            Items.SEERS_ICON to Answer.Box(OLD_ICON),
            Items.ARCHER_ICON to Answer.Box(OLD_ICON),
            Items.MAGUS_ICON to Answer.Box(POWER_ICON),
            Items.VENATOR_ICON to Answer.Box(POWER_ICON),
            Items.AQUANITE_TENDON to
                Answer.Box(
                    "This tendon is very springy and stretchy.",
                    playerLine = "With this and a bit of mithril, I reckon I could make something useful to use with my crossbow...",
                ),
            Items.CONFLICTION_GAUNTLETS to
                Answer.Box(
                    "A pair of magical gauntlets that when worn grant your next magic attack of the same type a doubled chance of hitting if " +
                        "the previous attack missed.",
                ),
            Items.ANCIENT_ICON to Answer.Chat("The icon has a strange magical aura about it. Perhaps Eblis will be able to tell you more..."),
            Items.BLOOD_QUARTZ to Answer.Box(QUARTZ),
            Items.ICE_QUARTZ to Answer.Box(QUARTZ),
            Items.SHADOW_QUARTZ to Answer.Box(QUARTZ),
            Items.SMOKE_QUARTZ to Answer.Box(QUARTZ),
            Items.MASORI_CRAFTING_KIT to
                Answer.Box(
                    "A crafting kit containing fabric, leather and various other pieces in the colours of Masori equipment. It looks like it " +
                        "could be used to alter an Ava's Assembler.",
                ),
            Items.THREAD_OF_ELIDINIS to Answer.Box("This thread looks like it could be used to augment a rune pouch."),
        )

    private fun answer(
        player: Player,
        itemId: Int,
    ): Answer? =
        when (itemId) {
            // Transcript:Basilisk jaw - before / after speaking to Olaf the Bard.
            Items.BASILISK_JAW ->
                Answer.Box(
                    if (player.attr[BASILISK_STORY] == true) {
                        "It's the jaw of a Basilisk Knight. It looks like it could be attached to a Helm of Neitiznot."
                    } else {
                        "It's the jaw of a Basilisk Knight. Perhaps Olaf the Bard knows a good story about it."
                    },
                )
            // Transcript:Belle's folly (tarnished) - by Smithing level (70).
            Items.BELLES_FOLLY_TARNISHED ->
                Answer.Box(
                    if (player.skills.getMaxLevel(Skills.SMITHING) < 70) {
                        "The blade isn't in a great state, but it should be possible to repair it. However, you unfortunately don't feel you " +
                            "have the skills needed to do so yourself. To repair the sword, you'll need 70 Smithing."
                    } else {
                        "The blade isn't in a great state, but it should be possible to repair it. You think you have the skills needed to do " +
                            "so yourself. You'll need to use the polishing wheel in the Giants' Foundry."
                    },
                )
            else -> FIXED[itemId]
        }

    fun has(itemId: Int): Boolean = itemId == Items.BASILISK_JAW || itemId == Items.BELLES_FOLLY_TARNISHED || itemId in FIXED

    /** Plays the OSRS inspect answer for [itemId]; false when there is no sourced one. */
    fun inspect(
        player: Player,
        itemId: Int,
    ): Boolean {
        when (val a = answer(player, itemId) ?: return false) {
            is Answer.Chat -> player.message(a.text)
            is Answer.Box ->
                player.queue {
                    itemMessageBox(a.text, itemId)
                    a.playerLine?.let { chatPlayer(it) }
                }
        }
        return true
    }

    /** For tests. */
    fun textFor(
        player: Player,
        itemId: Int,
    ): String? =
        when (val a = answer(player, itemId)) {
            is Answer.Box -> a.text
            is Answer.Chat -> a.text
            null -> null
        }
}
