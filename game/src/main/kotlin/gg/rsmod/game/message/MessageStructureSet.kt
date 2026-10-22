package gg.rsmod.game.message

import gg.rsmod.game.message.impl.IgnoreMessage
import gg.rsmod.net.packet.*
import gg.rsmod.util.ServerProperties
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.io.File
import kotlin.collections.set

/**
 * Stores all the [MessageStructure]s that are used on the
 * [gg.rsmod.game.service.GameService].
 *
 * @author Tom <rspsmods@gmail.com>
 */
class MessageStructureSet {
    /**
     * The [MessageStructure]s stored respectively to their [Class].
     */
    private val structureClasses = Object2ObjectOpenHashMap<Class<*>, MessageStructure>()

    /**
     * The [MessageStructure]s stored respectively to their opcode.
     */
    private val structureOpcodes = arrayOfNulls<MessageStructure>(256)

    fun get(type: Class<*>): MessageStructure? = structureClasses[type]

    fun get(opcode: Int): MessageStructure? = structureOpcodes[opcode]

    /**
     * Decodes the [packetStructures] [File]. The format is irrelevant as long
     * as the [structureClasses] is populated with correct data.
     */
    fun load(packetStructures: File): MessageStructureSet {
        val properties = ServerProperties().loadYaml(packetStructures)
        load(properties, storeOpcodes = false)
        load(properties, storeOpcodes = true)
        return this
    }

    /** Message class that owns each client opcode, so a second packets.yml entry for it is caught at load. */
    private val opcodeOwners = arrayOfNulls<String>(256)

    /**
     * Item PKT: a client opcode has exactly one entry. A second one used to overwrite the first in silence - OPLOCT (42) was
     * listed as OpLocTMessage and OpLocUMessage, and the later one made every spell / familiar special on an object do nothing.
     */
    private fun storeOpcode(
        opcode: Int,
        className: String,
        structure: MessageStructure,
    ) {
        val owner = opcodeOwners[opcode]
        check(owner == null) { "packets.yml: client opcode $opcode is registered twice ($owner and $className) - one entry per in-packet opcode" }
        opcodeOwners[opcode] = className
        structureOpcodes[opcode] = structure
    }

    private fun load(
        properties: ServerProperties,
        storeOpcodes: Boolean,
    ) {
        val packets = properties.get<ArrayList<Any>>(if (storeOpcodes) "in-packets" else "out-packets")!!
        packets.forEach { packet ->
            val values = packet as LinkedHashMap<*, *>
            val className = values["message"] as String
            val packetType =
                if (values.containsKey(
                        "type",
                    )
                ) {
                    PacketType.valueOf((values["type"] as String).uppercase())
                } else {
                    PacketType.FIXED
                }
            val clazz = Class.forName(className)
            val packetLength = values["length"] as? Int ?: 0
            val ignore = values["ignore"] as? Boolean ?: false

            val packetOpcodes = mutableListOf<Int>()
            if (values.containsKey("opcodes")) {
                val split = (values["opcodes"] as String).trim().split(",")
                split.forEach { v -> packetOpcodes.add(v.toInt()) }
            } else if (values.containsKey("opcode")) {
                packetOpcodes.add(values["opcode"] as Int)
            }

            if (clazz::class.java != IgnoreMessage::class.java) {
                val packetStructure = if (values.containsKey("structure")) values["structure"] as ArrayList<*> else null
                val packetValues = Object2ObjectLinkedOpenHashMap<String, MessageValue>()
                packetStructure?.forEach { structure ->
                    val structValues = structure as LinkedHashMap<*, *>
                    val name = structValues["name"] as String
                    val order =
                        if (structValues.containsKey(
                                "order",
                            )
                        ) {
                            DataOrder.valueOf(structValues["order"] as String)
                        } else {
                            DataOrder.BIG
                        }
                    val transform =
                        if (structValues.containsKey(
                                "trans",
                            )
                        ) {
                            DataTransformation.valueOf(structValues["trans"] as String)
                        } else {
                            DataTransformation.NONE
                        }
                    val type = DataType.valueOf(structValues["type"] as String)
                    val signature =
                        if (structValues.containsKey(
                                "sign",
                            )
                        ) {
                            DataSignature.valueOf((structValues["sign"] as String).uppercase())
                        } else {
                            DataSignature.SIGNED
                        }
                    packetValues[name] =
                        MessageValue(
                            id = name,
                            order = order,
                            transformation = transform,
                            type = type,
                            signature = signature,
                        )
                }
                val messageStructure =
                    MessageStructure(
                        type = packetType,
                        opcodes = packetOpcodes.toIntArray(),
                        length = packetLength,
                        ignore = ignore,
                        values = packetValues,
                    )
                structureClasses[clazz] = messageStructure
                if (storeOpcodes) {
                    packetOpcodes.forEach { opcode -> storeOpcode(opcode, className, messageStructure) }
                }
            } else {
                val messageStructure =
                    MessageStructure(
                        type = packetType,
                        opcodes = packetOpcodes.toIntArray(),
                        length = packetLength,
                        ignore = ignore,
                        values = Object2ObjectLinkedOpenHashMap(0),
                    )
                structureClasses[clazz] = messageStructure
                if (storeOpcodes) {
                    packetOpcodes.forEach { opcode -> storeOpcode(opcode, className, messageStructure) }
                }
            }
        }
    }
}
