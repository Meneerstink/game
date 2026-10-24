package gg.rsmod.plugins.content.skills.summoning

/**
 * N301 (owner 2026-10-24: "alle Loyalty store-pets zeggen bij Talk-to maar 1 zin, de speler reageert niet en het gesprek stopt";
 * approved design GOEDGEKEURDE-ONTWERPEN punt 7): every pet species the Loyalty store sells has 2-3 short conversations of its own,
 * player and pet taking turns, one picked at random on Talk-to. The pet makes its species sound ([PetVoices.speciesVoice]) only on
 * a line where it makes an animal noise (a line that starts with `*...!*`, such as "*Woof!*" or "*Ook ook!*") - never on the others.
 * Only the Loyalty store pets: the grown-up 667 variants (Monkey, Terrier ...) are not sold and keep their transcript line.
 */
object LoyaltyPetChats {
    /** One line: the pet's ([pet] = true) or the player's. */
    data class Line(val pet: Boolean, val text: String) {
        /** The pet makes its species sound on this line: it starts with an animal noise in asterisks. */
        val sound: Boolean get() = pet && NOISE.containsMatchIn(text)
    }

    /** "*Woof!*", "*Ook ook!*", "*Quack!*" at the start of a pet line. */
    private val NOISE = Regex("^\\*[A-Za-z][A-Za-z ]*!\\*")

    private fun p(text: String) = Line(false, text)

    private fun n(text: String) = Line(true, text)

