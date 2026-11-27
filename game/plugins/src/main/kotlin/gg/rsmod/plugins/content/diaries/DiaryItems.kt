package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.sendRunEnergy
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

    /**
     * Ardougne (OSRS Wiki "Ardougne Diary" rewards): "10% increased chance of successfully pickpocketing within Ardougne" (medium) and
     * "anywhere around Gielinor" (hard).
     */
    fun ardougnePickpocketFactor(player: Player): Double = when {
        Diaries.hasClaimed(player, Diary.ARDOUGNE, Tier.HARD) -> 1.1
        Diaries.hasClaimed(player, Diary.ARDOUGNE, Tier.MEDIUM) && DiaryTasks.NEAR_ARDOUGNE.has(player.tile) -> 1.1
        else -> 1.0
    }

    /** Ardougne cloak 2 / 3: three / five teleports a day to the Ardougne farm; cloak 4 unlimited (OSRS Wiki). Day = 00:00 UTC. */
    val FARM_TELE_DAY = AttributeKey<Int>(persistenceKey = "diary_ard_farm_day")
    val FARM_TELE_USED = AttributeKey<Int>(persistenceKey = "diary_ard_farm_used")

    fun farmTeleLimit(cloak: Int): Int = when (cloak) {
        gg.rsmod.plugins.api.cfg.Items.ARDOUGNE_CLOAK_2 -> 3
        gg.rsmod.plugins.api.cfg.Items.ARDOUGNE_CLOAK_3 -> 5
        else -> Int.MAX_VALUE
    }

    private fun today() = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt()

    fun farmTelesUsed(player: Player): Int = if (player.attr[FARM_TELE_DAY] == today()) player.attr[FARM_TELE_USED] ?: 0 else 0

    /** True (and counted) when [cloak] still has a farm teleport left today; the counter is shared by every cloak, like OSRS. */
    fun useFarmTele(player: Player, cloak: Int): Boolean {
        val used = farmTelesUsed(player)
        if (used >= farmTeleLimit(cloak)) return false
        player.attr[FARM_TELE_DAY] = today()
        player.attr[FARM_TELE_USED] = used + 1
        return true
    }

    /** Explorer's ring (OSRS Wiki): run energy 50% 2x / 3x / 4x a day (ring 1-3), ring 4 full energy 3x a day. Shared day counter. */
    val EXPLORER_RUN_DAY = AttributeKey<Int>(persistenceKey = "diary_explorer_run_day")
    val EXPLORER_RUN_USED = AttributeKey<Int>(persistenceKey = "diary_explorer_run_used")
    private val EXPLORER_RINGS = intArrayOf(gg.rsmod.plugins.api.cfg.Items.EXPLORERS_RING_1, gg.rsmod.plugins.api.cfg.Items.EXPLORERS_RING_2,
        gg.rsmod.plugins.api.cfg.Items.EXPLORERS_RING_3, gg.rsmod.plugins.api.cfg.Items.EXPLORERS_RING_4)

    fun explorerTier(ring: Int): Int = EXPLORER_RINGS.indexOf(ring) + 1

    fun explorerRunUses(ring: Int): Int = when (explorerTier(ring)) { 1 -> 2; 2 -> 3; 3 -> 4; else -> 3 }

    fun explorerRunRestore(player: Player, ring: Int): String {
        val used = if (player.attr[EXPLORER_RUN_DAY] == today()) player.attr[EXPLORER_RUN_USED] ?: 0 else 0
        if (used >= explorerRunUses(ring)) return "You have used all of your ring's run energy restores for today."
        if (player.runEnergy >= 100.0) return "You already have full run energy."
        val add = if (explorerTier(ring) == 4) 100.0 else 50.0
        player.runEnergy = minOf(100.0, player.runEnergy + add)
        player.sendRunEnergy(player.runEnergy.toInt())
        player.attr[EXPLORER_RUN_DAY] = today()
        player.attr[EXPLORER_RUN_USED] = used + 1
        return "You feel refreshed and renewed."
    }

    /** Explorer's ring 2: three cabbage-patch teleports a day (South Falador farm); ring 3 and 4 unlimited (OSRS Wiki). */
    val EXPLORER_TELE_DAY = AttributeKey<Int>(persistenceKey = "diary_explorer_tele_day")
    val EXPLORER_TELE_USED = AttributeKey<Int>(persistenceKey = "diary_explorer_tele_used")
    val CABBAGE_PATCH = Tile(3053, 3284, 0)

    fun cabbageTeleport(player: Player, ring: Int) {
        if (explorerTier(ring) < 2) { player.message("Your ring needs to be upgraded to teleport you to the cabbage patch."); return }
        player.canTeleport(TeleportType.JEWELRY) {
            if (!useCabbageTele(player, ring)) player.message("You have used all of your cabbage-patch teleports for today.")
            else player.teleport(CABBAGE_PATCH, TeleportType.JEWELRY)
        }
    }

    private fun useCabbageTele(player: Player, ring: Int): Boolean {
        if (explorerTier(ring) >= 3) return true
        val used = if (player.attr[EXPLORER_TELE_DAY] == today()) player.attr[EXPLORER_TELE_USED] ?: 0 else 0
        if (used >= 3) return false
        player.attr[EXPLORER_TELE_DAY] = today()
        player.attr[EXPLORER_TELE_USED] = used + 1
        return true
    }

    /** Lumbridge & Draynor elite: "Ability to use fairy rings without the need of a Dramen or Lunar staff" (claimed elite). */
    fun fairyRingsWithoutStaff(player: Player): Boolean = Diaries.hasClaimed(player, Diary.LUMBRIDGE, Tier.ELITE)

    /**
     * Falador shield prayer restore (OSRS Wiki "Falador shield"): shield 1 = 25%, 2 = 50%, 3 = 100% once a day, 4 = 100% twice a day.
     * The day counter is shared by every shield (00:00 UTC). Returns the message for the player.
     */
    val FALADOR_PRAYER_DAY = AttributeKey<Int>(persistenceKey = "diary_fally_pray_day")
    val FALADOR_PRAYER_USED = AttributeKey<Int>(persistenceKey = "diary_fally_pray_used")

    fun faladorShieldPercent(shield: Int): Int = when (shield) {
        gg.rsmod.plugins.api.cfg.Items.FALADOR_SHIELD_1 -> 25
        gg.rsmod.plugins.api.cfg.Items.FALADOR_SHIELD_2 -> 50
        else -> 100
    }

    fun faladorShieldUses(shield: Int): Int = if (shield == gg.rsmod.plugins.api.cfg.Items.FALADOR_SHIELD_4) 2 else 1

    fun faladorPrayerUsed(player: Player): Int = if (player.attr[FALADOR_PRAYER_DAY] == today()) player.attr[FALADOR_PRAYER_USED] ?: 0 else 0

    fun faladorPrayerRestore(player: Player, shield: Int): String {
        val skills = player.skills
        val max = skills.getMaxLevel(gg.rsmod.plugins.api.Skills.PRAYER)
        val cur = skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.PRAYER)
        val used = faladorPrayerUsed(player)
        if (used >= faladorShieldUses(shield)) return "You have already used your shield's prayer restore today."
        if (cur >= max) return "You already have full prayer points."
        val add = maxOf(1, max * faladorShieldPercent(shield) / 100)
        skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.PRAYER, minOf(max, cur + add))
        player.attr[FALADOR_PRAYER_DAY] = today()
        player.attr[FALADOR_PRAYER_USED] = used + 1
        return "You feel a surge of energy as your prayer points are restored."
    }

    /** Ardougne medium / hard / elite: Wizard Cromperty gives 100 / 150 / 250 noted pure essence once a day (OSRS Wiki). */
    val CROMPERTY_DAY = AttributeKey<Int>(persistenceKey = "diary_cromperty_day")

    fun crompertyEssence(player: Player): Int = when (Diaries.claimed(player, Diary.ARDOUGNE)) {
        2 -> 100
        3 -> 150
        4 -> 250
        else -> 0
    }

    fun crompertyClaimedToday(player: Player) = player.attr[CROMPERTY_DAY] == today()

    fun markCrompertyToday(player: Player) { player.attr[CROMPERTY_DAY] = today() }

    /** Ardougne hard: "Watchtower Teleport can now be used to teleport to Yanille instead" (chosen at Two-pints). */
    val WATCHTOWER_YANILLE = AttributeKey<Boolean>(persistenceKey = "diary_watchtower_yanille")

    fun watchtowerToYanille(player: Player): Boolean =
        player.attr[WATCHTOWER_YANILLE] == true && Diaries.hasClaimed(player, Diary.ARDOUGNE, Tier.HARD)

    /** Varrock medium: Varrock Teleport lands at the Grand Exchange when the player chose that at Toby. */
    val VARROCK_GE = AttributeKey<Boolean>(persistenceKey = "diary_varrock_ge")

    fun varrockToGe(player: Player): Boolean =
        player.attr[VARROCK_GE] == true && Diaries.hasClaimed(player, Diary.VARROCK, Tier.MEDIUM)

    /** Kandarin medium: "10% increased experience from cutting maple trees in Seers' Village". */
    fun mapleXpBonus(player: Player): Double =
        if (Diaries.hasClaimed(player, Diary.KANDARIN, Tier.MEDIUM) && DiaryTasks.SEERS.has(player.tile)) 1.1 else 1.0
}
