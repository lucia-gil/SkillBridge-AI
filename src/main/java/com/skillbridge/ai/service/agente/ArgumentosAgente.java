package com.skillbridge.ai.service.agente;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
/** Identificadores de destino son recursos, nunca identidad del actor. */
public final class ArgumentosAgente {
 private ArgumentosAgente(){}
 public record Vacio(){}
 public record Consulta(@NotNull @Size(max=500) String pregunta){}
 public record ProyectoId(@NotNull @Positive Long proyectoId){}
 public record Candidatos(@NotNull @Positive Long proyectoId,@NotNull @Min(1) @Max(100) Integer dedicacion){}
 public record Biografia(@NotBlank @Size(max=1500) String biografia){}
 public record Propuesta(@NotNull @Positive Long proyectoId,@NotNull @Positive Long candidatoId,@NotNull @Min(1) @Max(100) Integer dedicacion){}
 public record Resolucion(@NotNull @Positive Long propuestaId,@NotNull Boolean aprobar,@NotBlank @Size(max=300) String motivo){}
 public record EstadoCuenta(@NotNull @Positive Long cuentaId,@NotNull Boolean activar,@NotBlank @Size(max=300) String motivo){}
 public record ProyectoNuevo(@NotBlank @Size(max=150) String nombre,@NotNull @Size(max=3000) String descripcion,
     @NotNull @Size(max=15) List<@NotBlank @Size(max=60) String> tecnologias,@NotNull LocalDate fechaInicio,
     @NotNull LocalDate fechaFin,@NotNull @Min(0) @Max(100) Integer colaboradores){}
 /** Evento para la agenda personal del RM. fechaInicio en formato yyyy-MM-ddTHH:mm (hora de Lima). */
 public record EventoAgenda(@NotBlank @Size(max=150) String titulo,
     @NotBlank @Pattern(regexp="[a-z_]{2,40}") String tipo,
     @NotBlank @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}") String fechaInicio,
     @NotNull @Size(max=150) String ubicacion, @NotNull @Size(max=255) String enlace,
     @NotNull @Size(max=1500) String descripcion){}
 /** Evento en un proyecto donde el actor tiene asignación activa (colaborador o PM). */
 public record EventoProyectoNuevo(@NotNull @Positive Long proyectoId, @NotBlank @Size(max=150) String titulo,
     @NotBlank @Pattern(regexp="[a-z_]{2,40}") String tipo,
     @NotBlank @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}") String fechaInicio,
     @NotNull @Size(max=150) String ubicacion, @NotNull @Size(max=255) String enlace,
     @NotNull @Size(max=1500) String descripcion, @NotBlank @Pattern(regexp="todos|solo_pm|solo_colaboradores") String audiencia){}
 /** Cambio de un evento de la agenda personal del RM (todos los campos se reemplazan). */
 public record EventoAgendaEdicion(@NotNull @Positive Long eventoId, @NotBlank @Size(max=150) String titulo,
     @NotBlank @Pattern(regexp="[a-z_]{2,40}") String tipo,
     @NotBlank @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}") String fechaInicio,
     @NotNull @Size(max=150) String ubicacion, @NotNull @Size(max=255) String enlace,
     @NotNull @Size(max=1500) String descripcion){}
 public record EventoId(@NotNull @Positive Long eventoId){}
 public record ProyectoEdicion(@NotNull @Positive Long proyectoId,@NotBlank @Size(max=150) String nombre,
     @NotNull @Size(max=3000) String descripcion,@NotNull @Size(max=15) List<@NotBlank @Size(max=60) String> tecnologias,
     @NotNull LocalDate fechaInicio,@NotNull LocalDate fechaFin){}
}
