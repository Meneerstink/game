package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setVarbit

/**
 * N98 (owner VRAGEN 29 = C, 2026-10-17): "Rigour 74 Prayer + 70 Def + Dexterous prayer scroll, Augury 77 Prayer + 70 Def + Arcane prayer
 * scroll (eenmalig lezen, zoals OSRS)"; Rapid Renewal stays Prayer 65 only. The scrolls (OSRS 21034 / 21079, imported 24127 / 24128)
 * drop from breach monsters (1/200) and the Deadman's chest (1/10), owner 2026-10-22.
 */
object PrayerScrolls {
    const val DEFENCE_LEVEL = 70

    /** Breach monster kill: 1 in [BREACH_CHANCE] drops one of the two scrolls; a Deadman's chest: 1 in [CHEST_CHANCE]. */
    const val BREACH_CHANCE = 200
    const val CHEST_CHANCE = 10

    val RIGOUR_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "prayer_scroll_rigour")
    val AUGURY_UNLOCKED = AttributeKey<Boolean>(persistenceKey = "prayer_scroll_augury")

    val SCROLLS = intArrayOf(Items.DEXTEROUS_PRAYER_SCROLL, Items.ARCANE_PRAYER_SCROLL)

    private fun keyOf(prayer: Prayer): AttributeKey<Boolean>? =
        when (prayer) {
            Prayer.RIGOUR -> RIGOUR_UNLOCKED
            Prayer.AUGURY -> AUGURY_UNLOCKED
            else -> null
        }

    /** Why [player] may not use [prayer] yet, or null. Only Rigour and Augury have these two extra rules. */
    fun refusal(
        player: Player,
        prayer: Prayer,
    ): String? {
        val key = keyOf(prayer) ?: return null
        if (player.skills.getMaxLevel(Skills.DEFENCE) < DEFENCE_LEVEL) return "You need a Defence level of $DEFENCE_LEVEL to use ${prayer.named}."
        if (player.attr[key] != true) {
            val scroll = if (prayer == Prayer.RIGOUR) "Dexterous prayer scroll" else "Arcane prayer scroll"
            return "You need to learn ${prayer.named} from an $scroll first.".replace("an Dexterous", "a Dexterous")
        }
        return null
    }

    /** "Read": learns the prayer for good and uses the scroll; false when it was already learned (the scroll stays). */
    fun read(
        player: Player,
        scroll: Int,
    ): Boolean {
        val key = if (scroll == Items.DEXTEROUS_PRAYER_SCROLL) RIGOUR_UNLOCKED else AUGURY_UNLOCKED
        if (player.attr[key] == true) return false
        if (player.inventory.remove(scroll, 1).hasFailed()) return false
        player.attr[key] = true
        return true
    }

    /**
     * N358 (owner 2026-10-24: "rigour en augury staat bij mij gekleurd maar heb de scrolls nog niet gebruikt"): the prayer book draws a
     * prayer in colour once the Prayer level is reached (clientscript 52); the client also keeps Rigour and Augury grey while their
     * unlock varbit (5451 / 5452, `Prayers.RIGOUR_UNLOCK_VARBIT`) is 0 (client PrayerScrollUnlocks). Sent at login and when a
     * scroll is read; the Prayer stat is marked dirty so the client redraws both cells at once.
     */
    fun syncUnlocks(player: Player) {
        player.setVarbit(Prayers.RIGOUR_UNLOCK_VARBIT, if (player.attr[RIGOUR_UNLOCKED] == true) 1 else 0)
        player.setVarbit(Prayers.AUGURY_UNLOCK_VARBIT, if (player.attr[AUGURY_UNLOCKED] == true) 1 else 0)
        player.skills.dirty[Skills.PRAYER] = true
    }

    /** One of the two scrolls, or null - [chance] is the 1-in-N roll (breach monster or chest). */
    fun roll(
        random: java.util.Random,
        chance: Int,
    ): Int? = if (random.nextInt(chance) == 0) SCROLLS[random.nextInt(SCROLLS.size)] else null
}
