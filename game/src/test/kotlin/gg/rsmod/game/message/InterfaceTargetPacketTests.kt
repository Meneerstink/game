package gg.rsmod.game.message

import gg.rsmod.net.packet.DataOrder
import gg.rsmod.net.packet.DataTransformation
import gg.rsmod.net.packet.DataType
import gg.rsmod.util.ServerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Paths

/**
 * Pins the wire layout of `ClientProt.IF_BUTTONT` - the one packet the client sends for **every**
 * target-mode click on a component - against the order the client itself writes it in.
 *
 * ## Why this test exists
 *
 * `OpHeldUHandler` has to tell two very different interactions apart from this single packet:
 * "a spell or interface button was used on an inventory item", and "one inventory item was used on
 * another". It does that from the *source* component's fields, so if the field order here ever
 * drifts from the client's, the handler silently starts reading the wrong halves of the packet and
 * both interactions break in ways that look like content bugs rather than protocol bugs.
 *
 * That is not hypothetical. The owner's G5 - Pack yak Winter Storage entering inventory targeting
 * and then doing nothing when the item was clicked - was exactly this class of fault: the handler
 * discriminated on `from_slot == -1`, which is the source's *dynamic child index*, and both
 * Summoning special-move buttons are children created by clientscript 606 and so arrive with `0`.
 *
 * ## The client's own write, from `MiniMenu.sendTargetButton`
 *
 * ```java
 * ClientMessage message = ClientMessage.create(ClientProt.IF_BUTTONT, ...);   // (73, 16)
 * message.bitPacket.p4_alt2(button.slot);                    // clicked component hash
 * message.bitPacket.p2_alt2(InterfaceManager.targetInvObj);  // source item, -1 for a button
 * message.bitPacket.p2_alt3(InterfaceManager.targetComponent); // source child index
 * message.bitPacket.p4_alt3(InterfaceManager.targetSlot);    // source component hash
 * message.bitPacket.p2_alt2(button.invObject);               // clicked item
 * message.bitPacket.p2_alt1(button.id);                      // clicked child index / inv slot
 * ```
 *
 * The field *names* in the deob are inverted and must not be trusted: `Component.slot` holds the
 * component hash and `Component.id` the child index, which is provable from
 * `InterfaceList.getComponent(idAndSlot, component)` being called as
 * `getComponent(component.slot, component.id)`, and from `CC_CREATE` setting
 * `cc.slot = parent.slot; cc.id = componentId`. The byte widths are the reliable guide: a hash
 * needs four bytes, a child index two.
 *
 * ## The encodings, derived from `Packet`'s own primitives
 *
 * | client | bytes written | server |
 * |---|---|---|
 * | `p2_alt1` | `v, v>>8` | SHORT, LITTLE |
 * | `p2_alt2` | `v>>8, v+128` | SHORT, BIG, ADD |
 * | `p2_alt3` | `v+128, v>>8` | SHORT, LITTLE, ADD |
 * | `p4_alt2` | `v>>8, v, v>>24, v>>16` | INT, MIDDLE |
 * | `p4_alt3` | `v>>16, v>>24, v, v>>8` | INT, INVERSED_MIDDLE |
 *
 * Cross-checked against the already-working `OpNpcT` entry, whose client write uses three of the
 * same primitives and whose `packets.yml` structure agrees with this table field for field.
 */
class InterfaceTargetPacketTests {
    @Test
    fun `IF_BUTTONT is registered at the opcode and length the client sends`() {
        val packet = inPacket("gg.rsmod.game.message.impl.OpHeldUMessage")
        assertEquals("IF_BUTTONT is ClientProt(73, 16)", 73, packet.get<Int>("opcode"))
        assertEquals("IF_BUTTONT is ClientProt(73, 16)", 16, packet.get<Int>("length"))
        assertEquals("FIXED", packet.get<String>("type"))
    }

