const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../../main/resources/static/clerk.js'),'utf8');
function fixture(config,session=true) {
  const scripts=[],requests=[],redirects=[];
  let refreshes=0;
  const window={location:{assign:url=>redirects.push(url)},Clerk:{load:async()=>{},session:session?{getToken:async()=> 'fresh-'+(++refreshes)}:null}};
  const context=vm.createContext({window,URL,Headers,document:{createElement:()=>({setAttribute(){}}),head:{append(tag){scripts.push(tag.src);tag.onload();}}},fetch:async(url,options)=>{
    requests.push({url,options});return {ok:true,json:async()=>config};
  }});
  vm.runInContext(source,context);return {auth:window.ReconcileXAuth,scripts,requests,redirects};
}
test('missing Clerk configuration never loads external scripts',async()=>{
  const ui=fixture({clerkConfigured:false});
  await assert.rejects(ui.auth.load(),/not configured/);assert.equal(ui.scripts.length,0);
});
test('SDK loads UI before ClerkJS and each API request obtains a fresh bearer token',async()=>{
  const ui=fixture({clerkConfigured:true,publishableKey:'pk_test_fixture',frontendApi:'https://fixture.clerk.accounts.dev'});
  await Promise.all([ui.auth.load(),ui.auth.load()]);assert.equal(ui.scripts.length,2);
  assert.match(ui.scripts[0],/@clerk\/ui@1/);assert.match(ui.scripts[1],/@clerk\/clerk-js@6/);
  await ui.auth.fetch('/api/me');await ui.auth.fetch('/api/reconciliations',{method:'POST',headers:{'X-CSRF-TOKEN':'csrf'}});
  assert.equal(ui.requests[1].options.headers.get('Authorization'),'Bearer fresh-1');
  assert.equal(ui.requests[2].options.headers.get('Authorization'),'Bearer fresh-2');
  assert.equal(ui.requests[2].options.headers.get('X-CSRF-TOKEN'),'csrf');
  assert.doesNotMatch(source,/localStorage|sessionStorage|innerHTML/);
});
test('signed-out users are redirected without sending a private API request',async()=>{
  const ui=fixture({clerkConfigured:true,publishableKey:'pk_test_fixture',frontendApi:'https://fixture.clerk.accounts.dev'},false);
  await assert.rejects(ui.auth.fetch('/api/me'),/Sign in required/);
  assert.deepEqual(ui.redirects,['/login']);assert.equal(ui.requests.length,1);
});
