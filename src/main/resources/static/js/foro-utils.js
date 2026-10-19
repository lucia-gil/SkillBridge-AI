/*
 * foro-utils.js — utilidades compartidas por foros.html y foro-hilo.html
 * (project-manager y colaborador). Expone window.SBForo.
 *
 *  - normalizar / normalizar1a1: minúsculas, sin tildes, solo letras y números.
 *  - crearBuscador(): búsqueda "inteligente" en el navegador:
 *      · ignora tildes y mayúsculas, acepta palabras parciales ("mysq");
 *      · expande sinónimos de temas técnicos ("bd" → base de datos, mysql, sql…);
 *      · reconoce las palabras clave de cada tópico (las mismas que usa el
 *        clasificador del servidor, ForoClasificadorService);
 *      · tolera un error de tipeo en palabras largas ("kubernetis");
 *      · pondera dónde aparece (título > etiquetas > tópico > texto).
 *  - resaltar(): marca coincidencias con <mark> sin romper el escapado HTML.
 *  - enlazar(): convierte URLs del texto en enlaces (solo nodos de texto).
 *  - tiempoRelativo(): "hace 3 h", "ayer"…
 *  - crearAdjuntos(): selector de archivos/enlaces con validación y chips.
 */
(function () {
  "use strict";

  // ───────────── Texto ─────────────

  function escapar(texto) {
    return String(texto == null ? "" : texto)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  /** Cada carácter del original produce exactamente UN carácter: así los índices coinciden para resaltar. */
  function normalizar1a1(texto) {
    var s = String(texto == null ? "" : texto);
    var out = "";
    for (var i = 0; i < s.length; i++) {
      var c = s[i].normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
      c = c.charAt(0);
      out += /[a-z0-9+#]/.test(c) ? c : " ";
    }
    return out;
  }

  function normalizar(texto) {
    return normalizar1a1(texto).replace(/\s+/g, " ").trim();
  }

  // ───────────── Búsqueda inteligente ─────────────

  var SINONIMOS = [
    ["bd", "base de datos", "db", "database", "mysql", "sql", "rds", "workbench", "query", "consulta", "jdbc"],
    ["deploy", "despliegue", "desplegar", "produccion", "ec2", "aws", "instancia", "nube", "cloud"],
    ["error", "bug", "falla", "fallo", "excepcion", "exception", "problema", "no funciona", "se cae", "crash"],
    ["login", "inicio de sesion", "autenticacion", "auth", "contrasena", "password", "token", "sesion", "2fa"],
    ["prueba", "pruebas", "test", "testing", "qa", "selenium", "e2e"],
    ["reunion", "meeting", "kickoff", "daily", "retro", "llamada"],
    ["frontend", "front", "vista", "html", "css", "interfaz", "pantalla", "thymeleaf"],
    ["backend", "back", "api", "endpoint", "controlador", "servicio", "spring"],
    ["k8s", "kubernetes", "cluster", "namespace", "namespaces"],
    ["docker", "contenedor", "contenedores"],
    ["diseno", "figma", "mockup", "prototipo", "ux", "ui"],
    ["entrega", "entregable", "plazo", "deadline", "fecha limite"],
    ["permiso", "permisos", "rol", "roles", "acceso", "rbac"],
    ["lento", "rendimiento", "performance", "memoria", "timeout"]
  ];
  var VACIAS = { de: 1, la: 1, el: 1, en: 1, y: 1, a: 1, con: 1, por: 1, para: 1, que: 1, como: 1, un: 1, una: 1,
    los: 1, las: 1, del: 1, al: 1, es: 1, lo: 1, mi: 1, me: 1, o: 1, the: 1, of: 1 };

  function levenshtein1(a, b, max) {
    if (Math.abs(a.length - b.length) > max) return max + 1;
    var prev = [], cur = [], i, j;
    for (j = 0; j <= b.length; j++) prev[j] = j;
    for (i = 1; i <= a.length; i++) {
      cur = [i];
      var minFila = i;
      for (j = 1; j <= b.length; j++) {
        cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
        if (cur[j] < minFila) minFila = cur[j];
      }
      if (minFila > max) return max + 1;
      prev = cur;
    }
    return prev[b.length];
  }

  /** Coincide como palabra (términos cortos) o como subcadena (términos de 4+ letras). */
  function contiene(campo, termino) {
    if (!campo || !termino) return false;
    if (termino.length <= 3) return (" " + campo + " ").indexOf(" " + termino) >= 0;
    return campo.indexOf(termino) >= 0;
  }

  /**
   * @param docs     [{id, titulo, etiquetas[], topicoCodigo, topicoNombre, texto, proyecto, autor}]
   * @param topicos  [{codigo, nombre, palabrasClave: "a,b,c"}]
   */
  function crearBuscador(docs, topicos) {
    var palabrasTopico = (topicos || []).map(function (t) {
      return { codigo: t.codigo, palabras: [normalizar(t.nombre)].concat((t.palabrasClave || "").split(",").map(normalizar).filter(Boolean)) };
    });
    var frases = [];
    SINONIMOS.forEach(function (g) { g.forEach(function (p) { if (p.indexOf(" ") > 0) frases.push(p); }); });
    palabrasTopico.forEach(function (t) { t.palabras.forEach(function (p) { if (p.indexOf(" ") > 0) frases.push(p); }); });
    frases.sort(function (a, b) { return b.length - a.length; });

    var indice = docs.map(function (d) {
      var titulo = normalizar(d.titulo);
      var cuerpo = normalizar(d.texto);
      var etiquetas = normalizar((d.etiquetas || []).join(" "));
      return {
        doc: d,
        titulo: titulo,
        etiquetas: etiquetas,
        topico: normalizar(d.topicoNombre),
        cuerpo: cuerpo,
        proyecto: normalizar(d.proyecto),
        autor: normalizar(d.autor),
        // Palabras para la búsqueda aproximada (errores de tipeo): título, etiquetas y texto.
        palabras: (titulo + " " + etiquetas + " " + cuerpo.slice(0, 1500)).split(" ").filter(function (w) { return w.length >= 4; })
      };
    });

    function tokenizar(consulta) {
      var q = " " + normalizar(consulta) + " ";
      var tokens = [];
      frases.forEach(function (f) {
        if (q.indexOf(" " + f + " ") >= 0) { tokens.push(f); q = q.replace(" " + f + " ", " "); }
      });
      q.split(" ").forEach(function (t) {
        if (t && !VACIAS[t] && tokens.indexOf(t) < 0) tokens.push(t);
      });
      return tokens;
    }

    function variantesDe(token) {
      var vs = [token];
      SINONIMOS.forEach(function (grupo) {
        var pertenece = grupo.some(function (p) { return p === token || (token.length >= 3 && p.indexOf(token) === 0); });
        if (pertenece) grupo.forEach(function (p) { if (vs.indexOf(p) < 0) vs.push(p); });
      });
      return vs;
    }

    function topicosDe(token) {
      var codigos = [];
      palabrasTopico.forEach(function (t) {
        if (t.palabras.some(function (p) { return p === token || (token.length >= 4 && p.indexOf(token) === 0); })) codigos.push(t.codigo);
      });
      return codigos;
    }

    /** @return {resultados:[{doc, puntaje, terminos[]}], tokens[], expansion[]} */
    function buscar(consulta) {
      var tokens = tokenizar(consulta);
      if (!tokens.length) return { resultados: [], tokens: [], expansion: [] };
      var plan = tokens.map(function (t) { return { token: t, variantes: variantesDe(t), topicos: topicosDe(t) }; });
      var expansionUsada = {};
      var resultados = [];

      indice.forEach(function (e) {
        var total = 0, terminos = [], ok = true;
        for (var k = 0; k < plan.length; k++) {
          var p = plan[k], mejor = 0;
          p.variantes.forEach(function (v, idx) {
            var peso = 0;
            if (contiene(e.titulo, v)) peso += 6;
            if (contiene(e.etiquetas, v)) peso += 5;
            if (contiene(e.topico, v)) peso += 4;
            if (contiene(e.cuerpo, v)) peso += 2;
            if (contiene(e.proyecto, v)) peso += 2;
            if (contiene(e.autor, v)) peso += 1;
            if (peso > 0) {
              if (idx > 0) { peso *= 0.7; expansionUsada[v] = true; }
              if (terminos.indexOf(v) < 0) terminos.push(v);
            }
            if (peso > mejor) mejor = peso;
          });
          if (p.topicos.indexOf(e.doc.topicoCodigo) >= 0) mejor += 3;
          if (mejor === 0 && p.token.length >= 5) {
            var max = p.token.length >= 8 ? 2 : 1;
            for (var w = 0; w < e.palabras.length; w++) {
              var palabra = e.palabras[w].slice(0, p.token.length + max);
              if (levenshtein1(p.token, palabra, max) <= max || levenshtein1(p.token, e.palabras[w], max) <= max) {
                mejor = 1.5;
                if (terminos.indexOf(e.palabras[w]) < 0) terminos.push(e.palabras[w]);
                break;
              }
            }
          }
          if (mejor === 0) { ok = false; break; }
          total += mejor;
        }
        if (ok) resultados.push({ doc: e.doc, puntaje: total, terminos: terminos });
      });

      resultados.sort(function (a, b) { return b.puntaje - a.puntaje; });
      var expansion = Object.keys(expansionUsada).filter(function (v) { return tokens.indexOf(v) < 0; }).slice(0, 5);
      return { resultados: resultados, tokens: tokens, expansion: expansion };
    }

    return { buscar: buscar, tokenizar: tokenizar };
  }

  /** Devuelve HTML escapado con <mark> sobre los términos (normalizados) encontrados. */
  function resaltar(texto, terminos) {
    texto = String(texto == null ? "" : texto);
    if (!terminos || !terminos.length) return escapar(texto);
    var norm = normalizar1a1(texto);
    var marcas = [];
    terminos.forEach(function (t) {
      if (!t) return;
      var desde = 0, pos;
      while ((pos = norm.indexOf(t, desde)) >= 0) {
        var antes = pos === 0 ? " " : norm[pos - 1];
        if (t.length > 3 || antes === " ") marcas.push([pos, pos + t.length]);
        desde = pos + t.length;
      }
    });
    if (!marcas.length) return escapar(texto);
    marcas.sort(function (a, b) { return a[0] - b[0]; });
    var unidas = [marcas[0]];
    for (var i = 1; i < marcas.length; i++) {
      var ult = unidas[unidas.length - 1];
      if (marcas[i][0] <= ult[1]) ult[1] = Math.max(ult[1], marcas[i][1]); else unidas.push(marcas[i]);
    }
    var html = "", cursor = 0;
    unidas.forEach(function (m) {
      html += escapar(texto.slice(cursor, m[0])) + '<mark class="fr-hl">' + escapar(texto.slice(m[0], m[1])) + "</mark>";
      cursor = m[1];
    });
    return html + escapar(texto.slice(cursor));
  }

  /** Fragmento del texto alrededor de la primera coincidencia (o null). */
  function fragmento(texto, terminos, largo) {
    texto = String(texto == null ? "" : texto).replace(/\s+/g, " ");
    var norm = normalizar1a1(texto);
    var pos = -1;
    (terminos || []).forEach(function (t) {
      var p = norm.indexOf(t);
      if (p >= 0 && (pos < 0 || p < pos)) pos = p;
    });
    if (pos < 0) return null;
    largo = largo || 200;
    // Si la coincidencia está cerca del inicio, se muestra desde el principio.
    var ini = pos < 110 ? 0 : pos - 60;
    var fin = Math.min(texto.length, ini + largo);
    if (ini > 0) { var esp = texto.indexOf(" ", ini); if (esp > 0 && esp < pos) ini = esp + 1; }
    return (ini > 0 ? "…" : "") + texto.slice(ini, fin).trim() + (fin < texto.length ? "…" : "");
  }

  // ───────────── Enlaces y fechas ─────────────

  /** Convierte URLs en <a> dentro de los nodos de texto de `el` (no usa innerHTML con texto del usuario). */
  function enlazar(el) {
    if (!el) return;
    var re = /https?:\/\/[^\s<>"']+/g;
    var nodos = [];
    var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null);
    while (walker.nextNode()) nodos.push(walker.currentNode);
    nodos.forEach(function (nodo) {
      var texto = nodo.nodeValue;
      if (!re.test(texto)) return;
      re.lastIndex = 0;
      var frag = document.createDocumentFragment(), cursor = 0, m;
      while ((m = re.exec(texto))) {
        var url = m[0].replace(/[.,;:!?)\]]+$/, "");
        frag.appendChild(document.createTextNode(texto.slice(cursor, m.index)));
        var a = document.createElement("a");
        a.href = url; a.textContent = url; a.target = "_blank"; a.rel = "noopener noreferrer";
        frag.appendChild(a);
        cursor = m.index + url.length;
        re.lastIndex = cursor;
      }
      frag.appendChild(document.createTextNode(texto.slice(cursor)));
      nodo.parentNode.replaceChild(frag, nodo);
    });
  }

  var MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

  function parsearIso(iso) {
    var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso || "");
    return m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]) : null;
  }

  function tiempoRelativo(iso) {
    var d = parsearIso(iso);
    if (!d) return "";
    var ahora = new Date();
    var seg = Math.round((ahora - d) / 1000);
    if (seg < 0) seg = 0;
    if (seg < 60) return "hace un momento";
    var min = Math.round(seg / 60);
    if (min < 60) return "hace " + min + " min";
    var h = Math.round(min / 60);
    if (h < 24 && d.getDate() === ahora.getDate()) return "hace " + h + " h";
    var ayer = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() - 1);
    if (d.getFullYear() === ayer.getFullYear() && d.getMonth() === ayer.getMonth() && d.getDate() === ayer.getDate()) return "ayer";
    var dias = Math.floor((new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate()) - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400000);
    if (dias < 7) return "hace " + dias + " días";
    return d.getDate() + " " + MESES[d.getMonth()] + (d.getFullYear() !== ahora.getFullYear() ? " " + d.getFullYear() : "");
  }

  /** Pinta todas las [data-relativo="ISO"] y deja la fecha completa en el title. */
  function pintarFechas(raiz) {
    (raiz || document).querySelectorAll("[data-relativo]").forEach(function (el) {
      var rel = tiempoRelativo(el.getAttribute("data-relativo"));
      if (!rel) return;
      if (!el.title) el.title = el.textContent.trim();
      el.textContent = rel;
    });
  }

  // ───────────── Adjuntos ─────────────

  var EXTENSIONES = ["png", "jpg", "jpeg", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "zip"];
  var MAX_BYTES = 5 * 1024 * 1024;

  function tamano(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  }

  var ICONO_CLIP = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m21.4 11.1-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5"/></svg>';
  var ICONO_ENLACE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/></svg>';
  var ICONO_RESPONDER = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/></svg>';

  /**
   * Selector de adjuntos. opciones: {input (file), chips, botonArchivo, botonEnlace,
   * filaEnlace, inputEnlace, agregarEnlace, contenedorEnlaces (donde van los hidden "enlaces"),
   * maxArchivos, maxEnlaces, aviso(msg)}
   */
  function crearAdjuntos(o) {
    var archivos = [], enlaces = [];
    var aviso = o.aviso || function (m) { if (window.SBAI && SBAI.toast) SBAI.toast.show(m, { type: "error" }); };

    function sincronizar() {
      try {
        var dt = new DataTransfer();
        archivos.forEach(function (f) { dt.items.add(f); });
        o.input.files = dt.files;
      } catch (e) { /* navegador sin DataTransfer: se envía lo que tenga el input */ }
      if (o.contenedorEnlaces) {
        o.contenedorEnlaces.innerHTML = "";
        enlaces.forEach(function (u) {
          var h = document.createElement("input");
          h.type = "hidden"; h.name = "enlaces"; h.value = u;
          o.contenedorEnlaces.appendChild(h);
        });
      }
      pintar();
    }

    function pintar() {
      o.chips.innerHTML = "";
      archivos.forEach(function (f, i) {
        o.chips.appendChild(chip(ICONO_CLIP, f.name, tamano(f.size), function () { archivos.splice(i, 1); sincronizar(); }));
      });
      enlaces.forEach(function (u, i) {
        o.chips.appendChild(chip(ICONO_ENLACE, u.replace(/^https?:\/\/(www\.)?/i, ""), "enlace", function () { enlaces.splice(i, 1); sincronizar(); }));
      });
    }

    function chip(icono, nombre, detalle, quitar) {
      var el = document.createElement("span");
      el.className = "fr-file-chip";
      el.innerHTML = '<span style="display:inline-flex;width:14px;height:14px;flex-shrink:0">' + icono + "</span><span></span><small></small>" +
        '<button type="button" aria-label="Quitar"><svg class="icon"><use href="/img/icons.svg#icon-x"></use></svg></button>';
      el.children[1].textContent = nombre;
      el.children[2].textContent = detalle;
      el.querySelector("button").addEventListener("click", quitar);
      return el;
    }

    function agregarArchivos(lista) {
      Array.prototype.forEach.call(lista, function (f) {
        var ext = (f.name.split(".").pop() || "").toLowerCase();
        if (EXTENSIONES.indexOf(ext) < 0) { aviso("\"" + f.name + "\" no es un formato permitido."); return; }
        if (f.size > MAX_BYTES) { aviso("\"" + f.name + "\" pesa más de 5 MB."); return; }
        if (archivos.length >= o.maxArchivos) { aviso("Puedes adjuntar hasta " + o.maxArchivos + " archivos."); return; }
        if (archivos.some(function (a) { return a.name === f.name && a.size === f.size; })) return;
        archivos.push(f);
      });
      sincronizar();
    }

    o.botonArchivo.addEventListener("click", function () {
      var temp = document.createElement("input");
      temp.type = "file"; temp.multiple = true;
      temp.accept = EXTENSIONES.map(function (e) { return "." + e; }).join(",");
      temp.addEventListener("change", function () { agregarArchivos(temp.files); });
      temp.click();
    });

    if (o.botonEnlace) {
      o.botonEnlace.addEventListener("click", function () {
        o.filaEnlace.hidden = !o.filaEnlace.hidden;
        if (!o.filaEnlace.hidden) o.inputEnlace.focus();
      });
      function agregar() {
        var u = o.inputEnlace.value.trim();
        if (!u) return;
        if (!/^https?:\/\/[^\s<>"]+$/i.test(u)) { aviso("El enlace debe empezar con http:// o https://"); return; }
        if (enlaces.length >= o.maxEnlaces) { aviso("Puedes agregar hasta " + o.maxEnlaces + " enlaces."); return; }
        if (enlaces.indexOf(u) < 0) enlaces.push(u);
        o.inputEnlace.value = "";
        o.filaEnlace.hidden = true;
        sincronizar();
      }
      o.agregarEnlace.addEventListener("click", agregar);
      o.inputEnlace.addEventListener("keydown", function (e) { if (e.key === "Enter") { e.preventDefault(); agregar(); } });
    }

    return {
      agregarArchivos: agregarArchivos,
      cantidad: function () { return archivos.length + enlaces.length; },
      enlacePendiente: function () { return o.inputEnlace && o.inputEnlace.value.trim(); }
    };
  }

  window.SBForo = {
    escapar: escapar,
    normalizar: normalizar,
    crearBuscador: crearBuscador,
    resaltar: resaltar,
    fragmento: fragmento,
    enlazar: enlazar,
    tiempoRelativo: tiempoRelativo,
    pintarFechas: pintarFechas,
    crearAdjuntos: crearAdjuntos,
    iconos: { clip: ICONO_CLIP, enlace: ICONO_ENLACE, responder: ICONO_RESPONDER }
  };
})();
