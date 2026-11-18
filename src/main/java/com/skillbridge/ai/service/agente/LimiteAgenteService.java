package com.skillbridge.ai.service.agente;

import com.skillbridge.ai.model.AgenteLimite;
import com.skillbridge.ai.repository.AgenteLimiteRepository;
import com.skillbridge.ai.repository.AgenteMutexRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Límite de solicitudes al agente: como máximo agente.solicitudes-minuto
 * por usuario y minuto, y una sola en curso a la vez (lease de 4 minutos
 * que se libera al terminar). Si se supera, responde 429.
 */
@Service
public class LimiteAgenteService {

    private static final long DURACION_LEASE_SEGUNDOS = 240;

    private final AgenteLimiteRepository limiteRepository;
    private final AgenteMutexRepository mutexRepository;
    private final int maximoPorMinuto;

    public LimiteAgenteService(AgenteLimiteRepository limiteRepository, AgenteMutexRepository mutexRepository,
                               @Value("${agente.solicitudes-minuto:8}") int maximoPorMinuto) {
        this.limiteRepository = limiteRepository;
        this.mutexRepository = mutexRepository;
        this.maximoPorMinuto = Math.max(1, maximoPorMinuto);
    }

    /** Reserva una solicitud y devuelve el id del lease (para liberarlo después). */
    @Transactional
    public String adquirir(Long usuarioId) {
        long ahora = Instant.now().getEpochSecond();
        long ventana = ahora / 60;

        if (!limiteRepository.existsById(usuarioId)) {
            // Primera vez: el mutex evita que dos solicitudes creen la fila a la vez.
            mutexRepository.bloquear().orElseThrow(() -> new IllegalStateException("Falta la fila de agente_mutex."));
            if (limiteRepository.bloquear(usuarioId).isEmpty()) {
                limiteRepository.saveAndFlush(new AgenteLimite(usuarioId, ventana));
            }
        }
        AgenteLimite limite = limiteRepository.bloquear(usuarioId).orElseThrow();

        int cantidad = limite.getVentana() == ventana ? limite.getCantidad() : 0;
        if (cantidad >= maximoPorMinuto || limite.getLeaseHasta() > ahora) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite del agente; intenta más tarde.");
        }

        String leaseId = UUID.randomUUID().toString();
        limite.setVentana(ventana);
        limite.setCantidad(cantidad + 1);
        limite.setLeaseId(leaseId);
        limite.setLeaseHasta(ahora + DURACION_LEASE_SEGUNDOS);
        return leaseId;
    }

    @Transactional
    public void liberar(Long usuarioId, String leaseId) {
        limiteRepository.liberar(usuarioId, leaseId);
    }
}
