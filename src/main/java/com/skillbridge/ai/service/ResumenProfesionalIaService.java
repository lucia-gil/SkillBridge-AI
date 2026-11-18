package com.skillbridge.ai.service;

import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.AnalisisConstanciaIa;
import com.skillbridge.ai.dto.CertificadoFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.ResumenProfesionalIa;
import com.skillbridge.ai.dto.ResumenProfesionalIa.Evidencia;
import com.skillbridge.ai.model.CertificadoHabilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.CertificadoHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resumen profesional con IA (colaborador/resumen-profesional.html).
 *
 * Junta todas las constancias que el colaborador adjuntó a sus habilidades
 * (PDF subidos y links) y le pide a Claude un resumen tipo CV corto:
 * titular, presentación, fortalezas, qué acredita cada constancia y
 * sugerencias para reforzar el perfil. Claude lee los PDF completos
 * (bloques "document" de la API de Anthropic, incluso escaneados); de los
 * links se intenta descargar el texto de la página (si es privada o se arma
 * con JavaScript, Claude solo ve la dirección y lo dice).
 *
 * Flujo: el colaborador adjunta constancias a sus habilidades; el Resource
 * Manager, al validar, ve un resumen previo de cada constancia
 * (analizarConstancia). Cada vez que valida, el resumen profesional se
 * vuelve a armar solo, en segundo plano, con las habilidades ya validadas
 * (actualizarEnSegundoPlano). El colaborador lo ve pero no lo edita.
 */
@Service
public class ResumenProfesionalIaService {

    private static final Logger log = LoggerFactory.getLogger(ResumenProfesionalIaService.class);

