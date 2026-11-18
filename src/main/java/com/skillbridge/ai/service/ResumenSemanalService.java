package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EventoFila;
import com.skillbridge.ai.dto.PanelRm;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Entregable;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.CorreoAutorizadoRepository;
import com.skillbridge.ai.repository.EntregaRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.EmailService.Correo;
import com.skillbridge.ai.service.EmailService.Dato;
import com.skillbridge.ai.service.EmailService.Linea;
import com.skillbridge.ai.service.EmailService.Seccion;
import com.skillbridge.ai.util.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resumen semanal por correo (preferencia "Resumen semanal" de
 * Notificaciones, activada por defecto). Cada lunes a las 8:00 (hora de
 * Perú) le llega a cada cuenta activa que no lo apagó un correo con su
 * semana, según su rol:
 *
 *  - Colaborador: carga, proyectos, entregables que cierran en 7 días y
 *    eventos de sus proyectos. Si además lidera proyectos (PM): entregas por
 *    revisar y vacantes de sus proyectos.
 *  - Resource Manager: solicitudes por resolver, banca, sobreasignados,
 *    proyectos por iniciar con vacantes, quién se libera pronto y su agenda.
 *  - Administrador: cuentas y proyectos nuevos, invitaciones sin usar,
 *    cuentas suspendidas y proyectos en curso.
 *
 * "Enviarme el resumen ahora" (Notificaciones) manda el mismo correo al
 * momento, para revisarlo. El horario se cambia con resumen.semanal.cron y
 * se apaga del todo con resumen.semanal.habilitado=false. Pensado para una
 * sola instancia de la app (el EC2): con varias, cada una lo enviaría.
 */
@Service
public class ResumenSemanalService {

