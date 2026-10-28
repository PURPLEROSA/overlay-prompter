// Safe bridge between renderers and main (contextIsolation on, no Node in pages).
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('prompter', {
  getState: () => ipcRenderer.invoke('get-state'),
  onState: cb => ipcRenderer.on('state', (_e, s) => cb(s)),
  onCommand: cb => ipcRenderer.on('command', (_e, c) => cb(c)),
  updateSettings: patch => ipcRenderer.send('update-settings', patch),
  command: c => ipcRenderer.send('command', c),
  saveScript: s => ipcRenderer.send('save-script', s),
  selectScript: id => ipcRenderer.send('select-script', id),
  deleteScript: id => ipcRenderer.send('delete-script', id),
  openFile: () => ipcRenderer.invoke('open-file'),
  resizeOverlayBy: (dw, dh) => ipcRenderer.send('resize-overlay-by', { dw, dh })
});
