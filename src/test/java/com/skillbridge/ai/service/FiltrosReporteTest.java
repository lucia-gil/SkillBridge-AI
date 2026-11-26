package com.skillbridge.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FiltrosReporteTest {

    @Test
    @DisplayName("PM: el periodo rápido define el inicio del rango y el rango manual manda")
    void periodoRapido() {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));
        assertEquals(hoy.minusMonths(6), new ReportePmService.FiltrosPm(null, null, null, null, false, "6m", null).desdeEfectivo());
        assertEquals(hoy.minusYears(1), new ReportePmService.FiltrosPm(null, null, null, null, false, "1a", null).desdeEfectivo());
        assertEquals(hoy.minusYears(3), new ReportePmService.FiltrosPm(null, null, null, null, false, "3a", null).desdeEfectivo());
        assertNull(new ReportePmService.FiltrosPm(null, null, null, null, false, "", null).desdeEfectivo());
        LocalDate manual = LocalDate.of(2020, 1, 1);
        assertEquals(manual, new ReportePmService.FiltrosPm(null, null, manual, null, false, "6m", null).desdeEfectivo());
    }

    @Test
    @DisplayName("PM: valores fuera de lista se ignoran (periodo y 'últimos N')")
    void valoresInvalidos() {
        var f = new ReportePmService.FiltrosPm(null, null, null, null, false, "99y';drop", 7);
        assertEquals("", f.periodo());
        assertNull(f.ultimos());
        assertEquals(10, new ReportePmService.FiltrosPm(null, null, null, null, false, null, 10).ultimos());
    }

    @Test
    @DisplayName("RM: varios proyectos, sin nulos ni repetidos, y SQL solo con números")
    void variosProyectosRm() {
        var f = new RmReporteService.Filtros(null, null, null, Arrays.asList(3L, null, 3L, 8L), null, 0, false);
        assertEquals(List.of(3L, 8L), f.proyectoIds());
        assertTrue(f.porProyecto());
        assertFalse(f.vacios());
        assertEquals(" AND e.proyecto_id IN (3,8)", f.sqlProyectos("e.proyecto_id"));
        var vacio = new RmReporteService.Filtros(null, null, null, null, null, 0, false);
        assertEquals("", vacio.sqlProyectos("e.proyecto_id"));
        assertTrue(vacio.vacios());
    }
}
