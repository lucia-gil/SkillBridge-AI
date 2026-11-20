package com.skillbridge.ai.service;

import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reglas de "Ajustar carga" (ProyectoService.ajustarDedicacion) con los repositorios simulados. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProyectoServiceDedicacionTest {

    @Mock private ProyectoRepository proyectoRepository;
    @Mock private AsignacionRepository asignacionRepository;
    @Mock private com.skillbridge.ai.repository.PerfilRepository perfilRepository;
    @Mock private com.skillbridge.ai.repository.EntregableRepository entregableRepository;
    @Mock private com.skillbridge.ai.repository.ForoPublicacionRepository foroPublicacionRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private NotificacionService notificacionService;
    @Mock private ConfiguracionService configuracionService;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private com.skillbridge.ai.repository.PropuestaAsignacionRepository propuestaRepository;
    @Mock private com.skillbridge.ai.repository.ArchivoProyectoRepository archivoProyectoRepository;
    @Mock private com.skillbridge.ai.repository.RecomendacionIaLogRepository recomendacionIaLogRepository;
    @Mock private com.skillbridge.ai.repository.HabilidadRepository habilidadRepository;
    @Mock private com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;

    @InjectMocks
    private ProyectoService service;

    private Asignacion asignacion;

    @BeforeEach
    void setUp() {
        asignacion = new Asignacion();
        asignacion.setId(10L);
        asignacion.setProyectoId(3L);
        asignacion.setPerfilId(5L);
        asignacion.setEstado("activa");
        asignacion.setCargaPorcentaje(45);
        when(asignacionRepository.findById(10L)).thenReturn(Optional.of(asignacion));
        when(configuracionService.limiteCarga()).thenReturn(100);
        Proyecto p = new Proyecto();
        p.setNombre("App Móvil Aurora");
        when(proyectoRepository.findById(3L)).thenReturn(Optional.of(p));
    }

    @Test
    @DisplayName("Bajar la dedicación guarda, audita y avisa al colaborador")
    void bajarDedicacion() {
        service.ajustarDedicacion(10L, 35, "Rebalanceo", 1L);

        assertThat(asignacion.getCargaPorcentaje()).isEqualTo(35);
        verify(asignacionRepository).save(asignacion);
        verify(auditoriaService).registrar(eq(1L), eq("ASIGNACION_EDITADA"), eq("asignacion"), eq(10L),
                anyString(), anyString(), contains("45% → 35%"));
        verify(notificacionService).crear(5L, "asignacion", "Dedicación actualizada",
                "Tu dedicación en \"App Móvil Aurora\" cambió de 45% a 35%.", "proyectos.html");
    }

    @Test
    void subirDedicacionDentroDelLimite() {
        when(asignacionRepository.sumarCargaActivaDePerfil(5L)).thenReturn(80);   // 45 aquí + 35 en otro proyecto

        service.ajustarDedicacion(10L, 65, null, 1L);                            // 35 + 65 = 100

        assertThat(asignacion.getCargaPorcentaje()).isEqualTo(65);
        verify(asignacionRepository).save(asignacion);
    }

    @Test
    void subirDedicacionPorEncimaDelLimiteSeRechaza() {
        when(asignacionRepository.sumarCargaActivaDePerfil(5L)).thenReturn(80);

        assertThatThrownBy(() -> service.ajustarDedicacion(10L, 70, null, 1L))   // 35 + 70 = 105
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("105%").hasMessageContaining("100%");
        sinCambios();
    }

    @Test
    void respetaElLimiteConfiguradoPorElAdministrador() {
        when(configuracionService.limiteCarga()).thenReturn(80);

        assertThatThrownBy(() -> service.ajustarDedicacion(10L, 90, null, 1L))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("La dedicación debe estar entre 1% y 80%.");
        sinCambios();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 101})
    void fueraDeRangoSeRechaza(int carga) {
        assertThatThrownBy(() -> service.ajustarDedicacion(10L, carga, null, 1L))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessageContaining("entre 1% y 100%");
        sinCambios();
    }

    @Test
    void mismaDedicacionSeRechaza() {
        assertThatThrownBy(() -> service.ajustarDedicacion(10L, 45, null, 1L))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("La dedicación ya es 45%.");
        sinCambios();
    }

    @Test
    void asignacionFinalizadaNoSePuedeAjustar() {
        asignacion.setEstado("finalizada");

        assertThatThrownBy(() -> service.ajustarDedicacion(10L, 30, null, 1L))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("Solo se puede ajustar una asignación activa.");
        sinCambios();
    }

    @Test
    void asignacionInexistente() {
        when(asignacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ajustarDedicacion(99L, 30, null, 1L))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("La asignación ya no existe.");
        sinCambios();
    }

    @Test
    void cargaActivaSinAsignacionesEsCero() {
        when(asignacionRepository.sumarCargaActivaDePerfil(5L)).thenReturn(null);
        assertThat(service.cargaActivaDe(5L)).isZero();
    }

    private void sinCambios() {
        verify(asignacionRepository, never()).save(any());
        verify(notificacionService, never()).crear(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(auditoriaService);
    }
}
