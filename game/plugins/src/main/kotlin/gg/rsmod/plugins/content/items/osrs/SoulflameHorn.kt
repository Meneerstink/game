package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.mechanics.aid.AcceptAid
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids

/**
 * PROJECT48 DMM-ITEMS C1 Soulflame horn (OSRS Wiki "Soulflame horn", fetched 2026-11-21, re-read 23-11): 80 Magic to wield, +1 % magic damage.
 * Special attack Entice: it affects up to 3 other players within 3 tiles for 25 % special attack energy per other player
 * (usable only with at least one other player in range) and always also the user; "the player's next melee attack within 10 ticks
 * (6 seconds) is always a successful hit" - only the first accuracy roll of a multi-hit special. "If attacking another player, the
 * effect is replaced by a 50% accuracy increase for their next melee attack" (wiki change 22 July 2026): attack roll x [PVP_ACCURACY]
 * in MeleeCombatFormula.
 * Nobody in range: red "You blow your horn into the wind. No-one nearby is able to listen.", no flame, no energy spent.
 * "The special attack has a cooldown of 4 ticks". Other players need Accept Aid on (wiki: outside raids/Nex/Nightmare/Royal Titans/
 * Yama; ADAPTED: the exempt areas are not special-cased - Accept Aid defaults to on here).
 * Configure (OSRS Wiki, read 21-11): radius 0-3 tiles and the number of other players affected 1-20; the cost stays 25 % per other player,
 * up to 3 others. SOURCE_GAP (not built): the special-weapon accuracy variants (dragon claws, burning claws).
 *
 * Animation + effects (cache2762, ids from RuneLite gameval, imported by OsrsFxImportTool p48-soulflame, cfg/dmm2/ids.tsv):
 * SOULFLAME_HORN_BLOW_03 12158 (its frame sounds = YAMA_SOULFLAME_HORN_WHOOSH_01 + _FIRE_01) with VFX_SOULFLAME_HORN_SPOTANIM01 3282 on
 * the user; _BLOW_03_NO_FIRE 12159 (whoosh only) when nobody listens; VFX_SOULFLAME_HORN_IMPACT_SPOTANIM01 3283 +
 * YAMA_SOULFLAME_HORN_BUFF_01 10236 on every other enticed player. The chat line the enticed players get is not known: none is sent.
 */
object SoulflameHorn {
    const val ID = 25493
    const val RADIUS = 3
    const val MAX_ALLIES = 3
    const val COST_PER_ALLY = 25
    const val DURATION_TICKS = 10
    const val COOLDOWN_TICKS = 4
    const val PVP_ACCURACY = 1.5

    const val NO_ONE_LISTENS = "<col=ef1020>You blow your horn into the wind. No-one nearby is able to listen.</col>"

    val RADIUS_ATTR = AttributeKey<Int>(persistenceKey = "horn_radius")
    val MAX_OTHERS_ATTR = AttributeKey<Int>(persistenceKey = "horn_max_others")

    val ENTICE = TimerKey(persistenceKey = "dmm_entice", tickOffline = false, resetOnDeath = true)
    val COOLDOWN = TimerKey()

    /** The instant special: false (nothing spent here) when nobody else is near or the energy does not cover one ally. */
    fun use(player: Player): Boolean {
        if (player.timers.has(COOLDOWN)) return false
        player.timers[COOLDOWN] = COOLDOWN_TICKS
        val near =
            player.world.players.entries.filterNotNull()
                .filter { it !== player && it.isOnline && it.tile.height == player.tile.height && it.tile.getDistance(player.tile) <= (player.attr[RADIUS_ATTR] ?: RADIUS) }
                .filter(AcceptAid::accepts)
                .sortedBy { it.tile.getDistance(player.tile) }
        if (near.isEmpty()) {
            Dmm2Ids.seq(12159)?.let { player.animate(it) }
            player.message(NO_ONE_LISTENS)
            return false
        }
        val affordable = AttackTab.getEnergy(player) / COST_PER_ALLY
        val maxOthers = player.attr[MAX_OTHERS_ATTR] ?: MAX_ALLIES
        val allies = near.take(minOf(maxOthers, if (affordable >= MAX_ALLIES) 20 else affordable))
        if (allies.isEmpty()) {
            player.message("You don't have enough power left.")
            return false
        }
        AttackTab.spendSpecial(player, COST_PER_ALLY * minOf(allies.size, MAX_ALLIES))
        Dmm2Ids.seq(12158)?.let { player.animate(it) }
        Dmm2Ids.spot(3282)?.let { player.graphic(it) }
        player.timers[ENTICE] = DURATION_TICKS
        allies.forEach {
            it.timers[ENTICE] = DURATION_TICKS
            Dmm2Ids.spot(3283)?.let { spot -> it.graphic(spot) }
            Dmm2Ids.sound(10236)?.let { sound -> it.playSound(sound) }
        }
        return false // the energy is spent above (25 % per ally), not the registered flat cost
    }

    /** The first melee accuracy roll while enticed is used up: true = Entice applies to this roll. */
    fun consume(player: Player): Boolean {
        if (!player.timers.has(ENTICE)) return false
        player.timers.remove(ENTICE)
        return true
    }


    /** The Configure option: pick the radius or the number of other players, then type the value. */
    suspend fun configure(task: QueueTask, player: Player) {
        when (task.options("Radius (0-3 tiles).", "Max players (1-20).", title = "Soulflame horn")) {
            1 -> {
                val r = task.inputInt("Radius in tiles (0-3):").coerceIn(0, 3)
                player.attr[RADIUS_ATTR] = r
                player.message("Entice radius set to $r.")
            }
            else -> {
                val n = task.inputInt("Other players affected (1-20):").coerceIn(1, 20)
                player.attr[MAX_OTHERS_ATTR] = n
                player.message("Entice will affect up to $n other players.")
            }
        }
    }
}
