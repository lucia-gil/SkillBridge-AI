package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.InvitacionMasivaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Controller
public class AdminInvitacionMasivaController {
    private static final String REVISION = "admin.invitaciones.revision";
    private static final String IMPORTACION = "redirect:/administrador/usuarios/importar.html";

    private final InvitacionMasivaService importador;
    private final ShellModelBuilder shell;

    public AdminInvitacionMasivaController(InvitacionMasivaService importador, ShellModelBuilder shell) {
        this.importador = importador;
        this.shell = shell;
    }

    @GetMapping("/administrador/usuarios/importar.html")
    public String pagina(HttpSession session, Model model) {
        shell.aplicar(model, usuario(session), "usuarios.html", "Invitaciones masivas",
                "Autoriza varios correos desde una plantilla");
        model.addAttribute("revision", vigente(session));
        return "administrador/invitaciones-masivas";
    }

    @GetMapping("/administrador/usuarios/importar/plantilla")
    public ResponseEntity<byte[]> plantilla(@RequestParam String formato) throws IOException {
        boolean excel = "xlsx".equals(formato);
        if (!excel && !"csv".equals(formato)) return ResponseEntity.badRequest().build();

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        importador.escribirPlantilla(excel, salida);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=skillbridge-invitaciones." + formato)
                .contentType(MediaType.parseMediaType(excel
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv;charset=UTF-8"))
                .body(salida.toByteArray());
    }

    @PostMapping("/administrador/usuarios/importar/validar")
    public String validar(@RequestParam("archivo") MultipartFile archivo, HttpSession session, RedirectAttributes flash) {
        session.removeAttribute(REVISION);
        try {
            session.setAttribute(REVISION, importador.revisar(archivo, usuario(session).getUsuarioId()));
            return IMPORTACION + "#revision-invitaciones";
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return IMPORTACION;
    }

    @PostMapping("/administrador/usuarios/importar/confirmar")
    public String confirmar(@RequestParam String token, HttpSession session, RedirectAttributes flash) {
        var revision = vigente(session);
        if (revision == null || !revision.token().equals(token)) {
            flash.addFlashAttribute("error", "La revisión ya no está disponible. Vuelve a validar el archivo.");
            return IMPORTACION;
        }
        session.removeAttribute(REVISION);
        try {
            int total = importador.confirmar(revision, usuario(session).getUsuarioId());
            flash.addFlashAttribute("exito", "Correos autorizados: " + total + ". Los envíos quedaron en cola.");
            return "redirect:/administrador/usuarios.html#invitaciones";
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage() + " Vuelve a revisar el archivo.");
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("error", "No se guardó el lote. Puede que un correo ya esté autorizado. Revisa el archivo otra vez.");
        }
        return IMPORTACION;
    }

    @PostMapping("/administrador/usuarios/importar/descartar")
    public String descartar(HttpSession session) {
        session.removeAttribute(REVISION);
        return IMPORTACION;
    }

    private InvitacionMasivaService.Revision vigente(HttpSession session) {
        var revision = (InvitacionMasivaService.Revision) session.getAttribute(REVISION);
        if (revision == null || revision.vigente(usuario(session).getUsuarioId())) return revision;
        session.removeAttribute(REVISION);
        return null;
    }

    private static UsuarioSesion usuario(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }
}
