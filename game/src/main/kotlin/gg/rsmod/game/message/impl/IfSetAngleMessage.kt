package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/**
 * Sets the camera of an interface model component (667 IF_SETANGLE, client ServerProt 122): [zoom] is the component's
 * zoom2d (bigger = smaller model), [xan]/[yan] its pitch/yaw. Port of 2009scape `PacketDispatch.sendAngleOnInterface` /
 * `InterfaceSetAngle` (its DialogueInterpreter sends it with every npc dialogue head).
 */
data class IfSetAngleMessage(
    val hash: Int,
    val zoom: Int,
    val xan: Int,
    val yan: Int,
) : Message
