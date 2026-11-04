/**
 * topbar-actions.js — Comportamiento del topbar/sidebar reales (Thymeleaf,
 * fragments/topbar.html + fragments/sidebar.html), compartido por TODAS las
 * páginas de Administrador y Colaborador reescritas en esta entrega.
 *
 * Reemplaza la parte de static/js/shell.js que manejaba data-action="logout"
 * y data-action="mark-all-read-quick" (ver esa función en shell.js, que
 * sigue existiendo tal cual para las páginas que aún no se migraron: otros
 * roles y asistente-ia.html). Aquí "Marcar leídas" desde la campanita del
 * topbar llama al endpoint real de NotificacionesController en vez de
 * limitarse a actualizar sessionStorage.
 *
 * Cada página que incluye este script puede definir, ANTES o DESPUÉS de
 * cargarlo, `window.SBAI_FLASH = { exito: ..., error: ... }` (con
 * th:inline="javascript") para que los toasts de éxito/error de flash
 * attributes se muestren automáticamente al cargar.
 */
(function () {
  "use strict";

  // Evita envíos dobles: si el usuario hace clic otra vez mientras la
  // página procesa (p. ej. crear un entregable), no se crea un duplicado.
  var enviando = false;
  function marcarEnviando() {
    document.querySelectorAll(".modal .modal-footer .btn-primary, .modal .modal-footer .btn-danger").forEach(function (b) {
      b.disabled = true;
      b.classList.add("is-loading");
      if (!b.dataset.textoOriginal) { b.dataset.textoOriginal = b.textContent; b.textContent = "Procesando…"; }
    });
  }
  document.addEventListener("submit", function (e) {
    var f = e.target;
    // Fase de burbuja: si otra validación ya canceló el envío, no se marca.
    if (e.defaultPrevented || !f || (f.method || "").toLowerCase() !== "post") return;
    if (f.dataset.enviado) { e.preventDefault(); return; }
    f.dataset.enviado = "1";
    setTimeout(function () {
      f.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (b) { b.disabled = true; });
    }, 0);
  });
  // Si el navegador restaura la página con "Atrás", se reactivan los botones.
  window.addEventListener("pageshow", function () {
    enviando = false;
    document.querySelectorAll("[data-texto-original]").forEach(function (b) {
      b.disabled = false; b.classList.remove("is-loading"); b.textContent = b.dataset.textoOriginal; delete b.dataset.textoOriginal;
    });
    document.querySelectorAll("form[data-enviado]").forEach(function (f) {
      delete f.dataset.enviado;
      f.querySelectorAll('button[type="submit"], input[type="submit"]').forEach(function (b) { b.disabled = false; });
    });
  });

  function submitForm(action, fields) {
    if (enviando) return;
    enviando = true;
    marcarEnviando();
    var form = document.createElement("form");
    form.method = "POST";
    form.action = action;
    Object.keys(fields || {}).forEach(function (k) {
      // Un arreglo se envía como varios campos con el mismo nombre
      // (p.ej. reincorporar=3&reincorporar=7 -> List<Long> en Spring).
      var valores = Array.isArray(fields[k]) ? fields[k] : [fields[k]];
      valores.forEach(function (v) {
        var input = document.createElement("input");
        input.type = "hidden";
        input.name = k;
        input.value = v;
        form.appendChild(input);
      });
    });
    document.body.appendChild(form);
    form.submit();
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.submitForm = submitForm;

  // El modo claro/oscuro lo maneja SOLO theme-toggle.js (cargado desde
  // fragments/seguridad.html): aquí ya no hay lógica de tema.

  document.addEventListener("DOMContentLoaded", function () {
    if (window.SBAI.dropdown) window.SBAI.dropdown.init(document);

    // Sombra suave en el topbar cuando la página ya no está arriba del todo.
    var topbar = document.querySelector(".topbar");
    if (topbar) {
      var marcar = function () { topbar.classList.toggle("is-scrolled", window.scrollY > 4); };
      window.addEventListener("scroll", marcar, { passive: true });
      marcar();
    }

    document.body.addEventListener("click", function (e) {
      if (e.target.closest('[data-action="logout"]')) {
        // Logout real: POST /auth/logout con token CSRF (Spring Security).
        (window.SBAI && window.SBAI.logout) ? window.SBAI.logout() : window.location.assign("/auth/login.html");
        return;
      }
      var markAllBtn = e.target.closest('[data-action="mark-all-read-quick"]');
      if (markAllBtn) {
        submitForm("/notificaciones/marcar-todas", { volver: window.location.pathname });
      }
    });

    var flash = window.SBAI_FLASH || {};
    if (flash.exito && window.SBAI.toast) window.SBAI.toast.show(flash.exito, { type: "success" });
    if (flash.error && window.SBAI.toast) window.SBAI.toast.show(flash.error, { type: "error" });
  });
})();
