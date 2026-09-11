package gg.rsmod.plugins.content.activity.shooting_star

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Real-world 2011-era tile locations for the Shooting Star event, sourced from Void's
 * `content/activity/shooting_star/StarLocationData.kt`. These are plain overworld tile
 * coordinates (not cache-specific ids), so they carry over unchanged between donor caches.
 */
enum class StarLocation(val description: String, val tile: Tile) {
    AL_KHARID_BANK("the north west edge of the Al Kharid Bank.", Tile(3276, 3174, 0)),
    AL_KHARID_MINE("the north of the scorpion mine.", Tile(3299, 3308, 0)),
    MAGE_TRAINING_ARENA("the south of the entrance to the Mage Training Arena.", Tile(3348, 3284, 0)),
    CRAFTING_GUILD("the Crafting Guild by the gold rocks.", Tile(2940, 3281, 0)),
    RIMMINGTON_MINING_SITE("the centre of the big mine north of Rimmington.", Tile(2974, 3240, 0)),
    FALADOR_WEST_MINE("the mine east of the dark wizard tower.", Tile(2927, 3336, 0)),
    SOUTHERN_CRANDOR_MINE("the beach beneath the south-western mining area on Crandor.", Tile(2823, 3237, 0)),
    KELDAGRIM_ENTRANCE("the mine located south of cave entrance to Keldagrim.", Tile(2726, 3678, 0)),
    JATIZO_MINE("the right of the mine's entrance on north-west Jatizso.", Tile(2391, 3813, 0)),
    LUNAR_ISLE_MINE("the ladder to the Rune essence mines on Lunar Isle.", Tile(2146, 3942, 0)),
    MISCELLANIA_MINE_SITE("south of coal mine on Miscellania.", Tile(2531, 3887, 0)),
    CENTRAL_FREMENNIK_MINING("the central Fremennik Isles mining site.", Tile(2375, 3833, 0)),
    RELLEKKA_MINING_SITE("the mining site in the fenced off area inside the town of Rellekka.", Tile(2676, 3698, 0)),
    ARDOUGNE_MINING_SITE("the iron rocks north of the monastery, south of East Ardougne.", Tile(2701, 3333, 0)),
    COAL_TRUCK_MINING("the middle of the coal rocks that supply the Coal trucks.", Tile(2586, 3477, 0)),
    FIGHT_ARENA_MINING("the mining spot north-east of Yanille, south-west of Port Khazard.", Tile(2634, 3133, 0)),
}

/**
 * The 9 crashed-star tier objects, confirmed present and sequential in this project's own
 * generated `Objs.kt` (native to this 667 cache, not guessed).
 */
val ShootingStarTierObjects =
    listOf(
        Objs.CRASHED_STAR,
        Objs.CRASHED_STAR_38661,
        Objs.CRASHED_STAR_38662,
        Objs.CRASHED_STAR_38663,
        Objs.CRASHED_STAR_38664,
        Objs.CRASHED_STAR_38665,
        Objs.CRASHED_STAR_38666,
        Objs.CRASHED_STAR_38667,
        Objs.CRASHED_STAR_38668,
    )

val ShootingStarEventTimer = TimerKey(persistenceKey = "shooting_star_event", tickOffline = false)
val ShootingStarSpriteDespawnTimer = TimerKey(persistenceKey = "shooting_star_sprite_despawn", tickOffline = false)
val ShootingStarBonusOreTimer = TimerKey(persistenceKey = "shooting_star_bonus_ore", tickOffline = true, resetOnDeath = false)

/**
 * World-shared state for the single currently-active crashed star (Void's design only ever
 * runs one star event at a time; mirrored here). Not persisted across server restarts, matching
 * Void's own in-memory-only handling.
 */
object ShootingStarState {
    var tile: Tile? = null
    var tierIndex: Int = -1
    var obj: GameObject? = null
    var spriteRef: Npc? = null

    /**
     * PROVISIONAL, not sourced: the real per-tier "layer" depletion capacity is read by Void at
     * runtime from a cache-side `collect_for_next_layer` object parameter. Void's own Kotlin
     * source and its data tomls never hardcode that number (only `ores = ["stardust"]`, no capacity),
     * and this project's `ObjectDefProbeTool` does not parse arbitrary int params, so the true
     * figure could not be verified from any available source this batch. This flat capacity
     * (independent of tier, since no donor source ties a specific number to a specific tier
     * either) is a deliberate placeholder balance value, not a fact claim, pending a decoded
     * cache-param dump or owner sign-off.
     */
    const val PROVISIONAL_LAYER_CAPACITY = 120

    var collected: Int = 0

    val active: Boolean
        get() = tile != null

    fun reset() {
        tile = null
        tierIndex = -1
        obj = null
        collected = 0
    }

    fun percentageRemaining(): String {
        val remaining = (PROVISIONAL_LAYER_CAPACITY - collected).coerceAtLeast(0)
        val pct = (remaining.toDouble() / PROVISIONAL_LAYER_CAPACITY.toDouble()) * 100
        return String.format("%.2f", pct)
    }
}

/**
 * Void's real stardust-exchange reward formula (`ShootingStar.calculateRewards`), sourced
 * verbatim - ratios are per 200 stardust, scaled linearly to however much the player is
 * carrying.
 */
object ShootingStarRewards {
    fun calculate(stardust: Int): Map<Int, Int> {
        val coinsPerStardust = 50002.0 / 200
        val astralRunesPerStardust = 52.0 / 200
        val cosmicRunesPerStardust = 152.0 / 200
        val goldOresPerStardust = 20.0 / 200

        return linkedMapOf(
            gg.rsmod.plugins.api.cfg.Items.COINS to (coinsPerStardust * stardust).toInt(),
            gg.rsmod.plugins.api.cfg.Items.ASTRAL_RUNE to Math.round(astralRunesPerStardust * stardust).toInt(),
            gg.rsmod.plugins.api.cfg.Items.COSMIC_RUNE to Math.round(cosmicRunesPerStardust * stardust).toInt(),
            gg.rsmod.plugins.api.cfg.Items.GOLD_ORE_NOTED to Math.round(goldOresPerStardust * stardust).toInt(),
        )
    }
}
