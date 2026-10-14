'use strict';
(() => {
  let ready;
  function script(src, key) {
    return new Promise((resolve,reject) => {
      const tag=document.createElement('script');
      tag.src=src; tag.async=true; tag.crossOrigin='anonymous';
      if(key) tag.setAttribute('data-clerk-publishable-key',key);
      tag.onload=resolve; tag.onerror=() => reject(new Error('Could not load Clerk. Check your connection and retry.'));
      document.head.append(tag);
    });
  }
  async function initialize() {
    const response=await fetch('/api/auth/config');
    if(!response.ok) throw new Error('Sign-in service unavailable. Please retry.');
    const config=await response.json();
    if(!config.clerkConfigured) throw new Error('Clerk sign-in is not configured yet. Set CLERK_PUBLISHABLE_KEY and CLERK_ISSUER.');
    const origin=new URL(config.frontendApi);
    if(origin.protocol!=='https:' || origin.origin!==config.frontendApi) throw new Error('Invalid Clerk configuration.');
    await script(origin.origin+'/npm/@clerk/ui@1/dist/ui.browser.js');
    await script(origin.origin+'/npm/@clerk/clerk-js@6/dist/clerk.browser.js',config.publishableKey);
    await window.Clerk.load({ui:{ClerkUI:window.__internal_ClerkUICtor}});
    return window.Clerk;
  }
  async function load() {
    if(!ready) ready=initialize().catch(error => {ready=undefined; throw error;});
    return ready;
  }
  async function authenticatedFetch(url, options={}) {
    const clerk=await load();
    const token=clerk.session ? await clerk.session.getToken() : null;
    if(!token) { window.location.assign('/login'); throw new Error('Sign in required.'); }
    const headers=new Headers(options.headers);
    headers.set('Authorization','Bearer '+token);
    return fetch(url,{...options,headers,credentials:'same-origin'});
  }
  window.ReconcileXAuth={load,fetch:authenticatedFetch};
})();
