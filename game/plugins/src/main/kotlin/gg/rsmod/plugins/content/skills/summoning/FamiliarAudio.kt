package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.PLAYER_COMBAT_SOUND_RADIUS

/**
 * Server-addressable familiar presentation sounds from the QC3 table.
 *
 * Hit and death are kept in the shared NPC combat table because those events already have one
 * global dispatch point. This table is only for summon/spawn presentation: walk, ambient and
 * voice cues are NPC-definition or sequence audio and are emitted by the revision-667 client.
 * Keeping those client-owned cues out of this map prevents a second copy on every summon tick.
 */
object FamiliarAudio {
    /** Shared owner-approved mix level for familiar one-shot presentation/combat audio. */
    // Owner retest: the shared familiar cues were almost right but still about 5% too loud.
    const val SERVER_SOUND_VOLUME = 116

    private val SPAWN_SOUNDS =
        mapOf(
            SummoningPouchData.SPIRIT_TZ_KIH to 4677,
            SummoningPouchData.KARAMTHULHU_OVERLORD to 4254,
            SummoningPouchData.VOID_TORCHER to 4694,
            SummoningPouchData.PYRELORD to 4620,
            SummoningPouchData.OBSIDIAN_GOLEM to 4682,
            SummoningPouchData.FIRE_TITAN to 4699,
            SummoningPouchData.ICE_TITAN to 4706,
            SummoningPouchData.MOSS_TITAN to 4626,
            SummoningPouchData.LAVA_TITAN to 4647,
            SummoningPouchData.SWAMP_TITAN to 4683,
            SummoningPouchData.GEYSER_TITAN to 4659,
            SummoningPouchData.ABYSSAL_TITAN to 4656,
            SummoningPouchData.IRON_TITAN to 4646,
            SummoningPouchData.STEEL_TITAN to 4638,
        )

    fun spawnSound(data: SummoningPouchData): Int? = SPAWN_SOUNDS[data]

    /**
     * The owner hears the cue as before; the players around the familiar hear it too (conform summoning: "only the owner got
     * the sound" for summon, call, attacks and special moves of every familiar - RS: nearby players hear a familiar).
     * Same area-sound route and radius as a player's own combat sounds ([PLAYER_COMBAT_SOUND_RADIUS], ADAPTED), owner excluded
     * so he never gets it twice.
     */
    fun play(player: Player, sound: Int, delay: Int = 0, rate: Int = 256, loops: Int = 1, volume: Int = SERVER_SOUND_VOLUME, at: gg.rsmod.game.model.Tile? = null) {
        player.playSound(sound, volume = volume, delay = delay, rate = rate, loops = loops)
        if (sound <= 0) return
        val source = at ?: Familiar.current(player)?.tile ?: Pets.current(player)?.tile ?: player.tile
        player.world.spawn(
            AreaSound(tile = source, id = sound, radius = PLAYER_COMBAT_SOUND_RADIUS, volume = loops, delay = delay, playbackVolume = volume)
                .also { it.excluded = player },
        )
    }

    fun isFamiliarNpc(npcId: Int): Boolean = SummoningPouchData.values().any { it.npc == npcId }

    /**
     * The familiar's death sound: its own (or its creature's, NpcCombatAudio.resolveFamiliars) death row; nothing when the death
     * animation already carries an audible frame sound (the client plays it); else ADAPTED [Familiar.ARRIVAL_SOUND] 188 - the
     * 667 summoning cue of the same graphic family, "Your familiar slowly begins to fade away" (no source names a death sound).
     */
    fun deathSound(npc: gg.rsmod.game.model.entity.Npc): Int? {
        val row = gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.rowFor(npc.id)?.death ?: -1
        if (npc.combatDef.deathAnimation.any { gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.hasAudibleFrameSounds(npc, it) }) return null
        return if (row >= 0) row else Familiar.ARRIVAL_SOUND
    }

    /**
     * The dismiss sound: nothing when the despawn animation carries an audible frame sound; else ADAPTED
     * [Familiar.ARRIVAL_SOUND] 188 - the dismiss shows the same 1314/1315 puff as a summon, and neither Novite, Void nor
     * 2009Scape send a dismiss sound (conform summoning: dismiss silent for 71 familiars).
     */
    fun dismissSound(npc: gg.rsmod.game.model.entity.Npc): Int? {
        val data = SummoningPouchData.values.firstOrNull { it.npc == npc.id }
        val anim = data?.let { SummoningSpawnDespawnAnimations.despawnAnim(it) } ?: -1
        if (anim >= 0 && gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.hasAudibleFrameSounds(npc, anim)) return null
        return Familiar.ARRIVAL_SOUND
    }

    fun playSpawn(player: Player, data: SummoningPouchData) {
        spawnSound(data)?.let { play(player, it) }
    }
}
