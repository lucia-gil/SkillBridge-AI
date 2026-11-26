/**
 * rm-reportes.js — Dashboard de Reportes del Resource Manager.
 *
 * Dibuja en SVG (sin librerías ni CDN) los gráficos a partir de RR_DATA:
 *  - donas: estado de carga, proyectos por estado, cobertura de habilidades y
 *    resultado de propuestas; con tooltip, leyenda con cifras y teclado;
 *    clic en "Estado de carga" filtra al equipo (en el servidor, así el
 *    Excel/PDF coincide); clic en "Cobertura" filtra la tabla de habilidades;
 *  - barras: carga promedio por cargo (con la línea del límite), brechas de
 *    habilidades (demanda vs. capacidad) y origen de las propuestas.
 * Además: filtros que se aplican solos, paginación de tablas y filtro por riesgo.
 */
(function () {
  "use strict";
  var D = window.RR_DATA || {};
  var root = document.querySelector(".rr");
  if (!root) return;
  var tip = document.getElementById("rr-tooltip");
  var NS = "http://www.w3.org/2000/svg";

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function color(clave) {
    var v = getComputedStyle(root).getPropertyValue("--rr-" + clave).trim();
    return v || "#8a93a6";
  }
  function el(tag, attrs) {
    var n = document.createElementNS(NS, tag);
    Object.keys(attrs || {}).forEach(function (k) { n.setAttribute(k, attrs[k]); });
    return n;
  }
  function pct(v, total) { return total ? Math.round(v * 100 / total) : 0; }

  /* ---------------- Tooltip ---------------- */
  function mostrarTip(html, x, y) {
    tip.innerHTML = html;
    tip.hidden = false;
    var w = tip.offsetWidth, h = tip.offsetHeight;
    var left = Math.min(window.innerWidth - w - 8, x + 14), top = y - h - 12;
    if (top < 8) top = y + 16;
    tip.style.left = Math.max(8, left) + "px";
    tip.style.top = top + "px";
  }
  function ocultarTip() { tip.hidden = true; }

  /* ---------------- Dona ---------------- */
  function dona(cont) {
    var datos = (D[cont.getAttribute("data-donut")] || []).slice();
    var unidad = cont.getAttribute("data-unidad") || "";
    var accion = cont.getAttribute("data-accion");
    var total = datos.reduce(function (a, s) { return a + s.valor; }, 0);
    var R = 66, STROKE = 20, C = 2 * Math.PI * R, GAP = datos.filter(function (s) { return s.valor > 0; }).length > 1 ? 3 : 0;
    var activos = accion === "filtro" ? (D.estadosActivos || []) : [];

    var svg = el("svg", { viewBox: "0 0 168 168", role: "img", "aria-label": cont.closest(".rr-tile").querySelector("h3").textContent + ": " +
      datos.map(function (s) { return s.etiqueta + " " + s.valor; }).join(", ") });
    svg.appendChild(el("circle", { class: "track", cx: 84, cy: 84, r: R, "stroke-width": STROKE }));
    var g = el("g", { transform: "rotate(-90 84 84)" });
    svg.appendChild(g);
    var offset = 0, segs = [];
    datos.forEach(function (s, i) {
      if (!s.valor) { segs.push(null); return; }
      var largo = C * s.valor / total;
      var c = el("circle", {
        class: "seg", cx: 84, cy: 84, r: R, stroke: color(s.clave), "stroke-width": STROKE,
        "stroke-dasharray": Math.max(0.5, largo - GAP) + " " + (C - Math.max(0.5, largo - GAP)),
        "stroke-dashoffset": -offset, tabindex: 0, role: "button",
        "aria-label": s.etiqueta + ": " + s.valor + " " + unidad + " (" + pct(s.valor, total) + "%)"
      });
      if (activos.length && activos.indexOf(s.clave) < 0) c.classList.add("is-off");
      offset += largo;
      g.appendChild(c);
      segs.push(c);
    });
    var tVal = el("text", { class: "c-val", x: 84, y: 88, "text-anchor": "middle" });
    tVal.textContent = total;
    var tLbl = el("text", { class: "c-lbl", x: 84, y: 108, "text-anchor": "middle" });
    tLbl.textContent = unidad;
    svg.appendChild(tVal); svg.appendChild(tLbl);

    var ul = document.createElement("ul");
    ul.className = "rr-legend";
    datos.forEach(function (s, i) {
      var li = document.createElement("li");
      var on = activos.indexOf(s.clave) >= 0;
      li.innerHTML = '<button type="button" class="' + (on ? "is-on" : (activos.length ? "is-off" : "")) + '" data-i="' + i + '">' +
        '<i style="background:' + color(s.clave) + '"></i><span>' + esc(s.etiqueta) + "</span><b>" + s.valor + "</b><small>" + pct(s.valor, total) + "%</small></button>";
      ul.appendChild(li);
    });
    cont.innerHTML = "";
    cont.appendChild(svg);
    cont.appendChild(ul);

    function resaltar(i, ev) {
      var s = datos[i];
      cont.classList.add("has-hover");
      segs.forEach(function (c, j) { if (c) c.classList.toggle("is-hover", j === i); });
      ul.querySelectorAll("button").forEach(function (b, j) { b.classList.toggle("is-hover", j === i); });
      tVal.textContent = s.valor;
      tLbl.textContent = s.etiqueta.toLowerCase();
      if (ev) mostrarTip('<div class="tt-row"><i style="background:' + color(s.clave) + '"></i><b>' + esc(s.etiqueta) + "</b></div>" +
        s.valor + " " + esc(unidad) + " · " + pct(s.valor, total) + "% del total" +
        (accion === "filtro" ? "<br><small>Clic para filtrar</small>" : ""), ev.clientX, ev.clientY);
    }
    function limpiar() {
      cont.classList.remove("has-hover");
      segs.forEach(function (c) { if (c) c.classList.remove("is-hover"); });
      ul.querySelectorAll("button").forEach(function (b) { b.classList.remove("is-hover"); });
      tVal.textContent = total; tLbl.textContent = unidad;
      ocultarTip();
    }
    function elegir(i) {
      var s = datos[i];
      if (accion === "filtro") filtrarEstado(s.clave);
      else if (accion === "habilidades") filtrarCobertura(s.etiqueta);
    }
    segs.forEach(function (c, i) {
      if (!c) return;
      c.addEventListener("mousemove", function (e) { resaltar(i, e); });
      c.addEventListener("mouseleave", limpiar);
      c.addEventListener("focus", function () { resaltar(i); });
      c.addEventListener("blur", limpiar);
      c.addEventListener("click", function () { elegir(i); });
      c.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); elegir(i); } });
    });
    ul.querySelectorAll("button").forEach(function (b) {
      var i = +b.getAttribute("data-i");
      b.addEventListener("mouseenter", function () { if (datos[i].valor) resaltar(i); });
      b.addEventListener("mouseleave", limpiar);
      b.addEventListener("click", function () { elegir(i); });
    });
    if (!accion) ul.querySelectorAll("button").forEach(function (b) { b.style.cursor = "default"; });
  }

  /* ---------------- Barras ---------------- */
  function barras(cont) {
    var tipo = cont.getAttribute("data-bars");
    var datos = D[tipo] || [];
    if (!datos.length) {
      cont.innerHTML = '<p class="rr-empty-line">' + (tipo === "brechas" ? "Todas las habilidades pedidas están cubiertas." : "Sin datos con estos filtros.") + "</p>";
      return;
    }
    var html = "";
    if (tipo === "cargos") {
      var escala = Math.max(100, Math.max.apply(null, datos.map(function (b) { return b.valor; })));
      datos.forEach(function (b) {
        html += '<div class="rr-bar" data-tip="' + esc("<b>" + esc(b.etiqueta) + "</b>Carga promedio " + b.valor + "% · " + b.detalle) + '">' +
          '<span class="rr-bar-lbl">' + esc(b.etiqueta) + '</span><span class="rr-bar-track">' +
          '<span class="rr-bar-fill' + (b.valor > D.limite ? " is-over" : "") + '" style="width:0" data-w="' + (b.valor * 100 / escala) + '"></span></span>' +
          '<span class="rr-bar-val"><b>' + b.valor + "%</b> · " + b.referencia + "</span></div>";
      });
    } else if (tipo === "brechas") {
      var max = Math.max.apply(null, datos.map(function (b) { return b.valor; }));
      datos.forEach(function (b) {
        html += '<div class="rr-bar" data-tip="' + esc("<b>" + esc(b.etiqueta) + "</b>La piden " + b.valor + " proyecto(s) · " + esc(b.detalle)) + '">' +
          '<span class="rr-bar-lbl">' + esc(b.etiqueta) + '</span><span class="rr-bar-track">' +
          '<span class="rr-bar-fill is-demand" style="width:0" data-w="' + (b.valor * 100 / max) + '"></span>' +
          '<span class="rr-bar-fill is-supply" style="width:0" data-w="' + (Math.min(b.valor, b.referencia) * 100 / max) + '"></span></span>' +
          '<span class="rr-bar-val"><b>' + b.referencia + "</b> de " + b.valor + "</span></div>";
      });
      html += '<div class="rr-bars-legend"><span><i style="background:color-mix(in srgb, var(--rr-sobre) 26%, transparent)"></i>Proyectos que la piden</span>' +
        '<span><i style="background:var(--rr-navy)"></i>Personas con capacidad libre</span></div>';
    } else if (tipo === "origen") {
      var tot = datos.reduce(function (a, s) { return a + s.valor; }, 0);
      datos.forEach(function (s) {
        html += '<div class="rr-bar" data-tip="' + esc("<b>" + esc(s.etiqueta) + "</b>" + s.valor + " propuesta(s) · " + pct(s.valor, tot) + "%") + '">' +
          '<span class="rr-bar-lbl">' + esc(s.etiqueta) + '</span><span class="rr-bar-track"><span class="rr-bar-fill" style="width:0" data-w="' + pct(s.valor, tot) + '"></span></span>' +
          '<span class="rr-bar-val"><b>' + s.valor + "</b> · " + pct(s.valor, tot) + "%</span></div>";
      });
    }
    cont.innerHTML = html;
    requestAnimationFrame(function () {
      cont.querySelectorAll("[data-w]").forEach(function (f) { f.style.width = Math.max(1, +f.getAttribute("data-w")) + "%"; });
    });
    cont.querySelectorAll("[data-tip]").forEach(function (b) {
      b.addEventListener("mousemove", function (e) { mostrarTip(b.getAttribute("data-tip"), e.clientX, e.clientY); });
      b.addEventListener("mouseleave", ocultarTip);
    });
  }

  /* ---------------- Filtros ---------------- */
  var form = document.getElementById("rr-filtros");
  var enviando = false;
  function enviar() {
    if (!form || enviando) return;
    enviando = true;
    // Al aplicar filtros se vuelve a la misma pestaña
    form.action = form.getAttribute("action").split("#")[0] + (location.hash || "");
    form.classList.add("is-loading");
    Array.from(form.elements).forEach(function (e) {
      if (!e.name) return;
      if ((e.name === "q" && !e.value.trim()) || (e.name === "min" && e.value === "0") ||
          (["cargo", "proyecto", "categoria"].indexOf(e.name) >= 0 && !e.value)) e.disabled = true;
    });
    form.submit();
  }
  function filtrarEstado(clave) {
    var sel = document.getElementById("rr-estado");
    if (!sel) return;
    var opt = Array.from(sel.options).filter(function (o) { return o.value === clave; })[0];
    if (!opt) return;
    var marcados = Array.from(sel.options).filter(function (o) { return o.selected; });
    // Un clic: ver solo ese estado; otro clic sobre el mismo: quitar el filtro.
    var soloEse = marcados.length === 1 && marcados[0] === opt;
    Array.from(sel.options).forEach(function (o) { o.selected = !soloEse && o === opt; });
    enviar();
  }
  function filtrarCobertura(etiqueta) {
    var filas = Array.from(document.querySelectorAll('[data-rr-row="habilidades"]'));
    var actual = root.getAttribute("data-cob") === etiqueta ? "" : etiqueta;
    root.setAttribute("data-cob", actual);
    filas.forEach(function (f) { f.setAttribute("data-oculta", actual && f.getAttribute("data-cob") !== actual ? "1" : "0"); });
    paginadores.habilidades && paginadores.habilidades(1);
    abrirPestana("habilidades", true);
  }
  if (form) {
    var q = form.querySelector('input[name="q"]'), espera;
    q.addEventListener("input", function () { clearTimeout(espera); espera = setTimeout(enviar, 700); });
    q.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); clearTimeout(espera); enviar(); } });
    var rango = document.getElementById("rr-min"), salida = document.getElementById("rr-min-out");
    rango.addEventListener("input", function () { salida.textContent = rango.value + "%"; });
    rango.addEventListener("change", enviar);
    form.querySelector('input[name="brecha"]').addEventListener("change", enviar);
    form.querySelectorAll("select[data-rr-auto]").forEach(function (s) { s.addEventListener("change", enviar); });
    document.getElementById("rr-estado").addEventListener("change", function () { clearTimeout(espera); espera = setTimeout(enviar, 900); });
    var proyectos = document.getElementById("rr-proyectos");
    if (proyectos) proyectos.addEventListener("change", function () { clearTimeout(espera); espera = setTimeout(enviar, 900); });
  }

  /* ---------------- Paginación (8 filas) ---------------- */
  var paginadores = {};
  document.querySelectorAll("[data-pager]").forEach(function (pie) {
    var scope = pie.getAttribute("data-pager"), TAM = 8, pagina = 1;
    var prev = pie.querySelector("[data-prev]"), next = pie.querySelector("[data-next]");
    var label = pie.querySelector("[data-label]"), visibles = pie.querySelector("[data-visible]");
    function render(p) {
      if (p) pagina = p;
      var filas = Array.from(document.querySelectorAll('[data-rr-row="' + scope + '"]'));
      var validas = filas.filter(function (f) { return f.getAttribute("data-oculta") !== "1"; });
      var paginas = Math.max(1, Math.ceil(validas.length / TAM));
      pagina = Math.min(pagina, paginas);
      filas.forEach(function (f) { f.hidden = true; });
      validas.forEach(function (f, i) { f.hidden = i < (pagina - 1) * TAM || i >= pagina * TAM; });
      visibles.textContent = Math.max(0, Math.min(TAM, validas.length - (pagina - 1) * TAM));
      label.textContent = "Página " + pagina + " de " + paginas;
      prev.disabled = pagina === 1;
      next.disabled = pagina === paginas;
    }
    prev.addEventListener("click", function () { if (pagina > 1) render(pagina - 1); });
    next.addEventListener("click", function () { render(pagina + 1); });
    paginadores[scope] = render;
    render(1);
  });

  /* ---------------- Proyectos: filtro por riesgo ---------------- */
  var ctl = document.querySelector("[data-filtro-riesgo]");
  if (ctl) ctl.addEventListener("click", function (e) {
    var b = e.target.closest("button");
    if (!b) return;
    ctl.querySelectorAll("button").forEach(function (x) { x.classList.toggle("is-on", x === b); });
    var v = b.getAttribute("data-v");
    document.querySelectorAll("tr[data-riesgo]").forEach(function (tr) { tr.hidden = v && tr.getAttribute("data-riesgo") !== v; });
  });

  /* ---------------- Pestañas ---------------- */
  var tabs = Array.from(document.querySelectorAll(".rr-tabs [data-tab]"));
  function abrirPestana(nombre, desplazar) {
    var existe = tabs.some(function (t) { return t.getAttribute("data-tab") === nombre; });
    if (!existe) nombre = "resumen";
    tabs.forEach(function (t) {
      var on = t.getAttribute("data-tab") === nombre;
      t.setAttribute("aria-selected", on ? "true" : "false");
      t.tabIndex = on ? 0 : -1;
    });
    document.querySelectorAll("[data-panel]").forEach(function (p) { p.hidden = p.getAttribute("data-panel") !== nombre; });
    try { history.replaceState(null, "", location.pathname + location.search + (nombre === "resumen" ? "" : "#" + nombre)); } catch (e) { /* nada */ }
    if (desplazar) document.querySelector(".rr-tabs-wrap").scrollIntoView({ behavior: "smooth", block: "start" });
  }
  tabs.forEach(function (t, i) {
    t.addEventListener("click", function () { abrirPestana(t.getAttribute("data-tab")); });
    t.addEventListener("keydown", function (e) {
      var j = e.key === "ArrowRight" ? i + 1 : e.key === "ArrowLeft" ? i - 1 : null;
      if (j === null) return;
      e.preventDefault();
      var dest = tabs[(j + tabs.length) % tabs.length];
      abrirPestana(dest.getAttribute("data-tab"));
      dest.focus();
    });
  });
  abrirPestana((location.hash || "#resumen").slice(1));

  /* ---------------- Logos de tecnologías ---------------- */
  if (window.SBAI && window.SBAI.techIcons) {
    root.querySelectorAll("[data-tech-logo]").forEach(function (n) {
      var html = window.SBAI.techIcons.logo(n.getAttribute("data-tech-logo"));
      if (html) n.innerHTML = html;
    });
  }

  /* ---------------- Dibujar (y redibujar al cambiar de tema) ---------------- */
  function dibujar() {
    document.querySelectorAll("[data-donut]").forEach(dona);
    document.querySelectorAll("[data-bars]").forEach(barras);
  }
  dibujar();
  new MutationObserver(function () { dibujar(); }).observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
})();
