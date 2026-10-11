// StudyFit · versión de prueba en navegador.
// Reproduce la lógica de Engine.kt (fases, descansos, llamadas) con tiempo acelerado.
'use strict';

const $ = (s) => document.querySelector(s);
const app = $('#app');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const PLACES = { CASA: 'En casa', FUERA: 'Al aire libre', UNI: 'Universidad', BIBLIO: 'Biblioteca / poco espacio' };
const AMBIENT = ['Nada', 'Lluvia', 'Ruido', 'Bach', 'Pachelbel', 'Tu música'];
const DAY = 86400000;

// ---------------- Almacenamiento ----------------
const DEFAULTS = {
  phase: 'IDLE', breakKind: 'EXERCISE', techId: 'pomodoro', place: 'CASA', subject: '', cycles: 4, cycleIndex: 1,
  phaseStart: 0, phaseEnd: 0, sessionStart: 0, studiedMs: 0, pendingMood: false, pendingCompleted: true,
  ambient: 0, water: {}, waterGoal: 8, sessions: [], exams: [], topics: [], bedHour: 23, bedMinute: 30, sleepOn: true,
  eyesOn: true, postureOn: true, nextEyes: 0, nextPosture: 0,
};
let S;
function load() {
  try { S = Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem('studyfit') || '{}')); }
  catch (e) { S = Object.assign({}, DEFAULTS); }
}
function save() { try { localStorage.setItem('studyfit', JSON.stringify(S)); } catch (e) { /* sin almacenamiento: se queda en memoria */ } }
load();

let speed = 20;
const MIN = () => 60000 / speed;
$('#speed').onchange = (e) => { speed = +e.target.value; };
$('#reset').onclick = () => { if (confirm('¿Borrar todos los datos de prueba?')) { S = Object.assign({}, DEFAULTS); save(); go('home'); } };

const tech = (id = S.techId) => DATA.techniques.find((t) => t.id === id) || DATA.techniques[0];
const inSession = () => S.phase === 'STUDY' || S.phase === 'BREAK';
const dayKey = (t = Date.now()) => { const d = new Date(t); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); };
const startOfDay = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
const waterToday = () => S.water[dayKey()] || 0;
const addWater = (n = 1) => { S.water[dayKey()] = Math.max(0, waterToday() + n); save(); };
const fmtMin = (m) => (m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`);
const fmtClock = (ms) => { const s = Math.max(0, Math.floor(ms / 1000)); return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`; };
const shuffle = (a) => a.map((x) => [Math.random(), x]).sort((p, q) => p[0] - q[0]).map((p) => p[1]);
const pick = (a) => a[Math.floor(Math.random() * a.length)];

function isLate() {
  if (!S.sleepOn) return false;
  const d = new Date(); const now = d.getHours() * 60 + d.getMinutes(); const bed = S.bedHour * 60 + S.bedMinute;
  return bed > 300 ? now >= bed || now < 300 : now >= bed && now < 300;
}
function streak() {
  const days = new Set(S.sessions.filter((s) => s.minutes > 0).map((s) => dayKey(s.start)));
  const d = new Date(); if (!days.has(dayKey(d))) d.setDate(d.getDate() - 1);
  let n = 0; while (days.has(dayKey(d))) { n++; d.setDate(d.getDate() - 1); }
  return n;
}

// ---------------- Motor (igual que Engine.kt) ----------------
function startSession(techId, place, subject, cycles) {
  if (S.pendingMood) saveSession(0, 0);
  const now = Date.now(); const t = tech(techId);
  Object.assign(S, { techId, place, subject, cycles, cycleIndex: 1, studiedMs: 0, sessionStart: now, phase: 'STUDY', paused: false, breakWaiting: false, phaseStart: now, phaseEnd: now + t.studyMin * MIN(), phaseTotal: t.studyMin * MIN() });
  scheduleMicro(); save();
}
function scheduleMicro() { const now = Date.now(); S.nextEyes = now + 20 * MIN(); S.nextPosture = now + 30 * MIN(); }

function onPhaseEnd() {
  const t = tech(); const now = Date.now();
  if (S.phase === 'STUDY') {
    S.studiedMs += Math.max(0, Math.min(now, S.phaseEnd) - S.phaseStart);
    if (S.cycleIndex >= S.cycles) { finish(true); save(); return call('end'); }
    const i = S.cycleIndex;
    const kind = t.longEvery <= S.cycles && i % t.longEvery === 0 ? 'LONG' : (i === 2 || (i > 2 && (i - 2) % 3 === 0)) ? 'SNACK' : 'EXERCISE';
    const len = kind === 'LONG' ? t.longBreakMin : t.breakMin;
    S.breakLen = len * MIN(); S.phaseTotal = S.breakLen;
    Object.assign(S, S.manualBreak !== false ? { breakKind: kind, phase: 'BREAK', breakWaiting: true, phaseStart: now, phaseEnd: 8.64e15 } : { breakKind: kind, phase: 'BREAK', breakWaiting: false, phaseStart: now, phaseEnd: now + S.breakLen });
    save(); stopAmbient();
    call({ EXERCISE: 'exercise', SNACK: 'snack', LONG: 'long' }[kind]);
  } else if (S.phase === 'BREAK') {
    Object.assign(S, { breakWaiting: false, cycleIndex: S.cycleIndex + 1, phase: 'STUDY', phaseStart: now, phaseEnd: now + t.studyMin * MIN(), phaseTotal: t.studyMin * MIN() });
    scheduleMicro(); save(); call('back');
  }
}
function leftMs() { return S.paused ? S.pausedLeft : S.breakWaiting ? S.breakLen : S.phaseEnd - Date.now(); }
function pauseSession() {
  if (!inSession() || S.paused || S.breakWaiting) return; const now = Date.now();
  if (S.phase === 'STUDY') S.studiedMs += Math.max(0, Math.min(now, S.phaseEnd) - S.phaseStart);
  Object.assign(S, { pausedLeft: Math.max(1000, S.phaseEnd - now), pausedAt: now, paused: true, phaseEnd: 8.64e15 }); stopAmbient(); save();
}
function resumeSession() { if (!S.paused) return; const now = Date.now(); Object.assign(S, { phaseStart: now, phaseEnd: now + S.pausedLeft, paused: false }); save(); }
function startBreak() { if (S.phase !== 'BREAK' || !S.breakWaiting) return; const now = Date.now(); Object.assign(S, { breakWaiting: false, phaseStart: now, phaseEnd: now + S.breakLen }); save(); }
function finish(completed) {
  S.breakWaiting = false;
  if (S.phase === 'STUDY' && !S.paused) S.studiedMs += Math.max(0, Math.min(Date.now(), S.phaseEnd) - S.phaseStart);
  Object.assign(S, { phase: 'DONE', paused: false, pendingMood: true, pendingCompleted: completed }); stopAmbient(); save();
}
function saveSession(focus, stress) {
  if (!S.pendingMood) return;
  S.sessions.push({ start: S.sessionStart, end: Date.now(), minutes: Math.round(S.studiedMs / MIN()), tech: S.techId, subject: S.subject, place: S.place, completed: S.pendingCompleted, focus, stress });
  Object.assign(S, { pendingMood: false, phase: 'IDLE' }); save();
}

