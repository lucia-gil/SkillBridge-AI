/**
 * rm-inicio.js — Inicio del Resource Manager.
 *  - Pestañas (ui-kit.js): "Gestión de la banca" / "Alertas de carga" / "Se liberan pronto".
 *  - Carrusel de disponibles con flechas (se ocultan en los extremos).
 *  - [data-rmi-go="banca"]: botón que cambia de pestaña.
 */
(function () {
  "use strict";
  document.addEventListener("DOMContentLoaded", function () {
    if (window.SBAI && window.SBAI.uiKit) window.SBAI.uiKit.init(document);

    document.querySelectorAll("[data-rmi-carousel]").forEach(function (car) {
      var track = car.querySelector("[data-rmi-track]");
      var prev = car.querySelector("[data-rmi-prev]");
      var next = car.querySelector("[data-rmi-next]");
      if (!track) return;
      function paso() {
        var tarjeta = track.firstElementChild;
        return tarjeta ? tarjeta.getBoundingClientRect().width + 12 : 240;
      }
      function actualizar() {
        var max = track.scrollWidth - track.clientWidth - 2;
        if (prev) prev.disabled = track.scrollLeft <= 2;
        if (next) next.disabled = track.scrollLeft >= max;
      }
      if (prev) prev.addEventListener("click", function () { track.scrollBy({ left: -paso(), behavior: "smooth" }); });
      if (next) next.addEventListener("click", function () { track.scrollBy({ left: paso(), behavior: "smooth" }); });
      track.addEventListener("scroll", actualizar, { passive: true });
      window.addEventListener("resize", actualizar);
      // El panel puede estar oculto al cargar (otra pestaña activa): recalcular al mostrarse.
      document.addEventListener("click", function (e) { if (e.target.closest("[data-tab]")) setTimeout(actualizar, 0); });
      actualizar();
    });

    document.addEventListener("click", function (e) {
      var go = e.target.closest("[data-rmi-go]");
      if (!go) return;
      var tab = document.querySelector('[data-tabs] [data-tab="' + go.getAttribute("data-rmi-go") + '"]');
      if (tab) { tab.click(); tab.focus(); }
    });
  });
})();