    @Test
    fun `IF_BUTTONT's fields are in the order and encoding the client writes them`() {
        val fields = structureOf("gg.rsmod.game.message.impl.OpHeldUMessage")
        assertEquals(
            "the field order no longer matches MiniMenu.sendTargetButton",
            listOf("to_component", "from_item", "from_slot", "from_component", "to_item", "to_slot"),
            fields.map { it.name },
        )

        // p4_alt2(button.slot) - the clicked component's hash.
        assertField(fields[0], DataType.INT, DataOrder.MIDDLE, DataTransformation.NONE)
        // p2_alt2(targetInvObj) - the source's item, and the discriminator OpHeldUHandler uses.
        assertField(fields[1], DataType.SHORT, DataOrder.BIG, DataTransformation.ADD)
        // p2_alt3(targetComponent) - the source's dynamic child index. 0 for a button made by
        // CC_CREATE, -1 for a static one; this is the field that must NOT be used to discriminate.
        assertField(fields[2], DataType.SHORT, DataOrder.LITTLE, DataTransformation.ADD)
        // p4_alt3(targetSlot) - the source component's hash, which is what the plugin binding
        // (on_spell_on_item) is keyed on.
        assertField(fields[3], DataType.INT, DataOrder.INVERSED_MIDDLE, DataTransformation.NONE)
        // p2_alt2(button.invObject) - the clicked item.
        assertField(fields[4], DataType.SHORT, DataOrder.BIG, DataTransformation.ADD)
        // p2_alt1(button.id) - the clicked child index, i.e. the inventory slot.
        assertField(fields[5], DataType.SHORT, DataOrder.LITTLE, DataTransformation.NONE)
    }

    @Test
    fun `OpNpcT still agrees with the same encoding table`() {
        /*
         * The independent cross-check. OpNpcT is known-good - familiar attack orders and every
         * item-on-npc interaction go through it - and its client write shares three primitives
         * with IF_BUTTONT. If the table above were wrong, this would disagree too.
         */
        val fields = structureOf("gg.rsmod.game.message.impl.OpNpcTMessage")
        val byName = fields.associateBy { it.name }
        // p2_alt3(targetComponent)
        assertField(byName.getValue("component_slot"), DataType.SHORT, DataOrder.LITTLE, DataTransformation.ADD)
        // p2_alt1(targetInvObj)
        assertField(byName.getValue("verify"), DataType.SHORT, DataOrder.LITTLE, DataTransformation.NONE)
        // p4_alt3(targetSlot)
        assertField(byName.getValue("component_hash"), DataType.INT, DataOrder.INVERSED_MIDDLE, DataTransformation.NONE)
    }

    private data class Field(
        val name: String,
        val type: DataType,
        val order: DataOrder,
        val transformation: DataTransformation,
    )

    private fun assertField(
        field: Field,
        type: DataType,
        order: DataOrder,
        transformation: DataTransformation,
    ) {
        assertEquals("${field.name}: wrong type", type, field.type)
        assertEquals("${field.name}: wrong byte order", order, field.order)
        assertEquals("${field.name}: wrong transformation", transformation, field.transformation)
    }

    private fun inPacket(message: String): ServerProperties {
        val properties = ServerProperties().loadYaml(Paths.get("..", "data", "packets.yml").toFile())
        val packets = properties.get<ArrayList<*>>("in-packets")!!
        val match =
            packets
                .map { it as LinkedHashMap<*, *> }
                .firstOrNull { it["message"] == message }
        assertNotNull("$message is not registered in packets.yml at all", match)
        @Suppress("UNCHECKED_CAST")
        return ServerProperties().loadMap(match as Map<String, Any>)
    }

    private fun structureOf(message: String): List<Field> {
        val structures = inPacket(message).get<ArrayList<*>>("structure")!!
        return structures.map { entry ->
            val map = entry as LinkedHashMap<*, *>
            Field(
                name = map["name"] as String,
                type = DataType.valueOf(map["type"] as String),
                order = if (map.containsKey("order")) DataOrder.valueOf(map["order"] as String) else DataOrder.BIG,
                transformation =
                    if (map.containsKey("trans")) {
                        DataTransformation.valueOf(map["trans"] as String)
                    } else {
                        DataTransformation.NONE
                    },
            )
        }
    }
}
