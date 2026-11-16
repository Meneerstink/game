package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import kotlin.random.Random

/** Pinned Puzzle box/Guide and cache306/688..692. Boxes supplement the held clue. */
object TreasureTrailPuzzleBoxes {
    data class Source(val key: String, val item: Int, val picture: Int)
    // Wiki item identities + cache gameval symbols, recorded in PUZZLE-SOURCE-AUDIT.md.
    val sources = listOf(
        Source("Clue scroll (hard) - BLUE GRIM GUIDED", 19887, 2),
        Source("Clue scroll (hard) - IWPPLQTP", 23173, 2),
    )
    val pictures = listOf(1, 2, 3, 4, 5, 6, 9)
    const val CLICK_FIRST = 6
    const val CLOSE = 5
    const val MODEL_FIRST = 31
    const val STRIDE = 50
    class Session(val contract: ClueStepContract, val box: Item, val source: Source, val board: TreasureTrailSlidingPuzzle) {
        fun valid(player: Player): Boolean = contract.valid(player) && player.inventory.any { it === box }
    }
    private val sessions = HashMap<Player, Session>()
    fun session(player: Player): Session? = sessions[player]
    fun source(step: TreasureTrailSourceStep): Source? = sources.singleOrNull { it.key == step.source.key }
    fun tier(item: Int): ClueScrollTier? = if (sources.any { ClueItems.local(it.item) == item }) ClueScrollTier.HARD else null

    fun available(world: World, row: TreasureTrailStepCatalogue.Step): Boolean =
        row.challenge == TreasureTrailStepCatalogue.Challenge.SLIDING_PUZZLE && ClueInterfaces.of(306) != null &&
            TreasureTrailPuzzleDialogue.npcs(row).any { id -> world.definitions.getNullable(NpcDef::class.java, id)?.let { npc ->
                npc.name.equals(row.npcName, true) && npc.options.any { it.equals("Talk-to", true) }
            } == true } &&
            sources.singleOrNull { it.key == row.key }?.item?.let(ClueItems::local)?.let {
                world.definitions.getNullable(ItemDef::class.java, it)?.let { box ->
                    box.name == "Puzzle box (hard)" && box.inventoryMenu.any { op -> op.equals("Open", true) }
                }
            } == true

    /** Repeated dialogue cannot issue another held/stored box; no inventory space spends nothing. */
    fun issue(player: Player, step: TreasureTrailSourceStep, random: Random = Random.Default): Item? {
        val source = source(step) ?: return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        val id = ClueItems.local(source.item) ?: return null
        if (player.world.definitions.getNullable(ItemDef::class.java, id) == null) return null
        player.inventory.firstOrNull { it?.id == id }?.let { return it }
        if (ClueOwnership.stored(player).any { it.id == id }) return null
        val slot = player.inventory.rawItems.indexOfFirst { it == null }
        if (slot < 0 || !contract.valid(player)) return null
        val box = Item(id).putAttr(ItemAttribute.CLUE_TASK_KEY, step.persistenceId)
        player.inventory[slot] = box
        // Initial scramble is always 101 legal slides. Opening a discarded unsolved board re-scrambles.
        sessions[player] = Session(contract, box, source, TreasureTrailSlidingPuzzle.create(random))
        return box
    }

    fun start(player: Player, step: TreasureTrailSourceStep, box: Item, random: Random = Random.Default): Session? {
        val source = source(step) ?: return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        if (box.id != ClueItems.local(source.item) || player.inventory.none { it === box }) return null
        val previous = sessions[player]
        if (previous != null && previous.box === box && previous.valid(player)) return previous
        val board = if (box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE) == 1)
            TreasureTrailSlidingPuzzle.restore(IntArray(25) { (it + 1) % 25 })
        else TreasureTrailSlidingPuzzle.create(random)
        return Session(contract, box, source, board).also { sessions[player] = it }
    }

    fun open(player: Player, box: Item): Boolean {
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.HARD) as? TreasureTrailSourceStep ?: return false
        val iface = ClueInterfaces.of(306) ?: return false
        if (player.interfaces.isVisible(iface)) close(player)
        val session = start(player, step, box) ?: return false
        // An old interface's close callback must not discard the newly installed board.
        sessions.remove(player)
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN_FULL)
        sessions[player] = session
        player.setInterfaceEvents(iface, CLOSE, -1..-1, 0x2)
        repeat(25) { player.setInterfaceEvents(iface, CLICK_FIRST + it, -1..-1, 0x2) }
        render(player, session, iface)
        return true
    }

    fun move(player: Player, session: Session, cell: Int): Boolean {
        if (sessions[player] !== session || !session.valid(player) || !session.board.move(cell)) return false
        session.box.removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
        player.inventory.dirty = true
        return true
    }

    fun click(player: Player, cell: Int): Boolean {
        val iface = ClueInterfaces.of(306) ?: return false
        if (!player.interfaces.isVisible(iface)) return false
        val session = sessions[player] ?: return false
        if (!session.valid(player)) { remove(player); return false }
        val moved = move(player, session, cell)
        player.playSound(if (moved) 1859 else 2304)
        if (moved) render(player, session, iface)
        return moved
    }

    private fun render(player: Player, session: Session, iface: Int) {
        repeat(168) { player.setComponentHidden(iface, MODEL_FIRST + it, true) }
        val picture = pictures.indexOf(session.source.picture)
        check(picture >= 0)
        session.board.tiles().forEachIndexed { cell, piece ->
            if (piece != 0) {
                val component = MODEL_FIRST + picture * 24 + piece - 1
                player.setComponentPosition(iface, component, cell % 5 * STRIDE, cell / 5 * STRIDE)
                player.setComponentHidden(iface, component, false)
            }
        }
    }

    /** Solving never advances a clue. Close records the result, then the issuing NPC must receive it. */
    fun close(player: Player) {
        sessions.remove(player)?.let { session ->
            if (session.valid(player) && session.board.solved) {
                session.box.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1)
                player.inventory.dirty = true
            }
        }
    }
    fun remove(player: Player) { sessions.remove(player) }
    fun handIn(player: Player, step: TreasureTrailSourceStep, box: Item): Boolean {
        val source = source(step) ?: return false
        val contract = ClueStepContract.capture(player, step) ?: return false
        if (box.id != ClueItems.local(source.item) || box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE) != 1 ||
            player.inventory.none { it === box }) return false
        // The core transaction consumes the first matching auxiliary item. Bind that exact object as well.
        if (player.inventory.firstOrNull { it?.id == box.id } !== box) return false
        return contract.advance(player, listOf(box.id)).also { if (it) remove(player) }
    }
}
