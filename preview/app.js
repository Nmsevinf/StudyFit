// StudyFit · versión de prueba en navegador.
// Reproduce la lógica de Engine.kt (fases, descansos, llamadas) con tiempo acelerado.
'use strict';

const $ = (s) => document.querySelector(s);
const app = $('#app');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const PLACES = { CASA: ['🏠', 'En casa'], FUERA: ['🌳', 'Al aire libre'], UNI: ['🎓', 'Universidad'], BIBLIO: ['📚', 'Biblioteca / poco espacio'] };
const AMBIENT = ['Silencio', 'Ruido blanco', 'Ruido marrón', 'Lluvia'];
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
  Object.assign(S, { techId, place, subject, cycles, cycleIndex: 1, studiedMs: 0, sessionStart: now, phase: 'STUDY', phaseStart: now, phaseEnd: now + t.studyMin * MIN() });
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
    Object.assign(S, { breakKind: kind, phase: 'BREAK', phaseStart: now, phaseEnd: now + len * MIN() });
    save(); stopAmbient();
    call({ EXERCISE: 'exercise', SNACK: 'snack', LONG: 'long' }[kind]);
  } else if (S.phase === 'BREAK') {
    Object.assign(S, { cycleIndex: S.cycleIndex + 1, phase: 'STUDY', phaseStart: now, phaseEnd: now + t.studyMin * MIN() });
    scheduleMicro(); save(); call('back');
  }
}
function finish(completed) {
  if (S.phase === 'STUDY') S.studiedMs += Math.max(0, Math.min(Date.now(), S.phaseEnd) - S.phaseStart);
  Object.assign(S, { phase: 'DONE', pendingMood: true, pendingCompleted: completed }); stopAmbient(); save();
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
    if (now >= S.phaseEnd) onPhaseEnd();
    if (S.phase === 'STUDY') {
      if (S.eyesOn && now >= S.nextEyes && S.nextEyes < S.phaseEnd - MIN()) { notify('Descansa la vista 👀', DATA.eyes); S.nextEyes = now + 20 * MIN(); save(); }
      if (S.postureOn && now >= S.nextPosture && S.nextPosture < S.phaseEnd - MIN()) { notify('Revisa tu postura 🪑', pick(DATA.posture)); S.nextPosture = now + 30 * MIN(); save(); }
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
function playAmbient(k) {
  stopAmbient(); if (!k) return; const a = audio(); if (!a) return;
  const len = a.sampleRate * 4; const buf = a.createBuffer(1, len, a.sampleRate); const d = buf.getChannelData(0);
  let brown = 0, lp = 0, drop = 0;
  for (let i = 0; i < len; i++) {
    const w = Math.random() * 2 - 1;
    if (k === 1) d[i] = w * 0.18;
    else if (k === 2) { brown = (brown + w * 0.02) / 1.02; d[i] = brown * 3.2; }
    else { lp += (w - lp) * 0.25; if (Math.random() < 0.0009) drop = 0.5 + Math.random() * 0.4; drop *= 0.992; d[i] = lp * 0.35 + w * drop * 0.25; }
  }
  const src = a.createBufferSource(); src.buffer = buf; src.loop = true;
  const g = a.createGain(); g.gain.value = 0.6; src.connect(g).connect(a.destination); src.start(); ambNode = src;
}
function stopAmbient() { try { ambNode && ambNode.stop(); } catch (e) { } ambNode = null; }

// ---------------- Navegación ----------------
let current = {};
let callState = null;
function go(name, arg) {
  current = {}; app.className = 'screen'; app.scrollTop = 0;
  (SCREENS[name] || SCREENS.home)(arg);
  current.name = name;
}
function call(type) { callState = { type, ringing: true }; go('call'); }
const chips = (items, sel, cols, onclick) =>
  `<div class="chips" style="--cols:${cols}">${items.map(([v, l]) => `<button class="chip ${v === sel ? 'on' : ''}" data-v="${esc(v)}" onclick="${onclick}(this)">${esc(l)}</button>`).join('')}</div>`;
function selectChip(el) { el.parentNode.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c === el)); return el.dataset.v; }

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
  let main;
  if (inSession()) {
    main = `<div class="card primary"><p><b>${S.phase === 'STUDY' ? 'Estudiando' : 'En descanso'} · ${esc(tech().name)}</b></p>
      <p id="homeLeft"></p><button class="btn white big" onclick="go('session')">Volver a la sesión</button></div>`;
    current.tick = () => { const e = $('#homeLeft'); if (e) e.textContent = `Quedan ${fmtClock(S.phaseEnd - Date.now())} en este bloque`; };
  } else {
    main = (isLate() ? `<div class="card soft"><p><b>🌙 Ya es tu hora de dormir</b></p><p class="muted">Dormir consolida lo que has estudiado. Si es urgente, haz un bloque corto y a la cama.</p></div>` : '') +
      `<button class="btn big" onclick="go('start')">▶&nbsp; Empezar a estudiar</button>`;
  }
  let examHtml = '<p class="muted">Sin exámenes apuntados.</p>';
  if (exam) {
    const days = Math.round((exam.date - today0) / DAY);
    examHtml = `<p>📅 ${esc(exam.subject)}: ${days === 0 ? '¡HOY!' : days === 1 ? 'mañana' : `en ${days} días`}</p><p class="muted">Recomendado hoy: ${recMin(days)} de estudio</p>`;
  }
  app.innerHTML = `
    <p class="muted">${hello} 👋</p><h1>StudyFit</h1><p class="muted">Estudia mejor, cuida tu cuerpo.</p>
    <div class="card accent"><p><b>ℹ️ En la app real</b></p><p>Aquí te avisaría si falta activar algún permiso (bloqueo de apps, notificaciones…).</p></div>
    ${main}
    <div class="card"><p><b>Hoy</b></p>
      <div class="stats"><div><b>${fmtMin(todayMin)}</b><span>estudiado</span></div><div><b>${streak()} 🔥</b><span>días seguidos</span></div><div><b>${waterToday()}/${S.waterGoal}</b><span>vasos de agua</span></div></div>
      <div class="row"><button class="btn blue" style="flex:2" onclick="addWater(1);go('home')">+1 vaso 💧</button><button class="btn outline" style="color:var(--blue)" onclick="addWater(-1);go('home')">−1</button></div>
    </div>
    <div class="card"><p><b>Plan</b></p>${examHtml}<p>${due ? `🔁 ${due} tema(s) para repasar hoy` : '🔁 Nada que repasar hoy'}</p>
      <button class="btn" onclick="go('planner')">Abrir planificador</button></div>
    ${stressHigh ? `<div class="card soft"><p><b>💚 Te noto con estrés</b></p><p>${esc(pick(DATA.stress))}</p></div>` : ''}
    <div class="row"><button class="btn" onclick="go('stats')">📊 Estadísticas</button><button class="btn grey" onclick="go('settings')">⚙️ Ajustes</button></div>
    <button class="btn outline" onclick="simulateNfc()">📲 Simular etiqueta NFC en la mesa</button>`;
  if (S.pendingMood) askMood();
};

