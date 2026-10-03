/**
 * modal.js — Sistema de modales genérico + modal de selección de rol.
 *
 * SBAI.modal.open(innerHtml, opts)   → abre un modal a partir de un
 *                                       fragmento de HTML (el <div class="modal">…</div> completo).
 * SBAI.modal.closeTop()              → cierra el modal más reciente.
 * SBAI.modal.confirm({...})          → modal de confirmación reutilizable (reemplaza a
 *                                       window.confirm): ícono, consecuencias, motivo
 *                                       (queda en Auditoría) y "escribe el nombre para confirmar".
 * SBAI.modal.notice({...})           → aviso con un botón (reemplaza a window.alert).
 * SBAI.modal.openRolePicker(opts)    → popup de selección de rol (login,
 *                                       fin de registro, fin de recuperar).
 */
(function () {
  "use strict";

  var stack = [];

  function icon(name, cls) {
    // Mismo fix que en toast.js: ruta absoluta fija en vez de
    // window.SBAI.state.STATIC, que no existe en las paginas reales
    // (ese script solo se cargaba en el mock viejo).
    return '<svg class="icon ' + (cls || "") + '"><use href="' + "/img/icons.svg#" + name + '"></use></svg>';
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
  var uid = 0;
  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  var TONOS = {
    destructive: { icon: "icon-trash", btn: "btn-destructive-solid" },
    warning: { icon: "icon-alert-triangle", btn: "btn-primary" },
    primary: { icon: "icon-info", btn: "btn-primary" }
  };

  /**
   * Modal de confirmación (reemplaza a window.confirm).
   *
   *   title, desc          texto plano (se escapa solo; usa descHtml / bodyHtml para HTML propio)
   *   variant              "destructive" | "warning" | "primary"
   *   icon                 id del ícono (por defecto según variant)
   *   subject              { nombre, meta } → tarjetita con lo que se va a afectar
   *   consequences         ["Se borran sus entregables", ...] → lista de efectos
   *   notice               { tone: "warn"|"danger"|"info", text } → aviso dentro del modal
   *   reason               { label, placeholder, required, min, max, presets: [..] } → pide un motivo
   *   typeToConfirm        "Nombre exacto" → el botón se habilita al escribirlo
   *   confirmLabel / cancelLabel
   *   onConfirm(datos)     datos = { motivo }  (motivo "" si no se pidió)
   */
  function confirm(opts) {
    opts = opts || {};
    var variant = TONOS[opts.variant] ? opts.variant : "primary";
    var tono = TONOS[variant];
    var id = ++uid;
    var r = opts.reason || null;
    var rMax = r ? (r.max || 300) : 0;
    var rMin = r ? (r.required ? (r.min || 5) : 0) : 0;

    var body = "";
    if (opts.subject) {
      body += '<div class="mc-subject"><strong>' + esc(opts.subject.nombre) + "</strong>" +
        (opts.subject.meta ? "<span>" + esc(opts.subject.meta) + "</span>" : "") + "</div>";
    }
    if (opts.consequences && opts.consequences.length) {
      body += '<ul class="mc-list">' + opts.consequences.map(function (c) { return "<li>" + esc(c) + "</li>"; }).join("") + "</ul>";
    }
    if (opts.notice) {
      var nt = opts.notice.tone || "warn";
      body += '<div class="callout ' + esc(nt) + ' mc-notice"><span class="callout-icon">' +
        icon(nt === "info" ? "icon-info" : "icon-alert-triangle") + '</span><span class="callout-body">' + esc(opts.notice.text) + "</span></div>";
    }
    if (opts.bodyHtml) body += opts.bodyHtml;
    if (r) {
      body += '<div class="field mc-reason">' +
        '<div class="mc-reason-head"><label class="field-label" for="mc-motivo-' + id + '">' + esc(r.label || "Motivo") +
        ' <span class="mc-tag ' + (r.required ? "is-req" : "") + '">' + (r.required ? "obligatorio" : "opcional") + "</span></label>" +
        '<span class="mc-count" id="mc-count-' + id + '">0/' + rMax + "</span></div>" +
        (r.presets && r.presets.length
          ? '<div class="mc-presets" role="group" aria-label="Motivos frecuentes">' + r.presets.map(function (t) {
              return '<button type="button" class="mc-preset" data-preset="' + esc(t) + '">' + esc(t) + "</button>";
            }).join("") + "</div>"
          : "") +
        '<textarea class="input mc-textarea" id="mc-motivo-' + id + '" rows="3" maxlength="' + rMax + '" placeholder="' +
        esc(r.placeholder || "Cuéntanos brevemente por qué…") + '"></textarea>' +
        '<div class="field-error" id="mc-error-' + id + '"></div>' +
        (r.hint ? '<span class="field-hint">' + esc(r.hint) + "</span>" : "") + "</div>";
    }
    if (opts.typeToConfirm) {
      body += '<div class="field mc-type"><label class="field-label" for="mc-type-' + id + '">Para confirmar, escribe <code>' +
        esc(opts.typeToConfirm) + '</code></label><input class="input" id="mc-type-' + id + '" autocomplete="off" spellcheck="false" placeholder="' +
        esc(opts.typeToConfirm) + '"></div>';
    }

    var html =
        '<div class="modal modal-confirm is-' + variant + '" role="alertdialog" aria-modal="true" aria-labelledby="mc-title-' + id + '"' +
        (opts.desc || opts.descHtml ? ' aria-describedby="mc-desc-' + id + '"' : "") + ">" +
        '<div class="mc-head"><span class="mc-icon" aria-hidden="true">' + icon(opts.icon || tono.icon) + "</span>" +
        '<div class="mc-head-text"><h2 class="mc-title" id="mc-title-' + id + '">' + esc(opts.title) + "</h2>" +
        (opts.desc || opts.descHtml ? '<p class="mc-desc" id="mc-desc-' + id + '">' + (opts.descHtml || esc(opts.desc)) + "</p>" : "") +
        '</div><button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>" +
        (body ? '<div class="modal-body mc-body">' + body + "</div>" : "") +
        '<div class="modal-footer mc-foot">' +
        '<button type="button" class="btn btn-secondary" data-modal-close data-mc-cancel>' + esc(opts.cancelLabel || "Cancelar") + "</button>" +
        '<button type="button" class="btn ' + tono.btn + '" data-confirm-action>' + esc(opts.confirmLabel || "Confirmar") + "</button>" +
        "</div></div>";

    var backdrop = open(html);
    var okBtn = backdrop.querySelector("[data-confirm-action]");
    var ta = backdrop.querySelector("#mc-motivo-" + id);
    var errEl = backdrop.querySelector("#mc-error-" + id);
    var countEl = backdrop.querySelector("#mc-count-" + id);
    var typeInput = backdrop.querySelector("#mc-type-" + id);
    var presetBtns = backdrop.querySelectorAll(".mc-preset");

    function motivo() { return ta ? ta.value.trim() : ""; }
    function mostrarError(msg) {
      var field = ta.closest(".field");
      field.classList.toggle("has-error", !!msg);
      errEl.textContent = msg || "";
      errEl.style.display = msg ? "flex" : "none";
    }
    function motivoValido() {
      if (!r) return true;
      var m = motivo();
      if (r.required && m.length < rMin) {
        mostrarError(m ? "Escribe al menos " + rMin + " caracteres para dejar un motivo claro." : "Indica el motivo: queda registrado en Auditoría.");
        return false;
      }
      mostrarError("");
      return true;
    }
    function tipeoValido() {
      return !typeInput || typeInput.value.trim().toLowerCase() === String(opts.typeToConfirm).trim().toLowerCase();
    }
    function refrescar() {
      okBtn.disabled = !tipeoValido();
      if (ta) {
        countEl.textContent = ta.value.length + "/" + rMax;
        presetBtns.forEach(function (b) { b.classList.toggle("is-active", b.getAttribute("data-preset") === ta.value.trim()); });
      }
    }
    function enviar() {
      if (!tipeoValido()) { typeInput.focus(); return; }
      if (!motivoValido()) { ta.focus(); return; }
      var datos = { motivo: motivo() };
      okBtn.disabled = true;
      close(backdrop);
      if (opts.onConfirm) opts.onConfirm(datos);
    }

    if (ta) {
      ta.addEventListener("input", function () { if (errEl.textContent) motivoValido(); refrescar(); });
      ta.addEventListener("keydown", function (e) { if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) { e.preventDefault(); enviar(); } });
      presetBtns.forEach(function (b) {
        b.addEventListener("click", function () {
          ta.value = b.getAttribute("data-preset").slice(0, rMax);
          ta.focus();
          ta.setSelectionRange(ta.value.length, ta.value.length);
          if (errEl.textContent) motivoValido();
          refrescar();
        });
      });
    }
    if (typeInput) {
      typeInput.addEventListener("input", refrescar);
      typeInput.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); enviar(); } });
    }
    okBtn.addEventListener("click", enviar);
    refrescar();

    // Sin campos que llenar, el foco va a "Cancelar": evita confirmar una acción destructiva con un Enter de más.
    if (!ta && !typeInput) {
      var cancel = backdrop.querySelector("[data-mc-cancel]");
      setTimeout(function () { if (cancel) cancel.focus(); }, 70);
    }
    return backdrop;
  }

  /** Aviso con un solo botón (reemplaza a window.alert). opts: title, desc, bodyHtml, variant, buttonLabel. */
  function notice(opts) {
    opts = opts || {};
    var variant = TONOS[opts.variant] ? opts.variant : "primary";
    var id = ++uid;
    var html =
        '<div class="modal modal-confirm is-' + variant + '" role="alertdialog" aria-modal="true" aria-labelledby="mc-title-' + id + '">' +
        '<div class="mc-head"><span class="mc-icon" aria-hidden="true">' + icon(opts.icon || TONOS[variant].icon) + "</span>" +
        '<div class="mc-head-text"><h2 class="mc-title" id="mc-title-' + id + '">' + esc(opts.title) + "</h2>" +
        (opts.desc ? '<p class="mc-desc">' + esc(opts.desc) + "</p>" : "") + "</div>" +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>" +
        (opts.bodyHtml ? '<div class="modal-body mc-body">' + opts.bodyHtml + "</div>" : "") +
        '<div class="modal-footer mc-foot"><button type="button" class="btn btn-primary" data-modal-close>' + esc(opts.buttonLabel || "Entendido") + "</button></div></div>";
    return open(html);
  }

  /*
   * Confirmación declarativa para formularios que se envían directo:
   *   <form method="post" action="…" data-confirm-title="¿Quitar tu foto?"
   *         data-confirm-desc="…" data-confirm-label="Quitar foto">
   * (data-confirm-variant: destructive por defecto.)
   */
  document.addEventListener("submit", function (e) {
    var f = e.target;
    if (!f || !f.hasAttribute || !f.hasAttribute("data-confirm-title")) return;
    e.preventDefault();
    confirm({
      title: f.getAttribute("data-confirm-title"),
      desc: f.getAttribute("data-confirm-desc") || "",
      confirmLabel: f.getAttribute("data-confirm-label") || "Confirmar",
      variant: f.getAttribute("data-confirm-variant") || "destructive",
      onConfirm: function () { HTMLFormElement.prototype.submit.call(f); }
    });
  }, true);

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
    notice: notice,
    esc: esc,
    openRolePicker: openRolePicker,
    icon: icon
  };
})();
