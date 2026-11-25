/**
 * csrf-fresco.js — Formularios de /auth/ (login, registro, recuperar y
 * restablecer contraseña) con un token CSRF siempre vigente.
 *
 * El token que trae la página está atado a la sesión de cuando se abrió. Si
 * la pantalla quedó abierta y la sesión venció (expiración, computadora
 * suspendida), Spring Security rechazaba el primer intento con "Tu sesión
 * expiró" y recién dejaba entrar al segundo. Aquí, justo antes de enviar, se
 * pide un token nuevo a GET /auth/csrf y se pone en el formulario.
 *
 * Va en la fase de burbuja sobre window, o sea DESPUÉS de las validaciones
 * propias de cada página: si alguna canceló el envío, no se hace nada.
 */
(function () {
  "use strict";
  if (!window.fetch) return;

  window.addEventListener("submit", function (e) {
    var form = e.target;
    if (e.defaultPrevented || !form || String(form.getAttribute("method") || "get").toLowerCase() !== "post") return;
    if (form.dataset.csrfFresco === "1") { delete form.dataset.csrfFresco; return; }   // segunda pasada: ya lleva token nuevo

    e.preventDefault();
    var boton = e.submitter || null;
    fetch("/auth/csrf", { credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json" } })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (t) {
        var campo = form.elements[t.param];
        if (!campo) {
          campo = document.createElement("input");
          campo.type = "hidden";
          campo.name = t.param;
          form.appendChild(campo);
        }
        campo.value = t.token;
      })
      .catch(function () { /* sin red: se envía con el token que ya tenía */ })
      .then(function () {
        form.dataset.csrfFresco = "1";
        // requestSubmit conserva el botón pulsado y vuelve a pasar por las validaciones.
        if (form.requestSubmit) form.requestSubmit(boton && boton.form === form ? boton : undefined);
        else { delete form.dataset.csrfFresco; HTMLFormElement.prototype.submit.call(form); }
      });
  });
})();
