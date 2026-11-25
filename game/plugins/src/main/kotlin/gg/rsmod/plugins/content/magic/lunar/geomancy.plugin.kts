package gg.rsmod.plugins.content.magic.lunar

on_button(430, gg.rsmod.plugins.content.magic.SpellbookData.GEOMANCY.component) {
    if (Geomancy.cast(player)) {
        val p = player
        world.queue {
            while (p.isOnline && p.isInterfaceVisible(Geomancy.INTERFACE)) {
                wait(1)
                Geomancy.refresh(p)
            }
        }
    }
}
on_button(Geomancy.INTERFACE, 2) { player.closeInterface(Geomancy.INTERFACE) }
(5..10).forEach { component -> on_button(Geomancy.INTERFACE, component) { Geomancy.select(player, component - 5) } }
