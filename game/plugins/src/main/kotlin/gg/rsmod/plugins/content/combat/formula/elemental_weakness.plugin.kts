package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.Server.Companion.logger

/* Loads the OSRS monster elemental weaknesses (see [ElementalWeakness]). */
on_world_init {
    ElementalWeakness.load()
    logger.info("Elemental weakness: loaded {} monster weaknesses.", ElementalWeakness.size)
}
