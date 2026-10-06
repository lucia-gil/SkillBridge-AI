/**
 * ui-kit.js — Piezas de interfaz compartidas (modelo v7, fase 5).
 *
 *  SBAI.uiKit.init(raiz)  inicializa dentro de "raiz" (p.ej. un modal recién abierto):
 *   - Pestañas:   [data-tabs] con botones [data-tab="x"] y paneles [data-tab-panel="x"].
 *   - Tabla mini: [data-mini-table] con filas [data-mini-row] (data-buscar="texto"),
 *                 buscador [data-mini-search] y paginación [data-mini-pag] dentro del
 *                 mismo contenedor [data-mini]. Tamaño de página: data-page-size (6).
 *
 * Todo es del lado del cliente porque el equipo de UN proyecto es acotado;
 * las listas grandes (personas de la organización) se buscan en el servidor
 * (selector-personas.js).
 */
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};

  function norm(t) {
    return String(t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim();
  }

  function initTabs(raiz) {
    raiz.querySelectorAll("[data-tabs]").forEach(function (barra) {
      if (barra.dataset.tabsListo) return;
      barra.dataset.tabsListo = "1";
      var contenedor = barra.parentElement;
      var botones = Array.prototype.slice.call(barra.querySelectorAll("[data-tab]"));
      function activar(nombre) {
        botones.forEach(function (b) {
          var on = b.dataset.tab === nombre;
          b.classList.toggle("active", on);
          b.setAttribute("aria-selected", on ? "true" : "false");
          b.tabIndex = on ? 0 : -1;
        });
        contenedor.querySelectorAll("[data-tab-panel]").forEach(function (p) {
          p.hidden = p.dataset.tabPanel !== nombre;
        });
      }
      botones.forEach(function (b, i) {
        b.setAttribute("role", "tab");
        b.addEventListener("click", function () { activar(b.dataset.tab); });
        b.addEventListener("keydown", function (e) {
          if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
          var j = (i + (e.key === "ArrowRight" ? 1 : -1) + botones.length) % botones.length;
          botones[j].focus();
          activar(botones[j].dataset.tab);
        });
      });
      var inicial = botones.filter(function (b) { return b.classList.contains("active"); })[0] || botones[0];
      if (inicial) activar(inicial.dataset.tab);
    });
  }

  function initMini(raiz) {
    raiz.querySelectorAll("[data-mini]").forEach(function (bloque) {
      if (bloque.dataset.miniListo) return;
      bloque.dataset.miniListo = "1";
      var filas = Array.prototype.slice.call(bloque.querySelectorAll("[data-mini-row]"));
      var buscador = bloque.querySelector("[data-mini-search]");
      var pag = bloque.querySelector("[data-mini-pag]");
      var vacio = bloque.querySelector("[data-mini-empty]");
      var tam = Number(bloque.dataset.pageSize) || 6;
      var pagina = 1;
      function pintar() {
        var q = norm(buscador ? buscador.value : "");
        var visibles = filas.filter(function (f) { return !q || norm(f.dataset.buscar || f.textContent).indexOf(q) !== -1; });
        var paginas = Math.max(1, Math.ceil(visibles.length / tam));
        pagina = Math.min(pagina, paginas);
        filas.forEach(function (f) { f.hidden = true; });
        visibles.slice((pagina - 1) * tam, pagina * tam).forEach(function (f) { f.hidden = false; });
        if (vacio) vacio.hidden = visibles.length !== 0;
        if (pag) {
          pag.innerHTML = paginas > 1
            ? '<button type="button" class="page-btn" data-mp="-1"' + (pagina <= 1 ? " disabled" : "") + ' aria-label="Página anterior">‹</button>' +
              '<span class="text-tertiary">' + pagina + " / " + paginas + "</span>" +
              '<button type="button" class="page-btn" data-mp="1"' + (pagina >= paginas ? " disabled" : "") + ' aria-label="Página siguiente">›</button>'
            : "";
        }
      }
      if (buscador) buscador.addEventListener("input", function () { pagina = 1; pintar(); });
      if (pag) pag.addEventListener("click", function (e) {
        var b = e.target.closest("[data-mp]");
        if (!b) return;
        pagina += Number(b.dataset.mp);
        pintar();
      });
      pintar();
    });
  }

  window.SBAI.uiKit = {
    init: function (raiz) {
      raiz = raiz || document;
      initTabs(raiz);
      initMini(raiz);
    }
  };
})();
