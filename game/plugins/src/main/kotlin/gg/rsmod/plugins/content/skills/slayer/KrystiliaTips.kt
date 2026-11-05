package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.combat.SlayerAssignment

/**
 * N522 nachtrun deel 3: "Got any tips for me?" - the exact OSRS Slayer task tips (OSRS Wiki Transcript:Slayer task tips, 2026-11-05), one per
 * Krystilia task (Transcript:Krystilia: "Krystilia provides the corresponding Slayer task tip", then "You've got to do the task in the
 * Wilderness."). A task without a tip on the Wiki gets only her fixed line.
 */
object KrystiliaTips {
    val TIPS: Map<SlayerAssignment, String> =
        mapOf(
        SlayerAssignment.ABYSSAL_DEMON to "Abyssal Demons are nasty creatures to fight. They aren't really part of this realm, and are able to move very quickly to trap their prey.",
        SlayerAssignment.ANKOU to "Ankou are undead skeletal ghosts. Their blows can be powerful but they have limited defences, so killing them quickly is often the best way to protect yourself.",
        SlayerAssignment.AVIANSIE to "Aviansies are bird-like creatures found in the icy dungeons of the north. Melee weapons can't reach them, so use Magic or Ranged attacks.",
        SlayerAssignment.BANDIT to "Bandits band together for protection against enemies such as yourself.",
        SlayerAssignment.BEAR to "Bears are tough creatures and fierce fighters, watch out for their powerful claws.",
        SlayerAssignment.BLACK_DEMON to "Powerful weapons known as demonbane weapons can be used to quickly defeat even powerful demons like the Black Demon with greater ease.",
        SlayerAssignment.BLACK_DRAGON to "Black Dragons are the strongest dragons and very fierce, watch out for their fiery breath. Those with higher slayer knowledge and who seek a much tougher, but substantially more rewarding foe, may wish to fight Brutal versions of these creatures in the Kourend Catacombs.",
        SlayerAssignment.BLACK_KNIGHT to "Black Knights like using black armour and weapons, this makes them susceptible to magic attacks.",
        SlayerAssignment.BLOODVELD to "Bloodveld are strange demonic creatures, their long rasping tongue has magical properties so it pierces straight through physical defences.",
        SlayerAssignment.CHAOS_DRUID to "Chaos Druids are druids who've turned to evil. Some of them have spells that can harm you, or even teleport you back to them if you flee.",
        SlayerAssignment.DARK_WARRIOR to "Dark Warriors wear protective armour. If you want to fight them with melee, try crush-based attacks. Or try magic instead.",
        SlayerAssignment.DUST_DEVIL to "Dust Devils use clouds of dust, sand, ash, and whatever else they can inhale to blind and disorientate their victims. Make sure to wear a facemask when fighting them.",
        SlayerAssignment.EARTH_WARRIOR to "Earth Warriors are a kind of earth elemental, grind them to dust with blunt weapons.",
        SlayerAssignment.ENT to "Ents are walking tree-people. Bring a hatchet to chop their wood once you've slain them.",
        SlayerAssignment.FIRE_GIANT to "Like other giants, Fire Giants have little to no magical ability, and they're particularly weak to water spells.",
        SlayerAssignment.GREATER_DEMON to "Demons are particularly weak to water spells. Though Greater Demons are not the strongest type of demon they are still dangerous.",
        SlayerAssignment.GREEN_DRAGON to "Like all dragons, Green Dragons have thick scales and a powerful fiery breath. Unless you can stay out of their reach, bringing dragonfire protection is a must.",
        SlayerAssignment.HELLHOUNDS to "Hellhounds are a cross between Dogs and Demons. They are dangerous with a fierce bite, but have all of the same weaknesses as most demons.",
        SlayerAssignment.HILL_GIANT to "Hill Giants often wield large weapons, learn to recognise what kind of weapon it is and act accordingly.",
        SlayerAssignment.ICE_GIANT to "Like other giants, Ice Giants have little to no magical ability, and they're particularly weak to fire spells.",
        SlayerAssignment.ICE_WARRIOR to "Ice Warriors are a kind of ice elemental, shatter them with blunt weapons or melt them with fire.",
        SlayerAssignment.JELLY to "Jellies are nasty cube-like gelatinous creatures which absorb everything they come across into themselves. Their magical acids bypass physical defences.",
        SlayerAssignment.LAVA_DRAGON to "Lava dragons live in a volcanic area of the Wilderness. Their bones are considered highly worthy offerings by the gods.",
        SlayerAssignment.LESSER_DEMON to "Demons are weak to magical attacks. Though Lesser Demons are relatively weak they are still dangerous.",
        SlayerAssignment.MAGIC_AXE to "Magic Axes have a mind of their own. You may find magic or slash-based melee attacks are slightly more effective against them.",
        SlayerAssignment.MAMMOTH to "Mammoths plod around the Wilderness. They're big and not very smart, so you can probably trap them on the terrain and kill them from afar.",
        SlayerAssignment.MOSS_GIANT to "Like other giants, Moss Giants have little to no magical ability, and they're particularly weak to fire spells.",
        SlayerAssignment.NECHRYAEL to "Nechryael are demons of decay which summon small winged beings to help them fight their victims. Their minions can bypass protective prayers so be sure to wear thick armour.",
        SlayerAssignment.PIRATE to "Pirates can be found near the sea. They like to collect coins among other things.",
        SlayerAssignment.REVENANT to "Revenants can be found in the Wilderness, residing in the deepest cavern of the Forinthry dungeon. They use all three styles of combat, but a charged Revenant bracelet will protect you from their attacks.",
        SlayerAssignment.ROGUE to "Rogues are found in lawless areas, such as the deep Wilderness. They can be very protective of their loot.",
        SlayerAssignment.SCORPION to "Scorpions are almost always poisonous, their hard carapace makes them resistant to crushing and stabbing attacks.",
        SlayerAssignment.SKELETON to "Skeletons are undead so magic is your best bet against them, there is even a spell specially for fighting the undead.",
        SlayerAssignment.SPIDER to "Spiders are often poisonous, and many varieties are camouflaged too.",
        SlayerAssignment.SPIRITUAL_CREATURE to "Spiritual Creatures can be found in the icy caverns near Trollheim, supporting the cause of their chosen god. Wearing equipment aligned with each of the gods can help you avoid undesired conflict.",
        SlayerAssignment.ZOMBIE to "Zombies are undead so magic is your best bet against them, there is even a spell specially for fighting the undead.",
        SlayerAssignment.CALLISTO to "Callisto roams the Wilderness. His roar can knock you off your feet, and he can hit through prayers.",
        SlayerAssignment.CHAOS_ELEMENTAL to "The Chaos Elemental roams the deepest Wilderness. It can teleport you and make you unequip items randomly.",
        SlayerAssignment.CHAOS_FANATIC to "The Chaos Fanatic has a magic attack that ricochets around, but you can try to dodge it. He can also unequip your items.",
        SlayerAssignment.SCORPIA to "Scorpia's minions fill her cave, and will fight to protect their matriarch. Expect to be poisoned.",
        SlayerAssignment.VENENATIS to "Venenatis lives in the Wilderness. Avoid her sticky webs!",
        SlayerAssignment.VETION to "Vet'ion is in the Wilderness. He dies only when he has been beaten twice. While his dogs guard him, he is immune to damage.",
        )
}
