# Contributing

- `npm install` then `npm start`. Keep zero runtime dependencies if possible (only Electron).
- Plain JS, no bundler. Each file starts with a comment explaining its role — keep that up.
- Main process (`src/main.js`) is the single source of truth for settings/scripts; renderers only send intents over IPC (see `src/preload.js`).
- New setting? Add a default in `src/store.js`, a control in `src/panel/`, and apply it in `src/overlay/overlay.js`.
- New hotkey? Add it to `HOTKEYS` in `src/main.js` and to the README tables + panel hotkey list.
- Test on both Windows and macOS before merging anything touching windows/hotkeys/content protection.
- Commit messages: short imperative ("Add countdown before play").
