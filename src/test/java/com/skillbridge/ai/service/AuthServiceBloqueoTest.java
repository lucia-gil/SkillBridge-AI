package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.ResultadoLogin;
import com.skillbridge.ai.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Bloqueo de login por IP tras 5 intentos fallidos (anti fuerza bruta). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceBloqueoTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks
    private AuthService authService;

    private ResultadoLogin fallar(String ip) {
        return authService.intentarLogin("nadie@empresa.com", "incorrecta", ip);
    }

    @Test
    @DisplayName("Tras 5 intentos fallidos la IP queda bloqueada, aunque la contraseña sea la correcta")
    void cincoFallos_bloqueanLaIp() {
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertFalse(authService.estaBloqueada("10.0.0.1"), "no debe bloquear antes del 5.º fallo");
            assertEquals("Credenciales incorrectas.", fallar("10.0.0.1").getMensajeError());
        }

        assertTrue(authService.estaBloqueada("10.0.0.1"));
        ResultadoLogin sexto = fallar("10.0.0.1");
        assertFalse(sexto.isExito());
        assertTrue(sexto.getMensajeError().startsWith("Demasiados intentos fallidos"));
        // Bloqueada: ya ni siquiera se consulta la base.
        verify(usuarioRepository, times(5)).findByCorreoIgnoreCase(anyString());
    }

    @Test
    @DisplayName("El bloqueo es por IP: otra IP puede seguir intentando")
    void bloqueo_esPorIp() {
        when(usuarioRepository.findByCorreoIgnoreCase(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) fallar("10.0.0.1");

        assertTrue(authService.estaBloqueada("10.0.0.1"));
        assertFalse(authService.estaBloqueada("10.0.0.2"));
        assertEquals("Credenciales incorrectas.", fallar("10.0.0.2").getMensajeError());
    }
}
