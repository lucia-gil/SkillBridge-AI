/**
 * landing.js — Vida para la landing pública.
 *  1. "Red de talento": fondo animado del hero (canvas). Nodos = personas y
 *     habilidades que se conectan cuando están cerca (la idea del producto).
 *  2. Contadores que suben al entrar en pantalla (tira de stats).
 *  3. Aparición suave de secciones al hacer scroll.
 * Respeta "reducir movimiento" del sistema operativo y pausa la animación
 * cuando la pestaña no está visible. Sin dependencias.
 */
(function () {
  "use strict";
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- 1. Red de talento ---------- */
  function red() {
    var canvas = document.getElementById("hero-network");
    if (!canvas || !canvas.getContext) return;
    var ctx = canvas.getContext("2d");
    var SKILLS = ["Java", "React", "Docker", "Spring", "SQL", "Python", "AWS", "UX", "Kotlin", "Figma", "Node", "QA"];
    var nodos = [], W = 0, H = 0, dpr = Math.min(window.devicePixelRatio || 1, 2), raf = null;

    function oscuro() { return document.documentElement.getAttribute("data-theme") === "dark"; }

    function medir() {
      var r = canvas.getBoundingClientRect();
      W = r.width; H = r.height;
      canvas.width = W * dpr; canvas.height = H * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var n = Math.round(Math.min(70, Math.max(28, W * H / 16000)));
      nodos = [];
      for (var i = 0; i < n; i++) {
        nodos.push({
          x: Math.random() * W, y: Math.random() * H,
          vx: (Math.random() - .5) * .35, vy: (Math.random() - .5) * .35,
          r: Math.random() * 1.8 + 1.4,
          skill: i < SKILLS.length ? SKILLS[i] : null,
          fase: Math.random() * Math.PI * 2
        });
      }
    }

    function dibujar(t) {
      ctx.clearRect(0, 0, W, H);
      var dark = oscuro();
      var lineaRGB = dark ? "120,160,255" : "61,90,180";
      var DIST = 135;
      for (var i = 0; i < nodos.length; i++) {
        var a = nodos[i];
        if (!reduce) {
          a.x += a.vx; a.y += a.vy;
          if (a.x < -20) a.x = W + 20; if (a.x > W + 20) a.x = -20;
          if (a.y < -20) a.y = H + 20; if (a.y > H + 20) a.y = -20;
        }
        for (var j = i + 1; j < nodos.length; j++) {
          var b = nodos[j], dx = a.x - b.x, dy = a.y - b.y, d = Math.sqrt(dx * dx + dy * dy);
          if (d < DIST) {
            ctx.strokeStyle = "rgba(" + lineaRGB + "," + ((1 - d / DIST) * (dark ? .35 : .22)).toFixed(3) + ")";
            ctx.lineWidth = 1;
            ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
          }
        }
      }
      for (var k = 0; k < nodos.length; k++) {
        var n = nodos[k];
        var pulso = reduce ? 1 : (1 + .25 * Math.sin(t / 900 + n.fase));
        if (n.skill) {
          // Nodos "habilidad": halo + etiqueta
          var g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, 16 * pulso);
          g.addColorStop(0, dark ? "rgba(139,92,246,.45)" : "rgba(139,92,246,.28)");
          g.addColorStop(1, "rgba(139,92,246,0)");
          ctx.fillStyle = g; ctx.beginPath(); ctx.arc(n.x, n.y, 16 * pulso, 0, Math.PI * 2); ctx.fill();
          ctx.fillStyle = dark ? "#C4B5FD" : "#7C3AED";
          ctx.beginPath(); ctx.arc(n.x, n.y, 3.2, 0, Math.PI * 2); ctx.fill();
          ctx.font = "600 11px Inter, system-ui, sans-serif";
          ctx.fillStyle = dark ? "rgba(226,232,255,.55)" : "rgba(30,41,90,.42)";
          ctx.fillText(n.skill, n.x + 7, n.y - 7);
        } else {
          ctx.fillStyle = dark ? "rgba(103,232,249,.7)" : "rgba(37,99,235,.45)";
          ctx.beginPath(); ctx.arc(n.x, n.y, n.r * pulso, 0, Math.PI * 2); ctx.fill();
        }
      }
      if (!reduce) raf = requestAnimationFrame(dibujar);
    }

    medir();
    dibujar(0);
    var to;
    window.addEventListener("resize", function () { clearTimeout(to); to = setTimeout(function () { medir(); if (reduce) dibujar(0); }, 150); });
    document.addEventListener("visibilitychange", function () {
      if (reduce) return;
      if (document.hidden) { cancelAnimationFrame(raf); raf = null; }
      else if (!raf) raf = requestAnimationFrame(dibujar);
    });
    // Al cambiar de tema se redibuja con los colores correctos.
    new MutationObserver(function () { if (reduce) dibujar(0); }).observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  }

  /* ---------- 2. Contadores ---------- */
  function contar(el) {
    var fin = Number(el.dataset.count), pre = el.dataset.prefix || "", suf = el.dataset.suffix || "";
    if (reduce || !fin) { el.textContent = pre + fin + suf; return; }
    var t0 = null, dur = 1400;
    function paso(t) {
      if (!t0) t0 = t;
      var p = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - p, 3);
      el.textContent = pre + Math.round(fin * e) + suf;
      if (p < 1) requestAnimationFrame(paso);
    }
    requestAnimationFrame(paso);
  }

  /* ---------- 3. Aparición al hacer scroll ---------- */
  function aparecer() {
    var els = document.querySelectorAll("[data-reveal], [data-count]");
    if (!("IntersectionObserver" in window)) {
      els.forEach(function (el) { el.classList.add("is-visible"); if (el.dataset.count) contar(el); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        en.target.classList.add("is-visible");
        if (en.target.dataset.count) contar(en.target);
        io.unobserve(en.target);
      });
    }, { threshold: .18 });
    els.forEach(function (el) { io.observe(el); });
  }

  function init() { document.documentElement.classList.add("js-landing"); red(); aparecer(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
