package com.skillbridge.ai.dto;

import java.util.Locale;

/**
 * Metadatos de un adjunto del foro (sin el binario). Lo arma
 * ForoAdjuntoRepository.listarInfo con un "select new", para no cargar
 * LONGBLOBs al pintar un hilo o la lista de hilos.
 */
public class ForoAdjuntoInfo {

    private final Long id;
    private final Long publicacionId;
    private final String tipo;
    private final String nombre;
    private final String mimeTipo;
    private final Integer tamanoBytes;
    private final String url;

    public ForoAdjuntoInfo(Long id, Long publicacionId, String tipo, String nombre, String mimeTipo,
                            Integer tamanoBytes, String url) {
        this.id = id;
        this.publicacionId = publicacionId;
        this.tipo = tipo;
        this.nombre = nombre;
        this.mimeTipo = mimeTipo;
        this.tamanoBytes = tamanoBytes;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getMimeTipo() {
        return mimeTipo;
    }

    public Integer getTamanoBytes() {
        return tamanoBytes;
    }

    public String getUrl() {
        return url;
    }

    public boolean isEsEnlace() {
        return "enlace".equals(tipo);
    }

    public boolean isEsImagen() {
        return !isEsEnlace() && mimeTipo != null && mimeTipo.startsWith("image/");
    }

    /** Extensión en mayúsculas para la etiqueta del archivo (PDF, DOCX...). */
    public String getExtension() {
        if (isEsEnlace()) return "LINK";
        int punto = nombre != null ? nombre.lastIndexOf('.') : -1;
        if (punto < 0 || punto == nombre.length() - 1) return "ARCHIVO";
        return nombre.substring(punto + 1).toUpperCase(Locale.ROOT);
    }

    /** Familia de archivo para elegir color/ícono en la vista. */
    public String getFamilia() {
        if (isEsEnlace()) return "enlace";
        if (isEsImagen()) return "imagen";
        String ext = getExtension();
        switch (ext) {
            case "PDF": return "pdf";
            case "DOC": case "DOCX": case "TXT": case "MD": return "documento";
            case "XLS": case "XLSX": case "CSV": return "hoja";
            case "PPT": case "PPTX": return "presentacion";
            case "ZIP": case "RAR": case "7Z": return "comprimido";
            default: return "archivo";
        }
    }

    /** Tono del design system para la etiqueta del archivo (ver .tone-* en foros.css). */
    public String getTono() {
        switch (getFamilia()) {
            case "pdf": return "red";
            case "documento": return "blue";
            case "hoja": return "green";
            case "presentacion": return "amber";
            case "enlace": return "violet";
            case "imagen": return "violet";
            default: return "neutral";
        }
    }

    public String getTamanoLabel() {
        if (tamanoBytes == null) return "";
        if (tamanoBytes < 1024) return tamanoBytes + " B";
        if (tamanoBytes < 1024 * 1024) return Math.round(tamanoBytes / 1024.0) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", tamanoBytes / (1024.0 * 1024.0));
    }

    /** Dominio de un enlace, para mostrarlo debajo del título. */
    public String getDominio() {
        if (url == null) return "";
        String sinEsquema = url.replaceFirst("^https?://", "");
        int barra = sinEsquema.indexOf('/');
        String host = barra >= 0 ? sinEsquema.substring(0, barra) : sinEsquema;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
