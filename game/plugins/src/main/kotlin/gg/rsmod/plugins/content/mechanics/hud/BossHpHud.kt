package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player

/**
 * N606 (13-11): the OSRS boss "HP HUD" (OSRS interface 303 hpbar_hud) - the health bar at the top centre of the game view during a
 * boss fight. OSRS shows it for every Tombs of Amascut boss (Kaoz Entry guide 05-09-2022, screens/N606/hpbar-osrs/strips-*.png:
 * Zebak, Ba-Ba, Akkha, Obelisk, Tumeken's Warden green/red; Kephri's shield and Het's Seal cyan; a P2 Warden's damage bar yellow).
 * The client ([RspsHudBossBar]) draws OSRS 303 itself; RuneLite's Opponent Information hides its own panel for the npc the HP HUD
 * shows (OpponentInfoOverlay.hasHpHud), so ours does the same.
 *
 * Payload `hpbar|<npc index>|<style>|<hp>|<max>|<name>` or `hpbar|none`; sent by [ClientHudSync] on change only.
 */
object BossHpHud {
    /** Bar colours: OSRS 303 components 13/14/15 (STANDARD) or the server-set colours measured in the OSRS video (SHIELD / DAMAGE). */
    enum class Style(val code: Int) { STANDARD(0), SHIELD(1), DAMAGE(2) }

    data class Bar(val npc: Npc, val hp: Int, val max: Int, val style: Style = Style.STANDARD, val name: String = nameOf(npc))

    /** Content registers who sees an HP HUD; the first provider with a bar wins. */
    val providers = mutableListOf<(Player) -> Bar?>()

    /**
     * After the boss dies OSRS keeps the empty bar ("Kephri 0 / 130") a few seconds before it goes (Kaoz 17:48-17:52 video seconds
     * 1068 shown, 1072 gone) -> 6 ticks (3.6 s).
     */
    const val DEATH_HOLD_TICKS = 6

    private class Last(val bar: Bar, val cycle: Int)
    private val LAST = AttributeKey<Last>()

    /**
     * OSRS hp_hud_update: npc param 510 (the HUD name) or else the npc name. For every ToA HUD npc param 510 is the name without its
     * colour tags (11750/11751 "<col=00ffff>Obelisk</col>" -> "Obelisk"; N462 cache/npc-defs.json); a different 510 is passed explicitly.
     */
    fun nameOf(npc: Npc): String {
        val id = npc.getTransmogId().takeIf { it >= 0 } ?: npc.id
        return npc.world.definitions.get(NpcDef::class.java, id).name.replace(Regex("<[^>]*>"), "").trim()
    }

    fun payload(player: Player): String {
        val now = player.world.currentCycle
        val bar = providers.firstNotNullOfOrNull { runCatching { it(player) }.getOrNull() }
        if (bar != null) {
            player.attr[LAST] = Last(bar, now)
            return encode(bar)
        }
        val last = player.attr[LAST] ?: return "hpbar|none"
        val gone = last.bar.npc.isDead() || !last.bar.npc.isSpawned()
        if (gone && now - last.cycle <= DEATH_HOLD_TICKS) return encode(last.bar.copy(hp = 0))
        player.attr.remove(LAST)
        return "hpbar|none"
    }

    private fun encode(bar: Bar): String =
        "hpbar|${bar.npc.index}|${bar.style.code}|${bar.hp.coerceAtLeast(0)}|${bar.max.coerceAtLeast(1)}|${bar.name.replace('|', ' ')}"
}
