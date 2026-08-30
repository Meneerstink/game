package gg.rsmod.plugins.content.daily

on_npc_killed { killer, npc -> DailyObjectives.onKill(killer, npc) }
