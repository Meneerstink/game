package gg.rsmod.game.service.game

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.util.ServerProperties
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ObjectMetadataService : Service {
    private lateinit var path: Path

    override fun init(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        path = Paths.get(serviceProperties.getOrDefault("path", "./data/cfg/objs.yml"))
        if (!Files.exists(path)) {
            throw FileNotFoundException("Path does not exist. $path")
        }

        Files.newBufferedReader(path).use { reader ->
            load(world.definitions, reader)
        }
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
    }

    override fun bindNet(
        server: Server,
        world: World,
    ) {
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
    }

    private fun load(
        definitions: DefinitionSet,
        reader: BufferedReader,
    ) {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

        val reference = object : TypeReference<List<Metadata>>() {}
        mapper.readValue<List<Metadata>>(reader, reference)?.let { metadataSet ->
            metadataSet.forEach { metadata ->
                val def = definitions.getNullable(ObjectDef::class.java, metadata.id) ?: return@forEach
                def.examine = metadata.examine
                def.depleted = metadata.depleted
                metadata.solid?.let { def.solid = it }
            }
        }
        fillMissingExamines(definitions)
    }

    /**
     * Owner 2026-09-27: "every item placed in the mansion has no examine just like the skills area". Every copied loc
     * (Royal Hall variants, basement stations, signs, stairs) has an id of its own that objs.yml never lists, and some
     * sources have no examine either, so the server logged "No examine info found" and sent nothing. A named loc without
     * one takes the most common examine among locs of the same name, else a plain "A <name>." line.
     */
    private fun fillMissingExamines(definitions: DefinitionSet) {
        val all = definitions.getAll(ObjectDef::class.java).values.filterIsInstance<ObjectDef>()
        fun named(def: ObjectDef) = def.name.isNotBlank() && !def.name.equals("null", ignoreCase = true)
        val byName =
            all.filter { named(it) && it.examine != null }
                .groupBy { it.name.lowercase() }
                .mapValues { (_, defs) -> defs.groupingBy { it.examine!! }.eachCount().maxByOrNull { it.value }!!.key }
        all.filter { named(it) && it.examine == null }.forEach { def ->
            def.examine = byName[def.name.lowercase()] ?: plainExamine(def.name)
        }
    }

    companion object {
        fun plainExamine(name: String): String {
            val lower = name.lowercase()
            val article = if (lower.first() in "aeiou") "An" else "A"
            return "$article $lower."
        }
    }

    private data class Metadata(
        val id: Int = -1,
        val depleted: Int = -1,
        val examine: String? = null,
        val solid: Boolean? = null,
    )
}
