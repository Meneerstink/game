package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef

/**
 * Read-only diagnostic for world objects, the object-side counterpart of [ItemParamProbeTool].
 *
 * Object content in this project is bound per `(objectId, optionIndex)` through
 * `on_obj_option(obj, option)`, so almost every "the door/gate/box does nothing" defect comes down
 * to one of two things: the option the player clicked sits at a different index than the plugin
 * assumed, or the object the player clicked is a variant id nobody bound. Both are answered
 * directly by the object definitions, and there was no way to read them outside a running server.
 *
 * Modes:
 *  * `<cachePath> <objectId> [objectId ...]` - dump name and options for specific ids.
 *  * `<cachePath> name <substring>` - every object whose name contains the substring.
 *  * `<cachePath> option <substring>` - every object with a matching option, and at which index.
 *
 * Option indices are printed 1-based, i.e. as `on_obj_option` binds them.
 */
object ObjectDefProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) {
            "Usage: <cachePath> <objectId> [objectId ...] | <cachePath> name <substring> | <cachePath> option <substring>"
        }
        val cachePath = args[0]

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ObjectDef::class.java)
            @Suppress("UNCHECKED_CAST")
            val objects = definitions.getAll(ObjectDef::class.java) as Map<Int, ObjectDef>

            when (args[1]) {
                "name", "option" -> {
                    require(args.size >= 3) { "Usage: <cachePath> ${args[1]} <substring>" }
                    val needle = args.drop(2).joinToString(" ").lowercase()
                    var hits = 0
                    objects.keys.sorted().forEach { id ->
                        val def = objects.getValue(id)
                        val optionIndex = def.options.indexOfFirst { it?.lowercase()?.contains(needle) == true }
                        val matches =
                            if (args[1] == "name") {
                                def.name.lowercase().contains(needle)
                            } else {
                                optionIndex != -1
                            }
                        if (matches) {
                            hits++
                            println("OBJECT_$id ${describe(def)}")
                        }
                    }
                    println("${args[1].uppercase()}_SEARCH='$needle' SCANNED=${objects.size} HITS=$hits")
                }

                "doorpairs" -> {
                    /*
                     * The pairing rule the general-doors system uses, kept identical to
                     * content/mechanics/doors/doors.plugin.kts so the derived set can be counted
                     * and spot-checked outside a running server.
                     *
                     * A definition advertising `Open` is the closed half, and its opened half is a
                     * neighbouring id that carries `Close` in the same option slot under the same
                     * name. Neither direction can be preferred: data/cfg/doors/single-doors.json
                     * contains both 10527 -> 10528 and 24931 -> 24930. So a pair is only reported
                     * when exactly one neighbour qualifies AND no other closed id claims the same
                     * opened half - the ambiguous remainder is where the double doors live
                     * (1551/1552/1553, 31825/31826/31827), and guessing there swings the wrong leaf.
                     */
                    val pairs = LinkedHashMap<Int, Pair<Int, Int>>()
                    val ambiguous = LinkedHashMap<Int, Int>()
                    val claims = HashMap<Int, MutableList<Int>>()

                    objects.keys.sorted().forEach { id ->
                        val def = objects.getValue(id)
                        val slot = def.options.indexOfFirst { it.equals("Open", ignoreCase = true) }
                        if (slot == -1 || def.name.isBlank()) {
                            return@forEach
                        }
                        val candidates =
                            listOf(id + 1, id - 1).filter { candidate ->
                                val other = objects[candidate] ?: return@filter false
                                other.name == def.name && other.options.getOrNull(slot).equals("Close", ignoreCase = true)
                            }
                        when (candidates.size) {
                            1 -> {
                                pairs[id] = candidates.single() to slot
                                claims.getOrPut(candidates.single()) { mutableListOf() }.add(id)
                            }
                            0 -> ambiguous[id] = slot
                            else -> ambiguous[id] = slot
                        }
                    }

                    var accepted = 0
                    pairs.forEach { (closed, pair) ->
                        val (opened, slot) = pair
                        val contenders = claims.getValue(opened)
                        if (contenders.size == 1) {
                            accepted++
                            println("DOOR_PAIR closed=$closed opened=$opened optionSlot=${slot + 1} name='${objects.getValue(closed).name}'")
                        } else {
                            println(
                                "DOOR_CONTESTED closed=$closed opened=$opened optionSlot=${slot + 1} " +
                                    "claimedBy=$contenders name='${objects.getValue(closed).name}'",
                            )
                        }
                    }
                    ambiguous.forEach { (id, slot) ->
                        println("DOOR_UNPAIRED id=$id optionSlot=${slot + 1} name='${objects.getValue(id).name}'")
                    }
                    println(
                        "DOORPAIRS SCANNED=${objects.size} PAIRED=$accepted " +
                            "CONTESTED=${pairs.size - accepted} UNPAIRED=${ambiguous.size}",
                    )
                }

                else ->
                    args.drop(1).map { it.toInt() }.forEach { id ->
                        val def = objects[id]
                        if (def == null) {
                            println("OBJECT_$id=ABSENT")
                        } else {
                            println("OBJECT_$id ${describe(def)}")
                        }
                    }
            }
        } finally {
            library.close()
        }
    }

    private fun describe(def: ObjectDef): String {
        val options =
            def.options
                .mapIndexed { i, option -> if (option.isNullOrBlank()) null else "${i + 1}:'$option'" }
                .filterNotNull()
                .joinToString(",")
        return "name='${def.name}' size=${def.width}x${def.length} blockwalk=${def.blockwalk} solid=${def.solid} " +
            "anim=${def.animation} options=[$options]"
    }
}
