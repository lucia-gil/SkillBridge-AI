package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RF09. */
@Entity
@Table(name = "preferencias_notificacion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"perfil_id", "tipo_evento"}))
@Getter
@Setter
@NoArgsConstructor
public class PreferenciaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 30)
    private TipoEventoNotificacion tipoEvento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CanalPreferencia canal;
}
