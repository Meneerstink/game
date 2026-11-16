package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Player

/** Server playback contract. Actual LOCAL animation/graphic ids and measured durations belong to Claude's AV import. */
object DeadmanHomeTeleport {
    data class Stage(val animation: Int, val graphic: Int, val ticks: Int) {
        init { require(animation >= 0 && graphic >= 0 && ticks > 0) }
    }
    private val styles = mutableMapOf<NigelRewardStore.Teleport, List<Stage>>()

    fun install(teleport: NigelRewardStore.Teleport, stages: List<Stage>) {
        require(teleport != NigelRewardStore.Teleport.DEFAULT && stages.isNotEmpty())
        styles[teleport] = stages.toList()
    }

    fun available(teleport: NigelRewardStore.Teleport): Boolean =
        teleport == NigelRewardStore.Teleport.DEFAULT || styles.containsKey(teleport)

    fun stages(player: Player): List<Stage>? = NigelRewardStore.active?.selectedTeleport(player)?.let { styles[it]?.toList() }
}
