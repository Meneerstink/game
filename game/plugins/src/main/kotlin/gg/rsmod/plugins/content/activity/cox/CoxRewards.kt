package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import kotlin.random.Random

/**
 * The Ancient chest's rewards (OSRS 539 raids_rewards; tsps 'The ancient chest opens interface 539 (Take, Take-all, Bank-all,
 * Discard-all); unclaimed loot is placed on the ground on leaving'). Loot = CoxLoot (Wiki numbers), one list per raider, kept until
 * claimed. Components: rebuilt slots 24..29 (items) + 30 (the reward book = dark journal), static buttons 6 Bank-all, 8 Take-all,
 * 10 Discard-all (OSRS 539:6/8/10 ops).
 */
object CoxRewards {
    const val FIRST_SLOT = 24
    const val SLOTS = 6
    const val BOOK = 30
    const val BANK_ALL = 6
    const val TAKE_ALL = 8
    const val DISCARD_ALL = 10

    /** Local item id + amount + noted, per raider (lower-case name). */
    class Reward(val item: Int, val amount: Int)

    private val pending = java.util.concurrent.ConcurrentHashMap<String, MutableList<Reward>>()

    /** Completed raids per account (Collection Log / Drop Viewer count, like ToaChest.COMPLETIONS). */
    val COMPLETIONS = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "cox_completions")

    fun has(player: Player): Boolean = pending[player.username.lowercase()]?.isNotEmpty() == true

    /** Raiders whose chest holds a unique (the purple light, CoxChest). */
    private val uniqueFinders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun hadUnique(player: Player): Boolean = player.username.lowercase() in uniqueFinders

    /**
     * The raid is complete: the team's uniques (by team points) go to raiders weighted by personal points; everybody else rolls commons.
     * [owned] answers whether a raider already owns an OSRS item (ancient tablet, dark journal).
     */
    fun generate(raid: CoxRaid, raiders: List<Player>, random: Random = Random.Default, owned: (Player, Int) -> Boolean = { _, _ -> false }) {
        val byName = raiders.associateBy { it.username.lowercase() }
        val points = byName.keys.associateWith { raid.points[it] ?: 0 }
        val uniques = HashMap<String, CoxLoot.Unique>()
        repeat(CoxLoot.uniqueCount(raid.totalPoints(), random)) {
            val who = CoxLoot.pickReceiver(points.filterKeys { it !in uniques }, random) ?: return@repeat
            uniques[who] = CoxLoot.pickUnique(random)
        }
        uniqueFinders.removeAll(byName.keys)
        uniqueFinders.addAll(uniques.keys)
        byName.forEach { (name, player) ->
            player.attr[COMPLETIONS] = (player.attr[COMPLETIONS] ?: 0) + 1
            val drops = CoxLoot.chest(uniques[name], points[name] ?: 0, owned(player, CoxLoot.ANCIENT_TABLET), owned(player, CoxLoot.DARK_JOURNAL), random)
            pending[name] = drops.mapNotNull { d ->
                val local = CoxLootItems.local(d.osrs) ?: return@mapNotNull null
                val id = if (d.noted) player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, local).noteLinkId.takeIf { it > 0 } ?: local else local
                Reward(id, d.amount)
            }.toMutableList()
        }
    }

    fun open(player: Player) {
        val iface = CoxInterfaces.of(CoxInterfaces.REWARDS) ?: return
        player.openInterface(iface, InterfaceDestination.MAIN_SCREEN)
        draw(player, iface)
    }

    private fun draw(player: Player, iface: Int) {
        val list = pending[player.username.lowercase()].orEmpty()
        val journal = CoxLootItems.local(CoxLoot.DARK_JOURNAL)
        val book = list.firstOrNull { it.item == journal }
        val items = list.filter { it !== book }
        for (s in 0 until SLOTS) {
            val r = items.getOrNull(s)
            if (r == null) player.setComponentHidden(iface, FIRST_SLOT + s, true)
            else { player.setComponentItem(iface, FIRST_SLOT + s, r.item, r.amount); player.setComponentHidden(iface, FIRST_SLOT + s, false) }
        }
        if (book == null) player.setComponentHidden(iface, BOOK, true)
        else { player.setComponentItem(iface, BOOK, book.item, 1); player.setComponentHidden(iface, BOOK, false) }
    }

    /** A click in the window: an item slot (Take) or one of the three buttons. */
    fun click(player: Player, component: Int) {
        val iface = CoxInterfaces.of(CoxInterfaces.REWARDS) ?: return
        val key = player.username.lowercase()
        val list = pending[key] ?: return
        val journal = CoxLootItems.local(CoxLoot.DARK_JOURNAL)
        val book = list.firstOrNull { it.item == journal }
        val items = list.filter { it !== book }
        when (component) {
            TAKE_ALL -> list.toList().forEach { take(player, list, it) }
            BANK_ALL -> list.toList().forEach { r -> if (player.bank.add(r.item, r.amount).hasSucceeded()) list.remove(r) }
            DISCARD_ALL -> list.clear()
            BOOK -> book?.let { take(player, list, it) }
            in FIRST_SLOT until FIRST_SLOT + SLOTS -> items.getOrNull(component - FIRST_SLOT)?.let { take(player, list, it) }
        }
        if (list.isEmpty()) { pending.remove(key); uniqueFinders.remove(key) }
        draw(player, iface)
        CoxChest.refresh(player)
    }

    private fun take(player: Player, list: MutableList<Reward>, r: Reward) {
        if (player.inventory.add(r.item, r.amount).hasSucceeded()) list.remove(r)
        else player.message("You don't have enough inventory space.")
    }

    /** Leaving with unclaimed loot: it is placed on the ground (tsps rsprox) where the raider leaves. */
    fun dropUnclaimed(player: Player, world: World) {
        uniqueFinders.remove(player.username.lowercase())
        val list = pending.remove(player.username.lowercase()) ?: return
        // OSRS Wiki 'Storage unit' (30107, by the mountain's bank chest): it holds 'any rewards the player did not retrieve from the ancient
        // chest at the end of the raid' - into the account's private storage, not on the ground.
        CoxStorage.addUnclaimed(player, list.map { it.item to it.amount })
    }
}
