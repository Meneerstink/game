package gg.rsmod.plugins.content.areas.home

/**
 * R14.3/R02.1: four real exit markers at the home enclave's safe-boundary edges, one real
 * object per cardinal direction, leading to whatever actual terrain already surrounds this
 * verified BH-bank hub - not a teleport. Reuses the verified [Objs.GATE] cache object (photo
 * 2's glow effect is not reproducible without a confirmed client-side cosmetic asset - art
 * simplification, per R14.1's explicit allowance).
 *
 * Known gap (see OWNER_TASK_STATUS.md R02.1): placed with default (non-blocking) collision, no
 * open/closed object-swap animation - walking past them already works today since there is no
 * wall here yet to block it. The continuous solid wall perimeter connecting them, and a real
 * open/close swap, are NOT yet built: correct wall-segment collision/rotation and the gate's
 * paired open-state object id cannot be verified without an in-game screenshot in this
 * environment. The mechanical safe boundary ([BountyHunterHome.SAFE_RADIUS]) already works
 * independently of the visible wall.
 */
val home = world.gameContext.home
val gateTiles = BountyHunterHome.gateTiles(home)

gateTiles.forEach { gate ->
    spawn_obj(obj = Objs.GATE, x = gate.x, z = gate.z, height = gate.height, type = 0, rot = 0)
}

// Confirmed live this session: Objs.GATE's only real cache option is "Open" (not a "cross"
// pass-through - that was this file's first, wrong, unverified guess). No open/closed object
// swap or collision change is wired yet - see the file header's known-gap note.
on_obj_option(obj = Objs.GATE, option = "open") {
    player.message("The magical barrier shimmers as you pass through.")
}
