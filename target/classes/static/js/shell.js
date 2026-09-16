/**
 * shell.js — Construye el shell compartido (sidebar + topbar) a partir
 * de la configuración de rol en state.js y monta las interacciones
 * comunes: menú de usuario, campana de notificaciones y atajo ⌘K.
 *
 * Esta es la implementación "en vivo" del mecanismo de fragmentos que
 * pide la sección 8 del brief: fragments/sidebar.html y fragments/topbar.html
 * documentan el mismo marcado listo para th:fragment; aquí se genera en
 * JS para que la demo funcione abriendo los archivos con file:// sin
 * depender de fetch() (que los navegadores bloquean entre archivos locales).
 */
(function () {
  "use strict";

  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '"><use href="' + window.SBAI.state.STATIC + "img/icons.svg#" + name + '"></use></svg>';
  }

  function sidebarHtml(roleSlug, config, activeHref) {
    var user = config.user();
    var items = config.items.map(function (item) {
      var isActive = item.href === activeHref;
      var badge = item.badge ? item.badge() : null;
      return '<a class="sidebar-nav-item' + (isActive ? " active" : "") + '" href="' + item.href + '"' + (isActive ? ' aria-current="page"' : "") + ">" +
        icon(item.icon) +
        '<span class="sidebar-nav-item-label">' + item.label + "</span>" +
        (badge ? '<span class="counter-badge">' + badge + "</span>" : "") +
        "</a>";
    }).join("");

    return (
      '<div class="sidebar-brand"><span class="sidebar-brand-mark">' + icon("icon-logo") + '</span><span class="sidebar-brand-text">SkillBridge AI</span></div>' +
      '<div class="sidebar-role-badge">' + config.label + "</div>" +
      '<nav class="sidebar-nav" aria-label="Navegación principal">' + items + "</nav>" +
      '<div class="sidebar-footer">' +
      '<button type="button" class="sidebar-user" data-dropdown-trigger="user-menu-sidebar">' +
      '<span class="avatar avatar-sm">' + user.iniciales + "</span>" +
      '<span class="sidebar-user-text"><span class="sidebar-user-name">' + user.nombre + '</span><span class="sidebar-user-email">' + user.correo + "</span></span>" +
      icon("icon-more-h") +
      "</button>" +
      userMenuPanel("user-menu-sidebar", "align-left") +
      "</div>"
    );
  }

  function userMenuPanel(id, align) {
    // El menú del pie del sidebar debe abrir hacia ARRIBA (el botón está
    // pegado abajo); el del topbar debe abrir hacia ABAJO (comportamiento
    // por defecto de .dropdown-panel). Antes este estilo se aplicaba
    // siempre, lo que hacía que el menú del topbar se abriera fuera de
    // la pantalla por encima del header.
    var openUpwardStyle = id === "user-menu-sidebar" ? ' style="bottom: calc(100% + 8px); top: auto;"' : "";
    return '<div class="dropdown-panel dropdown-menu' + (align === "align-right" ? " align-right" : "") + '" data-dropdown-panel="' + id + '"' + openUpwardStyle + '>' +
      '<a class="dropdown-item" href="mi-cuenta.html">' + icon("icon-user") + "Mi cuenta</a>" +
      '<a class="dropdown-item" href="mi-cuenta.html">' + icon("icon-settings") + "Configuración</a>" +
      '<div class="dropdown-divider"></div>' +
      '<button type="button" class="dropdown-item danger" data-action="logout">' + icon("icon-log-out") + "Cerrar sesión</button>" +
      "</div>";
  }

  function topbarHtml(roleSlug, config, opts) {
    var user = config.user();
    var unread = window.SBAI.state.getUnreadCount(roleSlug);
    return (
      '<div class="topbar-heading"><span class="topbar-title">' + (opts.title || "") + '</span>' +
      '<span class="topbar-subtitle">' + (opts.subtitle || "") + "</span></div>" +
      '<button type="button" class="topbar-search" data-open-command-palette><svg class="icon"><use href="' + window.SBAI.state.STATIC + 'img/icons.svg#icon-search"></use></svg>' +
      '<span>Buscar colaboradores, proyectos o habilidades…</span><kbd>⌘K</kbd></button>' +
      '<div class="topbar-actions">' +
      '<a class="topbar-ai-btn" href="asistente-ia.html">' + icon("icon-sparkles") + "<span>Asistente IA</span></a>" +
      '<div class="dropdown">' +
      '<button type="button" class="topbar-icon-btn" data-dropdown-trigger="notif-panel" aria-label="Notificaciones">' + icon("icon-bell") +
      (unread > 0 ? '<span class="counter-badge" data-unread-badge>' + unread + "</span>" : "") + "</button>" +
      notifPanelHtml(roleSlug) +
      "</div>" +
      '<div class="dropdown">' +
      '<button type="button" class="topbar-user-btn" data-dropdown-trigger="user-menu-topbar">' +
      '<span class="avatar avatar-sm">' + user.iniciales + "</span>" +
      icon("icon-chevron-down", "icon-sm") +
      "</button>" +
      userMenuPanel("user-menu-topbar", "align-right") +
      "</div></div>"
    );
  }

  var NOTIF_KEY_BY_ROLE = { colaborador: "colaborador", "project-manager": "pm", "resource-manager": "rm", administrador: "administrador" };

  function notifIconClass(tipo) {
    return { alert: "alert", ai: "ai", info: "info", success: "success", mail: "mail" }[tipo] || "info";
  }

  function notifPanelHtml(roleSlug) {
    var list = (MOCK.notifications[NOTIF_KEY_BY_ROLE[roleSlug]] || []).slice(0, 4);
    var items = list.map(function (n) {
      var read = window.SBAI.state.isRead(roleSlug, n.id);
      return '<div class="notif-item' + (read ? "" : " unread") + '" data-notif-id="' + n.id + '">' +
        '<span class="notif-item-icon ' + notifIconClass(n.tipo) + '">' + icon(n.icon, "icon-sm") + "</span>" +
        '<span class="notif-item-body"><span class="notif-item-title">' + n.titulo + '</span>' +
        '<span class="notif-item-desc">' + n.desc + '</span>' +
        '<span class="notif-item-time">' + n.hora + "</span></span></div>";
    }).join("") || '<div class="state-panel" style="padding:32px 16px;"><span class="state-panel-icon">' + icon("icon-bell") + '</span><span class="state-panel-desc">No tienes notificaciones.</span></div>';

    return '<div class="dropdown-panel notif-panel align-right" data-dropdown-panel="notif-panel">' +
      '<div class="notif-panel-header"><h3>Notificaciones</h3><button type="button" class="btn btn-tertiary btn-sm" data-action="mark-all-read-quick">Marcar leídas</button></div>' +
      '<div class="notif-panel-list">' + items + "</div>" +
      '<div class="notif-panel-footer"><a href="notificaciones.html">Ver todas las notificaciones →</a></div>' +
      "</div>";
  }

  function refreshBadges(roleSlug, config) {
    document.querySelectorAll(".sidebar-nav-item .counter-badge").forEach(function (el) {
      var link = el.closest(".sidebar-nav-item");
      var item = config.items.filter(function (it) { return it.href === link.getAttribute("href"); })[0];
      if (item && item.badge) {
        var val = item.badge();
        if (val > 0) { el.textContent = val; el.hidden = false; } else { el.hidden = true; }
      }
    });
    var bellBadge = document.querySelector("[data-unread-badge]");
    var unread = window.SBAI.state.getUnreadCount(roleSlug);
    if (bellBadge) {
      if (unread > 0) { bellBadge.textContent = unread; bellBadge.hidden = false; }
      else { bellBadge.hidden = true; }
    }
  }

  function wireShellActions(roleSlug, config) {
    document.body.addEventListener("click", function (e) {
      var logoutBtn = e.target.closest('[data-action="logout"]');
      if (logoutBtn) {
        window.SBAI.state.clearSession();
        // Cierre de sesion real: invalida la HttpSession del servidor
        // (antes solo se limpiaba sessionStorage, que era el mecanismo de
        // la demo sin backend, y no cerraba ninguna sesion de verdad).
        window.location.href = "/auth/logout";
        return;
      }
      var markAllBtn = e.target.closest('[data-action="mark-all-read-quick"]');
      if (markAllBtn) {
        window.SBAI.state.markAllRead(roleSlug);
        document.querySelectorAll(".notif-item.unread").forEach(function (el) { el.classList.remove("unread"); });
        refreshBadges(roleSlug, config);
        window.SBAI.toast.show("Notificaciones marcadas como leídas.", { type: "success" });
        return;
      }
      var notifItem = e.target.closest(".notif-item[data-notif-id]");
      if (notifItem) {
        window.SBAI.state.markRead(roleSlug, notifItem.dataset.notifId);
        notifItem.classList.remove("unread");
        refreshBadges(roleSlug, config);
      }
    });
  }

  function init(opts) {
    var config = window.SBAI.state.requireRole(opts.role);
    if (!config) return null;

    var sidebarEl = document.getElementById("sbai-sidebar");
    var topbarEl = document.getElementById("sbai-topbar");
    if (sidebarEl) sidebarEl.innerHTML = sidebarHtml(opts.role, config, opts.active);
    if (topbarEl) topbarEl.innerHTML = topbarHtml(opts.role, config, opts);

    window.SBAI.dropdown.init(document);
    window.SBAI.commandPalette.init(opts.role);
    wireShellActions(opts.role, config);

    if (window.SBAI.tableFilter) window.SBAI.tableFilter.init(document);

    return config;
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.shell = { init: init, refreshBadges: refreshBadges, icon: icon };
})();
