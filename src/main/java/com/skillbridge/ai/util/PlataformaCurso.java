package com.skillbridge.ai.util;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconoce de qué plataforma es el enlace de un curso (YouTube, Coursera,
 * Microsoft Learn, roadmap.sh...) para mostrar su logo y poder filtrar por
 * plataforma. El Administrador también puede elegirla a mano.
 */
public final class PlataformaCurso {

    public record Plataforma(String clave, String nombre) {}

    /** Orden = prioridad de detección (fragmento del host -> plataforma). */
    private static final Map<String, Plataforma> POR_HOST = new LinkedHashMap<>();
    private static final Map<String, Plataforma> POR_CLAVE = new LinkedHashMap<>();

    static {
        registrar("youtube", "YouTube", "youtube.com", "youtu.be");
        registrar("coursera", "Coursera", "coursera.org");
        registrar("udemy", "Udemy", "udemy.com");
        registrar("edx", "edX", "edx.org");
        registrar("freecodecamp", "freeCodeCamp", "freecodecamp.org");
        registrar("microsoft", "Microsoft Learn", "learn.microsoft.com", "microsoft.com");
        registrar("aws", "AWS Skill Builder", "skillbuilder.aws", "aws.training", "aws.amazon.com");
        registrar("google", "Google", "cloudskillsboost.google", "grow.google", "developers.google.com", "google.com");
        registrar("roadmap", "roadmap.sh", "roadmap.sh");
        registrar("platzi", "Platzi", "platzi.com");
        registrar("linkedin", "LinkedIn Learning", "linkedin.com");
        registrar("cisco", "Cisco NetAcad", "netacad.com");
        registrar("ibm", "IBM SkillsBuild", "skillsbuild.org", "ibm.com");
        registrar("kaggle", "Kaggle", "kaggle.com");
        registrar("github", "GitHub", "github.com", "github.io");
        registrar("oracle", "Oracle", "oracle.com");
        POR_CLAVE.put("otra", new Plataforma("otra", "Sitio web"));
    }

    private static final Pattern YOUTUBE_ID = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private PlataformaCurso() {}

    private static void registrar(String clave, String nombre, String... hosts) {
        Plataforma p = new Plataforma(clave, nombre);
        POR_CLAVE.put(clave, p);
        for (String h : hosts) POR_HOST.put(h, p);
    }

    /** Plataforma según el host del enlace; "otra" si no se reconoce o no hay enlace. */
    public static Plataforma detectar(String url) {
        if (url == null || url.isBlank()) return POR_CLAVE.get("otra");
        try {
            String host = URI.create(url.trim()).getHost();
            if (host != null) {
                host = host.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, Plataforma> e : POR_HOST.entrySet()) {
                    String h = e.getKey();
                    if (host.equals(h) || host.endsWith("." + h)) return e.getValue();
                }
            }
        } catch (IllegalArgumentException ignorado) {
            // URL mal formada: se valida en CursoService
        }
        return POR_CLAVE.get("otra");
    }

    /** Plataforma por su clave (la que elige el Administrador); null si no existe. */
    public static Plataforma porClave(String clave) {
        return clave == null ? null : POR_CLAVE.get(clave.trim().toLowerCase(Locale.ROOT));
    }

    public static List<Plataforma> todas() {
        return List.copyOf(POR_CLAVE.values());
    }

    /** Id de un video de YouTube (para la miniatura y el reproductor), o null. */
    public static String youtubeId(String url) {
        if (url == null) return null;
        Matcher m = YOUTUBE_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
