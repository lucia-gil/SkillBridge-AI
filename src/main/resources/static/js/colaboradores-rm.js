/**
 * colaboradores-rm.js — Directorio del equipo (resource-manager/colaboradores.html).
 *
 *  - Habilidad y estado filtran en el servidor (recarga con ?habilidadId=&estado=).
 *  - El buscador filtra en el cliente la lista que ya devolvió el servidor
 *    (nombre, correo, cargo y habilidades) y la paginación se recalcula.
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
    var termino = document.getElementById("cl-termino");
    var buscar = document.getElementById("cl-buscar");
    var page = 1;
    var coinciden = rows;

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
        return partes.every(function (p) { return r._texto.indexOf(p) !== -1; });
      });
      termino.textContent = buscar.value.trim();
      page = 1;
      render();
    }

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
    document.querySelectorAll("[data-suspender]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        SBAI.modal.confirm({
          title: "¿Marcar a " + btn.dataset.nombre + " como no disponible?",
          desc: "Deja de aparecer para asignaciones y propuestas hasta que lo vuelvas a marcar disponible. Sigue pudiendo iniciar sesión; su historial se conserva.",
          variant: "warning", icon: "icon-user-x",
          subject: { nombre: btn.dataset.nombre },
          // Modelo v7: un perfil suspendido no sigue en proyectos.
          consequences: [
            "Si colabora en proyectos, esas asignaciones se finalizan y su carga se libera.",
            "Si lidera algún proyecto, no se podrá suspender hasta usar “Cambiar PM” en Asignaciones."
          ],
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

  document.addEventListener("DOMContentLoaded", function () {
    // Los avisos de SBAI_FLASH ya los muestra topbar-actions.js.
    logos();
    filtrosServidor();
    acciones();
    tabla();
  });
})();
