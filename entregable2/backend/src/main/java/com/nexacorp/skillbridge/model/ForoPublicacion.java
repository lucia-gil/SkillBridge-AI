package com.nexacorp.skillbridge.model;

import com.nexacorp.skillbridge.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF06. Tabla autorreferencial: publicacionPadre == null es un hilo raíz
 * (tiene título); no null es una respuesta (a ese hilo o a otra respuesta).
 * numVistas es una extensión propia (no está en el documento fuente) para
 * no perder esa funcionalidad, visible en los mockups.
 */
@Entity
@Table(name = "foro_publicaciones")
@Getter
@Setter
@NoArgsConstructor
public class ForoPublicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Perfil autor;

    /** NULL = hilo raíz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publicacion_padre_id")
    private ForoPublicacion publicacionPadre;

    @Column(length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "JSON")
    private List<String> etiquetas;

    @Column(name = "es_solucion", nullable = false)
    private boolean esSolucion = false;

    @Column(name = "num_vistas", nullable = false)
    private int numVistas = 0;

    @Column(name = "fecha_publicacion", nullable = false, updatable = false)
    private LocalDateTime fechaPublicacion;

    @PrePersist
    void onCreate() {
        this.fechaPublicacion = LocalDateTime.now();
    }
}
