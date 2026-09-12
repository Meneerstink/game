package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.playSound

/**
 * Source-backed audio events for the four God Wars bodyguard groups and the explicit spiritual
 * warrior combat definitions.
 *
 * The Void 667 combat files distinguish target sounds from area sounds. The shared NPC combat
 * definition has no audio fields, so these events are kept in a narrow overlay: it does not
 * replace the generic combat formulas, attack animations, projectiles or reset lifecycle.
 *
 * Unresolved source keys intentionally remain absent. In particular, the donor has no concrete
 * attack sound for Wingman Skree, Starlight or Tstanon Karlak, and no death sound entry for
 * Steelwill.
 */
object GodWarsMinionAudio {
    data class SourcedSound(
        val id: Int,
        val area: Boolean,
    )

    /** Explicit target/area attack sounds from the Void 667 bodyguard combat definitions. */
    private val attackSounds = mapOf(
        Npcs.SERGEANT_STRONGSTACK to SourcedSound(469, area = false), // goblin_attack
        Npcs.SERGEANT_STEELWILL to SourcedSound(3870, area = true), // sergeant_steelwill_attack
        Npcs.SERGEANT_GRIMSPIKE to SourcedSound(3851, area = true), // sergeant_grimspike_attack
        Npcs.FLOCKLEADER_GEERIN to SourcedSound(2699, area = false), // aviansie_attack
        Npcs.FLIGHT_KILISA to SourcedSound(2699, area = false), // aviansie_attack
        Npcs.GROWLER to SourcedSound(3877, area = true), // growler_sonic_spell_cast
        Npcs.BREE to SourcedSound(2693, area = true), // bree_arrow_shoot
        Npcs.BALFRUG_KREEYATH to SourcedSound(3884, area = false), // balfrug_kreeyath_attack
        Npcs.SPIRITUAL_WARRIOR to SourcedSound(2508, area = false), // spiritual_warrior_zamorak_attack
        Npcs.SPIRITUAL_WARRIOR_6255 to SourcedSound(2548, area = false), // spiritual_warrior_saradomin_attack
        Npcs.SPIRITUAL_WARRIOR_6277 to SourcedSound(2867, area = false), // gorak_attack
    )

    /** Explicit NPC defend sounds from the Void 667 combat/sound definitions. */
    private val defendSounds = mapOf(
        Npcs.SERGEANT_STRONGSTACK to SourcedSound(472, area = false), // goblin_defend
        Npcs.SERGEANT_STEELWILL to SourcedSound(3874, area = true), // sergeant_steelwill_defend
        Npcs.SERGEANT_GRIMSPIKE to SourcedSound(472, area = false), // goblin_defend
        Npcs.WINGMAN_SKREE to SourcedSound(3859, area = true), // aviansie_defend
        Npcs.FLOCKLEADER_GEERIN to SourcedSound(3859, area = true), // aviansie_defend
        Npcs.FLIGHT_KILISA to SourcedSound(3859, area = true), // aviansie_defend
        Npcs.SPIRITUAL_WARRIOR_6229 to SourcedSound(3859, area = true), // aviansie_defend
        Npcs.STARLIGHT to SourcedSound(3859, area = true), // aviansie_defend
        Npcs.GROWLER to SourcedSound(3869, area = false), // growler_defend
        Npcs.BREE to SourcedSound(3866, area = false), // bree_defend
        Npcs.BALFRUG_KREEYATH to SourcedSound(404, area = false), // balfrug_kreeyath_defend
        Npcs.ZAKLN_GRITCH to SourcedSound(404, area = false), // zakln_gritch_defend
        Npcs.SPIRITUAL_WARRIOR to SourcedSound(3841, area = false), // spiritual_warrior_zamorak_defend
        Npcs.SPIRITUAL_WARRIOR_6255 to SourcedSound(29, area = false), // spiritual_warrior_saradomin_defend
        Npcs.SPIRITUAL_WARRIOR_6277 to SourcedSound(2869, area = false), // gorak_defend
    )

    /** Explicit bodyguard death sounds from the Void 667 sound definitions. */
    private val deathSounds = mapOf(
        Npcs.SERGEANT_STRONGSTACK to SourcedSound(471, area = true), // goblin_death
        Npcs.SERGEANT_GRIMSPIKE to SourcedSound(471, area = true), // goblin_death
        Npcs.WINGMAN_SKREE to SourcedSound(3854, area = true), // aviansie_death
        Npcs.FLOCKLEADER_GEERIN to SourcedSound(3854, area = true), // aviansie_death
        Npcs.FLIGHT_KILISA to SourcedSound(3854, area = true), // aviansie_death
        Npcs.GROWLER to SourcedSound(3867, area = true), // growler_death
        Npcs.BREE to SourcedSound(3827, area = true), // bree_death
        Npcs.BALFRUG_KREEYATH to SourcedSound(403, area = true), // balfrug_kreeyath_death
        Npcs.ZAKLN_GRITCH to SourcedSound(403, area = true), // zakln_gritch_death
        Npcs.SPIRITUAL_WARRIOR to SourcedSound(3880, area = true), // spiritual_warrior_zamorak_death
        Npcs.SPIRITUAL_WARRIOR_6277 to SourcedSound(2868, area = true), // gorak_death
    )

    fun attackSoundFor(npcId: Int): SourcedSound? = attackSounds[npcId]

    fun defendSoundFor(npcId: Int): SourcedSound? = defendSounds[npcId]

    fun deathSoundFor(npcId: Int): SourcedSound? = deathSounds[npcId]

    fun deathSounds(): Map<Int, SourcedSound> = deathSounds

    fun defendSounds(): Map<Int, SourcedSound> = defendSounds

    fun playAttack(
        npc: Npc,
        target: Pawn,
    ) {
        play(npc, target, attackSoundFor(npc.id))
    }

    fun playDeath(npc: Npc) {
        play(npc, null, deathSoundFor(npc.id))
    }

    /**
     * Void's NPC defend hook emits the sound through the attacking character. For a player
     * attacker this is a direct player sound unless the source definition is explicitly an area
     * sound, in which case the area is centered on that player's tile. Non-player attackers are
     * intentionally ignored because Void's Character.sound also returns for non-players.
     */
    fun playDefend(
        source: Pawn,
        npc: Npc,
    ) {
        val sound = defendSoundFor(npc.id) ?: return
        if (source !is Player) return
        if (sound.area) {
            source.world.spawn(AreaSound(tile = source.tile, id = sound.id, radius = 5, volume = 1))
        } else {
            source.playSound(sound.id)
        }
    }

    private fun play(
        npc: Npc,
        target: Pawn?,
        sound: SourcedSound?,
    ) {
        if (sound == null) return
        if (sound.area) {
            npc.world.spawn(AreaSound(tile = npc.tile, id = sound.id, radius = 10, volume = 1))
        } else if (target is Player) {
            target.playSound(sound.id)
        }
    }
}
