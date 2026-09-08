package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.StructDef

/**
 * Read-only diagnostic for the two config tables the client's own scripts are built on: enums and
 * structs.
 *
 * A great deal of what a 2011 interface displays is not sent by the server and is not hard-coded in
 * the interface either - it is looked up by a clientscript through CS2 opcode 3408 (`ENUM`) or read
 * off a struct. The quest list is the clearest example: interface 190's `onLoad` (clientscript 2165
 * -> 2160) reads an enum to learn how many quests exist and which struct describes each one, and
 * the server has no way to answer "what is the name of quest slot 47?" without reading the same
 * table.
 *
 * `EnumDef` and `StructDef` are already decoded by the server at boot, so this tool only exposes
 * what is there. It writes nothing.
 *
 * Modes:
 *  * `<cachePath> enum <id> [id ...]` - key/value type and every entry of those enums.
 *  * `<cachePath> struct <id> [id ...]` - every param of those structs.
 *  * `<cachePath> enumfind <substring>` - every enum holding a string value that contains the
 *    substring, with the matching keys. The offline way to find which table a piece of
 *    user-visible wording lives in.
 *  * `<cachePath> structfind <substring>` - the same over struct params.
 *
 * Types are printed as the CS2 type characters the cache stores, e.g. `105 'i'` for int and
 * `115 's'` for string.
 */
object ConfigDefProbeTool {
    private const val MAX_PRINTED_ENTRIES = 400

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) {
            "Usage: <cachePath> enum|struct <id> [id ...] | <cachePath> enumfind|structfind <substring>"
        }
        val cachePath = args[0]
        val mode = args[1].lowercase()

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet()
            when (mode) {
                "enum", "enumfind", "enumint" -> definitions.load(library, EnumDef::class.java)
                "varbit" -> definitions.load(library, gg.rsmod.game.fs.def.VarbitDef::class.java)
                "struct", "structfind" -> definitions.load(library, StructDef::class.java)
                else -> error("Unknown mode '$mode', expected 'enum', 'struct', 'enumfind', 'structfind', 'enumint' or 'varbit'")
            }

            when (mode) {
                "enum" -> args.drop(2).map { it.toInt() }.forEach { printEnum(definitions, it) }
                "struct" -> args.drop(2).map { it.toInt() }.forEach { printStruct(definitions, it) }
                "enumfind" -> findEnum(definitions, args.drop(2).joinToString(" "))
                "structfind" -> findStruct(definitions, args.drop(2).joinToString(" "))
                "enumint" -> findEnumInt(definitions, args.drop(2).map { it.toInt() })
                "varbit" -> {
                    val from = args[2].toInt()
                    val to = args[3].toInt()
                    for (id in from..to) {
                        val def = definitions.getNullable(gg.rsmod.game.fs.def.VarbitDef::class.java, id)
                        println(if (def == null) "VARBIT_$id ABSENT" else "VARBIT_$id varp=${def.varp} bits=${def.startBit}..${def.endBit}")
                    }
                }
            }
        } finally {
            library.close()
        }
    }

    private fun enums(definitions: DefinitionSet): Map<Int, EnumDef> {
        @Suppress("UNCHECKED_CAST")
        return definitions.getAll(EnumDef::class.java) as Map<Int, EnumDef>
    }

    private fun structs(definitions: DefinitionSet): Map<Int, StructDef> {
        @Suppress("UNCHECKED_CAST")
        return definitions.getAll(StructDef::class.java) as Map<Int, StructDef>
    }

    private fun printEnum(
        definitions: DefinitionSet,
        id: Int,
    ) {
        val def = enums(definitions)[id]
        if (def == null) {
            println("ENUM_$id ABSENT")
            return
        }
        println(
            "ENUM_$id keyType=${describeType(def.keyType)} valType=${describeType(def.valueType)} " +
                "size=${def.values.size} defaultInt=${def.defaultInt} defaultString='${def.defaultString}'",
        )
        def.values.keys.sorted().take(MAX_PRINTED_ENTRIES).forEach { key ->
            println("  $key=${render(def.values[key])}")
        }
        if (def.values.size > MAX_PRINTED_ENTRIES) {
            println("  ... ${def.values.size - MAX_PRINTED_ENTRIES} more")
        }
    }

    private fun printStruct(
        definitions: DefinitionSet,
        id: Int,
    ) {
        val def = structs(definitions)[id]
        if (def == null) {
            println("STRUCT_$id ABSENT")
            return
        }
        println("STRUCT_$id size=${def.values.size}")
        def.values.keys.sorted().forEach { key ->
            println("  param$key=${render(def.values[key])}")
        }
    }

    private fun findEnum(
        definitions: DefinitionSet,
        needle: String,
    ) {
        val lowered = needle.lowercase()
        val all = enums(definitions)
        var hits = 0
        all.keys.sorted().forEach { id ->
            val def = all.getValue(id)
            val matches = def.values.keys.sorted().filter { (def.values[it] as? String)?.lowercase()?.contains(lowered) == true }
            if (matches.isNotEmpty()) {
                hits++
                println("ENUM_$id size=${def.values.size} matches=${matches.size}")
                matches.take(20).forEach { println("  $it=${render(def.values[it])}") }
            }
        }
        println("ENUMFIND='$needle' SCANNED=${all.size} HITS=$hits")
    }

    /** Every enum holding any of the given integer values - the only way to find "which table maps slots to struct/varbit N". */
    private fun findEnumInt(
        definitions: DefinitionSet,
        needles: List<Int>,
    ) {
        val all = enums(definitions)
        var hits = 0
        all.keys.sorted().forEach { id ->
            val def = all.getValue(id)
            val matches = def.values.keys.sorted().filter { (def.values[it] as? Int) in needles }
            if (matches.isNotEmpty()) {
                hits++
                println("ENUM_$id size=${def.values.size} matches=${matches.size}")
                matches.take(20).forEach { println("  $it=${render(def.values[it])}") }
            }
        }
        println("ENUMINT=$needles SCANNED=${all.size} HITS=$hits")
    }

    private fun findStruct(
        definitions: DefinitionSet,
        needle: String,
    ) {
        val lowered = needle.lowercase()
        val all = structs(definitions)
        var hits = 0
        all.keys.sorted().forEach { id ->
            val def = all.getValue(id)
            val matches = def.values.keys.sorted().filter { (def.values[it] as? String)?.lowercase()?.contains(lowered) == true }
            if (matches.isNotEmpty()) {
                hits++
                println("STRUCT_$id size=${def.values.size} matches=${matches.size}")
                matches.forEach { println("  param$it=${render(def.values[it])}") }
            }
        }
        println("STRUCTFIND='$needle' SCANNED=${all.size} HITS=$hits")
    }

    private fun render(value: Any?): String = if (value is String) "'$value'" else value.toString()

    private fun describeType(type: Int): String =
        if (type in 32..126) {
            "$type '${type.toChar()}'"
        } else {
            "$type"
        }
}
