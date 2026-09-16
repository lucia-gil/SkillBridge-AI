package com.skillbridge.ai.dto;

public record PropuestaAsignacionFila(Long id, String proyecto, String candidato, String candidatoIniciales,
                                      String candidatoCargo, String solicitante, int dedicacion, int scoreTotal,
                                      int scoreHabilidades, int scoreExperiencia, int scoreDisponibilidad,
                                      int cargaActual, int cargaProyectada, boolean cargaCompatible,
                                      String estado, String fecha, String motivo) {}
