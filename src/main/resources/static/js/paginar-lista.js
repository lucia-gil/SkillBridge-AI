/**
 * paginar-lista.js — Paginación simple en el cliente para tablas y listas
 * que no tenían una propia. Uso:
 *
 *   <table class="data-table" data-paginar="8">…</table>   (pagina las filas del tbody)
 *   <div data-paginar="6">…hijos…</div>                     (pagina los hijos directos)
 *
 * Dibuja debajo el mismo pie que el resto del sistema ("Mostrando X de Y" y
 * botones ‹ ›), y paginacion.js le agrega los números de página. Si hay
 * menos elementos que el tamaño de página no muestra nada.
 */
(function () {
  "use strict";
  var n = 0;

  function filas(el) {
    var cuerpo = el.tagName === "TABLE" ? el.tBodies[0] : el;
    if (!cuerpo) return [];
    return Array.prototype.filter.call(cuerpo.children, function (c) {
      return c.tagName !== "TEMPLATE" && !c.hasAttribute("data-pl-fijo");
    });
  }

  function montar(el) {
    if (el.dataset.plListo) return;
    el.dataset.plListo = "1";
    var tam = Math.max(1, parseInt(el.dataset.paginar, 10) || 8);
    var items = filas(el);
    if (items.length <= tam) return;
    var id = "pl" + (++n), pagina = 1, total = Math.ceil(items.length / tam);

    var pie = document.createElement("div");
    pie.className = "table-footer pl-footer";
    pie.innerHTML =
      '<span class="text-tertiary" style="font-size:var(--fs-xs)">Mostrando <span data-pl-vis>0</span> de ' + items.length + "</span>" +
      '<div class="pagination"><button class="page-btn" type="button" aria-label="Página anterior">‹</button>' +
      '<span class="text-tertiary" id="' + id + '-page-label" style="font-size:var(--fs-xs)">Página 1 de ' + total + "</span>" +
      '<button class="page-btn" type="button" aria-label="Página siguiente">›</button></div>';
    var ancla = el.closest(".table-scroll") || el;
    ancla.parentNode.insertBefore(pie, ancla.nextSibling);

    var btns = pie.querySelectorAll(".page-btn"), label = pie.querySelector("#" + id + "-page-label"), vis = pie.querySelector("[data-pl-vis]");
    function pintar() {
      var desde = (pagina - 1) * tam, hasta = desde + tam;
      items.forEach(function (it, i) { it.hidden = i < desde || i >= hasta; });
      btns[0].disabled = pagina <= 1;
      btns[1].disabled = pagina >= total;
      label.textContent = "Página " + pagina + " de " + total;
      vis.textContent = Math.min(hasta, items.length) - desde;
    }
    btns[0].addEventListener("click", function () { if (pagina > 1) { pagina--; pintar(); } });
    btns[1].addEventListener("click", function () { if (pagina < total) { pagina++; pintar(); } });
    pintar();
  }

  function init(root) { (root || document).querySelectorAll("[data-paginar]").forEach(montar); }
  window.SBAI = window.SBAI || {};
  window.SBAI.paginarLista = { init: init };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { init(); });
  else init();
})();
