/**
 * theme-toggle.js — Interruptor de modo claro/oscuro para las páginas que
 * NO usan fragments/topbar.html + topbar-actions.js (ese par ya trae su
 * propia lógica de tema, sin cambios, para no arriesgar las vistas que ya
 * funcionan en producción).
 *
 * Usan este archivo:
 *  - Páginas públicas/de acceso sin topbar (login, registro, recuperar y
 *    restablecer contraseña, landing): el botón #theme-toggle-btn vive
 *    directo en su HTML.
 *  - Páginas legacy armadas por shell.js (asistente-ia.html de los 4 roles,
 *    proyecto-detalle.html): el botón se inyecta dinámicamente dentro del
 *    topbar generado por shell.js, así que este script usa DELEGACIÓN DE
 *    EVENTOS (document.addEventListener en vez de buscar el botón por id
 *    al cargar) para que el clic funcione sin importar si el botón ya
 *    existía en el DOM o lo agregó shell.js después.
 */
(function () {
  "use strict";

  var THEME_KEY = "sbai-theme";

  function aplicarTema(tema) {
    if (tema === "dark") {
      document.documentElement.setAttribute("data-theme", "dark");
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
    var etiqueta = tema === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro";
    var botones = document.querySelectorAll("#theme-toggle-btn");
    for (var i = 0; i < botones.length; i++) {
      botones[i].setAttribute("aria-label", etiqueta);
    }
  }

  function temaActual() {
    return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
  }

  function initTema() {
    var guardado = null;
    try { guardado = window.localStorage.getItem(THEME_KEY); } catch (e) { /* localStorage no disponible */ }
    aplicarTema(guardado === "dark" ? "dark" : "light");
  }

  // Se aplica de inmediato (no espera DOMContentLoaded) para evitar un
  // parpadeo de tema claro antes de pintar la pagina.
  initTema();

  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest("#theme-toggle-btn");
    if (!btn) return;
    var nuevo = temaActual() === "dark" ? "light" : "dark";
    aplicarTema(nuevo);
    try { window.localStorage.setItem(THEME_KEY, nuevo); } catch (e2) { /* ignorar */ }
  });
})();
