package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.content.skills.summoning.LoyaltyPetChats.Line

/**
 * Owner 2026-10-29 ("Ja, eigen gesprekjes"): the 667 pets outside [LoyaltyPetChats] - the grown-up cats and dogs, the adults of the
 * Loyalty babies, the god birds and the Summoning-era pets - each get 2-3 short conversations of their own instead of one transcript
 * line, the same way: player and pet in turn, one picked at random on Talk-to, the species sound only on a line that starts with an
 * animal noise in asterisks ([Line.sound]; species without a voice in [PetVoices.CLASSIC] get no such lines).
 */
object ClassicPetChats {
    private fun p(text: String) = Line(false, text)

    private fun n(text: String) = Line(true, text)

    /** Conversations per species key ([speciesOf]). */
    val CHATS: Map<String, List<List<Line>>> =
        mapOf(
            "Cat" to listOf(
                listOf(p("Caught any rats today?"), n("Three. I left them in your boots."), p("Why my boots?"), n("*Purr!* Because I love you.")),
                listOf(n("Your lap. Now."), p("I'm in the middle of something."), n("Wrong. You are in the middle of being my chair.")),
                listOf(p("Why do you knock things off the table?"), n("To see if they still fall."), p("And?"), n("Still falling. I'll check again later.")),
            ),
            "Hellcat" to listOf(
                listOf(n("*Hiss!* Something in this town smells of holy water."), p("That's the Varrock church."), n("Burn it. Kidding. Mostly.")),
                listOf(p("Would you like a saucer of milk?"), n("Only if it's boiling."), p("Milk doesn't really boil, it-"), n("Mine does.")),
                listOf(n("In my old home, the rats were the size of trolls."), p("Did you catch them?"), n("Every single one. Then I ate the trolls.")),
            ),
            "Clockwork cat" to listOf(
                listOf(n("*Purr!* Tick... tick... purr."), p("Are you running down?"), n("Wind my key and I'll chase a rat. A small one.")),
                listOf(p("Do you ever wish you were a real cat?"), n("Real cats need feeding. I need oiling. Who is the winner?")),
                listOf(n("Warning. Spring tension low."), p("What happens then?"), n("I sit very still and look very smug. As cats do.")),
            ),
            "Bulldog" to listOf(
                listOf(n("*Woof!* I am guarding you."), p("From what?"), n("Everything. Mostly pigeons.")),
                listOf(p("Why do you snore so loudly?"), n("To scare off burglars. While I sleep. Very clever."), p("It scares me off.")),
                listOf(n("I may be short, but I am very wide."), p("Is that a good thing?"), n("Try to get past me and find out.")),
            ),
            "Dalmatian" to listOf(
                listOf(p("How many spots have you got?"), n("I lost count at Falador."), p("Does it matter?"), n("*Woof!* Only to the spots.")),
                listOf(n("I can smell smoke!"), p("That's a cooking range."), n("Oh. Then I can smell lunch! Even better!")),
                listOf(p("Want to play hide and seek?"), n("I always lose. Everyone says they can 'spot' me.")),
            ),
            "Greyhound" to listOf(
                listOf(n("*Woof!* Race you to the bank!"), p("You'll win."), n("I know. I just like hearing you say it.")),
                listOf(p("Can you slow down a little?"), n("This IS slow. This is my sleepy walk.")),
                listOf(n("I can run faster than your run energy."), p("Everyone can run faster than my run energy."), n("Then I'll wait. Briefly.")),
            ),
            "Labrador" to listOf(
                listOf(n("*Woof!* Is that food? Can I have it? Is THAT food?"), p("That's a rock."), n("Can I have it anyway?")),
                listOf(p("You're soaking wet again."), n("There was a river. Rivers are for jumping in. Everyone knows that.")),
                listOf(n("I love you. I love the bank. I love that man."), p("That's a guard."), n("I love guards too!")),
            ),
            "Sheepdog" to listOf(
                listOf(n("*Woof!* All present and correct."), p("Who is?"), n("You. I'm herding you. You keep wandering off.")),
                listOf(p("Miss the sheep in Lumbridge?"), n("Every day. They never argued back.")),
                listOf(n("Three steps left, please."), p("Why?"), n("Because you're out of line. Literally.")),
            ),
            "Terrier" to listOf(
                listOf(n("*Woof!* I dug a hole."), p("Where?"), n("Right where you're standing."), p("...I see it now.")),
                listOf(p("You barked at a dragon earlier."), n("It started it."), p("It was asleep."), n("It was asleep RUDELY.")),
                listOf(n("I'm small, but my bark is enormous."), p("And your bite?"), n("My bite is in training.")),
            ),
            "Chameleon" to listOf(
                listOf(p("Where have you gone now?"), n("Look at the wall. Closer. Closer... boo."), p("Very funny.")),
                listOf(n("*Hiss!* I've turned blue."), p("Is that bad?"), n("It means I'm sulking. You forgot my flies.")),
                listOf(p("Can you go invisible in the Wilderness?"), n("Yes. And you can't. So I'll wait for you at Edgeville.")),
            ),
            "Gecko" to listOf(
                listOf(n("I licked a window in Falador."), p("Why?"), n("It was there. Like mountains, but cleaner.")),
                listOf(p("Can you really walk on the ceiling?"), n("Of course. Want to watch?"), p("Not in the bank, please.")),
                listOf(n("*Hiss!* Something buzzed past us."), p("A fly?"), n("Was a fly. Now it's breakfast.")),
            ),
            "Giant crab" to listOf(
                listOf(n("*Clack clack!* I'm getting bigger."), p("You'll need a bigger shell."), n("I'm thinking of a barrel. Maybe a boat.")),
                listOf(p("Why do you walk sideways?"), n("Why do you walk forwards? Very strange creature.")),
                listOf(n("A crab never forgets."), p("Forgets what?"), n("I... forgot. *Clack!*")),
            ),
            "Monkey" to listOf(
                listOf(n("*Ook ook!* Ape Atoll misses me."), p("Do you miss it?"), n("Only the bananas. And the singing. Mostly the bananas.")),
                listOf(p("Stop pulling my hair."), n("I'm grooming you. You have bugs."), p("I do not have bugs."), n("Not any more.")),
                listOf(n("I could steal that guard's helmet."), p("Please don't."), n("*Ook!* Too late. Do you want it?")),
            ),
            "Penguin" to listOf(
                listOf(n("*Squawk!* Agent reporting."), p("Agent?"), n("I mean... penguin. Ordinary penguin. Nothing to see here.")),
                listOf(p("Do you work for someone?"), n("I am a simple bird who likes fish."), n("That is my cover. My cover is perfect.")),
                listOf(n("The cows of Lumbridge are watching us."), p("They're just cows."), n("That's exactly what they want you to think.")),
            ),
            "Platypus" to listOf(
                listOf(n("*Quack!* I am a mammal."), p("You've got a bill, like a duck."), n("And I lay eggs. Science is still recovering.")),
                listOf(p("Is it true you have venom?"), n("Only on my back feet. Shake hands, not feet.")),
                listOf(n("I found a shiny thing in the river."), p("Is it treasure?"), n("It's a spoon. But it's MY spoon.")),
            ),
            "Raccoon" to listOf(
                listOf(n("*Chitter!* I found a pie."), p("Where?"), n("Cooling on a windowsill in Varrock. It's ours now.")),
                listOf(p("Wash your paws before you eat."), n("I always wash my food. Even the gold coins.")),
                listOf(n("The masked bandit strikes again."), p("You stole my cabbage."), n("A cabbage is a cabbage. It wanted to be free.")),
            ),
            "Squirrel" to listOf(
                listOf(n("*Squeak!* Winter is coming!"), p("It's summer."), n("Winter is ALWAYS coming. Have you got acorns?")),
                listOf(p("You've hidden something in my bank."), n("Nuts. Seventeen of them. For emergencies.")),
                listOf(n("I can jump between trees."), p("So can I, with the Agility course."), n("Show off.")),
            ),
            "Raven" to listOf(
                listOf(n("*Caw!* Something shiny over there."), p("It's a gravestone."), n("It's a SHINY gravestone.")),
                listOf(p("Can you talk like a person?"), n("I could. I simply choose not to with most people. Caw.")),
                listOf(n("Nevermore."), p("Never more what?"), n("Never more of those terrible fish you keep giving me.")),
            ),
            "Vulture" to listOf(
                listOf(n("How are you feeling? Any aches?"), p("I'm fine."), n("Oh. Pity. I mean, good. Good for you.")),
                listOf(p("Why do you follow me so closely in the Wilderness?"), n("Pure friendship. And patience.")),
                listOf(n("*Screech!* The desert was lovely this time of year."), p("Hot?"), n("Full of lost adventurers. Lovely.")),
            ),
            "Saradomin bird" to listOf(
                listOf(n("*Tweet!* Saradomin watches over you."), p("Does he?"), n("Well, I do. He's very busy.")),
                listOf(p("Can you bless my sword?"), n("I can sit on it. That's a start.")),
                listOf(n("Wisdom is knowing when to fight."), p("And when not to?"), n("That's more wisdom. You can't have it all at once.")),
            ),
            "Zamorak bird" to listOf(
                listOf(n("*Screech!* Chaos is beautiful."), p("You knocked over my potions."), n("Exactly. Beautiful.")),
                listOf(p("Do you ever do anything nice?"), n("Once. I didn't enjoy it.")),
                listOf(n("Let's go into the Wilderness."), p("Why?"), n("No reason. Strictly peaceful. *Screech!*")),
            ),
            "Guthix bird" to listOf(
                listOf(n("*Tweet!* Balance in all things."), p("Even in my inventory?"), n("Especially there. It's a mess.")),
                listOf(p("Whose side are you on?"), n("Nobody's. Everybody's. It's very tiring.")),
                listOf(n("For every fish you eat, a fish must swim free."), p("I've eaten a lot of sharks."), n("We'll talk about that later.")),
            ),
            "Baby kurask" to listOf(
                listOf(n("Do you have a leaf-bladed sword?"), p("Why?"), n("Just checking. No reason. Keep it in the bank.")),
                listOf(p("Your spikes are sharp."), n("They're for hugging. Carefully.")),
                listOf(n("When I grow up I want to be a Slayer task."), p("That's a strange dream."), n("It's the only way to meet people.")),
            ),
            "Baby basilisk" to listOf(
                listOf(n("Look into my eyes."), p("I'd rather not."), n("Wise. Very wise. Have you got a mirror shield?")),
                listOf(p("Why do you keep staring at me?"), n("It's how I say hello. Most people go stiff with joy.")),
                listOf(n("I'm practising my gaze."), p("On what?"), n("That chicken. It's been standing very still for an hour.")),
            ),
            "Minitrice" to listOf(
                listOf(n("I'm a little bit chicken, a little bit lizard."), p("Which bit lays eggs?"), n("Nobody knows. Not even me.")),
                listOf(p("Can you stop glaring at the guards?"), n("I'm not glaring. My face just does that.")),
                listOf(n("Cockatrices in the Fremennik caves say I'm cute."), p("Is that a compliment?"), n("From them, it's a threat.")),
            ),
            "Abyssal minion" to listOf(
                listOf(n("The Abyss was warm and full of screaming."), p("And here?"), n("Too quiet. Can we visit a bank queue?")),
                listOf(p("What do you eat?"), n("Rune essence. Cooked, not raw. I have standards.")),
                listOf(n("I can hold your runes for you."), p("You'll eat them."), n("Only the ones I like. Mostly the nature runes.")),
            ),
            "Rune guardian" to listOf(
                listOf(n("This way to the altar."), p("We're not going to an altar."), n("Everyone is always going to an altar. Eventually.")),
                listOf(p("What are you guarding now?"), n("You. Like an altar, but with legs.")),
                listOf(n("I can hear the essence humming."), p("I can't hear anything."), n("You lack the ears. And the runecrafting level.")),
            ),
            "Broav" to listOf(
                listOf(n("I can smell where you've been."), p("Where have I been?"), n("Somewhere with kebabs. You didn't share.")),
                listOf(p("Can you track anything?"), n("Anything. Bring me a clue scroll and stand back.")),
                listOf(n("Somebody's been here. Wearing boots. Like yours."), p("Those are my footprints."), n("Case closed. You did it.")),
            ),
            "Creeping hand" to listOf(
                listOf(p("Shake hands?"), n("I AM a hand. This is a very personal question.")),
                listOf(n("Give me a high five."), p("You only have five."), n("That's what makes it high.")),
                listOf(p("Where's the rest of you?"), n("Off being a zombie somewhere. I got the good part.")),
            ),
            "Ex-ex-parrot" to listOf(
                listOf(n("Pieces of eight! Pieces of... where did my feathers go?"), p("You're a bit... dead."), n("I'm resting.")),
                listOf(p("Is this parrot no more?"), n("This parrot is extremely more. This parrot is BACK.")),
                listOf(n("Polly wants a brain."), p("Don't you mean a cracker?"), n("No. I said what I said.")),
            ),
            "Tooth creature" to listOf(
                listOf(n("Have you brushed your teeth today?"), p("Yes."), n("I can tell when you lie. I'm made of teeth.")),
                listOf(p("What do you eat?"), n("Nothing sugary. Nothing hard. Mostly soup.")),
                listOf(n("When I grow up I'll be a whole set of teeth."), p("Ambitious."), n("Then dentures. That's the dream.")),
            ),
            "Phoenix eggling" to listOf(
                listOf(n("I'm a bit warm. Is that normal?"), p("You're on fire."), n("Oh good, then it's normal.")),
                listOf(p("Will you be a full phoenix one day?"), n("Yes. Then I'll burst into flames and start over. Again.")),
                listOf(n("Hold me near the logs."), p("Why?"), n("I want to help with your Firemaking.")),
            ),
            "Sneakerpeeper" to listOf(
                listOf(n("Shh. I'm sneaking."), p("I can see you."), n("You are very good at peeping. I'm impressed.")),
                listOf(p("What are you looking at?"), n("Everything. Quietly. From behind your leg.")),
                listOf(n("I peeped into the bank."), p("And?"), n("You've got a LOT of bones in there.")),
            ),
            "Tzrek-jad" to listOf(
                listOf(n("Kl-Tzhaar ka JalYt."), p("What does that mean?"), n("It means you are small and squishy. With respect.")),
                listOf(p("Do you remember the Fight Kiln?"), n("I remember the heat. I miss the heat. You are very cold.")),
                listOf(n("One day I will be as big as TzTok-Jad."), p("Please don't step on me then.")),
            ),
        )

    /** The species key of a 667 pet name without [LoyaltyPetChats] conversations, or null. */
    fun speciesOf(name: String): String? =
        when (name) {
            "Pet kitten", "Pet cat", "Overgrown cat" -> "Cat"
            "Hellcat", "Overgrown hellcat" -> "Hellcat"
            "Raven chick", "Raven" -> "Raven"
            "Vulture chick", "Vulture" -> "Vulture"
            "Saradomin chick", "Saradomin bird", "Saradomin owl" -> "Saradomin bird"
            "Zamorak chick", "Zamorak bird", "Zamorak hawk" -> "Zamorak bird"
            "Guthix chick", "Guthix bird", "Guthix raptor" -> "Guthix bird"
            "Milk tooth creature", "Tooth creature (healthy)" -> "Tooth creature"
            "Sneakerpeeper spawn", "Sneakerpeeper" -> "Sneakerpeeper"
            else -> name.takeIf { it in CHATS }
        }
}
