
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
    document.addEventListener("click", function (e) {
      var button = e.target.closest && e.target.closest(".admin-app button[type='submit'], .admin-app [data-admin-submit]");
      if (!button || button.disabled || button.dataset.loadingBound === "1") return;
      button.dataset.loadingBound = "1";

      var form = button.form;
      if (form && form.checkValidity && !form.checkValidity()) {
        button.dataset.loadingBound = "0";
        return;
      }

      var label = button.querySelector(".btn-label");
      if (!label) {
        label = document.createElement("span");
        label.className = "btn-label";
        while (button.firstChild) label.appendChild(button.firstChild);
        button.appendChild(label);
      }

      var spinner = document.createElement("span");
      spinner.className = "admin-loading-spinner";
      spinner.setAttribute("aria-hidden", "true");
      button.insertBefore(spinner, label);
      button.dataset.adminLoading = "1";
      button.setAttribute("aria-busy", "true");
      button.disabled = true;
    }, true);
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

  document.addEventListener("DOMContentLoaded", function () {
    document.documentElement.classList.add("admin-ui-ready");
    observeModals();
    enhanceLoading();
    enhanceTooltips();
    enhanceStatus();
  });
})();
