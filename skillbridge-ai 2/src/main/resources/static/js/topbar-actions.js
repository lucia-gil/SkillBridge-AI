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

  function submitForm(action, fields) {
    var form = document.createElement("form");
    form.method = "POST";
    form.action = action;
    Object.keys(fields || {}).forEach(function (k) {
      var input = document.createElement("input");
      input.type = "hidden";
      input.name = k;
      input.value = fields[k];
      form.appendChild(input);
    });
    document.body.appendChild(form);
    form.submit();
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.submitForm = submitForm;

  document.addEventListener("DOMContentLoaded", function () {
    if (window.SBAI.dropdown) window.SBAI.dropdown.init(document);

    document.body.addEventListener("click", function (e) {
      if (e.target.closest('[data-action="logout"]')) {
        window.location.href = "/auth/logout";
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
