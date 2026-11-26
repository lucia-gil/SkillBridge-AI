package com.skillbridge.ai.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.skillbridge.ai.dto.CoberturaHabilidadFila;
import com.skillbridge.ai.dto.ReporteRm;
import com.skillbridge.ai.dto.ReporteRm.*;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.List;

/**
 * Exportaciones del dashboard del Resource Manager.
 *
 * <p><b>Excel</b>: hoja "Resumen" con portada, indicadores, hallazgos y 4
 * gráficos nativos (se pueden editar en Excel); hojas de detalle con
 * encabezado fijo, autofiltro, barras de datos y colores por estado; y una
 * hoja con los datos de los gráficos.</p>
 *
 * <p><b>PDF</b>: A4 horizontal, portada con indicadores y hallazgos, página
 * de panorama con donas y barras dibujadas, y tablas por sección, con pie de
 * página y "Página N de M".</p>
 */
@Service
public class RmReporteExportService {

    // Paleta (la misma del dashboard)
    static final Color NAVY = new Color(24, 44, 78);
    static final Color TINTA = new Color(29, 36, 51);
    static final Color GRIS = new Color(107, 115, 133);
    static final Color LINEA = new Color(225, 229, 236);
    static final Color FONDO = new Color(246, 248, 251);
    static final Map<String, Color> COLORES = new HashMap<>();
    static {
        // estados de carga
        COLORES.put("banca", new Color(0x8F, 0xA7, 0xD6)); COLORES.put("sub", new Color(0x9A, 0xA6, 0xB8));
        COLORES.put("opt", new Color(0x2E, 0x9A, 0x65)); COLORES.put("lim", new Color(0xD9, 0x9A, 0x2B));
        COLORES.put("sobre", new Color(0xCF, 0x4A, 0x3C));
        // estados de proyecto
        COLORES.put("planificacion", new Color(0x2A, 0x78, 0xD6)); COLORES.put("activo", new Color(0x1B, 0xAF, 0x7A));
        COLORES.put("en_pausa", new Color(0xED, 0xA1, 0x00)); COLORES.put("completado", new Color(0x62, 0x50, 0xD6));
        COLORES.put("cancelado", new Color(0xE3, 0x49, 0x48));
        // cobertura / propuestas / riesgo / tonos
        COLORES.put("cubierta", COLORES.get("opt")); COLORES.put("ajustada", COLORES.get("lim")); COLORES.put("sin", COLORES.get("sobre"));
        COLORES.put("aprobada", COLORES.get("opt")); COLORES.put("rechazada", COLORES.get("sobre")); COLORES.put("pendiente", COLORES.get("lim"));
        COLORES.put("alto", COLORES.get("sobre")); COLORES.put("medio", COLORES.get("lim")); COLORES.put("bajo", COLORES.get("opt"));
        COLORES.put("red", COLORES.get("sobre")); COLORES.put("amber", COLORES.get("lim")); COLORES.put("green", COLORES.get("opt"));
        COLORES.put("blue", new Color(0x2A, 0x78, 0xD6)); COLORES.put("violet", new Color(0x62, 0x50, 0xD6));
    }

    static Color color(String clave) { return COLORES.getOrDefault(clave, GRIS); }

    private static String claveCobertura(String estado) {
        if (CoberturaHabilidadFila.SIN_COBERTURA.equals(estado)) return "sin";
        if (CoberturaHabilidadFila.AJUSTADA.equals(estado)) return "ajustada";
        return "cubierta";
    }

    private static String clavePropuesta(String estado) {
        return switch (estado) { case "Aprobada" -> "aprobada"; case "Rechazada" -> "rechazada"; default -> "pendiente"; };
    }

    private static String etiquetaTono(String tono) {
        return switch (tono) { case "red" -> "ALERTA"; case "amber" -> "ATENCIÓN"; case "green" -> "OK"; case "violet" -> "IA"; default -> "INFO"; };
    }

    // ═════════════════════════════ EXCEL ═════════════════════════════

    public void excel(ReporteRm d, OutputStream salida) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Estilos s = new Estilos(wb);
            XSSFSheet resumen = wb.createSheet("Resumen");
            XSSFSheet col = wb.createSheet("Colaboradores");
            XSSFSheet pro = wb.createSheet("Proyectos");
            XSSFSheet hab = wb.createSheet("Habilidades");
            XSSFSheet dec = wb.createSheet("Decisiones");
            XSSFSheet prox = wb.createSheet("Próximos 30 días");
            XSSFSheet graf = wb.createSheet("Datos de gráficos");

            int filaGraficos = hojaResumen(resumen, d, s);
            hojaColaboradores(col, d, s);
            hojaProyectos(pro, d, s);
            hojaHabilidades(hab, d, s);
            hojaDecisiones(dec, d, s);
            hojaProximos(prox, d, s);
            int[] rangos = hojaDatosGraficos(graf, d, s);
            graficos(resumen, graf, d, filaGraficos, rangos);

