package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef

/**
 * Read-only diagnostic: dumps the full generic opcode-249 params map ([ItemDef.params], every
 * `paramId -> value` pair regardless of whether this project's code currently attaches any known
 * meaning to that id) for one or more item ids, loaded through the same production
 * [DefinitionSet]/[ItemDef] path the server itself boots with - never a second parallel decoder.
 *
 * Built for the 2026-09-04 owner-approved unattended run's Twisted Bow special-attack-bar
 * reopening (`RSPS_CURRENT_SPRINT.json` FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE): comparing this
 * output for items 841 (Shortbow), 861 (Magic shortbow) and 22326 (Twisted bow) is what proved
 * item param 687 - not weaponType, not param 686 - is the one that differs between the three and is
 * read directly by CS2 script 1136 (interface 884 component 2) to show/hide the special-attack-bar
 * layer (component 884:19), independently of the server's [SpecialAttacks.hasSpecialAttack] map.
 *
 * Usage: `./gradlew :game:runItemParamProbeTool --args="<cachePath> <itemId> [itemId ...]"`
 *        `./gradlew :game:runItemParamProbeTool --args="<cachePath> menu <substring>"`
 *
 * The `menu` mode lists every item whose inventory or worn option list contains the given
 * substring. It exists because option-driven content ("Dismantle", "Check-charges", ...) has to be
 * bound to exactly the set of items the cache actually gives that option to - a hand-written id list
 * silently misses variants.
 */
object ItemParamProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <itemId> [itemId ...] | <cachePath> menu <substring>" }
        val cachePath = args[0]

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ItemDef::class.java)
            @Suppress("UNCHECKED_CAST")
            val items = definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>

            if (args[1] == "menu") {
                require(args.size == 3) { "Usage: <cachePath> menu <substring>" }
                val needle = args[2].lowercase()
                var hits = 0
                items.keys.sorted().forEach { id ->
                    val def = items.getValue(id)
                    val inv = def.inventoryMenu.indexOfFirst { it?.lowercase()?.contains(needle) == true }
                    val worn = def.equipmentMenu.indexOfFirst { it?.lowercase()?.contains(needle) == true }
                    if (inv != -1 || worn != -1) {
                        hits++
                        println("ITEM_$id name=${def.name} invOption=${if (inv == -1) -1 else inv + 1} wornOption=${if (worn == -1) -1 else worn + 1}")
                    }
                }
                println("MENU_SEARCH='$needle' SCANNED=${items.size} HITS=$hits")
                return
            }

            val itemIds = args.drop(1).map { it.toInt() }
            for (id in itemIds) {
                val def = items[id]
                if (def == null) {
                    println("ITEM_$id=ABSENT")
                    continue
                }
                println(
                    "ITEM_$id name=${def.name} weaponType=${def.weaponType} " +
                        "manwear=${def.maleWornModel} womanwear=${def.maleWornModel2}",
                )
                println("  INV_MENU=${def.inventoryMenu.toList()}")
                println("  EQUIP_MENU=${def.equipmentMenu.toList()}")
                val paramIds = def.params.keys.toIntArray().sortedArray()
                if (paramIds.isEmpty()) {
                    println("  PARAMS=NONE")
                } else {
                    for (paramId in paramIds) {
                        println("  PARAM_$paramId=${def.params.get(paramId)}")
                    }
                }
            }
        } finally {
            library.close()
        }
    }
}
