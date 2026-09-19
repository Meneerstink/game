package gg.rsmod.game.tools.importer

/**
 * Decoded form of a **modern OSRS** item definition, read out of the pinned upstream cache's
 * index 2 / group 10.
 *
 * This is deliberately a separate type from [gg.rsmod.game.fs.def.ItemDef]: the two formats share
 * many opcode numbers but not all of them, and the modern one carries fields rev-667 has no
 * concept of (int-width model ids at opcodes 44-54, sub-ops and conditional ops at 43/200-202,
 * `category`, placeholder links). Keeping them apart is what makes the compatibility analysis in
 * `RSPS_CURRENT_SPRINT.json` task A5 possible at all - a shared type would quietly hide exactly
 * the differences that have to be proven.
 *
 * Field names follow the upstream client/RuneLite naming so they can be cross-checked directly
 * against `net.runelite.cache.definitions.loaders.ItemLoader`, from which this opcode table is
 * transcribed.
 */
class ModernItemDef(val id: Int) {
    var name: String = "null"
    var examine: String? = null
    var inventoryModel: Int = 0
    var zoom2d: Int = 2000
    var xan2d: Int = 0
    var yan2d: Int = 0
    var zan2d: Int = 0
    var xOffset2d: Int = 0
    var yOffset2d: Int = 0
    var stackable: Int = 0
    var cost: Int = 1
    var wearPos1: Int = -1
    var wearPos2: Int = -1
    var wearPos3: Int = -1
    var members: Boolean = false
    var tradeable: Boolean = true
    var geTradeable: Boolean = false
    var weight: Int = 0
    var category: Int = -1
    var maleModel0: Int = -1
    var maleModel1: Int = -1
    var maleModel2: Int = -1
    var femaleModel0: Int = -1
    var femaleModel1: Int = -1
    var femaleModel2: Int = -1
    var maleOffset: Int = 0
    var femaleOffset: Int = 0
    var maleHeadModel: Int = -1
    var maleHeadModel2: Int = -1
    var femaleHeadModel: Int = -1
    var femaleHeadModel2: Int = -1
    var resizeX: Int = 128
    var resizeY: Int = 128
    var resizeZ: Int = 128
    var ambient: Int = 0
    var contrast: Int = 0
    var team: Int = 0
    var shiftClickDropIndex: Int = -2
    var notedId: Int = -1
    var notedTemplate: Int = -1
    var boughtId: Int = -1
    var boughtTemplateId: Int = -1
    var placeholderId: Int = -1
    var placeholderTemplateId: Int = -1
    var colorFind: IntArray = IntArray(0)
    var colorReplace: IntArray = IntArray(0)
    var textureFind: IntArray = IntArray(0)
    var textureReplace: IntArray = IntArray(0)
    val groundOptions: Array<String?> = arrayOfNulls(5)
    val inventoryOptions: Array<String?> = arrayOfNulls(5)
    val countObj: IntArray = IntArray(10)
    val countCo: IntArray = IntArray(10)
    val params: MutableMap<Int, Any> = LinkedHashMap()

    /** Bytes left after the terminating opcode 0; non-zero means an opcode length differs from this decoder's table. */
    var trailingBytes: Int = 0

    /** Every opcode actually present in the source bytes, in order - the input to compatibility analysis. */
    val opcodesSeen: MutableList<Int> = mutableListOf()

    /** Every model id this definition references, ignoring the -1/unset ones. */
    fun modelDependencies(): Map<String, Int> =
        linkedMapOf(
            "inventoryModel" to inventoryModel,
            "maleModel0" to maleModel0,
            "maleModel1" to maleModel1,
            "maleModel2" to maleModel2,
            "femaleModel0" to femaleModel0,
            "femaleModel1" to femaleModel1,
            "femaleModel2" to femaleModel2,
            "maleHeadModel" to maleHeadModel,
            "maleHeadModel2" to maleHeadModel2,
            "femaleHeadModel" to femaleHeadModel,
            "femaleHeadModel2" to femaleHeadModel2,
        ).filterValues { it != -1 && it != 0 }

