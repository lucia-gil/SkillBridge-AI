/**
 * multiselect.js — Buscador con selección múltiple y lista con scroll
 * (RG-225 / RG-245). Convierte cualquier
 *
 *   <select multiple data-multiselect data-placeholder="Todos los proyectos"
 *           data-noun="proyectos">…</select>
 *
 * en un desplegable con buscador, casillas, "Seleccionar todo / Limpiar" y
 * chips con lo elegido. El <select> original queda oculto y sincronizado, así
 * que los formularios GET/POST lo envían igual que siempre (un parámetro
 * repetido por cada opción marcada). Sin dependencias.
 */
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim(); }

  var abierto = null;
  document.addEventListener("mousedown", function (e) {
    if (abierto && !abierto.contains(e.target)) cerrar(abierto);
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && abierto) { var w = abierto; cerrar(w); w.querySelector(".ms-trigger").focus(); }
  });
  function cerrar(wrap) {
    wrap.classList.remove("open");
    wrap.querySelector(".ms-panel").hidden = true;
    wrap.querySelector(".ms-trigger").setAttribute("aria-expanded", "false");
    if (abierto === wrap) abierto = null;
  }

  function mejorar(sel) {
    if (sel.dataset.msDone) return;
    sel.dataset.msDone = "1";
    var placeholder = sel.dataset.placeholder || "Todos";
    var noun = sel.dataset.noun || "opciones";
    var opciones = Array.prototype.slice.call(sel.options).filter(function (o) { return o.value !== ""; });

    var wrap = document.createElement("div");
    wrap.className = "ms-wrap";
    sel.parentNode.insertBefore(wrap, sel);
    wrap.appendChild(sel);
    sel.classList.add("ms-native");
    sel.tabIndex = -1;
    sel.setAttribute("aria-hidden", "true");

    var trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "select ms-trigger";
    trigger.setAttribute("aria-haspopup", "listbox");
    trigger.setAttribute("aria-expanded", "false");
    wrap.appendChild(trigger);

    var panel = document.createElement("div");
    panel.className = "ms-panel";
    panel.hidden = true;
    panel.innerHTML =
      '<div class="ms-search"><svg class="icon icon-sm" aria-hidden="true"><use href="/img/icons.svg#icon-search"></use></svg>' +
      '<input type="search" class="ms-input" placeholder="Buscar ' + esc(noun) + '…" autocomplete="off"></div>' +
      '<div class="ms-tools"><button type="button" data-ms-all>Seleccionar todo</button><button type="button" data-ms-none>Limpiar</button></div>' +
      '<div class="ms-list" role="listbox" aria-multiselectable="true">' +
      opciones.map(function (o, i) {
        return '<label class="ms-option" data-i="' + i + '" data-hay="' + esc(norm(o.text)) + '">' +
          '<input type="checkbox" value="' + esc(o.value) + '"' + (o.selected ? " checked" : "") + '>' +
          '<span>' + esc(o.text) + "</span></label>";
      }).join("") +
      '<div class="ms-empty" hidden>Sin coincidencias.</div></div>';
    wrap.appendChild(panel);

    var chips = document.createElement("div");
    chips.className = "ms-chips";
    wrap.appendChild(chips);

    var input = panel.querySelector(".ms-input");
    var checks = Array.prototype.slice.call(panel.querySelectorAll(".ms-option input"));

    function pintar() {
      var marcadas = opciones.filter(function (o) { return o.selected; });
      if (!marcadas.length) {
        trigger.innerHTML = '<span class="ms-placeholder">' + esc(placeholder) + "</span>";
      } else if (marcadas.length === 1) {
        trigger.innerHTML = "<span>" + esc(marcadas[0].text) + "</span>";
      } else {
        trigger.innerHTML = "<span>" + marcadas.length + " " + esc(noun) + ' seleccionados</span>';
      }
      chips.innerHTML = marcadas.length > 1 ? marcadas.map(function (o) {
        return '<span class="ms-chip">' + esc(o.text) + '<button type="button" aria-label="Quitar ' + esc(o.text) +
          '" data-ms-remove="' + esc(o.value) + '">×</button></span>';
      }).join("") : "";
    }
    function sync() {
      checks.forEach(function (c, i) { opciones[i].selected = c.checked; });
      sel.dispatchEvent(new Event("change", { bubbles: true }));
      pintar();
    }

    trigger.addEventListener("click", function () {
      if (wrap.classList.contains("open")) { cerrar(wrap); return; }
      if (abierto) cerrar(abierto);
      wrap.classList.add("open");
      panel.hidden = false;
      trigger.setAttribute("aria-expanded", "true");
      abierto = wrap;
      input.value = ""; input.dispatchEvent(new Event("input"));
      setTimeout(function () { input.focus(); }, 0);
    });
    input.addEventListener("input", function () {
      var q = norm(input.value), vis = 0;
      panel.querySelectorAll(".ms-option").forEach(function (l) {
        l.hidden = q && l.dataset.hay.indexOf(q) === -1;
        if (!l.hidden) vis++;
      });
      panel.querySelector(".ms-empty").hidden = vis !== 0;
    });
    input.addEventListener("keydown", function (e) { if (e.key === "Enter") e.preventDefault(); });
    panel.addEventListener("change", sync);
    panel.querySelector("[data-ms-all]").addEventListener("click", function () {
      panel.querySelectorAll(".ms-option").forEach(function (l) { if (!l.hidden) l.querySelector("input").checked = true; });
      sync();
    });
    panel.querySelector("[data-ms-none]").addEventListener("click", function () {
      checks.forEach(function (c) { c.checked = false; });
      sync();
    });
    chips.addEventListener("click", function (e) {
      var b = e.target.closest("[data-ms-remove]");
      if (!b) return;
      checks.forEach(function (c) { if (c.value === b.dataset.msRemove) c.checked = false; });
      sync();
    });
    pintar();
  }

  function init(root) {
    (root || document).querySelectorAll("select[multiple][data-multiselect]").forEach(mejorar);
  }
  window.SBAI.multiselect = { init: init };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { init(); });
  else init();
})();
