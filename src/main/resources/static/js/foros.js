/*
 * foros.js — lista de hilos (project-manager/foros.html y colaborador/foros.html).
 * Requiere foro-utils.js y window.FORO = {baseUrl, hilos, topicos, proyectosPublicar,
 * maxArchivos, maxEnlaces}, que la plantilla arma con th:inline.
 *
 * Filtros combinables: vista (todos/sin responder/resueltos/míos), proyecto,
 * tópico y búsqueda inteligente. El estado queda en la URL (?q=&topico=...)
 * para poder compartir o volver a una búsqueda.
 */
(function () {
  "use strict";

  document.addEventListener("DOMContentLoaded", function () {
    var F = window.FORO || {};
    var U = window.SBForo;
    var submitForm = window.SBAI && window.SBAI.submitForm;
    var lista = document.getElementById("thread-list");
    if (!U) return;

    var hilos = F.hilos || [];
    var topicos = F.topicos || [];
    var topicoPorCodigo = {};
    topicos.forEach(function (t) { topicoPorCodigo[t.codigo] = t; });

    var filas = {};
    if (lista) lista.querySelectorAll(".fr-thread").forEach(function (el) { filas[el.getAttribute("data-id")] = el; });

    var docs = hilos.map(function (h) {
      return {
        id: String(h.id), titulo: h.titulo, etiquetas: h.etiquetas || [], topicoCodigo: h.topicoCodigo,
        topicoNombre: h.topicoNombre, texto: h.textoBusqueda || h.extracto || "", proyecto: h.proyectoNombre,
        autor: h.autorNombre, h: h
      };
    });
    var buscador = U.crearBuscador(docs, topicos);

    var VISTAS = { todos: "Todos los hilos", sin_responder: "Sin responder", resueltos: "Resueltos", mios: "Mis hilos" };
    var estado = { vista: "todos", proyecto: "todos", topico: "todos", q: "", orden: "actividad", ordenManual: false };

    // ── Estado inicial desde la URL ──
    var params = new URLSearchParams(location.search);
    if (params.get("q")) estado.q = params.get("q");
    if (params.get("topico") && topicoPorCodigo[params.get("topico")]) estado.topico = params.get("topico");
    if (params.get("proyecto")) estado.proyecto = params.get("proyecto");
    if (params.get("vista") && VISTAS[params.get("vista")]) estado.vista = params.get("vista");

    var inputQ = document.getElementById("q");
    var selOrden = document.getElementById("orden");
    if (inputQ) inputQ.value = estado.q;

    function pasaFiltros(h, ignorarTopico) {
      if (estado.vista === "sin_responder" && h.numRespuestas !== 0) return false;
      if (estado.vista === "resueltos" && !h.tieneAceptada) return false;
      if (estado.vista === "mios" && !h.esMio) return false;
      if (estado.proyecto !== "todos" && String(h.proyectoId) !== estado.proyecto) return false;
      if (!ignorarTopico && estado.topico !== "todos" && h.topicoCodigo !== estado.topico) return false;
      return true;
    }

    function comparar(a, b) {
      var ha = a.h, hb = b.h;
      if (ha.esFijado !== hb.esFijado) return ha.esFijado ? -1 : 1;
      switch (estado.orden) {
        case "recientes": return hb.fechaIso.localeCompare(ha.fechaIso);
        case "utiles": return (hb.numDestacados - ha.numDestacados) || hb.ultimaActividadIso.localeCompare(ha.ultimaActividadIso);
        case "respondidos": return (hb.numRespuestas - ha.numRespuestas) || hb.ultimaActividadIso.localeCompare(ha.ultimaActividadIso);
        case "relevancia": return (b.puntaje - a.puntaje) || hb.ultimaActividadIso.localeCompare(ha.ultimaActividadIso);
        default: return hb.ultimaActividadIso.localeCompare(ha.ultimaActividadIso);
      }
    }

    function aplicar() {
      var res = estado.q.trim() ? buscador.buscar(estado.q) : null;
      var puntaje = {}, terminos = {};
      if (res) res.resultados.forEach(function (r) { puntaje[r.doc.id] = r.puntaje; terminos[r.doc.id] = r.terminos; });

      // Conteo por tópico con los demás filtros aplicados (sin el de tópico).
      var conteo = {};
      var total = 0;
      docs.forEach(function (d) {
        if (!pasaFiltros(d.h, true)) return;
        if (res && puntaje[d.id] === undefined) return;
        conteo[d.topicoCodigo] = (conteo[d.topicoCodigo] || 0) + 1;
        total++;
      });
      document.querySelectorAll("#topic-chips [data-topico]").forEach(function (chip) {
        var c = chip.getAttribute("data-topico");
        var n = c === "todos" ? total : (conteo[c] || 0);
        chip.querySelector(".fr-chip-count").textContent = n;
        chip.classList.toggle("is-empty", c !== "todos" && n === 0 && c !== estado.topico);
        chip.classList.toggle("is-active", c === estado.topico);
        chip.setAttribute("aria-pressed", c === estado.topico ? "true" : "false");
      });

      var visibles = docs.filter(function (d) {
        return pasaFiltros(d.h, false) && (!res || puntaje[d.id] !== undefined);
      }).map(function (d) { return { id: d.id, h: d.h, doc: d, puntaje: puntaje[d.id] || 0 }; });

      if (res && !estado.ordenManual) estado.orden = "relevancia";
      if (!res && estado.orden === "relevancia") estado.orden = "actividad";
      if (selOrden) {
        selOrden.querySelector('option[value="relevancia"]').hidden = !res;
        selOrden.value = estado.orden;
      }
      visibles.sort(comparar);

      Object.keys(filas).forEach(function (id) { filas[id].hidden = true; });
      visibles.forEach(function (v) {
        var fila = filas[v.id];
        if (!fila) return;
        fila.hidden = false;
        lista.appendChild(fila);
        var t = terminos[v.id];
        var tituloEl = fila.querySelector("[data-titulo]");
        var extractoEl = fila.querySelector("[data-extracto]");
        if (t && t.length) {
          tituloEl.innerHTML = U.resaltar(v.h.titulo, t);
          var frag = U.fragmento(v.doc.texto, t, 220);
          extractoEl.innerHTML = U.resaltar(frag || v.h.extracto, t);
        } else {
          tituloEl.textContent = v.h.titulo;
          extractoEl.textContent = v.h.extracto;
        }
      });

      pintarResumen(visibles.length, res);
      pintarVacio(visibles.length, res);
      marcarNav();
      guardarUrl();
    }

    function pintarResumen(n, res) {
      var cont = document.getElementById("result-count");
      if (!cont) return;
      var html = "<strong>" + n + "</strong> " + (n === 1 ? "hilo" : "hilos");
      if (res) html += " para “" + U.escapar(estado.q.trim()) + "”";
      var pills = [];
      if (estado.vista !== "todos") pills.push(['vista', VISTAS[estado.vista]]);
      if (estado.proyecto !== "todos") {
        var btn = document.querySelector('#proyecto-nav [data-proyecto="' + estado.proyecto + '"] .fr-nav-label');
        pills.push(['proyecto', btn ? btn.textContent : "Proyecto"]);
      }
      if (estado.topico !== "todos" && topicoPorCodigo[estado.topico]) pills.push(['topico', topicoPorCodigo[estado.topico].nombre]);
      if (pills.length) {
        html += ' <span class="fr-active-filters">' + pills.map(function (p) {
          return '<button type="button" class="fr-filter-pill" data-quitar="' + p[0] + '" aria-label="Quitar filtro ' + U.escapar(p[1]) + '">' +
            U.escapar(p[1]) + '<svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button>';
        }).join("") + "</span>";
      }
      cont.innerHTML = html;

      var exp = document.getElementById("expansion");
      if (exp) {
        if (res && res.expansion.length) {
          exp.hidden = false;
          exp.innerHTML = "También buscamos temas relacionados: <strong>" + res.expansion.map(U.escapar).join("</strong>, <strong>") + "</strong>";
        } else {
          exp.hidden = true;
        }
      }
      var clear = document.getElementById("q-clear");
      if (clear) clear.hidden = !estado.q;
    }

    function pintarVacio(n, res) {
      var vacio = document.getElementById("dynamic-empty");
      if (!vacio || !hilos.length) return;
      vacio.hidden = n !== 0;
      if (n !== 0) return;
      var titulo = vacio.querySelector("[data-vacio-titulo]");
      var desc = vacio.querySelector("[data-vacio-desc]");
      var cta = vacio.querySelector("[data-vacio-cta]");
      if (res) {
        titulo.textContent = "No encontramos hilos para “" + estado.q.trim() + "”";
        desc.textContent = "Prueba con otra palabra o quita algún filtro. Si nadie lo preguntó todavía, ábrelo tú.";
        if (cta) cta.hidden = false;
      } else {
        titulo.textContent = "No hay hilos con estos filtros";
        desc.textContent = "Quita algún filtro de la barra lateral o de los tópicos.";
        if (cta) cta.hidden = true;
      }
    }

    function marcarNav() {
      document.querySelectorAll("#vista-nav [data-vista]").forEach(function (b) {
        var on = b.getAttribute("data-vista") === estado.vista;
        b.classList.toggle("is-active", on);
        b.setAttribute("aria-current", on ? "true" : "false");
      });
      document.querySelectorAll("#proyecto-nav [data-proyecto]").forEach(function (b) {
        var on = b.getAttribute("data-proyecto") === estado.proyecto;
        b.classList.toggle("is-active", on);
        b.setAttribute("aria-current", on ? "true" : "false");
      });
    }

    function guardarUrl() {
      var p = new URLSearchParams();
      if (estado.q.trim()) p.set("q", estado.q.trim());
      if (estado.topico !== "todos") p.set("topico", estado.topico);
      if (estado.proyecto !== "todos") p.set("proyecto", estado.proyecto);
      if (estado.vista !== "todos") p.set("vista", estado.vista);
      var qs = p.toString();
      history.replaceState(null, "", location.pathname + (qs ? "?" + qs : ""));
    }

    // ── Eventos ──
    var temporizador = null;
    if (inputQ) {
      inputQ.addEventListener("input", function () {
        clearTimeout(temporizador);
        temporizador = setTimeout(function () { estado.q = inputQ.value; estado.ordenManual = false; aplicar(); }, 120);
      });
      inputQ.addEventListener("keydown", function (e) { if (e.key === "Escape") { inputQ.value = ""; estado.q = ""; aplicar(); } });
    }
    var qClear = document.getElementById("q-clear");
    if (qClear) qClear.addEventListener("click", function () { inputQ.value = ""; estado.q = ""; aplicar(); inputQ.focus(); });
    document.addEventListener("keydown", function (e) {
      if (e.key === "/" && inputQ && document.activeElement !== inputQ && !/input|textarea|select/i.test(document.activeElement.tagName)) {
        e.preventDefault();
        inputQ.focus();
      }
    });

    document.querySelectorAll("[data-hint]").forEach(function (b) {
      b.addEventListener("click", function () { inputQ.value = b.getAttribute("data-hint"); estado.q = inputQ.value; estado.ordenManual = false; aplicar(); });
    });
    document.querySelectorAll("#vista-nav [data-vista]").forEach(function (b) {
      b.addEventListener("click", function () { estado.vista = b.getAttribute("data-vista"); aplicar(); });
    });
    document.querySelectorAll("#proyecto-nav [data-proyecto]").forEach(function (b) {
      b.addEventListener("click", function () { estado.proyecto = b.getAttribute("data-proyecto"); aplicar(); });
    });
    var buscarProyecto = document.getElementById("proyecto-search");
    if (buscarProyecto) {
      buscarProyecto.addEventListener("input", function () {
        var q = U.normalizar(buscarProyecto.value);
        document.querySelectorAll("#proyecto-nav [data-proyecto]").forEach(function (b) {
          if (b.getAttribute("data-proyecto") === "todos") return;
          b.hidden = q && U.normalizar(b.textContent).indexOf(q) < 0;
        });
      });
    }
    document.querySelectorAll("#topic-chips [data-topico]").forEach(function (b) {
      b.addEventListener("click", function () {
        var c = b.getAttribute("data-topico");
        estado.topico = estado.topico === c && c !== "todos" ? "todos" : c;
        aplicar();
      });
    });
    if (selOrden) selOrden.addEventListener("change", function () { estado.orden = selOrden.value; estado.ordenManual = true; aplicar(); });

    document.addEventListener("click", function (e) {
      var quitar = e.target.closest("[data-quitar]");
      if (quitar) {
        estado[quitar.getAttribute("data-quitar")] = "todos";
        aplicar();
        return;
      }
      var topicoBadge = e.target.closest("[data-filtro-topico]");
      if (topicoBadge) {
        e.preventDefault();
        estado.topico = topicoBadge.getAttribute("data-filtro-topico");
        aplicar();
        window.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }
      var etiqueta = e.target.closest("[data-filtro-etiqueta]");
      if (etiqueta) {
        e.preventDefault();
        inputQ.value = etiqueta.getAttribute("data-filtro-etiqueta");
        estado.q = inputQ.value;
        estado.ordenManual = false;
        aplicar();
        window.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }
      var fijar = e.target.closest("[data-fijar]");
      if (fijar && submitForm) {
        e.preventDefault();
        submitForm(F.baseUrl + "/foros/" + fijar.getAttribute("data-fijar") + "/fijar", { valor: fijar.getAttribute("data-valor") });
      }
    });

    // Filas: clic en cualquier parte abre el hilo (el título es el enlace real).
    if (lista) {
      lista.addEventListener("click", function (e) {
        if (e.target.closest("a, button")) return;
        var fila = e.target.closest(".fr-thread");
        if (fila) location.href = fila.querySelector(".fr-thread-link").href;
      });
    }

    U.pintarFechas(document);
    aplicar();

    // ───────── Nuevo hilo ─────────
    function sugerirTopico(titulo, mensaje) {
      var t = " " + U.normalizar(titulo) + " ", c = " " + U.normalizar(mensaje) + " ";
      var mejor = null, max = 0;
      topicos.forEach(function (tp) {
        if (tp.codigo === "general" || !tp.palabrasClave) return;
        var puntos = 0;
        tp.palabrasClave.split(",").forEach(function (p) {
          if (!p) return;
          var f = " " + p + " ";
          puntos += 3 * (t.split(f).length - 1) + (c.split(f).length - 1);
        });
        if (puntos > max) { max = puntos; mejor = tp; }
      });
      return mejor || topicoPorCodigo.general || null;
    }

    function abrirNuevoHilo(tituloInicial) {
      var proyectos = F.proyectosPublicar || [];
      if (!proyectos.length) return;
      var preferido = estado.proyecto !== "todos" ? estado.proyecto : null;
      var opts = proyectos.map(function (p) {
        return '<option value="' + p.id + '"' + (String(p.id) === preferido ? " selected" : "") + ">" + U.escapar(p.nombre) + "</option>";
      }).join("");
      var optsTopico = '<option value="auto">Automático (lo elegimos por ti)</option>' + topicos.map(function (t) {
        return '<option value="' + U.escapar(t.codigo) + '"' + (t.codigo === estado.topico ? " selected" : "") + ">" + U.escapar(t.nombre) + "</option>";
      }).join("");

      var html = '<div class="modal modal-wide fr-modal" role="dialog" aria-modal="true" aria-labelledby="nh-titulo-h">' +
        '<div class="modal-header"><div><h2 id="nh-titulo-h">Nuevo hilo</h2><p>Pregunta, comparte un hallazgo o pide ayuda a tu equipo.</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body">' +
          '<div class="field-row">' +
            '<div class="field"><label class="field-label" for="nh-proyecto">Proyecto</label><select class="select" id="nh-proyecto">' + opts + "</select></div>" +
            '<div class="field"><label class="field-label" for="nh-topico">Tópico</label><select class="select" id="nh-topico">' + optsTopico + "</select>" +
              '<span class="fr-topic-suggest" id="nh-sugerencia"><svg class="icon"><use href="/img/icons.svg#icon-sparkles"></use></svg><span>Escribe y te sugerimos uno</span></span></div>' +
          "</div>" +
          '<div class="field"><label class="field-label" for="nh-titulo">Título</label><input class="input" id="nh-titulo" maxlength="200" placeholder="Ej.: La app no conecta con MySQL en RDS desde la EC2"></div>' +
          '<div class="fr-similar" id="nh-similares" hidden><div class="fr-similar-title"><svg class="icon"><use href="/img/icons.svg#icon-search"></use></svg>¿Ya lo preguntaron? Hilos parecidos</div><div data-lista></div></div>' +
          '<div class="field"><label class="field-label" for="nh-mensaje">Mensaje</label>' +
            '<textarea class="input fr-textarea" id="nh-mensaje" rows="6" maxlength="10000" placeholder="Cuenta qué intentabas hacer, qué pasó (el error exacto, si hay uno) y qué ya probaste."></textarea></div>' +
          '<div class="field"><label class="field-label" for="nh-etiquetas">Etiquetas <span class="text-tertiary">(opcional)</span></label>' +
            '<input class="input" id="nh-etiquetas" placeholder="Se detectan solas, p. ej. MySQL, Docker. Sepáralas con comas."></div>' +
          '<div class="field" style="margin-bottom:0"><span class="field-label">Adjuntos <span class="text-tertiary">(opcional)</span></span>' +
            '<div class="fr-attach-bar">' +
              '<button type="button" class="fr-attach-btn" id="nh-btn-archivo">' + U.iconos.clip + "Adjuntar archivo</button>" +
              '<button type="button" class="fr-attach-btn" id="nh-btn-enlace">' + U.iconos.enlace + "Agregar enlace</button>" +
              '<span class="fr-attach-hint">Hasta ' + F.maxArchivos + " archivos de 5 MB: imágenes, PDF, Word, Excel, PowerPoint, TXT, CSV o ZIP.</span>" +
            "</div>" +
            '<div class="fr-link-row" id="nh-fila-enlace" hidden><input class="input" id="nh-enlace" type="url" placeholder="https://…"><button type="button" class="btn btn-secondary btn-sm" id="nh-agregar-enlace">Agregar</button></div>' +
            '<div class="fr-file-chips" id="nh-chips"></div>' +
            '<input type="file" name="archivos" id="nh-archivos" multiple hidden>' +
            '<div id="nh-enlaces" hidden></div>' +
          "</div>" +
        "</div>" +
        '<div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" id="nh-publicar">Publicar hilo</button></div></div>';

      var bd = SBAI.modal.open(html);
      var $ = function (s) { return bd.querySelector(s); };
      var adj = U.crearAdjuntos({
        input: $("#nh-archivos"), chips: $("#nh-chips"), botonArchivo: $("#nh-btn-archivo"), botonEnlace: $("#nh-btn-enlace"),
        filaEnlace: $("#nh-fila-enlace"), inputEnlace: $("#nh-enlace"), agregarEnlace: $("#nh-agregar-enlace"),
        contenedorEnlaces: $("#nh-enlaces"), maxArchivos: F.maxArchivos || 3, maxEnlaces: F.maxEnlaces || 3
      });

      var titulo = $("#nh-titulo"), mensaje = $("#nh-mensaje"), selTopico = $("#nh-topico");
      if (tituloInicial) titulo.value = tituloInicial;

      var temp = null;
      function actualizarAyudas() {
        clearTimeout(temp);
        temp = setTimeout(function () {
          var sug = sugerirTopico(titulo.value, mensaje.value);
          var texto = $("#nh-sugerencia span");
          if (selTopico.value === "auto") {
            texto.textContent = (titulo.value.trim() || mensaje.value.trim()) && sug
              ? "Sugerido: " + sug.nombre : "Escribe y te sugerimos uno";
          } else {
            texto.textContent = "Elegido por ti";
          }
          var cont = $("#nh-similares");
          if (titulo.value.trim().length >= 6) {
            var res = buscador.buscar(titulo.value).resultados.slice(0, 3);
            cont.hidden = !res.length;
            cont.querySelector("[data-lista]").innerHTML = res.map(function (r) {
              return '<a href="foro-hilo.html?id=' + encodeURIComponent(r.doc.id) + '" target="_blank" rel="noopener"><span>' +
                U.escapar(r.doc.titulo) + "</span><span>" + (r.doc.h.tieneAceptada ? "Resuelto" : r.doc.h.numRespuestas + " resp.") + "</span></a>";
            }).join("");
          } else {
            cont.hidden = true;
          }
        }, 160);
      }
      titulo.addEventListener("input", actualizarAyudas);
      mensaje.addEventListener("input", actualizarAyudas);
      selTopico.addEventListener("change", actualizarAyudas);
      actualizarAyudas();
      setTimeout(function () { (tituloInicial ? mensaje : titulo).focus(); }, 50);

      $("#nh-publicar").addEventListener("click", function () {
        var t = titulo.value.trim(), m = mensaje.value.trim();
        if (!t) { SBAI.toast.show("Escribe un título para tu hilo.", { type: "error" }); titulo.focus(); return; }
        if (!m) { SBAI.toast.show("Escribe un mensaje para tu hilo.", { type: "error" }); mensaje.focus(); return; }
        if (adj.enlacePendiente()) { SBAI.toast.show("Tienes un enlace sin agregar: pulsa \"Agregar\" o bórralo.", { type: "error" }); return; }
        this.disabled = true;
        this.textContent = "Publicando…";
        var form = document.createElement("form");
        form.method = "post";
        form.enctype = "multipart/form-data";
        form.action = F.baseUrl + "/foros/nuevo-hilo";
        form.hidden = true;
        [["proyectoId", $("#nh-proyecto").value], ["titulo", t], ["contenido", m],
         ["topico", selTopico.value], ["etiquetas", $("#nh-etiquetas").value]].forEach(function (par) {
          var i = document.createElement("input");
          i.type = "hidden"; i.name = par[0]; i.value = par[1];
          form.appendChild(i);
        });
        form.appendChild($("#nh-archivos"));
        form.appendChild($("#nh-enlaces"));
        document.body.appendChild(form);
        form.submit();
      });
    }

    var nuevoBtn = document.getElementById("new-thread-btn");
    if (nuevoBtn) nuevoBtn.addEventListener("click", function () { abrirNuevoHilo(""); });
    document.querySelectorAll("[data-vacio-cta]").forEach(function (b) {
      b.addEventListener("click", function () { abrirNuevoHilo(estado.q.trim()); });
    });
  });
})();
