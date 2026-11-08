/*
 * proyecto-form.js — Piezas de los formularios de proyecto (PM y Admin).
 *
 *  SBAI.techInput.create(host, opciones)
 *    Campo de tecnologías con chips y autocompletado contra el Catálogo de
 *    habilidades del Administrador (ya no se escribe texto libre con comas).
 *      opciones.catalogo   [{nombre, categoria}]  (del modelo: catalogoTecnologias)
 *      opciones.valores    ["React", ...]         chips iniciales (editar)
 *      opciones.max        máximo de chips (12)
 *      opciones.inputId    id del <input> interno (para el <label for>)
 *    Teclado: ↑ ↓ para moverse, Enter / Tab / coma para agregar, Backspace
 *    (dos veces) para quitar el último chip, Esc para cerrar sugerencias.
 *    Las tecnologías que el proyecto ya tenía y no están en el catálogo se
 *    muestran como chip "heredado": se pueden quitar, no volver a agregar.
 *    Devuelve {getValues(), setValues(lista), focus(), el}.
 *
 *  SBAI.dateRange.create(opciones)
 *    Inicio + entrega esperada con: mínimo de entrega que sigue al inicio,
 *    ajuste automático si la entrega queda antes del mínimo, atajos de
 *    duración y resumen en vivo ("Duración: 2 semanas · Entrega: jue 6 nov").
 *      opciones.inicio, opciones.fin   <input type="date">
 *      opciones.resumen                elemento donde va el resumen
 *      opciones.atajos                 contenedor para los botones de duración
 *      opciones.minInicio              ISO mínimo para el inicio (crear: hoy)
 *      opciones.diasMinimos            7 (mismo mínimo que ProyectoService)
 *    Devuelve {validar() -> null | {campo, mensaje}}.
 *
 *  Depende de tech-icons.js (logos) si está cargado; si no, usa iniciales.
 */
