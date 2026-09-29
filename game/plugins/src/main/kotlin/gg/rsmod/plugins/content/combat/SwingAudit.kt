package gg.rsmod.plugins.content.combat

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment

/**
 * Debug toolkit (C:\RSPS\TRACES.md, owner 2026-09-28 "veel combat bugs pvp/pvm en sound bugs en veel visueel"): after every
 * player swing (normal attack, special, spell) the combat loop calls [after], which logs what the swing showed and heard and
 * names what is missing:
 *  - PROBLEM swing without an animation (the player stands still while hitting);
 *  - PROBLEM swing animation that does not exist in the cache;
 *  - PROBLEM silent swing: no sound to the player, no area sound at the attacker or target, and no audible frame sound on the
 *    animation (the "no weapon sound" class, e.g. imported OSRS weapons without their sound).
 * Npc swings are audited in NpcAttacks (avtrace/breachtrace `PROBLEM npcattack`).
 */
object SwingAudit {
    fun after(
        pawn: Pawn,
        target: Pawn,
        kind: String,
    ) {
        if (pawn !is Player || !Trace.enabled(Trace.Cat.COMBAT)) return
        val world = pawn.world
        val cycle = world.currentCycle
        val weapon = pawn.getEquipment(EquipmentType.WEAPON)?.id ?: -1
        val anim = pawn.pendingAnimation()
        val animDef = if (anim >= 0) runCatching { world.definitions.getNullable(AnimDef::class.java, anim) }.getOrNull() else null
        // Sound audit 2026-09-29: the swinging player's own client plays every frame sound of his animation, radius 0 included
        // (unarmed punch/kick 422/423 carry 4277/4281...), so only an animation without any frame sound is silent for him.
        val frameSound = animDef?.hasFrameSounds == true
        val t = pawn.tile
        val tt = target.tile
        val heard =
            Trace.soundNoted("p:${pawn.username}", cycle) ||
                Trace.soundNoted("t:${t.x},${t.z},${t.height}", cycle) ||
                Trace.soundNoted("t:${tt.x},${tt.z},${tt.height}", cycle) ||
                frameSound
        val targetName = (target as? Player)?.username ?: (target as? Npc)?.let { "npc ${it.id}#${it.index}" } ?: "$target"
        val style = runCatching { CombatConfigs.getCombatClass(pawn).name }.getOrDefault("?")
        Trace.log(Trace.Cat.COMBAT) { "swing ${pawn.username} -> $targetName kind=$kind class=$style weapon=$weapon anim=$anim frameSound=$frameSound sound=$heard" }
        if (anim < 0) {
            Trace.problem(Trace.Cat.COMBAT) { "swing without animation: ${pawn.username} weapon=$weapon kind=$kind class=$style -> $targetName" }
        } else if (animDef == null) {
            Trace.problem(Trace.Cat.COMBAT) { "swing animation $anim does not exist in the cache: ${pawn.username} weapon=$weapon kind=$kind" }
        }
        if (!heard) {
            Trace.problem(Trace.Cat.COMBAT) { "silent swing: ${pawn.username} weapon=$weapon kind=$kind class=$style anim=$anim - no sound to the player, no area sound, no frame sound" }
        }
    }
}
