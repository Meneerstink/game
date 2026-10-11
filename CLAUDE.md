# RSPS server repo - rules in `C:\RSPS\AGENTS.md` (read it first)

> **BUG ORDER (owner 2026-10-04): work strictly top to bottom in `C:\RSPS\BUGS_PRIORITY.md` (hardest first, all owner bugs). That file wins over every priority below. Only the owner's verdict changes a status; an agent writes at most READY FOR OWNER TEST.**
>
> **OWNER PRIORITY #1 (2026-10-03): agility obstacles and noclip issues, solved structurally.**
> Owner: "agility obstakels en noclip issues structureerlijk oplossen." Every agility obstacle / shortcut / rock / boulder /
> forced movement must never leave a player inside scenery, stuck, or out of sync with the client - fixed once for the whole
> class, not per obstacle. Reported 5-6 times, every earlier "fix" failed (09-30, 10-01, 10-02 #147; 10-02 23:58 stuck at
> 2878,3678 Trollheim; 10-03 again "noclipping in muren", reported during the Barrows session). Rules + history: `agent-handoffs/2026-10-03-owner-buglijst.txt` (reproduce in the own client first,
> status stays WAITING FOR OWNER until the owner has seen it). An explicit new owner message still wins.
> **OWNER MAJOR BUG (2026-10-03 03:xx, all caps): THE DEADMAN MODE HUD MUST ALWAYS BE ON SCREEN - IT MAY NEVER DISAPPEAR.**
> Owner: "DE DEADMANMODE HUD VERDWIJNT NU DE DEADMANMODE HUD MOET ALTIJD IN BEELD ZIJN ... DE DMM HUD MAG NOOOIT VERDWIJNEN".
> Not only in dangerous zones: always, everywhere, after every walk/teleport/login/death/instance/interface change - buglist E16 (+E9).
> **OWNER PRIORITY - HIGH (2026-10-03, repeat ~10x): the Deadman HUD disappears again in a dangerous zone.**
> Owner: "me deadmanhud is weer verdwenen in een dangerous zone dit is ook iets wat al 10 keer geprompt is ... ook hoge prio."
> Find why every earlier fix failed before touching code; reproduce it in the own client; whole class (every way of
> entering/leaving a dangerous zone: walk, teleport, login, death, instance) - buglist E9.
> **OWNER PRIORITY - HIGH (2026-10-03, repeat many times): Barrows is UNPLAYABLE.** Owner: "barrows dus onspeelbaar ook fucking vaak gemeld", "barrows functioneert nog steeds niet".
> Owner: in Ahrim's crypt, click the sarcophagus -> "enter tunnel"; climb back up, enter Torag's crypt, search -> "You don't
> find anything" while no brother has been killed yet. More precisely (owner): the other sarcophagi say "You don't find
> anything" as soon as one brother has to be killed in the hidden tunnel - the hidden-tunnel brother blocks the other five
> from spawning. Barrows must work exactly like OSRS (brother spawns, hidden tunnel, kill count, chest) - buglist E8.
> Also Barrows (owner 2026-10-03): every tunnel door opens by default (OSRS: a fixed set of doors is locked each run, only
> one route leads to the chest) - E10. Fully zoomed out with the camera you see black (reported during Barrows; check
> whether it also happens elsewhere) - E11.
> **OWNER WISH (2026-10-03, after priority #1): Grand Exchange exactly like the OSRS Grand Exchange.**
> Owner: "onze huidige grand exchange heeft niet alle knoppen en werkt niet precies als de osrs grand exchange." Every
> button and behaviour of the OSRS GE must exist and work the same (buglist E6, `agent-handoffs/2026-10-03-owner-buglijst.txt`).
> **OWNER WISH (2026-10-03, after priority #1): the bank must work exactly like the OSRS bank.**
> Owner: "onze bank! moet precies functioneren als osrs bank" - every button, setting and behaviour of the OSRS bank (buglist E7).

Rule 1: do exactly what the owner's latest message asks, completely, nothing unrelated.
Build/test any time: the live server runs from `game\build\live-lib` (owner 2026-10-02); never stop or restart it while
someone is online (`tools\live\Test-NobodyOnline.ps1`, AGENTS.md 5). Run only the test classes you touched or that guard your change:
`gradlew :game:plugins:test --tests '*Name*'` / `:game:test --tests '*Name*'`, then `:game:installDist`. The full
`:game:plugins:test` (1765 tests, ~11 min) only when the owner asks for it or once at the end of a large multi-day job -
never for small fixes (owner 2026-09-27).
Caches change only through the transactional tools in `game\src\main\kotlin\gg\rsmod\game\tools\importer`.
Metadata: `data\cfg\*.yml`; generated ids: `game\plugins\src\main\kotlin\gg\rsmod\plugins\api\cfg`.
