package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.model.attr.TEAM_TEST_ACCOUNT_ATTR

/*
 * T3 (owner 2026-10-13): a team test account on the test server (TeamTestAccounts) puts on its preset at every login
 * (TeamAccountPresets). Two ticks later, as the Fight Caves resume does, so the login's own tabs and equipment are sent first.
 */
on_login {
    if (player.attr[TEAM_TEST_ACCOUNT_ATTR] != true) return@on_login
    player.queue {
        wait(2)
        TeamAccountPresets.apply(player)
    }
}
