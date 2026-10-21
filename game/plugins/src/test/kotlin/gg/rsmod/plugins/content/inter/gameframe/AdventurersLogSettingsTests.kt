package gg.rsmod.plugins.content.inter.gameframe

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N123 (owner 2026-10-11 "ALLES van Dungeoneering volledig verwijderen"): the Adventurer's Log settings page 623 must not offer
 * a row for removed content. Class, read from the cache rather than a hand list: EVERY `Toggle On/Off` switch of 623 (a GRAPHIC
 * whose onVarTransmit hook is clientscript 4732(component, bit)) with its label (the TEXT component right after it). A switch
 * whose label names Dungeoneering is hidden - switch and label - every time the page is opened from the Options tab and has no
 * server handler; every other switch is visible and flips exactly its own cache bit of varp 2396 (owner 2026-10-04 032152).
 * Runs the real compiled settings_tab.plugin.kts (scripts/build/classes), so it also proves the Options tab button wiring.
 */
class AdventurersLogSettingsTests {
    private class Captured {
        val hides = mutableListOf<IfSetHideMessage>()
    }

    private fun player(captured: Captured): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.interfaces.displayMode } returns DisplayMode.FIXED
        every { player.varps } returns VarpSet(setOf(VARP))
        every { player.write(*anyVararg<Message>()) } answers {
            firstArg<Array<Message>>().forEach { message -> if (message is IfSetHideMessage) captured.hides += message }
        }
        return player
    }

    private fun click(
        player: Player,
        parent: Int,
        child: Int,
    ) {
        val plugin = buttons[(parent shl 16) or child] ?: throw AssertionError("$parent:$child has no server handler")
        plugin.invoke(Plugin(player))
    }

    private fun hidden623(captured: Captured): List<Pair<Int, Boolean>> =
        captured.hides.filter { it.hash ushr 16 == INTERFACE }.map { (it.hash and 0xFFFF) to it.hidden }

    @Test
    fun `N123 class - every 623 switch whose cache label names Dungeoneering is hidden with its label on every open`() {
        val removed = SWITCHES.keys.filter { LABELS.getValue(it).contains("Dungeoneering", ignoreCase = true) }
        assertTrue("the cache still has Dungeoneering rows on 623 (labels: $LABELS)", removed.isNotEmpty())
        val expected = removed.flatMap { listOf(it, it + 1) }.sorted()
        val captured = Captured()
        val player = player(captured)
        repeat(3) { attempt ->
            captured.hides.clear()
            click(player, 261, 18)
            val hides = hidden623(captured)
            assertEquals("open #${attempt + 1}: components of 623 hidden after the page is mounted", expected.map { it to true }, hides.sortedBy { it.first })
        }
    }

    @Test
    fun `N123 class - removed switches have no handler, every other switch flips exactly its own cache bit of varp 2396`() {
        val problems = mutableListOf<String>()
        for ((component, bit) in SWITCHES) {
            val label = LABELS.getValue(component)
            val bound = buttons.containsKey((INTERFACE shl 16) or component)
            if (label.contains("Dungeoneering", ignoreCase = true)) {
                if (bound) problems += "623:$component '$label' (removed) still has a server handler"
                continue
            }
            if (!bound) {
                problems += "623:$component '$label' has no server handler"
                continue
            }
            val player = player(Captured())
            click(player, INTERFACE, component)
            if (player.varps.getState(VARP) != (1 shl bit)) problems += "623:$component '$label' set varp $VARP to ${player.varps.getState(VARP)}, want bit $bit"
            click(player, INTERFACE, component)
            if (player.varps.getState(VARP) != 0) problems += "623:$component '$label' second click left ${player.varps.getState(VARP)}"
        }
        assertEquals("switches decoded from the cache: $SWITCHES", 19, SWITCHES.size)
        assertEquals("Adventurer's Log switch problems", emptyList<String>(), problems)
    }

    @Test
    fun `N123 the kept rows are never hidden and the page is still opened as a main-screen modal`() {
        val captured = Captured()
        click(player(captured), 261, 18)
        val kept = SWITCHES.keys.filterNot { LABELS.getValue(it).contains("Dungeoneering", ignoreCase = true) }.flatMap { listOf(it, it + 1) }
        assertEquals("kept rows hidden", emptyList<Int>(), hidden623(captured).map { it.first }.filter { it in kept })
        assertFalse("nothing on 623 is un-hidden", hidden623(captured).any { !it.second })
    }

    companion object {
        private const val INTERFACE = 623
        private const val VARP = 2396
        private const val SCRIPT = "gg.rsmod.plugins.content.inter.gameframe.Settings_tab_plugin"
        private const val SOURCE = "src/main/kotlin/gg/rsmod/plugins/content/inter/gameframe/settings_tab.plugin.kts"

        private val LIBRARY by lazy { CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()) }
        private val DECODE by lazy {
            InterfaceHookProbeTool::class.java.declaredMethods.first { it.name.startsWith("decodeComponent") }.apply { isAccessible = true }
        }

        private fun field(
            decoded: Any,
            getter: String,
        ): Any? = decoded.javaClass.getDeclaredMethod(getter).apply { isAccessible = true }.invoke(decoded)

        private val COMPONENTS: Map<Int, Any> by lazy {
            val archive = LIBRARY.index(3).archive(INTERFACE) ?: error("interface $INTERFACE missing from the cache")
            archive.fileIds().sorted().associateWith { slot -> DECODE.invoke(InterfaceHookProbeTool, LIBRARY.data(3, INTERFACE, slot)!!) }
        }

        /** Switch component -> bit, from the cache hook `onVarTransmit = [4732, component, bit]` on a GRAPHIC(5) component. */
        @Suppress("UNCHECKED_CAST")
        val SWITCHES: Map<Int, Int> by lazy {
            COMPONENTS.mapNotNull { (slot, decoded) ->
                val hook = (field(decoded, "getHooks") as Map<String, List<Any>?>)["onVarTransmit"]
                if (field(decoded, "getType") == 5 && hook != null && hook[0] == 4732) slot to (hook[2] as Int) else null
            }.toMap()
        }

        /** The TEXT label drawn next to each switch (the component right after it). */
        val LABELS: Map<Int, String> by lazy {
            SWITCHES.keys.associateWith { switch ->
                val label = COMPONENTS[switch + 1] ?: error("623:${switch + 1} missing")
                assertEquals("623:${switch + 1} is the TEXT label of switch $switch", 4, field(label, "getType"))
                (field(label, "getText") as String?).orEmpty()
            }
        }

        /** The button handlers the real compiled settings_tab.plugin.kts registers. */
        @Suppress("UNCHECKED_CAST")
        val buttons: Map<Int, Plugin.() -> Unit> by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            val classes = File("scripts/build/classes/kotlin/main")
            val compiled = File(classes, SCRIPT.replace('.', '/') + ".class")
            if (!compiled.exists() || compiled.lastModified() < File(SOURCE).lastModified()) {
                fail("${compiled.path} is missing or older than $SOURCE: the scripts compile did not run first")
            }
            URLClassLoader(arrayOf(classes.toURI().toURL()), AdventurersLogSettingsTests::class.java.classLoader)
                .loadClass(SCRIPT)
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            PluginRepository::class.java.getDeclaredField("buttonPlugins").apply { isAccessible = true }.get(world.plugins) as Map<Int, Plugin.() -> Unit>
        }
    }
}
