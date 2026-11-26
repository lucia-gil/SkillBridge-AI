package com.skillbridge.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Interpretación por palabras del buscador del Colaborador (cuando la IA no responde). */
class BuscadorColaboradorServiceTest {

    @Test
    @DisplayName("Reconoce qué tipo de cosa busca el colaborador")
    void tipoPorPalabras() {
        assertEquals("entregables", BuscadorColaboradorService.tipoPorPalabras("mis entregables completados"));
        assertEquals("entregables", BuscadorColaboradorService.tipoPorPalabras("¿qué tareas tengo?"));
        assertEquals("foros", BuscadorColaboradorService.tipoPorPalabras("hilos del foro sobre Firebase"));
        assertEquals("eventos", BuscadorColaboradorService.tipoPorPalabras("reuniones de esta semana"));
        assertEquals("cursos", BuscadorColaboradorService.tipoPorPalabras("cursos de Java"));
        assertNull(BuscadorColaboradorService.tipoPorPalabras("App Móvil Aurora"));
    }

    @Test
    @DisplayName("Traduce los estados de la entrega")
    void estadosPorPalabras() {
        assertEquals(List.of("completado"), BuscadorColaboradorService.estadosPorPalabras("entregables completados"));
        assertTrue(BuscadorColaboradorService.estadosPorPalabras("qué me falta corregir").containsAll(List.of("requiere_ajustes", "rehacer")));
        assertTrue(BuscadorColaboradorService.estadosPorPalabras("entregables pendientes").containsAll(List.of("pendiente", "vencido")));
        assertEquals(List.of("vencido"), BuscadorColaboradorService.estadosPorPalabras("entregas vencidas"));
        assertTrue(BuscadorColaboradorService.estadosPorPalabras("mis entregables").isEmpty());
    }

    @Test
    @DisplayName("Ignora palabras vacías al buscar en todo")
    void palabrasClave() {
        assertEquals(List.of("docker"), BuscadorColaboradorService.palabrasClave("¿hay algo de Docker?"));
    }
}
