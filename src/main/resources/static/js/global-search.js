/**
 * global-search.js — Buscador global predictivo del topbar.
 * Consulta /cuenta/buscar desde 2 letras (con debounce), agrupa resultados
 * por tipo, resalta la coincidencia y se maneja con teclado
 * (flechas, Enter, Esc). Ctrl/Cmd+K enfoca el campo desde cualquier pantalla.
 *
 * Buscador inteligente: desde 3 letras siempre aparece "Preguntar a la IA".
 * Va primero si lo escrito parece una pregunta (con o sin "?": palabra
 * interrogativa, verbo de búsqueda o 2+ palabras) y al final si no. También
 * se pregunta con el botón ✨ del campo o con Ctrl/Shift+Enter. Solo entonces
 * se llama, con jQuery/AJAX, a POST /cuenta/buscar-ia: la IA convierte la
 * pregunta en filtros y el servidor busca en la base con el alcance de tu rol.
 */
(function () {
  "use strict";
  var MIN = 2, MIN_IA = 3, DEBOUNCE = 140;

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) {
    return String(s || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  }
  // Resalta la coincidencia respetando tildes (busca sobre texto normalizado, corta sobre el original).
  function resaltar(texto, q) {
    var t = String(texto || ""), i = norm(t).indexOf(norm(q));
    if (i < 0 || !q) return esc(t);
    return esc(t.slice(0, i)) + "<mark>" + esc(t.slice(i, i + q.length)) + "</mark>" + esc(t.slice(i + q.length));
  }
  // ¿Parece una pregunta? No hace falta el "?": basta una palabra interrogativa
  // o de búsqueda en cualquier parte, o escribir 2 palabras o más.
  var INTERROGATIVAS = /\b(quien|quienes|que|cual|cuales|cuanto|cuantos|cuantas|cuando|donde|como|hay|dame|muestra|muestrame|mostrar|lista|listar|busca|buscar|encuentra|necesito|quiero|tengo|tiene|tienen|sabe|saben|libre|libres|disponible|disponibles|atrasado|atrasados|vacante|vacantes|proyectos|personas|colaboradores|mis)\b/;
  function esPregunta(q) {
    var n = norm(q).replace(/[¿?¡!.,;:]/g, " ").trim();
    return /[?¿]/.test(q) || INTERROGATIVAS.test(n) || n.split(/\s+/).length >= 2;
  }
  function icono(id) {
    return '<svg class="icon icon-sm" aria-hidden="true"><use href="/img/icons.svg#' + id + '"></use></svg>';
  }

  document.addEventListener("DOMContentLoaded", function () {
    var root = document.querySelector("[data-global-search]");
    if (!root) return;
    var input = root.querySelector(".gs-input");
    var panel = root.querySelector(".gs-panel");
    var clearBtn = root.querySelector(".gs-clear");
    var iaBtn = root.querySelector(".gs-ia-btn");
    var items = [], active = -1, timer = null, reqId = 0;

    function abrir() { panel.hidden = false; root.classList.add("open"); input.setAttribute("aria-expanded", "true"); }
    function cerrar() { panel.hidden = true; root.classList.remove("open"); input.setAttribute("aria-expanded", "false"); active = -1; }

    function itemIa(q, i) {
      return '<a class="gs-item gs-ia" role="option" id="gs-opt-' + i + '" data-i="' + i + '" href="#" data-ia>' +
        '<span class="gs-item-icon">' + icono("icon-sparkles") + "</span>" +
        '<span class="gs-item-text"><span class="gs-item-title">Preguntar a la IA: “' + esc(q) + '”</span>' +
        '<span class="gs-item-sub">Entiende preguntas y busca con tus datos reales</span></span>' +
        '<span class="gs-item-go">↵</span></a>';
    }

    function pintar(data, q) {
      var pregunta = esPregunta(q), conIa = q.length >= MIN_IA;
      items = data.slice();
      if (!data.length && !conIa) {
        panel.innerHTML = '<div class="gs-empty">' + icono("icon-search") +
          '<span>Sin resultados para “' + esc(q) + '”. Prueba con otras letras.</span></div>';
        abrir(); return;
      }
      var html = "", grupo = null;
      if (conIa && pregunta) { items.unshift({ ia: true }); html += itemIa(q, 0); }
      data.forEach(function (it, j) {
        var i = j + (conIa && pregunta ? 1 : 0);
        if (it.tipo !== grupo) { grupo = it.tipo; html += '<div class="gs-group">' + esc(grupo) + "</div>"; }
        html += '<a class="gs-item" role="option" id="gs-opt-' + i + '" data-i="' + i + '" href="' + esc(it.url) + '">' +
          '<span class="gs-item-icon">' + icono(it.icono) + "</span>" +
          '<span class="gs-item-text"><span class="gs-item-title">' + resaltar(it.titulo, q) + "</span>" +
          '<span class="gs-item-sub">' + esc(it.detalle) + "</span></span>" +
          '<span class="gs-item-go">↵</span></a>';
      });
      if (conIa && !pregunta) { items.push({ ia: true }); html += itemIa(q, items.length - 1); }
      if (!data.length && conIa) html += '<div class="gs-hint">No hay coincidencias exactas; pregúntale a la IA.</div>';
      panel.innerHTML = html;
      active = 0; marcar();
      abrir();
    }

    // ---- Buscador inteligente ----
    function preguntarIa(q) {
      if (q.length < MIN_IA) {
        panel.innerHTML = '<div class="gs-hint">Escribe al menos ' + MIN_IA + ' letras para preguntar a la IA.</div>'; abrir(); return;
      }
      clearTimeout(timer);
      if (!(window.SBAI && window.SBAI.api)) {
        panel.innerHTML = '<div class="gs-empty"><span>No se pudo conectar con la IA. Recarga la página e inténtalo de nuevo.</span></div>'; abrir(); return;
      }
      var mine = ++reqId;
      items = []; active = -1;
      panel.innerHTML = '<div class="gs-loading gs-ia-loading"><span class="gs-spinner"></span>La IA está interpretando tu pregunta…</div>';
      abrir();
      window.SBAI.api.post("/cuenta/buscar-ia", { q: q })
        .done(function (r) {
          if (mine !== reqId) return;
          items = r.resultados;
          var html = '<div class="gs-ia-head">' + icono("icon-sparkles") + "<div><b>" + esc(r.interpretacion) + "</b>" +
            '<div class="gs-ia-chips">' + r.chips.map(function (c) { return "<span>" + esc(c) + "</span>"; }).join("") +
            '<span class="gs-ia-src">' + (r.porIa ? "Interpretado por la IA" : "Por palabras clave") + "</span></div>" +
            (r.aviso ? '<small class="gs-ia-aviso">' + esc(r.aviso) + "</small>" : "") + "</div></div>";
          r.resultados.forEach(function (it, i) {
            html += '<a class="gs-item" role="option" id="gs-opt-' + i + '" data-i="' + i + '" href="' + esc(it.url) + '">' +
              '<span class="gs-item-icon">' + icono(it.icono) + "</span>" +
              '<span class="gs-item-text"><span class="gs-item-title">' + esc(it.titulo) + "</span>" +
              '<span class="gs-item-sub">' + esc(it.detalle) + "</span></span>" +
              '<span class="gs-item-go">↵</span></a>';
          });
          // Sin chips = no se entendió la pregunta: el aviso del servidor ya dice qué probar.
          if (!r.resultados.length && r.chips.length) html += '<div class="gs-hint">Prueba con otras palabras o menos filtros.</div>';
          panel.innerHTML = html;
          active = r.resultados.length ? 0 : -1; marcar();
        })
        .fail(function (xhr) {
          if (mine !== reqId) return;
          panel.innerHTML = '<div class="gs-empty">' + icono("icon-alert-circle") + "<span>" + esc(window.SBAI.api.mensajeError(xhr)) + "</span></div>";
        });
    }

    function elegir(el) {
      if (!el) return;
      if (el.hasAttribute("data-ia")) preguntarIa(input.value.trim());
      else window.location.href = el.getAttribute("href");
    }

    function marcar() {
      panel.querySelectorAll(".gs-item").forEach(function (el, i) {
        var on = i === active;
        el.classList.toggle("active", on);
        if (on) { input.setAttribute("aria-activedescendant", el.id); el.scrollIntoView({ block: "nearest" }); }
      });
    }

    function buscar(q) {
      var mine = ++reqId;
      panel.innerHTML = '<div class="gs-loading"><span class="gs-spinner"></span>Buscando…</div>';
      abrir();
      fetch("/cuenta/buscar?q=" + encodeURIComponent(q), { credentials: "same-origin", headers: { Accept: "application/json" } })
        .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
        .then(function (data) { if (mine === reqId) pintar(data, q); })
        .catch(function () {
          if (mine !== reqId) return;
          if (q.length >= MIN_IA) { pintar([], q); return; }  // la opción de IA sigue disponible
          panel.innerHTML = '<div class="gs-empty"><span>No se pudo buscar. Verifica tu sesión e inténtalo de nuevo.</span></div>';
        });
    }

    input.addEventListener("input", function () {
      var q = input.value.trim();
      clearBtn.hidden = !input.value;
      if (iaBtn) iaBtn.disabled = q.length < MIN_IA;
      clearTimeout(timer);
      if (q.length < MIN) {
        if (!q.length) { cerrar(); return; }
        panel.innerHTML = '<div class="gs-hint">Escribe al menos ' + MIN + ' letras para buscar.</div>'; abrir(); return;
      }
      timer = setTimeout(function () { buscar(q); }, DEBOUNCE);
    });
    input.addEventListener("focus", function () { if (input.value.trim().length >= MIN && panel.innerHTML) abrir(); });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        if (!items.length || panel.hidden) return;
        e.preventDefault();
        active = (active + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length; marcar();
      } else if (e.key === "Enter" && (e.ctrlKey || e.metaKey || e.shiftKey)) {
        e.preventDefault(); preguntarIa(input.value.trim());
      } else if (e.key === "Enter") {
        var el = panel.querySelector(".gs-item.active");
        if (el) { e.preventDefault(); elegir(el); }
      } else if (e.key === "Escape") { cerrar(); input.blur(); }
    });
    panel.addEventListener("click", function (e) {
      var el = e.target.closest(".gs-item[data-ia]");
      if (el) { e.preventDefault(); elegir(el); }
    });
    panel.addEventListener("mousemove", function (e) {
      var el = e.target.closest(".gs-item"); if (!el) return;
      active = +el.dataset.i; marcar();
    });
    clearBtn.addEventListener("click", function () {
      input.value = ""; clearBtn.hidden = true; if (iaBtn) iaBtn.disabled = true; cerrar(); input.focus();
    });
    if (iaBtn) iaBtn.addEventListener("click", function () { preguntarIa(input.value.trim()); input.focus(); });
    document.addEventListener("mousedown", function (e) { if (!root.contains(e.target)) cerrar(); });
    document.addEventListener("keydown", function (e) {
      if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) { e.preventDefault(); input.focus(); input.select(); }
      else if (e.key === "/" && !/^(INPUT|TEXTAREA|SELECT)$/.test((document.activeElement || {}).tagName || "")) { e.preventDefault(); input.focus(); }
    });
  });
})();