    /** Conversations per species (the 667 pet name, Pets.Variant.name). */
    val CHATS: Map<String, List<List<Line>>> =
        mapOf(
            "Hatchling dragon" to listOf(
                listOf(p("Who's a fearsome beast, then?"), n("*Rawr!*"), n("I am. Fear me. Also, could you carry me up the stairs?")),
                listOf(p("Why do you keep sniffing my coin pouch?"), n("Dragons like gold. It's in the contract."), p("What contract?"), n("The one I just made up.")),
                listOf(n("*Snort!* One day I'll be big enough to fly you to Karamja."), p("And until then?"), n("Until then, you carry the snacks.")),
            ),
            "Baby penguin" to listOf(
                listOf(n("*Squawk!* Is it warm here? It feels warm. Too warm."), p("We're not going back to the ice just for you."), n("Fine. I shall sulk with a dignified waddle.")),
                listOf(p("Do you know any good fish jokes?"), n("Plenty. But they're all a bit... krill-ing."), p("That was terrible."), n("*Squawk!* I'm here all week.")),
                listOf(n("I am not a bird that can't fly. I am a bird that chooses not to."), p("Very wise.")),
            ),
            "Baby raccoon" to listOf(
                listOf(p("What have you got in your paws?"), n("Nothing."), p("That's my ring."), n("*Chitter!* ...finders keepers?")),
                listOf(n("Can we go through the bins in Varrock tonight?"), p("Absolutely not."), n("*Chitter chitter!* You never let me have any fun.")),
                listOf(p("Why are you wearing a mask?"), n("It's not a mask. It's my face."), p("Oh. Sorry."), n("Apology accepted. Now hand over the cake.")),
            ),
            "Baby gecko" to listOf(
                listOf(n("I can climb walls, you know."), p("Then why are you riding on my boot?"), n("Saving energy. It's a gecko thing.")),
                listOf(p("Did you just lick your own eyeball?"), n("Yes. Want me to do yours?"), p("No thank you.")),
                listOf(n("If I lose my tail, it grows back."), p("Please don't test that."), n("*Hiss!* No promises.")),
            ),
            "Baby squirrel" to listOf(
                listOf(n("*Squeak!* Nuts? Nuts? Have you got nuts?"), p("No."), n("Are you SURE? Check again!")),
                listOf(p("Where did you bury the last acorn?"), n("Somewhere safe."), p("Where?"), n("*Squeak!* ...I've forgotten.")),
                listOf(n("I buried a gold coin outside Lumbridge."), p("MY gold coin?"), n("OUR gold coin. We're partners.")),
            ),
            "Baby chameleon" to listOf(
                listOf(p("Where did you go? I can't see you!"), n("I'm right here. I'm being the floor."), p("Very convincing.")),
                listOf(n("*Hiss!* I'm turning red. That means I'm hungry."), p("You were green a minute ago."), n("That meant I was hungry too.")),
                listOf(p("Can you turn gold?"), n("Only for very good friends."), p("And?"), n("...Maybe tomorrow.")),
            ),
            "Baby monkey" to listOf(
                listOf(p("Hey little guy, what have you got there?"), n("*Ook ook!* It is MY banana. You may look. Not touch."), p("I paid 7,500 Loyalty points for you."), n("And I am worth every single one.")),
                listOf(n("Why does everyone on Ape Atoll think I'm a spy?"), p("Are you a spy?"), n("*Ook!* ...No comment.")),
                listOf(p("Stop throwing things at people."), n("I only throw things at people who deserve it."), p("You threw a pie at the Wise Old Man."), n("*Ook ook!* He knows what he did.")),
            ),
            "Baby giant crab" to listOf(
                listOf(n("*Click click!* I walk sideways because forwards is boring."), p("Makes sense, I suppose.")),
                listOf(p("Do you ever stop pinching things?"), n("Do you ever stop breathing?"), p("...Fair point.")),
                listOf(n("When I grow up I'll be a GIANT crab."), p("You're already called a giant crab."), n("*Click!* Then I'm off to a great start.")),
            ),
            "Baby icefiend" to listOf(
                listOf(n("*Crackle!* Can we go somewhere colder?"), p("We're standing next to a fire."), n("Exactly my point.")),
                listOf(p("Why are your feet frozen to my backpack?"), n("So I don't fall off."), p("It's dripping all over my food."), n("Free ice. You're welcome.")),
                listOf(n("My friends on Ice Mountain will be so jealous."), p("Of what?"), n("Of my very warm, very sweaty friend.")),
            ),
            "Baby platypus" to listOf(
                listOf(p("Are you a duck or a beaver?"), n("*Quack!* I'm a mystery. Nobody understands me.")),
                listOf(n("Did you know I lay eggs?"), p("But you're a mammal."), n("Rules are for other animals.")),
                listOf(p("What's that on your back foot?"), n("A venomous spur. Very dangerous."), p("Should I be worried?"), n("*Quack!* Only if you touch my fish.")),
            ),
            "Hell-kitten" to listOf(
                listOf(n("*Mew!* Mortal. Fetch me milk. Warm. Now."), p("Say please."), n("...Pleeease.")),
                listOf(p("Why are you on fire?"), n("I'm not on fire. I AM fire."), p("You're also sitting on my cape."), n("...Mine now.")),
                listOf(n("*Mew!*"), p("Aww, that was almost cute."), n("Tell anyone and I'll burn down your bank.")),
            ),
            "Puppy" to listOf(
                listOf(p("Who's a good dog?"), n("*Woof! Woof!* Me! It's me! Is it me?"), p("Yes, it's you."), n("Best. Day. EVER!")),
                listOf(n("*Woof!* Can we go for a walk? Please? Walk? WALK?"), p("We ARE walking."), n("Oh. Then this is the best walk ever!")),
                listOf(p("Did you chew my boots?"), n("Define 'chew'."), p("There are teeth marks all over them."), n("*Woof!* Those were there when I found them.")),
            ),
        )

    /** The species key of a Loyalty store pet's 667 name ("Baby dragon" -> the hatchling's chats, every puppy -> "Puppy"). */
    fun speciesOf(name: String): String? =
        when {
            name == "Baby dragon" || name == "Hatchling dragon" -> "Hatchling dragon"
            name.endsWith(" puppy") -> "Puppy"
            name in CHATS -> name
            else -> null
        }
}
