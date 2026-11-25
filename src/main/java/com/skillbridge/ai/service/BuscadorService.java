package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Buscador global predictivo (topbar). Devuelve coincidencias de
 * colaboradores, proyectos y habilidades a partir de 2 letras, con
 * alcance segun el rol efectivo de quien busca:
 *  - administrador / resource_manager: todo.
 *  - project_manager: todos los colaboradores y habilidades, y solo los
 *    proyectos que gestiona o en los que participa.
 *  - colaborador: solo sus propios proyectos y las habilidades; NO lista
 *    a otras personas (misma regla de privacidad que el resto de vistas).
 * Los datos de esta app son de volumen de curso, por eso se filtra en memoria
 * sobre las consultas ya existentes (sin agregar queries nuevas al esquema).
 */
@Service
public class BuscadorService {

    private static final int MAX_POR_GRUPO = 5;

    private final ProyectoRepository proyectoRepository;
    private final PerfilRepository perfilRepository;
    private final HabilidadRepository habilidadRepository;
    private final AsignacionRepository asignacionRepository;

    public BuscadorService(ProyectoRepository proyectoRepository, PerfilRepository perfilRepository,
                           HabilidadRepository habilidadRepository, AsignacionRepository asignacionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.perfilRepository = perfilRepository;
        this.habilidadRepository = habilidadRepository;
        this.asignacionRepository = asignacionRepository;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buscar(String consulta, UsuarioSesion sesion) {
        String q = normalizar(consulta);
        List<Map<String, Object>> salida = new ArrayList<>();
        if (q.length() < 2 || sesion == null) return salida;

        String rol = sesion.getRolEfectivo();
        String slug = Roles.slug(rol);
        boolean ve_personas = vePersonas(rol);

        // ---- Proyectos ----
        Set<Long> gestionados = new LinkedHashSet<>();
        List<Proyecto> proyectos = proyectosVisibles(sesion, gestionados);
        int n = 0;
        for (Proyecto p : proyectos) {
            if (n >= MAX_POR_GRUPO) break;
            String texto = normalizar(p.getNombre());
            if (texto.contains(q)) {
                salida.add(item("Proyectos", "icon-folder", p.getNombre(), etiquetaEstado(p.getEstado()),
                        destinoProyecto(slug, p, gestionados.contains(p.getId()))));
                n++;
            }
        }

        // ---- Colaboradores ----
        if (ve_personas) {
            n = 0;
            Set<Long> equipoPm = equipoDelPm(sesion);
            for (Perfil p : personasVisibles(sesion)) {
                if (n >= MAX_POR_GRUPO) break;
                String nombre = p.getUsuario() != null ? p.getUsuario().getNombreCompleto() : "";
                String correo = p.getUsuario() != null ? p.getUsuario().getCorreo() : "";
                String cargo = p.getCargo() != null ? p.getCargo() : "";
                if (normalizar(nombre + " " + cargo + " " + correo).contains(q)) {
                    salida.add(item("Colaboradores", "icon-user", nombre,
                            cargo.isEmpty() ? correo : cargo, destinoColaborador(slug, p.getId(), nombre, equipoPm)));
                    n++;
                }
            }
        }

        // ---- Habilidades ----
        n = 0;
        for (Habilidad h : habilidadRepository.findAllConCategoriaOrderByNombre()) {
            if (n >= MAX_POR_GRUPO) break;
            String categoria = h.getCategoria() != null ? h.getCategoria().getNombre() : "";
            if (normalizar(h.getNombre() + " " + categoria).contains(q)) {
                salida.add(item("Habilidades", "icon-book", h.getNombre(), categoria, destinoHabilidad(slug, h.getId(), h.getNombre())));
                n++;
            }
        }
        return salida;
    }

    /**
     * Proyectos que puede ver quien busca: Admin y RM todos; PM y Colaborador
     * solo aquellos en los que participa (y el PM, los que lidera, que se
     * agregan a "gestionados"). Lo comparte BuscadorIaService.
     */
    List<Proyecto> proyectosVisibles(UsuarioSesion sesion, Set<Long> gestionados) {
        if (sesion == null) return List.of();
        String rol = sesion.getRolEfectivo();
        if (Roles.ADMINISTRADOR.equals(rol) || Roles.RESOURCE_MANAGER.equals(rol)) {
            return proyectoRepository.findAllByOrderByFechaCreacionDesc();
        }
        Long perfilId = sesion.getPerfilId();
        if (perfilId == null) return List.of();
        List<Proyecto> propios = new ArrayList<>();
        if (Roles.PROJECT_MANAGER.equals(rol)) {
            // Vista Project Manager: solo los proyectos que lidera.
            for (Proyecto p : asignacionRepository.listarProyectosGestionados(perfilId)) {
                if (gestionados.add(p.getId())) propios.add(p);
            }
        } else if (Roles.COLABORADOR.equals(rol)) {
            // Vista Colaborador: solo los proyectos donde trabaja o trabajó como
            // colaborador (las asignaciones canceladas no cuentan).
            Set<Long> vistos = new LinkedHashSet<>();
            for (Asignacion a : asignacionRepository.listarPorPerfil(perfilId)) {
                if (a.getProyecto() == null || !Roles.COLABORADOR.equals(a.getRolEnProyecto())
                        || "cancelada".equals(a.getEstado())) continue;
                if (vistos.add(a.getProyecto().getId())) propios.add(a.getProyecto());
            }
        }
        return propios;
    }

    /** Admin, Resource Manager y Project Manager pueden buscar personas; el Colaborador no. */
    static boolean vePersonas(String rol) {
        return Roles.ADMINISTRADOR.equals(rol) || Roles.RESOURCE_MANAGER.equals(rol) || Roles.PROJECT_MANAGER.equals(rol);
    }

    /**
     * Personas que puede encontrar quien busca: Admin y RM a todas las cuentas
     * activas; el PM solo a Miembros asignables (no ve administradores ni
     * resource managers); el Colaborador a nadie.
     */
    List<Perfil> personasVisibles(UsuarioSesion sesion) {
        if (sesion == null || !vePersonas(sesion.getRolEfectivo())) return List.of();
        // Nadie se encuentra a sí mismo en "Personas".
        List<Perfil> activos = perfilRepository.listarActivosConUsuario().stream()
                .filter(p -> sesion.getPerfilId() == null || !sesion.getPerfilId().equals(p.getId()))
                .toList();
        if (Roles.PROJECT_MANAGER.equals(sesion.getRolEfectivo())) {
            return activos.stream().filter(ProyectoService::esMiembroAsignable).toList();
        }
        return activos;
    }

    /** Destino de un proyecto en el buscador global. */
    static String destinoProyecto(String slug, Proyecto p, boolean loGestiona) {
        // RM y el PM que lo lidera: vista de detalle del proyecto
        if ("resource-manager".equals(slug)) return "/resource-manager/proyectos/" + p.getId() + ".html";
        if ("project-manager".equals(slug) && loGestiona) return "/project-manager/proyectos/" + p.getId() + ".html";
        String b = codificar(p.getNombre());
        return "/" + slug + "/proyectos.html?buscar=" + b;
    }

    /**
     * Admin: Usuarios filtrado. RM: directo a la ficha tipo CV del
     * colaborador. PM (no ve fichas): si la persona ya está en uno de sus
     * equipos, Asignaciones filtrado por ella; si no, Asignaciones con el
     * selector "Proponer" abierto y su nombre ya escrito.
     */
    static String destinoColaborador(String slug, Long perfilId, String nombre, Set<Long> equipoPm) {
        String b = codificar(nombre);
        switch (slug) {
            case "administrador": return "/administrador/usuarios.html?buscar=" + b;
            case "resource-manager": return "/resource-manager/colaboradores/" + perfilId;
            default:
                return equipoPm.contains(perfilId)
                        ? "/project-manager/asignaciones.html?buscar=" + b
                        : "/project-manager/asignaciones.html?proponer=" + b;
        }
    }

    /** Personas con asignación activa en los proyectos que lidera el PM (vacío para otros roles). */
    Set<Long> equipoDelPm(UsuarioSesion sesion) {
        if (sesion == null || !Roles.PROJECT_MANAGER.equals(sesion.getRolEfectivo()) || sesion.getPerfilId() == null) {
            return Set.of();
        }
        Set<Long> equipo = new java.util.HashSet<>();
        for (Proyecto p : asignacionRepository.listarProyectosGestionados(sesion.getPerfilId())) {
            for (Asignacion a : asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa")) {
                equipo.add(a.getPerfilId());
            }
        }
        return equipo;
    }

    private static String destinoHabilidad(String slug, Long habilidadId, String nombre) {
        String b = codificar(nombre);
        if ("administrador".equals(slug)) return "/administrador/habilidades.html?buscar=" + b;
        if ("colaborador".equals(slug)) return "/colaborador/perfil.html?buscar=" + b;
        // El RM ya tiene un filtro por habilidad en su directorio de colaboradores.
        if ("resource-manager".equals(slug)) return "/resource-manager/colaboradores.html?habilidadId=" + habilidadId;
        // PM: sus proyectos filtrados por esa tecnologia/habilidad (el buscador
        // de Proyectos del PM tambien busca dentro de las tecnologias).
        if ("project-manager".equals(slug)) return "/project-manager/proyectos.html?buscar=" + b;
        return "/" + slug + "/inicio.html";
    }

    static String etiquetaEstado(String estado) {
        if (estado == null) return "";
        switch (estado) {
            case "planificacion": return "Planificación";
            case "activo": return "Activo";
            case "en_pausa": return "En pausa";
            case "completado": return "Completado";
            case "cancelado": return "Cancelado";
            default: return estado;
        }
    }

    private static String codificar(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }

    static Map<String, Object> item(String tipo, String icono, String titulo, String detalle, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("icono", icono);
        m.put("titulo", titulo);
        m.put("detalle", detalle == null ? "" : detalle);
        m.put("url", url);
        return m;
    }
}
