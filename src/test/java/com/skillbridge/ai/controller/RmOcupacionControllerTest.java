package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.AsignacionCargaFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Servicio REST de "Ajustar carga" del Resource Manager (MockMvc sin levantar la app:
 * el servicio y el repositorio están simulados; la seguridad por rol se prueba aparte).
 */
@ExtendWith(MockitoExtension.class)
class RmOcupacionControllerTest {

    @Mock private ProyectoService proyectoService;
    @Mock private AsignacionRepository asignacionRepository;
    @InjectMocks private RmOcupacionController controller;

    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        UsuarioSesion rm = new UsuarioSesion();
        rm.setUsuarioId(2L);
        session = new MockHttpSession();
        session.setAttribute(SesionKeys.USUARIO, rm);
    }

    private void asignacionDePerfil(long asignacionId, long perfilId) {
        Asignacion a = new Asignacion();
        a.setId(asignacionId);
        a.setPerfilId(perfilId);
        when(asignacionRepository.findById(asignacionId)).thenReturn(Optional.of(a));
    }

    @Test
    void putDedicacionRespondeLaCargaActualizada() throws Exception {
        asignacionDePerfil(10L, 5L);
        when(asignacionRepository.sumarCargaActivaDePerfil(5L)).thenReturn(70);
        when(proyectoService.limiteCargaConfigurado()).thenReturn(100);
        when(proyectoService.asignacionesActivasDe(5L)).thenReturn(List.of(
                new AsignacionCargaFila(10L, "App Móvil Aurora", "Colaborador", false, 35),
                new AsignacionCargaFila(11L, "Portal Andes", "Colaborador", false, 35)));

        mvc.perform(put("/resource-manager/api/asignaciones/10/dedicacion").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cargaPorcentaje\":35,\"motivo\":\"Rebalanceo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfilId").value(5))
                .andExpect(jsonPath("$.ocupacion").value(70))
                .andExpect(jsonPath("$.limite").value(100))
                .andExpect(jsonPath("$.cargaPct").value(70))
                .andExpect(jsonPath("$.cargaClave").value("opt"))
                .andExpect(jsonPath("$.cargaLabel").value("Óptimo"))
                .andExpect(jsonPath("$.mensaje").value("Dedicación actualizada a 35%."))
                .andExpect(jsonPath("$.asignaciones.length()").value(2));

        verify(proyectoService).ajustarDedicacion(10L, 35, "Rebalanceo", 2L);
    }

    @Test
    void putSinDedicacionResponde400() throws Exception {
        mvc.perform(put("/resource-manager/api/asignaciones/10/dedicacion").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Indica la nueva dedicación."));
        verify(proyectoService, never()).ajustarDedicacion(anyLong(), anyInt(), any(), any());
    }

    @Test
    void reglaDeNegocioRotaResponde400ConElMensaje() throws Exception {
        asignacionDePerfil(10L, 5L);
        doThrow(new OperacionInvalidaException("Con ese cambio la carga total sería 105%, por encima del límite de 100%."))
                .when(proyectoService).ajustarDedicacion(10L, 70, null, 2L);

        mvc.perform(put("/resource-manager/api/asignaciones/10/dedicacion").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cargaPorcentaje\":70}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Con ese cambio la carga total sería 105%, por encima del límite de 100%."));
    }

    @Test
    void asignacionInexistenteResponde404() throws Exception {
        when(asignacionRepository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(put("/resource-manager/api/asignaciones/99/dedicacion").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cargaPorcentaje\":30}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La asignación ya no existe."));
    }

    @Test
    void finalizarDejaALaPersonaEnLaBanca() throws Exception {
        asignacionDePerfil(10L, 5L);
        when(asignacionRepository.sumarCargaActivaDePerfil(5L)).thenReturn(null);
        when(proyectoService.limiteCargaConfigurado()).thenReturn(100);
        when(proyectoService.asignacionesActivasDe(5L)).thenReturn(List.of());

        mvc.perform(post("/resource-manager/api/asignaciones/10/finalizar").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fin de fase\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocupacion").value(0))
                .andExpect(jsonPath("$.cargaClave").value("banca"))
                .andExpect(jsonPath("$.cargaLabel").value("Sin asignación"))
                .andExpect(jsonPath("$.mensaje").value("Asignación finalizada."));

        verify(proyectoService).finalizarAsignacion(10L, "Fin de fase", 2L);
    }

    @Test
    void formularioClasicoRedirigeConMensaje() throws Exception {
        mvc.perform(post("/resource-manager/asignaciones/10/dedicacion").session(session)
                        .param("cargaPorcentaje", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/resource-manager/colaboradores.html"))
                .andExpect(flash().attribute("exito", "Dedicación actualizada a 40%."));
    }

    @Test
    void ocupacionRedirigeAColaboradores() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/resource-manager/ocupacion.html"))
                .andExpect(redirectedUrl("/resource-manager/colaboradores.html"));
    }
}
