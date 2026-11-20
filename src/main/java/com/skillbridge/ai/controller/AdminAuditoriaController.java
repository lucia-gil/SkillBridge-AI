package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.AuditoriaFila;
import com.skillbridge.ai.dto.TopUsuarioAuditoria;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AuditoriaService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Auditoría (administrador/auditoria.html). Ver Javadoc de AuditoriaFila: tipo/severidad/origen se derivan del texto de "accion", no son columnas reales. */
@Controller
@RequestMapping("/administrador")
public class AdminAuditoriaController {

    private final AuditoriaService auditoriaService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminAuditoriaController(AuditoriaService auditoriaService, ShellModelBuilder shellModelBuilder) {
        this.auditoriaService = auditoriaService;
        this.shellModelBuilder = shellModelBuilder;
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
                usuario, accion, PageRequest.of(Math.max(0, page), 12, Sort.by(Sort.Direction.DESC, "fecha")));
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
}
