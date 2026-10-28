// Overlay renderer: smooth scrolling text driven by requestAnimationFrame.
// Scroll position `offset` (px) is time-based (speed px/sec), so it is frame-rate independent.
const $ = id => document.getElementById(id);
const textEl = $('text'), viewport = $('viewport'), statusEl = $('status');
let settings = {}, playing = false, offset = 0, last = performance.now(), lastScriptKey = '';

function readlineY() { return viewport.clientHeight * 0.35; }       // must match --readline in CSS
function maxOffset() { return Math.max(0, textEl.scrollHeight); }

function render() {
  // Text starts with its first line on the reading line, then moves up.
  textEl.style.transform = `translateY(${readlineY() - offset - settings.fontSize * 0.7}px)`;
  statusEl.textContent = (playing ? '▶ ' : '⏸ ') + settings.speed + ' px/s';
}

function tick(now) {
  const dt = Math.min(0.1, (now - last) / 1000); last = now;
  if (playing) {
    offset += settings.speed * dt;
    if (offset >= maxOffset()) { offset = maxOffset(); playing = false; }
  }
  render();
  requestAnimationFrame(tick);
}

function applySettings(s) {
  settings = s;
  const root = document.documentElement.style;
  root.setProperty('--font-size', s.fontSize + 'px');
  root.setProperty('--bg-opacity', s.opacity);
  document.body.className = [s.rtl ? 'rtl' : 'ltr', s.mirror && 'mirror', !s.highlightLine && 'no-highlight',
    s.clickThrough && 'clickthrough'].filter(Boolean).join(' ');
}

function applyScript(script) {
  const key = script ? script.id + script.updatedAt : '';
  if (key === lastScriptKey) return;              // don't reset scroll on unrelated updates
  lastScriptKey = key;
  textEl.textContent = script ? script.text : 'אין תסריט · No script — open the control panel';
  offset = 0;
}

const lineStep = () => settings.fontSize * 1.4 * 3; // jump = 3 lines
window.prompter.onCommand(cmd => {
  if (cmd === 'togglePlay') playing = !playing;
  else if (cmd === 'play') playing = true;
  else if (cmd === 'pause') playing = false;
  else if (cmd === 'jumpBack') offset = Math.max(0, offset - lineStep());
  else if (cmd === 'jumpForward') offset = Math.min(maxOffset(), offset + lineStep());
  else if (cmd === 'restart') { offset = 0; playing = false; }
});
window.prompter.onState(st => { applySettings(st.settings); applyScript(st.script); });

// Mouse wheel scrubs the text manually (when not click-through).
window.addEventListener('wheel', e => { offset = Math.min(maxOffset(), Math.max(0, offset + e.deltaY)); });

// Resize grip -> main process (see main.js 'resize-overlay-by').
$('grip').addEventListener('pointerdown', e => {
  let x = e.screenX, y = e.screenY;
  const move = ev => { window.prompter.resizeOverlayBy(ev.screenX - x, ev.screenY - y); x = ev.screenX; y = ev.screenY; };
  const up = () => { removeEventListener('pointermove', move); removeEventListener('pointerup', up); };
  addEventListener('pointermove', move); addEventListener('pointerup', up);
});

window.prompter.getState().then(st => { applySettings(st.settings); applyScript(st.script); requestAnimationFrame(tick); });
