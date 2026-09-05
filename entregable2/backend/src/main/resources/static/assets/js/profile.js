/* SkillBridge AI — "Mi cuenta" (frontend hardcodeado, sin backend).
   Guardar cambios / actualizar contraseña se validan y aplican solo en el
   DOM de esta pestaña (no hay persistencia real todavía). */
(function () {
  "use strict";

  function $(id) { return document.getElementById(id); }

  var toast = window.SkillBridgeToast || function (m) { console.log(m); };

  // ---------------- Guardar cambios (datos personales) ----------------
  var nameInput = $("profile-name");
  var cargoInput = $("profile-cargo");
  var telInput = $("profile-telefono");
  var areaSelect = $("profile-area");

  var originals = {
    name: nameInput ? nameInput.value : "",
    cargo: cargoInput ? cargoInput.value : "",
    tel: telInput ? telInput.value : "",
    area: areaSelect ? areaSelect.value : "",
  };

  var saveBtn = $("profile-save");
  if (saveBtn) {
    saveBtn.addEventListener("click", function () {
      if (!nameInput || !nameInput.value.trim()) {
        toast("El nombre visible no puede quedar vacío.");
        return;
      }
      originals = {
        name: nameInput.value.trim(),
        cargo: cargoInput ? cargoInput.value.trim() : "",
        tel: telInput ? telInput.value.trim() : "",
        area: areaSelect ? areaSelect.value : "",
      };
      document.querySelectorAll("[data-profile-name-target]").forEach(function (el) {
        el.textContent = originals.name;
      });
      var avatarLetters = originals.name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(function (p) { return p[0].toUpperCase(); })
        .join("");
      document.querySelectorAll(".user-avatar, .user-chip-avatar").forEach(function (el) {
        if (avatarLetters) el.textContent = avatarLetters;
      });
      toast("Cambios guardados (simulado — se conectará al backend con Spring Boot).", "success");
    });
  }

  var cancelBtn = $("profile-cancel");
  if (cancelBtn) {
    cancelBtn.addEventListener("click", function () {
      if (nameInput) nameInput.value = originals.name;
      if (cargoInput) cargoInput.value = originals.cargo;
      if (telInput) telInput.value = originals.tel;
      if (areaSelect) areaSelect.value = originals.area;
      toast("Cambios descartados.");
    });
  }

  // ---------------- Medidor de fuerza de la nueva contraseña ----------------
  var newPw = $("profile-pw-new");
  if (newPw) {
    newPw.addEventListener("input", function () {
      var v = newPw.value || "";
      var hasLen = v.length >= 10;
      var hasUpperNum = /[A-Z]/.test(v) && /[0-9]/.test(v);
      var hasSymbol = /[^A-Za-z0-9]/.test(v);
      var score = (v ? 1 : 0) + (hasLen ? 1 : 0) + (hasUpperNum ? 1 : 0) + (hasSymbol ? 1 : 0);

      var bars = document.querySelectorAll("#profile-pw-bars .pw-bar");
      var barColors = ["#DC2626", "#DC2626", "#D97706", "#15803D"];
      bars.forEach(function (bar, i) {
        bar.style.background = i < score ? barColors[Math.min(score, 4) - 1] : "#E4E8EE";
      });

      var label = $("profile-pw-strength-label");
      if (label) {
        var text = !v ? "SIN DATOS" : score <= 2 ? "DÉBIL" : score === 3 ? "MEDIA" : "FUERTE";
        label.textContent = text;
        label.style.color = !v ? "#8A97A8" : score <= 2 ? "#DC2626" : score === 3 ? "#D97706" : "#15803D";
      }
    });
  }

  // ---------------- Actualizar contraseña ----------------
  var pwSaveBtn = $("profile-pw-save");
  if (pwSaveBtn) {
    pwSaveBtn.addEventListener("click", function () {
      var current = $("profile-pw-current") ? $("profile-pw-current").value : "";
      var pw1 = $("profile-pw-new") ? $("profile-pw-new").value : "";
      var pw2 = $("profile-pw-new2") ? $("profile-pw-new2").value : "";

      if (!current) {
        toast("Ingresa tu contraseña actual.");
        return;
      }
      if (pw1.length < 10 || pw1 !== pw2) {
        toast("La nueva contraseña debe tener 10+ caracteres y coincidir en ambos campos.");
        return;
      }
      [$("profile-pw-current"), $("profile-pw-new"), $("profile-pw-new2")].forEach(function (el) {
        if (el) el.value = "";
      });
      var label = $("profile-pw-strength-label");
      if (label) { label.textContent = "SIN DATOS"; label.style.color = "#8A97A8"; }
      document.querySelectorAll("#profile-pw-bars .pw-bar").forEach(function (bar) {
        bar.style.background = "#E4E8EE";
      });
      toast("Contraseña actualizada (simulado).", "success");
    });
  }
})();
