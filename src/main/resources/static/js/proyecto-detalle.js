/**
 * proyecto-detalle.js — Vista de detalle de proyecto (compartido/proyecto-detalle.html).
 *  - logos de tecnologías (tech-icons.js);
 *  - texto de confirmación de "Asignar" (RM) según la dedicación escrita.
 */
(function () {
  "use strict";

  if (window.SBAI && window.SBAI.techIcons) {
    document.querySelectorAll(".pd [data-tech-logo]").forEach(function (el) {
      var html = window.SBAI.techIcons.logo(el.getAttribute("data-tech-logo"));
      if (html) el.innerHTML = html;
    });
  }

  document.querySelectorAll(".pd-sug-form[data-carga-actual]").forEach(function (form) {
    var input = form.querySelector("input[name=cargaPorcentaje]");
    if (!input) return;
    function actualizar() {
      var ded = parseInt(input.value, 10) || 0;
      var actual = parseInt(form.getAttribute("data-carga-actual"), 10) || 0;
      form.setAttribute("data-confirm-desc", "Se suma al equipo de " + form.getAttribute("data-proyecto") +
          " con " + ded + "% de dedicación. Su carga pasaría de " + actual + "% a " + (actual + ded) + "%.");
    }
    input.addEventListener("input", actualizar);
  });
})();
