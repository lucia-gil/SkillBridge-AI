/*
 * cursos.js — Catálogo de formación de SkillBridge.
 *
 * Un solo motor para las dos vistas:
 *  - modo "catalogo": Colaborador, PM y RM (compartido/cursos.html) — solo lo publicado.
 *  - modo "admin":    Administrador (administrador/cursos.html) — todo, con acciones.
 *
 * Los datos llegan como JSON en window.SBAI_CURSOS (inlining de Thymeleaf):
 *   { modo, cursos: [CursoVista...], misHabilidades: [ids], usuario }
 * Los filtros son <select class="select">, así que js/select-ui.js los convierte
 * en el selector propio del sitio (con buscador, grupos e íconos).
 * Guardados y progreso de rutas viven en localStorage (comodidad por persona).
 */
(function () {
  "use strict";

  var D = window.SBAI_CURSOS || {};
  var MODO = D.modo === "admin" ? "admin" : "catalogo";
  var CURSOS = Array.isArray(D.cursos) ? D.cursos : [];
  var MIS_HAB = new Set((D.misHabilidades || []).map(Number));
  var POR_ID = {};
  CURSOS.forEach(function (c) { POR_ID[c.id] = c; });

  // ───────────────────────── utilidades ─────────────────────────
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }
  function norm(s) { return String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").trim(); }
  function icono(id, cls) { return '<svg class="icon ' + (cls || "") + '" aria-hidden="true"><use href="/img/icons.svg#' + id + '"></use></svg>'; }
  function tech(nombre) { return nombre && window.SBAI && SBAI.techIcons ? SBAI.techIcons.logo(nombre) : ""; }
  function horas(h) { return h >= 100 ? Math.round(h) + " h" : h + (h === 1 ? " hora" : " h"); }

  // ───────────────────────── catálogos ─────────────────────────
  var TIPOS = {
    CURSO: { label: "Curso", plural: "Cursos", icon: "icon-graduation", cta: "Empezar curso" },
    VIDEO: { label: "Video", plural: "Videos", icon: "icon-play", cta: "Ver video" },
    RUTA: { label: "Ruta", plural: "Rutas", icon: "icon-route", cta: "Ver ruta" },
    ESPECIALIZACION: { label: "Especialización", plural: "Especializaciones", icon: "icon-layers", cta: "Ver programa" }
  };
  var NIVELES = { BASICO: ["Básico", 1], INTERMEDIO: ["Intermedio", 2], AVANZADO: ["Avanzado", 3], EXPERTO: ["Experto", 4] };
  var IDIOMAS = { ES: "Español", EN: "Inglés", PT: "Portugués" };
  var ESTADOS = { PUBLICADO: "Publicado", BORRADOR: "Borrador", ARCHIVADO: "Archivado" };

  // Logo de cada plataforma: color de marca + glifo (texto o SVG simple).
  var SVG_PLAY = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#fff" d="M9 7.2v9.6l7.6-4.8z"/></svg>';
  var SVG_MS = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#F25022" d="M4 4h7.4v7.4H4z"/><path fill="#7FBA00" d="M12.6 4H20v7.4h-7.4z"/><path fill="#00A4EF" d="M4 12.6h7.4V20H4z"/><path fill="#FFB900" d="M12.6 12.6H20V20h-7.4z"/></svg>';
  var SVG_RUTA = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="#fff" stroke-width="2.2" stroke-linecap="round"><circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M8 18h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7"/></svg>';
  var SVG_WEB = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="#fff" stroke-width="2"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 2.5 14.4 0 17M12 3.5c-2.5 2.6-2.5 14.4 0 17"/></svg>';
  var PLATAFORMAS = {
    youtube: { nombre: "YouTube", color: "#FF0033", glifo: SVG_PLAY, hosts: ["youtube.com", "youtu.be"] },
    coursera: { nombre: "Coursera", color: "#0056D2", glifo: "C", hosts: ["coursera.org"] },
    udemy: { nombre: "Udemy", color: "#A435F0", glifo: "U", hosts: ["udemy.com"] },
    edx: { nombre: "edX", color: "#02262B", glifo: "edX", hosts: ["edx.org"] },
    freecodecamp: { nombre: "freeCodeCamp", color: "#0A0A23", glifo: "fCC", hosts: ["freecodecamp.org"] },
    microsoft: { nombre: "Microsoft Learn", color: "#2B2B2B", glifo: SVG_MS, hosts: ["learn.microsoft.com", "microsoft.com"] },
    aws: { nombre: "AWS Skill Builder", color: "#232F3E", glifo: '<b style="color:#FF9900">aws</b>', hosts: ["skillbuilder.aws", "aws.training", "aws.amazon.com"] },
    google: { nombre: "Google", color: "#1A73E8", glifo: "G", hosts: ["cloudskillsboost.google", "grow.google", "developers.google.com", "google.com"] },
    roadmap: { nombre: "roadmap.sh", color: "#111827", glifo: SVG_RUTA, hosts: ["roadmap.sh"] },
    platzi: { nombre: "Platzi", color: "#0AE98A", glifo: '<b style="color:#121F3D">P</b>', hosts: ["platzi.com"] },
    linkedin: { nombre: "LinkedIn Learning", color: "#0A66C2", glifo: "in", hosts: ["linkedin.com"] },
    cisco: { nombre: "Cisco NetAcad", color: "#1BA0D7", glifo: "NA", hosts: ["netacad.com"] },
    ibm: { nombre: "IBM SkillsBuild", color: "#0F62FE", glifo: "IBM", hosts: ["skillsbuild.org", "ibm.com"] },
    kaggle: { nombre: "Kaggle", color: "#20BEFF", glifo: "k", hosts: ["kaggle.com"] },
    github: { nombre: "GitHub", color: "#181717", glifo: "GH", hosts: ["github.com", "github.io"] },
    oracle: { nombre: "Oracle", color: "#C74634", glifo: "O", hosts: ["oracle.com"] },
    otra: { nombre: "Sitio web", color: "#64748B", glifo: SVG_WEB, hosts: [] }
  };
  function plat(k) { return PLATAFORMAS[k] || PLATAFORMAS.otra; }
  function logoPlataforma(k, cls) {
    var p = plat(k);
    return '<span class="cx-plat ' + (cls || "") + '" style="--plat:' + p.color + '" title="' + esc(p.nombre) + '" aria-hidden="true">' + p.glifo + "</span>";
  }
  function detectarPlataforma(url) {
    try {
      var host = new URL(url).hostname.toLowerCase();
      for (var k in PLATAFORMAS) {
        if (PLATAFORMAS[k].hosts.some(function (h) { return host === h || host.endsWith("." + h); })) return k;
      }
    } catch (e) { /* URL incompleta mientras se escribe */ }
    return "otra";
  }
  function youtubeId(url) {
    var m = /(?:youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/.exec(url || "");
    return m ? m[1] : null;
  }
  // Íconos de plataforma también dentro del selector propio (select-ui.js).
  window.SBAI = window.SBAI || {};
  SBAI.selectIcons = SBAI.selectIcons || {};
  SBAI.selectIcons.plataforma = function (valor) { return valor ? logoPlataforma(valor, "cx-plat-sm") : ""; };

  // ─────────────────── guardados y progreso (por persona) ───────────────────
  var CLAVE = "sbai-cursos-" + (D.usuario || "anon");
  var mem = { guardados: [], progreso: {} };
  try { var m0 = JSON.parse(localStorage.getItem(CLAVE) || "{}"); mem.guardados = m0.guardados || []; mem.progreso = m0.progreso || {}; } catch (e) { /* sin storage */ }
  function persistir() { try { localStorage.setItem(CLAVE, JSON.stringify(mem)); } catch (e) { /* sin storage */ } }
  function guardado(id) { return mem.guardados.indexOf(id) >= 0; }
  function alternarGuardado(id) {
    var i = mem.guardados.indexOf(id);
    if (i >= 0) mem.guardados.splice(i, 1); else mem.guardados.push(id);
    persistir();
    return i < 0;
  }
  function hechos(c) { return (mem.progreso[c.id] || []).filter(function (i) { return i < (c.pasos || []).length; }); }
  function porcentaje(c) { var n = (c.pasos || []).length; return n ? Math.round(hechos(c).length * 100 / n) : 0; }

  // ───────────────────────── estado de filtros ─────────────────────────
  var F = { q: "", tipo: "", categoria: "", habilidad: "", nivel: "", plataforma: "", idioma: "",
    gratis: false, cert: false, paraTi: false, guardados: false, estado: "", orden: "relevancia" };
  (function leerUrl() {
    var u = new URLSearchParams(location.search);
    Object.keys(F).forEach(function (k) {
      if (!u.has(k)) return;
      F[k] = typeof F[k] === "boolean" ? u.get(k) === "1" : u.get(k);
    });
    if (u.has("buscar")) F.q = u.get("buscar");   // desde el buscador global
  })();
  function escribirUrl() {
    var u = new URLSearchParams();
    Object.keys(F).forEach(function (k) {
      var v = F[k];
      if (v === "" || v === false || (k === "orden" && v === "relevancia")) return;
      u.set(k, v === true ? "1" : v);
    });
    var qs = u.toString();
    history.replaceState(null, "", location.pathname + (qs ? "?" + qs : ""));
  }

  function coincide(c, ignorarTipo) {
    if (!ignorarTipo && F.tipo && c.tipo !== F.tipo) return false;
    if (F.categoria && String(c.categoriaId) !== F.categoria) return false;
    if (F.habilidad && String(c.habilidadId) !== F.habilidad) return false;
    if (F.nivel && c.nivel !== F.nivel) return false;
    if (F.plataforma && c.plataforma !== F.plataforma) return false;
    if (F.idioma && c.idioma !== F.idioma) return false;
    if (F.estado && c.estado !== F.estado) return false;
    if (F.gratis && !c.gratuito) return false;
    if (F.cert && !c.conCertificado) return false;
    if (F.paraTi && !MIS_HAB.has(c.habilidadId)) return false;
    if (F.guardados && !guardado(c.id)) return false;
    if (F.q) {
      var texto = norm([c.titulo, c.descripcion, c.habilidad, c.categoria, c.plataformaNombre, TIPOS[c.tipo] && TIPOS[c.tipo].label]
        .concat((c.pasos || []).map(function (p) { return p.titulo; })).join(" "));
      if (norm(F.q).split(/\s+/).some(function (w) { return texto.indexOf(w) < 0; })) return false;
    }
    return true;
  }
  function ordenar(lista) {
    var o = F.orden;
    return lista.slice().sort(function (a, b) {
      if (o === "az") return a.titulo.localeCompare(b.titulo, "es");
      if (o === "recientes") return b.creadoEn - a.creadoEn;
      if (o === "corto") return a.duracionHoras - b.duracionHoras;
      if (o === "largo") return b.duracionHoras - a.duracionHoras;
      // relevancia: destacados, luego "para ti", luego recientes
      return (b.destacado - a.destacado) || ((MIS_HAB.has(b.habilidadId) ? 1 : 0) - (MIS_HAB.has(a.habilidadId) ? 1 : 0)) || (b.creadoEn - a.creadoEn);
    });
  }

  // ───────────────────────── render ─────────────────────────
  var app = document.getElementById("cursos-app");
  if (!app) return;

  function opciones(lista, actual, todos) {
    return '<option value="">' + esc(todos) + "</option>" + lista.map(function (o) {
      return '<option value="' + esc(o.v) + '"' + (o.g ? ' data-group="' + esc(o.g) + '"' : "") + (String(o.v) === String(actual) ? " selected" : "") + ">" + esc(o.t) + "</option>";
    }).join("");
  }
  function distintos(fn) {
    var vistos = {}, out = [];
    CURSOS.forEach(function (c) { var o = fn(c); if (o && !vistos[o.v]) { vistos[o.v] = 1; out.push(o); } });
    return out.sort(function (a, b) { return String(a.t).localeCompare(String(b.t), "es"); });
  }

  function cifra(n, etiqueta, icon, tono) {
    return '<div class="cx-stat ' + (tono || "") + '">' + icono(icon) + '<strong data-count="' + n + '">' + n + "</strong><span>" + esc(etiqueta) + "</span></div>";
  }

  function portada(c) {
    var p = plat(c.plataforma), t = TIPOS[c.tipo] || TIPOS.CURSO;
    var arriba = '<span class="cx-cover-tags"><span class="cx-type">' + icono(t.icon) + esc(t.label) + "</span>" +
      (c.destacado ? '<span class="cx-feat" title="Destacado por el equipo">' + icono("icon-star") + "<span>Destacado</span></span>" : "") + "</span>";
    var esquina = MODO === "admin"
      ? '<span class="cx-state is-' + esc((c.estado || "").toLowerCase()) + '">' + esc(ESTADOS[c.estado] || c.estado) + "</span>"
      : '<button type="button" class="cx-save' + (guardado(c.id) ? " is-on" : "") + '" data-guardar="' + c.id + '" aria-pressed="' + guardado(c.id) + '" aria-label="' + (guardado(c.id) ? "Quitar de guardados" : "Guardar para después") + '">' + icono("icon-bookmark") + "</button>";
    var fondo;
    if (c.youtubeId) {
      fondo = '<img class="cx-thumb" loading="lazy" alt="" src="https://i.ytimg.com/vi/' + esc(c.youtubeId) + '/hqdefault.jpg" onerror="this.remove()">' +
        '<button type="button" class="cx-play" data-reproducir="' + c.id + '" aria-label="Reproducir ' + esc(c.titulo) + '">' + icono("icon-play") + "</button>";
    } else {
      var lg = tech(c.habilidad);
      fondo = '<div class="cx-art">' + (lg ? '<span class="cx-art-tech">' + lg + "</span>" : logoPlataforma(c.plataforma, "cx-plat-xl")) + "</div>";
    }
    var pasos = (c.pasos || []).length ? '<span class="cx-steps-chip">' + icono("icon-route") + c.pasos.length + " pasos</span>" : "";
    return '<div class="cx-cover is-' + c.tipo.toLowerCase() + (c.youtubeId ? " has-thumb" : "") + '" style="--plat:' + p.color + '">' +
      fondo + '<div class="cx-cover-top">' + arriba + esquina + "</div>" +
      '<div class="cx-cover-bottom">' + logoPlataforma(c.plataforma, "cx-plat-sm") + "<span>" + esc(c.plataformaNombre || p.nombre) + "</span>" + pasos + "</div></div>";
  }

  function nivelHtml(n) {
    var d = NIVELES[n] || ["", 0];
    return '<span class="cx-level" title="Nivel ' + esc(d[0]) + '"><i class="' + (d[1] >= 1 ? "on" : "") + '"></i><i class="' + (d[1] >= 2 ? "on" : "") + '"></i><i class="' + (d[1] >= 3 ? "on" : "") + '"></i><i class="' + (d[1] >= 4 ? "on" : "") + '"></i>' + esc(d[0]) + "</span>";
  }

  function tarjeta(c, i) {
    var t = TIPOS[c.tipo] || TIPOS.CURSO;
    var paraTi = MODO === "catalogo" && MIS_HAB.has(c.habilidadId);
    var progreso = (c.pasos || []).length && MODO === "catalogo"
      ? '<div class="cx-progress" title="Tu avance"><span style="width:' + porcentaje(c) + '%"></span></div><div class="cx-progress-label">' + hechos(c).length + " de " + c.pasos.length + " pasos" + (porcentaje(c) === 100 ? " · ¡completada!" : "") + "</div>"
      : "";
    var acciones = MODO === "admin"
      ? '<div class="cx-actions">' +
          '<button type="button" class="btn btn-secondary btn-sm" data-editar="' + c.id + '">' + icono("icon-edit") + "Editar</button>" +
          '<button type="button" class="cx-icon-btn' + (c.destacado ? " is-on" : "") + '" data-destacar="' + c.id + '" aria-pressed="' + c.destacado + '" title="' + (c.destacado ? "Quitar de destacados" : "Destacar") + '">' + icono("icon-star") + "</button>" +
          '<button type="button" class="cx-icon-btn" data-estado="' + c.id + '" title="' + (c.estado === "PUBLICADO" ? "Archivar" : "Publicar") + '">' + icono(c.estado === "PUBLICADO" ? "icon-eye-off" : "icon-eye") + "</button>" +
          (c.url ? '<a class="cx-icon-btn" href="' + esc(c.url) + '" target="_blank" rel="noopener noreferrer" title="Abrir enlace">' + icono("icon-external-link") + "</a>" : "") +
        "</div>"
      : '<div class="cx-actions"><button type="button" class="btn btn-primary btn-sm cx-cta" data-abrir="' + c.id + '">' + icono(t.icon) + esc(t.cta) + "</button>" +
          (c.url ? '<a class="cx-icon-btn" href="' + esc(c.url) + '" target="_blank" rel="noopener noreferrer" title="Abrir en ' + esc(c.plataformaNombre) + '">' + icono("icon-external-link") + "</a>" : "") + "</div>";
    return '<article class="cx-card cx-reveal' + (c.destacado ? " is-featured" : "") + '" style="--i:' + (i % 12) + '" data-id="' + c.id + '">' +
      portada(c) +
      '<div class="cx-body">' +
        '<div class="cx-kicker">' + (c.categoria ? "<span>" + esc(c.categoria) + "</span>" : "<span>General</span>") + nivelHtml(c.nivel) + "</div>" +
        '<h3 class="cx-title"><button type="button" data-abrir="' + c.id + '">' + esc(c.titulo) + "</button></h3>" +
        (c.descripcion ? '<p class="cx-desc">' + esc(c.descripcion) + "</p>" : "") +
        '<div class="cx-tags">' +
          (paraTi ? '<span class="cx-tag is-foryou">' + icono("icon-sparkles") + "Para ti</span>" : "") +
          (c.habilidad ? '<span class="cx-tag is-skill">' + tech(c.habilidad) + esc(c.habilidad) + "</span>" : "") +
          (c.gratuito ? '<span class="cx-tag is-free">Gratis</span>' : '<span class="cx-tag">De pago</span>') +
          (c.conCertificado ? '<span class="cx-tag is-cert">' + icono("icon-award") + "Certificado</span>" : "") +
          '<span class="cx-tag">' + icono("icon-clock") + horas(c.duracionHoras) + "</span>" +
          '<span class="cx-tag">' + esc(c.idioma) + "</span>" +
        "</div>" + progreso + acciones +
      "</div></article>";
  }

  function estructura() {
    var rutas = CURSOS.filter(function (c) { return c.tipo === "RUTA" || c.tipo === "ESPECIALIZACION"; });
    var nGratis = CURSOS.filter(function (c) { return c.gratuito; }).length;
    var nCert = CURSOS.filter(function (c) { return c.conCertificado; }).length;
    var hero = MODO === "admin"
      ? '<section class="cx-hero is-admin"><div class="cx-hero-text"><span class="cx-eyebrow">' + icono("icon-graduation") + "Formación · Administración</span>" +
          "<h1>Catálogo de formación</h1><p>Publica cursos, videos, rutas y especializaciones. Todo lo publicado lo ven colaboradores, Project Managers y Resource Managers.</p>" +
          '<div class="cx-hero-actions"><button type="button" class="btn btn-primary" id="cx-nuevo">' + icono("icon-plus") + "Nuevo recurso</button></div></div>" +
          '<div class="cx-stats">' +
            cifra(CURSOS.filter(function (c) { return c.estado === "PUBLICADO"; }).length, "Publicados", "icon-eye", "is-green") +
            cifra(rutas.length, "Rutas y programas", "icon-route", "is-violet") +
            cifra(nGratis, "Gratuitos", "icon-zap", "is-blue") +
            cifra(nCert, "Con certificado", "icon-award", "is-amber") +
          "</div></section>"
      : '<section class="cx-hero"><div class="cx-hero-text"><span class="cx-eyebrow">' + icono("icon-graduation") + "Academia SkillBridge</span>" +
          "<h1>Aprende algo nuevo hoy</h1><p>Cursos, videos y rutas elegidos por el equipo para que crezcas en tus proyectos. Gratis y a tu ritmo.</p>" +
          '<label class="cx-search">' + icono("icon-search") + '<input type="search" id="cx-q" placeholder="¿Qué quieres aprender? Ej.: Docker, React, nube…" autocomplete="off" value="' + esc(F.q) + '"><kbd>/</kbd></label></div>' +
          '<div class="cx-stats">' +
            cifra(CURSOS.length, "Recursos", "icon-book", "is-blue") +
            cifra(rutas.length, "Rutas y programas", "icon-route", "is-violet") +
            cifra(nGratis, "Gratuitos", "icon-zap", "is-green") +
            cifra(nCert, "Con certificado", "icon-award", "is-amber") +
          "</div></section>";

    var tabs = '<div class="cx-tabs" role="tablist" aria-label="Tipo de recurso">' +
      [["", "Todo", "icon-grid"]].concat(Object.keys(TIPOS).map(function (k) { return [k, TIPOS[k].plural, TIPOS[k].icon]; }))
        .map(function (t) { return '<button type="button" role="tab" class="cx-tab" data-tipo="' + t[0] + '">' + icono(t[2]) + "<span>" + esc(t[1]) + '</span><b data-n-tipo="' + t[0] + '">0</b></button>'; }).join("") +
      '<span class="cx-tab-ink" aria-hidden="true"></span></div>';

    var cats = distintos(function (c) { return c.categoriaId ? { v: c.categoriaId, t: c.categoria } : null; });
    var habs = distintos(function (c) { return c.habilidadId ? { v: c.habilidadId, t: c.habilidad, g: c.categoria || "Otras" } : null; });
    var plats = distintos(function (c) { return { v: c.plataforma, t: c.plataformaNombre || plat(c.plataforma).nombre }; });
    var idiomas = distintos(function (c) { return { v: c.idioma, t: IDIOMAS[c.idioma] || c.idioma }; });

    var filtros = '<div class="cx-toolbar">' +
      (MODO === "admin" ? '<label class="cx-search is-small">' + icono("icon-search") + '<input type="search" id="cx-q" placeholder="Buscar en el catálogo…" autocomplete="off" value="' + esc(F.q) + '"></label>' : "") +
      '<div class="cx-filters" id="cx-filtros">' +
        '<select class="select" data-f="categoria" aria-label="Categoría">' + opciones(cats, F.categoria, "Todas las categorías") + "</select>" +
        '<select class="select" data-f="habilidad" aria-label="Habilidad" data-tech-icons>' + opciones(habs, F.habilidad, "Todas las habilidades") + "</select>" +
        '<select class="select" data-f="nivel" aria-label="Nivel">' + opciones(Object.keys(NIVELES).map(function (k) { return { v: k, t: NIVELES[k][0] }; }), F.nivel, "Cualquier nivel") + "</select>" +
        '<select class="select" data-f="plataforma" aria-label="Plataforma" data-icons="plataforma">' + opciones(plats, F.plataforma, "Todas las plataformas") + "</select>" +
        (idiomas.length > 1 ? '<select class="select" data-f="idioma" aria-label="Idioma">' + opciones(idiomas, F.idioma, "Cualquier idioma") + "</select>" : "") +
        (MODO === "admin" ? '<select class="select" data-f="estado" aria-label="Estado">' + opciones(Object.keys(ESTADOS).map(function (k) { return { v: k, t: ESTADOS[k] }; }), F.estado, "Todos los estados") + "</select>" : "") +
      "</div>" +
      '<div class="cx-toggles">' +
        '<button type="button" class="cx-toggle cx-filter-btn" data-filtros aria-expanded="false" aria-controls="cx-filtros">' + icono("icon-filter") + 'Filtros <b id="cx-n-filtros" hidden>0</b></button>' +
        '<button type="button" class="cx-toggle" data-t="gratis">' + icono("icon-zap") + "Gratis</button>" +
        '<button type="button" class="cx-toggle" data-t="cert">' + icono("icon-award") + "Con certificado</button>" +
        (MODO === "catalogo" && MIS_HAB.size ? '<button type="button" class="cx-toggle" data-t="paraTi">' + icono("icon-sparkles") + "Para ti</button>" : "") +
        (MODO === "catalogo" ? '<button type="button" class="cx-toggle" data-t="guardados">' + icono("icon-bookmark") + 'Guardados <b id="cx-n-guardados">' + mem.guardados.length + "</b></button>" : "") +
        '<span class="cx-spacer"></span>' +
        '<select class="select cx-sort" data-f="orden" data-search="off" aria-label="Ordenar">' +
          [["relevancia", "Más relevantes"], ["recientes", "Más recientes"], ["az", "A → Z"], ["corto", "Más cortos primero"], ["largo", "Más largos primero"]]
            .map(function (o) { return '<option value="' + o[0] + '"' + (F.orden === o[0] ? " selected" : "") + ">" + o[1] + "</option>"; }).join("") +
        "</select>" +
      "</div></div>";

    app.innerHTML = hero +
      '<section class="cx-section"><div class="cx-section-head"><div><h2>' + icono("icon-book") + (MODO === "admin" ? "Recursos" : "Explora el catálogo") + '</h2><p id="cx-resumen"></p></div></div>' +
      tabs + filtros + '<div class="cx-active" id="cx-active"></div>' +
      '<div class="cx-grid" id="cx-grid" aria-live="polite"></div>' +
      '<nav class="cx-pager" id="cx-pager" aria-label="Páginas del catálogo" hidden></nav>' +
      '<div class="cx-empty" id="cx-empty" hidden>' + icono("icon-search") + '<strong>No encontramos recursos con esos filtros</strong><p>Prueba con otra palabra o limpia los filtros.</p><button type="button" class="btn btn-secondary btn-sm" data-limpiar>Limpiar filtros</button></div>' +
      "</section>";
  }

  var ETIQUETAS_F = { categoria: "Categoría", habilidad: "Habilidad", nivel: "Nivel", plataforma: "Plataforma", idioma: "Idioma", estado: "Estado" };
  // Paginación: 12 por página (4 filas de 3). Si cambia algún filtro o la
  // búsqueda se vuelve a la página 1; marcar progreso o guardar no la cambia.
  var POR_PAGINA = 12, PAGINA = 1, firmaFiltros = null;
  function pintarLista() {
    var lista = ordenar(CURSOS.filter(function (c) { return coincide(c); }));
    var firma = JSON.stringify(F);
    if (firma !== firmaFiltros) { PAGINA = 1; firmaFiltros = firma; }
    var paginas = Math.max(1, Math.ceil(lista.length / POR_PAGINA));
    PAGINA = Math.min(Math.max(1, PAGINA), paginas);
    var desde = (PAGINA - 1) * POR_PAGINA;
    var grid = document.getElementById("cx-grid");
    grid.innerHTML = lista.slice(desde, desde + POR_PAGINA).map(tarjeta).join("");
    document.getElementById("cx-empty").hidden = lista.length > 0;
    pintarPaginacion(lista.length, paginas, desde);
    // contadores por tipo (con el resto de filtros aplicados)
    var base = CURSOS.filter(function (c) { return coincide(c, true); });
    document.querySelectorAll("[data-n-tipo]").forEach(function (b) {
      var k = b.getAttribute("data-n-tipo");
      b.textContent = k ? base.filter(function (c) { return c.tipo === k; }).length : base.length;
    });
    document.querySelectorAll(".cx-tab").forEach(function (t) { var on = t.getAttribute("data-tipo") === F.tipo; t.classList.toggle("is-active", on); t.setAttribute("aria-selected", on); });
    moverTinta();
    document.querySelectorAll(".cx-toggle").forEach(function (t) { var on = !!F[t.getAttribute("data-t")]; t.classList.toggle("is-on", on); t.setAttribute("aria-pressed", on); });
    var nSel = Object.keys(ETIQUETAS_F).filter(function (k) { return F[k]; }).length;
    var badge = document.getElementById("cx-n-filtros");
    if (badge) { badge.textContent = nSel; badge.hidden = !nSel; }
    var resumen = document.getElementById("cx-resumen");
    if (resumen) resumen.textContent = lista.length + (lista.length === 1 ? " recurso" : " recursos") + (lista.length !== CURSOS.length ? " de " + CURSOS.length : "") + (MODO === "admin" ? " en el catálogo" : " para aprender");
    // filtros activos como píldoras
    var act = [];
    if (F.q) act.push(["q", "“" + F.q + "”"]);
    Object.keys(ETIQUETAS_F).forEach(function (k) {
      if (!F[k]) return;
      var sel = document.querySelector('[data-f="' + k + '"]');
      var o = sel && sel.querySelector('option[value="' + CSS.escape(F[k]) + '"]');
      act.push([k, ETIQUETAS_F[k] + ": " + (o ? o.textContent : F[k])]);
    });
    [["gratis", "Gratis"], ["cert", "Con certificado"], ["paraTi", "Para ti"], ["guardados", "Guardados"]].forEach(function (b) { if (F[b[0]]) act.push([b[0], b[1]]); });
    document.getElementById("cx-active").innerHTML = act.length
      ? act.map(function (a) { return '<button type="button" class="cx-pill" data-quitar="' + a[0] + '">' + esc(a[1]) + icono("icon-x") + "</button>"; }).join("") + '<button type="button" class="cx-clear" data-limpiar>Limpiar todo</button>'
      : "";
    revelar();
    escribirUrl();
  }

  function pintarPaginacion(total, paginas, desde) {
    var nav = document.getElementById("cx-pager");
    if (!nav) return;
    nav.hidden = paginas <= 1;
    if (paginas <= 1) { nav.innerHTML = ""; return; }
    // Números con "…" si hay muchas páginas: 1 … 4 5 6 … 12
    var nums = [];
    for (var n = 1; n <= paginas; n++) {
      if (n === 1 || n === paginas || Math.abs(n - PAGINA) <= 1) nums.push(n);
      else if (nums[nums.length - 1] !== "…") nums.push("…");
    }
    nav.innerHTML = '<span class="cx-pager-info">Mostrando ' + (desde + 1) + "–" + Math.min(desde + POR_PAGINA, total) + " de " + total + "</span>" +
      '<div class="pagination">' +
        '<button type="button" class="page-btn" data-pagina="' + (PAGINA - 1) + '"' + (PAGINA === 1 ? " disabled" : "") + ' aria-label="Página anterior">‹</button>' +
        nums.map(function (n) {
          return n === "…" ? '<span class="cx-pager-gap" aria-hidden="true">…</span>'
            : '<button type="button" class="page-btn page-num' + (n === PAGINA ? " active" : "") + '" data-pagina="' + n + '"' + (n === PAGINA ? ' aria-current="page"' : "") + ' aria-label="Página ' + n + '">' + n + "</button>";
        }).join("") +
        '<button type="button" class="page-btn" data-pagina="' + (PAGINA + 1) + '"' + (PAGINA === paginas ? " disabled" : "") + ' aria-label="Página siguiente">›</button>' +
      "</div>";
  }

  function moverTinta() {
    var activo = document.querySelector(".cx-tab.is-active"), tinta = document.querySelector(".cx-tab-ink");
    if (!activo || !tinta) return;
    tinta.style.width = activo.offsetWidth + "px";
    tinta.style.transform = "translateX(" + activo.offsetLeft + "px)";
  }

  // Aparición escalonada al entrar en pantalla (y al filtrar).
  var observador = "IntersectionObserver" in window ? new IntersectionObserver(function (entradas) {
    entradas.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("is-in"); observador.unobserve(e.target); } });
  }, { rootMargin: "0px 0px -8% 0px" }) : null;
  function revelar() {
    document.querySelectorAll(".cx-reveal:not(.is-in)").forEach(function (el) {
      if (observador) observador.observe(el); else el.classList.add("is-in");
    });
  }

  // ───────────────────────── modales ─────────────────────────
  function abrirModal(html, clase) {
    if (window.SBAI && SBAI.modal && SBAI.modal.open) return SBAI.modal.open('<div class="modal cx-modal ' + (clase || "") + '" role="dialog" aria-modal="true">' + html + "</div>");
    return null;
  }
  function reproductor(id, titulo, url) {
    abrirModal('<div class="cx-player-head"><strong>' + esc(titulo) + '</strong><button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icono("icon-x") + "</button></div>" +
      '<div class="cx-player"><iframe src="https://www.youtube-nocookie.com/embed/' + esc(id) + '?autoplay=1&rel=0" title="' + esc(titulo) + '" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe></div>' +
      '<div class="cx-player-foot"><a class="btn btn-secondary btn-sm" href="' + esc(url) + '" target="_blank" rel="noopener noreferrer">' + icono("icon-external-link") + "Abrir en YouTube</a></div>", "is-player");
  }

  function detalle(c) {
    var t = TIPOS[c.tipo] || TIPOS.CURSO, esRuta = (c.pasos || []).length > 0;
    var hechosSet = new Set(hechos(c));
    var pasos = esRuta ? '<div class="cx-timeline-head"><strong>' + c.pasos.length + " pasos</strong>" +
        '<span class="cx-ring" style="--p:' + porcentaje(c) + '"><b>' + porcentaje(c) + '%</b></span></div><ol class="cx-timeline">' +
        c.pasos.map(function (p, k) {
          return '<li class="' + (hechosSet.has(k) ? "is-done" : "") + '">' +
            '<label class="cx-step-check"><input type="checkbox" data-paso="' + k + '"' + (hechosSet.has(k) ? " checked" : "") + (MODO === "admin" ? " disabled" : "") + '><span aria-hidden="true">' + (k + 1) + "</span></label>" +
            '<div class="cx-step-body"><strong>' + esc(p.titulo) + "</strong>" +
              '<span class="cx-step-meta">' + logoPlataforma(p.plataforma, "cx-plat-xs") + esc(plat(p.plataforma).nombre) + "</span></div>" +
            '<div class="cx-step-actions">' +
              (p.youtubeId ? '<button type="button" class="cx-icon-btn" data-reproducir-paso="' + k + '" title="Ver aquí">' + icono("icon-play") + "</button>" : "") +
              (p.url ? '<a class="cx-icon-btn" href="' + esc(p.url) + '" target="_blank" rel="noopener noreferrer" title="Abrir">' + icono("icon-external-link") + "</a>" : "") +
            "</div></li>";
        }).join("") + "</ol>" : "";
    var bd = abrirModal(
      '<div class="cx-detail-cover" style="--plat:' + plat(c.plataforma).color + '">' +
        (c.youtubeId ? '<img alt="" src="https://i.ytimg.com/vi/' + esc(c.youtubeId) + '/hqdefault.jpg" onerror="this.remove()">' : "") +
        '<div class="cx-detail-cover-in"><span class="cx-type">' + icono(t.icon) + esc(t.label) + "</span>" +
        '<button type="button" class="modal-close" data-modal-close aria-label="Cerrar">' + icono("icon-x") + "</button></div></div>" +
      '<div class="cx-detail">' +
        '<div class="cx-detail-plat">' + logoPlataforma(c.plataforma) + "<span>" + esc(c.plataformaNombre) + "</span>" + (c.categoria ? "<span class='cx-dot'>·</span><span>" + esc(c.categoria) + "</span>" : "") + "</div>" +
        "<h2>" + esc(c.titulo) + "</h2>" +
        (c.descripcion ? "<p>" + esc(c.descripcion) + "</p>" : "") +
        '<div class="cx-detail-facts">' +
          "<div><span>Nivel</span>" + nivelHtml(c.nivel) + "</div>" +
          "<div><span>Duración</span><b>" + horas(c.duracionHoras) + "</b></div>" +
          "<div><span>Idioma</span><b>" + esc(IDIOMAS[c.idioma] || c.idioma) + "</b></div>" +
          "<div><span>Costo</span><b>" + (c.gratuito ? "Gratis" : "De pago") + "</b></div>" +
          "<div><span>Certificado</span><b>" + (c.conCertificado ? "Sí" : "No") + "</b></div>" +
          (c.habilidad ? "<div><span>Habilidad</span><b class='cx-detail-skill'>" + tech(c.habilidad) + esc(c.habilidad) + "</b></div>" : "") +
        "</div>" + pasos +
        '<div class="cx-detail-actions">' +
          (c.youtubeId ? '<button type="button" class="btn btn-primary" data-reproducir="' + c.id + '">' + icono("icon-play") + "Ver aquí</button>" : "") +
          (c.url ? '<a class="btn ' + (c.youtubeId ? "btn-secondary" : "btn-primary") + '" href="' + esc(c.url) + '" target="_blank" rel="noopener noreferrer">' + icono("icon-external-link") + "Abrir en " + esc(c.plataformaNombre) + "</a>" : "") +
          (MODO === "catalogo" ? '<button type="button" class="btn btn-tertiary" data-guardar="' + c.id + '">' + icono("icon-bookmark") + (guardado(c.id) ? "Guardado" : "Guardar") + "</button>" : "") +
        "</div></div>", "is-detail");
    if (!bd) return;
    bd.addEventListener("change", function (e) {
      var cb = e.target.closest("[data-paso]");
      if (!cb) return;
      var k = +cb.getAttribute("data-paso"), lista = mem.progreso[c.id] || [];
      lista = lista.filter(function (x) { return x !== k; });
      if (cb.checked) lista.push(k);
      mem.progreso[c.id] = lista;
      persistir();
      cb.closest("li").classList.toggle("is-done", cb.checked);
      var ring = bd.querySelector(".cx-ring");
      if (ring) { ring.style.setProperty("--p", porcentaje(c)); ring.querySelector("b").textContent = porcentaje(c) + "%"; }
      if (porcentaje(c) === 100 && cb.checked && SBAI.toast) SBAI.toast.show("¡Completaste “" + c.titulo + "”! 🎉", { type: "success" });
      refrescarTodo();
    });
    bd.addEventListener("click", function (e) {
      var rp = e.target.closest("[data-reproducir-paso]");
      if (rp) { var p = c.pasos[+rp.getAttribute("data-reproducir-paso")]; reproductor(p.youtubeId, p.titulo, p.url); }
    });
  }

  function refrescarTodo() {
    pintarLista();
  }

  // ───────────────────────── eventos ─────────────────────────
  function enlazar() {
    var q = document.getElementById("cx-q"), temporizador;
    if (q) {
      q.addEventListener("input", function () { clearTimeout(temporizador); temporizador = setTimeout(function () { F.q = q.value.trim(); pintarLista(); }, 140); });
      document.addEventListener("keydown", function (e) {
        if (e.key === "/" && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName) && !document.activeElement.isContentEditable) { e.preventDefault(); q.focus(); }
      });
    }
    app.addEventListener("change", function (e) {
      var s = e.target.closest("[data-f]");
      if (!s) return;
      F[s.getAttribute("data-f")] = s.value;
      pintarLista();
    });
    app.addEventListener("click", function (e) {
      var el;
      if ((el = e.target.closest(".cx-tab"))) { F.tipo = el.getAttribute("data-tipo"); pintarLista(); return; }
      if ((el = e.target.closest("[data-filtros]"))) {
        var barra = el.closest(".cx-toolbar"), abierta = barra.classList.toggle("is-open");
        el.setAttribute("aria-expanded", abierta); return;
      }
      if ((el = e.target.closest(".cx-toggle"))) { var k = el.getAttribute("data-t"); F[k] = !F[k]; pintarLista(); return; }
      if ((el = e.target.closest("[data-quitar]"))) {
        var kq = el.getAttribute("data-quitar");
        F[kq] = typeof F[kq] === "boolean" ? false : "";
        if (kq === "q" && q) q.value = "";
        sincronizarSelects(); pintarLista(); return;
      }
      if (e.target.closest("[data-limpiar]")) { limpiar(); return; }
      if ((el = e.target.closest("[data-pagina]"))) {
        if (el.disabled) return;
        PAGINA = +el.getAttribute("data-pagina");
        pintarLista();
        var tabs = document.querySelector(".cx-tabs");
        if (tabs) window.scrollTo({ top: tabs.getBoundingClientRect().top + window.scrollY - 96, behavior: "smooth" });
        return;
      }
      clicComun(e);
    });
    document.addEventListener("click", function (e) { if (!app.contains(e.target)) clicComun(e); });
    window.addEventListener("resize", moverTinta);
  }

  function clicComun(e) {
    var el;
    if ((el = e.target.closest("[data-guardar]"))) {
      var id = +el.getAttribute("data-guardar"), on = alternarGuardado(id);
      if (SBAI.toast) SBAI.toast.show(on ? "Guardado en tu lista para después." : "Quitado de guardados.", { type: on ? "success" : "default" });
      if (el.closest(".cx-detail")) el.innerHTML = icono("icon-bookmark") + (on ? "Guardado" : "Guardar");
      var n = document.getElementById("cx-n-guardados"); if (n) n.textContent = mem.guardados.length;
      if (!el.closest(".cx-detail") && !F.guardados) {   // solo actualizar el botón, sin re-pintar todo
        el.classList.toggle("is-on", on); el.setAttribute("aria-pressed", on);
        el.setAttribute("aria-label", on ? "Quitar de guardados" : "Guardar para después");
        el.classList.remove("cx-pop"); void el.offsetWidth; el.classList.add("cx-pop");
      } else pintarLista();
      return;
    }
    if ((el = e.target.closest("[data-reproducir]"))) { var c = POR_ID[el.getAttribute("data-reproducir")]; if (c && c.youtubeId) reproductor(c.youtubeId, c.titulo, c.url); return; }
    if ((el = e.target.closest("[data-abrir]"))) { var c2 = POR_ID[el.getAttribute("data-abrir")]; if (c2) detalle(c2); return; }
    if (MODO === "admin") clicAdmin(e);
  }

  function limpiar() {
    Object.keys(F).forEach(function (k) { F[k] = typeof F[k] === "boolean" ? false : (k === "orden" ? "relevancia" : ""); });
    var q = document.getElementById("cx-q"); if (q) q.value = "";
    sincronizarSelects(); pintarLista();
  }
  function sincronizarSelects() {
    document.querySelectorAll("[data-f]").forEach(function (s) { s.value = F[s.getAttribute("data-f")] || (s.getAttribute("data-f") === "orden" ? "relevancia" : ""); });
  }

  // ───────────────────────── administración ─────────────────────────
  function clicAdmin(e) {
    var el;
    if (e.target.closest("#cx-nuevo")) { formulario(null); return; }
    if ((el = e.target.closest("[data-editar]"))) { formulario(POR_ID[el.getAttribute("data-editar")]); return; }
    if ((el = e.target.closest("[data-destacar]"))) {
      var id = el.getAttribute("data-destacar");
      el.disabled = true;
      fetch("/administrador/cursos/" + encodeURIComponent(id) + "/destacar", { method: "POST", headers: { "Accept": "application/json" } })
        .then(function (r) { if (!r.ok) throw new Error(); return r.json(); })
        .then(function (r) {
          POR_ID[id].destacado = r.destacado;
          if (SBAI.toast) SBAI.toast.show(r.destacado ? "Ahora aparece entre los destacados." : "Quitado de destacados.", { type: "success" });
          pintarLista();
        })
        .catch(function () { el.disabled = false; if (SBAI.toast) SBAI.toast.show("No se pudo actualizar. Intenta de nuevo.", { type: "error" }); });
      return;
    }
    if ((el = e.target.closest("[data-estado]"))) {
      var c = POR_ID[el.getAttribute("data-estado")], publicar = c.estado !== "PUBLICADO";
      SBAI.modal.confirm({
        title: publicar ? "¿Publicar “" + c.titulo + "”?" : "¿Archivar “" + c.titulo + "”?",
        desc: publicar ? "Aparecerá en la vista Cursos de colaboradores, Project Managers y Resource Managers."
                       : "Dejará de verse en el catálogo del equipo. Podrás volver a publicarlo cuando quieras.",
        variant: publicar ? "primary" : "warning", icon: publicar ? "icon-eye" : "icon-eye-off",
        confirmLabel: publicar ? "Publicar" : "Archivar",
        onConfirm: function () { SBAI.submitForm("/administrador/cursos/" + c.id + "/estado", { estado: publicar ? "PUBLICADO" : "ARCHIVADO" }); }
      });
    }
  }

  function formulario(c) {
    var dlg = document.getElementById("curso-dialog"), form = document.getElementById("curso-form");
    if (!dlg || !form) return;
    form.reset();
    form.action = c ? "/administrador/cursos/" + c.id + "/editar" : "/administrador/cursos";
    document.getElementById("curso-dialog-title").textContent = c ? "Editar recurso" : "Nuevo recurso";
    var el = form.elements;
    var tipo = c ? c.tipo : "CURSO";
    form.querySelectorAll('[name="tipo"]').forEach(function (r) { r.checked = r.value === tipo; });
    el.titulo.value = c ? c.titulo : "";
    el.descripcion.value = c && c.descripcion ? c.descripcion : "";
    el.url.value = c && c.url ? c.url : "";
    el.duracionHoras.value = c ? c.duracionHoras : 1;
    el.nivel.value = c ? c.nivel : "BASICO";
    el.idioma.value = c ? c.idioma : "ES";
    el.plataforma.value = c && c.plataformaManual ? c.plataforma : "";
    el.categoriaId.value = c && c.categoriaId ? c.categoriaId : "";
    filtrarHabilidades(c && c.habilidadId ? String(c.habilidadId) : "");
    el.gratuito.checked = c ? c.gratuito : true;
    el.conCertificado.checked = c ? c.conCertificado : false;
    el.destacado.checked = c ? c.destacado : false;
    var lista = document.getElementById("cx-pasos");
    lista.innerHTML = "";
    (c && c.pasos && c.pasos.length ? c.pasos : [{}, {}]).forEach(function (p) { agregarPaso(p.titulo, p.url); });
    error("");
    actualizarTipo(); vistaPrevia();
    dlg.showModal();
    setTimeout(function () { el.titulo.focus(); }, 30);
  }

  var TODAS_HABS = null;
  function filtrarHabilidades(seleccion) {
    var form = document.getElementById("curso-form"), hab = form.elements.habilidadId, cat = form.elements.categoriaId;
    if (!TODAS_HABS) TODAS_HABS = Array.prototype.map.call(hab.options, function (o) { return { v: o.value, t: o.textContent, cat: o.getAttribute("data-categoria-id") || "" }; });
    var actual = seleccion != null ? seleccion : hab.value;
    hab.innerHTML = TODAS_HABS.filter(function (h) { return !h.v || !cat.value || h.cat === cat.value; })
      .map(function (h) { return '<option value="' + esc(h.v) + '" data-categoria-id="' + esc(h.cat) + '">' + esc(h.t) + "</option>"; }).join("");
    hab.value = Array.prototype.some.call(hab.options, function (o) { return o.value === actual; }) ? actual : "";
  }

  function agregarPaso(titulo, url) {
    var lista = document.getElementById("cx-pasos");
    var li = document.createElement("li");
    li.className = "cx-step-row";
    li.innerHTML = '<span class="cx-step-num" aria-hidden="true"></span>' +
      '<input class="input" name="pasoTitulo" maxlength="150" placeholder="Título del paso" value="' + esc(titulo || "") + '">' +
      '<input class="input" name="pasoUrl" maxlength="500" placeholder="https://… (YouTube, curso, documentación)" value="' + esc(url || "") + '">' +
      '<span class="cx-step-plat" aria-hidden="true"></span>' +
      '<div class="cx-step-tools"><button type="button" class="cx-icon-btn" data-paso-subir title="Subir">' + icono("icon-arrow-up") + "</button>" +
      '<button type="button" class="cx-icon-btn" data-paso-quitar title="Quitar">' + icono("icon-trash") + "</button></div>";
    lista.appendChild(li);
    numerarPasos();
  }
  function numerarPasos() {
    document.querySelectorAll("#cx-pasos .cx-step-row").forEach(function (li, i) {
      li.querySelector(".cx-step-num").textContent = i + 1;
      var u = li.querySelector('[name="pasoUrl"]').value.trim();
      li.querySelector(".cx-step-plat").innerHTML = u ? logoPlataforma(detectarPlataforma(u), "cx-plat-xs") : "";
    });
  }
  function actualizarTipo() {
    var form = document.getElementById("curso-form");
    var tipo = (form.querySelector('[name="tipo"]:checked') || {}).value || "CURSO";
    var esRuta = tipo === "RUTA" || tipo === "ESPECIALIZACION";
    document.getElementById("cx-bloque-pasos").hidden = !esRuta;
    form.elements.url.required = !esRuta;
    document.getElementById("cx-url-ayuda").textContent = esRuta
      ? "Opcional: enlace al roadmap completo o a la página del programa."
      : "Pega el enlace de YouTube, del curso o de la plataforma: el logo se detecta solo.";
    // Los pasos solo se envían si es ruta/especialización.
    form.querySelectorAll('#cx-pasos input').forEach(function (i) { i.disabled = !esRuta; });
  }
  function vistaPrevia() {
    var form = document.getElementById("curso-form"), url = form.elements.url.value.trim();
    var clave = form.elements.plataforma.value || (url ? detectarPlataforma(url) : "");
    var yt = youtubeId(url), caja = document.getElementById("cx-preview");
    if (!url && !form.elements.plataforma.value) { caja.innerHTML = '<span class="cx-preview-empty">' + icono("icon-globe") + "La plataforma aparecerá aquí al pegar el enlace</span>"; return; }
    caja.innerHTML = (yt ? '<img alt="" src="https://i.ytimg.com/vi/' + esc(yt) + '/mqdefault.jpg" onerror="this.remove()">' : "") +
      '<span class="cx-preview-plat">' + logoPlataforma(clave) + "<span><b>" + esc(plat(clave).nombre) + "</b><small>" +
      (form.elements.plataforma.value ? "Elegida a mano" : "Detectada por el enlace") + (yt ? " · video de YouTube" : "") + "</small></span></span>";
  }
  function error(msg, campo) {
    var e = document.getElementById("curso-form-error");
    e.textContent = msg || ""; e.hidden = !msg;
    if (campo) campo.focus();
  }

  function enlazarFormulario() {
    var dlg = document.getElementById("curso-dialog"), form = document.getElementById("curso-form");
    if (!dlg || !form) return;
    form.addEventListener("change", function (e) {
      if (e.target.name === "tipo") actualizarTipo();
      if (e.target.name === "categoriaId") filtrarHabilidades();
      if (e.target.name === "plataforma") vistaPrevia();
    });
    form.addEventListener("input", function (e) {
      if (e.target.name === "url") vistaPrevia();
      if (e.target.name === "pasoUrl") numerarPasos();
      if (!document.getElementById("curso-form-error").hidden) error("");
    });
    form.addEventListener("click", function (e) {
      var el;
      if (e.target.closest("[data-paso-agregar]")) { agregarPaso(); var u = document.querySelectorAll('#cx-pasos [name="pasoTitulo"]'); u[u.length - 1].focus(); return; }
      if ((el = e.target.closest("[data-paso-quitar]"))) { el.closest("li").remove(); if (!document.querySelector("#cx-pasos li")) agregarPaso(); numerarPasos(); return; }
      if ((el = e.target.closest("[data-paso-subir]"))) { var li = el.closest("li"); if (li.previousElementSibling) li.parentNode.insertBefore(li, li.previousElementSibling); numerarPasos(); }
    });
    var desde = document.getElementById("cx-desde-catalogo");
    if (desde) {
      desde.innerHTML = '<option value="">+ Agregar un recurso del catálogo como paso…</option>' +
        CURSOS.filter(function (c) { return c.url && c.tipo !== "RUTA" && c.tipo !== "ESPECIALIZACION"; })
          .map(function (c) { return '<option value="' + c.id + '" data-group="' + esc((TIPOS[c.tipo] || TIPOS.CURSO).plural) + '">' + esc(c.titulo) + "</option>"; }).join("");
      desde.addEventListener("change", function () {
        var c = POR_ID[desde.value]; if (!c) return;
        var vacio = Array.prototype.find.call(document.querySelectorAll("#cx-pasos .cx-step-row"), function (li) { return !li.querySelector('[name="pasoTitulo"]').value && !li.querySelector('[name="pasoUrl"]').value; });
        if (vacio) { vacio.querySelector('[name="pasoTitulo"]').value = c.titulo; vacio.querySelector('[name="pasoUrl"]').value = c.url; numerarPasos(); }
        else agregarPaso(c.titulo, c.url);
        desde.value = "";
      });
    }
    form.addEventListener("submit", function (e) {
      var el = form.elements, tipo = (form.querySelector('[name="tipo"]:checked') || {}).value;
      var esRuta = tipo === "RUTA" || tipo === "ESPECIALIZACION";
      if (el.titulo.value.trim().length < 3) { e.preventDefault(); error("Escribe un título de al menos 3 caracteres.", el.titulo); return; }
      if (!esRuta && !el.url.value.trim()) { e.preventDefault(); error("Agrega el enlace del recurso.", el.url); return; }
      if (el.url.value && !/^https?:\/\//i.test(el.url.value.trim())) { e.preventDefault(); error("El enlace debe empezar con https:// o http://.", el.url); return; }
      if (esRuta) {
        var filas = Array.prototype.filter.call(document.querySelectorAll("#cx-pasos .cx-step-row"), function (li) {
          return li.querySelector('[name="pasoTitulo"]').value.trim() || li.querySelector('[name="pasoUrl"]').value.trim();
        });
        if (!filas.length) { e.preventDefault(); error("Agrega al menos un paso a la ruta."); return; }
        for (var i = 0; i < filas.length; i++) {
          var t = filas[i].querySelector('[name="pasoTitulo"]'), u = filas[i].querySelector('[name="pasoUrl"]');
          if (!t.value.trim()) { e.preventDefault(); error("El paso " + (i + 1) + " necesita un título.", t); return; }
          if (u.value.trim() && !/^https?:\/\//i.test(u.value.trim())) { e.preventDefault(); error("El enlace del paso " + (i + 1) + " debe empezar con https://.", u); return; }
        }
      }
      form.querySelector('[type="submit"]').textContent = "Guardando…";
    });
    document.querySelectorAll("[data-cerrar-dialogo]").forEach(function (b) { b.addEventListener("click", function () { dlg.close(); }); });
    dlg.addEventListener("click", function (e) { if (e.target === dlg) dlg.close(); });
  }

  // ───────────────────────── inicio ─────────────────────────
  function iniciar() {
    estructura();
    enlazar();
    if (MODO === "admin") enlazarFormulario();
    pintarLista();
    requestAnimationFrame(moverTinta);
    // Cifras del hero: cuenta animada.
    document.querySelectorAll(".cx-stat strong").forEach(function (s) {
      var fin = +s.getAttribute("data-count"), t0 = null;
      if (!fin || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      function paso(t) { if (!t0) t0 = t; var p = Math.min(1, (t - t0) / 700); s.textContent = Math.round(fin * (1 - Math.pow(1 - p, 3))); if (p < 1) requestAnimationFrame(paso); }
      requestAnimationFrame(paso);
    });
    var f = window.adminCursoFeedback;
    if (f && SBAI.toast) { if (f.exito) SBAI.toast.show(f.exito, { type: "success" }); if (f.error) SBAI.toast.show(f.error, { type: "error" }); }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciar); else iniciar();
})();
