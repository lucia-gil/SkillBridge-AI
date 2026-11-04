/*
 * select-ui.js — selector desplegable propio para TODO el sitio.
 *
 * Mejora automáticamente cada <select class="select"> (no múltiple) por un
 * botón + panel con buscador, grupos, íconos y teclado. Se carga desde
 * fragments/seguridad.html (incluido por el topbar), así que aplica a todas
 * las páginas internas sin tocar cada plantilla.
 *
 * El <select> original NO se reemplaza: queda oculto y sincronizado. Por eso
 * los formularios envían lo mismo de siempre y el JS de cada página puede
 * seguir leyendo/escribiendo select.value y escuchando "change".
 *
 * Opciones (atributos opcionales):
 *   <optgroup label="...">       → cabecera de grupo fija al hacer scroll
 *   <option data-group="...">    → igual que optgroup, agrupa sin cambiar el HTML del select
 *   <option data-meta="...">     → texto secundario a la derecha
 *   <select data-tech-icons>     → logo de tecnología junto a cada opción (usa tech-icons.js)
 *   <select data-icons="nombre"> → ícono propio: SBAI.selectIcons.nombre(valor, texto) devuelve HTML
 *   <select data-search="on|off">→ fuerza mostrar/ocultar el buscador (por defecto: si hay más de 7 opciones)
 *   <select data-native>         → no mejorar este select (queda el nativo)
 */
