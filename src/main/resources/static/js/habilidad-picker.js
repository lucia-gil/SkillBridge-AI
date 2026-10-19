/**
 * habilidad-picker.js — Selector de UNA habilidad del catálogo, con buscador
 * y lista con scroll agrupada por categoría. Usa el mismo diseño que el
 * "Stack tecnológico" de Crear proyecto (clases .ti-* de proyecto-form.css y
 * logos de tech-icons.js). Lo usa "Agregar habilidad" en Mi perfil del
 * colaborador.
 *
 *   var picker = SBAI.habilidadPicker.montar(host, {
 *     catalogo: [{ id, nombre, categoriaNombre }],
 *     excluir:  [ids ya declarados]
 *   });
 *   picker.valor()  -> id elegido (o "")
 */
(function () {
  "use strict";
  window.SBAI = window.SBAI || {};
  var contador = 0;

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function clave(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim(); }
  function logoHtml(nombre) {
    if (window.SBAI.techIcons && window.SBAI.techIcons.logo) return window.SBAI.techIcons.logo(nombre);
    var ini = String(nombre || "?").replace(/[^A-Za-z0-9]/g, "").slice(0, 2).toUpperCase();
    return '<span class="tech-chip-logo">' + esc(ini) + "</span>";
  }

  function montar(host, o) {
    o = o || {};
    var excluir = (o.excluir || []).map(String);
    var catalogo = (o.catalogo || []).filter(function (h) { return excluir.indexOf(String(h.id)) === -1; })
      .map(function (h) { return { id: String(h.id), nombre: h.nombre, categoria: h.categoriaNombre || "Otras", k: clave(h.nombre) }; })
      .sort(function (a, b) { return a.categoria.localeCompare(b.categoria) || a.nombre.localeCompare(b.nombre); });
    var uid = "hp-" + (++contador);
    var elegida = null, opciones = [], activo = -1;

    host.innerHTML =
      '<div class="ti">' +
        '<div class="ti-box">' +
          '<div class="ti-chips"></div>' +
          '<input type="text" class="ti-input" autocomplete="off" spellcheck="false" role="combobox" aria-autocomplete="list"' +
          ' aria-expanded="false" aria-controls="' + uid + '-list" placeholder="Escribe una habilidad…">' +
        "</div>" +
        '<div class="ti-panel" id="' + uid + '-list" role="listbox" hidden></div>' +
        '<div class="ti-meta"><span class="ti-hint">Elige del catálogo con ↑ ↓ y Enter, o haz clic.</span>' +
        '<span class="ti-count">' + catalogo.length + " disponibles</span></div>" +
      "</div>";
    var raiz = host.querySelector(".ti"), caja = host.querySelector(".ti-box"), chips = host.querySelector(".ti-chips");
    var input = host.querySelector(".ti-input"), panel = host.querySelector(".ti-panel"), hint = host.querySelector(".ti-hint");

    // Valor elegido: se muestra como en un <select> normal (logo + nombre +
    // flecha), sin etiqueta ni "x", porque aquí solo se elige UNA habilidad.
    var editando = false;
    function pintarChip() {
      var mostrar = elegida && !editando;
      chips.innerHTML = mostrar
        ? '<span class="hp-value">' + logoHtml(elegida.nombre) + '<span class="hp-value-name">' + esc(elegida.nombre) + "</span>" +
          '<span class="hp-value-cat">' + esc(elegida.categoria) + "</span>" +
          '<svg class="icon icon-sm hp-value-caret" aria-hidden="true"><use href="/img/icons.svg#icon-chevron-down"></use></svg></span>'
        : "";
      input.hidden = !!mostrar;
      raiz.classList.toggle("hp-has-value", !!mostrar);
      hint.textContent = elegida ? "Haz clic en la habilidad para cambiarla." : "Elige del catálogo con ↑ ↓ y Enter, o haz clic.";
      hint.className = "ti-hint";
    }

    function opcionHtml(op, i, q) {
      var nombre = esc(op.nombre);
      if (q) {
        var j = op.k.indexOf(clave(q));
        if (j >= 0) nombre = esc(op.nombre.slice(0, j)) + "<mark>" + esc(op.nombre.slice(j, j + q.length)) + "</mark>" + esc(op.nombre.slice(j + q.length));
      }
      return '<div class="ti-opt' + (i === activo ? " is-active" : "") + '" role="option" id="' + uid + "-o" + i + '" data-i="' + i + '"' +
        ' aria-selected="' + (i === activo) + '">' + logoHtml(op.nombre) +
        '<span class="ti-opt-name">' + nombre + '</span><span class="ti-opt-cat">' + esc(op.categoria) + "</span></div>";
    }

    function pintarPanel() {
      var q = input.value.trim(), k = clave(q), html = "", grupo = null;
      opciones = catalogo.filter(function (t) { return !k || t.k.indexOf(k) >= 0 || clave(t.categoria).indexOf(k) === 0; });
      if (activo >= opciones.length) activo = opciones.length - 1;
      if (q && activo < 0 && opciones.length) activo = 0;
      opciones.forEach(function (op, i) {
        if (!q && op.categoria !== grupo) { grupo = op.categoria; html += '<div class="ti-group" role="presentation">' + esc(grupo) + "</div>"; }
        html += opcionHtml(op, i, q);
      });
      if (!catalogo.length) html = '<div class="ti-empty">Ya declaraste todas las habilidades del catálogo.</div>';
      else if (!opciones.length) html = '<div class="ti-empty"><strong>“' + esc(q) + '”</strong> no está en el catálogo.' +
        "<span>Pide al Administrador que la agregue al Catálogo de habilidades.</span></div>";
      panel.innerHTML = html;
      panel.hidden = false;
      input.setAttribute("aria-expanded", "true");
      var el = activo >= 0 ? panel.querySelector('[data-i="' + activo + '"]') : null;
      if (el) el.scrollIntoView({ block: "nearest" });
    }
    function cerrar() { panel.hidden = true; input.setAttribute("aria-expanded", "false"); }
    function elegir(i) {
      if (!opciones[i]) return;
      elegida = opciones[i]; input.value = ""; activo = -1; editando = false;
      cerrar(); pintarChip();
    }

    caja.addEventListener("click", function () {
      if (elegida && !editando) { editando = true; pintarChip(); input.placeholder = "Buscar otra habilidad…"; }
      input.focus();
    });
    input.addEventListener("focus", function () { raiz.classList.add("is-focus"); pintarPanel(); });
    input.addEventListener("blur", function () {
      raiz.classList.remove("is-focus");
      setTimeout(function () {
        cerrar();
        // Si se abrió para cambiar y no eligió otra, vuelve a mostrar la elegida.
        if (editando) { editando = false; input.value = ""; input.placeholder = "Escribe una habilidad…"; pintarChip(); }
      }, 150);
    });
    input.addEventListener("input", function () { activo = -1; pintarPanel(); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        if (panel.hidden) pintarPanel();
        if (!opciones.length) return;
        activo = (activo + (e.key === "ArrowDown" ? 1 : -1) + opciones.length) % opciones.length;
        pintarPanel();
      } else if (e.key === "Enter" || (e.key === "Tab" && activo >= 0 && !panel.hidden)) {
        if (activo >= 0) { e.preventDefault(); elegir(activo); }
        else if (e.key === "Enter") e.preventDefault();
      } else if (e.key === "Escape") { if (!panel.hidden) { e.stopPropagation(); cerrar(); } }
    });
    panel.addEventListener("mousedown", function (e) {
      var opt = e.target.closest(".ti-opt");
      if (!opt) return;
      e.preventDefault();
      elegir(Number(opt.dataset.i));
    });
    pintarChip();

    return {
      valor: function () { return elegida ? elegida.id : ""; },
      enfocar: function () { input.focus(); },
      marcarError: function (msg) {
        hint.textContent = msg; hint.className = "ti-hint is-error";
        raiz.classList.remove("is-shake"); void raiz.offsetWidth; raiz.classList.add("is-shake");
      }
    };
  }

  window.SBAI.habilidadPicker = { montar: montar };
})();
