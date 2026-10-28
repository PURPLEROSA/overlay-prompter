// Tiny JSON persistence (no dependencies). Lives in Electron's userData folder:
//   Windows: %APPDATA%/Overlay Prompter/prompter-data.json
//   macOS:   ~/Library/Application Support/Overlay Prompter/prompter-data.json
const fs = require('fs');
const path = require('path');

const DEFAULTS = {
  settings: {
    speed: 40,          // pixels per second
    fontSize: 44,       // px
    opacity: 0.55,      // background opacity 0..1 (text stays fully opaque)
    mirror: false,      // horizontal flip (for beam-splitter glass rigs)
    rtl: true,          // Hebrew right-to-left
    highlightLine: true,
    clickThrough: false,
    bounds: null        // overlay window {x,y,width,height}; null = top-center default
  },
  scripts: [],          // [{id, title, text, updatedAt}]
  currentScriptId: null
};

class Store {
  constructor(dir) {
    this.file = path.join(dir, 'prompter-data.json');
    this.data = structuredClone(DEFAULTS);
    try {
      const raw = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      this.data = { ...this.data, ...raw, settings: { ...DEFAULTS.settings, ...(raw.settings || {}) } };
    } catch { /* first run or corrupt file -> defaults */ }
  }
  save() {
    // Write to temp then rename so a crash never leaves a half-written file.
    const tmp = this.file + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(this.data, null, 2));
    fs.renameSync(tmp, this.file);
  }
}

module.exports = { Store, DEFAULTS };
