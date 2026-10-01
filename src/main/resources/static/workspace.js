'use strict';
const workspaceFields = {
  risk:[field('volatility','Annual volatility (%)','',{max:60,step:'.1'}),ageField('runs','Simulation count',100,2000),ageField('seed','Random seed',0,2147483647)],
  tax:[field('withdrawalOrder','Withdrawal order after RRIF minimum','',{type:'select',choices:[['TFSA_FIRST','TFSA → other → RRSP'],['RRSP_FIRST','RRSP → other → TFSA'],['OTHER_FIRST','Other → RRSP → TFSA']]}),field('tfsaShare','Monthly saving to TFSA (%)','',{max:100}),field('rrspShare','Monthly saving to RRSP (%)','',{max:100})],
  household:[field('partnerBirth','Partner date of birth','',{type:'date',min:'1906-09-29',max:AS_OF}),field('partnerSavings','Partner retirement investments (CAD)'),field('partnerSaving','Partner monthly saving (CAD)'),ageField('partnerAge','Partner retirement age',1,119),field('partnerSpending','Partner monthly spending (today CAD)'),field('partnerCpp','Partner CPP at 65 (monthly CAD)'),field('partnerOas','Partner OAS at 65 (monthly CAD)')]
};
const analysisDefaults={volatility:12,runs:500,seed:2026,withdrawalOrder:'TFSA_FIRST',tfsaShare:100,rrspShare:0,partnerBirth:'1996-01-01',partnerSavings:10000,partnerSaving:500,partnerAge:65,partnerSpending:2500,partnerCpp:0,partnerOas:0};
const analysisCopies={
  risk:['How could market uncertainty change my outcome?','Before-tax simulations with independent annual returns. These are model outcomes, not forecasts.','Run simulations'],
  tax:['What could I spend after tax?','Ontario estimate with separate TFSA, RRSP/RRIF and other accounts. Annual withdrawals. This analysis treats your spending target as after-tax. Saving left after the two allocations goes to other investments. Future contribution room, age/pension credits and capital gains are excluded; other-account growth is taxable interest.','Calculate after-tax estimate'],
  household:['How do our two retirement plans compare?','Two independent plans. Your return, inflation and planning-age assumptions are shared. Enter each person’s own spending target, not the household target twice. Partner benefits use your selected start ages. No pension splitting, shared withdrawals or survivor benefits.','Compare household plans']
};
$('#analysis-sections').innerHTML=Object.entries(analysisCopies).map(([key,[title,copy,button]])=>`<details class="card analysis-section"><summary>${title}</summary><p class="subtle">${copy}</p><form id="${key}-form" class="analysis-form"><div class="form-grid">${fieldsHtml(workspaceFields[key],analysisDefaults,key)}</div><button class="primary-button">${button}</button></form><div id="${key}-output" class="analysis-output" role="status" aria-live="polite"></div></details>`).join('');
$('#view-scenarios').insertAdjacentHTML('beforeend',`<details class="card library-section" open><summary>Your saved plans</summary><p class="subtle">Named snapshots saved in this app’s database. They remain available after restarting.</p><form id="library-form" class="library-form"><label for="scenario-name">Plan name</label><input id="scenario-name" maxlength="80" placeholder="My retirement plan" required><button class="primary-button">Save current plan</button></form><p id="library-status" class="subtle" role="status"></p><div id="saved-plans"></div></details>`);
let analysisSequence=0, chatAvailable=false, chatBusy=false, chatGeneration=0, libraryPlans=[];
async function api(path,body,method=body?'POST':'GET'){
  const response=await fetch(path,{method,headers:body?{'Content-Type':'application/json'}:{},...(body?{body:JSON.stringify(body)}:{})});
  if(response.status===204)return null;
  const data=await response.json();if(!response.ok)throw new Error(data.detail||'The request could not be completed. Please try again.');return data;
}
function values(form,fields){return Object.fromEntries(fields.map(f=>{const input=form.querySelector(`[data-field="${f.key}"]`);return [f.key,f.type==='number'?Number(input.value):input.value];}));}
function stats(items){return `<dl class="analysis-stats">${items.map(([label,value])=>`<div><dt>${escape(label)}</dt><dd>${escape(value)}</dd></div>`).join('')}</dl>`;}
for(const key of Object.keys(workspaceFields))$('#'+key+'-form').addEventListener('submit',async event=>{
  event.preventDefault();if(!calculatedPlan){toast('Calculate your plan first.');return;}
  const form=event.target,button=form.querySelector('button'),v=values(form,workspaceFields[key]),snapshot=clone(calculatedPlan),token=analysisSequence;
  button.disabled=true;text(key+'-output','Calculating…');
  try{
    let body,endpoint=key;
    if(key==='risk')body={plan:retirementRequest(snapshot),volatility:v.volatility/100,simulations:v.runs,seed:v.seed};
    if(key==='tax'){
      endpoint='drawdown';if(v.tfsaShare+v.rrspShare>100)throw new Error('TFSA and RRSP allocations must total at most 100%.');
      body={plan:retirementRequest(snapshot),province:snapshot.province,tfsa:snapshot.tfsaBalance,rrsp:snapshot.rrspBalance,other:snapshot.otherBalance,tfsaContributionShare:v.tfsaShare/100,rrspContributionShare:v.rrspShare/100,strategy:v.withdrawalOrder};
    }
    if(key==='household'){
      const partner={...snapshot,birthDate:v.partnerBirth,tfsaBalance:v.partnerSavings,rrspBalance:0,otherBalance:0,monthlyContribution:v.partnerSaving,retirementAge:v.partnerAge,monthlySpending:v.partnerSpending,cppMonthlyAt65:v.partnerCpp,oasMonthlyAt65:v.partnerOas,otherMonthlyIncome:0};
      body={primary:retirementRequest(snapshot),partner:retirementRequest(partner)};
    }
    const result=await api('/api/analysis/'+endpoint,body);
    if(token!==analysisSequence||JSON.stringify(v)!==JSON.stringify(values(form,workspaceFields[key]))){text(key+'-output','Your plan changed. Run this analysis again.');return;}
    let html='';
    if(key==='risk')html=stats([['Runs without spending shortfall',result.successPercent+'%'],['Savings at retirement · 10th percentile',money.format(result.retirementP10)],['Median savings at retirement',money.format(result.retirementP50)],['Savings at retirement · 90th percentile',money.format(result.retirementP90)],['Ending balance · 10th percentile',money.format(result.endingP10)],['Median ending balance',money.format(result.endingP50)],['Ending balance · 90th percentile',money.format(result.endingP90)]]);
    if(key==='tax')html=stats([['Lifetime estimated income tax',money.format(result.totalTax)],['Lifetime OAS recovery',money.format(result.totalOasRecovery)],['Unfunded spending & tax',money.format(result.totalShortfall)],['Ending investments',money.format(result.endingBalance)]])+`<details><summary>Annual account withdrawals</summary><div class="table-scroll" tabindex="0"><table><thead><tr><th>Age at start</th><th>TFSA withdrawal</th><th>RRSP/RRIF withdrawal</th><th>Other withdrawal</th><th>RRIF minimum</th><th>Income tax</th><th>OAS recovery</th><th>Shortfall</th><th>TFSA balance</th><th>RRSP balance</th><th>Other balance</th></tr></thead><tbody>${result.years.map(y=>`<tr><td>${y.age}</td>${['tfsaWithdrawal','rrspWithdrawal','otherWithdrawal','rrifMinimum','incomeTax','oasRecovery','shortfall','tfsa','rrsp','other'].map(k=>`<td>${money.format(y[k])}</td>`).join('')}</tr>`).join('')}</tbody></table></div></details><p class="subtle"><a href="https://www.canada.ca/en/revenue-agency/services/tax/individuals/tax-rates-brackets/current-year.html" target="_blank" rel="noopener">2026 tax brackets</a> · <a href="https://www.canada.ca/en/revenue-agency/services/forms-publications/publications/ic78-18/registered-retirement-income-funds.html" target="_blank" rel="noopener">RRIF rules</a></p>`;
    if(key==='household')html=stats([['Combined monthly spending supported',money.format(result.combinedSupportedMonthlySpending)],['Your monthly spending supported',money.format(result.primary.sustainableMonthlySpending)],['Partner monthly spending supported',money.format(result.partner.sustainableMonthlySpending)],['Your first shortfall',result.primary.firstShortfallAge===null?'None in plan':'Age '+result.primary.firstShortfallAge],['Partner first shortfall',result.partner.firstShortfallAge===null?'None in plan':'Age '+result.partner.firstShortfallAge]]);
    $('#'+key+'-output').innerHTML=html+`<p class="subtle">${escape(result.assumptions)}</p>`;
  }catch(error){text(key+'-output',error.message);}finally{button.disabled=false;}
});
async function loadLibrary(){try{libraryPlans=await api('/api/scenarios');$('#saved-plans').innerHTML=libraryPlans.map(p=>`<article class="saved-plan"><div><strong>${escape(p.name)}</strong><small>${new Date(p.createdAt).toLocaleDateString('en-CA')}</small></div><div><button class="text-button" data-load-plan="${escape(p.id)}">Load</button><button class="text-button" data-remove-plan="${escape(p.id)}">Remove</button></div></article>`).join('');text('library-status',libraryPlans.length?`${libraryPlans.length} saved plan${libraryPlans.length===1?'':'s'}`:'No saved plans yet.');}catch(error){text('library-status',error.message);}}
$('#library-form').addEventListener('submit',async event=>{event.preventDefault();if(!calculatedPlan){toast('Calculate your plan first.');return;}const button=event.target.querySelector('button');button.disabled=true;try{await api('/api/scenarios',{name:$('#scenario-name').value,document:envelope()});$('#scenario-name').value='';await loadLibrary();toast('Named plan saved to the database.');}catch(error){text('library-status',error.message);}finally{button.disabled=false;}});
$('#saved-plans').addEventListener('click',async event=>{const button=event.target.closest('button');if(!button)return;button.disabled=true;try{if(button.dataset.loadPlan){const saved=libraryPlans.find(p=>p.id===button.dataset.loadPlan);if(await update(readEnvelope(saved.document))){view('overview');toast('Saved plan loaded.');}}else if(button.dataset.removePlan){await api('/api/scenarios/'+encodeURIComponent(button.dataset.removePlan),null,'DELETE');await loadLibrary();toast('Named plan removed.');}}catch(error){text('library-status',error.message);}finally{button.disabled=false;}});
function chatMessage(role,message){const node=document.createElement('article');node.className='chat-message '+role;const label=document.createElement('strong');label.textContent=role==='user'?'You':'Dream Planner';const content=document.createElement('p');content.textContent=message;node.append(label,content);$('#chat-messages').append(node);node.scrollIntoView({block:'nearest'});return node;}
async function chatStatus(){try{const status=await api('/api/chat/status');chatAvailable=status.available;text('chat-availability','Calculator explanations. No AI provider receives your information.');}catch{chatAvailable=false;text('chat-availability','Calculator explanations are currently unavailable.');}}
document.querySelectorAll('[data-open-chat]').forEach(button=>button.addEventListener('click',()=>{$('#chat-dialog').showModal();chatStatus();}));
$('#close-chat').addEventListener('click',()=>$('#chat-dialog').close());
$('#clear-chat').addEventListener('click',()=>{++chatGeneration;$('#chat-messages').replaceChildren();});
document.querySelectorAll('[data-topic]').forEach(button=>button.addEventListener('click',async()=>{
  if(chatBusy)return;const topic=button.dataset.topic,generation=chatGeneration;
  if(topic==='summary'&&!calculatedPlan){toast('Calculate your plan first.');return;}
  chatMessage('user',button.textContent);chatBusy=true;
  document.querySelectorAll('[data-topic]').forEach(b=>b.disabled=true);
  try{
    const response=await api('/api/chat',{message:topic,...(topic==='summary'?{plan:retirementRequest(clone(calculatedPlan))}:{})});
    if(generation===chatGeneration)chatMessage('assistant',response.answer);
  }catch(error){if(generation===chatGeneration)chatMessage('assistant',error.message);}
  finally{chatBusy=false;document.querySelectorAll('[data-topic]').forEach(b=>b.disabled=false);}
}));
const originalRender=render;
render=function(){originalRender();++analysisSequence;for(const key of Object.keys(workspaceFields))if($('#'+key+'-output').textContent)text(key+'-output','Your plan changed. Run this analysis again.');};
loadLibrary();
