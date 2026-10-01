const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../main/resources/static');
const source=fs.readFileSync(path.join(root,'dialogue.js'),'utf8');
const app=fs.readFileSync(path.join(root,'app.js'),'utf8');
const plain=value=>JSON.parse(JSON.stringify(value));
const setup=source.slice(source.indexOf('const sources ='),source.indexOf('// Original vector artwork'));
const age=app.slice(app.indexOf('function currentAge('),app.indexOf('function view('));
const fields=source.slice(source.indexOf('const savingsFields='),source.indexOf('function enter('));
const requests=source.slice(source.indexOf('function requestFor('),source.indexOf('async function calculateConversation('));
const helpers=vm.runInNewContext('(()=>{const AS_OF="2026-09-29";let topic="house";'+setup+age+fields+requests+'return {topics,sources,assumptions,fields,seed,illustrationInputs,requestFor};})()');
const artwork=vm.runInNewContext('(()=>{'+source.slice(source.indexOf('// Original vector artwork'),source.indexOf("$('#summit-welcome').innerHTML"))+'return {advisors,artwork};})()');

test('each advisor has a short introduction and at most two reachable choices',()=>{
  for(const [key,topic] of Object.entries(helpers.topics)){
    const visited=new Set();
    function visit(id){
      if(id==='calculate'||visited.has(id))return;
      const node=topic.nodes[id];assert.ok(node);visited.add(id);
      assert.ok(node.text.length<=200,'Avoid paginated speeches');
      assert.ok(node.choices.length>=1&&node.choices.length<=2);
      for(const choice of node.choices)visit(choice.to);
    }
    visit('start');assert.equal(visited.size,Object.keys(topic.nodes).length);
    assert.equal(new URL(helpers.sources[key]).hostname,'www.canada.ca');
  }
});

test('only personal goal questions remain: four for savings, five for retirement',()=>{
  assert.equal(helpers.fields('house').length,4);
  assert.equal(helpers.fields('education').length,4);
  assert.equal(helpers.fields('retirement').length,5);
  for(const key of ['house','education','retirement']){
    assert.ok(helpers.fields(key).every(f=>!f.key.includes('Rate')&&f.key!=='planningAge'));
  }
});

test('defaults use 2.1% inflation and distinguish short and long savings horizons',()=>{
  for(const key of ['house','education']){
    for(const years of [1,9,10,30]){
      const answers={...helpers.seed(key),years,annualReturnRate:99,annualInflationRate:99};
      const i=helpers.illustrationInputs(key,answers),request=helpers.requestFor(key,i);
      assert.equal(request.annualInflationRate,.021);
      assert.equal(request.annualReturnRate,years<10?.024:.048);
      assert.equal(answers.annualReturnRate,99,'Never mutate the answers');
    }
  }
});

test('retirement uses automatic growth through accumulation and withdrawal, and a valid horizon',()=>{
  for(const retirementAge of [65,95,119]){
    const i=helpers.illustrationInputs('retirement',{...helpers.seed('retirement'),retirementAge});
    const request=helpers.requestFor('retirement',i);
    assert.equal(request.savings.currentAge,30);
    assert.equal(request.savings.annualReturnRate,.048);
    assert.equal(request.retirementReturnRate,.048);
    assert.equal(request.savings.annualInflationRate,.021);
    assert.equal(request.planningAge,Math.max(95,retirementAge+1));
    assert.equal(request.cppMonthlyAt65,0);
    assert.equal(request.oasMonthlyAt65,0);
  }
});

test('retirement date calculation respects the assessment date birthday boundary',()=>{
  for(const [birthDate,expected] of [['1996-09-29',30],['1996-09-30',29],['1996-10-01',29]]){
    const i=helpers.illustrationInputs('retirement',{...helpers.seed('retirement'),birthDate});
    assert.equal(helpers.requestFor('retirement',i).savings.currentAge,expected);
  }
});

