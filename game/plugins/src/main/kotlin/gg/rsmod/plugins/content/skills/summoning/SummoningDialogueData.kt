package gg.rsmod.plugins.content.skills.summoning

/**
 * Real familiar "Interact" dialogue.
 *
 * The familiar npcs in this cache carry `OPTIONS=[Interact, , Withdraw|Store, , ]`
 * (`runNpcDefProbeTool 6796 6815 6817 6991`), and "Interact" is the talk option: it opens the
 * ordinary NPC chatbox with the familiar's own chathead, two lines per box - the untranslatable
 * noise on the first line and the parenthetical translation on the second. The owner's own
 * reference screenshot shows exactly that for the spirit mosquito
 * ("Whiiiiine whinewhiiiine?" / "(Have you ever tasted pirate blood?)"), and that exact pair is
 * the first line of the spirit mosquito's third conversation below - i.e. the shape of this table
 * is confirmed against the owner's evidence, not just against the source.
 *
 * Source: the runescape.wiki `Transcript:<familiar>` pages, which reproduce the familiar
 * conversation scripts as they have stood since Summoning launched in 2008 and were unchanged
 * through 2011. Only the *unconditional* conversations are carried here. Every transcript also
 * lists conversations gated on a specific state (a bone in the inventory for the spirit wolf,
 * logs for the beaver, a keris for the spirit kalphite, being below full life points for the
 * unicorn stallion, and so on); reproducing those without their real trigger conditions would
 * make them fire at the wrong times, so they are deliberately left out rather than guessed at.
 *
 * KNOWN DATA GAP, disclosed rather than filled in: 19 of the roster's 78 familiars are covered.
 * The rest have no sourced transcript in this run. [SummoningDialogue] therefore falls back to
 * the familiar's real cache-defined behaviour for those (see its kdoc) instead of inventing
 * lines for them. Adding a familiar is purely a matter of appending its sourced transcript here.
 */
object SummoningDialogueData {
    /**
     * One chatbox: [speech] on the first line and, when the player is able to understand this
     * familiar, [translation] on the second - as real RS renders it.
     *
     * A few familiars speak plain Common instead of animal noise, and their conversations include
     * the player's own replies; [speaker] carries that. Those lines have no translation, because
     * there is nothing to translate.
     */
    data class Line(
        val speech: String,
        val translation: String = "",
        val speaker: Speaker = Speaker.FAMILIAR,
    )

    enum class Speaker { FAMILIAR, PLAYER }

    private fun conversation(vararg lines: Pair<String, String>): List<Line> = lines.map { Line(it.first, it.second) }

    /** A conversation in plain Common: [Speaker.FAMILIAR] and [Speaker.PLAYER] lines, no translations. */
    private fun spokenConversation(vararg lines: Pair<Speaker, String>): List<Line> =
        lines.map { Line(it.second, speaker = it.first) }

    /**
     * Most transcripts are mixed: the familiar speaks noise with a parenthetical translation and
     * the player answers in plain Common. [npc] and [you] build those two line kinds so a mixed
     * conversation can be written as a plain `listOf(...)`.
     */
    private fun npc(
        speech: String,
        translation: String = "",
    ) = Line(speech, translation)

    private fun you(line: String) = Line(line, speaker = Speaker.PLAYER)

