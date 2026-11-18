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

  /* ── Ajustar carga (antes "Rebalancear" en Ocupación del equipo) ── */
  function esc(t) { var d = document.createElement("div"); d.textContent = t == null ? "" : t; return d.innerHTML; }

  function ajustarCarga() {
    var plantillas = document.getElementById("rebalancear-templates");
    if (!plantillas) return;
    var LIMITE = Number(plantillas.getAttribute("data-limite")) || 100;
    document.querySelectorAll("[data-rebalancear]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var tpl = plantillas.querySelector('template[data-perfil="' + btn.dataset.rebalancear + '"]');
        var filas = tpl ? tpl.innerHTML : "";
        var carga = Number(btn.dataset.carga);
        var exceso = carga - LIMITE;
        var html = '<div class="modal modal-wide" role="dialog" aria-modal="true">' +
          '<div class="modal-header"><div><h2>' + (exceso > 0 ? "Rebalancear carga" : "Ajustar carga") + '</h2><p>' + esc(btn.dataset.nombre) +
          ' · carga total <strong>' + carga + '%</strong> (límite ' + LIMITE + '%)</p></div>' +
          '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
          '<div class="modal-body">' +
          (exceso > 0 ? '<div class="callout warn" style="margin-bottom:var(--space-4)"><span class="callout-icon"><svg class="icon"><use href="/img/icons.svg#icon-alert-triangle"></use></svg></span>' +
            '<span class="callout-body">Supera el límite en <strong>' + exceso + '%</strong>. Baja la dedicación en algún proyecto o finaliza una asignación.</span></div>' : "") +
          '<div class="table-scroll rb-table"><table class="data-table"><thead><tr><th>Proyecto</th><th style="width:130px">Dedicación (%)</th><th>Acciones</th></tr></thead><tbody>' +
          (filas.trim() ? filas : '<tr><td colspan="3" class="text-tertiary" style="text-align:center">Sin asignaciones activas.</td></tr>') +
          '</tbody></table></div>' +
          '<p class="text-tertiary rb-preview" style="font-size:var(--fs-xs);margin-top:var(--space-3)"></p>' +
          '</div><div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cerrar</button></div></div>';
        var backdrop = window.SBAI.modal.open(html);
        var preview = backdrop.querySelector(".rb-preview");
        function recalcular() {
          var total = 0;
          backdrop.querySelectorAll("tr[data-asignacion]").forEach(function (tr) { total += Number(tr.querySelector(".rb-input").value) || 0; });
          preview.textContent = "Con los valores escritos, la carga total quedaría en " + total + "%" + (total > LIMITE ? " (sigue por encima del límite)." : ".");
        }
        backdrop.querySelectorAll(".rb-input").forEach(function (i) { i.addEventListener("input", recalcular); });
        recalcular();
        backdrop.querySelectorAll("[data-rb-guardar]").forEach(function (b) {
          b.addEventListener("click", function () {
            var tr = b.closest("tr");
            var nuevo = Number(tr.querySelector(".rb-input").value);
            var antes = Number(tr.dataset.carga);
            if (!Number.isInteger(nuevo) || nuevo < 1 || nuevo > LIMITE) return window.SBAI.toast.show("La dedicación debe estar entre 1% y " + LIMITE + "%.", { type: "error" });
            if (nuevo === antes) return window.SBAI.toast.show("No cambiaste la dedicación.", { type: "info" });
            var proyecto = tr.querySelector(".cell-primary span").textContent;
            window.SBAI.modal.confirm({
              title: "¿Cambiar dedicación a " + nuevo + "%?",
              desc: btn.dataset.nombre + " en “" + proyecto + "”: " + antes + "% → " + nuevo + "%.",
              variant: "primary", icon: "icon-sliders",
              reason: { label: "Motivo", required: false, placeholder: "Opcional: queda registrado en Auditoría.", presets: ["Rebalanceo por sobre-asignación", "Cambio de prioridades", "A solicitud del colaborador"] },
              confirmLabel: "Guardar cambio", cancelLabel: "Volver",
              onConfirm: function (d) {
                var datos = { cargaPorcentaje: nuevo };
                if (d.motivo) datos.motivo = d.motivo;
                window.SBAI.submitForm("/resource-manager/asignaciones/" + tr.dataset.asignacion + "/dedicacion", datos);
              }
            });
          });
        });
        backdrop.querySelectorAll("[data-rb-finalizar]").forEach(function (b) {
          b.addEventListener("click", function () {
            var tr = b.closest("tr");
            window.SBAI.proyectoAcciones.finalizarAsignacion({
              url: "/resource-manager/asignaciones/" + tr.dataset.asignacion + "/finalizar",
              nombre: btn.dataset.nombre,
              proyecto: tr.querySelector(".cell-primary span").textContent,
              extra: { volver: "colaboradores" }
            });
          });
        });
      });
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
