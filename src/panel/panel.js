// Control panel renderer: script CRUD + settings UI. Main process is the source of truth;
// every change is sent to main, which persists it and broadcasts 'state' back.
const $ = id => document.getElementById(id);
let state = null, editingId = null;

const ranges = ['speed', 'fontSize', 'opacity'];
const checks = ['rtl', 'mirror', 'highlightLine', 'clickThrough'];

function renderList() {
  const ul = $('list'); ul.innerHTML = '';
  for (const s of state.scripts) {
    const li = document.createElement('li');
    li.textContent = s.title; li.title = s.title;
    if (s.id === state.currentScriptId) li.classList.add('active');
    li.onclick = () => window.prompter.selectScript(s.id);
    ul.appendChild(li);
  }
}

window.prompter.onState(st => {
  const scriptChanged = !state || st.currentScriptId !== state.currentScriptId;
  state = st;
  for (const k of ranges) { $(k).value = st.settings[k]; document.querySelector(`output[for=${k}]`).textContent = st.settings[k]; }
  for (const k of checks) $(k).checked = !!st.settings[k];
  renderList();
  // Only overwrite the editor when switching scripts, so typing isn't clobbered.
  if (scriptChanged) { editingId = st.script?.id || null; $('title').value = st.script?.title || ''; $('editor').value = st.script?.text || ''; }
});

for (const k of ranges) $(k).addEventListener('input', e => window.prompter.updateSettings({ [k]: Number(e.target.value) }));
for (const k of checks) $(k).addEventListener('change', e => window.prompter.updateSettings({ [k]: e.target.checked }));
document.querySelectorAll('[data-cmd]').forEach(b => b.onclick = () => window.prompter.command(b.dataset.cmd));

$('save').onclick = () => window.prompter.saveScript({ id: editingId, title: $('title').value.trim(), text: $('editor').value });
$('new').onclick = () => { editingId = null; $('title').value = ''; $('editor').value = ''; $('editor').focus(); };
$('delete').onclick = () => { if (editingId && confirm('למחוק את התסריט? Delete this script?')) window.prompter.deleteScript(editingId); };
$('load').onclick = async () => {
  const f = await window.prompter.openFile();
  if (f) { editingId = null; $('title').value = f.title; $('editor').value = f.text; }
};
