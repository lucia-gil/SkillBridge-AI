package com.skillbridge.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Envio real de notificaciones por correo (Gmail SMTP + contraseña de
 * aplicacion, ver application.properties). Nunca debe romper el flujo
 * principal de la app: cualquier fallo de envio (SMTP caido, correo
 * invalido, timeout, etc.) se registra en el log y se ignora - crear una
 * notificacion en la plataforma no debe fallar por un problema de correo.
 *
 * El correo se envia como HTML con una plantilla simple (franja de color,
 * titulo, detalle) en vez de texto plano, para que se vea mas cuidado en
 * el cliente de correo del destinatario.
 */
@Service
public class EmailService {

    private static final Logger log = Logger.getLogger(EmailService.class.getName());

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remitente;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Compatibilidad con el envio simple (sin plantilla), por si se necesita en otro punto. */
    public void enviar(String destinatario, String asunto, String cuerpo) {
        enviarNotificacion(destinatario, asunto, asunto, cuerpo);
    }

    /**
     * Envia una notificacion con la plantilla HTML de SkillBridge AI.
     *
     * @param destinatario correo del usuario
     * @param asunto       asunto del correo (linea "Asunto:")
     * @param titulo       titulo destacado dentro de la tarjeta del correo
     * @param detalle      texto de detalle debajo del titulo (puede ser null/vacio)
     */
    public void enviarNotificacion(String destinatario, String asunto, String titulo, String detalle) {
        enviarNotificacion(destinatario, asunto, titulo, detalle, null, null);
    }

    /**
     * Igual que enviarNotificacion(destinatario, asunto, titulo, detalle),
     * pero agrega un boton con un link de accion dentro del correo (ej. para
     * que una invitacion traiga el link directo a la pagina de inicio, con
     * instruccion de hacer clic en "Crear cuenta"). Si enlace es null o
     * vacio, no se muestra ningun boton y el correo queda identico al de
     * siempre.
     */
    public void enviarNotificacion(String destinatario, String asunto, String titulo, String detalle,
                                    String enlace, String textoBoton) {
        if (destinatario == null || destinatario.isBlank()) return;
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom("SkillBridge AI <" + remitente + ">");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(construirHtml(titulo, detalle, enlace, textoBoton), true);
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.log(Level.WARNING, "No se pudo enviar el correo de notificacion a " + destinatario, e);
        }
    }

    private String construirHtml(String titulo, String detalle, String enlace, String textoBoton) {
        String tituloSeguro = escapar(titulo);
        String detalleSeguro = (detalle != null && !detalle.isBlank())
                ? escapar(detalle)
                : "Tienes una nueva notificación en SkillBridge AI.";
        return "<!DOCTYPE html>"
                + "<html lang=\"es\">"
                + "<body style=\"margin:0; padding:0; background-color:#f0f2f5; font-family:'Segoe UI', Roboto, Arial, sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f0f2f5; padding:40px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"520\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 4px 24px rgba(15,26,46,0.12);\">"
                // — Header con logo y nombre —
                + "<tr><td style=\"background:#0f1a2e; padding:28px 32px; text-align:center;\">"
                + "<img src=\"https://i.imgur.com/placeholder.png\" alt=\"\" width=\"36\" height=\"36\" style=\"display:inline-block; vertical-align:middle; margin-right:10px; border-radius:8px;\">"
                + "<span style=\"color:#ffffff; font-size:20px; font-weight:700; letter-spacing:0.3px; vertical-align:middle;\">SkillBridge AI</span>"
                + "</td></tr>"
                // — Accent bar —
                + "<tr><td style=\"background:linear-gradient(90deg,#2f3fe6,#6b7cfa); height:4px; font-size:0; line-height:0;\">&nbsp;</td></tr>"
                // — Body —
                + "<tr><td style=\"padding:32px 32px 12px 32px;\">"
                + "<p style=\"margin:0 0 8px 0; font-size:11px; font-weight:700; color:#2f3fe6; text-transform:uppercase; letter-spacing:1px;\">Nueva notificación</p>"
                + "<h1 style=\"margin:0 0 16px 0; font-size:22px; line-height:1.35; color:#0f1a2e; font-weight:700;\">" + tituloSeguro + "</h1>"
                + "<p style=\"margin:0; font-size:15px; line-height:1.65; color:#374151;\">" + detalleSeguro + "</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px 28px 32px;\">"
                + "<div style=\"border-top:1px solid #e5e7eb; padding-top:20px;\">"
                + botonHtml(enlace, textoBoton)
                + "</div></td></tr>"
                + "</table>"
                // — Footer —
                + "<p style=\"margin:20px 0 0 0; font-size:11px; color:#9ca3af; text-align:center;\">Este es un mensaje automático de SkillBridge AI · No respondas a este correo</p>"
                + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }

    private String botonHtml(String enlace, String textoBoton) {
        if (enlace == null || enlace.isBlank()) {
            return "<p style=\"margin:0; font-size:13px; color:#6b7385;\">Ingresa a <strong style=\"color:#0f1a2e;\">SkillBridge AI</strong> para ver los detalles y tomar acción.</p>";
        }
        String texto = (textoBoton != null && !textoBoton.isBlank()) ? escapar(textoBoton) : "Ver en SkillBridge AI";
        String enlaceSeguro = escapar(enlace);
        return "<a href=\"" + enlaceSeguro + "\" target=\"_blank\" "
                + "style=\"display:inline-block; background:#0f1a2e; color:#ffffff; "
                + "text-decoration:none; font-size:14px; font-weight:600; padding:14px 28px; border-radius:10px; "
                + "box-shadow:0 4px 12px rgba(15,26,46,0.25);\">"
                + texto + "</a>"
                + "<p style=\"margin:14px 0 0 0; font-size:12px; color:#9ca3af;\">Si el botón no funciona, copia y pega este link:<br>"
                + "<a href=\"" + enlaceSeguro + "\" style=\"color:#2f3fe6; word-break:break-all;\">" + enlaceSeguro + "</a></p>";
    }

    private String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
