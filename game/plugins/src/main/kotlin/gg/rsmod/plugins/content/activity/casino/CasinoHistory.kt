package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.persistNow
import org.apache.logging.log4j.LogManager

/** The four games, in the order they appear in the lobby. */
enum class CasinoGame(
    val displayName: String,
) {
    DICE("Dice"),
    MINES("Mines"),
    BLACKJACK("Blackjack"),
    FLOWER_POKER("Flower Poker"),
}

/**
 * One settled wager, with everything needed to replay it against the published verifier.
 *
 * [serverSeedHash] rather than the plaintext seed: the seed is still live when the round is recorded, so writing
 * the plaintext anywhere - even the house's own log - would hand anyone who reads that log the next result. The
 * plaintext is published exactly once, by [CasinoSeeds.rotate], and [CasinoHistory.logReveal] writes it then.
 */
data class CasinoRound(
    val game: CasinoGame,
    val stake: Long,
    val payout: Long,
    val clientSeed: String,
    val serverSeedHash: String,
    val nonce: Int,
    val detail: String,
    val timeMs: Long = System.currentTimeMillis(),
) {
    /** Net coins to the player; negative means the house won. */
    val profit: Long
        get() = payout - stake

    val won: Boolean
        get() = payout > 0
}

/**
 * Game history, in two places with two different jobs.
 *
 * Owner 2026-09-20: "Proper game history/logging".
 *
 *  - **The player's own recent rounds** persist on the account so "what did I just roll, and with which seeds?"
 *    survives a relog. Capped at [MAX_ENTRIES] so a heavy gambler cannot grow their save file without bound.
 *  - **The house audit trail** is a separate append-only log (`data/logs/casino/`) that no player can reach. It is
 *    what settles a dispute and what makes a payout bug visible after the fact.
 *
 * Entries serialise as tab-separated fields; every free-text field is sanitised on the way in, so a crafted client
 * seed cannot forge extra columns or inject newlines into the audit log.
 */
object CasinoHistory {
    const val MAX_ENTRIES = 20

    val ENTRIES = AttributeKey<MutableList<String>>(persistenceKey = "casino_history")

    private val logger = LogManager.getLogger("CasinoLogger")

    /** Records a settled round on the player and in the house audit trail. */
    fun record(
        player: Player,
        round: CasinoRound,
    ) {
        val entries = player.attr[ENTRIES] ?: mutableListOf<String>().also { player.attr[ENTRIES] = it }
        entries.add(0, encode(round))
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }
        logger.info(
            "\tsettle\tplayer=${clean(player.username)}\tgame=${round.game.name}\tstake=${round.stake}" +
                "\tpayout=${round.payout}\tprofit=${round.profit}\tclientSeed=${clean(round.clientSeed)}" +
                "\tserverSeedHash=${round.serverSeedHash}\tnonce=${round.nonce}\tdetail=${clean(round.detail)}",
        )
        // Audit E-07: every settled round (stake, payout, consumed nonce, this row) goes to disk now, not at the next
        // autosave, so a crash can never roll a finished round back to a state where its nonce is unused again.
        player.persistNow()
    }

    /**
     * Records the one moment a plaintext server seed becomes public. Everything the seed produced is already
     * settled, so this is the line that lets an outsider re-derive those rounds end to end.
     */
    fun logReveal(
        player: Player,
        revealed: CasinoSeeds.Revealed,
    ) {
        logger.info(
            "\treveal\tplayer=${clean(player.username)}\tserverSeed=${clean(revealed.serverSeed)}" +
                "\tserverSeedHash=${revealed.hash}\trounds=${revealed.rounds}",
        )
    }

    /** Records a stake movement that is not a settled round, e.g. a refund after a failed start. */
    fun logAdjustment(
        player: Player,
        game: CasinoGame,
        reason: String,
        amount: Long,
    ) {
        logger.info(
            "\tadjust\tplayer=${clean(player.username)}\tgame=${game.name}\treason=${clean(reason)}\tamount=$amount",
        )
    }

    /** The player's recent rounds, newest first. */
    fun recent(player: Player): List<CasinoRound> = player.attr[ENTRIES].orEmpty().mapNotNull(::decode)

    fun clear(player: Player) {
        player.attr[ENTRIES] = mutableListOf()
    }

    private fun encode(round: CasinoRound): String =
        listOf(
            round.game.name,
            round.stake.toString(),
            round.payout.toString(),
            clean(round.clientSeed),
            round.serverSeedHash,
            round.nonce.toString(),
            round.timeMs.toString(),
            clean(round.detail),
        ).joinToString("\t")

    private fun decode(raw: String): CasinoRound? {
        val parts = raw.split('\t')
        if (parts.size < 8) {
            return null
        }
        val game = CasinoGame.values().firstOrNull { it.name == parts[0] } ?: return null
        return CasinoRound(
            game = game,
            stake = parts[1].toLongOrNull() ?: return null,
            payout = parts[2].toLongOrNull() ?: return null,
            clientSeed = parts[3],
            serverSeedHash = parts[4],
            nonce = parts[5].toIntOrNull() ?: return null,
            timeMs = parts[6].toLongOrNull() ?: 0L,
            detail = parts[7],
        )
    }

    /** Strips the field and record separators so no input can forge a column or a log line. */
    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}
