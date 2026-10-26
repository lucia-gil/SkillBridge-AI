/**
 * habilidades-catalogo.js — Catálogo de habilidades (administrador/habilidades.html).
 *
 *  - Filtros: búsqueda, demanda y categoría (la lista de categorías de la
 *    izquierda funciona como filtro), con paginación de 6 filas. Escribe la
 *    etiqueta "Página N de M" que paginacion.js convierte en números.
 *  - Modales para agregar/editar habilidad y categoría: vista previa en vivo,
 *    selector de categoría por tarjetas, validación en línea (vacío,
 *    repetido, largo máximo) y aviso de impacto antes de guardar.
 *  - Los envíos siguen siendo los mismos POST de siempre al controlador
 *    (HabilidadesController); csrf.js agrega el token al formulario.
 *
 * Datos de entrada (inline en la plantilla): CATEGORIAS (id, nombre,
 * descripcion), SK_EXITO / SK_ERROR (avisos del último POST).
 */
(function () {
  "use strict";

  var PAGE_SIZE = 6;
  var MAX_NOMBRE_HABILIDAD = 80;  // habilidades.nombre VARCHAR(80)
  var MAX_NOMBRE_CATEGORIA = 60;  // categorias_habilidad.nombre VARCHAR(60)
  var MAX_DESCRIPCION = 255;      // categorias_habilidad.descripcion VARCHAR(255)

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  function icon(name, cls) {
    return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="/img/icons.svg#' + name + '"></use></svg>';
  }
  function normalize(v) {
    return (v || "").toString().normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();
  }
  function plural(n, uno, varios) { return n + " " + (n === 1 ? uno : varios); }

  /** "Spring Boot" → "SB", "Docker" → "DO", "C#" → "C#". */
  function iniciales(nombre) {
    var palabras = (nombre || "").trim().split(/\s+/).filter(Boolean);
    if (!palabras.length) return "?";
    if (palabras.length > 1) return (palabras[0][0] + palabras[1][0]).toUpperCase();
    return palabras[0].slice(0, 2).toUpperCase();
  }

  function submitForm(action, fields) {
    var form = document.createElement("form");
    form.method = "POST";
    form.action = action;
    Object.keys(fields).forEach(function (k) {
      var input = document.createElement("input");
      input.type = "hidden"; input.name = k; input.value = fields[k];
      form.appendChild(input);
    });
    document.body.appendChild(form);
    form.submit();
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (window.SBAI && SBAI.dropdown) SBAI.dropdown.init(document);
    document.body.addEventListener("click", function (e) {
      if (e.target.closest('[data-action="logout"]')) {
        (window.SBAI && window.SBAI.logout) ? window.SBAI.logout() : window.location.assign("/auth/login.html");
      }
    });
    if (window.SK_EXITO) SBAI.toast.show(window.SK_EXITO, { type: "success" });
    if (window.SK_ERROR) SBAI.toast.show(window.SK_ERROR, { type: "error" });

    /* ───────────── Datos ───────────── */
    var catItems = Array.from(document.querySelectorAll(".sk-cat[data-cat-id]"));
    var categorias = {};   // id → { id, nombre, descripcion, tono, cantidad }
    (window.CATEGORIAS || []).forEach(function (c) {
      categorias[String(c.id)] = { id: c.id, nombre: c.nombre, descripcion: c.descripcion || "", tono: String(Number(c.id) % 8), cantidad: 0 };
    });
    catItems.forEach(function (li) {
      var c = categorias[li.dataset.catId];
      if (c) c.cantidad = Number(li.dataset.cantidad) || 0;
    });
    var listaCategorias = Object.keys(categorias).map(function (k) { return categorias[k]; })
      .sort(function (a, b) { return a.nombre.localeCompare(b.nombre, "es"); });

    var rows = Array.from(document.querySelectorAll("tr[data-skill-row]"));
    var habilidades = {};  // id → datos de la fila
    rows.forEach(function (tr) {
      habilidades[tr.dataset.id] = {
        id: tr.dataset.id, nombre: tr.dataset.nombre, catId: tr.dataset.catId,
        personas: Number(tr.dataset.personas) || 0, proyectos: Number(tr.dataset.proyectos) || 0
      };
      var mono = tr.querySelector(".sk-mono");
      if (mono) mono.textContent = iniciales(tr.dataset.nombre);
    });

    // Barra de proporción de cada categoría (respecto de la más grande).
    var max = Math.max.apply(null, catItems.map(function (li) { return Number(li.dataset.cantidad) || 0; }).concat([1]));
    catItems.forEach(function (li) {
      if (!li.dataset.catId) return;
      var bar = li.querySelector(".sk-cat-bar i");
      if (bar) requestAnimationFrame(function () { bar.style.width = Math.round((Number(li.dataset.cantidad) || 0) * 100 / max) + "%"; });
    });

    /* ───────────── Filtros + paginación ───────────── */
    var search = document.getElementById("skills-search");
    var demanda = document.getElementById("skills-demanda");
    var emptyPanel = document.getElementById("skills-empty");
    var tableWrap = document.querySelector(".sk-table-wrap");
    var visibleCount = document.getElementById("skills-visible-count");
    var filteredCount = document.getElementById("skills-filtered-count");
    var pageLabel = document.getElementById("skills-page-label");
    var prev = document.getElementById("skills-prev");
    var next = document.getElementById("skills-next");
    var scopeLabel = document.getElementById("sk-scope-label");
    var scopeClear = document.getElementById("sk-scope-clear");
    var state = { q: "", demanda: "todos", catId: "", page: 1 };

    function matches(tr) {
      if (state.q && normalize(tr.dataset.nombre).indexOf(state.q) === -1) return false;
      if (state.demanda !== "todos" && tr.dataset.demanda !== state.demanda) return false;
      if (state.catId && tr.dataset.catId !== state.catId) return false;
      return true;
    }

    function pintarAlcance() {
      var c = state.catId ? categorias[state.catId] : null;
      if (c) {
        scopeLabel.innerHTML = 'Habilidades en <span class="sk-tag" data-tono="' + c.tono + '">' + esc(c.nombre) + "</span>";
      } else {
        scopeLabel.textContent = "Habilidades";
      }
      scopeClear.hidden = !c;
      catItems.forEach(function (li) {
        var activo = li.dataset.catId === state.catId;
        li.classList.toggle("is-active", activo);
        li.querySelector(".sk-cat-select").setAttribute("aria-pressed", activo ? "true" : "false");
      });
    }

    function render() {
      var matched = rows.filter(matches);
      var pages = Math.max(1, Math.ceil(matched.length / PAGE_SIZE));
      state.page = Math.min(state.page, pages);
      var visibles = matched.slice((state.page - 1) * PAGE_SIZE, state.page * PAGE_SIZE);
      rows.forEach(function (tr) { tr.hidden = true; });
      visibles.forEach(function (tr) { tr.hidden = false; });
      var vacio = matched.length === 0;
      emptyPanel.hidden = !vacio;
      tableWrap.hidden = vacio && rows.length > 0;
      visibleCount.textContent = visibles.length;
      filteredCount.textContent = matched.length;
      pageLabel.textContent = "Página " + state.page + " de " + pages;
      prev.disabled = state.page === 1;
      next.disabled = state.page === pages;
      pintarAlcance();
    }

    function setCategoria(id) {
      state.catId = id;
      state.page = 1;
      render();
    }

    search.addEventListener("input", function () { state.q = normalize(search.value); state.page = 1; render(); });
    demanda.addEventListener("change", function () { state.demanda = demanda.value; state.page = 1; render(); });
    prev.addEventListener("click", function () { if (state.page > 1) { state.page--; render(); } });
    next.addEventListener("click", function () { state.page++; render(); });
    scopeClear.addEventListener("click", function () { setCategoria(""); });
    document.getElementById("skills-reset").addEventListener("click", function () {
      search.value = ""; demanda.value = "todos";
      state.q = ""; state.demanda = "todos"; setCategoria("");
      search.focus();
    });
    catItems.forEach(function (li) {
      li.querySelector(".sk-cat-select").addEventListener("click", function () {
        // Volver a pulsar la categoría activa la quita (muestra todas).
        setCategoria(state.catId === li.dataset.catId ? "" : li.dataset.catId);
      });
    });

    var buscar = new URLSearchParams(window.location.search).get("buscar");
    if (buscar) { search.value = buscar; state.q = normalize(buscar); }
    render();

    /* ───────────── Modales ───────────── */
    function headerHtml(titulo, subtitulo, iconName, tono) {
      return '<div class="modal-header"><div class="sk-modal-title">' +
        '<span class="sk-modal-badge"' + (tono != null ? ' data-tono="' + tono + '"' : "") + ">" + icon(iconName) + "</span>" +
        "<div><h2>" + esc(titulo) + "</h2><p>" + esc(subtitulo) + "</p></div></div>" +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icon("icon-x") + "</button></div>";
    }

    function footerHtml(confirmLabel) {
      return '<div class="modal-footer">' +
        '<button type="button" class="btn btn-secondary" data-modal-close>Cancelar</button>' +
        '<button type="button" class="btn btn-primary" data-sk-confirm disabled>' + esc(confirmLabel) + "</button></div>";
    }

    /**
     * Valida un nombre: vacío, largo máximo y repetido (sin distinguir
     * mayúsculas ni tildes, igual que el servidor con IgnoreCase).
     * Devuelve el mensaje de error o "" si es válido.
     */
    function validarNombre(valor, existentes, maxLen, propio, tipo) {
      var limpio = valor.trim();
      if (!limpio) return "Escribe un nombre para la " + tipo + ".";
      if (limpio.length > maxLen) return "Máximo " + maxLen + " caracteres.";
      var n = normalize(limpio);
      var repetido = existentes.some(function (e) { return normalize(e) === n && normalize(e) !== normalize(propio || ""); });
      if (repetido) return "Ya existe una " + tipo + " con ese nombre.";
      return "";
    }

    function enlazarCampo(campo, input, maxLen, mensajeBase) {
      var count = campo.querySelector(".sk-field-count");
      var msg = campo.querySelector(".sk-field-msg");
      return function (error, mostrarError) {
        var len = input.value.trim().length;
        if (count) {
          count.textContent = len + "/" + maxLen;
          count.classList.toggle("is-near", len > maxLen * 0.85);
        }
        var visible = !!(error && mostrarError);
        campo.classList.toggle("is-invalid", visible);
        input.setAttribute("aria-invalid", visible ? "true" : "false");
        msg.textContent = visible ? error : mensajeBase;
      };
    }

    /* Habilidad: agregar (sin id) o editar (con id). */
    function abrirModalHabilidad(id) {
      var h = id ? habilidades[id] : null;
      var catInicial = h ? h.catId : (state.catId || (listaCategorias[0] && String(listaCategorias[0].id)) || "");
      if (!listaCategorias.length) {
        SBAI.toast.show("Primero crea una categoría para poder agregar habilidades.", { type: "error" });
        return;
      }
      var tonoInicial = categorias[catInicial] ? categorias[catInicial].tono : null;

      var opciones = listaCategorias.map(function (c) {
        var idOpt = "sk-opt-" + c.id;
        return '<div class="sk-opt" data-tono="' + c.tono + '">' +
          '<input type="radio" name="sk-cat" id="' + idOpt + '" value="' + c.id + '"' + (String(c.id) === String(catInicial) ? " checked" : "") + ">" +
          '<label for="' + idOpt + '"><span class="sk-opt-dot"></span><span class="sk-opt-name">' + esc(c.nombre) + "</span>" +
          '<span class="sk-opt-count">' + c.cantidad + "</span>" +
          '<span class="sk-opt-check">' + icon("icon-check") + "</span></label></div>";
      }).join("");

      var impacto = "";
      if (h && (h.personas > 0 || h.proyectos > 0)) {
        var partes = [];
        if (h.personas > 0) partes.push(plural(h.personas, "persona la tiene", "personas la tienen") + " en su perfil");
        if (h.proyectos > 0) partes.push(plural(h.proyectos, "proyecto la pide", "proyectos la piden"));
        impacto = '<div class="sk-impact">' + icon("icon-info") + "<span>" + esc(partes.join(" y ")) +
          ". El cambio se verá reflejado en todos esos lugares.</span></div>";
      }

      var html = '<div class="modal sk-modal" role="dialog" aria-modal="true" aria-labelledby="sk-modal-h">' +
        headerHtml(h ? "Editar habilidad" : "Agregar habilidad",
          h ? "Cambia su nombre o muévela a otra categoría." : "Súmala al catálogo para que las personas puedan declararla.",
          h ? "icon-edit" : "icon-plus", tonoInicial) +
        '<div class="modal-body">' +
          '<div class="sk-preview"><span class="sk-mono"></span><span class="sk-skill-name"></span><span class="sk-tag"></span>' +
          '<span class="sk-preview-caption">Así se verá en el catálogo</span></div>' +
          '<div class="sk-field" data-sk-nombre><label class="sk-field-label" for="sk-skill-name">Nombre</label>' +
            '<input class="input" id="sk-skill-name" maxlength="' + (MAX_NOMBRE_HABILIDAD + 20) + '" autocomplete="off" autofocus placeholder="Ej. OpenTelemetry" value="' + esc(h ? h.nombre : "") + '">' +
            '<div class="sk-field-foot"><span class="sk-field-msg" aria-live="polite"></span><span class="sk-field-count"></span></div></div>' +
          '<fieldset class="sk-cat-picker"><legend>Categoría</legend><div class="sk-cat-options">' + opciones + "</div></fieldset>" +
          impacto +
        "</div>" + footerHtml(h ? "Guardar cambios" : "Agregar habilidad") + "</div>";

      var backdrop = SBAI.modal.open(html);
      var modal = backdrop.querySelector(".sk-modal");
      modal.querySelector("h2").id = "sk-modal-h";
      var input = modal.querySelector("#sk-skill-name");
      var campo = modal.querySelector("[data-sk-nombre]");
      var badge = modal.querySelector(".sk-modal-badge");
      var pMono = modal.querySelector(".sk-preview .sk-mono");
      var pNombre = modal.querySelector(".sk-preview .sk-skill-name");
      var pTag = modal.querySelector(".sk-preview .sk-tag");
      var confirmar = modal.querySelector("[data-sk-confirm]");
      var pintarCampo = enlazarCampo(campo, input, MAX_NOMBRE_HABILIDAD, h ? "" : "Usa el nombre con el que la gente la conoce.");
      var existentes = Object.keys(habilidades).map(function (k) { return habilidades[k].nombre; });
      var tocado = false;

      function catElegida() {
        var r = modal.querySelector('input[name="sk-cat"]:checked');
        return r ? r.value : "";
      }

      function actualizar() {
        var nombre = input.value.trim();
        var c = categorias[catElegida()];
        var tono = c ? c.tono : "7";
        [badge, pMono, pTag].forEach(function (el) { el.setAttribute("data-tono", tono); });
        pMono.textContent = nombre ? iniciales(nombre) : "?";
        pNombre.textContent = nombre || "Nombre de la habilidad";
        pNombre.classList.toggle("is-placeholder", !nombre);
        pTag.textContent = c ? c.nombre : "Sin categoría";

        var error = validarNombre(input.value, existentes, MAX_NOMBRE_HABILIDAD, h && h.nombre, "habilidad");
        pintarCampo(error, tocado || (error && error.indexOf("Ya existe") === 0));
        var sinCambios = h && nombre === h.nombre && catElegida() === h.catId;
        confirmar.disabled = !!error || !c || sinCambios;
        confirmar.title = sinCambios ? "No hay cambios para guardar" : "";
      }

      function guardar() {
        tocado = true;
        actualizar();
        if (confirmar.disabled) { input.focus(); return; }
        confirmar.disabled = true;
        confirmar.textContent = "Guardando…";
        var datos = { nombre: input.value.trim(), categoriaId: catElegida() };
        submitForm(h ? "/administrador/habilidades/" + h.id + "/editar" : "/administrador/habilidades", datos);
      }

      input.addEventListener("input", actualizar);
      input.addEventListener("blur", function () { tocado = true; actualizar(); });
      modal.querySelectorAll('input[name="sk-cat"]').forEach(function (r) { r.addEventListener("change", actualizar); });
      modal.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && !e.shiftKey && e.target.tagName !== "TEXTAREA" && e.target.tagName !== "BUTTON") {
          e.preventDefault(); guardar();
        }
      });
      confirmar.addEventListener("click", guardar);
      actualizar();
      setTimeout(function () { input.focus(); input.select(); }, 100);
    }

    /* Categoría: agregar (sin id) o editar (con id). */
    function abrirModalCategoria(id) {
      var c = id ? categorias[id] : null;
      var tono = c ? c.tono : String((Math.max.apply(null, listaCategorias.map(function (x) { return Number(x.id); }).concat([0])) + 1) % 8);
      var impacto = c && c.cantidad > 0
        ? '<div class="sk-impact">' + icon("icon-info") + "<span>" + esc(plural(c.cantidad, "habilidad usa", "habilidades usan")) +
          " esta categoría. Si cambias el nombre, todas se mostrarán con el nuevo.</span></div>"
        : "";

      var html = '<div class="modal sk-modal" role="dialog" aria-modal="true" aria-labelledby="sk-modal-h">' +
        headerHtml(c ? "Editar categoría" : "Nueva categoría",
          c ? "Ajusta cómo se agrupan las habilidades del catálogo." : "Agrupa habilidades afines para encontrarlas más rápido.",
          "icon-folder", tono) +
        '<div class="modal-body">' +
          '<div class="sk-preview" data-tono="' + tono + '"><span class="sk-cat-swatch">' + icon("icon-folder", "icon-sm") + "</span>" +
            '<span class="sk-skill-name"></span><span class="sk-tag" data-tono="' + tono + '"></span>' +
            '<span class="sk-preview-caption">Así se verá en el catálogo</span></div>' +
          '<div class="sk-field" data-sk-nombre><label class="sk-field-label" for="sk-cat-name">Nombre</label>' +
            '<input class="input" id="sk-cat-name" maxlength="' + (MAX_NOMBRE_CATEGORIA + 20) + '" autocomplete="off" autofocus placeholder="Ej. Inteligencia artificial" value="' + esc(c ? c.nombre : "") + '">' +
            '<div class="sk-field-foot"><span class="sk-field-msg" aria-live="polite"></span><span class="sk-field-count"></span></div></div>' +
          '<div class="sk-field" data-sk-desc><label class="sk-field-label" for="sk-cat-desc">Descripción <small>(opcional)</small></label>' +
            '<textarea id="sk-cat-desc" maxlength="' + MAX_DESCRIPCION + '" placeholder="Qué tipo de habilidades reúne">' + esc(c ? c.descripcion : "") + "</textarea>" +
            '<div class="sk-field-foot"><span class="sk-field-msg"></span><span class="sk-field-count"></span></div></div>' +
          impacto +
        "</div>" + footerHtml(c ? "Guardar cambios" : "Crear categoría") + "</div>";

      var backdrop = SBAI.modal.open(html);
      var modal = backdrop.querySelector(".sk-modal");
      modal.querySelector("h2").id = "sk-modal-h";
      var input = modal.querySelector("#sk-cat-name");
      var desc = modal.querySelector("#sk-cat-desc");
      var campo = modal.querySelector("[data-sk-nombre]");
      var campoDesc = modal.querySelector("[data-sk-desc]");
      var pNombre = modal.querySelector(".sk-preview .sk-skill-name");
      var pTag = modal.querySelector(".sk-preview .sk-tag");
      var confirmar = modal.querySelector("[data-sk-confirm]");
      var pintarCampo = enlazarCampo(campo, input, MAX_NOMBRE_CATEGORIA, "");
      var pintarDesc = enlazarCampo(campoDesc, desc, MAX_DESCRIPCION, "");
      var existentes = listaCategorias.map(function (x) { return x.nombre; });
      var tocado = false;

      function actualizar() {
        var nombre = input.value.trim();
        pNombre.textContent = nombre || "Nombre de la categoría";
        pNombre.classList.toggle("is-placeholder", !nombre);
        pTag.textContent = plural(c ? c.cantidad : 0, "habilidad", "habilidades");
        var error = validarNombre(input.value, existentes, MAX_NOMBRE_CATEGORIA, c && c.nombre, "categoría");
        pintarCampo(error, tocado || (error && error.indexOf("Ya existe") === 0));
        pintarDesc("", false);
        var sinCambios = c && nombre === c.nombre && desc.value.trim() === (c.descripcion || "").trim();
        confirmar.disabled = !!error || sinCambios;
        confirmar.title = sinCambios ? "No hay cambios para guardar" : "";
      }

      function guardar() {
        tocado = true;
        actualizar();
        if (confirmar.disabled) { input.focus(); return; }
        confirmar.disabled = true;
        confirmar.textContent = "Guardando…";
        // La descripción actual viaja siempre: el servidor la reemplaza con lo
        // que llegue, y antes este modal la mandaba vacía y la borraba.
        var datos = { nombre: input.value.trim(), descripcion: desc.value.trim() };
        submitForm(c ? "/administrador/categorias/" + c.id + "/editar" : "/administrador/categorias", datos);
      }

      input.addEventListener("input", actualizar);
      input.addEventListener("blur", function () { tocado = true; actualizar(); });
      desc.addEventListener("input", actualizar);
      modal.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && !e.shiftKey && e.target.tagName !== "TEXTAREA" && e.target.tagName !== "BUTTON") {
          e.preventDefault(); guardar();
        }
      });
      confirmar.addEventListener("click", guardar);
      actualizar();
      setTimeout(function () { input.focus(); input.select(); }, 100);
    }

    document.getElementById("add-skill-btn").addEventListener("click", function () { abrirModalHabilidad(null); });
    document.getElementById("add-category-btn").addEventListener("click", function () { abrirModalCategoria(null); });

    document.querySelectorAll("[data-edit-skill]").forEach(function (btn) {
      btn.addEventListener("click", function () { abrirModalHabilidad(btn.dataset.id); });
    });
    document.querySelectorAll("[data-edit-category]").forEach(function (btn) {
      btn.addEventListener("click", function () { abrirModalCategoria(btn.dataset.id); });
    });

    document.querySelectorAll("[data-delete-skill]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var h = habilidades[btn.dataset.id];
        var c = categorias[h.catId];
        var enUso = h.personas > 0 || h.proyectos > 0;
        SBAI.modal.confirm({
          title: "¿Eliminar \"" + h.nombre + "\"?",
          desc: enUso
            ? "Está en uso: " + plural(h.personas, "persona", "personas") + " y " + plural(h.proyectos, "proyecto", "proyectos") + ". El sistema no permitirá eliminarla mientras se use."
            : "Nadie la usa todavía. Esta acción no se puede deshacer.",
          subject: { nombre: h.nombre, meta: c ? c.nombre : "" },
          confirmLabel: "Eliminar habilidad", variant: "destructive",
          onConfirm: function () { submitForm("/administrador/habilidades/" + h.id + "/eliminar", {}); }
        });
      });
    });

    document.querySelectorAll("[data-delete-category]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var c = categorias[btn.dataset.id];
        var cantidad = c ? c.cantidad : Number(btn.dataset.cantidad) || 0;
        SBAI.modal.confirm({
          title: "¿Eliminar \"" + btn.dataset.nombre + "\"?",
          desc: cantidad > 0
            ? "Tiene " + plural(cantidad, "habilidad", "habilidades") + ". Muévelas a otra categoría antes de eliminarla."
            : "Está vacía. Esta acción no se puede deshacer.",
          subject: { nombre: btn.dataset.nombre, meta: plural(cantidad, "habilidad", "habilidades") },
          confirmLabel: "Eliminar categoría", variant: "destructive",
          onConfirm: function () { submitForm("/administrador/categorias/" + btn.dataset.id + "/eliminar", {}); }
        });
      });
    });
  });
})();