// Bucle principal
setInterval(() => {
  const d = new Date(); $('#clock').textContent = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  if (inSession()) {
    const now = Date.now();
    if (!S.breakWaiting && !S.paused && now >= S.phaseEnd) onPhaseEnd();
    if (S.phase === 'STUDY' && !S.paused) {
      if (S.eyesOn && now >= S.nextEyes && S.nextEyes < S.phaseEnd - MIN()) { notify('Descansa la vista', DATA.eyes); S.nextEyes = now + 20 * MIN(); save(); }
      if (S.postureOn && now >= S.nextPosture && S.nextPosture < S.phaseEnd - MIN()) { notify('Revisa tu postura', pick(DATA.posture)); S.nextPosture = now + 30 * MIN(); save(); }
    }
  }
  if (typeof current.tick === 'function') current.tick();
}, 250);

// ---------------- Avisos ----------------
let toastT, notifT;
function toast(msg) { const t = $('#toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), 2200); }
function notify(title, text) {
  const n = $('#notif'); n.innerHTML = `<b>${esc(title)}</b>${esc(text)}`; n.classList.add('show');
  clearTimeout(notifT); notifT = setTimeout(() => n.classList.remove('show'), 5000);
  try { navigator.vibrate && navigator.vibrate(200); } catch (e) { }
}

// ---------------- Sonido (tono de llamada y ambiente, generados) ----------------
let ctx, ringNode, ambNode;
function audio() { try { ctx = ctx || new (window.AudioContext || window.webkitAudioContext)(); if (ctx.state === 'suspended') ctx.resume(); return ctx; } catch (e) { return null; } }
function startRing() {
  const a = audio(); if (!a) return; stopRing();
  const o = a.createOscillator(); const g = a.createGain(); o.type = 'sine'; o.frequency.value = 880;
  g.gain.value = 0; o.connect(g).connect(a.destination); o.start();
  const t0 = a.currentTime;
  for (let i = 0; i < 40; i++) { g.gain.setValueAtTime(0.18, t0 + i * 1.2); g.gain.setValueAtTime(0, t0 + i * 1.2 + 0.6); }
  ringNode = o;
  try { navigator.vibrate && navigator.vibrate([700, 500, 700, 500, 700]); } catch (e) { }
}
function stopRing() { try { ringNode && ringNode.stop(); } catch (e) { } ringNode = null; try { navigator.vibrate && navigator.vibrate(0); } catch (e) { } }
// Mismo motor que Ambient.kt: 1 lluvia, 2 ruido, 3 Bach BWV 846, 4 piano tranquilo, 5 tu música
let pianoTimer = null, ownAudio = null, ownUrls = [];
function playAmbient(k) {
  stopAmbient(); if (!k) return;
  if (k === 5) return playOwn();
  const a = audio(); if (!a) return;
  if (k === 3 || k === 4) return pianoLoop(a, k === 3 ? bachPrelude : canon);
  const len = a.sampleRate * 4; const buf = a.createBuffer(1, len, a.sampleRate); const d = buf.getChannelData(0);
  let brown = 0, lp = 0, drop = 0;
  for (let i = 0; i < len; i++) {
    const w = Math.random() * 2 - 1;
    if (k === 2) { brown = (brown + w * 0.02) / 1.02; d[i] = brown * 3.2; }
    else { lp += (w - lp) * 0.25; if (Math.random() < 0.0009) drop = 0.5 + Math.random() * 0.4; drop *= 0.992; d[i] = lp * 0.35 + w * drop * 0.25; }
  }
  const src = a.createBufferSource(); src.buffer = buf; src.loop = true;
  const g = a.createGain(); g.gain.value = 0.6; src.connect(g).connect(a.destination); src.start(); ambNode = src;
}
function stopAmbient() {
  try { ambNode && ambNode.stop(); } catch (e) { } ambNode = null;
  clearTimeout(pianoTimer); pianoTimer = null;
  if (pianoBus) { try { pianoBus.disconnect(); } catch (e) { } pianoBus = null; }
  if (ownAudio) { ownAudio.pause(); ownAudio = null; }
}
const NOTE = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };
const midi = (n) => { let i = 1, acc = 0; while (n[i] === '#' || n[i] === 'b') { acc += n[i] === '#' ? 1 : -1; i++; } return 12 * (+n.slice(i) + 1) + NOTE[n[0]] + acc; };
const toneCache = {};
function tone(a, m) {
  if (toneCache[m]) return toneCache[m];
  const f = 440 * Math.pow(2, (m - 69) / 12), rate = a.sampleRate, len = Math.floor(rate * 3.2);
  const buf = a.createBuffer(1, len, rate), d = buf.getChannelData(0);
  const decay = 0.9 + Math.max(0, m - 40) * 0.045, amps = [1, 0.45, 0.22, 0.12, 0.06, 0.03];
  for (let i = 0; i < len; i++) {
    const t = i / rate; let s = 0;
    for (let h = 0; h < amps.length; h++) { const fh = f * (h + 1) * (1 + 0.0004 * h * h); if (fh > rate / 2.2) break; s += amps[h] * Math.sin(2 * Math.PI * fh * t) * Math.exp(-t * decay * (1 + h * 0.6)); }
    d[i] = s * Math.min(1, t / 0.004);
  }
  return (toneCache[m] = buf);
}
let pianoBus = null;
function pianoLoop(a, next) {
  const bus = a.createGain(); bus.gain.value = 0.16;
  const dry = a.createGain(); const wet = a.createGain(); wet.gain.value = 0.22;
  const d1 = a.createDelay(); d1.delayTime.value = 0.083; const f1 = a.createGain(); f1.gain.value = 0.42;
  const d2 = a.createDelay(); d2.delayTime.value = 0.127; const f2 = a.createGain(); f2.gain.value = 0.36;
  dry.connect(bus); dry.connect(d1); d1.connect(f1).connect(d1); d1.connect(wet); dry.connect(d2); d2.connect(f2).connect(d2); d2.connect(wet); wet.connect(bus);
  bus.connect(a.destination); pianoBus = bus;
  let start = a.currentTime + 0.3;
  const schedule = () => {
    if (pianoBus !== bus) return;
    const [notes, dur] = next();
    for (const n of notes) { const s = a.createBufferSource(); s.buffer = n.str ? strings(a, n.midi, n.dur) : tone(a, n.midi); const g = a.createGain(); g.gain.value = n.vel; s.connect(g).connect(dry); s.start(start + n.at); }
    start += dur;
    pianoTimer = setTimeout(schedule, Math.max(0, (start - a.currentTime - 1.5) * 1000));
  };
  schedule();
}
const BACH = ['C4 E4 G4 C5 E5', 'C4 D4 A4 D5 F5', 'B3 D4 G4 D5 F5', 'C4 E4 G4 C5 E5', 'C4 E4 A4 E5 A5', 'C4 D4 F#4 A4 D5', 'B3 D4 G4 D5 G5', 'B3 C4 E4 G4 C5',
  'A3 C4 E4 G4 C5', 'D3 A3 D4 F#4 C5', 'G3 B3 D4 G4 B4', 'G3 Bb3 E4 G4 C#5', 'F3 A3 D4 A4 D5', 'F3 Ab3 D4 F4 B4', 'E3 G3 C4 G4 C5', 'E3 F3 A3 C4 F4',
  'D3 F3 A3 C4 F4', 'G2 D3 G3 B3 F4', 'C3 E3 G3 C4 E4', 'C3 G3 Bb3 C4 E4', 'F2 F3 A3 C4 E4', 'F#2 C3 A3 C4 Eb4', 'Ab2 F3 B3 C4 D4', 'G2 F3 G3 B3 D4',
  'G2 E3 G3 C4 E4', 'G2 D3 G3 C4 F4', 'G2 D3 G3 B3 F4', 'G2 Eb3 A3 C4 F#4', 'G2 E3 G3 C4 G4', 'G2 D3 G3 C4 F4', 'G2 D3 G3 B3 F4', 'C2 C3 G3 Bb3 E4',
  'C2 C3 F3 A3 C4', 'C2 B2 G3 B3 D4'].map((b) => b.split(' ').map(midi));
function bachPrelude() {
  const six = 60 / 66 / 4, out = []; let t = 0;
  for (const bar of BACH) for (let r = 0; r < 2; r++) { [0, 1, 2, 3, 4, 2, 3, 4].forEach((n, k) => out.push({ at: t + k * six, midi: bar[n], vel: k === 0 ? 0.95 : k === 1 ? 0.75 : 0.6 })); t += 8 * six; }
  ['C2', 'C3', 'E4', 'G4', 'C5'].forEach((n, i) => out.push({ at: t + i * 0.09, midi: midi(n), vel: 0.7 }));
  return [out, t + 6];
}
const strCache = {};
function strings(a, m, dur) {
  const key = m + ':' + dur.toFixed(2); if (strCache[key]) return strCache[key];
  const f = 440 * Math.pow(2, (m - 69) / 12), rate = a.sampleRate, att = 0.14, rel = 0.45, len = Math.floor((dur + rel) * rate);
  const buf = a.createBuffer(1, len, rate), d = buf.getChannelData(0); const ph = new Float64Array(8);
  for (let i = 0; i < len; i++) {
    const t = i / rate, vib = 1 + 0.0035 * Math.sin(2 * Math.PI * 5.2 * t) * Math.min(1, t / 0.4); let s = 0;
    for (let h = 0; h < 8; h++) { const fh = f * (h + 1) * vib; if (fh > rate / 2.2) break; ph[h] += 2 * Math.PI * fh / rate; s += Math.sin(ph[h]) / Math.pow(h + 1, 1.25); }
    const env = t < att ? t / att : t < dur ? 1 - 0.15 * ((t - att) / dur) : 0.85 * (1 - (t - dur) / rel);
    d[i] = s * Math.max(0, Math.min(1, env));
  }
  return (strCache[key] = buf);
}
// Pachelbel, Canon en Re (h. 1680): bajo que se repite, acordes y melodía en variaciones
let canonRound = 0;
function canon() {
  const half = 60 / 56 * 2, out = [];
  const bass = ['D3', 'A2', 'B2', 'F#2', 'G2', 'D2', 'G2', 'A2'].map(midi);
  const ch = ['F#3 A3 D4', 'E3 A3 C#4', 'F#3 B3 D4', 'F#3 A3 C#4', 'G3 B3 D4', 'F#3 A3 D4', 'G3 B3 D4', 'E3 A3 C#4'].map((c) => c.split(' ').map(midi));
  for (let k = 0; k < 8; k++) { const t = k * half; out.push({ at: t, midi: bass[k], vel: 0.55, dur: half * 0.98, str: true }); ch[k].forEach((n) => out.push({ at: t + 0.03, midi: n, vel: 0.16, dur: half * 0.98, str: true })); }
  const line = (notes, step, vel) => notes.split(' ').forEach((n, i) => out.push({ at: i * step, midi: midi(n), vel, dur: step * 0.95, str: true }));
  const r = canonRound++ % 6;
  if (r === 1 || r === 5) line('F#5 E5 D5 C#5 B4 A4 B4 C#5', half, 0.42);
  if (r === 2) line('D5 C#5 B4 A4 G4 F#4 G4 E4', half, 0.42);
  if (r === 3) line('D5 F#5 A5 G5 F#5 D5 F#5 E5 D5 B4 D5 A5 G5 B5 A5 G5', half / 2, 0.36);
  if (r === 4) ch.flatMap((c) => [c[0] + 12, c[1] + 12, c[2] + 12, c[1] + 12]).forEach((n, i) => out.push({ at: i * half / 4, midi: n, vel: 0.3, dur: half / 4 * 0.9, str: true }));
  return [out, 8 * half];
}
function playOwn(i = 0) {
  if (!ownUrls.length) { const f = document.createElement('input'); f.type = 'file'; f.accept = 'audio/*'; f.multiple = true;
    f.onchange = () => { ownUrls = [...f.files].map((x) => URL.createObjectURL(x)); if (ownUrls.length) playOwn(); }; f.click(); return; }
  ownAudio = new Audio(ownUrls[i % ownUrls.length]); ownAudio.onended = () => playOwn(i + 1); ownAudio.play().catch(() => { });
}

// ---------------- Navegación ----------------
let current = {};
let callState = null;
const phone = $('.phone');
function go(name, arg) {
  current = {}; app.className = 'screen'; app.scrollTop = 0; phone.classList.remove('night');
  (SCREENS[name] || SCREENS.home)(arg);
  current.name = name;
}
function night(on) { app.classList.toggle('dark', on); phone.classList.toggle('night', on); }
function call(type) { callState = { type, ringing: true }; go('call'); }
const chips = (items, sel, cols, onclick) =>
  `<div class="chips" style="--cols:${cols}">${items.map(([v, l]) => `<button class="chip ${String(v) === String(sel) ? 'on' : ''}" data-v="${esc(v)}" onclick="${onclick}(this)">${esc(l)}</button>`).join('')}</div>`;
function selectChip(el) { el.parentNode.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c === el)); return el.dataset.v; }
const ringSvg = (track, fill) => `<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="46" stroke="${track}"/><circle id="ringFill" cx="50" cy="50" r="46" stroke="${fill}" stroke-dasharray="289.03" stroke-dashoffset="289.03"/></svg>`;
const setRing = (p) => { const c = $('#ringFill'); if (c) c.style.strokeDashoffset = String(289.03 * (1 - Math.max(0, Math.min(1, p)))); };
const placeChips = (sel, fn) => chips(Object.entries(PLACES), sel, 2, fn);

