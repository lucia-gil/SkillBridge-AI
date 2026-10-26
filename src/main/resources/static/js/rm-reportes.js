/**
 * rm-reportes.js — Reportes del Resource Manager (resource-manager/reportes.html).
 *
 * Los filtros se aplican en el servidor (formulario GET), así lo exportado a
 * Excel/PDF coincide con lo que se ve. Este script solo:
 *  - envía el formulario solo al cambiar un filtro (la búsqueda espera a que
 *    se deje de escribir o a Enter), para no tener un botón "Aplicar";
 *  - muestra el valor del deslizador de ocupación mínima mientras se mueve;
 *  - pagina en el cliente las dos tablas (8 filas), escribiendo la etiqueta
 *    "Página N de M" que paginacion.js convierte en números.
 */
(function () {
  "use strict";

  var PAGE_SIZE = 8;

  function paginar(scope, prefijo) {
    var rows = Array.from(document.querySelectorAll('[data-rr-row="' + scope + '"]'));
    var label = document.getElementById(prefijo + "-page-label");
    if (!rows.length || !label) return;
    var prev = document.getElementById(prefijo + "-prev");
    var next = document.getElementById(prefijo + "-next");
    var visibles = document.getElementById(prefijo + "-visible");
    var pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
    var page = 1;

    function render() {
      rows.forEach(function (r, i) { r.hidden = i < (page - 1) * PAGE_SIZE || i >= page * PAGE_SIZE; });
      visibles.textContent = Math.min(PAGE_SIZE, rows.length - (page - 1) * PAGE_SIZE);
      label.textContent = "Página " + page + " de " + pages;
      prev.disabled = page === 1;
      next.disabled = page === pages;
    }
    prev.addEventListener("click", function () { if (page > 1) { page--; render(); } });
    next.addEventListener("click", function () { if (page < pages) { page++; render(); } });
    render();
  }

  document.addEventListener("DOMContentLoaded", function () {
    // Los avisos de SBAI_FLASH ya los muestra topbar-actions.js.
    paginar("ocupacion", "rr-oc");
    paginar("cobertura", "rr-cb");

    var form = document.getElementById("rr-filtros");
    if (!form) return;
    var enviando = false;
    function enviar() {
      if (enviando) return;
      enviando = true;
      form.classList.add("is-loading");
      form.setAttribute("aria-busy", "true");
      // Sin parámetros vacíos en la URL (q=&min=0): enlaces más limpios.
      Array.from(form.elements).forEach(function (el) {
        if (!el.name) return;
        if ((el.name === "q" && !el.value.trim()) || (el.name === "min" && el.value === "0")) el.disabled = true;
      });
      form.submit();
    }

    var q = form.querySelector('input[name="q"]');
    var espera;
    q.addEventListener("input", function () {
      clearTimeout(espera);
      espera = setTimeout(enviar, 700);
    });
    q.addEventListener("keydown", function (e) {
      if (e.key === "Enter") { e.preventDefault(); clearTimeout(espera); enviar(); }
    });

    var rango = document.getElementById("rr-min");
    var salida = document.getElementById("rr-min-out");
    rango.addEventListener("input", function () { salida.textContent = rango.value + "%"; });
    rango.addEventListener("change", enviar);

    form.querySelector('input[name="brecha"]').addEventListener("change", enviar);
    document.getElementById("rr-estado").addEventListener("change", function () {
      // El multiselect dispara "change" por cada casilla: se espera un instante
      // por si el usuario marca varias seguidas.
      clearTimeout(espera);
      espera = setTimeout(enviar, 900);
    });
  });
})();
