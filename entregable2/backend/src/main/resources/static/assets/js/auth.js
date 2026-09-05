/* SkillBridge AI — autenticación de demo (frontend hardcodeado, sin backend).
   Valida contra una lista fija de 4 usuarios (uno por rol) con una sola
   contraseña de prueba. Pensado para reemplazarse por Spring Security más
   adelante: toda la lógica de "sesión" vive aquí y en localStorage. */
(function () {
  "use strict";

  var DEMO_PASSWORD = "Nexa2026*";
  var USERS = {
    "mariana.ruiz@nexacorp.com": { role: "colaborador", home: "../colaborador/dashboard.html" },
    "javier.molina@nexacorp.com": { role: "project-manager", home: "../project-manager/dashboard.html" },
    "paula.vega@nexacorp.com": { role: "resource-manager", home: "../resource-manager/dashboard.html" },
    "ana.villalba@nexacorp.com": { role: "administrador", home: "../administrador/inicio.html" }
  };

  function $(id) { return document.getElementById(id); }

  function showAlert(id, message) {
    var el = $(id);
    if (!el) return;
    el.textContent = message;
    el.classList.add("visible");
  }

  function hideAlert(id) {
    var el = $(id);
    if (!el) return;
    el.classList.remove("visible");
    el.textContent = "";
  }

  function showFieldError(id) { var el = $(id); if (el) el.classList.add("visible"); }
  function hideFieldError(id) { var el = $(id); if (el) el.classList.remove("visible"); }
  function markBox(id, bad) {
    var el = $(id);
    if (!el) return;
    el.classList.toggle("has-error", !!bad);
  }

  // ---------------- Mostrar / ocultar contraseña ----------------
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-toggle-pw]");
    if (!btn) return;
    var input = $(btn.getAttribute("data-toggle-pw"));
    if (!input) return;
    var showing = input.type === "text";
    input.type = showing ? "password" : "text";
    btn.textContent = showing ? "Mostrar" : "Ocultar";
  });

  // ---------------- LOGIN ----------------
  var loginBtn = $("login-submit");
  if (loginBtn) {
    loginBtn.addEventListener("click", function () {
      var email = ($("login-email").value || "").trim().toLowerCase();
      var pw = $("login-password").value || "";

      hideAlert("login-alert");
      hideFieldError("login-email-error");
      hideFieldError("login-password-error");
      markBox("login-email-box", false);
      markBox("login-password-box", false);

      var valid = true;
      if (!email || email.indexOf("@") === -1) {
        showFieldError("login-email-error");
        markBox("login-email-box", true);
        valid = false;
      }
      if (!pw) {
        showFieldError("login-password-error");
        markBox("login-password-box", true);
        valid = false;
      }
      if (!valid) return;

      var user = USERS[email];
      if (!user) {
        showAlert("login-alert", "No reconocemos ese correo. Usa una de las cuentas de demostración de abajo.");
        markBox("login-email-box", true);
        return;
      }
      if (pw !== DEMO_PASSWORD) {
        showAlert("login-alert", "Contraseña incorrecta. Pista: es la misma para las 4 cuentas de demo.");
        markBox("login-password-box", true);
        return;
      }

      try { localStorage.setItem("skillbridge_role", user.role); } catch (err) {}
      window.location.href = user.home;
    });
  }

  document.addEventListener("click", function (e) {
    var fillBtn = e.target.closest("[data-fill-account]");
    if (!fillBtn) return;
    var email = fillBtn.getAttribute("data-fill-account");
    if ($("login-email")) $("login-email").value = email;
    if ($("login-password")) $("login-password").value = DEMO_PASSWORD;
    hideAlert("login-alert");
    hideFieldError("login-email-error");
    hideFieldError("login-password-error");
    markBox("login-email-box", false);
    markBox("login-password-box", false);
  });

  // ---------------- REGISTRO ----------------
  var regCorreo = $("reg-correo");
  if (regCorreo) {
    regCorreo.addEventListener("input", function () {
      var badge = $("reg-correo-badge");
      if (!badge) return;
      var ok = /@nexacorp\.com$/i.test(regCorreo.value.trim());
      badge.style.display = ok ? "" : "none";
    });
  }

  var regPw = $("reg-password");
  if (regPw) {
    regPw.addEventListener("input", function () {
      var v = regPw.value || "";
      var hasLen = v.length >= 8;
      var hasUpperNum = /[A-Z]/.test(v) && /[0-9]/.test(v);
      var hasSymbol = /[^A-Za-z0-9]/.test(v);
      var score = (v ? 1 : 0) + (hasLen ? 1 : 0) + (hasUpperNum ? 1 : 0) + (hasSymbol ? 1 : 0);

      var bars = document.querySelectorAll("#reg-pw-bars .pw-bar");
      var barColors = ["#DC2626", "#DC2626", "#D97706", "#15803D"];
      bars.forEach(function (bar, i) {
        bar.style.background = i < score ? barColors[Math.min(score, 4) - 1] : "#E4E8EE";
      });

      var label = $("reg-pw-strength-label");
      if (label) {
        var text = !v ? "SIN DATOS" : score <= 2 ? "DÉBIL" : score === 3 ? "MEDIA" : "FUERTE";
        label.textContent = "SEGURIDAD DE LA CONTRASEÑA — " + text;
      }

      function setCheck(id, ok, textOk, textNo) {
        var el = $(id);
        if (!el) return;
        el.textContent = (ok ? "✓ " : "○ ") + (ok ? textOk : textNo);
        el.style.color = ok ? "#15803D" : "#8A97A8";
      }
      setCheck("reg-pw-check-len", hasLen, "8+ caracteres", "8+ caracteres");
      setCheck("reg-pw-check-upper", hasUpperNum, "Mayúscula y número", "Mayúscula y número");
      setCheck("reg-pw-check-symbol", hasSymbol, "Símbolo especial", "Símbolo especial");
    });
  }

  var regBtn = $("reg-submit");
  if (regBtn) {
    regBtn.addEventListener("click", function () {
      hideAlert("reg-alert");
      hideFieldError("reg-correo-error");
      hideFieldError("reg-password-error");

      var nombres = ($("reg-nombres").value || "").trim();
      var apellidos = ($("reg-apellidos").value || "").trim();
      var correo = ($("reg-correo").value || "").trim();
      var pw1 = $("reg-password").value || "";
      var pw2 = $("reg-password2").value || "";
      var terms = $("reg-terms");

      var errors = [];
      if (!nombres || !apellidos) errors.push("Completa tus nombres y apellidos.");
      if (!/@nexacorp\.com$/i.test(correo)) {
        showFieldError("reg-correo-error");
        errors.push("El correo debe ser del dominio @nexacorp.com.");
      }
      if (pw1.length < 8 || pw1 !== pw2) {
        showFieldError("reg-password-error");
        errors.push("La contraseña debe tener 8+ caracteres y coincidir en ambos campos.");
      }
      if (!terms || !terms.checked) errors.push("Debes aceptar la política de tratamiento de datos.");

      if (errors.length) {
        showAlert("reg-alert", errors[0]);
        return;
      }

      showAlert("reg-alert", "");
      var alertEl = $("reg-alert");
      if (alertEl) {
        alertEl.classList.remove("alert-error");
        alertEl.classList.add("alert-success", "visible");
        alertEl.textContent = "Cuenta creada. Un Administrador debe asignarte un rol antes de tu primer ingreso — redirigiendo a inicio de sesión…";
      }
      setTimeout(function () { window.location.href = "login.html"; }, 1400);
    });
  }

  // ---------------- RESTABLECER CONTRASEÑA ----------------
  var sendBtn = $("reset-send");
  if (sendBtn) {
    sendBtn.addEventListener("click", function () {
      hideFieldError("reset-email-error");
      var email = ($("reset-email").value || "").trim();
      if (!/@nexacorp\.com$/i.test(email)) {
        showFieldError("reset-email-error");
        return;
      }
      var box = $("reset-sent-box");
      if (box) box.style.display = "flex";
    });
  }

  var saveBtn = $("reset-save");
  if (saveBtn) {
    saveBtn.addEventListener("click", function () {
      var pw1 = $("reset-newpw").value || "";
      var pw2 = $("reset-newpw2").value || "";
      var mismatchEl = $("reset-mismatch-error");
      if (pw1.length < 10 || pw1 !== pw2) {
        if (mismatchEl) mismatchEl.style.display = "block";
        return;
      }
      if (mismatchEl) mismatchEl.style.display = "none";
      window.location.href = "login.html";
    });
  }
})();
