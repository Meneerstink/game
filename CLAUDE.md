# RSPS server repo - read `C:\RSPS\AGENTS.md` first

This is the server repository of the RSPS project. All rules live in `C:\RSPS\AGENTS.md` (one rulebook for every agent);
the start order is `C:\RSPS\RSPS_CURRENT_SPRINT.json` -> `C:\RSPS\HANDOFF_CURRENT.md` -> the work list it names.

Repo specifics:
- Build/test only while the game server and file-server are stopped: `gradlew :game:plugins:test`, `:game:test`,
  `:game:installDist`; run the smallest task that proves the change first.
- Caches here (`data\cache`) and in `C:\RSPS\file-server\cache` change only together, through the transactional tools
  in `game\src\main\kotlin\gg\rsmod\game\tools\importer`.
- Item/NPC/object metadata: `data\cfg\*.yml`; generated ids: `game\plugins\src\main\kotlin\gg\rsmod\plugins\api\cfg`.
- Local Git only; commit your own hunks, never reset/clean/stash/push.
