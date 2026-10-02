package com.skillbridge.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Un mensaje dentro del detalle de un hilo: el mensaje inicial (esMensajeInicial)
 * o una respuesta. Desde la v6 incluye cita ("responder a"), huella de edición,
 * destacados y adjuntos. Lo arma ForoService.aMensaje().
 */
public class RespuestaFila {

    private final Long id;
    private final Long autorPerfilId;
    private final String autorNombre;
    private final String autorCargo;
    private final String iniciales;
    private final String fechaLabel;
    private final String fechaIso;
    private final String contenido;
    private final boolean esSolucion;
    private final boolean esMensajeInicial;

    private boolean esAutorDelHilo;
    private boolean esMio;
    private boolean editado;
    private int numEdiciones;
    private String fechaEdicionLabel = "";
    /** -1 = sin límite de ediciones. */
    private int edicionesRestantes = -1;
    private boolean permiteEditar;
    private boolean permiteEliminar;
    private boolean permiteMarcarSolucion;
    private CitaForo cita;
    private long numDestacados;
    private boolean destacadoPorMi;
    private boolean puedeDestacar;
    private List<ForoAdjuntoInfo> adjuntos = new ArrayList<>();

    public RespuestaFila(Long id, Long autorPerfilId, String autorNombre, String autorCargo, String fechaLabel,
                          String fechaIso, String contenido, boolean esSolucion, boolean esMensajeInicial) {
        this.id = id;
        this.autorPerfilId = autorPerfilId;
        this.autorNombre = autorNombre;
        this.autorCargo = autorCargo != null ? autorCargo : "";
        this.iniciales = InicialesUtil.de(autorNombre);
        this.fechaLabel = fechaLabel;
        this.fechaIso = fechaIso;
        this.contenido = contenido != null ? contenido : "";
        this.esSolucion = esSolucion;
        this.esMensajeInicial = esMensajeInicial;
    }

    public Long getId() {
        return id;
    }

    public Long getAutorPerfilId() {
        return autorPerfilId;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getAutorCargo() {
        return autorCargo;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    public String getFechaIso() {
        return fechaIso;
    }

    public String getContenido() {
        return contenido;
    }

    public boolean isEsSolucion() {
        return esSolucion;
    }

    public boolean isEsMensajeInicial() {
        return esMensajeInicial;
    }

    public boolean isEsAutorDelHilo() {
        return esAutorDelHilo;
    }

    public void setEsAutorDelHilo(boolean esAutorDelHilo) {
        this.esAutorDelHilo = esAutorDelHilo;
    }

    public boolean isEsMio() {
        return esMio;
    }

    public void setEsMio(boolean esMio) {
        this.esMio = esMio;
    }

    public boolean isEditado() {
        return editado;
    }

    public void setEditado(boolean editado) {
        this.editado = editado;
    }

    public int getNumEdiciones() {
        return numEdiciones;
    }

    public void setNumEdiciones(int numEdiciones) {
        this.numEdiciones = numEdiciones;
    }

    public String getFechaEdicionLabel() {
        return fechaEdicionLabel;
    }

    public void setFechaEdicionLabel(String fechaEdicionLabel) {
        this.fechaEdicionLabel = fechaEdicionLabel;
    }

    public int getEdicionesRestantes() {
        return edicionesRestantes;
    }

    public void setEdicionesRestantes(int edicionesRestantes) {
        this.edicionesRestantes = edicionesRestantes;
    }

    /** El autor puede editar, pero ya usó todas sus ediciones. */
    public boolean isLimiteEdicionAlcanzado() {
        return esMio && edicionesRestantes == 0;
    }

    public boolean isPermiteEditar() {
        return permiteEditar;
    }

    public void setPermiteEditar(boolean permiteEditar) {
        this.permiteEditar = permiteEditar;
    }

    public boolean isPermiteEliminar() {
        return permiteEliminar;
    }

    public void setPermiteEliminar(boolean permiteEliminar) {
        this.permiteEliminar = permiteEliminar;
    }

    public boolean isPermiteMarcarSolucion() {
        return permiteMarcarSolucion;
    }

    public void setPermiteMarcarSolucion(boolean permiteMarcarSolucion) {
        this.permiteMarcarSolucion = permiteMarcarSolucion;
    }

    public CitaForo getCita() {
        return cita;
    }

    public void setCita(CitaForo cita) {
        this.cita = cita;
    }

    public long getNumDestacados() {
        return numDestacados;
    }

    public void setNumDestacados(long numDestacados) {
        this.numDestacados = numDestacados;
    }

    public boolean isDestacadoPorMi() {
        return destacadoPorMi;
    }

    public void setDestacadoPorMi(boolean destacadoPorMi) {
        this.destacadoPorMi = destacadoPorMi;
    }

    public boolean isPuedeDestacar() {
        return puedeDestacar;
    }

    public void setPuedeDestacar(boolean puedeDestacar) {
        this.puedeDestacar = puedeDestacar;
    }

    public List<ForoAdjuntoInfo> getAdjuntos() {
        return adjuntos;
    }

    public void setAdjuntos(List<ForoAdjuntoInfo> adjuntos) {
        this.adjuntos = adjuntos;
    }

    public List<ForoAdjuntoInfo> getImagenes() {
        return adjuntos.stream().filter(ForoAdjuntoInfo::isEsImagen).toList();
    }

    /** Archivos que no son imagen + enlaces (se pintan como tarjetas). */
    public List<ForoAdjuntoInfo> getOtrosAdjuntos() {
        return adjuntos.stream().filter(a -> !a.isEsImagen()).toList();
    }
}
