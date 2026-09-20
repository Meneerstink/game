package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * Mines against the house.
 *
 * SOURCE: Roat Pkz wiki "Mines" - a 5x5 grid of 25 tiles, the player chooses 1-24 mines, reveals tiles one at a
 * time and may cash out at any moment; hitting a mine loses the whole wager. 98% RTP, so the multiplier after each
 * safe reveal is [CasinoOdds.minesMultiplier].
 *
 * The board is decided **before the first click**, by `(clientSeed, serverSeed, nonce, mineCount)`. That single
 * fact is what makes the game disconnect-proof: the mines are already where they are, so logging out mid-board,
 * pulling the plug on a bad click or replaying a packet cannot move them. A round therefore survives a relog
 * instead of being force-resolved, which would otherwise be either a free escape or a stolen stake.
 *
 * The stake is debited when the board is created, so an abandoned board costs exactly what it should: the stake.
 * Seeds are frozen for the duration ([CasinoSeeds] changes are refused while a board is live), because changing
 * them mid-board would redefine where the mines are.
 */
object MinesGame {
    val STAKE = AttributeKey<String>(persistenceKey = "casino_mines_stake")
    val MINE_COUNT = AttributeKey<String>(persistenceKey = "casino_mines_count")
    val NONCE = AttributeKey<String>(persistenceKey = "casino_mines_nonce")
    val REVEALED = AttributeKey<String>(persistenceKey = "casino_mines_revealed")
    val CLIENT_SEED = AttributeKey<String>(persistenceKey = "casino_mines_client_seed")
    val SERVER_SEED = AttributeKey<String>(persistenceKey = "casino_mines_server_seed")

    enum class Rejection(
        val message: String,
    ) {
        ALREADY_PLAYING("Finish or cash out your current mines board first."),
        BAD_MINE_COUNT(
            "Choose between ${ProvablyFairMines.MIN_MINES} and ${ProvablyFairMines.MAX_MINES} mines.",
        ),
        STAKE_TOO_SMALL("The minimum bet is ${CasinoWallet.MIN_WAGER} coins."),
        STAKE_TOO_LARGE("The maximum bet is ${CasinoWallet.MAX_WAGER} coins."),
        PAYOUT_TOO_LARGE("A full clear on that board would pay more coins than you can hold. Lower your bet."),
        NOT_ENOUGH_COINS("You do not have enough coins for that bet."),
    }

    /** A live board. */
    class Board(
        val stake: Long,
        val mineCount: Int,
        val nonce: Int,
        val clientSeed: String,
        val serverSeed: String,
        val revealed: MutableSet<Int>,
    ) {
        /** Where the mines are. Derived, never stored, so the board cannot drift from its seeds. */
        val mines: Set<Int> by lazy { ProvablyFairMines.generateLayout(clientSeed, serverSeed, nonce, mineCount) }

        val serverSeedHash: String by lazy { ProvablyFair.sha256Hex(serverSeed) }

        val safeCells: Int
            get() = ProvablyFairMines.SLOTS - mineCount

        val gems: Int
            get() = revealed.size

        val cleared: Boolean
            get() = gems >= safeCells

        fun multiplier(): Double = CasinoOdds.minesMultiplier(mineCount, gems)

        fun cashoutValue(): Long = CasinoOdds.minesPayout(stake, mineCount, gems)

        /** What the next safe reveal would be worth, for the interface's "next" figure. */
        fun nextMultiplier(): Double? = if (cleared) null else CasinoOdds.minesMultiplier(mineCount, gems + 1)
    }

