// Main process: owns windows, global hotkeys, persistence.
// Two windows:
//   overlay -> transparent, frameless, always-on-top teleprompter (src/overlay)
//   panel   -> normal window: script editor + controls (src/panel)
// All state changes flow: panel/hotkey -> main (source of truth) -> overlay + panel.
const { app, BrowserWindow, globalShortcut, ipcMain, screen, dialog } = require('electron');
const fs = require('fs');
const path = require('path');
const { Store } = require('./store');

let store, overlay, panel;
const screenshotArg = process.argv.find(a => a.startsWith('--screenshot='));

// ---------- state helpers ----------
const settings = () => store.data.settings;
function currentScript() {
  return store.data.scripts.find(s => s.id === store.data.currentScriptId) || null;
}
function broadcast(channel, payload) {
  for (const w of [overlay, panel]) if (w && !w.isDestroyed()) w.webContents.send(channel, payload);
}
function pushState() {
  broadcast('state', { settings: settings(), script: currentScript(),
    scripts: store.data.scripts.map(({ id, title, updatedAt }) => ({ id, title, updatedAt })),
    currentScriptId: store.data.currentScriptId, overlayVisible: overlay && overlay.isVisible() });
}
function updateSettings(patch) {
  Object.assign(settings(), patch);
  if ('clickThrough' in patch) applyClickThrough();
  store.save();
  pushState();
}
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

// ---------- overlay window ----------
function defaultBounds() {
  // Top-center of the primary display = closest to a typical laptop/monitor webcam.
  const { workArea } = screen.getPrimaryDisplay();
  const width = Math.round(Math.min(900, workArea.width * 0.6));
  const height = 260;
  return { x: Math.round(workArea.x + (workArea.width - width) / 2), y: workArea.y + 10, width, height };
}

function createOverlay() {
  overlay = new BrowserWindow({
    ...(settings().bounds || defaultBounds()),
    transparent: true,
    frame: false,
    hasShadow: false,
    resizable: true,
    skipTaskbar: true,
    alwaysOnTop: true,
    fullscreenable: false,
    backgroundColor: '#00000000',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false }
  });
  // 'screen-saver' is the highest z-level Electron exposes; beats most fullscreen apps.
  overlay.setAlwaysOnTop(true, 'screen-saver');
  // Show on every macOS Space, including over fullscreen apps (no-op on Windows).
  overlay.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  // Exclude from screen capture: Windows uses WDA_EXCLUDEFROMCAPTURE (Win10 2004+),
  // macOS uses NSWindowSharingNone. See README "Limitations".
  overlay.setContentProtection(true);
  overlay.loadFile(path.join(__dirname, 'overlay', 'index.html'));

  // Remember position/size (debounced).
  let t;
  const remember = () => { clearTimeout(t); t = setTimeout(() => { settings().bounds = overlay.getBounds(); store.save(); }, 400); };
  overlay.on('move', remember);
  overlay.on('resize', remember);
  overlay.webContents.on('did-finish-load', () => { applyClickThrough(); pushState(); });
}

function applyClickThrough() {
  if (!overlay) return;
  // forward:true keeps mousemove events flowing to the page (used for hover hints)
  // while clicks pass through to the app underneath.
  overlay.setIgnoreMouseEvents(!!settings().clickThrough, { forward: true });
}

// ---------- panel window ----------
function createPanel() {
  panel = new BrowserWindow({
    width: 760, height: 720, title: 'Overlay Prompter — Control Panel',
    show: !screenshotArg,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false }
  });
  panel.loadFile(path.join(__dirname, 'panel', 'index.html'));
  panel.webContents.on('did-finish-load', pushState);
  panel.on('closed', () => { panel = null; app.quit(); }); // closing the panel quits the app
}