// ---------------- Pantallas ----------------
const SCREENS = {};

SCREENS.home = () => {
  const h = new Date().getHours();
  const hello = h >= 6 && h <= 13 ? 'Buenos días' : h >= 14 && h <= 20 ? 'Buenas tardes' : 'Buenas noches';
  const today0 = startOfDay(Date.now());
  const todayMin = S.sessions.filter((s) => s.start >= today0).reduce((a, s) => a + s.minutes, 0);
  const exam = S.exams.slice().sort((a, b) => a.date - b.date).find((e) => e.date >= today0);
  const due = S.topics.filter((t) => t.nextDue <= Date.now()).length;
  const recent = S.sessions.slice(-3).filter((s) => s.stress > 0);
  const stressHigh = recent.length && recent.reduce((a, s) => a + s.stress, 0) / recent.length >= 4;
  const st = streak();
  let main;
  if (inSession()) {
    main = `<div class="card night"><p><b style="font-weight:500">${S.phase === 'STUDY' ? 'Estudiando' : 'En descanso'}, ${esc(tech().name)}</b></p>
      <p class="muted" id="homeLeft"></p><button class="btn bone big" style="margin-top:12px" onclick="go('session')">Volver a la sesión</button></div>`;
    current.tick = () => { const e = $('#homeLeft'); if (e) e.textContent = S.paused ? `En pausa: quedan ${fmtClock(leftMs())}` : S.breakWaiting ? 'El descanso espera a que lo empieces' : `Quedan ${fmtClock(leftMs())} en este bloque`; };
  } else {
    main = (isLate() ? `<div class="card"><p><b style="font-weight:500">Ya es tu hora de dormir</b></p><p class="muted">Dormir fija lo que has estudiado. Si es urgente, haz un bloque corto y a la cama.</p></div>` : '') +
      `<button class="btn big" style="margin-top:20px" onclick="go('start')">Empezar a estudiar</button>`;
  }
  let examHtml = '<p class="muted">No tienes exámenes apuntados.</p>';
  if (exam) {
    const days = Math.round((exam.date - today0) / DAY);
    examHtml = `<p><b style="font-weight:500">${esc(exam.subject)}: ${days === 0 ? 'hoy' : days === 1 ? 'mañana' : `en ${days} días`}</b></p><p class="muted">Te recomiendo ${recMin(days)} de estudio hoy.</p>`;
  }
  app.innerHTML = `
    <p class="muted" style="margin-top:8px;font-size:15px">${hello}</p><h1>¿Qué estudiamos hoy?</h1>
    <div class="card warn"><p class="small">En la app real aquí te avisa si falta activar algún permiso.</p></div>
    ${main}
    <h2>Hoy</h2>
    <div class="stats"><div><b>${fmtMin(todayMin)}</b><span>estudiado</span></div><div><b>${st}</b><span>${st === 1 ? 'día seguido' : 'días seguidos'}</span></div><div><b>${waterToday()}/${S.waterGoal}</b><span>vasos de agua</span></div></div>
    <div class="row"><button class="btn water-soft" onclick="addWater(1);go('home')">+1 vaso de agua</button><button class="btn soft fixed" style="width:64px" onclick="addWater(-1);go('home')">−1</button></div>
    <h2>Plan</h2>
    <div class="card">${examHtml}<p style="margin-top:8px">${due ? `${due} tema(s) para repasar hoy` : 'Nada que repasar hoy'}</p>
      <button class="btn text" onclick="go('planner')">Abrir el plan</button></div>
    ${stressHigh ? `<div class="card olive"><p><b style="font-weight:500">Has tenido sesiones con estrés</b></p><p>${esc(pick(DATA.stress))}</p></div>` : ''}
    <hr>
    <div class="row"><button class="btn outline" onclick="go('stats')">Estadísticas</button><button class="btn outline" onclick="go('settings')">Ajustes</button></div>
    <button class="btn text" onclick="simulateNfc()">Simular la etiqueta NFC de la mesa</button>`;
  if (S.pendingMood) askMood();
};

