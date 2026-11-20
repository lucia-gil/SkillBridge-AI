package com.skillbridge.ai.service.agente;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.AgenteConfirmacionVista;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.AgenteAuditoria;
import com.skillbridge.ai.model.AgenteConfirmacion;
import com.skillbridge.ai.repository.AgenteAuditoriaRepository;
import com.skillbridge.ai.repository.AgenteConfirmacionRepository;
import com.skillbridge.ai.repository.AgenteMutexRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Confirmación explícita de las escrituras del agente: el modelo solo
 * PREPARA un cambio (preparar); se ejecuta cuando la persona pulsa
 * "Confirmar cambio" (resolver). Cada paso queda en agente_auditoria.
 */
@Service
public class ConfirmacionAgenteService {
    @org.springframework.beans.factory.annotation.Value("${agente.enabled:true}")
    private boolean servidorActivo;
    @org.springframework.beans.factory.annotation.Autowired
    private com.skillbridge.ai.service.ConfiguracionService configuracion;


    private static final long VIGENCIA_SEGUNDOS = 300;

    private final AgenteConfirmacionRepository confirmacionRepository;
    private final AgenteAuditoriaRepository auditoriaRepository;
    private final AgenteMutexRepository mutexRepository;
    private final HerramientasAgente herramientas;
    private final PoliticaAgente politica;
    private final ObjectMapper mapper;

    public ConfirmacionAgenteService(AgenteConfirmacionRepository confirmacionRepository,
                                     AgenteAuditoriaRepository auditoriaRepository,
                                     AgenteMutexRepository mutexRepository, HerramientasAgente herramientas,
                                     PoliticaAgente politica, ObjectMapper mapper) {
        this.confirmacionRepository = confirmacionRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.mutexRepository = mutexRepository;
        this.herramientas = herramientas;
        this.politica = politica;
        this.mapper = mapper;
    }

    /** Valida la herramienta y sus argumentos y deja la propuesta pendiente (reemplaza la anterior). */
    @Transactional
    public void preparar(UsuarioSesion s, String nombre, JsonNode args) {
        HerramientasAgente.Definicion d = herramientas.definir(s, nombre);
        if (!d.escritura()) throw new IllegalArgumentException();
        herramientas.autorizarRecurso(s, herramientas.argumentos(d, args));

        bloquearMutex();
        confirmacionRepository.cancelarPendientes(s.getUsuarioId(), s.getRolEfectivo());
        Instant ahora = Instant.now();
        confirmacionRepository.save(new AgenteConfirmacion(UUID.randomUUID().toString(), s.getUsuarioId(),
                s.getRolEfectivo(), nombre, args.toString(), ahora, ahora.plusSeconds(VIGENCIA_SEGUNDOS)));
        auditar(s, nombre, "PREPARADA", null);
    }

    /** La propuesta pendiente y vigente (lista de 0 o 1 elementos). */
    @Transactional(readOnly = true)
    public List<AgenteConfirmacionVista> pendientes(UsuarioSesion s) {
        politica.validar(s);
        return confirmacionRepository
                .findFirstByUsuarioIdAndRolAndEstadoAndExpiraAfterOrderByCreadaDesc(
                        s.getUsuarioId(), s.getRolEfectivo(), AgenteConfirmacion.PENDIENTE, Instant.now())
                .map(c -> List.of(new AgenteConfirmacionVista(c.getId(), c.getHerramienta(), c.getArgumentos(), c.getExpira())))
                .orElse(List.of());
    }

    /** Ejecuta (confirmar=true) o descarta la propuesta. Solo funciona una vez y antes de que venza. */
    @Transactional
    public void resolver(UsuarioSesion s, String id, boolean confirmar) {
        if (confirmar && (!servidorActivo || !configuracion.activo("asistente_ia_habilitado")))
            throw new org.springframework.security.access.AccessDeniedException("Asistente desactivado.");
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Confirmación inválida.");
        // Mismo orden de bloqueos siempre: primero el mutex global, luego la confirmación.
        bloquearMutex();
        politica.validar(s);
        AgenteConfirmacion c = confirmacionRepository
                .bloquearPendiente(id, s.getUsuarioId(), s.getRolEfectivo(), Instant.now())
                .orElseThrow(() -> new AccessDeniedException("Confirmación inexistente, vencida o utilizada."));

        if (confirmar) {
            try {
                herramientas.escribir(s, c.getHerramienta(), mapper.readTree(c.getArgumentos()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Confirmación inválida.");
            }
        }
        c.setEstado(confirmar ? AgenteConfirmacion.EJECUTADA : AgenteConfirmacion.CANCELADA);
        auditar(s, c.getHerramienta(), confirmar ? "EJECUTADA" : "CANCELADA", id);
    }

    /** Nunca guarda prompts, respuestas ni argumentos: solo quién, qué herramienta y el resultado. */
    @Transactional
    public void auditar(UsuarioSesion s, String nombre, String resultado, String correlacion) {
        String herramienta = nombre == null || nombre.length() > 80 ? "desconocida" : nombre;
        auditoriaRepository.save(new AgenteAuditoria(s.getUsuarioId(), herramienta, resultado, correlacion));
    }

    private void bloquearMutex() {
        mutexRepository.bloquear().orElseThrow(() -> new IllegalStateException("Falta la fila de agente_mutex."));
    }
}
