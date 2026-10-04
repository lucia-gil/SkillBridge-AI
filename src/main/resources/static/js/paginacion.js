/**
 * paginacion.js — Paginación con números y elipsis (RG-226 / RG-246).
 *
 * Cada tabla del sistema ya tiene su propia paginación en el cliente con
 * botones ‹ › y una etiqueta "Página N de M". En vez de reescribir la lógica
 * de cada vista, este script se monta encima de cualquier `.pagination`:
 * lee la etiqueta, dibuja los números (1 … 4 5 6 … 12) entre ‹ y ›, y al
 * hacer clic en un número usa los mismos botones ‹ › de la vista hasta
 * llegar a esa página. Así los filtros y contadores de cada pantalla siguen
 * funcionando exactamente igual.
 */
(function () {
  "use strict";
  var RE = /P[aá]gina\s+(\d+)\s+de\s+(\d+)/i;

  function leer(label) {
    var m = RE.exec(label.textContent || "");
    return m ? { actual: +m[1], total: +m[2] } : null;
  }

  // 1 … (actual-1) actual (actual+1) … total, siempre con 1 y total visibles.
  function numeros(actual, total) {
    if (total <= 7) return Array.from({ length: total }, function (_, i) { return i + 1; });
    var set = [1, total, actual, actual - 1, actual + 1];
    if (actual <= 3) set.push(2, 3, 4);
    if (actual >= total - 2) set.push(total - 1, total - 2, total - 3);
    var lista = set.filter(function (n, i, a) { return n >= 1 && n <= total && a.indexOf(n) === i; }).sort(function (a, b) { return a - b; });
    var out = [];
    lista.forEach(function (n, i) {
      if (i && n - lista[i - 1] > 1) out.push("…");
      out.push(n);
    });
    return out;
  }

  function montar(pag) {
    if (pag.dataset.numerada) return;
    var botones = pag.querySelectorAll(".page-btn");
    var label = pag.querySelector('[id$="page-label"]');
    if (botones.length < 2 || !label) return;
    var prev = botones[0], next = botones[botones.length - 1];
    if (!leer(label)) return;
    pag.dataset.numerada = "1";

    var cont = document.createElement("span");
    cont.className = "page-numbers";
    pag.insertBefore(cont, next);
    label.classList.add("page-label-sr");
    var salto = pag.querySelector('input[type="number"]');

    function irA(n) {
      var guard = 0, est = leer(label);
      while (est && est.actual !== n && guard++ < 500) {
        var btn = est.actual < n ? next : prev;
        if (btn.disabled) break;
        btn.click();
        est = leer(label);
      }
    }

    function pintar() {
      var est = leer(label);
      if (!est) return;
      cont.innerHTML = "";
      numeros(est.actual, est.total).forEach(function (n) {
        if (n === "…") {
          var e = document.createElement("span");
          e.className = "page-ellipsis"; e.textContent = "…"; e.setAttribute("aria-hidden", "true");
          cont.appendChild(e);
          return;
        }
        var b = document.createElement("button");
        b.type = "button";
        b.className = "page-btn page-num" + (n === est.actual ? " active" : "");
        b.textContent = n;
        b.setAttribute("aria-label", "Ir a la página " + n);
        if (n === est.actual) b.setAttribute("aria-current", "page");
        b.addEventListener("click", function () { irA(n); });
        cont.appendChild(b);
      });
      if (salto) salto.hidden = est.total <= 7;
    }

    new MutationObserver(pintar).observe(label, { childList: true, characterData: true, subtree: true });
    pintar();
  }

  function init() { document.querySelectorAll(".pagination").forEach(montar); }
  window.SBAI = window.SBAI || {};
  window.SBAI.paginacion = { init: init };
  // Después de que cada vista haya armado su propia paginación.
  if (document.readyState === "complete") init();
  else window.addEventListener("load", init);
})();