    fun active(player: Player): Board? {
        val stake = player.attr[STAKE]?.toLongOrNull() ?: return null
        val mineCount = player.attr[MINE_COUNT]?.toIntOrNull() ?: return null
        val nonce = player.attr[NONCE]?.toIntOrNull() ?: return null
        val clientSeed = player.attr[CLIENT_SEED] ?: return null
        val serverSeed = player.attr[SERVER_SEED] ?: return null
        if (mineCount !in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            return null
        }
        val revealed =
            player.attr[REVEALED]
                .orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until ProvablyFairMines.SLOTS }
                .toMutableSet()
        return Board(stake, mineCount, nonce, clientSeed, serverSeed, revealed)
    }

    fun isPlaying(player: Player): Boolean = active(player) != null

    fun validate(
        player: Player,
        stake: Long,
        mineCount: Int,
    ): Rejection? {
        if (isPlaying(player)) {
            return Rejection.ALREADY_PLAYING
        }
        if (mineCount !in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            return Rejection.BAD_MINE_COUNT
        }
        if (stake < CasinoWallet.MIN_WAGER) {
            return Rejection.STAKE_TOO_SMALL
        }
        if (stake > CasinoWallet.MAX_WAGER) {
            return Rejection.STAKE_TOO_LARGE
        }
        val fullClear = CasinoOdds.minesPayout(stake, mineCount, ProvablyFairMines.SLOTS - mineCount)
        if (!CasinoWallet.fitsPayout(fullClear)) {
            return Rejection.PAYOUT_TOO_LARGE
        }
        if (CasinoWallet.balance(player) < stake) {
            return Rejection.NOT_ENOUGH_COINS
        }
        return null
    }

    /** Debits the stake and lays the board. Returns null when the bet was refused; nothing changes then. */
    fun start(
        player: Player,
        stake: Long,
        mineCount: Int,
    ): Board? {
        if (validate(player, stake, mineCount) != null) {
            return null
        }
        if (!CasinoWallet.withdraw(player, stake)) {
            return null
        }
        val clientSeed = CasinoSeeds.clientSeed(player)
        val serverSeed = CasinoSeeds.serverSeed(player)
        val nonce = CasinoSeeds.takeNonce(player)

        player.attr[STAKE] = stake.toString()
        player.attr[MINE_COUNT] = mineCount.toString()
        player.attr[NONCE] = nonce.toString()
        player.attr[CLIENT_SEED] = clientSeed
        player.attr[SERVER_SEED] = serverSeed
        player.attr[REVEALED] = ""
        return Board(stake, mineCount, nonce, clientSeed, serverSeed, mutableSetOf())
    }

    /** What one reveal did. */
    sealed class Reveal {
        /** A gem; the board is still live. */
        data class Gem(
            val cell: Int,
            val board: Board,
        ) : Reveal()

        /** A mine; the board is over and the stake is lost. */
        data class Boom(
            val cell: Int,
            val mines: Set<Int>,
            val stake: Long,
        ) : Reveal()

        /** The last safe cell; the board pays out automatically. */
        data class Cleared(
            val cell: Int,
            val mines: Set<Int>,
            val payout: Long,
            val credited: CasinoWallet.Payout,
        ) : Reveal()

        /** The click was not a legal move; nothing changed. */
        object Ignored : Reveal()
    }

    /**
     * Reveals [cell].
     *
     * Re-clicking a revealed cell, clicking off the grid, or clicking with no live board are all [Reveal.Ignored] -
     * the packet is simply not a move. That is the packet-abuse guard for this game: the only state a client can
     * drive is "which of the 25 cells", and every value outside the legal set is a no-op rather than an error the
     * board has to recover from.
     */
    fun reveal(
        player: Player,
        cell: Int,
    ): Reveal {
        val board = active(player) ?: return Reveal.Ignored
        if (cell !in 0 until ProvablyFairMines.SLOTS || cell in board.revealed) {
            return Reveal.Ignored
        }
        if (cell in board.mines) {
            val mines = board.mines
            val stake = board.stake
            clear(player)
            CasinoHistory.record(
                player,
                CasinoRound(
                    game = CasinoGame.MINES,
                    stake = stake,
                    payout = 0,
                    clientSeed = board.clientSeed,
                    serverSeedHash = board.serverSeedHash,
                    nonce = board.nonce,
                    detail = "mines=${board.mineCount} gems=${board.gems} hit=$cell",
                ),
            )
            return Reveal.Boom(cell, mines, stake)
        }

        board.revealed += cell
        player.attr[REVEALED] = board.revealed.sorted().joinToString(",")

        if (!board.cleared) {
            return Reveal.Gem(cell, board)
        }

        // Every safe cell is open: the board can only pay out now, so settle it rather than making the player
        // click "cash out" on a board with nothing left to reveal.
        val payout = board.cashoutValue()
        val mines = board.mines
        clear(player)
        val credited = CasinoWallet.deposit(player, payout)
        CasinoHistory.record(
            player,
            CasinoRound(
                game = CasinoGame.MINES,
                stake = board.stake,
                payout = payout,
                clientSeed = board.clientSeed,
                serverSeedHash = board.serverSeedHash,
                nonce = board.nonce,
                detail = "mines=${board.mineCount} gems=${board.gems} cleared",
            ),
        )
        return Reveal.Cleared(cell, mines, payout, credited)
    }

    /** Cashing out. Null when there is nothing to cash out, or nothing has been revealed yet. */
    data class Cashout(
        val payout: Long,
        val gems: Int,
        val mines: Set<Int>,
        val credited: CasinoWallet.Payout,
    )

    fun cashout(player: Player): Cashout? {
        val board = active(player) ?: return null
        if (board.gems == 0) {
            // Cashing out at 0 gems would return 98% of the stake, i.e. a guaranteed 2% loss for nothing. Refuse
            // it: the player has not played a move yet.
            return null
        }
        val payout = board.cashoutValue()
        val mines = board.mines
        val gems = board.gems
        clear(player)
        val credited = CasinoWallet.deposit(player, payout)
        CasinoHistory.record(
            player,
            CasinoRound(
                game = CasinoGame.MINES,
                stake = board.stake,
                payout = payout,
                clientSeed = board.clientSeed,
                serverSeedHash = board.serverSeedHash,
                nonce = board.nonce,
                detail = "mines=${board.mineCount} gems=$gems cashout",
            ),
        )
        return Cashout(payout, gems, mines, credited)
    }

    private fun clear(player: Player) {
        player.attr.remove(STAKE)
        player.attr.remove(MINE_COUNT)
        player.attr.remove(NONCE)
        player.attr.remove(REVEALED)
        player.attr.remove(CLIENT_SEED)
        player.attr.remove(SERVER_SEED)
    }
}
