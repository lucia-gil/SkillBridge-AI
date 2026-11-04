package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Todo lo que muestra el dashboard de Reportes del Resource Manager y lo que
 * se exporta a Excel / PDF, ya filtrado (RmReporteService). La vista y los
 * archivos salen de este mismo objeto, así lo descargado coincide con lo visto.
 */
public record ReporteRm(
        String generado,
        String filtrosLabel,
        boolean sinFiltros,
        int limiteCarga,
        Kpis kpis,
        List<Hallazgo> hallazgos,
        List<Segmento> distribucionCarga,
        List<Segmento> estadosProyecto,
        List<Segmento> coberturaHabilidades,
        List<Barra> cargaPorCargo,
        List<Barra> brechas,
        List<Persona> personas,
        int personasEvaluadas,
        List<ProyectoFila> proyectos,
        List<CoberturaHabilidadFila> habilidades,
        Decisiones decisiones,
        List<PropuestaFila> propuestas,
        Entregas entregas,
        List<Liberacion> liberaciones,
        List<Riesgo> enRiesgo,
        Opciones opciones) {

    /** Indicadores de la portada. */
    public record Kpis(int ocupacionPromedio, int capacidadLibre, String capacidadLibreFte, int enBanca, int sobreAsignados,
                       int alLimite, int vacantes, int proyectosVigentes, int coberturaPct, int habilidadesConBrecha,
                       int habilidadesPedidas, int propuestasPendientes, int tasaAprobacion, int entregasATiempoPct) { }

    /** Parte de un total (donas): clave estable para color/filtro, etiqueta, cantidad. */
    public record Segmento(String clave, String etiqueta, int valor) { }

    /** Barra horizontal: etiqueta, valor principal, valor de referencia y detalle. */
    public record Barra(String etiqueta, int valor, int referencia, String detalle) { }

    /** Conclusión automática: tono (red, amber, blue, green, violet), título y detalle. */
    public record Hallazgo(String tono, String icono, String titulo, String detalle) { }

    public record Persona(Long perfilId, String nombre, String iniciales, String cargo, int carga, int libre,
                          String estado, String estadoClave, List<String> proyectos, Integer diasEnBanca,
                          String proximaLiberacion, int habilidades, int habilidadesPorValidar, String topHabilidades) {
        public int cargaBarra() { return Math.min(100, carga); }
    }

    public record ProyectoFila(Long id, String nombre, String estado, String estadoLabel, String pm, int integrantes,
                               int requeridos, int vacantes, int coberturaPct, List<String> sinCubrir, int avance,
                               String finLabel, String plazo, String plazoTono, long entregablesVencidos,
                               String riesgo, List<String> motivos) {
        public String riesgoClave() {
            return switch (riesgo) { case "Alto" -> "alto"; case "Medio" -> "medio"; default -> "bajo"; };
        }
    }

    public record Decisiones(int total, int aprobadas, int rechazadas, int pendientes, int tasaAprobacion,
                             String tiempoMedioResolucion, int scorePromedioAprobadas, List<Segmento> porOrigen,
                             List<Segmento> resultado, long iaRecomendaciones, long iaAciertos) {
        public int iaAciertoPct() { return iaRecomendaciones == 0 ? 0 : (int) Math.round(iaAciertos * 100.0 / iaRecomendaciones); }
    }

    public record PropuestaFila(String fecha, String proyecto, String candidato, String solicitante, String origen,
                                int score, int dedicacion, String estado, String resolucion, String motivo) { }

    public record Entregas(int entregables, int entregas, int aTiempo, int aTiempoPct, int revisadas,
                           int calificacionPromedioPct, int vencidosSinEntrega) { }

    public record Liberacion(String nombre, String proyecto, String fecha, long dias, int libera) { }

    public record Riesgo(String nombre, int cargaFutura) { }

    public record Opcion(String valor, String etiqueta) { }

    public record Opciones(List<String> cargos, List<Opcion> proyectos, List<Opcion> categorias) { }
}
