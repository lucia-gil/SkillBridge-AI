/**
 * rm-inicio.js — Inicio del Resource Manager.
 *  - Pestañas (ui-kit.js): "Gestión de la banca" / "Alertas de carga" / "Se liberan pronto".
 *  - Carrusel de disponibles: flechas en su propia barra, contador "1–3 de 4";
 *    avanza de a una vista completa y oculta las flechas si todo cabe.
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
      var count = car.querySelector("[data-rmi-count]");
      if (!track) return;
      var tarjetas = Array.prototype.slice.call(track.children);

      // Avanza una "vista" completa: las tarjetas que se ven ahora.
      function paso() { return track.clientWidth + parseFloat(getComputedStyle(track).columnGap || 12); }

      function actualizar() {
        var max = track.scrollWidth - track.clientWidth - 2;
        var cabe = max <= 0;
        car.classList.toggle("is-static", cabe);
        if (prev) prev.disabled = track.scrollLeft <= 2;
        if (next) next.disabled = track.scrollLeft >= max;
        if (!count || !tarjetas.length) return;
        // Tarjetas visibles de punta a punta (con 2 px de tolerancia).
        var caja = track.getBoundingClientRect();
        var visibles = tarjetas.map(function (t, i) {
          var r = t.getBoundingClientRect();
          return r.left >= caja.left - 2 && r.right <= caja.right + 2 ? i + 1 : 0;
        }).filter(Boolean);
        var total = tarjetas.length;
        var texto = total + (total === 1 ? " persona" : " personas");
        if (!cabe && visibles.length) {
          var desde = visibles[0], hasta = visibles[visibles.length - 1];
          texto = (desde === hasta ? desde : desde + "–" + hasta) + " de " + total + " personas";
        }
        count.textContent = texto;
      }

      if (prev) prev.addEventListener("click", function () { track.scrollBy({ left: -paso(), behavior: "smooth" }); });
      if (next) next.addEventListener("click", function () { track.scrollBy({ left: paso(), behavior: "smooth" }); });
      var espera;
      track.addEventListener("scroll", function () { clearTimeout(espera); espera = setTimeout(actualizar, 60); }, { passive: true });
      window.addEventListener("resize", actualizar);
      if (window.ResizeObserver) new ResizeObserver(actualizar).observe(track);
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
