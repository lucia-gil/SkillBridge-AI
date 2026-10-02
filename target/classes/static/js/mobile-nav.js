/**
 * mobile-nav.js — Menú hamburguesa: en pantallas <= 860px el sidebar es un
 * drawer con fondo oscurecido. Se cierra con el fondo, Esc o al navegar.
 */
(function () {
  "use strict";
  document.addEventListener("DOMContentLoaded", function () {
    if (!document.querySelector(".sidebar")) return;
    var backdrop = document.createElement("div");
    backdrop.className = "sidebar-backdrop";
    document.body.appendChild(backdrop);
    function set(open) { document.body.classList.toggle("sidebar-open", open); }
    document.addEventListener("click", function (e) {
      if (e.target.closest('[data-action="toggle-sidebar"]')) { set(!document.body.classList.contains("sidebar-open")); return; }
      if (e.target === backdrop || e.target.closest(".sidebar-nav-item")) set(false);
    });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") set(false); });
    window.addEventListener("resize", function () { if (window.innerWidth > 860) set(false); });
  });
})();
