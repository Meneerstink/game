# RSPS server repo - rules in `C:\RSPS\AGENTS.md` (read it first)

Rule 1: do exactly what the owner's latest message asks, completely, nothing unrelated.
Build/test only with the servers stopped. Run only the test classes you touched or that guard your change:
`gradlew :game:plugins:test --tests '*Name*'` / `:game:test --tests '*Name*'`, then `:game:installDist`. The full
`:game:plugins:test` (1765 tests, ~11 min) only when the owner asks for it or once at the end of a large multi-day job -
never for small fixes (owner 2026-09-27).
Caches change only through the transactional tools in `game\src\main\kotlin\gg\rsmod\game\tools\importer`.
Metadata: `data\cfg\*.yml`; generated ids: `game\plugins\src\main\kotlin\gg\rsmod\plugins\api\cfg`.
