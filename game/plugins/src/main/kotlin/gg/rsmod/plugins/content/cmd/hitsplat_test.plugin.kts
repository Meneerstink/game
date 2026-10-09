package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.combat.dealHit

/** Ticks before the first splat (time to close the console and start a capture) and between two splats. */
val START_DELAY = 6
val STEP_TICKS = 5

/**
 * `hitsplats` (owner 2026-10-09, OSRS hitsplats): shows every OSRS hitsplat this server sends, for a client check against OSRS.
 *  - `hitsplats self`: on you, no source (you are the target: lit) - block 12, damage 16, poison 65, venom 5, disease 4, heal 6,
 *    burn 74, recoil 16, then four damage splats in one tick (the four OSRS slots).
 *  - `hitsplats npc`: on a spawned npc next to you - without a source you are a third party (tint 17 / block 13 / poison 66),
 *    then your own hits on it (lit 16, block 12, max 43 at 30 >= your max 30).
 * Lifepoints are restored after every hit; the npc is removed at the end.
 */
on_command("hitsplats", Privilege.ADMIN_POWER) {
    val which = player.getCommandArgs().getOrNull(0)?.lowercase() ?: "self"
    val p = player
    if (which == "npc") {
        val npc = Npc(1, p.tile.transform(1, 0), world)
        world.spawn(npc)
        TestSpawnRegistry.trackNpc(npc)
        // Never fights back, never dies during the test (lifepoints above its maximum show a full bar).
        npc.lock = gg.rsmod.game.model.LockState.FULL
        world.queue {
            fun heal() = npc.setCurrentLifepoints(1000)
            heal()
            wait(START_DELAY)
            val steps: List<Pair<String, () -> Unit>> =
                listOf(
                    "no source 5 (tint 17)" to { npc.hit(damage = 5) },
                    "no source 0 (block tint 13)" to { npc.hit(damage = 0) },
                    "no source poison 6 (tint 66)" to { npc.hit(damage = 6, type = HitType.POISON) },
                    "your hit 7 (lit 16)" to { p.dealHit(target = npc, minHit = 7.0, maxHit = 7.01, landHit = true, delay = 0, hitType = HitType.MELEE) },
                    "your miss (block lit 12)" to { p.dealHit(target = npc, maxHit = 30.0, landHit = false, delay = 0, hitType = HitType.MELEE) },
                    "your max 30 (max 43)" to { p.dealHit(target = npc, minHit = 30.0, maxHit = 30.01, landHit = true, delay = 0, hitType = HitType.MELEE) },
                )
            for ((label, step) in steps) {
                if (!p.isOnline || npc.isDead()) break
                p.message("hitsplats npc: $label")
                step()
                wait(STEP_TICKS)
                heal()
            }
            wait(2)
            if (npc.isSpawned()) world.remove(npc)
        }
        return@on_command
    }
    p.queue {
        fun heal() = p.setCurrentLifepoints(p.getMaximumLifepoints())
        wait(START_DELAY)
        val steps: List<Pair<String, () -> Unit>> =
            listOf(
                "block (12)" to { p.hit(damage = 0) },
                "damage 15 (16)" to { p.hit(damage = 15) },
                "poison 6 (65)" to { p.hit(damage = 6, type = HitType.POISON) },
                "venom 8 (5)" to { p.hit(damage = 8, type = HitType.VENOM) },
                "disease 2 (4)" to { p.hit(damage = 2, type = HitType.DISEASE) },
                "heal 9 (6)" to { p.hit(damage = 0, type = HitType.HEAL).hitmarks[0].damage = 9 },
                "burn 3 (74)" to { p.hit(damage = 3, type = HitType.BURN) },
                "recoil 4 (16)" to { p.hit(damage = 4, type = HitType.REFLECTED) },
                "four at once (slots)" to { listOf(11, 22, 33, 4).forEach { p.hit(damage = it) } },
            )
        for ((label, step) in steps) {
            p.message("hitsplats self: $label")
            step()
            wait(STEP_TICKS)
            heal()
        }
    }
}
