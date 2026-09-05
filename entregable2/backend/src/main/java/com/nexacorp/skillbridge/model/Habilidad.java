package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo cerrado (RF08). categoria es texto libre, no hay tabla de categorías propia. */
@Entity
@Table(name = "habilidades")
@Getter
@Setter
@NoArgsConstructor
public class Habilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false, length = 60)
    private String categoria;
}
