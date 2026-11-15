package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The raid's gourd potions (OSRS Wiki Chambers of Xeric 'Supplies' table + Overload (Chambers of Xeric)): Elder (Attack/Strength/Defence),
 * Twisted (Ranged), Kodai (Magic), Revitalisation (restores stats and Prayer), Prayer enhance (prayer over time), Xeric's aid (works as a
 * Saradomin brew), Overload (all combat stats by 4+10% / 5+13% / 6+16% for weak / normal / strong, damaging 50, reapplied every 15 seconds
 * for 5 minutes, then healing 50). Item ids: 4 doses ascending per strength (-) / normal / (+) from the first-dose ids below (cache names).
 * Only usable inside the raid (tsps message 'You can only sip this potion while inside the Chambers of Xeric.'); the gourd is gone with the
 * last sip.
 * ONZEKER (donor tsps Potions.plugin.js, not on the Wiki per strength): Elder/Twisted/Kodai flat+percent = the overload's per strength;
 * Revitalisation restores 6+20% / 8+25% / 11+30%; Prayer enhance a point every 6 ticks for 268 / 375 / 483 ticks; Xeric's aid +10/15/20%
 * Defence and Hitpoints, -5/7.5/10% of the others (+2). LATER: the raid's antipoisons (25754-25765, not imported) and potion points.
 */
object CoxPotions {
    enum class Kind(val firstDose: IntArray) {
        ELDER(intArrayOf(20913, 20917, 20921)), TWISTED(intArrayOf(20925, 20929, 20933)), KODAI(intArrayOf(20937, 20941, 20945)),
        REVITALISATION(intArrayOf(20949, 20953, 20957)), PRAYER_ENHANCE(intArrayOf(20961, 20965, 20969)),
        XERICS_AID(intArrayOf(20973, 20977, 20981)), OVERLOAD(intArrayOf(20985, 20989, 20993)),
    }

    data class Dose(val kind: Kind, val tier: Int, val doses: Int, val osrs: Int)

    private val FLAT = intArrayOf(4, 5, 6)
    private val PERCENT = intArrayOf(10, 13, 16)
    private val RESTORE_FLAT = intArrayOf(6, 8, 11)
    private val RESTORE_PERCENT = intArrayOf(20, 25, 30)
    private val ENHANCE_TICKS = intArrayOf(268, 375, 483)
    private val AID_UP = intArrayOf(100, 150, 200)
    private val AID_DOWN = intArrayOf(50, 75, 100)

    /** OSRS id -> dose. */
    val DOSES: Map<Int, Dose> = Kind.values().flatMap { k -> k.firstDose.flatMapIndexed { tier, first -> (0..3).map { d -> first + d to Dose(k, tier, d + 1, first + d) } } }.toMap()

    fun local(d: Dose): Int? = CoxIds.item(d.osrs)

    private class Overload(var endsAt: Int, var nextBoostAt: Int, val tier: Int)
    private class Enhance(val endsAt: Int, var nextAt: Int)
    private val overloads = ConcurrentHashMap<String, Overload>()
    private val enhances = ConcurrentHashMap<String, Enhance>()

