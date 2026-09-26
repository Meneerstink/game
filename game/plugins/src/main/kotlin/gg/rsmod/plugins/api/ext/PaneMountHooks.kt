package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player

/**
 * Work that must run right after an interface is mounted in a gameframe pane - after its IF_OPENSUB is written, so the
 * client already has the parent when content opens sub-interfaces inside it (`on_interface_open` runs before that
 * packet). [Player.openInterface] with an [gg.rsmod.plugins.api.InterfaceDestination] is the one caller: login, the
 * display-mode switch and every other pane (re)mount go through it.
 */
object PaneMountHooks {
    private val hooks = HashMap<Int, (Player) -> Unit>()

    /** Registers (or replaces, on script reload) what runs after [interfaceId] is mounted in a pane. */
    fun register(
        interfaceId: Int,
        hook: (Player) -> Unit,
    ) {
        hooks[interfaceId] = hook
    }

    fun afterMount(
        player: Player,
        interfaceId: Int,
    ) {
        hooks[interfaceId]?.invoke(player)
    }
}
