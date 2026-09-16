/**
 * table-filter.js — Motor genérico de filtros para tablas y listas.
 *
 * Marcado esperado:
 *   Controles:  <select data-filter-scope="asignaciones" data-filter-key="estado">
 *               <input  data-filter-scope="asignaciones" data-filter-key="q">
 *               <button data-filter-scope="notif" data-filter-key="tab" data-filter-value="alertas">
 *   Filas:      <tr data-filter-scope="asignaciones" data-f-estado="Validada" data-f-search="mariana ruiz portal andes">
 *   Vacío:      <div data-filter-empty="asignaciones" hidden>...</div>
 *
 * Un valor de control igual a "todos" (o vacío) significa "sin filtro".
 * Se pueden registrar predicados a medida por clave (p.ej. "no leídas")
 * con registerCustomMatcher.
 */
(function () {
  "use strict";

  var state = {};      // { scope: { key: value } }
  var customMatchers = {}; // { "scope:key": fn(rowEl, value) }

  function normalize(str) {
    return (str || "")
      .toString()
      .toLowerCase()
      .replace(/[áàä]/g, "a").replace(/[éèë]/g, "e").replace(/[íìï]/g, "i")
      .replace(/[óòö]/g, "o").replace(/[úùü]/g, "u").replace(/ñ/g, "n");
  }

  function registerCustomMatcher(scope, key, fn) {
    customMatchers[scope + ":" + key] = fn;
  }

  function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

  function rowMatches(row, scope) {
    var filters = state[scope] || {};
    for (var key in filters) {
      if (!filters.hasOwnProperty(key)) continue;
      var value = filters[key];
      if (value === undefined || value === null || value === "" || value === "todos" || value === "todas" || value === "all") continue;
      var custom = customMatchers[scope + ":" + key];
      if (custom) {
        if (!custom(row, value)) return false;
        continue;
      }
      if (key === "q") {
        var haystack = normalize(row.dataset.fSearch || row.textContent);
        if (haystack.indexOf(normalize(value)) === -1) return false;
      } else {
        var attr = "f" + capitalize(key);
        var rowVal = row.dataset[attr];
        if (rowVal !== value) return false;
      }
    }
    return true;
  }

  function applyScope(scope) {
    var rows = document.querySelectorAll('[data-filter-scope="' + scope + '"][data-f-search], [data-filter-scope="' + scope + '"].filter-row');
    // También aceptar filas marcadas explícitamente con data-filter-row="scope"
    var explicit = document.querySelectorAll('[data-filter-row="' + scope + '"]');
    var all = rows.length ? rows : explicit;
    var visible = 0;
    all.forEach(function (row) {
      var match = rowMatches(row, scope);
      row.hidden = !match;
      if (match) visible++;
    });
    var empty = document.querySelector('[data-filter-empty="' + scope + '"]');
    if (empty) empty.hidden = visible !== 0;
    var countEl = document.querySelector('[data-filter-count="' + scope + '"]');
    if (countEl) countEl.textContent = String(visible);
    return visible;
  }

  function setFilter(scope, key, value) {
    state[scope] = state[scope] || {};
    state[scope][key] = value;
    applyScope(scope);
  }

  function initScope(scope) {
    applyScope(scope);
  }

  function init(root) {
    root = root || document;
    var scopes = {};

    root.querySelectorAll("[data-filter-scope][data-filter-key]").forEach(function (ctrl) {
      var scope = ctrl.dataset.filterScope;
      scopes[scope] = true;
      if (ctrl.dataset.filterBound) return;
      ctrl.dataset.filterBound = "1";
      var key = ctrl.dataset.filterKey;

      if (ctrl.tagName === "BUTTON") {
        var value = ctrl.dataset.filterValue || "todos";
        ctrl.addEventListener("click", function () {
          // Resaltar el botón activo entre hermanos del mismo grupo
          var group = document.querySelectorAll(
            '[data-filter-scope="' + scope + '"][data-filter-key="' + key + '"]'
          );
          group.forEach(function (b) { b.classList.remove("active"); });
          ctrl.classList.add("active");
          setFilter(scope, key, value);
        });
      } else {
        var evt = ctrl.tagName === "SELECT" ? "change" : "input";
        ctrl.addEventListener(evt, function () { setFilter(scope, key, ctrl.value); });
      }
    });

    Object.keys(scopes).forEach(initScope);
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.tableFilter = {
    init: init,
    setFilter: setFilter,
    applyScope: applyScope,
    registerCustomMatcher: registerCustomMatcher,
    normalize: normalize
  };
})();
