/**
 * modal.js — Sistema de modales genérico + modal de selección de rol.
 *
 * SBAI.modal.open(innerHtml, opts)   → abre un modal a partir de un
 *                                       fragmento de HTML (el <div class="modal">…</div> completo).
 * SBAI.modal.closeTop()              → cierra el modal más reciente.
 * SBAI.modal.confirm({...})          → modal de confirmación reutilizable
 *                                       (usado por acciones de tabla:
 *                                       Suspender, Liberar, Rebalancear...).
 * SBAI.modal.openRolePicker(opts)    → popup de selección de rol (login,
 *                                       fin de registro, fin de recuperar).
 */
(function () {
  "use strict";

  var stack = [];

  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '"><use href="' + window.SBAI.state.STATIC + "img/icons.svg#" + name + '"></use></svg>';
  }

  function open(innerHtml, opts) {
    opts = opts || {};
    var backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";
    backdrop.innerHTML = innerHtml;
    document.body.appendChild(backdrop);
    document.body.style.overflow = "hidden";
    stack.push(backdrop);

    if (!opts.persistent) {
      backdrop.addEventListener("mousedown", function (e) {
        if (e.target === backdrop) close(backdrop);
      });
    }
    backdrop.querySelectorAll("[data-modal-close]").forEach(function (btn) {
      btn.addEventListener("click", function () { close(backdrop); });
    });
    document.addEventListener("keydown", escHandler);

    var firstField = backdrop.querySelector("input, select, textarea, button.btn-primary");
    if (firstField) setTimeout(function () { firstField.focus(); }, 60);

    if (opts.onMount) opts.onMount(backdrop);
    return backdrop;
  }

  function escHandler(e) {
    if (e.key === "Escape" && stack.length) close(stack[stack.length - 1]);
  }

  function close(backdrop) {
    backdrop = backdrop || stack[stack.length - 1];
    if (!backdrop) return;
    var idx = stack.indexOf(backdrop);
    if (idx >= 0) stack.splice(idx, 1);
    if (backdrop.parentNode) backdrop.parentNode.removeChild(backdrop);
    if (!stack.length) {
      document.body.style.overflow = "";
      document.removeEventListener("keydown", escHandler);
    }
  }

  function closeTop() { close(stack[stack.length - 1]); }

  /* ---------------- Confirmación genérica ---------------- */
  function confirm(opts) {
    opts = opts || {};
    var variant = opts.variant || "primary"; // primary | destructive
    var btnClass = variant === "destructive" ? "btn-destructive-solid" : "btn-primary";
    var html =
      '<div class="modal" role="dialog" aria-modal="true">' +
      '<div class="modal-header"><div><h2>' + opts.title + "</h2>" +
      (opts.desc ? "<p>" + opts.desc + "</p>" : "") +
      '</div><button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>" +
      '<div class="modal-body">' + (opts.bodyHtml || "") + "</div>" +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary" data-modal-close>' + (opts.cancelLabel || "Cancelar") + "</button>" +
      '<button type="button" class="btn ' + btnClass + '" data-confirm-action>' + (opts.confirmLabel || "Confirmar") + "</button>" +
      "</div></div>";
    var backdrop = open(html);
    backdrop.querySelector("[data-confirm-action]").addEventListener("click", function () {
      close(backdrop);
      if (opts.onConfirm) opts.onConfirm();
    });
    return backdrop;
  }

  /* ---------------- Selector de rol ---------------- */
  var ROLES = [
    { slug: "colaborador", icon: "icon-user", title: "Colaborador",
      desc: "Ve tu perfil, tus proyectos, foros y el asistente IA." },
    { slug: "project-manager", icon: "icon-target", title: "Project Manager",
      desc: "Gestiona proyectos, asignaciones de tu equipo, AI Talent Matching, foros y reportes." },
    { slug: "resource-manager", icon: "icon-layers", title: "Resource Manager",
      desc: "Ve ocupación y carga de todos los colaboradores, asignaciones globales y catálogo." },
    { slug: "administrador", icon: "icon-sliders", title: "Administrador",
      desc: "Usuarios y roles, catálogo de habilidades, proyectos, reportes globales y auditoría." }
  ];

  function openRolePicker() {
    var cards = ROLES.map(function (r) {
      return '<button type="button" class="role-card" data-role-slug="' + r.slug + '">' +
        '<span class="role-card-icon">' + icon(r.icon, "icon-lg") + "</span>" +
        '<span class="role-card-title">' + r.title + "</span>" +
        '<span class="role-card-desc">' + r.desc + "</span>" +
        "</button>";
    }).join("");

    var html =
      '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
      '<div class="modal-header"><div><h2>¿Con qué rol quieres entrar?</h2>' +
      "<p>Esta demo aún no verifica credenciales — elige el rol para continuar con su usuario de referencia.</p></div>" +
      '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>" +
      '<div class="modal-body"><div class="role-grid">' + cards + "</div></div>" +
      "</div>";

    var backdrop = open(html, { persistent: false });
    backdrop.querySelectorAll("[data-role-slug]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var slug = btn.dataset.roleSlug;
        btn.disabled = true;
        btn.innerHTML = '<span class="role-card-icon">' + icon("icon-check-circle", "icon-lg") + "</span>" +
          '<span class="role-card-title">Entrando…</span>';
        window.SBAI.state.setRole(slug);
        setTimeout(function () {
          window.location.href = window.SBAI.state.ROLE_HOME[slug];
        }, 420);
      });
    });
    return backdrop;
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.modal = {
    open: open,
    close: close,
    closeTop: closeTop,
    confirm: confirm,
    openRolePicker: openRolePicker,
    icon: icon
  };
})();
