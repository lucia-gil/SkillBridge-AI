package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Alcance por rol del buscador del topbar (normal y con IA). */
@ExtendWith(MockitoExtension.class)
class BuscadorServiceAlcanceTest {

    @Mock private ProyectoRepository proyectoRepository;
    @Mock private PerfilRepository perfilRepository;
    @Mock private HabilidadRepository habilidadRepository;
    @Mock private AsignacionRepository asignacionRepository;

    @InjectMocks
    private BuscadorService buscador;

    private static UsuarioSesion sesion(String rol, Long perfilId) {
        UsuarioSesion s = new UsuarioSesion();
        s.setRolEfectivo(rol);
        s.setPerfilId(perfilId);
        return s;
    }

    private static Proyecto proyecto(long id, String nombre) {
        Proyecto p = new Proyecto();
        p.setId(id);
        p.setNombre(nombre);
        p.setEstado("activo");
        return p;
    }

    private static Asignacion asignacion(Proyecto p, String rolEnProyecto, String estado) {
        Asignacion a = new Asignacion();
        ReflectionTestUtils.setField(a, "proyecto", p);
        a.setRolEnProyecto(rolEnProyecto);
        a.setEstado(estado);
        return a;
    }

    private static Perfil perfil(long id, String nombre, String rolOrganizacional) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombreCompleto(nombre);
        u.setCorreo(nombre.toLowerCase().replace(' ', '.') + "@empresa.com");
        u.setRolOrganizacional(rolOrganizacional);
        u.setEstado("activo");
        Perfil p = new Perfil();
        p.setId(id);
        p.setEstado("activo");
        ReflectionTestUtils.setField(p, "usuario", u);
        return p;
    }

    private static List<Long> ids(List<Proyecto> proyectos) {
        return proyectos.stream().map(Proyecto::getId).toList();
    }

    @Test
    @DisplayName("Colaborador: solo ve proyectos donde es colaborador, sin asignaciones canceladas")
    void colaborador_soloSusProyectos() {
        Proyecto activo = proyecto(1, "Portal");
        Proyecto finalizado = proyecto(2, "Legado");
        Proyecto cancelado = proyecto(3, "Cancelado");
        Proyecto liderado = proyecto(4, "Liderado");
        when(asignacionRepository.listarPorPerfil(10L)).thenReturn(List.of(
                asignacion(activo, Roles.COLABORADOR, "activa"),
                asignacion(finalizado, Roles.COLABORADOR, "finalizada"),
                asignacion(cancelado, Roles.COLABORADOR, "cancelada"),
                asignacion(liderado, Roles.PROJECT_MANAGER, "activa")));

        List<Proyecto> visibles = buscador.proyectosVisibles(sesion(Roles.COLABORADOR, 10L), new HashSet<>());

        assertEquals(List.of(1L, 2L), ids(visibles));
        verify(proyectoRepository, never()).findAllByOrderByFechaCreacionDesc();
    }

    @Test
    @DisplayName("Colaborador sin perfil: no ve ningún proyecto")
    void colaborador_sinPerfil_nada() {
        assertTrue(buscador.proyectosVisibles(sesion(Roles.COLABORADOR, null), new HashSet<>()).isEmpty());
        verifyNoInteractions(proyectoRepository, asignacionRepository);
    }

    @Test
    @DisplayName("Project Manager: solo ve los proyectos que lidera")
    void pm_soloLiderados() {
        when(asignacionRepository.listarProyectosGestionados(20L)).thenReturn(List.of(proyecto(5, "Lidero")));
        Set<Long> gestionados = new HashSet<>();

        List<Proyecto> visibles = buscador.proyectosVisibles(sesion(Roles.PROJECT_MANAGER, 20L), gestionados);

        assertEquals(List.of(5L), ids(visibles));
        assertEquals(Set.of(5L), gestionados);
        verify(asignacionRepository, never()).listarPorPerfil(any());
        verify(proyectoRepository, never()).findAllByOrderByFechaCreacionDesc();
    }

    @Test
    @DisplayName("Admin y Resource Manager: ven todos los proyectos")
    void adminYRm_todo() {
        when(proyectoRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of(proyecto(1, "A"), proyecto(2, "B")));

        assertEquals(2, buscador.proyectosVisibles(sesion(Roles.ADMINISTRADOR, null), new HashSet<>()).size());
        assertEquals(2, buscador.proyectosVisibles(sesion(Roles.RESOURCE_MANAGER, 30L), new HashSet<>()).size());
    }

    @Test
    @DisplayName("Personas: el Colaborador no ve a nadie; el PM solo a Miembros asignables")
    void personas_porRol() {
        Perfil miembro = perfil(1, "Ana Miembro", null);
        Perfil rm = perfil(2, "Rita Recursos", Roles.RESOURCE_MANAGER);
        when(perfilRepository.listarActivosConUsuario()).thenReturn(List.of(miembro, rm));

        assertTrue(buscador.personasVisibles(sesion(Roles.COLABORADOR, 10L)).isEmpty());
        assertEquals(List.of(miembro), buscador.personasVisibles(sesion(Roles.PROJECT_MANAGER, 20L)));
        assertEquals(List.of(miembro, rm), buscador.personasVisibles(sesion(Roles.RESOURCE_MANAGER, 30L)));
    }

    @Test
    @DisplayName("Buscador normal del Colaborador: no devuelve personas ni proyectos ajenos")
    void buscar_colaborador_sinAjenos() {
        Proyecto mio = proyecto(1, "Portal Clientes");
        when(asignacionRepository.listarPorPerfil(10L)).thenReturn(List.of(asignacion(mio, Roles.COLABORADOR, "activa")));
        when(habilidadRepository.findAllConCategoriaOrderByNombre()).thenReturn(List.of());

        List<Map<String, Object>> r = buscador.buscar("portal", sesion(Roles.COLABORADOR, 10L));

        assertEquals(1, r.size());
        assertEquals("Portal Clientes", r.get(0).get("titulo"));
        assertTrue(r.stream().noneMatch(m -> "Colaboradores".equals(m.get("tipo"))));
        verify(perfilRepository, never()).listarActivosConUsuario();
        verify(proyectoRepository, never()).findAllByOrderByFechaCreacionDesc();
    }
}
