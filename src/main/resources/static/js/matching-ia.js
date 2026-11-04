/**
 * matching-ia.js — AI Talent Matching con IA (RF05), jQuery/AJAX contra el
 * servicio REST /api/matching (MatchingApiController).
 *
 *  - [data-mia-analizar]: botón "Analizar con IA" de la vista del proyecto;
 *    reemplaza la lista de candidatos por el ranking con la explicación de la IA.
 *  - [data-mia-panel]: "Pregúntale a la IA" en AI Talent Matching (PM y RM);
 *    petición en lenguaje natural → filtros → ranking → explicación.
 *
 * El puntaje lo calcula Java con datos reales; la IA solo interpreta la
 * petición y explica. Proponer (PM) o asignar (RM) siguen siendo formularios
 * normales con confirmación: la decisión final la toma una persona.
 */
(function ($) {
  "use strict";
  if (!$ || !window.SBAI || !window.SBAI.api) return;

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="/img/icons.svg#' + name + '"></use></svg>';
  }
  function toast(msg, tipo) {
    if (window.SBAI.toast) window.SBAI.toast.show(esc(msg), { type: tipo || "default" });
  }

  /* ---------------- Tarjeta de candidato ---------------- */
  function barra(nombre, valor) {
    return '<div class="mia-crit"><span>' + nombre + '</span><div class="mia-crit-bar"><i style="width:' + valor + '%"></i></div><b>' + valor + "%</b></div>";
  }

  function tarjeta(c, a, opts) {
    var area = opts.area, esRm = area === "resource-manager";
    var nombre = esRm
        ? '<a href="/resource-manager/colaboradores/' + c.perfilId + '">' + esc(c.nombre) + "</a>"
        : "<b>" + esc(c.nombre) + "</b>";
    var skills = (c.habilidades || []).map(function (h) {
      return '<li class="' + (h.cumple ? "is-ok" : "") + '" title="Pide ' + esc(h.requerido) + " · tiene " + esc(h.tiene) + '">' + esc(h.nombre) + "</li>";
    }).join("");
    var volver = opts.volver;
    var form;
    if (esRm) {
      form = '<form method="post" class="mia-form" action="/resource-manager/proyectos/' + a.proyectoId + '/asignar"' +
          ' data-confirm-variant="primary" data-confirm-label="Asignar" data-confirm-title="¿Asignar a ' + esc(c.nombre) + '?"' +
          ' data-confirm-desc="Se suma a ' + esc(a.proyecto) + " con " + a.dedicacion + "% de dedicación. Su carga pasaría de " + c.cargaActual + "% a " + c.cargaProyectada + '%."' +
          ' data-carga-actual="' + c.cargaActual + '" data-proyecto="' + esc(a.proyecto) + '">' +
          '<input type="hidden" name="perfilId" value="' + c.perfilId + '"><input type="hidden" name="volver" value="' + esc(volver) + '">' +
          '<label><span>Dedicación</span><input type="number" class="input" name="cargaPorcentaje" min="1" max="100" value="' + a.dedicacion + '">%</label>' +
          '<button type="submit" class="btn btn-primary btn-sm">Asignar</button></form>';
    } else {
      form = '<form method="post" class="mia-form" action="/project-manager/proyectos/' + a.proyectoId + '/proponer">' +
          '<input type="hidden" name="perfilId" value="' + c.perfilId + '"><input type="hidden" name="volver" value="' + esc(volver) + '">' +
          '<label><span>Dedicación</span><input type="number" class="input" name="cargaPorcentaje" min="1" max="100" value="' + a.dedicacion + '">%</label>' +
          '<button type="submit" class="btn btn-secondary btn-sm">Proponer al RM</button></form>';
    }
    return '<li class="mia-card' + (c.recomendado ? " is-top" : "") + '" data-perfil="' + c.perfilId + '">' +
        '<div class="mia-card-top"><span class="avatar avatar-sm">' + esc(c.iniciales) + "</span>" +
        '<div class="mia-who">' + nombre + "<small>" + esc(c.cargo) + "</small></div>" +
        (c.recomendado ? '<span class="mia-top-tag">' + icon("icon-star", "icon-sm") + "Recomendado</span>" : "") +
        '<span class="mia-score" style="--p:' + c.score + '"><b>' + c.score + "</b></span></div>" +
        '<p class="mia-why' + (a.ia ? " is-ia" : "") + '">' + icon(a.ia ? "icon-sparkles" : "icon-info", "icon-sm") + "<span>" + esc(c.explicacion) + "</span></p>" +
        (skills ? '<ul class="mia-skills">' + skills + "</ul>" : "") +
        '<div class="mia-crits">' + barra("Habilidades", c.scoreHabilidades) + barra("Experiencia", c.scoreExperiencia) + barra("Disponibilidad", c.scoreDisponibilidad) + "</div>" +
        '<p class="mia-facts"><span>' + c.experienciaAnios + " año(s) de experiencia · " + c.disponibilidad + "% disponible</span>" +
        '<b>Carga ' + c.cargaActual + "% → " + c.cargaProyectada + "%</b></p>" +
        form + "</li>";
  }

  function lista(a, opts) {
    return a.candidatos.map(function (c) { return tarjeta(c, a, opts); }).join("");
  }

  function resumen(a) {
    return '<div class="mia-resumen' + (a.ia ? " is-ia" : "") + '">' + icon(a.ia ? "icon-sparkles" : "icon-info") +
        "<div><b>" + (a.ia ? "Análisis de la IA" : "Análisis del sistema") + "</b><p>" + esc(a.resumen) + "</p>" +
        (a.aviso ? '<small class="mia-aviso">' + esc(a.aviso) + "</small>" : "") + "</div></div>";
  }

  // Texto de confirmación de "Asignar" según la dedicación escrita
  $(document).on("input", ".mia-form[data-carga-actual] input[name=cargaPorcentaje]", function () {
    var $f = $(this).closest("form"), ded = parseInt(this.value, 10) || 0, actual = parseInt($f.attr("data-carga-actual"), 10) || 0;
    $f.attr("data-confirm-desc", "Se suma a " + $f.attr("data-proyecto") + " con " + ded + "% de dedicación. Su carga pasaría de " + actual + "% a " + (actual + ded) + "%.");
  });

  function cargando($donde, texto) {
    $donde.html('<div class="mia-loading"><span class="mia-spinner" aria-hidden="true"></span>' + esc(texto) + "</div>");
  }

  /* ---------------- Vista del proyecto: "Analizar con IA" ---------------- */
  $(document).on("click", "[data-mia-analizar]", function () {
    var $btn = $(this), $card = $btn.closest(".pd-sug"), $list = $card.find(".pd-sug-list");
    var opts = { area: $btn.data("area"), volver: $btn.data("volver") };
    var ded = parseInt($card.find(".pd-sug-form input[name=cargaPorcentaje]").first().val(), 10) || 50;
    $btn.prop("disabled", true).addClass("is-busy");
    $card.find(".mia-resumen").remove();
    var $estado = $('<div class="mia-estado"></div>').insertBefore($list);
    cargando($estado, "La IA está analizando a los candidatos…");
    window.SBAI.api.post("/api/matching/proyectos/" + $btn.data("proyecto") + "/analizar", { dedicacion: ded })
      .done(function (a) {
        $estado.replaceWith(resumen(a));
        if (a.candidatos.length) {
          $list.html(lista(a, opts)).addClass("is-ia").scrollTop(0);
        }
        $card.find(".pd-sug-sub").text(a.ia ? "Ranking del sistema con explicaciones de la IA" : "Ranking del sistema (la IA no respondió)");
        $btn.find("span").text("Volver a analizar");
      })
      .fail(function (xhr) { $estado.remove(); toast(window.SBAI.api.mensajeError(xhr), "error"); })
      .always(function () { $btn.prop("disabled", false).removeClass("is-busy"); });
  });

  /* ---------------- AI Talent Matching: "Pregúntale a la IA" ---------------- */
  $("[data-mia-panel]").each(function () {
    var $panel = $(this), $form = $panel.find("form.mia-ask"), $txt = $form.find("textarea"), $out = $panel.find(".mia-out");
    var opts = { area: $panel.data("area"), volver: "matching" };

    $panel.on("click", "[data-mia-ejemplo]", function () {
      $txt.val($(this).text()).trigger("focus");
    });
    $txt.on("keydown", function (e) {
      if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); $form.trigger("submit"); }
    });

    $form.on("submit", function (e) {
      e.preventDefault();
      var texto = $.trim($txt.val());
      if (texto.length < 4) { $txt.trigger("focus"); return; }
      var $btn = $form.find("button[type=submit]").prop("disabled", true);
      cargando($out, "Interpretando tu petición y calculando el ranking…");
      window.SBAI.api.post("/api/matching/consulta", { texto: texto })
        .done(function (a) {
          var i = a.interpretacion || {};
          var chips = '<span class="mia-chip is-proj">' + icon("icon-folder", "icon-sm") + esc(a.proyecto) + "</span>" +
              (i.habilidades && i.habilidades.length
                ? i.habilidades.map(function (h) { return '<span class="mia-chip">' + esc(h) + "</span>"; }).join("")
                : '<span class="mia-chip is-muted">Habilidades del proyecto</span>') +
              '<span class="mia-chip">' + (i.cantidad || 1) + (i.cantidad === 1 ? " persona" : " personas") + "</span>" +
              '<span class="mia-chip">' + a.dedicacion + "% de dedicación</span>" +
              '<span class="mia-chip is-src">' + (i.porIa ? icon("icon-sparkles", "icon-sm") + "Interpretado por la IA" : "Interpretado por palabras clave") + "</span>";
          var html = '<div class="mia-filtros"><small>Entendí:</small>' + chips +
              '<a class="mia-ver" href="/' + opts.area + "/proyectos/" + a.proyectoId + '.html">Ver proyecto ' + icon("icon-chevron-right", "icon-sm") + "</a></div>" +
              resumen(a);
          html += a.candidatos.length ? '<ol class="mia-grid">' + lista(a, opts) + "</ol>" : "";
          $out.html(html);
        })
        .fail(function (xhr) {
          $out.html('<div class="mia-resumen is-error">' + icon("icon-alert-circle") + "<div><b>No pude completar la búsqueda</b><p>" +
              esc(window.SBAI.api.mensajeError(xhr)) + "</p></div></div>");
        })
        .always(function () { $btn.prop("disabled", false); });
    });
  });
})(window.jQuery);
