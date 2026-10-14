'use strict';
(async () => {
  const feedback = document.getElementById('auth-feedback');
  const signup = window.location.pathname === '/signup';
  if (signup) {
    document.getElementById('login-title').textContent = 'Create your ReconcileX account';
    document.title = 'Create an account · ReconcileX';
  }
  try {
    const clerk = await window.ReconcileXAuth.load();
    if (clerk.session) {
      await clerk.session.getToken();
      window.location.assign('/dashboard'); return;
    }
    const options = {
      routing:'hash', signInUrl:'/login', signUpUrl:'/signup',
      forceRedirectUrl:'/dashboard', signInForceRedirectUrl:'/dashboard', signUpForceRedirectUrl:'/dashboard',
      appearance:{
        theme:'simple', cssLayerName:'clerk',
        options:{elevation:'flush',socialButtonsVariant:'blockButton',autoFocus:false},
        variables:{
          colorPrimary:'var(--color-accent)', colorText:'var(--color-ink)',
          colorTextSecondary:'var(--color-muted)', colorBackground:'var(--color-bg)',
          colorInputBackground:'var(--color-bg)', colorInputText:'var(--color-ink)',
          colorBorder:'var(--color-border)', colorDanger:'var(--color-error)',
          fontFamily:'Satoshi, sans-serif',fontSize:'14px',borderRadius:'2px'
        }
      }
    };
    if (signup) clerk.mountSignUp(document.getElementById('clerk-auth'),options);
    else clerk.mountSignIn(document.getElementById('clerk-auth'),options);
    feedback.textContent = '';
  } catch (error) { feedback.textContent = error.message; feedback.setAttribute('role','alert'); }
})();
