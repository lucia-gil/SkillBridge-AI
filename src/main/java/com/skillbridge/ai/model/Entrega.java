package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que UN colaborador sube para UN entregable. Como máximo una fila por
 * (entregable, perfil) — igual criterio que certificados_habilidad:
 * "editar entrega" y "borrar entrega" actúan sobre esta misma fila, no
 * se acumulan versiones.
 *
 * Contenido: texto, urlEntrega y archivo son independientes entre sí (se
 * exige al menos uno en EntregableService, no aquí). "Atrasado" no se
 * guarda: se calcula comparando fechaEntrega contra
 * entregable.fechaCierre, así nunca queda desincronizado si el PM mueve
 * el cierre después de que alguien ya entregó.
 */
@Entity
@Table(name = "entregas")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entregable_id", nullable = false)
    private Long entregableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregable_id", insertable = false, updatable = false)
    private Entregable entregable;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", insertable = false, updatable = false)
    private Perfil perfil;

    @Column(name = "texto")
    private String texto;

    @Column(name = "url_entrega", length = 500)
    private String urlEntrega;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "archivo_contenido")
    private byte[] archivoContenido;

    @Column(name = "archivo_nombre", length = 200)
    private String archivoNombre;

    @Column(name = "archivo_tipo", length = 100)
    private String archivoTipo;

    @Column(name = "fecha_entrega", insertable = false, updatable = false)
    private LocalDateTime fechaEntrega;

    /** 'enviado' | 'revisado' (revisado = el PM ya calificó/comentó). */
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "enviado";

    @Column(name = "calificacion")
    private BigDecimal calificacion;

    @Column(name = "comentario_pm")
    private String comentarioPm;

    @Column(name = "revisado_por_id")
    private Long revisadoPorId;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    public Long getId() { return id; }
    public Long getEntregableId() { return entregableId; }
    public void setEntregableId(Long entregableId) { this.entregableId = entregableId; }
    public Entregable getEntregable() { return entregable; }
    public Long getPerfilId() { return perfilId; }
    public void setPerfilId(Long perfilId) { this.perfilId = perfilId; }
    public Perfil getPerfil() { return perfil; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getUrlEntrega() { return urlEntrega; }
    public void setUrlEntrega(String urlEntrega) { this.urlEntrega = urlEntrega; }
    public byte[] getArchivoContenido() { return archivoContenido; }
    public void setArchivoContenido(byte[] archivoContenido) { this.archivoContenido = archivoContenido; }
    public String getArchivoNombre() { return archivoNombre; }
    public void setArchivoNombre(String archivoNombre) { this.archivoNombre = archivoNombre; }
    public String getArchivoTipo() { return archivoTipo; }
    public void setArchivoTipo(String archivoTipo) { this.archivoTipo = archivoTipo; }
    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public BigDecimal getCalificacion() { return calificacion; }
    public void setCalificacion(BigDecimal calificacion) { this.calificacion = calificacion; }
    public String getComentarioPm() { return comentarioPm; }
    public void setComentarioPm(String comentarioPm) { this.comentarioPm = comentarioPm; }
    public Long getRevisadoPorId() { return revisadoPorId; }
    public void setRevisadoPorId(Long revisadoPorId) { this.revisadoPorId = revisadoPorId; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public void setFechaRevision(LocalDateTime fechaRevision) { this.fechaRevision = fechaRevision; }

    public boolean tieneArchivo() {
        return archivoContenido != null && archivoContenido.length > 0;
    }
}
