const {test}=require('node:test');
const assert=require('node:assert/strict');
const {deploy}=require('./render.cjs');
const {verifyDeployment}=require('./smoke.cjs');
const sha='a'.repeat(40),base='https://fixture.onrender.com';
function fixture({status='live',revision=sha,apiStatus=200,readyStatus=200}={}) {
  const calls=[];
  const fetcher=async(url,options={})=>{
    calls.push({url,options});
    const path=new URL(url).pathname;
    if(url.startsWith('https://api.render.com')) return Response.json(options.method==='POST'?{id:'dep-fixture'}:{status,commit:{id:sha}},{status:apiStatus});
    if(path==='/api/health') return Response.json({status:'UP',revision});
    if(path==='/api/ready') return Response.json({status:readyStatus===200?'UP':'DOWN'},{status:readyStatus});
    if(path==='/api/auth/config') return Response.json({clerkConfigured:true,demo:true,frontendApi:'https://fixture.clerk.accounts.dev'});
    if(path==='/api/auth/csrf') return Response.json({token:'fixture'},{headers:{'Set-Cookie':'SESSION=fixture; Secure; HttpOnly; SameSite=Lax'}});
    if(path==='/api/me' || path.startsWith('/api/reconciliations')) return Response.json({code:'UNAUTHENTICATED'},{status:401});
    if(path==='/dashboard') return new Response(null,{status:302,headers:{Location:base+'/login'}});
    return new Response('fixture',{headers:{'Strict-Transport-Security':'max-age=31536000'}});
  };
  return {fetcher,calls};
}
test('deployment targets the tested SHA and checks the hosted revision',async()=>{
  const f=fixture();const result=await deploy({key:'fixture-secret',service:'srv-fixture',sha,url:base,fetcher:f.fetcher});
  assert.equal(result.status,'passed');
  assert.equal(JSON.parse(f.calls[0].options.body).commitId,sha);
  assert.equal(f.calls[0].options.headers.Authorization,'Bearer fixture-secret');
  assert.equal(f.calls[1].url,base+'/api/health');
  assert.ok(f.calls.filter(c=>!c.url.startsWith('https://api.render.com')).every(c=>!c.options.headers?.Authorization));
});
test('one release wake failure cannot skip deployment or final hosted checks',async()=>{
  const f=fixture();let wakes=0;
  const fetcher=async(url,options)=>{
    if(url===base+'/api/health' && ++wakes===1) throw new Error('Cold-start timeout');
    return f.fetcher(url,options);
  };
  assert.equal((await deploy({key:'secret',service:'srv-fixture',sha,url:base,fetcher})).status,'passed');
  assert.equal(wakes,2); // One wake, then the mandatory final revision check.
  assert.ok(f.calls.some(c=>c.url===base+'/api/ready'));
});
test('Render HTTP and terminal deployment failures are not mistaken for success',async()=>{
  for(const config of [{apiStatus:401},{status:'build_failed'},{status:'update_failed'},{status:'canceled'}]) {
    const f=fixture(config);await assert.rejects(deploy({key:'secret',service:'srv-fixture',sha,url:base,fetcher:f.fetcher}),/HTTP|failed/);
  }
});
test('deployment deadline is bounded and malformed targets do not make requests',async()=>{
  const f=fixture({status:'build_in_progress'});let now=0;
  await assert.rejects(deploy({key:'secret',service:'srv-fixture',sha,url:base,fetcher:f.fetcher,clock:()=>now,sleep:async()=>{now+=21*60*1000;}}),/deadline/);
  await assert.rejects(deploy({key:'secret',service:'../other',sha,url:base,fetcher:f.fetcher}),/Provide/);
  await assert.rejects(deploy({key:'secret',service:'srv-fixture',sha:'main',url:base,fetcher:f.fetcher}),/Provide/);
});
test('smoke checks reject wrong revision and unavailable database',async()=>{
  for(const config of [{revision:'wrong'},{readyStatus:503}]) {
    await assert.rejects(verifyDeployment(base,sha,{fetcher:fixture(config).fetcher}));
  }
  await assert.rejects(verifyDeployment('http://example.com',sha),/HTTPS/);
});
