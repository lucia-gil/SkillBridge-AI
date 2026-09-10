/**
 * mock-data.js — Dataset simulado de SkillBridge AI.
 *
 * Todo el estado de la demo vive aquí, en memoria. No hay llamadas a
 * backend ni APIs externas. Cada arreglo está pensado para migrar
 * directo a un modelo de Spring (th:each="x : ${lista}") — por eso los
 * campos usan nombres planos y predecibles en vez de estructuras anidadas
 * innecesarias.
 *
 * Namespace único: window.MOCK
 */
(function () {
  "use strict";

  var MOCK = {};

  /* ---------------------------------------------------------
   * Metadatos de plataforma
   * --------------------------------------------------------- */
  MOCK.meta = {
    org: "NexaCorp",
    domain: "nexacorp.com",
    productName: "SkillBridge AI",
    version: "v2.4.0",
    todayLabel: "Martes 18 de agosto, 2026",
    todayShort: "18 ago 2026",
    stats: { colaboradores: 184, proyectosActivos: 27, habilidadesMapeadas: 312 }
  };

  /* ---------------------------------------------------------
   * Usuarios de referencia por rol (los 4 usuarios "de sesión")
   * --------------------------------------------------------- */
  MOCK.roleUsers = {
    colaborador: {
      id: "COL-0142",
      nombre: "Mariana Ruiz",
      iniciales: "MR",
      correo: "mariana.ruiz@nexacorp.com",
      cargo: "Frontend Engineer Sr.",
      celula: "Célula Andes",
      area: "Ingeniería de Software",
      ubicacion: "Bogotá, CO (GMT-5)",
      ingreso: "12 feb 2022",
      manager: "Javier Molina",
      telefono: "+57 310 442 1180",
      rolLabel: "Colaborador",
      rolSlug: "colaborador",
      disponibilidad: 20,
      cargaTotal: 80,
      alcanceRol: "Ve solo sus proyectos, su perfil, foros y el asistente IA."
    },
    pm: {
      id: "PM-0031",
      nombre: "Javier Molina",
      iniciales: "JM",
      correo: "javier.molina@nexacorp.com",
      cargo: "Project Manager",
      area: "Delivery",
      ubicacion: "Bogotá, CO (GMT-5)",
      telefono: "+57 315 908 2244",
      rolLabel: "Project Manager",
      rolSlug: "project-manager",
      alcanceRol: "Gestiona 4 proyectos, sus equipos y ejecuta el matching de talento."
    },
    rm: {
      id: "RM-0009",
      nombre: "Paula Vega",
      iniciales: "PV",
      correo: "paula.vega@nexacorp.com",
      cargo: "Resource Manager",
      area: "Delivery",
      telefono: "+57 300 771 5590",
      rolLabel: "Resource Manager",
      rolSlug: "resource-manager",
      alcanceRol: "Aprueba asignaciones y excepciones de carga de los 184 colaboradores."
    },
    admin: {
      id: "ADM-0002",
      nombre: "Ana Villalba",
      iniciales: "AV",
      correo: "ana.villalba@nexacorp.com",
      cargo: "Administradora de plataforma",
      area: "TI Corporativa",
      telefono: "+57 320 118 3376",
      rolLabel: "Administrador",
      rolSlug: "administrador",
      alcanceRol: "Acceso total: usuarios, roles, catálogo, configuración y auditoría."
    }
  };

  /* ---------------------------------------------------------
   * Colaboradores operativos (equipo evaluado en asignaciones,
   * ocupación y matching). Incluye a Mariana como colaboradora.
   * --------------------------------------------------------- */
  MOCK.collaborators = [
    { id: "COL-0142", nombre: "Mariana Ruiz", iniciales: "MR", correo: "mariana.ruiz@nexacorp.com", cargo: "Frontend Engineer Sr.", area: "Ingeniería", celula: "Andes", estado: "Activo", ultimoAcceso: "hoy, 09:02", ocupacionProm: 83,
      skillsTop: ["React", "TypeScript", "Thymeleaf"] },
    { id: "COL-0087", nombre: "Diego Salazar", iniciales: "DS", correo: "diego.salazar@nexacorp.com", cargo: "Tech Lead Backend", area: "Ingeniería", celula: "Andes", estado: "Activo", ultimoAcceso: "hoy, 09:40", ocupacionProm: 83,
      skillsTop: ["Java / Spring Boot", "Kafka", "PostgreSQL"] },
    { id: "COL-0104", nombre: "Tomás Herrera", iniciales: "TH", correo: "tomas.herrera@nexacorp.com", cargo: "Backend Engineer Sr.", area: "Ingeniería", celula: "Aurora", estado: "Activo", ultimoAcceso: "ayer, 19:12", ocupacionProm: 105,
      skillsTop: ["Java / Spring Boot", "React", "PostgreSQL"] },
    { id: "COL-0055", nombre: "Camila Ortega", iniciales: "CO", correo: "camila.ortega@nexacorp.com", cargo: "Data Engineer", area: "Datos", celula: "Retail", estado: "Activo", ultimoAcceso: "hoy, 08:10", ocupacionProm: 80,
      skillsTop: ["PostgreSQL", "Apache Kafka", "Python"] },
    { id: "COL-0118", nombre: "Lucía Fernández", iniciales: "LF", correo: "lucia.fernandez@nexacorp.com", cargo: "DevOps Engineer Sr.", area: "Plataforma", celula: "Retail", estado: "Activo", ultimoAcceso: "hoy, 07:44", ocupacionProm: 83,
      skillsTop: ["Kubernetes", "Terraform", "Kafka"] },
    { id: "COL-0071", nombre: "Andrés Peña", iniciales: "AP", correo: "andres.pena@nexacorp.com", cargo: "QA Engineer", area: "Calidad", celula: "Andes", estado: "Activo", ultimoAcceso: "hoy, 08:55", ocupacionProm: 75,
      skillsTop: ["Selenium", "Accesibilidad WCAG", "Playwright"] },
    { id: "COL-0063", nombre: "Sofía Cárdenas", iniciales: "SC", correo: "sofia.cardenas@nexacorp.com", cargo: "UX Designer", area: "Diseño", celula: "Aurora", estado: "Activo", ultimoAcceso: "hoy, 08:20", ocupacionProm: 51,
      skillsTop: ["Figma", "Investigación UX", "Accesibilidad WCAG"] },
    { id: "COL-0029", nombre: "Ricardo Bastos", iniciales: "RB", correo: "ricardo.bastos@nexacorp.com", cargo: "Platform Engineer", area: "Plataforma", celula: "Interno", estado: "Bloqueado", ultimoAcceso: "12 ago 2026", ocupacionProm: 40,
      skillsTop: ["Kubernetes", "Terraform", "Docker"] }
  ];

  function findCollaborator(nombre) {
    for (var i = 0; i < MOCK.collaborators.length; i++) {
      if (MOCK.collaborators[i].nombre === nombre) return MOCK.collaborators[i];
    }
    return null;
  }
  MOCK.findCollaborator = findCollaborator;

  /* ---------------------------------------------------------
   * Proyectos
   * --------------------------------------------------------- */
  MOCK.projects = [
    { codigo: "PRJ-PA-2026", nombre: "Portal Andes", iniciales: "PA", cliente: "BanCredit", pm: "Javier Molina",
      inicio: "02 mar 2026", fin: "30 nov 2026", equipo: 7, avance: 68, estado: "Activo",
      descripcion: "Portal transaccional de banca digital para BanCredit: onboarding de clientes, pagos y centro de notificaciones. Rediseño completo del design system y migración del backend legado a Spring Boot 3.",
      stack: ["Java 21", "Spring Boot 3.3", "Thymeleaf", "React 18", "PostgreSQL 16", "GitLab CI"] },
    { codigo: "PRJ-AU-2026", nombre: "App Móvil Aurora", iniciales: "AU", cliente: "Aurora Telecom", pm: "Javier Molina",
      inicio: "15 jun 2026", fin: "15 dic 2026", equipo: 6, avance: 34, estado: "En riesgo",
      descripcion: "App móvil de autogestión para clientes de Aurora Telecom: consumo en tiempo real, pagos y soporte por chat. Sprint 7 cerró 18% bajo el plan.",
      stack: ["React Native", "Java / Spring Boot", "PostgreSQL", "Figma"] },
    { codigo: "PRJ-NR-2026", nombre: "Núcleo Retail", iniciales: "NR", cliente: "Grupo Sol", pm: "Javier Molina",
      inicio: "01 sep 2026", fin: "28 feb 2027", equipo: 5, avance: 12, estado: "Planeado",
      descripcion: "Reemplazo del core transaccional de tiendas de Grupo Sol: catálogo unificado, inventario en tiempo real y motor de promociones. Incluye migración de 240 puntos de venta y una capa de eventos con Kafka para sincronización offline-first.",
      stack: ["Java 21", "Spring Boot 3.3", "Thymeleaf", "React 18", "PostgreSQL 16", "Kafka", "Kubernetes", "Terraform", "GitLab CI"],
      habilidadesRequeridas: [
        { nombre: "Kubernetes", nivel: "Avanzado", estado: "1 vacante" },
        { nombre: "Java / Spring Boot", nivel: "Avanzado", estado: "cubierto" },
        { nombre: "PostgreSQL", nivel: "Intermedio", estado: "cubierto" },
        { nombre: "React", nivel: "Avanzado", estado: "cubierto" },
        { nombre: "Apache Kafka", nivel: "Intermedio", estado: "parcial" },
        { nombre: "Terraform", nivel: "Básico", estado: "cubierto" }
      ],
      cronograma: [
        { fase: "Descubrimiento y arquitectura", rango: "sep" },
        { fase: "Plataforma e infraestructura", rango: "sep–oct" },
        { fase: "Desarrollo de módulos core", rango: "oct–dic" },
        { fase: "QA y pruebas de carga", rango: "ene" },
        { fase: "Estabilización y hand-off", rango: "feb" }
      ],
      equipoAsignado: [
        { nombre: "Camila Ortega", rol: "Data Engineer", desde: "01 sep 2026", dedicacion: 40 },
        { nombre: "Lucía Fernández", rol: "DevOps", desde: "01 sep 2026", dedicacion: 50 },
        { nombre: "Mariana Ruiz", rol: "Consultora UI", desde: "15 sep 2026", dedicacion: 20 },
        { nombre: "Andrés Peña", rol: "QA", desde: "01 oct 2026", dedicacion: 30 },
        { nombre: null, rol: "Vacante — Kubernetes Avanzado / Platform Eng.", desde: "por definir", dedicacion: 60 }
      ],
      resumenIA: "El equipo cubre 5 de 6 habilidades requeridas. La brecha crítica es Kubernetes Avanzado: sin cubrir, la fase de plataforma (sep–oct) arrancaría con 60% de capacidad. Lucía Fernández es el mejor match interno (94%)." },
    { codigo: "PRJ-MC-2026", nombre: "Motor Cobranzas v2", iniciales: "MC", cliente: "Seguros Vital", pm: "Javier Molina",
      inicio: "10 ene 2026", fin: "10 sep 2026", equipo: 5, avance: 91, estado: "Activo",
      descripcion: "Reescritura del motor de cobranzas y gestión de mora de Seguros Vital, con reglas de negocio configurables y trazabilidad completa para auditoría.",
      stack: ["Java 21", "Spring Boot 3.3", "PostgreSQL", "Kafka"] },
    { codigo: "PRJ-CT-2026", nombre: "Migración Cloud Titán", iniciales: "CT", cliente: "Titán Logística", pm: "Paula Vega",
      inicio: "05 ene 2025", fin: "22 ago 2025", equipo: 8, avance: 100, estado: "Cerrado",
      descripcion: "Migración de la plataforma logística de Titán a infraestructura cloud con Kubernetes y Terraform, liderada por Lucía Fernández.",
      stack: ["Kubernetes", "Terraform", "Docker", "Java / Spring Boot"] },
    { codigo: "PRJ-IN-2026", nombre: "Intranet NexaCorp v2", iniciales: "IN", cliente: "Interno", pm: "Paula Vega",
      inicio: "20 jul 2026", fin: "20 ene 2027", equipo: 4, avance: 41, estado: "Activo",
      descripcion: "Segunda versión de la intranet corporativa de NexaCorp — la misma plataforma SkillBridge AI que estás usando.",
      stack: ["Java 21", "Spring Boot", "Thymeleaf", "PostgreSQL"] },
    { codigo: "PRJ-SV-2026", nombre: "Data Lake Seguros Vital", iniciales: "SV", cliente: "Seguros Vital", pm: "Paula Vega",
      inicio: "01 ago 2026", fin: "30 abr 2027", equipo: 6, avance: 22, estado: "Bloqueado",
      descripcion: "Data lake corporativo para consolidar pólizas, siniestros y cobranzas de Seguros Vital en una capa analítica única.",
      stack: ["Apache Kafka", "PostgreSQL", "Python", "Terraform"] },
    { codigo: "PRJ-FX-2026", nombre: "Motor de Fraude FinTrust", iniciales: "FX", cliente: "FinTrust Pagos", pm: "Javier Molina",
      inicio: "12 abr 2026", fin: "20 dic 2026", equipo: 6, avance: 55, estado: "Activo",
      descripcion: "Motor de detección de fraude en tiempo real para FinTrust Pagos, con reglas configurables y modelos de scoring sobre el histórico de transacciones.",
      stack: ["Java 21", "Spring Boot 3.3", "Kafka", "PostgreSQL", "Python"] },
    { codigo: "PRJ-ED-2026", nombre: "Portal Educativo Andes+", iniciales: "ED", cliente: "Fundación Andes+", pm: "Paula Vega",
      inicio: "01 oct 2026", fin: "01 jun 2027", equipo: 4, avance: 5, estado: "Planeado",
      descripcion: "Portal de becas y seguimiento académico para la Fundación Andes+, con panel para tutores y reportes de deserción temprana.",
      stack: ["React 18", "Java / Spring Boot", "PostgreSQL", "Figma"] },
    { codigo: "PRJ-HL-2026", nombre: "App Salud Vitalis", iniciales: "HL", cliente: "Vitalis Salud", pm: "Javier Molina",
      inicio: "18 may 2026", fin: "18 nov 2026", equipo: 5, avance: 29, estado: "En riesgo",
      descripcion: "App de telemedicina para Vitalis Salud: agenda de citas, historia clínica básica y videoconsulta. Retraso por integración con el proveedor de videollamadas.",
      stack: ["React Native", "Java / Spring Boot", "PostgreSQL", "WebRTC"] },
    { codigo: "PRJ-LG-2026", nombre: "Optimizador Rutas LogiSur", iniciales: "LG", cliente: "LogiSur", pm: "Paula Vega",
      inicio: "03 feb 2026", fin: "03 oct 2026", equipo: 5, avance: 72, estado: "Activo",
      descripcion: "Optimizador de rutas de última milla para LogiSur, con recálculo dinámico según tráfico y ventanas de entrega.",
      stack: ["Java 21", "Spring Boot", "PostgreSQL", "Python", "Kubernetes"] },
    { codigo: "PRJ-RT-2026", nombre: "CRM Retail Andino", iniciales: "RT", cliente: "Retail Andino", pm: "Javier Molina",
      inicio: "10 mar 2026", fin: "10 sep 2026", equipo: 5, avance: 40, estado: "Bloqueado",
      descripcion: "CRM omnicanal para Retail Andino, bloqueado a la espera de la definición del modelo de fidelización por parte del cliente.",
      stack: ["React 18", "Java / Spring Boot", "PostgreSQL"] },
    { codigo: "PRJ-IOT-2026", nombre: "Plataforma IoT Sensores Sol", iniciales: "IO", cliente: "Grupo Sol", pm: "Paula Vega",
      inicio: "15 nov 2026", fin: "15 jul 2027", equipo: 6, avance: 0, estado: "Planeado",
      descripcion: "Plataforma de ingesta y monitoreo de sensores IoT en tiendas de Grupo Sol, para inventario en tiempo real y alertas de cadena de frío.",
      stack: ["Kafka", "Kubernetes", "Python", "Terraform", "PostgreSQL"] }
  ];

  function findProject(nombre) {
    for (var i = 0; i < MOCK.projects.length; i++) {
      if (MOCK.projects[i].nombre === nombre) return MOCK.projects[i];
    }
    return null;
  }
  MOCK.findProject = findProject;

  /* ---------------------------------------------------------
   * Asignaciones (colaborador × proyecto)
   * --------------------------------------------------------- */
  MOCK.assignments = [
    { colaborador: "Mariana Ruiz", iniciales: "MR", proyecto: "Portal Andes", dedicacion: 45, periodo: "02 mar — 30 nov 2026", cargaTotal: 80, validacion: "Validada" },
    { colaborador: "Diego Salazar", iniciales: "DS", proyecto: "Portal Andes", dedicacion: 50, periodo: "02 mar — 30 nov 2026", cargaTotal: 95, validacion: "Al límite" },
    { colaborador: "Andrés Peña", iniciales: "AP", proyecto: "Portal Andes", dedicacion: 30, periodo: "11 may — 30 nov 2026", cargaTotal: 60, validacion: "Validada" },
    { colaborador: "Tomás Herrera", iniciales: "TH", proyecto: "App Móvil Aurora", dedicacion: 70, periodo: "15 jun — 15 dic 2026", cargaTotal: 120, validacion: "Bloqueada" },
    { colaborador: "Sofía Cárdenas", iniciales: "SC", proyecto: "App Móvil Aurora", dedicacion: 70, periodo: "15 jun — 30 oct 2026", cargaTotal: 70, validacion: "Validada" },
    { colaborador: "Mariana Ruiz", iniciales: "MR", proyecto: "App Móvil Aurora", dedicacion: 35, periodo: "01 jul — 15 dic 2026", cargaTotal: 80, validacion: "Validada" },
    { colaborador: "Lucía Fernández", iniciales: "LF", proyecto: "Núcleo Retail", dedicacion: 50, periodo: "01 sep 26 — 28 feb 27", cargaTotal: 110, validacion: "En revisión" },
    { colaborador: "Camila Ortega", iniciales: "CO", proyecto: "Núcleo Retail", dedicacion: 40, periodo: "01 sep 26 — 28 feb 27", cargaTotal: 85, validacion: "Validada" },
    { colaborador: null, iniciales: "—", proyecto: "Núcleo Retail", vacanteNombre: "Vacante K8s Avanzado", dedicacion: 60, periodo: "por definir", cargaTotal: null, validacion: "Sin cubrir" },
    { colaborador: "Tomás Herrera", iniciales: "TH", proyecto: "Motor Cobranzas v2", dedicacion: 50, periodo: "ene — sep 2026", cargaTotal: 120, validacion: "Bloqueada" },
    { colaborador: "Diego Salazar", iniciales: "DS", proyecto: "Motor Cobranzas v2", dedicacion: 45, periodo: "ene — sep 2026", cargaTotal: 95, validacion: "Al límite" },
    { colaborador: "Camila Ortega", iniciales: "CO", proyecto: "Motor Cobranzas v2", dedicacion: 45, periodo: "feb — sep 2026", cargaTotal: 85, validacion: "Validada" }
  ];

  MOCK.assignmentsKpi = { total: 23, validadas: 19, enRevision: 2, bloqueadas: 1, vacantes: 1 };

  /* ---------------------------------------------------------
   * Catálogo de habilidades
   * --------------------------------------------------------- */
  MOCK.skillCategories = [
    { nombre: "Backend", cantidad: 72 }, { nombre: "Frontend", cantidad: 54 },
    { nombre: "DevOps & Cloud", cantidad: 48 }, { nombre: "Datos", cantidad: 41 },
    { nombre: "QA", cantidad: 33 }, { nombre: "Diseño", cantidad: 26 },
    { nombre: "Gestión", cantidad: 21 }, { nombre: "Seguridad", cantidad: 17 }
  ];

  MOCK.forumCategoriesCount = [
    { nombre: "Backend", cantidad: 72 }, { nombre: "Frontend", cantidad: 54 },
    { nombre: "Arquitectura", cantidad: 41 }, { nombre: "DevOps & Cloud", cantidad: 38 },
    { nombre: "Datos e IA", cantidad: 27 }, { nombre: "Procesos y QA", cantidad: 16 }
  ];

  MOCK.skillsCatalog = [
    { nombre: "Kubernetes", categoria: "DevOps & Cloud", personas: 22, proyectos: 9, nivelMedio: "Avanzado", demanda: "Alta" },
    { nombre: "Java / Spring Boot", categoria: "Backend", personas: 68, proyectos: 21, nivelMedio: "Avanzado", demanda: "Alta" },
    { nombre: "React", categoria: "Frontend", personas: 45, proyectos: 14, nivelMedio: "Intermedio", demanda: "Media" },
    { nombre: "Apache Kafka", categoria: "Backend", personas: 19, proyectos: 7, nivelMedio: "Intermedio", demanda: "Alta" },
    { nombre: "PostgreSQL", categoria: "Datos", personas: 57, proyectos: 24, nivelMedio: "Intermedio", demanda: "Media" },
    { nombre: "Terraform", categoria: "DevOps & Cloud", personas: 14, proyectos: 6, nivelMedio: "Básico", demanda: "Media" },
    { nombre: "Thymeleaf", categoria: "Frontend", personas: 31, proyectos: 11, nivelMedio: "Intermedio", demanda: "Baja" },
    { nombre: "Selenium", categoria: "QA", personas: 17, proyectos: 8, nivelMedio: "Básico", demanda: "Baja" },
    { nombre: "Figma", categoria: "Diseño", personas: 12, proyectos: 5, nivelMedio: "Intermedio", demanda: "Baja" },
    { nombre: "OpenTelemetry", categoria: "DevOps & Cloud", personas: 4, proyectos: 2, nivelMedio: "Básico", demanda: "Media" }
  ];

  MOCK.skillsDemand = [
    { nombre: "Kubernetes", vacantes: 14 }, { nombre: "Java / Spring Boot", vacantes: 11 },
    { nombre: "React", vacantes: 9 }, { nombre: "Apache Kafka", vacantes: 7 },
    { nombre: "Data Engineering", vacantes: 5 }, { nombre: "Terraform", vacantes: 4 },
    { nombre: "UX Research", vacantes: 3 }, { nombre: "Selenium", vacantes: 2 }
  ];

  MOCK.marianaSkills = [
    { nombre: "React", nivel: "Experto", anios: "6 años" },
    { nombre: "TypeScript", nivel: "Avanzado", anios: "5 años" },
    { nombre: "Thymeleaf", nivel: "Avanzado", anios: "3 años" },
    { nombre: "Java / Spring Boot", nivel: "Intermedio", anios: "2 años" },
    { nombre: "Accesibilidad WCAG", nivel: "Avanzado", anios: "4 años" },
    { nombre: "Figma", nivel: "Intermedio", anios: "3 años" },
    { nombre: "Testing (Jest / Playwright)", nivel: "Avanzado", anios: "4 años" },
    { nombre: "PostgreSQL", nivel: "Básico", anios: "1 año" },
    { nombre: "Docker", nivel: "Intermedio", anios: "2 años" },
    { nombre: "Kubernetes", nivel: "Básico", anios: "1 año" }
  ];

  MOCK.marianaHistorial = [
    { proyecto: "Portal Andes", cliente: "BanCredit", rol: "Frontend Sr.", periodo: "mar 2026 — actual", estado: "En curso" },
    { proyecto: "App Móvil Aurora", cliente: "Aurora Telecom", rol: "Frontend", periodo: "jun 2026 — actual", estado: "En riesgo" },
    { proyecto: "Motor Cobranzas", cliente: "Seguros Vital", rol: "Frontend", periodo: "sep 2025 — feb 2026", estado: "Cerrado" },
    { proyecto: "Migración Cloud Titán", cliente: "Titán Logística", rol: "UI Support", periodo: "ene 2025 — ago 2025", estado: "Cerrado" },
    { proyecto: "Intranet NexaCorp v1", cliente: "Interno", rol: "Frontend", periodo: "abr 2024 — dic 2024", estado: "Cerrado" }
  ];

  /* ---------------------------------------------------------
   * Notificaciones por rol
   * --------------------------------------------------------- */
  MOCK.notifications = {
    colaborador: [
      { id: "n1", grupo: "hoy", tipo: "alert", icon: "icon-alert-triangle", titulo: "Alerta de sobrecarga", desc: "Tomás Herrera quedaría al 120% si se confirma la asignación a Núcleo Retail.", hora: "09:41", accion: "Revisar asignación", leida: false, categoria: "alertas" },
      { id: "n2", grupo: "hoy", tipo: "ai", icon: "icon-sparkles", titulo: "Recomendación de IA lista", desc: "5 candidatos sugeridos para la vacante Platform Engineer de Núcleo Retail.", hora: "09:14", accion: "Ver resultados", leida: false, categoria: "asignaciones" },
      { id: "n3", grupo: "hoy", tipo: "success", icon: "icon-plus-circle", titulo: "Nueva asignación", desc: "Javier Molina te asignó a Núcleo Retail con 20% de dedicación desde el 15 de septiembre.", hora: "08:52", accion: "Aceptar", leida: false, categoria: "asignaciones" },
      { id: "n4", grupo: "hoy", tipo: "info", icon: "icon-mail", titulo: "Respuesta en foro", desc: "Diego Salazar respondió en “Manejo de sesiones con Spring Security”.", hora: "08:20", accion: "Abrir hilo", leida: false, categoria: "foros" },
      { id: "n5", grupo: "ayer", tipo: "ai", icon: "icon-sparkles", titulo: "Resumen IA disponible", desc: "El hilo “Migración a Java 21” tiene un resumen generado con 23 respuestas.", hora: "18:40", accion: "Leer resumen", leida: true, categoria: "foros" },
      { id: "n6", grupo: "ayer", tipo: "alert", icon: "icon-alert-triangle", titulo: "Proyecto en riesgo", desc: "App Móvil Aurora quedó 18% bajo el avance planificado del sprint 7.", hora: "17:05", accion: "Ver proyecto", leida: true, categoria: "alertas" },
      { id: "n7", grupo: "ayer", tipo: "success", icon: "icon-check-circle", titulo: "Habilidad validada", desc: "Tu nivel Avanzado en Testing (Jest / Playwright) fue validado por Andrés Peña.", hora: "11:12", accion: null, leida: true, categoria: "asignaciones" },
      { id: "n8", grupo: "semana", tipo: "mail", icon: "icon-mail", titulo: "Recordatorio por correo", desc: "Se envió a tu correo el resumen semanal de carga y asignaciones.", hora: "14 ago", accion: null, leida: true, categoria: "alertas" },
      { id: "n9", grupo: "semana", tipo: "success", icon: "icon-plus-circle", titulo: "Nuevo miembro en tu equipo", desc: "Sofía Cárdenas se unió a App Móvil Aurora como UX Designer.", hora: "12 ago", accion: null, leida: true, categoria: "asignaciones" }
    ],
    pm: [
      { id: "n1", grupo: "hoy", tipo: "alert", icon: "icon-alert-triangle", titulo: "Asignación bloqueada", desc: "Tomás Herrera al 120% en Aurora + Motor Cobranzas. Requiere excepción de Paula Vega.", hora: "09:41", accion: "Solicitar excepción", leida: false, categoria: "alertas" },
      { id: "n2", grupo: "hoy", tipo: "ai", icon: "icon-sparkles", titulo: "Matching completado", desc: "5 candidatos para la vacante Platform Engineer de Núcleo Retail. Top match: Lucía Fernández (94%).", hora: "09:14", accion: "Ver resultados", leida: false, categoria: "solicitudes" },
      { id: "n3", grupo: "hoy", tipo: "alert", icon: "icon-trending-down", titulo: "Proyecto bajo plan", desc: "App Móvil Aurora cerró el sprint 7 con 9 de 14 historias.", hora: "08:05", accion: "Ver proyecto", leida: false, categoria: "alertas" },
      { id: "n4", grupo: "ayer", tipo: "success", icon: "icon-check-circle", titulo: "Asignación aceptada", desc: "Mariana Ruiz aceptó su asignación al 20% en Núcleo Retail.", hora: "16:22", accion: null, leida: true, categoria: "solicitudes" },
      { id: "n5", grupo: "ayer", tipo: "mail", icon: "icon-mail", titulo: "Recordatorio por correo", desc: "Demo con BanCredit el 25 de agosto a las 10:00.", hora: "09:00", accion: null, leida: true, categoria: "alertas" }
    ],
    rm: [
      { id: "n1", grupo: "hoy", tipo: "alert", icon: "icon-alert-triangle", titulo: "2 colaboradores sobre-asignados", desc: "Tomás Herrera 120% y Lucía Fernández 110% en las semanas 34–36.", hora: "09:41", accion: "Rebalancear", leida: false, categoria: "alertas" },
      { id: "n2", grupo: "hoy", tipo: "ai", icon: "icon-zap", titulo: "Solicitud de excepción", desc: "Javier Molina solicita aprobar a Tomás Herrera al 120% hasta el 5 de septiembre.", hora: "09:38", accion: "Revisar solicitud", leida: false, categoria: "solicitudes" },
      { id: "n3", grupo: "hoy", tipo: "info", icon: "icon-trending-down", titulo: "Capacidad ociosa detectada", desc: "Ricardo Bastos lleva 4 semanas al 40%. 312 h libres en el trimestre.", hora: "07:58", accion: "Ver disponibilidad", leida: false, categoria: "alertas" },
      { id: "n4", grupo: "ayer", tipo: "success", icon: "icon-check-circle", titulo: "Asignación validada", desc: "Camila Ortega al 40% en Núcleo Retail: dentro del límite de carga.", hora: "15:10", accion: null, leida: true, categoria: "solicitudes" },
      { id: "n5", grupo: "ayer", tipo: "mail", icon: "icon-mail", titulo: "Reporte semanal enviado", desc: "Ocupación promedio del equipo: 77% (meta 85%).", hora: "08:00", accion: null, leida: true, categoria: "alertas" }
    ],
    administrador: [
      { id: "n1", grupo: "hoy", tipo: "alert", icon: "icon-shield", titulo: "Cambio de rol registrado", desc: "Ricardo Bastos pasó a estado suspendido. Acción registrada en auditoría.", hora: "09:41", accion: "Ver en auditoría", leida: false, categoria: "alertas" },
      { id: "n2", grupo: "hoy", tipo: "alert", icon: "icon-user-x", titulo: "3 usuarios sin rol asignado", desc: "Ingresaron esta semana y no pueden acceder a ningún módulo.", hora: "08:56", accion: "Asignar roles", leida: false, categoria: "solicitudes" },
      { id: "n3", grupo: "hoy", tipo: "ai", icon: "icon-sparkles", titulo: "Pico de uso del asistente IA", desc: "1.482 consultas esta semana (+23%). Cuota mensual al 68%.", hora: "08:12", accion: "Ver consumo", leida: false, categoria: "alertas" },
      { id: "n4", grupo: "ayer", tipo: "success", icon: "icon-database", titulo: "Respaldo completado", desc: "Copia de seguridad diaria de la base de datos finalizada sin errores.", hora: "02:00", accion: null, leida: true, categoria: "alertas" },
      { id: "n5", grupo: "ayer", tipo: "mail", icon: "icon-mail", titulo: "12 correos enviados", desc: "Notificación masiva de alerta de sobrecarga a PMs y Resource Managers.", hora: "08:31", accion: null, leida: true, categoria: "solicitudes" }
    ]
  };

  /* ---------------------------------------------------------
   * Foros
   * --------------------------------------------------------- */
  MOCK.forumThreads = [
    { id: "t-java21", titulo: "Migración a Java 21: qué se rompió y cómo lo resolvimos", categoria: "Arquitectura", autor: "Diego Salazar", iniciales: "DS", tiempo: "ayer, 16:20", respuestas: 23, vistas: 412, fijado: true, resumenIA: "El hilo concluye que los fallos vinieron de tres frentes: reflexión en librerías de mapeo, cambios en el sealed-class checking y el retiro del Security Manager. La solución adoptada fue actualizar MapStruct a 1.6, migrar los tests a JUnit 5.11 y aislar el módulo legado tras un adaptador.", proyecto: "Motor Cobranzas v2" },
    { id: "t-security", titulo: "Manejo de sesiones con Spring Security en el portal", categoria: "Backend", autor: "Mariana Ruiz", iniciales: "MR", tiempo: "hace 1 h", respuestas: 14, vistas: 268, fijado: false, proyecto: "Portal Andes" },
    { id: "t-thymeleaf", titulo: "Convenciones de fragmentos Thymeleaf: ¿por vista o por componente?", categoria: "Frontend", autor: "Sofía Cárdenas", iniciales: "SC", tiempo: "hace 5 h", respuestas: 8, vistas: 131, fijado: false, proyecto: "App Móvil Aurora" },
    { id: "t-indices", titulo: "Estrategia de índices para el catálogo de habilidades", categoria: "Datos e IA", autor: "Camila Ortega", iniciales: "CO", tiempo: "hace 8 h", respuestas: 19, vistas: 297, fijado: false, proyecto: "Núcleo Retail" },
    { id: "t-helm", titulo: "Helm charts compartidos entre proyectos: propuesta", categoria: "DevOps & Cloud", autor: "Lucía Fernández", iniciales: "LF", tiempo: "hace 1 día", respuestas: 31, vistas: 503, fijado: false, proyecto: "Núcleo Retail" },
    { id: "t-a11y", titulo: "Checklist de accesibilidad AA antes de cada release", categoria: "Procesos y QA", autor: "Andrés Peña", iniciales: "AP", tiempo: "hace 2 días", respuestas: 11, vistas: 184, fijado: false, proyecto: "Portal Andes" },
    { id: "t-matching", titulo: "¿Cómo documentamos los criterios del modelo de matching?", categoria: "Datos e IA", autor: "Paula Vega", iniciales: "PV", tiempo: "hace 3 días", respuestas: 6, vistas: 97, fijado: false, proyecto: null }
  ];

  MOCK.threadActivityByProject = [
    { proyecto: "Portal Andes", hilos: 28 }, { proyecto: "Motor Cobranzas v2", hilos: 17 },
    { proyecto: "App Móvil Aurora", hilos: 12 }, { proyecto: "Núcleo Retail", hilos: 5 }
  ];
  MOCK.threadsNeedingReply = [
    { titulo: "¿Movemos el hito de QA de Aurora al 4 sep?", autor: "Sofía Cárdenas", tiempo: "hace 3 h" },
    { titulo: "Presupuesto de infraestructura para Núcleo Retail", autor: "Lucía Fernández", tiempo: "ayer" },
    { titulo: "Criterios de aceptación del módulo de cobros", autor: "Andrés Peña", tiempo: "hace 2 días" }
  ];

  MOCK.threadDetail = {
    id: "t-java21",
    titulo: "Migración a Java 21: qué se rompió y cómo lo resolvimos",
    categoria: "Arquitectura",
    fijado: true,
    respuestas: 23,
    vistas: 412,
    autor: "Diego Salazar",
    autorCargo: "Tech Lead Backend · Motor Cobranzas v2",
    abierto: "17 ago 2026",
    ultimaActividad: "hace 1 h",
    posts: [
      { autor: "Diego Salazar", iniciales: "DS", cargo: "Tech Lead Backend · Motor Cobranzas v2", tiempo: "17 ago 2026, 16:20", util: 18, aceptada: false,
        texto: "Cerramos la migración del módulo de cobranzas a Java 21 y quiero dejar registrado qué nos rompió, porque Portal Andes y Núcleo Retail van a pasar por lo mismo. Tres frentes: MapStruct 1.5 falla al generar mappers con records, los tests con Mockito inline dejaron de resolver clases selladas, y el retiro del Security Manager tumbó nuestro sandbox de plugins." },
      { autor: "Lucía Fernández", iniciales: "LF", cargo: "DevOps · Núcleo Retail", tiempo: "17 ago 2026, 17:05", util: 12, aceptada: true,
        texto: "Confirmo lo de MapStruct: subir a 1.6.0 lo resuelve sin tocar código. Para el sandbox nosotros lo reemplazamos por contenedores efímeros con límites de recursos a nivel de Kubernetes; queda más limpio que el Security Manager y no depende de la JVM." },
      { autor: "Mariana Ruiz", iniciales: "MR", cargo: "Frontend Sr. · Portal Andes", tiempo: "18 ago 2026, 09:12", util: 7, aceptada: false,
        texto: "¿El cambio de Mockito los obligó a reescribir muchos tests? En Portal Andes tenemos 1.200 pruebas y necesito estimar el esfuerzo antes de comprometer el sprint 14." },
      { autor: "Diego Salazar", iniciales: "DS", cargo: "Tech Lead Backend", tiempo: "18 ago 2026, 09:40", util: 9, aceptada: false,
        texto: "Menos de lo que temes: de 860 tests tocamos 41, casi todos por mocks de clases finales. Subiendo a Mockito 5.12 y JUnit 5.11 el resto pasó sin cambios. Nos tomó día y medio entre dos personas." },
      { autor: "Camila Ortega", iniciales: "CO", cargo: "Data Engineer", tiempo: "18 ago 2026, 11:30", util: 5, aceptada: false,
        texto: "Agrego un dato para quien migre con Kafka: los serializadores propios que usaban reflexión sobre campos privados necesitan --add-opens. Lo documenté en la guía de la célula de datos." }
    ],
    resumenIA: "Tres causas de ruptura al migrar a Java 21: generación de mappers con records (MapStruct 1.5), mocks de clases selladas (Mockito) y el retiro del Security Manager.",
    accionesAcordadas: ["Subir MapStruct a 1.6.0", "Mockito 5.12 + JUnit 5.11", "Sandbox con contenedores efímeros en K8s", "Documentar --add-opens para Kafka"],
    resumenGeneradoInfo: "GENERADO POR IA · 18 AGO 09:05 · 23 MENSAJES",
    participantes: ["DS", "LF", "MR", "CO"],
    participantesExtra: 7,
    hilosRelacionados: [
      { titulo: "Estrategia de índices para el catálogo de habilidades", categoria: "Datos e IA", respuestas: 19 },
      { titulo: "Helm charts compartidos entre proyectos", categoria: "DevOps & Cloud", respuestas: 31 },
      { titulo: "Manejo de sesiones con Spring Security", categoria: "Backend", respuestas: 14 }
    ]
  };

  /* ---------------------------------------------------------
   * Auditoría
   * --------------------------------------------------------- */
  MOCK.auditLog = [
    { fecha: "18 ago 09:41", usuario: "Ana Villalba", accion: "Rol modificado", detalle: "Ricardo Bastos: Colaborador → suspendido", origen: "10.4.22.18", severidad: "Alta", tipo: "roles" },
    { fecha: "18 ago 09:22", usuario: "Paula Vega", accion: "Excepción de carga", detalle: "Tomás Herrera aprobado al 120% hasta el 5 sep", origen: "10.4.19.7", severidad: "Alta", tipo: "asignaciones" },
    { fecha: "18 ago 09:14", usuario: "Javier Molina", accion: "Matching ejecutado", detalle: "Núcleo Retail · vacante Platform Engineer", origen: "10.4.31.2", severidad: "Info", tipo: "ia" },
    { fecha: "18 ago 08:56", usuario: "Ana Villalba", accion: "Habilidad creada", detalle: "“OpenTelemetry” añadida a DevOps & Cloud", origen: "10.4.22.18", severidad: "Media", tipo: "roles" },
    { fecha: "18 ago 08:31", usuario: "Sistema", accion: "Notificación masiva", detalle: "12 correos enviados: alerta de sobrecarga", origen: "—", severidad: "Info", tipo: "ia" },
    { fecha: "17 ago 18:40", usuario: "Diego Salazar", accion: "Resumen IA generado", detalle: "Hilo “Migración a Java 21”", origen: "10.4.27.44", severidad: "Info", tipo: "ia" }
  ];
  MOCK.auditSeverityBreakdown = { alta: 12, media: 88, info: 1104 };
  MOCK.auditTopUsers = [
    { nombre: "Ana Villalba", iniciales: "AV", eventos: 214 },
    { nombre: "Paula Vega", iniciales: "PV", eventos: 186 },
    { nombre: "Javier Molina", iniciales: "JM", eventos: 159 }
  ];

  /* ---------------------------------------------------------
   * Ocupación semanal (mapa de calor)
   * --------------------------------------------------------- */
  MOCK.occupancyWeeks = ["S34", "S35", "S36", "S37", "S38", "S39"];
  MOCK.occupancyHeatmap = [
    { nombre: "Mariana Ruiz", iniciales: "MR", cargo: "Frontend Sr.", valores: [80, 80, 95, 95, 75, 75], promedio: 83 },
    { nombre: "Diego Salazar", iniciales: "DS", cargo: "Tech Lead", valores: [95, 95, 90, 80, 80, 60], promedio: 83 },
    { nombre: "Tomás Herrera", iniciales: "TH", cargo: "Backend Sr.", valores: [120, 120, 110, 100, 90, 90], promedio: 105 },
    { nombre: "Camila Ortega", iniciales: "CO", cargo: "Data Engineer", valores: [85, 85, 85, 85, 70, 70], promedio: 80 },
    { nombre: "Lucía Fernández", iniciales: "LF", cargo: "DevOps", valores: [110, 110, 70, 70, 70, 70], promedio: 83 },
    { nombre: "Andrés Peña", iniciales: "AP", cargo: "QA", valores: [60, 60, 60, 90, 90, 90], promedio: 75 },
    { nombre: "Sofía Cárdenas", iniciales: "SC", cargo: "UX Designer", valores: [70, 70, 45, 45, 45, 30], promedio: 51 },
    { nombre: "Ricardo Bastos", iniciales: "RB", cargo: "Platform Eng.", valores: [40, 40, 40, 40, 40, 40], promedio: 40 }
  ];
  MOCK.overallocationCases = [
    { nombre: "Tomás Herrera", iniciales: "TH", cargo: "Backend Sr.", pct: 120, detalle: "Aurora 70% + Motor Cobranzas 50%. Excede el límite en 20 pts durante 3 semanas." },
    { nombre: "Lucía Fernández", iniciales: "LF", cargo: "DevOps", pct: 110, detalle: "Se normaliza a 70% en la semana 36 al cerrar Migración Cloud Titán." }
  ];
  MOCK.occupancyBuckets = { subutilizados: 2, optimo: 4, alLimite: 1, sobreAsignados: 2, horasLibres: 312 };
  MOCK.occupancyMonthly = [
    { mes: "MAR", pct: 68 }, { mes: "ABR", pct: 72 }, { mes: "MAY", pct: 75 },
    { mes: "JUN", pct: 81 }, { mes: "JUL", pct: 79 }, { mes: "AGO", pct: 77 }
  ];

  /* ---------------------------------------------------------
   * Reportes
   * --------------------------------------------------------- */
  MOCK.pmReport = {
    avancePonderado: 57, avanceDelta: -4,
    historiasCerradas: 312, historiasDelta: 38,
    desviacionPlazo: "6 días", desviacionNota: "Aurora concentra 5",
    horasAsignadas: 3480, horasDisponibles: 4120,
    sprints: [
      { s: "S9", pct: 62 }, { s: "S10", pct: 71 }, { s: "S11", pct: 80 },
      { s: "S12", pct: 88 }, { s: "S13", pct: 74 }, { s: "S14", pct: 66 }
    ],
    ocupacionEquipo: [
      { nombre: "Tomás Herrera", pct: 120 }, { nombre: "Diego Salazar", pct: 95 },
      { nombre: "Camila Ortega", pct: 85 }, { nombre: "Mariana Ruiz", pct: 80 },
      { nombre: "Sofía Cárdenas", pct: 70 }, { nombre: "Andrés Peña", pct: 60 }
    ],
    detalleProyectos: [
      { proyecto: "Portal Andes", historias: "128 / 186", horas: "1.240 h", desviacion: "+2 d", riesgo: "Bajo" },
      { proyecto: "App Móvil Aurora", historias: "64 / 190", horas: "980 h", desviacion: "-5 d", riesgo: "Alto" },
      { proyecto: "Núcleo Retail", historias: "18 / 150", horas: "310 h", desviacion: "0 d", riesgo: "Medio" },
      { proyecto: "Motor Cobranzas v2", historias: "102 / 112", horas: "950 h", desviacion: "+3 d", riesgo: "Bajo" }
    ]
  };

  MOCK.rmReport = {
    ocupacionPromedio: 77, meta: 85, sobreAsignados: 2, capacidadLibre: 312, solicitudesPendientes: 4, vencenHoy: 2,
    evolucion: MOCK.occupancyMonthly,
    ocupacionPorColaborador: [
      { nombre: "Mariana Ruiz", iniciales: "MR", cargo: "Frontend Sr.", promedio: 83 },
      { nombre: "Diego Salazar", iniciales: "DS", cargo: "Tech Lead", promedio: 83 },
      { nombre: "Tomás Herrera", iniciales: "TH", cargo: "Backend Sr.", promedio: 105 },
      { nombre: "Camila Ortega", iniciales: "CO", cargo: "Data Engineer", promedio: 80 },
      { nombre: "Lucía Fernández", iniciales: "LF", cargo: "DevOps", promedio: 83 },
      { nombre: "Andrés Peña", iniciales: "AP", cargo: "QA", promedio: 75 },
      { nombre: "Sofía Cárdenas", iniciales: "SC", cargo: "UX Designer", promedio: 51 },
      { nombre: "Ricardo Bastos", iniciales: "RB", cargo: "Platform Eng.", promedio: 40 }
    ]
  };

  MOCK.adminReport = {
    colaboradoresActivos: 184, colaboradoresDelta: 6,
    proyectosEnCurso: 27, proyectosCierranSep: 4,
    ocupacionPromedio: 77, ocupacionMeta: 85,
    habilidadesCatalogo: 312, habilidadesSinAsignar: 18,
    consultasIA: 1482, consultasDelta: 23,
    vacantesPorHabilidad: MOCK.skillsDemand
  };

  /* ---------------------------------------------------------
   * AI Talent Matching
   * --------------------------------------------------------- */
  MOCK.matchingVacancy = {
    proyecto: "Núcleo Retail", puesto: "Platform Engineer", dedicacion: 60,
    abiertaDesde: "01 sep 2026", diasAbierta: 11,
    otrasVacantes: [
      { nombre: "Aurora · QA Automation", dedicacion: 40 },
      { nombre: "Data Lake · Data Engineer", dedicacion: 50 }
    ]
  };

  MOCK.matchingCandidates = [
    { nombre: "Lucía Fernández", iniciales: "LF", cargo: "DevOps Engineer Sr.", score: 94, disponible: 30, top: true,
      trasAsignar: 130,
      criterios: [
        { nombre: "Habilidades coincidentes", detalle: "6 de 6", pct: 100, extra: "Kubernetes Avanzado, Terraform, Kafka, Java/Spring, PostgreSQL, React (nivel básico exigido)." },
        { nombre: "Experiencia relevante", detalle: "5 proyectos", pct: 92, extra: "Migración Cloud Titán (lead), Motor Cobranzas v2, Intranet NexaCorp v2 y 2 proyectos previos con la misma arquitectura." },
        { nombre: "Disponibilidad", detalle: "30% libre desde 1 sep", pct: 80, extra: "Cierra Cloud Titán en la semana 36; su carga cae de 110% a 70% justo al inicio de la fase de plataforma." }
      ],
      habilidadesCoincidentes: [
        { nombre: "Kubernetes", requerido: "Avanzado", tiene: "EXPERTO" },
        { nombre: "Terraform", requerido: "Básico", tiene: "AVANZADO" },
        { nombre: "Apache Kafka", requerido: "Intermedio", tiene: "AVANZADO" },
        { nombre: "Java / Spring Boot", requerido: "Avanzado", tiene: "AVANZADO" },
        { nombre: "PostgreSQL", requerido: "Intermedio", tiene: "INTERMEDIO" },
        { nombre: "React", requerido: "Básico", tiene: "BÁSICO" }
      ],
      explicacion: "Cubre las 6 habilidades requeridas, 3 de ellas por encima del nivel mínimo. Lideró la migración de Cloud Titán, el proyecto con arquitectura más parecida. Su carga baja de 110% a 70% el 1 de septiembre, justo cuando arranca la fase de plataforma." },
    { nombre: "Tomás Herrera", iniciales: "TH", cargo: "Backend Engineer Sr.", score: 87, disponible: 0, top: false, trasAsignar: 130,
      criterios: [
        { nombre: "Habilidades coincidentes", detalle: "5 de 6", pct: 83, extra: "Cubre Java/Spring, PostgreSQL, React y Kafka en nivel avanzado; sin experiencia formal en Terraform." },
        { nombre: "Experiencia relevante", detalle: "3 proyectos", pct: 74, extra: "Motor Cobranzas v2 y App Móvil Aurora, ambos con componentes de infraestructura compartida." },
        { nombre: "Disponibilidad", detalle: "0% libre", pct: 20, extra: "Ya está al 120% entre Aurora y Motor Cobranzas v2. Asignarlo requiere aprobación de excepción." }
      ],
      explicacion: "Buen match técnico pero sin disponibilidad: ya está al 120% de carga entre dos proyectos activos. Asignarlo implicaría una excepción de carga aprobada por Resource Manager." },
    { nombre: "Diego Salazar", iniciales: "DS", cargo: "Tech Lead Backend", score: 84, disponible: 5, top: false, trasAsignar: 130,
      criterios: [
        { nombre: "Habilidades coincidentes", detalle: "5 de 6", pct: 83, extra: "Fuerte en Java/Spring y Kafka; nivel básico en Terraform." },
        { nombre: "Experiencia relevante", detalle: "4 proyectos", pct: 80, extra: "Tech Lead en Portal Andes y Motor Cobranzas v2." },
        { nombre: "Disponibilidad", detalle: "5% libre", pct: 24, extra: "Carga actual 95% entre Portal Andes y Motor Cobranzas v2." }
      ],
      explicacion: "Perfil técnico sólido como tech lead, pero con margen de disponibilidad muy ajustado (5% libre)." },
    { nombre: "Camila Ortega", iniciales: "CO", cargo: "Data Engineer", score: 71, disponible: 15, top: false, trasAsignar: 130,
      criterios: [
        { nombre: "Habilidades coincidentes", detalle: "4 de 6", pct: 67, extra: "Fuerte en PostgreSQL y Kafka; sin experiencia en Kubernetes ni Terraform." },
        { nombre: "Experiencia relevante", detalle: "3 proyectos", pct: 65, extra: "Data Engineer en Núcleo Retail y Motor Cobranzas v2." },
        { nombre: "Disponibilidad", detalle: "15% libre", pct: 40, extra: "Carga actual 85% entre Núcleo Retail y Motor Cobranzas v2." }
      ],
      explicacion: "Cubre el componente de datos pero le faltan las habilidades de plataforma (Kubernetes, Terraform) que exige la vacante." },
    { nombre: "Ricardo Bastos", iniciales: "RB", cargo: "Platform Engineer", score: 63, disponible: 60, top: false, trasAsignar: 130,
      criterios: [
        { nombre: "Habilidades coincidentes", detalle: "3 de 6", pct: 50, extra: "Kubernetes y Terraform en nivel intermedio; sin experiencia reciente en Kafka ni React." },
        { nombre: "Experiencia relevante", detalle: "1 proyecto", pct: 35, extra: "Soporte de plataforma interno, sin proyectos de cliente en los últimos 12 meses." },
        { nombre: "Disponibilidad", detalle: "60% libre", pct: 95, extra: "Es quien tiene más holgura hoy: 40% de carga desde hace 4 semanas." }
      ],
      explicacion: "El match más bajo del grupo, pero es el único con disponibilidad inmediata amplia — buen candidato de apoyo o mentoría cruzada." }
  ];

  MOCK.matchingProposal = {
    texto: "Asignar a Lucía al 40% desde el 1 de septiembre y completar el 20% restante con Ricardo Bastos como apoyo. Ningún colaborador supera el 100%.",
    impactoCapacidad: "-96 h / mes"
  };

  /* ---------------------------------------------------------
   * Asistente IA — conversaciones canónicas por rol
   * --------------------------------------------------------- */
  MOCK.aiHistory = {
    colaborador: [
      { titulo: "Dudas sobre Spring Security", tiempo: "HOY 09:02" },
      { titulo: "Plan para subir de nivel", tiempo: "15 AGO" },
      { titulo: "Resumen del sprint 12", tiempo: "08 AGO" }
    ],
    pm: [
      { titulo: "Cobertura de vacante Núcleo Retail", tiempo: "HOY 09:14" },
      { titulo: "Riesgos del sprint 7 de Aurora", tiempo: "AYER 17:22" },
      { titulo: "Comparativa de carga por célula", tiempo: "14 AGO" },
      { titulo: "Resumen del foro de arquitectura", tiempo: "11 AGO" }
    ],
    rm: [
      { titulo: "Sobre-asignados esta semana", tiempo: "HOY 08:10" },
      { titulo: "Capacidad libre del trimestre", tiempo: "AYER 11:40" },
      { titulo: "Brechas de habilidades Q3", tiempo: "13 AGO" }
    ],
    administrador: [
      { titulo: "Usuarios sin rol asignado", tiempo: "HOY 08:56" },
      { titulo: "Estado del servicio de matching", tiempo: "AYER 09:00" },
      { titulo: "Resumen de auditoría semanal", tiempo: "12 AGO" }
    ]
  };

  MOCK.aiContextLabel = {
    colaborador: "27 proyectos · tu perfil · foros suscritos",
    pm: "27 proyectos · 184 perfiles · 312 habilidades · 248 hilos de foro",
    rm: "184 perfiles · ocupación de 8 semanas · 312 habilidades",
    administrador: "184 usuarios · 27 proyectos · 312 habilidades · auditoría completa"
  };

  MOCK.aiConversations = {
    colaborador: [
      { role: "user", texto: "¿Qué tengo pendiente esta semana en Portal Andes?", meta: "09:02 · MARIANA RUIZ" },
      { role: "assistant", texto: "Tienes 3 pendientes: revisar el PR #482 del módulo de autenticación (vence hoy), el refactor del design system (19 ago) y validar accesibilidad AA del checkout en Aurora (22 ago). El sprint 12 cerró con 4 tickets abiertos y Diego Salazar te pidió revisión.", meta: "09:02 · FUENTES: ASIGNACIONES, PORTAL ANDES" },
      { role: "user", texto: "¿Qué me falta para subir mi nivel de Kubernetes a Intermedio?", meta: "09:05 · MARIANA RUIZ" },
      { role: "assistant", texto: "Hoy tienes Kubernetes en nivel Básico (1 año). Para pasar a Intermedio el catálogo exige experiencia práctica en 2 proyectos con despliegue propio. Núcleo Retail arranca su fase de plataforma en septiembre y tiene 60% de capacidad sin cubrir: es la vía más rápida. Lucía Fernández (nivel Experto) figura como mentora disponible.", meta: "09:05 · FUENTES: PERFIL, CATÁLOGO DE HABILIDADES, PROYECTOS",
        card: { titulo: "Núcleo Retail · Consultora UI (20%)", sub: "Ya estás propuesta desde el 15 de septiembre", accion: "Ver proyecto" } }
    ],
    pm: [
      { role: "user", texto: "¿Quién del equipo puede cubrir la vacante de Kubernetes en Núcleo Retail sin quedar sobre-asignado?", meta: "09:14 · JAVIER MOLINA" },
      { role: "assistant", texto: "Lucía Fernández es la mejor opción: match del 94%, cubre las 6 habilidades requeridas y su carga baja de 110% a 70% el 1 de septiembre, cuando arranca la fase de plataforma. Ricardo Bastos tiene 60% libre desde ya, pero su match es 63% (solo 3 de 6 habilidades).", meta: "09:14 · ASISTENTE SKILLBRIDGE · FUENTES: PERFILES, ASIGNACIONES, FOROS" },
      { role: "user", texto: "¿Y si necesito a alguien disponible esta misma semana?", meta: "09:16 · JAVIER MOLINA" },
      { role: "assistant", texto: "Esta semana solo Ricardo Bastos (40% de carga) y Sofía Cárdenas (70%) tienen holgura. Ninguno cumple Kubernetes Avanzado. Una alternativa es asignar a Ricardo al 60% ahora y sumar a Lucía como mentora al 10% desde el 1 de septiembre — cubre el arranque sin exceder ningún límite de carga.", meta: "09:16 · ASISTENTE SKILLBRIDGE · FUENTES: PERFILES, ASIGNACIONES, FOROS" },
      { role: "user", texto: "Muéstrame qué se discutió sobre Java 21 en los foros", meta: "09:19 · JAVIER MOLINA" },
      { role: "assistant", texto: "El hilo “Migración a Java 21: qué se rompió” (Arquitectura, 23 respuestas) concluye tres puntos: actualizar MapStruct a 1.6, subir Mockito a 5.12 / JUnit 5.11, y reemplazar el Security Manager por contenedores efímeros. Diego Salazar reporta día y medio de esfuerzo para 860 tests.", meta: "09:19 · ASISTENTE SKILLBRIDGE · FUENTES: PERFILES, ASIGNACIONES, FOROS" }
    ],
    rm: [
      { role: "user", texto: "¿Quién está sobre-asignado esta semana?", meta: "08:10 · PAULA VEGA" },
      { role: "assistant", texto: "Dos casos: Tomás Herrera al 120% (Aurora 70% + Motor Cobranzas v2 50%) y Lucía Fernández al 110% (se normaliza a 70% en la semana 36 al cerrar Cloud Titán). Tomás requiere excepción o rebalanceo; Lucía se resuelve sola en 2 semanas.", meta: "08:10 · ASISTENTE SKILLBRIDGE · FUENTES: OCUPACIÓN, ASIGNACIONES" },
      { role: "user", texto: "¿Cuánta capacidad libre tenemos para el próximo trimestre?", meta: "08:12 · PAULA VEGA" },
      { role: "assistant", texto: "312 horas libres en las próximas 6 semanas, concentradas en Ricardo Bastos (60% libre) y Sofía Cárdenas (30% libre promedio). La ocupación promedio del equipo es 77%, 8 puntos bajo la meta de 85%.", meta: "08:12 · ASISTENTE SKILLBRIDGE · FUENTES: OCUPACIÓN, REPORTES" }
    ],
    administrador: [
      { role: "user", texto: "¿Cuántos usuarios están sin rol asignado?", meta: "08:56 · ANA VILLALBA" },
      { role: "assistant", texto: "3 usuarios ingresaron esta semana sin rol asignado y no pueden acceder a ningún módulo. Puedes asignarles rol desde Usuarios y roles → filtro “sin rol”.", meta: "08:56 · ASISTENTE SKILLBRIDGE · FUENTES: USUARIOS, AUDITORÍA" },
      { role: "user", texto: "¿Qué tan degradado está el servicio de matching?", meta: "08:58 · ANA VILLALBA" },
      { role: "assistant", texto: "El servicio de matching está al 97,1% de disponibilidad en las últimas 24 horas, por debajo del resto de la plataforma (99,8–100%). No hay incidentes críticos abiertos, pero es el componente a vigilar hoy.", meta: "08:58 · ASISTENTE SKILLBRIDGE · FUENTES: SALUD DE LA PLATAFORMA" }
    ]
  };

  MOCK.aiSuggestions = {
    colaborador: ["Resume el hilo de Java 21", "¿Quién sabe Thymeleaf avanzado?", "¿Cuánta capacidad libre tengo?"],
    pm: ["¿Qué proyectos están en riesgo esta semana?", "Resume el sprint 12 de Portal Andes", "¿Quién sabe Terraform nivel Avanzado?"],
    rm: ["¿Qué colaborador tiene más capacidad libre?", "Resume la ocupación de la célula Andes", "¿Qué habilidades tienen más brecha?"],
    administrador: ["Resume la auditoría de las últimas 24 horas", "¿Qué módulos usan más el asistente IA?", "¿Cuántas habilidades no están vinculadas a proyectos?"]
  };

  MOCK.aiFallbackReply = "Todavía no tengo un dato preparado para esa consulta específica en esta demo, pero en producción el asistente respondería usando datos de perfiles, asignaciones, proyectos y foros a los que tu rol tiene acceso.";

  /* ---------------------------------------------------------
   * Salud de la plataforma (Admin)
   * --------------------------------------------------------- */
  MOCK.platformHealth = [
    { nombre: "Aplicación Spring Boot", estado: "ok", label: "Operativa", pct: "99,8%" },
    { nombre: "Base de datos PostgreSQL", estado: "ok", label: "Operativa", pct: "99,9%" },
    { nombre: "Servicio de matching", estado: "degraded", label: "Degradado", pct: "97,1%" },
    { nombre: "Envío de correos", estado: "ok", label: "Operativo", pct: "100%" }
  ];
  MOCK.attentionItems = [
    "3 usuarios nuevos sin rol asignado desde el 14 de agosto.",
    "El servicio de matching está degradado (97,1% de disponibilidad).",
    "18 habilidades del catálogo no están vinculadas a ningún proyecto."
  ];

  /* ---------------------------------------------------------
   * Usuarios y roles (Admin)
   * --------------------------------------------------------- */
  MOCK.usersTable = [
    { nombre: "Mariana Ruiz", iniciales: "MR", correo: "mariana.ruiz@nexacorp.com", rol: "Colaborador", area: "Ingeniería", ultimoAcceso: "hoy, 09:02", estado: "Activo" },
    { nombre: "Javier Molina", iniciales: "JM", correo: "javier.molina@nexacorp.com", rol: "Project Manager", area: "Delivery", ultimoAcceso: "hoy, 08:47", estado: "Activo" },
    { nombre: "Paula Vega", iniciales: "PV", correo: "paula.vega@nexacorp.com", rol: "Resource Manager", area: "Delivery", ultimoAcceso: "hoy, 07:58", estado: "Activo" },
    { nombre: "Diego Salazar", iniciales: "DS", correo: "diego.salazar@nexacorp.com", rol: "Colaborador", area: "Ingeniería", ultimoAcceso: "hoy, 09:40", estado: "Activo" },
    { nombre: "Tomás Herrera", iniciales: "TH", correo: "tomas.herrera@nexacorp.com", rol: "Colaborador", area: "Ingeniería", ultimoAcceso: "ayer, 19:12", estado: "Activo" },
    { nombre: "Camila Ortega", iniciales: "CO", correo: "camila.ortega@nexacorp.com", rol: "Colaborador", area: "Datos", ultimoAcceso: "hoy, 08:10", estado: "Activo" },
    { nombre: "Lucía Fernández", iniciales: "LF", correo: "lucia.fernandez@nexacorp.com", rol: "Colaborador", area: "Plataforma", ultimoAcceso: "hoy, 07:44", estado: "Activo" },
    { nombre: "Andrés Peña", iniciales: "AP", correo: "andres.pena@nexacorp.com", rol: "Colaborador", area: "Calidad", ultimoAcceso: "hoy, 08:55", estado: "Activo" },
    { nombre: "Sofía Cárdenas", iniciales: "SC", correo: "sofia.cardenas@nexacorp.com", rol: "Colaborador", area: "Diseño", ultimoAcceso: "hoy, 08:20", estado: "Activo" },
    { nombre: "Ricardo Bastos", iniciales: "RB", correo: "ricardo.bastos@nexacorp.com", rol: "Colaborador", area: "Plataforma", ultimoAcceso: "12 ago 2026", estado: "Bloqueado" },
    { nombre: "Ana Villalba", iniciales: "AV", correo: "ana.villalba@nexacorp.com", rol: "Administrador", area: "TI Corporativa", ultimoAcceso: "hoy, 09:31", estado: "Activo" }
  ];
  MOCK.usersKpi = { total: 184, sinRol: 3, suspendidos: 1 };

  window.MOCK = MOCK;
})();