function simulateNfc() { toast('Etiqueta NFC detectada'); go(inSession() ? 'session' : 'start'); }

SCREENS.start = () => {
  let chosen = S.techId, place = S.place, cycles = [2, 3, 4, 5, 6, 8].includes(S.cycles) ? S.cycles : 4, amb = S.ambient;
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Nueva sesión</h1>
    <p class="muted">Elige cómo quieres estudiar. La app bloquea distracciones y te avisa para moverte, comer y volver.</p>
    <h2>Técnica de estudio</h2>
    ${DATA.techniques.map((t) => `<div class="card opt ${t.id === chosen ? 'sel' : ''}" tabindex="0" data-t="${t.id}"><p><b style="font-weight:500">${esc(t.name)}</b></p><p class="accent">${esc(t.short)}</p><p class="muted">${esc(t.howTo)}</p></div>`).join('')}
    <h2>¿Dónde estás?</h2><p class="muted">Para proponerte ejercicios que puedas hacer ahí.</p>
    ${placeChips(place, 'pickPlace')}
    <h2>¿Qué vas a estudiar?</h2><input type="text" id="subj" placeholder="Asignatura o tema (opcional)" value="${esc(S.subject)}">
    <h2>¿Cuántos bloques?</h2>${chips([2, 3, 4, 5, 6, 8].map((n) => [n, String(n)]), cycles, 6, 'pickCycles')}
    <p class="muted" id="est"></p>
    <h2>Sonido de fondo</h2><p class="muted">Se genera en el móvil, no gasta datos.</p>
    ${chips(AMBIENT.map((n, i) => [i, n]), amb, 3, 'pickAmb')}<p class="muted">Bach y Piano suenan con un piano generado en el momento. En «Tu música» eliges tus propios archivos.</p>
    <button class="btn big" style="margin-top:24px" id="go">Empezar</button>`;
  const est = () => { const t = tech(chosen); $('#est').textContent = `Unos ${fmtMin(t.studyMin * cycles + t.breakMin * (cycles - 1))} en total, ${fmtMin(t.studyMin * cycles)} de estudio. En la prueba el tiempo va acelerado.`; };
  app.querySelectorAll('[data-t]').forEach((c) => c.onclick = () => { chosen = c.dataset.t; app.querySelectorAll('[data-t]').forEach((x) => x.classList.toggle('sel', x === c)); est(); });
  window.pickPlace = (el) => { place = selectChip(el); };
  window.pickCycles = (el) => { cycles = +selectChip(el); est(); };
  window.pickAmb = (el) => { amb = +selectChip(el); };
  est();
  $('#go').onclick = () => {
    const begin = () => { S.ambient = amb; startSession(chosen, place, $('#subj').value.trim(), cycles); go('session'); };
    if (isLate()) dialog('Es tarde', 'Ya pasó tu hora de dormir. Estudiar con sueño rinde poco y empeora la memoria de mañana. ¿Seguro?', [['Solo un rato', begin], ['Mejor a dormir', () => go('home')]]);
    else begin();
  };
};

let guideOpen = false;
SCREENS.session = () => {
  if (!inSession()) return go('home');
  const study = S.phase === 'STUDY'; const t = tech();
  if (S.paused) {
    const col = study ? 'var(--primary)' : 'var(--olive)';
    app.innerHTML = `<p class="small" style="color:var(--primary)">${study ? 'Estudio en pausa' : 'Descanso en pausa'}</p>
      <div class="ringwrap small">${ringSvg('#F6F1EA', study ? '#8C5A3C' : '#6F7448')}<div class="in"><div class="timer" id="timer" style="color:var(--muted)">--:--</div></div></div>
      <h1>Tómate el tiempo que necesites</h1>
      <p class="muted">El tiempo está congelado y el bloqueo de apps desactivado. Cuando acabes, vuelve y reanuda: seguirás justo donde lo dejaste.</p>
      <button class="btn big" style="margin-top:20px;background:${col}" onclick="resumeSession();go('session')">Reanudar</button>
      <hr><button class="btn text" style="color:var(--muted)" onclick="toast('En el móvil abriría el marcador con el 112')">Llamada de emergencia (112)</button>
      <button class="btn text" id="exit" style="color:var(--muted)">Terminar la sesión (mantén pulsado)</button>`;
  } else if (study) {
    night(true);
    app.innerHTML = `
      <div class="row"><p class="muted">Bloque ${S.cycleIndex} de ${S.cycles}</p><button class="btn text fixed" style="color:var(--night-text)" onclick="pauseSession();go('session')">Pausar</button><button class="btn text fixed" id="exit" style="text-align:right">Salir</button></div>
      <div class="ringwrap">${ringSvg('#352B24', '#D4B188')}<div class="in"><div class="timer" id="timer">--:--</div><p class="muted" style="margin-top:6px">${esc(t.name)}</p></div></div>
      <p class="step-now" id="stepNow"></p>
      ${S.subject ? `<p class="muted" style="margin-top:8px">${esc(S.subject)}</p>` : ''}
      <button class="btn text" id="guideBtn" style="margin-top:24px">${guideOpen ? 'Ocultar la guía' : 'Ver la guía completa'}</button>
      <div id="guide" style="display:${guideOpen ? 'block' : 'none'}">${t.steps.map((s, i) => `<p class="step" data-i="${i}">Min ${s.at}&nbsp;&nbsp; ${esc(s.text)}</p>`).join('')}</div>
      <p class="muted" style="margin-top:28px">Sonido de fondo</p>
      ${chips(AMBIENT.map((n, i) => [i, n]), S.ambient, 3, 'setAmb')}
      <div class="card" style="background:#352B24;margin-top:18px"><p class="muted">En la app real, si abres otra app te devuelve aquí.</p>
        <button class="btn text" onclick="toast('Ahora toca estudiar. Descanso en ' + fmtClock(S.phaseEnd - Date.now()))">Probar: abrir Instagram</button></div>
      <hr><button class="btn text" onclick="toast('En el móvil abriría el marcador con el 112')">Llamada de emergencia (112)</button>`;
    $('#guideBtn').onclick = () => { guideOpen = !guideOpen; $('#guide').style.display = guideOpen ? 'block' : 'none'; $('#guideBtn').textContent = guideOpen ? 'Ocultar la guía' : 'Ver la guía completa'; };
    if (!ambNode && S.ambient) playAmbient(S.ambient);
  } else {
    const [title, sub, type] = {
      EXERCISE: ['Toca moverse', 'Unos minutos de movimiento y vuelves con la cabeza despejada.', 'exercise'],
      SNACK: ['Pausa para comer algo', 'Algo ligero, agua, y de vuelta.', 'snack'],
      LONG: ['Descanso largo', 'Muévete, bebe agua y come algo ligero.', 'long'],
    }[S.breakKind];
    app.innerHTML = `<p class="small" style="color:var(--olive)">Descanso</p>
      <div class="ringwrap small">${ringSvg('#ECEDE0', '#6F7448')}<div class="in"><div class="timer" id="timer">--:--</div></div></div>
      <h1>${title}</h1>${S.breakWaiting ? `<p class="muted">El descanso está en pausa. Elige qué vas a hacer y empieza cuando estés lista.</p>
      <button class="btn olive big" style="margin-top:16px" onclick="callState={type:'${type}',ringing:false};go('call')">Elegir ejercicio y empezar</button>
      <button class="btn outline olive" onclick="startBreak();go('session')">Empezar descanso ya</button>` : `<p class="muted">${sub}</p>
      <button class="btn olive big" style="margin-top:16px" onclick="callState={type:'${type}',ringing:false};go('call')">Ver qué hacer</button>`}
      <div class="card water"><div class="row"><div><p><b style="font-weight:500">Agua</b></p><p class="muted">${waterToday()} de ${S.waterGoal} vasos hoy</p></div>
        <button class="btn water fixed" onclick="addWater();go('session')">+1 vaso</button></div></div>
      <p class="muted">En el descanso puedes usar el móvil. Te aviso cuando toque volver.</p>
      ${S.breakWaiting ? '' : '<button class="btn outline olive" onclick="pauseSession();go(\'session\')">Pausar el descanso</button>'}
      <hr><button class="btn text" id="exit" style="color:var(--muted)">Terminar la sesión (mantén pulsado)</button>`;
  }
  window.setAmb = (el) => { S.ambient = +selectChip(el); save(); playAmbient(S.ambient); };
  const ex = $('#exit'); let pt;
  ex.onpointerdown = () => { pt = setTimeout(() => dialog('¿Terminar la sesión?', 'Se guardará como interrumpida. Si es una urgencia, sal sin problema.',
    [['Terminar', () => { finish(false); go('home'); }], ['Seguir', null]]), 700); };
  ex.onpointerup = ex.onpointerleave = () => clearTimeout(pt);
  ex.onclick = () => toast('Mantén pulsado para salir');
  const key = `${S.phase}-${S.cycleIndex}-${S.breakWaiting}-${S.paused}`; let lastStep = -1;
  current.tick = () => {
    if (!inSession() || `${S.phase}-${S.cycleIndex}-${S.breakWaiting}-${S.paused}` !== key) { if (current.name === 'session') go('session'); return; }
    const now = Date.now();
    if (S.breakWaiting) { $('#timer').textContent = fmtClock(S.breakLen); setRing(0); return; }
    const left = leftMs();
    $('#timer').textContent = fmtClock(left);
    setRing(1 - left / (S.phaseTotal || 1));
    if (study && !S.paused) {
      const el = Math.floor(((S.phaseTotal || 0) - left) / MIN());
      let idx = 0; t.steps.forEach((s, i) => { if (s.at <= el) idx = i; });
      if (idx !== lastStep) {
        lastStep = idx; $('#stepNow').textContent = t.steps[idx].text;
        app.querySelectorAll('.step').forEach((p) => { const i = +p.dataset.i; p.classList.toggle('now', i === idx); p.classList.toggle('past', i < idx); });
      }
    }
  };
  current.tick();
};

SCREENS.call = () => {
  const type = callState?.type || 'exercise'; const t = tech();
  if (callState.ringing) {
    const breakLen = type === 'long' ? t.longBreakMin : t.breakMin;
    const bed = `${String(S.bedHour).padStart(2, '0')}:${String(S.bedMinute).padStart(2, '0')}`;
    const V = {
      exercise: ['Hora de moverte', `Descanso activo, ${PLACES[S.place].toLowerCase()}`, `${breakLen}′`, false, 'var(--olive)'],
      long: ['Descanso largo', 'Movimiento, agua y algo de comer', `${breakLen}′`, false, 'var(--olive)'],
      snack: ['Pausa para comer algo', 'Algo ligero que te dé energía', `${breakLen}′`, false, 'var(--olive)'],
      back: ['Vuelve a estudiar', `${t.name}, bloque ${S.cycleIndex} de ${S.cycles}`, `${t.studyMin}′`, true, 'var(--sand)'],
      end: ['Sesión terminada', 'Buen trabajo. Ahora descansa de verdad.', `${Math.round(S.studiedMs / MIN())}′`, false, 'var(--primary)'],
      start: ['Es tu hora de estudiar', 'Prepara agua y apuntes, y empezamos', '▶', true, 'var(--sand)'],
      sleep: ['Hora de dormir', 'Dormir fija lo que has estudiado hoy', bed, true, 'var(--night-surface)'],
    }[type];
    const [title, sub, label, dark, color] = V;
    night(dark);
    const fg = dark && type !== 'sleep' ? 'var(--night)' : type === 'sleep' ? 'var(--night-text)' : '#fff';
    app.innerHTML = `<div class="call"><p class="muted">StudyFit</p>
      <div class="halo" style="--c:${color};--fg:${fg}"><i></i><i></i><b>${esc(label)}</b></div>
      <h1>${title}</h1><p class="muted" style="margin-bottom:48px">${esc(sub)}</p>
      <button class="btn big ${dark ? 'bone' : type === 'end' ? '' : 'olive'}" id="accept">Aceptar</button>
      ${type === 'start' || type === 'sleep' ? '<button class="btn text" id="later" style="width:100%;text-align:center">Ahora no</button>' : ''}</div>`;
    startRing();
    $('#accept').onclick = () => { stopRing(); if (type === 'start') return go('start'); if (type === 'sleep') return go('home'); callState.ringing = false; go('call'); };
    const l = $('#later'); if (l) l.onclick = () => { stopRing(); go('settings'); };
    return;
  }
  stopRing();
  let html = '';
  const breakLeft = '<div id="blbox"></div>';
  const fillBL = () => { const b = $('#blbox'); if (!b) return; b.innerHTML = S.phase === 'BREAK' && S.breakWaiting ? `<p class="muted">La cuenta atrás de ${fmtMin(Math.round(S.breakLen / MIN()))} empieza cuando pulses el botón.</p><button class="btn olive big" id="startBreak">Empezar descanso</button>` : '<p class="muted" id="bl"></p>'; const sb = $('#startBreak'); if (sb) sb.onclick = () => { startBreak(); fillBL(); }; };
  current.tick = () => { const e = $('#bl'); if (e) e.textContent = S.phase === 'BREAK' ? (S.paused ? `Descanso en pausa (quedan ${fmtClock(leftMs())})` : `Quedan ${fmtClock(leftMs())} de descanso`) : ''; };
  if (type === 'exercise' || type === 'long') html += `<h1>Muévete</h1>${breakLeft}<p class="muted" style="margin-top:14px">¿Dónde estás ahora?</p>
      ${placeChips(S.place, 'callPlace')}<div id="routine"></div>`;
  if (type === 'snack') html += `<h1>Algo de comer</h1>${breakLeft}`;
  if (type === 'snack' || type === 'long') {
    const nightTime = new Date().getHours() >= 21 || new Date().getHours() < 6;
    html += `<h2>Ideas para comer</h2><p class="muted">Para concentrarte sin pasarte: proteína o grasa buena con fibra, en raciones pequeñas.</p>
      ${shuffle(nightTime ? DATA.snacksNight : DATA.snacksDay).slice(0, 3).map((s) => `<div class="card"><p><b style="font-weight:500">${esc(s.name)}</b></p><p class="muted">${esc(s.why)}</p></div>`).join('')}
      <div class="card water"><p style="white-space:pre-line" class="small">${esc(DATA.snackTips)}</p></div>
      <button class="btn water" onclick="addWater();this.textContent='Apuntado: '+waterToday()+' vasos hoy'">He bebido un vaso de agua</button>`;
  }
  if (type === 'back') {
    html = `<h1>A estudiar</h1><p class="muted">Bloque ${S.cycleIndex} de ${S.cycles}, ${esc(t.name)}</p>
      <div class="card" style="margin-top:14px"><p class="muted">Para empezar</p><p>${esc(t.steps[0].text)}</p></div>
      <p class="muted">El bloqueo de distracciones ya está activo.</p><button class="btn big" onclick="go('session')">Ir a la sesión</button>`;
  } else if (type === 'end') {
    html = `<h1>Sesión terminada</h1><p>Has terminado todos los bloques. Ahora descansa de verdad: sal a la calle, come algo o habla con alguien.</p>
      <div class="card water"><p>${esc(DATA.water)}</p></div><button class="btn big" onclick="go('home')">Ver el resumen</button>`;
  } else {
    html += `<hr><button class="btn olive" onclick="go(inSession() ? 'session' : 'home')">Volver a la sesión</button>`;
  }
  app.innerHTML = html; fillBL();
  if (type === 'exercise' || type === 'long') {
    const minutes = type === 'long' ? Math.max(3, t.longBreakMin - 5) : t.breakMin;
    window.callPlace = (el) => { S.place = selectChip(el); save(); routine(S.place, minutes); };
    routine(S.place, minutes);
  }
};

let exInt;
function routine(place, minutes) {
  clearInterval(exInt);
  const budget = Math.max(2, minutes - 1) * 60; const out = []; let tt = 0;
  for (const e of shuffle(DATA.exercises[place])) { if (tt + e.seconds <= budget) { out.push(e); tt += e.seconds + 15; } if (out.length >= 5) break; }
  if (!out.length) out.push(DATA.exercises[place][0]);
  let i = 0;
  $('#routine').innerHTML = `<div class="card olive" style="margin-top:14px"><p class="small" style="color:var(--olive)" id="exn"></p><canvas id="fig" class="fig"></canvas><h2 style="margin:4px 0" id="exname"></h2><p id="exhow"></p>
    <div class="extimer" id="exclock"></div><div class="row"><button class="btn olive" id="exgo">Empezar</button><button class="btn outline olive" id="exnext">Siguiente</button></div></div>
    <p style="margin-top:14px"><b style="font-weight:500">Rutina completa</b></p><p class="muted">Toca un ejercicio para verlo.</p>${out.map((e, k) => `<button class="btn text" style="display:block;color:var(--text)" data-k="${k}">${esc(e.name)}, ${e.seconds} s</button>`).join('')}
    <p class="muted" style="margin-top:8px">En la prueba el cronómetro de cada ejercicio va en tiempo real.</p>`;
  const show = () => { const e = out[i]; $('#exn').textContent = `Ejercicio ${i + 1} de ${out.length}`; $('#exname').textContent = e.name; $('#exhow').textContent = e.how; $('#exclock').textContent = fmtClock(e.seconds * 1000); figure($('#fig'), e.name); };
  $('#routine').querySelectorAll('[data-k]').forEach((b) => b.onclick = () => { clearInterval(exInt); i = +b.dataset.k; show(); app.scrollTop = 0; });
  $('#exnext').onclick = () => { clearInterval(exInt); i = (i + 1) % out.length; show(); };
  $('#exgo').onclick = () => {
    clearInterval(exInt); const end = Date.now() + out[i].seconds * 1000;
    exInt = setInterval(() => { const l = end - Date.now(); const c = $('#exclock'); if (c) c.textContent = l > 0 ? fmtClock(l) : 'Hecho'; if (l <= 0) { clearInterval(exInt); try { navigator.vibrate && navigator.vibrate(400); } catch (e) { } } }, 250);
  };
  show();
}

function recMin(d) { return d <= 3 ? '3 h' : d <= 7 ? '2 h' : d <= 14 ? '1 h 30 min' : d <= 30 ? '1 h' : '30 min'; }
function advice(d) { return d <= 1 ? 'Solo repaso y recuerdo activo, nada nuevo. Duerme bien.' : d <= 7 ? 'Haz exámenes antiguos y recuerdo activo.' : d <= 14 ? 'Termina temario y empieza a hacer problemas.' : 'Avanza temario poco a poco y añade los temas a repasos.'; }
const fmtDate = (t) => new Date(t).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short' });
const INTERVALS = [1, 3, 7, 14, 30];

SCREENS.planner = () => {
  const today0 = startOfDay(Date.now()); const now = Date.now();
  const exams = S.exams.slice().sort((a, b) => a.date - b.date);
  const topics = S.topics.slice().sort((a, b) => a.nextDue - b.nextDue);
  const due = topics.filter((t) => t.nextDue <= now); const later = topics.filter((t) => t.nextDue > now);
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Plan</h1>
    <h2>Exámenes</h2>${exams.length ? '' : '<p class="muted">Añade tus exámenes y te digo cuánto estudiar cada día.</p>'}
    ${exams.map((e) => { const d = Math.round((e.date - today0) / DAY); return `<div class="card" ${d >= 0 && d <= 7 ? 'style="background:var(--primary-soft)"' : ''}><p><b style="font-weight:500">${esc(e.subject)}</b></p>
      <p>${fmtDate(e.date)}, ${d < 0 ? 'ya pasó' : d === 0 ? 'es hoy. Mucha suerte' : d === 1 ? 'mañana' : `faltan ${d} días`}</p>
      ${d >= 0 ? `<p class="muted">Recomendado: ${recMin(d)} al día. ${advice(d)}</p>` : ''}
      <button class="btn text danger" onclick="S.exams=S.exams.filter(x=>x.id!==${e.id});save();go('planner')">Borrar</button></div>`; }).join('')}
    <div class="card"><input type="text" id="exs" placeholder="Asignatura (p. ej. Bases de Datos)"><input type="date" id="exd">
      <button class="btn" id="addEx">Añadir examen</button></div>
    <h2>Repasos</h2><p class="muted">Cuando aprendas un tema, añádelo. Te lo pediré repasar a los 1, 3, 7, 14 y 30 días.</p>
    ${due.length ? '' : '<div class="card"><p>Nada que repasar hoy.</p></div>'}
    ${due.map((t) => `<div class="card olive"><p><b style="font-weight:500">${esc(t.name)}</b></p>${t.subject ? `<p class="muted">${esc(t.subject)}</p>` : ''}
      <p class="muted">Repaso ${t.stage + 1}. Primero intenta recordarlo sin mirar.</p>
      <div class="row"><button class="btn olive" onclick="review(${t.id},true)">Lo recordaba</button><button class="btn outline olive" onclick="review(${t.id},false)">Lo olvidé</button></div></div>`).join('')}
    ${later.length ? `<p style="margin-top:14px"><b style="font-weight:500">Próximos repasos</b></p>${later.map((t) => `<p class="muted">${esc(t.name)}, ${fmtDate(t.nextDue)}</p>`).join('')}` : ''}
    <div class="card"><input type="text" id="tn" placeholder="Tema (p. ej. Normalización 3FN)"><input type="text" id="ts" placeholder="Asignatura (opcional)">
      <button class="btn" id="addT">Añadir tema aprendido</button>
      <button class="btn text" onclick="S.topics.forEach(t=>t.nextDue=0);save();go('planner')">Prueba: que todos toquen hoy</button></div>`;
  $('#addEx').onclick = () => { const d = $('#exd').value; if (!d) return toast('Elige una fecha'); const [y, m, dd] = d.split('-').map(Number);
    S.exams.push({ id: Date.now(), subject: $('#exs').value.trim() || 'Examen', date: new Date(y, m - 1, dd).getTime() }); save(); go('planner'); };
  $('#addT').onclick = () => { const n = $('#tn').value.trim(); if (!n) return toast('Escribe el tema');
    S.topics.push({ id: Date.now(), name: n, subject: $('#ts').value.trim(), stage: 0, nextDue: startOfDay(Date.now()) + INTERVALS[0] * DAY }); save(); go('planner'); };
};
function review(id, ok) {
  const t = S.topics.find((x) => x.id === id); if (!t) return;
  t.stage = ok ? Math.min(t.stage + 1, INTERVALS.length - 1) : 0; t.nextDue = startOfDay(Date.now()) + INTERVALS[t.stage] * DAY; save(); go('planner');
}

