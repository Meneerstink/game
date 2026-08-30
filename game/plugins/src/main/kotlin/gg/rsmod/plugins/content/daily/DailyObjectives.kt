package gg.rsmod.plugins.content.daily

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.wilderness.WildernessHotspot
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/**
 * R14.28/29: three real, kill-tracked daily objectives, reset alongside the existing loyalty
 * daily timer ([gg.rsmod.game.model.timer.DAILY_TIMER], see `daily.plugin.kts`). Progress is
 * driven by [gg.rsmod.game.plugin.PluginRepository.executeNpcKilled] (`on_npc_killed`), a
 * generic "any npc killed by a player" hook added this pass specifically so this feature (and
 * any future one) can count kills without touching or overwriting any npc-specific
 * `on_npc_death` handler bound elsewhere.
 *
 * Kept deliberately simple and honest: three fixed objectives (not a randomised daily pool,
 * which would need a verified random-selection UI this pass doesn't have time to build), each
 * with a real target count and a real loyalty point reward, persisted so progress survives
 * logout/relog within the same day.
 */
object DailyObjectives {
    data class Objective(
        val id: Int,
        val label: String,
        val target: Int,
        val reward: Int,
        val counts: (Npc) -> Boolean,
    )

    private val PROGRESS_ATTRS =
        listOf(
            AttributeKey<Int>(persistenceKey = "daily_obj_1_progress"),
            AttributeKey<Int>(persistenceKey = "daily_obj_2_progress"),
            AttributeKey<Int>(persistenceKey = "daily_obj_3_progress"),
        )
    private val COMPLETED_ATTRS =
        listOf(
            AttributeKey<Boolean>(persistenceKey = "daily_obj_1_done"),
            AttributeKey<Boolean>(persistenceKey = "daily_obj_2_done"),
            AttributeKey<Boolean>(persistenceKey = "daily_obj_3_done"),
        )

    val OBJECTIVES =
        listOf(
            Objective(0, "Defeat 15 monsters", target = 15, reward = 150, counts = { true }),
            Objective(
                1,
                "Defeat 5 monsters in the Wilderness",
                target = 5,
                reward = 300,
                counts = { npc -> npc.tile.getWildernessLevel() > 0 },
            ),
            Objective(
                2,
                "Defeat 3 monsters at today's Wilderness hotspot (${WildernessHotspot.current.label})",
                target = 3,
                reward = 450,
                counts = { npc -> WildernessHotspot.isActive(npc.tile) },
            ),
        )

    /** Called from `on_npc_killed` for every kill, regardless of npc id. */
    fun onKill(
        killer: Player,
        npc: Npc,
    ) {
        // R08.11-consistent: only real Wilderness danger counts toward the wilderness-flavoured
        // objectives - a kill inside the safe home hub never counts as a "Wilderness" kill even
        // if the tile's raw level is nonzero, matching AreaState's own safe/dangerous split.
        val dangerous = !AreaState.isSafe(npc.tile, killer.world.gameContext.home)
        OBJECTIVES.forEachIndexed { index, objective ->
            if (killer.attr[COMPLETED_ATTRS[index]] == true) return@forEachIndexed
            if (objective.id != 0 && !dangerous) return@forEachIndexed
            if (!objective.counts(npc)) return@forEachIndexed

            val progress = (killer.attr[PROGRESS_ATTRS[index]] ?: 0) + 1
            killer.attr[PROGRESS_ATTRS[index]] = progress
            if (progress >= objective.target) {
                killer.attr[COMPLETED_ATTRS[index]] = true
                killer.addLoyalty(objective.reward)
                killer.filterableMessage(
                    "Daily objective complete: \"${objective.label}\". You receive ${objective.reward} loyalty points.",
                )
            }
        }
    }

    fun progressLine(
        player: Player,
        objective: Objective,
    ): String {
        val index = objective.id
        val done = player.attr[COMPLETED_ATTRS[index]] == true
        val progress = (player.attr[PROGRESS_ATTRS[index]] ?: 0).coerceAtMost(objective.target)
        return if (done) {
            "- [DONE] ${objective.label} (+${objective.reward} loyalty)"
        } else {
            "- ${objective.label}: $progress/${objective.target}"
        }
    }

    /** Called from the DAILY_TIMER reset in `daily.plugin.kts`. */
    fun reset(player: Player) {
        PROGRESS_ATTRS.forEach { player.attr[it] = 0 }
        COMPLETED_ATTRS.forEach { player.attr[it] = false }
    }
}
