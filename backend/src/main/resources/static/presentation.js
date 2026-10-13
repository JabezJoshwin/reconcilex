'use strict';

// Presentation is independent of the reconciliation workflow and its financial data.
(() => {
  const decisions = [
    ['Money representation', 'Integer minor units on the server. Decimal strings in the browser. Precision survives the entire round trip.', 'Java long → JSON string → textContent'],
    ['Duplicate policy', 'A repeated payment reference is evidence to investigate, not a record to overwrite. Ambiguity always takes precedence over a match.', 'Every occurrence retained in PostgreSQL'],
    ['Failure boundaries', 'Validate first. Persist together. A failed database write rolls the run back, so a completed report never represents a partial save.', 'One service transaction · complete results'],
    ['Scope with intent', 'Shared payment references only. Fees, payouts and foreign exchange need different models, and do not belong in this matching rule.', 'One-to-one reconciliation · no fuzzy matching']
  ];
  let index = 0;
  function showDecision(direction) {
    index = (index + direction + decisions.length) % decisions.length;
    document.getElementById('decision-topic').textContent = decisions[index][0];
    document.getElementById('decision-text').textContent = decisions[index][1];
    document.getElementById('decision-detail').textContent = decisions[index][2];
  }
  document.getElementById('decision-prev').addEventListener('click', () => showDecision(-1));
  document.getElementById('decision-next').addEventListener('click', () => showDecision(1));

  const toggle = document.getElementById('motion-toggle');
  let media;
  let paused = false;
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
  function setupMotion() {
    media?.revert();
    document.documentElement.classList.toggle('motion-paused', paused || reduced.matches);
    toggle.textContent = reduced.matches ? 'Reduced motion enabled' : (paused ? 'Resume motion' : 'Pause motion');
    toggle.setAttribute('aria-pressed', String(paused || reduced.matches));
    toggle.disabled = reduced.matches;
    if (paused || reduced.matches || !window.gsap || !window.ScrollTrigger) return;
    gsap.registerPlugin(ScrollTrigger);
    media = gsap.matchMedia();
    media.add('(prefers-reduced-motion: no-preference)', () => {
      gsap.from('.hero-content > *', {y: 24, opacity: 0, stagger: .12, duration: .9, ease: 'power2.out'});
      gsap.to('.marquee-track', {xPercent: -50, duration: 32, repeat: -1, ease: 'none'});
    });
    media.add('(min-width: 960px) and (prefers-reduced-motion: no-preference)', () => {
      const cards = gsap.utils.toArray('.principle-card');
      ScrollTrigger.create({trigger: '.principles-layout', start: 'top 120px', end: 'bottom 75%', pin: '.principles-heading', pinSpacing: false});
      cards.slice(0, -1).forEach((card, i) => {
        ScrollTrigger.create({trigger: card, start: `top ${120 + i * 24}px`, endTrigger: '.principle-stack', end: 'bottom 540px', pin: true, pinSpacing: false});
        gsap.to(card, {scale: .96, ease: 'none', scrollTrigger: {trigger: cards[i + 1], start: 'top bottom', end: 'top 160px', scrub: true}});
      });
    });
  }
  toggle.addEventListener('click', () => { paused = !paused; setupMotion(); });
  reduced.addEventListener('change', setupMotion);
  setupMotion();
  document.fonts?.ready.then(() => window.ScrollTrigger?.refresh());
  window.addEventListener('load', () => window.ScrollTrigger?.refresh());
  // Opening a disclosure or loading a report changes the page's scroll geometry.
  document.querySelectorAll('details').forEach(detail => detail.addEventListener('toggle', () => window.ScrollTrigger?.refresh()));
})();
