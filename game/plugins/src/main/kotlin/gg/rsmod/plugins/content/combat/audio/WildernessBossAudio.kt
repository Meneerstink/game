package gg.rsmod.plugins.content.combat.audio

import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.areas.wilderness.bosses.*

/** Owner V1 (2026-11-05 18:0x): named current-OSRS cues on the old bosses; all other assignments remain empty. */
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

    // N522-GELUIDSBRONNEN: owner-approved function mapping, OSRS_IMPORT_MASTER synth mapping and CACHE-D byte proof.
    // Radius 15 matches these Artio/Calvar'ion frame cues; delay 0 is the approved server-event adaptation, not a frame-timing claim.
    private fun sourced(cue: String, osrsName: String, osrs: Int, local: Int) =
        Cue(cue, "N522-GELUIDSBRONNEN / owner V1 2026-11-05 18:0x: $osrsName ($osrs)", local, 15, 0)
    private fun Map<String, Cue>.with(vararg bindings: Cue): Map<String, Cue> = this + bindings.associateBy { it.name }

    private val vetion = cues("melee", "earthquake", "lightning").with(
        sourced("attack.melee", "wbr_vetion_attack_melee_woosh_sword_back_02", 6854, 10583),
        sourced("impact.melee", "wbr_vetion_sword_silce_02", 6864, 10586),
        sourced("attack.lightning", "wbr_vetion_attack_magic_first_arm_movement_01", 6862, 10453),
        sourced("attack.earthquake", "wbr_vention_attack_magic_stomp_01", 6944, 10587),
        sourced("defend", "wbr_vetion_defend_parry_shield_glow_01", 6855, 10589),
    )
    private val hounds = cues("melee").with(
        sourced("attack.melee", "wbr_vention_hellhound_attack_bark_01", 6904, 10305),
        sourced("defend", "wbr_vention_hellhound_defend_snarl_01", 6928, 10306),
        sourced("death", "wbr_vention_hellhound_death_01", 6952, 10307),
        // N581 dagrun 2026-11-06: OsrsFxImportTool batch n522-houndcall, tx-20261106-115630.
        sourced("spawn", "wbr_vention_hellhound_call_01", 6910, 10598),
    )

    val contracts: Map<Int, Map<String, Cue>> = mapOf(
        OldWildernessBosses.CALLISTO to cues("melee", "shockwave", "roar", "heal").with(
            sourced("attack.melee", "wbr_callisto_attack_melee_scratch_01", 6900, 10562),
            sourced("impact.melee", "wbr_callisto_attack_melee_first_hand_impact_01", 6849, 10563),
            sourced("attack.roar", "wbr_callisto_attack_melee_roar_01", 6873, 10467),
            sourced("attack.shockwave", "kingicebreath / Callisto Wiki icy-breath equivalence; Claude byte-check 18:4x", 586, 586),
            sourced("death", "wbr_callisto_death_roar_01", 6885, 10575),
        ),
        OldWildernessBosses.VETION to vetion,
        OldWildernessBosses.VETION_REBORN to vetion.with(
            sourced("death", "wbr_vetion_death_disintergrate_01", 6851, 10597),
            sourced("spawn", "wbr_vetion_spawn_01", 6853, 10518),
        ),
        OldWildernessBosses.HELLHOUND to hounds, OldWildernessBosses.GREATER_HELLHOUND to hounds,
        RevenantMaledictus.NPC to cues("standard", "blood", "ice", "surge"),
        VenenatisCombat.SPIDERLING to cues("melee", "drain"), VenenatisCombat.SPINDEL_SPIDERLING to cues("melee", "drain"),
        EscapeCaveMonsters.ids[0] to cues("melee"), EscapeCaveMonsters.ids[1] to cues("ranged"),
        // Claude merge review: Calvar'ion's hounds ARE the 2023-rework hellhounds (OSRS 12107/12108) whose Jagex names are
        // wbr_vention_hellhound_*, so they carry the same sourced cues as the old Vet'ion hounds.
        CalvarionCombat.HOUND to hounds, CalvarionCombat.GREATER_HOUND to hounds,
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
        AvTrace.log { "wildy cue npc=${npc.id} cue=$name source=${cue.source} sound=${cue.sound} radius=${cue.radius} delay=${cue.delay} result=dispatch" }
        if (cue.radius!! > 0) npc.world.spawn(AreaSound(npc.tile, cue.sound, cue.radius, 1, cue.delay!!))
        else (target as? Player)?.playSound(cue.sound, delay = cue.delay!!)
    }
}
