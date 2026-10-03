package gg.rsmod.plugins.content.mechanics.pvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E16 / E9 (owner 2026-10-03, all caps: "DE DMM HUD MOET ALTIJD IN BEELD ZIJN ... MAG NOOOIT VERDWIJNEN"). The HUD vanished because
 * it lived in the shared PvP-overlay slot that six activities take over, and because its only driver was a timer an exception
 * could delete (research-2026-10-03-dmm-hud.md R1/R2). Guards for the whole class, over every source file:
 *  - nothing mounts interface 381 (the HUD is drawn by the client from the `dmmz|` / `dmmt|` side channel);
 *  - DeadmanHud is driven from the end-of-cycle ClientHud.tick hook, never from a timer;
 *  - the client draws it after the whole interface tree (InterfaceManager.method3833 -> DeadmanSkullHud.drawOverlay).
 */
class DeadmanHudAlwaysOnTests {
    private val plugins = File("src/main/kotlin")
    private val client = File("../../../../2011scape-client/2011scape-client/runescape/src/main/java")

    private fun sources(root: File) = root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts") || it.name.endsWith(".java")) }

    @Test
    fun `no plugin mounts the old HUD interface 381 in any slot`() {
        val offenders =
            sources(plugins).flatMap { f ->
                f.readLines().withIndex().filter { (_, l) ->
                    (l.contains("openInterface") || l.contains("openOverlay")) && (l.contains("381") || l.contains("DeadmanHud.INTERFACE_ID"))
                }.map { (i, l) -> "${f.name}:${i + 1} ${l.trim()}" }
            }.toList()
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `the HUD is driven by the end-of-cycle hook, not by a timer`() {
        val hook = File(plugins, "gg/rsmod/plugins/content/mechanics/hud/client_hud.plugin.kts").readText()
        assertTrue(hook.contains("DeadmanHud.refresh(player)"), "ClientHud.tick must call DeadmanHud.refresh")
        val timers =
            sources(plugins).filter { f -> f.readText().let { it.contains("on_timer") && it.contains("DeadmanHud.refresh") } }.map { it.name }.toList()
        assertEquals(emptyList(), timers)
    }

    @Test
    fun `the client draws the HUD after the whole interface tree from the side channel`() {
        if (!client.exists()) return
        val im = File(client, "InterfaceManager.java").readText()
        val top = im.indexOf("draw(topLevelInterface, x1, y1")
        val overlay = im.indexOf("DeadmanSkullHud.drawOverlay(x1, y1)")
        assertTrue(top >= 0 && overlay > top, "drawOverlay must follow the top-level draw in method3833")
        val hud = File(client, "RspsHud.java").readText()
        assertTrue(hud.contains("\"dmmz\"") && hud.contains("DeadmanSkullHud.update"), "RspsHud must route dmmz/dmmt to DeadmanSkullHud")
        assertTrue(!File(client, "DeadmanSkullHud.java").readText().contains("InterfaceList.getComponent((INTERFACE_ID"), "HUD text must not come from interface 381")
    }
}
