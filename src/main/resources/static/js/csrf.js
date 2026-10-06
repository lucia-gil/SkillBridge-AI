/**
 * csrf.js — Token CSRF de Spring Security para todo lo que se envía desde JS.
 *
 * Spring Security rechaza cualquier POST sin el token CSRF de la sesión
 * (protege contra que otra página envíe formularios en tu nombre). Los
 * <form th:action> ya lo traen solo; este script cubre lo demás:
 *   1. formularios enviados por el usuario sin th:action (evento "submit"),
 *   2. formularios armados por JS y enviados con form.submit()
 *      (SBAI.submitForm, asignaciones, foros…), que NO disparan "submit",
 *   3. fetch() con POST/PUT/PATCH/DELETE al mismo sitio (cabecera X-CSRF-TOKEN).
 * También expone SBAI.logout(): el cierre de sesión es un POST con token.
 *
 * El token lo deja fragments/seguridad.html en window.SBAI_CSRF.
 */
(function () {
  "use strict";
  var cfg = window.SBAI_CSRF || {};
  window.SBAI = window.SBAI || {};

  function asegurar(form) {
    if (!cfg.token || !form || !form.getAttribute) return;
    if (String(form.getAttribute("method") || "get").toLowerCase() !== "post") return;
    var campo = null;
    for (var i = 0; i < form.elements.length; i++) {
      if (form.elements[i].name === cfg.param) { campo = form.elements[i]; break; }
    }
    if (!campo) {
      campo = document.createElement("input");
      campo.type = "hidden";
      campo.name = cfg.param;
      form.appendChild(campo);
    }
    campo.value = cfg.token;
  }

  if (cfg.token) {
    // 1) Envíos normales (incluye requestSubmit), en fase de captura.
    document.addEventListener("submit", function (e) { asegurar(e.target); }, true);

    // 2) form.submit() programático.
    var submitNativo = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
      asegurar(this);
      return submitNativo.call(this);
    };

    // 3) fetch() que modifica datos en este mismo sitio.
    if (typeof window.fetch === "function") {
      var fetchNativo = window.fetch;
      window.fetch = function (recurso, opciones) {
        opciones = opciones || {};
        var metodo = String(opciones.method || (recurso && recurso.method) || "GET").toUpperCase();
        var url = typeof recurso === "string" ? recurso : (recurso && recurso.url) || "";
        var mismoSitio = !/^[a-z][a-z0-9+.-]*:/i.test(url) || url.indexOf(window.location.origin + "/") === 0;
        if (mismoSitio && metodo !== "GET" && metodo !== "HEAD" && metodo !== "OPTIONS") {
          var cabeceras = new Headers(opciones.headers || (typeof recurso === "object" && recurso.headers) || {});
          if (!cabeceras.has(cfg.header)) cabeceras.set(cfg.header, cfg.token);
          opciones = Object.assign({}, opciones, { headers: cabeceras });
        }
        return fetchNativo.call(this, recurso, opciones);
      };
    }
  }

  window.SBAI.csrf = { token: cfg.token, header: cfg.header, param: cfg.param, asegurar: asegurar };

  /** Cierre de sesión: POST /auth/logout con token (Spring Security). */
  window.SBAI.logout = function () {
    if (window.SBAI._cerrandoSesion) return;   // evita doble envío (dos listeners)
    window.SBAI._cerrandoSesion = true;
    var form = document.createElement("form");
    form.method = "POST";
    form.action = "/auth/logout";
    form.style.display = "none";
    document.body.appendChild(form);
    form.submit();
  };
})();
