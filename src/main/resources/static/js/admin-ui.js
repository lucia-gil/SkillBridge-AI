
/*
 * admin-ui.js — mejoras de interacción exclusivamente para /administrador/*
 * No modifica endpoints, formularios, datos Thymeleaf ni lógica de negocio.
 */
(function () {
  "use strict";

  function enhanceModal(backdrop) {
    if (!backdrop || backdrop.dataset.adminEnhanced === "1") return;
    backdrop.dataset.adminEnhanced = "1";

    var dialog = backdrop.querySelector('[role="dialog"], [role="alertdialog"], .modal');
    if (!dialog) return;

    var focusable = function () {
      return Array.prototype.slice.call(dialog.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )).filter(function (el) {
        return el.offsetParent !== null;
      });
    };

    function trap(e) {
      if (e.key !== "Tab") return;
      var items = focusable();
      if (!items.length) return;
      var first = items[0], last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }

    dialog.addEventListener("keydown", trap);

    var close = dialog.querySelector("[data-modal-close]");
    if (close) close.setAttribute("aria-label", close.getAttribute("aria-label") || "Cerrar");

    setTimeout(function () {
      var first = focusable()[0];
      if (first) first.focus();
    }, 80);
  }

  function observeModals() {
    document.querySelectorAll(".modal-backdrop").forEach(enhanceModal);
    new MutationObserver(function (mutations) {
      mutations.forEach(function (mutation) {
        Array.prototype.forEach.call(mutation.addedNodes, function (node) {
          if (!node || node.nodeType !== 1) return;
          if (node.matches && node.matches(".modal-backdrop")) enhanceModal(node);
          if (node.querySelectorAll) node.querySelectorAll(".modal-backdrop").forEach(enhanceModal);
        });
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  function enhanceLoading() {
    document.addEventListener("submit", function (e) {
      var form = e.target;
      if (!form.closest || !form.closest(".admin-app") || e.defaultPrevented || !form.checkValidity()) return;
      var button = e.submitter;
      if (!button || button.hasAttribute("data-admin-no-loading") || button.dataset.adminLoading === "1") return;
      button.dataset.adminLoading = "1";
      button.setAttribute("aria-busy", "true");
      var spinner = document.createElement("span");
      spinner.className = "admin-loading-spinner";
      spinner.setAttribute("aria-hidden", "true");
      button.prepend(spinner);
      // No se deshabilita el botón antes del envío: el navegador aún lo necesita.
      window.setTimeout(function () { button.disabled = true; }, 0);
    });
    window.addEventListener("pageshow", function () {
      document.querySelectorAll(".admin-app [data-admin-loading]").forEach(function (button) {
        button.disabled = false;
        button.removeAttribute("aria-busy");
        delete button.dataset.adminLoading;
        var spinner = button.querySelector(".admin-loading-spinner");
        if (spinner) spinner.remove();
      });
    });
  }

  function enhanceTooltips() {
    document.querySelectorAll(".admin-app [aria-label]").forEach(function (el) {
      if (!el.hasAttribute("title") && el.classList.contains("topbar-icon-btn")) {
        el.setAttribute("title", el.getAttribute("aria-label"));
      }
    });
  }

  function enhanceStatus() {
    document.querySelectorAll(".admin-app .data-table tbody tr").forEach(function (row) {
      if (!row.hasAttribute("tabindex") && row.querySelector("a, button")) return;
      row.classList.add("admin-clickable-row");
    });
  }

  function enhanceLowDataStates() {
    document.querySelectorAll(".admin-app .card").forEach(function (card) {
      if (card.closest(".admin-workflow, [data-admin-compact]")) return;
      var body = card.querySelector(".data-table tbody");
      if (!body || card.querySelector(".admin-complementary-panel")) return;
      var rows = Array.prototype.slice.call(body.children).filter(function (row) { return !row.hidden; });
      if (rows.length > 3) return;
      card.classList.add("admin-low-data");
      var panel = document.createElement("aside");
      panel.className = "admin-complementary-panel";
      panel.setAttribute("aria-label", "Resumen de contenido");
      panel.innerHTML = '<span class="admin-complementary-panel__icon" aria-hidden="true">+</span>' +
        '<div><div class="admin-complementary-panel__title">Espacio listo para crecer</div>' +
        '<div class="admin-complementary-panel__copy">Usa las acciones de esta vista para completar el catálogo o revisar los registros disponibles.</div></div>' +
        '<span class="admin-complementary-panel__count">' + rows.length + ' registro' + (rows.length === 1 ? '' : 's') + '</span>';
      card.appendChild(panel);
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.documentElement.classList.add("admin-ui-ready");
    observeModals();
    enhanceLoading();
    enhanceTooltips();
    enhanceStatus();
    enhanceLowDataStates();
  });
})();