    /** Claude acepta pedidos de hasta 32 MB; el PDF viaja en base64 (+33 %), así que se deja margen. */
    private static final long MAX_BYTES_PDF_TOTAL = 20L * 1024 * 1024;
    private static final int MAX_PDFS = 12;
    private static final int MAX_LINKS = 10;
    private static final int MAX_CARACTERES_LINK = 3_000;
    private static final int MAX_OUTPUT_TOKENS = 4_096;

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ES);

    private final AsistenteIaService asistenteIaService;
    private final HabilidadService habilidadService;
    private final CertificadoHabilidadRepository certificadoRepository;
    private final PerfilRepository perfilRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    /** Lectura de páginas públicas (enlaces del perfil): espera hasta 6 s. */
    private final RestClient clientePaginas = clienteSinRedirecciones(Duration.ofSeconds(6));
    /** Descarga de PDF de Google Drive: espera hasta 15 s. */
    private final RestClient clienteDrive = clienteSinRedirecciones(Duration.ofSeconds(15));

    /**
     * RestClient (como en AsistenteIaService) que NO sigue redirecciones solo:
     * leerPagina y descargarPdfDeDrive las siguen a mano para revisar cada
     * salto (que no apunte a la red interna ni salga de Google Drive).
     */
    private static RestClient clienteSinRedirecciones(Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection conexion, String metodo) throws java.io.IOException {
                super.prepareConnection(conexion, metodo);
                conexion.setInstanceFollowRedirects(false);
            }
        };
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(lectura);
        return RestClient.builder().requestFactory(fabrica).build();
    }

    /** Lo necesario de una respuesta HTTP, sin lanzar excepción por 3xx/4xx/5xx. */
    private record Respuesta(int estado, String ubicacion, String tipo, byte[] cuerpo) {}

    private static Respuesta pedir(RestClient cliente, URI uri, String accept) {
        return cliente.get().uri(uri)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (SkillBridge AI)")
                .headers(h -> { if (accept != null) h.set(HttpHeaders.ACCEPT, accept); })
                .exchange((pedido, resp) -> new Respuesta(resp.getStatusCode().value(),
                        resp.getHeaders().getFirst(HttpHeaders.LOCATION),
                        resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        // El cuerpo solo se usa con 200; con 4xx/5xx HttpURLConnection no deja leerlo.
                        resp.getStatusCode().is2xxSuccessful() ? resp.getBody().readAllBytes() : new byte[0]));
    }

    public ResumenProfesionalIaService(AsistenteIaService asistenteIaService, HabilidadService habilidadService,
                                       CertificadoHabilidadRepository certificadoRepository,
                                       PerfilRepository perfilRepository, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.asistenteIaService = asistenteIaService;
        this.habilidadService = habilidadService;
        this.certificadoRepository = certificadoRepository;
        this.perfilRepository = perfilRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────── Lectura ───────────────────────────────

    /** El resumen guardado del perfil (con fechas y si quedó desactualizado), o vacío si nunca se generó. */
    public Optional<ResumenProfesionalIa> obtener(Long perfilId) {
        List<Map<String, Object>> filas;
        try {
            filas = jdbc.queryForList("SELECT contenido_json, firma_fuentes, fuentes_usadas, generado_en, editado_en "
                    + "FROM perfil_resumen_ia WHERE perfil_id = ?", perfilId);
        } catch (Exception e) {
            log.warn("Resumen IA: no se pudo leer el resumen del perfil {}: {}", perfilId, e.getMessage());
            return Optional.empty();
        }
        if (filas.isEmpty()) return Optional.empty();
        Map<String, Object> f = filas.get(0);
        try {
            ResumenProfesionalIa r = objectMapper.readValue((String) f.get("contenido_json"), ResumenProfesionalIa.class);
            r.setGeneradoEnLabel(fecha(f.get("generado_en")));
            r.setEditadoEnLabel(fecha(f.get("editado_en")));
            r.setFuentesUsadas(f.get("fuentes_usadas") instanceof Number n ? n.intValue() : 0);
            String firmaGuardada = (String) f.get("firma_fuentes");
            r.setDesactualizado(firmaGuardada != null && !firmaGuardada.equals(firma(perfilId)));
            return Optional.of(r);
        } catch (JsonProcessingException e) {
            log.warn("Resumen IA: el JSON guardado del perfil {} no se pudo leer: {}", perfilId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Cuántas constancias tiene el perfil (PDF y/o link) - para saber si se puede generar. */
    public int contarConstancias(Long perfilId) {
        return habilidadService.habilidadesConValidacion(perfilId).stream()
                .mapToInt(h -> h.getCertificados().size()).sum();
    }

    // ─────────────────────────── Reunir evidencias ───────────────────────────

    /** Texto para Claude + PDF adjuntos (subidos o descargados de Drive) de un grupo de habilidades. */
    private record Fuentes(String texto, List<AsistenteIaService.Archivo> pdfs, int total) {}

    private Fuentes reunir(List<HabilidadPerfilFila> habilidades) {
        List<AsistenteIaService.Archivo> pdfs = new ArrayList<>();
        StringBuilder msg = new StringBuilder();
        long bytesPdf = 0;
        int links = 0;
        int fuentes = 0;
        int numeroDoc = 0;
        for (HabilidadPerfilFila h : habilidades) {
            msg.append("\n- ").append(h.getNombre()).append(" (").append(h.getCategoriaNombre()).append(") · nivel declarado: ")
                    .append(h.getNivelLabel()).append(h.isValidada() ? " · VALIDADA por el Resource Manager" : " · sin validar");
            if (h.getCertificados().isEmpty()) {
                msg.append("\n    Sin constancia adjunta.");
                continue;
            }
            for (CertificadoFila c : h.getCertificados()) {
                fuentes++;
                if (c.tieneArchivo()) {
                    CertificadoHabilidad cert = certificadoRepository.findById(c.id()).orElse(null);
                    byte[] datos = cert != null ? cert.getContenidoArchivo() : null;
                    if (datos == null || datos.length == 0) {
                        msg.append("\n    PDF \"").append(c.nombreArchivo()).append("\": no se pudo leer.");
                    } else if (pdfs.size() >= MAX_PDFS || bytesPdf + datos.length > MAX_BYTES_PDF_TOTAL) {
                        msg.append("\n    PDF \"").append(c.nombreArchivo()).append("\": no se adjuntó por tamaño (solo considera su nombre).");
                    } else {
                        numeroDoc++;
                        bytesPdf += datos.length;
                        pdfs.add(new AsistenteIaService.Archivo(c.nombreArchivo(), "application/pdf", datos));
                        msg.append("\n    PDF \"").append(c.nombreArchivo()).append("\" → es el DOCUMENTO ").append(numeroDoc)
                                .append(" adjunto (en el mismo orden).");
                    }
                }
                if (c.tieneEnlace()) {
                    msg.append("\n    Link: ").append(c.urlArchivo().trim());
                    // Link de Google Drive a un PDF compartido "con cualquier persona": se descarga y Claude lo lee completo.
                    byte[] pdfDrive = pdfs.size() < MAX_PDFS ? descargarPdfDeDrive(c.urlArchivo().trim()) : null;
                    if (pdfDrive != null && bytesPdf + pdfDrive.length <= MAX_BYTES_PDF_TOTAL) {
                        numeroDoc++;
                        bytesPdf += pdfDrive.length;
                        pdfs.add(new AsistenteIaService.Archivo("drive-" + numeroDoc + ".pdf", "application/pdf", pdfDrive));
                        msg.append("\n    (Es un PDF en Google Drive: es el DOCUMENTO ").append(numeroDoc).append(" adjunto.)");
                    } else if (links < MAX_LINKS) {
                        links++;
                        String texto = leerPagina(c.urlArchivo().trim());
                        msg.append(texto != null
                                ? "\n    Texto de la página (recortado): \"" + texto + "\""
                                : "\n    (No se pudo leer el contenido de la página: puede ser privada o necesitar iniciar sesión.)");
                    }
                }
            }
        }
        return new Fuentes(msg.toString(), pdfs, fuentes);
    }

    // ─────────────────────── Resumen profesional (automático) ───────────────────────

    /** Perfiles cuyo resumen se está actualizando ahora mismo (para mostrar "Actualizando…"). */
    private final java.util.Set<Long> enProceso = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService segundoPlano = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "resumen-ia");
        t.setDaemon(true);
        return t;
    });

    public boolean estaActualizando(Long perfilId) {
        return enProceso.contains(perfilId);
    }

    /**
     * Se llama cuando el Resource Manager valida una habilidad (y cuando el
     * colaborador cambia o quita una habilidad ya validada): vuelve a armar
     * el resumen en segundo plano, sin hacer esperar a nadie. Si ya hay uno
     * en curso para ese perfil, no encola otro.
     */
    public void actualizarEnSegundoPlano(Long perfilId) {
        if (perfilId == null || !asistenteIaService.configurado()) return;
        if (!enProceso.add(perfilId)) return;
        segundoPlano.submit(() -> {
            try {
                generar(perfilId);
            } catch (Exception e) {
                log.warn("Resumen IA: no se pudo actualizar el resumen del perfil {}: {}", perfilId, e.getMessage());
            } finally {
                enProceso.remove(perfilId);
            }
        });
    }

    /**
     * Arma el resumen con Claude SOLO con las habilidades que el Resource
     * Manager ya validó (y sus constancias) y lo guarda. El colaborador no lo
     * puede editar: lo ve tal cual en "Resumen profesional". Si ya no queda
     * ninguna habilidad validada, se borra el resumen.
     */
    public void generar(Long perfilId) {
        Perfil perfil = perfilRepository.buscarConUsuario(perfilId).orElse(null);
        if (perfil == null) return;
        List<HabilidadPerfilFila> validadas = habilidadService.habilidadesConValidacion(perfilId).stream()
                .filter(HabilidadPerfilFila::isValidada).toList();
        if (validadas.isEmpty()) {
            eliminar(perfilId);
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("COLABORADOR: ").append(perfil.getUsuario().getNombreCompleto());
        if (perfil.getCargo() != null && !perfil.getCargo().isBlank()) msg.append(" — cargo: ").append(perfil.getCargo());
        if (perfil.getExperienciaAnios() != null && perfil.getExperienciaAnios() > 0) msg.append(" — ").append(perfil.getExperienciaAnios()).append(" año(s) de experiencia declarados");
        msg.append("\n\nHABILIDADES VALIDADAS POR EL RESOURCE MANAGER Y SUS CONSTANCIAS:\n");
        Fuentes f = reunir(validadas);
        msg.append(f.texto());

        String prompt = "Eres el asistente de SkillBridge AI, una plataforma interna de gestión de talento. A partir de las "
                + "habilidades de un colaborador que el Resource Manager YA VALIDÓ y de las constancias que las respaldan (los PDF "
                + "van como documentos, en el orden indicado; de los links tienes la dirección y, si se pudo, el texto de la página), "
                + "arma un RESUMEN PROFESIONAL corto, tipo CV, para que el Resource Manager y los Project Managers entiendan su "
                + "perfil sin abrir cada archivo.\n\n"
                + "Responde SOLO con un JSON válido (sin markdown, sin ```), con esta forma exacta:\n"
                + "{\"titular\": string, \"resumen\": string, \"fortalezas\": [string], "
                + "\"evidencias\": [{\"habilidad\": string, \"fuente\": string, \"tipo\": \"pdf\"|\"link\"|\"pdf+link\", \"detalle\": string}], "
                + "\"sugerencias\": [string]}\n\n"
                + "Reglas:\n"
                + "- Todo en español neutro y profesional, en tercera persona, sin exagerar ni adornar.\n"
                + "- \"titular\": una sola línea (máx. 110 caracteres), p. ej. \"Desarrollador frontend con base en React y Docker\".\n"
                + "- \"resumen\": 3 a 5 líneas que digan qué sabe hacer y qué lo respalda.\n"
                + "- \"fortalezas\": 3 a 5 viñetas cortas, solo lo que esté respaldado por las habilidades validadas y sus constancias.\n"
                + "- \"evidencias\": un elemento por cada constancia, con la habilidad tal como se llama en la lista, un nombre "
                + "corto para \"fuente\" (emisor y curso si aparecen; si no, el nombre del archivo o el dominio) y en \"detalle\" "
                + "qué acredita en 1 o 2 líneas (emisor, fecha, horas o nota si aparecen). Si una habilidad validada no tiene "
                + "constancia, no la pongas en \"evidencias\".\n"
                + "- \"sugerencias\": 1 a 3 ideas concretas para que el colaborador refuerce su perfil (p. ej. una habilidad "
                + "validada sin constancia o una certificación que complemente lo que ya tiene). Lista vacía si no hace falta.\n"
                + "- No inventes datos que no estén en las constancias o en la lista.";

        AsistenteIaService.Resultado r = asistenteIaService.consultarConDocumentos(prompt, msg.toString(), f.pdfs(), MAX_OUTPUT_TOKENS);
        if (!r.ok()) {
            throw new OperacionInvalidaException("La IA no pudo generar el resumen en este momento.");
        }
        ResumenProfesionalIa resumen = parsear(r.texto(), ResumenProfesionalIa.class);
        normalizar(resumen);
        if (resumen.getTitular() == null && resumen.getResumen() == null) {
            throw new OperacionInvalidaException("La IA devolvió una respuesta vacía.");
        }
        guardar(perfilId, resumen, firma(perfilId), f.total());
    }

    public void eliminar(Long perfilId) {
        jdbc.update("DELETE FROM perfil_resumen_ia WHERE perfil_id = ?", perfilId);
    }

    // ─────────────────── Resumen previo de una constancia (para el RM) ───────────────────

    /**
     * Lo que el Resource Manager ve al validar: qué es la constancia de UNA
     * habilidad y si de verdad la respalda. Se guarda (constancia_analisis_ia)
     * con la huella de la constancia, así no se vuelve a llamar a Claude
     * mientras el colaborador no la cambie.
     */
    public Optional<AnalisisConstanciaIa> analisisGuardado(Long perfilId, Long habilidadId) {
        HabilidadPerfilFila h = habilidad(perfilId, habilidadId);
        if (h == null || h.getCertificados().isEmpty()) return Optional.empty();
        return leerAnalisis(perfilId, habilidadId, firmaConstancia(perfilId, h));
    }

    /** Los análisis vigentes de todas las habilidades del perfil, por habilidadId. */
    public Map<Long, AnalisisConstanciaIa> analisisGuardados(Long perfilId) {
        Map<Long, AnalisisConstanciaIa> out = new java.util.HashMap<>();
        for (HabilidadPerfilFila h : habilidadService.habilidadesConValidacion(perfilId)) {
            if (h.getCertificados().isEmpty()) continue;
            leerAnalisis(perfilId, h.getHabilidadId(), firmaConstancia(perfilId, h)).ifPresent(a -> out.put(h.getHabilidadId(), a));
        }
        return out;
    }

    public AnalisisConstanciaIa analizarConstancia(Long perfilId, Long habilidadId) {
        HabilidadPerfilFila h = habilidad(perfilId, habilidadId);
        if (h == null) throw new OperacionInvalidaException("Esa habilidad ya no está declarada en el perfil.");
        if (h.getCertificados().isEmpty()) throw new OperacionInvalidaException("Esta habilidad no tiene constancia adjunta.");
        String firma = firmaConstancia(perfilId, h);
        Optional<AnalisisConstanciaIa> previo = leerAnalisis(perfilId, habilidadId, firma);
        if (previo.isPresent()) return previo.get();
        if (!asistenteIaService.configurado()) {
            throw new OperacionInvalidaException("La IA todavía no está configurada en este servidor (falta la clave de Claude).");
        }

        Fuentes f = reunir(List.of(h));
        String prompt = "Eres el asistente de SkillBridge AI. Un Resource Manager está por VALIDAR la habilidad que declaró un "
                + "colaborador y necesita un resumen previo de la constancia que adjuntó (PDF como documento y/o link), para no "
                + "tener que abrirla completa. Responde SOLO con un JSON válido (sin markdown, sin ```), con esta forma exacta:\n"
                + "{\"veredicto\": \"respalda\"|\"parcial\"|\"no_relacionada\"|\"no_legible\", \"resumen\": string, "
                + "\"emisor\": string|null, \"curso\": string|null, \"titular\": string|null, \"fecha\": string|null, "
                + "\"duracion\": string|null, \"nota\": string|null, \"alertas\": [string], "
                + "\"nivel_sugerido\": \"basico\"|\"intermedio\"|\"avanzado\"|\"experto\"|null, \"motivo_nivel\": string|null}\n"
                + "Reglas: en español, breve y objetivo. \"resumen\": 2 o 3 líneas que digan qué es la constancia y qué acredita. "
                + "\"veredicto\": \"respalda\" si acredita la habilidad al nivel declarado o cerca; \"parcial\" si la acredita solo "
                + "en parte o a un nivel menor; \"no_relacionada\" si trata de otro tema; \"no_legible\" si no se pudo ver el contenido. "
                + "\"titular\": a nombre de quién está la constancia, si aparece. \"alertas\": cosas a revisar (nombre distinto al "
                + "del colaborador, fecha muy antigua, constancia sin datos verificables, link no legible). "
                + "\"nivel_sugerido\": el nivel que de verdad acredita la constancia por su contenido, duración y profundidad, "
                + "aunque sea MAYOR o MENOR que el declarado (basico: introducción o curso corto; intermedio: curso completo con "
                + "práctica; avanzado: especialización, proyecto real o certificación técnica; experto: certificación profesional "
                + "exigente o experiencia sostenida); null si es no_relacionada o no_legible. \"motivo_nivel\": una línea que lo explique. "
                + "Hoy es " + java.time.LocalDate.now() + ": no marques como alerta una fecha anterior o igual a hoy. No inventes datos.";
        Perfil perfil = perfilRepository.buscarConUsuario(perfilId).orElse(null);
        String msg = "COLABORADOR: " + (perfil != null ? perfil.getUsuario().getNombreCompleto() : "—")
                + "\nHABILIDAD A VALIDAR Y SU CONSTANCIA:" + f.texto();
        AsistenteIaService.Resultado r = asistenteIaService.consultarConDocumentos(prompt, msg, f.pdfs(), 1500);
        if (!r.ok()) throw new OperacionInvalidaException("La IA no pudo revisar la constancia en este momento. Intenta de nuevo en un minuto.");
        AnalisisConstanciaIa a = parsear(r.texto(), AnalisisConstanciaIa.class);
        a.normalizar();
        try {
            jdbc.update("INSERT INTO constancia_analisis_ia (perfil_id, habilidad_id, firma, contenido_json, generado_en) "
                    + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE firma = VALUES(firma), contenido_json = VALUES(contenido_json), "
                    + "generado_en = VALUES(generado_en)", perfilId, habilidadId, firma, objectMapper.writeValueAsString(a),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Resumen IA: no se pudo guardar el análisis de la constancia {}/{}: {}", perfilId, habilidadId, e.getMessage());
        }
        a.setGeneradoEnLabel(LocalDateTime.now().format(FECHA));
        return a;
    }

    private Optional<AnalisisConstanciaIa> leerAnalisis(Long perfilId, Long habilidadId, String firma) {
        try {
            List<Map<String, Object>> filas = jdbc.queryForList("SELECT contenido_json, firma, generado_en FROM constancia_analisis_ia "
                    + "WHERE perfil_id = ? AND habilidad_id = ?", perfilId, habilidadId);
            if (filas.isEmpty() || !firma.equals(filas.get(0).get("firma"))) return Optional.empty();
            AnalisisConstanciaIa a = objectMapper.readValue((String) filas.get(0).get("contenido_json"), AnalisisConstanciaIa.class);
            // "No se pudo leer" no se reutiliza: suele ser un link de Drive que todavía era privado;
            // si el colaborador ya lo hizo público, al volver a pedirlo la IA sí lo puede leer.
            if ("no_legible".equals(a.getVeredicto())) return Optional.empty();
            a.setGeneradoEnLabel(fecha(filas.get(0).get("generado_en")));
            return Optional.of(a);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private HabilidadPerfilFila habilidad(Long perfilId, Long habilidadId) {
        return habilidadService.habilidadesConValidacion(perfilId).stream()
                .filter(x -> x.getHabilidadId().equals(habilidadId)).findFirst().orElse(null);
    }

    private String firmaConstancia(Long perfilId, HabilidadPerfilFila h) {
        Map<Long, Object> fechas = new java.util.HashMap<>();
        for (Object[] f : certificadoRepository.fechasDePerfil(perfilId)) fechas.put((Long) f[0], f[1]);
        // "v2|": versión del análisis (ahora incluye nivel sugerido); los guardados antes se vuelven a pedir una vez.
        StringBuilder sb = new StringBuilder("v2|").append(h.getHabilidadId()).append(':').append(h.getNivelNumero()).append(';');
        h.getCertificados().stream().sorted((a, b) -> Long.compare(a.id(), b.id())).forEach(c ->
                sb.append(c.id()).append('|').append(c.tieneArchivo()).append('|').append(c.urlArchivo())
                  .append('|').append(fechas.get(c.id())).append(';'));
        return sha256(sb.toString());
    }

    // ─────────────────────────────── Internos ───────────────────────────────

    private void guardar(Long perfilId, ResumenProfesionalIa r, String firma, int fuentes) {
        String json;
        try {
            json = objectMapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new OperacionInvalidaException("No se pudo guardar el resumen.");
        }
        jdbc.update("INSERT INTO perfil_resumen_ia (perfil_id, contenido_json, firma_fuentes, fuentes_usadas, generado_en, editado_en) "
                + "VALUES (?, ?, ?, ?, ?, NULL) ON DUPLICATE KEY UPDATE contenido_json = VALUES(contenido_json), "
                + "firma_fuentes = VALUES(firma_fuentes), fuentes_usadas = VALUES(fuentes_usadas), "
                + "generado_en = VALUES(generado_en), editado_en = NULL",
                perfilId, json, firma, fuentes, Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * Huella de las habilidades y constancias actuales: si cambia (agregó,
     * quitó o reemplazó una constancia, o cambió un nivel), el resumen
     * guardado se marca como desactualizado para sugerir regenerarlo.
     */
    private String firma(Long perfilId) {
        StringBuilder sb = new StringBuilder();
        Map<Long, Object> fechas = new java.util.HashMap<>();
        for (Object[] f : certificadoRepository.fechasDePerfil(perfilId)) fechas.put((Long) f[0], f[1]);
        habilidadService.habilidadesConValidacion(perfilId).stream()
                .filter(HabilidadPerfilFila::isValidada)
                .sorted((a, b) -> Long.compare(a.getHabilidadId(), b.getHabilidadId()))
                .forEach(h -> {
                    sb.append(h.getHabilidadId()).append(':').append(h.getNivelNumero()).append(';');
                    h.getCertificados().stream().sorted((a, b) -> Long.compare(a.id(), b.id())).forEach(c ->
                            sb.append(c.id()).append('|').append(c.tieneArchivo()).append('|')
                              .append(c.urlArchivo()).append('|').append(fechas.get(c.id())).append(';'));
                });
        return sha256(sb.toString());
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private <T> T parsear(String texto, Class<T> tipo) {
        String t = texto == null ? "" : texto.trim();
        if (t.startsWith("```")) {
            int ini = t.indexOf('\n');
            int fin = t.lastIndexOf("```");
            if (ini > 0 && fin > ini) t = t.substring(ini + 1, fin).trim();
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        if (a >= 0 && b > a) t = t.substring(a, b + 1);
        try {
            return objectMapper.readValue(t, tipo);
        } catch (JsonProcessingException e) {
            log.warn("Resumen IA: respuesta no interpretable: {}", t.length() > 300 ? t.substring(0, 300) + "..." : t);
            throw new OperacionInvalidaException("La IA devolvió una respuesta que no se pudo interpretar. Intenta de nuevo.");
        }
    }

    private static void normalizar(ResumenProfesionalIa r) {
        r.setTitular(recortar(r.getTitular(), 160));
        r.setResumen(recortar(r.getResumen(), 1500));
        r.setFortalezas(r.getFortalezas().stream().map(s -> recortar(s, 200)).filter(java.util.Objects::nonNull).limit(8).toList());
        r.setSugerencias(r.getSugerencias().stream().map(s -> recortar(s, 250)).filter(java.util.Objects::nonNull).limit(6).toList());
        List<Evidencia> ev = new ArrayList<>();
        for (Evidencia e : r.getEvidencias()) {
            if (e == null) continue;
            String tipo = e.getTipo() == null ? "" : e.getTipo().toLowerCase(Locale.ROOT);
            e.setTipo(tipo.contains("pdf") && tipo.contains("link") ? "pdf+link" : tipo.contains("pdf") ? "pdf" : "link");
            e.setHabilidad(recortar(e.getHabilidad(), 80));
            e.setFuente(recortar(e.getFuente(), 150));
            e.setDetalle(recortar(e.getDetalle(), 400));
            if (e.getDetalle() != null || e.getFuente() != null) ev.add(e);
        }
        r.setEvidencias(ev);
    }

    // ───────────── Lectura de links (solo páginas públicas, con límites) ─────────────

    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_DESC = Pattern.compile(
            "(?is)<meta[^>]+(?:name|property)\\s*=\\s*[\"'](?:og:)?description[\"'][^>]*content\\s*=\\s*[\"']([^\"']*)[\"']");

    /**
     * Descarga el texto de una página pública para que Claude sepa qué es la
     * constancia. Solo http/https hacia direcciones públicas (nunca la red
     * interna del servidor), sin seguir más de 2 redirecciones, con tiempo y
     * tamaño limitados. Si algo falla devuelve null y Claude trabaja solo con
     * la dirección.
     */
    private String leerPagina(String url) {
        try {
            URI uri = URI.create(url);
            for (int salto = 0; salto < 3; salto++) {
                if (!esPublica(uri)) return null;
                Respuesta resp = pedir(clientePaginas, uri, "text/html,application/xhtml+xml");
                int st = resp.estado();
                if (st >= 300 && st < 400) {
                    if (resp.ubicacion() == null) return null;
                    uri = uri.resolve(resp.ubicacion());
                    continue;
                }
                if (st != 200) return null;
                String tipo = resp.tipo() == null ? "" : resp.tipo();
                if (!tipo.contains("html") && !tipo.contains("text")) return null;
                byte[] cuerpo = resp.cuerpo();
                String html = new String(cuerpo, 0, Math.min(cuerpo.length, 600_000), StandardCharsets.UTF_8);
                return textoDeHtml(html);
            }
        } catch (Exception e) {
            log.debug("Resumen IA: no se pudo leer {}: {}", url, e.getMessage());
        }
        return null;
    }

    private static final Pattern DRIVE_ID = Pattern.compile(
            "https?://(?:drive|docs)\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:[^#]*&)?id=)([A-Za-z0-9_-]{10,})");
    private static final java.util.Set<String> HOSTS_DRIVE = java.util.Set.of(
            "drive.google.com", "docs.google.com", "drive.usercontent.google.com");

    /**
     * Si el link es de un archivo de Google Drive compartido públicamente,
     * descarga el archivo (solo si es un PDF de hasta 10 MB) para que Claude
     * lo lea. Si el archivo es privado, Drive responde con la página de
     * inicio de sesión y se devuelve null.
     */
    private byte[] descargarPdfDeDrive(String url) {
        Matcher m = DRIVE_ID.matcher(url);
        if (!m.find()) return null;
        try {
            URI uri = URI.create("https://drive.google.com/uc?export=download&id=" + m.group(1));
            for (int salto = 0; salto < 4; salto++) {
                if (uri.getHost() == null || !HOSTS_DRIVE.contains(uri.getHost().toLowerCase(Locale.ROOT))) return null;
                Respuesta resp = pedir(clienteDrive, uri, null);
                int st = resp.estado();
                if (st >= 300 && st < 400) {
                    if (resp.ubicacion() == null) return null;
                    uri = uri.resolve(resp.ubicacion());
                    continue;
                }
                byte[] cuerpo = resp.cuerpo();
                if (st == 200 && cuerpo.length > 5 && cuerpo.length <= 10L * 1024 * 1024
                        && new String(cuerpo, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-")) {
                    return cuerpo;
                }
                return null;
            }
        } catch (Exception e) {
            log.debug("Resumen IA: no se pudo descargar el PDF de Drive {}: {}", url, e.getMessage());
        }
        return null;
    }

    private static boolean esPublica(URI uri) {
        try {
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!esquema.equals("http") && !esquema.equals("https")) return false;
            if (uri.getHost() == null) return false;
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                        || a.isAnyLocalAddress() || a.isMulticastAddress()) return false;
                byte[] b = a.getAddress();
                if (b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64) return false;   // 100.64.0.0/10
                if (b.length == 16 && (b[0] & 0xFE) == 0xFC) return false;                          // fc00::/7
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String textoDeHtml(String html) {
        StringBuilder sb = new StringBuilder();
        Matcher t = TITLE.matcher(html);
        if (t.find()) sb.append(limpiar(t.group(1))).append(". ");
        Matcher d = META_DESC.matcher(html);
        if (d.find()) sb.append(limpiar(d.group(1))).append(". ");
        String cuerpo = html.replaceAll("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ");
        sb.append(limpiar(cuerpo));
        String s = sb.toString().replace('"', '\'').trim();
        if (s.isBlank()) return null;
        return s.length() > MAX_CARACTERES_LINK ? s.substring(0, MAX_CARACTERES_LINK) + "…" : s;
    }

    private static String limpiar(String s) {
        return s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "'").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ").trim();
    }

    // ───────────── Utilidades ─────────────

    private static String valor(List<String> lista, int i) {
        return lista != null && i < lista.size() ? lista.get(i) : null;
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    /** Una viñeta por línea (quita guiones/puntos iniciales que el usuario haya escrito). */
    private static List<String> lineas(String texto, int maxLineas, int maxCaracteres) {
        List<String> out = new ArrayList<>();
        if (texto == null) return out;
        for (String l : texto.split("\\r?\\n")) {
            String t = recortar(l.replaceFirst("^\\s*[-•*·]\\s*", ""), maxCaracteres);
            if (t != null) out.add(t);
            if (out.size() >= maxLineas) break;
        }
        return out;
    }

    private static String fecha(Object o) {
        LocalDateTime f = o instanceof Timestamp ts ? ts.toLocalDateTime() : o instanceof LocalDateTime l ? l : null;
        return f == null ? null : f.format(FECHA);
    }
}
