# Summer 2026 Archive Seed

This directory stores reusable app-private JSON state for summer 2026 archived-game examples.

It contains:

- `current_game_state.json`: the preserved Potlatch Revived semifinal, Red Fish Blue Fish vs
  Rippit, ending 14-12.
- `archived_games/`: 31 archived-game files from the seeded Pixel 8 state.
- `components/`: named copies of the most useful individual states from the full snapshot.

The archived games include:

- Potlatch Revived Mixed Legends pool games, semifinal, and final.
- Northeast Masters Super Regionals Grand Masters and Great Grand Masters games, including MAGMA.
- 2026 D-I College Championships open and women's games, with a fuller weekend-style set from
  pool play through finals.
- The saved setup drafts and in-progress/pre-game archive rows that were already on the emulator.

The `components/` directory includes:

- `saved-setup-drafts/`: the four original Potlatch saved setup draft archive files.
- `completed-from-setup-drafts/`: completed archived-game versions made from those four drafts.
- `semifinal-rfbf-vs-rippit/current-game-full-undo-history.json`: the full current-game bucket
  for the Red Fish Blue Fish vs Rippit semifinal, preserving undo history.
- `semifinal-rfbf-vs-rippit/completed-archive.json`: the completed archived-game version of that
  same semifinal.

Use the top-level `current_game_state.json` and `archived_games/` directory to restore the full
archive seed state. Use the named `components/` files when you want to rebuild a narrower
state, such as only the setup drafts, only their completed versions, or either semifinal variant.

To restore this exact archive/current-game state onto a debug build:

```bash
ANDROID_SERIAL=emulator-5558 ./gradlew installDebug
/Users/Mike/Library/Android/sdk/platform-tools/adb -s emulator-5558 shell am force-stop rmjarvis.ultiobserver
tar -C tools/archive-seeds/summer-2026 -cf - current_game_state.json archived_games \
    | /Users/Mike/Library/Android/sdk/platform-tools/adb -s emulator-5558 exec-in run-as rmjarvis.ultiobserver \
        sh -c 'cd files && rm -rf archived_games && mkdir -p archived_games && tar xf -'
/Users/Mike/Library/Android/sdk/platform-tools/adb -s emulator-5558 shell am force-stop rmjarvis.ultiobserver
```

The restore command replaces only `current_game_state.json` and `archived_games/`. It leaves
profile, settings, and other app-private files alone.
