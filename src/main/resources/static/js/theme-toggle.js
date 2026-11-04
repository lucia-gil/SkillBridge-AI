/**
 * theme-toggle.js — ÚNICO controlador del modo claro/oscuro de todo el sitio.
 *
 * - El tema guardado ya lo aplica, antes del primer pintado, un script de una
 *   línea en el <head> de cada plantilla (sin él, cada página se pintaba en
 *   claro y pasaba a oscuro al terminar de cargar: un destello que parecía
 *   que el modo oscuro "se perdía" al navegar o al volver atrás).
 * - Se carga desde fragments/seguridad.html (todas las páginas con topbar) y
 *   directamente en las páginas públicas; está protegido contra doble carga.
 * - Botón: cualquier #theme-toggle-btn o [data-theme-toggle] (delegación de
 *   eventos, funciona también si el botón se inserta después, p. ej. shell.js).
 * - Al cambiar, se desactivan las transiciones un instante: antes TODA la
 *   página animaba sus colores a la vez y el cambio se sentía trabado.
 * - Se resincroniza al volver con "atrás" (bfcache) y entre pestañas.
 */
(function () {
  "use strict";
  if (window.__sbaiTema) return;
  window.__sbaiTema = true;

  var THEME_KEY = "sbai-theme";
  var raiz = document.documentElement;

  function guardado() {
    try { return window.localStorage.getItem(THEME_KEY) === "dark" ? "dark" : "light"; } catch (e) { return "light"; }
  }
  function actual() { return raiz.getAttribute("data-theme") === "dark" ? "dark" : "light"; }

  function etiquetar(tema) {
    var texto = tema === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro";
    document.querySelectorAll("#theme-toggle-btn, [data-theme-toggle]").forEach(function (b) {
      b.setAttribute("aria-label", texto);
      b.setAttribute("title", texto);
      b.setAttribute("aria-pressed", tema === "dark" ? "true" : "false");
    });
  }

  function aplicar(tema, conTransicionCortada) {
    if (conTransicionCortada) raiz.classList.add("tema-cambiando");
    if (tema === "dark") raiz.setAttribute("data-theme", "dark");
    else raiz.removeAttribute("data-theme");
    etiquetar(tema);
    if (conTransicionCortada) {
      // Dos frames: el navegador pinta el tema nuevo sin animar y luego
      // vuelven las transiciones normales (hover, etc.).
      requestAnimationFrame(function () { requestAnimationFrame(function () { raiz.classList.remove("tema-cambiando"); }); });
    }
  }

  // Por si alguna página no tiene el script del <head>.
  if (actual() !== guardado()) aplicar(guardado(), false);
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { etiquetar(actual()); });
  else etiquetar(actual());

  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest("#theme-toggle-btn, [data-theme-toggle]");
    if (!btn) return;
    var nuevo = actual() === "dark" ? "light" : "dark";
    aplicar(nuevo, true);
    try { window.localStorage.setItem(THEME_KEY, nuevo); } catch (e2) { /* sin localStorage: solo esta página */ }
    btn.classList.remove("tt-anim"); void btn.offsetWidth; btn.classList.add("tt-anim");
  });

  // Volver con "atrás"/"adelante" desde la caché del navegador: la página
  // se restaura tal como estaba, aunque el tema se haya cambiado después.
  window.addEventListener("pageshow", function () { if (actual() !== guardado()) aplicar(guardado(), true); });
  // Otra pestaña cambió el tema.
  window.addEventListener("storage", function (e) { if (e.key === THEME_KEY) aplicar(guardado(), true); });

  window.SBAI = window.SBAI || {};
  window.SBAI.tema = { actual: actual, aplicar: function (t) { aplicar(t, true); try { localStorage.setItem(THEME_KEY, t); } catch (e) {} } };
})();