    private val COMBAT = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)

    private fun boost(p: Player, skill: Int, flat: Int, percent: Int) {
        val amount = flat + p.skills.getMaxLevel(skill) * percent / 100
        p.skills.alterCurrentLevel(skill, amount, amount)
    }

    fun drink(p: Player, slot: Int, osrs: Int) {
        val d = DOSES[osrs] ?: return
        val local = local(d) ?: return
        if (p.inventory[slot]?.id != local) return
        if (p.timers.has(POTION_DELAY)) return
        if (CoxRaids.raidAt(p.tile) == null) { p.message("You can only sip this potion while inside the Chambers of Xeric."); return }
        if (d.kind == Kind.OVERLOAD && p.getCurrentLifepoints() <= 50) { p.message("You need more than 50 Hitpoints to survive the power of the overload."); return }
        if (!p.inventory.remove(local, 1, beginSlot = slot).hasSucceeded()) return
        if (d.doses > 1) CoxIds.item(d.osrs - 1)?.let { p.inventory.add(it, 1, beginSlot = slot) }
        p.animate(Anims.EAT_FOOD)
        p.playSound(Sfx.LIQUID)
        p.timers[POTION_DELAY] = 3
        p.timers[FOOD_DELAY] = 3
        apply(p, d)
        val name = p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, local).name.replace(Regex(" ?\\(([1234])\\)$"), "").lowercase()
        p.filterableMessage("You drink some of your $name.")
        p.filterableMessage(if (d.doses == 1) "You have finished your potion." else if (d.doses - 1 == 1) "You have 1 dose of potion left." else "You have ${d.doses - 1} doses of potion left.")
    }

    private fun apply(p: Player, d: Dose) {
        val t = d.tier
        when (d.kind) {
            Kind.ELDER -> intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { boost(p, it, FLAT[t], PERCENT[t]) }
            Kind.TWISTED -> boost(p, Skills.RANGED, FLAT[t], PERCENT[t])
            Kind.KODAI -> boost(p, Skills.MAGIC, FLAT[t], PERCENT[t])
            Kind.REVITALISATION -> {
                p.restorePrayer(RESTORE_FLAT[t] + p.skills.getMaxLevel(Skills.PRAYER) * RESTORE_PERCENT[t] / 100)
                for (s in 0..24) {
                if (s == Skills.CONSTITUTION) continue
                val base = p.skills.getMaxLevel(s)
                val cur = p.skills.getCurrentLevel(s)
                if (cur < base) p.skills.setCurrentLevel(s, minOf(base, cur + RESTORE_FLAT[t] + base * RESTORE_PERCENT[t] / 100))
            }
            }
                        Kind.PRAYER_ENHANCE -> enhances[p.username.lowercase()] = Enhance(p.world.currentCycle + ENHANCE_TICKS[t], p.world.currentCycle + 6)
            Kind.XERICS_AID -> {
                val up = AID_UP[t]; val down = AID_DOWN[t]
                val def = 2 + p.skills.getMaxLevel(Skills.DEFENCE) * up / 1000
                p.skills.alterCurrentLevel(Skills.DEFENCE, def, def)
                val hpMax = p.getMaximumLifepoints()
                val heal = hpMax * up / 1000
                p.setCurrentLifepoints(minOf(hpMax + heal, p.getCurrentLifepoints() + heal))
                intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.MAGIC, Skills.RANGED).forEach { s ->
                    val cur = p.skills.getCurrentLevel(s)
                    p.skills.setCurrentLevel(s, maxOf(0, cur - (2 + cur * down / 1000)))
                }
            }
            Kind.OVERLOAD -> {
                COMBAT.forEach { boost(p, it, FLAT[t], PERCENT[t]) }
                overloads[p.username.lowercase()] = Overload(p.world.currentCycle + 500, p.world.currentCycle + 25, t)
                p.queue { repeat(5) { if (p.getCurrentLifepoints() > 10) p.hit(10); wait(1) } }
            }
        }
    }

    /** Every tick (cox_raid.plugin.kts): overloads reapply every 15 seconds for 5 minutes then heal 50; prayer enhance a point every 6 ticks. */
    fun tick(world: gg.rsmod.game.model.World) {
        val now = world.currentCycle
        for ((name, o) in overloads.entries.toList()) {
            val p = world.getPlayerForName(name) ?: continue
            if (now >= o.endsAt) {
                overloads.remove(name)
                COMBAT.forEach { s -> if (p.skills.getCurrentLevel(s) > p.skills.getMaxLevel(s)) p.skills.setCurrentLevel(s, p.skills.getMaxLevel(s)) }
                p.heal(50)
                p.message("<col=ef1020>The effects of overload have worn off, and you feel normal again.</col>")
                continue
            }
            if (now >= o.nextBoostAt) { o.nextBoostAt = now + 25; COMBAT.forEach { boost(p, it, FLAT[o.tier], PERCENT[o.tier]) } }
        }
        for ((name, e) in enhances.entries.toList()) {
            val p = world.getPlayerForName(name) ?: continue
            if (now >= e.endsAt) { enhances.remove(name); continue }
            if (now >= e.nextAt) {
                e.nextAt = now + 6
                p.restorePrayer(1)
            }
        }
    }

    /** Leaving the raid ends the raid's potion effects. */
    fun clear(p: Player) { overloads.remove(p.username.lowercase()); enhances.remove(p.username.lowercase()) }
}
