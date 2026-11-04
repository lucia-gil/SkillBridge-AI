/*
 * chat.js — Chat de proyectos (RF07) con jQuery + AJAX.
 *
 * Consume el servicio REST /api/chat (ChatApiController) con SBAI.api
 * (js/sbai-api.js = $.ajax + JSON + token CSRF):
 *   - al abrir una sala: GET  /salas/{id}/mensajes        (últimos 40)
 *   - cada 3 segundos:   GET  /salas/{id}/mensajes?despues=ULTIMO_ID
 *                        (sondeo "polling": parece tiempo real sin WebSockets;
 *                         con la pestaña oculta baja a 15 s)
 *   - al enviar:         POST /salas/{id}/mensajes   {contenido}
 *   - al leer:           POST /salas/{id}/leido      {ultimoId}
 *   - resumir:           POST /salas/{id}/resumen-ia
 * Todo el texto se inserta escapado (sin riesgo de inyectar HTML).
 */
(function ($) {
  "use strict";
  if (!$ || !window.SBAI || !SBAI.api) return;

  var CFG = window.SBAI_CHAT || {};
  var SONDEO_MS = 3000, SONDEO_OCULTO_MS = 15000, SALAS_MS = 15000;
  var AGRUPAR_MS = 5 * 60 * 1000;   // mensajes seguidos del mismo autor se agrupan

  var $app = $("#chat-app");
  if (!$app.length) return;
  var $salas = $("#ch-salas"), $lista = $("#ch-lista"), $msgs = $("#ch-mensajes"), $texto = $("#ch-texto");

  var estado = { salas: [], sala: null, ultimoId: 0, primerId: null, ids: {}, temporizador: null, cargando: false, nuevos: 0 };

  // ───────────────────────── utilidades ─────────────────────────
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  /** Escapa y convierte URLs en enlaces (solo http/https) y saltos de línea en <br>. */
  function formatear(texto) {
    return esc(texto).replace(/\bhttps?:\/\/[^\s<]+[^\s<.,;:!?)\]'"]/g, function (url) {
      return '<a href="' + url + '" target="_blank" rel="noopener noreferrer">' + url + "</a>";
    }).replace(/\n/g, "<br>");
  }
  function tono(id) { return Number(id || 0) % 8; }
  function inicial(nombre) { return esc((nombre || "?").trim().charAt(0).toUpperCase()); }
  function avatar(m) {
    return m.tieneFoto
      ? '<img class="ch-avatar" src="/perfil/foto/' + esc(m.usuarioId) + '" alt="" loading="lazy">'
      : '<span class="ch-avatar" aria-hidden="true">' + esc(m.iniciales) + "</span>";
  }
  function alFinal() { var el = $msgs[0]; return el.scrollHeight - el.scrollTop - el.clientHeight < 80; }
  function bajar(suave) { var el = $msgs[0]; el.scrollTo({ top: el.scrollHeight, behavior: suave ? "smooth" : "auto" }); }
  function avisoError(xhr) { if (SBAI.toast) SBAI.toast.show(SBAI.api.mensajeError(xhr), { type: "error" }); }

  // ───────────────────────── salas ─────────────────────────
  function pintarSalas() {
    var q = ($("#ch-buscar").val() || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
    var lista = estado.salas.filter(function (s) {
      return !q || s.proyecto.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").indexOf(q) >= 0;
    });
    var total = estado.salas.reduce(function (n, s) { return n + (s.noLeidos || 0); }, 0);
    $("#ch-total").text(total ? total + " sin leer" : estado.salas.length + (estado.salas.length === 1 ? " sala" : " salas"));
    if (!estado.salas.length) {
      $salas.html('<li class="ch-rooms-empty">Cuando te asignen a un proyecto, aquí aparecerá su sala de chat.</li>');
      return;
    }
    if (!lista.length) { $salas.html('<li class="ch-rooms-empty">Ningún proyecto coincide.</li>'); return; }
    $salas.html(lista.map(function (s) {
      var activa = estado.sala && estado.sala.id === s.id;
      var vista = s.ultimoTexto ? '<b>' + esc(s.ultimoAutor) + ":</b> " + esc(s.ultimoTexto) : "<i>Sin mensajes todavía</i>";
      return '<li role="option" aria-selected="' + activa + '">' +
        '<button type="button" class="ch-room' + (activa ? " is-active" : "") + (s.noLeidos ? " has-unread" : "") + '" data-sala="' + s.id + '">' +
          '<span class="ch-room-mark" data-tono="' + tono(s.proyectoId) + '" aria-hidden="true">' + inicial(s.proyecto) + "</span>" +
          '<span class="ch-room-main">' +
            '<span class="ch-room-top"><span class="ch-room-name">' + esc(s.proyecto) + "</span>" +
              '<span class="ch-room-time">' + esc(s.ultimoCuando || "") + "</span></span>" +
            '<span class="ch-room-bottom"><span class="ch-room-preview">' + vista + "</span>" +
              (s.noLeidos ? '<span class="ch-unread" aria-label="' + s.noLeidos + ' sin leer">' + (s.noLeidos > 99 ? "99+" : s.noLeidos) + "</span>" : "") +
            "</span>" +
          "</span>" +
        "</button></li>";
    }).join(""));
  }

  function cargarSalas(despues) {
    return SBAI.api.get("/api/chat/salas").done(function (salas) {
      estado.salas = salas || [];
      pintarSalas();
      actualizarBadgeSidebar();
      if (despues) despues();
    }).fail(function (xhr) {
      $salas.html('<li class="ch-rooms-empty">No se pudieron cargar las salas. ' + esc(SBAI.api.mensajeError(xhr)) + "</li>");
    });
  }

  function actualizarBadgeSidebar() {
    var total = estado.salas.reduce(function (n, s) { return n + (s.noLeidos || 0); }, 0);
    var $item = $('.sidebar-nav-item[href$="chat.html"]');
    var $b = $item.find(".counter-badge");
    if (total > 0) {
      if (!$b.length) $b = $('<span class="counter-badge"></span>').appendTo($item);
      $b.text(total > 99 ? "99+" : total);
    } else $b.remove();
  }

  // ───────────────────────── conversación ─────────────────────────
  function abrir(salaId) {
    var sala = estado.salas.find(function (s) { return s.id === salaId; });
    if (!sala) return;
    detenerSondeo();
    estado.sala = sala; estado.ultimoId = 0; estado.primerId = null; estado.ids = {}; estado.nuevos = 0;
    history.replaceState(null, "", location.pathname + "?sala=" + sala.id);
    $app.attr("data-estado", "conversacion");
    $("#ch-vacio").prop("hidden", true);
    $("#ch-conv").prop("hidden", false);
    $("#ch-titulo").text(sala.proyecto);
    $("#ch-marca").attr("data-tono", tono(sala.proyectoId)).text(sala.proyecto.trim().charAt(0).toUpperCase());
    $("#ch-miembros-txt").text(sala.miembros + (sala.miembros === 1 ? " integrante" : " integrantes") + " · " + sala.miRol);
    $("#ch-caras").empty(); $("#ch-miembros").prop("hidden", true).empty(); $("#ch-miembros-btn").attr("aria-expanded", "false");
    $("#ch-resumen").prop("hidden", true).empty();
    $("#ch-nuevos").prop("hidden", true);
    $("#ch-solo-lectura").prop("hidden", !sala.soloLectura);
    $texto.prop("disabled", sala.soloLectura).val("");
    ajustarAltura(); refrescarEnviar();
    $lista.html('<li class="ch-loading">Cargando mensajes…</li>');
    $("#ch-anteriores").prop("hidden", true);
    pintarSalas();

    SBAI.api.get("/api/chat/salas/" + sala.id + "/miembros").done(pintarMiembros);
    SBAI.api.get("/api/chat/salas/" + sala.id + "/mensajes").done(function (msgs) {
      if (!estado.sala || estado.sala.id !== sala.id) return;
      $lista.empty();
      if (!msgs.length) {
        $lista.html('<li class="ch-first">' +
          '<span class="ch-first-icon" aria-hidden="true">👋</span><strong>Empieza la conversación</strong>' +
          "<span>Los mensajes de esta sala los ven todos los integrantes de " + esc(sala.proyecto) + ".</span></li>");
      } else {
        agregar(msgs, false);
        $("#ch-anteriores").prop("hidden", msgs.length < 40);
      }
      bajar(false);
      marcarLeido();
      if (!sala.soloLectura && window.matchMedia("(min-width: 861px)").matches) $texto.trigger("focus");
      programarSondeo();
    }).fail(function (xhr) { $lista.html('<li class="ch-loading">' + esc(SBAI.api.mensajeError(xhr)) + "</li>"); });
  }

  function pintarMiembros(miembros) {
    var caras = miembros.slice(0, 4).map(function (m) { return avatar(m); }).join("");
    $("#ch-caras").html(caras);
    $("#ch-miembros").html('<ul>' + miembros.map(function (m) {
      return "<li>" + avatar(m) + '<span><b>' + esc(m.nombre) + "</b><small>" + esc(m.cargo || "") + "</small></span>" +
        '<em class="' + (m.rol === "PM" ? "is-pm" : "") + '">' + esc(m.rol) + "</em></li>";
    }).join("") + "</ul>");
  }

  /** Agrega mensajes (al final o, con alPrincipio, antes de los existentes). */
  function agregar(msgs, alPrincipio) {
    msgs = msgs.filter(function (m) { return !estado.ids[m.id]; });
    if (!msgs.length) return 0;
    $lista.find(".ch-first").remove();
    var html = "", anterior = alPrincipio ? null : ultimoRenderizado();
    msgs.forEach(function (m) {
      estado.ids[m.id] = true;
      if (!anterior || anterior.dia !== m.dia) html += '<li class="ch-day" data-dia="' + esc(m.dia) + '"><span>' + esc(m.dia) + "</span></li>";
      var seguido = anterior && anterior.autorId === m.autorId && anterior.dia === m.dia &&
        (new Date(m.iso) - new Date(anterior.iso)) < AGRUPAR_MS;
      html += '<li class="ch-msg' + (m.propio ? " is-mine" : "") + (seguido ? " is-cont" : "") + '" data-id="' + m.id + '"' +
        ' data-autor="' + m.autorId + '" data-iso="' + esc(m.iso) + '" data-dia="' + esc(m.dia) + '">' +
        (m.propio ? "" : '<span class="ch-msg-av">' + (seguido ? "" : avatar(m)) + "</span>") +
        '<div class="ch-bubble">' +
          (seguido || m.propio ? "" : '<span class="ch-author">' + esc(m.autor) + (m.cargo ? '<small>' + esc(m.cargo) + "</small>" : "") + "</span>") +
          '<p class="ch-text">' + formatear(m.contenido) + "</p>" +
          '<time datetime="' + esc(m.iso) + '" title="' + esc(m.fechaCompleta) + '">' + esc(m.hora) + "</time>" +
        "</div></li>";
      anterior = m;
    });
    if (alPrincipio) {
      // Evitar separador de día duplicado en la unión
      var $primero = $lista.children().first();
      if ($primero.hasClass("ch-day") && msgs.length && $primero.attr("data-dia") === msgs[msgs.length - 1].dia) $primero.remove();
      $lista.prepend(html);
    } else $lista.append(html);
    msgs.forEach(function (m) {
      if (m.id > estado.ultimoId) estado.ultimoId = m.id;
      if (estado.primerId === null || m.id < estado.primerId) estado.primerId = m.id;
    });
    return msgs.length;
  }

  function ultimoRenderizado() {
    var $u = $lista.children(".ch-msg").last();
    return $u.length ? { autorId: Number($u.attr("data-autor")), iso: $u.attr("data-iso"), dia: $u.attr("data-dia") } : null;
  }

  function marcarLeido() {
    if (!estado.sala || !estado.ultimoId || document.hidden) return;
    var s = estado.sala;
    SBAI.api.post("/api/chat/salas/" + s.id + "/leido", { ultimoId: estado.ultimoId }).done(function () {
      s.noLeidos = 0; pintarSalas(); actualizarBadgeSidebar();
    });
  }

  // ───────────────────────── sondeo (polling) ─────────────────────────
  function programarSondeo() {
    detenerSondeo();
    estado.temporizador = setTimeout(sondear, document.hidden ? SONDEO_OCULTO_MS : SONDEO_MS);
  }
  function detenerSondeo() { if (estado.temporizador) clearTimeout(estado.temporizador); estado.temporizador = null; }

  function sondear() {
    var s = estado.sala;
    if (!s) return;
    var estabaAbajo = alFinal();
    SBAI.api.get("/api/chat/salas/" + s.id + "/mensajes", { despues: estado.ultimoId }).done(function (msgs) {
      if (!estado.sala || estado.sala.id !== s.id) return;
      var n = agregar(msgs, false);
      if (n) {
        var ajenos = msgs.filter(function (m) { return !m.propio; }).length;
        if (estabaAbajo) { bajar(true); marcarLeido(); }
        else if (ajenos) { estado.nuevos += ajenos; $("#ch-nuevos").prop("hidden", false).find("span").text(estado.nuevos === 1 ? "1 mensaje nuevo" : estado.nuevos + " mensajes nuevos"); }
      }
    }).always(programarSondeo);
  }

  // Las otras salas (vistas previas y no leídos) se refrescan con menos frecuencia.
  setInterval(function () { if (!document.hidden) cargarSalas(); }, SALAS_MS);
  document.addEventListener("visibilitychange", function () {
    if (!document.hidden && estado.sala) { sondear(); marcarLeido(); }
  });

  // ───────────────────────── enviar ─────────────────────────
  function ajustarAltura() { var el = $texto[0]; el.style.height = "auto"; el.style.height = Math.min(el.scrollHeight, 160) + "px"; }
  function refrescarEnviar() { $("#ch-enviar").prop("disabled", !$.trim($texto.val()) || $texto.prop("disabled")); }

  $texto.on("input", function () { ajustarAltura(); refrescarEnviar(); });
  $texto.on("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); $("#ch-form").trigger("submit"); }
  });

  $("#ch-form").on("submit", function (e) {
    e.preventDefault();
    var s = estado.sala, contenido = $.trim($texto.val());
    if (!s || !contenido || estado.cargando) return;
    estado.cargando = true;
    $("#ch-enviar").prop("disabled", true).addClass("is-sending");
    SBAI.api.post("/api/chat/salas/" + s.id + "/mensajes", { contenido: contenido }).done(function (m) {
      $texto.val(""); ajustarAltura();
      agregar([m], false); bajar(true);
      s.ultimoId = m.id; s.ultimoTexto = m.contenido; s.ultimoAutor = "Tú"; s.ultimoCuando = m.hora; s.ultimoIso = m.iso;
      estado.salas.sort(function (a, b) { return String(b.ultimoIso || "").localeCompare(String(a.ultimoIso || "")); });
      pintarSalas();
    }).fail(avisoError).always(function () {
      estado.cargando = false; $("#ch-enviar").removeClass("is-sending"); refrescarEnviar(); $texto.trigger("focus");
    });
  });

  // ───────────────────────── anteriores, nuevos, miembros, resumen ─────────────────────────
  $("#ch-anteriores").on("click", function () {
    var s = estado.sala, $b = $(this);
    if (!s || !estado.primerId) return;
    $b.prop("disabled", true).text("Cargando…");
    var el = $msgs[0], alto = el.scrollHeight;
    SBAI.api.get("/api/chat/salas/" + s.id + "/mensajes", { antes: estado.primerId }).done(function (msgs) {
      agregar(msgs, true);
      el.scrollTop = el.scrollHeight - alto;   // mantener la posición de lectura
      $b.prop("hidden", msgs.length < 40);
    }).fail(avisoError).always(function () { $b.prop("disabled", false).text("Ver mensajes anteriores"); });
  });

  $("#ch-nuevos").on("click", function () { bajar(true); });
  $msgs.on("scroll", function () {
    if (alFinal() && estado.nuevos) { estado.nuevos = 0; $("#ch-nuevos").prop("hidden", true); marcarLeido(); }
  });

  $("#ch-miembros-btn").on("click", function () {
    var abierto = $("#ch-miembros").prop("hidden");
    $("#ch-miembros").prop("hidden", !abierto);
    $(this).attr("aria-expanded", abierto);
  });
  $(document).on("click", function (e) {
    if (!$(e.target).closest("#ch-miembros, #ch-miembros-btn").length) { $("#ch-miembros").prop("hidden", true); $("#ch-miembros-btn").attr("aria-expanded", "false"); }
  });

  $("#ch-resumir").on("click", function () {
    var s = estado.sala, $b = $(this);
    if (!s) return;
    $b.prop("disabled", true).find("span").text("Resumiendo…");
    var $r = $("#ch-resumen").prop("hidden", false).html('<div class="ch-summary-in is-loading"><span class="ch-dots"><i></i><i></i><i></i></span> La IA está leyendo la conversación…</div>');
    SBAI.api.post("/api/chat/salas/" + s.id + "/resumen-ia").done(function (r) {
      var lineas = String(r.resumen || "").split(/\n+/).map(function (l) { return l.replace(/^\s*[-•*]\s*/, "").trim(); }).filter(Boolean);
      $r.html('<div class="ch-summary-in"><div class="ch-summary-head"><span><svg class="icon" aria-hidden="true"><use href="/img/icons.svg#icon-sparkles"></use></svg>Resumen con IA</span>' +
        '<button type="button" class="ch-icon-btn" data-cerrar-resumen aria-label="Cerrar resumen"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        "<ul>" + lineas.map(function (l) { return "<li>" + esc(l) + "</li>"; }).join("") + "</ul></div>");
    }).fail(function (xhr) { $r.prop("hidden", true).empty(); avisoError(xhr); })
      .always(function () { $b.prop("disabled", false).find("span").text("Resumir con IA"); });
  });
  $("#ch-resumen").on("click", "[data-cerrar-resumen]", function () { $("#ch-resumen").prop("hidden", true).empty(); });

  // ───────────────────────── navegación ─────────────────────────
  $salas.on("click", "[data-sala]", function () { abrir(Number($(this).attr("data-sala"))); });
  $("#ch-buscar").on("input", pintarSalas);
  $("#ch-volver").on("click", function () {
    detenerSondeo(); estado.sala = null;
    $app.attr("data-estado", "lista");
    history.replaceState(null, "", location.pathname);
    pintarSalas();
  });

  // Inicio: cargar salas y abrir la pedida (?sala=), o la más reciente en escritorio.
  cargarSalas(function () {
    var pedida = CFG.salaInicial || Number(new URLSearchParams(location.search).get("sala")) || null;
    if (pedida && estado.salas.some(function (s) { return s.id === pedida; })) abrir(pedida);
    else if (estado.salas.length && window.matchMedia("(min-width: 861px)").matches) abrir(estado.salas[0].id);
  });
})(window.jQuery);
