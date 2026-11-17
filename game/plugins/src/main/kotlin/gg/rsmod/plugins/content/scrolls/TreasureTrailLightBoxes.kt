package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.RspsPaths
import java.io.File
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.*
import kotlin.random.Random

/** A solved light box is handed back to its issuing NPC; solving itself never advances the clue. */
object TreasureTrailLightBoxes {
    const val SOURCE_ITEM = 20355
    const val SOURCE_INTERFACE = 322
    private val mapping by lazy {
        val file = File(RspsPaths.data("cfg/clue/lightbox.tsv"))
        if (!file.isFile) emptyMap() else {
            val lines = file.readLines()
            require(lines.first().endsWith("applied=true"))
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.associate {
                val fields = it.split('\t'); (fields[0] to fields[1].toInt()) to fields[2].toInt()
            }
        }
    }
    fun iface(): Int? = mapping["interface" to SOURCE_INTERFACE]
    fun control(): Int? = mapping["control" to 1356]
    const val CLOSE = 35
    val buttons = (8..15).toList()
    private val MASKS = listOf(ItemAttribute.CLUE_LIGHT_A, ItemAttribute.CLUE_LIGHT_B,
        ItemAttribute.CLUE_LIGHT_C, ItemAttribute.CLUE_LIGHT_D, ItemAttribute.CLUE_LIGHT_E,
        ItemAttribute.CLUE_LIGHT_F, ItemAttribute.CLUE_LIGHT_G, ItemAttribute.CLUE_LIGHT_H)
    class Session(val contract: ClueStepContract, val box: Item, val board: TreasureTrailLightPuzzle) {
        fun valid(player: Player): Boolean = contract.valid(player) && player.inventory.any { it === box }
    }
    private val sessions = HashMap<Player, Session>()
    fun session(player: Player): Session? = sessions[player]
    fun supports(row: TreasureTrailStepCatalogue.Step): Boolean = row.tier == ClueScrollTier.MASTER &&
        row.action == TreasureTrailStepCatalogue.Action.TALK &&
        (row.challenge == TreasureTrailStepCatalogue.Challenge.LIGHT_BOX || TreasureTrailStepCatalogue.Challenge.LIGHT_BOX in row.puzzles)
    fun available(world: World): Boolean = iface() != null && control() != null &&
        ClueItems.local(SOURCE_ITEM)?.let { id -> world.definitions.getNullable(ItemDef::class.java, id)?.let {
            it.name == "Light box" && it.inventoryMenu.any { op -> op.equals("Open", true) }
        } } == true
    private fun write(box: Item, board: TreasureTrailLightPuzzle) {
        box.putAttr(ItemAttribute.CLUE_LIGHT_BITS, board.bits)
        board.effects().forEachIndexed { index, bits -> box.putAttr(MASKS[index], bits) }
        if (board.solved) box.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1) else box.removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
    }
    fun restore(box: Item): TreasureTrailLightPuzzle? {
        val bits = box.getAttr(ItemAttribute.CLUE_LIGHT_BITS) ?: return null
        val masks = MASKS.map { box.getAttr(it) ?: return null }.toIntArray()
        return runCatching { TreasureTrailLightPuzzle.restore(masks, bits) }.getOrNull()
    }
    fun issue(player: Player, step: TreasureTrailSourceStep, random: Random = Random.Default): Item? {
        if (!supports(step.source)) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        val id = ClueItems.local(SOURCE_ITEM) ?: return null
        if (player.world.definitions.getNullable(ItemDef::class.java, id) == null) return null
        player.inventory.firstOrNull { it?.id == id }?.let { return it }
        if (ClueOwnership.stored(player).any { it.id == id }) return null
        val slot = player.inventory.rawItems.indexOfFirst { it == null }
        if (slot < 0 || !contract.valid(player)) return null
        val box = Item(id).putAttr(ItemAttribute.CLUE_TASK_KEY, step.persistenceId)
        write(box, TreasureTrailLightPuzzle.create(random))
        player.inventory[slot] = box
        return box
    }
    fun start(player: Player, step: TreasureTrailSourceStep, box: Item): Session? {
        if (!supports(step.source) || box.id != ClueItems.local(SOURCE_ITEM) || player.inventory.none { it === box }) return null
        val contract = ClueStepContract.capture(player, step) ?: return null
        sessions[player]?.let { if (it.box === box && it.valid(player)) return it }
        val board = restore(box) ?: run {
            println("PROBLEM CLUE light box has absent/corrupt saved network; item retained")
            return null
        }
        return Session(contract, box, board).also { sessions[player] = it }
    }
    fun open(player: Player, box: Item): Boolean {
        val iface = iface() ?: return false
        val step = ClueScrollManager.activeStep(player, ClueScrollTier.MASTER) as? TreasureTrailSourceStep ?: return false
        val session = start(player, step, box) ?: return false
        // Closing an old modal must not discard the newly installed session.
        sessions.remove(player)
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN_FULL)
        sessions[player] = session
        player.setInterfaceEvents(iface, CLOSE, -1..-1, 0x2)
        buttons.forEach { player.setInterfaceEvents(iface, it, -1..-1, 0x2) }
        render(player, session, iface)
        return true
    }
    fun toggle(player: Player, session: Session, button: Int): Boolean {
        if (sessions[player] !== session || !session.valid(player) || !session.board.toggle(button)) return false
        write(session.box, session.board)
        player.inventory.dirty = true
        return true
    }
    fun click(player: Player, component: Int): Boolean {
        val iface = iface() ?: return false
        if (!player.interfaces.isVisible(iface)) return false
        val session = sessions[player] ?: return false
        val button = buttons.indexOf(component)
        if (!toggle(player, session, button)) return false
        render(player, session, iface)
        // Source1336's imported client script plays the press sound once.
        // Completion sound is published only after its Wiki/cache identity is verified.
        if (session.board.solved) mapping["sound" to 580]?.let { player.playSound(it) }
        return true
    }
    private fun render(player: Player, session: Session, iface: Int) {
        // Ported source1339 renders actual imported item icons and source spacing.
        // The mapped varp avoids overwriting an unrelated native setting.
        val variable = control() ?: return
        player.setVarp(variable, session.board.bits)
    }

    fun remove(player: Player) { sessions.remove(player) }
    fun handIn(player: Player, step: TreasureTrailSourceStep, box: Item): Boolean {
        if (!supports(step.source) || box.id != ClueItems.local(SOURCE_ITEM) || restore(box)?.solved != true ||
            player.inventory.firstOrNull { it?.id == box.id } !== box) return false
        val contract = ClueStepContract.capture(player, step) ?: return false
        return contract.advance(player, listOf(box.id)).also { if (it) remove(player) }
    }
}