    fun describe(): String {
        val lines = mutableListOf<String>()
        lines += "id=$id"
        lines += "name=$name"
        lines += "examine=${examine ?: "<none>"}"
        lines += "opcodes=${opcodesSeen.joinToString(",")}"
        lines += "inventoryModel=$inventoryModel zoom2d=$zoom2d xan2d=$xan2d yan2d=$yan2d zan2d=$zan2d xOffset2d=$xOffset2d yOffset2d=$yOffset2d"
        lines += "wearPos1=$wearPos1 wearPos2=$wearPos2 wearPos3=$wearPos3 members=$members tradeable=$tradeable geTradeable=$geTradeable"
        lines += "cost=$cost stackable=$stackable weight=$weight category=$category team=$team"
        lines += "maleModel0=$maleModel0 maleModel1=$maleModel1 maleModel2=$maleModel2 maleOffset=$maleOffset maleHeadModel=$maleHeadModel maleHeadModel2=$maleHeadModel2"
        lines += "femaleModel0=$femaleModel0 femaleModel1=$femaleModel1 femaleModel2=$femaleModel2 femaleOffset=$femaleOffset femaleHeadModel=$femaleHeadModel femaleHeadModel2=$femaleHeadModel2"
        lines += "resizeX=$resizeX resizeY=$resizeY resizeZ=$resizeZ ambient=$ambient contrast=$contrast"
        lines += "colorFind=${colorFind.joinToString(",")} colorReplace=${colorReplace.joinToString(",")}"
        lines += "textureFind=${textureFind.joinToString(",")} textureReplace=${textureReplace.joinToString(",")}"
        lines += "groundOptions=${groundOptions.joinToString(",") { it ?: "-" }}"
        lines += "inventoryOptions=${inventoryOptions.joinToString(",") { it ?: "-" }}"
        lines += "notedId=$notedId notedTemplate=$notedTemplate placeholderId=$placeholderId placeholderTemplateId=$placeholderTemplateId"
        lines += "params=${params.entries.joinToString(",") { "${it.key}=${it.value}" }}"
        lines += "modelDependencies=${modelDependencies().entries.joinToString(",") { "${it.key}:${it.value}" }}"
        return lines.joinToString("\n")
    }
}

/**
 * Decoder for the modern OSRS item-definition opcode stream, transcribed from
 * `net.runelite.cache.definitions.loaders.ItemLoader` and `EntityOpsLoader` at the same time as the
 * pinned cache snapshot was taken.
 *
 * Like [ItemDefCodec] on the rev-667 side, an unrecognised opcode is a hard error rather than a
 * skip: a wrong field width silently desynchronises the whole stream and would produce
 * plausible-looking but fabricated model ids, which is precisely the failure this pipeline must
 * never produce.
 */
object ModernItemDefDecoder {
    fun decode(
        id: Int,
        data: ByteArray,
    ): ModernItemDef {
        val def = ModernItemDef(id)
        val buf = ModernBuffer(data)
        while (true) {
            val opcode = buf.u8()
            if (opcode == 0) break
            def.opcodesSeen += opcode
            decodeOpcode(def, buf, opcode)
        }
        if (def.stackable == 1) def.weight = 0
        def.trailingBytes = data.size - buf.position
        return def
    }

