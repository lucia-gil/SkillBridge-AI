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
        if (destinatario == null || destinatario.isBlank()) return;
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom("SkillBridge AI <" + remitente + ">");
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(construirHtml(titulo, detalle), true);
            mailSender.send(mensaje);
        } catch (Exception e) {
            log.log(Level.WARNING, "No se pudo enviar el correo de notificacion a " + destinatario, e);
        }
    }

    private String construirHtml(String titulo, String detalle) {
        String tituloSeguro = escapar(titulo);
        String detalleSeguro = (detalle != null && !detalle.isBlank())
                ? escapar(detalle)
                : "Tienes una nueva notificación en SkillBridge AI.";
        return "<!DOCTYPE html>"
                + "<html lang=\"es\">"
                + "<body style=\"margin:0; padding:0; background-color:#eef1f6; font-family:Segoe UI, Arial, sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#eef1f6; padding:32px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 10px rgba(16,24,40,0.08);\">"
                + "<tr><td style=\"background:linear-gradient(90deg,#7c3aed,#3b82f6); padding:22px 28px;\">"
                + "<span style=\"color:#ffffff; font-size:18px; font-weight:700; letter-spacing:0.3px;\">SkillBridge AI</span>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 28px 8px 28px;\">"
                + "<p style=\"margin:0 0 6px 0; font-size:12px; font-weight:600; color:#7c3aed; text-transform:uppercase; letter-spacing:0.5px;\">Nueva notificación</p>"
                + "<h1 style=\"margin:0 0 14px 0; font-size:20px; line-height:1.35; color:#111827;\">" + tituloSeguro + "</h1>"
                + "<p style=\"margin:0; font-size:15px; line-height:1.6; color:#374151;\">" + detalleSeguro + "</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 28px 26px 28px;\">"
                + "<div style=\"border-top:1px solid #e5e7eb; padding-top:16px;\">"
                + "<p style=\"margin:0; font-size:12px; color:#9ca3af;\">Ingresa a SkillBridge AI para ver los detalles y tomar acción.</p>"
                + "</div></td></tr>"
                + "</table>"
                + "<p style=\"margin:18px 0 0 0; font-size:11px; color:#9ca3af;\">Este es un mensaje automático de SkillBridge AI, no respondas a este correo.</p>"
                + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }

    private String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
