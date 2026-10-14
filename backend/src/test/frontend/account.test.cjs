const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname,'../../main/resources/static');
const settle = async () => { for(let i=0;i<20;i++) await Promise.resolve(); };
function setup(file, fetcher, pathname='/login', clerkOverride) {
  const nodes=new Map(), redirects=[], loads=[];
  function element() { return {children:[],events:{},attributes:{},append(...items){this.children.push(...items);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;},removeAttribute(k){delete this.attributes[k];},addEventListener(k,v){this.events[k]=v;},scrollIntoView(){}}; }
  const get=id => { if(!nodes.has(id)) nodes.set(id,element()); return nodes.get(id); };
  const window={events:{},location:{pathname,search:'',assign:url=>redirects.push(url)},addEventListener(k,v){this.events[k]=v;}};
  window.ReconcileXAuth={fetch:(...args)=>fetcher(...args),load:clerkOverride || (async()=>({session:{getToken:async()=> 'token'},signOut:async()=>{}}))};
  const context=vm.createContext({document:{getElementById:get,createElement:element},window,fetch:fetcher,URLSearchParams,Date,busy:false,setBusy(){},loadRun:async(...args)=>loads.push(args),setFeedback(){}});
  vm.runInContext(fs.readFileSync(path.join(root,file),'utf8'),context);
  return {get,window,context,redirects,loads};
}
test('account HTML has one document and unique IDs; workflow is private-only',()=>{
  for(const file of ['index.html','login.html','dashboard.html']) {
    const html=fs.readFileSync(path.join(root,file),'utf8');
    assert.equal((html.match(/<html\b/g)||[]).length,1);
    assert.equal((html.match(/<body\b/g)||[]).length,1);
    const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(x=>x[1]);
    assert.equal(new Set(ids).size,ids.length,file+' contains duplicate IDs');
  }
  assert.doesNotMatch(fs.readFileSync(path.join(root,'index.html'),'utf8'),/src="\/app.js"|id="upload-form"/);
  assert.match(fs.readFileSync(path.join(root,'dashboard.html'),'utf8'),/id="upload-form"/);
});
test('unconfigured Clerk shows a clear setup error',async()=>{
  const ui=setup('auth.js',async()=>({ok:true}),'/login',async()=>{throw new Error('Clerk is not configured');});
  await settle();
  assert.match(ui.get('auth-feedback').textContent,/not configured/);
});
test('signup has distinct accessible copy and fixed dashboard redirect',async()=>{
  let mounted;
  const ui=setup('auth.js',async()=>({ok:true}),'/signup',async()=>({session:null,mountSignUp:(node,options)=>{mounted=options;}}));
  await settle();
  assert.equal(mounted.forceRedirectUrl,'/dashboard');
  assert.equal(mounted.appearance.theme,'simple');
  assert.equal(mounted.appearance.cssLayerName,'clerk');
  assert.equal(mounted.appearance.options.elevation,'flush');
  assert.equal(mounted.appearance.options.autoFocus,false);
  assert.equal(mounted.appearance.variables.colorPrimary,'var(--color-accent)');
  assert.deepEqual(ui.redirects,[]);
  assert.equal(ui.get('login-title').textContent,'Create your ReconcileX account');
});
test('auth display typography cannot leak into nested Clerk headings or paragraphs',()=>{
  const css=fs.readFileSync(path.join(root,'account.css'),'utf8');
  assert.doesNotMatch(css,/\.auth-layout\s+(h1|p)\s*[,\{]/);
  assert.match(css,/\.auth-intro\s*>\s*h1/);
  assert.match(css,/#clerk-auth \.cl-headerTitle\s*\{[^}]*font-size: 26px/);
  assert.match(css,/#clerk-auth \.cl-cardBox, #clerk-auth \.cl-card\s*\{[^}]*box-shadow: none/);
  assert.doesNotMatch(css,/\.cl-internal-/);
});
test('history renders account text safely and opens the selected persisted run',async()=>{
  const run={id:'11111111-1111-4111-8111-111111111111',createdAt:'2026-10-14T00:00:00Z',summary:{internalRows:11,processorRows:10,distinctIds:10,counts:{MATCHED:1}}};
  const ui=setup('dashboard.js',async url=>({ok:true,json:async()=>url==='/api/me'?{displayName:'<script>alert(1)</script>',email:'fixture@example.test'}:{runs:[run],page:0,totalRuns:1}}));
  await settle();
  assert.equal(ui.get('account-name').textContent,'<script>alert(1)</script> · fixture@example.test');
  const row=ui.get('history-body').children[0];
  assert.equal(row.children[3].textContent,'9');
  await row.children[4].children[0].events.click();
  assert.equal(ui.loads[0][0],run.id);
  assert.equal(ui.get('history-next').disabled,true);
  assert.doesNotMatch(fs.readFileSync(path.join(root,'dashboard.js'),'utf8'),/innerHTML/);
});
test('expired dashboard sessions redirect without replaying requests',async()=>{
  const ui=setup('dashboard.js',async()=>({status:401,ok:false}));
  await settle();
  assert.deepEqual(ui.redirects,['/login']);
});
test('restored private dashboard reloads instead of displaying stale cached reports',async()=>{
  const ui=setup('dashboard.js',async()=>({status:401,ok:false}));
  let reloads=0; ui.window.location.reload=()=>reloads++;
  await settle();
  ui.window.events.pageshow({persisted:false}); assert.equal(reloads,0);
  ui.window.events.pageshow({persisted:true}); assert.equal(reloads,1);
});
test('logout includes CSRF and reports failures without pretending success',async()=>{
  let logout;
  const ui=setup('dashboard.js',async(url,options)=>{
    if(url==='/api/auth/csrf') return {ok:true,json:async()=>({token:'csrf-token',headerName:'X-CSRF-TOKEN'})};
    if(url==='/logout') { logout=options; return {ok:true}; }
    return {ok:true,json:async()=>url==='/api/me'?{displayName:'User',email:'test@example.test'}:{runs:[],page:0,totalRuns:0}};
  });
  await settle(); await ui.get('logout-button').events.click();
  assert.equal(logout.method,'POST'); assert.equal(logout.headers['X-CSRF-TOKEN'],'csrf-token');
  assert.deepEqual(ui.redirects,['/login']);
  ui.context.fetch=async()=>({ok:false});
  ui.window.ReconcileXAuth.fetch=async()=>({ok:false});
  await ui.get('logout-button').events.click();
  assert.equal(ui.get('logout-button').disabled,false);
  assert.match(ui.get('history-feedback').textContent,/Could not log out/);
});
