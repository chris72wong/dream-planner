'use strict';
const GOAL_STORAGE='north.goals.v1';
const goalNames={retirement:'Retirement',house:'House',vacation:'Vacation',car:'Car',other:'Something else'};
let selectedGoals=[];
let activeView='home';
try {
  const saved=JSON.parse(localStorage.getItem(GOAL_STORAGE));
  if(Array.isArray(saved))selectedGoals=Object.keys(goalNames).filter(key=>saved.includes(key));
} catch { /* The selector still works when storage is unavailable. */ }

function allowedViews(){
  return ['home','advisor','timeline','analysis',...selectedGoals.map(key=>({retirement:'overview',house:'goals'})[key]||key),
    ...(selectedGoals.includes('retirement')?['scenarios']:[])];
}
const originalView=view;
view=function(name){
  activeView=allowedViews().includes(name)?name:'home';
  originalView(activeView);
  text('view-label',({home:'Home',overview:'Retirement',goals:'House',accounts:'Accounts',scenarios:'Scenarios',analysis:'Analysis',timeline:'Details',...goalNames})[activeView]);
  $('#status-bar').hidden=!['overview','goals','scenarios'].includes(activeView);
  $('.top-actions').hidden=!['overview','goals','scenarios'].includes(activeView);
  text('edit-plan',activeView==='goals'?'Edit house goal':'Edit plan');
  $('#save-house').hidden=activeView!=='goals';
};
function filterWorkspace(){
  document.querySelectorAll('[data-view]').forEach(button=>button.hidden=!allowedViews().includes(button.dataset.view));
  consolidateAccounts();
  document.querySelectorAll('.account-mini').forEach(card=>{if(card.querySelector('.fhsa'))card.hidden=!selectedGoals.includes('house');});
  document.querySelectorAll('.goal-home').forEach(card=>card.hidden=!selectedGoals.includes('house'));
  document.querySelectorAll('#goal-cards > article:not(.goal-home)').forEach(card=>card.hidden=true);
  if(!allowedViews().includes(activeView))view('home');
}
function consolidateAccounts(){
  const cards=Array.from(document.querySelectorAll('#account-cards .account-detail'));
  if(!cards.length)return;
  const expanded=new Map(Array.from(document.querySelectorAll('.account-row'),row=>[
    row.querySelector('[data-account]').dataset.account,
    {open:row.open,sections:Array.from(row.querySelectorAll('.account-detail > details'),section=>section.open)}
  ]));
  $('#retirement-accounts').replaceChildren();
  $('#house-accounts').replaceChildren();
  for(const card of cards){
    const key=card.querySelector('[data-account]').dataset.account;
    const row=document.createElement('details');row.className='account-row';
    row.open=expanded.get(key)?.open||false;
    card.querySelectorAll(':scope > details').forEach((section,index)=>section.open=expanded.get(key)?.sections[index]||false);
    const summary=document.createElement('summary');
    const label=document.createElement('span');label.textContent=card.querySelector('.account-title h2').textContent;
    const room=document.createElement('strong');room.textContent=card.querySelector('.account-room strong').textContent;
    summary.append(label,room);row.append(summary,card);
    (key==='fhsa'?$('#house-accounts'):$('#retirement-accounts')).append(row);
  }
}
$('#goal-filter').addEventListener('change',()=>{
  $('#goals-continue').disabled=!$('#goal-filter input:checked');
});
$('#goal-filter').addEventListener('submit',event=>{
  event.preventDefault();
  selectedGoals=Array.from(document.querySelectorAll('#goal-filter input:checked'),input=>input.value);
  if(!selectedGoals.length)return;
  try{localStorage.setItem(GOAL_STORAGE,JSON.stringify(selectedGoals));text('goal-filter-status','');}
  catch{text('goal-filter-status','Device storage unavailable. Choices apply to this session.');}
  filterWorkspace();
  view(({retirement:'overview',house:'goals'})[selectedGoals[0]]||selectedGoals[0]);
  $('#main').focus();
});
$('#main').setAttribute('tabindex','-1');
document.querySelectorAll('#goal-filter input').forEach(input=>input.checked=selectedGoals.includes(input.value));
$('#goals-continue').disabled=!selectedGoals.length;

