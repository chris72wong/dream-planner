const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.resolve(__dirname,'../../main/resources/static/app.js'),'utf8');

function harness(initialVisibility='visible') {
  let now=0,nextTimer=0;
  const timers=new Map(),listeners=new Map(),calls=[];
  const document={visibilityState:initialVisibility,addEventListener:(event,listener)=>listeners.set(event,listener)};
  const context=vm.createContext({
    document,Intl,AbortController,
    Date:{now:()=>now},
    setTimeout:(callback,delay)=>{const id=++nextTimer;timers.set(id,{callback,at:now+delay});return id;},
    clearTimeout:id=>timers.delete(id),
    // Rejecting intervals makes a periodic keep-alive regression fail immediately.
    setInterval:()=>{throw new Error('Periodic requests are forbidden');},
    fetch:(url,options)=>new Promise((resolve,reject)=>{
      calls.push({url,options,resolve,reject});
      options.signal?.addEventListener('abort',()=>reject(new Error('Aborted')),{once:true});
    })
  });
  vm.runInContext(source,context);
  return {
    calls,timers,context,
    visibility(value){document.visibilityState=value;listeners.get('visibilitychange')();},
    advance(ms){now+=ms;for(const [id,timer] of [...timers])if(timer.at<=now){timers.delete(id);timer.callback();}}
  };
}
const settle=async()=>{for(let i=0;i<6;i++)await Promise.resolve();};

test('arrival makes one empty uncached request and never repeats while visible',async()=>{
  const h=harness();
  assert.equal(h.calls.length,1);
  assert.equal(h.calls[0].url,'/api/health');
  assert.equal(h.calls[0].options.method,'GET');
  assert.equal(h.calls[0].options.cache,'no-store');
  assert.equal(h.calls[0].options.body,undefined);
  h.calls[0].resolve({status:204});await settle();
  assert.equal(h.timers.size,0);
  h.advance(3600000);h.visibility('visible');
  assert.equal(h.calls.length,1);
});

test('a page opened hidden wakes only when it first becomes visible',()=>{
  const h=harness('hidden');
  assert.equal(h.calls.length,0);
  h.visibility('visible');
  assert.equal(h.calls.length,1);
});

test('returning wakes once after a minute and rapid tab switches are throttled',async()=>{
  const h=harness();
  h.calls[0].resolve({status:204});await settle();
  h.advance(59999);h.visibility('hidden');h.visibility('visible');
  assert.equal(h.calls.length,1);
  h.advance(1);h.visibility('hidden');h.visibility('visible');
  assert.equal(h.calls.length,2);
  h.visibility('hidden');h.visibility('visible');
  assert.equal(h.calls.length,2);
});

test('an in-flight request suppresses duplicate attempts even past the throttle',()=>{
  const h=harness();
  // Visibility handlers can run before an overdue timeout callback is dispatched.
  h.context.Date.now=()=>120000;
  h.visibility('hidden');h.visibility('visible');
  assert.equal(h.calls.length,1);
});

test('timeout aborts at thirty seconds with no retry and later return can recover',async()=>{
  const h=harness();
  h.advance(29999);assert.equal(h.calls[0].options.signal.aborted,false);
  h.advance(1);assert.equal(h.calls[0].options.signal.aborted,true);
  await settle();
  h.advance(30000);assert.equal(h.calls.length,1);
  h.visibility('hidden');h.visibility('visible');
  assert.equal(h.calls.length,2);
});

test('network and HTTP failures stay silent with no automatic retry',async()=>{
  for(const failure of ['network','http']){
    const h=harness();
    if(failure==='network')h.calls[0].reject(new Error('Offline'));
    else h.calls[0].resolve({ok:false,status:503});
    await settle();assert.equal(h.timers.size,0);
    h.advance(120000);assert.equal(h.calls.length,1);
    h.visibility('hidden');h.visibility('visible');
    assert.equal(h.calls.length,2);
  }
});

test('calculations proceed while the wake-up request is still pending',async()=>{
  const h=harness();
  const calculation=vm.runInContext("post('home', {targetToday:30000})",h.context);
  assert.equal(h.calls.length,2);
  assert.equal(h.calls[1].url,'/api/plans/home');
  h.calls[1].resolve({ok:true,json:async()=>({finalBalance:40000})});
  assert.equal((await calculation).finalBalance,40000);
  assert.equal(h.calls[0].options.signal.aborted,false);
});
