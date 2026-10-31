package gg.rsmod.plugins.content.inter.magic

import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.attr.INTERACTING_SLOT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.switchSpellbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Paths

/**
 * N72 (owner 2026-10-16 19:43 "magic spellbook needs to exact same as osrs style layout options"): the OSRS spell filters on every
 * spellbook. Class: every filter control of the three books (Standard 192, Ancient 193, Lunar 430) - each 667 bottom-row "Filter" /
 * "Sort" button the cache gives the book (scanned from the cache, not a hand list) and every OSRS filter-menu action (enable /
 * disable filtering, Show Combat / Teleport / Utility, lacks level / runes / requirements, icon resizing) - must reach the server
 * and change the saved spellbook filter state in varp 1376 that the client layout reads, the same on every book.
 *
 * Varp 1376 (667 cache varbits 5822-5824 sort, 6459-6467 category filters; the removed Dungeoneering book's bits 18+ carry the OSRS
 * state): Standard combat 9 / skill 10 / misc 11 / teleport 12, Lunar combat 13 / misc 14 / teleport 15, Ancient combat 16 /
 * teleport 17; OSRS 18 enabled, 19 hide combat, 20 hide teleport, 21 hide utility, 22 lacks level, 23 lacks runes,
 * 24 lacks requirements, 25 icon resizing off. The client sends an OSRS action as IF_BUTTON1 on the book's full-size layer
 * (book struct param 688: 192:97, 193:54, 430:66) with the action as slot.
 */
class SpellFiltersTests {
    private val h = SpellTargetMaskHarness

    @After
    fun cleanUp() = h.cleanUp()

