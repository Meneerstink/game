package gg.rsmod.plugins.content.areas.home

import gg.rsmod.plugins.content.combat.Combat

val homeTile = world.gameContext.home
val bankTile = HomeLayout.bank.tile(homeTile)
val safeArea = BountyHunterHome.safeArea(homeTile)

spawn_obj(obj = Objs.BANK_CHEST_42192, x = bankTile.x, z = bankTile.z, height = bankTile.height, rot = 1)
spawn_obj(obj = 2738, x = bankTile.x, z = bankTile.z - 1, height = bankTile.height, type = 22)

on_enter_simple_polygon_area(safeArea) {
    Combat.reset(player)
    player.resetFacePawn()
}
