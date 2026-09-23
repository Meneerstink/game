package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.game.model.entity.Player

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

    fun play(player: Player, sound: Int, delay: Int = 0, rate: Int = 256, loops: Int = 1) {
        player.playSound(sound, volume = SERVER_SOUND_VOLUME, delay = delay, rate = rate, loops = loops)
    }

    fun isFamiliarNpc(npcId: Int): Boolean = SummoningPouchData.values().any { it.npc == npcId }

    fun playSpawn(player: Player, data: SummoningPouchData) {
        spawnSound(data)?.let { play(player, it) }
    }
}