// ---------- hotkeys ----------
// CommandOrControl = Cmd on macOS, Ctrl on Windows. These work while other apps are focused.
const HOTKEYS = {
  'CommandOrControl+Shift+Space': () => broadcast('command', 'togglePlay'),
  'CommandOrControl+Shift+Up':    () => updateSettings({ speed: clamp(settings().speed + 5, 5, 300) }),
  'CommandOrControl+Shift+Down':  () => updateSettings({ speed: clamp(settings().speed - 5, 5, 300) }),
  'CommandOrControl+Shift+Left':  () => broadcast('command', 'jumpBack'),
  'CommandOrControl+Shift+Right': () => broadcast('command', 'jumpForward'),
  'CommandOrControl+Shift+H':     () => { overlay.isVisible() ? overlay.hide() : overlay.showInactive(); pushState(); },
  'CommandOrControl+Shift+L':     () => updateSettings({ clickThrough: !settings().clickThrough }),
  'CommandOrControl+Shift+R':     () => broadcast('command', 'restart'),
  'CommandOrControl+Shift+=':     () => updateSettings({ fontSize: clamp(settings().fontSize + 4, 16, 140) }),
  'CommandOrControl+Shift+-':     () => updateSettings({ fontSize: clamp(settings().fontSize - 4, 16, 140) })
};
function registerHotkeys() {
  const failed = [];
  for (const [acc, fn] of Object.entries(HOTKEYS)) {
    try { if (!globalShortcut.register(acc, fn)) failed.push(acc); } catch { failed.push(acc); }
  }
  if (failed.length) console.warn('[hotkeys] could not register (taken by another app?):', failed.join(', '));
  return failed;
}

// ---------- IPC from renderers ----------
ipcMain.handle('get-state', () => ({ settings: settings(), script: currentScript() }));
ipcMain.on('update-settings', (_e, patch) => updateSettings(patch));
ipcMain.on('command', (_e, cmd) => {
  if (cmd === 'toggleOverlay') { overlay.isVisible() ? overlay.hide() : overlay.showInactive(); pushState(); }
  else if (cmd === 'resetPosition') { overlay.setBounds(defaultBounds()); }
  else broadcast('command', cmd);
});
ipcMain.on('save-script', (_e, { id, title, text }) => {
  let s = store.data.scripts.find(x => x.id === id);
  if (!s) { s = { id: id || String(Date.now()), title: '', text: '' }; store.data.scripts.unshift(s); }
  Object.assign(s, { title: title || 'Untitled', text, updatedAt: new Date().toISOString() });
  store.data.currentScriptId = s.id;
  store.save(); pushState();
});
ipcMain.on('select-script', (_e, id) => { store.data.currentScriptId = id; store.save(); pushState(); });
ipcMain.on('delete-script', (_e, id) => {
  store.data.scripts = store.data.scripts.filter(s => s.id !== id);
  if (store.data.currentScriptId === id) store.data.currentScriptId = store.data.scripts[0]?.id || null;
  store.save(); pushState();
});
ipcMain.handle('open-file', async () => {
  const r = await dialog.showOpenDialog(panel, { properties: ['openFile'], filters: [{ name: 'Text', extensions: ['txt', 'md'] }] });
  if (r.canceled || !r.filePaths[0]) return null;
  const p = r.filePaths[0];
  return { title: path.basename(p).replace(/\.(txt|md)$/i, ''), text: fs.readFileSync(p, 'utf8') };
});
// Resize grip (bottom corner of the overlay). Transparent windows on Windows cannot be
// resized by their edges, so the renderer sends pointer deltas instead.
ipcMain.on('resize-overlay-by', (_e, { dw, dh }) => {
  const b = overlay.getBounds();
  overlay.setBounds({ ...b, width: Math.max(240, b.width + dw), height: Math.max(100, b.height + dh) });
});

// ---------- boot ----------
app.whenReady().then(() => {
  store = new Store(app.getPath('userData'));
  if (!store.data.scripts.length) {
    // Seed a Hebrew sample so first launch shows something useful.
    const sample = fs.readFileSync(path.join(__dirname, '..', 'samples', 'sample-he.txt'), 'utf8');
    store.data.scripts.push({ id: 'sample', title: 'דוגמה — Sample', text: sample, updatedAt: new Date().toISOString() });
    store.data.currentScriptId = 'sample';
    store.save();
  }
  createOverlay();
  createPanel();
  const failed = registerHotkeys();
  console.log('[startup] overlay + panel created; hotkeys failed:', failed.length);

  if (screenshotArg) {
    // Dev/CI helper: capture the overlay to a PNG (with alpha) then quit.
    const out = path.resolve(screenshotArg.split('=')[1]);
    overlay.webContents.once('did-finish-load', () => setTimeout(async () => {
      const img = await overlay.webContents.capturePage();
      fs.writeFileSync(out, img.toPNG());
      console.log('[screenshot] saved', out, img.getSize());
      app.quit();
    }, 2500));
  }
});
app.on('will-quit', () => globalShortcut.unregisterAll());
app.on('window-all-closed', () => app.quit());