SCREENS.stats = () => {
  const L = S.sessions; const today0 = startOfDay(Date.now());
  const sum = (from) => L.filter((s) => s.start >= from).reduce((a, s) => a + s.minutes, 0);
  const days = [...Array(7)].map((_, i) => { const d = new Date(); d.setDate(d.getDate() - (6 - i)); const k = dayKey(d);
    return [d.toLocaleDateString('es-ES', { weekday: 'short' }).slice(0, 2), L.filter((s) => dayKey(s.start) === k).reduce((a, s) => a + s.minutes, 0)]; });
  const max = Math.max(30, ...days.map((d) => d[1]));
  const byTech = {}; L.forEach((s) => (byTech[s.tech] = byTech[s.tech] || []).push(s));
  const rated = Object.fromEntries(Object.entries(byTech).map(([k, l]) => { const f = l.filter((s) => s.focus > 0); return [k, f.length ? f.reduce((a, s) => a + s.focus, 0) / f.length : null]; }).filter((e) => e[1] !== null));
  const best = Object.entries(rated).sort((a, b) => b[1] - a[1])[0];
  const st = L.filter((s) => s.stress > 0); const avg = (a) => a.reduce((x, s) => x + s.stress, 0) / a.length;
  const done = L.filter((s) => s.completed).length;
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Estadísticas</h1>
    <div class="stats" style="margin-top:18px"><div><b>${fmtMin(sum(today0))}</b><span>hoy</span></div><div><b>${fmtMin(sum(today0 - 6 * DAY))}</b><span>7 días</span></div><div><b>${streak()}</b><span>días seguidos</span></div></div>
    <p class="muted">${fmtMin(sum(today0 - 29 * DAY))} en 30 días. Sesiones completas: ${done}, interrumpidas: ${L.length - done}.</p>
    <h2>Últimos 7 días</h2><div class="bars">${days.map(([l, m], i) => `<div class="${i === 6 ? 'today' : ''}">${m ? (m >= 60 ? `${Math.floor(m / 60)}h${m % 60}` : `${m}′`) : ''}<i style="height:${m / max * 100}%"></i>${l}</div>`).join('')}</div>
    <h2>Tus técnicas</h2>${L.length ? '' : '<p class="muted">Aún no hay sesiones. Empieza una.</p>'}
      ${Object.entries(byTech).map(([k, l]) => `<p>${esc(tech(k).name)}: ${l.length} sesiones, ${fmtMin(l.reduce((a, s) => a + s.minutes, 0))}${rated[k] != null ? `, concentración ${rated[k].toFixed(1)} de 5` : ''}</p>`).join('')}
      ${best && Object.keys(rated).length >= 2 ? `<p style="color:var(--primary);margin-top:8px">La que mejor te funciona: ${esc(tech(best[0]).name)}</p>` : ''}
    ${st.length ? `<h2>Estrés</h2><p>Media reciente: ${avg(st.slice(-5)).toFixed(1)} de 5</p>` : ''}`;
};

SCREENS.settings = () => {
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Ajustes</h1>
    <h2>Permisos</h2><p class="muted">En la app real aquí activas notificaciones, bloqueo de apps, llamadas a pantalla completa, alarmas exactas y batería sin restricción.</p>
    <h2>Apps permitidas mientras estudias</h2><p class="muted">En la app real eliges de la lista de tus apps (lector de PDF, calculadora…).</p>
    <h2>Empezar a una hora fija</h2><p class="muted">A la hora que elijas, StudyFit te llama para empezar.</p>
    <button class="btn outline" onclick="call('start')">Probar la llamada de inicio</button>
    <button class="btn outline" onclick="call('sleep')">Probar el aviso de dormir</button>
    <h2>Descansos</h2>
    <label class="sw">Empezar el descanso cuando yo lo pulse<input type="checkbox" ${S.manualBreak !== false ? 'checked' : ''} onchange="S.manualBreak=this.checked;save()"></label>
    <h2>Recordatorios de salud</h2>
    <label class="sw">Vista 20-20-20 cada 20 min durante el estudio<input type="checkbox" ${S.eyesOn ? 'checked' : ''} onchange="S.eyesOn=this.checked;save()"></label>
    <label class="sw">Postura cada 30 min durante el estudio<input type="checkbox" ${S.postureOn ? 'checked' : ''} onchange="S.postureOn=this.checked;save()"></label>
    <p class="muted" style="margin-top:8px">Objetivo de agua: ${S.waterGoal} vasos al día</p>
    <div class="row"><button class="btn outline" onclick="S.waterGoal=Math.max(4,S.waterGoal-1);save();go('settings')">−</button><button class="btn outline" onclick="S.waterGoal=Math.min(14,S.waterGoal+1);save();go('settings')">+</button></div>
    <h2>Sueño</h2>
    <label class="sw">Avisarme de la hora de dormir<input type="checkbox" ${S.sleepOn ? 'checked' : ''} onchange="S.sleepOn=this.checked;save()"></label>
    <input type="time" value="${String(S.bedHour).padStart(2, '0')}:${String(S.bedMinute).padStart(2, '0')}" onchange="const [h,m]=this.value.split(':');S.bedHour=+h;S.bedMinute=+m;save()">
    <p class="muted">Te aviso 30 minutos antes y a la hora.</p>`;
};

