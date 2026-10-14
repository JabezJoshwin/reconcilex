'use strict';
const assert=require('node:assert/strict');

async function verifyDeployment(origin,revision,{local=false,fetcher=fetch}={}) {
  const url=new URL(origin);
  assert.equal(url.origin,origin,'Use an origin without a trailing slash');
  assert.ok(url.protocol==='https:' || local && ['localhost','127.0.0.1'].includes(url.hostname),'Hosted checks require HTTPS');
  const request=path=>fetcher(origin+path,{redirect:'manual',signal:AbortSignal.timeout(20000)});
  const health=await request('/api/health'); assert.equal(health.status,200);
  const data=await health.json(); assert.equal(data.status,'UP'); assert.equal(data.revision,revision,'Wrong deployed revision');
  const ready=await request('/api/ready'); assert.equal(ready.status,200); assert.equal((await ready.json()).status,'UP');
  for(const path of ['/','/login','/signup','/styles.css','/theme.css','/account.css','/clerk.js','/auth.js','/deployment.js']) {
    const response=await request(path); assert.equal(response.status,200,path+' unavailable');
    if(!local) assert.ok(response.headers.get('strict-transport-security'),path+' missing HSTS');
  }
  for(const path of ['/api/me','/api/reconciliations','/api/reconciliations/11111111-1111-4111-8111-111111111111/export']) {
    assert.equal((await request(path)).status,401,path+' must require authentication');
  }
  const dashboard=await request('/dashboard'); assert.equal(dashboard.status,302);
  // Local container checks use a configured public origin even on loopback HTTP.
  if(!local) assert.equal(dashboard.headers.get('location'),origin+'/login');
  const configResponse=await request('/api/auth/config'); assert.equal(configResponse.status,200);
  const config=await configResponse.json(); assert.equal(config.clerkConfigured,true); assert.equal(config.demo,true);
  assert.ok(!JSON.stringify(config).includes('sk_test_') && !JSON.stringify(config).includes('sk_live_'));
  const csrf=await request('/api/auth/csrf'); assert.equal(csrf.status,200);
  const cookie=csrf.headers.get('set-cookie') || '';
  assert.match(cookie,/HttpOnly/i); assert.match(cookie,/Secure/i); assert.match(cookie,/SameSite=Lax/i);
  if(!local) {
    const clerkOrigin=new URL(config.frontendApi); assert.equal(clerkOrigin.protocol,'https:');
    for(const path of ['/.well-known/jwks.json','/npm/@clerk/clerk-js@6/dist/clerk.browser.js','/npm/@clerk/ui@1/dist/ui.browser.js']) {
      const response=await fetcher(clerkOrigin.origin+path,{signal:AbortSignal.timeout(20000)}); assert.equal(response.status,200,'Clerk resource unavailable');
    }
  }
  return {revision,status:'passed',authenticatedWalkthrough:'requires browser sign-in'};
}
module.exports={verifyDeployment};
if(require.main===module) verifyDeployment(process.env.APP_URL,process.env.DEPLOY_SHA,{local:process.argv.includes('--local')})
  .then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error('Deployment smoke check failed: '+error.message);process.exitCode=1;});
