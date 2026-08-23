/* SkillBridge AI — navegación e interacciones del frontend estático */
(function () {
  "use strict";

  function rootPath() {
    // pages/<rol>/archivo.html -> ../../   |   pages/auth/archivo.html -> ../../
    return document.body.hasAttribute("data-role") || document.querySelector(".auth-body")
      ? "../../"
      : "";
  }

  // ---- Toasts: feedback visual real para acciones simuladas (sin backend) ----
  window.SkillBridgeToast = function (message, type) {
    var host = document.querySelector(".toast-host");
    if (!host) {
      host = document.createElement("div");
      host.className = "toast-host";
      document.body.appendChild(host);
    }
    var el = document.createElement("div");
    el.className = "toast" + (type === "success" ? " toast-success" : "");
    el.textContent = message;
    host.appendChild(el);
    requestAnimationFrame(function () { el.classList.add("show"); });
    setTimeout(function () {
      el.classList.remove("show");
      setTimeout(function () { el.remove(); }, 220);
    }, 2600);
  };

  // ---- Menú lateral en pantallas angostas (hamburguesa) ----
  document.addEventListener("click", function (e) {
    var toggle = e.target.closest(".sidebar-toggle");
    var backdrop = e.target.closest(".sidebar-backdrop");
    var sidebar = document.querySelector(".sidebar");
    if (!sidebar) return;

    if (toggle) {
      var willOpen = !sidebar.classList.contains("open");
      sidebar.classList.toggle("open", willOpen);
      var bd = document.querySelector(".sidebar-backdrop");
      if (bd) bd.classList.toggle("open", willOpen);
      toggle.setAttribute("aria-expanded", willOpen ? "true" : "false");
      return;
    }
    if (backdrop) {
      sidebar.classList.remove("open");
      backdrop.classList.remove("open");
    }
  });

  // ---- Botones/elementos "de acción" que aún no tienen backend real:
  //      dan feedback visible en vez de no hacer nada. ----
  document.addEventListener("click", function (e) {
    var fake = e.target.closest(".js-fake-action");
    if (!fake) return;
    if (fake.closest("[data-user-toggle], [data-toggle-pw], [data-fill-account], .pill-toggle")) return;
    e.preventDefault();
    window.SkillBridgeToast("Acción simulada en este frontend hardcodeado — se conectará al backend cuando exista la API de Spring Boot.");
  });

  // ---- Interruptores (2FA, notificaciones, etc.): funcionan de verdad en el navegador ----
  document.addEventListener("click", function (e) {
    var toggle = e.target.closest(".pill-toggle");
    if (!toggle) return;
    var on = toggle.getAttribute("aria-pressed") === "true";
    toggle.setAttribute("aria-pressed", on ? "false" : "true");
  });

  // ---- "▲ Útil (N)" en los foros: vota de verdad en esta pestaña ----
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("span, button").forEach(function (el) {
      if (/^▲\s*Útil\s*\([0-9]+\)$/.test((el.textContent || "").trim())) {
        el.style.cursor = "pointer";
      }
    });
  });
  document.addEventListener("click", function (e) {
    var el = e.target.closest("span, button");
    if (!el || el.getAttribute("data-voted") === "1") return;
    var m = (el.textContent || "").trim().match(/^▲\s*Útil\s*\(([0-9]+)\)$/);
    if (!m) return;
    var next = parseInt(m[1], 10) + 1;
    el.textContent = "▲ Útil (" + next + ")";
    el.setAttribute("data-voted", "1");
    el.style.color = "#15803D";
    el.style.cursor = "default";
  });

  // ---- Menú de usuario (topbar) ----
  document.addEventListener("click", function (e) {
    var toggle = e.target.closest("[data-user-toggle]");
    var dropdown = document.querySelector("[data-user-dropdown]");

    if (toggle) {
      e.stopPropagation();
      if (dropdown) {
        var open = dropdown.classList.toggle("open");
        toggle.setAttribute("aria-expanded", open ? "true" : "false");
      }
      return;
    }
    if (dropdown && !e.target.closest("[data-user-dropdown]")) {
      dropdown.classList.remove("open");
      var chip = document.querySelector("[data-user-toggle]");
      if (chip) chip.setAttribute("aria-expanded", "false");
    }
  });

  // ---- Buscador del sidebar: colapsar en pantallas angostas (mejora táctil) ----
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      var openDropdown = document.querySelector(".user-dropdown.open");
      if (openDropdown) openDropdown.classList.remove("open");
    }
  });

  // ---- Cerrar sesión: limpia el rol guardado y vuelve al inicio ----
  document.addEventListener("click", function (e) {
    var logout = e.target.closest("[data-logout]");
    if (!logout) return;
    e.preventDefault();
    try { localStorage.removeItem("skillbridge_role"); } catch (err) {}
    window.location.href = rootPath() + "index.html";
  });

  // ---- Filas/tarjetas navegables marcadas con data-href (p. ej. proyecto -> detalle) ----
  document.addEventListener("click", function (e) {
    var el = e.target.closest("[data-href]");
    if (!el) return;
    if (e.target.closest("a, button")) return; // no interceptar controles internos
    window.location.href = el.getAttribute("data-href");
  });

  // ---- Formularios de autenticación: evitan recarga y guían al siguiente paso ----
  document.addEventListener("submit", function (e) {
    var form = e.target.closest("form");
    if (!form) return;
    e.preventDefault();
    var redirect = form.getAttribute("data-redirect");
    if (redirect) window.location.href = redirect;
  });

  // ---- Recordar el rol elegido al entrar desde la pantalla de inicio ----
  document.addEventListener("click", function (e) {
    var roleBtn = e.target.closest("[data-select-role]");
    if (!roleBtn) return;
    try { localStorage.setItem("skillbridge_role", roleBtn.getAttribute("data-select-role")); } catch (err) {}
  });
})();