function dialog(title, msg, buttons) {
  const bg = document.createElement('div'); bg.className = 'dialog-bg';
  bg.innerHTML = `<div class="dialog"><h2 style="margin-top:0">${esc(title)}</h2><p style="white-space:pre-line">${esc(msg)}</p><div class="btns" style="margin-top:14px"></div></div>`;
  buttons.forEach(([label, fn], i) => { const b = document.createElement('button'); b.className = 'btn' + (i ? ' outline' : ''); b.textContent = label;
    b.onclick = () => { bg.remove(); fn && fn(); }; bg.querySelector('.btns').appendChild(b); });
  phone.appendChild(bg);
}

function askMood() {
  const mins = Math.round(S.studiedMs / MIN());
  let focus = 3, stress = 2;
  const bg = document.createElement('div'); bg.className = 'dialog-bg';
  bg.innerHTML = `<div class="dialog"><h2 style="margin-top:0">¿Cómo ha ido?</h2>
    <p>${S.pendingCompleted ? `Sesión completada: ${fmtMin(mins)} de estudio.` : `Sesión interrumpida: ${fmtMin(mins)} de estudio.`}</p>
    <h2>¿Qué tal tu concentración?</h2><p class="muted">1 = muy baja, 5 = muy alta</p>${chips([1, 2, 3, 4, 5].map((n) => [n, String(n)]), 3, 5, 'moodF')}
    <h2>¿Y tu nivel de estrés?</h2><p class="muted">1 = nada, 5 = mucho</p>${chips([1, 2, 3, 4, 5].map((n) => [n, String(n)]), 2, 5, 'moodS')}
    <button class="btn" id="mSave" style="margin-top:16px">Guardar</button><button class="btn text" id="mSkip" style="width:100%;text-align:center">Saltar</button></div>`;
  phone.appendChild(bg);
  window.moodF = (el) => { focus = +selectChip(el); };
  window.moodS = (el) => { stress = +selectChip(el); };
  bg.querySelector('#mSave').onclick = () => { bg.remove(); saveSession(focus, stress);
    if (stress >= 4) dialog('Un momento para ti', DATA.stress.join('\n\n'), [['Gracias', () => go('home')]]); else go('home'); };
  bg.querySelector('#mSkip').onclick = () => { bg.remove(); saveSession(0, 0); go('home'); };
}

