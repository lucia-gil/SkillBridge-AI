package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Datos del Inicio del Resource Manager (resource-manager/inicio.html).
 * Todo sale de la BD (RmPanelService); nada es inventado ni de mock-data.js.
 * En las plantillas se leen con llamada a método: ${panel.balance().optimos()}.
 */
public record PanelRm(
        Tendencia tendencia,
        Prediccion prediccion,
        Balance balance,
        List<PersonaDisponible> disponibles,
        long enBanca,
        int capacidadLibreTotal,
        List<Liberacion> liberados,
        List<Solicitud> solicitudes,
        long solicitudesTotal,
        List<ProyectoProximo> porIniciar,
        Gantt gantt,
        List<HabilidadDemanda> habilidades,
        int limiteCarga) {

    /** Ocupación promedio de las últimas semanas (según fechas de las asignaciones). */
    public record Tendencia(List<Integer> semanas, int actual, int variacion, String puntosSparkline,
                            String areaSparkline) {
        public boolean sube() { return variacion > 0; }
        public boolean baja() { return variacion < 0; }
    }

    /** Personas que superarían el límite de carga en los próximos días (asignaciones ya programadas). */
    public record Prediccion(int dias, List<PersonaRiesgo> enRiesgo) {
        public boolean hayRiesgo() { return !enRiesgo.isEmpty(); }
    }

    public record PersonaRiesgo(Long perfilId, String nombre, int cargaFutura) { }

    /** Distribución de la carga de todos los Miembros disponibles. */
    public record Balance(int total, int sinAsignar, int baja, int media, int optimos, int alta, int sobre) {
        public int pct(int n) { return total == 0 ? 0 : Math.round(n * 100f / total); }
    }

    public record HabilidadNivel(String nombre, int nivel) { }

    /** Miembro con capacidad libre (carga menor al 30%). */
    public record PersonaDisponible(Long perfilId, String nombre, String iniciales, String cargo, int carga,
                                    int libre, List<HabilidadNivel> habilidades, Integer diasEnBanca,
                                    String sugerenciaProyecto, Long sugerenciaProyectoId, List<String> sugerenciaHabilidades) {
        public boolean enBanca() { return carga == 0; }
        public boolean bancaLarga() { return diasEnBanca != null && diasEnBanca > 30; }
    }

    /** Asignación que termina pronto: esa capacidad vuelve a quedar libre. */
    public record Liberacion(Long perfilId, String nombre, String iniciales, String proyecto, String fechaLabel,
                             long diasRestantes, int cargaQueLibera, int cargaActual) { }

    /** Propuesta de un Project Manager pendiente de revisión del RM. */
    public record Solicitud(Long id, String solicitante, String candidato, String candidatoIniciales,
                            String proyecto, int dedicacion, Integer score, String fechaLabel,
                            String origen, boolean superaLimite) {
        public String origenLabel() {
            if ("manual".equals(origen)) return "Manual";
            if ("reincorporacion".equals(origen)) return "Reincorporación";
            return "Talent Matching";
        }
    }

    public record ProyectoProximo(Long id, String nombre, String estado, String fechaLabel, Long diasParaIniciar,
                                  int requeridos, int asignados, int vacantes, List<String> habilidades) { }

    /** Línea de tiempo compacta: ventana de días con la posición de cada proyecto en %. */
    public record Gantt(List<GanttFila> filas, List<GanttMarca> meses, double hoyPct, String rangoLabel) { }

    public record GanttFila(Long id, String nombre, String estado, String estadoLabel, double inicioPct,
                            double anchoPct, boolean abierto, boolean empiezaAntes, String fechasLabel,
                            int equipo, int vacantes) { }

    public record GanttMarca(String etiqueta, double pct) { }

    /** Demanda (proyectos vigentes) vs. oferta (Miembros que la tienen) de una habilidad. */
    public record HabilidadDemanda(Long id, String nombre, int personas, int conCapacidad, int proyectos,
                                   int plazasAbiertas, int faltan, int barraPct) {
        public boolean enRiesgo() { return personas <= 2; }
    }
}
