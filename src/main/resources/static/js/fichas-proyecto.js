/**
 * fichas-proyecto.js — Botón "Generar resúmenes IA" de las fichas de
 * proyectos (reportes PM y RM). Pide los resúmenes con los mismos filtros de
 * la página y los escribe en cada ficha (como texto, nunca como HTML).
 */
(function () {
  "use strict";
  document.querySelectorAll("[data-fp]").forEach(function (panel) {
    var btn = panel.querySelector("[data-fp-generar]");
    var url = panel.getAttribute("data-fp-ia");
    if (!btn || !url || !window.SBAI || !window.SBAI.api) return;
    btn.addEventListener("click", function () {
      var etiqueta = btn.querySelector("span");
      var original = etiqueta.textContent;
      btn.disabled = true;
      etiqueta.textContent = "Generando resúmenes…";
      panel.classList.add("fp-ia-loading");
      window.SBAI.api.post(url)
        .done(function (r) {
          var n = 0;
          Object.keys(r.resumenes || {}).forEach(function (id) {
            var card = panel.querySelector('[data-fp-id="' + id.replace(/[^0-9]/g, "") + '"]');
            if (!card) return;
            var caja = card.querySelector("[data-fp-ia-texto]");
            caja.querySelector("p").textContent = r.resumenes[id];
            caja.hidden = false;
            n++;
          });
          if (window.SBAI.toast) {
            window.SBAI.toast.show(n ? "Resúmenes IA listos para " + n + " proyecto(s)." : "La IA no respondió. Intenta de nuevo en un momento.",
                { type: n ? "success" : "error" });
          }
          etiqueta.textContent = n ? "Volver a generar" : original;
        })
        .fail(function (xhr) {
          if (window.SBAI.toast) window.SBAI.toast.show(window.SBAI.api.mensajeError ? window.SBAI.api.mensajeError(xhr) : "No se pudo generar.", { type: "error" });
          etiqueta.textContent = original;
        })
        .always(function () { btn.disabled = false; panel.classList.remove("fp-ia-loading"); });
    });
  });

  // "Incluir resumen IA": agrega ia=false a los enlaces de exportación cuando se desmarca.
  var sw = document.getElementById("export-ia");
  if (sw) sw.addEventListener("change", function () {
    document.querySelectorAll("[data-export]").forEach(function (a) {
      var u = a.getAttribute("href").replace(/([?&])ia=false&?/, "$1").replace(/[?&]$/, "");
      if (!sw.checked) u += (u.indexOf("?") < 0 ? "?" : "&") + "ia=false";
      a.setAttribute("href", u);
    });
  });
})();
