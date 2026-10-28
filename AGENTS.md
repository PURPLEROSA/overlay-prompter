# AGENTS.md — guide for AI assistants (Claude, ChatGPT, Cursor, …)

## What this is
Electron teleprompter. A transparent, frameless, always-on-top **overlay** window shows a scrolling script over any app (Zoom, Teams, TikTok Live Studio). A normal **panel** window edits scripts and settings. Primary user reads Hebrew (RTL).

## Layout
```
src/main.js          Main process: creates windows, global hotkeys, IPC handlers, persistence. SOURCE OF TRUTH.
src/store.js         JSON file store in app.getPath('userData')/prompter-data.json (settings + scripts).
src/preload.js       contextBridge API `window.prompter` (the only renderer<->main surface).
src/overlay/         Overlay renderer. overlay.js = rAF scroll loop (offset px, speed px/s), commands, resize grip.
src/panel/           Control panel renderer: script list/editor, sliders, checkboxes.
assets/fonts/        Bundled Heebo (OFL) Hebrew font.
samples/             Sample Hebrew script seeded on first run.
screens/             Screenshots (generated with `npm run screenshot`).
```

## Data flow
panel UI / global hotkey → IPC (`update-settings`, `command`, `save-script`…) → main mutates `store.data`, saves, then `pushState()` broadcasts `state` to both windows. Transient playback commands (`togglePlay`, `jumpBack`, `restart`…) are forwarded as `command` events; **scroll position and play state live only in the overlay renderer** (not persisted).

## Key window flags (main.js `createOverlay`)
- `transparent`, `frame:false`, `setAlwaysOnTop(true,'screen-saver')`, `setVisibleOnAllWorkspaces(true,{visibleOnFullScreen:true})`
- `setContentProtection(true)` — hide from screen capture (Win10 2004+ / macOS; not guaranteed for all capture methods)
- `setIgnoreMouseEvents(clickThrough,{forward:true})` — click-through toggle
- Dragging via CSS `-webkit-app-region: drag` on `#dragbar`; resizing via `#grip` → `resize-overlay-by` IPC (transparent windows on Windows can't edge-resize).

## Conventions
- No Node in renderers (contextIsolation on). Add new capabilities to preload.js explicitly.
- Keep it dependency-free and bundler-free. Comment non-obvious platform behavior.
- Hotkeys use `CommandOrControl+Shift+…`; registration failures are logged, not fatal.

## Roadmap hooks
- **Voice-follow (NOT implemented)**: the panel checkbox is disabled on purpose. Plan: capture mic in the overlay renderer, run streaming Hebrew speech-to-text (e.g. local whisper.cpp / Web Speech where available / cloud STT), fuzzy-align recognized words to script tokens, and set `offset` so the matched word sits on the reading line. Don't fake it with timers.
- Countdown before play, per-script speed, markdown rendering (bold/cue colors), multi-monitor picker, remote control from phone (local websocket).

## Android app (`android/`)
- Plain Android framework views + Kotlin, no AndroidX deps. Gradle 8.5 wrapper, AGP 8.2.2, Kotlin 1.9.22, JDK 17. Build: `cd android && ./gradlew assembleDebug` (needs `local.properties` with `sdk.dir`, gitignored).
- `MainActivity` — editor, saved scripts list, paste, `.txt` import (ACTION_OPEN_DOCUMENT), receives `ACTION_SEND text/plain`. Starts `OverlayService`.
- `PermissionActivity` — Hebrew onboarding → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- `OverlayService` — foreground service (`specialUse` type on 34) with ongoing notification (toggle/hide/stop actions). Owns two `TYPE_APPLICATION_OVERLAY` windows: the panel (toolbar, ScrollView text, reading-line band, resize handle) and a bubble (collapsed state or unlock when locked). Lock = `FLAG_NOT_TOUCHABLE` on the panel. Scroll driven by `Choreographer` at `speed` px/s. `FLAG_SECURE` on both windows (`Store.secure`).
- `Store` — SharedPreferences: scripts JSON + settings (font, speed, opacity, mirror, highlight).
- Font: `res/font/heebo.ttf` copied from `assets/fonts/Heebo.ttf`. APK copied to `dist/` (gitignored).
