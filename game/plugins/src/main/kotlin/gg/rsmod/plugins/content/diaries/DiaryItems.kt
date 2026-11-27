package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * DIARIES-OSRS D6: what the reward items do (OSRS Wiki item pages, 27-11).
 *  - Kandarin headgear 1-4: "Acts as a light source when worn or held in the inventory"; worn: double logs from regular trees (no
 *    extra xp). Headgear 3: one teleport to Sherlock per day, headgear 4: unlimited (options "Teleport", worn "Teleport").
 *  - Western banner 3: one teleport to the Piscatoris Fishing Colony per day, banner 4: unlimited. Banner 1/2 have the option too, but
 *    (wiki) "the ability to teleport using the banner is not available until the completion of the hard or elite" diary.
 *  - Wilderness sword 3/4 teleports are DmmItems.wildernessSwordTeleport (already there).
 * Days are UTC days (OSRS resets at 00:00 UTC).
 */
object DiaryItems {
    private val SHERLOCK = Tile(2733, 3415, 0)
    private val PISCATORIS = Tile(2337, 3690, 0)
    private val KANDARIN_DAY = AttributeKey<Int>(persistenceKey = "diary_kandarin_tele_day")
    private val WESTERN_DAY = AttributeKey<Int>(persistenceKey = "diary_western_tele_day")

    fun utcDay(): Int = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt()

    fun tierOf(player: Player, diary: Diary, item: Int): Int = DiaryRewards.items(player.world, diary).indexOf(item) + 1

    fun headgearWorn(player: Player): Boolean = DiaryRewards.items(player.world, Diary.KANDARIN).any { it >= 0 && player.equipment.contains(it) }

    fun headgearCarried(player: Player): Boolean =
        DiaryRewards.items(player.world, Diary.KANDARIN).any { it >= 0 && (player.equipment.contains(it) || player.inventory.contains(it)) }

    fun headgearTeleport(player: Player, item: Int) {
        val tier = tierOf(player, Diary.KANDARIN, item)
        if (tier < 3) {
            player.message("Your headgear needs to be upgraded before it can teleport you.")
            return
        }
        daily(player, tier == 3, KANDARIN_DAY, "headgear") { player.teleport(SHERLOCK, TeleportType.JEWELRY) }
    }

    fun bannerTeleport(player: Player, item: Int) {
        val tier = tierOf(player, Diary.WESTERN, item)
        if (tier < 3) {
            player.message("Your banner needs to be upgraded before it can teleport you.")
            return
        }
        daily(player, tier == 3, WESTERN_DAY, "banner") { player.teleport(PISCATORIS, TeleportType.JEWELRY) }
    }

    private fun daily(player: Player, limited: Boolean, key: AttributeKey<Int>, what: String, go: () -> Unit) {
        player.canTeleport(TeleportType.JEWELRY) {
            if (limited) {
                val today = utcDay()
                if (player.attr[key] == today) {
                    player.message("You have already used your $what's teleport today. It recharges at midnight.")
                    return@canTeleport
                }
                player.attr[key] = today
            }
            go()
        }
    }

    /** Wilderness elite: dragon bones of a kill in the Wilderness drop noted. */
    fun wildyDragonBones(player: Player, tile: Tile, items: List<gg.rsmod.game.model.item.Item>): List<gg.rsmod.game.model.item.Item> {
        if (items.none { it.id == gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES }) return items
        if (!Diaries.hasClaimed(player, Diary.WILDERNESS, Tier.ELITE)) return items
        if (tile.getWildernessLevel() <= 0) return items
        return items.map { if (it.id == gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES) gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES_NOTED, it.amount) else it }
    }

    /** Kandarin hard: Camelot Teleport lands at Seers' Village bank when the player chose that at the Wedge. */
    val CAMELOT_SEERS = AttributeKey<Boolean>(persistenceKey = "diary_camelot_seers")

    fun camelotToSeers(player: Player): Boolean =
        player.attr[CAMELOT_SEERS] == true && Diaries.hasClaimed(player, Diary.KANDARIN, Tier.HARD)

    /** Varrock medium: Varrock Teleport lands at the Grand Exchange when the player chose that at Toby. */
    val VARROCK_GE = AttributeKey<Boolean>(persistenceKey = "diary_varrock_ge")

    fun varrockToGe(player: Player): Boolean =
        player.attr[VARROCK_GE] == true && Diaries.hasClaimed(player, Diary.VARROCK, Tier.MEDIUM)

    /** Kandarin medium: "10% increased experience from cutting maple trees in Seers' Village". */
    fun mapleXpBonus(player: Player): Double =
        if (Diaries.hasClaimed(player, Diary.KANDARIN, Tier.MEDIUM) && DiaryTasks.SEERS.has(player.tile)) 1.1 else 1.0
}