test('every topic expands inputs, projection and timeline inside the same bubble',()=>{
  const definitions=source.slice(source.indexOf('function metric('),source.indexOf('function renderResult('));
  const api=vm.runInNewContext('(()=>{const AS_OF="2026-09-29";let topic="house";'+setup+fields+'const escape=v=>String(v);const money=new Intl.NumberFormat("en-CA",{style:"currency",currency:"CAD"});'+definitions+'return {detailedPlan};})()');
  const projection={inflationAdjustedFinalBalance:42000,totalInvestmentGrowth:2000,annualBreakdown:[{projectionYear:1,ageAtYearEnd:31,endingBalance:15000,inflationAdjustedEndingBalance:14700}]};
  for(const key of ['house','retirement','education']){
    const result={projection,savings:projection,nominalTarget:55000,requiredMonthlyContribution:400,endingBalance:20000,firstShortfallAge:null,retirementYears:[{ageAtYearEnd:66,endingBalance:40000,inflationAdjustedEndingBalance:20000}]};
    const html=api.detailedPlan(key,helpers.illustrationInputs(key,helpers.seed(key)),result);
    assert.ok(html.startsWith('<details class="plan-details">'));
    assert.ok(html.includes('<summary>View detailed plan</summary>'));
    assert.ok(html.includes('Your numbers')&&html.includes('Your projection')&&html.includes('Year-by-year balances'));
    assert.ok(html.includes('Today’s dollars'));
    assert.ok(!html.includes('data-view')&&!html.includes('href="/'));
    assert.ok(html.includes('Save on this device')&&html.includes('Export plan'));
  }
});

test('retained artwork uses three distinct advisors and unique gradient IDs',()=>{
  assert.equal(new Set(Object.values(artwork.advisors).map(a=>a.name)).size,3);
  const ids=new Set();
  for(const key of ['house','retirement','education'])for(const small of [false,true]){
    const svg=artwork.artwork(key,small),id=svg.match(/linearGradient id="([^"]+)"/)[1];
    assert.ok(!ids.has(id));ids.add(id);assert.ok(svg.includes('url(#'+id+')'));
    assert.ok(svg.includes(artwork.advisors[key].name)||small);
  }
});

function calculationHarness(){
  let resolve,reject;
  const response=new Promise((yes,no)=>{resolve=yes;reject=no;});
  const button={disabled:false,textContent:''};
  const context=vm.createContext({
    topic:'house',activeView:'advisor',pending:0,stepIndex:3,node:'calculate',history:[],
    sessions:{house:{inputs:plain(helpers.seed('house')),result:null}},
    clone:plain,illustrationInputs:helpers.illustrationInputs,requestFor:helpers.requestFor,
    post:()=>response,$:()=>button,renderConversation:()=>{},text:(id,value)=>{context.error=value;}
  });
  vm.runInContext(source.slice(source.indexOf('async function calculateConversation('),source.indexOf('function metric(')),context);
  return {context,button,resolve,reject};
}
test('leaving a topic while calculating discards a late response',async()=>{
  const h=calculationHarness(),run=h.context.calculateConversation();
  h.context.pending++;h.context.activeView='home';
  h.resolve({nominalTarget:100});await run;
  assert.equal(h.context.node,'calculate');
  assert.equal(h.context.sessions.house.result,null);
});
test('a failed request retains answers and enables retry',async()=>{
  const h=calculationHarness(),before=plain(h.context.sessions.house.inputs),run=h.context.calculateConversation();
  h.reject(new Error('Failed to fetch'));await run;
  assert.deepEqual(plain(h.context.sessions.house.inputs),before);
  assert.equal(h.button.disabled,false);assert.equal(h.button.textContent,'Try again');
  assert.ok(h.context.error.includes('Your answers are still here'));
});
test('successful calculation displays results only for its own topic',async()=>{
  const h=calculationHarness(),run=h.context.calculateConversation();
  h.resolve({nominalTarget:100});await run;
  assert.equal(h.context.node,'result');
  assert.equal(h.context.sessions.house.result.nominalTarget,100);
  assert.equal(h.context.sessions.house.calculatedInputs.annualInflationRate,2.1);
});
