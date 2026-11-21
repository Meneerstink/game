package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.SoulflameHorn

/** PROJECT48 DMM-ITEMS C1: the Soulflame horn's Entice special fires from the special bar (rules in [SoulflameHorn]). */
SpecialAttacks.registerInstant(SoulflameHorn.COST_PER_ALLY, SoulflameHorn.ID) { player -> SoulflameHorn.use(player) }
