package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.ResultadoLogin;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** En producción no se puede entrar con la contraseña de las cuentas de ejemplo. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceClaveDemoTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void cuentaDeEjemplo() {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setCorreo("admin@nexacorp.com");
        u.setEstado("activo");
        u.setContrasenaHash("hash-de-admin123");
        when(usuarioRepository.findByCorreoIgnoreCase("admin@nexacorp.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("admin123"), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Con el bloqueo activo (perfil prod), admin123 no inicia sesión aunque sea la contraseña correcta")
    void prod_bloqueaClaveDemo() {
        ReflectionTestUtils.setField(authService, "bloquearClaveDemo", true);

        ResultadoLogin r = authService.intentarLogin("admin@nexacorp.com", "admin123", "10.0.0.9");

        assertFalse(r.isExito());
        assertTrue(r.getMensajeError().contains("contraseña de ejemplo"));
        verify(auditoriaService).registrar(eq(7L), eq("LOGIN_BLOQUEADO"), any(String.class));
    }

    @Test
    @DisplayName("Sin el bloqueo (desarrollo local), admin123 pasa ese filtro y sigue con las demás reglas")
    void local_noBloquea() {
        ReflectionTestUtils.setField(authService, "bloquearClaveDemo", false);
        // Cuenta suspendida: el login se detiene en la regla siguiente, lo que
        // demuestra que la contraseña de ejemplo no fue rechazada.
        usuarioRepository.findByCorreoIgnoreCase("admin@nexacorp.com").get().setEstado("suspendido");

        ResultadoLogin r = authService.intentarLogin("admin@nexacorp.com", "admin123", "10.0.0.9");

        assertFalse(r.isExito());
        assertTrue(r.getMensajeError().contains("inactiva"));
        verify(auditoriaService, never()).registrar(eq(7L), eq("LOGIN_BLOQUEADO"), contains("ejemplo"));
    }
}
