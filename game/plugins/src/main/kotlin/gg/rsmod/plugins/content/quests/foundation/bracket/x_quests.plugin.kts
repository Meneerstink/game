package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.combat.StyleType

// PROJECT48 X1-X6 (run 3, 22-11): combat definitions of the imported quest foes. Stats, bonuses, attack speed and style: the OSRS Wiki
// monster infoboxes (saved in team\dossiers\PROJECT48-notities\wiki, 22-11); hitpoints x10 as on this server (Secrets of the North does
// the same); ranged defence = the wiki's light-ranged defence. The import dropped these stats (OsrsNpcImportTool p48x), so they are here.
// Owned quest foes: respawnDelay 0 (QuestFights spawns and removes them).

set_combat_def(npc = XNpcs.BASILISK_YOUNGLING) {
    configs { attackSpeed = 4; attackStyle = StyleType.SLASH; respawnDelay = 0 }
    stats { hitpoints = 600; attack = 25; strength = 40; defence = 70; magic = 1; ranged = 1 }
    bonuses { defenceStab = 20; defenceSlash = 20; defenceCrush = 0; defenceMagic = 20; defenceRanged = 0 }
    anims { attack = XFoes.BASILISK_ATTACK; death = XFoes.BASILISK_DEATH }
}

set_combat_def(npc = XNpcs.MONSTROUS_BASILISK) {
    configs { attackSpeed = 4; attackStyle = StyleType.SLASH; respawnDelay = 0 }
    stats { hitpoints = 1700; attack = 88; strength = 98; defence = 130; magic = 1; ranged = 1 }
    bonuses { defenceStab = 35; defenceSlash = 35; defenceCrush = 0; defenceMagic = 35; defenceRanged = 0 }
    anims { attack = XFoes.BASILISK_ATTACK; death = XFoes.BASILISK_DEATH }
}

set_combat_def(npc = XNpcs.TYPHOR) {
    configs { attackSpeed = 4; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 3600; attack = 186; strength = 186; defence = 186; magic = 186; ranged = 1 }
    bonuses { defenceStab = 30; defenceSlash = 30; defenceCrush = 0; defenceMagic = 30; defenceRanged = 0 }
    anims { attack = XFoes.TYPHOR_MELEE; death = XFoes.TYPHOR_DEATH }
}

set_combat_def(npc = XNpcs.JORMUNGAND) {
    configs { attackSpeed = 5; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 6000; attack = 214; strength = 214; defence = 214; magic = 214; ranged = 214 }
    bonuses { defenceStab = 50; defenceSlash = 50; defenceCrush = 10; defenceMagic = 50; defenceRanged = 50 }
    anims { attack = XFoes.JORMUNGAND_MELEE; death = XFoes.JORMUNGAND_DEATH }
}

set_combat_def(npc = XNpcs.ABOMINATION) {
    configs { attackSpeed = 5; attackStyle = StyleType.SLASH; respawnDelay = 0 }
    stats { hitpoints = 2000; attack = 110; strength = 110; defence = 110; magic = 110; ranged = 110 }
    bonuses { defenceStab = 80; defenceSlash = 80; defenceCrush = 80; defenceMagic = 20; defenceRanged = 180 }
    anims { attack = XFoes.ABOMINATION_MELEE; death = XFoes.ABOMINATION_DEATH }
}

set_combat_def(npc = XNpcs.ARAXYTE) {
    configs { attackSpeed = 4; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 600; attack = 80; strength = 125; defence = 60; magic = 60; ranged = 1 }
    bonuses { defenceStab = 60; defenceSlash = 30; defenceCrush = 20; defenceMagic = 20; defenceRanged = 20 }
    anims { attack = XFoes.ARAXYTE_ATTACK; death = XFoes.ARAXYTE_DEATH }
}

set_combat_def(npc = XNpcs.HESPORI) {
    configs { attackSpeed = 6; attackStyle = StyleType.RANGED; respawnDelay = 0 }
    stats { hitpoints = 3600; attack = 1; strength = 1; defence = 120; magic = 126; ranged = 150 }
    bonuses { attackMagic = 150; attackRanged = 150; defenceStab = 60; defenceSlash = 20; defenceCrush = 60; defenceMagic = 80; defenceRanged = 80 }
    anims { attack = XFoes.HESPORI_ATTACK; death = XFoes.HESPORI_DEATH }
}

set_combat_def(npc = XNpcs.MOTHER) {
    configs { attackSpeed = 4; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 2350; attack = 150; strength = 150; defence = 170; magic = 60; ranged = 105 }
    bonuses { attackBonus = 60; strengthBonus = 50; defenceMagic = 200 }
    anims { attack = XFoes.TROLL_MELEE; death = XFoes.TROLL_DEATH }
}

set_combat_def(npc = XNpcs.DONT_KNOW_WHAT) {
    configs { attackSpeed = 3; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 2200; attack = 120; strength = 90; defence = 160; magic = 60; ranged = 115 }
    bonuses { defenceMagic = 200 }
    anims { attack = XFoes.TROLL_MELEE; death = XFoes.TROLL_DEATH }
}

set_combat_def(npc = XNpcs.RANIS_DRAKAN) {
    configs { attackSpeed = 5; attackStyle = StyleType.SLASH; respawnDelay = 0 }
    stats { hitpoints = 4000; attack = 120; strength = 120; defence = 120; magic = 120; ranged = 1 }
    bonuses { strengthBonus = 34; defenceStab = 60; defenceSlash = 60; defenceCrush = 60; defenceMagic = 60; defenceRanged = 60 }
    anims { attack = XFoes.RANIS_CLAW; death = XFoes.RANIS_DEATH }
}

on_npc_combat(*XFoes.ids) {
    npc.queue { XFoes.handleSpecialCombat(this) }
}
