/**
 * project-ui.js — Vistas de proyectos:
 *  1. Periodo/duración: [data-periodo] con data-ini / data-fin (dd/MM/yyyy) o
 *     data-periodo="dd/MM/yyyy → dd/MM/yyyy|actualidad" se pinta como
 *     "6 meses" + "01/03/2026 → 30/08/2026" (no solo fechas).
 *  2. Filtros extra (tecnología, duración, avance, miembros) sobre la
 *     paginación existente de cada página (window.SBAI.extraRowFilter) y sobre
 *     tarjetas ([data-project-card]) en la vista del colaborador.
 */
(function () {
  "use strict";
  var MS_DIA = 86400000;
  function parseFecha(s) {
    var m = /(\d{1,2})\/(\d{1,2})\/(\d{4})/.exec(s || ""); if (!m) return null;
    return new Date(+m[3], +m[2] - 1, +m[1]);
  }
  function dias(a, b) { return Math.max(0, Math.round((b - a) / MS_DIA)); }
  function textoDuracion(d) {
    if (d < 1) return "Menos de 1 día";
    if (d < 14) return d + (d === 1 ? " día" : " días");
    if (d < 60) { var w = Math.round(d / 7); return w + " semanas"; }
    var meses = Math.round(d / 30.44);
    if (meses < 12) return meses + (meses === 1 ? " mes" : " meses");
    var a = Math.floor(meses / 12), r = meses % 12;
    return a + (a === 1 ? " año" : " años") + (r ? " " + r + (r === 1 ? " mes" : " meses") : "");
  }
  function rango(el) {
    var ini, fin, abierto = false;
    if (el.hasAttribute("data-ini")) { ini = parseFecha(el.getAttribute("data-ini")); fin = parseFecha(el.getAttribute("data-fin")); }
    else {
      var partes = (el.getAttribute("data-periodo") || "").split(/→|->/);
      ini = parseFecha(partes[0]); fin = parseFecha(partes[1]);
      if (!fin && /actualidad/i.test(partes[1] || "")) { fin = new Date(); abierto = true; }
    }
    return { ini: ini, fin: fin, abierto: abierto };
  }
  function renderPeriodo(el) {
    if (el.dataset.periodoDone) return; el.dataset.periodoDone = "1";
    var r = rango(el);
    var ini = el.getAttribute("data-ini") || (el.getAttribute("data-periodo") || "").split(/→|->/)[0].trim();
    var fin = el.getAttribute("data-fin") || (el.getAttribute("data-periodo") || "").split(/→|->/)[1];
    fin = (fin || "").trim();
    if (!r.ini || !r.fin) { el.textContent = (ini || "—") + " → " + (fin || "—"); el.dataset.dias = ""; return; }
    var d = dias(r.ini, r.fin); el.dataset.dias = d;
    el.classList.add("periodo-cell");
    el.innerHTML = "<strong>" + textoDuracion(d) + (r.abierto ? " (en curso)" : "") + "</strong><span>" + ini + " → " + fin + "</span>";
  }

  /* ---------- Filtros ---------- */
  function norm(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim(); }
  var estados = {};   // scope -> { tech, dur, avance, miembros }

  function datosFila(row) {
    if (row._pd) return row._pd;
    var techEl = row.querySelector("[data-tech-list]");
    var tech = techEl ? techEl.getAttribute("data-tech-list").split(",").map(norm).filter(Boolean) : [];
    var per = row.querySelector("[data-periodo]"); var d = null;
    if (per) { renderPeriodo(per); d = per.dataset.dias === "" ? null : +per.dataset.dias; }
    var fill = row.querySelector(".progress-bar-fill"); var av = 0;
    if (fill) { var m = /width:\s*([\d.]+)%/.exec(fill.getAttribute("style") || ""); av = m ? parseFloat(m[1]) : 0; }
    var mi = row.querySelector("[data-n]"); var n = mi ? +mi.getAttribute("data-n") : null;
    row._pd = { tech: tech, dias: d, avance: av, miembros: n };
    return row._pd;
  }
  function coincide(scope, row) {
    var e = estados[scope]; if (!e) return true;
    var d = datosFila(row);
    if (e.tech && d.tech.indexOf(e.tech) === -1) return false;
    if (e.dur && d.dias !== null) {
      var dm = d.dias / 30.44;
      if (e.dur === "corto" && !(dm <= 3)) return false;
      if (e.dur === "medio" && !(dm > 3 && dm <= 6)) return false;
      if (e.dur === "largo" && !(dm > 6)) return false;
    }
    if (e.avance) {
      var lo = +e.avance.split("-")[0], hi = +e.avance.split("-")[1];
      if (d.avance < lo || d.avance > hi) return false;
    }
    if (e.miembros && d.miembros !== null) {
      if (e.miembros === "1-3" && !(d.miembros >= 1 && d.miembros <= 3)) return false;
      if (e.miembros === "4-6" && !(d.miembros >= 4 && d.miembros <= 6)) return false;
      if (e.miembros === "7+" && !(d.miembros >= 7)) return false;
    }
    return true;
  }
  window.SBAI = window.SBAI || {};
  window.SBAI.extraRowFilter = function (scope, row) { return coincide(scope, row); };

  function opt(v, t) { return '<option value="' + v + '">' + t + "</option>"; }
  function construirBarra(bar) {
    var scope = bar.getAttribute("data-project-filters");
    var cont = bar.getAttribute("data-rows") ? document.querySelectorAll(bar.getAttribute("data-rows")) : document.querySelectorAll('[data-filter-row="' + scope + '"]');
    var filas = Array.prototype.slice.call(cont);
    var techs = {}; filas.forEach(function (r) {
      var el = r.querySelector("[data-tech-list]"); if (!el) return;
      el.getAttribute("data-tech-list").split(",").forEach(function (t) { t = t.trim(); if (t) techs[norm(t)] = t; });
    });
    var keys = Object.keys(techs).sort();
    estados[scope] = {};
    bar.classList.add("filter-bar");
    bar.innerHTML =
      '<div class="field"><label>Tecnología</label><select class="select" data-k="tech">' + opt("", "Todas") + keys.map(function (k) { return opt(k, techs[k]); }).join("") + "</select></div>" +
      '<div class="field"><label>Duración</label><select class="select" data-k="dur">' + opt("", "Cualquiera") + opt("corto", "Corta (hasta 3 meses)") + opt("medio", "Media (3 a 6 meses)") + opt("largo", "Larga (más de 6 meses)") + "</select></div>" +
      '<div class="field"><label>Avance</label><select class="select" data-k="avance">' + opt("", "Cualquiera") + opt("0-25", "0% – 25%") + opt("26-50", "26% – 50%") + opt("51-75", "51% – 75%") + opt("76-100", "76% – 100%") + "</select></div>" +
      (bar.getAttribute("data-hide") === "miembros" ? "" : '<div class="field"><label>Miembros</label><select class="select" data-k="miembros">' + opt("", "Cualquiera") + opt("1-3", "1 a 3") + opt("4-6", "4 a 6") + opt("7+", "7 o más") + "</select></div>") +
      '<button type="button" class="btn btn-tertiary btn-sm filter-reset">Limpiar filtros</button>';
    var aplicando = false;
    function aplicar() {
      // El "change" que se dispara abajo vuelve a llamar a aplicar() desde el
      // listener de los filtros de tarjetas: sin este freno era recursión
      // infinita ("Maximum call stack size exceeded") en colaborador/proyectos.
      if (aplicando) return;
      aplicando = true;
      try { aplicarFiltros(); } finally { aplicando = false; }
    }
    function aplicarFiltros() {
      // Vuelve a disparar el ciclo de filtrado/paginación propio de la página.
      var trigger = document.querySelector('[data-filter-scope="' + scope + '"][data-filter-key="estado"], [data-filter-scope="' + scope + '"][data-filter-key="q"]');
      if (trigger) trigger.dispatchEvent(new Event(trigger.tagName === "SELECT" ? "change" : "input", { bubbles: true }));
      // Vista de tarjetas (colaborador): filtrar directamente
      document.querySelectorAll("[data-project-card]").forEach(function (c) {
        if (c.getAttribute("data-project-card") !== scope) return;
        var q = document.querySelector('[data-filter-scope="' + scope + '"][data-filter-key="q"]');
        var est = document.querySelector('[data-filter-scope="' + scope + '"][data-filter-key="estado"]');
        var ok = coincide(scope, c);
        if (q && q.value && norm(c.getAttribute("data-f-search") || c.textContent).indexOf(norm(q.value)) === -1) ok = false;
        if (est && est.value && est.value !== "todos" && c.getAttribute("data-f-estado") !== est.value) ok = false;
        c.hidden = !ok;
      });
      var vacio = document.querySelector('[data-cards-empty="' + scope + '"]');
      if (vacio) vacio.hidden = !!document.querySelector('[data-project-card="' + scope + '"]:not([hidden])');
    }
    bar.addEventListener("change", function (e) { var k = e.target.getAttribute("data-k"); if (!k) return; estados[scope][k] = e.target.value; aplicar(); });
    bar.querySelector(".filter-reset").addEventListener("click", function () {
      estados[scope] = {}; bar.querySelectorAll("select").forEach(function (s) { s.value = ""; });
      document.querySelectorAll('[data-filter-scope="' + scope + '"]').forEach(function (c) { if (c.tagName === "SELECT") c.value = "todos"; else if (c.tagName === "INPUT") c.value = ""; });
      aplicar();
    });
    // filtros de tarjetas: búsqueda y estado también actúan sobre las tarjetas
    document.querySelectorAll('[data-filter-scope="' + scope + '"]').forEach(function (c) {
      if (c.closest("[data-project-filters]")) return;
      c.addEventListener(c.tagName === "SELECT" ? "change" : "input", function () { if (document.querySelector('[data-project-card="' + scope + '"]')) aplicar(); });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-periodo]").forEach(renderPeriodo);
    document.querySelectorAll("[data-project-filters]").forEach(construirBarra);
    new MutationObserver(function (m) { m.forEach(function (r) { r.addedNodes.forEach(function (n) { if (n.nodeType === 1) { if (n.matches && n.matches("[data-periodo]")) renderPeriodo(n); n.querySelectorAll && n.querySelectorAll("[data-periodo]").forEach(renderPeriodo); } }); }); })
      .observe(document.body, { childList: true, subtree: true });
  });
})();
