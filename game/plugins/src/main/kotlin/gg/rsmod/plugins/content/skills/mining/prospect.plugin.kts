package gg.rsmod.plugins.content.skills.mining

/*
 * "Prospect" on rocks: offered by the 667 cache on most rocks, bound only in the Living Rock Caverns. One object FALLBACK (a bound
 * Prospect always wins), text and timing from the 667 Void donor (content/skill/mining/Mining.kt objectApproach("Prospect")).
 */
val PROSPECT_DEPLETED: Set<Int> =
    RockType.objects.map { world.definitions.get(ObjectDef::class.java, it).depleted }.filter { it != -1 }.toSet()

world.plugins.bindObjectFallback { player, obj, opt ->
    val id = obj.getTransform(player)
    val def = player.world.definitions.get(ObjectDef::class.java, id)
    if (!def.options.getOrNull(opt - 1).equals("prospect", ignoreCase = true)) return@bindObjectFallback false
    if (id in PROSPECT_DEPLETED) {
        player.message("There is currently no ore available in this rock.")
        return@bindObjectFallback true
    }
    val rock = RockType.values.firstOrNull { id in it.objectIds }
    player.queue {
        player.message("You examine the rock for ores...")
        wait(4)
        if (rock == null) {
            player.message("This rock contains no ore.")
        } else {
            val ore = player.world.definitions.get(ItemDef::class.java, rock.products.firstOrNull()?.item ?: rock.reward).name.lowercase()
            player.message("This rock contains $ore.")
        }
    }
    true
}
