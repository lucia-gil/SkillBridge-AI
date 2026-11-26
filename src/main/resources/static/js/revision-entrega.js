/**
 * revision-entrega.js — Modal "Revisar entrega" (PM y Administrador).
 *
 * Ya no se pone nota: se elige un estado de revisión y se deja un
 * comentario obligatorio (mínimo 10 caracteres), también cuando está
 * completa. Los chips de sugerencia solo rellenan el inicio del texto;
 * el revisor lo completa con lo que corresponda.
 *
 * Uso: SBAI.revisionEntrega.init({ base: "/project-manager", entregableId: 12 })
 * Botones: <button data-revisar="ID" data-nombre="..." data-estado="..." data-comentario="...">
 */
(function () {
  "use strict";

  var MIN = 10, MAX = 1000;

  var ESTADOS = [
    { valor: "completado", titulo: "Completado", icono: "icon-check-circle",
      ayuda: "Cumple con lo pedido. Queda cerrada.",
      sugerencias: ["Buen trabajo, cumple con lo pedido. ", "Bien hecho; para la próxima ten en cuenta ", "Entrega completa y ordenada. "] },
    { valor: "requiere_ajustes", titulo: "Requiere ajustes", icono: "icon-alert-triangle",
      ayuda: "Va bien, pero falta algo para llegar al 100%.",
      sugerencias: ["Vas bien, pero falta ", "Corrige lo siguiente y vuelve a enviarlo: ", "Completa la parte de "] },
    { valor: "rehacer", titulo: "Rehacer", icono: "icon-x",
      ayuda: "No corresponde a lo pedido o falta casi todo.",
      sugerencias: ["La entrega no corresponde a lo pedido: ", "Falta la mayor parte del entregable. Revisa ", "Vuelve a hacerlo siguiendo "] }
  ];

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function html(nombre, estadoActual, comentario) {
    var opciones = ESTADOS.map(function (e) {
      return '<label class="rev-opcion rev-' + e.valor + '">' +
        '<input type="radio" name="rev-estado" value="' + e.valor + '"' + (estadoActual === e.valor ? " checked" : "") + '>' +
        '<span class="rev-opcion-icono"><svg class="icon"><use href="/img/icons.svg#' + e.icono + '"></use></svg></span>' +
        '<span class="rev-opcion-texto"><strong>' + e.titulo + '</strong><small>' + e.ayuda + '</small></span>' +
        '</label>';
    }).join("");
    return '<div class="modal rev-modal" role="dialog" aria-modal="true" aria-labelledby="rev-titulo">' +
      '<div class="modal-header"><div><h2 id="rev-titulo">Revisar entrega</h2><p>' + esc(nombre) + '</p></div>' +
      '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
      '<div class="modal-body">' +
      '<div class="field"><span class="field-label">Estado de la revisión</span><div class="rev-opciones" role="radiogroup">' + opciones + '</div></div>' +
      '<div class="field"><label class="field-label" for="rev-comentario">Comentario para el colaborador <span class="rev-obligatorio">(obligatorio)</span></label>' +
      '<div class="rev-sugerencias" id="rev-sugerencias" hidden></div>' +
      '<textarea class="input rev-textarea" id="rev-comentario" rows="4" maxlength="' + MAX + '" placeholder="Qué está bien, qué falta o qué debe tener en cuenta…">' + esc(comentario) + '</textarea>' +
      '<div class="rev-pie"><span class="rev-error" id="rev-error" role="alert"></span><span class="rev-contador" id="rev-contador">0 / ' + MAX + '</span></div>' +
      '</div></div>' +
      '<div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
      '<button type="button" class="btn btn-primary" id="rev-confirm">Guardar revisión</button></div></div>';
  }

  function abrir(btn, cfg) {
    var actual = btn.dataset.estado;
    var esRevisado = ESTADOS.some(function (e) { return e.valor === actual; });
    var backdrop = SBAI.modal.open(html(btn.dataset.nombre, esRevisado ? actual : "", btn.dataset.comentario || ""));
    var area = backdrop.querySelector("#rev-comentario");
    var contador = backdrop.querySelector("#rev-contador");
    var error = backdrop.querySelector("#rev-error");
    var chips = backdrop.querySelector("#rev-sugerencias");

    function elegido() {
      var r = backdrop.querySelector('input[name="rev-estado"]:checked');
      return r ? r.value : "";
    }
    function contar() {
      var n = area.value.trim().length;
      contador.textContent = area.value.length + " / " + MAX;
      contador.classList.toggle("rev-contador-bajo", n > 0 && n < MIN);
      if (n >= MIN && error.textContent.indexOf("comentario") >= 0) error.textContent = "";
    }
    function pintarSugerencias() {
      var e = ESTADOS.filter(function (x) { return x.valor === elegido(); })[0];
      if (!e) { chips.hidden = true; chips.innerHTML = ""; return; }
      chips.hidden = false;
      chips.innerHTML = '<span class="rev-sugerencias-label">Sugerencias:</span>' + e.sugerencias.map(function (t) {
        return '<button type="button" class="chip-filter rev-chip" data-texto="' + esc(t) + '">' + esc(t.trim().replace(/[:,.]$/, "")) + '…</button>';
      }).join("");
      chips.querySelectorAll(".rev-chip").forEach(function (c) {
        c.addEventListener("click", function () {
          // Si el texto actual empieza con otra sugerencia, se reemplaza esa parte; si no, se antepone.
          var actual = area.value;
          ESTADOS.forEach(function (x) { x.sugerencias.forEach(function (t) { if (actual.indexOf(t) === 0) actual = actual.slice(t.length); }); });
          area.value = c.dataset.texto + actual;
          area.focus();
          area.setSelectionRange(area.value.length, area.value.length);
          contar();
        });
      });
    }

    backdrop.querySelectorAll('input[name="rev-estado"]').forEach(function (r) {
      r.addEventListener("change", function () {
        if (error.textContent.indexOf("estado") >= 0) error.textContent = "";
        pintarSugerencias();
      });
    });
    area.addEventListener("input", contar);
    contar();
    pintarSugerencias();

    backdrop.querySelector("#rev-confirm").addEventListener("click", function () {
      var estado = elegido();
      var texto = area.value.trim();
      if (!estado) { error.textContent = "Elige un estado para la revisión."; return; }
      if (texto.length < MIN) {
        error.textContent = "Escribe un comentario (mínimo " + MIN + " caracteres).";
        area.focus();
        return;
      }
      SBAI.submitForm(cfg.base + "/entregas/" + btn.dataset.revisar + "/revisar", {
        entregableId: cfg.entregableId,
        estado: estado,
        comentario: texto
      });
    });
  }

  function init(cfg) {
    document.querySelectorAll("[data-revisar]").forEach(function (btn) {
      btn.addEventListener("click", function () { abrir(btn, cfg); });
    });
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.revisionEntrega = { init: init };
})();
