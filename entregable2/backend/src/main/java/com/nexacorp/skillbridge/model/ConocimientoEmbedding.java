package com.nexacorp.skillbridge.model;

import com.nexacorp.skillbridge.converter.DoubleListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Diferenciador de búsqueda semántica. referenciaId es otra referencia
 * "genérica" sin FK real (apunta a foro_publicaciones, perfiles o
 * proyectos según tipoOrigen).
 *
 * LIMITACIÓN DECLARADA: vectorEmbedding es JSON, no un tipo vector nativo
 * (el documento fuente menciona pgvector, que es de PostgreSQL; este
 * proyecto usa MySQL). La similitud de coseno se calcula en la aplicación.
 * Ver detalle en schema.sql y en el informe.
 */
@Entity
@Table(name = "conocimiento_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class ConocimientoEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_origen", nullable = false, length = 20)
    private TipoOrigenEmbedding tipoOrigen;

    @Column(name = "referencia_id", nullable = false)
    private Long referenciaId;

    @Column(name = "contenido_indexado", nullable = false, columnDefinition = "TEXT")
    private String contenidoIndexado;

    @Convert(converter = DoubleListJsonConverter.class)
    @Column(name = "vector_embedding", nullable = false, columnDefinition = "JSON")
    private List<Double> vectorEmbedding;

    @Column(name = "fecha_indexado", nullable = false, updatable = false)
    private LocalDateTime fechaIndexado;

    @PrePersist
    void onCreate() {
        this.fechaIndexado = LocalDateTime.now();
    }
}
