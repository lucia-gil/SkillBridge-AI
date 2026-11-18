/**
 * colaboradores-rm.js — Directorio del equipo (resource-manager/colaboradores.html).
 *
 *  - Habilidad y estado filtran en el servidor (recarga con ?habilidadId=&estado=).
 *  - El buscador filtra en el cliente la lista que ya devolvió el servidor
 *    (nombre, correo, cargo y habilidades) y la paginación se recalcula.
 *  - Las tarjetas de capacidad (antes "Ocupación del equipo") filtran por
 *    rango de carga, combinadas con el buscador; otro clic quita el filtro.
 *  - "Ajustar carga" abre las asignaciones activas de la persona para bajar
 *    la dedicación o finalizar una asignación (RG-214).
 *  - Paginación de 8 filas con la etiqueta "Página N de M" que paginacion.js
 *    convierte en números.
 *  - Confirmaciones de "No disponible" / "Disponible" (misma lógica de antes).
 */
(function () {
  "use strict";

  var PAGE_SIZE = 8;

  function navegar(cambios) {
    var params = new URLSearchParams(window.location.search);
    Object.keys(cambios).forEach(function (k) {
      if (cambios[k]) params.set(k, cambios[k]); else params.delete(k);
    });
    var q = params.toString();
    window.location.href = "/resource-manager/colaboradores.html" + (q ? "?" + q : "");
  }

  function normalizar(t) {
    return (t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim();
  }

  function logos() {
    if (!(window.SBAI && window.SBAI.techIcons)) return;
    document.querySelectorAll(".cl [data-tech-logo]").forEach(function (el) {
      var html = window.SBAI.techIcons.logo(el.getAttribute("data-tech-logo"));
      if (html) el.innerHTML = html;
    });
  }

  function tabla() {
    var rows = Array.from(document.querySelectorAll("[data-colaborador-row]"));
    if (!rows.length) return;
    rows.forEach(function (r) { r._texto = normalizar(r.getAttribute("data-buscar")); });

    var visibleCount = document.getElementById("colaboradores-visible-count");
    var totalCount = document.getElementById("colaboradores-total-count");
    var pageLabel = document.getElementById("colaboradores-page-label");
    var prev = document.getElementById("colaboradores-prev");
    var next = document.getElementById("colaboradores-next");
    var vacio = document.getElementById("cl-sin-resultados");
    var vacioTexto = document.getElementById("cl-sin-resultados-texto");
    var buscar = document.getElementById("cl-buscar");
    var kpis = Array.from(document.querySelectorAll("[data-cl-carga]"));
    var page = 1;
    var coinciden = rows;
    var cargaActiva = "";

    function render() {
      var pages = Math.max(1, Math.ceil(coinciden.length / PAGE_SIZE));
      page = Math.min(page, pages);
      var desde = (page - 1) * PAGE_SIZE;
      rows.forEach(function (r) { r.hidden = true; });
      coinciden.slice(desde, desde + PAGE_SIZE).forEach(function (r) { r.hidden = false; });
      visibleCount.textContent = Math.max(0, Math.min(PAGE_SIZE, coinciden.length - desde));
      totalCount.textContent = coinciden.length;
      pageLabel.textContent = "Página " + page + " de " + pages;
      prev.disabled = page === 1;
      next.disabled = page === pages;
      vacio.hidden = coinciden.length > 0;
    }

    function filtrar() {
      var q = normalizar(buscar.value);
      var partes = q.split(/\s+/).filter(Boolean);
      coinciden = rows.filter(function (r) {
        return (!cargaActiva || r.getAttribute("data-carga") === cargaActiva) &&
          partes.every(function (p) { return r._texto.indexOf(p) !== -1; });
      });
      var termino = buscar.value.trim();
      var rango = cargaActiva ? kpis.filter(function (k) { return k.getAttribute("data-cl-carga") === cargaActiva; })[0] : null;
      var nombreRango = rango ? rango.querySelector(".hero-card-label").textContent.toLowerCase() : "";
      vacioTexto.textContent = termino && rango ? "Nadie en «" + nombreRango + "» coincide con «" + termino + "»."
        : termino ? "Nadie coincide con «" + termino + "» en esta lista."
        : rango ? "No hay nadie en «" + nombreRango + "» con los filtros actuales." : "Nadie coincide con estos filtros.";
      page = 1;
      render();
    }

    kpis.forEach(function (k) {
      k.addEventListener("click", function () {
        var valor = k.getAttribute("data-cl-carga");
        cargaActiva = cargaActiva === valor ? "" : valor;
        kpis.forEach(function (o) { o.setAttribute("aria-pressed", String(o.getAttribute("data-cl-carga") === cargaActiva)); });
        filtrar();
        if (cargaActiva) document.querySelector(".cl").scrollIntoView({ behavior: "smooth", block: "start" });
      });
    });

    prev.addEventListener("click", function () { if (page > 1) { page--; render(); } });
    next.addEventListener("click", function () { page++; render(); });
    buscar.addEventListener("input", filtrar);
    buscar.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && buscar.value) { buscar.value = ""; filtrar(); }
    });
    render();
  }

  function filtrosServidor() {
    var sel = document.getElementById("filtro-habilidad");
    if (sel) sel.addEventListener("change", function () { navegar({ habilidadId: sel.value }); });
    document.querySelectorAll("[data-cl-estado]").forEach(function (b) {
      b.addEventListener("click", function () {
        if (b.classList.contains("is-active")) return;
        navegar({ estado: b.getAttribute("data-cl-estado") });
      });
    });
  }

  function acciones() {
    var submitForm = window.SBAI && window.SBAI.submitForm;
    function lista(attr) {
      return (attr || "").split("|").map(function (x) { return x.trim(); }).filter(Boolean);
    }
    document.querySelectorAll("[data-suspender]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var nombre = btn.dataset.nombre;
        var lidera = lista(btn.dataset.lidera);
        var colabora = lista(btn.dataset.colabora);

        // Si lidera proyectos, el backend lo rechaza (UsuarioService.salirDeProyectos):
        // se avisa ANTES, sin pedir motivo, y se ofrece ir directo a "Cambiar PM".
        if (lidera.length) {
          SBAI.modal.confirm({
            title: nombre + " no se puede marcar como no disponible todavía",
            desc: "Es Project Manager de " + (lidera.length === 1 ? "un proyecto activo" : lidera.length + " proyectos activos") +
                  ". Primero asigna otro PM con “Cambiar PM” en Asignaciones; después podrás marcarlo como no disponible.",
            variant: "warning", icon: "icon-user-x",
            subject: { nombre: nombre, meta: "Lidera: " + lidera.join(", ") },
            confirmLabel: "Ir a Asignaciones", cancelLabel: "Volver",
            onConfirm: function () { window.location.href = "/resource-manager/asignaciones.html"; }
          });
          return;
        }

        SBAI.modal.confirm({
          title: "¿Marcar a " + nombre + " como no disponible?",
          desc: "Deja de aparecer para asignaciones y propuestas hasta que lo vuelvas a marcar disponible. Sigue pudiendo iniciar sesión; su historial se conserva.",
          variant: "warning", icon: "icon-user-x",
          subject: { nombre: nombre },
          // Modelo v7: un perfil no disponible no sigue en proyectos.
          consequences: colabora.length
            ? ["Se finalizarán sus asignaciones en: " + colabora.join(", ") + ", y su carga se libera."]
            : ["No tiene asignaciones activas: no se finaliza ninguna."],
          reason: { label: "Motivo", required: true, placeholder: "Ej.: licencia prolongada.",
            presets: ["Licencia prolongada", "Terminó su contrato", "Solicitud del área"] },
          confirmLabel: "Marcar no disponible", cancelLabel: "Volver",
          onConfirm: function (d) {
            submitForm("/resource-manager/colaboradores/" + btn.dataset.suspender + "/estado", { nuevoEstado: "inactivo", motivo: d.motivo });
          }
        });
      });
    });
    document.querySelectorAll("[data-reactivar]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        submitForm("/resource-manager/colaboradores/" + btn.dataset.reactivar + "/estado", { nuevoEstado: "activo" });
      });
    });
  }

  /* ── Ajustar carga (antes "Rebalancear" en Ocupación del equipo) ──
     Guarda por AJAX contra el REST de RmOcupacionController (SBAI.api agrega
     el token CSRF): no se recarga la página, así se conservan el scroll, el
     buscador y la tarjeta que está filtrando. Con la respuesta se repintan
     el modal, la fila de la persona y los números de las tarjetas. */
  function esc(t) { var d = document.createElement("div"); d.textContent = t == null ? "" : t; return d.innerHTML; }
  function icono(n) { return '<svg class="icon icon-sm" aria-hidden="true"><use href="/img/icons.svg#' + n + '"></use></svg>'; }

  /** Asignaciones activas por persona: primero las de la plantilla del servidor, luego las que devuelve el REST. */
  var asignacionesDe = {};
  function leerPlantillas(plantillas) {
    plantillas.querySelectorAll("template[data-perfil]").forEach(function (tpl) {
      asignacionesDe[tpl.getAttribute("data-perfil")] = Array.from(tpl.content.querySelectorAll("tr[data-asignacion]")).map(function (tr) {
        var rol = tr.querySelector(".rb-rol");
        return {
          asignacionId: Number(tr.dataset.asignacion), carga: Number(tr.dataset.carga), esProjectManager: tr.dataset.pm === "true",
          proyecto: tr.querySelector(".cell-primary span").textContent, rolLabel: rol ? rol.textContent : ""
        };
      });
    });
  }

  function filasHtml(lista, limite) {
    if (!lista.length) return '<tr><td colspan="3" class="text-tertiary" style="text-align:center">Sin asignaciones activas.</td></tr>';
    return lista.map(function (a) {
      return '<tr data-asignacion="' + a.asignacionId + '" data-carga="' + a.carga + '">' +
        '<td class="cell-primary"><span>' + esc(a.proyecto) + '</span><span class="rb-rol">' + esc(a.rolLabel) + '</span></td>' +
        '<td><input class="input rb-input" type="number" min="1" max="' + limite + '" value="' + a.carga + '" aria-label="Dedicación (%)"></td>' +
        '<td class="cell-actions"><button type="button" data-rb-guardar>Guardar</button>' +
        (a.esProjectManager ? "" : '<button type="button" class="danger" data-rb-finalizar>Finalizar</button>') + "</td></tr>";
    }).join("");
  }

  /** Repinta la fila de la persona (barra, %, estado, botón) y recuenta las tarjetas. */
  function actualizarFila(btn, r) {
    var tr = btn.closest("tr[data-colaborador-row]");
    var carga = tr.querySelector(".cl-load");
    carga.setAttribute("data-estado", r.cargaClave);
    carga.title = r.cargaLabel + " · límite " + r.limite + "%";
    carga.querySelector(".cl-load-fill").style.width = r.cargaPct + "%";
    carga.querySelector(".cl-load-value").textContent = r.ocupacion + "%";
    carga.querySelector(".cl-load-label").textContent = r.cargaLabel;
    if (tr.getAttribute("data-carga")) tr.setAttribute("data-carga", r.cargaClave);
    btn.dataset.carga = r.ocupacion;
    var sobre = r.cargaClave === "sobre";
    var texto = (sobre ? "Rebalancear" : "Ajustar") + " la carga de " + btn.dataset.nombre;
    btn.classList.toggle("is-alert", sobre);
    btn.title = texto; btn.setAttribute("aria-label", texto);
    btn.querySelector("span").textContent = sobre ? "Rebalancear" : "Ajustar";
    if (r.ocupacion === 0) {   // sin carga: sin botón, pero con el espacio para que las columnas sigan alineadas
      var vacio = document.createElement("span");
      vacio.className = "cl-ajustar-vacio"; vacio.setAttribute("aria-hidden", "true");
      btn.replaceWith(vacio);
    }
    document.querySelectorAll("[data-cl-carga]").forEach(function (k) {
      var n = document.querySelectorAll('[data-colaborador-row][data-carga="' + k.getAttribute("data-cl-carga") + '"]').length;
      k.querySelector(".hero-card-value").textContent = n;
    });
  }

  function ajustarCarga() {
    var plantillas = document.getElementById("rebalancear-templates");
    if (!plantillas) return;
    var LIMITE = Number(plantillas.getAttribute("data-limite")) || 100;
    var api = window.SBAI.api;
    leerPlantillas(plantillas);

    function abrir(btn) {
      var perfil = btn.dataset.rebalancear;
      var html = '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
        '<div class="modal-header"><div><h2 class="rb-titulo"></h2><p>' + esc(btn.dataset.nombre) +
        ' · carga total <strong class="rb-total"></strong> (límite ' + LIMITE + '%)</p></div>' +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
        '<div class="modal-body"><div class="rb-aviso"></div>' +
        '<div class="table-scroll rb-table"><table class="data-table"><thead><tr><th>Proyecto</th><th style="width:130px">Dedicación (%)</th><th>Acciones</th></tr></thead><tbody class="rb-filas"></tbody></table></div>' +
        '<p class="text-tertiary rb-preview" style="font-size:var(--fs-xs);margin-top:var(--space-3)"></p>' +
        '</div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cerrar</button></div></div>';
      var backdrop = window.SBAI.modal.open(html);

      function pintar() {
        var carga = Number(btn.dataset.carga) || 0;
        var exceso = carga - LIMITE;
        backdrop.querySelector(".rb-titulo").textContent = exceso > 0 ? "Rebalancear carga" : "Ajustar carga";
        backdrop.querySelector(".rb-total").textContent = carga + "%";
        backdrop.querySelector(".rb-aviso").innerHTML = exceso > 0
          ? '<div class="callout warn" style="margin-bottom:var(--space-4)"><span class="callout-icon"><svg class="icon"><use href="/img/icons.svg#icon-alert-triangle"></use></svg></span>' +
            '<span class="callout-body">Supera el límite en <strong>' + exceso + '%</strong>. Baja la dedicación en algún proyecto o finaliza una asignación.</span></div>'
          : "";
        backdrop.querySelector(".rb-filas").innerHTML = filasHtml(asignacionesDe[perfil] || [], LIMITE);
        recalcular();
      }
      function recalcular() {
        var total = 0;
        backdrop.querySelectorAll("tr[data-asignacion]").forEach(function (tr) { total += Number(tr.querySelector(".rb-input").value) || 0; });
        backdrop.querySelector(".rb-preview").textContent = "Con los valores escritos, la carga total quedaría en " + total + "%" + (total > LIMITE ? " (sigue por encima del límite)." : ".");
      }
      function aplicar(r) {
        asignacionesDe[perfil] = r.asignaciones;
        actualizarFila(btn, r);
        if (document.body.contains(backdrop)) pintar();
        window.SBAI.toast.show(esc(r.mensaje), { type: "success" });
      }
      function fallo(xhr) {
        window.SBAI.toast.show(esc(api.mensajeError(xhr)), { type: "error" });
      }

      backdrop.addEventListener("input", function (e) { if (e.target.classList.contains("rb-input")) recalcular(); });
      backdrop.addEventListener("click", function (e) {
        var guardar = e.target.closest("[data-rb-guardar]"), finalizar = e.target.closest("[data-rb-finalizar]");
        if (!guardar && !finalizar) return;
        var tr = e.target.closest("tr[data-asignacion]");
        var proyecto = tr.querySelector(".cell-primary span").textContent;
        var id = tr.dataset.asignacion;
        if (guardar) {
          var nuevo = Number(tr.querySelector(".rb-input").value);
          var antes = Number(tr.dataset.carga);
          if (!Number.isInteger(nuevo) || nuevo < 1 || nuevo > LIMITE) return window.SBAI.toast.show("La dedicación debe estar entre 1% y " + LIMITE + "%.", { type: "error" });
          if (nuevo === antes) return window.SBAI.toast.show("No cambiaste la dedicación.", { type: "info" });
          window.SBAI.modal.confirm({
            title: "¿Cambiar dedicación a " + nuevo + "%?",
            desc: btn.dataset.nombre + " en “" + proyecto + "”: " + antes + "% → " + nuevo + "%.",
            variant: "primary", icon: "icon-sliders",
            reason: { label: "Motivo", required: false, placeholder: "Opcional: queda registrado en Auditoría.", presets: ["Rebalanceo por sobre-asignación", "Cambio de prioridades", "A solicitud del colaborador"] },
            confirmLabel: "Guardar cambio", cancelLabel: "Volver",
            onConfirm: function (d) {
              api.put("/resource-manager/api/asignaciones/" + id + "/dedicacion", { cargaPorcentaje: nuevo, motivo: d.motivo || null }).done(aplicar).fail(fallo);
            }
          });
        } else {
          window.SBAI.proyectoAcciones.finalizarAsignacion({
            nombre: btn.dataset.nombre, proyecto: proyecto,
            alConfirmar: function (motivo) {
              api.post("/resource-manager/api/asignaciones/" + id + "/finalizar", { motivo: motivo || null }).done(aplicar).fail(fallo);
            }
          });
        }
      });
      pintar();
    }

    document.querySelectorAll("[data-rebalancear]").forEach(function (btn) {
      btn.addEventListener("click", function () { abrir(btn); });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    // Los avisos de SBAI_FLASH ya los muestra topbar-actions.js.
    logos();
    filtrosServidor();
    acciones();
    ajustarCarga();
    tabla();
  });
})();
