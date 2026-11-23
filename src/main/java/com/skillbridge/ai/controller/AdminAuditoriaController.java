package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.AuditoriaFila;
import com.skillbridge.ai.dto.TopUsuarioAuditoria;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.service.AuditoriaExcelService;
import com.skillbridge.ai.service.AuditoriaCsvService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Auditoría (administrador/auditoria.html). Ver Javadoc de AuditoriaFila: tipo/severidad/origen se derivan del texto de "accion", no son columnas reales. */
@Controller
@RequestMapping("/administrador")
public class AdminAuditoriaController {

    private final AuditoriaService auditoriaService;
    private final ShellModelBuilder shellModelBuilder;

    private final AuditoriaExcelService excel;
    private final AuditoriaCsvService csv;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final long MESES_EXPORTACION = 6;

    public AdminAuditoriaController(AuditoriaService auditoriaService, ShellModelBuilder shellModelBuilder,
                                    AuditoriaExcelService excel, AuditoriaCsvService csv) {
        this.auditoriaService = auditoriaService;
        this.shellModelBuilder = shellModelBuilder;
        this.excel = excel;
        this.csv = csv;
    }

    @GetMapping("/auditoria.html")
    public String auditoria(HttpSession session, Model model,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "") String usuario, @RequestParam(defaultValue = "") String accion,
            @RequestParam(defaultValue = "0") int page) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Page<AuditoriaFila> pagina = auditoriaService.buscar(
                desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                usuario, accion, PageRequest.of(Math.max(0, page), 12, Sort.by(Sort.Direction.DESC, "fecha", "id")));
        List<AuditoriaFila> eventos = pagina.getContent();
        List<TopUsuarioAuditoria> topUsuarios = auditoriaService.topUsuarios(5);
        Map<String, Long> porSeveridad = auditoriaService.severidades(
                desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay(), usuario, accion);

        shellModelBuilder.aplicar(model, sesion, "auditoria.html", "Auditoría",
                "Trazabilidad completa de acciones en la plataforma");

        model.addAttribute("eventos", eventos);
        model.addAttribute("pagina", pagina);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("usuarioFiltro", usuario);
        model.addAttribute("accionFiltro", accion);
        model.addAttribute("topUsuarios", topUsuarios);
        model.addAttribute("altaCount", porSeveridad.getOrDefault("Alta", 0L));
        model.addAttribute("mediaCount", porSeveridad.getOrDefault("Media", 0L));
        model.addAttribute("infoCount", porSeveridad.getOrDefault("Info", 0L));
        return "administrador/auditoria";
    }

    @GetMapping("/auditoria/exportar")
    public ResponseEntity<StreamingResponseBody> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "") String usuario, @RequestParam(defaultValue = "") String accion,
            @RequestParam(defaultValue = "xlsx") String formato) {
        if (!"xlsx".equals(formato) && !"csv".equals(formato)) {
            throw errorExportacion("Selecciona el formato Excel (.xlsx) o CSV.");
        }
        if (usuario.length() > 100 || accion.length() > 100) {
            throw errorExportacion("Los filtros de usuario y acción admiten hasta 100 caracteres.");
        }
        if (!fechaValida(desde) || !fechaValida(hasta)) {
            throw errorExportacion("Ingresa fechas válidas para la exportación.");
        }

        LocalDate fin = hasta != null ? hasta : LocalDate.now(LIMA);
        if (desde != null && hasta == null) fin = desde.plusMonths(MESES_EXPORTACION);
        LocalDate inicio = desde;
        if (inicio == null) {
            inicio = fin.minusMonths(MESES_EXPORTACION);
            if (inicio.plusMonths(MESES_EXPORTACION).isBefore(fin)) inicio = inicio.plusDays(1);
        }
        if (!fechaValida(inicio) || !fechaValida(fin) || fin.isBefore(inicio)
                || fin.isAfter(inicio.plusMonths(MESES_EXPORTACION))) {
            throw errorExportacion("Selecciona un rango de hasta 6 meses. La fecha final debe ser igual o posterior a la inicial.");
        }

        LocalDate inicioExportacion = inicio;
        LocalDate finExportacion = fin;
        boolean esCsv = "csv".equals(formato);
        String nombre = "skillbridge-auditoria_" + inicio + "_" + fin + "." + formato;
        StreamingResponseBody archivo = salida -> {
            if (esCsv) csv.escribir(inicioExportacion, finExportacion, usuario, accion, salida);
            else excel.escribir(inicioExportacion, finExportacion, usuario, accion, salida);
        };
        String tipo = esCsv ? "text/csv;charset=UTF-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .contentType(MediaType.parseMediaType(tipo))
                .body(archivo);
    }

    private boolean fechaValida(LocalDate fecha) {
        return fecha == null || (fecha.getYear() >= 1000 && fecha.isBefore(LocalDate.of(9999, 12, 31)));
    }

    private ResponseStatusException errorExportacion(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
