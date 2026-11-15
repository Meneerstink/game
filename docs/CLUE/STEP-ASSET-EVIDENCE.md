# Selected-step cache evidence

Read directly from OSRS cache 2686 with `tools/clue/ClueStepAssetProbe.java`.
The JSON contains 20 NPC definitions with raw SHA-256 hashes and 21 scene probes.
The 12-tile scene search is research only, never a gameplay completion radius.

Reproduce after the core classes have compiled:

```powershell
$clueClasspath = "game/build/classes/kotlin/main;game/build/install/game/lib/*"
& "C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe" -Xmx512m -cp $clueClasspath tools/clue/ClueStepAssetProbe.java C:/RSPS/import-source/openrs2-2686/cache data/cfg/clue/steps.json docs/CLUE/cache/step-assets.json
```

## Required NPC assets

Existing 667 identities: Uri 5141-5143; hard Double agents 5144/5145; Saradomin wizard 1264; Zamorak wizard 1007. Existing models and source combat behaviour must still be verified together. No duplicate allocations were performed.

Not present by current source name: source 306 Lumbridge Guide, 5081 Wizard Traiborn, 6586 Sherlock, 7306 Falo. Target 881 is Traiborn, the historically renamed version (Wizard_Traiborn.wiki records the rename); using it needs an explicit identity mapping, not a name-only fallback. Source 7312 is the master Double agent. Source 6587/6588 are the elite coordinate guards; 7310 is the Brassican Mage used by both selected master coordinates. Ancient Wizards 7307/7308/7309 are only needed for multicombat hot/cold targets, not those two coordinate steps.

## STASH placements

| Selected step | Source base loc | Physical tile | Effective plane | Source forms |
|---|---:|---|---:|---|
| Dance at the crossroads north of Draynor | 28968 | 3111,3289,0 | 0 | 6657, 15943 |
| Headbang in the mine north of Al Kharid | 28965 | 3303,3289,0 | 0 | 16014, 16015 |
| Cheer at the Druids' Circle | 28973 | 2924,3477,0 | 0 | 16014, 16015 |
| Clap on the causeway to the Wizards' Tower | 28959 | 3115,3194,1 | 0 | 16016, 16017 |
| Jump for joy at the beehives | 28974 | 2764,3438,0 | 0 | 6657, 15943 |
| Cheer in the Edgeville general store | 29005 | 3077,3503,0 | 0 | 16018, 16019 |
| Shrug in Catherby bank | 29001 | 2807,3437,0 | 0 | 16018, 16019 |
| Yawn in the Castle Wars lobby | 28991 | 2444,3093,0 | 0 | 16024, 28933 |
| Spin on the bridge by the Barbarian Village | 28989 | 3110,3422,0 | 0 | 16018, 16019 |
| Salute in the banana plantation | 29014 | 2909,3169,0 | 0 | 28940, 28941 |
| Blow a raspberry in the Fishing Guild bank | 29010 | 2593,3409,0 | 0 | 28940, 28941 |
| Yawn in the rogues' general store | 29013 | 3027,3699,0 | 0 | 28936, 28937 |
| Bow upstairs in the Edgeville Monastery | 29029 | 3056,3482,0 | 0 | 28948, 28949 |
| Salute in the Warriors' guild bank | 29028 | 2844,3537,0 | 0 | 28944, 28945 |
| Laugh in front of the gem store in Ardougne market | 29038 | 2672,3302,0 | 0 | 28944, 28945 |
| Show your anger at the Wise old man | 29059 | 3095,3254,0 | 0 | 28950, 28951 |
| Clap in the magic axe hut | 29056 | 3186,3958,0 | 0 | 28952, 28953 |


No STASH was imported, placed, built or activated. The exporter follows the source bridge flag to distinguish physical and effective planes. The base multilocs use OSRS varbits, which must be remapped safely before target-cache use.

## Supported-area gaps

* Lumbridge Swamp bookcase at 3146,3177,0 is present in OSRS (loc 9523). The target tile has the 667 ruined hut/decorations and no bookcase. This is not an available step just because its map square exists. Replace it with another sourced easy search step in a verified local building, or import the complete compatible hut footprint after review. Advice: replace the selected step; do not spawn a floating bookcase.
* Port Sarim jail bucket 9568 is present at 3013,3179,0 but the target definition has no Search option. The source has Search. A guarded options-only cache fix is required before its search handler can work.
* The Duke bedroom has source closed chest 375 and target 37009 at the clue point. The local open/search variants must be traced; the nearby quest chest 6910 is a different placement. Never complete this step by searching any chest in Lumbridge.

## Per-step combat exceptions

The coordinate generator now reads each coordinate table instead of inferring the enemy from the tier. The hard Emir's Arena point 02.39N 29.58E explicitly says `fight = No`. Both selected master points (Crandor 03.26N 12.18E and Witchaven 03.50N 09.07E) explicitly say Brassican Mage 7310. Crandor's image filename has a 12.16E typo; the generator matches the written coordinates 12.18E. Both elite points list Armadylean or Bandosian, without a pinned numeric selection probability; do not invent one.

Evidence JSON SHA-256: `4d982f42c3cd29268462dd8895ab13561a02d1fc53e6eb339d6890b5223c81e2`.

## Mimic minions and combat XP

The read-only source export now includes all three existing imported minions: OSRS 8635/8636/8637, verified local 14403/14404/14405 by name and combat level. `guardians.json` preserves their complete pinned Wiki stats and the combat-XP bonus for all fourteen variants. Negative bonuses are intentional: warrior magic defence -4; mage ranged defence -5.

Mimic has a source XP bonus of 25%; Third Age Warrior 7.5%, Ranger/Mage 0%. The runtime boss definition still needs its 1.25 multiplier and a targeted test. Minion combat routing, attack visuals, candy and forward movement are not activated by this data export.

The Double agent source also explicitly exempts the first step of a trail from spawning an agent. Hard/master emote handlers must preserve that exception, require the agent defeat on later steps, and recheck clue gear when talking to Uri.
