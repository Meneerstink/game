package gg.rsmod.plugins.content.daily

on_npc_completed { killer, npc -> DailyObjectives.onKill(killer, npc) }
