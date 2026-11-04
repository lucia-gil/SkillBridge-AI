/**
 * asistente-ia.js — Asistente IA (chatbot) con jQuery/AJAX contra el
 * servicio REST /api/asistente/** (AsistenteIaController):
 *   GET    /api/asistente/conversaciones        → historial
 *   GET    /api/asistente/conversaciones/{id}   → mensajes de una conversación
 *   DELETE /api/asistente/conversaciones/{id}   → eliminarla
 *   POST   /api/asistente/mensaje {conversacionId, mensaje} → respuesta de la IA
 * El servidor guarda cada turno (memoria) y arma el contexto real del usuario.
 */
(function ($) {
  "use strict";
  var $app = $("#as-app");
  if (!$app.length || !window.SBAI || !window.SBAI.api) return;
  var api = window.SBAI.api;

  var $lista = $("#as-lista"), $msgs = $("#as-msgs"), $empty = $("#as-empty"), $titulo = $("#as-titulo");
  var $input = $("#as-input"), $send = $("#as-send"), $borrar = $("#as-borrar");
  var estado = { id: null, enviando: false, conversaciones: [] };

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function icon(n, cls) { return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="/img/icons.svg#' + n + '"></use></svg>'; }

  /** Markdown mínimo y seguro: **negrita**, viñetas "- " / "* " / "1. " y saltos de línea. */
  function formato(texto) {
    var lineas = esc(texto).split(/\r?\n/), html = "", lista = null;
    function cerrar() { if (lista) { html += "</" + lista + ">"; lista = null; } }
    lineas.forEach(function (l) {
      var t = l.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>").replace(/`([^`]+)`/g, "<code>$1</code>");
      var m;
      if ((m = t.match(/^\s*[-*•]\s+(.*)$/))) { if (lista !== "ul") { cerrar(); html += "<ul>"; lista = "ul"; } html += "<li>" + m[1] + "</li>"; }
      else if ((m = t.match(/^\s*\d+[.)]\s+(.*)$/))) { if (lista !== "ol") { cerrar(); html += "<ol>"; lista = "ol"; } html += "<li>" + m[1] + "</li>"; }
      else if (/^\s*$/.test(t)) { cerrar(); }
      else { cerrar(); html += "<p>" + t.replace(/^#{1,4}\s*/, "") + "</p>"; }
    });
    cerrar();
    return html;
  }

  /* ---------------- Mensajes ---------------- */
  function burbuja(m) {
    if (m.deUsuario) {
      return '<div class="as-msg is-user"><div class="as-bubble">' + esc(m.texto).replace(/\n/g, "<br>") + "</div>" +
          '<span class="as-meta">' + esc(m.hora || "") + "</span></div>";
    }
    var acciones = (m.acciones || []).map(function (a) {
      return '<a class="as-accion" href="' + esc(a.url) + '">' + esc(a.etiqueta) + icon("icon-chevron-right", "icon-sm") + "</a>";
    }).join("");
    return '<div class="as-msg is-bot' + (m.error ? " is-error" : "") + '"><span class="as-bot-avatar sm" aria-hidden="true">' +
        icon(m.error ? "icon-alert-circle" : "icon-sparkles") + "</span><div class=\"as-msg-body\"><div class=\"as-bubble\">" + formato(m.texto) + "</div>" +
        (acciones ? '<div class="as-acciones">' + acciones + "</div>" : "") +
        '<span class="as-meta">Asistente SkillBridge' + (m.hora ? " · " + esc(m.hora) : "") + "</span></div></div>";
  }

  function alFinal() { $msgs.stop().animate({ scrollTop: $msgs[0].scrollHeight }, 220); }

  function mostrarVacio(vacio) {
    $empty.toggle(vacio);
    $borrar.prop("hidden", estado.id == null);
  }

  function escribiendo(on) {
    $msgs.find(".as-typing").remove();
    if (on) $msgs.append('<div class="as-msg is-bot as-typing"><span class="as-bot-avatar sm">' + icon("icon-sparkles") +
        '</span><div class="as-bubble"><i></i><i></i><i></i><span class="sr-only">El asistente está escribiendo…</span></div></div>');
  }

  /* ---------------- Historial ---------------- */
  function pintarLista() {
    if (!estado.conversaciones.length) {
      $lista.html('<li class="as-hist-empty">Tus conversaciones aparecerán aquí.</li>');
      return;
    }
    $lista.html(estado.conversaciones.map(function (c) {
      return '<li><button type="button" class="as-hist-item' + (c.id === estado.id ? " is-active" : "") + '" data-id="' + c.id + '"' +
          (c.id === estado.id ? ' aria-current="true"' : "") + ">" + icon("icon-message", "icon-sm") +
          '<span><b>' + esc(c.titulo) + "</b><small>" + esc(c.cuando) + "</small></span></button></li>";
    }).join(""));
  }

  function cargarLista() {
    return api.get("/api/asistente/conversaciones").done(function (lista) {
      estado.conversaciones = lista;
      pintarLista();
    }).fail(function () { $lista.html('<li class="as-hist-empty">No se pudo cargar el historial.</li>'); });
  }

  function url(id) {
    try { history.replaceState(null, "", location.pathname + (id ? "?c=" + id : "")); } catch (e) { /* sin historial */ }
  }

  function abrir(id) {
    estado.id = id;
    pintarLista();
    $msgs.children(":not(#as-empty)").remove();
    $empty.hide();
    $msgs.append('<div class="as-cargando">Cargando conversación…</div>');
    api.get("/api/asistente/conversaciones/" + id).done(function (mensajes) {
      $msgs.find(".as-cargando").remove();
      var c = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
      $titulo.text(c ? c.titulo : "Conversación");
      $msgs.append(mensajes.map(burbuja).join(""));
      mostrarVacio(false);
      $msgs.scrollTop($msgs[0].scrollHeight);
      url(id);
    }).fail(function (xhr) {
      $msgs.find(".as-cargando").remove();
      SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" });
      nueva();
    });
    $app.removeClass("hist-abierto");
    $("#as-ver-hist").attr("aria-expanded", "false");
  }

  function nueva() {
    estado.id = null;
    $msgs.children(":not(#as-empty)").remove();
    $titulo.text("Nueva conversación");
    mostrarVacio(true);
    pintarLista();
    url(null);
    $app.removeClass("hist-abierto");
    $input.trigger("focus");
  }

  /* ---------------- Enviar ---------------- */
  function enviar(texto) {
    texto = $.trim(texto);
    if (!texto || estado.enviando) return;
    estado.enviando = true;
    mostrarVacio(false);
    $empty.hide();
    var ahora = new Date();
    $msgs.append(burbuja({ deUsuario: true, texto: texto, hora: ("0" + ahora.getHours()).slice(-2) + ":" + ("0" + ahora.getMinutes()).slice(-2) }));
    $input.val("").trigger("input");
    escribiendo(true);
    alFinal();

    api.post("/api/asistente/mensaje", { conversacionId: estado.id, mensaje: texto })
      .done(function (r) {
        escribiendo(false);
        $msgs.append(burbuja({ deUsuario: false, texto: r.respuesta, hora: r.hora, acciones: r.acciones, error: !r.ok }));
        if (r.nueva) {
          estado.id = r.conversacionId;
          $titulo.text(r.titulo);
          url(r.conversacionId);
        }
        cargarLista();
        mostrarVacio(false);
        alFinal();
      })
      .fail(function (xhr) {
        escribiendo(false);
        $msgs.append(burbuja({ deUsuario: false, texto: api.mensajeError(xhr), error: true }));
        alFinal();
      })
      .always(function () { estado.enviando = false; $input.trigger("input").trigger("focus"); });
  }

  /* ---------------- Eventos ---------------- */
  $("#as-form").on("submit", function (e) { e.preventDefault(); enviar($input.val()); });
  $input.on("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); enviar($input.val()); }
  }).on("input", function () {
    this.style.height = "auto";
    this.style.height = Math.min(this.scrollHeight, 160) + "px";
    $send.prop("disabled", !$.trim(this.value) || estado.enviando);
  });
  $msgs.on("click", "[data-as-sug]", function () { enviar($(this).text()); });
  $lista.on("click", ".as-hist-item", function () { abrir(Number($(this).data("id"))); });
  $("#as-nueva").on("click", nueva);
  $("#as-ver-hist").on("click", function () {
    var abierto = $app.toggleClass("hist-abierto").hasClass("hist-abierto");
    $(this).attr("aria-expanded", abierto ? "true" : "false");
  });
  $borrar.on("click", function () {
    if (estado.id == null) return;
    var id = estado.id;
    SBAI.modal.confirm({
      title: "¿Eliminar esta conversación?",
      desc: "Se borran todos sus mensajes y el asistente deja de recordarla.",
      confirmLabel: "Eliminar",
      variant: "destructive",
      onConfirm: function () {
        api.del("/api/asistente/conversaciones/" + id).done(function () {
          SBAI.toast.show("Conversación eliminada.", { type: "success" });
          nueva();
          cargarLista();
        }).fail(function (xhr) { SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" }); });
      }
    });
  });

  var inicial = Number($app.data("inicial")) || null;
  cargarLista().always(function () {
    if (inicial) abrir(inicial); else mostrarVacio(true);
  });
})(window.jQuery);
