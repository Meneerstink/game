package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.ANIM_BURROW
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.ANIM_EMERGE
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.ANIM_PLAYER_STOMP
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.WYRMS
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.WYRM_IDLE
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.WYRM_TARGET
import gg.rsmod.plugins.content.npcs.definitions.other.Strykewyrms.wyrmFor

/**
 * Strykewyrms (2011). Each wyrm rests as a mound; "Investigate" (667 cache option; Novite used "Stomp") on the mound (Slayer 93 ice / 77 desert
 * / 73 jungle) wakes it. The wyrm melees, casts (anim 12794, projectile 2314: ice freezes 1/10,
 * jungle poisons, desert burns) and burrows under its target (12796 -> 12795) for a 30 hit.
 * When it loses its target it burrows back into a mound. Ids/anims/gfx from the Novite donor
 * Strykewyrm/StrykewyrmCombat; stats from the Matrix 718 definitions.
 */





val table = DropTableFactory

WYRMS.forEach { wyrm ->
    on_npc_option(npc = wyrm.mound, option = "investigate") {
        val npc = player.getInteractingNpc()
        if (npc.getTransmogId() != -1) {
            player.message("The wyrm is already awake.")
            return@on_npc_option
        }
        if (player.skills.getCurrentLevel(Skills.SLAYER) < wyrm.slayer) {
            player.message("You need a Slayer level of at least ${wyrm.slayer} to fight this creature.")
            return@on_npc_option
        }
        if (player.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)) {
            player.message("You are already in combat.")
            return@on_npc_option
        }
        player.queue {
            player.faceTile(npc.tile)
            player.animate(ANIM_PLAYER_STOMP)
            wait(2)
            if (npc.getTransmogId() != -1 || npc.isDead()) return@queue
            wake(npc, wyrm, player)
        }
    }

    set_combat_def(npc = wyrm.mound) {
        configs {
            attackSpeed = 4
            attackStyle = StyleType.CRUSH
            respawnDelay = 60
        }
        stats {
            hitpoints = wyrm.hitpoints
            attack = wyrm.attack
            strength = wyrm.strength
            defence = wyrm.defence
            magic = wyrm.magic
            ranged = 1
        }
        anims {
            attack = 12791
            block = 12792
            death = 12793
        }
        aggro {
            radius = 0
        }
    }

    val drops = table.build {
        guaranteed { obj(Items.BIG_BONES) }
        main {
            total(128)
            obj(Items.COINS_995, quantityRange = 1000..3000, slots = 30)
            obj(Items.RUNITE_ORE, quantityRange = 1..2, slots = 8)
            obj(Items.MAGIC_LOGS, quantityRange = 20..30, slots = 8)
            obj(Items.DEATH_RUNE, quantityRange = 30..60, slots = 10)
            obj(Items.BLOOD_RUNE, quantityRange = 20..40, slots = 8)
            obj(Items.GRIMY_TORSTOL, quantityRange = 1..3, slots = 6)
            obj(Items.MAGIC_SEED, slots = 2)
            table(Gems.gemTable, slots = 10)
            table(Rare.rareTable, slots = 6)
            nothing(40)
        }
    }
    table.register(drops, wyrm.mound)
}

fun wake(npc: Npc, wyrm: Wyrm, target: Pawn) {
    npc.setTransmogId(wyrm.wyrm)
    npc.addBlock(UpdateBlockType.APPEARANCE)
    npc.animate(ANIM_EMERGE)
    npc.attr[WYRM_TARGET] = java.lang.ref.WeakReference(target)
    npc.attr[WYRM_IDLE] = 0
    npc.walkRadius = 6
    npc.world.queue {
        wait(2)
        if (!npc.isDead()) npc.attack(target)
    }
}

fun sleep(npc: Npc) {
    npc.animate(ANIM_BURROW)
    npc.world.queue {
        wait(2)
        npc.setTransmogId(-1)
        npc.addBlock(UpdateBlockType.APPEARANCE)
        npc.walkRadius = 0
        npc.setCurrentLifepoints(npc.getMaximumLifepoints())
        npc.attr.remove(WYRM_TARGET)
        npc.attr.remove(WYRM_IDLE)
    }
}

/**
 * Awake wyrms that have been out of combat for 20 seconds burrow back into their mound.
 */
val WYRM_TIMER = TimerKey()

on_world_init {
    world.timers[WYRM_TIMER] = 10
}

on_timer(WYRM_TIMER) {
    world.npcs.forEach { npc ->
        if (wyrmFor(npc) != null && npc.getTransmogId() != -1 && !npc.isDead()) {
            if (npc.getCombatTarget() == null && !npc.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)) {
                val idle = (npc.attr[WYRM_IDLE] ?: 0) + 10
                npc.attr[WYRM_IDLE] = idle
                if (idle >= 33) sleep(npc)
            } else {
                npc.attr[WYRM_IDLE] = 0
            }
        }
    }
    world.timers[WYRM_TIMER] = 10
}

WYRMS.forEach { wyrm ->
    on_npc_death(wyrm.mound) {
        table.getDrop(world, npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death, npc.id, npc.tile)
    }
    on_npc_spawn(wyrm.mound) {
        npc.setTransmogId(-1)
        npc.walkRadius = 0
    }
}


/**
 * Only awake wyrms can be attacked.
 */
can_attack { attacker, target ->
    if (target is Npc && wyrmFor(target) != null && target.getTransmogId() == -1) {
        if (attacker is Player && attacker.world.plugins.notifyAttackRefusal) attacker.message("The mound stirs, but nothing emerges. Try stomping on it.")
        false
    } else {
        true
    }
}

on_npc_combat(*StrykewyrmCombatScript.ids) {
    npc.queue { StrykewyrmCombatScript.handleSpecialCombat(this) }
}