go(inSession() ? 'session' : 'home');

// ---------------- Muñeco animado (mismo dibujo que MoveView.kt) ----------------
let figRaf = null;
function figure(cv, name) {
  cancelAnimationFrame(figRaf);
  const id = MOVES.map[name]; const an = id && MOVES.moves[id];
  if (!cv) return; cv.style.display = an ? 'block' : 'none'; if (!an) return;
  const dpr = window.devicePixelRatio || 1; const W = cv.clientWidth, H = cv.clientHeight;
  cv.width = W * dpr; cv.height = H * dpr; const c = cv.getContext('2d'); c.scale(dpr, dpr);
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches; const t0 = performance.now();
  const draw = (now) => {
    const u = reduce ? 0 : ((now - t0) / 1000 / an.dur) % 1;
    const s = Math.min(W, H) / 100, ox = (W - 100 * s) / 2, oy = (H - 100 * s) / 2, X = (v) => ox + v * s, Y = (v) => oy + v * s;
    c.clearRect(0, 0, W, H); c.lineCap = 'round'; c.lineJoin = 'round';
    c.strokeStyle = '#E7DDD0'; c.lineWidth = 1.6 * s; c.beginPath(); c.moveTo(X(0), Y(91.5)); c.lineTo(X(100), Y(91.5)); c.stroke();
    (an.props || []).forEach((pl) => { c.beginPath(); pl.forEach(([x, y], i) => i ? c.lineTo(X(x), Y(y)) : c.moveTo(X(x), Y(y))); c.stroke(); });
    const n = an.frames.length, x = u * n, i = Math.floor(x) % n, t = (1 - Math.cos(Math.PI * (x - Math.floor(x)))) / 2;
    const f0 = an.frames[i], f1 = an.frames[(i + 1) % n];
    const p = (k) => f0[k] && [X(f0[k][0] + ((f1[k] || f0[k])[0] - f0[k][0]) * t), Y(f0[k][1] + ((f1[k] || f0[k])[1] - f0[k][1]) * t)];
    const seg = (a, b, col) => { const A = p(a), B = p(b); if (!A || !B) return; c.strokeStyle = col; c.beginPath(); c.moveTo(...A); c.lineTo(...B); c.stroke(); };
    c.lineWidth = 3.2 * s;
    const front = 'ls' in f0, NEAR = '#3A2E25', BACK = front ? NEAR : '#C4B2A0';
    const LA = front ? 'ls' : 'neck', RA = front ? 'rs' : 'neck', LL = front ? 'lp' : 'hip', RL = front ? 'rp' : 'hip';
    [[LL, 'lk'], ['lk', 'lf'], ['lf', 'lt'], [LA, 'le'], ['le', 'lh']].forEach(([a, b]) => seg(a, b, BACK));
    if (p('spine')) { const h = p('hip'), sp = p('spine'), nk = p('neck'); c.strokeStyle = NEAR; c.beginPath(); c.moveTo(...h); c.quadraticCurveTo(...sp, ...nk); c.stroke(); } else seg('hip', 'neck', NEAR);
    if (front) { seg('ls', 'rs', NEAR); seg('lp', 'rp', NEAR); }
    [[RL, 'rk'], ['rk', 'rf'], ['rf', 'rt'], [RA, 're'], ['re', 'rh']].forEach(([a, b]) => seg(a, b, NEAR));
    const hd = p('head'); c.fillStyle = '#8C5A3C'; c.beginPath(); c.arc(hd[0], hd[1], 5.5 * s, 0, 2 * Math.PI); c.fill();
    if (!reduce && document.body.contains(cv)) figRaf = requestAnimationFrame(draw);
  };
  figRaf = requestAnimationFrame(draw);
}
