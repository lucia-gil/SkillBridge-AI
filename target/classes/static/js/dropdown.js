/**
 * dropdown.js — Popovers genéricos (menú de usuario, campana de
 * notificaciones, menús "···" de acciones). Los filtros de tabla usan
 * <select> nativos; este módulo es solo para paneles flotantes.
 *
 * Marcado esperado:
 *   <div class="dropdown">
 *     <button data-dropdown-trigger="id">...</button>
 *     <div class="dropdown-panel ..." data-dropdown-panel="id">...</div>
 *   </div>
 */
(function () {
  "use strict";

  var openPanels = {};

  function closeAll(exceptId) {
    Object.keys(openPanels).forEach(function (id) {
      if (id === exceptId) return;
      var panel = openPanels[id];
      panel.classList.remove("open");
      var trigger = document.querySelector('[data-dropdown-trigger="' + id + '"]');
      if (trigger) trigger.setAttribute("aria-expanded", "false");
    });
  }

  function toggle(id) {
    var panel = document.querySelector('[data-dropdown-panel="' + id + '"]');
    if (!panel) return;
    var isOpen = panel.classList.contains("open");
    closeAll(isOpen ? null : id);
    panel.classList.toggle("open", !isOpen);
    var trigger = document.querySelector('[data-dropdown-trigger="' + id + '"]');
    if (trigger) trigger.setAttribute("aria-expanded", String(!isOpen));
    openPanels[id] = panel;
  }

  function close(id) {
    var panel = document.querySelector('[data-dropdown-panel="' + id + '"]');
    if (panel) panel.classList.remove("open");
  }

  function init(root) {
    root = root || document;
    root.querySelectorAll("[data-dropdown-trigger]").forEach(function (btn) {
      if (btn.dataset.dropdownBound) return;
      btn.dataset.dropdownBound = "1";
      btn.setAttribute("aria-expanded", "false");
      btn.addEventListener("click", function (e) {
        e.stopPropagation();
        toggle(btn.dataset.dropdownTrigger);
      });
    });
    root.querySelectorAll("[data-dropdown-panel]").forEach(function (panel) {
      openPanels[panel.dataset.dropdownPanel] = panel;
    });
  }

  // Cierra los paneles abiertos al hacer clic fuera de ellos. Importante:
  // NO usamos stopPropagation() dentro del panel (como en una versión
  // anterior) porque eso impedía que los clics en botones de acción del
  // panel (Marcar leídas, Cerrar sesión) llegaran al delegado de eventos
  // de shell.js en document.body — los dejaba inertes.
  document.addEventListener("click", function (e) {
    if (e.target.closest && e.target.closest("[data-dropdown-panel]")) return;
    closeAll(null);
  });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeAll(null); });

  window.SBAI = window.SBAI || {};
  window.SBAI.dropdown = { init: init, toggle: toggle, close: close, closeAll: closeAll };
})();
