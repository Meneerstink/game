# A6 source/runtime audit

## Number challenge: Father Aereck

The selected pinned `Clue_scroll_(medium)_-_AREA_CHEF_TREK.wiki` does not allow both answers unconditionally. Its footnote says **20 only if the player failed to save Jarvis in the 2017 Halloween event**; otherwise 19. The current generated `answers` set contains `{19,20}` and must not be treated as an unconditional accepted set. There is no Jarvis/2017 Halloween implementation or saved event-state provider in this server (targeted source/cfg search, 15 November). Therefore the supported ordinary state accepts **19**, with the absent historical state recorded explicitly. Do not invent an event completion flag or silently accept 20.

Question, answer and footnote provenance: `sources/Clue_scroll_(medium)_-_AREA_CHEF_TREK.wiki`, pinned by `sources/step-manifest.json`; the generator validates the source SHA. This is a source correction to apply after the frozen integration, not an owner question.

## STASH Settings source

`tools/clue/ClueSettingProbe.java` decodes source-only struct 6245 in cache2686, archive2/group34, with exact framing and SHA. Output `cache/stash-setting.json`: `c160459064efde53b1fb24881c3a17c85d8e12d8999e5ed327b63a177f6b1bae`.

Source fields retain the actual label **STASH units take equipped items** and help text describing inventory priority. Parameter1077=451 is retained as an uninterpreted source field; it is not proof of local varp451. Do not copy source selectors into the target namespace or claim a Settings handler from a source string. Runtime storage already reads the persistent TAKE_EQUIPPED flag; the Settings entry/visual route remains required.

Reproduce:

```powershell
& 'C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe' -Xmx256m -cp 'game/build/install/game/lib/*' tools/clue/ClueSettingProbe.java C:/RSPS/import-source/openrs2-2686/cache docs/CLUE/cache/stash-setting.json 6245
```

No target cache was opened, no apply or CacheTransaction was used. The actual probe completed successfully and refuses unknown opcodes/trailing bytes.
