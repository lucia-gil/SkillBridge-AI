/**
 * twofa.js — Tarjeta de doble factor (TOTP) en "Mi cuenta".
 * Flujo: Activar -> POST /cuenta/2fa/iniciar (secreto + otpauth://) -> QR local
 * (vendor-qrcode.js, sin servicios externos) -> el usuario escribe el codigo de
 * 6 digitos -> POST /cuenta/2fa/activar. Desactivar exige un codigo vigente.
 */
(function () {
  "use strict";
  function post(url, params) {
    var body = new URLSearchParams(params || {});
    return fetch(url, { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" }, body: body })
      .then(function (r) { return r.json().catch(function () { return {}; }).then(function (j) { if (!r.ok) throw new Error(j.error || "No se pudo completar la operacion."); return j; }); });
  }
  function toast(msg, type) { if (window.SBAI && SBAI.toast) SBAI.toast.show(msg, { type: type || "success" }); else if (window.console) console.warn(msg); }

  document.addEventListener("DOMContentLoaded", function () {
    var root = document.getElementById("twofa-card"); if (!root) return;
    var estado = root.querySelector("[data-2fa-estado]"), acciones = root.querySelector("[data-2fa-acciones]"),
        setup = root.querySelector("[data-2fa-setup]"), off = root.querySelector("[data-2fa-off]");

    function pintar(activo) {
      estado.textContent = activo ? "Activado" : "Desactivado";
      estado.className = "badge " + (activo ? "badge-green" : "badge-neutral");
      acciones.querySelector("[data-2fa-activar]").hidden = activo;
      acciones.querySelector("[data-2fa-desactivar]").hidden = !activo;
      setup.hidden = true; off.hidden = true;
    }
    fetch("/cuenta/2fa/estado", { credentials: "same-origin" }).then(function (r) { return r.json(); })
      .then(function (j) { pintar(!!j.activo); }).catch(function () { estado.textContent = "No disponible"; });

    acciones.querySelector("[data-2fa-activar]").addEventListener("click", function () {
      post("/cuenta/2fa/iniciar").then(function (j) {
        var qr = qrcode(0, "M"); qr.addData(j.uri); qr.make();
        setup.querySelector("[data-2fa-qr]").innerHTML = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
        setup.querySelector("[data-2fa-secreto]").textContent = j.secreto.replace(/(.{4})/g, "$1 ").trim();
        setup.hidden = false; off.hidden = true;
        setup.querySelector("input").value = ""; setup.querySelector("input").focus();
      }).catch(function (e) { toast(e.message, "error"); });
    });
    setup.querySelector("[data-2fa-confirmar]").addEventListener("click", function () {
      post("/cuenta/2fa/activar", { codigo: setup.querySelector("input").value }).then(function () {
        pintar(true); toast("Doble factor activado. Desde ahora se pedira un codigo al iniciar sesion.");
      }).catch(function (e) { toast(e.message, "error"); });
    });
    acciones.querySelector("[data-2fa-desactivar]").addEventListener("click", function () { off.hidden = false; setup.hidden = true; off.querySelector("input").value = ""; off.querySelector("input").focus(); });
    off.querySelector("[data-2fa-confirmar-off]").addEventListener("click", function () {
      post("/cuenta/2fa/desactivar", { codigo: off.querySelector("input").value }).then(function () {
        pintar(false); toast("Doble factor desactivado.");
      }).catch(function (e) { toast(e.message, "error"); });
    });
    root.querySelectorAll(".otp-input").forEach(function (i) { i.addEventListener("input", function () { i.value = i.value.replace(/\D/g, "").slice(0, 6); }); });
  });
})();
