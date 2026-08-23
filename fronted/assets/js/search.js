/* SkillBridge AI — búsqueda en vivo del topbar (datos hardcodeados). */
(function () {
  "use strict";

  var COLABORADORES = [
    "Mariana Ruiz", "Javier Molina", "Paula Vega", "Ana Villalba", "Tomás Herrera",
    "Diego Salazar", "Camila Ortega", "Lucía Fernández", "Andrés Peña", "Sofía Cárdenas",
    "Ricardo Bastos"
  ];
  var PROYECTOS = [
    "Portal Andes", "App Móvil Aurora", "Núcleo Retail", "Motor Cobranzas v2",
    "Migración Cloud Titán", "Intranet NexaCorp v2", "Data Lake Seguros Vital"
  ];
  var HABILIDADES = [
    "Kubernetes", "Java / Spring Boot", "React", "Apache Kafka", "Data Engineering",
    "PostgreSQL", "Terraform", "GitLab CI"
  ];

  // A dónde manda cada categoría de resultado según el rol activo. Si el rol
  // no tiene una pantalla propia para esa categoría, cae a su Inicio.
  var ROLE_TARGETS = {
    "colaborador": { colaboradores: "perfil.html", proyectos: "dashboard.html", habilidades: "perfil.html" },
    "project-manager": { colaboradores: "asignaciones.html", proyectos: "proyectos.html", habilidades: "talent-matching.html" },
    "resource-manager": { colaboradores: "ocupacion.html", proyectos: "asignaciones.html", habilidades: "talent-matching.html" },
    "administrador": { colaboradores: "usuarios.html", proyectos: "proyectos.html", habilidades: "catalogo-habilidades.html" }
  };

  var ICONS = {
    colaboradores: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
    proyectos: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/></svg>',
    habilidades: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 3 1.9 4.6L18 9l-4.1 1.4L12 15l-1.9-4.6L6 9l4.1-1.4Z"/><circle cx="18" cy="18" r="2.5"/></svg>'
  };

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function initSearch(root) {
    var role = root.getAttribute("data-role");
    var input = root.querySelector(".search-input");
    var dropdown = root.querySelector("[data-search-dropdown]");
    if (!input || !dropdown) return;
    var targets = ROLE_TARGETS[role] || {};

    function renderGroup(label, key, items, query) {
      var matches = items.filter(function (i) { return i.toLowerCase().indexOf(query) !== -1; });
      if (!matches.length) return "";
      var href = targets[key] || "dashboard.html";
      var html = '<div class="search-cat">' + label + '</div>';
      matches.slice(0, 5).forEach(function (m) {
        html += '<a class="search-item" href="' + href + '">' +
          '<span class="search-item-icon">' + ICONS[key] + '</span>' +
          '<span class="search-item-text">' +
          '<span class="search-item-title">' + escapeHtml(m) + '</span>' +
          '<span class="search-item-desc">Ver en ' + escapeHtml(label.toLowerCase()) + '</span>' +
          '</span></a>';
      });
      return html;
    }

    input.addEventListener("input", function () {
      var query = input.value.trim().toLowerCase();
      if (!query) {
        dropdown.classList.remove("open");
        dropdown.innerHTML = "";
        return;
      }
      var html = renderGroup("Colaboradores", "colaboradores", COLABORADORES, query) +
        renderGroup("Proyectos", "proyectos", PROYECTOS, query) +
        renderGroup("Habilidades", "habilidades", HABILIDADES, query);

      dropdown.innerHTML = html || '<div class="search-empty">Sin resultados para "' + escapeHtml(input.value.trim()) + '"</div>';
      dropdown.classList.add("open");
    });

    document.addEventListener("click", function (e) {
      if (!root.contains(e.target)) {
        dropdown.classList.remove("open");
      }
    });

    input.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        dropdown.classList.remove("open");
        input.blur();
      }
    });
  }

  document.querySelectorAll("[data-search-root]").forEach(initSearch);

  // Atajo ⌘K / Ctrl+K enfoca el buscador de la página actual.
  document.addEventListener("keydown", function (e) {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
      var input = document.querySelector(".search-input");
      if (input) {
        e.preventDefault();
        input.focus();
      }
    }
  });
})();
