/**
 * asistente-ia.js — Asistente IA (chatbot) con jQuery/AJAX contra el
 * servicio REST /api/asistente/** (AsistenteIaController):
 *   GET    /api/asistente/conversaciones        → historial
 *   GET    /api/asistente/conversaciones/{id}   → mensajes de una conversación
 *   DELETE /api/asistente/conversaciones/{id}   → eliminarla
 *   POST   /api/asistente/mensaje {conversacionId, mensaje} → respuesta de la IA
 *   GET    /api/asistente/en-curso              → conversación que sigue esperando respuesta
 * El servidor guarda cada turno (memoria) y arma el contexto real del usuario.
 */
(function ($) {
  "use strict";
  var $app = $("#as-app");
  if (!$app.length || !window.SBAI || !window.SBAI.api) return;
  var api = window.SBAI.api;

  var $lista = $("#as-lista"), $msgs = $("#as-msgs"), $empty = $("#as-empty"), $titulo = $("#as-titulo");
  var $input = $("#as-input"), $send = $("#as-send"), $borrar = $("#as-borrar");
  var $renombrar = $("#as-renombrar"), $fijar = $("#as-fijar"), $adj = $("#as-adjuntos"), $file = $("#as-file");
  var MAX_ADJ = Number($("#as-clip").data("max")) || 3, MAX_BYTES = 5 * 1024 * 1024;
  var estado = { id: null, enviando: false, conversaciones: [], archivos: [] };

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  // Mascota del asistente: la imagen va encima del ícono; si el archivo no está, se ve el ícono.
  var MASCOTA = '<img src="/img/chatcito.jpg" alt="">';
  function sinImagen(img) { if (img && img.parentNode) img.parentNode.removeChild(img); }
  document.addEventListener("error", function (e) {
    var t = e.target;
    if (t && t.tagName === "IMG" && t.closest && t.closest(".as-bot-avatar, .as-empty-icon")) sinImagen(t);
  }, true);
  $(".as-empty-icon img").each(function () { if (this.complete && !this.naturalWidth) sinImagen(this); });
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
      return '<div class="as-msg is-user">' + adjuntosHtml(m.adjuntos) +
          (m.texto ? '<div class="as-bubble">' + esc(m.texto).replace(/\n/g, "<br>") + "</div>" : "") +
          '<span class="as-meta">' + esc(m.hora || "") + "</span></div>";
    }
    var acciones = (m.acciones || []).map(function (a) {
      return '<a class="as-accion" href="' + esc(a.url) + '">' + esc(a.etiqueta) + icon("icon-chevron-right", "icon-sm") + "</a>";
    }).join("");
    return '<div class="as-msg is-bot' + (m.error ? " is-error" : "") + '"><span class="as-bot-avatar sm" aria-hidden="true">' +
        (m.error ? icon("icon-alert-circle") : icon("icon-sparkles") + MASCOTA) + "</span><div class=\"as-msg-body\"><div class=\"as-bubble\">" + formato(m.texto) + "</div>" +
        (acciones ? '<div class="as-acciones">' + acciones + "</div>" : "") +
        '<span class="as-meta">Asistente SkillBridge' + (m.hora ? " · " + esc(m.hora) : "") + "</span></div></div>";
  }

  /** Adjuntos de un mensaje: miniaturas para imágenes, ficha para PDF y texto. */
  function adjuntosHtml(adjs, locales) {
    if (!adjs || !adjs.length) return "";
    return '<div class="as-msg-files">' + adjs.map(function (a, i) {
      var href = a.id ? "/api/asistente/adjuntos/" + a.id : null;
      var src = a.id ? href : (locales && locales[i]);
      if (a.imagen) {
        return (href ? '<a class="as-file-img" href="' + href + '" target="_blank" rel="noopener" title="' + esc(a.nombre) + '">' : '<span class="as-file-img">') +
          '<img src="' + esc(src) + '" alt="' + esc(a.nombre) + '">' + (href ? "</a>" : "</span>");
      }
      return (href ? '<a class="as-file" href="' + href + '" target="_blank" rel="noopener">' : '<span class="as-file">') +
        icon("icon-file-text") + "<span><b>" + esc(a.nombre) + "</b><small>" + esc(a.tamano || "") + "</small></span>" + (href ? "</a>" : "</span>");
    }).join("") + "</div>";
  }

  function alFinal() { $msgs.stop().animate({ scrollTop: $msgs[0].scrollHeight }, 220); }

  function mostrarVacio(vacio) {
    $empty.toggle(vacio);
    $borrar.prop("hidden", estado.id == null);
    $renombrar.prop("hidden", estado.id == null);
    $fijar.prop("hidden", estado.id == null);
    var c = actual();
    $fijar.toggleClass("is-on", !!(c && c.fijada)).attr("aria-pressed", c && c.fijada ? "true" : "false")
      .attr("title", c && c.fijada ? "Quitar de fijadas" : "Fijar conversación");
  }

  function escribiendo(on) {
    $msgs.find(".as-typing").remove();
    if (on) $msgs.append('<div class="as-msg is-bot as-typing"><span class="as-bot-avatar sm">' + icon("icon-sparkles") + MASCOTA +
        '</span><div class="as-bubble"><i></i><i></i><i></i><span class="sr-only">El asistente está escribiendo…</span></div></div>');
  }

  /* ---------------- Historial ---------------- */
  function pintarLista() {
    if (!estado.conversaciones.length) {
      $lista.html('<li class="as-hist-empty">Tus conversaciones aparecerán aquí.</li>');
      return;
    }
    var fijadas = estado.conversaciones.filter(function (c) { return c.fijada; });
    var resto = estado.conversaciones.filter(function (c) { return !c.fijada; });
    function item(c) {
      return '<li class="as-hist-li' + (c.id === estado.id ? " is-active" : "") + '" data-id="' + c.id + '">' +
          '<button type="button" class="as-hist-item' + (c.id === estado.id ? " is-active" : "") + '" data-id="' + c.id + '"' +
          (c.id === estado.id ? ' aria-current="true"' : "") + ">" + icon(c.fijada ? "icon-pin" : "icon-message", "icon-sm") +
          '<span><b>' + esc(c.titulo) + "</b><small>" + esc(c.cuando) + "</small></span></button>" +
          '<button type="button" class="as-hist-more" data-menu="' + c.id + '" aria-label="Opciones de la conversación" aria-haspopup="menu">' + icon("icon-more-h", "icon-sm") + "</button></li>";
    }
    $lista.html((fijadas.length ? '<li class="as-hist-group">Fijadas</li>' + fijadas.map(item).join("") +
        (resto.length ? '<li class="as-hist-group">Recientes</li>' : "") : "") + resto.map(item).join(""));
  }

  function cargarLista() {
    return api.get("/api/asistente/conversaciones").done(function (lista) {
      estado.conversaciones = lista;
      pintarLista();
    }).fail(function () { $lista.html('<li class="as-hist-empty">No se pudo cargar el historial.</li>'); });
  }

  function actual() { return estado.conversaciones.filter(function (x) { return x.id === estado.id; })[0]; }

  /* ---------------- Renombrar y fijar ---------------- */
  function cambiar(id, datos) {
    return api.put("/api/asistente/conversaciones/" + id, datos).done(function (v) {
      estado.conversaciones.forEach(function (c) { if (c.id === v.id) { c.titulo = v.titulo; c.fijada = v.fijada; } });
      estado.conversaciones.sort(function (a, b) { return (b.fijada ? 1 : 0) - (a.fijada ? 1 : 0); });
      if (v.id === estado.id) $titulo.text(v.titulo);
      pintarLista();
      mostrarVacio(estado.id == null);
    }).fail(function (xhr) { SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" }); });
  }
  function fijar(id) {
    var c = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
    if (!c) return;
    cambiar(id, { fijada: !c.fijada }).done(function (v) {
      SBAI.toast.show(v.fijada ? "Conversación fijada arriba del historial." : "Conversación quitada de fijadas.", { type: "success" });
    });
  }
  /** Edita el nombre en el mismo lugar: Enter guarda, Esc cancela. */
  function renombrar(id, $donde) {
    var c = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
    if (!c) return;
    var $in = $('<input class="as-rename" maxlength="120" aria-label="Nuevo nombre de la conversación">').val(c.titulo);
    var $orig = $donde.contents().detach();
    $donde.append($in);
    $in.trigger("focus").trigger("select");
    var listo = false;
    function terminar(guardar) {
      if (listo) return;
      listo = true;
      var nuevo = $.trim($in.val());
      $in.remove();
      $donde.append($orig);
      if (guardar && nuevo && nuevo !== c.titulo) cambiar(id, { titulo: nuevo }); else pintarLista();
    }
    $in.on("keydown", function (e) {
      if (e.key === "Enter") { e.preventDefault(); terminar(true); }
      if (e.key === "Escape") { e.preventDefault(); terminar(false); }
    }).on("blur", function () { terminar(true); });
  }

  /* Menú ⋯ de cada conversación */
  function cerrarMenu() { $(".as-menu").remove(); $(".as-hist-more").attr("aria-expanded", "false"); }
  $lista.on("click", ".as-hist-more", function (e) {
    e.stopPropagation();
    var id = Number($(this).data("menu")), abierto = $(this).attr("aria-expanded") === "true";
    cerrarMenu();
    if (abierto) return;
    var c = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
    var $m = $('<div class="as-menu" role="menu">' +
      '<button type="button" role="menuitem" data-acc="renombrar">' + icon("icon-edit", "icon-sm") + "Cambiar nombre</button>" +
      '<button type="button" role="menuitem" data-acc="fijar">' + icon("icon-pin", "icon-sm") + (c && c.fijada ? "Quitar de fijadas" : "Fijar") + "</button>" +
      '<button type="button" role="menuitem" data-acc="eliminar" class="is-danger">' + icon("icon-trash", "icon-sm") + "Eliminar</button></div>");
    $(this).attr("aria-expanded", "true").closest("li").append($m);
    $m.find("button").first().trigger("focus");
    $m.on("click", "button", function (ev) {
      ev.stopPropagation();
      var acc = $(this).data("acc"), $li = $m.closest("li");
      cerrarMenu();
      if (acc === "renombrar") renombrar(id, $li.find(".as-hist-item span").first());
      else if (acc === "fijar") fijar(id);
      else eliminar(id);
    });
  });
  $(document).on("click", function (e) { if (!$(e.target).closest(".as-menu").length) cerrarMenu(); })
    .on("keydown", function (e) { if (e.key === "Escape") cerrarMenu(); });

  function url(id) {
    try { history.replaceState(null, "", location.pathname + (id ? "?c=" + id : "")); } catch (e) { /* sin historial */ }
  }

  /*
   * Respuesta en curso: si la persona sale de la página mientras la IA
   * responde y vuelve, se reabre esa conversación (su mensaje ya está
   * guardado) con "escribiendo…" y se consulta cada 3 s hasta que termine,
   * igual que el sondeo del chat de proyectos.
   */
  var sondeo = null;
  function esperarRespuesta(id) {
    estado.enviando = true;
    $input.trigger("input");
    escribiendo(true);
    alFinal();
    sondeo = setTimeout(function revisar() {
      api.get("/api/asistente/en-curso").done(function (r) {
        if (r.conversacionId === id) { sondeo = setTimeout(revisar, 3000); return; }
        sondeo = null;
        estado.enviando = false;
        escribiendo(false);
        cargarLista().always(function () { if (estado.id === id) abrir(id); });
        $(document).trigger("sbai:asistente-respondio");
      }).fail(function () { sondeo = setTimeout(revisar, 6000); });
    }, 3000);
  }
  function dejarDeEsperar() {
    if (sondeo) { clearTimeout(sondeo); sondeo = null; estado.enviando = false; }
  }

  /*
   * La IA pone el título de una conversación nueva en paralelo a la respuesta.
   * Si todavía no llegó, se revisa el historial un par de veces para mostrarlo.
   */
  function esperarTitulo(id, provisional) {
    [4000, 10000].forEach(function (ms) {
      setTimeout(function () {
        var c = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
        if (c && c.titulo !== provisional) return;   // ya cambió
        cargarLista().done(function () {
          var nuevo = estado.conversaciones.filter(function (x) { return x.id === id; })[0];
          if (nuevo && estado.id === id) $titulo.text(nuevo.titulo);
        });
      }, ms);
    });
  }

  function abrir(id, alTerminar) {
    if (estado.id !== id) dejarDeEsperar();
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
      if (alTerminar) alTerminar();
    }).fail(function (xhr) {
      $msgs.find(".as-cargando").remove();
      SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" });
      nueva();
    });
    $app.removeClass("hist-abierto");
    $("#as-ver-hist").attr("aria-expanded", "false");
  }

  function nueva() {
    dejarDeEsperar();
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
    var archivos = estado.archivos.slice();
    if ((!texto && !archivos.length) || estado.enviando) return;
    estado.enviando = true;
    mostrarVacio(false);
    $empty.hide();
    var ahora = new Date();
    var locales = archivos.map(function (f) { return f.type.indexOf("image/") === 0 ? URL.createObjectURL(f) : null; });
    var $mio = $(burbuja({ deUsuario: true, texto: texto, hora: ("0" + ahora.getHours()).slice(-2) + ":" + ("0" + ahora.getMinutes()).slice(-2) }));
    if (archivos.length) $mio.prepend(adjuntosHtml(archivos.map(function (f) {
      return { nombre: f.name, tamano: tamano(f.size), imagen: f.type.indexOf("image/") === 0 };
    }), locales));
    $msgs.append($mio);
    $input.val("").trigger("input");
    limpiarAdjuntos();
    escribiendo(true);
    alFinal();

    var peticion;
    if (archivos.length) {
      var fd = new FormData();
      if (estado.id != null) fd.append("conversacionId", estado.id);
      fd.append("mensaje", texto);
      archivos.forEach(function (f) { fd.append("archivos", f, f.name); });
      peticion = $.ajax({ url: "/api/asistente/mensaje", type: "POST", data: fd, processData: false, contentType: false, dataType: "json" });
    } else {
      peticion = api.post("/api/asistente/mensaje", { conversacionId: estado.id, mensaje: texto });
    }
    peticion
      .done(function (r) {
        escribiendo(false);
        // Las miniaturas locales pasan a apuntar al archivo ya guardado
        if (r.adjuntos && r.adjuntos.length) $mio.find(".as-msg-files").replaceWith(adjuntosHtml(r.adjuntos));
        locales.forEach(function (u) { if (u) URL.revokeObjectURL(u); });
        $msgs.append(burbuja({ deUsuario: false, texto: r.respuesta, hora: r.hora, acciones: r.acciones, error: !r.ok }));
        if (r.nueva) {
          estado.id = r.conversacionId;
          $titulo.text(r.titulo);
          url(r.conversacionId);
          esperarTitulo(r.conversacionId, r.titulo);
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
    $send.prop("disabled", (!$.trim(this.value) && !estado.archivos.length) || estado.enviando);
  });
  $msgs.on("click", "[data-as-sug]", function () { enviar($(this).text()); });
  $lista.on("click", ".as-hist-item", function () { abrir(Number($(this).data("id"))); });
  $("#as-nueva").on("click", nueva);
  $("#as-ver-hist").on("click", function () {
    var abierto = $app.toggleClass("hist-abierto").hasClass("hist-abierto");
    $(this).attr("aria-expanded", abierto ? "true" : "false");
  });
  $borrar.on("click", function () { if (estado.id != null) eliminar(estado.id); });
  $renombrar.on("click", function () { if (estado.id != null) renombrar(estado.id, $(".as-chat-title h2")); });
  $fijar.on("click", function () { if (estado.id != null) fijar(estado.id); });

  /* ---------------- Adjuntos ---------------- */
  function tamano(b) { return b < 1024 ? b + " B" : b < 1048576 ? Math.round(b / 1024) + " KB" : (b / 1048576).toFixed(1) + " MB"; }
  var TIPOS = /^(image\/(png|jpe?g|webp|gif)|application\/pdf|text\/.*|application\/json)$/;
  var EXT = /\.(txt|csv|md|json|log|xml|sql|java|js|html|css|ya?ml|properties|png|jpe?g|webp|gif|pdf)$/i;
  function agregarArchivos(lista) {
    Array.prototype.forEach.call(lista, function (f) {
      if (estado.archivos.length >= MAX_ADJ) { SBAI.toast.show("Puedes adjuntar hasta " + MAX_ADJ + " archivos por mensaje.", { type: "error" }); return; }
      if (!TIPOS.test(f.type || "") && !EXT.test(f.name)) { SBAI.toast.show(esc(f.name) + ": tipo no admitido (imágenes, PDF o texto).", { type: "error" }); return; }
      var total = estado.archivos.reduce(function (a, x) { return a + x.size; }, 0) + f.size;
      if (total > MAX_BYTES) { SBAI.toast.show("Los adjuntos de un mensaje pueden pesar hasta 5 MB en total.", { type: "error" }); return; }
      estado.archivos.push(f);
    });
    pintarAdjuntos();
  }
  function pintarAdjuntos() {
    $adj.find("img").each(function () { URL.revokeObjectURL(this.src); });
    $adj.prop("hidden", !estado.archivos.length).html(estado.archivos.map(function (f, i) {
      var img = f.type.indexOf("image/") === 0;
      return '<li class="' + (img ? "is-img" : "") + '">' + (img ? '<img src="' + URL.createObjectURL(f) + '" alt="">' : icon("icon-file-text")) +
        '<span><b>' + esc(f.name) + "</b><small>" + tamano(f.size) + "</small></span>" +
        '<button type="button" data-quitar="' + i + '" aria-label="Quitar ' + esc(f.name) + '">' + icon("icon-x", "icon-sm") + "</button></li>";
    }).join(""));
    $input.trigger("input");
  }
  function limpiarAdjuntos() { estado.archivos = []; pintarAdjuntos(); $file.val(""); }
  $("#as-clip").on("click", function () { $file.trigger("click"); });
  $file.on("change", function () { agregarArchivos(this.files); $file.val(""); });
  $adj.on("click", "[data-quitar]", function () { estado.archivos.splice(Number($(this).data("quitar")), 1); pintarAdjuntos(); });
  // Pegar una imagen (Ctrl+V) en la caja de texto
  $input.on("paste", function (e) {
    var items = (e.originalEvent.clipboardData || {}).files;
    if (items && items.length) { e.preventDefault(); agregarArchivos(items); }
  });
  // Arrastrar y soltar sobre la conversación
  var $chat = $(".as-chat"), $drop = $("#as-drop"), arrastres = 0;
  $chat.on("dragenter", function (e) { if (hayArchivos(e)) { arrastres++; $drop.prop("hidden", false); } })
    .on("dragleave", function () { if (--arrastres <= 0) { arrastres = 0; $drop.prop("hidden", true); } })
    .on("dragover", function (e) { if (hayArchivos(e)) e.preventDefault(); })
    .on("drop", function (e) {
      if (!hayArchivos(e)) return;
      e.preventDefault(); arrastres = 0; $drop.prop("hidden", true);
      agregarArchivos(e.originalEvent.dataTransfer.files);
    });
  function hayArchivos(e) { var dt = e.originalEvent && e.originalEvent.dataTransfer; return dt && Array.prototype.indexOf.call(dt.types || [], "Files") >= 0; }

  function eliminar(id) {
    SBAI.modal.confirm({
      title: "¿Eliminar esta conversación?",
      desc: "Se borran todos sus mensajes y el asistente deja de recordarla.",
      confirmLabel: "Eliminar",
      variant: "destructive",
      onConfirm: function () {
        api.del("/api/asistente/conversaciones/" + id).done(function () {
          SBAI.toast.show("Conversación eliminada.", { type: "success" });
          if (id === estado.id) nueva();
          cargarLista();
        }).fail(function (xhr) { SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" }); });
      }
    });
  }

  var inicial = Number($app.data("inicial")) || null;
  cargarLista().always(function () {
    api.get("/api/asistente/en-curso").always(function (r) {
      var pendiente = r && r.conversacionId;
      if (pendiente && (!inicial || inicial === pendiente)) {
        abrir(pendiente, function () { esperarRespuesta(pendiente); });
      } else if (inicial) {
        abrir(inicial);
      } else {
        mostrarVacio(true);
      }
    });
  });
})(window.jQuery);

/* Confirmaciones independientes del texto del modelo. No ejecutar desde Markdown. */
(function ($) {
  "use strict";
  var panel = $('<section aria-live="polite" class="as-confirmaciones"></section>');
  $("#as-form").before(panel);
  function cargar() {
    $.getJSON('/api/asistente/confirmaciones').done(function (filas) {
      panel.empty();
      filas.forEach(function (p) {
        var caja=$('<div class="as-bubble"></div>').appendTo(panel);
        $('<strong></strong>').text('Revisa antes de confirmar: '+p.herramienta).appendTo(caja);
        var argumentos;try{argumentos=JSON.stringify(JSON.parse(p.argumentos),null,2);}catch(e){argumentos='Datos inválidos';return;}
        $('<pre style="white-space:pre-wrap;overflow-wrap:anywhere"></pre>').text(argumentos).appendTo(caja);
        $('<p></p>').text('Esta acción utiliza exactamente estos datos. Caduca en cinco minutos.').appendTo(caja);
        ['confirmar','cancelar'].forEach(function (accion) {
          $('<button type="button" class="as-accion"></button>').text(accion==='confirmar'?'Confirmar cambio':'Cancelar').appendTo(caja).on('click',function(){
            caja.find('button').prop('disabled',true);
            $.ajax({url:'/api/asistente/confirmaciones/'+encodeURIComponent(p.id)+'/'+accion,type:'POST'})
              .done(function(){caja.empty().append($('<p></p>').text(accion==='confirmar'?'Cambio confirmado y registrado.':'Propuesta cancelada.'));})
              .fail(function(xhr){caja.append($('<p></p>').text((xhr.responseJSON||{}).error||'No se pudo completar. Actualiza antes de reintentar.'));});
          });
        });
      });
    });
  }
  $(document).ajaxComplete(function(_e,_xhr,settings){if(settings.url==='/api/asistente/mensaje')cargar();});
  $(document).on('sbai:asistente-respondio', cargar);
  $(function(){cargar(); $('#as-file').prop('disabled',true);});
})(jQuery);
