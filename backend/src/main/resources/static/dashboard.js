'use strict';
(() => {
  const authFetch = (...args) => window.ReconcileXAuth.fetch(...args);
  let page = 0, total = 0, loading = false;
  const byId = id => document.getElementById(id);
  async function history(nextPage = 0) {
    if (loading) return;
    loading = true;
    for (const id of ['history-prev','history-next','history-retry']) byId(id).disabled = true;
    byId('history-feedback').textContent = 'Loading your runs…';
    try {
      const response = await authFetch('/api/reconciliations?page=' + nextPage + '&size=20');
      if (response.status === 401) { window.location.assign('/login'); return; }
      if (!response.ok) throw new Error('Could not load your runs. Please retry.');
      const data = await response.json(); page = data.page; total = data.totalRuns;
      byId('history-body').replaceChildren();
      for (const run of data.runs) {
        const tr = document.createElement('tr');
        const values = [new Date(run.createdAt).toLocaleString(undefined,{timeZone:'UTC'}), run.summary.internalRows + ' / ' + run.summary.processorRows, run.summary.counts.MATCHED, run.summary.distinctIds - run.summary.counts.MATCHED];
        for (const value of values) { const td = document.createElement('td'); td.textContent = String(value); tr.append(td); }
        const cell = document.createElement('td'), button = document.createElement('button');
        button.className = 'button secondary'; button.textContent = 'Open report'; button.setAttribute('aria-label','Open reconciliation ' + run.id);
        button.addEventListener('click',async () => {
          if (busy) return;
          setBusy(true);
          try { await loadRun(run.id,0,''); byId('workspace').scrollIntoView({block:'start'}); }
          catch(error) { setFeedback(error.message,'error'); }
          finally { setBusy(false); }
        });
        cell.append(button); tr.append(cell); byId('history-body').append(tr);
      }
      byId('history-feedback').textContent = total ? '' : 'No runs yet. Create your first reconciliation below.';
      byId('history-page').textContent = total ? (page*20+1) + '–' + Math.min((page+1)*20,total) + ' of ' + total + ' runs' : '0 runs';
    } catch(error) { byId('history-feedback').textContent = error.message; }
    finally {
      loading = false; byId('history-prev').disabled = page === 0;
      byId('history-next').disabled = (page+1)*20 >= total; byId('history-retry').disabled = false;
    }
  }
  byId('history-prev').addEventListener('click',() => history(page-1));
  byId('history-next').addEventListener('click',() => history(page+1));
  byId('history-retry').addEventListener('click',() => history(page));
  byId('export-link').addEventListener('click',async event => {
    event.preventDefault();
    if (!activeRun) return;
    try {
      const response = await authFetch('/api/reconciliations/'+encodeURIComponent(activeRun.id)+'/export');
      if (!response.ok) throw new Error('Could not export the report. Please retry.');
      const url = URL.createObjectURL(await response.blob());
      const link = document.createElement('a'); link.href=url; link.download='reconcilex-'+activeRun.id+'.csv';
      document.body.append(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url),1000);
    } catch(error) { setFeedback(error.message,'error'); }
  });
  window.addEventListener('reconciliation-saved',() => history(0));
  // Do not restore stale private report DOM from back/forward cache after logout.
  window.addEventListener('pageshow',event => { if(event.persisted) window.location.reload(); });
  byId('logout-button').addEventListener('click',async () => {
    byId('logout-button').disabled = true;
    try {
      const csrfResponse = await fetch('/api/auth/csrf');
      if (!csrfResponse.ok) throw new Error('Could not log out. Please retry.');
      const csrf = await csrfResponse.json();
      const response = await authFetch('/logout',{method:'POST',headers:{[csrf.headerName]:csrf.token}});
      if (!response.ok) throw new Error('Could not log out. Please retry.');
      const clerk = await window.ReconcileXAuth.load();
      await clerk.signOut();
      window.location.assign('/login');
    } catch(error) { byId('history-feedback').textContent = error.message; byId('logout-button').disabled = false; }
  });
  (async () => {
    try {
      const clerk = await window.ReconcileXAuth.load();
      if (!clerk.session) { window.location.assign('/login'); return; }
      const response = await authFetch('/api/me');
      if(response.status === 401) { window.location.assign('/login'); return; }
      if(!response.ok) throw new Error('Could not load your account. Reload to retry.');
      const user = await response.json();
      const profile = clerk.user;
      byId('account-name').textContent = profile ? (profile.fullName || 'ReconcileX user') + ' · ' + (profile.primaryEmailAddress?.emailAddress || '') : user.displayName + ' · ' + user.email;
      await history();
    } catch(error) { byId('history-feedback').textContent = error.message; }
  })();
})();
