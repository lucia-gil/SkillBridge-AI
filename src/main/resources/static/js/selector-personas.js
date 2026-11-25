/**
 * selector-personas.js — Selector de personas para asignar (Resource Manager)
 * o proponer (Project Manager) colaboradores en un proyecto. Modelo v7, fase 4.
 *
 *  SBAI.selectorPersonas.abrir({
 *    proyectoId: 12, proyecto: "Migración a Kubernetes",
 *    endpoint:  "/resource-manager/proyectos/12/candidatos",   // JSON (GET)
 *    accion:    "/resource-manager/proyectos/12/asignar-varios", // POST final
 *    modo:      "asignar" | "proponer",
 *    extra:     { volver: "asignaciones" }                       // campos extra del POST
 *  });
 *
 * Modo "pm" (fase 5): elegir UNA persona como Project Manager (al crear un
 * proyecto o en "Cambiar PM"). No envía nada: llama a onElegir(candidato).
 *  SBAI.selectorPersonas.abrir({ modo: "pm", endpoint: ".../candidatos-pm",
 *    proyecto: "Nombre", onElegir: function (c) { ... } });
 *
 * La búsqueda, los filtros, el orden y la paginación los resuelve el
 * servidor (SelectorPersonasService): funciona igual con 20 que con miles de
 * personas. La selección se conserva al cambiar de página o de filtro.
 * El backend vuelve a validar todo al enviar.
 */
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};

  function esc(t) {
    var d = document.createElement("div");
    d.textContent = t == null ? "" : String(t);
    return d.innerHTML;
  }
  function icon(nombre, cls) {
    return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="/img/icons.svg#' + nombre + '"></use></svg>';
  }
  function hoyIso() {
    var d = new Date();
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  }
  function toast(msg, tipo) {
    if (window.SBAI.toast) window.SBAI.toast.show(msg, { type: tipo || "error" });
  }
  /** Semáforo de carga: verde < 70, ámbar 70–90, rojo > 90. */
  function tono(carga) { return carga > 90 ? "red" : carga >= 70 ? "amber" : "green"; }

  function abrir(opts) {
    var proponer = opts.modo === "proponer";
    var modoPm = opts.modo === "pm";
    var verbo = modoPm ? "Elegir" : proponer ? "Proponer" : "Asignar";
    var estado = {
      q: "", habilidades: [], cargo: "", disponibleMin: "", experienciaMin: "",
      soloConCapacidad: false, dedicacion: modoPm ? (opts.dedicacion || 20) : 50, orden: modoPm ? "disponibilidad" : "match", pagina: 1
    };
    var seleccion = {};          // perfilId -> candidato
    var ultima = null;           // última respuesta del servidor
    var primeraCarga = true;
    var controlador = null;
    var temporizador = null;

    var html =
      '<div class="modal modal-picker' + (modoPm ? " is-pm" : "") + '" role="dialog" aria-modal="true" aria-labelledby="sp-titulo">' +
      '<div class="modal-header"><div>' +
      '<h2 id="sp-titulo">' + (modoPm ? "Elegir Project Manager" : verbo + " colaboradores") + "</h2>" +
      '<p class="sp-sub"><span>' + esc(opts.proyecto || "") + '</span><span class="sp-vacantes" data-sp-vacantes></span></p>' +
      "</div>" +
      '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>" +

      '<div class="sp-body">' +
      // ---- Filtros
      '<aside class="sp-filtros" aria-label="Filtros">' +
      '<div class="sp-bloque"><span class="sp-label">Habilidades que pide el proyecto <span class="sp-hint">(tócalas para exigirlas)</span></span><div class="sp-chips" data-sp-req></div></div>' +
      '<div class="sp-bloque"><label class="sp-label" for="sp-otra">Filtrar por otra habilidad</label>' +
      '<select class="select" id="sp-otra"><option value="">Agregar habilidad…</option></select>' +
      '<div class="sp-chips" data-sp-extra></div></div>' +
      '<div class="sp-bloque"><label class="sp-label" for="sp-cargo">Cargo</label><select class="select" id="sp-cargo"><option value="">Todos los cargos</option></select></div>' +
      '<div class="sp-bloque"><label class="sp-label" for="sp-disp">Disponible al menos <strong data-sp-disp-val>0%</strong></label>' +
      '<input type="range" id="sp-disp" min="0" max="100" step="5" value="0"></div>' +
      '<div class="sp-bloque"><label class="sp-label" for="sp-exp">Experiencia mínima</label>' +
      '<select class="select" id="sp-exp"><option value="">Cualquiera</option><option value="1">1+ año</option><option value="3">3+ años</option><option value="5">5+ años</option><option value="8">8+ años</option></select></div>' +
      '<label class="sp-switch"><input type="checkbox" id="sp-cap"><span>Solo quienes tienen capacidad para la dedicación indicada</span></label>' +
      '<button type="button" class="btn btn-secondary btn-sm sp-limpiar" data-sp-limpiar>Limpiar filtros</button>' +
      "</aside>" +
      // ---- Resultados
      '<section class="sp-resultados">' +
      '<div class="sp-toolbar">' +
      '<button type="button" class="btn btn-secondary btn-sm sp-toggle-filtros" data-sp-toggle aria-expanded="false">' + icon("icon-filter", "icon-sm") + "Filtros</button>" +
      '<label class="sp-buscar">' + icon("icon-search") + '<input type="search" id="sp-q" placeholder="Buscar por nombre, correo o cargo…" autocomplete="off"></label>' +
      '<select class="select sp-orden" id="sp-orden" aria-label="Ordenar">' +
      '<option value="match">Mejor coincidencia</option><option value="disponibilidad">Más disponibles</option>' +
      '<option value="experiencia">Más experiencia</option><option value="nombre">Nombre (A–Z)</option></select>' +
      (modoPm ? '<label class="sp-switch sp-switch-inline"><input type="checkbox" id="sp-cap-pm"><span>Solo con capacidad para el 20%</span></label>' : "") +
      "</div>" +
      (modoPm ? '<p class="sp-ayuda">Solo Miembros activos (nunca Administradores ni Resource Managers). Quien ya colabora en el proyecto asciende conservando su dedicación; quien viene de fuera entra con 20%.</p>' : "") +
      '<div class="sp-meta" data-sp-meta aria-live="polite"></div>' +
      '<div class="sp-lista" data-sp-lista role="list"></div>' +
      '<div class="sp-paginacion" data-sp-pag></div>' +
      "</section></div>" +

      // ---- Bandeja de seleccionados + acción
      '<div class="modal-footer sp-footer">' +
      '<div class="sp-bandeja"><span class="sp-label" data-sp-cuenta>0 seleccionados</span><div class="sp-chips" data-sp-sel></div></div>' +
      '<div class="sp-acciones">' +
      (modoPm ? "" : '<label class="sp-campo"><span>Dedicación (%)</span><input class="input" type="number" min="1" max="100" id="sp-ded" value="50"></label>') +
      (proponer || modoPm ? "" : '<label class="sp-campo"><span>Inicio</span><input class="input" type="date" id="sp-fecha" value="' + hoyIso() + '"></label>') +
      '<button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
      '<button type="button" class="btn btn-primary" data-sp-enviar disabled>' + verbo + "</button>" +
      "</div></div></div>";

    var bd = window.SBAI.modal.open(html);
    var $ = function (sel) { return bd.querySelector(sel); };
    var lista = $("[data-sp-lista]");

    // ---------- Carga desde el servidor
    function params() {
      var u = new URLSearchParams();
      if (estado.q) u.set("q", estado.q);
      estado.habilidades.forEach(function (id) { u.append("habilidad", id); });
      if (estado.cargo) u.set("cargo", estado.cargo);
      if (estado.disponibleMin) u.set("disponibleMin", estado.disponibleMin);
      if (estado.experienciaMin) u.set("experienciaMin", estado.experienciaMin);
      if (estado.soloConCapacidad) u.set("soloConCapacidad", "true");
      u.set("dedicacion", estado.dedicacion);
      u.set("orden", estado.orden);
      u.set("pagina", estado.pagina);
      return u.toString();
    }
    function cargar() {
      if (controlador) controlador.abort();
      controlador = new AbortController();
      lista.classList.add("is-cargando");
      fetch(opts.endpoint + "?" + params(), { signal: controlador.signal, headers: { Accept: "application/json" }, credentials: "same-origin" })
        .then(function (r) {
          if (r.redirected) { window.location.href = r.url; throw new Error("sesion"); }
          return r.json().then(function (j) { if (!r.ok) throw new Error(j.error || "No se pudo cargar la lista."); return j; });
        })
        .then(function (data) { ultima = data; pintar(data); })
        .catch(function (e) {
          if (e.name === "AbortError" || e.message === "sesion") return;
          lista.innerHTML = '<div class="state-panel"><span class="state-panel-title">' + esc(e.message) + "</span></div>";
        })
        .finally(function () { lista.classList.remove("is-cargando"); });
    }
    function recargar(diferido) {
      estado.pagina = 1;
      clearTimeout(temporizador);
      if (diferido) temporizador = setTimeout(cargar, 250); else cargar();
    }

    // ---------- Pintado
    function pintarFiltrosIniciales(data) {
      // Por defecto se filtra por lo que pide el proyecto: el usuario lo puede quitar.
      var req = $("[data-sp-req]");
      req.innerHTML = data.habilidadesProyecto.length
        ? data.habilidadesProyecto.map(function (h) {
            return '<button type="button" class="sp-pill" data-hab="' + h.id + '" aria-pressed="false">' + esc(h.nombre) +
              ' <span class="sp-pill-nivel">nv. ' + h.nivel + "</span></button>";
          }).join("")
        : '<span class="sp-vacio">Este proyecto no define habilidades requeridas.</span>';
      $("#sp-otra").innerHTML = '<option value="">Agregar habilidad…</option>' +
        data.catalogoHabilidades.map(function (h) { return '<option value="' + h.id + '">' + esc(h.nombre) + "</option>"; }).join("");
      $("#sp-cargo").innerHTML = '<option value="">Todos los cargos</option>' +
        data.cargos.map(function (c) { return '<option>' + esc(c) + "</option>"; }).join("");
      if ($("#sp-ded")) $("#sp-ded").max = data.limiteCarga;
      if (modoPm) $("#sp-orden").value = estado.orden;
    }
    function nombreHabilidad(id) {
      var h = (ultima ? ultima.catalogoHabilidades : []).filter(function (x) { return String(x.id) === String(id); })[0];
      return h ? h.nombre : "Habilidad";
    }
    function pintarPills() {
      bd.querySelectorAll("[data-sp-req] .sp-pill").forEach(function (b) {
        var on = estado.habilidades.indexOf(b.dataset.hab) !== -1;
        b.classList.toggle("is-on", on);
        b.setAttribute("aria-pressed", on ? "true" : "false");
      });
      var reqIds = Array.prototype.map.call(bd.querySelectorAll("[data-sp-req] .sp-pill"), function (b) { return b.dataset.hab; });
      $("[data-sp-extra]").innerHTML = estado.habilidades.filter(function (id) { return reqIds.indexOf(id) === -1; })
        .map(function (id) {
          return '<button type="button" class="sp-pill is-on" data-quitar-hab="' + id + '">' + esc(nombreHabilidad(id)) + " " + icon("icon-x", "icon-xs") + "</button>";
        }).join("");
    }
    function pintar(data) {
      if (primeraCarga) { pintarFiltrosIniciales(data); primeraCarga = false; }
      pintarPills();
      var vac = data.vacantes;
      $("[data-sp-vacantes]").textContent = modoPm ? "Project Manager" : (vac === 1 ? "1 vacante" : vac + " vacantes");
      $("[data-sp-meta]").textContent = data.total === 1 ? "1 persona" : data.total + " personas";

      if (!data.items.length) {
        lista.innerHTML = '<div class="state-panel"><span class="state-panel-icon">' + icon("icon-search") +
          '</span><span class="state-panel-title">Nadie coincide con estos filtros</span>' +
          '<span class="state-panel-desc">Quita alguna habilidad o baja la disponibilidad mínima.</span></div>';
      } else {
        lista.innerHTML = data.items.map(function (c) {
          var marcado = !!seleccion[c.perfilId];
          var bloqueado = !c.seleccionable || (opts.modo === "proponer" && c.propuestaPendiente);
          var motivo = c.propuestaPendiente ? "Ya tiene una propuesta pendiente en este proyecto" : c.motivo;
          var chips = c.habilidades.map(function (h) {
            return '<span class="sp-skill' + (h.requerida ? (h.cumple ? " is-match" : " is-short") : "") + '" title="Nivel ' + h.nivel + '">' +
              esc(h.nombre) + ' <b>' + h.nivel + "</b></span>";
          }).join("");
          var proyectada = c.enEquipo ? c.cargaActual : c.cargaActual + estado.dedicacion;
          return '<label class="sp-card' + (bloqueado ? " is-blocked" : "") + (marcado ? " is-selected" : "") + '" role="listitem">' +
            '<input type="checkbox" class="sp-check" value="' + c.perfilId + '"' + (marcado ? " checked" : "") + (bloqueado && !marcado ? " disabled" : "") + ">" +
            '<span class="avatar avatar-sm">' + esc(c.iniciales) + "</span>" +
            '<span class="sp-card-main">' +
            '<span class="sp-card-top"><strong>' + esc(c.nombre) + "</strong>" +
            (c.requeridas ? '<span class="sp-match" title="Habilidades pedidas que cumple">' + c.coincidencias + "/" + c.requeridas + "</span>" : "") +
            "</span>" +
            '<span class="sp-card-sub">' + esc(c.cargo) + " · " + c.experienciaAnios + " año" + (c.experienciaAnios === 1 ? "" : "s") +
            " · " + c.proyectosVigentes + " proyecto" + (c.proyectosVigentes === 1 ? "" : "s") + "</span>" +
            (chips ? '<span class="sp-skills">' + chips + "</span>" : "") +
            (bloqueado && motivo ? '<span class="sp-motivo">' + icon("icon-alert-circle", "icon-xs") + esc(motivo) + "</span>" : "") +
            (c.nota ? '<span class="sp-nota">' + icon("icon-info", "icon-xs") + esc(c.nota) + "</span>" : "") +
            "</span>" +
            '<span class="sp-carga tone-' + tono(c.cargaActual) + '" title="Carga actual ' + c.cargaActual + "%; con esta asignación " + proyectada + '%">' +
            '<span class="sp-carga-num">' + c.cargaActual + '%</span><span class="sp-carga-bar"><i style="width:' + Math.min(100, c.cargaActual) + '%"></i>' +
            '<i class="sp-carga-extra" style="left:' + Math.min(100, c.cargaActual) + "%;width:" + Math.max(0, Math.min(100 - c.cargaActual, proyectada - c.cargaActual)) + '%"></i></span>' +
            '<span class="sp-carga-lbl">' + c.disponible + "% libre · quedaría en <b>" + proyectada + "%</b></span></span>" +
            "</label>";
        }).join("");
      }
      // Paginación
      var pag = $("[data-sp-pag]");
      pag.innerHTML = data.paginas > 1
        ? '<button type="button" class="page-btn" data-pag="-1"' + (data.pagina <= 1 ? " disabled" : "") + ' aria-label="Página anterior">‹</button>' +
          '<span class="text-tertiary">Página ' + data.pagina + " de " + data.paginas + "</span>" +
          '<button type="button" class="page-btn" data-pag="1"' + (data.pagina >= data.paginas ? " disabled" : "") + ' aria-label="Página siguiente">›</button>'
        : "";
      pintarSeleccion();
    }
    function pintarSeleccion() {
      var ids = Object.keys(seleccion);
      var vac = ultima ? ultima.vacantes : 0;
      $("[data-sp-cuenta]").textContent = modoPm ? (ids.length ? "Project Manager elegido" : "Nadie elegido")
        : ids.length + " de " + vac + " vacante" + (vac === 1 ? "" : "s");
      $("[data-sp-sel]").innerHTML = ids.length
        ? ids.map(function (id) {
            return '<button type="button" class="sp-pill is-on" data-quitar-sel="' + id + '" title="Quitar">' + esc(seleccion[id].nombre) + " " + icon("icon-x", "icon-xs") + "</button>";
          }).join("")
        : '<span class="sp-vacio">Marca personas de la lista.</span>';
      var boton = $("[data-sp-enviar]");
      boton.disabled = ids.length === 0;
      boton.textContent = !modoPm && ids.length > 1 ? verbo + " " + ids.length : verbo;
    }

    // ---------- Eventos
    $("#sp-q").addEventListener("input", function (e) { estado.q = e.target.value.trim(); recargar(true); });
    $("#sp-orden").addEventListener("change", function (e) { estado.orden = e.target.value; recargar(); });
    $("#sp-cargo").addEventListener("change", function (e) { estado.cargo = e.target.value; recargar(); });
    $("#sp-exp").addEventListener("change", function (e) { estado.experienciaMin = e.target.value; recargar(); });
    $("#sp-cap").addEventListener("change", function (e) { estado.soloConCapacidad = e.target.checked; recargar(); });
    $("#sp-disp").addEventListener("input", function (e) {
      bd.querySelector("[data-sp-disp-val]").textContent = e.target.value + "%";
      estado.disponibleMin = e.target.value === "0" ? "" : e.target.value;
      recargar(true);
    });
    if ($("#sp-cap-pm")) $("#sp-cap-pm").addEventListener("change", function (e) { estado.soloConCapacidad = e.target.checked; recargar(); });
    if ($("#sp-ded")) $("#sp-ded").addEventListener("input", function (e) {
      var v = parseInt(e.target.value, 10);
      if (Number.isInteger(v) && v >= 1) { estado.dedicacion = v; recargar(true); }
    });
    $("#sp-otra").addEventListener("change", function (e) {
      var id = e.target.value;
      if (id && estado.habilidades.indexOf(id) === -1) { estado.habilidades.push(id); recargar(); }
      e.target.value = "";
    });
    bd.addEventListener("click", function (e) {
      var pill = e.target.closest("[data-sp-req] .sp-pill");
      if (pill) {
        var i = estado.habilidades.indexOf(pill.dataset.hab);
        if (i === -1) estado.habilidades.push(pill.dataset.hab); else estado.habilidades.splice(i, 1);
        recargar();
        return;
      }
      var quitarHab = e.target.closest("[data-quitar-hab]");
      if (quitarHab) { estado.habilidades.splice(estado.habilidades.indexOf(quitarHab.dataset.quitarHab), 1); recargar(); return; }
      var quitarSel = e.target.closest("[data-quitar-sel]");
      if (quitarSel) {
        delete seleccion[quitarSel.dataset.quitarSel];
        var cb = lista.querySelector('.sp-check[value="' + quitarSel.dataset.quitarSel + '"]');
        if (cb) { cb.checked = false; cb.closest(".sp-card").classList.remove("is-selected"); if (cb.closest(".is-blocked")) cb.disabled = true; }
        pintarSeleccion();
        return;
      }
      var pag = e.target.closest("[data-pag]");
      if (pag) { estado.pagina += Number(pag.dataset.pag); cargar(); lista.scrollTop = 0; return; }
      var toggle = e.target.closest("[data-sp-toggle]");
      if (toggle) {
        var abierto = bd.querySelector(".modal-picker").classList.toggle("is-filtros-abiertos");
        toggle.setAttribute("aria-expanded", abierto ? "true" : "false");
        return;
      }
      if (e.target.closest("[data-sp-limpiar]")) {
        estado.q = ""; estado.habilidades = []; estado.cargo = ""; estado.disponibleMin = ""; estado.experienciaMin = ""; estado.soloConCapacidad = false;
        $("#sp-q").value = ""; $("#sp-cargo").value = ""; $("#sp-exp").value = ""; $("#sp-cap").checked = false;
        $("#sp-disp").value = 0; bd.querySelector("[data-sp-disp-val]").textContent = "0%";
        recargar();
      }
    });
    lista.addEventListener("change", function (e) {
      var cb = e.target.closest(".sp-check");
      if (!cb || !ultima) return;
      var c = ultima.items.filter(function (x) { return String(x.perfilId) === cb.value; })[0];
      if (cb.checked && modoPm) {
        // Selección única: desmarca a quien estuviera elegido.
        Object.keys(seleccion).forEach(function (id) { delete seleccion[id]; });
        lista.querySelectorAll(".sp-check:checked").forEach(function (otro) {
          if (otro !== cb) { otro.checked = false; otro.closest(".sp-card").classList.remove("is-selected"); }
        });
        seleccion[cb.value] = c;
      } else if (cb.checked) {
        if (Object.keys(seleccion).length >= ultima.vacantes) {
          cb.checked = false;
          toast("Este proyecto solo tiene " + ultima.vacantes + " vacante(s).");
          return;
        }
        seleccion[cb.value] = c;
      } else {
        delete seleccion[cb.value];
      }
      cb.closest(".sp-card").classList.toggle("is-selected", cb.checked);
      pintarSeleccion();
    });

    $("[data-sp-enviar]").addEventListener("click", function () {
      var ids = Object.keys(seleccion);
      if (modoPm) {
        if (!ids.length) return toast("Elige a una persona.");
        var elegido = seleccion[ids[0]];
        window.SBAI.modal.close(bd);
        if (opts.onElegir) opts.onElegir(elegido);
        return;
      }
      var d = estado.dedicacion;
      var limite = ultima ? ultima.limiteCarga : 100;
      if (!ids.length) return toast("Marca al menos una persona.");
      if (!Number.isInteger(d) || d < 1 || d > limite) return toast("La dedicación debe estar entre 1% y " + limite + "%.");
      var exceden = ids.map(function (id) { return seleccion[id]; }).filter(function (c) { return c.cargaActual + d > limite; });
      if (exceden.length) {
        return toast("Con " + d + "% superarían el límite: " + exceden.map(function (c) { return c.nombre; }).join(", ") + ". Baja la dedicación o quítalos.");
      }
      var fecha = proponer ? "" : $("#sp-fecha").value;
      if (!proponer && !fecha) return toast("Elige la fecha de inicio.");
      var nombres = ids.map(function (id) { return seleccion[id].nombre + " (" + seleccion[id].cargaActual + "% → " + (seleccion[id].cargaActual + d) + "%)"; });
      var campos = Object.assign({ perfilIds: ids, cargaPorcentaje: d }, proponer ? {} : { fechaInicio: fecha }, opts.extra || {});
      window.SBAI.modal.confirm({
        variant: "primary", icon: "icon-users",
        title: proponer
          ? "¿Proponer " + (ids.length === 1 ? "a " + seleccion[ids[0]].nombre : ids.length + " personas") + "?"
          : "¿Asignar " + (ids.length === 1 ? "a " + seleccion[ids[0]].nombre : ids.length + " personas") + "?",
        desc: proponer
          ? "Se enviará una propuesta por persona al Resource Manager para “" + (opts.proyecto || "este proyecto") + "”, con " + d + "% de dedicación."
          : "Se asignarán a “" + (opts.proyecto || "este proyecto") + "” con " + d + "% de dedicación desde el " + fecha + ".",
        consequences: nombres,
        confirmLabel: proponer ? "Enviar propuestas" : "Asignar", cancelLabel: "Volver",
        onConfirm: function () { window.SBAI.submitForm(opts.accion, campos); }
      });
    });

    cargar();
    setTimeout(function () { $("#sp-q").focus(); }, 80);
    return bd;
  }

  window.SBAI.selectorPersonas = { abrir: abrir };
})();
