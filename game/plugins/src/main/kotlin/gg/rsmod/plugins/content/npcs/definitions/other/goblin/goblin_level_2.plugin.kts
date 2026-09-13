package gg.rsmod.plugins.content.npcs.definitions.other.goblin

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.plugins.content.drops.DropTableFactory

val unarmed =
    listOf(
        Npcs.GOBLIN_4261,
        Npcs.GOBLIN_4263,
        Npcs.GOBLIN_4264,
        Npcs.GOBLIN_4265,
        Npcs.GOBLIN_4266,
        Npcs.GOBLIN_4267,
        Npcs.GOBLIN_4268,
        Npcs.GOBLIN_4269,
        Npcs.GOBLIN_4270,
        Npcs.GOBLIN_4271,
        // R04.3: level-2 recolour/repeat spawns added this session, no reliable way to tell
        // armed from unarmed without a screenshot - defaulted to unarmed (the more common
        // variant above: 10 unarmed vs 1 armed).
        Npcs.GOBLIN_11233,
        Npcs.GOBLIN_11235,
        Npcs.GOBLIN_11237,
        Npcs.GOBLIN_11239,
        Npcs.GOBLIN_11241,
        Npcs.GOBLIN_12354,
        Npcs.GOBLIN_12356,
        Npcs.GOBLIN_13247,
        Npcs.GOBLIN_13248,
        Npcs.GOBLIN_13249,
        Npcs.GOBLIN_13250,
        Npcs.GOBLIN_4272,
        Npcs.GOBLIN_4273,
        Npcs.GOBLIN_4274,
        Npcs.GOBLIN_4275,
        Npcs.GOBLIN_4276,
        Npcs.GOBLIN_8637,
        Npcs.GOBLIN_8638,
    )
val armed = listOf(Npcs.GOBLIN_4262)
val ids = (unarmed + armed).toIntArray()

val table = DropTableFactory
val goblin =
    table.build {
        guaranteed {
            obj(Items.BONES)
        }

        main {
            total(1024)
            obj(Items.BRONZE_SQ_SHIELD, slots = 24)
            obj(Items.BRONZE_SPEAR, slots = 32)
            obj(Items.SLING, quantity = 1, slots = 32)

            obj(Items.WATER_RUNE, quantity = 6, slots = 48)
            obj(Items.BODY_RUNE, quantity = 7, slots = 40)
            obj(Items.EARTH_RUNE, quantity = 4, slots = 24)
            obj(Items.BRONZE_BOLTS, quantity = 8, slots = 24)

            obj(Items.COINS_995, quantity = 5, slots = 224)
            obj(Items.COINS_995, quantity = 9, slots = 24)
            obj(Items.COINS_995, quantity = 15, slots = 24)
            obj(Items.COINS_995, quantity = 20, slots = 16)
            obj(Items.COINS_995, quantity = 1, slots = 8)

            nothing(slots = 272)

            obj(Items.HAMMER, quantity = 1, slots = 120)
            obj(Items.GOBLIN_BOOK, quantity = 1, slots = 16)
            obj(Items.GOBLIN_MAIL, quantity = 1, slots = 40)
            obj(Items.CHEFS_HAT, quantity = 1, slots = 24)
            obj(Items.BEER, quantity = 1, slots = 16)
            obj(Items.BRASS_NECKLACE, quantity = 1, slots = 8)
            obj(Items.AIR_TALISMAN, quantity = 1, slots = 8)
        }
        table("Charms") {
            total(1024)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 82)
            obj(Items.GREEN_CHARM, quantity = 1, slots = 21)
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 9)
            obj(Items.BLUE_CHARM, quantity = 1, slots = 1)
            nothing(slots = 911)
        }
        table("Tertiary") {
            total(1024)
            obj(Items.CLUE_SCROLL_EASY, quantity = 1, slots = 6)
            nothing(1018)
        }
    }

table.register(goblin, *ids)

on_npc_pre_death(*ids) {
    val p = npc.damageMap.getMostDamage()!! as Player
    if (!gg.rsmod.plugins.content.combat.audio.NpcCombatAudio.hasDeathSound(npc.id)) p.playSound(Sfx.GOBLIN_DEATH)
}

on_npc_death(*ids) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 35
        }
        stats {
            hitpoints = 50
            attack = if (it in unarmed) 1 else 3
            strength = 1
            defence = if (it in unarmed) 1 else 4
            magic = 1
            ranged = 1
        }
        bonuses {
            attackStab = -21
            attackCrush = -15
            defenceStab = -15
            defenceSlash = -15
            defenceCrush = -15
            defenceMagic = -15
            defenceRanged = -15
        }
        anims {
            attack = Anims.GOBLIN_ATTACK
            death = Anims.GOBLIN_DEATH
            block = Anims.GOBLIN_BLOCK
        }
        slayer {
            assignment = SlayerAssignment.GOBLIN
            level = 1
            experience = 5.0
        }
    }
}
