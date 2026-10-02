/**
 * global-search.js — Buscador global predictivo del topbar.
 * Consulta /cuenta/buscar desde 2 letras (con debounce), agrupa resultados
 * por tipo, resalta la coincidencia y se maneja con teclado
 * (flechas, Enter, Esc). Ctrl/Cmd+K enfoca el campo desde cualquier pantalla.
 */
(function () {
  "use strict";
  var MIN = 2, DEBOUNCE = 140;

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) {
    return String(s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  }
  // Resalta la coincidencia respetando tildes (busca sobre texto normalizado, corta sobre el original).
  function resaltar(texto, q) {
    var t = String(texto || ""), i = norm(t).indexOf(norm(q));
    if (i < 0 || !q) return esc(t);
    return esc(t.slice(0, i)) + "<mark>" + esc(t.slice(i, i + q.length)) + "</mark>" + esc(t.slice(i + q.length));
  }
  function icono(id) {
    return '<svg class="icon icon-sm" aria-hidden="true"><use href="/img/icons.svg#' + id + '"></use></svg>';
  }

  document.addEventListener("DOMContentLoaded", function () {
    var root = document.querySelector("[data-global-search]");
    if (!root) return;
    var input = root.querySelector(".gs-input");
    var panel = root.querySelector(".gs-panel");
    var clearBtn = root.querySelector(".gs-clear");
    var items = [], active = -1, timer = null, reqId = 0;

    function abrir() { panel.hidden = false; root.classList.add("open"); input.setAttribute("aria-expanded", "true"); }
    function cerrar() { panel.hidden = true; root.classList.remove("open"); input.setAttribute("aria-expanded", "false"); active = -1; }

    function pintar(data, q) {
      items = data;
      if (!data.length) {
        panel.innerHTML = '<div class="gs-empty">' + icono("icon-search") +
          '<span>Sin resultados para “' + esc(q) + '”. Prueba con otras letras.</span></div>';
        abrir(); return;
      }
      var html = "", grupo = null;
      data.forEach(function (it, i) {
        if (it.tipo !== grupo) { grupo = it.tipo; html += '<div class="gs-group">' + esc(grupo) + "</div>"; }
        html += '<a class="gs-item" role="option" id="gs-opt-' + i + '" data-i="' + i + '" href="' + esc(it.url) + '">' +
          '<span class="gs-item-icon">' + icono(it.icono) + "</span>" +
          '<span class="gs-item-text"><span class="gs-item-title">' + resaltar(it.titulo, q) + "</span>" +
          '<span class="gs-item-sub">' + esc(it.detalle) + "</span></span>" +
          '<span class="gs-item-go">↵</span></a>';
      });
      panel.innerHTML = html;
      active = 0; marcar();
      abrir();
    }

    function marcar() {
      panel.querySelectorAll(".gs-item").forEach(function (el, i) {
        var on = i === active;
        el.classList.toggle("active", on);
        if (on) { input.setAttribute("aria-activedescendant", el.id); el.scrollIntoView({ block: "nearest" }); }
      });
    }

    function buscar(q) {
      var mine = ++reqId;
      panel.innerHTML = '<div class="gs-loading"><span class="gs-spinner"></span>Buscando…</div>';
      abrir();
      fetch("/cuenta/buscar?q=" + encodeURIComponent(q), { credentials: "same-origin", headers: { Accept: "application/json" } })
        .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
        .then(function (data) { if (mine === reqId) pintar(data, q); })
        .catch(function () {
          if (mine !== reqId) return;
          panel.innerHTML = '<div class="gs-empty"><span>No se pudo buscar. Verifica tu sesión e inténtalo de nuevo.</span></div>';
        });
    }

    input.addEventListener("input", function () {
      var q = input.value.trim();
      clearBtn.hidden = !input.value;
      clearTimeout(timer);
      if (q.length < MIN) {
        if (!q.length) { cerrar(); return; }
        panel.innerHTML = '<div class="gs-hint">Escribe al menos ' + MIN + ' letras para buscar.</div>'; abrir(); return;
      }
      timer = setTimeout(function () { buscar(q); }, DEBOUNCE);
    });
    input.addEventListener("focus", function () { if (input.value.trim().length >= MIN && panel.innerHTML) abrir(); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        if (!items.length || panel.hidden) return;
        e.preventDefault();
        active = (active + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length; marcar();
      } else if (e.key === "Enter") {
        var el = panel.querySelector(".gs-item.active");
        if (el) { e.preventDefault(); window.location.href = el.getAttribute("href"); }
      } else if (e.key === "Escape") { cerrar(); input.blur(); }
    });
    panel.addEventListener("mousemove", function (e) {
      var el = e.target.closest(".gs-item"); if (!el) return;
      active = +el.dataset.i; marcar();
    });
    clearBtn.addEventListener("click", function () { input.value = ""; clearBtn.hidden = true; cerrar(); input.focus(); });
    document.addEventListener("mousedown", function (e) { if (!root.contains(e.target)) cerrar(); });
    document.addEventListener("keydown", function (e) {
      if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) { e.preventDefault(); input.focus(); input.select(); }
      else if (e.key === "/" && !/^(INPUT|TEXTAREA|SELECT)$/.test((document.activeElement || {}).tagName || "")) { e.preventDefault(); input.focus(); }
    });
  });
})();