    private static final Logger log = LoggerFactory.getLogger(ResumenSemanalService.class);
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    private static final DateTimeFormatter DIA_LARGO = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);
    private static final String PREFERENCIA = "resumen_semanal";
    private static final int MAX_LINEAS = 5;

    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsignacionRepository asignacionRepository;
    private final EntregableRepository entregableRepository;
    private final EntregaRepository entregaRepository;
    private final NotificacionRepository notificacionRepository;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final ProyectoRepository proyectoRepository;
    private final CorreoAutorizadoRepository correoAutorizadoRepository;
    private final PreferenciaNotificacionService preferencias;
    private final EventoService eventoService;
    private final ChatService chatService;
    private final RmPanelService rmPanelService;
    private final EmailService emailService;
    private final TransactionTemplate lectura;

    @Value("${app.base-url:}")
    private String baseUrl;

    @Value("${resumen.semanal.habilitado:true}")
    private boolean habilitado;

    public ResumenSemanalService(PerfilRepository perfilRepository, UsuarioRepository usuarioRepository,
                                 AsignacionRepository asignacionRepository, EntregableRepository entregableRepository,
                                 EntregaRepository entregaRepository, NotificacionRepository notificacionRepository,
                                 PropuestaAsignacionRepository propuestaRepository, ProyectoRepository proyectoRepository,
                                 CorreoAutorizadoRepository correoAutorizadoRepository,
                                 PreferenciaNotificacionService preferencias, EventoService eventoService,
                                 ChatService chatService, RmPanelService rmPanelService, EmailService emailService,
                                 PlatformTransactionManager transacciones) {
        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.asignacionRepository = asignacionRepository;
        this.entregableRepository = entregableRepository;
        this.entregaRepository = entregaRepository;
        this.notificacionRepository = notificacionRepository;
        this.propuestaRepository = propuestaRepository;
        this.proyectoRepository = proyectoRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.preferencias = preferencias;
        this.eventoService = eventoService;
        this.chatService = chatService;
        this.rmPanelService = rmPanelService;
        this.emailService = emailService;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Cada lunes a las 8:00 de Perú (configurable con resumen.semanal.cron). */
    @Scheduled(cron = "${resumen.semanal.cron:0 0 8 * * MON}", zone = "America/Lima")
    public void enviarATodos() {
        if (!habilitado) return;
        List<Long> perfiles = lectura.execute(t -> perfilRepository.listarTodosConUsuario().stream()
                .filter(p -> p.getUsuario() != null && p.getUsuario().isActivo())
                .filter(p -> Boolean.TRUE.equals(preferencias.obtener(p.getId()).get(PREFERENCIA)))
                .map(Perfil::getId).toList());
        int enviados = 0;
        for (Long perfilId : perfiles) {
            try {
                if (enviarA(perfilId)) enviados++;
            } catch (RuntimeException e) {
                log.warn("Resumen semanal: no se pudo armar el del perfil {}: {}", perfilId, e.getMessage());
            }
        }
        log.info("Resumen semanal: {} correos programados.", enviados);
    }

    /** Arma y envía el resumen de una persona (también lo usa "Enviarme el resumen ahora"). */
    public boolean enviarA(Long perfilId) {
        Correo correo = lectura.execute(t -> construir(perfilId));
        if (correo == null) return false;
        emailService.enviar(correo);
        return true;
    }

    // ───────────────────────── contenido ─────────────────────────

    private Correo construir(Long perfilId) {
        Perfil perfil = perfilRepository.buscarConUsuario(perfilId).orElse(null);
        if (perfil == null || perfil.getUsuario() == null) return null;
        String rol = perfil.getUsuario().getRolOrganizacional();
        LocalDate hoy = LocalDate.now();
        LocalDateTime desde = hoy.atStartOfDay(), hasta = hoy.plusDays(7).atStartOfDay();
        String nombre = perfil.getUsuario().getNombreCompleto().trim().split("\\s+")[0];
        String semana = "Semana del " + hoy.format(DIA_LARGO) + " al " + hoy.plusDays(6).format(DIA_LARGO);

        List<Dato> datos = new ArrayList<>();
        List<Seccion> secciones = new ArrayList<>();
        String slug;
        if (Roles.ADMINISTRADOR.equals(rol)) {
            slug = "administrador";
            administrador(datos, secciones, desde.minusDays(7));
        } else if (Roles.RESOURCE_MANAGER.equals(rol)) {
            slug = "resource-manager";
            resourceManager(perfil, datos, secciones, desde, hasta);
        } else {
            // Quien lidera algún proyecto va a su Inicio de Project Manager.
            slug = miembro(perfil, datos, secciones, desde, hasta) ? "project-manager" : "colaborador";
        }
        long avisos = notificacionRepository.countByPerfilIdAndLeidaFalse(perfilId);
        long mensajes = chatService.totalNoLeidos(perfilId);
        String pendientes = avisos + mensajes == 0 ? "No tienes avisos ni mensajes sin leer."
                : "Tienes " + plural(avisos, "aviso", "avisos") + " y " + plural(mensajes, "mensaje", "mensajes")
                  + " sin leer en la plataforma.";
        String enlace = baseUrl != null && !baseUrl.isBlank() ? baseUrl + "/" + slug + "/inicio.html" : null;
        return new Correo(perfil.getUsuario().getCorreo(), "Tu resumen semanal de SkillBridge AI", "Resumen semanal · " + semana,
                "Hola, " + nombre + ": así viene tu semana", pendientes, enlace, "Ir a mi Inicio", true, datos, secciones);
    }

    /** Colaborador (y, si lidera proyectos, también su parte de PM). Devuelve true si lidera. */
    private boolean miembro(Perfil p, List<Dato> datos, List<Seccion> secciones, LocalDateTime desde, LocalDateTime hasta) {
        Long perfilId = p.getId();
        List<Asignacion> activas = asignacionRepository.listarPorPerfilYEstado(perfilId, "activa");
        Integer carga = asignacionRepository.sumarCargaActivaDePerfil(perfilId);
        List<Entregable> porEntregar = entregableRepository.listarVisiblesParaPerfil(perfilId).stream()
                .filter(e -> !e.getFechaCierre().isBefore(desde) && e.getFechaCierre().isBefore(hasta))
                .filter(e -> entregaRepository.findByEntregableIdAndPerfilId(e.getId(), perfilId).isEmpty())
                .toList();
        datos.add(new Dato((carga == null ? 0 : carga) + "%", "Carga actual"));
        datos.add(new Dato(String.valueOf(activas.stream().map(Asignacion::getProyectoId).distinct().count()), "Proyectos activos"));
        datos.add(new Dato(String.valueOf(porEntregar.size()), "Entregas para esta semana"));

        secciones.add(new Seccion("Entregables que cierran esta semana",
                porEntregar.stream().limit(MAX_LINEAS).map(e -> new Linea(e.getTitulo(),
                        e.getProyecto().getNombre() + " · cierra el " + dia(e.getFechaCierre()))).toList(),
                "No tienes entregas pendientes que cierren esta semana."));

        boolean lidera = activas.stream().anyMatch(a -> Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()));
        Map<Long, EventoFila> eventos = new LinkedHashMap<>();
        eventoService.listar(perfilId, false, null).forEach(e -> eventos.put(e.getId(), e));
        if (lidera) eventoService.listar(perfilId, true, null).forEach(e -> eventos.put(e.getId(), e));
        secciones.add(seccionEventos(new ArrayList<>(eventos.values()), desde, hasta, "No hay eventos en tus proyectos esta semana."));

        if (lidera) {
            List<Linea> revisar = new ArrayList<>(), vacantes = new ArrayList<>();
            for (Asignacion a : activas) {
                if (!Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto())) continue;
                for (Entregable e : entregableRepository.findByProyectoIdOrderByFechaCierreAsc(a.getProyectoId())) {
                    long enviadas = entregaRepository.countByEntregableIdAndEstado(e.getId(), "enviado");
                    if (enviadas > 0) revisar.add(new Linea(e.getTitulo(), a.getProyecto().getNombre() + " · "
                            + plural(enviadas, "entrega por revisar", "entregas por revisar")));
                }
                int requeridos = a.getProyecto().getColaboradoresRequeridos() == null ? 0 : a.getProyecto().getColaboradoresRequeridos();
                long ocupados = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(a.getProyectoId(), "activa", Roles.COLABORADOR);
                if (requeridos > ocupados) vacantes.add(new Linea(a.getProyecto().getNombre(),
                        plural(requeridos - ocupados, "vacante", "vacantes") + " de " + requeridos + " colaboradores"));
            }
            secciones.add(new Seccion("Como Project Manager: entregas por revisar", revisar.stream().limit(MAX_LINEAS).toList(),
                    "No tienes entregas esperando revisión."));
            secciones.add(new Seccion("Tus proyectos con vacantes", vacantes.stream().limit(MAX_LINEAS).toList(),
                    "Tus proyectos tienen el equipo completo."));
        }
        return lidera;
    }

    private void resourceManager(Perfil p, List<Dato> datos, List<Seccion> secciones, LocalDateTime desde, LocalDateTime hasta) {
        PanelRm panel = rmPanelService.construir();
        datos.add(new Dato(String.valueOf(panel.solicitudesTotal()), "Solicitudes por resolver"));
        datos.add(new Dato(String.valueOf(panel.enBanca()), "En la banca"));
        datos.add(new Dato(String.valueOf(panel.balance().sobre()), "Sobreasignados"));

        secciones.add(new Seccion("Solicitudes por resolver", panel.solicitudes().stream().limit(MAX_LINEAS)
                .map(s -> new Linea(s.candidato() + " → " + s.proyecto(),
                        "Pedido por " + s.solicitante() + " · " + s.dedicacion() + "%" + (s.superaLimite() ? " · supera el límite de carga" : "")))
                .toList(), "No hay solicitudes de asignación pendientes."));
        secciones.add(new Seccion("Proyectos por iniciar con vacantes", panel.porIniciar().stream()
                .filter(x -> x.vacantes() > 0).limit(MAX_LINEAS)
                .map(x -> new Linea(x.nombre(), plural(x.vacantes(), "vacante", "vacantes") + " · inicia " + x.fechaLabel()))
                .toList(), "Ningún proyecto por iniciar necesita gente."));
        secciones.add(new Seccion("Se liberan pronto", panel.liberados().stream().limit(MAX_LINEAS)
                .map(l -> new Linea(l.nombre(), "Libera " + l.cargaQueLibera() + "% de " + l.proyecto() + " · " + l.fechaLabel()))
                .toList(), "Nadie termina una asignación en los próximos días."));
        secciones.add(seccionEventos(eventoService.listarResourceManager(p.getUsuarioId()), desde, hasta,
                "No tienes eventos ni hitos esta semana."));
    }

    private void administrador(List<Dato> datos, List<Seccion> secciones, LocalDateTime haceUnaSemana) {
        long cuentasNuevas = usuarioRepository.countByFechaCreacionAfter(haceUnaSemana);
        long proyectosNuevos = proyectoRepository.countByFechaCreacionAfter(haceUnaSemana);
        long invitaciones = correoAutorizadoRepository.countByUtilizadoFalse();
        datos.add(new Dato(String.valueOf(cuentasNuevas), "Cuentas nuevas en 7 días"));
        datos.add(new Dato(String.valueOf(proyectosNuevos), "Proyectos nuevos en 7 días"));
        datos.add(new Dato(String.valueOf(invitaciones), "Invitaciones sin usar"));

        List<Linea> estado = new ArrayList<>();
        estado.add(new Linea("Cuentas activas: " + usuarioRepository.countByEstado("activo"),
                "Suspendidas: " + usuarioRepository.countByEstado("inactivo")));
        estado.add(new Linea("Proyectos en curso: " + (proyectoRepository.countByEstado("activo") + proyectoRepository.countByEstado("planificacion")),
                "En pausa: " + proyectoRepository.countByEstado("en_pausa")));
        estado.add(new Linea("Propuestas de asignación pendientes: " + propuestaRepository.listarPendientes().size(),
                "Las resuelve el Resource Manager"));
        secciones.add(new Seccion("Estado de la plataforma", estado, ""));
    }

    private Seccion seccionEventos(List<EventoFila> eventos, LocalDateTime desde, LocalDateTime hasta, String vacio) {
        String d = desde.toString().substring(0, 16), h = hasta.toString().substring(0, 16);
        List<Linea> lineas = eventos.stream()
                .filter(e -> e.getFechaInicioIso().compareTo(d) >= 0 && e.getFechaInicioIso().compareTo(h) < 0)
                .sorted(java.util.Comparator.comparing(EventoFila::getFechaInicioIso))
                .limit(MAX_LINEAS)
                .map(e -> new Linea(e.getTitulo(), dia(LocalDateTime.parse(e.getFechaInicioIso())) + " " + e.getHoraLabel()
                        + " · " + e.getTipoNombre() + " · " + e.getProyectoNombre()))
                .toList();
        return new Seccion("Agenda de la semana", lineas, vacio);
    }

    /** "lun 23 nov" (sin el punto de la abreviatura). */
    private static String dia(LocalDateTime f) {
        return f.format(DIA).replace(".", "");
    }

    private static String plural(long n, String uno, String varios) {
        return n + " " + (n == 1 ? uno : varios);
    }
}
