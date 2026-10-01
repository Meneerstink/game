package gg.rsmod.plugins.content.mechanics.aid

/** Accept Aid starts ON for every account, new and existing (owner 2026-10-01); see [AcceptAid.applyDefault]. */
on_login {
    AcceptAid.applyDefault(player)
}
