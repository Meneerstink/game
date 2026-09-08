package gg.rsmod.plugins.content.skills.fishing


/*
 * Every (spot npc, tool option) pair that has already been bound. FishingSpot's lists repeat the
 * same npc more than once (e.g. FISHING_SPOT_NET_HARPOON is listed twice) and the same npc can
 * appear under two groups, and the engine refuses a second binding for a npc option - which took
 * the whole server down at boot with "Npc is already bound to a plugin".
 */
val boundSpotOptions = mutableSetOf<Pair<Int, String>>()

FishingSpot.values().forEach { spot ->
    spot.objectIds.forEach { spotId ->
        /*
         * FishingSpot groups spot npcs by the tools they normally offer, but a few npcs in this
         * cache carry only part of their group's options - npc 233 has [Bait] and no "Net", for
         * example. on_npc_option throws on an option the npc does not have, which is the other way
         * this used to take the server down, so an unsupported pair is skipped instead.
         */
        val options = world.definitions.get(NpcDef::class.java, spotId).options
        spot.tools.forEach { tool ->
            if (options.none { it?.lowercase() == tool.option.lowercase() }) {
                return@forEach
            }
            if (!boundSpotOptions.add(spotId to tool.option.lowercase())) {
                return@forEach
            }
            on_npc_option(spotId, tool.option) {
                val fishingSpot = player.getInteractingNpc()
                player.queue {
                    var attempts = 0
                    // If the spot is a Rocktail shoal, check all four tiles
                    val isRocktailShoal = fishingSpot.id == Npcs.ROCKTAIL_SHOAL
                    val tilesToCheck =
                        if (isRocktailShoal) {
                            listOf(
                                fishingSpot.tile, // Assuming this is the bottom-left tile
                                fishingSpot.tile.transform(1, 0), // Bottom-right tile
                                fishingSpot.tile.transform(0, 1), // Top-left tile
                                fishingSpot.tile.transform(1, 1), // Top-right tile
                            )
                        } else {
                            listOf(fishingSpot.tile) // For other spots, just check the NPC's tile
                        }

                    while (tilesToCheck.none { player.tile.getDistance(it) <= 1 }) {
                        if (attempts++ >= 10) {
                            player.message(Entity.YOU_CANT_REACH_THAT)
                            return@queue
                        }
                        wait(1)
                    }
                    Fishing.fish(this, fishingSpot, tool)
                }
            }
        }
    }
}
