/**
 * sbai-api.js — Ayudante para consumir los servicios web REST de SkillBridge
 * con jQuery/AJAX (chat, chatbot, AI Talent Matching, buscador...).
 *
 *   SBAI.api.get("/api/chat/salas/3/mensajes", { despues: 120 })
 *       .done(function (datos) { ... });
 *   SBAI.api.post("/api/asistente/mensaje", { mensaje: "Hola" })
 *       .done(function (r) { ... });
 *
 * - Envía y recibe JSON.
 * - Agrega solo el token CSRF de Spring Security en POST/PUT/PATCH/DELETE
 *   al mismo sitio (cabecera que deja fragments/seguridad.html en SBAI_CSRF).
 * - Si la sesión expiró (401, o Spring Security redirige al login), lleva al
 *   login en vez de fallar en silencio.
 * - SBAI.api.mensajeError(xhr) da un texto claro para mostrar al usuario.
 */
(function ($) {
  "use strict";
  window.SBAI = window.SBAI || {};
  if (!$) { return; }   // página sin jQuery: no hace nada

  var cfg = window.SBAI_CSRF || {};

  function mismoSitio(url) {
    return !/^[a-z][a-z0-9+.-]*:/i.test(url || "") || String(url).indexOf(window.location.origin + "/") === 0;
  }

  $.ajaxSetup({
    headers: { "X-Requested-With": "XMLHttpRequest" },
    beforeSend: function (xhr, opciones) {
      var metodo = String(opciones.type || "GET").toUpperCase();
      if (cfg.token && mismoSitio(opciones.url) && metodo !== "GET" && metodo !== "HEAD" && metodo !== "OPTIONS") {
        xhr.setRequestHeader(cfg.header || "X-CSRF-TOKEN", cfg.token);
      }
    }
  });

  // Sesión vencida: Spring Security responde 401 o redirige al HTML del login.
  $(document).ajaxComplete(function (e, xhr) {
    var tipo = xhr.getResponseHeader && xhr.getResponseHeader("Content-Type") || "";
    var esLogin = xhr.status === 200 && tipo.indexOf("text/html") >= 0 && /name="password"/.test(xhr.responseText || "");
    if (xhr.status === 401 || esLogin) {
      window.location.href = "/auth/login.html?expirada";
    }
  });

  function json(metodo, url, datos) {
    return $.ajax({
      url: url,
      type: metodo,
      dataType: "json",
      contentType: datos === undefined ? undefined : "application/json; charset=UTF-8",
      data: datos === undefined ? undefined : JSON.stringify(datos)
    });
  }

  window.SBAI.api = {
    get: function (url, params) { return $.ajax({ url: url, type: "GET", dataType: "json", data: params }); },
    post: function (url, datos) { return json("POST", url, datos === undefined ? {} : datos); },
    put: function (url, datos) { return json("PUT", url, datos === undefined ? {} : datos); },
    del: function (url) { return $.ajax({ url: url, type: "DELETE", dataType: "json" }); },
    /** Texto para el usuario a partir de un error de jQuery. */
    mensajeError: function (xhr) {
      var r = xhr && xhr.responseJSON;
      if (r && (r.error || r.mensaje)) return r.error || r.mensaje;
      if (!xhr || xhr.status === 0) return "No hay conexión con el servidor. Revisa tu red e intenta de nuevo.";
      if (xhr.status === 403) return "No tienes permiso para hacer esto.";
      if (xhr.status === 404) return "No encontramos lo que buscabas.";
      if (xhr.status === 429) return "Demasiadas solicitudes seguidas. Espera un momento.";
      return "Algo salió mal (" + xhr.status + "). Intenta de nuevo.";
    }
  };
})(window.jQuery);
