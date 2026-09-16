package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "propuestas_asignacion")
public class PropuestaAsignacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "proyecto_id", nullable = false) private Long proyectoId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proyecto_id", insertable = false, updatable = false) private Proyecto proyecto;
    @Column(name = "candidato_perfil_id", nullable = false) private Long candidatoPerfilId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "candidato_perfil_id", insertable = false, updatable = false) private Perfil candidato;
    @Column(name = "solicitado_por_perfil_id", nullable = false) private Long solicitadoPorPerfilId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "solicitado_por_perfil_id", insertable = false, updatable = false) private Perfil solicitante;
    @Column(name = "revisado_por_perfil_id") private Long revisadoPorPerfilId;
    @Column(name = "dedicacion_porcentaje", nullable = false) private Integer dedicacionPorcentaje;
    @Column(name = "score_total", nullable = false) private Integer scoreTotal;
    @Column(name = "score_habilidades", nullable = false) private Integer scoreHabilidades;
    @Column(name = "score_experiencia", nullable = false) private Integer scoreExperiencia;
    @Column(name = "score_disponibilidad", nullable = false) private Integer scoreDisponibilidad;
    @Column(nullable = false, length = 20) private String estado = "pendiente";
    @Column(name = "motivo_resolucion", length = 500) private String motivoResolucion;
    @Column(name = "fecha_solicitud", insertable = false, updatable = false) private LocalDateTime fechaSolicitud;
    @Column(name = "fecha_resolucion") private LocalDateTime fechaResolucion;

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Proyecto getProyecto() { return proyecto; }
    public Long getCandidatoPerfilId() { return candidatoPerfilId; }
    public void setCandidatoPerfilId(Long candidatoPerfilId) { this.candidatoPerfilId = candidatoPerfilId; }
    public Perfil getCandidato() { return candidato; }
    public Long getSolicitadoPorPerfilId() { return solicitadoPorPerfilId; }
    public void setSolicitadoPorPerfilId(Long id) { this.solicitadoPorPerfilId = id; }
    public Perfil getSolicitante() { return solicitante; }
    public Long getRevisadoPorPerfilId() { return revisadoPorPerfilId; }
    public void setRevisadoPorPerfilId(Long id) { this.revisadoPorPerfilId = id; }
    public Integer getDedicacionPorcentaje() { return dedicacionPorcentaje; }
    public void setDedicacionPorcentaje(Integer v) { this.dedicacionPorcentaje = v; }
    public Integer getScoreTotal() { return scoreTotal; }
    public void setScoreTotal(Integer v) { this.scoreTotal = v; }
    public Integer getScoreHabilidades() { return scoreHabilidades; }
    public void setScoreHabilidades(Integer v) { this.scoreHabilidades = v; }
    public Integer getScoreExperiencia() { return scoreExperiencia; }
    public void setScoreExperiencia(Integer v) { this.scoreExperiencia = v; }
    public Integer getScoreDisponibilidad() { return scoreDisponibilidad; }
    public void setScoreDisponibilidad(Integer v) { this.scoreDisponibilidad = v; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMotivoResolucion() { return motivoResolucion; }
    public void setMotivoResolucion(String v) { this.motivoResolucion = v; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public LocalDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(LocalDateTime v) { this.fechaResolucion = v; }
}
