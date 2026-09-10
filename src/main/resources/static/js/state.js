/**
 * state.js — Estado de sesión en el navegador.
 *
 * SkillBridge AI todavía no tiene backend: el "rol activo" elegido en el
 * popup de login se guarda en sessionStorage (equivalente de navegador a
 * una sesión de servidor) para que la navegación entre las páginas
 * estáticas del shell respete el rol sin volver a mostrar el selector.
 * sessionStorage se limpia solo al cerrar la pestaña/navegador, igual que
 * una sesión real — es la pieza que permite que esta demo sea multi-página
 * (requisito de la sección 8) sin perder el estado de "sesión en memoria"
 * pedido en la sección 4.
 *
 * Namespace: window.SBAI.state
 */
(function () {
  "use strict";

  var LS_ROLE = "sbai_active_role";
  var LS_READ_PREFIX = "sbai_read_";
  var LS_TOGGLE_PREFIX = "sbai_toggle_";
  var LS_FLAG_PREFIX = "sbai_flag_";

  // Prefijo relativo desde cualquier templates/<grupo>/<archivo>.html hasta static/
  var STATIC = "../../static/";

  var ROLE_HOME = {
    colaborador: "../colaborador/inicio.html",
    "project-manager": "../project-manager/inicio.html",
    "resource-manager": "../resource-manager/inicio.html",
    administrador: "../administrador/inicio.html"
  };
  var LOGIN_PATH = "../auth/login.html";

  function safeGet(key) {
    try { return sessionStorage.getItem(key); } catch (e) { return null; }
  }
  function safeSet(key, val) {
    try { sessionStorage.setItem(key, val); } catch (e) { /* noop */ }
  }
  function safeRemove(key) {
    try { sessionStorage.removeItem(key); } catch (e) { /* noop */ }
  }

  function getRole() { return safeGet(LS_ROLE); }
  function setRole(role) { safeSet(LS_ROLE, role); }
  function clearSession() {
    safeRemove(LS_ROLE);
    try {
      var toRemove = [];
      for (var i = 0; i < sessionStorage.length; i++) {
        var k = sessionStorage.key(i);
        if (k && k.indexOf("sbai_") === 0) toRemove.push(k);
      }
      toRemove.forEach(safeRemove);
    } catch (e) { /* noop */ }
  }

  /* -------- Navegación por rol -------- */
  var NAV_CONFIG = {
    colaborador: {
      label: "Colaborador",
      user: function () { return MOCK.roleUsers.colaborador; },
      items: [
        { label: "Inicio", icon: "icon-home", href: "inicio.html" },
        { label: "Mi perfil", icon: "icon-user", href: "perfil.html" },
        { label: "Mis proyectos", icon: "icon-folder", href: "proyectos.html" },
        { label: "Calendario", icon: "icon-calendar", href: "calendario.html" },
        { label: "Foros", icon: "icon-message", href: "foros.html" },
        { label: "Asistente IA", icon: "icon-sparkles", href: "asistente-ia.html" },
        { label: "Notificaciones", icon: "icon-bell", href: "notificaciones.html", badge: function () { return getUnreadCount("colaborador"); } }
      ]
    },
    "project-manager": {
      label: "Project Manager",
      user: function () { return MOCK.roleUsers.pm; },
      items: [
        { label: "Inicio", icon: "icon-home", href: "inicio.html" },
        { label: "Proyectos", icon: "icon-folder", href: "proyectos.html" },
        { label: "Calendario", icon: "icon-calendar", href: "calendario.html" },
        { label: "Asignaciones", icon: "icon-target", href: "asignaciones.html" },
        { label: "AI Talent Matching", icon: "icon-search-check", href: "ai-talent-matching.html", badge: function () { return MOCK.matchingVacancy.otrasVacantes.length + 1; } },
        { label: "Foros", icon: "icon-message", href: "foros.html" },
        { label: "Asistente IA", icon: "icon-sparkles", href: "asistente-ia.html" },
        { label: "Reportes", icon: "icon-bar-chart", href: "reportes.html" }
      ]
    },
    "resource-manager": {
      label: "Resource Manager",
      user: function () { return MOCK.roleUsers.rm; },
      items: [
        { label: "Inicio", icon: "icon-home", href: "inicio.html" },
        { label: "Ocupación del equipo", icon: "icon-layers", href: "ocupacion.html" },
        { label: "Asignaciones", icon: "icon-target", href: "asignaciones.html", badge: function () { return MOCK.rmReport.solicitudesPendientes; } },
        { label: "Colaboradores", icon: "icon-users", href: "colaboradores.html" },
        { label: "AI Talent Matching", icon: "icon-search-check", href: "ai-talent-matching.html", badge: function () { return MOCK.matchingVacancy.otrasVacantes.length + 1; } },
        { label: "Reportes", icon: "icon-bar-chart", href: "reportes.html" }
      ]
    },
    administrador: {
      label: "Administrador",
      user: function () { return MOCK.roleUsers.admin; },
      items: [
        { label: "Inicio", icon: "icon-home", href: "inicio.html" },
        { label: "Usuarios y roles", icon: "icon-users", href: "usuarios.html", badge: function () { return MOCK.usersKpi.sinRol; } },
        { label: "Catálogo de habilidades", icon: "icon-book", href: "habilidades.html" },
        { label: "Proyectos", icon: "icon-folder", href: "proyectos.html" },
        { label: "Reportes globales", icon: "icon-bar-chart", href: "reportes.html" },
        { label: "Auditoría", icon: "icon-shield", href: "auditoria.html", badge: function () { return MOCK.auditSeverityBreakdown.alta; } },
        { label: "Configuración", icon: "icon-sliders", href: "configuracion.html" }
      ]
    }
  };

  var NOTIF_KEY_BY_ROLE = { colaborador: "colaborador", "project-manager": "pm", "resource-manager": "rm", administrador: "administrador" };

  function notifKey(role) { return NOTIF_KEY_BY_ROLE[role] || role; }

  function getReadSet(role) {
    var raw = safeGet(LS_READ_PREFIX + role);
    if (!raw) return {};
    try { return JSON.parse(raw); } catch (e) { return {}; }
  }
  function saveReadSet(role, set) { safeSet(LS_READ_PREFIX + role, JSON.stringify(set)); }

  function isRead(role, id) {
    var list = MOCK.notifications[notifKey(role)] || [];
    var item = null;
    for (var i = 0; i < list.length; i++) { if (list[i].id === id) { item = list[i]; break; } }
    if (item && item.leida) return true;
    var readSet = getReadSet(role);
    return !!readSet[id];
  }
  function markRead(role, id) {
    var readSet = getReadSet(role);
    readSet[id] = true;
    saveReadSet(role, readSet);
  }
  function markAllRead(role) {
    var list = MOCK.notifications[notifKey(role)] || [];
    var readSet = getReadSet(role);
    list.forEach(function (n) { readSet[n.id] = true; });
    saveReadSet(role, readSet);
  }
  function getUnreadCount(role) {
    var list = MOCK.notifications[notifKey(role)] || [];
    var count = 0;
    for (var i = 0; i < list.length; i++) { if (!isRead(role, list[i].id)) count++; }
    return count;
  }

  /* -------- Toggles genéricos (Mi cuenta / Configuración) -------- */
  function getToggle(key, defaultVal) {
    var raw = safeGet(LS_TOGGLE_PREFIX + key);
    if (raw === null) return defaultVal;
    return raw === "1";
  }
  function setToggle(key, val) { safeSet(LS_TOGGLE_PREFIX + key, val ? "1" : "0"); }

  /* -------- Flags genéricos de un solo uso (p.ej. asignación aceptada) -------- */
  function getFlag(key) { return safeGet(LS_FLAG_PREFIX + key) === "1"; }
  function setFlag(key) { safeSet(LS_FLAG_PREFIX + key, "1"); }

  /* -------- Guard de rol: exige que la página se abra con el rol correcto -------- */
  function requireRole(pageRole) {
    var role = getRole();
    if (!role) {
      window.location.href = LOGIN_PATH;
      return null;
    }
    if (role !== pageRole) {
      window.location.href = ROLE_HOME[role] || LOGIN_PATH;
      return null;
    }
    return NAV_CONFIG[pageRole];
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.state = {
    STATIC: STATIC,
    ROLE_HOME: ROLE_HOME,
    LOGIN_PATH: LOGIN_PATH,
    NAV_CONFIG: NAV_CONFIG,
    getRole: getRole,
    setRole: setRole,
    clearSession: clearSession,
    requireRole: requireRole,
    isRead: isRead,
    markRead: markRead,
    markAllRead: markAllRead,
    getUnreadCount: getUnreadCount,
    getToggle: getToggle,
    setToggle: setToggle,
    getFlag: getFlag,
    setFlag: setFlag
  };
})();