// Each short-term goal has its own inputs and uses the existing savings engine.
for(const key of ['vacation','car','other']){
  const section=document.createElement('section');section.id='view-'+key;section.className='view';section.hidden=true;
  section.setAttribute('aria-label',goalNames[key]);
  section.innerHTML=`<div class="section-heading"><h2>${goalNames[key]}</h2></div><form class="card savings-goal" id="${key}-goal-form"><div class="form-grid">${key==='other'?'<div class="field full-width"><label for="other-name">Goal name</label><input id="other-name" name="goalName" maxlength="80" placeholder="e.g. Education" required></div>':''}${[
    ['targetToday','Savings target (CAD)',10000,0,1e9,'.01'],['currentSavings','Saved so far (CAD)',0,0,1e9,'.01'],['monthlyContribution','Monthly saving (CAD)',200,0,1e9,'.01'],['years','Years until goal',2,1,30,1],['annualReturnRate','Annual return (%)',0,-99,100,'.01'],['annualInflationRate','Inflation (%)',0,-99,100,'.01']
  ].map(([name,label,value,min,max,step])=>`<div class="field"><label for="${key}-${name}">${label}</label><input id="${key}-${name}" name="${name}" type="number" value="${value}" min="${min}" max="${max}" step="${step}" required></div>`).join('')}</div><button class="primary-button">Calculate</button><div class="savings-result" role="status" aria-live="polite"></div><p class="subtle">CAD · fixed returns · before tax</p></form>`;
  $('#main').append(section);
  const form=section.querySelector('form'),output=form.querySelector('.savings-result'),storage='north.savings.'+key+'.v1';
  try{const saved=JSON.parse(localStorage.getItem(storage));if(saved)for(const input of form.querySelectorAll('input'))if(Object.hasOwn(saved,input.name))input.value=saved[input.name];}catch{}
  form.addEventListener('input',()=>output.replaceChildren());
  form.addEventListener('submit',async event=>{
    event.preventDefault();const values=Object.fromEntries(new FormData(form)),fingerprint=JSON.stringify(values),button=form.querySelector('button');
    const request=Object.fromEntries(Object.entries(values).filter(([key])=>key!=='goalName').map(([key,value])=>[key,Number(value)]));
    request.annualReturnRate/=100;request.annualInflationRate/=100;
    button.disabled=true;output.textContent='Calculating…';
    try{
      const result=await post('home',request);
      if(fingerprint!==JSON.stringify(Object.fromEntries(new FormData(form))))return;
      output.innerHTML=`<dl class="analysis-stats"><div><dt>Projected savings</dt><dd>${money.format(result.projection.finalBalance)}</dd></div><div><dt>Monthly saving needed</dt><dd>${money.format(result.requiredMonthlyContribution)}</dd></div></dl><p class="subtle">Target at goal date: ${money.format(result.nominalTarget)}</p>`;
      try{localStorage.setItem(storage,JSON.stringify(values));}catch{output.insertAdjacentHTML('beforeend','<p class="subtle">Device storage unavailable. Inputs apply to this session.</p>');}
    }catch(error){output.textContent=error.message;}finally{button.disabled=false;}
  });
}

// Keep the editor focused on the selected goals while retaining existing plan files.
const originalRenderWizard=renderWizard;
renderWizard=function(){
  originalRenderWizard();
  const unrelatedKeys=['fhsaBalance','homeCash','homeMonthly','homeTarget','homeYears','homeReturn','monthlyTakeHome','monthlyExpenses','emergencyCash','emergencyMonths','debtBalance','debtPayment','debtApr'];
  $('#wizard-fields').querySelectorAll('[data-field]').forEach(input=>{
    if(unrelatedKeys.includes(input.dataset.field))input.closest('.field').remove();
  });
  $('#wizard-fields').querySelectorAll('.wizard-extra').forEach(section=>{if(!section.querySelector('input,select'))section.remove();});
};
const renderBeforeFilter=render;
render=function(){renderBeforeFilter();filterWorkspace();text('result-context',JSON.stringify(calculatedPlan)===JSON.stringify(example)?'Example inputs — edit to use your own.':'Calculated from your inputs.');};

// House edits do not require the retirement questionnaire.
const houseFields=definitions.filter(f=>['fhsaBalance','homeCash','homeMonthly','homeTarget','homeYears','homeReturn','inflation'].includes(f.key));
$('#view-goals .section-heading button').textContent='Edit house goal';
const originalOpenWizard=openWizard;
openWizard=function(index=0){
  const profileOnly=index===0&&activeView==='goals';
  if(activeView!=='goals'&&!profileOnly){originalOpenWizard(index);return;}
  const existing=$('#house-dialog');if(existing)existing.remove();
  const dialog=document.createElement('dialog');dialog.id='house-dialog';dialog.setAttribute('aria-labelledby','house-editor-title');
  dialog.innerHTML=`<div class="dialog-heading"><h2 id="house-editor-title">${profileOnly?'Profile':'House savings'}</h2><button type="button" class="icon-button" aria-label="Close editor">×</button></div><form><div class="form-grid">${fieldsHtml(profileOnly?steps[0].fields:houseFields,plan,'house')}</div><p class="error" role="alert" hidden></p><button class="primary-button">Calculate</button></form>`;
  document.body.append(dialog);dialog.querySelector('.icon-button').addEventListener('click',()=>dialog.close());
  dialog.querySelector('form').addEventListener('submit',async event=>{
    event.preventDefault();const next=clone(plan);collect(dialog,next);const button=event.target.querySelector('button');button.disabled=true;
    if(await update(next))dialog.close();else{const error=dialog.querySelector('.error');error.textContent=$('#form-error').textContent;error.hidden=false;}
    button.disabled=false;
  });dialog.showModal();
};
$('#save-house').addEventListener('click',()=>$('#save-plan').click());
$('#clear-device-data').addEventListener('click',()=>{
  if(!window.confirm('Remove Dream Planner browser saves, goal choices and short-term goal inputs? Named scenarios and current unsaved work will remain.'))return;
  try{[STORAGE,GOAL_STORAGE,...['vacation','car','other'].map(key=>'north.savings.'+key+'.v1')].forEach(key=>localStorage.removeItem(key));text('save-state','Browser saves removed');toast('Dream Planner browser data removed. Named scenarios are separate.');}
  catch{toast('Browser data could not be removed. Check your browser storage settings.');}
});
filterWorkspace();
view('home');
