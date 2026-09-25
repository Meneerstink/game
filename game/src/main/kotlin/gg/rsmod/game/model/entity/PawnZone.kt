package gg.rsmod.game.model.entity

import gg.rsmod.game.model.Tile

/**
 * The tile every zone rule (guarded city, PvP area, danger warning) must judge: the tile the client shows. During a transit
 * ([Pawn.beginTransit], [Pawn.obstacleUntilUnlocked]) that is where it began, so a skulled player crossing a shortcut into a guarded
 * city is not an intruder before he is visibly across (owner 2026-09-24: guards attacked while the player was still on the
 * shortcut). An extension, so it reads the pawn's own [Pawn.tile] and transit state and works the same on any pawn.
 */
fun Pawn.zoneTile(): Tile = if (inTransit()) transitOrigin ?: tile else tile
