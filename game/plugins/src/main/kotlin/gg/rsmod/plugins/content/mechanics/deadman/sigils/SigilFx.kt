package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound

/**
 * PROJECT48 SIGILS (owner 21-11: "geluiden ... de animatie die de speler moet doen als sommige sigils activeren"): what the player
 * sees and hears when a sigil is attuned, unattuned, activated or goes off. Everything is OSRS: the spot animations, sequences and
 * sounds Jagex made for the Deadman sigils (RuneLite gameval names below), imported by `OsrsFxImportTool sigil-fx` (local ids in
 * OSRS_IMPORT_MASTER.yml). The cache says WHAT exists and which sound sits in which animation; WHEN the server plays each one is not
 * in the cache - the moments below follow the names (attune / unattune / activate / the sigil's own icon).
 */
object SigilFx {
    /** OSRS spotanim 1970..1993 -> local [SPOT_BASE] + (id - 1970); 2560 -> [ETERNAL_JEWELLER]. */
    const val SPOT_BASE = 3478
    private const val OSRS_SPOT_FIRST = 1970

    private fun spot(osrs: Int) = SPOT_BASE + osrs - OSRS_SPOT_FIRST

    val ATTUNE_COMBAT = spot(1970) // SIGIL_ATTUNE_COMBAT
    val ATTUNE_UTILITY = spot(1971) // SIGIL_ATTUNE_UTILITY
    val ATTUNE_SKILLING = spot(1972) // SIGIL_ATTUNE_SKILLING
    val UNATTUNE = spot(1973) // SIGIL_UNATTUNE
    const val ETERNAL_JEWELLER = 3502 // SIGIL_OF_THE_ETERNAL_JEWELLER 2560

    /** The sigil's own icon over the player (spotanim SIGIL_OF_<name>, all on sequence FX_SPELLCAST_DMM 9151). */
    val ICON: Map<Sigil, Int> =
        mapOf(
            Sigil.THE_FORMIDABLE_FIGHTER to spot(1974),
            Sigil.BINDING to spot(1976),
            Sigil.THE_MENACING_MAGE to spot(1977),
            Sigil.THE_PORCUPINE to spot(1979),
            Sigil.THE_RUTHLESS_RANGER to spot(1981),
            Sigil.FINALITY to spot(1982),
            Sigil.RAMPAGE to spot(1983),
            Sigil.SPECIALISED_STRIKES to spot(1993),
            Sigil.THE_ETERNAL_JEWELLER to ETERNAL_JEWELLER,
        )

    /** SIGIL_OF_RAMPAGE_BUFF_01.. (1984..): the glow of a Rampage stack. */
    fun rampageBuff(stack: Int): Int = spot(1983 + stack.coerceIn(1, 5))

    /** HUMAN_SPELLCAST_DMM_NOHELD 10655: the player's activation gesture; its frames carry DMM_SIGIL_COMBAT_ACTIVATION 5074. */
    const val ANIM_ACTIVATE = 17784

    // Sounds (OSRS synth -> local): 5074 -> 11000, 5064..5073 -> 11001..11010, 5075..5094 -> 11011..11030.
    private fun synth(osrs: Int) = if (osrs == 5074) 11000 else if (osrs < 5074) 11001 + osrs - 5064 else 11011 + osrs - 5075

    val SOUND_ATTUNE = synth(5078) // DMM_SIGIL_ATTUNE
    val SOUND_UNATTUNE = synth(5066) // DMM_SIGIL_DEATTUNE
    val SOUND_ACTIVATE = synth(5069) // DMM_SIGIL_ACTIVATE
    val SOUND_RAMPAGE = listOf(5086, 5068, 5080, 5070, 5091).map { synth(it) } // DMM_RAMPAGE_POWER_UP_01..05
    val SOUND_ENDED = synth(5090) // DMM_SIGIL_NO_LONGER_ACTIVE
    val SOUND_READY = synth(5092) // DMM_SIGIL_COOLDOWN_FINISH

    /** One timer per activatable sigil for "cooldown finished" and one per sigil with a window for "no longer active". */
    val COOLDOWN_TIMER: Map<Sigil, gg.rsmod.game.model.timer.TimerKey> =
        Sigil.values().filter { it.cooldownSeconds > 0 }.associateWith { gg.rsmod.game.model.timer.TimerKey() }
    val WINDOW_TIMER: Map<Sigil, gg.rsmod.game.model.timer.TimerKey> =
        Sigil.values().filter { it.activatable && it.windowSeconds > 0 }.associateWith { gg.rsmod.game.model.timer.TimerKey() }

    private fun ticks(seconds: Int) = (seconds * 1000 + 599) / 600

    fun attuned(
        player: Player,
        sigil: Sigil,
    ) {
        player.graphic(
            when (sigil.kind) {
                Sigil.Kind.COMBAT -> ATTUNE_COMBAT
                Sigil.Kind.UTILITY -> ATTUNE_UTILITY
                Sigil.Kind.SKILLING -> ATTUNE_SKILLING
            },
        )
        player.playSound(SOUND_ATTUNE)
    }

    /** SIGIL_OF_THE_GUARDIAN_ANGEL 1992: the wings when the sigil saves an item on a PvP death and is consumed. */
    val GUARDIAN_ANGEL = spot(1992)

    fun guardianAngel(player: Player) = player.graphic(GUARDIAN_ANGEL)

    fun unattuned(player: Player) {
        player.graphic(UNATTUNE)
        player.playSound(SOUND_UNATTUNE)
    }

    /**
     * An activatable sigil was activated: a combat sigil = the gesture (with its own sound) and the sigil's icon; a utility sigil
     * (Last Recall, Versatility, Serpent) = the activation sound and its icon if it has one.
     */
    fun activated(
        player: Player,
        sigil: Sigil,
    ) {
        if (sigil.kind == Sigil.Kind.COMBAT) player.animate(ANIM_ACTIVATE) else player.playSound(SOUND_ACTIVATE)
        ICON[sigil]?.let { player.graphic(it) }
        COOLDOWN_TIMER[sigil]?.let { player.timers[it] = ticks(sigil.cooldownSeconds) }
        WINDOW_TIMER[sigil]?.let { player.timers[it] = ticks(sigil.windowSeconds) }
    }

    /** The window of [sigil] closed - by time (its timer) or because the one-shot effect was used (Sigils.consumeWindow). */
    fun windowEnded(
        player: Player,
        sigil: Sigil,
    ) {
        WINDOW_TIMER[sigil]?.let { player.timers.remove(it) }
        player.playSound(SOUND_ENDED)
    }

    fun cooldownFinished(player: Player) = player.playSound(SOUND_READY)

    /** A passive sigil went off (Formidable Fighter's +10 is armed, a Menacing Mage / Ruthless Ranger effect starts, ...): its icon. */
    fun proc(
        player: Player,
        sigil: Sigil,
    ) {
        ICON[sigil]?.let { player.graphic(it) }
    }

    /** Rampage reached [stack] (1..): the buff glow and its power-up sound. */
    fun rampage(
        player: Player,
        stack: Int,
    ) {
        if (stack < 1) return
        player.graphic(rampageBuff(stack))
        player.playSound(SOUND_RAMPAGE[(stack - 1).coerceIn(0, SOUND_RAMPAGE.size - 1)])
    }
}
