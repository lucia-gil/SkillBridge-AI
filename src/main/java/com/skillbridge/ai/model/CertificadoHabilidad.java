package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Constancia/certificado que respalda UNA habilidad declarada por un
 * perfil (perfil_habilidad).
 *
 * Dos formas de respaldo, mutuamente excluyentes en la práctica (si se
 * sube un archivo, se usa ese; si no, se usa el link):
 *  - Opción A (link): url_archivo es un link externo (Drive, Coursera,
 *    LinkedIn Learning...) que el colaborador pega desde su propio perfil.
 *  - Opción B (archivo real): contenido_archivo/tipo_archivo guardan el
 *    PDF subido tal cual, mismo patrón que Usuario.fotoPerfil.
 */
@Entity
@Table(name = "certificados_habilidad")
public class CertificadoHabilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "habilidad_id", nullable = false)
    private Long habilidadId;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "url_archivo", length = 500)
    private String urlArchivo;

    @Lob
    @Column(name = "contenido_archivo")
    private byte[] contenidoArchivo;

    @Column(name = "tipo_archivo", length = 100)
    private String tipoArchivo;

    @Column(name = "fecha_subida", insertable = false, updatable = false)
    private LocalDateTime fechaSubida;

    public Long getId() {
        return id;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
    }

    public Long getHabilidadId() {
        return habilidadId;
    }

    public void setHabilidadId(Long habilidadId) {
        this.habilidadId = habilidadId;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getUrlArchivo() {
        return urlArchivo;
    }

    public void setUrlArchivo(String urlArchivo) {
        this.urlArchivo = urlArchivo;
    }

    public byte[] getContenidoArchivo() {
        return contenidoArchivo;
    }

    public void setContenidoArchivo(byte[] contenidoArchivo) {
        this.contenidoArchivo = contenidoArchivo;
    }

    public String getTipoArchivo() {
        return tipoArchivo;
    }

    public void setTipoArchivo(String tipoArchivo) {
        this.tipoArchivo = tipoArchivo;
    }

    public boolean tieneArchivo() {
        return contenidoArchivo != null && contenidoArchivo.length > 0;
    }

    public LocalDateTime getFechaSubida() {
        return fechaSubida;
    }
}
