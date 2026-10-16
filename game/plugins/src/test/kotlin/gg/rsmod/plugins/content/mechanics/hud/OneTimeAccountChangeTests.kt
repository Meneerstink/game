package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A42 (owner 2026-10-14 VRAGEN #2 = B, repeated 2026-10-15 23:50): the team switches "Esc closes the current interface" ON for
 * the owner's account once - at his next login, recorded in his save so it never runs again (he may switch it off later), never
 * through his client prefs or a hand-edited save. Class: every one-time account setting change of [ClientHudConfig.ONE_TIME_CHANGES]
 * (valid setting + value, only the named account, only that one key, once, kept across save/load, before the login push).
 */
class OneTimeAccountChangeTests {
    /** anudd's hud_settings exactly as saved (read-only grep of game\game\data\saves\anudd, line 52, 2026-10-16 02:20). */
    private val ownerSaved =
        "zoom.inner=true;zoom.enabled=true;chatbox.transparent=false;keybinding.escCloses=false;keybinding.inventory=F2;" +
            "keybinding.skills=NONE;keybinding.prayer=F3;keybinding.quests=NONE;keybinding.magic=F4;keybinding.equipment=NONE;" +
            "stretchedmode.enabled=true;stretchedmode.scalingFactor=17"

    private val hudSettings = AttributeKey<Any>("hud_settings")

    /** A player whose attributes were loaded the way JsonPlayerSerializer loads them (untyped keys by persistence name). */
    private fun player(
        username: String,
        saved: Map<String, Any>,
    ): Player {
        val attr = AttributeMap()
        saved.forEach { (key, value) -> attr[AttributeKey<Any>(key)] = value }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns attr
        every { player.username } returns username
        return player
    }

    private fun escCloses(player: Player): Boolean =
        !ClientHudConfig.bool(player, "keybinding.enabled") || ClientHudConfig.bool(player, "keybinding.escCloses")

    @AfterTest
    fun clearObserver() {
        ClientHud.observer = null
    }

    @Test
    fun `every one-time change names a real setting, a valid value, a lowercase account and its own id`() {
        assertTrue(ClientHudConfig.ONE_TIME_CHANGES.isNotEmpty())
        val ids = ClientHudConfig.ONE_TIME_CHANGES.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids must be unique: $ids")
        ClientHudConfig.ONE_TIME_CHANGES.forEach { change ->
            val setting = ClientHudConfig.setting(change.key)
            assertTrue(setting != null, "${change.id}: unknown setting ${change.key}")
            assertTrue(setting.valid(change.value), "${change.id}: ${change.value} is not valid for ${change.key}")
            assertEquals(change.username.lowercase(), change.username, "${change.id}: account names are saved in lower case")
            assertTrue(change.username.isNotBlank() && change.id.isNotBlank())
        }
        // The owner's order: exactly this one key on exactly his account (VRAGEN #2 = B).
        val a42 = ClientHudConfig.ONE_TIME_CHANGES.single { it.id == "A42" }
        assertEquals("anudd", a42.username)
        assertEquals("keybinding.escCloses", a42.key)
        assertEquals("true", a42.value)
    }

    @Test
    fun `the owner's own save gets Esc-closes on at login, every other setting unchanged, pushed to his client`() {
        val owner = player("anudd", mapOf("hud_settings" to ownerSaved))
        assertFalse(escCloses(owner), "precondition: his save has Esc-closes off")
        val others = ClientHudConfig.SETTINGS.map { it.key }.filter { it != "keybinding.escCloses" }.associateWith { ClientHudConfig.get(owner, it) }
        val sent = mutableListOf<String>()
        ClientHud.observer = { _, payload -> sent += payload }

        ClientHudConfig.login(owner)

        assertTrue(escCloses(owner), "Esc must close 667/670 for him after the login")
        assertEquals("true", ClientHudConfig.get(owner, "keybinding.escCloses"))
        others.forEach { (key, value) -> assertEquals(value, ClientHudConfig.get(owner, key), "$key changed") }
        assertEquals("F2", ClientHudConfig.get(owner, "keybinding.inventory"))
        assertEquals("F3", ClientHudConfig.get(owner, "keybinding.prayer"))
        assertEquals("F4", ClientHudConfig.get(owner, "keybinding.magic"))
        assertEquals("true", ClientHudConfig.get(owner, "stretchedmode.enabled"))
        assertEquals("17", ClientHudConfig.get(owner, "stretchedmode.scalingFactor"))
        // The client gets the new value in the same login push (RspsFunctionKeys / RspsHudConfig read it from there).
        val cfg = sent.filter { it.startsWith("cfg|") }.joinToString(";") { it.removePrefix("cfg|") }
        assertTrue("keybinding.escCloses=true" in cfg.split(';'), "login push: $cfg")
        assertFalse("keybinding.escCloses=false" in cfg.split(';'))
    }

    @Test
    fun `the change runs once - kept in the save, switching it off again stays off after the next logins`() {
        val owner = player("anudd", mapOf("hud_settings" to ownerSaved))
        ClientHudConfig.login(owner)
        // Saved and loaded again exactly like a logout/login (JsonPlayerSerializer: toPersistentMap -> AttributeKey<Any>(name)).
        val saved = owner.attr.toPersistentMap()
        assertTrue(saved.keys.any { it == "hud_one_time" }, "the done-flag must be persistent: ${saved.keys}")
        val relogged = player("anudd", saved)
        assertTrue(escCloses(relogged))
        // He switches it off himself (Keybinding window 1181:127 -> hud set) - his choice must win from now on.
        assertTrue(ClientHudConfig.set(relogged, "keybinding.escCloses", "false"))
        ClientHudConfig.login(relogged)
        assertFalse(escCloses(relogged), "the one-time change ran a second time")
        val again = player("anudd", relogged.attr.toPersistentMap())
        ClientHudConfig.login(again)
        assertFalse(escCloses(again))
        // Restore defaults (Keybinding window) resets hud settings only, never the done-flag.
        ClientHudConfig.reset(again)
        assertTrue(again.attr.toPersistentMap().containsKey("hud_one_time"))
    }

    @Test
    fun `no other account is touched, not even a similar name`() {
        listOf("anudd2", "anudd22", "teamtest1", "sumtest", "xanudd", "anud").forEach { name ->
            val other = player(name, mapOf("hud_settings" to ownerSaved))
            ClientHudConfig.login(other)
            assertFalse(escCloses(other), "$name got the owner's change")
            assertEquals(ownerSaved, other.attr[hudSettings])
            assertFalse(other.attr.toPersistentMap().containsKey("hud_one_time"), "$name got a done-flag")
        }
    }

    @Test
    fun `an owner account that already has Esc-closes on is only flagged, nothing else changes`() {
        val owner = player("anudd", mapOf("hud_settings" to "keybinding.inventory=F2"))
        ClientHudConfig.login(owner)
        assertTrue(escCloses(owner))
        assertEquals("keybinding.inventory=F2", owner.attr[hudSettings])
        assertTrue(owner.attr.toPersistentMap().containsKey("hud_one_time"))
        val fresh = player("anudd", emptyMap())
        ClientHudConfig.login(fresh)
        assertNull(fresh.attr[hudSettings])
        assertTrue(escCloses(fresh))
    }
}
