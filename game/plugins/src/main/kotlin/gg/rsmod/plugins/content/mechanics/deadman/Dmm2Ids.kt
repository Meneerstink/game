package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.api.cfg.Items
import java.io.File

/** Source ids are keys, never local item ids. Claude supplies cfg/dmm2/ids.tsv after his cache transaction. */
object Dmm2Ids {
    enum class ItemId(val source: Int) {
        BLOOD_MONEY(13307), CHEST(13317), LEGS(13318), CAPE(13319), TELEPORT_CARD(13658),
        VOIDWAKER(28531), CLAWS(28534), GODSWORD(28537), TWISTED_BOW(28540),
        SCYTHE(28543), SCYTHE_EMPTY(28545), SHADOW(28547), SHADOW_EMPTY(28549),
        VENGEANCE(28561), FAIRIES(28564), ADVANCED_WEAPONRY(28567), UNDEAD(28570),
        DARK_BOW(29599), VOLATILE(29602), KHOPESH(33041), AVARICE(33044),
        FORTUITY_EMPTY(33047), FORTUITY(33050), FIRE_RUBY(34422), FIRE_AMULET(34425),
        ANCIENT_DEADMAN_CAPE(34607);
        val local: Int? get() = item(source)
        fun matches(id: Int?): Boolean = id != null && local == id
    }

    @Volatile private var installed: Map<String, Int>? = null
    /** Install once at boot before registering plugins; copies and validates the cache handoff. */
    fun install(lines: List<String>) { installed = parse(lines) }
    private val imported: Map<String, Int> get() = installed ?: diskTable
    private val diskTable: Map<String, Int> by lazy {
        val file = File(RspsPaths.data("cfg/dmm2/ids.tsv"))
        if (!file.isFile) emptyMap() else parse(file.readLines())
    }
    fun parse(lines: List<String>): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val fields = line.split('\t')
            require(fields.size == 3) { "DMM2 id row must be kind, source, local" }
            val source = fields[1].toInt(); val local = fields[2].toInt()
            require(source > 0 && local > 0 && fields[0] in setOf("item", "npc", "seq", "spotanim", "synth", "loc", "interface", "script", "sprite"))
            require(out.put("${fields[0]}:$source", local) == null) { "Duplicate DMM2 source id" }
        }
        require(out.filterKeys { it.startsWith("item:") }.values.distinct().size == out.count { it.key.startsWith("item:") })
        return out.toMap()
    }
    fun item(source: Int): Int? = imported["item:$source"] ?: when (source) {
        28564 -> Items.TRINKET_OF_FAIRIES
        28570 -> Items.TRINKET_OF_UNDEAD
        33044 -> Items.TRINKET_OF_AVARICE
        33047 -> Items.TRINKET_OF_FORTUITY_INACTIVE
        else -> null
    }
    fun npc(source: Int): Int? = imported["npc:$source"]
    fun seq(source: Int): Int? = imported["seq:$source"]
    fun spot(source: Int): Int? = imported["spotanim:$source"]
    fun loc(source: Int): Int? = imported["loc:$source"]
    fun sound(source: Int): Int? = imported["synth:$source"]
    fun script(source: Int): Int? = imported["script:$source"]
    fun sprite(source: Int): Int? = imported["sprite:$source"]
    fun interfaceId(source: Int): Int? = imported["interface:$source"]
}
