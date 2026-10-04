/**
 * asignacion-masiva.js — Selección múltiple de colaboradores (checkboxes)
 * para el panel "Asignar colaboradores" del modal de un proyecto.
 * Lo usan pm-asignaciones.js y rm-asignaciones.js.
 *
 *  SBAI.asignacionMasiva.montar(panel, {
 *    perfiles:   [{perfilId, nombre, correo, cargo, cargaActual, proyectosActivos}],
 *    limiteCarga: 100,                 // límite configurado en Configuración
 *    url:        '/…/proyectos/ID/asignar-varios',
 *    extra:      { volver: 'asignaciones' },   // campos extra del POST (opcional)
 *    proyecto:   'Nombre del proyecto'
 *  });
 *
 * El panel debe tener data-vacantes, un contenedor [data-bulk-list], los
 * campos [data-field="cargaPorcentaje"] y [data-field="fechaInicio"] y un
 * botón [data-assign-project]. Las personas que ya están en el equipo se
 * leen de las filas [data-miembro-perfil] del mismo modal.
 *
 * El backend vuelve a validar TODO (vacantes, carga, máximo de proyectos):
 * lo de aquí es solo para avisar antes de enviar.
 */
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) {
    return String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim();
  }
  function iniciales(nombre) {
    var partes = String(nombre || "").trim().split(/\s+/);
    return ((partes[0] || "").charAt(0) + (partes.length > 1 ? partes[partes.length - 1].charAt(0) : "")).toUpperCase();
  }
  function toast(msg, type) {
    if (window.SBAI.toast) window.SBAI.toast.show(msg, { type: type || "error" });
  }

  function enviar(url, campos, perfilIds) {
    var form = document.createElement("form");
    form.method = "post";
    form.action = url;
    function add(name, value) {
      var input = document.createElement("input");
      input.type = "hidden"; input.name = name; input.value = value;
      form.appendChild(input);
    }
    Object.keys(campos).forEach(function (k) { if (campos[k] != null) add(k, campos[k]); });
    perfilIds.forEach(function (id) { add("perfilIds", id); });
    document.body.appendChild(form);
    form.submit();
  }

  function montar(panel, opts) {
    if (!panel || panel.dataset.bulkMontado) return;
    panel.dataset.bulkMontado = "1";
    opts = opts || {};
    var perfiles = opts.perfiles || [];
    var limite = Number(opts.limiteCarga) || 100;
    var vacantes = Math.max(0, Number(panel.dataset.vacantes) || 0);
    var modal = panel.closest(".modal") || document;
    var enEquipo = {};
    modal.querySelectorAll("[data-miembro-perfil]").forEach(function (el) { enEquipo[el.dataset.miembroPerfil] = true; });

    var cont = panel.querySelector("[data-bulk-list]");
    var dedicacionInput = panel.querySelector('[data-field="cargaPorcentaje"]');
    var fechaInput = panel.querySelector('[data-field="fechaInicio"]');
    var boton = panel.querySelector("[data-assign-project]");
    var seleccion = {};   // perfilId -> perfil

    cont.innerHTML =
      '<div class="bulk-toolbar">' +
        '<input type="search" class="input bulk-search" placeholder="Filtrar por nombre, correo o cargo…" autocomplete="off">' +
        '<span class="bulk-count" aria-live="polite"></span>' +
      '</div>' +
      '<div class="bulk-table"><table class="data-table"><thead><tr>' +
        '<th class="bulk-check-col"><input type="checkbox" class="bulk-check" data-bulk-all aria-label="Seleccionar visibles"></th>' +
        '<th>Colaborador</th><th>Carga actual</th>' +
      '</tr></thead><tbody></tbody></table>' +
      '<div class="bulk-empty" hidden>Nadie coincide con ese filtro.</div></div>';

    var tbody = cont.querySelector("tbody");
    var buscador = cont.querySelector(".bulk-search");
    var contador = cont.querySelector(".bulk-count");
    var todos = cont.querySelector("[data-bulk-all]");
    var vacio = cont.querySelector(".bulk-empty");

    tbody.innerHTML = perfiles.map(function (p) {
      var yaEsta = !!enEquipo[String(p.perfilId)];
      var sinCapacidad = !yaEsta && p.cargaActual >= limite;
      var bloqueado = yaEsta || sinCapacidad;
      var nivel = p.cargaActual >= limite ? "red" : (p.cargaActual >= limite * 0.8 ? "amber" : "green");
      var motivo = yaEsta ? "Ya está en el equipo" : (sinCapacidad ? "Sin capacidad" : "");
      return '<tr class="bulk-row' + (bloqueado ? " is-blocked" : "") + '" data-perfil="' + esc(p.perfilId) + '" data-hay="' +
        esc(norm(p.nombre + " " + p.correo + " " + (p.cargo || ""))) + '">' +
        '<td class="bulk-check-col"><input type="checkbox" class="bulk-check" value="' + esc(p.perfilId) + '"' +
          (bloqueado ? " disabled" : "") + ' aria-label="Seleccionar a ' + esc(p.nombre) + '"></td>' +
        '<td class="cell-person"><span class="avatar avatar-sm">' + esc(iniciales(p.nombre)) + '</span>' +
          '<span class="cell-person-text"><span class="cell-person-name">' + esc(p.nombre) + '</span>' +
          '<span class="cell-person-sub">' + esc(p.cargo ? p.cargo + " · " + p.correo : p.correo) + '</span></span></td>' +
        '<td><div class="progress-inline bulk-load"><div class="progress-bar"><div class="progress-bar-fill ' + nivel +
          '" style="width:' + Math.min(100, p.cargaActual) + '%"></div></div><span class="progress-value">' + esc(p.cargaActual) + '%</span></div>' +
          '<span class="bulk-sub">' + (motivo ? esc(motivo) : esc(p.proyectosActivos) + " proyecto(s) activo(s)") + '</span>' +
          '<span class="bulk-warn" hidden></span></td>' +
      '</tr>';
    }).join("");

    var filas = Array.prototype.slice.call(tbody.querySelectorAll(".bulk-row"));
    var porId = {};
    perfiles.forEach(function (p) { porId[String(p.perfilId)] = p; });

    function seleccionados() { return Object.keys(seleccion); }
    function dedicacion() { return Number(dedicacionInput ? dedicacionInput.value : 0); }

    function excede(p) { return p.cargaActual + dedicacion() > limite; }

    function refrescar() {
      var n = seleccionados().length;
      var lleno = n >= vacantes;
      var d = dedicacion();
      filas.forEach(function (fila) {
        var cb = fila.querySelector(".bulk-check");
        var p = porId[fila.dataset.perfil];
        if (!fila.classList.contains("is-blocked")) {
          cb.disabled = !cb.checked && lleno;
        }
        fila.classList.toggle("is-selected", cb.checked);
        var warn = fila.querySelector(".bulk-warn");
        var supera = !fila.classList.contains("is-blocked") && Number.isFinite(d) && d > 0 && excede(p);
        warn.hidden = !supera;
        if (supera) warn.textContent = "Con " + d + "% llegaría a " + (p.cargaActual + d) + "% (límite " + limite + "%)";
      });
      contador.innerHTML = "<strong>" + n + "</strong> seleccionado(s) · " + vacantes + " vacante(s)" +
        (lleno && n > 0 ? ' <span class="bulk-full">· cupo cubierto</span>' : "");
      var visiblesLibres = filas.filter(function (f) { return !f.hidden && !f.classList.contains("is-blocked"); });
      var visiblesMarcadas = visiblesLibres.filter(function (f) { return f.querySelector(".bulk-check").checked; });
      todos.checked = visiblesLibres.length > 0 && visiblesMarcadas.length === Math.min(visiblesLibres.length, vacantes);
      todos.indeterminate = visiblesMarcadas.length > 0 && !todos.checked;
      todos.disabled = visiblesLibres.length === 0 || vacantes === 0;
      if (boton) {
        boton.disabled = n === 0;
        boton.textContent = n > 1 ? "Asignar " + n + " seleccionados" : (n === 1 ? "Asignar seleccionado" : "Asignar seleccionados");
      }
    }

    function marcar(cb, valor) {
      cb.checked = valor;
      if (valor) seleccion[cb.value] = porId[cb.value]; else delete seleccion[cb.value];
    }

    tbody.addEventListener("change", function (e) {
      var cb = e.target.closest(".bulk-check");
      if (!cb) return;
      if (cb.checked && seleccionados().length >= vacantes) {
        cb.checked = false;
        toast("Este proyecto solo tiene " + vacantes + " vacante(s).", "error");
        return;
      }
      marcar(cb, cb.checked);
      refrescar();
    });
    // Clic en cualquier parte de la fila marca/desmarca.
    tbody.addEventListener("click", function (e) {
      if (e.target.closest(".bulk-check")) return;
      var fila = e.target.closest(".bulk-row");
      if (!fila) return;
      var cb = fila.querySelector(".bulk-check");
      if (cb.disabled) return;
      cb.checked = !cb.checked;
      cb.dispatchEvent(new Event("change", { bubbles: true }));
    });
    todos.addEventListener("change", function () {
      var visibles = filas.filter(function (f) { return !f.hidden && !f.classList.contains("is-blocked"); });
      if (todos.checked) {
        visibles.forEach(function (f) {
          var cb = f.querySelector(".bulk-check");
          if (!cb.checked && seleccionados().length < vacantes) marcar(cb, true);
        });
        if (visibles.some(function (f) { return !f.querySelector(".bulk-check").checked; })) {
          toast("Se marcaron solo " + vacantes + " persona(s): es el número de vacantes del proyecto.", "info");
        }
      } else {
        visibles.forEach(function (f) { marcar(f.querySelector(".bulk-check"), false); });
      }
      refrescar();
    });
    buscador.addEventListener("input", function () {
      var q = norm(buscador.value);
      var visibles = 0;
      filas.forEach(function (f) {
        f.hidden = q.length > 0 && f.dataset.hay.indexOf(q) === -1;
        if (!f.hidden) visibles++;
      });
      vacio.hidden = visibles !== 0;
      refrescar();
    });
    buscador.addEventListener("keydown", function (e) { if (e.key === "Enter") e.preventDefault(); });
    if (dedicacionInput) dedicacionInput.addEventListener("input", refrescar);

    if (boton) boton.addEventListener("click", function () {
      var ids = seleccionados();
      var d = dedicacion();
      var fecha = fechaInput ? fechaInput.value : "";
      if (!ids.length) return toast("Marca al menos un colaborador.");
      if (ids.length > vacantes) return toast("Este proyecto solo tiene " + vacantes + " vacante(s).");
      if (!Number.isInteger(d) || d < 1 || d > limite) return toast("La dedicación debe estar entre 1% y " + limite + "%.");
      if (!fecha) return toast("Selecciona una fecha de inicio.");
      var exceden = ids.map(function (id) { return porId[id]; }).filter(excede);
      if (exceden.length) {
        return toast("Con " + d + "% de dedicación superarían el límite de carga: " +
          exceden.map(function (p) { return p.nombre; }).join(", ") + ". Baja la dedicación o desmárcalos.");
      }
      var nombres = ids.map(function (id) { return porId[id].nombre; });
      var campos = Object.assign({ cargaPorcentaje: d, fechaInicio: fecha }, opts.extra || {});
      var ejecutar = function () { boton.disabled = true; enviar(opts.url, campos, ids); };
      if (window.SBAI.modal && window.SBAI.modal.confirm) {
        window.SBAI.modal.confirm({
          title: ids.length === 1 ? "¿Asignar a " + nombres[0] + "?" : "¿Asignar a " + ids.length + " colaboradores?",
          desc: "Se asignarán a \"" + (opts.proyecto || "este proyecto") + "\" con " + d + "% de dedicación desde el " + fecha + ".",
          variant: "primary", icon: "icon-users",
          consequences: nombres,
          confirmLabel: ids.length === 1 ? "Asignar" : "Asignar " + ids.length, cancelLabel: "Volver",
          onConfirm: ejecutar
        });
      } else {
        ejecutar();
      }
    });

    refrescar();
  }

  window.SBAI.asignacionMasiva = { montar: montar };
})();