(function () {
  "use strict";

  window.SBAI = window.SBAI || {};

  // ═══════════════════════════ Utilidades ═══════════════════════════

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  /** "My SQL" -> "mysql", "Node.js" -> "nodejs" (mismo criterio que TecnologiaCatalogoService.clave). */
  function clave(s) {
    return String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9+#]/g, "");
  }
  function palabras(s) {
    return String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().split(/[^a-z0-9+#]+/).filter(Boolean);
  }
  function distancia(a, b) {
    var prev = [], cur = [], i, j;
    for (j = 0; j <= b.length; j++) prev[j] = j;
    for (i = 1; i <= a.length; i++) {
      cur = [i];
      for (j = 1; j <= b.length; j++) {
        cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      }
      prev = cur;
    }
    return prev[b.length];
  }
  function logoHtml(nombre) {
    if (window.SBAI.techIcons && window.SBAI.techIcons.logo) return window.SBAI.techIcons.logo(nombre);
    var ini = String(nombre || "?").replace(/[^A-Za-z0-9]/g, "").slice(0, 2).toUpperCase();
    return '<span class="tech-chip-logo">' + esc(ini) + "</span>";
  }

  // ═══════════════════════════ Campo de tecnologías ═══════════════════════════

  var contador = 0;

  function createTechInput(host, o) {
    o = o || {};
    var catalogo = (o.catalogo || []).map(function (t) { return { nombre: t.nombre, categoria: t.categoria || "Otras", k: clave(t.nombre) }; });
    var porClave = {};
    catalogo.forEach(function (t) { porClave[t.k] = t; });
    var max = o.max || 12;
    var seleccion = [];          // [{nombre, heredada}]
    var activo = -1;             // índice de la opción resaltada
    var opciones = [];           // opciones visibles [{nombre, categoria, sugerencia?}]
    var armadoBorrar = false;
    var uid = "ti-" + (++contador);

    host.innerHTML =
      '<div class="ti">' +
        '<div class="ti-box">' +
          '<div class="ti-chips" role="list" aria-label="Tecnologías seleccionadas"></div>' +
          '<input type="text" class="ti-input" autocomplete="off" spellcheck="false" role="combobox" aria-autocomplete="list"' +
            ' aria-expanded="false" aria-controls="' + uid + '-list" id="' + esc(o.inputId || uid + "-input") + '"' +
            ' placeholder="Escribe una tecnología…">' +
        "</div>" +
        '<div class="ti-panel" id="' + uid + '-list" role="listbox" hidden></div>' +
        '<div class="ti-meta"><span class="ti-hint" aria-live="polite"></span><span class="ti-count"></span></div>' +
      "</div>";
    var raiz = host.querySelector(".ti");
    var caja = host.querySelector(".ti-box");
    var chips = host.querySelector(".ti-chips");
    var input = host.querySelector(".ti-input");
    var panel = host.querySelector(".ti-panel");
    var hint = host.querySelector(".ti-hint");
    var cuenta = host.querySelector(".ti-count");

    var HINT_BASE = "Elige del catálogo con ↑ ↓ y Enter o Tab.";
    function avisar(msg, tipo) {
      hint.innerHTML = msg || HINT_BASE;
      hint.className = "ti-hint" + (tipo ? " is-" + tipo : "");
    }

    function seleccionadas() { return seleccion.map(function (s) { return clave(s.nombre); }); }

    function pintarChips() {
      chips.innerHTML = seleccion.map(function (s, i) {
        return '<span class="tech-chip ti-chip' + (s.heredada ? " is-legacy" : "") + (armadoBorrar && i === seleccion.length - 1 ? " is-armed" : "") + '" role="listitem"' +
          (s.heredada ? ' title="No está en el catálogo: se mantiene porque el proyecto ya la tenía."' : "") + ">" +
          logoHtml(s.nombre) + "<span>" + esc(s.nombre) + "</span>" +
          '<button type="button" class="ti-remove" data-quitar="' + i + '" aria-label="Quitar ' + esc(s.nombre) + '">' +
          '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M7 7l10 10M17 7L7 17"/></svg></button></span>';
      }).join("");
      cuenta.textContent = seleccion.length + "/" + max;
      cuenta.classList.toggle("is-full", seleccion.length >= max);
      input.placeholder = seleccion.length ? (seleccion.length >= max ? "Llegaste al máximo" : "Agregar otra…") : "Escribe una tecnología…";
      input.disabled = false;
      raiz.classList.toggle("has-values", seleccion.length > 0);
    }

    function agregar(nombre) {
      var t = porClave[clave(nombre)];
      if (!t) return false;
      if (seleccionadas().indexOf(t.k) >= 0) { avisar("<strong>" + esc(t.nombre) + "</strong> ya está en el stack.", "info"); return false; }
      if (seleccion.length >= max) { avisar("Puedes agregar hasta " + max + " tecnologías.", "warn"); return false; }
      seleccion.push({ nombre: t.nombre, heredada: false });
      armadoBorrar = false;
      input.value = "";
      pintarChips();
      avisar(null);
      pintarPanel();
      return true;
    }

    function quitar(i) {
      seleccion.splice(i, 1);
      armadoBorrar = false;
      pintarChips();
      pintarPanel();
      input.focus();
    }

    function buscar(q) {
      var k = clave(q);
      var usadas = seleccionadas();
      var lista = [];
      catalogo.forEach(function (t) {
        if (usadas.indexOf(t.k) >= 0) return;
        var puntos = 0;
        if (t.k === k) puntos = 100;
        else if (t.k.indexOf(k) === 0) puntos = 80;
        else if (palabras(t.nombre).some(function (w) { return w.indexOf(k) === 0; })) puntos = 70;
        else if (k.length >= 2 && t.k.indexOf(k) >= 0) puntos = 50;
        else if (clave(t.categoria).indexOf(k) === 0) puntos = 20;
        if (puntos) lista.push({ nombre: t.nombre, categoria: t.categoria, puntos: puntos });
      });
      lista.sort(function (a, b) { return b.puntos - a.puntos || a.nombre.localeCompare(b.nombre); });
      if (!lista.length && k.length >= 3) {
        var mejor = null, dMin = 99;
        catalogo.forEach(function (t) {
          if (usadas.indexOf(t.k) >= 0) return;
          var tope = Math.min(k.length, t.k.length) >= 6 ? 2 : 1;
          var d = distancia(k, t.k);
          if (d <= tope && d < dMin) { dMin = d; mejor = t; }
        });
        if (mejor) lista.push({ nombre: mejor.nombre, categoria: mejor.categoria, sugerencia: true });
      }
      return lista.slice(0, 8);
    }

    function resaltar(nombre, q) {
      var k = String(q || "").trim().toLowerCase();
      if (!k) return esc(nombre);
      var i = nombre.toLowerCase().indexOf(k);
      if (i < 0) return esc(nombre);
      return esc(nombre.slice(0, i)) + "<mark>" + esc(nombre.slice(i, i + k.length)) + "</mark>" + esc(nombre.slice(i + k.length));
    }

    function opcionHtml(op, i, q) {
      return '<div class="ti-opt' + (i === activo ? " is-active" : "") + '" role="option" id="' + uid + "-o" + i + '" data-i="' + i + '"' +
        ' aria-selected="' + (i === activo ? "true" : "false") + '">' +
        logoHtml(op.nombre) +
        '<span class="ti-opt-name">' + (op.sugerencia ? "¿Quisiste decir <strong>" + esc(op.nombre) + "</strong>?" : resaltar(op.nombre, q)) + "</span>" +
        '<span class="ti-opt-cat">' + esc(op.categoria) + "</span></div>";
    }

    function pintarPanel() {
      if (document.activeElement !== input) { cerrar(); return; }
      var q = input.value.trim();
      var html = "";
      if (seleccion.length >= max) {
        opciones = [];
        html = '<div class="ti-empty">Llegaste al máximo de ' + max + " tecnologías. Quita una para agregar otra.</div>";
      } else if (!q) {
        // Sin texto: catálogo completo agrupado por categoría.
        var usadas = seleccionadas();
        var grupos = {};
        opciones = [];
        catalogo.forEach(function (t) {
          if (usadas.indexOf(t.k) >= 0) return;
          (grupos[t.categoria] = grupos[t.categoria] || []).push(t);
        });
        Object.keys(grupos).sort().forEach(function (cat) {
          html += '<div class="ti-group" role="presentation">' + esc(cat) + "</div>";
          grupos[cat].forEach(function (t) {
            var i = opciones.length;
            opciones.push({ nombre: t.nombre, categoria: t.categoria });
            html += opcionHtml(opciones[i], i, "");
          });
        });
        if (!opciones.length) html = '<div class="ti-empty">Ya agregaste todas las tecnologías del catálogo.</div>';
      } else {
        opciones = buscar(q);
        if (activo >= opciones.length) activo = opciones.length - 1;
        if (activo < 0 && opciones.length) activo = 0;
        html = opciones.map(function (op, i) { return opcionHtml(op, i, q); }).join("");
        if (!opciones.length) {
          html = '<div class="ti-empty"><strong>“' + esc(q) + "”</strong> no está en el catálogo." +
            '<span>Revisa cómo se escribe o pide al Administrador que la agregue al Catálogo de habilidades.</span></div>';
        }
      }
      panel.innerHTML = html;
      panel.hidden = false;
      input.setAttribute("aria-expanded", "true");
      if (activo >= 0 && opciones[activo]) {
        input.setAttribute("aria-activedescendant", uid + "-o" + activo);
        var el = panel.querySelector('[data-i="' + activo + '"]');
        if (el) el.scrollIntoView({ block: "nearest" });
      } else {
        input.removeAttribute("aria-activedescendant");
      }
    }

    function cerrar() {
      panel.hidden = true;
      input.setAttribute("aria-expanded", "false");
      input.removeAttribute("aria-activedescendant");
    }

    function mover(delta) {
      if (panel.hidden) { activo = -1; pintarPanel(); }
      if (!opciones.length) return;
      activo = (activo + delta + opciones.length) % opciones.length;
      pintarPanel();
    }

    /** Agrega la opción resaltada; si no hay, intenta con lo escrito. */
    function confirmar() {
      if (activo >= 0 && opciones[activo]) return agregar(opciones[activo].nombre);
      var q = input.value.trim();
      if (!q) return false;
      if (porClave[clave(q)]) return agregar(q);
      avisar("<strong>“" + esc(q) + "”</strong> no está en el catálogo.", "error");
      raiz.classList.remove("is-shake"); void raiz.offsetWidth; raiz.classList.add("is-shake");
      clearTimeout(raiz._shakeT); raiz._shakeT = setTimeout(function () { raiz.classList.remove("is-shake"); }, 600);
      return false;
    }

    // ── Eventos ──
    caja.addEventListener("mousedown", function (e) {
      if (e.target.closest(".ti-remove")) return;
      if (e.target !== input) { e.preventDefault(); input.focus(); }
    });
    chips.addEventListener("click", function (e) {
      var b = e.target.closest("[data-quitar]");
      if (b) quitar(parseInt(b.getAttribute("data-quitar"), 10));
    });
    panel.addEventListener("mousedown", function (e) { e.preventDefault(); });
    panel.addEventListener("click", function (e) {
      var op = e.target.closest(".ti-opt");
      if (op) { activo = parseInt(op.getAttribute("data-i"), 10); confirmar(); }
    });
    panel.addEventListener("mousemove", function (e) {
      var op = e.target.closest(".ti-opt");
      if (!op) return;
      var i = parseInt(op.getAttribute("data-i"), 10);
      if (i !== activo) {
        panel.querySelectorAll(".ti-opt.is-active").forEach(function (x) { x.classList.remove("is-active"); x.setAttribute("aria-selected", "false"); });
        op.classList.add("is-active"); op.setAttribute("aria-selected", "true");
        activo = i;
      }
    });
    input.addEventListener("focus", function () { raiz.classList.add("is-focus"); activo = -1; pintarPanel(); });
    input.addEventListener("blur", function () {
      raiz.classList.remove("is-focus");
      armadoBorrar = false;
      pintarChips();
      setTimeout(cerrar, 0);
    });
    input.addEventListener("input", function () {
      armadoBorrar = false;
      activo = input.value.trim() ? 0 : -1;
      if (/[,;]/.test(input.value)) {               // escribió una coma: agregar lo anterior
        var partes = input.value.split(/[,;]/);
        input.value = partes.pop();
        partes.forEach(function (p) { if (p.trim() && !agregar(p)) { var r = buscar(p); if (r[0]) agregar(r[0].nombre); } });
      }
      avisar(null);
      pintarPanel();
    });
    input.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") { e.preventDefault(); mover(1); }
      else if (e.key === "ArrowUp") { e.preventDefault(); mover(-1); }
      else if (e.key === "Enter") { e.preventDefault(); if (!panel.hidden || input.value.trim()) confirmar(); }
      else if (e.key === "Tab" && input.value.trim()) { e.preventDefault(); confirmar(); }
      else if (e.key === "Escape" && !panel.hidden) { e.preventDefault(); e.stopPropagation(); cerrar(); }
      else if (e.key === "Backspace" && !input.value && seleccion.length) {
        e.preventDefault();
        if (armadoBorrar) { quitar(seleccion.length - 1); }
        else {
          armadoBorrar = true;
          pintarChips();
          avisar("Presiona Backspace otra vez para quitar <strong>" + esc(seleccion[seleccion.length - 1].nombre) + "</strong>.", "info");
        }
      }
    });
    input.addEventListener("paste", function (e) {
      var texto = (e.clipboardData || window.clipboardData).getData("text");
      if (!/[,;\n\t]/.test(texto)) return;           // un solo valor: el flujo normal lo resuelve
      e.preventDefault();
      var fuera = [];
      texto.split(/[,;\n\t]+/).forEach(function (p) {
        p = p.trim();
        if (!p) return;
        if (!agregar(p)) {
          var r = buscar(p);
          if (r.length && !r[0].sugerencia && r[0].puntos >= 70) agregar(r[0].nombre); else fuera.push(p);
        }
      });
      if (fuera.length) avisar("No están en el catálogo: <strong>" + fuera.map(esc).join(", ") + "</strong>.", "error");
    });

    function setValues(lista) {
      seleccion = [];
      (lista || []).forEach(function (n) {
        n = String(n || "").trim();
        if (!n) return;
        var t = porClave[clave(n)];
        var nombre = t ? t.nombre : n;
        if (seleccion.some(function (s) { return clave(s.nombre) === clave(nombre); })) return;
        seleccion.push({ nombre: nombre, heredada: !t });
      });
      pintarChips();
      if (seleccion.some(function (s) { return s.heredada; })) {
        avisar("Los chips con borde punteado no están en el catálogo: se conservan porque el proyecto ya los tenía.", "info");
      } else {
        avisar(null);
      }
    }

    setValues(o.valores);
    return {
      el: raiz,
      getValues: function () { return seleccion.map(function (s) { return s.nombre; }); },
      setValues: setValues,
      focus: function () { input.focus(); }
    };
  }

  window.SBAI.techInput = { create: createTechInput };

  // ═══════════════════════════ Rango de fechas ═══════════════════════════

  var MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  var DIAS = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"];

  function pad(n) { return n < 10 ? "0" + n : "" + n; }
  function aFecha(iso) { var p = (iso || "").split("-"); return p.length === 3 ? new Date(+p[0], +p[1] - 1, +p[2]) : null; }
  function aIso(d) { return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()); }
  function sumarDias(iso, n) { var d = aFecha(iso); d.setDate(d.getDate() + n); return aIso(d); }
  function sumarMeses(iso, n) {
    var d = aFecha(iso), dia = d.getDate();
    d.setDate(1); d.setMonth(d.getMonth() + n);
    var ultimo = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    d.setDate(Math.min(dia, ultimo));
    return aIso(d);
  }
  function diasEntre(a, b) { return Math.round((aFecha(b) - aFecha(a)) / 86400000); }
  function fechaCorta(iso) { var d = aFecha(iso); return DIAS[d.getDay()] + " " + d.getDate() + " " + MESES[d.getMonth()] + (d.getFullYear() !== new Date().getFullYear() ? " " + d.getFullYear() : ""); }
  function duracionTexto(dias) {
    if (dias < 14) return dias + (dias === 1 ? " día" : " días");
    if (dias < 60) {
      var s = Math.floor(dias / 7), r = dias % 7;
      return s + " semanas" + (r ? " y " + r + (r === 1 ? " día" : " días") : "");
    }
    var m = Math.round(dias / 30.44);
    return "≈ " + m + (m === 1 ? " mes" : " meses");
  }

  function createDateRange(o) {
    var inicio = o.inicio, fin = o.fin, resumen = o.resumen;
    var minDias = o.diasMinimos || 7;
    var maxDias = o.diasMaximos || 1095;
    var diasMaxInicio = o.diasMaximoInicio || 365;
    var limiteInicio = sumarDias(o.minInicio || aIso(new Date()), diasMaxInicio);
    var aviso = "";            // mensaje temporal (ej. ajuste automático)
    if (o.minInicio) inicio.min = o.minInicio;
    inicio.max = limiteInicio;

    var ATAJOS = [["2 semanas", 14, 0], ["1 mes", 0, 1], ["3 meses", 0, 3], ["6 meses", 0, 6]];
    if (o.atajos) {
      o.atajos.innerHTML = '<span class="dr-presets-label">Duración rápida:</span>' + ATAJOS.map(function (a, i) {
        return '<button type="button" class="dr-preset" data-atajo="' + i + '">' + a[0] + "</button>";
      }).join("");
      o.atajos.addEventListener("click", function (e) {
        var b = e.target.closest("[data-atajo]");
        if (!b) return;
        if (!inicio.value) {
          marcar(inicio, "Elige primero la fecha de inicio.");
          inicio.focus();
          return;
        }
        var a = ATAJOS[+b.getAttribute("data-atajo")];
        fin.value = a[2] ? sumarMeses(inicio.value, a[2]) : sumarDias(inicio.value, a[1]);
        duracionPrevia = diasEntre(inicio.value, fin.value);
        aviso = "";
        limpiar(fin);
        actualizar();
      });
    }

    function marcar(input, msg) {
      var field = input.closest(".field");
      if (!field) return;
      field.classList.add("has-error");
      var err = field.querySelector(".field-error");
      if (!err) { err = document.createElement("div"); err.className = "field-error"; field.appendChild(err); }
      err.textContent = msg;
      err.style.display = "flex";
    }
    function limpiar(input) {
      var field = input.closest(".field");
      if (!field) return;
      field.classList.remove("has-error");
      var err = field.querySelector(".field-error");
      if (err) err.style.display = "none";
    }

    function actualizar() {
      if (inicio.value) { fin.min = sumarDias(inicio.value, minDias); fin.max = sumarDias(inicio.value, maxDias); }
      else { fin.removeAttribute("min"); fin.removeAttribute("max"); }
      if (o.atajos) o.atajos.querySelectorAll(".dr-preset").forEach(function (b, i) {
        var a = ATAJOS[i];
        var destino = inicio.value ? (a[2] ? sumarMeses(inicio.value, a[2]) : sumarDias(inicio.value, a[1])) : null;
        b.classList.toggle("is-active", !!destino && destino === fin.value);
      });
      if (!resumen) return;
      var html, tono = "";
      if (!inicio.value && !fin.value) {
        html = "Elige el inicio y la entrega esperada (entre " + minDias + " días y " + Math.round(maxDias / 365) + " años).";
      } else if (!inicio.value) {
        html = "Falta la fecha de inicio."; tono = "warn";
      } else if (!fin.value) {
        html = "Falta la fecha de entrega esperada. Usa un atajo o elige una fecha desde el " + fechaCorta(fin.min) + "."; tono = "warn";
      } else {
        var d = diasEntre(inicio.value, fin.value);
        if (d < minDias) {
          html = "La entrega debe ser al menos " + minDias + " días después del inicio (desde el " + fechaCorta(fin.min) + ")."; tono = "error";
        } else if (d > maxDias) {
          html = "La entrega no puede ser más de " + Math.round(maxDias / 365) + " años después del inicio (hasta el " + fechaCorta(fin.max) + ")."; tono = "error";
        } else {
          html = '<span class="dr-sum-main">Duración: <strong>' + duracionTexto(d) + "</strong></span>" +
            '<span class="dr-sum-sep">·</span><span>Entrega: <strong>' + fechaCorta(fin.value) + "</strong></span>";
          tono = "ok";
        }
      }
      if (aviso) html += '<span class="dr-sum-note">' + aviso + "</span>";
      resumen.innerHTML = '<svg class="icon" aria-hidden="true"><use href="/img/icons.svg#icon-calendar"></use></svg><span class="dr-sum-text">' + html + "</span>";
      resumen.className = "dr-summary" + (tono ? " is-" + tono : "");
    }

    var duracionPrevia = inicio.value && fin.value ? diasEntre(inicio.value, fin.value) : null;
    var inicioPrevio = inicio.value;
    inicio.addEventListener("change", function () {
      limpiar(inicio);
      if (inicio.value === inicioPrevio) return; // mismo valor: no borrar el aviso
      inicioPrevio = inicio.value;
      aviso = "";
      if (inicio.value && fin.value && diasEntre(inicio.value, fin.value) < minDias) {
        // Mantener la duración que tenía (si era válida) en vez de dejar una entrega imposible.
        var dias = duracionPrevia && duracionPrevia >= minDias ? duracionPrevia : minDias;
        fin.value = sumarDias(inicio.value, dias);
        aviso = "Movimos la entrega para mantener " + (dias === minDias ? "el mínimo de " + minDias + " días" : "la misma duración") + ".";
        limpiar(fin);
      }
      if (inicio.value && fin.value) duracionPrevia = diasEntre(inicio.value, fin.value);
      actualizar();
    });
    fin.addEventListener("change", function () {
      limpiar(fin);
      aviso = "";
      if (inicio.value && fin.value) duracionPrevia = diasEntre(inicio.value, fin.value);
      actualizar();
    });
    actualizar();

    return {
      validar: function () {
        limpiar(inicio); limpiar(fin);
        if (!inicio.value) { marcar(inicio, "Elige la fecha de inicio."); return { campo: inicio, mensaje: "Elige la fecha de inicio." }; }
        if (o.minInicio && inicio.value < o.minInicio) { marcar(inicio, "No puede ser anterior a hoy."); return { campo: inicio, mensaje: "La fecha de inicio no puede ser anterior a hoy." }; }
        if (inicio.value > limiteInicio) { marcar(inicio, "No puede planificarse a más de " + Math.round(diasMaxInicio / 365) + " año(s) desde hoy."); return { campo: inicio, mensaje: "La fecha de inicio no puede planificarse a más de " + Math.round(diasMaxInicio / 365) + " año(s) desde hoy." }; }
        if (!fin.value) { marcar(fin, "Elige la fecha de entrega esperada."); return { campo: fin, mensaje: "Elige la fecha de entrega esperada." }; }
        if (diasEntre(inicio.value, fin.value) < minDias) {
          marcar(fin, "Debe ser al menos " + minDias + " días después del inicio.");
          return { campo: fin, mensaje: "Debe haber al menos " + minDias + " días entre el inicio y la entrega." };
        }
        if (diasEntre(inicio.value, fin.value) > maxDias) {
          marcar(fin, "No puede ser más de " + Math.round(maxDias / 365) + " años después del inicio.");
          return { campo: fin, mensaje: "La duración no puede superar los " + Math.round(maxDias / 365) + " años." };
        }
        return null;
      },
      marcar: marcar,
      limpiar: limpiar
    };
  }

  window.SBAI.dateRange = { create: createDateRange, sumarDias: sumarDias, aIso: aIso };

  // ═══════════════════════════ Habilidades requeridas ═══════════════════════════
  // SBAI.habilidadesRequeridas.create(host, opciones)
  //   Lista editable de {habilidad, nivel 1-5} para "Nuevo proyecto" (modelo
  //   v7 fase 5): filas con <select> de habilidad (catálogo con id real,
  //   window.SBAI_CATALOGO_HAB) + <select> de nivel + quitar, y un botón
  //   "Agregar habilidad". Es manual o se pre-llena con "Extraer con IA".
  //     opciones.catalogo [{id, nombre, categoria}]
  //     opciones.valores  [{habilidadId, nombre, nivelRequerido}] iniciales
  //   Devuelve {getValues() -> [{habilidadId, nivelRequerido}], setValues(lista)}.
  function createHabilidadesRequeridas(host, opciones) {
    var o = opciones || {};
    var catalogo = o.catalogo || [];
    var filas = [];
    host.innerHTML = '<div class="hr-filas"></div>' +
      '<button type="button" class="btn btn-secondary btn-small hr-agregar" style="margin-top:var(--space-2);">' +
      '<svg class="icon"><use href="/img/icons.svg#icon-plus"></use></svg> Agregar habilidad</button>';
    var filasEl = host.querySelector(".hr-filas");
    var btnAgregar = host.querySelector(".hr-agregar");

    function porId(id) {
      for (var i = 0; i < catalogo.length; i++) if (String(catalogo[i].id) === String(id)) return catalogo[i];
      return null;
    }

    function opcionesDisponibles(idActual) {
      var usados = filas.map(function (f) { return f.habilidadId; }).filter(function (id) { return id && id !== idActual; });
      return catalogo.filter(function (h) { return usados.indexOf(h.id) === -1; });
    }

    function refrescarSelects() {
      filas.forEach(function (f) {
        var disponibles = opcionesDisponibles(f.habilidadId);
        var actual = f.habilidadId;
        f.select.innerHTML = '<option value="">Elige una habilidad…</option>' + disponibles.map(function (h) {
          return '<option value="' + h.id + '"' + (String(h.id) === String(actual) ? " selected" : "") + '>' +
            esc(h.nombre) + (h.categoria ? " (" + esc(h.categoria) + ")" : "") + '</option>';
        }).join("");
      });
    }

    function quitarFila(fila) {
      filas = filas.filter(function (f) { return f !== fila; });
      fila.el.remove();
      refrescarSelects();
    }

    function agregarFila(habilidadId, nivel) {
      var fila = { habilidadId: habilidadId || "", nivelRequerido: nivel || 3 };
      var row = document.createElement("div");
      row.className = "hr-fila";
      row.style.display = "flex";
      row.style.gap = "8px";
      row.style.alignItems = "flex-start";
      row.style.marginBottom = "8px";
      row.innerHTML =
        '<select class="input hr-select-habilidad" style="flex:1;"></select>' +
        '<select class="input hr-select-nivel" style="width:150px;">' +
        [1, 2, 3, 4, 5].map(function (n) {
          return '<option value="' + n + '">Nivel ' + n + (n === 1 ? " (básico)" : n === 5 ? " (experto)" : "") + '</option>';
        }).join("") +
        '</select>' +
        '<button type="button" class="btn btn-secondary btn-icon hr-quitar" aria-label="Quitar habilidad">' +
        '<svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button>';
      filasEl.appendChild(row);
      fila.el = row;
      fila.select = row.querySelector(".hr-select-habilidad");
      fila.nivelSelect = row.querySelector(".hr-select-nivel");
      fila.nivelSelect.value = String(fila.nivelRequerido);
      filas.push(fila);
      refrescarSelects();
      fila.select.addEventListener("change", function () { fila.habilidadId = fila.select.value ? parseInt(fila.select.value, 10) : ""; refrescarSelects(); });
      fila.nivelSelect.addEventListener("change", function () { fila.nivelRequerido = parseInt(fila.nivelSelect.value, 10) || 3; });
      row.querySelector(".hr-quitar").addEventListener("click", function () { quitarFila(fila); });
      return fila;
    }

    btnAgregar.addEventListener("click", function () { agregarFila("", 3); });
    (o.valores || []).forEach(function (v) { if (v && v.habilidadId) agregarFila(v.habilidadId, v.nivelRequerido || 3); });

    return {
      getValues: function () {
        return filas.filter(function (f) { return f.habilidadId; })
          .map(function (f) { return { habilidadId: f.habilidadId, nivelRequerido: f.nivelRequerido || 3 }; });
      },
      setValues: function (lista) {
        filas.slice().forEach(quitarFila);
        (lista || []).forEach(function (v) { if (v && v.habilidadId) agregarFila(v.habilidadId, v.nivelRequerido || 3); });
      }
    };
  }

  window.SBAI.habilidadesRequeridas = { create: createHabilidadesRequeridas };
})();
