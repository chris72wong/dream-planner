const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'../../main/resources/static');
const source=fs.readFileSync(path.join(root,'dialogue.js'),'utf8');
// Exercise the authored graph and actual serializers without starting a browser.
const graph=vm.runInNewContext(`(()=>{${source.slice(source.indexOf('const sources ='),source.indexOf('// Original vector artwork'))}return {topics,sources};})()`);
const app=fs.readFileSync(path.join(root,'app.js'),'utf8');
const shared=app.match(/^function (?:currentAge|retirementRequest)\(.*$/gm).join('\n');
const serializers=vm.runInNewContext(`(()=>{${shared}\n${source.slice(source.indexOf('function requestFor('),source.indexOf('async function calculateConversation('))}return {requestFor,retirementRequest};})()`);
const plain=value=>JSON.parse(JSON.stringify(value));
const artwork=vm.runInNewContext(`(()=>{${source.slice(source.indexOf('// Original vector artwork'),source.indexOf("$('#summit-welcome').innerHTML"))}return {advisors,artwork};})()`);
const captionPages=vm.runInNewContext(`(()=>{${source.slice(source.indexOf('function captionPages('),source.indexOf('function renderConversation('))}return captionPages;})()`);

test('subtitle passages preserve all educational content and remain short',()=>{
  for(const topic of Object.values(graph.topics))for(const node of Object.values(topic.nodes)){
    const pages=captionPages(node.text);
    assert.equal(pages.join(' '),node.text);
    assert.ok(pages.every(page=>page.length<=220),'Long captions obscure the advisor on phones');
  }
});

test('each topic has its own named advisor and environment',()=>{
  const names=Object.values(artwork.advisors).map(a=>a.name);
  const locations=Object.values(artwork.advisors).map(a=>a.location);
  assert.equal(new Set(names).size,3);assert.equal(new Set(locations).size,3);
  for(const key of ['house','retirement','education']){
    const svg=artwork.artwork(key);
    assert.ok(svg.includes(artwork.advisors[key].name));
    assert.ok(svg.includes(artwork.advisors[key].location.toLowerCase()));
    assert.ok(svg.includes('advisor-eyes'));assert.ok(svg.includes('advisor-mouth-speaking'));
  }
});

test('welcome previews and full scenes have unique, valid gradient references',()=>{
  const ids=new Set();
  for(const key of ['house','retirement','education'])for(const small of [false,true]){
    const svg=artwork.artwork(key,small),id=svg.match(/linearGradient id="([^"]+)"/)[1];
    assert.ok(!ids.has(id),'SVG gradient IDs must not collide');ids.add(id);
    assert.ok(svg.includes(`url(#${id})`));assert.ok(!svg.includes('url(#sky-'));
  }
});

for(const [key,topic] of Object.entries(graph.topics)){
  test(`${key}: every choice is reachable and has a destination`,()=>{
    const visited=new Set();
    function visit(id){
      if(id==='calculate'||visited.has(id))return;
      const node=topic.nodes[id];assert.ok(node,`Missing destination: ${id}`);visited.add(id);
      assert.ok(node.text.trim());assert.ok(node.choices.length>=1&&node.choices.length<=3);
      for(const choice of node.choices){assert.ok(choice.label.trim());visit(choice.to);}
    }
    visit('start');assert.equal(visited.size,Object.keys(topic.nodes).length);
    assert.equal(new URL(graph.sources[key]).hostname,'www.canada.ca');
    assert.equal(new URL(graph.sources[key]).protocol,'https:');
  });
}

test('retirement dialogue matches the existing detailed-plan request',()=>{
  for(const rate of [0,5.25,-1.5]){
    const inputs={birthDate:'1996-01-01',retirementAge:65,planningAge:95,currentSavings:12345.67,monthlyContribution:450.50,monthlySpending:2500,annualReturnRate:rate,annualInflationRate:2,retirementReturnRate:4};
    const plan={...inputs,tfsaBalance:12345.67,rrspBalance:0,otherBalance:0,annualReturn:rate,inflation:2,retirementReturn:4,cppMonthlyAt65:0,cppStartAge:65,oasMonthlyAt65:0,oasStartAge:65,otherMonthlyIncome:0,otherIncomeStartAge:65};
    assert.deepEqual(plain(serializers.requestFor('retirement',inputs)),plain(serializers.retirementRequest(plan)));
  }
});

test('home and education use identical savings inputs and convert percentages once',()=>{
  const inputs={targetToday:30000,years:10,currentSavings:0,monthlyContribution:300,annualReturnRate:5.25,annualInflationRate:2};
  const expected={...inputs,annualReturnRate:.0525,annualInflationRate:.02};
  for(const key of ['house','education'])assert.deepEqual(plain(serializers.requestFor(key,inputs)),expected);
  assert.equal(inputs.annualReturnRate,5.25,'Serialization must not mutate session inputs');
});
