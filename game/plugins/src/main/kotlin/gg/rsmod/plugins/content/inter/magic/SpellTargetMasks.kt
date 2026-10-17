package gg.rsmod.plugins.content.inter.magic

import gg.rsmod.plugins.content.magic.SpellbookData

/**
 * N76 (owner 2026-10-16): spells whose 667 baked target mask offers a target kind the server never accepts.
 *
 * The 667 cache bakes Tele Block (192:86, events 0x5400) and the player-only utility spells (0x5000) with the target mask
 * NPC + player (bits 11..17 of the events: TGT_NPC 0x02, TGT_PLAYER 0x08 - the client's TargetMask). The client's
 * MiniMenu.addNpcEntries then offers "Cast -> <npc>" for every NPC under the cursor, and among the target entries the last
 * picked entity is on top, so a familiar, pet or any NPC on the opponent's tile takes the left-click; the server refuses that
 * cast and the spell is lost. OSRS Wiki Tele Block, 17 October 2019: "The Teleblock spell no longer gives options for casting it
 * on NPCs." Donors (Darkan CombatSpell.kt:326 TELEPORT_BLOCK, Void TeleportBlock.kt:46, Novite PlayerCombat.java:480) only
 * refuse the NPC cast on the server and keep the misclick; the OSRS behaviour is ported here.
 *
 * Every spell of the same kind gets the same correction - the target bit the server has no handler for is cleared, every other
 * bit of the baked mask kept: Tele Block (the combat engine refuses non-players, MagicCombatStrategy), Teleother Lumbridge /
 * Falador / Camelot (standard_utility_spells: on_spell_on_player only), Cure Other, Energy Transfer, Stat Spy (OSRS Wiki: "cast on
 * other players"; monsters use Monster Examine), Vengeance Other, Heal Other (OSRS Wiki: "another player") - lunar_spells binds
 * players only - and Monster Examine, which lunar_spells binds to NPCs only (OSRS Wiki: cast on monsters).
 *
 * Sent with the legacy Autocast strip in magic_tab.plugin.kts, one cycle after every open of the book (the client reloads the
 * baked masks on open: ROOT_CAUSES 143), so a book switch, relog or gameframe rebuild re-arms them. SpellTargetMaskTests checks
 * every targeted component of 192/193/430 in the real cache against the server's own cast handlers.
 */
object SpellTargetMasks {
    const val TGT_NPC = 0x02
    const val TGT_PLAYER = 0x08
    private const val TARGET_SHIFT = 11

    /** The 667 baked events of every corrected component (decoded from the cache; SpellTargetMaskTests pins them). */
    private val PLAYER_ONLY: Map<SpellbookData, Int> =
        mapOf(
            SpellbookData.TELEPORT_BLOCK to 0x005400,
            SpellbookData.TELEOTHER_LUMBRIDGE to 0x005000,
            SpellbookData.TELEOTHER_FALADOR to 0x005000,
            SpellbookData.TELEOTHER_CAMELOT to 0x005000,
            SpellbookData.CURE_OTHER to 0x005000,
            SpellbookData.ENERGY_TRANSFER to 0x005000,
            SpellbookData.STAT_SPY to 0x005000,
            SpellbookData.VENGEANCE_OTHER to 0x005000,
            SpellbookData.HEAL_OTHER to 0x005000,
        )

    private val NPC_ONLY: Map<SpellbookData, Int> =
        mapOf(
            SpellbookData.MONSTER_EXAMINE to 0x005000,
        )

    /** The baked 667 events of each corrected spell, keyed by spell. */
    val BAKED: Map<SpellbookData, Int> = PLAYER_ONLY + NPC_ONLY

    private fun without(
        events: Int,
        target: Int,
    ): Int = events and (target shl TARGET_SHIFT).inv()

    /** Component -> events to send for [book] (192 standard, 193 Ancient, 430 Lunar); empty for a book without corrections. */
    fun overrides(book: Int): List<Pair<Int, Int>> =
        PLAYER_ONLY.filterKeys { it.interfaceId == book }.map { (spell, baked) -> spell.component to without(baked, TGT_NPC) } +
            NPC_ONLY.filterKeys { it.interfaceId == book }.map { (spell, baked) -> spell.component to without(baked, TGT_PLAYER) }
}
