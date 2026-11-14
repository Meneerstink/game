package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * N415. OSRS Wiki Saradomin Strike / Claws of Guthix / Flames of Zamorak: initially usable only against
 * the arena battle mages;100 casts per spell, including splashes. Counters record casts, not hits or kills.
 * Transcript:Mage Arena II supplies the progress wording and thresholds. No old unrestricted cast is
 * treated as proof of training, except the owner's explicit2B grandfathering of old MA2 completion and its rights.
 * The existing MA1 completion flag and existing owned items are preserved.
 */
object GodSpellTraining {
    data class Training(val spell: CombatSpell, val word: String, val key: AttributeKey<Int>)

    val SPELLS = listOf(
        Training(CombatSpell.SARADOMIN_STRIKE, "strike", AttributeKey(persistenceKey = "mage_arena_saradomin_casts")),
        Training(CombatSpell.CLAWS_OF_GUTHIX, "claw", AttributeKey(persistenceKey = "mage_arena_guthix_casts")),
        Training(CombatSpell.FLAMES_OF_ZAMORAK, "flame", AttributeKey(persistenceKey = "mage_arena_zamorak_casts")),
    )
    val BATTLE_MAGES = setOf(Npcs.BATTLE_MAGE, Npcs.BATTLE_MAGE_913, Npcs.BATTLE_MAGE_914)
    const val REQUIRED_CASTS = 100
    // The arena, its enclosed battle-mage spawns and both lever landings are in source square48_61.
    // A battle mage outside this square (e.g. an admin spawn) cannot train a spell.
    const val ARENA_SQUARE = 12349

    fun casts(player: Player, training: Training): Int = if (MageArenaII.legacyCompleted(player)) REQUIRED_CASTS
        else (player.attr[training.key] ?: 0).coerceIn(0, REQUIRED_CASTS)
    fun mastered(player: Player): Boolean = SPELLS.all { casts(player, it) == REQUIRED_CASTS }

    private fun arenaTile(tile: Tile): Boolean = tile.height == 0 && tile.regionId == ARENA_SQUARE

    fun trainingTarget(player: Player, target: Pawn): Boolean =
        MageArena.completed(player) && target is Npc && target.id in BATTLE_MAGES &&
            arenaTile(player.tile) && arenaTile(target.tile)

    /** Called by the common manual/autocast validation BEFORE any runes, animation or XP. */
    fun canCast(player: Player, target: Pawn, spell: CombatSpell): Boolean {
        val training = SPELLS.firstOrNull { it.spell == spell } ?: return true
        if (casts(player, training) == REQUIRED_CASTS || trainingTarget(player, target)) return true
        player.message("You still need to train with the ${training.word} spell inside the arena before you can use it outside.")
        return false
    }

    /** Only the shared successful-launch path calls this; neither an attempted cast nor its eventual damage counts. */
    fun cast(player: Player, target: Pawn, spell: CombatSpell) {
        val training = SPELLS.firstOrNull { it.spell == spell } ?: return
        if (!trainingTarget(player, target)) return
        val before = casts(player, training)
        if (before == REQUIRED_CASTS) return
        player.attr[training.key] = before + 1
        if (before + 1 == REQUIRED_CASTS) player.message(progress(player, training))
    }

    fun progress(player: Player, training: Training): String = when (casts(player, training)) {
        in 100..Int.MAX_VALUE -> "You're experienced enough to use the ${training.word} spell anywhere."
        in 90..99 -> "You are almost ready to use the ${training.word} spell outside of the arena."
        in 51..89 -> "You have completed over half of the training needed to use the ${training.word} spell outside of the arena."
        else -> "You still need to train with the ${training.word} spell inside the arena before you can use it outside."
    }
}
