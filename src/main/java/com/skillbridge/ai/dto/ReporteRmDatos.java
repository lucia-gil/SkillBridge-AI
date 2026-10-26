package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Todo lo que muestra (y exporta) la pantalla de Reportes del Resource Manager,
 * ya filtrado. La vista, el Excel y el PDF salen de este mismo objeto, así
 * lo exportado siempre coincide con lo que se ve.
 *
 * @param limiteCarga          límite de carga vigente (Configuración)
 * @param ocupacionPromedio    promedio entre quienes tienen asignaciones (igual que en Inicio)
 * @param sobreAsignados       miembros por encima del límite
 * @param enBanca              miembros activos sin ninguna asignación
 * @param capacidadLibre       suma de (límite − carga) de los miembros por debajo del límite, en %
 * @param miembrosEvaluados    miembros activos considerados (con o sin asignación)
 * @param ocupacion            filas de ocupación que pasan los filtros
 * @param cobertura            oferta contra demanda por habilidad que pasa los filtros
 * @param habilidadesConBrecha habilidades pedidas sin cobertura o ajustadas (sin filtrar)
 * @param habilidadesPedidas   habilidades pedidas por proyectos vigentes (sin filtrar)
 * @param proyectosVigentes    proyectos en planificación o activos
 * @param filtrosLabel         descripción legible de los filtros aplicados
 * @param generado             fecha y hora de generación
 */
public record ReporteRmDatos(int limiteCarga, int ocupacionPromedio, int sobreAsignados, int enBanca,
                             int capacidadLibre, int miembrosEvaluados,
                             List<OcupacionColaboradorFila> ocupacion,
                             List<CoberturaHabilidadFila> cobertura,
                             int habilidadesConBrecha, int habilidadesPedidas, int proyectosVigentes,
                             String filtrosLabel, String generado) {

    /** Cuántas filas de ocupación (ya filtradas) tienen este estado de carga. */
    public long conteo(String estado) {
        return ocupacion.stream().filter(o -> estado.equals(o.getEstadoLabel())).count();
    }

    /** Capacidad libre expresada en personas a tiempo completo (una decimal). */
    public String capacidadLibrePersonas() {
        double personas = capacidadLibre / 100.0;
        return (Math.round(personas * 10) / 10.0 + "").replace(".0", "").replace('.', ',');
    }
}
