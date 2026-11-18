package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.JsonNode

/** Static form of cache2686 source688/690: 25 cells, 24 movable model layers per sourced picture. */
object CluePuzzleInterfaceBake {
    const val CELLS = 25
    const val TILES = 24
    const val CLICK_FIRST = 6
    const val MODEL_FIRST = CLICK_FIRST + CELLS
    const val SIDE = 49
    const val STRIDE = 50 // source688:49+(251-5*49)/(5-1), integer arithmetic
    val SELECTORS = listOf(1,2,3,4,5,6,9)

    data class Picture(val selector: Int, val models: List<Int>)

    /**
     * OSRS offsetX/Y are camera translations, not the 667 IF3 projection-centre fields.
     * For the unrotated frame meshes, bake (xoff, yoff, yoff) before camera pitch,
     * exactly as CC_SETMODELANGLE/InterfaceManager would do with xof2d/yof2d.
     * Decode from source on each import; never translate an already ported mesh.
     */
    fun adaptFrameModel(id: Int, model: ModelData, source: JsonNode) {
        val frame = source.elements().asSequence().singleOrNull {
            it["type"].asInt() == 6 && it["modelId"].asInt() == id
        } ?: return
        require(frame["rotationY"].asInt() == 0 && frame["rotationZ"].asInt() == 0)
        require(model.version < 13) { "Puzzle source offsets require OSRS coarse model units" }
        val x = frame["offsetX2d"].asInt()
        val y = frame["offsetY2d"].asInt()
        repeat(model.vertexCount) { v ->
            model.vertexX[v] += x
            model.vertexY[v] += y
            model.vertexZ[v] += y
        }
    }
    fun pictures(node: JsonNode): List<Picture> = node.map { row ->
        Picture(row["selector"].asInt(),row["models"].map { it.asInt() })
    }.also { rows ->
        require(rows.map { it.selector } == SELECTORS)
        require(rows.all { it.models.size == TILES && it.models.distinct().size == TILES })
    }
    fun modelComponent(picture: Int, piece: Int): Int {
        require(picture in SELECTORS.indices && piece in 0 until TILES)
        return MODEL_FIRST + picture * TILES + piece
    }

    fun components(source: JsonNode, pictures: List<Picture>, models: Map<Int,Int>, sprites: Map<Int,Int>): List<OsrsInterfacePortTool.C> {
        require(source.fieldNames().asSequence().map(String::toInt).sorted().toList() == (0..5).toList())
        require(pictures.map { it.selector } == SELECTORS)
        fun JsonNode.n(key: String): Int = get(key).asInt()
        fun JsonNode.b(key: String): Boolean = get(key).asBoolean()
        val components=(0..5).map { id ->
            val n=source[id.toString()]
            val type=n.n("type")
            require(type in setOf(0,3,5,6))
            fun hook(key: String): List<Int> = n[key]?.takeUnless { it.isNull }?.map { it.asInt() }?.let {
                require(it.size==3 && it[0]==44 && it[1]==-2147483645)
                listOf(it[0],it[1],sprites.getValue(it[2]))
            }.orEmpty()
            OsrsInterfacePortTool.C(id,type,n.n("originalX"),n.n("originalY"),n.n("originalWidth"),n.n("originalHeight"),
                n.n("parentId").let { if(it<0)-1 else it and 65535 },n.n("widthMode"),n.n("heightMode"),
                n.n("xPositionMode"),n.n("yPositionMode"),n.b("isHidden"),
                scrollW=n.n("scrollWidth"),scrollH=n.n("scrollHeight"),noClickThrough=n.b("noClickThrough"),
                sprite=if(type==5)sprites.getValue(n.n("spriteId")) else -1,colour=n.n("textColor"),filled=n.b("filled"),
                ops=n["actions"]?.map { it.asText() }.orEmpty(),model=if(type==6)models.getValue(n.n("modelId")) else -1,
                // Frame offsets are baked in adaptFrameModel, never interpreted as screen pixels.
                originX=0,originY=0,xan=n.n("rotationX"),yan=n.n("rotationZ"),zan=n.n("rotationY"),
                zoom=n.n("modelZoom"),ortho=n.b("orthogonal"),mouseOver=hook("onMouseOverListener"),mouseLeave=hook("onMouseLeaveListener"))
        }.toMutableList()
        require(components.single { it.id==4 }.let { it.w==251 && it.h==251 })
        repeat(CELLS) { cell ->
            // These are the type4 empty-text source click regions; the model itself receives no ops.
            components += OsrsInterfacePortTool.C(CLICK_FIRST+cell,4,cell%5*STRIDE,cell/5*STRIDE,SIDE,SIDE,4,
                0,0,0,0,false,ops=listOf("Move"),opBase="<col=ff9040>Sliding piece</col>")
        }
        pictures.forEachIndexed { index,picture ->
            require(picture.models.size==TILES)
            picture.models.forEachIndexed { piece,model ->
                components += OsrsInterfacePortTool.C(modelComponent(index,piece),6,0,0,SIDE,SIDE,4,0,0,0,0,true,
                    model=models.getValue(model),xan=513,zoom=1400)
            }
        }
        return components
    }
}
