package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*

/**
 * Server playback contract. Actual LOCAL animation/graphic ids and measured durations belong to Claude's AV import.
 *
 * DMM2 punt 2 (owner 18-11 15:5x chose 'lijst in de chatbox'): OSRS Wiki "Animation overrides" - home teleport animations "can be
 * toggled by right-clicking 'Animation' from the home teleport in the spellbook. If multiple animations are toggled on then the
 * animation is chosen randomly from the toggled options. Including the default animation, at least one of the animations must be
 * toggled on." The 'Animation' op is in the cache (InterfaceOpAddTool tx-20261118-150431 on 192:24, 193:48, 430:39, the OSRS 218
 * Home Teleport has the same op); the server enables it (IF_SETEVENTS op1+op2) only for a player with an unlocked style. The
 * toggle list itself has no OSRS source (server-side in OSRS) -> chatbox list, owner's choice.
 */
object DeadmanHomeTeleport {
    data class Stage(val animation: Int, val graphic: Int, val ticks: Int) {
        init { require(animation >= 0 && graphic >= 0 && ticks > 0) }
    }
    private val styles = mutableMapOf<NigelRewardStore.Teleport, List<Stage>>()

    /** IF_BUTTON2: the second op of a component ('Animation'), as getInteractingOpcode() reports it (notes.plugin.kts). */
    const val OP_ANIMATION = 64
    private const val OP1_ONLY = 0x2
    private const val OP1_AND_OP2 = 0x6
    private val HOME_TELEPORT_COMPONENT = mapOf(192 to 24, 193 to 48, 430 to 39)

    fun install(teleport: NigelRewardStore.Teleport, stages: List<Stage>) {
        require(teleport != NigelRewardStore.Teleport.DEFAULT && stages.isNotEmpty())
        styles[teleport] = stages.toList()
    }

    fun available(teleport: NigelRewardStore.Teleport): Boolean =
        teleport == NigelRewardStore.Teleport.DEFAULT || styles.containsKey(teleport)

    /** The styles this player can toggle: unlocked AND playable on this server (all ids imported). */
    fun choices(player: Player): List<NigelRewardStore.Teleport> =
        NigelRewardStore.active?.teleportStyles(player)?.filter { available(it) } ?: listOf(NigelRewardStore.Teleport.DEFAULT)

    /** Random pick among the toggled-on styles; null = the default cast. */
    fun stages(player: Player): List<Stage>? {
        val store = NigelRewardStore.active ?: return null
        val pick = store.teleportsOn(player).filter { available(it) }.randomOrNull() ?: return null
        return styles[pick]?.toList()
    }

    /** Sends the Home Teleport events of the open book: 'Animation' clickable only when there is something to toggle. */
    fun refreshOp(player: Player) {
        val book = player.spellbookInterfaceId()
        val component = HOME_TELEPORT_COMPONENT[book] ?: return
        player.setInterfaceEvents(book, component, -1..-1, if (choices(player).size > 1) OP1_AND_OP2 else OP1_ONLY)
    }

    private fun label(teleport: NigelRewardStore.Teleport) = when (teleport) {
        NigelRewardStore.Teleport.DEFAULT -> "Default"
        NigelRewardStore.Teleport.ARMAGEDDON -> "Deadman: Armageddon"
        NigelRewardStore.Teleport.ANNIHILATION -> "Deadman: Annihilation"
    }

    /** The chatbox toggle list; stays open after every toggle until 'Close.' (or the player walks away / closes it). */
    suspend fun QueueTask.toggleMenu() {
        val store = NigelRewardStore.active ?: return
        repeat(10) {
            val styles = choices(player)
            val on = store.teleportsOn(player)
            val lines = styles.map { "${label(it)}: " + if (it in on) "<col=00c800>On</col>" else "<col=ff0000>Off</col>" } + "Close."
            val teleport = styles.getOrNull(options(*lines.toTypedArray(), title = "Home Teleport animations") - 1) ?: return
            if (store.toggleTeleport(player, teleport) == NigelRewardStore.Result.LAST_TELEPORT_ON)
                player.message("At least one of the animations must be toggled on.")
        }
    }
}
