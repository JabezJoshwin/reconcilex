'use strict';
(async () => {
  try {
    const response=await fetch('/api/auth/config');
    if(!response.ok || !(await response.json()).demo) return;
    const notice=document.createElement('aside');
    notice.className='demo-notice';
    notice.setAttribute('aria-label','Demo environment');
    notice.textContent='Portfolio demo · synthetic data only · free hosting may take a minute to wake up';
    document.body.prepend(notice);
  } catch { /* A notice failure never blocks the application. */ }
})();
