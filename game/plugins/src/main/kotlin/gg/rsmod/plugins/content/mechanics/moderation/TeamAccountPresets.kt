package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.serializer.TeamTestAccounts
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.calculateAndSetCombatLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses

/**
 * T3 (owner 2026-10-13): what each fixed team test account ([TeamTestAccounts], C:\RSPS\team\accounts.txt) wears at every
 * login on the test server - all skills 99 and one gear set each, so two accounts can PvP at once:
 *   teamtest1 - the owner's own hybrid set (Toxic staff of the dead + Elidinis' ward, Karil's), normal prayers
 *   teamtest2 - the owner's own ranged set (Dark bow + dragon arrows, Karil's 100), Ancient Curses
 *   teamtest3 - OSRS melee (::gear_torva), Ancient Curses
 *   teamtest4 - plain 667 gear (Bandos, whip, dragon defender), normal prayers - AGENTS.md 1.3: every visual check
 *               runs with 667 gear AND the owner's OSRS gear
 * The owner's sets are the two Equipment lines that occur most in his bugs\ reports (13x and 12x, 2026-10-03..10-13).
 * Many ported OSRS items still have bugs (owner 2026-10-13): every piece that does not go on is listed in the chat and
 * EquipAction writes a PROBLEM trace for it. The accounts keep the owner rank, so ::gear_<set> / ::osrsgear switch sets.
 */
object TeamAccountPresets {
    private val OWNER_COMMON = listOf(
        Items.NEITIZNOT_FACEGUARD, Items.INFERNAL_CAPE, Items.AMULET_OF_FURY, Items.GLOVES_7462, Items.ETERNAL_BOOTS,
        Items.RING_OF_SUFFERING_R,
    ).map { it to 1 }

    private val FOOD = listOf(Items.SHARK to 10)

    class Account(val name: String, val curses: Boolean, val preset: GearPresets.Preset)

    val ALL: List<Account> =
        listOf(
            Account(
                "teamtest1", false,
                GearPresets.Preset(
                    "teamtest1", "Owner hybrid (Toxic staff of the dead + Elidinis' ward, Karil's)",
                    OWNER_COMMON + listOf(Items.TOXIC_STAFF_OF_THE_DEAD to 1, Items.ELIDINIS_WARD to 1, Items.KARILS_TOP to 1,
                        Items.KARILS_SKIRT to 1, Items.OPAL_DRAGON_BOLTS_E to 301),
                    FOOD,
                ),
            ),
            Account(
                "teamtest2", true,
                GearPresets.Preset(
                    "teamtest2", "Owner ranged (Dark bow + dragon arrows, Karil's 100)",
                    OWNER_COMMON + listOf(Items.DARK_BOW to 1, Items.KARILS_TOP_100 to 1, Items.KARILS_SKIRT_100 to 1,
                        Items.DRAGON_ARROW to 98),
                    FOOD,
                ),
            ),
            Account("teamtest3", true, GearPresets.forCommand("gear_torva")!!),
            Account(
                "teamtest4", false,
                GearPresets.Preset(
                    "teamtest4", "Plain 667 melee (Bandos, Abyssal whip, Dragon defender)",
                    listOf(Items.HELM_OF_NEITIZNOT, Items.FIRE_CAPE, Items.AMULET_OF_FURY, Items.BANDOS_CHESTPLATE, Items.BANDOS_TASSETS,
                        Items.ABYSSAL_WHIP, Items.DRAGON_DEFENDER, Items.GLOVES_7462, Items.DRAGON_BOOTS, Items.BERSERKER_RING).map { it to 1 },
                    listOf(Items.DRAGON_CLAWS to 1) + FOOD,
                ),
            ),
        )

    fun forName(name: String): Account? = ALL.firstOrNull { it.name == name.trim().lowercase() }

    /**
     * Set once [apply] ran for this login (T1): a bug replay (BugReplay) waits for it, because the preset runs 2 ticks after
     * login and would otherwise overwrite the replayed gear. Not persisted - every login is a fresh account.
     */
    val PRESET_APPLIED_ATTR = AttributeKey<Boolean>()

    /**
     * The Ancient Curses book on a team test account (the preset, and a bug replay of a curses report - BugReplay): unlocked
     * as the Temple at Senntisten reward does (FoundationQuests), what a real curses player has.
     */
    fun useCurses(player: Player) {
        if (forName(player.username) == null) return
        player.attr[AncientCurses.NPC_UNLOCKED_ATTR] = true
        player.attr[AncientCurses.UNLOCKED_ATTR] = true
        if (AncientCurses.getBook(player) != AncientCurses.PrayerBook.ANCIENT) AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
    }

    /** All skills 99, stats full, the account's prayer book and gear set. */
    fun apply(player: Player) {
        val account = forName(player.username) ?: return
        for (i in gg.rsmod.plugins.content.mechanics.removed.RemovedSkills.activeSkills(player)) {
            player.skills.setBaseLevel(i, 99)
        }
        player.calculateAndSetCombatLevel()
        player.setCurrentLifepoints(player.getMaximumLifepoints())
        player.setCurrentPrayerPoints(player.getMaximumPrayerPoints())
        player.runEnergy = 100.0
        if (account.curses) useCurses(player)
        GearPresets.apply(player, account.preset)
        player.attr[PRESET_APPLIED_ATTR] = true
        player.message("<col=ff981f>Team test account ${account.name}</col>: 99 all, ${if (account.curses) "Ancient Curses" else "normal prayers"}, " +
            "test server only, nothing is saved. Other sets: ::gear_<set>, ::osrsgear list.")
    }
}
