package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mapea "proyectos" (RF03). "tecnologias" es la columna JSON del esquema
 * (lista libre de strings, ej. ["React","Node"]) - se mapea como String con
 * el JSON ya serializado; ProyectoService la lee/escribe con ObjectMapper,
 * igual que se hace con las habilidades declaradas en el registro.
 *
 * No existe columna de "% de avance" en el esquema: la UI la calcula como
 * un heuristico de tiempo transcurrido entre fecha_inicio y
 * fecha_fin_estimada (ver ProyectoService.avanceHeuristico), documentado
 * para no confundirlo con un dato real de progreso de tareas.
 */
@Entity
@Table(name = "proyectos")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "tecnologias", columnDefinition = "json")
    private String tecnologias;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "planificacion";

    @Column(name = "colaboradores_requeridos", nullable = false)
    private Integer colaboradoresRequeridos = 0;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTecnologias() {
        return tecnologias;
    }

    public void setTecnologias(String tecnologias) {
        this.tecnologias = tecnologias;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Integer getColaboradoresRequeridos() {
        return colaboradoresRequeridos;
    }

    public void setColaboradoresRequeridos(Integer colaboradoresRequeridos) {
        this.colaboradoresRequeridos = colaboradoresRequeridos;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFinEstimada() {
        return fechaFinEstimada;
    }

    public void setFechaFinEstimada(LocalDate fechaFinEstimada) {
        this.fechaFinEstimada = fechaFinEstimada;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