    private fun decodeOpcode(
        def: ModernItemDef,
        buf: ModernBuffer,
        opcode: Int,
    ) {
        when {
            opcode == 1 -> def.inventoryModel = buf.u16()
            opcode == 2 -> def.name = buf.string()
            opcode == 3 -> def.examine = buf.string()
            opcode == 4 -> def.zoom2d = buf.u16()
            opcode == 5 -> def.xan2d = buf.u16()
            opcode == 6 -> def.yan2d = buf.u16()
            opcode == 7 -> def.xOffset2d = buf.i16()
            opcode == 8 -> def.yOffset2d = buf.i16()
            opcode == 9 -> buf.string()
            opcode == 11 -> def.stackable = 1
            opcode == 12 -> def.cost = buf.i32()
            opcode == 13 -> def.wearPos1 = buf.i8()
            opcode == 14 -> def.wearPos2 = buf.i8()
            opcode == 15 -> def.tradeable = false
            opcode == 16 -> def.members = true
            opcode == 23 -> {
                def.maleModel0 = buf.u16()
                def.maleOffset = buf.u8()
            }
            opcode == 24 -> def.maleModel1 = buf.u16()
            opcode == 25 -> {
                def.femaleModel0 = buf.u16()
                def.femaleOffset = buf.u8()
            }
            opcode == 26 -> def.femaleModel1 = buf.u16()
            opcode == 27 -> def.wearPos3 = buf.i8()
            opcode in 30..34 -> def.groundOptions[opcode - 30] = buf.string().takeIf { !it.equals("Hidden", true) }
            opcode in 35..39 -> def.inventoryOptions[opcode - 35] = buf.string()
            opcode == 40 -> {
                val count = buf.u8()
                def.colorFind = IntArray(count)
                def.colorReplace = IntArray(count)
                for (i in 0 until count) {
                    def.colorFind[i] = buf.u16()
                    def.colorReplace[i] = buf.u16()
                }
            }
            opcode == 41 -> {
                val count = buf.u8()
                def.textureFind = IntArray(count)
                def.textureReplace = IntArray(count)
                for (i in 0 until count) {
                    def.textureFind[i] = buf.u16()
                    def.textureReplace[i] = buf.u16()
                }
            }
            opcode == 42 -> def.shiftClickDropIndex = buf.i8()
            opcode == 43 -> {
                // Worn sub-options (build 2686, verified on Ring of shadows 28327): op index, then (sub-op id, name)
                // pairs until a 0 id. The single-pair reading this decoder used before desynced the stream after the
                // first entry and then failed on a text byte ("Unknown opcode 85").
                buf.u8() // op index
                while (true) {
                    val subOp = buf.u8()
                    if (subOp == 0) break
                    buf.string()
                }
            }
            opcode == 44 -> def.inventoryModel = buf.i32()
            opcode == 45 -> {
                def.maleModel0 = buf.i32()
                def.maleOffset = buf.u8()
            }
            opcode == 46 -> def.maleModel1 = buf.i32()
            opcode == 47 -> def.maleModel2 = buf.i32()
            opcode == 48 -> {
                def.femaleModel0 = buf.i32()
                def.femaleOffset = buf.u8()
            }
            opcode == 49 -> def.femaleModel1 = buf.i32()
            opcode == 50 -> def.femaleModel2 = buf.i32()
            opcode == 51 -> def.maleHeadModel = buf.i32()
            opcode == 52 -> def.maleHeadModel2 = buf.i32()
            opcode == 53 -> def.femaleHeadModel = buf.i32()
            opcode == 54 -> def.femaleHeadModel2 = buf.i32()
            opcode == 65 -> def.geTradeable = true
            opcode == 75 -> def.weight = buf.i16()
            opcode == 78 -> def.maleModel2 = buf.u16()
            opcode == 79 -> def.femaleModel2 = buf.u16()
            opcode == 90 -> def.maleHeadModel = buf.u16()
            opcode == 91 -> def.femaleHeadModel = buf.u16()
            opcode == 92 -> def.maleHeadModel2 = buf.u16()
            opcode == 93 -> def.femaleHeadModel2 = buf.u16()
            opcode == 94 -> def.category = buf.u16()
            opcode == 95 -> def.zan2d = buf.u16()
            opcode == 97 -> def.notedId = buf.u16()
            opcode == 98 -> def.notedTemplate = buf.u16()
            opcode in 100..109 -> {
                def.countObj[opcode - 100] = buf.u16()
                def.countCo[opcode - 100] = buf.u16()
            }
            opcode == 110 -> def.resizeX = buf.u16()
            opcode == 111 -> def.resizeY = buf.u16()
            opcode == 112 -> def.resizeZ = buf.u16()
            opcode == 113 -> def.ambient = buf.i8()
            opcode == 114 -> def.contrast = buf.i8()
            opcode == 115 -> def.team = buf.u8()
            opcode == 139 -> def.boughtId = buf.u16()
            opcode == 140 -> def.boughtTemplateId = buf.u16()
            opcode == 148 -> def.placeholderId = buf.u16()
            opcode == 149 -> def.placeholderTemplateId = buf.u16()
            opcode == 160 -> def.stackable = 2
            opcode == 200 -> {
                buf.u8() // op index
                buf.u8() // sub-op id
                buf.string()
            }
            opcode == 201 -> {
                buf.u8() // op index
                buf.u16() // varp
                buf.u16() // varbit
                buf.i32() // min
                buf.i32() // max
                buf.string()
            }
            opcode == 202 -> {
                buf.u8() // op index
                buf.u16() // sub-op id
                buf.u16() // varp
                buf.u16() // varbit
                buf.i32() // min
                buf.i32() // max
                buf.string()
            }
            opcode == 249 -> {
                val count = buf.u8()
                repeat(count) {
                    val isString = buf.u8() == 1
                    val key = buf.u24()
                    def.params[key] = if (isString) buf.string() else buf.i32()
                }
            }
            else ->
                throw IllegalArgumentException(
                    "Unknown modern item-def opcode $opcode at offset ${buf.position} - this decoder's table is " +
                        "transcribed from RuneLite's ItemLoader and must be extended there and here together " +
                        "before this upstream build can be read safely.",
                )
        }
    }
}

/** Big-endian cursor over a modern item-definition byte stream. */
class ModernBuffer(private val data: ByteArray) {
    var position: Int = 0
        private set

    fun u8(): Int = data[position++].toInt() and 0xFF

    fun i8(): Int = data[position++].toInt()

    fun u16(): Int = (u8() shl 8) or u8()

    fun i16(): Int = u16().let { if (it > 32767) it - 65536 else it }

    fun u24(): Int = (u8() shl 16) or (u8() shl 8) or u8()

    fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun string(): String {
        val start = position
        while (data[position].toInt() != 0) position++
        val value = String(data, start, position - start, Charsets.ISO_8859_1)
        position++
        return value
    }
}
