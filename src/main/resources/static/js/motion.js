/*
 * motion.js — Movimiento sutil en todo el sitio (se carga desde
 * fragments/seguridad.html):
 *  - Los bloques principales de cada página aparecen con un leve deslizamiento
 *    al entrar en pantalla (al cargar y al hacer scroll).
 *  - Sidebar: entrada escalonada del menú la primera vez de la sesión,
 *    desvanecido arriba/abajo cuando el menú tiene scroll y tooltips con el
 *    nombre de cada sección cuando el sidebar está colapsado.
 * Respeta "reducir movimiento" del sistema operativo.
 */
(function () {
  "use strict";
  if (window.__sbaiMotion) return;
  window.__sbaiMotion = true;

  var reducir = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  function sidebar() {
    var sb = document.querySelector(".sidebar");
    if (!sb) return;
    var nav = sb.querySelector(".sidebar-nav");
    sb.querySelectorAll(".sidebar-nav-item").forEach(function (a, i) {
      a.style.setProperty("--i", i);
      var etiqueta = a.querySelector(".sidebar-nav-item-label");
      if (etiqueta && !a.getAttribute("data-tip")) a.setAttribute("data-tip", etiqueta.textContent.trim());
    });
    try {
      if (!reducir && !sessionStorage.getItem("sbai-sb-intro")) {
        sb.classList.add("sb-intro");
        sessionStorage.setItem("sbai-sb-intro", "1");
        setTimeout(function () { sb.classList.remove("sb-intro"); }, 1400);
      }
    } catch (e) { /* sin sessionStorage */ }
    if (nav) {
      var bordes = function () {
        nav.classList.toggle("has-top", nav.scrollTop > 4);
        nav.classList.toggle("has-bottom", nav.scrollTop + nav.clientHeight < nav.scrollHeight - 4);
      };
      nav.addEventListener("scroll", bordes, { passive: true });
      window.addEventListener("resize", bordes);
      bordes();
      // El activo siempre visible si el menú es largo.
      var activo = nav.querySelector(".sidebar-nav-item.active");
      if (activo && activo.offsetTop > nav.clientHeight - 40) nav.scrollTop = activo.offsetTop - nav.clientHeight / 2;
    }
  }

  function revelar() {
    if (reducir || !("IntersectionObserver" in window)) return;
    var main = document.querySelector(".page-content");
    if (!main) return;
    var bloques = Array.prototype.filter.call(main.children, function (el) {
      return !el.matches("script, template, style, .cx-app, [hidden]") && el.offsetParent !== null;
    });
    if (!bloques.length) return;
    var io = new IntersectionObserver(function (entradas) {
      entradas.forEach(function (e) {
        if (!e.isIntersecting) return;
        e.target.classList.add("mv-in");
        io.unobserve(e.target);
        // Al terminar se quita la clase de animación: sin transform residual.
        setTimeout(function () { e.target.classList.remove("mv", "mv-in"); e.target.style.removeProperty("--mv-i"); }, 900);
      });
    }, { rootMargin: "0px 0px -6% 0px", threshold: 0.01 });
    var visibles = 0;
    bloques.forEach(function (el) {
      var r = el.getBoundingClientRect();
      el.style.setProperty("--mv-i", r.top < window.innerHeight ? visibles++ : 0);
      el.classList.add("mv");
      io.observe(el);
    });
  }

  function iniciar() { sidebar(); revelar(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar); else iniciar();
})();
