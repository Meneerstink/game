package gg.rsmod.plugins.content.areas.watson

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import java.io.File

/** Wiki-exact named scenery; only the tool's dual-cache/250-placement verified mapping is accepted. */
object MimicArenaScenery {
    data class Source(val id: Int,val name: String,val examine: String,val options: List<String?>)
    data class Mapping(val region: Int,val locals: Map<Int,Int>)
    val sources by lazy { parseSources(File(RspsPaths.data("cfg/clue/mimic-scenery.json")).readText()) }
    fun parseSources(text: String): List<Source> = JsonParser().parse(text).asJsonArray.map { node -> node.asJsonObject.let { row ->
        Source(row["source"].asInt,row["name"].asString,row["examine"].asString,
            row["options"].asJsonArray.map { if(it.isJsonNull)null else it.asString })
    } }.also { rows ->
        require(rows.map { it.id }.toSet()==(34727..34732).toSet() && rows.size==6)
        require(rows.all { it.examine.isNotBlank() && it.options.size==5 })
    }
    fun parse(text: String): Mapping {
        val lines=text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.first().startsWith('#') && lines.first().endsWith("applied=true"))
        val rows=lines.filterNot { it.startsWith('#') }.map { it.split('\t') }
        require(rows.all { it.size==3 })
        val region=rows.single { it[0]=="region" }
        require(region[1]=="10819")
        val target=region[2].toInt();require(target in 0..65535 && target!=10819)
        val ids=rows.filter { it[0]=="loc" }.map { it[1].toInt() to it[2].toInt() }
        require(rows.size==7 && ids.size==6 && ids.map { it.first }.toSet()==(34727..34732).toSet())
        require(ids.map { it.second }.distinct().size==6 && ids.all { it.second>=0 })
        return Mapping(target,ids.toMap())
    }
    val mapping by lazy { File(RspsPaths.data("cfg/clue/mimic-arena.tsv")).takeIf(File::isFile)?.readText()?.let(::parse) }
    fun install(world: World,layout: Mapping?=mapping,rows: List<Source> = sources) {
        if(layout==null)return
        // Validate the whole set before publishing any examine text.
        val definitions=rows.map { row ->
            val id=layout.locals.getValue(row.id)
            val def=world.definitions.get(ObjectDef::class.java,id)
            check(def.name==row.name && def.options.map { it?.takeUnless(String::isBlank) }==row.options) {
                "CLUE Mimic scenery${row.id}->$id does not match its applied definition"
            }
            def to row.examine
        }
        definitions.forEach { (def,text) -> def.examine=text }
    }
}
