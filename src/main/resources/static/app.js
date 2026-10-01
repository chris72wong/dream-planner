'use strict';
const $ = selector => document.querySelector(selector);
const money = new Intl.NumberFormat('en-CA', {style:'currency', currency:'CAD', maximumFractionDigits:0});
const AS_OF = '2026-09-29';
const clone = value => JSON.parse(JSON.stringify(value));
const text = (id, value) => { $('#'+id).textContent = value; };
const escape = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[char]);
let activeView = 'home';
function currentAge(inputs) {
  const [year, month, day] = inputs.birthDate.split('-').map(Number);
  const [assessmentYear, assessmentMonth, assessmentDay] = AS_OF.split('-').map(Number);
  return assessmentYear - year - (month > assessmentMonth || month === assessmentMonth && day > assessmentDay ? 1 : 0);
}
function view(name) {
  activeView = name === 'advisor' ? 'advisor' : 'home';
  document.querySelectorAll('.view').forEach(element => { element.hidden = element.id !== 'view-'+activeView; });
  document.body.classList.add('dialogue-mode');
  document.body.classList.toggle('scene-mode', activeView === 'advisor');
}
async function post(endpoint, body) {
  const response = await fetch('/api/plans/'+endpoint, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
  const data = await response.json();
  if (!response.ok) throw new Error(data.detail || 'Check your answers and try again.');
  return data;
}

// Wake the calculator on arrival/return only; never keep it alive on a timer.
(() => {
  let inFlight = false;
  let lastAttempt = -Infinity;
  let wasVisible = document.visibilityState === 'visible';

  async function wakeCalculator() {
    if (document.visibilityState !== 'visible' || inFlight || Date.now() - lastAttempt < 60000) return;
    inFlight = true;
    lastAttempt = Date.now();
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 30000);
    try {
      await fetch('/api/health', {method: 'GET', cache: 'no-store', signal: controller.signal});
    } catch {
      // Best effort only: calculation requests remain independent.
    } finally {
      clearTimeout(timeout);
      inFlight = false;
    }
  }

  document.addEventListener('visibilitychange', () => {
    const visible = document.visibilityState === 'visible';
    const returned = visible && !wasVisible;
    wasVisible = visible;
    if (returned) void wakeCalculator();
  });
  void wakeCalculator();
})();
