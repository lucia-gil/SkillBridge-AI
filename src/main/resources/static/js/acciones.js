/**
 * acciones.js — Comportamientos que antes iban como atributos onclick /
 * onchange / onerror en el HTML. La Content-Security-Policy no permite
 * JavaScript en atributos (script-src-attr 'none'), así que se declaran con
 * data-* y se manejan aquí, para todas las páginas:
 *   data-imprimir             → botón que abre el diálogo de impresión.
 *   data-confirmar="texto"    → pide confirmación antes del clic/envío.
 *   data-autoenviar           → envía su formulario al cambiar.
 *   <img data-quitar-si-falla> → se quita si la imagen no carga.
 */
(function () {
  "use strict";
  document.addEventListener("click", function (e) {
    var el = e.target.closest ? e.target.closest("[data-imprimir], [data-confirmar]") : null;
    if (!el) return;
    if (el.hasAttribute("data-imprimir")) { e.preventDefault(); window.print(); return; }
    if (!window.confirm(el.getAttribute("data-confirmar"))) e.preventDefault();
  });
  document.addEventListener("change", function (e) {
    var el = e.target;
    if (el && el.hasAttribute && el.hasAttribute("data-autoenviar") && el.form) {
      if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
    }
  });
  // "error" no burbujea: se escucha en fase de captura (también imágenes agregadas después).
  document.addEventListener("error", function (e) {
    var el = e.target;
    if (el && el.tagName === "IMG" && el.hasAttribute("data-quitar-si-falla")) el.remove();
  }, true);
})();
