/**
 * command-palette.js — Buscador global ⌘K / Ctrl+K.
 * Filtra colaboradores, proyectos y habilidades del dataset mock y
 * ofrece una acción razonable al seleccionar un resultado, según el rol
 * activo (nunca deja un resultado sin acción).
 */
(function () {
  "use strict";

  var backdropEl = null;
  var currentRole = null;

  function normalize(str) { return window.SBAI.tableFilter.normalize(str); }
  function icon(name) { return window.SBAI.modal.icon(name); }

  function buildIndex() {
    var items = [];
    MOCK.collaborators.forEach(function (c) {
      items.push({ type: "Colaboradores", icon: "icon-user", title: c.nombre, sub: c.cargo + " · " + c.area, ref: c });
    });
    MOCK.projects.forEach(function (p) {
      items.push({ type: "Proyectos", icon: "icon-folder", title: p.nombre, sub: p.cliente + " · " + p.estado, ref: p });
    });
    MOCK.skillsCatalog.forEach(function (s) {
      items.push({ type: "Habilidades", icon: "icon-book", title: s.nombre, sub: s.categoria + " · demanda " + s.demanda, ref: s });
    });
    return items;
  }

  function render(query) {
    var index = buildIndex();
    var q = normalize(query);
    var results = q ? index.filter(function (it) { return normalize(it.title + " " + it.sub).indexOf(q) !== -1; }) : index.slice(0, 7);
    var resultsEl = backdropEl.querySelector(".cmdk-results");

    if (!results.length) {
      resultsEl.innerHTML = '<div class="cmdk-empty">Sin resultados para “' + query + '”. Prueba con otro nombre.</div>';
      return;
    }

    var groups = {};
    results.forEach(function (it) {
      groups[it.type] = groups[it.type] || [];
      groups[it.type].push(it);
    });

    var html = "";
    Object.keys(groups).forEach(function (groupName) {
      html += '<div class="cmdk-group-label">' + groupName + "</div>";
      groups[groupName].slice(0, 6).forEach(function (it, idx) {
        html += '<div class="cmdk-item" data-cmdk-type="' + it.type + '" data-cmdk-title="' + it.title.replace(/"/g, "&quot;") + '">' +
          '<span class="cmdk-item-icon">' + icon(it.icon) + "</span>" +
          '<span class="cmdk-item-text"><span class="cmdk-item-title">' + it.title + '</span><span class="cmdk-item-sub">' + it.sub + "</span></span>" +
          "</div>";
      });
    });
    resultsEl.innerHTML = html;

    resultsEl.querySelectorAll(".cmdk-item").forEach(function (el) {
      el.addEventListener("click", function () {
        onSelect(el.dataset.cmdkType, el.dataset.cmdkTitle);
      });
    });
  }

  function onSelect(type, title) {
    close();
    if (type === "Proyectos") {
      var p = MOCK.findProject(title);
      if (currentRole === "project-manager" && title === "Núcleo Retail") {
        window.location.href = "proyecto-detalle.html";
        return;
      }
      window.SBAI.toast.show("<strong>" + title + "</strong> · " + p.cliente + " · avance " + p.avance + "% · " + p.estado, { icon: "icon-folder" });
      return;
    }
    if (type === "Colaboradores") {
      var c = MOCK.findCollaborator(title);
      if (currentRole === "resource-manager") {
        window.location.href = "colaboradores.html?buscar=" + encodeURIComponent(title);
        return;
      }
      window.SBAI.toast.show("<strong>" + title + "</strong> · " + c.cargo + " · ocupación promedio " + c.ocupacionProm + "%", { icon: "icon-user" });
      return;
    }
    if (type === "Habilidades") {
      if (currentRole === "administrador") {
        window.location.href = "habilidades.html?buscar=" + encodeURIComponent(title);
        return;
      }
      window.SBAI.toast.show("Mostrando <strong>" + title + "</strong> en el catálogo de habilidades.", { icon: "icon-book" });
      return;
    }
  }

  function open() {
    if (backdropEl) return;
    backdropEl = document.createElement("div");
    backdropEl.className = "cmdk-backdrop";
    backdropEl.innerHTML =
      '<div class="cmdk" role="dialog" aria-modal="true" aria-label="Buscar">' +
      '<div class="cmdk-input-row">' + icon("icon-search") +
      '<input type="text" class="cmdk-input" placeholder="Buscar colaboradores, proyectos o habilidades…" autocomplete="off">' +
      "<kbd>ESC</kbd></div>" +
      '<div class="cmdk-results"></div></div>';
    document.body.appendChild(backdropEl);
    document.body.style.overflow = "hidden";

    var input = backdropEl.querySelector(".cmdk-input");
    input.addEventListener("input", function () { render(input.value); });
    backdropEl.addEventListener("mousedown", function (e) { if (e.target === backdropEl) close(); });
    render("");
    setTimeout(function () { input.focus(); }, 30);
  }

  function close() {
    if (!backdropEl) return;
    backdropEl.parentNode.removeChild(backdropEl);
    backdropEl = null;
    document.body.style.overflow = "";
  }

  function init(role) {
    currentRole = role;
    document.addEventListener("keydown", function (e) {
      var isCmdK = (e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K");
      if (isCmdK) {
        e.preventDefault();
        backdropEl ? close() : open();
        return;
      }
      if (e.key === "Escape" && backdropEl) close();
    });
    document.querySelectorAll("[data-open-command-palette]").forEach(function (el) {
      el.addEventListener("click", open);
    });
  }

  window.SBAI = window.SBAI || {};
  window.SBAI.commandPalette = { init: init, open: open, close: close };
})();