function simulateNfc() { toast('Etiqueta NFC detectada 📲'); go(inSession() ? 'session' : 'start'); }

SCREENS.start = () => {
  let chosen = S.techId, place = S.place, cycles = [2, 3, 4, 5, 6, 8].includes(S.cycles) ? S.cycles : 4, amb = S.ambient;
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Nueva sesión</h1>
    <p class="muted">Elige cómo quieres estudiar. La app se encarga del resto: bloquea distracciones y te avisa para moverte, comer y volver.</p>
    <h2>1 · Técnica de estudio</h2>
    ${DATA.techniques.map((t) => `<div class="card click ${t.id === chosen ? 'sel' : ''}" data-t="${t.id}"><p><b>${esc(t.name)}</b></p><p class="tag">${esc(t.short)}</p><p class="muted">${esc(t.howTo)}</p></div>`).join('')}
    <h2>2 · ¿Dónde estás?</h2><p class="muted">Así te propongo ejercicios que puedas hacer ahí.</p>
    ${chips(Object.entries(PLACES).map(([k, [e, l]]) => [k, `${e} ${l}`]), place, 2, 'pickPlace')}
    <h2>3 · ¿Qué vas a estudiar?</h2><input type="text" id="subj" placeholder="Asignatura o tema (opcional)" value="${esc(S.subject)}">
    <h2>4 · ¿Cuántos bloques?</h2>${chips([2, 3, 4, 5, 6, 8].map((n) => [String(n), String(n)]), String(cycles), 6, 'pickCycles')}
    <p class="muted" id="est"></p>
    <h2>5 · Opciones</h2><p class="muted">Sonido ambiente (generado en el móvil, no gasta datos):</p>
    ${chips(AMBIENT.map((n, i) => [String(i), n]), String(amb), 2, 'pickAmb')}
    <button class="btn big" id="go">▶&nbsp; Empezar</button>`;
  const est = () => { const t = tech(chosen); $('#est').textContent = `≈ ${fmtMin(t.studyMin * cycles + t.breakMin * (cycles - 1))} en total, ${fmtMin(t.studyMin * cycles)} de estudio. (En la prueba va acelerado.)`; };
  app.querySelectorAll('[data-t]').forEach((c) => c.onclick = () => { chosen = c.dataset.t; app.querySelectorAll('[data-t]').forEach((x) => x.classList.toggle('sel', x === c)); est(); });
  window.pickPlace = (el) => { place = selectChip(el); };
  window.pickCycles = (el) => { cycles = +selectChip(el); est(); };
  window.pickAmb = (el) => { amb = +selectChip(el); };
  est();
  $('#go').onclick = () => {
    const begin = () => { S.ambient = amb; startSession(chosen, place, $('#subj').value.trim(), cycles); go('session'); };
    if (isLate()) dialog('🌙 Es tarde', 'Ya pasó tu hora de dormir. Estudiar con sueño rinde poco y empeora la memoria de mañana. ¿Seguro?', [['Solo un rato', begin], ['Mejor a dormir', () => go('home')]]);
    else begin();
  };
};

SCREENS.session = () => {
  if (!inSession()) return go('home');
  const study = S.phase === 'STUDY'; const t = tech();
  app.className = 'screen' + (study ? ' dark' : '');
  let body;
  if (study) {
    body = `<div class="card deep"><p class="tag">AHORA</p><p style="font-size:19px"><b id="stepNow"></b></p></div>
      <p class="tag" style="margin-top:10px">Guía de la técnica</p>
      ${t.steps.map((s, i) => `<p class="step" data-i="${i}">min ${s.at} · ${esc(s.text)}</p>`).join('')}
      <p class="tag" style="margin-top:14px">Sonido ambiente</p>${chips(AMBIENT.map((n, i) => [String(i), n]), String(S.ambient), 2, 'setAmb')}
      <div class="card deep"><p class="muted">🔒 En la app real, si abres otra app (Instagram, WhatsApp…) te devuelve aquí. Prueba:</p>
      <button class="btn outline" onclick="toast('📚 Ahora toca estudiar. Descanso en ' + fmtClock(S.phaseEnd - Date.now()))">Abrir Instagram</button></div>`;
    if (!ambNode && S.ambient) playAmbient(S.ambient);
  } else {
    const [title, type] = { EXERCISE: ['Toca moverse 🏃', 'exercise'], SNACK: ['Pausa para tentempié 🍎', 'snack'], LONG: ['Descanso largo: ejercicio + tentempié 🌿', 'long'] }[S.breakKind];
    body = `<div class="card"><p><b>${title}</b></p><p class="muted">Lugar: ${PLACES[S.place].join(' ')}</p>
        <button class="btn" onclick="callState={type:'${type}',ringing:false};go('call')">Ver qué hacer</button></div>
      <div class="card soft"><p><b>💧 Bebe agua</b></p><div class="row"><p class="muted">Llevas ${waterToday()} vasos hoy</p><button class="btn blue" onclick="addWater();go('session')">+1 vaso</button></div></div>
      <p class="muted">En el descanso puedes usar el móvil. Te llamaré cuando toque volver.</p>`;
  }
  app.innerHTML = `<p class="tag center">${study ? `📚 BLOQUE ${S.cycleIndex} DE ${S.cycles}` : '🌿 DESCANSO'}</p>
    <p class="center">${esc(t.name)}${S.subject ? ' · ' + esc(S.subject) : ''}</p>
    <div class="timer" id="timer">--:--</div><div class="progress"><i id="prog"></i></div>${body}
    <hr style="border:none;border-top:1px solid rgba(128,128,128,.3);margin:14px 0">
    <div class="row"><button class="btn outline danger" onclick="toast('En el móvil abriría el marcador con el 112')">📞 Llamar al 112</button>
    <button class="btn outline" id="exit">Salir (mantén pulsado)</button></div>`;
  window.setAmb = (el) => { S.ambient = +selectChip(el); save(); playAmbient(S.ambient); };
  // Mantener pulsado para salir
  const ex = $('#exit'); let pt;
  const down = () => { pt = setTimeout(() => dialog('¿Terminar la sesión?', 'Se guardará como interrumpida. Si es una urgencia, sal sin problema.',
    [['Sí, salir', () => { finish(false); go('home'); }], ['Seguir estudiando', null]]), 700); };
  const up = () => clearTimeout(pt);
  ex.onpointerdown = down; ex.onpointerup = up; ex.onpointerleave = up;
  ex.onclick = () => toast('Mantén pulsado para salir de la sesión');
  const key = `${S.phase}-${S.cycleIndex}`; let lastStep = -1;
  current.tick = () => {
    if (!inSession() || `${S.phase}-${S.cycleIndex}` !== key) { if (current.name === 'session') go('session'); return; }
    const now = Date.now();
    $('#timer').textContent = fmtClock(S.phaseEnd - now);
    $('#prog').style.width = Math.min(100, (now - S.phaseStart) / (S.phaseEnd - S.phaseStart) * 100) + '%';
    if (study) {
      const el = Math.floor((now - S.phaseStart) / MIN());
      let idx = 0; t.steps.forEach((s, i) => { if (s.at <= el) idx = i; });
      if (idx !== lastStep) { lastStep = idx; $('#stepNow').textContent = t.steps[idx].text; app.querySelectorAll('.step').forEach((p) => p.classList.toggle('now', +p.dataset.i === idx)); }
    }
  };
};

SCREENS.call = () => {
  const type = callState?.type || 'exercise'; const t = tech();
  if (callState.ringing) {
    const [emoji, title, sub] = {
      exercise: ['🏃', '¡Hora de moverte!', `Descanso activo · ${PLACES[S.place][1]}`],
      long: ['🌿', 'Descanso largo', 'Ejercicio + agua + tentempié'],
      snack: ['🍎', 'Pausa para tentempié', 'Recarga energía sin pasarte'],
      back: ['📚', 'Vuelve a estudiar', `${t.name} · bloque ${S.cycleIndex} de ${S.cycles}`],
      end: ['🎉', '¡Sesión terminada!', 'Muy bien hecho'],
    }[type];
    app.className = 'screen dark';
    app.innerHTML = `<div class="ring"><p class="muted">StudyFit te llama…</p><div class="emoji">${emoji}</div>
      <h1 style="margin-top:10px">${title}</h1><p class="muted" style="margin-bottom:60px">${esc(sub)}</p>
      <button class="btn green big" id="accept">✅&nbsp; Aceptar</button></div>`;
    startRing();
    $('#accept').onclick = () => { stopRing(); callState.ringing = false; go('call'); };
    return;
  }
  stopRing();
  let html = '';
  const breakLeft = '<p class="muted" id="bl"></p>';
  current.tick = () => { const e = $('#bl'); if (e) e.textContent = S.phase === 'BREAK' ? `Descanso: quedan ${fmtClock(S.phaseEnd - Date.now())}` : ''; };
  if (type === 'exercise' || type === 'long') html += `<h1>Muévete 🏃</h1>${breakLeft}<p class="muted">¿Dónde estás ahora?</p>
      ${chips(Object.entries(PLACES).map(([k, [e, l]]) => [k, `${e} ${l}`]), S.place, 2, 'callPlace')}<div id="routine"></div>`;
  if (type === 'snack') html += `<h1>Tentempié 🍎</h1>${breakLeft}`;
  if (type === 'snack' || type === 'long') {
    const night = new Date().getHours() >= 21 || new Date().getHours() < 6;
    html += `<h2>Ideas de tentempié</h2><p class="muted">Para concentrarte mejor sin engordar: proteína o grasa buena + fibra, raciones pequeñas.</p>
      ${shuffle(night ? DATA.snacksNight : DATA.snacksDay).slice(0, 3).map((s) => `<div class="card"><p><b>${esc(s.name)}</b></p><p class="muted">${esc(s.why)}</p></div>`).join('')}
      <div class="card soft"><p style="white-space:pre-line">${esc(DATA.snackTips)}</p></div>
      <button class="btn blue" onclick="addWater();this.textContent='💧 ¡Apuntado! ('+waterToday()+' hoy)'">💧 He bebido un vaso de agua</button>`;
  }
  if (type === 'back') {
    html = `<h1>¡A estudiar! 📚</h1><p>Bloque ${S.cycleIndex} de ${S.cycles} · ${esc(t.name)}</p>
      <div class="card soft"><p class="tag">Primer paso</p><p>${esc(t.steps[0].text)}</p></div>
      <p class="muted">El bloqueo de distracciones ya está activo.</p><button class="btn big" onclick="go('session')">Ir a la sesión</button>`;
  } else if (type === 'end') {
    html = `<h1>¡Enhorabuena! 🎉</h1><p>Has terminado todos los bloques. Ahora descansa de verdad: sal a la calle, come algo o habla con alguien.</p>
      <div class="card soft"><p>${esc(DATA.water)}</p></div><button class="btn big" onclick="go('home')">Ver resumen</button>`;
  } else {
    html += `<hr style="border:none;border-top:1px solid #e1e7e5;margin:14px 0"><button class="btn" onclick="go(inSession() ? 'session' : 'home')">Hecho · volver a la sesión</button>`;
  }
  app.innerHTML = html;
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
  const box = $('#routine');
  box.innerHTML = `<div class="card soft"><p class="tag" id="exn"></p><h2 style="margin:2px 0" id="exname"></h2><p id="exhow"></p>
    <div class="extimer" id="exclock"></div><div class="row"><button class="btn" id="exgo">▶ Empezar</button><button class="btn outline" id="exnext">Siguiente ›</button></div></div>
    <p><b>Rutina completa</b></p>${out.map((e) => `<p class="muted">• ${esc(e.name)} — ${e.seconds} s</p>`).join('')}
    <p class="muted">(En la prueba el cronómetro de cada ejercicio va en tiempo real.)</p>`;
  const show = () => { const e = out[i]; $('#exn').textContent = `EJERCICIO ${i + 1} DE ${out.length}`; $('#exname').textContent = e.name; $('#exhow').textContent = e.how; $('#exclock').textContent = fmtClock(e.seconds * 1000); };
  $('#exnext').onclick = () => { clearInterval(exInt); i = (i + 1) % out.length; show(); };
  $('#exgo').onclick = () => {
    clearInterval(exInt); const end = Date.now() + out[i].seconds * 1000;
    exInt = setInterval(() => { const l = end - Date.now(); $('#exclock') && ($('#exclock').textContent = l > 0 ? fmtClock(l) : '¡Hecho! ✅'); if (l <= 0) { clearInterval(exInt); try { navigator.vibrate && navigator.vibrate(400); } catch (e) { } } }, 250);
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
    <h2>📅 Exámenes</h2>${exams.length ? '' : '<p class="muted">Añade tus exámenes y te digo cuánto estudiar cada día.</p>'}
    ${exams.map((e) => { const d = Math.round((e.date - today0) / DAY); return `<div class="card ${d >= 0 && d <= 7 ? 'soft' : ''}"><p><b>${esc(e.subject)}</b></p>
      <p>${fmtDate(e.date)} · ${d < 0 ? 'ya pasó' : d === 0 ? '¡HOY! Mucha suerte 🍀' : d === 1 ? 'mañana' : `faltan ${d} días`}</p>
      ${d >= 0 ? `<p class="muted">Recomendado: ${recMin(d)} al día. ${advice(d)}</p>` : ''}
      <button class="btn outline danger" onclick="S.exams=S.exams.filter(x=>x.id!==${e.id});save();go('planner')">Borrar</button></div>`; }).join('')}
    <div class="card"><input type="text" id="exs" placeholder="Asignatura (p. ej. Bases de Datos)"><input type="date" id="exd">
      <button class="btn" id="addEx">+ Añadir examen</button></div>
    <h2>🔁 Repasos (repetición espaciada)</h2><p class="muted">Cuando aprendas un tema, añádelo aquí. Te lo pediré repasar a los 1, 3, 7, 14 y 30 días.</p>
    ${due.length ? '' : '<div class="card soft"><p>✅ Nada que repasar hoy.</p></div>'}
    ${due.map((t) => `<div class="card soft"><p><b>${esc(t.name)}</b></p>${t.subject ? `<p class="muted">${esc(t.subject)}</p>` : ''}
      <p class="muted">Repaso nº ${t.stage + 1}. Primero intenta recordarlo sin mirar.</p>
      <div class="row"><button class="btn" onclick="review(${t.id},true)">Lo recordaba 👍</button><button class="btn outline" style="color:var(--accent)" onclick="review(${t.id},false)">Lo olvidé</button></div></div>`).join('')}
    ${later.length ? `<p><b>Próximos repasos</b></p>${later.map((t) => `<p class="muted">• ${esc(t.name)} — ${fmtDate(t.nextDue)}</p>`).join('')}` : ''}
    <div class="card"><input type="text" id="tn" placeholder="Tema (p. ej. Normalización 3FN)"><input type="text" id="ts" placeholder="Asignatura (opcional)">
      <button class="btn" id="addT">+ Añadir tema aprendido</button>
      <button class="btn outline" onclick="S.topics.forEach(t=>t.nextDue=0);save();go('planner')">(Prueba) Hacer que todos toquen hoy</button></div>`;
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
    <div class="card"><p><b>Tiempo de estudio</b></p><p>Hoy: ${fmtMin(sum(today0))}</p><p>Últimos 7 días: ${fmtMin(sum(today0 - 6 * DAY))}</p>
      <p>Últimos 30 días: ${fmtMin(sum(today0 - 29 * DAY))}</p><p>Racha: ${streak()} días seguidos 🔥</p>
      <p class="muted">Sesiones completas: ${done} · interrumpidas: ${L.length - done}</p></div>
    <div class="card"><p><b>Últimos 7 días</b></p><div class="bars">${days.map(([l, m], i) => `<div class="${i === 6 ? 'today' : ''}">${m ? (m >= 60 ? `${Math.floor(m / 60)}h${m % 60}` : `${m}'`) : ''}<i style="height:${m / max * 100}%"></i>${l}</div>`).join('')}</div></div>
    <div class="card"><p><b>Tus técnicas</b></p>${L.length ? '' : '<p class="muted">Aún no hay sesiones. ¡Empieza una!</p>'}
      ${Object.entries(byTech).map(([k, l]) => `<p>• ${esc(tech(k).name)}: ${l.length} sesiones, ${fmtMin(l.reduce((a, s) => a + s.minutes, 0))}${rated[k] != null ? ` · concentración ${rated[k].toFixed(1)}/5` : ''}</p>`).join('')}
      ${best && Object.keys(rated).length >= 2 ? `<p class="tag" style="margin-top:8px">⭐ La que mejor te funciona: ${esc(tech(best[0]).name)}</p>` : ''}</div>
    ${st.length ? `<div class="card"><p><b>Estrés y ánimo</b></p><p>Estrés medio reciente: ${avg(st.slice(-5)).toFixed(1)}/5</p></div>` : ''}`;
};

SCREENS.settings = () => {
  app.innerHTML = `<button class="back" onclick="go('home')">‹ Volver</button><h1>Ajustes</h1>
    <h2>🔐 Permisos</h2><p class="muted">En la app real aquí activas: notificaciones, bloqueo de apps (accesibilidad), llamadas a pantalla completa, alarmas exactas y batería sin restricción.</p>
    <h2>✅ Apps permitidas mientras estudias</h2><p class="muted">En la app real eliges de la lista de tus apps (lector de PDF, calculadora…).</p>
    <h2>⏰ Arranque automático por horario</h2><p class="muted">A la hora que elijas, StudyFit te «llama» para empezar.</p>
    <button class="btn outline" onclick="call('start_demo')">Probar la llamada de inicio</button>
    <h2>💚 Recordatorios de salud</h2>
    <label class="sw">Vista 20-20-20 cada 20 min (durante el estudio)<input type="checkbox" ${S.eyesOn ? 'checked' : ''} onchange="S.eyesOn=this.checked;save()"></label>
    <label class="sw">Postura cada 30 min (durante el estudio)<input type="checkbox" ${S.postureOn ? 'checked' : ''} onchange="S.postureOn=this.checked;save()"></label>
    <p class="muted">Objetivo de agua: ${S.waterGoal} vasos/día</p>
    <div class="row"><button class="btn outline" onclick="S.waterGoal=Math.max(4,S.waterGoal-1);save();go('settings')">−</button><button class="btn outline" onclick="S.waterGoal=Math.min(14,S.waterGoal+1);save();go('settings')">+</button></div>
    <h2>🌙 Sueño</h2>
    <label class="sw">Avisarme de la hora de dormir<input type="checkbox" ${S.sleepOn ? 'checked' : ''} onchange="S.sleepOn=this.checked;save()"></label>
    <input type="time" value="${String(S.bedHour).padStart(2, '0')}:${String(S.bedMinute).padStart(2, '0')}" onchange="const [h,m]=this.value.split(':');S.bedHour=+h;S.bedMinute=+m;save()">
    <p class="muted">Te aviso 30 min antes y a la hora. Si intentas estudiar más tarde, te lo recordaré.</p>`;
};

// Llamada de inicio por horario (solo para probar)
const _call = SCREENS.call;
SCREENS.call = () => {
  if (callState.type !== 'start_demo') return _call();
  app.className = 'screen dark';
  app.innerHTML = `<div class="ring"><p class="muted">StudyFit te llama…</p><div class="emoji">📖</div><h1>Es tu hora de estudiar</h1>
    <p class="muted" style="margin-bottom:60px">Siéntate, apoya el móvil y empezamos</p>
    <button class="btn green big" onclick="stopRing();go('start')">✅&nbsp; Aceptar</button><button class="btn outline" onclick="stopRing();go('settings')">Ahora no</button></div>`;
  startRing();
};

function dialog(title, msg, buttons) {
  const bg = document.createElement('div'); bg.className = 'dialog-bg';
  bg.innerHTML = `<div class="dialog"><h2 style="margin-top:0">${esc(title)}</h2><p>${esc(msg)}</p><div class="btns"></div></div>`;
  buttons.forEach(([label, fn], i) => { const b = document.createElement('button'); b.className = 'btn' + (i ? ' outline' : ''); b.textContent = label;
    b.onclick = () => { bg.remove(); fn && fn(); }; bg.querySelector('.btns').appendChild(b); });
  $('.phone').appendChild(bg);
}

function askMood() {
  const mins = Math.round(S.studiedMs / MIN());
  let focus = 3, stress = 2;
  const bg = document.createElement('div'); bg.className = 'dialog-bg';
  bg.innerHTML = `<div class="dialog"><h2 style="margin-top:0">¿Cómo ha ido?</h2>
    <p>${S.pendingCompleted ? `¡Sesión completada! Has estudiado ${fmtMin(mins)}. 🎉` : `Sesión interrumpida (${fmtMin(mins)} estudiados).`}</p>
    <h2>¿Qué tal tu concentración?</h2>${chips(['😵', '😕', '😐', '🙂', '🤩'].map((e, i) => [String(i + 1), e]), '3', 5, 'moodF')}
    <h2>¿Nivel de estrés?</h2>${chips(['😌', '🙂', '😐', '😟', '😫'].map((e, i) => [String(i + 1), e]), '2', 5, 'moodS')}
    <button class="btn" id="mSave">Guardar</button><button class="btn outline" id="mSkip">Saltar</button></div>`;
  $('.phone').appendChild(bg);
  window.moodF = (el) => { focus = +selectChip(el); };
  window.moodS = (el) => { stress = +selectChip(el); };
  bg.querySelector('#mSave').onclick = () => { bg.remove(); saveSession(focus, stress);
    if (stress >= 4) dialog('💚 Un momento para ti', DATA.stress.join('\n\n'), [['Gracias', () => go('home')]]); else go('home'); };
  bg.querySelector('#mSkip').onclick = () => { bg.remove(); saveSession(0, 0); go('home'); };
}

go(inSession() ? 'session' : 'home');
