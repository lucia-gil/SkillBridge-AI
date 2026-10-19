/**
 * tabla-acciones.js — Botones de la columna "Acciones" más claros (RG-227).
 * Solo cambia la presentación: agrega un ícono según el texto del botón
 * ("Editar", "Eliminar", "Ver detalle"…). No toca ningún listener ni la
 * lógica de cada acción. También cubre tablas dentro de modales.
 */
(function () {
  "use strict";
  var ICONOS = [
    [/^ver perfil/i, "icon-user"], [/^ver|^abrir|^revisar/i, "icon-eye"], [/^editar|^cambiar/i, "icon-edit"],
    [/^eliminar|^borrar/i, "icon-trash"], [/^suspender/i, "icon-user-x"], [/^reactivar|^activar/i, "icon-user-check"],
    [/^finalizar|^revocar|^quitar/i, "icon-x"], [/^guardar/i, "icon-check"], [/^validar|^aprobar/i, "icon-check-circle"],
    [/^asignar/i, "icon-user-check"], [/^descargar/i, "icon-download"], [/^rebalancear|^ajustar/i, "icon-sliders"]
  ];
  function decorar(root) {
    (root || document).querySelectorAll(".cell-actions > button, .cell-actions > a").forEach(function (el) {
      if (el.dataset.accionDeco || el.querySelector("svg")) { el.dataset.accionDeco = "1"; return; }
      el.dataset.accionDeco = "1";
      var texto = (el.textContent || "").trim();
      for (var i = 0; i < ICONOS.length; i++) {
        if (ICONOS[i][0].test(texto)) {
          el.insertAdjacentHTML("afterbegin", '<svg class="icon icon-sm" aria-hidden="true"><use href="/img/icons.svg#' + ICONOS[i][1] + '"></use></svg>');
          break;
        }
      }
    });
  }
  function init() {
    decorar(document);
    new MutationObserver(function (ms) {
      ms.forEach(function (m) { m.addedNodes.forEach(function (n) { if (n.nodeType === 1) decorar(n); }); });
    }).observe(document.body, { childList: true, subtree: true });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
})();
