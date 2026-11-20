package com.skillbridge.ai.service;

import com.skillbridge.ai.service.EmailService.Correo;
import com.skillbridge.ai.service.EmailService.Dato;
import com.skillbridge.ai.service.EmailService.Linea;
import com.skillbridge.ai.service.EmailService.Seccion;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plantilla de los correos y armado del mensaje MIME (sin servidor SMTP: JavaMailSender simulado). */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "remitente", "notificaciones@skillbridge.test");
    }

    private static Correo aviso(String etiqueta) {
        return new Correo("ana@correo.com", "Dedicación actualizada", etiqueta, "Dedicación actualizada",
                "Tu dedicación cambió de 45% a 35%.", "http://13.216.85.235:8080/colaborador/proyectos.html",
                "Ver proyecto", true);
    }

    // ─────────────── HTML ───────────────

    @Test
    void htmlTieneLogoIconoTituloYBoton() {
        String html = service.html(aviso("Proyectos y asignaciones").conNombre("Ana Ríos"));

        assertThat(html).contains("src=\"cid:logo\"", "src=\"cid:icono\"")
                .contains("Dedicación actualizada")
                .contains("Hola, <strong>Ana</strong>:")
                .contains("href=\"http://13.216.85.235:8080/colaborador/proyectos.html\"")
                .contains("Ver proyecto")
                .contains("Preferencias de aviso")
                .contains("SkillBridge AI");
    }

    @Test
    void htmlNoUsaEmojisNiLaMarcaDeLosMockups() {
        String html = service.html(aviso("Seguridad de tu cuenta"));
        assertThat(html.codePoints().filter(cp -> cp >= 0x1F300 || (cp >= 0x2600 && cp <= 0x27BF)))
                .as("sin emojis").isEmpty();
        assertThat(html.toLowerCase()).doesNotContain("nexacorp");
    }

    @Test
    void htmlEscapaElContenidoDelUsuario() {
        Correo c = new Correo("ana@correo.com", "Asunto", "Foros", "<script>alert(1)</script>",
                "Hola \"equipo\" & <b>todos</b>", null, null, false).conNombre("<img src=x>");

        String html = service.html(c);

        assertThat(html).doesNotContain("<script>", "<b>todos</b>", "<img src=x>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("Hola &quot;equipo&quot; &amp; &lt;b&gt;todos&lt;/b&gt;");
    }

    @Test
    void sinNombreNoSaludaYSinEnlaceNoHayBoton() {
        Correo c = new Correo("ana@correo.com", "Asunto", "Cuenta", "Cuenta suspendida", "Motivo: inactividad.",
                null, null, false);

        String html = service.html(c);

        assertThat(html).doesNotContain("Hola, <strong>").doesNotContain("<a href=")
                .contains("Ingresa a").doesNotContain("Preferencias de aviso");
    }

    @Test
    void htmlMuestraDatosYSecciones() {
        Correo c = new Correo("luis@correo.com", "Tu semana", "Resumen semanal", "Así viene tu semana", null,
                "http://x/inicio.html", "Ir a mi Inicio", true,
                List.of(new Dato("2", "Solicitudes por resolver"), new Dato("3", "En la banca")),
                List.of(new Seccion("Se liberan pronto", List.of(new Linea("Brian Castro", "Libera 50%")), "Nadie"),
                        new Seccion("Agenda de la semana", List.of(), "No tienes eventos esta semana.")));

        String html = service.html(c);

        assertThat(html).contains("Solicitudes por resolver", "En la banca", "Se liberan pronto",
                "Brian Castro", "Libera 50%", "No tienes eventos esta semana.");
    }

    // ─────────────── Íconos por categoría ───────────────

    @ParameterizedTest(name = "{0} -> {1}.png")
    @CsvSource({
            "Proyectos y asignaciones, proyecto",
            "Resumen semanal · semana del 1 al 7, resumen",
            "Foros, foro",
            "Mensajes, mensaje",
            "Entregables, entregable",
            "Calendario, calendario",
            "Solicitudes de aprobación, solicitud",
            "AI Talent Matching, matching",
            "Seguridad de tu cuenta, seguridad",
            "Alerta, alerta",
            "Invitación, invitacion",
            "Cuenta, cuenta",
            "Administración, administracion",
            "Aviso de SkillBridge AI, aviso"
    })
    void cadaCategoriaAdjuntaSuIcono(String etiqueta, String icono) throws Exception {
        assertThat(new ClassPathResource("correo/iconos/" + icono + ".png").exists()).isTrue();
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        assertThat(service.probarEnvio(aviso(etiqueta))).isTrue();

        MimeMessage enviado = capturarEnviado();
        BodyPart icono1 = partes(enviado).stream().filter(p -> esInline(p, "<icono>")).findFirst().orElseThrow();
        byte[] png = icono1.getInputStream().readAllBytes();
        byte[] esperado = new ClassPathResource("correo/iconos/" + icono + ".png").getInputStream().readAllBytes();
        assertThat(png).isEqualTo(esperado);
    }

    // ─────────────── Envío ───────────────

    @Test
    void armaElMensajeMimeCompleto() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        boolean aceptado = service.probarEnvio(aviso("Proyectos y asignaciones").conNombre("Ana Ríos"));

        assertThat(aceptado).isTrue();
        MimeMessage m = capturarEnviado();
        assertThat(m.getSubject()).isEqualTo("Dedicación actualizada");
        assertThat(m.getAllRecipients()).extracting(Object::toString).containsExactly("ana@correo.com");
        assertThat(m.getFrom()[0].toString()).contains("SkillBridge AI").contains("notificaciones@skillbridge.test");
        assertThat(m.getHeader("Content-Language")).containsExactly("es");

        List<BodyPart> partes = partes(m);
        assertThat(partes).anyMatch(p -> esInline(p, "<logo>"));
        assertThat(partes).anyMatch(p -> esInline(p, "<icono>"));
        String texto = contenido(partes, "text/plain");
        assertThat(texto).contains("Hola, Ana:")
                .contains("Ver proyecto: http://13.216.85.235:8080/colaborador/proyectos.html")
                .contains("no respondas");
        assertThat(contenido(partes, "text/html")).contains("cid:logo");
    }

    @Test
    void siSmtpFallaDevuelveFalseSinLanzar() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("sin conexión")).when(mailSender).send(any(MimeMessage.class));

        assertThat(service.probarEnvio(aviso("Cuenta"))).isFalse();
    }

    @Test
    void sinDestinatarioNoIntentaEnviar() {
        Correo sinCorreo = new Correo(" ", "Asunto", "Cuenta", "Título", "Detalle", null, null, false);

        assertThat(service.probarEnvio(sinCorreo)).isFalse();
        service.enviar(sinCorreo);
        service.enviar((Correo) null);

        verify(mailSender, never()).createMimeMessage();
    }

    // ─────────────── utilidades ───────────────

    private MimeMessage capturarEnviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage m = captor.getValue();
        m.saveChanges();   // calcula las cabeceras (Content-Type) como lo haría el envío real
        return m;
    }

    private static List<BodyPart> partes(Part parte) throws Exception {
        List<BodyPart> lista = new ArrayList<>();
        Object contenido = parte.getContent();
        if (contenido instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                lista.add(bp);
                lista.addAll(partes(bp));
            }
        }
        return lista;
    }

    private static boolean esInline(BodyPart p, String contentId) {
        try {
            String[] ids = p.getHeader("Content-ID");
            return ids != null && contentId.equals(ids[0]) && p.isMimeType("image/png");
        } catch (Exception e) {
            return false;
        }
    }

    private static String contenido(List<BodyPart> partes, String tipo) throws Exception {
        for (BodyPart p : partes) {
            if (p.isMimeType(tipo)) return (String) p.getContent();
        }
        throw new AssertionError("Falta la parte " + tipo);
    }
}
