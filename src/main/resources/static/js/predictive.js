/**
 * predictive.js — Texto predictivo + filtrado en vivo para los buscadores de
 * tablas/listas (input[data-filter-key="q"]) y selector de colaboradores
 * tipo buscador (select[data-combobox]).
 *  - Filtra la tabla desde 2 letras (y restaura todo al borrar).
 *  - Sugiere coincidencias (fuente: data-f-search / primera celda de cada fila).
 *  - Lee ?buscar=... de la URL para llegar ya filtrado desde el buscador global.
 */
(function () {
  "use strict";
  function norm(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim(); }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function resaltar(t, q) {
    var i = norm(t).indexOf(norm(q)); if (i < 0 || !q) return esc(t);
    return esc(t.slice(0, i)) + "<mark>" + esc(t.slice(i, i + q.length)) + "</mark>" + esc(t.slice(i + q.length));
  }
  function fire(el, type) { el.dispatchEvent(new Event(type, { bubbles: true })); }

  /* ---------- 1. Buscadores de tablas ---------- */
  function enhanceSearch(input) {
    if (input.dataset.predictive) return; input.dataset.predictive = "1";
    var scope = input.getAttribute("data-filter-scope");
    input.setAttribute("autocomplete", "off");
    var wrap = document.createElement("div"); wrap.className = "pred-wrap";
    input.parentNode.insertBefore(wrap, input); wrap.appendChild(input);
    var box = document.createElement("div"); box.className = "pred-list"; box.hidden = true; wrap.appendChild(box);
    var active = -1, opts = [];

    function fuentes() {
      var rows = document.querySelectorAll('[data-filter-row="' + scope + '"], tr[data-filter-scope="' + scope + '"], [data-filter-scope="' + scope + '"][data-f-search]');
      var out = [], seen = {};
      rows.forEach(function (r) {
        var el = r.querySelector(".cell-person-name, .cell-name, td, h3, strong");
        var label = (el ? el.textContent : r.textContent).trim().replace(/\s+/g, " ");
        var hay = r.dataset.fSearch || label;
        if (label && !seen[label]) { seen[label] = 1; out.push({ label: label, hay: norm(hay + " " + label) }); }
      });
      return out;
    }
    function sugerir() {
      var q = norm(input.value);
      if (q.length < 2) { box.hidden = true; return; }
      opts = fuentes().filter(function (o) { return o.hay.indexOf(q) !== -1; }).slice(0, 6);
      if (!opts.length || (opts.length === 1 && norm(opts[0].label) === q)) { box.hidden = true; return; }
      box.innerHTML = opts.map(function (o, i) { return '<button type="button" class="pred-item" data-i="' + i + '">' + resaltar(o.label, input.value.trim()) + "</button>"; }).join("");
      active = -1; box.hidden = false;
    }
    input.addEventListener("input", function () { sugerir(); });   // el filtrado en sí lo hace table-filter con su propio listener
    input.addEventListener("keydown", function (e) {
      var btns = box.querySelectorAll(".pred-item");
      if (box.hidden || !btns.length) return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault(); active = (active + (e.key === "ArrowDown" ? 1 : -1) + btns.length) % btns.length;
        btns.forEach(function (b, i) { b.classList.toggle("active", i === active); });
      } else if (e.key === "Enter" && active >= 0) { e.preventDefault(); btns[active].click(); }
      else if (e.key === "Escape") { box.hidden = true; }
    });
    box.addEventListener("mousedown", function (e) {
      var b = e.target.closest(".pred-item"); if (!b) return; e.preventDefault();
      input.value = opts[+b.dataset.i].label; box.hidden = true; fire(input, "input"); fire(input, "change");
    });
    input.addEventListener("blur", function () { setTimeout(function () { box.hidden = true; }, 120); });

    // ?buscar= desde el buscador global
    var p = new URLSearchParams(window.location.search).get("buscar");
    if (p && !input.dataset.buscarAplicado) { input.dataset.buscarAplicado = "1"; input.value = p; setTimeout(function () { fire(input, "input"); }, 60); }
  }

  /* ---------- 2. Select -> combobox con buscador ---------- */
  function enhanceSelect(sel) {
    if (sel.dataset.cbDone) return; sel.dataset.cbDone = "1";
    var wrap = document.createElement("div"); wrap.className = "cb-wrap";
    sel.parentNode.insertBefore(wrap, sel); wrap.appendChild(sel); sel.classList.add("cb-native");
    var trigger = document.createElement("input"); trigger.type = "text"; trigger.className = "input cb-input";
    trigger.setAttribute("autocomplete", "off"); trigger.placeholder = sel.getAttribute("data-placeholder") || "Escribe para buscar…";
    var list = document.createElement("div"); list.className = "cb-list"; list.hidden = true;
    wrap.appendChild(trigger); wrap.appendChild(list);
    var opts = Array.prototype.map.call(sel.options, function (o) { return { value: o.value, label: o.textContent.trim() }; });
    var active = -1, shown = [];
    function actual() { var o = sel.options[sel.selectedIndex]; return o ? o.textContent.trim() : ""; }
    trigger.value = actual();

    function render(q) {
      var nq = norm(q);
      shown = opts.filter(function (o) { return !nq || norm(o.label).indexOf(nq) !== -1; }).slice(0, 30);
      list.innerHTML = shown.length
        ? shown.map(function (o, i) { return '<button type="button" class="cb-item' + (o.value === sel.value ? " selected" : "") + '" data-i="' + i + '">' + resaltar(o.label, q) + "</button>"; }).join("")
        : '<div class="cb-empty">Sin coincidencias</div>';
      active = shown.length ? 0 : -1; mark();
    }
    function mark() { list.querySelectorAll(".cb-item").forEach(function (b, i) { b.classList.toggle("active", i === active); }); }
    function pick(o) { sel.value = o.value; trigger.value = o.label; list.hidden = true; fire(sel, "change"); }
    trigger.addEventListener("focus", function () { trigger.select(); render(""); list.hidden = false; });
    trigger.addEventListener("input", function () { render(trigger.value); list.hidden = false; });
    trigger.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); if (!shown.length) return; active = (active + (e.key === "ArrowDown" ? 1 : -1) + shown.length) % shown.length; mark(); }
      else if (e.key === "Enter") { e.preventDefault(); if (active >= 0) pick(shown[active]); }
      else if (e.key === "Escape") { list.hidden = true; trigger.value = actual(); }
    });
    list.addEventListener("mousedown", function (e) { var b = e.target.closest(".cb-item"); if (!b) return; e.preventDefault(); pick(shown[+b.dataset.i]); });
    trigger.addEventListener("blur", function () { setTimeout(function () { list.hidden = true; trigger.value = actual(); }, 120); });
  }

  function scan(root) {
    (root || document).querySelectorAll('input[data-filter-key="q"]').forEach(enhanceSearch);
    (root || document).querySelectorAll("select[data-combobox]").forEach(enhanceSelect);
  }
  document.addEventListener("DOMContentLoaded", function () {
    scan(document);
    // Modales que se construyen después (templates/JS): reaplicar
    new MutationObserver(function (m) { m.forEach(function (r) { r.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); }); }); })
      .observe(document.body, { childList: true, subtree: true });
  });
})();
