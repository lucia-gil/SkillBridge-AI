package com.skillbridge.ai.service;

import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.CorreoAutorizadoRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests de UsuarioService.invitar(...): la validacion del correo
 * (formato + dominio permitido, RF08) explicada en pizarra - sin
 * levantar Spring ni tocar la base de datos real, solo con mocks de los
 * repositorios/servicios de los que depende UsuarioService.
 *
 * Patron AAA (Arrange-Act-Assert) en cada test: se arma el escenario
 * (que devuelven los mocks), se ejecuta el metodo bajo prueba, y se
 * verifica el resultado (excepcion esperada o que se guardo lo correcto).
 */
@ExtendWith(MockitoExtension.class) // activa @Mock/@InjectMocks sin arrancar todo Spring Boot
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PerfilRepository perfilRepository;
    @Mock private CorreoAutorizadoRepository correoAutorizadoRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private ConfiguracionService configuracionService;
    @Mock private EmailService emailService;
    @Mock private AsignacionRepository asignacionRepository;
    @Mock private ProyectoService proyectoService;
    @Mock private NotificacionService notificacionService;

    // Mockito crea un UsuarioService real e inyecta los @Mock de arriba
    // en su constructor automaticamente (son 9 parametros - sin esto
    // habria que instanciarlo a mano pasando los 9 mocks).
    @InjectMocks
    private UsuarioService usuarioService;

    private static final Long ACTOR_ID = 1L;

    // ------------------------------------------------------------
    // 1) Correo vacio o nulo: se rechaza ANTES de mirar el dominio
    //    configurado (por eso aqui no hace falta stubear
    //    configuracionService: el metodo nunca llega a usarlo).
    // ------------------------------------------------------------

    @Test
    @DisplayName("Correo nulo -> OperacionInvalidaException, sin guardar nada")
    void invitar_correoNulo_lanzaExcepcion() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any()))
                .thenReturn("nexacorp.com");

        OperacionInvalidaException ex = assertThrows(OperacionInvalidaException.class,
                () -> usuarioService.invitar(null, "NINGUNO", ACTOR_ID));

        assertEquals("Ingresa un correo corporativo.", ex.getMessage());
        verify(correoAutorizadoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Correo en blanco (solo espacios) -> OperacionInvalidaException")
    void invitar_correoEnBlanco_lanzaExcepcion() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any()))
                .thenReturn("nexacorp.com");

        assertThrows(OperacionInvalidaException.class,
                () -> usuarioService.invitar("   ", "NINGUNO", ACTOR_ID));
        verify(correoAutorizadoRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------
    // 2) Dominio configurado como lista "gmail.com, pucp.edu.pe"
    //    (el cambio de hoy): un correo de cada dominio permitido
    //    pasa, uno de otro dominio se rechaza con el mensaje nuevo.
    // ------------------------------------------------------------

    @Test
    @DisplayName("Dominio permitido 'gmail.com, pucp.edu.pe': correo @gmail.com SI pasa")
    void invitar_correoGmail_conDominiosPermitidos_seGuarda() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any()))
                .thenReturn("gmail.com, pucp.edu.pe");
        when(usuarioRepository.existsByCorreoIgnoreCase("cbb4tob@gmail.com")).thenReturn(false);
        when(correoAutorizadoRepository.existsByCorreoIgnoreCase("cbb4tob@gmail.com")).thenReturn(false);

        usuarioService.invitar("CBB4Tob@Gmail.com", "NINGUNO", ACTOR_ID); // con mayusculas a proposito

        // Captura lo que realmente se intento guardar para revisarlo.
        ArgumentCaptor<CorreoAutorizado> captor = ArgumentCaptor.forClass(CorreoAutorizado.class);
        verify(correoAutorizadoRepository).saveAndFlush(captor.capture());
        assertEquals("cbb4tob@gmail.com", captor.getValue().getCorreo()); // se normaliza a minusculas
    }

    @Test
    @DisplayName("Dominio permitido 'gmail.com, pucp.edu.pe': correo de OTRO dominio se rechaza")
    void invitar_correoHotmail_conDominiosPermitidos_seRechaza() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any()))
                .thenReturn("gmail.com, pucp.edu.pe");

        OperacionInvalidaException ex = assertThrows(OperacionInvalidaException.class,
                () -> usuarioService.invitar("ana@hotmail.com", "NINGUNO", ACTOR_ID));

        assertTrue(ex.getMessage().contains("@gmail.com"));
        assertTrue(ex.getMessage().contains("@pucp.edu.pe"));
        verify(correoAutorizadoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Dominio permitido '*': cualquier correo con formato valido pasa")
    void invitar_dominioLibre_aceptaCualquierCorreoValido() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any())).thenReturn("*");
        when(usuarioRepository.existsByCorreoIgnoreCase("alguien@otraempresa.io")).thenReturn(false);
        when(correoAutorizadoRepository.existsByCorreoIgnoreCase("alguien@otraempresa.io")).thenReturn(false);

        assertDoesNotThrow(() -> usuarioService.invitar("alguien@otraempresa.io", "NINGUNO", ACTOR_ID));
        verify(correoAutorizadoRepository).saveAndFlush(any());
    }

    // ------------------------------------------------------------
    // 3) Reglas de negocio que no dependen del dominio: correo
    //    repetido (ya es usuario, o ya fue invitado antes).
    // ------------------------------------------------------------

    @Test
    @DisplayName("Correo que ya tiene cuenta creada -> OperacionInvalidaException")
    void invitar_correoYaRegistrado_lanzaExcepcion() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any())).thenReturn("*");
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@nexacorp.com")).thenReturn(true);

        OperacionInvalidaException ex = assertThrows(OperacionInvalidaException.class,
                () -> usuarioService.invitar("ana@nexacorp.com", "NINGUNO", ACTOR_ID));

        assertEquals("Ese correo ya tiene una cuenta creada.", ex.getMessage());
        verify(correoAutorizadoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Correo ya invitado antes (pendiente) -> OperacionInvalidaException")
    void invitar_correoYaInvitado_lanzaExcepcion() {
        when(configuracionService.obtenerMapa()).thenReturn(Map.of());
        when(configuracionService.valor(any(), eq("dominio_correo_permitido"), any())).thenReturn("*");
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@nexacorp.com")).thenReturn(false);
        when(correoAutorizadoRepository.existsByCorreoIgnoreCase("ana@nexacorp.com")).thenReturn(true);

        OperacionInvalidaException ex = assertThrows(OperacionInvalidaException.class,
                () -> usuarioService.invitar("ana@nexacorp.com", "NINGUNO", ACTOR_ID));

        assertEquals("Ese correo ya fue invitado anteriormente.", ex.getMessage());
        verify(correoAutorizadoRepository, never()).saveAndFlush(any());
    }
}
