/*
 * foro-hilo.js — detalle de un hilo (project-manager/ y colaborador/foro-hilo.html).
 * Requiere foro-utils.js, modal.js, toast.js, topbar-actions.js (SBAI.submitForm)
 * y window.HILO = {id, baseUrl, titulo, topicoCodigo, etiquetas, topicos,
 * maxEdiciones, maxArchivos, maxEnlaces}.
 */
(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    var H = window.HILO || {};
    var U = window.SBForo;
    var submitForm = window.SBAI.submitForm;
    var base = H.baseUrl + "/foro-hilo/" + H.id;

    // ── Texto: enlaces clicables y fechas relativas ──
    document.querySelectorAll("[data-texto]").forEach(U.enlazar);
    U.pintarFechas(document);

    function mensaje(id) { return document.getElementById("m-" + id); }
    function textoDe(id) {
      var el = mensaje(id);
      var p = el && el.querySelector("[data-texto]");
      return p ? p.textContent : "";
    }
    function destellar(el) {
      if (!el) return;
      el.classList.remove("is-flash");
      void el.offsetWidth;
      el.classList.add("is-flash");
      setTimeout(function () { el.classList.remove("is-flash"); }, 1600);
    }
    function irA(id) {
      var el = mensaje(id);
      if (!el) return;
      el.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "center" });
      destellar(el);
      history.replaceState(null, "", "#m-" + id);
    }

    // Al llegar con #m-ID (desde una notificación o después de responder), resaltar ese mensaje.
    var m = /^#m-(\d+)$/.exec(location.hash);
    if (m) setTimeout(function () { destellar(mensaje(m[1])); }, 250);

    // ── Composer ──
    var form = document.getElementById("reply-form");
    var texto = document.getElementById("reply-text");
    var replying = document.getElementById("replying");
    var respuestaA = document.getElementById("respuestaA");
    var adjuntos = null;

    if (form) {
      adjuntos = U.crearAdjuntos({
        input: document.getElementById("reply-archivos"),
        chips: document.getElementById("reply-chips"),
        botonArchivo: document.getElementById("reply-btn-archivo"),
        botonEnlace: document.getElementById("reply-btn-enlace"),
        filaEnlace: document.getElementById("reply-fila-enlace"),
        inputEnlace: document.getElementById("reply-enlace"),
        agregarEnlace: document.getElementById("reply-agregar-enlace"),
        contenedorEnlaces: document.getElementById("reply-enlaces"),
        maxArchivos: H.maxArchivos || 3,
        maxEnlaces: H.maxEnlaces || 3
      });

      form.addEventListener("submit", function (e) {
        var hayTexto = texto.value.trim().length > 0;
        if (!hayTexto && adjuntos.cantidad() === 0) {
          e.preventDefault();
          SBAI.toast.show("Escribe una respuesta o adjunta un archivo antes de publicar.", { type: "error" });
          texto.focus();
          return;
        }
        if (adjuntos.enlacePendiente()) {
          e.preventDefault();
          SBAI.toast.show("Tienes un enlace sin agregar: pulsa \"Agregar\" o bórralo.", { type: "error" });
          return;
        }
        var btn = document.getElementById("publish-reply-btn");
        btn.disabled = true;
        btn.textContent = "Publicando…";
      });
      texto.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
          e.preventDefault();
          if (typeof form.requestSubmit === "function") form.requestSubmit(); else form.submit();
        }
      });
      // Pegar una imagen del portapapeles la adjunta (capturas de pantalla).
      texto.addEventListener("paste", function (e) {
        var items = (e.clipboardData && e.clipboardData.files) || [];
        if (items.length) {
          e.preventDefault();
          var renombrados = Array.prototype.map.call(items, function (f, i) {
            var ext = (f.type.split("/")[1] || "png").replace("jpeg", "jpg");
            return new File([f], "captura-" + Date.now() + (i ? "-" + i : "") + "." + ext, { type: f.type });
          });
          adjuntos.agregarArchivos(renombrados);
          SBAI.toast.show("Imagen pegada como adjunto.", { type: "success" });
        }
      });
      document.getElementById("cancel-reply").addEventListener("click", function () {
        respuestaA.value = "";
        replying.hidden = true;
        texto.focus();
      });
      if (location.hash === "#responder" || texto.value) setTimeout(function () { texto.focus(); }, 200);
    }

    function responderA(id) {
      if (!form) return;
      var el = mensaje(id);
      respuestaA.value = id;
      replying.querySelector("[data-replying-autor]").textContent = el ? el.getAttribute("data-autor") : "";
      var t = textoDe(id).replace(/\s+/g, " ").trim();
      replying.querySelector("[data-replying-texto]").textContent = t || "(mensaje con adjuntos)";
      replying.hidden = false;
      form.scrollIntoView({ behavior: "smooth", block: "center" });
      setTimeout(function () { texto.focus(); }, 300);
    }

    // ── Editar ──
    function avisoEdiciones(restantes) {
      if (restantes < 0) return "";
      var txt = restantes === 1 ? "Esta es tu última edición disponible." : "Te quedan " + restantes + " ediciones para este mensaje.";
      return '<p class="field-hint" style="margin:0">' + txt + " Se mostrará como “editado” y el texto anterior queda en el registro de auditoría.</p>";
    }

    function editarHilo(el) {
      var restantes = parseInt(el.getAttribute("data-restantes"), 10);
      var optsTopico = (H.topicos || []).map(function (t) {
        return '<option value="' + U.escapar(t.codigo) + '"' + (t.codigo === H.topicoCodigo ? " selected" : "") + ">" + U.escapar(t.nombre) + "</option>";
      }).join("");
      var html = '<div class="modal modal-wide fr-modal" role="dialog" aria-modal="true" aria-labelledby="eh-h">' +
        '<div class="modal-header"><div><h2 id="eh-h">Editar hilo</h2><p>Cambiar solo el tópico o las etiquetas no cuenta como edición.</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body">' +
          '<div class="field"><label class="field-label" for="eh-titulo">Título</label><input class="input" id="eh-titulo" maxlength="200"></div>' +
          '<div class="field"><label class="field-label" for="eh-mensaje">Mensaje</label><textarea class="input fr-textarea" id="eh-mensaje" rows="7" maxlength="10000"></textarea></div>' +
          '<div class="field-row">' +
            '<div class="field"><label class="field-label" for="eh-topico">Tópico</label><select class="select" id="eh-topico">' + optsTopico + "</select></div>" +
            '<div class="field"><label class="field-label" for="eh-etiquetas">Etiquetas</label><input class="input" id="eh-etiquetas" placeholder="Separadas por comas"></div>' +
          "</div>" + avisoEdiciones(restantes) +
        "</div>" +
        '<div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" id="eh-guardar">Guardar cambios</button></div></div>';
      var bd = SBAI.modal.open(html);
      bd.querySelector("#eh-titulo").value = H.titulo;
      bd.querySelector("#eh-mensaje").value = textoDe(el.getAttribute("data-msg-id"));
      bd.querySelector("#eh-etiquetas").value = H.etiquetas || "";
      bd.querySelector("#eh-guardar").addEventListener("click", function () {
        var t = bd.querySelector("#eh-titulo").value.trim();
        var c = bd.querySelector("#eh-mensaje").value.trim();
        if (!t || !c) { SBAI.toast.show("Completa el título y el mensaje.", { type: "error" }); return; }
        this.disabled = true;
        submitForm(base + "/editar", {
          titulo: t, contenido: c,
          topico: bd.querySelector("#eh-topico").value,
          etiquetas: bd.querySelector("#eh-etiquetas").value
        });
      });
    }

    function editarRespuesta(el) {
      var id = el.getAttribute("data-msg-id");
      var restantes = parseInt(el.getAttribute("data-restantes"), 10);
      var html = '<div class="modal modal-wide fr-modal" role="dialog" aria-modal="true" aria-labelledby="er-h">' +
        '<div class="modal-header"><div><h2 id="er-h">Editar respuesta</h2></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body"><div class="field"><label class="field-label" for="er-texto">Respuesta</label>' +
        '<textarea class="input fr-textarea" id="er-texto" rows="7" maxlength="10000"></textarea></div>' + avisoEdiciones(restantes) + "</div>" +
        '<div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" id="er-guardar">Guardar cambios</button></div></div>';
      var bd = SBAI.modal.open(html);
      var area = bd.querySelector("#er-texto");
      area.value = textoDe(id);
      setTimeout(function () { area.focus(); }, 50);
      bd.querySelector("#er-guardar").addEventListener("click", function () {
        var c = area.value.trim();
        if (!c) { SBAI.toast.show("Escribe una respuesta antes de guardar.", { type: "error" }); return; }
        this.disabled = true;
        submitForm(base + "/respuestas/" + id + "/editar", { contenido: c });
      });
    }

    // ── Destacar ("me fue útil") sin recargar ──
    function destacar(btn) {
      var id = btn.getAttribute("data-destacar");
      btn.disabled = true;
      fetch(H.baseUrl + "/foro-hilo/mensajes/" + id + "/destacar", {
        method: "POST",
        headers: { "Accept": "application/json", "X-Requested-With": "fetch" },
        credentials: "same-origin"
      }).then(function (r) {
        var tipo = r.headers.get("content-type") || "";
        if (tipo.indexOf("application/json") < 0) throw new Error("Tu sesión expiró. Vuelve a iniciar sesión.");
        return r.json().then(function (data) {
          if (!r.ok) throw new Error(data.error || "No se pudo guardar.");
          return data;
        });
      }).then(function (data) {
        btn.classList.toggle("is-on", data.destacado);
        btn.setAttribute("aria-pressed", data.destacado ? "true" : "false");
        btn.title = data.destacado ? "Quitar de útiles" : "Marcar como útil";
        btn.querySelector("[data-total]").textContent = data.total > 0 ? data.total : "";
      }).catch(function (err) {
        SBAI.toast.show(err.message || "No se pudo guardar.", { type: "error" });
      }).then(function () { btn.disabled = false; });
    }

    // ── Delegación de acciones ──
    document.addEventListener("click", function (e) {
      var b;
      if ((b = e.target.closest("[data-ir-a]"))) {
        e.preventDefault();
        irA(b.getAttribute("data-ir-a"));
      } else if ((b = e.target.closest("[data-responder]"))) {
        responderA(b.getAttribute("data-responder"));
      } else if ((b = e.target.closest("[data-destacar]"))) {
        destacar(b);
      } else if ((b = e.target.closest("[data-solucion]"))) {
        var autor = b.closest(".fr-msg").getAttribute("data-autor");
        SBAI.modal.confirm({
          title: "¿Marcar esta respuesta como solución?",
          desc: "La respuesta de " + autor + " aparecerá destacada arriba del hilo y el hilo quedará como resuelto.",
          confirmLabel: "Marcar como solución",
          onConfirm: function () { submitForm(base + "/respuestas/" + b.getAttribute("data-solucion") + "/marcar-solucion", {}); }
        });
      } else if ((b = e.target.closest("[data-editar]"))) {
        var el = b.closest(".fr-msg");
        if (el.getAttribute("data-inicial") === "true") editarHilo(el); else editarRespuesta(el);
      } else if ((b = e.target.closest("[data-eliminar]"))) {
        var msg = b.closest(".fr-msg");
        var esHilo = msg.getAttribute("data-inicial") === "true";
        SBAI.modal.confirm({
          title: esHilo ? "¿Eliminar este hilo?" : "¿Eliminar esta respuesta?",
          desc: esHilo ? "Se eliminará el hilo con todas sus respuestas y adjuntos. Esta acción no se puede deshacer."
                       : "Se eliminará la respuesta y sus adjuntos. Esta acción no se puede deshacer.",
          confirmLabel: "Eliminar", variant: "destructive",
          onConfirm: function () {
            submitForm(esHilo ? base + "/eliminar" : base + "/respuestas/" + b.getAttribute("data-eliminar") + "/eliminar", {});
          }
        });
      } else if ((b = e.target.closest("[data-fijar]"))) {
        submitForm(H.baseUrl + "/foros/" + b.getAttribute("data-fijar") + "/fijar", { valor: b.getAttribute("data-valor"), volver: "hilo" });
      } else if ((b = e.target.closest("#go-reply"))) {
        e.preventDefault();
        if (form) { form.scrollIntoView({ behavior: "smooth", block: "center" }); setTimeout(function () { texto.focus(); }, 300); }
      }
    });
  });
})();
