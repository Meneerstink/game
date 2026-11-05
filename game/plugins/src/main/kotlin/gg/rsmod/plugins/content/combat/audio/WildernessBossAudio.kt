package gg.rsmod.plugins.content.combat.audio

import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.areas.wilderness.bosses.*

/** V1: dispatch exists independently of cue selection. An unproven cue is explicitly empty, never guessed from a family. */
object WildernessBossAudio {
    data class Cue(val name: String, val source: String? = null, val sound: Int? = null, val radius: Int? = null, val delay: Int? = null) {
        init {
            require(sound == null || (!source.isNullOrBlank() && radius != null && delay != null))
            require(radius == null || radius in 0..15)
            require(delay == null || delay >= 0)
        }
    }
    private fun cues(vararg attacks: String): Map<String, Cue> =
        (attacks.flatMap { listOf("attack.$it", "impact.$it") } + listOf("defend", "death")).associateWith { Cue(it) }

    val contracts: Map<Int, Map<String, Cue>> = mapOf(
        OldWildernessBosses.CALLISTO to cues("melee", "shockwave", "roar", "heal"),
        OldWildernessBosses.VETION to cues("melee", "earthquake", "lightning"),
        OldWildernessBosses.VETION_REBORN to cues("melee", "earthquake", "lightning"),
        OldWildernessBosses.HELLHOUND to cues("melee"), OldWildernessBosses.GREATER_HELLHOUND to cues("melee"),
        RevenantMaledictus.NPC to cues("standard", "blood", "ice", "surge"),
        VenenatisCombat.SPIDERLING to cues("melee", "drain"), VenenatisCombat.SPINDEL_SPIDERLING to cues("melee", "drain"),
        EscapeCaveMonsters.ids[0] to cues("melee"), EscapeCaveMonsters.ids[1] to cues("ranged"),
        CalvarionCombat.HOUND to cues("melee"), CalvarionCombat.GREATER_HOUND to cues("melee"),
    )
    private val ACTION = AttributeKey<String>()
    private val MANUAL_ATTACK = AttributeKey<Boolean>()
    private val SEEN = AttributeKey<MutableSet<String>>()
    private val PLAYED = AttributeKey<MutableMap<String, Int>>()
    fun handles(id: Int): Boolean = id in contracts
    fun action(npc: Npc, name: String) { npc.attr[ACTION] = name }
    fun begin(npc: Npc, name: String, target: Pawn? = null) {
        action(npc, name)
        npc.attr[MANUAL_ATTACK] = true
        dispatch(npc, "attack.$name", target)
    }
    fun manualAttack(npc: Npc): Boolean = npc.attr[MANUAL_ATTACK] == true
    fun action(npc: Npc): String = npc.attr[ACTION] ?: if (npc.id == EscapeCaveMonsters.ids[1]) "ranged" else "melee"

    fun dispatch(npc: Npc, name: String, target: Pawn? = null) {
        val cue = contracts[npc.id]?.get(name) ?: return
        if (cue.sound == null || cue.source.isNullOrBlank()) {
            val seen = npc.attr[SEEN] ?: mutableSetOf<String>().also { npc.attr[SEEN] = it }
            if (seen.add(name)) AvTrace.log { "wildy cue npc=${npc.id} cue=$name source=UNPROVEN result=withheld" }
            return
        }
        val played = npc.attr[PLAYED] ?: mutableMapOf<String, Int>().also { npc.attr[PLAYED] = it }
        if (played[name] == npc.world.currentCycle) return
        played[name] = npc.world.currentCycle
        if (cue.radius!! > 0) npc.world.spawn(AreaSound(npc.tile, cue.sound, cue.radius, 1, cue.delay!!))
        else (target as? Player)?.playSound(cue.sound, delay = cue.delay!!)
    }
}
