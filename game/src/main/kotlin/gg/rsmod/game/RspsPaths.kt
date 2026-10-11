package gg.rsmod.game

/**
 * Where the server finds the workspace files that live outside its working directory (traces, crash logs, world edits).
 * Owner 2026-10-10 (VPS): the laptop keeps the `C:/RSPS` workspace; the VPS start script passes `-Drsps.root=<dir>` and
 * `-Drsps.data=<dir>/data` so the same build runs there without a Windows path.
 */
object RspsPaths {
    /** The workspace root: `bugs/`, `launcher-logs/`, `screens/` and the trace switch files. */
    val ROOT: String = (System.getProperty("rsps.root")?.takeIf { it.isNotBlank() } ?: "C:/RSPS").trimEnd('/', '\\')

    /** The game server's own data directory (cfg, cache, saves, world edits). */
    val DATA: String = (System.getProperty("rsps.data")?.takeIf { it.isNotBlank() } ?: "$ROOT/game/game/data").trimEnd('/', '\\')

    fun root(relative: String): String = "$ROOT/$relative"

    fun data(relative: String): String = "$DATA/$relative"
}
