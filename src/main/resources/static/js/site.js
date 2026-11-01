// Shared site behaviour: reveal-on-scroll, smart nav, count-up, page transitions and the
// orbit loader toolkit (window.MG). Loaders only ever show while the browser is really waiting.
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------- Orbit loader toolkit ----------
  const ORBIT = (center, progress) => `
    <div class="mg-orbit ${progress ? 'is-progress' : ''}">
      <svg viewBox="0 0 150 150" aria-hidden="true">
        <circle class="track" cx="75" cy="75" r="70"/><circle class="arc2" cx="75" cy="75" r="62"/><circle class="arc" cx="75" cy="75" r="70"/>
        <circle class="prog" cx="75" cy="75" r="70" stroke-dasharray="440" stroke-dashoffset="440" style="${progress ? '' : 'display:none'}"/>
        <g class="sat"><circle cx="75" cy="5" r="3.5"/></g>
      </svg>
      <div class="center">${center}</div>
    </div>`;
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  /** Full-screen loader. opts: { title, sub, steps, icon, progress, stepEvery } */
  function loader(opts = {}) {
    const el = document.createElement('div');
    el.className = 'mg-loader';
    el.setAttribute('role', 'status');
    el.setAttribute('aria-live', 'polite');
    const center = opts.progress ? '<b>0%</b>' : opts.icon ? `<i class="fa-solid ${esc(opts.icon)}"></i>` : '<img src="/images/brand/mgkb.png" alt="">';
    el.innerHTML = `<div class="glow"></div><div class="box">${ORBIT(center, opts.progress)}
      <h3>${esc(opts.title || 'Please wait')}</h3><p class="sub">${esc(opts.sub || '')}</p>
      ${opts.steps ? `<ul class="mg-steps">${opts.steps.map((s) => `<li><span class="ic"></span>${esc(s)}</li>`).join('')}</ul>` : ''}</div>`;
    document.body.appendChild(el);
    requestAnimationFrame(() => el.classList.add('open'));
    const lis = el.querySelectorAll('.mg-steps li');
    let current = 0, timer = null;
    const api = {
      step(i) { current = i; lis.forEach((li, n) => { li.classList.toggle('done', n < i); li.classList.toggle('active', n === i); }); return api; },
      sub(t) { el.querySelector('.sub').textContent = t; return api; },
      progress(p) {
        el.querySelector('.prog').style.strokeDashoffset = 440 * (1 - p);
        const b = el.querySelector('.center b'); if (b) b.textContent = Math.round(p * 100) + '%';
        return api;
      },
      success(title, sub) {
        clearInterval(timer);
        lis.forEach((li) => { li.classList.remove('active'); li.classList.add('done'); });
        el.querySelector('.mg-orbit').classList.add('is-progress');
        el.querySelector('.prog').style.cssText = 'stroke-dashoffset:0';
        el.querySelector('.center').innerHTML = '<svg viewBox="0 0 40 40" style="position:static;width:52px;height:52px"><path class="mg-success" d="M10 21 l7 7 l13 -15" fill="none" stroke="#C8962E" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
        if (title) el.querySelector('h3').textContent = title;
        if (sub !== undefined) el.querySelector('.sub').textContent = sub;
        return api;
      },
      close() { clearInterval(timer); el.classList.remove('open'); setTimeout(() => el.remove(), 400); },
    };
    if (opts.steps) {
      api.step(0);
      // Advance through the descriptive steps while we wait, but never past the last one:
      // the last step stays active until the real response arrives.
      if (opts.stepEvery) timer = setInterval(() => { if (current < lis.length - 1) api.step(current + 1); }, opts.stepEvery);
    }
    return api;
  }

  /** Button busy state while a request is in flight. */
  function busy(btn, text) {
    if (!btn || btn.classList.contains('mg-btn-busy')) return { ok() {}, reset() {} };
    const html = btn.innerHTML;
    btn.dataset.mgHtml = html;
    btn.style.minWidth = btn.offsetWidth + 'px';
    btn.classList.add('mg-btn-busy');
    btn.innerHTML = `<span class="mg-btn-spin"><svg viewBox="0 0 20 20"><circle class="t" cx="10" cy="10" r="8"/><circle class="a" cx="10" cy="10" r="8"/></svg></span>${esc(text || 'Please wait…')}`;
    return {
      ok(t = 'Saved') { btn.classList.remove('mg-btn-busy'); btn.innerHTML = `<i class="fa-solid fa-check"></i>${esc(t)}`; setTimeout(() => { btn.innerHTML = html; btn.style.minWidth = ''; }, 1600); },
      reset() { btn.innerHTML = html; btn.classList.remove('mg-btn-busy'); btn.style.minWidth = ''; },
    };
  }

  /** Chat typing indicator element. */
  function typing(label = 'Takaful Assistant is typing') {
    const d = document.createElement('div');
    d.className = 'mg-typing';
    d.innerHTML = `<span class="mini"><i></i></span>${esc(label)}`;
    return d;
  }

  // ---------- Page transitions: gold top bar + soft fade ----------
  const bar = document.createElement('div');
  bar.className = 'mg-topbar';
  function startNav() { bar.classList.remove('finish'); void bar.offsetWidth; bar.classList.add('run'); }
  function go(href) { startNav(); document.body.classList.add('mg-leaving'); setTimeout(() => { location.href = href; }, reduce ? 0 : 220); }
  document.addEventListener('click', (e) => {
    const a = e.target.closest('a[href]');
    if (!a || e.defaultPrevented || a.target === '_blank' || e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0 || a.hasAttribute('download') || a.hasAttribute('data-no-transition')) return;
    const url = new URL(a.href, location.href);
    if (url.origin !== location.origin || (url.pathname === location.pathname && url.hash) || url.pathname.startsWith('/files/') || /\.(pdf|png|jpe?g|svg)$/i.test(url.pathname)) return;
    e.preventDefault();
    go(a.href);
  });
  addEventListener('pageshow', (e) => {
    document.body.classList.remove('mg-leaving'); bar.classList.remove('run'); bar.classList.add('finish');
    // Coming back via the browser's Back button restores the old page from cache: clear any
    // loader or busy button that was showing when the user left.
    if (e.persisted) {
      document.querySelectorAll('.mg-loader').forEach((el) => el.remove());
      document.querySelectorAll('.mg-btn-busy').forEach((b) => { b.classList.remove('mg-btn-busy'); if (b.dataset.mgHtml) b.innerHTML = b.dataset.mgHtml; b.style.minWidth = ''; });
    }
  });

  // ---------- Forms: data-busy="Saving…" shows a spinner on the submit button while posting ----------
  document.addEventListener('submit', (e) => {
    const form = e.target;
    if (e.defaultPrevented || !form.matches('form[data-busy]')) return;
    const btn = e.submitter || form.querySelector('button[type="submit"], button:not([type])');
    busy(btn, form.dataset.busy || 'Please wait…');
    startNav();
  });

  document.addEventListener('DOMContentLoaded', () => {
    document.body.appendChild(bar);
    if (!document.body.classList.contains('intro-on')) {
      document.body.classList.add('mg-entering');
      setTimeout(() => document.body.classList.remove('mg-entering'), 450);
    }

    // Reveal on scroll
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
    }, { threshold: 0.12, rootMargin: '0px 0px -30px 0px' });
    document.querySelectorAll('.reveal, .bar-fill').forEach((el) => io.observe(el));

    // Count-up numbers
    const countIo = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        const el = en.target, target = parseFloat(el.dataset.count) || 0, start = performance.now(), dur = reduce ? 0 : 1400;
        const tick = (t) => {
          const p = dur ? Math.min(1, (t - start) / dur) : 1;
          el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3))).toLocaleString();
          if (p < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick); countIo.unobserve(el);
      });
    }, { threshold: 0.5 });
    document.querySelectorAll('[data-count]').forEach((el) => countIo.observe(el));

    // Smart nav: compact shadow on scroll; over a dark hero it stays transparent until the hero ends.
    const nav = document.querySelector('.nav-glass');
    const hero = document.querySelector('[data-dark-hero]');
    const progress = document.getElementById('read-progress');
    const parallax = document.querySelectorAll('[data-parallax]');
    let ticking = false;
    const onScroll = () => {
      const y = scrollY;
      if (nav) {
        nav.classList.toggle('compact', y > 24);
        if (hero) nav.classList.toggle('nav-over-hero', y < hero.offsetHeight - 80);
      }
      if (progress) progress.style.transform = `scaleX(${y / Math.max(1, document.documentElement.scrollHeight - innerHeight)})`;
      if (!reduce && hero && y < hero.offsetHeight) parallax.forEach((el) => { el.style.transform = `translate3d(0, ${y * el.dataset.parallax}px, 0)`; });
      ticking = false;
    };
    addEventListener('scroll', () => { if (!ticking) { requestAnimationFrame(onScroll); ticking = true; } }, { passive: true });
    onScroll();

    // Subtle 3D tilt on plan cards (pointer devices only)
    if (!reduce && matchMedia('(hover: hover)').matches) {
      document.querySelectorAll('.tilt').forEach((card) => {
        card.addEventListener('pointermove', (e) => {
          const r = card.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - .5, y = (e.clientY - r.top) / r.height - .5;
          card.style.transform = `perspective(900px) rotateX(${-y * 5}deg) rotateY(${x * 6}deg) translateY(-6px)`;
        });
        card.addEventListener('pointerleave', () => { card.style.transform = ''; });
      });
    }

    // Mobile menu
    document.querySelectorAll('.page-menu-open').forEach((button) => button.addEventListener('click', () => {
      const menu = document.querySelector('.page-mobile-menu');
      if (menu) { menu.classList.toggle('hidden'); button.setAttribute('aria-expanded', String(!menu.classList.contains('hidden'))); }
    }));
  });

  window.MG = { loader, busy, typing, go, esc, reduce };
})();
