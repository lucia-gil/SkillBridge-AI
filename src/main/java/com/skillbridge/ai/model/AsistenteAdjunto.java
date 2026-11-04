package com.skillbridge.ai.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** asistente_adjuntos: archivo o imagen adjunto a un mensaje del Asistente IA. */
@Entity
@Table(name = "asistente_adjuntos")
public class AsistenteAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensaje_id", nullable = false)
    private Long mensajeId;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "tipo", nullable = false, length = 100)
    private String tipo;

    @Column(name = "tamano", nullable = false)
    private int tamano;

    @Lob
    @Column(name = "datos", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] datos;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected AsistenteAdjunto() { }

    public AsistenteAdjunto(Long mensajeId, String nombre, String tipo, byte[] datos) {
        this.mensajeId = mensajeId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.datos = datos;
        this.tamano = datos.length;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getMensajeId() { return mensajeId; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public int getTamano() { return tamano; }
    public byte[] getDatos() { return datos; }
}
