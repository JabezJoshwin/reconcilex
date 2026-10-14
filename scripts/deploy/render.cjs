'use strict';
const {verifyDeployment}=require('./smoke.cjs');
const terminalFailures=new Set(['build_failed','update_failed','pre_deploy_failed','canceled','deactivated']);
async function deploy({key,service,sha,url,fetcher=fetch,sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms)),clock=Date.now}) {
  if(!key || !/^srv-[a-z0-9]+$/.test(service || '') || !/^[a-f0-9]{40}$/.test(sha || '')) throw new Error('Provide RENDER_API_KEY, RENDER_SERVICE_ID and a full commit SHA');
  if(new URL(url).protocol!=='https:') throw new Error('APP_URL must use HTTPS');
  const request=async(path,options={})=>{
    const response=await fetcher('https://api.render.com/v1'+path,{...options,headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},signal:AbortSignal.timeout(20000)});
    if(!response.ok) throw new Error('Render API returned HTTP '+response.status);
    return response.json();
  };
  const started=await request('/services/'+service+'/deploys',{method:'POST',body:JSON.stringify({commitId:sha,clearCache:'do_not_clear'})});
  if(!/^dep-[a-z0-9]+$/.test(started.id || '')) throw new Error('Render did not return a deployment ID');
  const deadline=clock()+20*60*1000;
  while(clock()<deadline) {
    const state=await request('/services/'+service+'/deploys/'+started.id);
    console.log('Render deployment '+started.id+': '+state.status);
    if(terminalFailures.has(state.status)) throw new Error('Render deployment failed: '+state.status);
    if(state.status==='live') {
      if(state.commit?.id!==sha) throw new Error('Render deployed a different commit');
      return verifyDeployment(url,sha,{fetcher});
    }
    await sleep(15000);
  }
  throw new Error('Render deployment exceeded the 20-minute deadline');
}
module.exports={deploy};
if(require.main===module) deploy({key:process.env.RENDER_API_KEY,service:process.env.RENDER_SERVICE_ID,sha:process.env.DEPLOY_SHA,url:process.env.APP_URL})
  .then(result=>console.log(JSON.stringify(result))).catch(error=>{console.error(error.message);process.exitCode=1;});
