package com.skillbridge.ai.service;

import com.skillbridge.ai.model.PreferenciaNotificacion;
import com.skillbridge.ai.repository.PreferenciaNotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciaNotificacionServiceTest {

    @Mock
    private PreferenciaNotificacionRepository repository;

    @InjectMocks
    private PreferenciaNotificacionService service;

    @ParameterizedTest(name = "canal {0} -> correo {1}")
    @CsvSource({"app_mail, true", "mail, true", "solo_mail, true", "app, false", "solo_app, false", "otro, false"})
    void incluyeCorreoSegunCanal(String canal, boolean esperado) {
        assertThat(PreferenciaNotificacionService.incluyeCorreo(canal)).isEqualTo(esperado);
    }

    @Test
    void incluyeCorreoConCanalNulo() {
        assertThat(PreferenciaNotificacionService.incluyeCorreo(null)).isFalse();
    }

    @Test
    void sinPreferenciasGuardadasTodoLlegaPorCorreo() {
        when(repository.findByPerfilId(7L)).thenReturn(List.of());

        Map<String, Boolean> mapa = service.obtener(7L);

        assertThat(mapa).containsOnlyKeys(PreferenciaNotificacionService.TIPOS_EVENTO);
        assertThat(mapa.values()).containsOnly(true);
    }

    @Test
    void lasPreferenciasGuardadasReemplazanAlValorPorDefecto() {
        when(repository.findByPerfilId(7L)).thenReturn(List.of(
                pref(7L, "resumen_semanal", "app"),
                pref(7L, "resultados_ia", "solo_mail")));

        Map<String, Boolean> mapa = service.obtener(7L);

        assertThat(mapa).containsEntry("resumen_semanal", false)
                .containsEntry("resultados_ia", true)
                .containsEntry("alertas_criticas", true);
    }

    @Test
    void actualizarGuardaLosCuatroTiposConSuCanal() {
        PreferenciaNotificacion existente = pref(7L, "alertas_criticas", "app_mail");
        when(repository.findByPerfilIdAndTipoEvento(eq(7L), any())).thenReturn(Optional.empty());
        when(repository.findByPerfilIdAndTipoEvento(7L, "alertas_criticas")).thenReturn(Optional.of(existente));

        service.actualizar(7L, Map.of("alertas_criticas", false, "resumen_semanal", true));

        ArgumentCaptor<PreferenciaNotificacion> guardadas = ArgumentCaptor.forClass(PreferenciaNotificacion.class);
        verify(repository, times(4)).save(guardadas.capture());
        Map<String, String> canales = new java.util.HashMap<>();
        guardadas.getAllValues().forEach(p -> {
            assertThat(p.getPerfilId()).isEqualTo(7L);
            canales.put(p.getTipoEvento(), p.getCanal());
        });
        assertThat(canales).containsEntry("alertas_criticas", "app")   // se apagó
                .containsEntry("resumen_semanal", "app_mail")            // se dejó encendido
                .containsEntry("solicitudes_aprobacion", "app")          // no vino: se apaga
                .containsEntry("resultados_ia", "app");
        assertThat(guardadas.getAllValues()).contains(existente);        // reutiliza la fila existente
    }

    @Test
    void textosDeLaPantallaDePreferencias() {
        for (String tipo : PreferenciaNotificacionService.TIPOS_EVENTO) {
            assertThat(PreferenciaNotificacionService.descripcion(tipo)).isNotBlank();
            assertThat(PreferenciaNotificacionService.etiqueta(tipo)).isNotBlank();
        }
        assertThat(PreferenciaNotificacionService.etiqueta("resumen_semanal")).isEqualTo("Resumen semanal");
        assertThat(PreferenciaNotificacionService.descripcion("desconocido")).isEmpty();
    }

    private static PreferenciaNotificacion pref(Long perfilId, String tipo, String canal) {
        PreferenciaNotificacion p = new PreferenciaNotificacion();
        p.setPerfilId(perfilId);
        p.setTipoEvento(tipo);
        p.setCanal(canal);
        return p;
    }
}
