package com.skillbridge.ai.dto;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Una fila de resource-manager/colaboradores.html: datos básicos +
 * ocupación (reusa la misma lógica de OcupacionColaboradorFila) + el
 * detalle completo de sus habilidades (con estado de validación) para que
 * el modal "Ver perfil" se arme en el navegador a partir de esta misma
 * fila, sin necesitar un endpoint AJAX aparte - mismo patrón que ya usa
 * resource-manager/asignaciones.html con la lista de PERFILES.
 */
public class ColaboradorFila {

    private final Long perfilId;
    private final String nombre;
    private final String iniciales;
    private final String correo;
    private final String cargo;
    private final int ocupacion;
    private final String estado;
    private final List<HabilidadPerfilFila> habilidades;
    private final boolean tieneFoto;
    private final Long usuarioId;

    public ColaboradorFila(Long perfilId, String nombre, String correo, String cargo,
                           int ocupacion, String estado, List<HabilidadPerfilFila> habilidades,
                           boolean tieneFoto, Long usuarioId) {
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.correo = correo;
        this.cargo = cargo != null ? cargo : "—";
        this.ocupacion = ocupacion;
        this.estado = estado;
        this.habilidades = habilidades;
        this.tieneFoto = tieneFoto;
        this.usuarioId = usuarioId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCorreo() {
        return correo;
    }

    public String getCargo() {
        return cargo;
    }

    public int getOcupacion() {
        return ocupacion;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoLabel() {
        if ("activo".equals(estado)) return "Disponible";
        if ("cuenta_suspendida".equals(estado)) return "Cuenta suspendida";
        return "No disponible";
    }

    public String getEstadoBadgeClass() {
        if ("activo".equals(estado)) return "badge-green";
        if ("cuenta_suspendida".equals(estado)) return "badge-red";
        return "badge-amber";
    }

    public List<HabilidadPerfilFila> getHabilidades() {
        return habilidades;
    }

    public boolean isTieneFoto() { return tieneFoto; }
    public Long getUsuarioId() { return usuarioId; }

    /** Cuántas habilidades se muestran en la fila del directorio; el resto va en el "+N". */
    public static final int HABILIDADES_VISIBLES = 2;

    /** Las de mayor nivel, solo para mostrar como badges en la fila de la tabla (las demás van en "+N"). */
    public List<HabilidadPerfilFila> getHabilidadesTop() {
        return habilidades.stream()
                .sorted(Comparator.comparingInt(HabilidadPerfilFila::getNivelNumero).reversed())
                .limit(HABILIDADES_VISIBLES)
                .collect(Collectors.toList());
    }

    // ── Directorio del RM: carga contra el límite configurado ──
    private int limite = 100;

    /** Límite de carga vigente (Configuración); lo fija el controlador. */
    public ColaboradorFila conLimite(int limite) {
        this.limite = limite;
        return this;
    }

    public int getLimite() { return limite; }

    // Participación actual en proyectos: el modal "No disponible" la usa para
    // avisar ANTES de confirmar (si lidera, no se puede; si colabora, qué
    // asignaciones se finalizan). Misma regla que UsuarioService.salirDeProyectos.
    private List<String> proyectosQueLidera = List.of();
    private List<String> proyectosDondeColabora = List.of();

    public ColaboradorFila conParticipacion(List<String> lidera, List<String> colabora) {
        this.proyectosQueLidera = lidera != null ? lidera : List.of();
        this.proyectosDondeColabora = colabora != null ? colabora : List.of();
        return this;
    }

    public List<String> getProyectosQueLidera() { return proyectosQueLidera; }
    public List<String> getProyectosDondeColabora() { return proyectosDondeColabora; }
    /** Unidos con "|" para pasarlos en un data-* (los nombres de proyecto no llevan "|"). */
    public String getLideraTexto() { return String.join("|", proyectosQueLidera); }
    public String getColaboraTexto() { return String.join("|", proyectosDondeColabora); }

    /** Mismos estados que Reportes/Ocupación: banca, sub, opt, lim, sobre. */
    public String getCargaClave() {
        return claveDeCarga(ocupacion, limite);
    }

    public String getCargaLabel() {
        return etiquetaDeCarga(getCargaClave());
    }

    /**
     * Rango de carga contra el límite de Configuración: banca (0%), sub, opt,
     * lim (a 10 puntos del límite o menos) o sobre (pasa el límite). Lo usan la
     * tabla de Colaboradores y el servicio REST de "Ajustar carga".
     */
    public static String claveDeCarga(int ocupacion, int limite) {
        if (ocupacion == 0) return "banca";
        if (ocupacion > limite) return "sobre";
        if (ocupacion >= limite - 10) return "lim";
        if (ocupacion >= 60) return "opt";
        return "sub";
    }

    public static String etiquetaDeCarga(String clave) {
        return switch (clave) {
            case "banca" -> "Sin asignación";
            case "sobre" -> "Sobre-asignado";
            case "lim" -> "Al límite";
            case "opt" -> "Óptimo";
            default -> "Subutilizado";
        };
    }

    /** Ancho de la barra respecto del límite (tope 100). */
    public int getCargaPct() {
        return limite <= 0 ? 0 : (int) Math.min(100, Math.round(ocupacion * 100.0 / limite));
    }

    /** Habilidades que no entran en las 3 principales (para el chip "+N"). */
    public List<HabilidadPerfilFila> getHabilidadesResto() {
        List<HabilidadPerfilFila> top = getHabilidadesTop();
        return habilidades.stream().filter(h -> !top.contains(h)).collect(Collectors.toList());
    }

    public String getHabilidadesRestoTexto() {
        return getHabilidadesResto().stream().map(HabilidadPerfilFila::getNombre).collect(Collectors.joining(", "));
    }

    public long getHabilidadesValidadas() {
        return habilidades.stream().filter(HabilidadPerfilFila::isValidada).count();
    }

    /** Texto en minúsculas para el buscador del cliente: nombre, correo, cargo y habilidades. */
    public String getTextoBusqueda() {
        StringBuilder sb = new StringBuilder();
        sb.append(nombre).append(' ').append(correo).append(' ').append(cargo);
        habilidades.forEach(h -> sb.append(' ').append(h.getNombre()));
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
