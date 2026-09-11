package gg.rsmod.plugins.content.scrolls

/**
 * Registers the 41 real Treasure Trail steps ported from Novite (24 map/dig clues, 17 emote
 * clues) under every [ClueScrollTier] - see [MAP_CLUE_LOCATIONS]/[EMOTE_CLUE_LOCATIONS] and
 * [MapClueStep]/[EmoteClueStep] doc comments for why these are shared across all four tiers
 * rather than split (no per-tier classification exists in the donor).
 */
ClueScrollTier.values().forEach { tier ->
    MAP_CLUE_LOCATIONS.forEach { location ->
        ClueScrollManager.registerStep(MapClueStep(tier, location))
    }
    EMOTE_CLUE_LOCATIONS.forEach { location ->
        ClueScrollManager.registerStep(EmoteClueStep(tier, location))
    }
}
