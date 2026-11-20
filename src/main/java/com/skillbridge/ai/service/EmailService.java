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
                         java.util.List<Dato> datos, java.util.List<Seccion> secciones, String nombre) {
        /** Aviso simple: título, detalle y botón. */
        public Correo(String destinatario, String asunto, String etiqueta, String titulo, String detalle,
                      String enlace, String textoBoton, boolean conPreferencias) {
            this(destinatario, asunto, etiqueta, titulo, detalle, enlace, textoBoton, conPreferencias,
                    java.util.List.of(), java.util.List.of(), null);
        }
        /** Con cifras y secciones (resumen semanal). */
        public Correo(String destinatario, String asunto, String etiqueta, String titulo, String detalle,
                      String enlace, String textoBoton, boolean conPreferencias,
                      java.util.List<Dato> datos, java.util.List<Seccion> secciones) {
            this(destinatario, asunto, etiqueta, titulo, detalle, enlace, textoBoton, conPreferencias, datos, secciones, null);
        }
        public Correo {
            datos = datos == null ? java.util.List.of() : datos;
            secciones = secciones == null ? java.util.List.of() : secciones;
        }
        /** El mismo correo saludando a la persona por su nombre ("Hola, Ana:"). */
        public Correo conNombre(String nombreCompleto) {
            return new Correo(destinatario, asunto, etiqueta, titulo, detalle, enlace, textoBoton, conPreferencias,
                    datos, secciones, nombreCompleto);
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

    // Colores de SkillBridge (los mismos de la app y del logo).
    private static final String NAVY = "#0f1a2e";
    private static final String NAVY_2 = "#1e3a8a";
    private static final String TURQUESA = "#2dd4bf";
    private static final String TEXTO = "#334155";
    private static final String SUAVE = "#64748b";
    private static final String FONDO = "#eef2f8";
    private static final String FUENTE = "'Segoe UI',Roboto,'Helvetica Neue',Helvetica,Arial,sans-serif";

    /** Color e ícono de cada categoría: el correo se reconoce de un vistazo. */
    private record Tono(String color, String fondo, String icono) {}

    private static Tono tonoDe(String etiqueta) {
        String e = etiqueta == null ? "" : etiqueta.toLowerCase(java.util.Locale.ROOT);
        if (e.startsWith("resumen")) return new Tono("#2563eb", "#eaf1ff", "📊");          // 📊
        if (e.contains("proyecto")) return new Tono("#2563eb", "#eaf1ff", "📁");           // 📁
        if (e.contains("foro")) return new Tono("#7c3aed", "#f3edff", "💬");               // 💬
        if (e.contains("mensaje")) return new Tono("#0d9488", "#e6f7f5", "✉️");            // ✉️
        if (e.contains("entregable")) return new Tono("#d97706", "#fff4e0", "📝");         // 📝
        if (e.contains("calendario")) return new Tono("#16a34a", "#e8f7ee", "📅");         // 📅
        if (e.contains("solicitud")) return new Tono("#4f46e5", "#eef0ff", "📥");          // 📥
        if (e.contains("matching")) return new Tono("#9333ea", "#f5ecff", "✨");                 // ✨
        if (e.contains("seguridad")) return new Tono("#dc2626", "#fdecec", "🔐");          // 🔐
        if (e.contains("alerta")) return new Tono("#dc2626", "#fdecec", "⚠️");             // ⚠️
        if (e.contains("invitaci")) return new Tono("#0d9488", "#e6f7f5", "🎉");           // 🎉
        if (e.contains("cuenta")) return new Tono("#2563eb", "#eaf1ff", "👤");             // 👤
        if (e.contains("administraci")) return new Tono("#475569", "#eef1f5", "🛡️"); // 🛡️
        return new Tono("#2563eb", "#eaf1ff", "🔔");                                       // 🔔
    }

    /** HTML del correo. Público para poder ver la plantilla en el navegador (vista previa). */
    public String html(Correo c) {
        Tono t = tonoDe(c.etiqueta());
        String etiqueta = c.etiqueta() != null && !c.etiqueta().isBlank() ? escapar(c.etiqueta()) : "Aviso de SkillBridge AI";
        String detalle = c.detalle() != null && !c.detalle().isBlank()
                ? escapar(c.detalle()).replace("\n", "<br>")
                : "Tienes una novedad en SkillBridge AI.";
        String saludo = primerNombre(c.nombre());
        return "<!DOCTYPE html><html lang=\"es\" xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">"
                + "<title>" + escapar(c.asunto()) + "</title></head>"
                + "<body style=\"margin:0; padding:0; background:" + FONDO + "; -webkit-text-size-adjust:100%;\">"
                // Texto de vista previa (la línea gris que muestra la bandeja de entrada)
                + "<div style=\"display:none; max-height:0; overflow:hidden; opacity:0; color:transparent;\">"
                + escapar(recortar(c.detalle() == null ? c.titulo() : c.detalle(), 140)) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"" + FONDO + "\" style=\"background:" + FONDO + ";\">"
                + "<tr><td align=\"center\" style=\"padding:36px 14px 28px 14px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"max-width:580px; background:#ffffff; border-radius:20px; overflow:hidden; font-family:" + FUENTE + "; "
                + "border:1px solid #e1e7f0; box-shadow:0 12px 32px -18px rgba(15,26,46,.35);\">"
                // Cabecera: logo sobre azul marino con un degradado suave
                + "<tr><td bgcolor=\"" + NAVY + "\" style=\"background:" + NAVY + "; background-image:linear-gradient(135deg," + NAVY + " 0%," + NAVY_2 + " 100%); padding:26px 36px;\">"
                + "<img src=\"cid:logo\" alt=\"SkillBridge\" width=\"114\" height=\"34\" "
                + "style=\"display:block; border:0; outline:none; height:34px; width:114px;\"></td></tr>"
                // Franja del color de la categoría hacia el turquesa del logo
                + "<tr><td height=\"4\" bgcolor=\"" + t.color() + "\" style=\"height:4px; line-height:4px; font-size:0; background:" + t.color() + "; "
                + "background-image:linear-gradient(90deg," + t.color() + "," + TURQUESA + ");\">&nbsp;</td></tr>"
                // Ícono + categoría
                + "<tr><td style=\"padding:34px 36px 0 36px;\">"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + "<td width=\"44\" height=\"44\" align=\"center\" valign=\"middle\" bgcolor=\"" + t.fondo() + "\" "
                + "style=\"width:44px; height:44px; border-radius:14px; background:" + t.fondo() + "; font-size:22px; line-height:44px;\">" + t.icono() + "</td>"
                + "<td style=\"padding-left:12px;\"><span style=\"display:inline-block; padding:5px 11px; border-radius:999px; background:" + t.fondo() + "; "
                + "color:" + t.color() + "; font-size:11px; font-weight:700; letter-spacing:.5px; text-transform:uppercase;\">" + etiqueta + "</span></td>"
                + "</tr></table></td></tr>"
                // Título, saludo y detalle
                + "<tr><td style=\"padding:20px 36px 0 36px;\">"
                + "<h1 style=\"margin:0; font-size:25px; line-height:1.28; font-weight:700; letter-spacing:-.3px; color:" + NAVY + ";\">"
                + escapar(c.titulo()) + "</h1>"
                + (saludo != null ? "<p style=\"margin:18px 0 0 0; font-size:16px; line-height:1.6; color:" + TEXTO + ";\">Hola, <strong>" + escapar(saludo) + "</strong>:</p>" : "")
                + "</td></tr>"
                + "<tr><td style=\"padding:14px 36px 0 36px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + "<td bgcolor=\"#f7f9fc\" style=\"background:#f7f9fc; border-left:4px solid " + t.color() + "; border-radius:12px; padding:16px 18px;\">"
                + "<p style=\"margin:0; font-size:15px; line-height:1.65; color:" + TEXTO + ";\">" + detalle + "</p>"
                + "</td></tr></table></td></tr>"
                + datosHtml(c, t) + seccionesHtml(c, t)
                + "<tr><td style=\"padding:28px 36px 34px 36px;\">" + boton(c, t) + "</td></tr>"
                // Pie dentro de la tarjeta
                + "<tr><td bgcolor=\"#f8fafc\" style=\"background:#f8fafc; border-top:1px solid #e8edf4; padding:20px 36px;\">"
                + "<p style=\"margin:0; font-size:13px; line-height:1.6; color:" + SUAVE + ";\">"
                + "<strong style=\"color:" + NAVY + ";\">SkillBridge AI</strong> · Gestión de talento y proyectos con IA</p>"
                + (c.conPreferencias()
                    ? "<p style=\"margin:6px 0 0 0; font-size:12px; line-height:1.6; color:" + SUAVE + ";\">"
                      + "Puedes elegir qué avisos te llegan por correo en <strong>Notificaciones → Preferencias de aviso</strong>.</p>"
                    : "")
                + "</td></tr>"
                + "</table>"
                // Nota bajo la tarjeta
                + "<p style=\"margin:18px 0 0 0; font-family:" + FUENTE + "; font-size:11px; line-height:1.6; color:#94a3b8; text-align:center;\">"
                + "Mensaje automático de SkillBridge AI · Por favor, no respondas a este correo.</p>"
                + "</td></tr></table></body></html>";
    }

    /** Fila de cifras destacadas (resumen semanal). */
    private String datosHtml(Correo c, Tono t) {
        if (c.datos().isEmpty()) return "";
        int n = Math.min(3, c.datos().size());
        StringBuilder celdas = new StringBuilder();
        for (Dato d : c.datos().subList(0, n)) {
            celdas.append("<td width=\"").append(100 / n).append("%\" valign=\"top\" style=\"padding:5px;\">")
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                    .append("<td bgcolor=\"").append(t.fondo()).append("\" style=\"background:").append(t.fondo())
                    .append("; border-radius:14px; padding:16px 14px;\">")
                    .append("<div style=\"font-size:26px; font-weight:700; line-height:1.15; color:").append(t.color()).append(";\">")
                    .append(escapar(d.valor())).append("</div>")
                    .append("<div style=\"margin-top:6px; font-size:12px; line-height:1.4; color:").append(TEXTO).append(";\">")
                    .append(escapar(d.etiqueta())).append("</div></td></tr></table></td>");
        }
        return "<tr><td style=\"padding:20px 31px 0 31px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" "
                + "cellspacing=\"0\" border=\"0\"><tr>" + celdas + "</tr></table></td></tr>";
    }

    /** Secciones con listas (resumen semanal). */
    private String seccionesHtml(Correo c, Tono t) {
        StringBuilder html = new StringBuilder();
        for (Seccion sec : c.secciones()) {
            html.append("<tr><td style=\"padding:28px 36px 0 36px;\">")
                    .append("<h2 style=\"margin:0 0 6px 0; font-size:16px; font-weight:700; color:").append(NAVY).append(";\">")
                    .append(escapar(sec.titulo())).append("</h2>");
            if (sec.lineas().isEmpty()) {
                html.append("<p style=\"margin:0; font-size:14px; line-height:1.6; color:").append(SUAVE).append(";\">")
                        .append(escapar(sec.vacio())).append("</p>");
            } else {
                html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
                for (Linea l : sec.lineas()) {
                    html.append("<tr><td width=\"18\" valign=\"top\" style=\"padding:13px 0 0 0;\">")
                            .append("<div style=\"width:8px; height:8px; border-radius:50%; background:").append(t.color()).append(";\"></div></td>")
                            .append("<td style=\"padding:9px 0; border-bottom:1px solid #eef2f7;\">")
                            .append("<div style=\"font-size:14px; line-height:1.45; color:").append(NAVY).append("; font-weight:600;\">")
                            .append(escapar(l.principal())).append("</div>");
                    if (l.secundario() != null && !l.secundario().isBlank()) {
                        html.append("<div style=\"margin-top:2px; font-size:12.5px; line-height:1.45; color:").append(SUAVE).append(";\">")
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

    private String boton(Correo c, Tono t) {
        if (c.enlace() == null || c.enlace().isBlank()) {
            return "<p style=\"margin:0; font-size:14px; line-height:1.6; color:" + SUAVE + ";\">"
                    + "Ingresa a <strong style=\"color:" + NAVY + ";\">SkillBridge AI</strong> para ver los detalles.</p>";
        }
        String texto = c.textoBoton() != null && !c.textoBoton().isBlank() ? escapar(c.textoBoton()) : "Ver en SkillBridge AI";
        String enlace = escapar(c.enlace());
        // Botón "a prueba de clientes de correo": tabla + enlace con relleno, en el color de la categoría.
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + "<td align=\"center\" bgcolor=\"" + t.color() + "\" style=\"border-radius:12px; background:" + t.color() + "; "
                + "box-shadow:0 8px 18px -10px " + t.color() + ";\">"
                + "<a href=\"" + enlace + "\" target=\"_blank\" style=\"display:inline-block; padding:14px 28px; font-family:" + FUENTE + "; "
                + "font-size:15px; font-weight:700; color:#ffffff; text-decoration:none; border-radius:12px;\">"
                + texto + " &nbsp;&rarr;</a></td></tr></table>"
                + "<p style=\"margin:20px 0 0 0; font-size:12px; line-height:1.6; color:" + SUAVE + ";\">"
                + "¿El botón no funciona? Copia este enlace en tu navegador:<br>"
                + "<a href=\"" + enlace + "\" style=\"color:" + t.color() + "; word-break:break-all;\">" + enlace + "</a></p>";
    }

    private static String textoPlano(Correo c) {
        StringBuilder t = new StringBuilder();
        t.append(c.titulo()).append("\n\n");
        String saludo = primerNombre(c.nombre());
        if (saludo != null) t.append("Hola, ").append(saludo).append(":\n\n");
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
        t.append("--\nSkillBridge AI · Gestión de talento y proyectos con IA.\n");
        if (c.conPreferencias()) t.append("Puedes elegir qué avisos te llegan por correo en Notificaciones > Preferencias de aviso.\n");
        t.append("Mensaje automático: por favor, no respondas a este correo.");
        return t.toString();
    }

    /** "Ana Ríos" -> "Ana" (null si no hay nombre). */
    private static String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return null;
        return nombreCompleto.trim().split("\\s+")[0];
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
