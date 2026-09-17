/**
 * pagination.js — Paginación en cliente, reutilizable.
 *
 * initSimple: pagina una lista de filas/filas de tabla ya renderizadas,
 *   sin filtros (p.ej. la vista rápida de usuarios del dashboard de admin).
 *   Modo "numbered" (números de página) o "dots" (bolitas tipo carrusel).
 *
 * initFiltered: pagina una tabla que además usa table-filter.js
 *   (data-filter-scope/data-filter-row). Solo pagina sobre las filas que
 *   ya pasaron el filtro, y vuelve a la página 1 cuando el filtro cambia.
 *   No modifica table-filter.js: escucha los mismos controles y corre
 *   después (se registra luego de tableFilter.init en cada página), así
 *   siempre lee el row.hidden ya actualizado por el filtro.
 */
(function () {
  "use strict";

  function buildPager(container, mode, total, pageSize, page, onGo) {
    container.innerHTML = "";
    var totalPages = Math.max(1, Math.ceil(total / pageSize));
    page = Math.max(1, Math.min(page, totalPages));

    if (mode === "dots") {
      for (var i = 1; i <= totalPages; i++) {
        (function (n) {
          var dot = document.createElement("button");
          dot.type = "button";
          dot.className = "pagination-dot" + (n === page ? " active" : "");
          dot.setAttribute("aria-label", "Página " + n);
          dot.addEventListener("click", function () { onGo(n); });
          container.appendChild(dot);
        })(i);
      }
      return totalPages;
    }

    function btn(text, dest, active, disabled, label) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "page-btn" + (active ? " active" : "");
      b.textContent = text;
      b.disabled = disabled;
      if (label) b.setAttribute("aria-label", label);
      b.addEventListener("click", function () { onGo(dest); });
      return b;
    }

    container.appendChild(btn("‹", page - 1, false, page === 1, "Página anterior"));
    var maxButtons = 5;
    var start = Math.max(1, page - Math.floor(maxButtons / 2));
    var end = Math.min(totalPages, start + maxButtons - 1);
    start = Math.max(1, end - maxButtons + 1);
    for (var p = start; p <= end; p++) {
      container.appendChild(btn(String(p), p, p === page, false, "Página " + p));
    }
    container.appendChild(btn("›", page + 1, false, page === totalPages, "Página siguiente"));
    return totalPages;
  }

  function initSimple(opts) {
    var wrap = typeof opts.container === "string" ? document.querySelector(opts.container) : opts.container;
    var pagerEl = typeof opts.pagerEl === "string" ? document.querySelector(opts.pagerEl) : opts.pagerEl;
    if (!wrap || !pagerEl) return;
    var rows = Array.prototype.slice.call(wrap.querySelectorAll(opts.rowSelector));
    if (!rows.length) return;

    var pageSize = opts.pageSize || 6;
    var mode = opts.mode || "numbered";
    var page = 1;
    var holder = opts.hideIfSinglePageEl ? document.querySelector(opts.hideIfSinglePageEl) : pagerEl.closest(".agenda-pagination");

    if (rows.length <= pageSize) {
      if (holder) holder.hidden = true;
      rows.forEach(function (r) { r.hidden = false; });
      return;
    }
    if (holder) holder.hidden = false;

    function render() {
      var totalPages = buildPager(pagerEl, mode, rows.length, pageSize, page, function (n) { page = n; render(); });
      page = Math.max(1, Math.min(page, totalPages));
      var start = (page - 1) * pageSize;
      var end = Math.min(start + pageSize, rows.length);
      rows.forEach(function (r, i) { r.hidden = !(i >= start && i < end); });
      if (opts.rangeEl) {
        var rangeNode = document.querySelector(opts.rangeEl);
        if (rangeNode) rangeNode.textContent = (start + 1) + "–" + end;
      }
      if (opts.totalEl) {
        var totalNode = document.querySelector(opts.totalEl);
        if (totalNode) totalNode.textContent = String(rows.length);
      }
    }
    render();

    // Navegación con ← / → cuando el cursor está sobre la zona indicada
    // (opts.keyboardHoverEl). Solo se activa ahí para no interferir con
    // las flechas del teclado en el resto de la página.
    if (opts.keyboardHoverEl) {
      var hoverTarget = typeof opts.keyboardHoverEl === "string" ? document.querySelector(opts.keyboardHoverEl) : opts.keyboardHoverEl;
      if (hoverTarget) {
        var isHovering = false;
        hoverTarget.addEventListener("mouseenter", function () { isHovering = true; });
        hoverTarget.addEventListener("mouseleave", function () { isHovering = false; });
        document.addEventListener("keydown", function (e) {
          if (!isHovering) return;
          var tag = (document.activeElement && document.activeElement.tagName) || "";
          if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
          if (e.key === "ArrowRight") { e.preventDefault(); page += 1; render(); }
          else if (e.key === "ArrowLeft") { e.preventDefault(); page -= 1; render(); }
        });
      }
    }
  }

  function initFiltered(opts) {
    var scope = opts.scope;
    var pageSize = opts.pageSize || 10;
    var pagerEl = typeof opts.pagerEl === "string" ? document.querySelector(opts.pagerEl) : opts.pagerEl;
    var rangeEl = opts.rangeEl ? document.querySelector(opts.rangeEl) : null;
    if (!pagerEl) return;
    var page = 1;

    function scopedRows() {
      return document.querySelectorAll('[data-filter-row="' + scope + '"]');
    }
    function visibleAfterFilter() {
      return Array.prototype.filter.call(scopedRows(), function (r) { return !r.hidden; });
    }

    function render(resetPage) {
      if (resetPage) page = 1;
      var all = scopedRows();
      all.forEach(function (r) { r.classList.remove("page-hidden"); });

      var rows = visibleAfterFilter();
      if (!rows.length) {
        pagerEl.innerHTML = "";
        if (rangeEl) rangeEl.textContent = "0";
        return;
      }
      var totalPages = buildPager(pagerEl, "numbered", rows.length, pageSize, page, function (n) { page = n; render(false); });
      page = Math.max(1, Math.min(page, totalPages));
      var start = (page - 1) * pageSize;
      var end = Math.min(start + pageSize, rows.length);
      rows.forEach(function (r, i) { if (!(i >= start && i < end)) r.classList.add("page-hidden"); });
      if (rangeEl) rangeEl.textContent = (start + 1) + "–" + end + " de " + rows.length;
    }

    document.querySelectorAll('[data-filter-scope="' + scope + '"][data-filter-key]').forEach(function (ctrl) {
      var evt = ctrl.tagName === "SELECT" ? "change" : (ctrl.tagName === "BUTTON" ? "click" : "input");
      ctrl.addEventListener(evt, function () { render(true); });
    });

    render(true);
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.pagination = { initSimple: initSimple, initFiltered: initFiltered };
})();
