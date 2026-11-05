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

    // Owner 2026-11-05 18:0x (V1): the old Callisto/Vet'ion models use the CURRENT OSRS Callisto/Vet'ion sounds per function. Names are
    // Jagex's own config names (OSRS Wiki 'List of sound IDs'); every local id is a byte-identical import (N522-FIX-GEDEELD CACHE-D;
    // 586 checked by Claude 2026-11-05). team/dossiers/N522-GELUIDSBRONNEN.txt.
    private const val LIST = "OSRS Wiki List of sound IDs"
    private fun cue(name: String, osrsName: String, osrsId: Int, local: Int, radius: Int = 15) =
        name to Cue(name, "$LIST: $osrsName ($osrsId) -> local $local", local, radius, 0)
    private fun filled(base: Map<String, Cue>, vararg set: Pair<String, Cue>): Map<String, Cue> = base + set.toMap()

    private val CALLISTO_CUES = filled(cues("melee", "shockwave", "roar", "heal"),
        cue("attack.melee", "wbr_callisto_attack_melee_scratch_01", 6900, 10562),
        cue("impact.melee", "wbr_callisto_attack_melee_first_hand_impact_01", 6849, 10563),
        cue("attack.roar", "wbr_callisto_attack_melee_roar_01", 6873, 10467),
        // Wiki Callisto: 'His shockwave attack uses the same projectile and sound as the King Black Dragon's icy breath'.
        cue("attack.shockwave", "kingicebreath", 586, 586),
        cue("death", "wbr_callisto_death_roar_01", 6885, 10575),
    )
    private val VETION_CUES = filled(cues("melee", "earthquake", "lightning"),
        cue("attack.melee", "wbr_vetion_attack_melee_woosh_sword_back_02", 6854, 10583),
        cue("impact.melee", "wbr_vetion_sword_silce_02", 6864, 10586),
        cue("attack.lightning", "wbr_vetion_attack_magic_first_arm_movement_01", 6862, 10453),
        cue("attack.earthquake", "wbr_vention_attack_magic_stomp_01", 6944, 10587),
        cue("defend", "wbr_vetion_defend_parry_shield_glow_01", 6855, 10589),
    )
    private val VETION_REBORN_CUES = filled(VETION_CUES,
        cue("death", "wbr_vetion_death_disintergrate_01", 6851, 10597),
    )
    private val HOUND_CUES = filled(cues("melee"),
        cue("attack.melee", "wbr_vention_hellhound_attack_bark_01", 6904, 10305, 10),
        cue("defend", "wbr_vention_hellhound_defend_snarl_01", 6928, 10306, 10),
        cue("death", "wbr_vention_hellhound_death_01", 6952, 10307, 10),
    )

    val contracts: Map<Int, Map<String, Cue>> = mapOf(
        OldWildernessBosses.CALLISTO to CALLISTO_CUES,
        OldWildernessBosses.VETION to VETION_CUES,
        OldWildernessBosses.VETION_REBORN to VETION_REBORN_CUES,
        OldWildernessBosses.HELLHOUND to HOUND_CUES, OldWildernessBosses.GREATER_HELLHOUND to HOUND_CUES,
        RevenantMaledictus.NPC to cues("standard", "blood", "ice", "surge"),
        VenenatisCombat.SPIDERLING to cues("melee", "drain"), VenenatisCombat.SPINDEL_SPIDERLING to cues("melee", "drain"),
        EscapeCaveMonsters.ids[0] to cues("melee"), EscapeCaveMonsters.ids[1] to cues("ranged"),
        CalvarionCombat.HOUND to HOUND_CUES, CalvarionCombat.GREATER_HOUND to HOUND_CUES,
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
