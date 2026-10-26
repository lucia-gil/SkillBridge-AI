package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Todo lo que muestra el perfil profesional de un colaborador
 * (resource-manager/colaborador-detalle.html), ya calculado por
 * PerfilVistaService: la plantilla solo pinta.
 */
public class PerfilColaboradorVista {

    // ── Identidad y contacto ──
    private Long perfilId;
    private Long usuarioId;
    private String nombre;
    private String iniciales;
    private boolean tieneFoto;
    private String correo;
    private String cargo;
    private String telefono;
    private String telefonoHref;
    private String ubicacion;
    private String miembroDesde;
    private String estadoLabel;
    private String estadoBadgeClass;
    private boolean cuentaActiva;

    // ── Presentación ──
    private String biografia;
    private List<String> etiquetas = List.of();
    private List<Enlace> enlaces = List.of();
    private int experienciaAnios;
    private int disponibilidad;

    // ── Carga ──
    private int ocupacion;
    private int limite;
    private String ocupacionEstado;
    private String ocupacionBadgeClass;
    private String ocupacionClave;

    // ── Proyectos ──
    private List<MiProyectoFila> proyectos = List.of();
    private List<MiProyectoFila> proyectosActuales = List.of();
    private int proyectosDistintos;
    private List<TecnologiaUso> tecnologias = List.of();

    // ── Habilidades y certificaciones ──
    private List<GrupoHabilidades> grupos = List.of();
    private int habilidadesTotal;
    private int habilidadesValidadas;
    private int habilidadesPendientes;
    private List<Certificacion> certificaciones = List.of();

    /** Un enlace externo del perfil (LinkedIn, GitHub, portafolio). */
    public static class Enlace {
        private final String tipo;
        private final String nombre;
        private final String url;
        private final String legible;

        public Enlace(String tipo, String nombre, String url, String legible) {
            this.tipo = tipo;
            this.nombre = nombre;
            this.url = url;
            this.legible = legible;
        }

        public String getTipo() { return tipo; }
        public String getNombre() { return nombre; }
        public String getUrl() { return url; }
        public String getLegible() { return legible; }
    }

    /** Habilidades de una categoría; tono = id de categoría % 8 (tonos-categoria.css). */
    public static class GrupoHabilidades {
        private final String categoria;
        private final int tono;
        private final List<HabilidadPerfilFila> habilidades;

        public GrupoHabilidades(String categoria, int tono, List<HabilidadPerfilFila> habilidades) {
            this.categoria = categoria;
            this.tono = tono;
            this.habilidades = habilidades;
        }

        public String getCategoria() { return categoria; }
        public int getTono() { return tono; }
        public List<HabilidadPerfilFila> getHabilidades() { return habilidades; }
    }

    /** Una constancia adjunta, con la habilidad que respalda. */
    public static class Certificacion {
        private final CertificadoFila certificado;
        private final String habilidad;
        private final String nivelLabel;
        private final boolean validada;
        private final int tono;
        private final String fechaLabel;

        public Certificacion(CertificadoFila certificado, String habilidad, String nivelLabel, boolean validada,
                             int tono, String fechaLabel) {
            this.certificado = certificado;
            this.habilidad = habilidad;
            this.nivelLabel = nivelLabel;
            this.validada = validada;
            this.tono = tono;
            this.fechaLabel = fechaLabel;
        }

        public CertificadoFila getCertificado() { return certificado; }
        public String getHabilidad() { return habilidad; }
        public String getNivelLabel() { return nivelLabel; }
        public boolean isValidada() { return validada; }
        public int getTono() { return tono; }
        public String getFechaLabel() { return fechaLabel; }
    }

    /** Tecnología usada en los proyectos de su historial y en cuántos. */
    public static class TecnologiaUso {
        private final String nombre;
        private final int proyectos;

        public TecnologiaUso(String nombre, int proyectos) {
            this.nombre = nombre;
            this.proyectos = proyectos;
        }

        public String getNombre() { return nombre; }
        public int getProyectos() { return proyectos; }
    }

    // ── Derivados para la plantilla ──

    public boolean isTieneContacto() {
        return !telefono.isEmpty() || !ubicacion.isEmpty();
    }

    public int getLibre() {
        return Math.max(0, limite - ocupacion);
    }

    /** Ángulo (0–360) del anillo de ocupación respecto del límite. */
    public int getOcupacionGrados() {
        if (limite <= 0) return 0;
        return (int) Math.round(Math.min(1.0, ocupacion / (double) limite) * 360);
    }

