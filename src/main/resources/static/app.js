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
