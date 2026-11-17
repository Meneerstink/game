package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.priv.Privilege

/**
 * GRAFISCH LAB - VASTE CAMERA (dossier GRAFISCH-TOOLING.txt, 90-min-plan stap 2). Alleen ADMIN (testserver/check78).
 *
 * ::camat <dx> <dz> <h> <ldx> <ldz> <lh>
 *   Zet de camera op (speler + dx, speler + dz) op hoogte h en laat hem kijken naar (speler + ldx, speler + ldz) op hoogte lh.
 *   Alles relatief aan de spelertegel, zodat dezelfde stand in elke instance (CoX/ToA) hetzelfde beeld geeft.
 *   De client verwacht tegels BINNEN het geladen kaartstuk (CAM_MOVETO/CAM_LOOKAT x/z = 1 byte, client ServerConnectionReader
 *   CAM_MOVETO/CAM_LOOKAT): kaartstuk-tegel = wereldtegel - lastKnownRegionBase (zelfde rekensom als SetMapFlag, Pawn.kt:639).
 *   Hoogte: client doet h << 2 en trekt dat af van de grondhoogte (Camera.moveTo/lookAt) = h * 4 eenheden boven de grond (tegel = 512).
 *   rate/speed 100 = meteen (snap, Camera.java:297/483). Terug naar normaal: ::camreset.
 */
on_command("camat", Privilege.ADMIN_POWER) {
    // Optioneel eerste argument '@x,z' = vast ANKER (wereldtegel) i.p.v. de speler: een speler die terugvecht loopt weg tijdens de foto's
    // (16-11 20:3x Dark altar: 'na'-foto's stonden elders dan 'voor').
    val all = player.getCommandArgs().toList()
    val anchor = all.firstOrNull()?.takeIf { it.startsWith("@") }?.drop(1)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }
    val a = if (anchor != null) all.drop(1) else all
    if (a.size < 6) {
        player.message("::camat [@x,z] dx dz h ldx ldz lh  (relatief aan jouw tegel of aan het anker; ::camreset = terug)")
        return@on_command
    }
    val v = a.take(6).map { it.toIntOrNull() ?: run { player.message("camat: geen getal '$it'"); return@on_command } }
    val base = player.lastKnownRegionBase ?: run { player.message("camat: geen kaartstuk"); return@on_command }
    val px = (anchor?.get(0) ?: player.tile.x) - base.x
    val pz = (anchor?.get(1) ?: player.tile.z) - base.z
    val cx = px + v[0]
    val cz = pz + v[1]
    val lx = px + v[3]
    val lz = pz + v[4]
    if (listOf(cx, cz, lx, lz).any { it !in 0..103 }) {
        player.message("camat: buiten het geladen kaartstuk (cam $cx,$cz look $lx,$lz; speler $px,$pz)")
        return@on_command
    }
    player.moveCameraTo(100, cx, cz, v[2], 0)
    player.cameraLookAt(lx, lz, v[5], 0, 100)
    player.message("camat ok cam=$cx,$cz,${v[2]} look=$lx,$lz,${v[5]} speler=$px,$pz tile=${player.tile.x},${player.tile.z},${player.tile.height}")
}
