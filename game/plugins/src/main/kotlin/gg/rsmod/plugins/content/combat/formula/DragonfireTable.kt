package gg.rsmod.plugins.content.combat.formula

/**
 * RCV-012 owner decision "dragonfire = OSRS model": the maximum dragonfire damage for every protection combination, taken verbatim
 * from the OSRS Wiki "Dragonfire" page, section Damage reduction (2026-09-14).
 *
 * - Chromatic: "Chromatic dragons, brutal dragons, lava dragons, frost dragons, and reanimated dragons all share the same dragonfire
 *   mechanics." Metallic dragons, Drakes and Galvek "are unaffected by Protect from Magic" (their table has no prayer rows).
 * - Where a row gives two values, the higher one applies when the player fails the Magic accuracy roll ("You're horribly burnt by the
 *   dragon fire!") and the lower one when the player wins it ("You manage to resist some of the dragon fire!").
 * - King Black Dragon: fiery breath and the icy / toxic / shocking breaths each have one value per row (no accuracy split).
 */
object DragonfireTable {
    enum class Type { CHROMATIC, METALLIC, KING_BLACK_DRAGON_FIERY, KING_BLACK_DRAGON_SPECIAL }

    enum class Potion { NONE, ANTIFIRE, SUPER_ANTIFIRE }

    const val BURNT_MESSAGE = "You're horribly burnt by the dragon fire!"
    const val RESIST_MESSAGE = "You manage to resist some of the dragon fire!"

    /** One wiki row: the max hit when the player fails the accuracy roll and when they win it (equal when the row has one value). */
    data class Max(val failed: Int, val won: Int) {
        val splitByAccuracy: Boolean get() = failed != won
    }

    private data class Key(val shield: Boolean, val prayer: Boolean, val potion: Potion)

    private fun row(
        shield: Boolean,
        prayer: Boolean,
        potion: Potion,
        failed: Int,
        won: Int = failed,
    ) = Key(shield, prayer, potion) to Max(failed, won)

    private val CHROMATIC =
        mapOf(
            row(shield = false, prayer = false, potion = Potion.NONE, failed = 50, won = 30),
            row(shield = true, prayer = false, potion = Potion.NONE, failed = 5),
            row(shield = false, prayer = true, potion = Potion.NONE, failed = 10),
            row(shield = true, prayer = true, potion = Potion.NONE, failed = 5),
            row(shield = false, prayer = false, potion = Potion.ANTIFIRE, failed = 35, won = 15),
            row(shield = false, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = false, prayer = true, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = false, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = true, prayer = true, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = true, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 0),
        )

    /** Metallic table (no Protect from Magic rows: the prayer does not change the result). */
    private val METALLIC =
        mapOf(
            row(shield = false, prayer = false, potion = Potion.NONE, failed = 50, won = 30),
            row(shield = true, prayer = false, potion = Potion.NONE, failed = 5),
            row(shield = false, prayer = false, potion = Potion.ANTIFIRE, failed = 35, won = 15),
            row(shield = false, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
        )

    private val KBD_FIERY =
        mapOf(
            row(shield = false, prayer = false, potion = Potion.NONE, failed = 65),
            row(shield = true, prayer = false, potion = Potion.NONE, failed = 15),
            row(shield = false, prayer = true, potion = Potion.NONE, failed = 20),
            row(shield = true, prayer = true, potion = Potion.NONE, failed = 15),
            row(shield = false, prayer = false, potion = Potion.ANTIFIRE, failed = 50),
            row(shield = false, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = true, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = false, prayer = true, potion = Potion.ANTIFIRE, failed = 5),
            row(shield = false, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 0),
            row(shield = true, prayer = true, potion = Potion.ANTIFIRE, failed = 0),
            row(shield = true, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 0),
        )

    private val KBD_SPECIAL =
        mapOf(
            row(shield = false, prayer = false, potion = Potion.NONE, failed = 50),
            row(shield = true, prayer = false, potion = Potion.NONE, failed = 10),
            row(shield = false, prayer = true, potion = Potion.NONE, failed = 15),
            row(shield = true, prayer = true, potion = Potion.NONE, failed = 10),
            row(shield = false, prayer = false, potion = Potion.ANTIFIRE, failed = 50),
            row(shield = false, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 50),
            row(shield = true, prayer = false, potion = Potion.ANTIFIRE, failed = 10),
            row(shield = true, prayer = false, potion = Potion.SUPER_ANTIFIRE, failed = 10),
            row(shield = false, prayer = true, potion = Potion.ANTIFIRE, failed = 15),
            row(shield = false, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 15),
            row(shield = true, prayer = true, potion = Potion.ANTIFIRE, failed = 10),
            row(shield = true, prayer = true, potion = Potion.SUPER_ANTIFIRE, failed = 10),
        )

    fun max(
        type: Type,
        shield: Boolean,
        prayer: Boolean,
        potion: Potion,
    ): Max =
        when (type) {
            Type.CHROMATIC -> CHROMATIC.getValue(Key(shield, prayer, potion))
            Type.METALLIC -> METALLIC.getValue(Key(shield, false, potion))
            Type.KING_BLACK_DRAGON_FIERY -> KBD_FIERY.getValue(Key(shield, prayer, potion))
            Type.KING_BLACK_DRAGON_SPECIAL -> KBD_SPECIAL.getValue(Key(shield, prayer, potion))
        }

    /** Metallic dragons by their combat definition (the Void sections the npc attack table was generated from). */
    val METALLIC_COMBAT_DEFS = setOf("bronze_dragon", "iron_dragon", "steel_dragon", "mithril_dragon")

    /** The King Black Dragon's icy / toxic / shocking breaths (npc-attacks.json attack ids). */
    val KBD_SPECIAL_ATTACKS = setOf("toxic", "ice", "shock")

    const val FROST_DRAGON_COMBAT_DEF = "frost_dragon"

    /** Owner answer Q13 (2026-09-14): a frost dragon's dragonfire freezes like the King Black Dragon's ice breath (npc-attacks.json 10). */
    const val FROST_DRAGON_FREEZE_TICKS = 10

    /**
     * OSRS Wiki "Dragonfire": "Frost dragons use plain dragonfire like common chromatic dragons, except that this dragonfire can also
     * freeze players. The freezing effect can be blocked with an anti-dragon shield or its variants combined with an antifire potion, or
     * with a super antifire potion alone." Other freezing attacks have no sourced block here.
     */
    fun blocksFreeze(
        combatDef: String,
        protection: DragonfireFormula.Protection,
    ): Boolean =
        combatDef == FROST_DRAGON_COMBAT_DEF &&
            (protection.potion == Potion.SUPER_ANTIFIRE || (protection.shield && protection.potion == Potion.ANTIFIRE))

    /** The dragonfire type of an npc attack from the data-driven attack table. */
    fun typeFor(
        combatDef: String,
        attackId: String,
    ): Type =
        when {
            combatDef == "king_black_dragon" && attackId in KBD_SPECIAL_ATTACKS -> Type.KING_BLACK_DRAGON_SPECIAL
            combatDef == "king_black_dragon" -> Type.KING_BLACK_DRAGON_FIERY
            combatDef in METALLIC_COMBAT_DEFS -> Type.METALLIC
            else -> Type.CHROMATIC
        }
}
