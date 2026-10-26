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
    document.querySelectorAll("[data-validar-habilidad]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var nombre = btn.dataset.habilidadNombre || "esta habilidad";
        var sinConstancia = btn.dataset.sinConstancia === "true";
        SBAI.modal.confirm({
          title: "¿Validar \"" + nombre + "\"?",
          desc: (sinConstancia
            ? "No adjuntó una constancia: la validación quedará solo con tu criterio. "
            : "") + "Quedará marcada como verificada: se usará en Talent Matching y se mostrará en su perfil.",
          confirmLabel: "Validar",
          onConfirm: function () {
            SBAI.submitForm("/resource-manager/colaboradores/" + perfilId + "/habilidades/" + btn.dataset.validarHabilidad + "/validar", {});
          }
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
