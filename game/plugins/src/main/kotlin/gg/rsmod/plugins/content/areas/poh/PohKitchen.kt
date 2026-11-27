package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C5): the kitchen's drinks, as OSRS (Wiki "Beer barrel", "Beer (Player-owned house)", "Kettle",
 * "Pot of tea", "Cup of tea (Player-owned house)").
 *
 * - A beer glass (from the shelves) on a barrel fills it with that barrel's drink - unlimited.
 * - Tea: a kettle on the sink / pump fills it, a full kettle on a stove with a hook or better boils it (the basic firepit has no hook),
 *   tea leaves in a teapot, the hot kettle on the teapot with leaves = a pot of tea (4), the pot on an empty cup pours a cup of tea;
 *   milk makes it milky (look only). A cup of tea boosts Construction by the CUP: clay +1, porcelain +2, trimmed porcelain +3 (the
 *   cup the kitchen's shelves give). Drinking gives the empty cup back.
 * - Everything the kitchen gives (the shelves' and barrels' items, tea, POH beer) disappears when the player leaves the house
 *   ([HOUSE_ONLY], checked every few ticks by [LEAVE_TIMER] while the player carries any of it).
 */
object PohKitchen {
    const val BEER_GLASS = 7742
    const val KETTLE = 7688
    const val FULL_KETTLE = 7690
    const val HOT_KETTLE = 7691
    const val TEA_LEAVES = 7738
    const val MILK = 1927
    const val BUCKET = 1925

    /** Barrel loc -> its POH drink (7740 beer, 7752 cider, 7744 Asgarnian ale, 7746 Greenman's, 7748 Dragon bitter, 7754 Chef's delight). */
    val BARRELS: Map<Int, Int> = mapOf(13568 to 7740, 13569 to 7752, 13570 to 7744, 13571 to 7746, 13572 to 7748, 13573 to 7754)

    val DRINKS: Set<Int> = BARRELS.values.toSet()

    /** The sink and pumps (2009scape: pump and drain 13559, pump and tub 13561, sink 13563). */
    val WATER: Set<Int> = setOf(13559, 13560, 13561, 13562, 13563, 13564)

    /** Every stove but the basic firepit (no hook): firepit with hook 13529 .. fancy range. */
    val STOVES: Set<Int> = (13529..13544).toSet()

    /** Teapot -> (teapot with leaves, pot of tea (4..1)). Three colours, one per shelf. */
    class Pot(val empty: Int, val leaves: Int, val doses: IntArray)

    val POTS: List<Pot> =
        listOf(
            Pot(7702, 7700, intArrayOf(7692, 7694, 7696, 7698)),
            Pot(7714, 7712, intArrayOf(7704, 7706, 7708, 7710)),
            Pot(7726, 7724, intArrayOf(7716, 7718, 7720, 7722)),
        )

    /** Empty cup -> (cup of tea, milky cup of tea, Construction boost). */
    class Cup(val empty: Int, val tea: Int, val milky: Int, val boost: Int)

    val CUPS: List<Cup> = listOf(Cup(7728, 7730, 7731, 1), Cup(7732, 7733, 7734, 2), Cup(7735, 7736, 7737, 3))

    /** Items that never leave the house: the kitchen's drinks and everything the tea chain uses. */
    val HOUSE_ONLY: Set<Int> =
        DRINKS + setOf(BEER_GLASS, KETTLE, FULL_KETTLE, HOT_KETTLE, TEA_LEAVES) + POTS.flatMap { listOf(it.empty, it.leaves) + it.doses.toList() } +
            CUPS.flatMap { listOf(it.empty, it.tea, it.milky) }

    val LEAVE_TIMER = TimerKey()
    const val LEAVE_CHECK_TICKS = 3

    /** Start watching for the player leaving the house once they carry a house-only item. */
    fun watch(player: Player) {
        if (!player.timers.has(LEAVE_TIMER)) player.timers[LEAVE_TIMER] = LEAVE_CHECK_TICKS
    }

    /** The timer: outside every house the house-only items go; inside it keeps watching while any is carried. */
    fun check(player: Player) {
        val carried = HOUSE_ONLY.filter { player.inventory.contains(it) }
        if (carried.isEmpty()) return
        if (PlayerHouse.houseArea(player.tile) != null) {
            player.timers[LEAVE_TIMER] = LEAVE_CHECK_TICKS
            return
        }
        carried.forEach { id -> player.inventory.remove(id, player.inventory.getItemCount(id)) }
        player.message("The items from your house's kitchen stay behind.")
    }

    fun inHouse(player: Player): Boolean = PlayerHouse.houseArea(player.tile) != null

    fun replace(
        player: Player,
        from: Int,
        to: Int,
    ): Boolean {
        if (!player.inventory.remove(from, 1).hasSucceeded()) return false
        player.inventory.add(to, 1)
        watch(player)
        return true
    }

    fun drink(
        player: Player,
        item: Int,
    ) {
        val cup = CUPS.firstOrNull { it.tea == item || it.milky == item }
        if (cup != null) {
            if (!replace(player, item, cup.empty)) return
            player.animate(829)
            player.playSound(gg.rsmod.plugins.api.cfg.Sfx.DRINK)
            player.skills.incrementCurrentLevel(Skills.CONSTRUCTION, cup.boost, true)
            player.message("You drink the tea. You feel ready to build!")
            return
        }
        if (item in DRINKS) {
            if (!replace(player, item, BEER_GLASS)) return
            player.animate(829)
            player.playSound(gg.rsmod.plugins.api.cfg.Sfx.DRINK)
            player.heal(1)
            player.message("You drink the ${player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name.lowercase()}. You feel slightly reinvigorated.")
        }
    }

    fun pour(
        player: Player,
        potItem: Int,
        cupItem: Int,
    ) {
        val pot = POTS.firstOrNull { potItem in it.doses } ?: return
        val cup = CUPS.firstOrNull { it.empty == cupItem } ?: return
        val i = pot.doses.indexOf(potItem)
        val nextPot = pot.doses.getOrNull(i + 1) ?: pot.empty
        if (!player.inventory.contains(potItem) || !player.inventory.contains(cupItem)) return
        player.inventory.remove(potItem, 1)
        player.inventory.remove(cupItem, 1)
        player.inventory.add(nextPot, 1)
        player.inventory.add(cup.tea, 1)
        watch(player)
        player.message("You pour some tea.")
    }
}
