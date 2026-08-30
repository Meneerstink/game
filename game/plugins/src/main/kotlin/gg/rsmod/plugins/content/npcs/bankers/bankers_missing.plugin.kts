package gg.rsmod.plugins.content.npcs.bankers

import gg.rsmod.plugins.content.inter.bank.openBank

/**
 * R04.5: 14 real, live banker-type npcs whose real cache "Bank" option was confirmed dead
 * (present in the cache, never bound) via this session's fresh `NpcCensus` boot output
 * (`npc_inventory.csv`) - not a guess, the census reads each npc's real `options` field
 * directly from the loaded cache. None of these ids are covered by the existing named
 * `bankers` list in `bankers.plugin.kts` (different named/regional banker npcs entirely -
 * Nardah's banker here is id 3046, distinct from the `BANKER_NARDAH_*` ids already bound
 * there). Reuses the exact same `player.openBank()` pattern and the existing shared `chat()`
 * dialogue in this package - zero new logic, only real ids wired.
 *
 * Their other dead option, "Collect", is deliberately left unbound: for these bankers it most
 * likely maps to the not-yet-built Grand Exchange collection box (same gap the existing
 * `bankers.plugin.kts` chat tree already honestly reports as "not implemented yet" for its
 * covered bankers) - binding it here would be guessing behaviour, not reusing verified logic.
 *
 * "talk-to" is deliberately NOT rebound here: the census confirms it's already bound (the
 * codebase's generic Talk-to fallback, per the prior session's R04.5 pass) - binding it again
 * would double-register the same npc+option and crash the boot
 * (`PluginRepository.bindNpc`'s duplicate-binding check).
 */
private val missingBankers =
    listOf(
        496, // Banker
        499, // Banker
        958, // Fadli
        1702, // Ghost banker
        2163, // Banker
        2164, // Banker
        3046, // Nardah Banker
        3824, // Arnold Lydspor
        4296, // Jade
        4519, // Sirsal Banker
        5383, // Odovacar
        5488, // Magnus Gram
        6362, // Eniola
        13455, // Ashuelot Reis
    )

missingBankers.forEach {
    on_npc_option(it, option = "Bank", lineOfSightDistance = 2) {
        player.openBank()
    }
}
