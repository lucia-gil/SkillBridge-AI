/**
 * colaborador-perfil.js — Perfil profesional del colaborador
 * (resource-manager/colaborador-detalle.html).
 *
 *  - logos de tecnologías (tech-icons.js);
 *  - pestañas de sección: marca la sección visible al desplazarse;
 *  - descripciones de proyecto largas: 3 líneas + "Ver más";
 *  - filtro "Por validar" en Habilidades;
 *  - copiar el correo;
 *  - confirmar "Validar" (avisa si no hay constancia adjunta).
 */
(function () {
  "use strict";

  function logos() {
    if (!(window.SBAI && window.SBAI.techIcons)) return;
    document.querySelectorAll(".cp [data-tech-logo]").forEach(function (el) {
      var html = window.SBAI.techIcons.logo(el.getAttribute("data-tech-logo"));
      if (html) el.innerHTML = html;
    });
  }

  function pestanas() {
    var links = Array.from(document.querySelectorAll(".cp-tabs a"));
    if (!links.length || !("IntersectionObserver" in window)) return;
    var secciones = links.map(function (a) { return document.querySelector(a.getAttribute("href")); });
    function marcar(id) {
      links.forEach(function (a) {
        var activo = a.getAttribute("href") === "#" + id;
        a.classList.toggle("is-active", activo);
        if (activo) a.setAttribute("aria-current", "true"); else a.removeAttribute("aria-current");
      });
    }
    var visibles = {};
    var obs = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) { visibles[e.target.id] = e.isIntersecting; });
      var primera = secciones.find(function (s) { return s && visibles[s.id]; });
      if (primera) marcar(primera.id);
    }, { rootMargin: "-90px 0px -55% 0px" });
    secciones.forEach(function (s) { if (s) obs.observe(s); });
    links.forEach(function (a) {
      a.addEventListener("click", function () { marcar(a.getAttribute("href").slice(1)); });
    });
  }

  function recortes() {
    document.querySelectorAll("[data-cp-clamp]").forEach(function (p) {
      p.classList.add("is-clamped");
      if (p.scrollHeight <= p.clientHeight + 2) { p.classList.remove("is-clamped"); return; }
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cp-more";
      btn.textContent = "Ver más";
      btn.setAttribute("aria-expanded", "false");
      btn.addEventListener("click", function () {
        var abierto = p.classList.toggle("is-clamped") === false;
        btn.textContent = abierto ? "Ver menos" : "Ver más";
        btn.setAttribute("aria-expanded", String(abierto));
      });
      p.insertAdjacentElement("afterend", btn);
    });
  }

  function filtroHabilidades() {
    var botones = Array.from(document.querySelectorAll("[data-cp-filtro]"));
    if (!botones.length) return;
    botones.forEach(function (b) {
      b.addEventListener("click", function () {
        var soloPendientes = b.dataset.cpFiltro === "pendientes";
        botones.forEach(function (x) {
          x.classList.toggle("is-active", x === b);
          x.setAttribute("aria-pressed", String(x === b));
        });
        document.querySelectorAll(".cp-group").forEach(function (g) {
          var hay = false;
          g.querySelectorAll(".cp-skill").forEach(function (s) {
            var ver = !soloPendientes || s.classList.contains("is-pending");
            s.hidden = !ver;
            if (ver) hay = true;
          });
          g.hidden = !hay;
        });
      });
    });
  }

  function copiar() {
    document.querySelectorAll("[data-copiar]").forEach(function (b) {
      b.addEventListener("click", function () {
        var texto = b.getAttribute("data-copiar");
        var ok = function () {
          b.classList.add("is-done");
          if (window.SBAI && SBAI.toast && SBAI.toast.show) SBAI.toast.show("Correo copiado", { type: "success" });
          setTimeout(function () { b.classList.remove("is-done"); }, 1600);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(texto).then(ok, function () {});
        }
      });
    });
  }

  function validar() {
    var perfilId = window.CP_PERFIL_ID;
    function esc(t) {
      return String(t == null ? "" : t).replace(/[&<>"']/g, function (c) {
        return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
      });
    }
    function urlValidar(btn) {
      return "/resource-manager/colaboradores/" + perfilId + "/habilidades/" + btn.dataset.validarHabilidad + "/validar";
    }

    // Resumen previo con IA de la constancia (se pide al servidor; si ya se
    // analizó y no cambió, responde al instante con el guardado).
    var NIVELES = ["Básico", "Intermedio", "Avanzado", "Experto"];
    var VEREDICTOS = {
      respalda: ["Respalda la habilidad", "badge-green"],
      parcial: ["La respalda en parte", "badge-amber"],
      no_relacionada: ["No se relaciona con la habilidad", "badge-red"],
      no_legible: ["No se pudo leer", "badge-neutral"]
    };
    function pintarAnalisis(caja, a) {
      var v = VEREDICTOS[a.veredicto] || VEREDICTOS.parcial;
      var datos = [["Emisor", a.emisor], ["Curso", a.curso], ["A nombre de", a.titular],
                   ["Fecha", a.fecha], ["Duración", a.duracion], ["Nota", a.nota]]
        .filter(function (d) { return d[1]; })
        .map(function (d) { return "<div><dt>" + d[0] + "</dt><dd>" + esc(d[1]) + "</dd></div>"; }).join("");
      var alertas = (a.alertas || []).map(function (x) { return "<li>⚠ " + esc(x) + "</li>"; }).join("");
      caja.innerHTML =
        '<div class="va-head"><span class="va-ia"><svg class="icon"><use href="/img/icons.svg#icon-sparkles"></use></svg>Resumen previo con IA</span>' +
        '<span class="badge ' + v[1] + '">' + v[0] + "</span></div>" +
        '<p class="va-resumen">' + esc(a.resumen || "Sin resumen.") + "</p>" +
        (datos ? '<dl class="va-datos">' + datos + "</dl>" : "") +
        (alertas ? '<ul class="va-alertas">' + alertas + "</ul>" : "");
    }

    document.querySelectorAll("[data-validar-habilidad]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var nombre = btn.dataset.habilidadNombre || "esta habilidad";
        var sinConstancia = btn.dataset.sinConstancia === "true";
        if (sinConstancia) {
          SBAI.modal.confirm({
            title: "¿Validar \"" + nombre + "\"?",
            desc: "No adjuntó una constancia: la validación quedará solo con tu criterio. " +
                  "Quedará marcada como verificada: se usará en Talent Matching y se mostrará en su perfil.",
            confirmLabel: "Validar",
            onConfirm: function () { SBAI.submitForm(urlValidar(btn), {}); }
          });
          return;
        }

        var pdf = btn.dataset.certPdf, link = btn.dataset.certLink;
        var declarado = btn.dataset.habilidadNivel || "Básico";
        if (NIVELES.indexOf(declarado) === -1) declarado = "Básico";
        var enlaces = (pdf ? '<a class="btn btn-ghost btn-sm" href="' + esc(pdf) + '" target="_blank" rel="noopener">' +
                        '<svg class="icon icon-sm"><use href="/img/icons.svg#icon-file-text"></use></svg>Ver PDF</a>' : "") +
                      (link ? '<a class="btn btn-ghost btn-sm" href="' + esc(link) + '" target="_blank" rel="noopener noreferrer">' +
                        '<svg class="icon icon-sm"><use href="/img/icons.svg#icon-external-link"></use></svg>Abrir enlace</a>' : "");
        var niveles = NIVELES.map(function (n, i) {
          return '<button type="button" class="va-nivel" data-nivel="' + n + '" aria-pressed="' + (n === declarado) + '">' +
                 '<span class="va-barras">' + [0, 1, 2, 3].map(function (k) { return '<i class="' + (k <= i ? "on" : "") + '"></i>'; }).join("") +
                 "</span>" + n + '<small class="va-tag-dec"' + (n === declarado ? "" : " hidden") + ">Declarado</small>" +
                 '<small class="va-tag-ia" hidden>Sugerido IA</small></button>';
        }).join("");
        var html = '<div class="modal va-modal" role="dialog" aria-modal="true"><div class="modal-header"><div>' +
          '<h2>Validar "' + esc(nombre) + '"</h2><p>Nivel declarado: ' + esc(declarado) + "</p></div>" +
          '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button></div>' +
          '<div class="modal-body"><div class="va-box" id="va-caja"><div class="va-cargando"><span class="va-spin"></span>' +
          "La IA está revisando la constancia… (puede tardar unos segundos)</div></div>" +
          (enlaces ? '<div class="va-links">' + enlaces + "</div>" : "") +
          '<div class="va-nivel-box"><div class="va-nivel-tit">Nivel con el que se valida</div>' +
          '<div class="va-niveles" role="group" aria-label="Nivel">' + niveles + "</div>" +
          '<p class="va-nivel-msg" id="va-nivel-msg"></p></div>' +
          '<div class="va-riesgo" id="va-riesgo" hidden></div>' +
          '<p class="va-nota">Es una ayuda: la IA puede equivocarse y la decisión final es tuya. Al validar, la habilidad se usará en Talent Matching ' +
          "y su resumen profesional se actualizará automáticamente.</p></div>" +
          '<div class="modal-footer"><button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
          '<button type="button" class="btn btn-primary" id="va-validar">Validar habilidad</button></div></div>';
        var backdrop = SBAI.modal.open(html);
        var caja = backdrop.querySelector("#va-caja");
        var boton = backdrop.querySelector("#va-validar");
        var msg = backdrop.querySelector("#va-nivel-msg");
        var riesgo = backdrop.querySelector("#va-riesgo");
        var elegido = declarado, sugerido = null;

        function pintarNivel() {
          backdrop.querySelectorAll(".va-nivel").forEach(function (b) {
            b.setAttribute("aria-pressed", String(b.dataset.nivel === elegido));
          });
          var dif = NIVELES.indexOf(elegido) - NIVELES.indexOf(declarado);
          msg.className = "va-nivel-msg" + (dif > 0 ? " is-up" : dif < 0 ? " is-down" : "");
          msg.textContent = dif === 0 ? "Se valida con el nivel que declaró el colaborador."
            : "Se validará como " + elegido + " (declaró " + declarado + "). " + (dif > 0 ? "Sube" : "Baja") +
              " " + Math.abs(dif) + " nivel" + (Math.abs(dif) > 1 ? "es" : "") + ".";
        }
        backdrop.querySelectorAll(".va-nivel").forEach(function (b) {
          b.addEventListener("click", function () { elegido = b.dataset.nivel; pintarNivel(); });
        });
        pintarNivel();

        // Si la IA dice que la constancia no respalda la habilidad (o no la pudo leer),
        // se puede validar igual, pero el RM tiene que confirmar que la revisó él mismo.
        function exigirConfirmacion(tipo) {
          var texto = tipo === "no_relacionada"
            ? "<b>La IA indica que esta constancia no respalda la habilidad.</b> Puedes no validarla y pedirle al colaborador otra constancia. " +
              "Si la revisaste y sí la respalda (la IA puede equivocarse), confírmalo para validar."
            : "<b>La IA no pudo leer la constancia.</b> Ábrela con los botones de arriba; si la revisaste y sí respalda la habilidad, confírmalo para validar.";
          riesgo.innerHTML = '<p>' + texto + '</p><label class="va-check"><input type="checkbox" id="va-ok"> ' +
            "Revisé la constancia yo mismo y sí respalda la habilidad</label>";
          riesgo.hidden = false;
          riesgo.classList.toggle("is-gris", tipo !== "no_relacionada");
          boton.disabled = true;
          boton.textContent = "Validar de todas formas";
          riesgo.querySelector("#va-ok").addEventListener("change", function () { boton.disabled = !this.checked; });
        }

        boton.addEventListener("click", function () {
          this.disabled = true;
          SBAI.submitForm(urlValidar(btn), { nivel: elegido });
        });

        fetch("/resource-manager/colaboradores/" + perfilId + "/habilidades/" + btn.dataset.validarHabilidad + "/analisis-ia",
              { method: "POST", headers: { "X-Requested-With": "XMLHttpRequest" } })
          .then(function (r) {
            var tipo = r.headers.get("Content-Type") || "";
            if (!r.ok || tipo.indexOf("application/json") === -1) throw new Error("http");
            return r.json();
          })
          .then(function (d) {
            if (d.ok && d.analisis) {
              pintarAnalisis(caja, d.analisis);
              var v = d.analisis.veredicto;
              if (v === "no_relacionada" || v === "no_legible") exigirConfirmacion(v);
              sugerido = NIVELES.indexOf(d.analisis.nivelSugerido) > -1 ? d.analisis.nivelSugerido : null;
              if (sugerido) {
                var tag = backdrop.querySelector('.va-nivel[data-nivel="' + sugerido + '"] .va-tag-ia');
                if (tag) tag.hidden = false;
                var nota = document.createElement("div");
                nota.className = "va-sugerido";
                nota.innerHTML = '<svg class="icon"><use href="/img/icons.svg#icon-sparkles"></use></svg><span>La IA sugiere <b>' +
                  esc(sugerido) + "</b>" + (d.analisis.motivoNivel ? ": " + esc(d.analisis.motivoNivel) : ".") + "</span>" +
                  (sugerido !== elegido ? '<button type="button" class="btn btn-ghost btn-sm">Usar sugerido</button>' : "");
                var usar = nota.querySelector("button");
                if (usar) usar.addEventListener("click", function () { elegido = sugerido; pintarNivel(); usar.remove(); });
                msg.parentNode.insertBefore(nota, msg);
              }
            } else {
              caja.innerHTML = '<p class="va-resumen">' + esc(d.error || "No se pudo revisar la constancia.") +
                               " Puedes abrirla con los botones de abajo.</p>";
            }
          })
          .catch(function () {
            caja.innerHTML = '<p class="va-resumen">No se pudo conectar con la IA. Revisa la constancia con los botones de abajo.</p>';
          });
      });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    logos();
    pestanas();
    recortes();
    filtroHabilidades();
    copiar();
    validar();
  });
})();
