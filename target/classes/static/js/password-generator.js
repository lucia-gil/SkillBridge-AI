/**
 * password-generator.js — Contraseña generativa. Cualquier botón
 * [data-generar-password] genera una clave segura (crypto.getRandomValues)
 * que cumple la política del backend (mayúscula, minúscula, número y
 * símbolo, 12+ caracteres), la escribe en los campos indicados y permite copiarla.
 *   data-target="#idCampo1,#idCampo2"   campos a rellenar (confirmación incluida)
 */
(function () {
  "use strict";
  var SETS = { u: "ABCDEFGHJKLMNPQRSTUVWXYZ", l: "abcdefghijkmnopqrstuvwxyz", n: "23456789", s: "!@#$%&*?-_+=" };
  function rnd(n) { var a = new Uint32Array(1); var lim = Math.floor(4294967296 / n) * n, x; do { crypto.getRandomValues(a); x = a[0]; } while (x >= lim); return x % n; }
  function pick(s) { return s.charAt(rnd(s.length)); }
  function generar(len) {
    len = Math.max(12, len || 16);
    var chars = [pick(SETS.u), pick(SETS.l), pick(SETS.n), pick(SETS.s)], all = SETS.u + SETS.l + SETS.n + SETS.s;
    while (chars.length < len) chars.push(pick(all));
    for (var i = chars.length - 1; i > 0; i--) { var j = rnd(i + 1); var t = chars[i]; chars[i] = chars[j]; chars[j] = t; }
    return chars.join("");
  }
  window.SBAI = window.SBAI || {}; window.SBAI.generarPassword = generar;

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-generar-password]"); if (!btn) return;
    e.preventDefault();
    var pwd = generar(parseInt(btn.getAttribute("data-length"), 10) || 16);
    (btn.getAttribute("data-target") || "").split(",").forEach(function (sel) {
      var el = sel.trim() && document.querySelector(sel.trim()); if (!el) return;
      el.value = pwd; el.type = "text";
      el.dispatchEvent(new Event("input", { bubbles: true }));
    });
    var out = btn.parentNode.querySelector(".pwd-generated");
    if (!out) { out = document.createElement("span"); out.className = "pwd-generated"; btn.parentNode.appendChild(out); }
    out.innerHTML = '<code></code><button type="button" class="btn btn-tertiary btn-sm" data-copiar-password>Copiar</button>';
    out.querySelector("code").textContent = pwd;
    out.dataset.pwd = pwd;
  });
  document.addEventListener("click", function (e) {
    var c = e.target.closest("[data-copiar-password]"); if (!c) return;
    var pwd = c.parentNode.dataset.pwd;
    (navigator.clipboard ? navigator.clipboard.writeText(pwd) : Promise.reject()).then(function () {
      c.textContent = "Copiada";
    }).catch(function () { window.prompt("Copia la contraseña:", pwd); });
    setTimeout(function () { c.textContent = "Copiar"; }, 1600);
  });
})();
