package com.skillbridge.ai.service;

import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Correos de SkillBridge AI (Gmail SMTP + contraseña de aplicación, ver
 * application.properties). El mapa completo de qué correo llega y cuándo
 * está en Documentacion_Correos.md.
 *
 * Cómo se envían:
 *  - En segundo plano: quien pide el correo (asignar, responder un foro...)
 *    no espera a Gmail. Si la acción ocurre dentro de una transacción, el
 *    correo sale recién cuando esa transacción se confirma: si se revierte,
 *    no se manda un aviso de algo que no pasó.
 *  - Nunca rompe el flujo principal: un fallo de SMTP solo queda en el log.
 *
 * Cómo se ven:
 *  - Plantilla HTML con tablas y estilos en línea (lo que respetan Gmail,
 *    Outlook y los clientes móviles), en español (lang y Content-Language).
 *  - Logo incrustado en el propio correo (imagen inline "cid:logo"): se ve
 *    aunque la app no tenga una URL pública con HTTPS.
 *  - Versión en texto plano para clientes sin HTML (también ayuda a que el
 *    correo no se marque como spam).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String LOGO = "correo/logo-correo.png";

    /**
     * Un correo con la plantilla de SkillBridge AI.
     *
     * @param etiqueta        categoría que se muestra sobre el título (p. ej. "Proyectos y asignaciones")
     * @param enlace          botón de acción (null = sin botón)
     * @param conPreferencias true si el aviso se puede apagar en Notificaciones → Preferencias de aviso (se explica en el pie)
     */
    public record Correo(String destinatario, String asunto, String etiqueta, String titulo, String detalle,
                         String enlace, String textoBoton, boolean conPreferencias,
                         java.util.List<Dato> datos, java.util.List<Seccion> secciones) {
        /** Aviso simple: título, detalle y botón. */
        public Correo(String destinatario, String asunto, String etiqueta, String titulo, String detalle,
                      String enlace, String textoBoton, boolean conPreferencias) {
            this(destinatario, asunto, etiqueta, titulo, detalle, enlace, textoBoton, conPreferencias,
                    java.util.List.of(), java.util.List.of());
        }
        public Correo {
            datos = datos == null ? java.util.List.of() : datos;
            secciones = secciones == null ? java.util.List.of() : secciones;
        }
    }

    /** Cifra destacada (p. ej. "60%" · "Carga actual"), en una fila de hasta 3. */
    public record Dato(String valor, String etiqueta) {}

    /** Bloque con título y una lista de líneas; si está vacío muestra "vacio". */
    public record Seccion(String titulo, java.util.List<Linea> lineas, String vacio) {}

    /** Una línea de una sección: texto principal y uno secundario (opcional). */
    public record Linea(String principal, String secundario) {}

    private final JavaMailSender mailSender;
    private final ExecutorService envios = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "correo");
        t.setDaemon(true);
        return t;
    });

    @Value("${spring.mail.username}")
    private String remitente;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Envío simple, por compatibilidad. */
    public void enviar(String destinatario, String asunto, String cuerpo) {
        enviarNotificacion(destinatario, asunto, asunto, cuerpo);
    }

    public void enviarNotificacion(String destinatario, String asunto, String titulo, String detalle) {
        enviarNotificacion(destinatario, asunto, titulo, detalle, null, null);
    }

    public void enviarNotificacion(String destinatario, String asunto, String titulo, String detalle,
                                   String enlace, String textoBoton) {
        enviar(new Correo(destinatario, asunto, "Aviso de SkillBridge AI", titulo, detalle, enlace, textoBoton, false));
    }

    /** Programa el envío (después del commit si hay una transacción en curso). */
    public void enviar(Correo correo) {
        if (correo == null || correo.destinatario() == null || correo.destinatario().isBlank()) return;
        Runnable tarea = () -> envios.submit(() -> enviarAhora(correo));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { tarea.run(); }
            });
        } else {
            tarea.run();
        }
    }

    /** Se usa solo en la prueba explícita para informar si el servidor SMTP aceptó el correo. */
    public boolean probarEnvio(Correo correo) {
        return enviarAhora(correo);
    }

    private boolean enviarAhora(Correo c) {
        if (c == null || c.destinatario() == null || c.destinatario().isBlank()) return false;
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            // multipart: texto plano + HTML + el logo como imagen inline
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(remitente, "SkillBridge AI");
            helper.setTo(c.destinatario());
            helper.setSubject(c.asunto());
            helper.setText(textoPlano(c), html(c));
            helper.addInline("logo", new ClassPathResource(LOGO), "image/png");
            mensaje.setHeader("Content-Language", "es");
            mailSender.send(mensaje);
            log.info("El servidor SMTP aceptó un correo de SkillBridge AI.");
            return true;
        } catch (Exception e) {
            log.warn("No se pudo entregar un correo a SMTP. Revisa la configuración y conexión del remitente. Tipo: {}.",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    @PreDestroy
    void cerrar() throws InterruptedException {
        envios.shutdown();
        envios.awaitTermination(10, TimeUnit.SECONDS);   // deja salir los correos ya pedidos
    }

    // ───────────────────────── plantilla ─────────────────────────

    // Colores de la marca (los mismos de la app y del logo).
    private static final String NAVY = "#0f1a2e";
    private static final String AZUL = "#2563eb";
    private static final String TURQUESA = "#2dd4bf";
    private static final String TEXTO = "#334155";
    private static final String SUAVE = "#64748b";

    /** HTML del correo. Público para poder ver la plantilla en el navegador (vista previa). */
    public String html(Correo c) {
        String detalle = c.detalle() != null && !c.detalle().isBlank()
                ? escapar(c.detalle()).replace("\n", "<br>")
                : "Tienes una novedad en SkillBridge AI.";
        String etiqueta = c.etiqueta() != null && !c.etiqueta().isBlank() ? escapar(c.etiqueta()) : "Aviso de SkillBridge AI";
        return "<!DOCTYPE html><html lang=\"es\" xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">"
                + "<title>" + escapar(c.asunto()) + "</title></head>"
                + "<body style=\"margin:0; padding:0; background:#eef1f6; -webkit-text-size-adjust:100%;\">"
                // Texto de vista previa (la línea gris que muestra la bandeja de entrada)
                + "<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent;\">"
                + escapar(recortar(c.detalle() == null ? c.titulo() : c.detalle(), 140)) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#eef1f6;\">"
                + "<tr><td align=\"center\" style=\"padding:32px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"max-width:560px; background:#ffffff; border-radius:16px; overflow:hidden; "
                + "font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif; border:1px solid #e2e8f0;\">"
                // Cabecera con el logo
                + "<tr><td align=\"left\" style=\"background:" + NAVY + "; padding:22px 32px;\">"
                + "<img src=\"cid:logo\" alt=\"SkillBridge\" width=\"107\" height=\"32\" "
                + "style=\"display:block; border:0; outline:none; height:32px; width:107px;\"></td></tr>"
                // Franja con los colores del logo
                + "<tr><td style=\"height:4px; line-height:4px; font-size:0; background:" + AZUL + "; "
                + "background-image:linear-gradient(90deg," + AZUL + "," + TURQUESA + ");\">&nbsp;</td></tr>"
                // Contenido
                + "<tr><td style=\"padding:30px 32px 8px 32px;\">"
                + "<p style=\"margin:0 0 10px 0; font-size:12px; font-weight:700; letter-spacing:.6px; text-transform:uppercase; color:" + AZUL + ";\">"
                + etiqueta + "</p>"
                + "<h1 style=\"margin:0 0 14px 0; font-size:22px; line-height:1.3; font-weight:700; color:" + NAVY + ";\">"
                + escapar(c.titulo()) + "</h1>"
                + "<p style=\"margin:0; font-size:15px; line-height:1.65; color:" + TEXTO + ";\">" + detalle + "</p>"
                + "</td></tr>"
                + datosHtml(c) + seccionesHtml(c)
                + "<tr><td style=\"padding:24px 32px 30px 32px;\">" + boton(c) + "</td></tr>"
                // Pie
                + "<tr><td style=\"background:#f8fafc; border-top:1px solid #e2e8f0; padding:18px 32px;\">"
                + "<p style=\"margin:0; font-size:12px; line-height:1.6; color:" + SUAVE + ";\">"
                + "<strong style=\"color:" + NAVY + ";\">SkillBridge AI</strong> · Gestión de talento y proyectos de NexaCorp.<br>"
                + (c.conPreferencias()
                    ? "Puedes elegir qué avisos te llegan por correo en <strong>Notificaciones → Preferencias de aviso</strong>.<br>"
                    : "")
                + "Este es un mensaje automático: no respondas a este correo.</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Fila de cifras destacadas (resumen semanal). */
    private String datosHtml(Correo c) {
        if (c.datos().isEmpty()) return "";
        StringBuilder celdas = new StringBuilder();
        int ancho = 100 / Math.min(3, c.datos().size());
        for (Dato d : c.datos().subList(0, Math.min(3, c.datos().size()))) {
            celdas.append("<td width=\"").append(ancho).append("%\" valign=\"top\" style=\"padding:4px;\">")
                    .append("<div style=\"background:#f1f5fb; border:1px solid #e2e8f0; border-radius:12px; padding:14px 12px;\">")
                    .append("<div style=\"font-size:22px; font-weight:700; line-height:1.2; color:").append(NAVY).append(";\">")
                    .append(escapar(d.valor())).append("</div>")
                    .append("<div style=\"margin-top:4px; font-size:12px; line-height:1.4; color:").append(SUAVE).append(";\">")
                    .append(escapar(d.etiqueta())).append("</div></div></td>");
        }
        return "<tr><td style=\"padding:20px 28px 0 28px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" "
                + "cellspacing=\"0\" border=\"0\"><tr>" + celdas + "</tr></table></td></tr>";
    }

    /** Secciones con listas (resumen semanal). */
    private String seccionesHtml(Correo c) {
        StringBuilder html = new StringBuilder();
        for (Seccion sec : c.secciones()) {
            html.append("<tr><td style=\"padding:24px 32px 0 32px;\">")
                    .append("<h2 style=\"margin:0 0 8px 0; font-size:15px; font-weight:700; color:").append(NAVY).append(";\">")
                    .append(escapar(sec.titulo())).append("</h2>");
            if (sec.lineas().isEmpty()) {
                html.append("<p style=\"margin:0; font-size:14px; line-height:1.6; color:").append(SUAVE).append(";\">")
                        .append(escapar(sec.vacio())).append("</p>");
            } else {
                html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
                for (Linea l : sec.lineas()) {
                    html.append("<tr><td style=\"padding:9px 0; border-bottom:1px solid #eef1f6;\">")
                            .append("<div style=\"font-size:14px; line-height:1.45; color:").append(TEXTO).append("; font-weight:600;\">")
                            .append(escapar(l.principal())).append("</div>");
                    if (l.secundario() != null && !l.secundario().isBlank()) {
                        html.append("<div style=\"font-size:12px; line-height:1.45; color:").append(SUAVE).append(";\">")
                                .append(escapar(l.secundario())).append("</div>");
                    }
                    html.append("</td></tr>");
                }
                html.append("</table>");
            }
            html.append("</td></tr>");
        }
        return html.toString();
    }

    private String boton(Correo c) {
        if (c.enlace() == null || c.enlace().isBlank()) {
            return "<p style=\"margin:0; font-size:14px; line-height:1.6; color:" + SUAVE + ";\">"
                    + "Ingresa a <strong style=\"color:" + NAVY + ";\">SkillBridge AI</strong> para ver los detalles.</p>";
        }
        String texto = c.textoBoton() != null && !c.textoBoton().isBlank() ? escapar(c.textoBoton()) : "Ver en SkillBridge AI";
        String enlace = escapar(c.enlace());
        // Botón "a prueba de clientes de correo": tabla + enlace con relleno.
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + "<td align=\"center\" bgcolor=\"" + NAVY + "\" style=\"border-radius:10px;\">"
                + "<a href=\"" + enlace + "\" target=\"_blank\" style=\"display:inline-block; padding:13px 26px; "
                + "font-size:15px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:10px;\">"
                + texto + " &rarr;</a></td></tr></table>"
                + "<p style=\"margin:18px 0 0 0; font-size:12px; line-height:1.6; color:" + SUAVE + ";\">"
                + "¿El botón no funciona? Copia este enlace en tu navegador:<br>"
                + "<a href=\"" + enlace + "\" style=\"color:" + AZUL + "; word-break:break-all;\">" + enlace + "</a></p>";
    }

    private static String textoPlano(Correo c) {
        StringBuilder t = new StringBuilder();
        t.append(c.titulo()).append("\n\n");
        if (c.detalle() != null && !c.detalle().isBlank()) t.append(c.detalle()).append("\n\n");
        for (Dato d : c.datos()) t.append("- ").append(d.etiqueta()).append(": ").append(d.valor()).append("\n");
        if (!c.datos().isEmpty()) t.append("\n");
        for (Seccion sec : c.secciones()) {
            t.append(sec.titulo().toUpperCase(java.util.Locale.ROOT)).append("\n");
            if (sec.lineas().isEmpty()) t.append(sec.vacio()).append("\n");
            for (Linea l : sec.lineas()) {
                t.append("- ").append(l.principal());
                if (l.secundario() != null && !l.secundario().isBlank()) t.append(" (").append(l.secundario()).append(")");
                t.append("\n");
            }
            t.append("\n");
        }
        if (c.enlace() != null && !c.enlace().isBlank()) {
            t.append(c.textoBoton() != null && !c.textoBoton().isBlank() ? c.textoBoton() : "Ver en SkillBridge AI")
                    .append(": ").append(c.enlace()).append("\n\n");
        }
        t.append("--\nSkillBridge AI · Gestión de talento y proyectos de NexaCorp.\n");
        if (c.conPreferencias()) t.append("Puedes elegir qué avisos te llegan por correo en Notificaciones > Preferencias de aviso.\n");
        t.append("Este es un mensaje automático: no respondas a este correo.");
        return t.toString();
    }

    private static String recortar(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