            for (XSSFSheet h : List.of(resumen, col, pro, hab, dec, prox, graf)) {
                h.getPrintSetup().setLandscape(true);
                h.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
                h.setFitToPage(true);
                h.getPrintSetup().setFitWidth((short) 1);
                h.getPrintSetup().setFitHeight((short) 0);
                h.getFooter().setLeft("SkillBridge AI · Reporte del Resource Manager");
                h.getFooter().setRight("Página &P de &N");
            }
            wb.setActiveSheet(0);
            wb.write(salida);
        }
    }

    /** Portada: título, filtros, 12 indicadores y hallazgos. Devuelve la fila donde empiezan los gráficos. */
    private int hojaResumen(XSSFSheet h, ReporteRm d, Estilos s) {
        h.setDisplayGridlines(false);
        for (int c = 0; c < 12; c++) h.setColumnWidth(c, 13 * 256);
        Kpis k = d.kpis();
        Decisiones de = d.decisiones();

        Row r0 = h.createRow(0); r0.setHeightInPoints(40);
        celda(r0, 0, "SkillBridge AI · Reporte de capacidad y talento", s.titulo);
        for (int c = 1; c < 12; c++) celda(r0, c, "", s.titulo);
        h.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
        Row r1 = h.createRow(1); r1.setHeightInPoints(20);
        celda(r1, 0, "Resource Manager · SkillBridge AI · Generado el " + d.generado() + " · límite de carga " + d.limiteCarga() + "%", s.subtitulo);
        for (int c = 1; c < 12; c++) celda(r1, c, "", s.subtitulo);
        h.addMergedRegion(new CellRangeAddress(1, 1, 0, 11));
        Row r2 = h.createRow(2); r2.setHeightInPoints(18);
        celda(r2, 0, d.filtrosLabel(), s.filtros);
        h.addMergedRegion(new CellRangeAddress(2, 2, 0, 11));

        String[][] fila1 = {
                {"Ocupación promedio", k.ocupacionPromedio() + "%", "entre quienes tienen proyectos"},
                {"Capacidad libre", k.capacidadLibreFte() + " FTE", k.capacidadLibre() + "% sumado entre todos"},
                {"Sin asignación", String.valueOf(k.enBanca()), "personas en la banca"},
                {"Sobre-asignados", String.valueOf(k.sobreAsignados()), k.alLimite() + " más al límite"},
                {"Vacantes abiertas", String.valueOf(k.vacantes()), "en " + k.proyectosVigentes() + " proyecto(s) con demanda"},
                {"Cobertura de habilidades", k.coberturaPct() + "%", k.habilidadesConBrecha() + " con brecha de " + k.habilidadesPedidas()}};
        String[][] fila2 = {
                {"Propuestas pendientes", String.valueOf(de.pendientes()), de.total() + " propuestas en total"},
                {"Tasa de aprobación", de.tasaAprobacion() + "%", de.aprobadas() + " aprobadas · " + de.rechazadas() + " rechazadas"},
                {"Tiempo de respuesta", de.tiempoMedioResolucion() != null ? de.tiempoMedioResolucion() : "—", "promedio para resolver una propuesta"},
                {"Acierto de la IA", de.iaRecomendaciones() > 0 ? de.iaAciertoPct() + "%" : "—", de.iaAciertos() + " de " + de.iaRecomendaciones() + " recomendaciones asignadas"},
                {"Entregas a tiempo", d.entregas().entregas() > 0 ? d.entregas().aTiempoPct() + "%" : "—", d.entregas().aTiempo() + " de " + d.entregas().entregas() + " entregas"},
                {"Revisiones completadas", d.entregas().revisadas() > 0 ? d.entregas().completadasPct() + "%" : "—", d.entregas().revisadas() + " entregas revisadas"}};
        tarjetas(h, 4, fila1, s);
        tarjetas(h, 8, fila2, s);

        int r = 12;
        Row sec = h.createRow(r++); sec.setHeightInPoints(22);
        celda(sec, 0, "Hallazgos clave", s.seccion);
        for (Hallazgo x : d.hallazgos()) {
            Row fila = h.createRow(r); fila.setHeightInPoints(38);
            celda(fila, 0, etiquetaTono(x.tono()), s.etiquetaTono(x.tono()));
            XSSFRichTextString texto = new XSSFRichTextString(x.titulo() + "\n" + x.detalle());
            texto.applyFont(0, x.titulo().length(), s.fuenteNegrita);
            Cell c = fila.createCell(1);
            c.setCellValue(texto);
            c.setCellStyle(s.hallazgo);
            for (int i = 2; i < 12; i++) celda(fila, i, "", s.hallazgo);
            h.addMergedRegion(new CellRangeAddress(r, r, 1, 11));
            r++;
        }
        Row g = h.createRow(r + 1); g.setHeightInPoints(22);
        celda(g, 0, "Gráficos (editables en Excel)", s.seccion);
        return r + 2;
    }

    private void tarjetas(XSSFSheet h, int fila, String[][] datos, Estilos s) {
        Row a = h.createRow(fila), b = h.createRow(fila + 1), c = h.createRow(fila + 2);
        a.setHeightInPoints(20); b.setHeightInPoints(32); c.setHeightInPoints(18);
        for (int i = 0; i < datos.length; i++) {
            int col = i * 2;
            celda(a, col, datos[i][0], s.kpiLabel); celda(a, col + 1, "", s.kpiLabel);
            celda(b, col, datos[i][1], s.kpiValor); celda(b, col + 1, "", s.kpiValor);
            celda(c, col, datos[i][2], s.kpiMeta); celda(c, col + 1, "", s.kpiMeta);
            for (int f = fila; f <= fila + 2; f++) h.addMergedRegion(new CellRangeAddress(f, f, col, col + 1));
        }
    }

    private void hojaColaboradores(XSSFSheet h, ReporteRm d, Estilos s) {
        String[] cab = {"Colaborador", "Cargo", "Ocupación", "Libre", "Estado", "Proyectos", "Detalle de proyectos",
                "Días sin proyecto", "Próxima liberación", "Habilidades", "Por validar", "Mejores habilidades"};
        int[] anchos = {24, 22, 12, 10, 16, 11, 40, 15, 17, 12, 12, 42};
        encabezado(h, cab, anchos, s);
        int r = 1;
        for (Persona p : d.personas()) {
            Row f = h.createRow(r);
            boolean z = r % 2 == 0;
            celda(f, 0, p.nombre(), s.texto(z, true)); celda(f, 1, p.cargo(), s.texto(z, false));
            numero(f, 2, p.carga() / 100.0, s.porcentaje(z)); numero(f, 3, p.libre() / 100.0, s.porcentaje(z));
            celda(f, 4, p.estado(), s.chip(p.estadoClave()));
            numero(f, 5, p.proyectos().size(), s.entero(z));
            celda(f, 6, String.join(" · ", p.proyectos()), s.texto(z, false));
            if (p.diasEnBanca() != null) numero(f, 7, p.diasEnBanca(), s.entero(z)); else celda(f, 7, "", s.texto(z, false));
            celda(f, 8, p.proximaLiberacion() != null ? p.proximaLiberacion() : "", s.texto(z, false));
            numero(f, 9, p.habilidades(), s.entero(z)); numero(f, 10, p.habilidadesPorValidar(), s.entero(z));
            celda(f, 11, p.topHabilidades(), s.texto(z, false));
            r++;
        }
        cerrarTabla(h, r, cab.length, "Nadie coincide con estos filtros.", s);
        barras(h, 2, r, new Color(0x2A, 0x78, 0xD6));
    }

    private void hojaProyectos(XSSFSheet h, ReporteRm d, Estilos s) {
        String[] cab = {"Proyecto", "Estado", "Project Manager", "Integrantes", "Requeridos", "Vacantes", "Cobertura de habilidades",
                "Habilidades sin cubrir", "Avance", "Fin estimado", "Plazo", "Entregables vencidos", "Riesgo", "Motivos del riesgo"};
        int[] anchos = {28, 14, 20, 12, 12, 10, 14, 30, 10, 14, 20, 12, 10, 50};
        encabezado(h, cab, anchos, s);
        int r = 1;
        for (ProyectoFila p : d.proyectos()) {
            Row f = h.createRow(r);
            boolean z = r % 2 == 0;
            celda(f, 0, p.nombre(), s.texto(z, true));
            celda(f, 1, p.estadoLabel(), s.chip(p.estado()));
            celda(f, 2, p.pm(), s.texto(z, false));
            numero(f, 3, p.integrantes(), s.entero(z)); numero(f, 4, p.requeridos(), s.entero(z)); numero(f, 5, p.vacantes(), s.entero(z));
            numero(f, 6, p.coberturaPct() / 100.0, s.porcentaje(z));
            celda(f, 7, String.join(", ", p.sinCubrir()), s.texto(z, false));
            numero(f, 8, p.avance() / 100.0, s.porcentaje(z));
            celda(f, 9, p.finLabel(), s.texto(z, false)); celda(f, 10, p.plazo(), s.texto(z, false));
            numero(f, 11, p.entregablesVencidos(), s.entero(z));
            celda(f, 12, p.riesgo(), s.chip(p.riesgoClave()));
            celda(f, 13, String.join(" · ", p.motivos()), s.texto(z, false));
            r++;
        }
        cerrarTabla(h, r, cab.length, "No hay proyectos vigentes con estos filtros.", s);
        barras(h, 6, r, COLORES.get("opt"));
        barras(h, 8, r, new Color(0x2A, 0x78, 0xD6));
    }

    private void hojaHabilidades(XSSFSheet h, ReporteRm d, Estilos s) {
        String[] cab = {"Habilidad", "Categoría", "Proyectos que la piden", "Proyectos", "Nivel pedido", "Personas con ese nivel",
                "Con capacidad libre", "Brecha", "Cobertura"};
        int[] anchos = {20, 18, 14, 44, 13, 14, 14, 10, 15};
        encabezado(h, cab, anchos, s);
        int r = 1;
        for (CoberturaHabilidadFila c : d.habilidades()) {
            Row f = h.createRow(r);
            boolean z = r % 2 == 0;
            celda(f, 0, c.nombre(), s.texto(z, true)); celda(f, 1, c.categoria(), s.texto(z, false));
            numero(f, 2, c.proyectos(), s.entero(z)); celda(f, 3, String.join(", ", c.proyectosNombres()), s.texto(z, false));
            celda(f, 4, c.nivelLabel(), s.texto(z, false));
            numero(f, 5, c.calificadas(), s.entero(z)); numero(f, 6, c.disponibles(), s.entero(z));
            numero(f, 7, Math.max(0, c.proyectos() - c.disponibles()), s.entero(z));
            celda(f, 8, c.estado(), s.chip(claveCobertura(c.estado())));
            r++;
        }
        cerrarTabla(h, r, cab.length, "No hay habilidades pedidas con estos filtros.", s);
    }

    private void hojaDecisiones(XSSFSheet h, ReporteRm d, Estilos s) {
        Decisiones de = d.decisiones();
        String[] cab = {"Fecha", "Proyecto", "Candidato", "Solicitado por", "Origen", "Puntaje", "Dedicación", "Estado", "Resuelta", "Comentario"};
        int[] anchos = {12, 26, 22, 22, 20, 10, 12, 12, 12, 46};
        for (int i = 0; i < anchos.length; i++) h.setColumnWidth(i, anchos[i] * 256);
        String[][] resumen = {{"Propuestas", String.valueOf(de.total())}, {"Aprobadas", String.valueOf(de.aprobadas())},
                {"Rechazadas", String.valueOf(de.rechazadas())}, {"Pendientes", String.valueOf(de.pendientes())},
                {"Tasa de aprobación", de.tasaAprobacion() + "%"}, {"Tiempo medio de resolución", Optional.ofNullable(de.tiempoMedioResolucion()).orElse("—")},
                {"Puntaje promedio de las aprobadas", de.scorePromedioAprobadas() + " / 100"},
                {"Recomendaciones de la IA asignadas", de.iaAciertos() + " de " + de.iaRecomendaciones() + " (" + de.iaAciertoPct() + "%)"}};
        Row t = h.createRow(0); t.setHeightInPoints(22); celda(t, 0, "Decisiones de asignación", s.seccion);
        int r = 1;
        for (String[] x : resumen) {
            Row f = h.createRow(r++);
            celda(f, 0, x[0], s.kpiLabelIzq); celda(f, 1, "", s.kpiLabelIzq); celda(f, 2, x[1], s.valorIzq);
            h.addMergedRegion(new CellRangeAddress(f.getRowNum(), f.getRowNum(), 0, 1));
        }
        r++;
        int inicio = r;
        Row hr = h.createRow(r++); hr.setHeightInPoints(28);
        for (int i = 0; i < cab.length; i++) celda(hr, i, cab[i], s.cabecera);
        for (PropuestaFila p : d.propuestas()) {
            Row f = h.createRow(r);
            boolean z = r % 2 == 0;
            celda(f, 0, p.fecha(), s.texto(z, false)); celda(f, 1, p.proyecto(), s.texto(z, true)); celda(f, 2, p.candidato(), s.texto(z, false));
            celda(f, 3, p.solicitante(), s.texto(z, false)); celda(f, 4, p.origen(), s.texto(z, false));
            numero(f, 5, p.score(), s.entero(z)); numero(f, 6, p.dedicacion() / 100.0, s.porcentaje(z));
            celda(f, 7, p.estado(), s.chip(clavePropuesta(p.estado()))); celda(f, 8, p.resolucion(), s.texto(z, false));
            celda(f, 9, p.motivo(), s.texto(z, false));
            r++;
        }
        if (!d.propuestas().isEmpty()) h.setAutoFilter(new CellRangeAddress(inicio, r - 1, 0, cab.length - 1));
        h.createFreezePane(0, inicio + 1);
    }

    private void hojaProximos(XSSFSheet h, ReporteRm d, Estilos s) {
        int[] anchos = {26, 30, 14, 14, 18};
        for (int i = 0; i < anchos.length; i++) h.setColumnWidth(i, anchos[i] * 256);
        Row t = h.createRow(0); t.setHeightInPoints(22); celda(t, 0, "Liberaciones en los próximos 30 días", s.seccion);
        Row hr = h.createRow(1); hr.setHeightInPoints(26);
        String[] cab = {"Colaborador", "Proyecto que deja", "Fecha", "Días restantes", "Dedicación que libera"};
        for (int i = 0; i < cab.length; i++) celda(hr, i, cab[i], s.cabecera);
        int r = 2;
        for (Liberacion l : d.liberaciones()) {
            Row f = h.createRow(r); boolean z = r % 2 == 0;
            celda(f, 0, l.nombre(), s.texto(z, true)); celda(f, 1, l.proyecto(), s.texto(z, false)); celda(f, 2, l.fecha(), s.texto(z, false));
            numero(f, 3, l.dias(), s.entero(z)); numero(f, 4, l.libera() / 100.0, s.porcentaje(z));
            r++;
        }
        if (d.liberaciones().isEmpty()) { celda(h.createRow(r++), 0, "Nadie se libera en los próximos 30 días.", s.vacio); }
        r += 2;
        Row t2 = h.createRow(r++); t2.setHeightInPoints(22); celda(t2, 0, "Riesgo de sobrecarga (asignaciones ya programadas)", s.seccion);
        Row hr2 = h.createRow(r++); hr2.setHeightInPoints(26);
        celda(hr2, 0, "Colaborador", s.cabecera); celda(hr2, 1, "Carga prevista", s.cabecera);
        for (Riesgo x : d.enRiesgo()) {
            Row f = h.createRow(r); boolean z = r % 2 == 0;
            celda(f, 0, x.nombre(), s.texto(z, true)); numero(f, 1, x.cargaFutura() / 100.0, s.porcentaje(z));
            r++;
        }
        if (d.enRiesgo().isEmpty()) celda(h.createRow(r), 0, "Nadie superaría el límite con lo programado.", s.vacio);
    }

    /** Datos que alimentan los gráficos. Devuelve [filaIni, filaFin] de cada bloque (4 bloques). */
    private int[] hojaDatosGraficos(XSSFSheet h, ReporteRm d, Estilos s) {
        h.setColumnWidth(0, 26 * 256); h.setColumnWidth(1, 16 * 256); h.setColumnWidth(2, 16 * 256);
        int[] rangos = new int[8];
        int r = 0;
        r = bloque(h, r, "Estado de carga", new String[]{"Estado", "Personas"},
                d.distribucionCarga().stream().map(x -> new Object[]{x.etiqueta(), x.valor()}).toList(), s, rangos, 0);
        r = bloque(h, r, "Proyectos por estado", new String[]{"Estado", "Proyectos"},
                d.estadosProyecto().stream().map(x -> new Object[]{x.etiqueta(), x.valor()}).toList(), s, rangos, 2);
        r = bloque(h, r, "Brechas de habilidades", new String[]{"Habilidad", "Proyectos que la piden", "Con capacidad libre"},
                d.brechas().stream().map(x -> new Object[]{x.etiqueta(), x.valor(), x.referencia()}).toList(), s, rangos, 4);
        bloque(h, r, "Carga promedio por cargo", new String[]{"Cargo", "Carga promedio", "Personas"},
                d.cargaPorCargo().stream().map(x -> new Object[]{x.etiqueta(), x.valor() / 100.0, x.referencia()}).toList(), s, rangos, 6);
        return rangos;
    }

    private int bloque(XSSFSheet h, int r, String titulo, String[] cab, List<Object[]> filas, Estilos s, int[] rangos, int pos) {
        celda(h.createRow(r++), 0, titulo, s.seccion);
        Row hr = h.createRow(r++);
        for (int i = 0; i < cab.length; i++) celda(hr, i, cab[i], s.cabecera);
        rangos[pos] = r;
        for (Object[] f : filas) {
            Row row = h.createRow(r++);
            for (int i = 0; i < f.length; i++) {
                if (f[i] instanceof Number n) numero(row, i, n.doubleValue(), n instanceof Double ? s.porcentaje(false) : s.entero(false));
                else celda(row, i, String.valueOf(f[i]), s.texto(false, i == 0));
            }
        }
        rangos[pos + 1] = Math.max(rangos[pos], r - 1);
        return r + 1;
    }

    private void graficos(XSSFSheet resumen, XSSFSheet datos, ReporteRm d, int fila, int[] rg) {
        XSSFDrawing dibujo = resumen.createDrawingPatriarch();
        dona(dibujo, datos, "Estado de carga del equipo", rg[0], rg[1], d.distribucionCarga(), 0, fila, 6, fila + 18);
        barrasDobles(dibujo, datos, "Brechas de habilidades", rg[4], rg[5], 6, fila, 12, fila + 18,
                d.brechas().isEmpty());
        dona(dibujo, datos, "Proyectos por estado", rg[2], rg[3], d.estadosProyecto(), 0, fila + 19, 6, fila + 37);
        barraSimple(dibujo, datos, "Carga promedio por cargo", rg[6], rg[7], 6, fila + 19, 12, fila + 37, d.cargaPorCargo().isEmpty());
    }

    private void dona(XSSFDrawing dibujo, XSSFSheet datos, String titulo, int ini, int fin, List<Segmento> segs,
                      int c1, int r1, int c2, int r2) {
        XSSFChart chart = dibujo.createChart(dibujo.createAnchor(0, 0, 0, 0, c1, r1, c2, r2));
        chart.setTitleText(titulo);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.RIGHT);
        XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(datos, new CellRangeAddress(ini, fin, 0, 0));
        XDDFNumericalDataSource<Double> val = XDDFDataSourcesFactory.fromNumericCellRange(datos, new CellRangeAddress(ini, fin, 1, 1));
        XDDFDoughnutChartData data = (XDDFDoughnutChartData) chart.createData(ChartTypes.DOUGHNUT, null, null);
        data.setVaryColors(true);
        XDDFChartData.Series serie = data.addSeries(cat, val);
        serie.setTitle(titulo, null);
        for (int i = 0; i < segs.size(); i++) {
            serie.getDataPoint(i).setFillProperties(new XDDFSolidFillProperties(xddf(color(segs.get(i).clave()))));
        }
        chart.plot(data);
        tituloPequeno(chart);
        etiquetasDona(chart);
        huecoDona(chart, 55);
    }

    /**
     * Tamaño del hueco de la dona. Se escribe directo en el XML porque
     * poi-ooxml-lite no trae la clase de ese tipo del esquema.
     */
    private void huecoDona(XSSFChart chart, int porcentaje) {
        var dona = chart.getCTChart().getPlotArea().getDoughnutChartArray(0);
        try (org.apache.xmlbeans.XmlCursor cur = dona.newCursor()) {
            cur.toEndToken();
            cur.beginElement(new javax.xml.namespace.QName("http://schemas.openxmlformats.org/drawingml/2006/chart", "holeSize", "c"));
            cur.insertAttributeWithValue("val", String.valueOf(porcentaje));
        }
    }

    /** Porcentaje dentro de cada porción de la dona. */
    private void etiquetasDona(XSSFChart chart) {
        var dLbls = chart.getCTChart().getPlotArea().getDoughnutChartArray(0).getSerArray(0).addNewDLbls();
        dLbls.addNewShowPercent().setVal(true);
        dLbls.addNewShowVal().setVal(false);
        dLbls.addNewShowCatName().setVal(false);
        dLbls.addNewShowSerName().setVal(false);
        dLbls.addNewShowLegendKey().setVal(false);
        dLbls.addNewShowLeaderLines().setVal(false);
    }

    private void barrasDobles(XSSFDrawing dibujo, XSSFSheet datos, String titulo, int ini, int fin, int c1, int r1, int c2, int r2, boolean vacio) {
        XSSFChart chart = dibujo.createChart(dibujo.createAnchor(0, 0, 0, 0, c1, r1, c2, r2));
        chart.setTitleText(vacio ? titulo + " (sin brechas)" : titulo);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        XDDFCategoryAxis x = chart.createCategoryAxis(AxisPosition.LEFT);
        x.setOrientation(AxisOrientation.MAX_MIN);
        XDDFValueAxis y = chart.createValueAxis(AxisPosition.BOTTOM);
        y.setCrosses(AxisCrosses.AUTO_ZERO);
        y.setMajorUnit(1);
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, x, y);
        data.setBarDirection(BarDirection.BAR);
        data.setGapWidth(60);
        XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(datos, new CellRangeAddress(ini, fin, 0, 0));
        XDDFChartData.Series s1 = data.addSeries(cat, XDDFDataSourcesFactory.fromNumericCellRange(datos, new CellRangeAddress(ini, fin, 1, 1)));
        s1.setTitle("Proyectos que la piden", null);
        s1.setFillProperties(new XDDFSolidFillProperties(xddf(COLORES.get("sobre"))));
        XDDFChartData.Series s2 = data.addSeries(cat, XDDFDataSourcesFactory.fromNumericCellRange(datos, new CellRangeAddress(ini, fin, 2, 2)));
        s2.setTitle("Con capacidad libre", null);
        s2.setFillProperties(new XDDFSolidFillProperties(xddf(NAVY)));
        chart.plot(data);
        tituloPequeno(chart);
    }

    private void barraSimple(XSSFDrawing dibujo, XSSFSheet datos, String titulo, int ini, int fin, int c1, int r1, int c2, int r2, boolean vacio) {
        XSSFChart chart = dibujo.createChart(dibujo.createAnchor(0, 0, 0, 0, c1, r1, c2, r2));
        chart.setTitleText(vacio ? titulo + " (sin datos)" : titulo);
        chart.setTitleOverlay(false);
        XDDFCategoryAxis x = chart.createCategoryAxis(AxisPosition.LEFT);
        x.setOrientation(AxisOrientation.MAX_MIN);
        XDDFValueAxis y = chart.createValueAxis(AxisPosition.BOTTOM);
        y.setCrosses(AxisCrosses.AUTO_ZERO);
        y.setMinimum(0);
        y.setNumberFormat("0%");
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, x, y);
        data.setBarDirection(BarDirection.BAR);
        data.setGapWidth(60);
        XDDFChartData.Series s1 = data.addSeries(
                XDDFDataSourcesFactory.fromStringCellRange(datos, new CellRangeAddress(ini, fin, 0, 0)),
                XDDFDataSourcesFactory.fromNumericCellRange(datos, new CellRangeAddress(ini, fin, 1, 1)));
        s1.setTitle("Carga promedio", null);
        s1.setFillProperties(new XDDFSolidFillProperties(xddf(new Color(0x2A, 0x78, 0xD6))));
        chart.plot(data);
        tituloPequeno(chart);
    }

    /** Título del gráfico en 12 pt negrita (el predeterminado es demasiado grande). */
    private static void tituloPequeno(XSSFChart chart) {
        try {
            var p = chart.getCTChart().getTitle().getTx().getRich().getPArray(0);
            for (var r : p.getRArray()) {
                var rpr = r.isSetRPr() ? r.getRPr() : r.addNewRPr();
                rpr.setSz(1200);
                rpr.setB(true);
            }
        } catch (Exception ignorado) {
            // si la estructura cambia, se deja el tamaño por defecto
        }
    }

    private static XDDFColor xddf(Color c) {
        return XDDFColor.from(new byte[]{(byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue()});
    }

    private void encabezado(XSSFSheet h, String[] cab, int[] anchos, Estilos s) {
        Row hr = h.createRow(0);
        hr.setHeightInPoints(30);
        for (int i = 0; i < cab.length; i++) {
            celda(hr, i, cab[i], s.cabecera);
            h.setColumnWidth(i, anchos[i] * 256);
        }
        h.createFreezePane(1, 1);
    }

    private void cerrarTabla(XSSFSheet h, int filas, int columnas, String vacio, Estilos s) {
        if (filas == 1) { celda(h.createRow(1), 0, vacio, s.vacio); return; }
        h.setAutoFilter(new CellRangeAddress(0, filas - 1, 0, columnas - 1));
    }

    /** Barras de datos (formato condicional) en una columna de porcentajes. */
    private void barras(XSSFSheet h, int columna, int filas, Color c) {
        if (filas <= 1) return;
        XSSFSheetConditionalFormatting cf = h.getSheetConditionalFormatting();
        XSSFConditionalFormattingRule regla = cf.createConditionalFormattingRule(new XSSFColor(new byte[]{(byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue()}, null));
        var db = regla.getDataBarFormatting();
        db.getMinThreshold().setRangeType(org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType.NUMBER);
        db.getMinThreshold().setValue(0d);
        db.getMaxThreshold().setRangeType(org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType.NUMBER);
        db.getMaxThreshold().setValue(1d);
        cf.addConditionalFormatting(new CellRangeAddress[]{new CellRangeAddress(1, filas - 1, columna, columna)}, regla);
    }

    private static void celda(Row r, int c, String v, XSSFCellStyle estilo) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v == null ? "" : v);
        cell.setCellStyle(estilo);
    }

    private static void numero(Row r, int c, double v, XSSFCellStyle estilo) {
        Cell cell = r.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(estilo);
    }

    /** Estilos reutilizados (Excel limita la cantidad de estilos por libro). */
    private static final class Estilos {
        final XSSFWorkbook wb;
        final XSSFCellStyle titulo, subtitulo, filtros, seccion, kpiLabel, kpiValor, kpiMeta, kpiLabelIzq, valorIzq, cabecera, hallazgo, vacio;
        final XSSFFont fuenteNegrita;
        private final Map<String, XSSFCellStyle> cache = new HashMap<>();

        Estilos(XSSFWorkbook wb) {
            this.wb = wb;
            titulo = estilo(NAVY, fuente(20, true, Color.WHITE), HorizontalAlignment.LEFT);
            titulo.setIndention((short) 1);
            subtitulo = estilo(NAVY, fuente(10, false, new Color(200, 212, 235)), HorizontalAlignment.LEFT);
            subtitulo.setIndention((short) 1);
            filtros = estilo(null, fuenteItalica(), HorizontalAlignment.LEFT);
            seccion = estilo(null, fuente(13, true, NAVY), HorizontalAlignment.LEFT);
            kpiLabel = borde(estilo(FONDO, fuente(9, true, GRIS), HorizontalAlignment.LEFT));
            kpiLabel.setIndention((short) 1);
            kpiValor = borde(estilo(FONDO, fuente(20, true, NAVY), HorizontalAlignment.LEFT));
            kpiValor.setIndention((short) 1);
            kpiMeta = borde(estilo(FONDO, fuente(8, false, GRIS), HorizontalAlignment.LEFT));
            kpiMeta.setIndention((short) 1);
            kpiLabelIzq = borde(estilo(FONDO, fuente(10, true, TINTA), HorizontalAlignment.LEFT));
            valorIzq = borde(estilo(null, fuente(11, true, NAVY), HorizontalAlignment.LEFT));
            cabecera = estilo(NAVY, fuente(10, true, Color.WHITE), HorizontalAlignment.CENTER);
            cabecera.setWrapText(true);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            hallazgo = borde(estilo(Color.WHITE, fuente(10, false, TINTA), HorizontalAlignment.LEFT));
            hallazgo.setWrapText(true);
            hallazgo.setVerticalAlignment(VerticalAlignment.CENTER);
            hallazgo.setIndention((short) 1);
            vacio = estilo(null, fuenteItalica(), HorizontalAlignment.LEFT);
            fuenteNegrita = fuente(10, true, NAVY);
        }

        XSSFCellStyle texto(boolean zebra, boolean negrita) {
            return cache.computeIfAbsent("t" + zebra + negrita, k -> {
                XSSFCellStyle e = borde(estilo(zebra ? FONDO : Color.WHITE, fuente(10, negrita, TINTA), HorizontalAlignment.LEFT));
                e.setVerticalAlignment(VerticalAlignment.CENTER);
                e.setWrapText(true);
                return e;
            });
        }

        XSSFCellStyle porcentaje(boolean zebra) {
            return cache.computeIfAbsent("p" + zebra, k -> {
                XSSFCellStyle e = borde(estilo(zebra ? FONDO : Color.WHITE, fuente(10, false, TINTA), HorizontalAlignment.RIGHT));
                e.setDataFormat(wb.createDataFormat().getFormat("0%"));
                e.setVerticalAlignment(VerticalAlignment.CENTER);
                return e;
            });
        }

        XSSFCellStyle entero(boolean zebra) {
            return cache.computeIfAbsent("e" + zebra, k -> {
                XSSFCellStyle e = borde(estilo(zebra ? FONDO : Color.WHITE, fuente(10, false, TINTA), HorizontalAlignment.RIGHT));
                e.setDataFormat(wb.createDataFormat().getFormat("0"));
                e.setVerticalAlignment(VerticalAlignment.CENTER);
                return e;
            });
        }

        /** Celda con fondo suave del color del estado y texto oscuro del mismo tono. */
        XSSFCellStyle chip(String clave) {
            return cache.computeIfAbsent("c" + clave, k -> {
                Color c = color(clave);
                Color fondo = new Color(mezcla(c.getRed()), mezcla(c.getGreen()), mezcla(c.getBlue()));
                Color texto = new Color((int) (c.getRed() * .62), (int) (c.getGreen() * .62), (int) (c.getBlue() * .62));
                XSSFCellStyle e = borde(estilo(fondo, fuente(10, true, texto), HorizontalAlignment.CENTER));
                e.setVerticalAlignment(VerticalAlignment.CENTER);
                return e;
            });
        }

        XSSFCellStyle etiquetaTono(String tono) {
            return cache.computeIfAbsent("h" + tono, k -> {
                XSSFCellStyle e = borde(estilo(color(tono), fuente(9, true, Color.WHITE), HorizontalAlignment.CENTER));
                e.setVerticalAlignment(VerticalAlignment.CENTER);
                return e;
            });
        }

        private static int mezcla(int v) { return (int) (v * .18 + 255 * .82); }

        private XSSFCellStyle estilo(Color fondo, XSSFFont f, HorizontalAlignment al) {
            XSSFCellStyle e = wb.createCellStyle();
            e.setFont(f);
            e.setAlignment(al);
            e.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fondo != null) {
                e.setFillForegroundColor(new XSSFColor(new byte[]{(byte) fondo.getRed(), (byte) fondo.getGreen(), (byte) fondo.getBlue()}, null));
                e.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return e;
        }

        private XSSFCellStyle borde(XSSFCellStyle e) {
            XSSFColor c = new XSSFColor(new byte[]{(byte) LINEA.getRed(), (byte) LINEA.getGreen(), (byte) LINEA.getBlue()}, null);
            e.setBorderBottom(BorderStyle.THIN); e.setBottomBorderColor(c);
            e.setBorderTop(BorderStyle.THIN); e.setTopBorderColor(c);
            e.setBorderLeft(BorderStyle.THIN); e.setLeftBorderColor(c);
            e.setBorderRight(BorderStyle.THIN); e.setRightBorderColor(c);
            return e;
        }

        private XSSFFont fuente(int puntos, boolean negrita, Color c) {
            XSSFFont f = wb.createFont();
            f.setFontName("Calibri");
            f.setFontHeightInPoints((short) puntos);
            f.setBold(negrita);
            f.setColor(new XSSFColor(new byte[]{(byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue()}, null));
            return f;
        }

        private XSSFFont fuenteItalica() {
            XSSFFont f = fuente(10, false, GRIS);
            f.setItalic(true);
            return f;
        }
    }

    // ═════════════════════════════ PDF ═════════════════════════════

    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.WHITE);
    private static final Font F_SUB = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(200, 212, 235));
    private static final Font F_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, NAVY);
    private static final Font F_SECCION_SUB = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, GRIS);
    private static final Font F_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, GRIS);
    private static final Font F_VALOR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, NAVY);
    private static final Font F_META = FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS);
    private static final Font F_CAB = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
    private static final Font F_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TINTA);
    private static final Font F_CELDA_B = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, TINTA);
    private static final Font F_H_TIT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TINTA);
    private static final Font F_H_DET = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(70, 78, 96));

    public void pdf(ReporteRm d, OutputStream salida) throws IOException {
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 38);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PiePagina(d.generado()));
            doc.addTitle("SkillBridge AI · Reporte de capacidad y talento");
            doc.addAuthor("SkillBridge AI");
            doc.open();

            // ── Portada ──
            PdfPTable banda = new PdfPTable(1);
            banda.setWidthPercentage(100);
            PdfPCell bc = new PdfPCell();
            bc.setBackgroundColor(NAVY);
            bc.setBorder(Rectangle.NO_BORDER);
            bc.setPadding(18);
            bc.setPaddingBottom(20);
            Paragraph eyebrow = new Paragraph("RESOURCE MANAGER  ·  REPORTE EJECUTIVO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(140, 175, 255)));
            bc.addElement(eyebrow);
            Paragraph tit = new Paragraph("Capacidad y talento del equipo", F_TITULO);
            tit.setSpacingBefore(4);
            bc.addElement(tit);
            Paragraph sub = new Paragraph("SkillBridge AI · Generado el " + d.generado() + " · Límite de carga " + d.limiteCarga() + "%", F_SUB);
            sub.setSpacingBefore(4);
            bc.addElement(sub);
            bc.addElement(new Paragraph(d.filtrosLabel(), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(170, 186, 214))));
            banda.addCell(bc);
            doc.add(banda);

            Kpis k = d.kpis();
            Decisiones de = d.decisiones();
            PdfPTable kpis = new PdfPTable(6);
            kpis.setWidthPercentage(100);
            kpis.setSpacingBefore(12);
            kpi(kpis, "OCUPACIÓN PROMEDIO", k.ocupacionPromedio() + "%", "entre quienes tienen proyectos", "blue");
            kpi(kpis, "CAPACIDAD LIBRE", k.capacidadLibreFte() + " FTE", k.capacidadLibre() + "% sumado", "violet");
            kpi(kpis, "SIN ASIGNACIÓN", String.valueOf(k.enBanca()), "personas en la banca", "banca");
            kpi(kpis, "SOBRE-ASIGNADOS", String.valueOf(k.sobreAsignados()), k.alLimite() + " más al límite", k.sobreAsignados() > 0 ? "red" : "green");
            kpi(kpis, "VACANTES ABIERTAS", String.valueOf(k.vacantes()), "en " + k.proyectosVigentes() + " proyecto(s)", k.vacantes() > 0 ? "amber" : "green");
            kpi(kpis, "COBERTURA DE HABILIDADES", k.coberturaPct() + "%", k.habilidadesConBrecha() + " con brecha de " + k.habilidadesPedidas(), k.habilidadesConBrecha() > 0 ? "amber" : "green");
            kpi(kpis, "PROPUESTAS PENDIENTES", String.valueOf(de.pendientes()), de.total() + " en total", "violet");
            kpi(kpis, "TASA DE APROBACIÓN", de.tasaAprobacion() + "%", de.aprobadas() + " aprobadas · " + de.rechazadas() + " rechazadas", "green");
            kpi(kpis, "TIEMPO DE RESPUESTA", Optional.ofNullable(de.tiempoMedioResolucion()).orElse("—"), "promedio por propuesta", "blue");
            kpi(kpis, "ACIERTO DE LA IA", de.iaRecomendaciones() > 0 ? de.iaAciertoPct() + "%" : "—", de.iaAciertos() + " de " + de.iaRecomendaciones() + " asignadas", "violet");
            kpi(kpis, "ENTREGAS A TIEMPO", d.entregas().entregas() > 0 ? d.entregas().aTiempoPct() + "%" : "—", d.entregas().aTiempo() + " de " + d.entregas().entregas(), "green");
            kpi(kpis, "COMPLETADAS", d.entregas().revisadas() > 0 ? d.entregas().completadasPct() + "%" : "—", d.entregas().revisadas() + " revisadas", "blue");
            doc.add(kpis);

            seccion(doc, "Hallazgos clave", "Conclusiones calculadas con los datos actuales: por dónde empezar.");
            PdfPTable hall = new PdfPTable(2);
            hall.setWidthPercentage(100);
            for (Hallazgo x : d.hallazgos()) {
                PdfPCell c = new PdfPCell();
                c.setBorder(Rectangle.LEFT | Rectangle.BOTTOM);
                c.setBorderWidthLeft(3.5f);
                c.setBorderColorLeft(color(x.tono()));
                c.setBorderColorBottom(Color.WHITE);
                c.setBorderWidthBottom(5);
                c.setBackgroundColor(FONDO);
                c.setPadding(9);
                Paragraph t = new Paragraph();
                t.add(new Chunk(etiquetaTono(x.tono()) + "   ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.5f, color(x.tono()))));
                t.add(new Chunk(x.titulo(), F_H_TIT));
                c.addElement(t);
                Paragraph det = new Paragraph(x.detalle(), F_H_DET);
                det.setSpacingBefore(2);
                c.addElement(det);
                hall.addCell(c);
            }
            hall.completeRow();
            doc.add(hall);

            // ── Panorama con gráficos ──
            doc.newPage();
            seccion(doc, "Panorama", "Cómo se reparte el equipo, el portafolio y la cobertura de habilidades.");
            PdfContentByte cb = writer.getDirectContent();
            PdfPTable donas = new PdfPTable(3);
            donas.setWidthPercentage(100);
            donas.addCell(celdaGrafico("Estado de carga del equipo", dona(cb, d.distribucionCarga(), "personas")));
            donas.addCell(celdaGrafico("Proyectos por estado", dona(cb, d.estadosProyecto(), "proyectos")));
            donas.addCell(celdaGrafico("Cobertura de habilidades pedidas", dona(cb, d.coberturaHabilidades(), "habilidades")));
            doc.add(donas);
            PdfPTable bars = new PdfPTable(2);
            bars.setWidthPercentage(100);
            bars.setSpacingBefore(10);
            bars.addCell(celdaGrafico("Brechas: proyectos que la piden vs. personas con capacidad", barrasBrecha(cb, d.brechas())));
            bars.addCell(celdaGrafico("Carga promedio por cargo (línea = límite " + d.limiteCarga() + "%)", barrasCarga(cb, d.cargaPorCargo(), d.limiteCarga())));
            doc.add(bars);

            // ── Tablas ──
            doc.newPage();
            seccion(doc, "Ocupación por colaborador", d.personas().size() + " de " + d.personasEvaluadas() + " personas · ordenadas de mayor a menor carga");
            PdfPTable tp = tabla(new float[]{2.1f, 1.9f, 1.7f, .7f, 1.3f, 3.4f, 1.1f, 1.2f, 2.6f},
                    "Colaborador", "Cargo", "Ocupación", "Libre", "Estado", "Proyectos", "Días sin proyecto", "Se libera", "Mejores habilidades");
            for (Persona p : d.personas()) {
                fila(tp, p.nombre(), true); fila(tp, p.cargo(), false);
                tp.addCell(barraCelda(cb, p.carga(), color(p.estadoClave()), d.limiteCarga()));
                fila(tp, p.libre() + "%", false); tp.addCell(chip(p.estado(), p.estadoClave()));
                fila(tp, p.proyectos().isEmpty() ? "—" : String.join(" · ", p.proyectos()), false);
                fila(tp, p.diasEnBanca() != null ? p.diasEnBanca() + " días" : "", false);
                fila(tp, Optional.ofNullable(p.proximaLiberacion()).orElse(""), false);
                fila(tp, p.topHabilidades(), false);
            }
            vacia(tp, d.personas().isEmpty(), 9, "Nadie coincide con estos filtros.");
            doc.add(tp);

            seccion(doc, "Proyectos vigentes", "Ordenados por riesgo: plazo, habilidades sin cubrir, vacantes y entregables vencidos.");
            PdfPTable tpr = tabla(new float[]{2.1f, 1.2f, 1.5f, .8f, .8f, 1.7f, 1.9f, .8f, 1.3f, .9f, 2.8f},
                    "Proyecto", "Estado", "Project Manager", "Equipo", "Vacantes", "Cobertura hab.", "Sin cubrir", "Avance", "Plazo", "Riesgo", "Motivos");
            for (ProyectoFila p : d.proyectos()) {
                fila(tpr, p.nombre(), true); tpr.addCell(chip(p.estadoLabel(), p.estado())); fila(tpr, p.pm(), false);
                fila(tpr, p.integrantes() + " / " + (p.requeridos() + 1), false); fila(tpr, String.valueOf(p.vacantes()), false);
                tpr.addCell(barraCelda(cb, p.coberturaPct(), COLORES.get("opt"), -1));
                fila(tpr, p.sinCubrir().isEmpty() ? "—" : String.join(", ", p.sinCubrir()), false);
                fila(tpr, p.avance() + "%", false); fila(tpr, p.plazo(), false);
                tpr.addCell(chip(p.riesgo(), p.riesgoClave()));
                fila(tpr, p.motivos().isEmpty() ? "Sin alertas" : String.join(" · ", p.motivos()), false);
            }
            vacia(tpr, d.proyectos().isEmpty(), 11, "No hay proyectos vigentes con estos filtros.");
            doc.add(tpr);

            seccion(doc, "Oferta contra demanda de habilidades", "Para cada habilidad que piden los proyectos: cuántas personas la tienen al nivel pedido y cuántas tienen capacidad.");
            PdfPTable th = tabla(new float[]{1.6f, 1.4f, 3.4f, 1.1f, 1.2f, 1.2f, .8f, 1.2f},
                    "Habilidad", "Categoría", "Proyectos que la piden", "Nivel pedido", "Con ese nivel", "Con capacidad", "Brecha", "Cobertura");
            for (CoberturaHabilidadFila c : d.habilidades()) {
                fila(th, c.nombre(), true); fila(th, c.categoria(), false);
                fila(th, c.proyectos() + " · " + String.join(", ", c.proyectosNombres()), false);
                fila(th, c.nivelLabel(), false); fila(th, String.valueOf(c.calificadas()), false); fila(th, String.valueOf(c.disponibles()), false);
                fila(th, String.valueOf(Math.max(0, c.proyectos() - c.disponibles())), false);
                th.addCell(chip(c.estado(), claveCobertura(c.estado())));
            }
            vacia(th, d.habilidades().isEmpty(), 8, "No hay habilidades pedidas con estos filtros.");
            doc.add(th);

            seccion(doc, "Decisiones de asignación",
                    de.total() + " propuestas · " + de.tasaAprobacion() + "% aprobadas" + (de.tiempoMedioResolucion() != null ? " · se resuelven en " + de.tiempoMedioResolucion() : "")
                            + " · origen: " + String.join(", ", de.porOrigen().stream().map(o -> o.etiqueta() + " " + o.valor()).toList()));
            PdfPTable td = tabla(new float[]{1f, 2f, 1.8f, 1.8f, 1.6f, .8f, 1f, 1f, 3f},
                    "Fecha", "Proyecto", "Candidato", "Solicitado por", "Origen", "Puntaje", "Dedicación", "Estado", "Comentario");
            for (PropuestaFila p : d.propuestas().stream().limit(25).toList()) {
                fila(td, p.fecha(), false); fila(td, p.proyecto(), true); fila(td, p.candidato(), false); fila(td, p.solicitante(), false);
                fila(td, p.origen(), false); fila(td, String.valueOf(p.score()), false); fila(td, p.dedicacion() + "%", false);
                td.addCell(chip(p.estado(), clavePropuesta(p.estado()))); fila(td, p.motivo(), false);
            }
            vacia(td, d.propuestas().isEmpty(), 9, "Todavía no hay propuestas de asignación.");
            doc.add(td);

            seccion(doc, "Próximos 30 días", "Quién se libera y quién superaría el límite con lo ya programado.");
            PdfPTable tl = tabla(new float[]{2f, 2.4f, 1.2f, 1f, 1.2f}, "Colaborador", "Proyecto que deja", "Fecha", "Días", "Libera");
            for (Liberacion l : d.liberaciones()) {
                fila(tl, l.nombre(), true); fila(tl, l.proyecto(), false); fila(tl, l.fecha(), false);
                fila(tl, String.valueOf(l.dias()), false); fila(tl, l.libera() + "%", false);
            }
            vacia(tl, d.liberaciones().isEmpty(), 5, "Nadie se libera en los próximos 30 días.");
            doc.add(tl);
            if (!d.enRiesgo().isEmpty()) {
                PdfPTable tr = tabla(new float[]{2f, 1.2f}, "En riesgo de sobrecarga", "Carga prevista");
                tr.setWidthPercentage(45);
                tr.setHorizontalAlignment(Element.ALIGN_LEFT);
                for (Riesgo x : d.enRiesgo()) { fila(tr, x.nombre(), true); tr.addCell(chip(x.cargaFutura() + "%", "sobre")); }
                tr.setSpacingBefore(8);
                doc.add(tr);
            }
        } catch (DocumentException ex) {
            throw new IOException("No se pudo generar el reporte PDF.", ex);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    private void kpi(PdfPTable t, String label, String valor, String meta, String tono) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.TOP | Rectangle.BOTTOM | Rectangle.LEFT | Rectangle.RIGHT);
        c.setBorderColor(LINEA);
        c.setBorderWidthTop(2.5f);
        c.setBorderColorTop(color(tono));
        c.setBorderWidthLeft(0); c.setBorderWidthRight(.5f);
        c.setPadding(8);
        c.setPaddingBottom(10);
        c.addElement(new Paragraph(label, F_LABEL));
        Paragraph v = new Paragraph(valor, F_VALOR);
        v.setSpacingBefore(3);
        c.addElement(v);
        c.addElement(new Paragraph(meta, F_META));
        t.addCell(c);
    }

    private void seccion(Document doc, String titulo, String sub) throws DocumentException {
        Paragraph p = new Paragraph(titulo, F_SECCION);
        p.setSpacingBefore(14);
        doc.add(p);
        Paragraph s = new Paragraph(sub, F_SECCION_SUB);
        s.setSpacingAfter(7);
        doc.add(s);
    }

    private PdfPTable tabla(float[] anchos, String... cab) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String c : cab) {
            PdfPCell h = new PdfPCell(new Phrase(c, F_CAB));
            h.setBackgroundColor(NAVY);
            h.setBorderColor(NAVY);
            h.setPadding(6);
            h.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(h);
        }
        return t;
    }

    private void fila(PdfPTable t, String texto, boolean negrita) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, negrita ? F_CELDA_B : F_CELDA));
        c.setPadding(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINEA);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c);
    }

    private void vacia(PdfPTable t, boolean vacia, int cols, String texto) {
        if (!vacia) return;
        PdfPCell c = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, GRIS)));
        c.setColspan(cols);
        c.setPadding(10);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINEA);
        t.addCell(c);
    }

    private PdfPCell chip(String texto, String clave) {
        Color c = color(clave);
        PdfPCell celda = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new Color((int) (c.getRed() * .6), (int) (c.getGreen() * .6), (int) (c.getBlue() * .6)))));
        celda.setBackgroundColor(new Color((int) (c.getRed() * .16 + 255 * .84), (int) (c.getGreen() * .16 + 255 * .84), (int) (c.getBlue() * .16 + 255 * .84)));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(Color.WHITE);
        celda.setBorderWidthBottom(2);
        celda.setPadding(5);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    /** Celda con una mini barra de porcentaje y su valor. limite >= 0 dibuja la marca del límite. */
    private PdfPCell barraCelda(PdfContentByte cb, int pct, Color c, int limite) {
        float w = 66, h = 12;
        PdfTemplate t = cb.createTemplate(w, h);
        t.setColorFill(new Color(232, 236, 242));
        t.roundRectangle(0, 3.5f, 44, 5, 2.5f); t.fill();
        t.setColorFill(c);
        float largo = Math.max(1.5f, Math.min(1f, pct / 100f) * 44);
        t.roundRectangle(0, 3.5f, largo, 5, 2.5f); t.fill();
        if (limite > 0 && limite <= 100) {
            t.setColorStroke(GRIS); t.setLineWidth(.6f);
            t.moveTo(44 * limite / 100f, 1.5f); t.lineTo(44 * limite / 100f, 10.5f); t.stroke();
        }
        texto(t, pct + "%", 49, 3.2f, 7.5f, true, TINTA, Element.ALIGN_LEFT);
        PdfPCell celda = new PdfPCell(Image.getInstance(t), false);
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(LINEA);
        celda.setPadding(5);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return celda;
    }

    private PdfPCell celdaGrafico(String titulo, Image img) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(LINEA);
        c.setPadding(10);
        c.addElement(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, TINTA)));
        img.setSpacingBefore(6);
        c.addElement(img);
        return c;
    }

    /** Dona con número total al centro y leyenda con cantidad y porcentaje. */
    private Image dona(PdfContentByte cb, List<Segmento> segs, String unidad) {
        float w = 240, h = 132, cx = 62, cy = 66, r = 50, grosor = 17;
        PdfTemplate t = cb.createTemplate(w, h);
        int total = segs.stream().mapToInt(Segmento::valor).sum();
        t.setLineWidth(grosor);
        if (total == 0) {
            t.setColorStroke(new Color(232, 236, 242));
            t.circle(cx, cy, r); t.stroke();
        } else {
            float inicio = 90;
            long conValor = segs.stream().filter(x -> x.valor() > 0).count();
            float hueco = conValor > 1 ? 2.2f : 0;
            for (Segmento s : segs) {
                if (s.valor() == 0) continue;
                float ext = 360f * s.valor() / total;
                t.setColorStroke(color(s.clave()));
                t.arc(cx - r, cy - r, cx + r, cy + r, inicio - hueco / 2, -(ext - hueco));
                t.stroke();
                inicio -= ext;
            }
        }
        texto(t, String.valueOf(total), cx, cy - 2, 20, true, NAVY, Element.ALIGN_CENTER);
        texto(t, unidad, cx, cy - 15, 7, false, GRIS, Element.ALIGN_CENTER);
        float y = cy + (segs.size() * 17) / 2f - 10;
        for (Segmento s : segs) {
            t.setColorFill(color(s.clave()));
            t.roundRectangle(134, y, 8, 8, 2); t.fill();
            texto(t, s.etiqueta(), 147, y + 1, 7.5f, false, TINTA, Element.ALIGN_LEFT);
            String pct = total == 0 ? "0%" : Math.round(s.valor() * 100f / total) + "%";
            texto(t, s.valor() + "  ·  " + pct, w - 2, y + 1, 7.5f, true, TINTA, Element.ALIGN_RIGHT);
            y -= 17;
        }
        return imagen(t);
    }

    private Image barrasBrecha(PdfContentByte cb, List<Barra> barras) {
        float w = 370, alto = Math.max(60, 22 * barras.size() + 28);
        PdfTemplate t = cb.createTemplate(w, alto);
        if (barras.isEmpty()) {
            texto(t, "Todas las habilidades pedidas están cubiertas.", 4, alto / 2, 8.5f, false, GRIS, Element.ALIGN_LEFT);
            return imagen(t);
        }
        int max = Math.max(1, barras.stream().mapToInt(Barra::valor).max().orElse(1));
        float x0 = 96, ancho = w - x0 - 40, y = alto - 18;
        for (Barra b : barras) {
            texto(t, recortar(b.etiqueta(), 18), x0 - 6, y + 2, 7.5f, true, TINTA, Element.ALIGN_RIGHT);
            t.setColorFill(new Color(250, 222, 218));
            t.roundRectangle(x0, y, ancho * b.valor() / max, 10, 3); t.fill();
            if (b.referencia() > 0) {
                t.setColorFill(NAVY);
                t.roundRectangle(x0, y + 2.5f, ancho * Math.min(b.valor(), b.referencia()) / max, 5, 2); t.fill();
            }
            texto(t, b.referencia() + " de " + b.valor(), x0 + ancho * b.valor() / max + 5, y + 2, 7, false, GRIS, Element.ALIGN_LEFT);
            y -= 22;
        }
        t.setColorFill(new Color(250, 222, 218)); t.rectangle(x0, 6, 8, 6); t.fill();
        texto(t, "Proyectos que la piden", x0 + 12, 6, 6.5f, false, GRIS, Element.ALIGN_LEFT);
        t.setColorFill(NAVY); t.rectangle(x0 + 100, 6, 8, 6); t.fill();
        texto(t, "Personas con capacidad libre", x0 + 112, 6, 6.5f, false, GRIS, Element.ALIGN_LEFT);
        return imagen(t);
    }

    private Image barrasCarga(PdfContentByte cb, List<Barra> barras, int limite) {
        List<Barra> top = barras.stream().limit(8).toList();
        float w = 370, alto = Math.max(60, 22 * top.size() + 14);
        PdfTemplate t = cb.createTemplate(w, alto);
        if (top.isEmpty()) {
            texto(t, "Sin personas con estos filtros.", 4, alto / 2, 8.5f, false, GRIS, Element.ALIGN_LEFT);
            return imagen(t);
        }
        int escala = Math.max(100, top.stream().mapToInt(Barra::valor).max().orElse(100));
        float x0 = 120, ancho = w - x0 - 64, y = alto - 18;
        for (Barra b : top) {
            texto(t, recortar(b.etiqueta(), 24), x0 - 6, y + 2, 7.5f, true, TINTA, Element.ALIGN_RIGHT);
            t.setColorFill(new Color(232, 236, 242));
            t.roundRectangle(x0, y, ancho, 10, 3); t.fill();
            t.setColorFill(b.valor() > limite ? COLORES.get("sobre") : new Color(0x2A, 0x78, 0xD6));
            t.roundRectangle(x0, y, Math.max(2, ancho * b.valor() / escala), 10, 3); t.fill();
            texto(t, b.valor() + "%  ·  " + b.detalle(), x0 + ancho + 5, y + 2, 7, false, GRIS, Element.ALIGN_LEFT);
            y -= 22;
        }
        float xl = x0 + ancho * limite / escala;
        t.setColorStroke(TINTA); t.setLineWidth(.7f); t.setLineDash(2, 2, 0);
        t.moveTo(xl, 4); t.lineTo(xl, alto - 4); t.stroke();
        return imagen(t);
    }

    private static Image imagen(PdfTemplate t) {
        try {
            Image img = Image.getInstance(t);
            img.setAlignment(Element.ALIGN_LEFT);
            return img;
        } catch (BadElementException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void texto(PdfTemplate t, String s, float x, float y, float tam, boolean negrita, Color c, int alineacion) {
        try {
            BaseFont bf = BaseFont.createFont(negrita ? BaseFont.HELVETICA_BOLD : BaseFont.HELVETICA, BaseFont.WINANSI, false);
            t.beginText();
            t.setFontAndSize(bf, tam);
            t.setColorFill(c);
            t.showTextAligned(alineacion, s, x, y, 0);
            t.endText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** Pie de página con "Página N de M" (el total se escribe al cerrar el documento). */
    private static final class PiePagina extends PdfPageEventHelper {
        private final String generado;
        private PdfTemplate total;
        private BaseFont bf;

        PiePagina(String generado) { this.generado = generado; }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
            try { bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false); } catch (Exception e) { throw new IllegalStateException(e); }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 20;
            cb.setColorStroke(LINEA); cb.setLineWidth(.6f);
            cb.moveTo(document.left(), y + 12); cb.lineTo(document.right(), y + 12); cb.stroke();
            cb.beginText();
            cb.setFontAndSize(bf, 7.5f);
            cb.setColorFill(GRIS);
            cb.showTextAligned(Element.ALIGN_LEFT, "SkillBridge AI · Reporte del Resource Manager · " + generado, document.left(), y, 0);
            String pag = "Página " + writer.getPageNumber() + " de ";
            float ancho = bf.getWidthPoint(pag, 7.5f);
            cb.showTextAligned(Element.ALIGN_LEFT, pag, document.right() - ancho - 14, y, 0);
            cb.endText();
            cb.addTemplate(total, document.right() - 14, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(bf, 7.5f);
            total.setColorFill(GRIS);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
