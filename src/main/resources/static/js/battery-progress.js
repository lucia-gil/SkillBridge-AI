/**
 * battery-progress.js — Barras de progreso estilo batería: barra gruesa con
 * el porcentaje DENTRO. El texto es oscuro sobre el fondo vacío y blanco sobre
 * la parte llena (dos capas recortadas con clip-path), así siempre se lee.
 * Se aplica automáticamente a todo .progress-inline / .progress-bar existente
 * sin tocar las plantillas.
 *
 * Color: todas las barras usan el mismo azul de marca. Solo las de carga
 * (data-escala="carga") que superan el 100% se pintan en mostaza.
 * data-escala="ranking" permite poner la etiqueta con data-etiqueta.
 */
(function () {
  "use strict";
  function pct(el) {
    var m = /width:\s*([\d.]+)%/.exec(el.getAttribute("style") || "");
    return m ? Math.max(0, Math.min(100, parseFloat(m[1]))) : 0;
  }
  var NIVELES = ["lv-azul", "lv-sobre"];
  function numero(t) { var m = /(-?[\d.]+)/.exec(t || ""); return m ? parseFloat(m[1]) : null; }
  // Un solo color estable para todas las barras; solo la carga que pasa
  // del 100% (sobreasignación) cambia a mostaza para llamar la atención.
  function nivel(escala, v) {
    return escala === "carga" && v > 100 ? "lv-sobre" : "lv-azul";
  }
  function colorear(bar, fill, p, texto) {
    var host = bar.closest("[data-escala]");
    var escala = host ? host.getAttribute("data-escala") : "avance";
    if (escala === "ninguna") return;
    // La carga puede pasar de 100: se usa el número de la etiqueta, no el ancho.
    var v = escala === "carga" && numero(texto) != null ? numero(texto) : p;
    NIVELES.concat(["green", "amber", "red"]).forEach(function (c) { fill.classList.remove(c); });
    fill.classList.add(nivel(escala, v));
  }
  function build(bar, texto) {
    if (bar.dataset.battery) return; bar.dataset.battery = "1";
    var fill = bar.querySelector(".progress-bar-fill"); if (!fill) return;
    var p = pct(fill);
    var host = bar.closest("[data-etiqueta]");
    var label = (host && host.getAttribute("data-etiqueta")) || texto || (Math.round(p) + "%");
    colorear(bar, fill, p, texto || (Math.round(p) + "%"));
    bar.classList.add("battery");
    bar.style.setProperty("--pb", p + "%");
    bar.style.setProperty("--pb-rest", (100 - p) + "%");
    bar.setAttribute("role", "progressbar"); bar.setAttribute("aria-valuemin", "0"); bar.setAttribute("aria-valuemax", "100"); bar.setAttribute("aria-valuenow", Math.round(p));
    var base = document.createElement("span"); base.className = "battery-label battery-label-base"; base.textContent = label;
    var top = document.createElement("span"); top.className = "battery-label battery-label-top"; top.textContent = label;
    bar.appendChild(base); bar.appendChild(top);
  }
  function scan(root) {
    (root || document).querySelectorAll(".progress-inline").forEach(function (w) {
      var bar = w.querySelector(".progress-bar"), val = w.querySelector(".progress-value");
      if (!bar) return;
      build(bar, val ? val.textContent.trim() : null);
      if (val) val.remove();   // el % ahora vive dentro de la barra
    });
    (root || document).querySelectorAll(".progress-bar:not([data-battery])").forEach(function (b) { build(b, null); });
  }
  window.SBAI = window.SBAI || {};
  window.SBAI.escalaBarra = nivel;
  document.addEventListener("DOMContentLoaded", function () {
    scan(document);
    new MutationObserver(function (m) { m.forEach(function (r) { r.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); }); }); })
      .observe(document.body, { childList: true, subtree: true });
  });
})();