    public int getProyectosAnteriores() {
        return proyectos.size() - proyectosActuales.size();
    }

    // ── Getters / setters ──

    public Long getPerfilId() { return perfilId; }
    public void setPerfilId(Long v) { this.perfilId = v; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long v) { this.usuarioId = v; }
    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v; }
    public String getIniciales() { return iniciales; }
    public void setIniciales(String v) { this.iniciales = v; }
    public boolean isTieneFoto() { return tieneFoto; }
    public void setTieneFoto(boolean v) { this.tieneFoto = v; }
    public String getCorreo() { return correo; }
    public void setCorreo(String v) { this.correo = v; }
    public String getCargo() { return cargo; }
    public void setCargo(String v) { this.cargo = v != null ? v : ""; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String v) { this.telefono = v != null ? v : ""; }
    public String getTelefonoHref() { return telefonoHref; }
    public void setTelefonoHref(String v) { this.telefonoHref = v; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String v) { this.ubicacion = v != null ? v : ""; }
    public String getMiembroDesde() { return miembroDesde; }
    public void setMiembroDesde(String v) { this.miembroDesde = v; }
    public String getEstadoLabel() { return estadoLabel; }
    public void setEstadoLabel(String v) { this.estadoLabel = v; }
    public String getEstadoBadgeClass() { return estadoBadgeClass; }
    public void setEstadoBadgeClass(String v) { this.estadoBadgeClass = v; }
    public boolean isCuentaActiva() { return cuentaActiva; }
    public void setCuentaActiva(boolean v) { this.cuentaActiva = v; }
    public String getBiografia() { return biografia; }
    public void setBiografia(String v) { this.biografia = v != null ? v : ""; }
    public List<String> getEtiquetas() { return etiquetas; }
    public void setEtiquetas(List<String> v) { this.etiquetas = v; }
    public List<Enlace> getEnlaces() { return enlaces; }
    public void setEnlaces(List<Enlace> v) { this.enlaces = v; }
    public int getExperienciaAnios() { return experienciaAnios; }
    public void setExperienciaAnios(int v) { this.experienciaAnios = v; }
    public int getDisponibilidad() { return disponibilidad; }
    public void setDisponibilidad(int v) { this.disponibilidad = v; }
    public int getOcupacion() { return ocupacion; }
    public void setOcupacion(int v) { this.ocupacion = v; }
    public int getLimite() { return limite; }
    public void setLimite(int v) { this.limite = v; }
    public String getOcupacionEstado() { return ocupacionEstado; }
    public void setOcupacionEstado(String v) { this.ocupacionEstado = v; }
    public String getOcupacionBadgeClass() { return ocupacionBadgeClass; }
    public void setOcupacionBadgeClass(String v) { this.ocupacionBadgeClass = v; }
    public String getOcupacionClave() { return ocupacionClave; }
    public void setOcupacionClave(String v) { this.ocupacionClave = v; }
    public List<MiProyectoFila> getProyectos() { return proyectos; }
    public void setProyectos(List<MiProyectoFila> v) { this.proyectos = v; }
    public List<MiProyectoFila> getProyectosActuales() { return proyectosActuales; }
    public void setProyectosActuales(List<MiProyectoFila> v) { this.proyectosActuales = v; }
    public int getProyectosDistintos() { return proyectosDistintos; }
    public void setProyectosDistintos(int v) { this.proyectosDistintos = v; }
    public List<TecnologiaUso> getTecnologias() { return tecnologias; }
    public void setTecnologias(List<TecnologiaUso> v) { this.tecnologias = v; }
    public List<GrupoHabilidades> getGrupos() { return grupos; }
    public void setGrupos(List<GrupoHabilidades> v) { this.grupos = v; }
    public int getHabilidadesTotal() { return habilidadesTotal; }
    public void setHabilidadesTotal(int v) { this.habilidadesTotal = v; }
    public int getHabilidadesValidadas() { return habilidadesValidadas; }
    public void setHabilidadesValidadas(int v) { this.habilidadesValidadas = v; }
    public int getHabilidadesPendientes() { return habilidadesPendientes; }
    public void setHabilidadesPendientes(int v) { this.habilidadesPendientes = v; }
    public List<Certificacion> getCertificaciones() { return certificaciones; }
    public void setCertificaciones(List<Certificacion> v) { this.certificaciones = v; }
}
