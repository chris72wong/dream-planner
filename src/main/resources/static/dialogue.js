'use strict';
(() => {
  const sources = {
    retirement:'https://www.canada.ca/en/revenue-agency/services/tax/individuals/educational-programs/saving-future.html',
    house:'https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/first-home-savings-account.html',
    education:'https://www.canada.ca/en/services/benefits/education/education-savings.html'
  };
  const assumptionSource='https://www.fpcanada.ca/projection-assumption-guidelines';
  // FP Canada 2026: 2.1% inflation, 2.4% short-term, 3.2% bonds, ~6.4% equities.
  // The 4.8% illustration is a derived 50/50 bond/equity blend, before tax and fees.
  const assumptions={inflation:2.1,shortTerm:2.4,balanced:4.8};
  const choice=(label,to)=>({label,to});
  const topics={
    house:{name:'Home',nodes:{
      start:{text:'Let’s see how your home savings could grow.',choices:[choice('Build my plan','calculate'),choice('About saving accounts','accounts')]},
      accounts:{text:'An FHSA can offer deductible contributions and tax-free qualifying home withdrawals. A TFSA offers tax-free growth and withdrawals. Check eligibility and contribution room.',source:true,choices:[choice('Build my plan','calculate'),choice('Back','start')]}
    }},
    retirement:{name:'Retirement',nodes:{
      start:{text:'Let’s see what your savings could support in retirement.',choices:[choice('Build my plan','calculate'),choice('About retirement accounts','accounts')]},
      accounts:{text:'RRSP contributions may reduce taxable income; withdrawals are generally taxable. TFSA growth and withdrawals are generally tax-free. Both have contribution limits.',source:true,choices:[choice('Build my plan','calculate'),choice('Back','start')]}
    }},
    education:{name:'Education',nodes:{
      start:{text:'Let’s see how much you could save for school.',choices:[choice('Build my plan','calculate'),choice('About RESPs','accounts')]},
      accounts:{text:'An RESP shelters investment growth for education and may qualify for government benefits. This calculator shows savings without grants, bonds or tax effects.',source:true,choices:[choice('Build my plan','calculate'),choice('Back','start')]}
    }}
  };
  // Original vector artwork keeps the scene crisp and requires no image runtime.
  const advisors={
    house:{name:'Amara',role:'Home advisor',location:'City terrace',skin:'#935b40',shade:'#77452f',jacket:'#243b51',hair:'#292321'},
    retirement:{name:'Daniel',role:'Retirement advisor',location:'Coastal promenade',skin:'#c29573',shade:'#9d6f50',jacket:'#53665a',hair:'#d1cbc1'},
    education:{name:'Mei',role:'Education advisor',location:'University courtyard',skin:'#d4a17f',shade:'#b58161',jacket:'#784b43',hair:'#252b30'}
  };
  function landscape(key){
    const clouds='<g class="scene-clouds" fill="#fff" opacity=".35"><path d="M130 140q20-35 55-10 30-60 85-13 45-13 60 25z"/><path d="M1050 110q20-40 65-20 30-35 65-5 40-5 45 25z"/></g>';
    const tree=(x,y,scale=1)=>`<g class="scene-tree" style="transform-origin:${x}px ${y+170*scale}px" transform="translate(${x} ${y}) scale(${scale})"><path d="M0 180V65" stroke="#665745" stroke-width="13"/><path d="m0 105-30-35m30 50 34-42" stroke="#665745" stroke-width="7"/><ellipse cy="28" rx="59" ry="73" fill="#566c53"/><ellipse cx="-36" cy="58" rx="39" ry="54" fill="#647b5e"/><ellipse cx="37" cy="55" rx="39" ry="51" fill="#708667"/></g>`;
    if(key==='house'){
      const buildings=[[0,240,150,280],[165,170,150,350],[340,230,140,290],[505,125,175,395],[710,200,145,320],[885,155,190,365],[1110,230,145,290],[1290,120,145,400],[1465,210,135,310]];
      return `<rect width="1600" height="1000" fill="url(#sky-house)"/>${clouds}<circle cx="1320" cy="165" r="55" fill="#f4dfac" opacity=".8"/><g fill="#809099" opacity=".35"><path d="M0 290h80v-80h65v-40h90v160h70V225h80v105h85V230h110v100h90V190h90v140h110V230h110v100h90V190h120v140h110V205h120v125h120V220h120v300H0z"/></g>${buildings.map(([x,y,w,h],b)=>`<g><rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${['#b9b4a8','#71828c','#9aa49e'][b%3]}"/><rect x="${x-4}" y="${y}" width="${w+8}" height="9" fill="#5d6b70"/>${Array.from({length:4},(_,row)=>Array.from({length:3},(_,col)=>`<rect x="${x+16+col*(w-30)/3}" y="${y+24+row*64}" width="${(w-55)/3}" height="42" fill="${row===2&&col===1?'#d7bd84':'#536975'}" opacity=".7"/>`).join('')).join('')}</g>`).join('')}<rect y="520" width="1600" height="110" fill="#555f61"/><path d="M0 574h1600" stroke="#d5d0bd" stroke-width="4" stroke-dasharray="50 55"/><g class="scene-car"><path d="M80 563v-24h28l20-24h60l25 24h20v24z" fill="#b0c2c2"/><path d="m135 521-14 18h70l-11-18z" fill="#46616b"/><circle cx="115" cy="564" r="12" fill="#303b3e"/><circle cx="201" cy="564" r="12" fill="#303b3e"/></g><rect y="630" width="1600" height="370" fill="#d4ccba"/><path d="M0 670h1600M0 790h1600M200 630l-90 370m300-370-40 370m690-370 50 370m200-370 90 370" stroke="#bab4a6" stroke-width="2"/>${tree(190,460,.8)}${tree(1400,435,1.1)}<path d="M0 690h400m800 0h400" stroke="#465850" stroke-width="13"/><path d="M40 630v170m100-170v170m100-170v170m100-170v170m900-170v170m100-170v170m100-170v170m100-170v170" stroke="#465850" stroke-width="8"/>`;
    }
    if(key==='retirement')return `<rect width="1600" height="1000" fill="url(#sky-retirement)"/>${clouds}<circle cx="1220" cy="200" r="72" fill="#f4deab"/><path d="M0 410q300-35 620 8 270-40 550-10 260-40 430 0v280H0z" fill="#739d9c"/><path d="M0 457q280-20 620 4 380-23 980-5" stroke="#b2ccbe" stroke-width="4" fill="none"/><g class="scene-waves" fill="none" stroke="#dce6ce" stroke-width="4" opacity=".7"><path d="M-100 512q200-17 400 0t400 0 400 0 400 0 400 0"/><path d="M-180 572q200-22 400 0t400 0 400 0 400 0 400 0"/></g><path d="M0 620q400-60 800 15t800-20v385H0z" fill="#d9cba9"/><path d="M0 699q410-64 790 11t810-27" fill="none" stroke="#eae0c6" stroke-width="30"/><path d="M0 824q450-120 1600-23v199H0z" fill="#b3a790"/><path d="M0 858q560-103 1600-16" stroke="#d4c6a9" stroke-width="4" fill="none"/><g class="scene-palm" style="transform-origin:230px 760px"><path d="M230 760q15-200-22-366" stroke="#887253" stroke-width="22" fill="none"/><path d="M208 394q-123-87-185 24 100-49 185-24m0 0q-70-142-143-96 87 35 143 96m0 0q48-140 124-103-87 52-124 103m0 0q139-71 199 38-112-64-199-38m0 0q-83 5-111 108 67-56 111-108" fill="#526d55"/></g><g transform="translate(1420 470) scale(.6)" class="scene-sailboat"><path d="M-70 45h130l-25 25h-85z" fill="#5b625d"/><path d="M0-110V40H-60z" fill="#eee8d7"/><path d="M8-75v113h49z" fill="#ddd4bb"/><path d="M0-115V50" stroke="#5f6964" stroke-width="4"/></g><g class="scene-birds" fill="none" stroke="#576c6d" stroke-width="3"><path d="M590 240q13-13 26 0 13-13 26 0m33-38q10-10 20 0 10-10 20 0"/></g>`;
    return `<rect width="1600" height="1000" fill="url(#sky-education)"/>${clouds}<rect y="500" width="1600" height="500" fill="#9baf8d"/><g fill="#b9a490" stroke="#a08b78" stroke-width="2"><rect x="80" y="240" width="430" height="325"/><rect x="1090" y="240" width="430" height="325"/><rect x="510" y="180" width="580" height="385"/></g><path d="m460 195 340-115 340 115z" fill="#59666a"/><path d="m475 205 325-105 325 105z" fill="#d7cabb"/><path d="M55 240h480m530 0h480M510 260h580M510 470h580" stroke="#e6dccc" stroke-width="14"/><circle cx="800" cy="161" r="28" fill="#f4ead8" stroke="#827e6e" stroke-width="5"/><path d="M800 143v19l13 8" stroke="#66695f" stroke-width="3" fill="none"/>${[140,255,370,1140,1255,1370].map(x=>`<path d="M${x} 330a30 30 0 0 1 60 0v67h-60zM${x} 459a30 30 0 0 1 60 0v60h-60z" fill="#506776"/><path d="M${x+30} 301v96m-30-43h60" stroke="#b9b6a4" stroke-width="3"/>`).join('')}<path d="M745 565V386a55 55 0 0 1 110 0v179z" fill="#485a61"/>${[580,665,915,1000].map(x=>`<rect x="${x}" y="271" width="26" height="285" fill="#e7dfce"/><rect x="${x-8}" y="270" width="42" height="14" fill="#d0c3af"/>`).join('')}<path d="M470 565h660v20H470zm-20 20h700v20H450zm-20 20h740v20H430z" fill="#a69e8f"/><path d="M640 625h320l410 375H230z" fill="#c6bda9"/><path d="M530 740h540m-650 110h760" stroke="#b5ac97" stroke-width="2"/>${tree(220,420,1.3)}${tree(1390,420,1.3)}<g class="scene-leaves" fill="#b19a62"><path d="m450 600 12-20 5 27-12 5zm700-150 10-20 8 20-8 9zm120 110 17-15-3 22-12 3z"/></g><path d="M110 766h210m940 0h210M130 795h170m980 0h170" stroke="#5b6a5a" stroke-width="12"/><path d="M140 770v80m150-80v80m1000-80v80m150-80v80" stroke="#5b6a5a" stroke-width="7"/>`;
  }
  function character(key){
    const a=advisors[key],older=key==='retirement',amara=key==='house';
    const hair=amara?'<g fill="#292321"><circle cx="-63" cy="-190" r="46"/><circle cx="-35" cy="-218" r="43"/><circle cx="12" cy="-228" r="43"/><circle cx="58" cy="-202" r="44"/><circle cx="85" cy="-164" r="34"/><circle cx="-87" cy="-156" r="35"/><circle cx="9" cy="-273" r="44"/></g>':older?'<path d="M-88-126q-25-102 30-130 52-23 109 4 45 22 40 94l-20-38q-30-18-55-38-32 28-82 22z" fill="#d1cbc1"/>':'<path d="M-96-97v-85q-3-95 94-95 98 0 98 96v117l-39 30-6-133-98-13-13 143z" fill="#252b30"/>';
    return `<g class="advisor-character" transform="translate(800 470)"><ellipse cx="0" cy="340" rx="180" ry="24" fill="#25362e" opacity=".13"/><g class="advisor-body"><path d="M-188 350v-143q0-116 131-133h113q132 16 132 133v143z" fill="${a.jacket}"/><path d="m-52 84 52 132 55-132" fill="#ede8dc"/><path d="m-55 90-28 62 42 18-11 43 52 74m55-197 28 62-42 18 11 43-52 74" fill="none" stroke="${older?'#778576':amara?'#456077':'#98675b'}" stroke-width="5"/><path d="M-29-14v115q29 31 58 0V-14" fill="${a.shade}"/><path d="M-183 211q-13 92 45 113l50-37-38-80" fill="${a.jacket}"/><g class="advisor-hand"><path d="m126 209 29 53-89-20q-18-29-30-10-7 22 15 42l100 51q55-11 38-74l-13-48" fill="${a.jacket}"/><path d="m70 249-15-16q-23-9-28 9-4 13 10 22l24 9z" fill="${a.skin}"/></g>${older?'<path d="m-7 183 7 34 9-34-9-13z" fill="#a7976b"/>':'<circle cx="0" cy="175" r="6" fill="#bba478"/>'}</g><g class="advisor-head"><ellipse cx="-89" cy="-112" rx="15" ry="25" fill="${a.skin}"/><ellipse cx="89" cy="-112" rx="15" ry="25" fill="${a.skin}"/><path d="M-84-164q0-83 84-83 85 0 85 83v71q-3 76-85 105-80-25-84-104z" fill="${a.skin}"/>${hair}<path d="M-63-143q18-10 35-2m56 0q18-9 36 2" stroke="${a.hair}" stroke-width="6" stroke-linecap="round" fill="none"/><g class="advisor-eyes"><ellipse cx="-44" cy="-122" rx="7" ry="9" fill="#263033"/><ellipse cx="44" cy="-122" rx="7" ry="9" fill="#263033"/><circle cx="-42" cy="-125" r="2" fill="#f4eee2"/><circle cx="46" cy="-125" r="2" fill="#f4eee2"/></g><path d="m-1-114-8 28 16 2" fill="none" stroke="${a.shade}" stroke-width="4" stroke-linecap="round"/><path class="advisor-mouth-rest" d="M-22-57q23 18 45 0" fill="none" stroke="#653f36" stroke-width="5" stroke-linecap="round"/><g class="advisor-mouth-speaking"><ellipse cx="0" cy="-52" rx="20" ry="11" fill="#633d36"/><path d="M-15-58h30" stroke="#eadfd1" stroke-width="4"/></g>${older?'<g stroke="#4c5e60" stroke-width="4" fill="none"><rect x="-73" y="-139" width="58" height="35" rx="12"/><rect x="15" y="-139" width="58" height="35" rx="12"/><path d="M-15-126q15-9 30 0m-88 0-15-5m161 5 15-5"/></g><path d="M-68-77l16 4m105-4 16-4M-21-14q21 8 42 0" stroke="#a4795d" stroke-width="2" fill="none"/>':amara?'<circle cx="-89" cy="-91" r="9" fill="none" stroke="#d7b675" stroke-width="4"/><circle cx="89" cy="-91" r="9" fill="none" stroke="#d7b675" stroke-width="4"/>':'<path d="M-80-197q45-9 80-40 18 38 80 49l-8-61-84-20-72 29z" fill="#252b30"/><circle cx="-89" cy="-91" r="5" fill="#d5b88b"/><circle cx="89" cy="-91" r="5" fill="#d5b88b"/>'}</g></g>`;
  }
  function artwork(key, small=false) {
    const a=advisors[key],id=`${small?'card':'stage'}-${key}`;
    return `<svg viewBox="0 0 1600 1000" preserveAspectRatio="xMidYMid slice" ${small?'aria-hidden="true"':`role="img" aria-label="${a.name}, your ${a.role.toLowerCase()}, in a ${a.location.toLowerCase()}"`} xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="${id}" x2="0" y2="1"><stop stop-color="${key==='house'?'#b2c8cb':key==='retirement'?'#a8cbc5':'#b9c8bf'}"/><stop offset="1" stop-color="#efe6d1"/></linearGradient></defs>${landscape(key).replaceAll('url(#sky-'+key+')',`url(#${id})`)}${character(key)}</svg>`;
  }
  $('#summit-welcome').innerHTML='<h1>Welcome to <span>Dream Planner</span></h1><p class="welcome-question">What would you like to plan for?</p><div class="topic-cards">'+Object.entries(topics).map(([key,t])=>'<button class="topic-card" data-topic-entry="'+key+'">'+artwork(key,true)+'<span class="topic-card-copy"><strong>'+t.name+'</strong></span></button>').join('')+'</div>';
  const scene=document.createElement('section');
  scene.id='view-advisor';scene.className='view';scene.hidden=true;scene.setAttribute('aria-label','Dream Planner advisor conversation');
  scene.innerHTML='<div class="advisor-layout"><div class="bank-scene" id="bank-art"></div><nav class="scene-navigation" aria-label="Conversation controls"><div class="scene-navigation-left"><button id="dialogue-home">← Topics</button><span class="scene-brand">Dream Planner</span></div><div class="scene-navigation-right"><button id="dialogue-motion">Pause animation</button><button id="dialogue-about">About & privacy</button></div></nav><div class="conversation-panel"><div class="speaker-bar"><span><strong id="advisor-name"></strong><span id="advisor-role"></span></span><button id="dialogue-back">← Back</button></div><div id="dialogue-content"></div></div></div>';
  $('#main').append(scene);
  const about=document.createElement('dialog');
  about.id='scene-about';about.setAttribute('aria-labelledby','scene-about-title');
  about.innerHTML='<div class="dialog-heading"><h2 id="scene-about-title">About Dream Planner</h2><button class="icon-button" aria-label="Close about Dream Planner">×</button></div><p>Virtual educational guides, with illustrative calculations in CAD. Results are not financial advice or guarantees.</p><p>Your answers go to our server for calculations. No AI provider receives them. Plans are not saved in a server database. Save on this device keeps answers in this browser; shared-device users may see them.</p><p>Tax and fees are excluded. Education estimates exclude RESP grants and bonds. Home estimates cover savings, not mortgage affordability. Verify account eligibility and contribution room separately.</p><button class="text-button" id="clear-device-data">Remove saved browser plans</button><p id="device-status" role="status"></p><p class="site-credit">© 2026 Christopher Wong.</p>';
  document.body.append(about);
  $('#dialogue-about').addEventListener('click',()=>about.showModal());
  about.querySelector('.icon-button').addEventListener('click',()=>about.close());
  const motionPreference=window.matchMedia('(prefers-reduced-motion: reduce)');
  let motionPaused=motionPreference.matches;
  function applyMotion(){scene.classList.toggle('motion-paused',motionPaused);scene.classList.toggle('motion-enabled',!motionPaused);text('dialogue-motion',motionPaused?'Resume animation':'Pause animation');$('#dialogue-motion').setAttribute('aria-pressed',String(motionPaused));}
  $('#dialogue-motion').addEventListener('click',()=>{motionPaused=!motionPaused;applyMotion();});
  motionPreference.addEventListener('change',event=>{motionPaused=event.matches;applyMotion();});applyMotion();
  let topic=null,node='start',history=[],stepIndex=null,pending=0;
  const sessions={};
  const STORAGE='dream-planner.advisor.v1';
  const savingsFields=[
    {key:'targetToday',label:'What is your savings target?',detail:'Savings target · today’s dollars',help:'Include the costs you want to cover, in CAD.',min:0,max:1e9,value:50000},
    {key:'years',label:'How many years until your goal?',detail:'Years to your goal',help:'From 1 to 30 years.',min:1,max:30,value:5,integer:true},
    {key:'currentSavings',label:'How much have you saved?',detail:'Already saved',help:'Savings set aside for this goal, in CAD.',min:0,max:1e9,value:0},
    {key:'monthlyContribution',label:'How much can you save each month?',detail:'Monthly savings',help:'A monthly amount in CAD.',min:0,max:1e9,value:300}
  ];
  const retirementFields=[
    {key:'birthDate',label:'What is your date of birth?',detail:'Date of birth',help:'Used to calculate your age as of September 29, 2026.',type:'date',min:'1906-09-29',max:AS_OF,value:'1996-01-01'},
    {key:'retirementAge',label:'When would you like to retire?',detail:'Retirement age',help:'Enter an age after your current age.',min:1,max:119,integer:true,value:65},
    {key:'currentSavings',label:'How much have you saved for retirement?',detail:'Already saved',help:'Your retirement investments, in CAD.',min:0,max:1e9,value:10000},
    {key:'monthlyContribution',label:'How much can you save each month?',detail:'Monthly savings',help:'Monthly retirement savings, in CAD.',min:0,max:1e9,value:500},
    {key:'monthlySpending',label:'How much would you spend each month in retirement?',detail:'Monthly spending · today’s dollars',help:'Estimate your spending in today’s CAD, before tax.',min:0,max:1e9,value:3500}
  ];
  function fields(key=topic){return key==='retirement'?retirementFields:savingsFields;}
  function seed(key){return Object.fromEntries(fields(key).map(f=>[f.key,key==='education'&&f.key==='targetToday'?30000:key==='education'&&f.key==='years'?10:f.value]));}
  function savedInputs(key){
    try{
      const saved=JSON.parse(localStorage.getItem(STORAGE)||'{}')[key],values=seed(key);
      for(const f of fields(key)){
        const v=saved?.[f.key];
        if(f.type==='date'){
          if(typeof v==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(v)&&Number.isFinite(Date.parse(v))&&new Date(v).toISOString().slice(0,10)===v&&v>=f.min&&v<=f.max)values[f.key]=v;
        }else if(typeof v==='number'&&Number.isFinite(v)&&v>=f.min&&v<=f.max&&(!f.integer||Number.isInteger(v)))values[f.key]=v;
      }
      return values;
    }catch{return seed(key);}
  }
  function illustrationInputs(key,answers){
    const ret=key==='retirement',growth=ret||answers.years>=10?assumptions.balanced:assumptions.shortTerm;
    return {...answers,annualInflationRate:assumptions.inflation,annualReturnRate:growth,...(ret?{retirementReturnRate:assumptions.balanced,planningAge:Math.max(95,answers.retirementAge+1)}:{})};
  }
  function enter(key){
    if(!topics[key])return;
    pending++;topic=key;node='start';history=[];stepIndex=null;
    sessions[key]??={inputs:savedInputs(key),result:null};
    scene.dataset.topic=key;$('#bank-art').innerHTML=artwork(key);
    text('advisor-name',advisors[key].name);text('advisor-role',advisors[key].role);
    view('advisor');renderConversation();
  }
  function go(to){
    if(to!=='calculate'&&!topics[topic].nodes[to])return;
    pending++;history.push({node,stepIndex});node=to;stepIndex=to==='calculate'?0:null;renderConversation();
  }
  function focusContent(selector){
    const content=$('#dialogue-content');content.querySelector(selector)?.focus({preventScroll:true});content.parentElement.scrollTop=0;
  }
  function renderConversation(){
    $('#dialogue-back').disabled=!history.length&&stepIndex===null;
    if(node==='calculate'){renderInput();return;}
    if(node==='result'){renderResult();return;}
    const n=topics[topic].nodes[node];
    $('#dialogue-content').innerHTML='<h1 class="advisor-dialogue" tabindex="-1">'+escape(n.text)+'</h1>'+(n.source?'<a class="dialogue-source" href="'+sources[topic]+'" target="_blank" rel="noopener">Canadian government guidance ↗</a>':'')+'<div class="dialogue-choices">'+n.choices.map(c=>'<button data-dialogue-choice="'+c.to+'">'+escape(c.label)+'<span aria-hidden="true">→</span></button>').join('')+'</div>';
    focusContent('h1');
  }
  function renderInput(){
    const f=fields()[stepIndex],value=sessions[topic].inputs[f.key];
    $('#dialogue-back').disabled=false;
    $('#dialogue-content').innerHTML='<p class="question-progress">'+(stepIndex+1)+' of '+fields().length+'</p><h1 class="advisor-dialogue">'+f.label+'</h1><form id="dialogue-input-form"><label class="sr-only" for="dialogue-answer">'+f.label+'</label><p id="answer-help">'+f.help+'</p><input id="dialogue-answer" name="answer" type="'+(f.type||'number')+'" min="'+f.min+'" max="'+f.max+'" '+(f.type?'':'step="'+(f.integer?1:'.01')+'"')+' value="'+escape(value)+'" aria-describedby="answer-help" required><p class="dialogue-error" role="alert" id="dialogue-error"></p><button class="primary-button" type="submit">'+(stepIndex===fields().length-1?'See my plan':'Continue →')+'</button></form>';
    focusContent('input');
  }
  function requestFor(key,inputs){
    if(key!=='retirement')return {...inputs,annualReturnRate:inputs.annualReturnRate/100,annualInflationRate:inputs.annualInflationRate/100};
    return {savings:{currentAge:currentAge(inputs),retirementAge:inputs.retirementAge,currentSavings:inputs.currentSavings,monthlyContribution:inputs.monthlyContribution,annualReturnRate:inputs.annualReturnRate/100,annualInflationRate:inputs.annualInflationRate/100},planningAge:inputs.planningAge,monthlySpending:inputs.monthlySpending,retirementReturnRate:inputs.retirementReturnRate/100,cppMonthlyAt65:0,cppStartAge:65,oasMonthlyAt65:0,oasStartAge:65,otherMonthlyIncome:0,otherIncomeStartAge:65};
  }
  async function calculateConversation(){
    const key=topic,token=++pending,inputs=illustrationInputs(key,clone(sessions[key].inputs)),button=$('#dialogue-input-form button');
    button.disabled=true;button.textContent='Calculating…';
    try{
      const result=await post(key==='retirement'?'retirement':'home',requestFor(key,inputs));
      if(token!==pending||topic!==key||activeView!=='advisor')return;
      sessions[key].result=result;sessions[key].calculatedInputs=inputs;
      history.push({node:'calculate',stepIndex});node='result';stepIndex=null;renderConversation();
    }catch(error){
      if(token===pending){
        text('dialogue-error',error.message==='Failed to fetch'?'Couldn’t reach the calculator. Your answers are still here. Please try again.':error.message);
        button.disabled=false;button.textContent='Try again';
      }
    }
  }
  function metric(label,value){return '<div><dt>'+escape(label)+'</dt><dd>'+money.format(value)+'</dd></div>';}
  function timeline(r,ret){
    const savings=ret?r.savings:r.projection;
    const rows=savings.annualBreakdown.map(year=>'<tr><td>'+(ret?year.ageAtYearEnd:year.projectionYear)+'</td><td>Saving</td><td>'+money.format(year.endingBalance)+'</td><td>'+money.format(year.inflationAdjustedEndingBalance)+'</td></tr>').join('');
    const retirement=ret?r.retirementYears.map(year=>'<tr><td>'+year.ageAtYearEnd+'</td><td>Retired</td><td>'+money.format(year.endingBalance)+'</td><td>'+money.format(year.inflationAdjustedEndingBalance)+'</td></tr>').join(''):'';
    return '<div class="plan-table-scroll" tabindex="0" role="region" aria-label="Annual savings balances"><table class="plan-table"><caption>Annual balances in CAD</caption><thead><tr><th scope="col">'+(ret?'Age':'Year')+'</th><th scope="col">Stage</th><th scope="col">Future dollars</th><th scope="col">Today’s dollars</th></tr></thead><tbody>'+rows+retirement+'</tbody></table></div>';
  }
  function detailedPlan(key,i,r){
    const ret=key==='retirement',p=ret?r.savings:r.projection;
    const inputList=fields(key).map(f=>'<div><dt>'+f.detail+'</dt><dd>'+escape(f.type?i[f.key]:['years','retirementAge'].includes(f.key)?i[f.key]:money.format(i[f.key]))+'</dd></div>').join('');
    const extra=ret?metric('Savings at retirement · today’s dollars',p.inflationAdjustedFinalBalance)+metric('Monthly savings needed for your spending target',r.requiredMonthlyContribution)+metric('Balance at age '+i.planningAge+' · future dollars',r.endingBalance):metric('Target · future dollars',r.nominalTarget)+metric('Monthly savings needed',r.requiredMonthlyContribution)+metric('Investment growth · future dollars',p.totalInvestmentGrowth);
    const limitations=ret?'Plans through age '+i.planningAge+'. CPP, OAS and other pension income are excluded. '+(r.firstShortfallAge===null?'No modeled spending shortfall within this horizon.':'First modeled spending shortfall: age '+r.firstShortfallAge+'.'):key==='education'?'RESP grants, bonds and tax effects are excluded. This does not assess RESP eligibility or contribution room.':'This is a savings goal, not a mortgage affordability assessment. Home costs may grow faster than general inflation.';
    return '<details class="plan-details"><summary>View detailed plan</summary><div class="plan-detail-body"><h2>Your numbers</h2><dl class="plan-inputs">'+inputList+'</dl><h2>Your projection</h2><dl class="dialogue-results detail-metrics">'+extra+'</dl><p class="result-assumptions">'+limitations+'</p><details class="plan-timeline"><summary>Year-by-year balances</summary>'+timeline(r,ret)+'</details><h2>Assumptions</h2><p class="result-assumptions">'+i.annualInflationRate+'% inflation · '+i.annualReturnRate+'% annual growth'+(ret?' before and during retirement':'')+'. Monthly saving, fixed returns, before tax and fees. Illustrations, not guarantees.</p><p class="result-assumptions">Inflation and short-term growth use <a href="'+assumptionSource+'" target="_blank" rel="noopener">FP Canada’s 2026 guidelines</a>. The 4.8% longer-term illustration blends 50% bonds at 3.2% and 50% equities at 6.4%; it is not a recommended allocation. Long-term assumptions suit horizons of 10+ years; shorter horizons use 2.4% growth. Actual returns and costs vary.</p><div class="plan-actions"><button class="text-button" id="dialogue-save">Save on this device</button><button class="text-button" id="dialogue-export">Export plan</button></div><p id="plan-action-status" class="result-assumptions" role="status"></p></div></details>';
  }
  function renderResult(){
    const s=sessions[topic],r=s.result,i=s.calculatedInputs,ret=topic==='retirement';
    const stats=ret?metric('Monthly spending your savings could support',r.sustainableMonthlySpending)+metric('Your monthly spending target',i.monthlySpending):metric('Projected savings · today’s dollars',r.projection.inflationAdjustedFinalBalance)+metric('Your savings target · today’s dollars',i.targetToday);
    $('#dialogue-content').innerHTML='<h1 class="advisor-dialogue" tabindex="-1">Your '+topics[topic].name.toLowerCase()+' plan</h1><dl class="dialogue-results">'+stats+'</dl><p class="result-assumptions">'+i.annualInflationRate+'% inflation · '+i.annualReturnRate+'% annual growth · '+(ret?'before tax and fees; pensions excluded.':'before tax and fees.')+'</p>'+detailedPlan(topic,i,r)+'<div class="dialogue-choices result-choices"><button data-dialogue-choice="calculate">Adjust my numbers <span aria-hidden="true">→</span></button></div>';
    focusContent('h1');
  }
  function savePlan(){
    try{
      const saved=JSON.parse(localStorage.getItem(STORAGE)||'{}');
      localStorage.setItem(STORAGE,JSON.stringify({...saved,[topic]:sessions[topic].inputs}));
      text('plan-action-status','Saved in this browser. Your answers will be ready next time.');
    }catch{text('plan-action-status','This browser couldn’t save your plan. You can export it instead.');}
  }
  function exportPlan(){
    const s=sessions[topic],blob=new Blob([JSON.stringify({version:1,assessmentDate:AS_OF,topic,inputs:s.calculatedInputs,result:s.result},null,2)],{type:'application/json'});
    const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='dream-planner-'+topic+'.json';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    text('plan-action-status','Plan exported.');
  }
  document.addEventListener('click',event=>{
    const entry=event.target.closest('[data-topic-entry]');if(entry){enter(entry.dataset.topicEntry);return;}
    const choice=event.target.closest('[data-dialogue-choice]');if(choice)go(choice.dataset.dialogueChoice);
    if(event.target.closest('#dialogue-save'))savePlan();
    if(event.target.closest('#dialogue-export'))exportPlan();
  });
  scene.addEventListener('submit',event=>{
    if(event.target.id!=='dialogue-input-form')return;event.preventDefault();
    const f=fields()[stepIndex],input=$('#dialogue-answer');
    if(!input.reportValidity())return;
    const value=f.type?input.value:Number(input.value),values={...sessions[topic].inputs,[f.key]:value};
    if(topic==='retirement'&&f.key==='retirementAge'&&value<=currentAge(values)){text('dialogue-error','Choose a retirement age after your current age.');return;}
    sessions[topic].inputs=values;
    if(stepIndex<fields().length-1){stepIndex++;renderInput();}else calculateConversation();
  });
  $('#dialogue-back').addEventListener('click',()=>{
    pending++;
    if(node==='calculate'&&stepIndex>0){stepIndex--;renderInput();return;}
    const previous=history.pop();
    node=previous?.node||'start';stepIndex=previous?.stepIndex??null;renderConversation();
  });
  $('#dialogue-home').addEventListener('click',()=>{pending++;view('home');$('#summit-welcome h1').setAttribute('tabindex','-1');$('#summit-welcome h1').focus();});
  $('#clear-device-data').addEventListener('click',()=>{
    try{for(const key of [STORAGE,'north.plan.v1','north.goals.v1',...['vacation','car','other'].map(key=>'north.savings.'+key+'.v1')])localStorage.removeItem(key);text('device-status','Saved browser plans removed. Current answers stay in this session.');}
    catch{text('device-status','This browser couldn’t remove saved data.');}
  });
  view('home');
})();

