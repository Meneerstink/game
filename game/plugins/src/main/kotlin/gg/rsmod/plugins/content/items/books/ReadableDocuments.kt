package gg.rsmod.plugins.content.items.books

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/** Wiki transcripts only. Acquisition evidence: N11 dossier S<item id>; exceptions: docs/N11-bundle-2.md. */
object ReadableDocuments {
    data class Document(val item: Int, val title: String, val sourceUrl: String, val text: String) {
        init {
            require(sourceUrl.startsWith("https://"))
            require(text.isNotBlank())
        }
        val book: Book = Book.fromText(title, text)
    }

    val entries: Map<Int, Document> = listOf(
        // Source: https://oldschool.runescape.wiki/w/Instruction_manual
        Document(Items.INSTRUCTION_MANUAL, "Instruction manual", "https://oldschool.runescape.wiki/w/Instruction_manual", """
            Constructing the cannon

            To construct the cannon, firstly set down the base of the cannon firmly onto the ground. Next add the Dwarf stand to the cannon base. Then add the Barrels. Lastly add the Furnace, which powers the cannon. You should now have a fully set up Multi Cannon, to splat nasty creatures!

            Making ammo.

            The ammo for the cannon is made from steel bars. Firstly you must heat up a steel bar in a furnace.

            Now pour the molten steel into a cannon ammo mould. You should now have a ready to fire Multi cannon ball.

            Firing the Cannon.

            The cannon will only fire when monsters are available to target. If you are carrying enough ammo the cannon will fire up to 30 rounds before it runs out and stops. The cannon will automatically target non friendly creatures

            Dwarf Cannon Warranty

            If your cannon is stolen or has been lost, after or during being set up, the Dwarf engineer will replace the parts, however cannon parts that were given away or dropped will not be replaced for free. It is only possible to operate one cannon at a time.

            By order of the members of the noble Dwarven Black Guard.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Astronomy_book; retrieved via https://osrsindex.com/wiki/astronomy-book?site=osrs_wiki
        Document(Items.ASTRONOMY_BOOK, "Astronomy book", "https://oldschool.runescape.wiki/w/Astronomy_book", """
            A History of Astronomy in Gielinor.

            At the start of the 4th Age, a learned man by the name of Scorpius, known well for his powers of vision and magic, sought communion with the gods of the world. So many unanswered questions had he that he devoted his entire life to this cause.

            After many years of study, seeking knowledge from the wise of that time, he developed a machine infused with magical power, infused with the ability to pierce into the very heavens - a huge eye that gave the user incredible sight, like never before.

            As time passed, Scorpius grew adept at using his specialized skills, and followed the movements of the stars in Gielinor, which he mapped and named, and are still used to this very day.

            Before long, Scorpius used his knowledge for predicting the future, and, in turn, he called upon the dark knowledge of Zamorakian worshippers to further his cause. Living below ground, the followers of the dark god remained until the civilisation of Ardougne grew in strength and control.

            The kings of that time worked to banish the Zamorakian followers in the area, hiding all reference to Scorpius's invention, due to its 'evil nature'.

            Years after, when the minds of the kings lent more towards the research of the unknown, the plans of Scorpius were uncovered and the heavenly eye constructed again.

            Since then, many have studied the ways of the astronomer, Scorpius, and in his memory a grave was constructed near the Observatory.

            Some claim his ghost still wanders nearby, in torment as he seeks the secrets of the heavens that can never be solved. Tales tell that he will grant those adept in the arts of the astronomer a blessing of unusual power.

            Here ends the tale of how astronomy entered the known world.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Guide_book; retrieved via https://osrsindex.com/wiki/guide-book?site=osrs_wiki
        Document(Items.GUIDE_BOOK, "Guide book", "https://oldschool.runescape.wiki/w/Guide_book", """
            Introduction.

            This book is your guide to the vibrant city of Ardougne. Ardougne is known as an exciting modern city located on the sun drenched southern coast of Kandarin.

            Ardougne: City of Shopping!

            Come sample the delights of the Ardougne market - the biggest in the known world! From spices to silk, there is something here for everybody! Other popular shopping destinations in the area include the Armoury and the ever popular Adventurers' supply store.

            Ardougne: City of Fun!

            If you're looking for entertainment in Ardougne, why not pay a visit to Ardougne City Zoo? Or relax with a drink in the ever popular Flying Horse Inn? And for the adventurous, there are always rats to be slaughtered in Ardougnes expansive and vermin-ridden sewers. There is truly something for everybody here!

            Ardougne: City of History!

            Ardougne is renowned as an important city of historical interest. One historic building is the magnificent Handelmort Mansion, currently owned by Lord Francis Kurt Handelmort. Also of historical interest is Ardougne Castle in the East side of the city, recently opened to the public. And of course, the Holy Order of Paladins still wander the streets of Ardougne, and are often of interest to tourists.

            Further Fields

            The area surrounding Ardougne is also of great interest to the cultural tourist.

            If you want to go further afield, why not have a look at the ominous Pillars Of Zanash, the mysterious marble pillars located just West of the city? Or perhaps the town of Brimhaven, on the exotic island of Karamja? It's only a short boat trip, with regular transport leaving from Ardougne Harbour at all times of the day, all year round.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Cocktail_guide
        Document(Items.COCKTAIL_GUIDE, "Cocktail guide", "https://oldschool.runescape.wiki/w/Cocktail_guide", """
            Blurberry Special

            Ingredients:

            1 shot vodka
            1 shot gin
            1 shot brandy
            Juice 2 lemons
            Juice 1 orange

            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Add diced orange followed by chunks of lemon. Top the glass with a slice of lime and finish with some equa leaves.

            Chocolate Saturday

            Ingredients:
            1 shot whisky
            1 sprig of equa leaves
            Dash of milk
            1 bar of chocolate

            Mix the ingredients together in the cocktail shaker along with a crumbled chocolate bar. Shake well and pour into a glass. Warm it in an oven, then add fresh cream. Finally sprinkle with chocolate dust.

            Drunk Dragon

            Ingredients:

            1 shot vodka
            1 shot gin
            Dwellberry juice
            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Add diced pineapple then top with fresh cream. Heat the drink briefly in a warm oven before serving.

            Fruit Blast

            Ingredients:

            Juice 1 pineapple
            Juice 1 orange
            Juice 1 lemon

            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Simply top with slices of fresh lemon to finish this light and refreshing mix.

            Pineapple Punch

            Ingredients:
            Juice 1 lemon
            Juice 1 orange
            Juice 2 pineapples

            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Add chunks of pineapple followed by diced lime. Finish the drink by placing a single slice of orange on top.

            Short Green Guy (SGG)

            Ingredients:

            1 shot vodka
            Juice 3 limes

            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Add a sprinkling of equa leaves followed by a single lime slice to finish this gnome classic.
            Wizard Blizzard

            Ingredients:

            2 shots vodka
            1 shot gin
            Juice 1 orange
            Juice 1 lime
            Juice 1 lemon

            Mix the ingredients together in the cocktail shaker. Shake well and pour into a glass. Add pineapple chunks to the drink. Finally finish this magical concoction with slices of lime.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Embalming_manual; retrieved via https://osrsindex.com/wiki/embalming-manual?site=osrs_wiki
        Document(Items.EMBALMING_MANUAL, "Embalming manual", "https://oldschool.runescape.wiki/w/Embalming_manual", """
            Introduction

            Embalming is an essential part of Menaphite funerary practice.

            After death, the spirits of the deceased return to their bodies to find sustenance. However, if their body decays or becomes unrecognisable, then the spirit goes hungry and their afterlife is jeopardised.

            Through embalming, we seek to prevent the decay of the body. In doing so, we offer our clients the best afterlife that money can pay for.

            As embalming is of a scientific persuasion, I will only deal with the technicalities of the job in this book. Prayers and other religious ceremonies will be left to the priests and acolytes of Icthlarin.

            Organ Removal

            The first step of embalming is to remove most of the large organs from the deceased. This done using incisions made in the body.

            Once removed, the organs are placed inside canopic jars. Each jar is then given to Icthlarin's priests to be blessed.

            Preservative Application

            Once the organs have been removed, salts are applied to the body in order to reduce moisture and prevent decay.

            Salt is a valuable spice, but it can thankfully be easily obtained from a saltwater lake north of Sophanem. However, the process of separating the salt from the water is slightly complicated. Natural evaporation is long and time consuming, so sun traps are used to speed up the crystallisation process.

            Corpse Wrapping

            An optional step reserved for our executive customers is the wrapping of the corpse in linen. This further decreases the decay of the body.

            Linen is available from any decent cloth merchant in the area. After all, it is the cloth of choice for most Menaphites.

            Corpse Sealing

            Regardless of if a corpse is wrapped or not, the next step is to seal the body using sap.

            Sap is a sought after and expensive commodity for those who live in desert climes. This is because it is only obtainable from evergreen trees whose natural habitat exists far from the harsh desert. The smart embalmer should always keep a plentiful supply of sap to avoid needless traveling to and from distant lands.

            To collect sap, a knife should be used to make a small cut in the tree. The sap can then be collected in a bucket.

            Final Blessings

            Once the body is fully prepared, the priests of Icthlarin finish the process by carrying out a final ceremony. In this ceremony, the deceased spirit departs their body and leaves for the afterlife. The details of this ceremony are of little consequence to the embalmer.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Crumbling_tome
        Document(Items.CRUMBLING_TOME, "Crumbling tome", "https://oldschool.runescape.wiki/w/Crumbling_tome", """
            During the great God Wars there were six brothers who fought side by side against the forces of evil. Their fervor in battle was awed and feared by all who saw them. Nothing is known of their origins, only that they were first seen when the legions of evil were pressing their hardest into civilised lands.
            It was the eldest who struck the first blow in that battle, hurling powerful battle magic into the enemies' ranks, opening a hole for four of the other brothers to charge through and into the heart of their forces. Little was seen or known of the sixth brother at the time, only that when one of the other brothers looked in danger his foe would be felled by a shower of crossbow bolts.
            It wasn't until the dust had settled that those left could look on their saviours, the six brothers who stood proud with the bodies of the fallen at their feet.
            For years these brothers fought hard against the enemy, throwing themselves into battle with little regard for their own safety and relying only on each other for aid. Stories were told around camp fires by soldiers of the feats they'd witnessed, they became legend long before the end.
            Some say he'd always been there, but no-one really knows for sure. The first real tale was after an incursion deep into the dark lands, where once again the brothers proved their worth. It was after the battle that a bent and robed figure was seen amongst the bodies of the dead. Most took him for a scavenger, but he moved towards the camp fire of the brothers with such directness that some at first thought him another companion.
            The brothers however reacted as with suspicion at his approach, drawing their weapons and rising from their places. The stranger merely stood before them, as though sizing them up before turning and walking back into the mists. Some say that it's the first time they'd ever seen the brothers unsure.
            As the campaign moved deeper into the dark lands the stranger appeared with greater frequency, often appearing on a rise near a battle and watching over it. The most disturbing thing was how his presence started to affect the brothers, they became distracted and on occasion even received injuries that required attention.
            It was deep in the heart of the enemies' territory, in the failing light of dusk, that the enemy struck back. Great monstrosities swept through the camps tearing at all in their path, soldiers scattered like chaff in the wind before the onslaught. The brothers, valiant as ever, stood back to back before the enemy and fought with all their might. As dawn broke the enemy fled the battle, moving faster than any man could give chase.
            It was as the soldiers went about rebuilding the camp that the brothers collapsed at their camp fire, they lay as though dead, yet breathing, muttering in strange tongues. Their injuries were found swollen and infected, none of the physicians knew what to do, it was like no infection they had ever seen before. All through the day the brothers were tended and cared for while the soldiers waited nervously for their heroes to rise once again.
            It was then, just as the sun's last rays vanished behind the horizon, that all hope was lost. As if on cue all the six brothers sighed one last breath and died. It was only then that the sentries noticed the stranger once again, standing on a rise just outside the camp.
            That night the camp was surrounded by the great beasts of the enemy once again, but they didn't attack, they merely baited the soldiers to come out and meet them. Once again at the break of dawn the enemy fled without a trace, leaving the soldiers tired from a restless night of worry and fear. That day the commanders decided to turn back, they could advance no further without the brothers' aid.
            As was the custom at the time, out of honour and respect for the mighty fallen, they constructed six great barrows for the brothers. A crypt for each brother covered in a small hill of earth that would hold their remains undisturbed by predators. The work took many days, for each night the enemy returned and the soldiers had to flee back inside their camp to wait away the night to the sounds of howling and scratching at the fences.
            But each day they resumed their work, their sense of duty to the brothers surmounting their fear of the dark forces in the land.
            On the last day the assembled troops buried the brothers inside their stone tombs, then with great reverence, but little ceremony, they covered the tombs with earth. As the army broke camp and turned to march back to their lands the stranger was seen once again, standing atop the central barrow... standing tall against the failing light with his arms outstretched to the sky.
            Not many of the original army made it back to these lands, many more had perished on the journey home, but tales were told of how each night an unnatural glow could be seen on the horizon where they had buried the brothers. Even today there are stories told about those mounds of earth, and of the sights and sounds that have been witnessed nearby.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Abyssal_book
        Document(Items.ABYSSAL_BOOK, "Abyssal book", "https://oldschool.runescape.wiki/w/Abyssal_book", """
            Introduction

            Despite having finally gained access to the strange dimension known as the Abyss (also referred to as Abyssal Space or the Abyssal Plane), we have found the area to still be something of an enigma.
            Although we have long had knowledge of the Abyss and the role it plays in teleportation magic, it was only recently that we finally discovered a way to enter this strange realm. The discovery came by complete chance, when a routine teleportation experiment seemingly went wrong.
            We are still not sure as to what exactly caused this teleportation failure, but the ability to finally access the Abyss led to a flurry of research from the Zamorakian Magical Institute (henceforth known within this document as the Z.M.I.).

            The results of our research so far are detailed in this document.

            Abyssal Teleportation

            While often referred to as such, the Abyss is not a plane in the traditional sense (such as Zanaris or Frenaskrae).
            The Abyss seems to be a strange dimension that sits between all other planes. In some ways, the Abyss acts a 'glue' that holds all planes together, yet keeps them separate, if you will. Because of this, the Abyss exists at the 'fringe' of reality, meaning that it does not conform to the same guidelines of space and time observed on Gielinor, or indeed on other planes.
            A clear example of the unique nature of the Abyss is that you may enter it and then leave it from an identical spot, yet reappear many hundreds of miles from your entry point. Similarly, you can enter the Abyss and spend hours there, while only moments have passed back on Gielinor.
            It is thanks to the above phenomena that the Abyss is key to teleportation magic. In fact, all teleportation that we know of uses the Abyss to make the passing of great distances occur in a very short space of time, at least from our perception.
            During a teleport, what is actually happening is that the spellcaster is entering the Abyss and then immediately leaving again. Certain values as to speed and direction are taken care of during the spellcasting. This allows some degree of precision in these teleports.

            While highly useful, the extensive use of the Abyss for teleportation is something to be cautious of. We have observed that the barriers between Gielinor and the Abyss are seemingly becoming weaker through excessive use.
            If care is not taken, the barriers to the Abyss could break down entirely. Indeed, we have already had isolated reports within the Z.M.I. of strange creatures entering Gielinor from the Abyss, not to mention the teleportation malfunction that first resulted in us gaining access to the dimension.
            Still, while the dangers are clear, the weakening of these barriers may prove useful if the correct care is taken. Perhaps an alternative method to proceed with Operation: Transient may present itself. One that won't alert the followers of other deities to our plans.

            Abyssal Creatures

            When we have accepted that the Abyss is somewhat of a tesseract or hypercube with a direct relation to our dimension, then the benefits of exploiting this resource become more obvious.
            Certain creatures of the Abyss contain unnecessary surplus organs that may be harvested to provide carrying pouches for various unstable magical objects.

            For example, I ran some experiments with Sample XJ13 (rune essence) and managed to place six parts in a space that would seemingly only hold one.
            Continued experimentation with these samples showed that moving items between our dimension and the Abyss degraded the use of these pouches, but casting a simple transfiguration spell upon them while within the Abyss restored their usage back to the original results.
            To improve our runecrafting output, I would strongly recommend the harvesting of these creatures for their organs. However, some degree of caution will be required, as the creatures of the Abyss are seemingly very aggressive. We should also accelerate our efforts to locate the lost Rune Essence Mine, as only with increased supply will these pouches see proper use.

            Abyssal Runecrafting

            As previously detailed, our gaining access to the Abyss was somewhat of a fluke, and a not easily repeatable fluke at that. Due to the Abyss not being a plane in the traditional sense, we have also had to resort to unusual measures to gain permanent access.

            We took a large number of initiates and gave each of them the supplies to cast teleportation spells. We then had them repeatedly teleport to various locations, seeking to replicate the original error that caused the first entry into the Abyss.
            Once one of our initiates had managed to 'fail' his teleport and appear in the Abyss, he was then charged with remaining there and holding a portal spell open so that more senior members of the Z.M.I. could gain entry.

            At the time of writing, this initiate is still there, as without him, the portal spell will collapse. Thanks to the slow passage of time within the Abyss, he is unlikely to tire, so will not need relieving any time soon.
            While originally only intended as a way for us to access the Abyss, the portal spell soon proved to have some very beneficial side effects. It has also allowed us to open portals to most of the runic altars found across Gielinor.

            Thanks to the portals, the Abyss has become a runecrafting gold mine, especially when combined with the pouches detailed previously. However, as also noted, lack of access to the lost Rune Essence Mine is preventing us from utilising the Abyss to its full potential.
            One recent development of concern in this area is the sudden increase in rune production from the Order of Wizards. Could it be that they have found a way to the Rune Essence Mine themselves?

            Perhaps more knowledge survived the destruction of the first Wizards' Tower than previously believed. This is something that requires immediate investigation. In the meantime, I have ordered our buyers to increase their purchasing of Sample XJ13.

            Until my next report, I remain, as ever, a loyal servant.
            Strength Through Chaos!
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Mourner_letter
        Document(Items.MOURNER_LETTER, "Mourner letter", "https://oldschool.runescape.wiki/w/Mourner_letter", """
            To whom it may concern:

            This is to certify that the bearer of this letter has passed all entry requirements for the Death Guard.

            Death Guard HQ
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Giant_champion_scroll; retrieved via https://osrsindex.com/wiki/giant-champion-scroll?site=osrs_wiki
        Document(Items.CHAMPIONS_SCROLL_6800, "Champion's scroll", "https://oldschool.runescape.wiki/w/Giant_champion_scroll", """
            Get yourself to the Champions' Guild, if you dare to face me puny human.

            Champion of Giants
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Pie_recipe_book
        Document(Items.PIE_RECIPE_BOOK, "Pie recipe book", "https://oldschool.runescape.wiki/w/Pie_recipe_book", """
            Redberry pie

            Pour a hand full of Redberries into an empty Pie Shell, bake until the berries are soft and serve warm.

            Meat pie

            Line a fresh Pie Shell with Cooked Meat and heat until the pastry starts to bronze, serve with a selection of sauces.

            Mud pie

            Start with a Pie Shell and add a Bucket of Compost, then pour in a Bucket of Water to keep the consistency gooey. To finish, cover with Clay and bake until a good shell forms. Serve at maximum speed with a good over-arm throw!

            Apple pie

            Take a Pie Shell and layer in Apple, cook until the juices start to bubble and leave to cool before serving.

            Garden pie

            Fill a Pie Shell with Tomato, then add Onion and top with Cabbage. Bake golden brown and serve with a steak.

            Fish pie

            Take one Pie Shell and fill with Trout, add a Cod for flavour, and then top with Potato for texture. Cook well until the potato turns golden and serve.

            Botanical pie

            Line a Pie Shell with a Golovanova fruit. Bake until the fruit has become crispy on the top. Best served with cream and some milk.

            Mushroom pie

            If you are looking for an acquired taste, fill your Pie Shell with a Sulliuscep cap and bake until the Pie Shell starts to take on the Sulliuscep's colour.

            Admiral pie

            For a more upperclass fish pie, fill your Pie Shell with Salmon and then add Tuna for colour. Top with Potato and cook until golden.

            Dragonfruit pie

            If you're looking for an exotic pie, fill your Pie Shell with a single Dragonfruit cut into chunks. Cook until the crust starts to glow.

            Wild pie

            Line a Pie Shell with Raw Bear Meat, then add Raw Chompy for substance, and top with fresh Rabbit Meat. Bake until the juices start to bubble and serve.

            Summer pie

            Into a Pie Shell, put Strawberry, then a layer of Watermelon, and top with Apple. Cook well and leave to cool before serving.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Al_kharid_flyer; retrieved via https://osrsindex.com/wiki/al-kharid-flyer?site=osrs_wiki
        Document(Items.AL_KHARID_FLYER, "Al kharid flyer", "https://oldschool.runescape.wiki/w/Al_kharid_flyer", """
            Come to the Al Kharid Market place! High quality produce at low, low prices! Show this flyer to a merchant for money off your next purchase, courtesy of Ali Morrisane!

            You notice that the money off voucher is out of date.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Security_book
        Document(Items.SECURITY_BOOK, "Security book", "https://oldschool.runescape.wiki/w/Security_book", """
            Account Tips

            Upgrade to a Jagex Account, if you haven't already done so.

            These use two-factor verification to keep people out of your account, even if they somehow learn your password.

            Ensure your email account is properly secure too, with its own two-factor verification.

            Password Tips

            A good password should be easy for you to remember, but hard for anyone else to guess.

            Choose a password containing both letters and numbers, but don't make it so hard that you'll forget it!

            Never write your password down or save it in a text file on your device, as someone could find it.

            Never give your password to anyone. This includes your friends, family and moderators in the game.

            Nor should you give anyone your account info on social media, even if they claim they can help you with RuneScape.

            Other Security Tips

            Set a Bank PIN to help protect your banked items, in case someone gets into your account.

            To set a Bank PIN, speak to a banker and ask to check your PIN settings.

            Never leave your account logged in while you're away from your device, as it only takes a few seconds to steal or ruin your account!

            Stronghold of Security

            The Stronghold of Security is located under the Barbarian Village in Misthalin, west of Varrock.

            It was discovered by accident, when a miner fell through the ground into a previously unexplored cave.

            The Stronghold contains many challenges, both for those who enjoy combat and for those who enjoy challenges of the mind.

            This book's guidance may be useful to you as you delve into the Stronghold.

            You can find the Stronghold of Security by looking for a hole in the middle of the Barbarian Village.

            Be sure to take your combat equipment though!
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Stronghold_notes
        Document(Items.STRONGHOLD_NOTES, "Stronghold notes", "https://oldschool.runescape.wiki/w/Stronghold_notes", """
            Description

            This stronghold was unearthed by a miner prospecting for new ores around the Barbarian Village. After gathering some equipment he ventured into the maze of tunnels and was missing for a long time. He finally emerged along with copious notes regarding the new beasts and strange experiences which had befallen him. He also mentioned that there was treasure to be had, but no one has been able to wring a word from him about this, he simply flapped his arms and slapped his head. This book details his notes and my diary of exploration. I am exploring to see if I can find out more...

            Level 1

            As well as goblins, creatures like a man but also like a cow infest this place! I have never seen anything like this before. The area itself is reminiscent of frontline castles, with many walls, doors and skeletons of dead enemies. I'm sure I hear voices in my head each time I pass through the gates. I have dubbed this level War as it seems like an eternal battleground. I found only one small peaceful area here.

            Level 2

            My supplies are running low and I find myself in barren passages with seemingly endless malnourished beasts attacking me, ravenous for food. Nothing appears to be able to grow, many adventurers have died through lack of food and the very air appears to suck vitality from me. I've come to call this place famine.

            Level 3

            Just breathing in this place makes me shudder at the thought of what foul disease I may contract. The walls and floor ooze and pulsate like something pox ridden. There is a very strange beast whom I narrowly escaped from. At first I thought it to be a cross between a cow and a sheep, something domesticated... but when it looked up at me I was overcome with weakness and barely got away with my life! Luckily I found a small place where I could heal myself and rest a while. I have named this area pestilence for it reeks with decay.

            Level 4

            On my first escapade into this place I was utterly shocked. The adventurers who had come before me must have made up a tiny proportion of the skeletons of the dead. Nothing truly alive exists here, even those beings who do wander the halls are not alive as such, but they do know that I am and I get the distinct impression that were they to have their way, I would not be for long! Death is everywhere and thus I shall name this place. There is one small place of life, which was gladdening to find and very worth my while!

            Navigation

            After getting lost several times I finally worked out the key to all the ladders and chains around this death infested place. All ropes and chains will take you to the start of the level that you are on. However most ladders will simply take you to the level above. The one exception is the ladder in the bottom level treasure room, which appears to lead through several extremely twisty passages and eventually takes you out of the dungeon completely. The portals may be used if you are of sufficient level or have already claimed your reward from the treasure room.

            Diary

            Day 1
            Today I set out to find out more about this place. From my research I knew about the sentient doors, imbued by some unknown force to talk to you and ask questions before they will let you pass. I have so far passed these doors without incident, giving the correct answer seems to work a treat.

            Day 2
            I have fought my way through the fearsome beasts on the first level and am preparing myself to journey deeper. I hope that things are not too difficult further on as I am already sick of bread and cheese for dinner.

            Day 3
            I ventured down into the famine level today... I was wounded and have returned to the relative safety of the level above. I am going to try to make my way out through the goblins and mancow things... I hope I make it.....
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Moonclan_manual; retrieved via https://osrsindex.com/wiki/moonclan-manual?site=osrs_wiki
        Document(Items.MOONCLAN_MANUAL, "Moonclan manual", "https://oldschool.runescape.wiki/w/Moonclan_manual", """
            The Basics of Magic

            A Primer in the Mystical Arts

            This primer has been collated from years of our research into the metanatural arts commonly known as 'magic'. All those wishing to set forth upon the Astral Path should read this primer until the concepts within are fully understood so that their potential may fully be realised.

            Chapter 1:
            Myths & Misconceptions

            Those familiar with the history of our people will know that the source of 'magic' has always been a contentious issue.

            Our Fremennik forefathers believe 'magic' to be a possession of the gods, and that our use of it risks far more than any benefits it may bring;

            We of course take the more enlightened view that we should use all tools at our disposal for our own betterment.

            Beyond any ethical or moral issues surrounding magic however, lies the key question:

            What exactly is magic?

            The other obvious question that presents itself then would be:

            Where exactly does it come from?

            I intend to explain here how both of these questions are actually the same question phrased differently.

            The first major misconception relating to magic is that the stones we use for our arts somehow contain magic within them.

            Although roughly correct, to believe this is how magic works limits your understanding of our own potential, and will prevent you ever achieving the feats which we are all capable of.

            Rather, the runes that we shape to our purposes serve to focus your own power, rather than containing the power themselves.

            A short history of how the runes were first discovered will serve to illuminate this point for you more fully.

            As we all know, from the tales told to us in the Secret Tongue, 'magic' was first discovered in these lands by V------- when he accidentally stumbled upon the Stone of J--.

            The stone was clearly not of this world, and the mere touch of it unlocked something within the mind of V-------.

            As we know, the stone was instantly removed by those powers who walk a higher Astral Path than ourselves, but its very existence showed V-------- possibilities that had never occurred to our kind before.

            His lifelong search for the stone never did reveal its eventual fate, and the myths suggest that the stone may yet still lie somewhere within this world, but he did discover something of great importance in the caves where first he found the stone; the very rocks that had surrounded it had been subtly changed by its presence.

            The rocks in their contact with the Stone of J-- had gained a yearning to be something that they were not, and V------- discovered that by focussing thought upon them he could transform them entirely for short periods of time into the very elements of the universe.

            This is a mere parlour trick, as many of our young have done the same thing with essence as children, making the cold-burning fire in their hands on summer nights, and V------- knew that should he be able to affect a permanent change upon these stones, he could have access to the very powers of the gods themselves!

            And so was created the Astral Temple. This was the first, and to this day we honour those who walked before us on the Astral Path, and prevent it falling into the ruin that this dimension has caused to its counterparts.

            By regular concentrated effort of will upon a large dolmen of essence, by a number of people simultaneously, the dolmen was finally convinced that it had become something which it was not. The Astral Rune, symbol of our very way of life, it is a testament to what is possible if we seek understanding.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Goblin_book
        Document(Items.GOBLIN_BOOK, "Goblin book", "https://oldschool.runescape.wiki/w/Goblin_book", """
            The Creation of the Goblins

            In beginning all gods have big war. Each god find army to fight for him.

            Big High War God not have army. Big High War God go to beardy-short-people and ask, Will you fight in my army? But beardy-short-people say, No, we fight for God of Shiny Light.
            Then Big High War God go to demons and ask, Will you fight in my army? But demons say, No, we fight for God of Dark Fire.

            Then Big High War God go to tall people with keen blades and say, Will you fight in my army? But tall people with keen blades say, No, some of us fight for God of Shiny Light and some of us fight for God of Dark Fire, but none of us fight for you!

            Big High War God very sad. He travel east and west, north and south, across land looking for army to fight for him.
            Then goblins say, We fight for you! At that time goblins very weak, very small, soft skin. Not like goblins today! But Big High War God say, I will make you my army. So Big High War God train goblins so they very strong. He give them good armour so they not be harmed. He make them strong in spirit so they not afraid of battle. He give them commanders so they know which way to go. He divide goblins into twelve tribes and send them into battle!
            Goblin armies fight very good! When other gods see this, they very jealous. So they say, We want goblins to fight for us too! So Big High War God take some tribes and sell them, one to God of Shiny Light and one to God of Dark Fire, and other tribes to other gods. But most still worship Big High War God who created them!

            The Several Commandments

            These are commands of Big High War God! Obey all commands all time or Big High War God kill you very bad!
            Always to slay enemies of Big High War God. Enemies must die!

            Not to run from battle. Cowards must die!

            Not to show mercy. Merciful must die!

            Not to doubt Big High War God. Doubters must die!

            Not to make own plans. Thinkers must die!

            The end of the war and the prophecy

            The war of gods last many lifetimes. Battle is glorious and many heroes live and die! Then all gods leave world, leave their armies behind. But goblins still soldiers, still fight! Goblins fight against tall people with keen blades. But tall people build cities with walls, they not want to fight. They not true soldiers like goblins! But now goblins not have enough to eat, and have no commanders to tell them who to fight. So goblin tribes fight one another.
            At last all goblin tribes have big battle on plain of mud. Battle last many days and many goblins die, battle is glorious! But goblin corpses cover the ground and it look like all die.

            Then Hopespear of the Narogoshuun tribe have vision of Big High War God. In night while soldiers rest he call leaders of all tribes together to give them message.
            This is the word of Big High War God: Battle is indeed glorious and goblins are soldiers, but if there too much battle all goblins die! No more must goblin fight against goblin or tribe against tribe. Goblins must find other enemies to fight, but not fight each other!
            And this is the word of Big High War God: Today I cannot lead you, but someday I will send a new Commander to lead you. Under new Commander goblins will conquer all of Gielinor, every race and every god! And then Big High War God will return and sit on throne of bronze and rule over all. War will end in victory and victory will last forever!

            So leaders of tribes stop battle. And on plain of mud all tribes build temple to Big High War God and offer sacrifices.
        """.trimIndent()),
        // Source: https://runescape.wiki/w/Newspaper; retrieved via https://runescape.fandom.com/wiki/Newspaper
        Document(Items.NEWSPAPER, "Newspaper", "https://runescape.wiki/w/Newspaper", """
            Issue 1

            Varrock gets Makeover

            The city of Varrock is the latest recipient of a complete makeover. When interviewed, King Roald said, 'In order to keep visitors coming to see the sights of our beautiful capital, we felt that tidying-up the city would be more effective than just issuing a decree - make sure you visit the new museum while you are here.'

            Obituaries

            Goblin-Died
            Giant Rat-Died
            Unicorn-Died
            Varrock Guard-Died
            Varrock Guard-Died
            Varrock Guard-Died
            Bear-Died

            Classifieds

            Lowe's Archery Emporium for the finest ranging weapons in town!

            Time to party! Visit the Fancy Dress Shop for all your party outfits.

            The Dancing Donkey - cold beer always in stock.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Glassblowing_book; retrieved via https://runescape.fandom.com/wiki/Glassblowing_book
        Document(Items.GLASSBLOWING_BOOK, "Glassblowing book", "https://oldschool.runescape.wiki/w/Glassblowing_book", """
            Ultimate Guide to Glassblowing

            Author: Fritz the Glassblower

            I can tell you now, the art of creating glass has long been a valued trade in RuneScape. Not many could get by without the use of some item made from this marvelous substance.

            It's within this book that we cover the basics of the art, in a hope to get you hooked on this popular pass-time and trade. To start with, you will need a few essential items:

            An empty bucket.

            These can be purchased from the majority of general stores and are required to carry the sand you will need to make glass.

            A glassblowing pipe

            Found on Entrana or often just north-west of the Ranging Guild. As its name suggests, this is used to blow air into molten glass.

            Seaweed

            Seaweed can be found on the shore in many places, but it is also a frequent find when Fishing with nets. This you will need to create soda ash - an important ingredient to the process.

            Soda ash

            Made by burning seaweed, you will need this to make glass. Other than making it yourself, you can purchase from other budding glassblowers.

            With these items you are ready to begin! You'll need to find a sandpit and there are a few dotted around RuneScape. Try the isle of Entrana or Yanille, for example. Use your bucket on the sandpit to fill it up.

            Next, you need to find your way to a furnace. Turn your seaweed into soda ash by using it on a range before using a bucket of sand or soda ash on the furnace to create molten glass.

            The last stage is simply to use the molten glass wth your glassblowing pipe and, depending on your level of skill, you can create a variety of items.

            I hope you enjoyed this guide, and please keep an eye out for more of my titles!
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Gianne%27s_cook_book; retrieved via https://osrsindex.com/wiki/gianne-s-cook-book?site=osrs_wiki
        Document(Items.GIANNES_COOK_BOOK, "Gianne's cook book", "https://oldschool.runescape.wiki/w/Gianne%27s_cook_book", """
            Chocolate Bomb


            Knead a ball of Gianne dough into a gnomebowl mould. Bake this briefly. Decadently add four bars of chocolate to the bowl and top with one sprig of equa leaves. Bake the bowl in the oven to melt the chocolate. Then mix in two big dollops of cream and finally sprinkle chocolate dust all over.

            Chocolate is a relatively recent cooking ingredients for gnomes, having been imported from human lands.

            Tangled Toad's Legs


            Shape a portion of fresh Gianne dough into a gnomebowl mould. Bake this until it is slightly springy. Add to the bowl... 4 pairs of toad's legs 2 portions of cheese 2 sprigs of equa leaves 2 dashes of gnome spice 1 bunch of dwellberries Bake the dish in the oven once more prior to serving.

            Tangled Toads Legs was a special dish created by gnome chef Deelie to celebrate the first Healthorg the Great Day.

            Worm Hole


            Starting with a gnomebowl mould, shape a portion of fresh Gianne dough into a rough bowl. Bake this until it is firm to the touch. Add to the bowl four king worms, two onions and a dash of gnome spices. Bake the bowl in the oven once more. To finish the dish simply add a topping of equa leaves.

            Worms are specially favoured by gnomes as they purportedly aid virility.

            Veg Ball


            As with all gnomebowl dishes, throw a ball of fresh Gianne dough into a mould. Bake this as usual. Bake this briefly. Add to the bowl two onions, two potatoes and a dash of gnome spices. Bake the bowl in the oven once more. To finish simply top with equa leaves.

            Vegetable dishes are seen as luxurious food, since for most of gnome history growing vegetables was harder than finding toads and worms

            Worm Crunchies


            Using a crunchy tray, form a portion of Gianne dough into small evenly sized balls. Heat these briefly in an oven. Mix into the dough balls two king worms, one sprig of equa leaves and a shake of gnome spices. Bake the crunchies for a short time in the oven. Sprinkle generously with gnome spices to finish.

            Crunchies were invented accidentally by Pukkamay, who was Deelie's assistant before his sacking. He started a successful crunchy making business before dying in a bizarre Terrorbird accident.

            Choc Chip Crunchies


            Fill up a crunchy tray with balls of Gianne dough. Heat these briefly in a warm oven. Break up two bars of chocolate and mix these with the balls of dough. Next add a little gnome spice to each ball. Bake the crunchies for a short time in the oven. Top the crunchies with a sprinkling of chocolate dust to finish.

            (A large chocolate stain covers the rest of the page.)

            Spicy Crunchies


            Using a crunchy tray, form a portion of Gianne dough into small evenly sized balls. Heat these briefly in a warm oven. Add a generous shake of spice and two sprigs of equa leaves to the dough balls. Bake the crunchies for a short time in the oven. Sprinkle a load more gnome spice over the cookies to finish.

            The special mix of herbs and spices in gnome spice is a closely guarded secret. It is rumoured to contain (the rest is scribbled out)

            Toad Crunchies


            Fill a crunchy tray with Gianne dough as normal. Heat these briefly in a warm oven. Mix into the dough balls two pairs of toad's legs and a shake of gnome spices. Bake the crunchies for a short time in the oven. Finish the crunchies with a sprinkling of equa leaves.

            When Pukkamay first made toad crunchies, everyone thought he was mad. 'Chewy toads, in crunchies? It'll never work' they said - how wrong they were...

            Worm Batta


            First take some fresh Gianne dough and place it in a batta tin. Bake the dough until it is lightly browned. Take one king worm, some gnome spice and a little cheese. Add these to the batta before briefly baking it in the oven once more. Finish the batta with a topping of equa leaves.

            Battas are usually cooked by gnome mother during the cold winter months.

            Toad Batta


            Mould some Gianne dough into a batta tin. Bake the tin until it is almost cooked. Next add some prime toad's legs, a sprig of equa leaves and some spice along with some cheese to the batta. Bake the batta in the oven once more and serve hot.

            Toad battas are sometimes called Toad in the Hole. Apparently there is a similar human dish that uses sausages. How odd.

            Cheese & Tomato Batta


            Place some Gianne dough in a batta tin. Bake it as normal. Top the plain batta with equal quantities of cheese and tomato. Place the batta in the oven once more until all the cheese has melted. Finish the dish with a sprinkling of equa leaves.

            The combination of cheese and tomato was discovered by the explorer Wingstone while was visiting the human lands. Apparently it's used a lot in a strange flat human dish called a pizza.

            Fruit Batta


            Prepare Gianne dough in a batta tin as normal. Bake the dough for a short while. Top the batta with chunks of pineapple, orange and lime. Lay four sprigs of equa leaves on top of the batta before baking it in the oven once more. Finish the batta with a sprinkling of gnome spices.

            Battas are normally savoury dish, and the fruit batta is definitely an acquired taste.

            Vegetable Batta


            Place some Gianne dough in a batta tin and bake as normal. Add to the plain batta two tomatos, one onion, one cabbage and some dwellberries. Top the batta with cheese and briefly place it in the oven once more. Finish the dish with a sprinkling of equa leaves.

            There's no better batta than a vegetable batta.
        """.trimIndent()),
        // Source: https://oldschool.runescape.wiki/w/Knight%27s_notes; retrieved via https://osrsindex.com/wiki/knight-s-notes?site=osrs_wiki
        Document(Items.KNIGHTS_NOTES, "Knight's notes", "https://oldschool.runescape.wiki/w/Knight%27s_notes", """
            My friend, you were right to send me to investigate the dwarf's drunken claims, for I have discovered a treasure beyond our wildest dreams. The aviansie are alive, and I suspect they still guard the Godsword! Beneath the remnants of the temple a great battle is being fought between followers of Bandos, Armadyl, Saradomin and Zamorak. My command was slaughtered and I am grieviously wounded. YOU MUST PREVENT THE GODSWORD FROM FALLING INTO THE WRONG HANDS! I do not know how I am going to get this message to you, why is that talking skull never around when he's needed? Your comrade, Sir Gerry.
        """.trimIndent()),
    ).associateBy { it.item }

    fun read(player: Player, slot: Int, id: Int): Boolean {
        val document = entries[id] ?: return false
        val held = player.inventory[slot] ?: return false
        if (held.id != id || held.getDef(player.world.definitions).noted) return false
        if (!player.lock.canInterfaceInteract()) return false
        if (player.timers.has(ACTIVE_COMBAT_TIMER)) {
            player.message("You can't read this while in combat.")
            return false
        }
        if (player.interfaces.currentModal != -1) {
            player.message("Please close the interface before reading.")
            return false
        }
        player.openBook(document.book)
        return true
    }
}
