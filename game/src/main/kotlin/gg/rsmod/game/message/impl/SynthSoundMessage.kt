package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * @author Tom <rspsmods@gmail.com>
 */
data class SynthSoundMessage(
    val sound: Int,
    /** Number of times the client repeats the synth sound; this is the packet's second byte. */
    val loops: Int,
    val delay: Int,
    /** Playback volume in the client sound mixer. */
    val volume: Int = 255,
    /** Playback rate/speed used by the revision-667 client. */
    val rate: Int = 256,
) : Message
