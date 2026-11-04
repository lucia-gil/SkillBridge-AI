package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * recomendaciones_ia_log: cada candidato que AI Talent Matching recomienda
 * con explicación de la IA (RF05). fue_asignado pasa a true si esa persona
 * termina asignada al proyecto, lo que permite medir qué tan acertada es la IA.
 */
@Entity
@Table(name = "recomendaciones_ia_log")
public class RecomendacionIaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "perfil_recomendado_id", nullable = false)
    private Long perfilRecomendadoId;

    @Column(name = "solicitado_por_id", nullable = false)
    private Long solicitadoPorId;

    @Column(name = "puntaje_compatibilidad", nullable = false, precision = 5, scale = 2)
    private BigDecimal puntajeCompatibilidad;

    @Column(name = "explicacion", nullable = false, columnDefinition = "TEXT")
    private String explicacion;

    @Column(name = "fue_asignado", nullable = false)
    private boolean fueAsignado;

    @Column(name = "fecha_generado", nullable = false)
    private LocalDateTime fechaGenerado;

    protected RecomendacionIaLog() { }

    public RecomendacionIaLog(Long proyectoId, Long perfilRecomendadoId, Long solicitadoPorId, int puntaje, String explicacion) {
        this.proyectoId = proyectoId;
        this.perfilRecomendadoId = perfilRecomendadoId;
        this.solicitadoPorId = solicitadoPorId;
        this.puntajeCompatibilidad = BigDecimal.valueOf(Math.max(0, Math.min(100, puntaje)));
        this.explicacion = explicacion;
        this.fechaGenerado = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public Long getPerfilRecomendadoId() { return perfilRecomendadoId; }
    public Long getSolicitadoPorId() { return solicitadoPorId; }
    public BigDecimal getPuntajeCompatibilidad() { return puntajeCompatibilidad; }
    public String getExplicacion() { return explicacion; }
    public boolean isFueAsignado() { return fueAsignado; }
    public LocalDateTime getFechaGenerado() { return fechaGenerado; }
}
