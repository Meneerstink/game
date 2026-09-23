# RSPS server repo - rules in `C:\RSPS\AGENTS.md` (read it first)

Rule 1: do exactly what the owner's latest message asks, completely, nothing unrelated.
Build/test only with the servers stopped: `gradlew :game:plugins:test`, `:game:test`, `:game:installDist` (smallest first).
Caches change only through the transactional tools in `game\src\main\kotlin\gg\rsmod\game\tools\importer`.
Metadata: `data\cfg\*.yml`; generated ids: `game\plugins\src\main\kotlin\gg\rsmod\plugins\api\cfg`.
