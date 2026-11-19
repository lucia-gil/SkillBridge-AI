package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.ReportePmFila;
import org.springframework.stereotype.Service;

/**
 * Resumen ejecutivo con IA para el Reporte de proyectos del PM (RF nuevo):
 * envuelve los datos YA calculados por ReportePmService (no vuelve a
 * consultar la base de datos ni inventa cifras) con un prompt para que
 * Claude redacte 3-5 líneas con el estado del portafolio, qué proyecto(s)
 * están en riesgo y por qué, y una recomendación. No se persiste nada: se
 * regenera cada vez que el PM lo pide, igual que el chat del Asistente IA.
 */
@Service
public class ReporteIaService {

    private static final int MAX_OUTPUT_TOKENS = 500;

    private final AsistenteIaService asistenteIaService;

    public ReporteIaService(AsistenteIaService asistenteIaService) {
        this.asistenteIaService = asistenteIaService;
    }

    public AsistenteIaService.Resultado resumirReportePm(ReportePmDatos datos) {
        String promptSistema = "Eres un asistente que resume reportes de gestion de proyectos de software "
                + "para un Project Manager, en espanol. Usa SOLO los datos que se te dan (ya estan calculados, "
                + "no inventes cifras ni nombres). Escribe un resumen ejecutivo de 3 a 5 lineas: "
                + "1) estado general del portafolio (avance, cuantos proyectos y como estan), "
                + "2) que proyecto(s) estan en riesgo y por que (cruza avance, equipo asignado/carga y fecha de fin), "
                + "si no hay ninguno en riesgo dilo brevemente y sigue, "
                + "3) una recomendacion concreta y accionable para el PM. "
                + "Tono directo y profesional, sin relleno, sin encabezados ni markdown, texto corrido.";

        if (datos.getFilas().isEmpty()) {
            return new AsistenteIaService.Resultado(
                    "No hay proyectos en el resultado actual para resumir. Ajusta los filtros e intenta de nuevo.", true);
        }

        return asistenteIaService.consultar(promptSistema, construirContexto(datos), MAX_OUTPUT_TOKENS);
    }

    private String construirContexto(ReportePmDatos datos) {
        StringBuilder sb = new StringBuilder();
        sb.append("KPIs del portafolio filtrado:\n");
        sb.append("- Total de proyectos: ").append(datos.getTotalProyectos()).append("\n");
        sb.append("- Avance promedio: ").append(datos.getAvancePromedio()).append("%\n");
        sb.append("- Asignaciones activas: ").append(datos.getAsignacionesActivas()).append("\n");
        sb.append("- Proyectos atrasados: ").append(datos.getAtrasados()).append("\n");
        sb.append("- Proyectos en riesgo alto: ").append(datos.getRiesgoAlto()).append("\n");
        sb.append("- Entregables vencidos (todo el portafolio filtrado): ").append(datos.getEntregablesVencidos()).append("\n\n");

        sb.append("Detalle por proyecto:\n");
        for (ReportePmFila f : datos.getFilas()) {
            sb.append("- \"").append(f.getProyecto()).append("\"")
              .append(" | estado: ").append(f.getEstado())
              .append(" | avance: ").append(f.getAvance()).append("%")
              .append(" | equipo activo: ").append(f.getEquipoActivo())
              .append(" | carga asignada: ").append(f.getCargaAsignada())
              .append(" | fecha fin: ").append(f.getFechaFinLabel())
              .append(" | situacion: ").append(f.getSituacion())
              .append(" | riesgo: ").append(f.getRiesgo().isEmpty() ? "N/A" : f.getRiesgo())
              .append("\n");
        }
        return sb.toString();
    }
}
