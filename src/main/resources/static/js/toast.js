/**
 * toast.js — Sistema de notificaciones flotantes (toasts).
 * Usado para dar feedback visible a acciones que no tienen una pantalla
 * de destino propia (regla de la sección 7: "ningún botón debe ser un
 * callejón sin salida").
 */
(function () {
  "use strict";

  var ICONS = { default: "icon-info", success: "icon-check-circle", error: "icon-alert-circle" };

  function region() {
    var el = document.getElementById("toast-region");
    if (!el) {
      el = document.createElement("div");
      el.id = "toast-region";
      el.className = "toast-region";
      el.setAttribute("role", "status");
      el.setAttribute("aria-live", "polite");
      document.body.appendChild(el);
    }
    return el;
  }

  function show(message, opts) {
    opts = opts || {};
    var type = opts.type || "default";
    var icon = opts.icon || ICONS[type] || ICONS.default;
    var el = document.createElement("div");
    el.className = "toast" + (type !== "default" ? " " + type : "");
    el.innerHTML =
      '<svg class="icon toast-icon"><use href="' + window.SBAI.state.STATIC + 'img/icons.svg#' + icon + '"></use></svg>' +
      '<div class="toast-text">' + message + "</div>" +
      '<button type="button" class="toast-close" aria-label="Cerrar"><svg class="icon icon-sm"><use href="' + window.SBAI.state.STATIC + 'img/icons.svg#icon-x"></use></svg></button>';
    region().appendChild(el);

    var timer = setTimeout(function () { dismiss(el); }, opts.duration || 4200);
    el.querySelector(".toast-close").addEventListener("click", function () {
      clearTimeout(timer);
      dismiss(el);
    });
    return el;
  }

  function dismiss(el) {
    if (!el || !el.parentNode) return;
    el.classList.add("leaving");
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 200);
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.toast = { show: show };
})();