(function () {
  "use strict";

  var SELECTOR = "select.select:not([multiple]):not([data-multiselect]):not([data-native])";
  var UMBRAL_BUSCADOR = 7;
  var registro = new WeakMap();   // select -> api
  var abierto = null;             // api del selector abierto
  var uid = 0;

  var descValue = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value");
  var descIndex = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "selectedIndex");

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim(); }
  function icono(id, cls) { return '<svg class="' + (cls || "icon") + '" aria-hidden="true"><use href="/img/icons.svg#' + id + '"></use></svg>'; }

  /** Resalta las coincidencias de la búsqueda sin romper el escapado. */
  function resaltar(texto, q) {
    if (!q) return esc(texto);
    var n = norm(texto), i = n.indexOf(q);
    if (i < 0) return esc(texto);
    return esc(texto.slice(0, i)) + "<mark>" + esc(texto.slice(i, i + q.length)) + "</mark>" + esc(texto.slice(i + q.length));
  }

  /** Lee las opciones del select (respeta optgroup y data-group). */
  function leerOpciones(sel) {
    var items = [];
    Array.prototype.forEach.call(sel.options, function (o, idx) {
      if (o.hidden) return;
      var og = o.parentNode && o.parentNode.tagName === "OPTGROUP" ? o.parentNode : null;
      items.push({
        idx: idx,
        value: o.value,
        label: (o.textContent || "").trim(),
        meta: o.getAttribute("data-meta") || "",
        group: og ? og.label : (o.getAttribute("data-group") || ""),
        disabled: o.disabled || (og && og.disabled),
        selected: idx === descIndex.get.call(sel)
      });
    });
    // data-group sin optgroup: grupos en orden alfabético, las opciones sin
    // grupo (p. ej. "Todas las habilidades") quedan arriba.
    var usaDataGroup = items.some(function (it) { return it.group; }) && !sel.querySelector("optgroup");
    if (usaDataGroup) {
      var orden = {};
      items.forEach(function (it, i) { orden[it.idx] = i; });
      items.sort(function (a, b) {
        if (!a.group !== !b.group) return a.group ? 1 : -1;
        var g = a.group.localeCompare(b.group, "es");
        return g || orden[a.idx] - orden[b.idx];
      });
    }
    return items;
  }

  function enhance(sel) {
    if (registro.has(sel) || sel.closest("template")) return;

    // Copia de un select ya mejorado (innerHTML/cloneNode): limpiar restos.
    var resto = sel.nextElementSibling;
    if (resto && resto.classList.contains("sx-trigger")) resto.remove();
    sel.classList.remove("sx-native");

    var id = "sx-" + (++uid);
    var btn = document.createElement("button");
    btn.type = "button";
    btn.id = id + "-btn";
    btn.setAttribute("aria-haspopup", "listbox");
    btn.setAttribute("aria-expanded", "false");
    btn.innerHTML = '<span class="sx-icon" hidden></span><span class="sx-value"></span>' + icono("icon-chevron-down", "icon sx-chev");
    var valueEl = btn.querySelector(".sx-value");
    var iconEl = btn.querySelector(".sx-icon");

    sel.insertAdjacentElement("afterend", btn);
    sel.classList.add("sx-native");
    sel.setAttribute("tabindex", "-1");
    sel.setAttribute("aria-hidden", "true");

    var panel = null, lista = null, buscador = null, items = [], activo = -1, consulta = "";

    function nombreAccesible() {
      var a = sel.getAttribute("aria-label");
      if (a) return a;
      if (sel.id) {
        var l = document.querySelector('label[for="' + CSS.escape(sel.id) + '"]');
        if (l) return l.textContent.trim();
      }
      var envoltura = sel.closest("label");
      if (envoltura) {
        // Solo el texto propio del <label>, no el de las opciones del select.
        return Array.prototype.filter.call(envoltura.childNodes, function (n) { return n.nodeType === 3; })
          .map(function (n) { return n.textContent; }).join(" ").replace(/\s+/g, " ").trim();
      }
      return "";
    }

    function copiarClases() {
      var cls = Array.prototype.filter.call(sel.classList, function (c) { return c !== "sx-native"; });
      btn.className = cls.join(" ") + " sx-trigger" + (abierto && abierto.sel === sel ? " is-open" : "");
    }

    // Ícono de una opción: logos de tecnología (data-tech-icons) o un
    // proveedor propio registrado en SBAI.selectIcons[nombre] (data-icons="nombre").
    function logo(label, valor) {
      var proveedor = sel.getAttribute("data-icons");
      if (proveedor && window.SBAI && SBAI.selectIcons && typeof SBAI.selectIcons[proveedor] === "function") {
        return valor ? (SBAI.selectIcons[proveedor](valor, label) || "") : "";
      }
      return sel.hasAttribute("data-tech-icons") && window.SBAI && SBAI.techIcons && label ? SBAI.techIcons.logo(label) : "";
    }

    function refrescar() {
      copiarClases();
      var o = sel.options[descIndex.get.call(sel)];
      var texto = o ? (o.textContent || "").trim() : "";
      valueEl.textContent = texto || "Selecciona…";
      btn.classList.toggle("is-placeholder", !o || (o.value === "" && o.disabled));
      var lg = o && o.value !== "" ? logo(texto, o.value) : "";
      iconEl.innerHTML = lg;
      iconEl.hidden = !lg;
      btn.disabled = sel.disabled;
      btn.hidden = sel.hidden || sel.style.display === "none";
      btn.title = texto;
      var nombre = nombreAccesible();
      btn.setAttribute("aria-label", (nombre ? nombre + ": " : "") + texto);
      if (abierto && abierto.sel === sel) pintar();
    }

    // ---- sincronización con el select real ----
    Object.defineProperty(sel, "value", {
      configurable: true,
      get: function () { return descValue.get.call(this); },
      set: function (v) { descValue.set.call(this, v); refrescar(); }
    });
    Object.defineProperty(sel, "selectedIndex", {
      configurable: true,
      get: function () { return descIndex.get.call(this); },
      set: function (v) { descIndex.set.call(this, v); refrescar(); }
    });
    sel.addEventListener("change", refrescar);
    sel.addEventListener("focus", function () { btn.focus(); });
    if (sel.form) sel.form.addEventListener("reset", function () { setTimeout(refrescar, 0); });
    // refrescar() nunca modifica el <select>, así que no hay bucle.
    new MutationObserver(refrescar).observe(sel, { childList: true, subtree: true, attributes: true, characterData: true,
      attributeFilter: ["disabled", "hidden", "style", "class", "aria-label", "label"] });

    // ---- panel ----
    function construir() {
      items = leerOpciones(sel);
      var conBuscador = sel.getAttribute("data-search") === "on" ||
        (sel.getAttribute("data-search") !== "off" && items.length > UMBRAL_BUSCADOR);
      panel = document.createElement("div");
      panel.className = "sx-panel";
      panel.id = id + "-panel";
      panel.innerHTML =
        (conBuscador ? '<div class="sx-search">' + icono("icon-search", "icon sx-search-icon") +
          '<input type="text" class="sx-search-input" autocomplete="off" spellcheck="false" placeholder="Buscar en ' + items.length + ' opciones…" aria-label="Buscar opción" aria-controls="' + id + '-list">' +
          '<button type="button" class="sx-clear" hidden aria-label="Borrar búsqueda">' + icono("icon-x") + "</button></div>" : "") +
        '<ul class="sx-list" role="listbox" id="' + id + '-list" aria-labelledby="' + btn.id + '"></ul>';
      lista = panel.querySelector(".sx-list");
      buscador = panel.querySelector(".sx-search-input");
      var limpiar = panel.querySelector(".sx-clear");

      (sel.closest("dialog") || document.body).appendChild(panel);

      if (buscador) {
        buscador.addEventListener("input", function () {
          consulta = norm(buscador.value);
          limpiar.hidden = !buscador.value;
          activo = -1;
          pintar();
          mover(1, true);
        });
        buscador.addEventListener("keydown", teclasPanel);
        limpiar.addEventListener("click", function () { buscador.value = ""; buscador.dispatchEvent(new Event("input")); buscador.focus(); });
      }
      lista.addEventListener("keydown", teclasPanel);
      lista.addEventListener("mousedown", function (e) { e.preventDefault(); }); // no robar el foco del buscador
      lista.addEventListener("click", function (e) {
        var li = e.target.closest(".sx-opt");
        if (li && !li.classList.contains("is-disabled")) elegir(+li.getAttribute("data-idx"));
      });
      lista.addEventListener("mousemove", function (e) {
        var li = e.target.closest(".sx-opt");
        if (li && !li.classList.contains("is-disabled")) marcar(li, false);
      });
    }

    function visibles() { return Array.prototype.slice.call(lista.querySelectorAll(".sx-opt:not(.is-disabled)")); }

    function pintar() {
      if (!lista) return;
      items = leerOpciones(sel);
      var html = "", grupoActual = null, n = 0;
      items.forEach(function (it) {
        var texto = norm(it.label + " " + it.meta + " " + it.group);
        if (consulta && texto.indexOf(consulta) < 0) return;
        if (it.group !== grupoActual) {
          grupoActual = it.group;
          if (it.group) html += '<li class="sx-group" role="presentation">' + esc(it.group) + "</li>";
        }
        n++;
        var lg = it.value !== "" ? logo(it.label, it.value) : "";
        html += '<li class="sx-opt' + (it.selected ? " is-selected" : "") + (it.disabled ? " is-disabled" : "") +
          (it.group ? " in-group" : "") + '" role="option" id="' + id + "-o" + it.idx + '" data-idx="' + it.idx + '"' +
          ' aria-selected="' + it.selected + '"' + (it.disabled ? ' aria-disabled="true"' : "") + ">" +
          (lg ? '<span class="sx-opt-icon">' + lg + "</span>" : "") +
          '<span class="sx-opt-label">' + resaltar(it.label, consulta) + "</span>" +
          (it.meta ? '<span class="sx-opt-meta">' + resaltar(it.meta, consulta) + "</span>" : "") +
          icono("icon-check", "icon sx-opt-check") + "</li>";
      });
      if (!n) html = '<li class="sx-empty" role="presentation">Sin resultados para «' + esc(buscador ? buscador.value : "") + "»</li>";
      lista.innerHTML = html;
    }

    function marcar(li, scroll) {
      lista.querySelectorAll(".sx-opt.is-active").forEach(function (x) { x.classList.remove("is-active"); });
      if (!li) { activo = -1; (buscador || btn).removeAttribute("aria-activedescendant"); return; }
      li.classList.add("is-active");
      activo = +li.getAttribute("data-idx");
      (buscador || lista).setAttribute("aria-activedescendant", li.id);
      if (scroll) li.scrollIntoView({ block: "nearest" });
    }

    function mover(delta, desdeInicio) {
      var v = visibles();
      if (!v.length) return marcar(null);
      var pos = v.findIndex(function (li) { return +li.getAttribute("data-idx") === activo; });
      if (desdeInicio || pos < 0) pos = delta > 0 ? 0 : v.length - 1;
      else pos = Math.max(0, Math.min(v.length - 1, pos + delta));
      marcar(v[pos], true);
    }

    function teclasPanel(e) {
      switch (e.key) {
        case "ArrowDown": e.preventDefault(); mover(1); break;
        case "ArrowUp": e.preventDefault(); mover(-1); break;
        case "PageDown": e.preventDefault(); mover(8); break;
        case "PageUp": e.preventDefault(); mover(-8); break;
        case "Home": if (!buscador || e.target !== buscador) { e.preventDefault(); mover(-1, true); } break;
        case "End": if (!buscador || e.target !== buscador) { e.preventDefault(); mover(1, true); } break;
        case "Enter": e.preventDefault(); if (activo >= 0) elegir(activo); break;
        case "Escape": e.preventDefault(); e.stopPropagation(); cerrar(true); break;
        case "Tab": cerrar(false); break;
      }
    }

    function posicionar() {
      if (!panel) return;
      var r = btn.getBoundingClientRect();
      var vw = document.documentElement.clientWidth, vh = window.innerHeight, margen = 8;
      var cont = panel.parentNode, base = { left: 0, top: 0 };
      if (cont !== document.body && getComputedStyle(cont).transform !== "none") base = cont.getBoundingClientRect();
      var ancho = Math.min(Math.max(r.width, 240), vw - margen * 2);
      var abajo = vh - r.bottom - margen, arriba = r.top - margen;
      var alto = Math.min(panel.scrollHeight, 360);
      var haciaArriba = abajo < Math.min(alto, 220) && arriba > abajo;
      var max = Math.max(140, Math.min(360, (haciaArriba ? arriba : abajo) - 6));
      panel.style.width = ancho + "px";
      panel.style.maxHeight = max + "px";
      panel.style.left = (Math.min(Math.max(margen, r.left), vw - ancho - margen) - base.left) + "px";
      panel.style.top = haciaArriba ? "" : (r.bottom + 6 - base.top) + "px";
      panel.style.bottom = haciaArriba ? (vh - r.top + 6 + base.top) + "px" : "";
      panel.classList.toggle("is-up", haciaArriba);
    }

    function abrir(textoInicial) {
      if (btn.disabled) return;
      if (abierto && abierto !== api) abierto.cerrar(false);
      if (abierto === api) return;
      abierto = api;
      consulta = "";
      construir();
      pintar();
      btn.setAttribute("aria-expanded", "true");
      btn.setAttribute("aria-controls", panel.id);
      btn.classList.add("is-open");
      posicionar();
      requestAnimationFrame(function () { if (panel) panel.classList.add("is-visible"); });
      var actual = lista.querySelector(".sx-opt.is-selected");
      if (actual) { marcar(actual, false); actual.scrollIntoView({ block: "center" }); } else mover(1, true);
      if (buscador) {
        if (textoInicial) { buscador.value = textoInicial; buscador.dispatchEvent(new Event("input")); }
        buscador.focus({ preventScroll: true });
      } else {
        lista.tabIndex = -1;
        lista.focus({ preventScroll: true });
      }
    }

    function cerrar(devolverFoco) {
      if (abierto !== api) return;
      abierto = null;
      btn.setAttribute("aria-expanded", "false");
      btn.removeAttribute("aria-controls");
      btn.classList.remove("is-open");
      if (panel) panel.remove();
      panel = lista = buscador = null;
      if (devolverFoco) btn.focus({ preventScroll: true });
    }

    function elegir(idx) {
      var cambio = idx !== descIndex.get.call(sel);
      descIndex.set.call(sel, idx);
      cerrar(true);
      refrescar();
      if (cambio) {
        sel.dispatchEvent(new Event("input", { bubbles: true }));
        sel.dispatchEvent(new Event("change", { bubbles: true }));
      }
    }

    btn.addEventListener("click", function () { abierto === api ? cerrar(true) : abrir(); });
    btn.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Enter" || e.key === " ") {
        e.preventDefault(); abrir();
      } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
        // Escribir sobre el botón cerrado: abre con la búsqueda, o salta a la primera coincidencia.
        if (leerOpciones(sel).length > UMBRAL_BUSCADOR && sel.getAttribute("data-search") !== "off") { e.preventDefault(); abrir(e.key); }
        else {
          var q = norm(e.key), o = leerOpciones(sel).filter(function (it) { return !it.disabled && norm(it.label).indexOf(q) === 0; })[0];
          if (o) elegir(o.idx);
        }
      }
    });

    var api = { sel: sel, btn: btn, abrir: abrir, cerrar: cerrar, refrescar: refrescar, posicionar: posicionar,
      contiene: function (n) { return btn.contains(n) || (panel && panel.contains(n)); } };
    registro.set(sel, api);
    refrescar();
  }

  function escanear(raiz) {
    if (!raiz || raiz.nodeType !== 1) return;
    if (raiz.matches && raiz.matches(SELECTOR)) enhance(raiz);
    raiz.querySelectorAll && raiz.querySelectorAll(SELECTOR).forEach(enhance);
  }

  // Cerrar al hacer clic fuera; reubicar al hacer scroll o cambiar el tamaño.
  document.addEventListener("mousedown", function (e) {
    if (abierto && !abierto.contiene(e.target)) abierto.cerrar(false);
  }, true);
  window.addEventListener("resize", function () { if (abierto) abierto.posicionar(); });
  document.addEventListener("scroll", function (e) {
    if (!abierto) return;
    if (e.target && e.target.closest && e.target.closest(".sx-panel")) return;
    if (!document.body.contains(abierto.btn)) return abierto.cerrar(false);
    abierto.posicionar();
  }, true);

  function iniciar() {
    escanear(document.body);
    new MutationObserver(function (muts) {
      muts.forEach(function (m) { m.addedNodes.forEach(escanear); });
      if (abierto && !document.body.contains(abierto.btn)) abierto.cerrar(false);
    }).observe(document.body, { childList: true, subtree: true });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar);
  else iniciar();

  window.SBAI = window.SBAI || {};
  window.SBAI.select = {
    mejorar: escanear,
    /** Vuelve a leer el select (útil si se cambió algo que el componente no detecta). */
    refrescar: function (sel) { var a = registro.get(sel); if (a) a.refrescar(); }
  };
})();
