/**
 * battery-progress.js — Barras de progreso estilo batería: barra gruesa con
 * el porcentaje DENTRO. El texto es oscuro sobre el fondo vacío y blanco sobre
 * la parte llena (dos capas recortadas con clip-path), así siempre se lee.
 * Se aplica automáticamente a todo .progress-inline / .progress-bar existente
 * sin tocar las plantillas.
 */
(function () {
  "use strict";
  function pct(el) {
    var m = /width:\s*([\d.]+)%/.exec(el.getAttribute("style") || "");
    return m ? Math.max(0, Math.min(100, parseFloat(m[1]))) : 0;
  }
  function build(bar, texto) {
    if (bar.dataset.battery) return; bar.dataset.battery = "1";
    var fill = bar.querySelector(".progress-bar-fill"); if (!fill) return;
    var p = pct(fill), label = texto || (Math.round(p) + "%");
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
  document.addEventListener("DOMContentLoaded", function () {
    scan(document);
    new MutationObserver(function (m) { m.forEach(function (r) { r.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); }); }); })
      .observe(document.body, { childList: true, subtree: true });
  });
})();