    private val conversations: Map<SummoningPouchData, List<List<Line>>> =
        mapOf(
            SummoningPouchData.SPIRIT_WOLF to
                listOf(
                    conversation("Whurf?" to "(What are you doing?)"),
                    conversation(
                        "Bark! Bark!" to "(Danger!)",
                        "Whiiiine..." to "(False alarm...)",
                    ),
                    conversation("Whuff whuff. Pantpant awf!" to "(I smell something good. Hunting time!)"),
                    conversation("Pant pant whine?" to "(When am I going to get to chase something?)"),
                ),
            SummoningPouchData.DREADFOWL to
                listOf(
                    conversation("Cluck cluck cluck!" to "(Attack! Fight! Annihilate!)"),
                    conversation("Bwaaak..." to "(Can it be fightin' time, please?)"),
                    conversation("Bwuckbwuck bwaak." to "(I want to fight something.)"),
                ),
            SummoningPouchData.SPIRIT_SPIDER to
                listOf(
                    conversation(
                        "Clickerclick?" to "(Where are we going?)",
                        "Clatterclick..." to "(Fine, don't tell me...)",
                        "Clacker!" to "(Don't want to know now.)",
                    ),
                    conversation(
                        "Clackclackclatter?" to "(Who is that?)",
                        "Clatterclatterclickclacker." to "(The two-legs over there.)",
                        "Clickclatter..." to "(Never mind...)",
                        "Clack!" to "(It doesn't matter now.)",
                    ),
                    conversation(
                        "Clickerclack?" to "(What are you doing?)",
                        "Clatterclickclack..." to "(I see, you don't think I'm smart enough to understand...)",
                        "Clack!" to "(Don't wanna know now.)",
                    ),
                    conversation(
                        "Click..." to "(Sigh...)",
                        "Clatter." to "(Nothing really.)",
                    ),
                ),
            SummoningPouchData.THORNY_SNAIL to
                listOf(
                    conversation(
                        "Slitherslime slitherslither schlorp schlorp." to "(All this running around the place is fun!)",
                        "Schlorpschlorp slither slime." to "(True, but it's mostly seeing the sort of sights you don't get at home.)",
                        "Schlorpschlorp slither." to "(Living things for a start.)",
                    ),
                    conversation(
                        "Schurpschlurp shclurp..." to "(I think my stomach is drying out...)",
                        "Slitherslither slitherslither..." to "(I am walking on it, you know...)",
                    ),
                    conversation(
                        "Schlorpschlorp." to "(Can you slow down?)",
                        "Slitherslimeslither schlorp slitherslither slithersclorp!" to
                            "(I bet if you had to run on your internal organs you'd want a break every now and then!)",
                    ),
                ),
            SummoningPouchData.GRANITE_CRAB to
                listOf(
                    conversation("Clickclickchiner!" to "(Rock fish now, please?)"),
                    conversation("Clickclickchitter?" to "(When can we go fishing? I want rock fish.)"),
                    conversation("Clickclickchitter!" to "(I'm stealthy!)"),
                ),
            SummoningPouchData.SPIRIT_MOSQUITO to
                listOf(
                    conversation(
                        "Whiiine whiiiiine?" to "(You have lovely ankles.)",
                        "Whiiiiinewhiiiiiiine whiiiiiiiine." to "(Thin skin. Your delicious blood is easier to get to.)",
                        "Whiiiiiiiiiiiiine!" to "(Oh, come on, you won't feel a thing...)",
                    ),
                    conversation(
                        "Whiiiiiine!" to "(How about that local sports team?)",
                        "Whiiiiine whiiiiine whinewhiiiine." to "(I must confess: I have no idea.)",
                        "Whiiiiine whiiiiinewhiiine." to "(I was just trying to be friendly.)",
                    ),
                    conversation(
                        "Whiiiiine whinewhiiiine?" to "(Have you ever tasted pirate blood?)",
                        "Whiiiiine whiiiiiiiiiiiiine?" to "(How about dwarf blood?)",
                        "Whine?" to "(Gnome blood, then?)",
                    ),
                    conversation(
                        "Whiiiiine!" to "(I'm soooo hungry!)",
                        "Whiiiine, whiiiinewhiiiiine whine..." to "(Well, if you're not too attached to your elbow...)",
                    ),
                ),
            SummoningPouchData.DESERT_WYRM to
                listOf(
                    conversation(
                        "Hssshsss ssss ssssss...Hssshsssssss sssssss sshss." to
                            "(This is so unsafe... I should have a hard-hat for this work.)",
                        "Hssss ssssshssshssss ssss." to "(Keep that up and you'll have the union on your back.)",
                    ),
                    conversation("Hsshssshssssss ssssss!" to "(You can't touch me, I'm part of the union!)"),
                    conversation(
                        "Hsss sssss hssssss sssshssshsssss." to "(You know, you might want to register with the union.)",
                        "Hssshssshsss sss sssss ssshsss." to "(I stop bugging you to join the union.)",
                    ),
                ),
            SummoningPouchData.SPIRIT_SCORPION to
                listOf(
                    conversation(
                        "Screeclickclick clicklick!" to "(Say hello to my little friend!)",
                        "Clickclicklick screescree." to "(My little friend: you ignored him last time you met him.)",
                        "Clickclick click clik screee?" to "(If I tell you, what is the point?)",
                    ),
                    conversation(
                        "Clickclick click scree click." to "(Hey, boss, I've been thinking.)",
                        "Screeclick click screee..." to "(See, I heard about this railway...)",
                        "Clickclick click click click." to "(That isn't important right now.)",
                    ),
                    conversation(
                        "Scree clickclick click click?" to "(Why do we never go to crossroads and rob travellers?)",
                        "Clickclick scree click." to "(Maybe we need to think bigger.)",
                    ),
                ),
            SummoningPouchData.ALBINO_RAT to
                listOf(
                    conversation(
                        "Squeak squeak squeesqueak!" to "(Hey boss, we going to do anything wicked today?)",
                        "Squeesquee?" to "(Not even a little?)",
                        "Squeeeeee..." to "(Awwwwww...)",
                    ),
                    conversation(
                        "Squee squee squee squeak squeak?" to "(Hey boss, can we go and loot something now?)",
                        "Squeaksqueak?" to "(I dunno - where are we headed?)",
                        "Squeak squeak squeak squeeee!" to "(When we get there, let's loot something nearby!)",
                    ),
                    conversation(
                        "Squeak squeeesquee squeak?" to "(So what we up to today, boss?)",
                        "Squeaksqueak squee sqeee!" to "(Let's go robbin' graves again!)",
                        "Squee..." to "(Nuffin'...)",
                    ),
                ),
            SummoningPouchData.SPIRIT_KALPHITE to
                listOf(
                    conversation(
                        "Clatter clatteryclatter hss." to "(This activity is not optimal for us.)",
                        "Click click hsshsshsss hsssss." to "(We would not have to 'put up' with this in the hive.)",
                    ),
                    conversation(
                        "Clack hiss hiss clatter. Clack clatter?" to "(We are growing infuriated. What is our goal?)",
                        "Clack clack clack hsss." to "(There is no indecision in the hive.)",
                    ),
                    conversation(
                        "Hssshssshsss hss click clatter clack." to "(We find this to be wasteful of our time.)",
                        "Clackclack clackclackclack hsss..." to "(We would not face this form of abuse in the hive.)",
                    ),
                    conversation(
                        "Click clatter clatter hssshsshsss." to "(We grow tired of your antics, biped.)",
                        "Hsshsshss clatter. Click click click hsshsssclatter." to
                            "(In an inefficient way. In the hive, you would be replaced.)",
                    ),
                ),
            SummoningPouchData.BEAVER to
                listOf(
                    conversation(
                        "Gnawgnawgnaw gnaw gnawgnaw?" to "(Vot are you doing 'ere when we could be logging and building mighty dams, alors?)",
                        "Gnawgnawgnaw." to "(Why vouldn't you want to build a dam again?)",
                    ),
                    conversation(
                        "Gnaw gnawgnaw gnawgnaw?" to "(Pardonnez-moi - you call yourself a lumberjack?)",
                        "Gnaw gnaw gnaw." to "(Carry on zen.)",
                    ),
                    conversation(
                        "Gnaw gnaw gnawgnawgnawgnaw!" to "(Paul Bunyan 'as nothing on moi!)",
                        "Gnawgnaw?" to "(What was zat?)",
                    ),
                    conversation(
                        "Gnawgnaw gnawgnawgnaw gnaw gnaw." to "(Zis is a fine day to make some lumber.)",
                        "Gnawgnaw gnaw gnaw? Gnawgnawgnaw!" to "(So why are you talking to moi? Get chopping!)",
                    ),
                ),
            SummoningPouchData.BULL_ANT to
                listOf(
                    conversation(
                        "Clatterclatter click clatter click." to "(All right you worthless biped, fall in!)",
                        "Clatterclatterclick clack?" to "(We're going to work you so hard your boots fall off, understood?)",
                        "Clatterclatter!" to "(Carry on Private!)",
                    ),
                    conversation(
                        "Clickerclicker!" to "(Aten...hut!)",
                        "Clatter click clatter click!" to "(As you were, Private!)",
                    ),
                    conversation(
                        "Clatterclatter click..." to "(I can't believe they stuck me with you...)",
                        "Clackclatter click clack!" to "(Stow that, Private, and get back to work!)",
                    ),
                    conversation(
                        "Clatterclick catter clatter click?" to
                            "(What in the name of all the layers of the abyss do you think you're doing, biped?)",
                        "Clatterclick click clatter clickclick!" to "(Well double-time it, Private, whatever it is!)",
                    ),
                ),
            SummoningPouchData.MACAW to
                listOf(
                    conversation("Awk! Gimme the rum! Gimme the rum!" to ""),
                    conversation("Awk! I'm a pirate! Awk! Yo, ho, ho!" to ""),
                    conversation("Awk! Caw! Shiver me timbers!" to ""),
                ),
            SummoningPouchData.SPIRIT_TERRORBIRD to
                listOf(
                    conversation("Screeeee. Screescree screeee screee." to "(This is a fun little walk.)"),
                    conversation("Screeeeeeee screeee screeeeeeee." to "(I can keep this up for hours.)"),
                ),
            SummoningPouchData.IBIS to
                listOf(
                    conversation(
                        "Chirruptwit clackchirrup!" to "(I am the best fisherman ever!)",
                        "Clack..." to "(At home...)",
                    ),
                    conversation("Chirrup clackclack." to "(I like to fish!)"),
                    conversation("Twitclack." to "(I want to go fiiiish.)"),
                    conversation(
                        "Clackchirrup." to "(Hey, where are we?)",
                        "Chirrupchirrup clack?" to "(I just noticed we weren't fishing.)",
                    ),
                ),
            SummoningPouchData.WAR_TORTOISE to
                listOf(
                    conversation(
                        "Hsssss?" to "(What are we doing in this dump?)",
                        "Hssssssss?" to "(Oh, you would say that, wouldn't you?)",
                    ),
                    conversation(
                        "Hsssss!" to "(Hold up a minute, there!)",
                        "Hsssss!" to "(Yes, but you'll soon start up again, won't you?)",
                    ),
                    conversation(
                        "Hssssss." to "(Only when you want me to carry those heavy things of yours.)",
                        "Hsssssss?" to "(What about those lead ingots?)",
                    ),
                ),
            SummoningPouchData.BUNYIP to
                listOf(
                    conversation(
                        "Glorp glorpglorp glorp glorpglorp?" to "(Where are we going and why is it not to the beach?)",
                        "Glorp! Glorpglorpglorp glorpglorp!" to "(Bonza! I'll get my board ready!)",
                        "Glorp glorp glorpglorp..." to "(Awww, that's a drag...)",
                    ),
                    conversation(
                        "Glorpglorp, glorp glorpglorp glorp?" to "(Hey Bruce, can we go down to the beach t'day?)",
                        "Glorpglorp!" to "(Bonza!)",
                    ),
                    conversation(
                        "Glorp glorpglorp glorpglorp glorp, glorpglorp!" to "(Pass me another bunch of shrimps, mate!)",
                        "Glorpglorpglorp, glorpglorp glorp!" to "(Righty, but I do know that I want some shrimps!)",
                    ),
                    conversation(
                        "Glorpglorp..." to "(Sigh...)",
                        "Glorp glorp glorpglorpglorp, glorpglorp." to "(I'm dryin' out in this sun, mate.)",
                        "Glorpglorpglorp, glorpglorp glorp glorp, glorp!" to "(Well, fish oil is bonza for the skin, ya know.)",
                    ),
                ),
            SummoningPouchData.FRUIT_BAT to
                listOf(
                    conversation("Squeekasqueek squeek?" to "(How much longer do you want me for?)"),
                    conversation("Squeakdqueesqueak." to "(This place is fun!)"),
                    conversation("Squeeksqueekasqueek?" to "(Where are we going?)"),
                    conversation(
                        "Squeeksqueekasqueek squee?" to "(Can you smell lemons?)",
                        "Squeaksqueak squeaksqueesqueak." to "(Must just be thinking about them.)",
                    ),
                ),
            SummoningPouchData.UNICORN_STALLION to
                listOf(
                    conversation(
                        "Neigh neigh neighneigh snort?" to "(Isn't everything so awesomely wonderful?)",
                        "Whicker whicker snuffle." to "(I can see you're not tuning in.)",
                        "Whicker!" to "(Cosmic.)",
                    ),
                    conversation(
                        "Whicker whicker. Neigh, neigh, whinny." to "(I feel so, like, enlightened. Let's meditate and enhance our auras.)",
                        "Whicker..." to "(Bipeds...)",
                    ),
                    conversation(
                        "Whinny whinny whinny." to "(I think I'm astrally projecting.)",
                        "Whicker whicker whicker." to "(You're, like, no fun at all, man.)",
                    ),
                    conversation(
                        "Whinny, neigh!" to "(Oh, happy day!)",
                        "Snuffle whicker" to "(Man, you're totally, like, uncosmic.)",
                    ),
                ),
            /*
             * The pack yak's only unconditioned line. Its four translated monologues are all listed
             * on the transcript under the pack-yak-mask heading, so they are gated on an item this
             * run has not wired up and are deliberately not reproduced here - a yak without the
             * mask really does say nothing but this.
             */
            SummoningPouchData.PACK_YAK to
                listOf(
                    conversation("Barooo! Barooobaroooo!" to ""),
                ),
            /*
             * The steel titan is one of the familiars that speaks plain Common rather than animal
             * noise, and its conversations include the player's own replies. That also settles the
             * owner's "Steel titan is a silent no-op": the 2011 Knowledge Base's comprehension rule
             * ("you can never understand a familiar with a Summoning level of 91") only ever
             * removes a *translation*, and a titan has none to remove - it is simply understood.
             */
            SummoningPouchData.STEEL_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.FAMILIAR to "Forward, master, to a battle that will waken the gods!",
                        Speaker.PLAYER to "I'd rather not, if it's all the same to you.",
                        Speaker.FAMILIAR to "I shall never meet my end at this rate...",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "How do you wish to meet your end, master?",
                        Speaker.PLAYER to "Hopefully not for a very long time.",
                        Speaker.FAMILIAR to "You do not wish to be torn asunder by the thousand limbs of a horde of demons?",
                        Speaker.PLAYER to "No! I'm quite happy picking flax and turning unstrung bows into gold...",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Why must we dawdle when glory awaits?",
                        Speaker.PLAYER to "I'm beginning to think you just want me to die horribly...",
                        Speaker.FAMILIAR to "We could have deaths that bards sing of for a thousand years.",
                        Speaker.PLAYER to "That's not much compensation.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Master, we should be marching into glorious battle!",
                        Speaker.PLAYER to "You know, I think you're onto something.",
                        Speaker.FAMILIAR to "We could find a death befitting such heroes of Gielinor!",
                        Speaker.PLAYER to "Ah. You know, I'd prefer not to die...",
                        Speaker.FAMILIAR to "Beneath the claws of a mighty foe shall I be sent into the embrace of death!",
                    ),
                ),
            SummoningPouchData.SPIRIT_TZ_KIH to
                listOf(
                    listOf(
                        you("How's it going, Tz-Kih?"),
                        npc("Pree pree?", "(Pray pray?)"),
                        you("Don't start all that again."),
                        npc("Squee, squee squee.", "(Hmph, silly JalYt.)"),
                    ),
                    listOf(
                        npc("Squee squeepree pree squee?", "(Have you heard blood bat JalYt?)"),
                        you("Blood bats? You mean vampyre bats?"),
                        npc("Sqee. Sqee sqee.", "(Yes. Blood bat.)"),
                        you("Yes, I've heard them. What about them?"),
                        npc(
                            "Squee squee squeesquee, squee pree pree. Squee squee.",
                            "(Tz-Kih like blood bat, but drink pray pray not blood blood. Blood blood is yuck.)",
                        ),
                        you("Thanks, Tz-Kih, that's nice to know."),
                    ),
                ),
            SummoningPouchData.COMPOST_MOUND to
                listOf(
                    listOf(
                        npc("Schlorp sclorp sclorp!", "(Oi've gotta braand new comboine 'aarvester!)"),
                        you("A what?"),
                        npc(
                            "Schlorp schlurp sclorpsclorp splutter.",
                            "(Well, it's a flat bit a metal wi' a 'andle that i can use ta 'aarvest all combinations o' plaants.)",
                        ),
                        you("You mean a spade?"),
                        npc("Schlurpschlorp.", "(Aye, 'aat'll be it.)"),
                    ),
                    listOf(
                        npc("Schlorp, splort, splort, splutter shclorp?", "(What we be doin' 'ere, zur?)"),
                        you("Oh, I have a few things to take care of here, is all."),
                        npc("Schorp, splutter, splutter. Schlup schorp.", "(Aye, right ye are, zur. Oi'll be roight there.)"),
                    ),
                    listOf(
                        npc("Schlurp...schlorpschlorp schlurp splutter?", "(Errr...are ye gonna eat that?)"),
                        you("Eat what?"),
                        npc("Schlurpschlurp, schlorp schlorp.", "(Y've got summat on yer, goin' wastin'.)"),
                        you("Ewwww!"),
                        npc("Schlurp splutter?", "(So ye don' want it then?)"),
                        you("No I do not want it! Nor do I want to put my boot in your mouth for you to clean it off."),
                        npc("Splutter?", "(An' why not?)"),
                        you("It'll likely come out dirtier than when I put it in!"),
                    ),
                ),
            SummoningPouchData.GIANT_CHINCHOMPA to
                listOf(
                    listOf(
                        npc("Wagooly wanay wahoo!", "(Half a pound of tuppenny rice, half a pound of treacle...)"),
                        you("I hate it when you sing that song."),
                        npc("Wannobly wanay wahoo!", "(...that's the way the money goes...)"),
                        you("Couldn't you sing 'Kumbaya' or something?"),
                        npc("Wooble! Wibbly nooble!", "(...BANG, goes the chinchompa!)"),
                        you("Sheesh."),
                    ),
                    listOf(
                        npc("Gobbobbly nobble?", "(What's small, brown and blows up?)"),
                        you("A brown balloon?"),
                        npc("Wernibbly doodle! Nooble, nooble.", "(A chinchompa! Pull my finger.)"),
                        you("I'm not pulling your finger."),
                        npc("Bidoodly dooble. Boooooooble.", "(Nothing will happen. Truuuuust meeeeee.)"),
                        you("Oh, go away."),
                    ),
                    listOf(
                        npc("Wanibble boobbly nooble.", "(I seem to have found a paper bag.)"),
                        you("Well done. Anything in it?"),
                        npc(
                            "Wanooble de nobble babooble...goombly!",
                            "(Hmmm. Let me see. It seems to be full of some highly sought after, very expensive...chinchompa breath!)",
                        ),
                        you("No, don't pop it!"),
                        you("You just cannot help yourself, can you?"),
                    ),
                ),
            SummoningPouchData.VAMPYRE_BAT to
                listOf(
                    listOf(
                        npc("Squeak squeak squeak!", "(Ven are you going to feed me?)"),
                        you("Well for a start, I'm not giving you any of my blood."),
                    ),
                    listOf(
                        npc("Squeak squeak!", "(I vant to eat somethink.)"),
                        you("I'm sure you do; let's go see what we can find."),
                    ),
                    listOf(
                        npc("Squeak squeak?", "(Ven can I eat somethink?)"),
                        you("Just as soon as I find something to attack."),
                    ),
                ),
            SummoningPouchData.EVIL_TURNIP to
                listOf(
                    spokenConversation(
                        Speaker.PLAYER to "So, how are you feeling?",
                        Speaker.FAMILIAR to "My roots feel hurty. I thinking it be someone I eated.",
                        Speaker.PLAYER to "You mean someTHING you ate?",
                        Speaker.FAMILIAR to "Hur hur hur. Yah, sure, why not.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "When we gonna fighting things, boss?",
                        Speaker.PLAYER to "Soon enough.",
                        Speaker.FAMILIAR to "Hur hur hur. I gets the fighting!",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "I are turnip, hear me roar! I too deadly to ignore!",
                        Speaker.PLAYER to "I'm glad it's on my side...and not behind me.",
                    ),
                ),
            SummoningPouchData.VOID_RAVAGER to
                listOf(
                    listOf(
                        npc("Axejillud!", "(You look delicious!)"),
                        you("Don't make me dismiss you!"),
                    ),
                    listOf(
                        npc("Paihia whangamata raglan!", "(Take me to the rift!)"),
                        you("I'm not taking you there! Goodness knows what you'd get up to."),
                        npc("Pukeko inna pongatree...", "(I promise not to destroy your world...)"),
                        you("If only I could believe you..."),
                    ),
                    listOf(
                        npc("Whet her'tis noblerint hemindt osuf fer?", "(How do you bear life without ravaging?)"),
                        you("It's not always easy."),
                        npc("Thesli ngsan darro wsofout rag eousfort une...", "(I could show you how to ravage, if you like...)"),
                    ),
                ),
            SummoningPouchData.VOID_SHIFTER to
                listOf(
                    listOf(
                        npc("Twohou sehol ds-bot halikein di gnity!", "(What a splendid day, sir/madam!)"),
                        you("Yes, it is!"),
                        npc(
                            "Infai r-Vero na-wher ewelayo ursce ne.",
                            "(It could only be marginally improved, perhaps, by tea and biscuits.)",
                        ),
                        you("What a marvellous idea!"),
                    ),
                    listOf(
                        npc("Hap pyfamil iesareal lali ke?", "(How do you do?)"),
                        you("Okay, I suppose."),
                        npc("Everyun ha ppyfam ilyi sunha'pp yinit sownway.", "(Marvellous, simply marvellous!)"),
                    ),
                ),
            SummoningPouchData.VOID_SPINNER to
                listOf(
                    listOf(
                        npc("Whizzz whirr spinnninnin!", "(Let's go play hide an' seek!)"),
                        you("Okay, you hide and I'll come find you."),
                        npc("Whirrwhirr spin whirr!", "(You'll never find me!)"),
                        you("What a disaster that would be..."),
                    ),
                    listOf(
                        npc("Whirr whirrrr spin.", "(My mummy told me I was clever.)"),
                        you("Aren't you meant to be the essence of a spinner? How do you have a mother?"),
                        npc("Whirrr-spinn spinnninnnin?", "(What you mean, 'essence'?)"),
                        you("Never mind, I don't think it matters."),
                        npc("Spiiiiinnn whhiiiiirrrr!", "(My logimical powers has proved me smarterer than you!)"),
                    ),
                    listOf(
                        npc("Whirr!", "(I want to tickle you!)"),
                        you("No! You've got so many tentacles!"),
                        npc("Spinn!", "(I'm coming to tickle you!)"),
                        you("Aieee!"),
                    ),
                    listOf(
                        npc("Spinnwhirr whizzwhizz?", "(Where's the sweeties?)"),
                        you("They are wherever good spinners go."),
                        npc("Spinnspinnnnnnin whirr!", "(Yay for me!)"),
                    ),
                ),
            SummoningPouchData.VOID_TORCHER to
                listOf(
                    listOf(
                        you("You okay there, spinner?"),
                        npc("Squa scree!", "(I not spinner!)"),
                        you("Sorry, splatter?"),
                        npc("Squa scree!", "(I not splatter either!)"),
                        you("No, wait, I meant defiler."),
                        npc("Scree!", "(I torcher!)"),
                        you("Hehe, I know. I was just messing with you."),
                        npc("Scree. Squa squa.", "(Grr. Don't be such a pest.)"),
                    ),
                    listOf(
                        npc("Squa scree scree scree scree...", "('T' is for torcher, that's good enough for me...)"),
                        npc("Squa scree scree scree scree?", "('T' is for torcher, I'm happy you can see.)"),
                        you("You're just a bit weird, aren't you?"),
                    ),
                    listOf(
                        npc("Scree, squa. scree! Screescree squa screescree! Screeeee!", "(Burn, baby, burn! Torcher inferno!)"),
                        you("*Wibble*"),
                    ),
                    listOf(
                        npc("Squa...scree...", "(So hungry...must devour...)"),
                        you("*Gulp* Er, yeah, I'll find you something to eat in a minute."),
                        npc("Squa scree scree?", "(Is flesh-bag scared of torcher?)"),
                        you("No, no. I, er, always look like this... honest."),
                    ),
                ),
            SummoningPouchData.PYRELORD to
                listOf(
                    listOf(
                        npc("Crackle sizzle hiss spit?", "(What are we doing here?)"),
                        you("Whatever I feel like doing."),
                        npc("Craclde spit sizzle hiss crack spit hiss sizzle, crack spit.", "(I was summoned by a greater demon once, you know.)"),
                        npc("Sizzle crack spit hiss crack...", "(He said we'd see the world...)"),
                        you("What happened?"),
                        npc("Crack sizzle sizzle spit hiss crackle!", "(He was slain; it was hilarious!)"),
                    ),
                    listOf(
                        npc("Hiss spit crackle sizzle spit crackle...", "(I used to be feared across five planes...)"),
                        you("Oh dear, now you're going to be sad all day!"),
                        npc("Crackle spit hiss sizzle crack sizzle.", "(At least I won't be the only one.)"),
                    ),
                    listOf(
                        npc("Crackle hiss sizzle spit?", "(Have you never been on fire?)"),
                        you("You say that like it's a bad thing."),
                        npc("Crackle spit? Sizzle hiss hiss crack spit sizzle!", "(Isn't it? It gives me the heebie-jeebies!)"),
                        you("You're afraid of something?"),
                        npc("Spit hiss sizzle crack spit crackle.", "(Yes: I'm afraid of being you.)"),
                        you("I don't think he likes me..."),
                    ),
                ),
            SummoningPouchData.BLOATED_LEECH to
                listOf(
                    listOf(
                        npc("Schlurp sclurpschlorp schlurp schlurp.", "(I'm afraid it's going to have to come off.)"),
                        you("What is?"),
                        npc("Schlurpschlurp. Schlurpschlorpschlorp.", "(Never mind. Trust me, I'm almost a doctor.)"),
                        you("I think I'll get a second opinion."),
                    ),
                    listOf(
                        npc("Schlurp schlorpschlurpsclurp schlorp schlurp.", "(You're in a critical condition.)"),
                        you("Is it terminal?"),
                        npc(
                            "Schlurp schlurp. Schlorpschlorp schlurp schlurp.",
                            "(Not yet. Let me get a better look and I'll see what I can do about it.)",
                        ),
                        you("There are two ways to take that...and I think I'll err on the side of caution."),
                    ),
                    listOf(
                        npc("Schlurpsclurp schlorpschlurp.", "(Let's get a look at that brain of yours.)"),
                        you("What? My brains stay inside my head, thanks."),
                        npc("Schlurpschlurp schlorschlurp.", "(That's okay, I can just drill a hole.)"),
                        you("How about you don't and pretend you did?"),
                    ),
                    listOf(
                        npc("Schlurp schlorp schlurpsclurp schlorp schlurp.", "(I think we're going to need to operate.)"),
                        you("I think we can skip that for now."),
                        npc("Schlurpschlurp schlorpschlurp schlorp?", "(Who's the doctor here?)"),
                        you("Not you."),
                        npc(
                            "Schlurpschlurp schlurp schlurp schlorp. Schlurpschlurp schlorp?",
                            "(I may not be a doctor, but I'm keen. Does that not count?)",
                        ),
                        you("In most other fields, yes; in medicine, no."),
                    ),
                ),
            SummoningPouchData.SPIRIT_JELLY to
                listOf(
                    listOf(
                        npc("Jigglejigglejigglejigglejiggle.", "(Play play play play!)"),
                        you("The only game I have time to play is the 'Staying Very Still' game."),
                        npc("Wigglewobble...", "(But that game is soooooo boooooring...)"),
                        you("How about we use the extra house rule, that makes it the 'Staying Very Still and Very Quiet' game."),
                        npc("B'doing! Flobbalobbaflobble.", "(Happy happy! I love new games!)"),
                    ),
                    listOf(
                        npc("Jigglebubblewobbleflobblelobble!", "(It's playtime now!)"),
                        you("Okay, how about we play the 'Staying Very Still' game."),
                        npc("Wigglewobble...", "(But that game is boooooring...)"),
                        you("If you win then you can pick the next game, how about that?"),
                        npc("B'doing!", "(Happy happy!)"),
                    ),
                    listOf(
                        npc("Flobbleflobblejigglewobblelobblewobble.", "(Can we go over there now, pleasepleasepleasepleeeeease?)"),
                        you("Go over where?"),
                        npc("Jiggleblobbleflobblejiggle.", "(I dunno, someplace fun, pleasepleaseplease!)"),
                        you("Okay, but first, let's play the 'Sitting Very Still' game."),
                        npc("Wigglewobble...", "(But that game is boooooring...)"),
                        you("Well, if you win we can go somewhere else, okay?"),
                        npc("B'doing!", "(Happy happy!)"),
                    ),
                ),
            SummoningPouchData.SPIRIT_KYATT to
                listOf(
                    listOf(
                        npc("Rawr grrrowl?", "(Guess who wants a belly rub, human.)"),
                        you("Umm...is it me?"),
                        npc("Hisss snarl?", "(No, human, it is not you. Guess again.)"),
                        you("Is it the Duke of Lumbridge?"),
                        npc("Grrrrowl rawr.", "(You try my patience, human!)"),
                        you("Is it Zamorak? That would explain why he's so cranky."),
                        npc("Hisss rawr.", "(Please do not make me destroy you before I get my belly rub!)"),
                    ),
                    listOf(
                        you("Here, kitty!"),
                        npc("Rowl hiss?", "(What do you want. human?)"),
                        you("I just thought I would see how you were."),
                        npc("Snarl grrrowl!", "(I do not have time for your distractions. Leave me be!)"),
                        you("Well, sorry! Would a ball of wool cheer you up?"),
                        npc("Growwwwl grrrrrr hiss rawr?", "(How dare you insult my intelli- what colour wool?)"),
                        you("Umm...white?"),
                        npc("Growl hisss!", "(I will end you!)"),
                    ),
                ),
            SummoningPouchData.SPIRIT_LARUPIA to
                listOf(
                    listOf(
                        you("Hello friend!"),
                        npc("Hsss snarl.", "('Friend', master? I do not understand this word.)"),
                        you("Friends are people, or animals, who like one another. I think we are friends."),
                        npc("Hisss growl.", "(Ah, I think I understand friends, master.)"),
                        you("Great!"),
                        npc("Hsssss grrrr.", "(A friend is someone who looks tasty, but you don't eat.)"),
                        you("!"),
                    ),
                    listOf(
                        npc("Growl hisssss!", "(What are we doing today, master?)"),
                        you("I don't know, what do you want to do?"),
                        npc("Sssss hssss snarl.", "(I desire only to hunt and to serve my master.)"),
                        you("Err...great! I guess I'll decide then."),
                    ),
                    listOf(
                        npc("Hssss snarl?", "(Master, do you ever worry that I might eat you?)"),
                        you("No, of course not! We're pals."),
                        npc("Growl ssssss hisss.", "(That is good, master.)"),
                        you("Should I?"),
                        npc("Snarrrl growl.", "(Of course not, master.)"),
                        you("Oh. Good."),
                    ),
                ),
            SummoningPouchData.SPIRIT_GRAAHK to
                listOf(
                    listOf(
                        you("Your spikes are looking particularly spiky today."),
                        npc("Graaaaahk raaaawr?", "(Really, you think so?)"),
                        you("Yes. Most pointy, indeed."),
                        npc("Raaaawr...", "(That's really kind of you to say. I was going to spike you but I won't now...)"),
                        you("Thanks?"),
                        npc("...Grrrrr ark.", "(...I'll do it later instead.)"),
                        you("*sigh!*"),
                    ),
                    listOf(
                        npc("Graahk grrrrowl?", "(My spikes hurt, could you pet them for me?)"),
                        you("Aww, of course I can I'll just... Oww! I think you drew blood that time."),
                    ),
                    listOf(
                        you("How's your day going?"),
                        npc("Graaahk. Grak grak!", "(It's great! Actually, We got something to show you!)"),
                        you("Oh? What's that?"),
                        npc("Grrrrrk hiss graaaaa!", "(You'll need to get closer!)"),
                        you("I can't see anything..."),
                        npc("Grah grr aaaaahk grahk.", "(It's really small - even closer.)"),
                        you("Oww! I'm going to have your spikes trimmed!"),
                    ),
                ),
            SummoningPouchData.KARAMTHULHU_OVERLORD to
                listOf(
                    spokenConversation(
                        Speaker.PLAYER to "Do you want...",
                        Speaker.FAMILIAR to "(Silence!)",
                        Speaker.PLAYER to "But I only...",
                        Speaker.FAMILIAR to "(Silence!)",
                        Speaker.PLAYER to "Now, listen here...",
                        Speaker.FAMILIAR to "(SIIIIIILLLLLEEEEENCE!)",
                        Speaker.PLAYER to "Fine!",
                        Speaker.FAMILIAR to "(Good!)",
                        Speaker.PLAYER to "Maybe I'll be so silent you'll think I never existed",
                        Speaker.FAMILIAR to "(Oh, how I long for that day...)",
                    ),
                    spokenConversation(
                        Speaker.PLAYER to "...",
                        Speaker.FAMILIAR to "(The answer is 'Be silent'!)",
                        Speaker.PLAYER to "You have no idea what I was going to ask you.",
                        Speaker.FAMILIAR to "(Yes I do; I know all!)",
                        Speaker.PLAYER to "Then you will not be surprised to know I was going to ask you what you wanted to do today.",
                        Speaker.FAMILIAR to "(You dare doubt me!)",
                        Speaker.PLAYER to "Well, how about I dismiss you so you can go and do what you like?",
                        Speaker.FAMILIAR to "(Silence! Your burbling vexes me greatly!)",
                    ),
                ),
            SummoningPouchData.SMOKE_DEVIL to
                listOf(
                    listOf(
                        npc("Parp! Hooot! Honkhonk! Parp!", "(When are you going to be done with that?)"),
                        you("Soon, I hope."),
                        npc("Parp parp! Hoooot!", "(Good, because this place is too breezy.)"),
                        you("What do you mean?"),
                        npc("Hooooooooonk! Parpparpparp! Hoot!", "(I mean, it's tricky to keep hovering in this draft.)"),
                        you("Ok, we'll move around a little if you like."),
                        npc("Peep peep!", "(Yes please!)"),
                    ),
                    listOf(
                        npc("Peep!", "(Hey!)"),
                        you("Yes?"),
                        npc("Honkhonk! Hoooot!", "(Where are we going again?)"),
                        you("Well, I have a lot of things to do today, so we might go a lot of places."),
                        npc("Honkhonk! Hoooot!", "(Are we there yet?)"),
                        you("No, not yet."),
                        npc("Honkhonk! Hoooot!", "(How about now?)"),
                        you("No."),
                        npc("Hooothoot! Honk! Parp!", "(Okay, just checking.)"),
                    ),
                    listOf(
                        npc("Hoot! Hoot! Honk! Parp!", "(Why is it always so cold here?)"),
                        you("I don't think it's that cold."),
                        npc("Honkhonk! Parp peep! Hoot!", "(It is compared to back home.)"),
                        you("How hot is it where you are from?"),
                        npc("Hooothoot! Parp! Peep! Honkhonk!", "(I can never remember. What is the vaporisation point of steel again?)"),
                        you("Pretty high."),
                        you("No wonder you feel cold here..."),
                    ),
                ),
            SummoningPouchData.SPIRIT_COBRA to
                listOf(
                    listOf(
                        npc("Hsssshsssss hssshssss hssss?", "(Do we have to do thissss right now?)"),
                        you("Yes, I'm afraid so."),
                        npc("Hsssssssshsss hssssshsssss...", "(You are under my sssspell...)"),
                        you("I will do as you ask..."),
                        npc("Hsssshsssss hssshssss hssss?", "(Do we have to do thissss right now?)"),
                        you("Not at all, I had just finished!"),
                    ),
                    listOf(
                        npc("Hssssshssssss...hssssss hssss.", "(You are feeling ssssleepy...)"),
                        you("I am feeling sssso ssssleepy..."),
                        npc("Hsssshsssssssss hssssssh sssssssss!", "(You will bring me lotssss of sssstuff!)"),
                        you("What ssssort of sssstuff?"),
                        npc("Hssshssshsss hsss hsss?", "(What ssssort of ssstuff have you got?)"),
                        you("All kindsss of sssstuff."),
                        npc("Hsssss hssss hsssssshssshssssss!", "(Then just keep bringing sssstuff until I'm ssssatissssfied!)"),
                    ),
                    listOf(
                        npc("Hssss hssssss ssss hssssss hssss!", "(I am the king of the world!)"),
                        you("You know, I think there is a law against snakes being the king."),
                        npc("Hsss hssss ssss hsssshsssss ssss...", "(My will is your command...)"),
                        you("I am yours to command..."),
                        npc("Hssss hssssss ssss hssssss hssss!", "(I am the king of the world!)"),
                        you("All hail King Serpentor!"),
                    ),
                ),
            SummoningPouchData.BARKER_TOAD to
                listOf(
                    listOf(
                        npc("Braaap, craaaawk craaaawk, craaaaawk.", "(Ladies and gentlemen; for my next trick, I shall swallow this fly!)"),
                        you("Seen it."),
                        npc("Craaaaaw craaaaaaaw braaaaap craaaaawk?", "(Ah, but last time was the frog...on fire?)"),
                        you("No! That would be a good trick."),
                        npc("Braaaaaaap...", "(Well, it won't be this time either.)"),
                        you("Awwwww..."),
                    ),
                    listOf(
                        npc("Braapbraapbraap! Craaaawk craaawk!", "(Roll up, roll up, roll up! See the greatest show on Gielinor!)"),
                        you("Where?"),
                        npc("Braap braap, craaawk.", "(Well, it's kind of...you.)"),
                        you("Me?"),
                        npc("Braapbraapbraap! Craaaawk braap craaawk!", "(Roll up, roll up, roll up! See the greatest freakshow on Gielinor!)"),
                        you("Don't make me smack you, slimy."),
                    ),
                    listOf(
                        npc("Braaaaaaaaaaaaaaaaaaaaaaap!", "(*Burp!*)"),
                        you("That's disgusting behaviour!"),
                        npc("Braap craaaaawk craaaawk.", "(That, my dear boy, was my world-renowned belching.)"),
                        you("I got that part. Why are you so happy about it?"),
                        npc("Braaaaaaap craaaaaawk craaaaaaaawk.", "(My displays have bedazzled the crowned heads of Gielinor.)"),
                        you("I'd give you a standing ovation, but I have my hands full."),
                    ),
                ),
            SummoningPouchData.RAVENOUS_LOCUST to
                listOf(
                    listOf(
                        npc("Click whiiine whiiiiine click click?", "(Hey, man, can you spare some lentils?)"),
                        you("What would you want with lentils?"),
                        npc("Whiiiiiinewhiiiiiiine click whiiiiiiiine.", "(I was going to make a casserole.)"),
                        you("How? You don't have a fire, pans or thumbs."),
                        npc("Whiiiiiiiiiiiiine!", "(Stop hassling me, man.)"),
                    ),
                    listOf(
                        npc("Whiiiiiine click click!", "(Man, it's a totally groovy day.)"),
                        you("That it is."),
                        npc("Whiiiiine whiiiiine whinewhiiiine.", "(Now, if only I wasn't being held down by 'The Man'.)"),
                        you("Which man?"),
                        npc("Clickclack whiiiiiine whiiiiinewhiiine.", "('The Man'; the one that keeps harshing my mellow.)"),
                        you("'Harshing your mellow'? Okay, I don't want to know any more."),
                    ),
                    listOf(
                        npc("Whiiiiine whinewhiiiine?", "(Man, how about time?)"),
                        you("I think it's about midday."),
                        npc("Clickwhiiiiine whiiiiiiiiiiiiine...", "(No, man. Isn't time, like, massive?)"),
                        you("I don't think an abstract concept can have mass..."),
                        npc("Whineclick click!", "(Oh, man, that's heavy.)"),
                    ),
                ),
            SummoningPouchData.ARCTIC_BEAR to
                listOf(
                    listOf(
                        npc("Grrrrraw! Unf unf. Grooooowl grrrrrr.", "(Crikey! We're tracking ourselves a real live one here. I call 'em 'Brighteyes'.)"),
                        you("Will you stop stalking me like that?"),
                        npc("Graaaaw! Unf grrrrrrrrrr grooooowl.", "(Lookit that! Something's riled this one up good and proper.)"),
                        you("Who are you talking to anyway?"),
                        npc("Graaaawl groooowl groooowl.", "(Looks like I've been spotted.)"),
                        you("Did you think you didn't stand out here or something?"),
                    ),
                    listOf(
                        npc("Graaawl grooowl graaaw grooowl.", "(These little guys get riled up real easy.)"),
                        you("Who wouldn't be upset with a huge bear tracking along behind them, commenting on everything they do?"),
                    ),
                ),
            SummoningPouchData.PHOENIX to
                listOf(
                    listOf(
                        npc("Skreee skree skrooo skrooooouuu.", "(I want to burn something.)"),
                        you("Why are you looking at me like that?"),
                        npc("Skeeeeooouooou! Skree skrooo, skrooouuee skreee!", "(Please! It won't hurt that much, and I'll bring you back straight away!)"),
                        you("Maybe later. Much later. When I'm dead from natural causes already. And medicine has failed to bring me back."),
                        npc("Skreee skreeeooouu skroou!", "(I'll hold you to it!)"),
                    ),
                    listOf(
                        you("May I ask you a question?"),
                        npc("Skreeoooouuu, skreeee skreeeeoooo.", "(Yes, but you have already asked me a question.)"),
                        npc("Skreeeooo, skreee skreeeeee skreeoooo.", "(You should have said 'may I ask you two questions?'.)"),
                        you("Erm, may I ask you two questions?"),
                        npc("Skroo.", "(No.)"),
                        you("..."),
                    ),
                    listOf(
                        you("May I ask you... TWO questions?"),
                        npc("Skree ree ree! Skree, skreee skrooou skreeeoou.", "(Heh heh heh. The answer to your first is yes. You may ask your second.)"),
                        you("What was Gielinor like in the distant past?"),
                        npc("Skreee skreeeeou skreeou. Skreee skree.", "(It was like it is now, only younger.)"),
                        you("..."),
                        you("You, madam, are the most pestiferous poultry I have ever met."),
                        npc("Skree ree ree!", "(Heh heh heh!)"),
                    ),
                ),
            SummoningPouchData.OBSIDIAN_GOLEM to
                listOf(
                    spokenConversation(
                        Speaker.FAMILIAR to "Let us go forth and prove our strength, Master!",
                        Speaker.PLAYER to "Where would you like to prove it?",
                        Speaker.FAMILIAR to "The caves of the TzHaar are filled with monsters for us to defeat, Master!",
                        Speaker.PLAYER to "Have you ever met TzTok-Jad?",
                        Speaker.FAMILIAR to "Alas, Master, I have not. No Master has ever taken me to see him.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "How many foes have you defeated, Master?",
                        Speaker.PLAYER to "Quite a few, I should think.",
                        Speaker.FAMILIAR to "Was your first foe as mighty as the volcano, Master?",
                        Speaker.PLAYER to "Um, not quite.",
                        Speaker.FAMILIAR to "I am sure it must have been a deadly opponent, Master!",
                        Speaker.PLAYER to "*Cough* It might have been a chicken. *Cough*",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Master! We are truly a mighty duo!",
                        Speaker.PLAYER to "Do you think so?",
                        Speaker.FAMILIAR to "Of course, Master! I am programmed to believe so.",
                        Speaker.PLAYER to "Do you do anything you're not programmed to?",
                        Speaker.FAMILIAR to "No, Master.",
                        Speaker.PLAYER to "I guess that makes things simple for you...",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Do you ever doubt your programming, Master?",
                        Speaker.PLAYER to "I don't have programming. I can think about anything I like.",
                        Speaker.FAMILIAR to "What do you think about, Master?",
                        Speaker.PLAYER to "Oh, simple things: the sound of one hand clapping, where the gods come from... Simple things.",
                        Speaker.FAMILIAR to "Paradox check = positive. Error. Reboot.",
                    ),
                ),
            SummoningPouchData.PRAYING_MANTIS to
                listOf(
                    listOf(
                        npc("Chitter chirrup chirrup?", "(Have you been following your training, grasshopper?)"),
                        you("Yes, almost every day."),
                        npc("Chirrupchirrup chirrup.", "('Almost' is not good enough.)"),
                        you("Well, I'm trying as hard as I can."),
                        npc("Chirrup chitter chitter chirrup?", "(How do you expect to achieve enlightenment at this rate, grasshopper?)"),
                        you("Spontaneously."),
                    ),
                    listOf(
                        npc("Chitterchitter chirrup clatter.", "(Today, grasshopper, I will teach you to walk on rice paper)"),
                        you("What if I can't find any?"),
                        npc("Clatter chitter click chitter...", "(Then we will wander about and punch monsters in the head...)"),
                        you("I could do so in an enlightened way if you want?"),
                        npc("Chirrupchitter!", "(That will do!)"),
                    ),
                    listOf(
                        npc("Clatter chirrup chirp chirrup clatter clatter.", "(A wise man once said; 'Feed your mantis and it will be happy')"),
                        you("Is there any point to that saying?"),
                        npc("Clatter chirrupchirrup chirp.", "(I find that a happy mantis is its own point.)"),
                    ),
                ),
            SummoningPouchData.FORGE_REGENT to
                listOf(
                    listOf(
                        npc("Crackley spit crack sizzle?", "(Can we go Smithing?)"),
                        you("Maybe."),
                        npc("Hiss?", "(Can we go smelt something?)"),
                        you("Maybe."),
                        npc("Flicker crackle sizzle?", "(Can we go mine something to smelt?)"),
                        you("Maybe."),
                        npc("Sizzle flicker!", "(Yay! I like doing that!)"),
                        you("..."),
                    ),
                    listOf(
                        npc("Sizzle!", "(I like logs.)"),
                        you("They are useful for making planks."),
                        npc("Sizzley crack hiss spit.", "(No, I just like walking on them. They burst into flames.)"),
                        you("It's a good job I can use you as a firelighter really!"),
                    ),
                    listOf(
                        npc("Sizzle...", "(I'm bored.)"),
                        you("Are you not enjoying what we're doing?"),
                        npc("Crackley crickle sizzle.", "(Oh yes, but I'm still bored.)"),
                        you("Oh, I see."),
                        npc("Sizzle hiss?", "(What's that over there?)"),
                        you("I don't know. Should we go and look?"),
                        npc("Hiss crackle spit sizzle crack?", "(Nah, that's old news - I'm bored of it now.)"),
                        npc("Crackle crickle spit hiss?", "(Oooooh ooooh oooooh, what's that over there?)"),
                        you("But...wha...where now?"),
                        npc("Sizzle crack crickle.", "(Oh no matter, it no longer interests me.)"),
                        you("You're hard work."),
                    ),
                ),
            SummoningPouchData.TALON_BEAST to
                listOf(
                    listOf(
                        npc("Hssssssssss?", "(Is this all you apes do all day, then?)"),
                        you("Well, we do a lot of other things, too."),
                        npc("Mrrrrow mrrrrowl. Raaaaawr, hssss hsss.", "(That's dull. Lets go find something and bite it.)"),
                        you("I wouldn't want to spoil my dinner."),
                        npc("Hssssss? Mrrrowl.", "(So, I have to watch you trudge about again? Talk about boring.)"),
                    ),
                    listOf(
                        npc("Mrooooooowl...", "(This place smells odd...)"),
                        you("Odd?"),
                        npc("Hssssssssssss...", "(Yes, not enough is rotting...)"),
                        you("For which I am extremely grateful."),
                    ),
                    listOf(
                        npc("Rawr! Mrrrrow!", "(C'mon! Lets go fight stuff!)"),
                        you("What sort of stuff?"),
                        npc("Mrrrrowl, purrrgrowl? Raaarwl rawvwszvwr. Mrow.", "(I dunno? Giants, monsters, vaguely-defined philosophical concepts. You know: stuff.)"),
                        you("How are we supposed to fight a philosophical concept?"),
                        npc("Mrrrrrrow purrrrrrrrow!", "(With subtle arguments and pointy sticks!)"),
                        you("Well, I can see you're going to go far in debates."),
                    ),
                ),
            SummoningPouchData.MOSS_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.FAMILIAR to "Oh, look! A bug!",
                        Speaker.PLAYER to "It's quite a large bug.",
                        Speaker.FAMILIAR to "He's so cute! I wanna keep him.",
                        Speaker.PLAYER to "Well, be careful.",
                        Speaker.FAMILIAR to "I'm gonna call him Buggie and I'm gonna keep him in a box.",
                        Speaker.PLAYER to "Don't get overexcited.",
                        Speaker.FAMILIAR to "I'm gonna feed him and we're gonna be so happy together!",
                        Speaker.FAMILIAR to "Aww...Buggie went squish.",
                        Speaker.PLAYER to "Sigh.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "What are we doing today?",
                        Speaker.PLAYER to "Let's just wait and see.",
                        Speaker.FAMILIAR to "I want to do some squishing of tiny things!",
                        Speaker.PLAYER to "Preferably not me.",
                        Speaker.FAMILIAR to "Even if only a little bit, like your foot or something?",
                        Speaker.PLAYER to "Um, no. I really don't fancy being squished today, thanks.",
                        Speaker.FAMILIAR to "Awww...",
                    ),
                ),
            SummoningPouchData.ICE_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.PLAYER to "How are you feeling?",
                        Speaker.FAMILIAR to "Hot.",
                        Speaker.PLAYER to "Are you ever anything else?",
                        Speaker.FAMILIAR to "Sometimes I'm just the right temperature: absolute zero.",
                        Speaker.PLAYER to "Absolute zero; what is it?",
                        Speaker.FAMILIAR to "Oh...it's the lowest temperature that can exist.",
                        Speaker.PLAYER to "Like the temperature of ice?",
                        Speaker.FAMILIAR to "Um, no. Rather a lot colder.",
                        Speaker.PLAYER to "Yikes! That's rather chilly.",
                        Speaker.FAMILIAR to "Yeah. Wonderful, isn't it?",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Can we just stay away from fire for a while?",
                        Speaker.PLAYER to "I like fire, it's so pretty.",
                        Speaker.FAMILIAR to "Personally, I think it's terrifying.",
                        Speaker.PLAYER to "Why?",
                        Speaker.FAMILIAR to "I'm not so keen on hot things.",
                        Speaker.PLAYER to "Ah.",
                        Speaker.FAMILIAR to "Indeed.",
                        Speaker.PLAYER to "...let's get on with it.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "It's too hot here.",
                        Speaker.PLAYER to "It's really not that hot. I think it's rather pleasant.",
                        Speaker.FAMILIAR to "Well, it's alright for some. Some of us don't like the heat. I burn easily - well, okay, melt.",
                        Speaker.PLAYER to "Well, at least I know where to get a nice cold drink if I need one.",
                        Speaker.FAMILIAR to "What was that?",
                        Speaker.PLAYER to "Nothing. Hehehehe",
                    ),
                ),
            SummoningPouchData.HYDRA to
                listOf(
                    listOf(
                        npc("Raaaaspraaaaasp?", "(Isn't it hard to get things done with just one head?)"),
                        you("Not really!"),
                        npc("Raaaasp raaaaasp raaaaasp?", "(Well, I suppose you work with what you've got, right?)"),
                        npc("Raaaaaasp rasssssssssp raaaaasp.", "(At least they don't have someone whittering in their ear all the time.)"),
                        npc("Raaaaaaasp!", "(Quiet, you!)"),
                    ),
                    listOf(
                        npc("Raaaaasp raaaaaasp!", "(Man, I feel good!)"),
                        npc("Raaaaaasp sssssssss raaaaasp.", "(That's easy for you to say.)"),
                        you("What's up?"),
                        npc("Raaa...", "(Well...)"),
                        npc("Raaaaaasp ssss rassssssp.", "(Don't pay any attention, they are just feeling whiny.)"),
                        you("But they're you, aren't they?"),
                        npc("Raaaaaaasp raaasp rassssp!", "(Don't remind me!)"),
                    ),
                    listOf(
                        npc("Rassssssp rasssssssp!", "(You know, two heads are better than one!)"),
                        npc("Raaaaasp rassssp ssssssp...", "(Unless you're the one doing all the heavy thinking...)"),
                        you("I think I'll stick to one for now, thanks."),
                    ),
                ),
            SummoningPouchData.SPIRIT_DAGANNOTH to
                listOf(
                    listOf(
                        npc("Grooooooowl graaaaawl raaaawl?", "(Are you ready to surrender to the power of the Deep Waters?)"),
                        you("Err, not really."),
                        npc("Rooooowl?", "(How about now?)"),
                        you("No, sorry."),
                        npc("Rooooowl?", "(How about now?)"),
                        you("No, sorry. You might want to try again a little later."),
                    ),
                    listOf(
                        npc("Groooooowl. Hsssssssssssssss!", "(The Deeps will swallow the lands. None will stand before us!)"),
                        you("What if we build boats?"),
                        npc("Hsssssssss groooooowl? Hssssshsss grrooooooowl?", "(What are boats? The tasty wooden containers full of meat?)"),
                        you("I suppose they could be described as such, yes."),
                    ),
                    listOf(
                        npc("Raaaawl!", "(Submit!)"),
                        you("Submit to what?"),
                        npc("Hssssssssss rawwwwwl graaaawl!", "(To the inevitable defeat of all life on the Surface!)"),
                        you("I think I'll wait a little longer before I just keel over and submit, thanks."),
                        npc("Hsssss, grooooowl, raaaaawl.", "(Well, it's your choice, but those that submit first will be eaten first.)"),
                        you("I'll pass on that one, thanks."),
                    ),
                ),
            SummoningPouchData.GEYSER_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.FAMILIAR to "Hey mate, how are you?",
                        Speaker.PLAYER to "Not so bad.",
                        Speaker.FAMILIAR to "Did you know that during the average human life-span the heart will beat approximately 2.5 billion times?",
                        Speaker.PLAYER to "Wow, that is a lot of non-stop work!",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Did you know that in one feeding a mosquito can absorb one-and-a-half times its own body weight in blood?",
                        Speaker.PLAYER to "Eugh.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Did you know that Gielinor gets 100 tons heavier every day, due to dust falling from space?",
                        Speaker.PLAYER to "What a fascinating fact.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Did you know that a snail can sleep for three years?",
                        Speaker.PLAYER to "I wish I could do that. Ah...sleep.",
                    ),
                ),
            SummoningPouchData.SWAMP_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.FAMILIAR to "I'm alone, all alone I say.",
                        Speaker.PLAYER to "Oh, stop being so melodramatic.",
                        Speaker.FAMILIAR to "It's not easy being greenery...well, decomposing greenery.",
                        Speaker.PLAYER to "Surely, you're not the only swamp...thing in the world? What about the other swamp titans?",
                        Speaker.FAMILIAR to "They're not my friends...they pick on me...they're so mean...",
                        Speaker.PLAYER to "Why would they do that?",
                        Speaker.FAMILIAR to "They think I DON'T smell.",
                        Speaker.PLAYER to "Oh, yes. That is, er, mean...",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Are you my friend, master?",
                        Speaker.PLAYER to "Of course I am. I summoned you, didn't I?",
                        Speaker.FAMILIAR to "Yes, but that was just to do some fighting. When you're done with me you'll send me back.",
                        Speaker.PLAYER to "I'm sure I'll need you again later.",
                        Speaker.FAMILIAR to "Please don't send me back.",
                    ),
                    spokenConversation(
                        Speaker.PLAYER to "Cheer up, it might never happen!",
                        Speaker.FAMILIAR to "Oh, why did you have to go and say something like that?",
                        Speaker.PLAYER to "Like what? I'm trying to cheer you up.",
                        Speaker.FAMILIAR to "There's no hope for me, oh woe, oh woe.",
                        Speaker.PLAYER to "I'll leave you alone, then.",
                        Speaker.FAMILIAR to "NO! Don't leave me, master!",
                    ),
                ),
            SummoningPouchData.WOLPERTINGER to
                listOf(
                    listOf(npc("Raaar! Mewble, whurf whurf.")),
                ),
            SummoningPouchData.ABYSSAL_TITAN to
                listOf(
                    listOf(npc("Scruunt, scraaan.")),
                ),
            SummoningPouchData.IRON_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.PLAYER to "Titan?",
                        Speaker.FAMILIAR to "Yes, boss?",
                        Speaker.PLAYER to "What's that in your hand?",
                        Speaker.FAMILIAR to "I'm glad you asked that, boss.",
                        Speaker.FAMILIAR to
                            "This is the first prototype for the Iron Titan (tm) action figure. " +
                            "You just pull this string here and he fights crime with real action sounds.",
                        Speaker.PLAYER to "Titan?",
                        Speaker.FAMILIAR to "Yes, boss?",
                        Speaker.PLAYER to "Never mind.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Boss!",
                        Speaker.PLAYER to "What?",
                        Speaker.FAMILIAR to "I've just had a vision of the future.",
                        Speaker.PLAYER to "I didn't know you were a fortune teller. Let's hear it then.",
                        Speaker.FAMILIAR to "Just imagine, boss, an Iron Titan (tm) on every desk.",
                        Speaker.PLAYER to "That doesn't even make sense.",
                        Speaker.FAMILIAR to "Hmm. It was a bit blurry, perhaps the future is having technical issues at the moment.",
                        Speaker.PLAYER to "Riiight.",
                    ),
                    spokenConversation(
                        Speaker.FAMILIAR to "Boss?",
                        Speaker.PLAYER to "Yes, titan?",
                        Speaker.FAMILIAR to "You know how you're the boss and I'm the titan?",
                        Speaker.PLAYER to "Yes?",
                        Speaker.FAMILIAR to "Do you think we could swap for a bit?",
                        Speaker.PLAYER to "No, titan!",
                        Speaker.FAMILIAR to "Aww...",
                    ),
                    spokenConversation(
                        Speaker.PLAYER to "How are you today, titan?",
                        Speaker.FAMILIAR to "I'm very happy.",
                        Speaker.PLAYER to "That's marvellous, why are you so happy?",
                        Speaker.FAMILIAR to "Because I love the great taste of Iron Titan cereal (tm)!",
                        Speaker.PLAYER to "?",
                        Speaker.PLAYER to "You're supposed to be working for me, not promoting yourself.",
                        Speaker.FAMILIAR to "Sorry, boss.",
                    ),
                ),
            SummoningPouchData.LAVA_TITAN to
                listOf(
                    spokenConversation(
                        Speaker.PLAYER to "Isn't it a lovely day, Titan?",
                        Speaker.FAMILIAR to "It is quite beautiful. The perfect sort of day for a limerick. Perhaps, I could tell you one?",
                        Speaker.PLAYER to "That sounds splendid.",
                        Speaker.FAMILIAR to "There once was a bard of Edgeville,",
                        Speaker.FAMILIAR to "Whose limericks were quite a thrill,",
                        Speaker.FAMILIAR to "He wrote this one here,",
                        Speaker.FAMILIAR to "His best? Nowhere near,",
                        Speaker.FAMILIAR to "But at least half a page it did fill.",
                    ),
                    spokenConversation(
                        Speaker.PLAYER to "I was just thinking about the River Lum, Titan. Isn't it beautiful?",
                        Speaker.FAMILIAR to "I had a bad experience with the River Lum once. Would you like me to tell you about it?",
                        Speaker.PLAYER to "Well, okay, but only if it's in the form of a limerick.",
                        Speaker.FAMILIAR to "I once saw a river called Lum,",
                        Speaker.FAMILIAR to "So lovely I had to succumb,",
                        Speaker.FAMILIAR to "The results, they were grave,",
                        Speaker.FAMILIAR to "For I boiled all the waves,",
                        Speaker.FAMILIAR to "And ended up just feeling glum.",
                    ),
                    spokenConversation(
                        Speaker.PLAYER to "Titan, I wish I had more money.",
                        Speaker.FAMILIAR to "Money doesn't always bring happiness. I know a limerick about money as it happens. Would you like to hear it?",
                        Speaker.PLAYER to "Sure.",
                        Speaker.FAMILIAR to "There was a king of Ardougne,",
                        Speaker.FAMILIAR to "Who cared too much about coin,",
                        Speaker.FAMILIAR to "He filled up ten pools,",
                        Speaker.FAMILIAR to "Then swam amongst jewels,",
                        Speaker.FAMILIAR to "And wouldn't let anyone join.",
                    ),
                ),
            SummoningPouchData.ABYSSAL_LURKER to
                listOf(
                    listOf(
                        npc("Craaw...", "(Djrej gf'jg sgshe...)"),
                        you("What? Are we in danger, or something?"),
                    ),
                    listOf(
                        npc("Craaw, screeeee!", "(To poshi v'kaa!)"),
                        you("What? Is that even a language?"),
                    ),
                    listOf(
                        npc("Craweeeeee?", "(G-harrve shelmie?)"),
                        you("What? Do you want something?"),
                    ),
                    listOf(
                        npc("Craaw craw!", "(Jehjfk i'ekfh skjd.)"),
                        you("What? Is there somebody down an old well, or something?"),
                    ),
                ),
            SummoningPouchData.ABYSSAL_PARASITE to
                listOf(
                    listOf(
                        npc("*Slobbenngs and slurpings.*", "(Ongk n'hd?)"),
                        you("Oh, I'm not feeling so well."),
                        npc("*Teeth-vibrating hisses and liquid slaverings.*", "(Uge f't es?)"),
                        you("Please have mercy!"),
                        npc("*Noises akin to a clogged drain being plunged.*", "(F'tp ohl't?)"),
                        you("I shouldn't have eaten that kebab. Please stop talking!"),
                    ),
                    listOf(
                        npc("*Obscene gurglings*", "(Ace'e e ur'y!)"),
                        you("I think I'm going to be sick... The noises! Oh, the terrifying noises."),
                    ),
                    listOf(
                        npc("*Stomach-turning slurping noises.*", "(Tdsa tukk!)"),
                        you("Oh, the noises again."),
                        npc("*Unpleasant slurpings.*", "(Hem s'htee?)"),
                        you("Please, just stop talking!"),
                    ),
                ),
            SummoningPouchData.STRANGER_PLANT to
                listOf(
                    listOf(
                        npc("SNAP RUSTLE SNAP!", "(I'M STRANGER PLANT!)"),
                        you("I know you are."),
                        npc("SNAP SNAP! SNAP RUSTLE SNAPSNAP!", "(I KNOW! I'M JUST SAYING!)"),
                        you("Do you have to shout like that all of the time?"),
                        npc("SNAP RUSTLE?", "(WHO'S SHOUTING?)"),
                        you("If this is you speaking normally, I'd hate to hear you shouting."),
                        npc("SNAP SNAP!", "(OH, SNAP!)"),
                    ),
                    listOf(
                        npc("SNAAAAP!", "(DIIIIVE!)"),
                        you("What? Help! Why dive?"),
                        npc(
                            "SNAP, SNAP RUSTLE! RUSTLESNAPSNAP SNAPRUSTLE SNAP-SNAPSNAP!",
                            "(OH, DON'T WORRY! I JUST LIKE TO YELL THAT FROM TIME TO TIME!)",
                        ),
                        you("Well, can you give me a little warning next time?"),
                        npc("SNAP, SNAPSNAPSNAP RUSTLE SNAPRUSTLE?", "(WHAT, AND TAKE ALL THE FUN OUT OF LIFE?)"),
                        you("If by 'fun' you mean 'sudden heart attacks', then yes, please take them out of my life!"),
                    ),
                    listOf(
                        npc("SNAPSNAPSNAP RUSTLE!", "(I THINK I'M WILTING!)"),
                        you("Do you need some water?"),
                        npc("SNAPSNAPRUSTLE! RUSTLERUSTLE SNAPSNAPSNAPSNAP!", "(DON'T BE SILLY! I CAN PULL THAT OUT OF THE GROUND!)"),
                        you("Then why are you wilting?"),
                        npc("SNAPRUSTLE! SNAPSNAP SNAPRUSTLE SNAP SNAP RUSTLE!", "(IT'S SIMPLE: THERE'S A DISTINCT LACK OF DRAMA!)"),
                        you("Drama?"),
                        npc("SNAP, RUSTLE!", "(YES, DRAMA!)"),
                        you("Okay..."),
                        you("Let's see if we can find some for you."),
                        npc("SNAP SNAP!", "(LEAD ON!)"),
                    ),
                ),
            SummoningPouchData.MAGPIE to
                listOf(
                    listOf(
                        npc("Quardle oodle...", "(There's nowt gannin on here...)"),
                        you("Err...sure? Maybe?"),
                        you("It seems upset, but what is it saying?"),
                    ),
                    listOf(
                        npc("Quardle ardle wardle doodle!", "(Howway, let's gaan see what's happenin' in toon.)"),
                        you("What? I can't understand what you're saying."),
                    ),
                    listOf(
                        npc("Wardle ardle oodle!", "(Are we gaan oot soon? I'm up fer a good walk, me.)"),
                        you("That...that was just noise. What does that mean?"),
                    ),
                    listOf(
                        npc("Quardle oodle ardle!", "(Ye' been plowdin' i' the clarts aall day.)"),
                        you("What? That made no sense."),
                    ),
                ),
        )

    /**
     * The six minotaurs share one transcript word for word; only the familiar's name differs.
     * Written once and applied to all six rather than copied six times, which is also what keeps
     * them from drifting apart.
     */
    private val MINOTAUR_CONVERSATIONS =
        listOf(
            spokenConversation(
                Speaker.FAMILIAR to "All this walking about is making me angry.",
                Speaker.PLAYER to "You seem to be quite happy about that.",
                Speaker.FAMILIAR to "Yeah! There's nothing like getting a good rage on and then working it out on some no-horns.",
                Speaker.PLAYER to "I can't say I know what you mean.",
                Speaker.FAMILIAR to "Well, I didn't think a no-horns like you would get it!",
            ),
            spokenConversation(
                Speaker.FAMILIAR to "Can you tell me why we're not fighting yet?",
                Speaker.PLAYER to "Buck up; I'll find you something to hit soon.",
                Speaker.FAMILIAR to "You'd better, no-horns, because that round head of yours is looking mighty axeable.",
            ),
            spokenConversation(
                Speaker.FAMILIAR to "Hey, no-horns?",
                Speaker.PLAYER to "Why do you keep calling me no-horns?",
                Speaker.FAMILIAR to "Do I really have to explain that?",
                Speaker.PLAYER to "No, thinking about it, it's pretty self-evident.",
                Speaker.FAMILIAR to "Glad we're on the same page, no-horns.",
                Speaker.PLAYER to "So, what did you want?",
                Speaker.FAMILIAR to "I've forgotten, now. I'm sure it'll come to me later.",
            ),
            spokenConversation(
                Speaker.FAMILIAR to "Hey, no-horns!",
                Speaker.PLAYER to "Yes?",
                Speaker.FAMILIAR to "Oh, I don't have anything to say, I was just yelling at you.",
                Speaker.PLAYER to "Why?",
                Speaker.FAMILIAR to "No reason. I do like to mess with the no-horns, though.",
            ),
        )

    private val minotaurs =
        listOf(
            SummoningPouchData.BRONZE_MINOTAUR,
            SummoningPouchData.IRON_MINOTAUR,
            SummoningPouchData.STEEL_MINOTAUR,
            SummoningPouchData.MITHRIL_MINOTAUR,
            SummoningPouchData.ADAMANT_MINOTAUR,
            SummoningPouchData.RUNE_MINOTAUR,
        ).associateWith { MINOTAUR_CONVERSATIONS }

    private val allConversations = conversations + minotaurs

    fun conversationsFor(pouch: SummoningPouchData): List<List<Line>> = allConversations[pouch].orEmpty()

    /** How much of the 78-familiar roster currently has sourced dialogue - asserted by tests. */
    val sourcedFamiliarCount: Int get() = allConversations.size

    /** Every familiar with a sourced transcript, for the completeness census. */
    val sourcedFamiliars: Set<SummoningPouchData> get() = allConversations.keys
}
