package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "foro_adjuntos": archivo (imagen, PDF, Word...) o enlace adjunto a
 * un mensaje del foro.
 *
 * tipo = "archivo": el binario va en `contenido` (mismo enfoque que
 *                   entregas.archivo_contenido, funciona en EC2 sin disco
 *                   compartido). Si en el futuro se usa S3, se llena `url`
 *                   y `contenido` queda NULL.
 * tipo = "enlace":  solo `url`.
 *
 * Para listar adjuntos NUNCA se carga esta entidad completa (traería el
 * binario): ForoAdjuntoRepository.listarInfo devuelve solo metadatos.
 */
@Entity
@Table(name = "foro_adjuntos")
public class ForoAdjunto {

    public static final String TIPO_ARCHIVO = "archivo";
    public static final String TIPO_ENLACE = "enlace";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "publicacion_id", nullable = false)
    private Long publicacionId;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "mime_tipo", length = 120)
    private String mimeTipo;

    @Column(name = "tamano_bytes")
    private Integer tamanoBytes;

    @Column(name = "contenido")
    private byte[] contenido;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public void setPublicacionId(Long publicacionId) {
        this.publicacionId = publicacionId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getMimeTipo() {
        return mimeTipo;
    }

    public void setMimeTipo(String mimeTipo) {
        this.mimeTipo = mimeTipo;
    }

    public Integer getTamanoBytes() {
        return tamanoBytes;
    }

    public void setTamanoBytes(Integer tamanoBytes) {
        this.tamanoBytes = tamanoBytes;
    }

    public byte[] getContenido() {
        return contenido;
    }

    public void setContenido(byte[] contenido) {
        this.contenido = contenido;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
