package gg.rsmod.plugins.content.areas.wilderness

on_obj_option(obj = Objs.LADDER_1765, option = "climb-down") {
    player.handleLadder(x = 3069, z = 10257, height = 0, underground = true)
}

on_obj_option(obj = Objs.LADDER_1767, option = "climb-down") {
    player.handleLadder(x = 3017, z = 10249, height = 0, underground = true)
}
