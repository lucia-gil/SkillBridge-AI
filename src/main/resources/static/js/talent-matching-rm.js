/**
 * talent-matching-rm.js — Decisión del Resource Manager sobre una propuesta.
 *  - Rechazar exige un comentario (el PM lo recibe como respuesta).
 *  - Aprobar pide confirmación mostrando la carga resultante.
 * El servidor vuelve a validar ambas reglas.
 */
(function () {
  "use strict";
  document.addEventListener("submit", function (e) {
    var f = e.target;
    if (!f || !f.hasAttribute || !f.hasAttribute("data-tm-decision")) return;
    var boton = e.submitter, decision = boton ? boton.value : "";
    var motivo = f.querySelector("input[name=motivo]"), error = f.querySelector(".decision-error");

    if (decision === "rechazar") {
      if (!motivo.value.trim()) {
        e.preventDefault();
        motivo.classList.add("is-invalid");
        if (error) error.hidden = false;
        motivo.focus();
      }
      return;
    }
    if (decision === "aprobar" && window.SBAI && window.SBAI.modal && window.SBAI.modal.confirm) {
      e.preventDefault();
      window.SBAI.modal.confirm({
        title: "¿Aprobar y asignar a " + f.getAttribute("data-tm-decision") + "?",
        desc: "Se suma a " + f.getAttribute("data-tm-proyecto") + " desde hoy. Su carga pasaría de " + f.getAttribute("data-tm-carga") + ".",
        confirmLabel: "Aprobar y asignar",
        variant: "primary",
        onConfirm: function () {
          var h = document.createElement("input");
          h.type = "hidden"; h.name = "decision"; h.value = "aprobar";
          f.appendChild(h);
          HTMLFormElement.prototype.submit.call(f);
        }
      });
    }
  });
  document.addEventListener("input", function (e) {
    var t = e.target;
    if (t && t.name === "motivo" && t.classList.contains("is-invalid") && t.value.trim()) {
      t.classList.remove("is-invalid");
      var err = t.closest("form").querySelector(".decision-error");
      if (err) err.hidden = true;
    }
  });
})();