    @Test
    fun `N72 class - every Filter and Sort button the cache gives the three books changes the shared filter state`() {
        val problems = mutableListOf<String>()
        var filters = 0
        var sorts = 0
        for ((book, spellbook) in BOOKS) {
            for (component in SpellTargetMaskTests.components(book)) {
                val ops = ops(book, component)
                when {
                    "Filter" in ops -> {
                        filters++
                        val player = h.player("n72f$book$component")
                        h.open(player, spellbook)
                        val category = categoryOf(book, component)
                        if (category == null) {
                            problems += "$book:$component 'Filter' has no 667 category (Novite ButtonHandler)"
                            continue
                        }
                        click(player, book, component)
                        val value = player.varps.getState(VARP)
                        val want = CATEGORY_BITS.getValue(category).fold(1 shl ENABLED) { acc, bit -> acc or (1 shl bit) }
                        if (value and want != want) {
                            problems += "$book:$component Filter $category: varp 1376 = 0x%08x, missing bits 0x%08x (dead button?)".format(value, want and value.inv())
                        }
                        click(player, book, component)
                        val after = player.varps.getState(VARP)
                        if (after and CATEGORY_BITS.getValue(category).fold(0) { acc, bit -> acc or (1 shl bit) } != 0) {
                            problems += "$book:$component Filter $category: a second click does not show the category again (0x%08x)".format(after)
                        }
                    }
                    "Sort" in ops -> {
                        sorts++
                        val player = h.player("n72s$book$component")
                        h.open(player, spellbook)
                        val index = SORT.getValue(book).indexOf(component)
                        click(player, book, component)
                        val value = player.varps.getState(VARP)
                        val want = index or (index shl 3) or (index shl 6)
                        if (index < 0 || value and 0x1FF != want) {
                            problems += "$book:$component Sort: varp 1376 sort bits 0x%03x, want 0x%03x (dead button?)".format(value and 0x1FF, want)
                        }
                    }
                }
            }
        }
        println("[N72] cache Filter buttons $filters, Sort buttons $sorts")
        assertEquals("the cache gives 4 + 2 + 3 Filter buttons", 9, filters)
        assertEquals("the cache gives 3 Sort buttons per book", 9, sorts)
        if (problems.isNotEmpty()) fail("${problems.size} spellbook filter/sort buttons do not work:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `N72 class - every OSRS filter menu action works on every book and the 667 layout bits follow only while filtering is on`() {
        val problems = mutableListOf<String>()
        for ((book, spellbook) in BOOKS) {
            val player = h.player("n72m$book")
            h.open(player, spellbook)
            val layer = MENU_LAYER.getValue(book)
            fun state() = player.varps.getState(VARP)
            // Filtering off: hiding Combat is remembered but nothing is hidden in the 667 layout.
            click(player, book, layer, slot = 1)
            if (state() and (1 shl 19) == 0) problems += "$book: 'Show Combat spells' not stored (0x%08x)".format(state())
            if (state() and ALL_CATEGORY_BITS != 0) problems += "$book: filtering off but category bits set (0x%08x)".format(state())
            // Enable spell filtering: the remembered choice applies to every book's 667 bits.
            click(player, book, layer, slot = 0)
            if (state() and (1 shl ENABLED) == 0) problems += "$book: 'Enable spell filtering' not stored (0x%08x)".format(state())
            if (state() and COMBAT_BITS != COMBAT_BITS) problems += "$book: combat hidden + enabled but 667 combat bits 0x%08x".format(state())
            // Teleport, Utility.
            click(player, book, layer, slot = 2)
            click(player, book, layer, slot = 3)
            if (state() and TELEPORT_BITS != TELEPORT_BITS) problems += "$book: teleport bits 0x%08x".format(state())
            if (state() and UTILITY_BITS != UTILITY_BITS) problems += "$book: utility bits 0x%08x".format(state())
            // The client-side filters: lacks level / runes / requirements, icon resizing.
            listOf(4 to 22, 5 to 23, 6 to 24, 7 to 25).forEach { (slot, bit) ->
                click(player, book, layer, slot = slot)
                if (state() and (1 shl bit) == 0) problems += "$book: menu action $slot did not set bit $bit (0x%08x)".format(state())
                click(player, book, layer, slot = slot)
                if (state() and (1 shl bit) != 0) problems += "$book: menu action $slot did not clear bit $bit again (0x%08x)".format(state())
            }
            // Disable spell filtering: every spell shows again, the choices are kept for the next enable.
            click(player, book, layer, slot = 0)
            if (state() and ALL_CATEGORY_BITS != 0) problems += "$book: filtering disabled but category bits 0x%08x".format(state())
            if (state() and (7 shl 19) != (7 shl 19)) problems += "$book: disabling forgot the menu choices (0x%08x)".format(state())
            click(player, book, layer, slot = 0)
            if (state() and ALL_CATEGORY_BITS != ALL_CATEGORY_BITS) problems += "$book: re-enabled but categories not hidden again (0x%08x)".format(state())
        }
        if (problems.isNotEmpty()) fail("${problems.size} OSRS filter menu problems:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `N72 the filter state is shared by the books, survives a spellbook switch and a relog, and old saves are cleaned at login`() {
        val player = h.player("n72r")
        h.open(player, Spellbook.STANDARD)
        click(player, 192, 97, slot = 0)
        click(player, 192, 97, slot = 1)
        click(player, 192, 97, slot = 5)
        val saved = player.varps.getState(VARP)
        h.open(player, Spellbook.ANCIENT)
        assertEquals("switching to Ancient keeps the state", saved, player.varps.getState(VARP))
        h.open(player, Spellbook.LUNAR)
        assertEquals("switching to Lunar keeps the state", saved, player.varps.getState(VARP))
        // Relog: the save keeps varp 1376 (JsonPlayerSerializer saves every non-zero varp); login re-projects it.
        h.cleanUp()
        val relogged = h.player("n72r", varps = mapOf(VARP to saved))
        h.open(relogged, Spellbook.STANDARD)
        assertEquals("relog keeps the filters", saved, relogged.varps.getState(VARP))
        // A pre-N72 save with the 667 Standard combat filter set but no OSRS filtering: login shows every spell.
        h.cleanUp()
        val old = h.player("n72o", varps = mapOf(VARP to ((1 shl 9) or 1)))
        h.open(old, Spellbook.STANDARD)
        // N72 (2026-10-31): the 667 Sort buttons are gone, so an old "combat first" sort goes back to the OSRS level order at login.
        assertEquals("old 667 category bits and sort are cleared at login (filtering is off, OSRS level order)", 0, old.varps.getState(VARP))
    }

    private fun click(
        player: Client,
        book: Int,
        component: Int,
        slot: Int = -1,
    ) {
        player.attr[INTERACTING_OPT_ATTR] = 1
        player.attr[INTERACTING_SLOT_ATTR] = slot
        assertTrue("$book:$component (slot $slot) has a server handler", h.world.plugins.executeButton(player, book, component))
        h.tick(1)
    }

    private fun categoryOf(
        book: Int,
        component: Int,
    ): String? = LEGACY_FILTERS[book]?.get(component)

    companion object {
        const val VARP = 1376
        const val ENABLED = 18
        val BOOKS = SpellTargetMaskTests.BOOKS
        val MENU_LAYER = mapOf(192 to 97, 193 to 54, 430 to 66)

        /** Novite ButtonHandler.java:416-431 / 604-625 (the ids match our cache). */
        val LEGACY_FILTERS =
            mapOf(
                192 to mapOf(7 to "COMBAT", 9 to "TELEPORT", 11 to "UTILITY", 13 to "UTILITY"),
                193 to mapOf(5 to "COMBAT", 7 to "TELEPORT"),
                430 to mapOf(5 to "COMBAT", 7 to "TELEPORT", 9 to "UTILITY"),
            )

        /** Cache enums 2169 / 2165 / 2173: sort button -> order index. */
        val SORT = mapOf(192 to listOf(15, 16, 17), 193 to listOf(9, 10, 11), 430 to listOf(11, 12, 13))

        val CATEGORY_BITS = mapOf("COMBAT" to listOf(9, 13, 16), "UTILITY" to listOf(10, 11, 14), "TELEPORT" to listOf(12, 15, 17))
        val COMBAT_BITS = (1 shl 9) or (1 shl 13) or (1 shl 16)
        val TELEPORT_BITS = (1 shl 12) or (1 shl 15) or (1 shl 17)
        val UTILITY_BITS = (1 shl 10) or (1 shl 11) or (1 shl 14)
        val ALL_CATEGORY_BITS = 0x1FF shl 9

        private val LIBRARY by lazy { com.displee.cache.CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()) }
        private val DECODE by lazy {
            InterfaceHookProbeTool::class.java.declaredMethods.first { it.name.startsWith("decodeComponent") }.apply { isAccessible = true }
        }

        @Suppress("UNCHECKED_CAST")
        fun ops(
            book: Int,
            component: Int,
        ): List<String?> {
            val data = LIBRARY.data(3, book, component) ?: return emptyList()
            val decoded = DECODE.invoke(InterfaceHookProbeTool, data)
            return decoded.javaClass.getDeclaredMethod("getOps").apply { isAccessible = true }.invoke(decoded) as List<String?>
        }
    }
}

/** The SpellTargetMaskTests world (real cache, the compiled magic tab script bound) with players that can carry a saved varp. */
object SpellTargetMaskHarness {
    private val players = mutableListOf<Client>()
    val world: gg.rsmod.game.model.World get() = SpellTargetMaskTests.world
    private val service = io.mockk.mockk<gg.rsmod.game.service.GameService>(relaxed = true)
    private val tasks =
        listOf(
            gg.rsmod.game.task.QueueHandlerTask(),
            gg.rsmod.game.task.sequential.SequentialPlayerCycleTask(),
            gg.rsmod.game.task.WorldRemoveTask(),
            gg.rsmod.game.task.sequential.SequentialNpcCycleTask(),
            gg.rsmod.game.task.sequential.SequentialSynchronizationTask(),
            gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask(),
        )
    private val worldCycle: java.lang.reflect.Method by lazy {
        gg.rsmod.game.model.World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
    }

    fun player(
        name: String,
        varps: Map<Int, Int> = emptyMap(),
    ): Client {
        val w = world
        val at = SpellTargetMaskTests.OWNER_TILE
        w.collision.isClipped(at)
        val client = Client(io.netty.channel.embedded.EmbeddedChannel(), w)
        client.loginUsername = name
        client.username = name
        client.tile = at
        client.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION, 99)
        client.setCurrentLifepoints(99)
        varps.forEach { (id, value) -> client.varps.setState(id, value) }
        val system = io.mockk.mockk<gg.rsmod.game.system.GameSystem>(relaxed = true)
        client.gameSystem = system
        assertTrue("registered $name", w.register(client))
        players += client
        // What a real login runs for the spellbook (the magic tab script's on_login).
        w.plugins.executeLogin(client)
        tick(1)
        return client
    }

    fun open(
        player: Client,
        book: Spellbook,
    ) {
        player.switchSpellbook(book)
        tick(2)
    }

    fun tick(n: Int) {
        repeat(n) {
            tasks.forEach { it.execute(world, service) }
            worldCycle.invoke(world)
        }
    }

    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
    }
}
