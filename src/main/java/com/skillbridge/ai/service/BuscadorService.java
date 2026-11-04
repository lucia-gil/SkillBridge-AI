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
        boolean ve_todo = Roles.ADMINISTRADOR.equals(rol) || Roles.RESOURCE_MANAGER.equals(rol);
        boolean ve_personas = ve_todo || Roles.PROJECT_MANAGER.equals(rol);

        // ---- Proyectos ----
        List<Proyecto> proyectos;
        Set<Long> gestionados = new LinkedHashSet<>();
        if (ve_todo) {
            proyectos = proyectoRepository.findAllByOrderByFechaCreacionDesc();
        } else {
            Set<Long> vistos = new LinkedHashSet<>();
            List<Proyecto> propios = new ArrayList<>();
            if (sesion.getPerfilId() != null) {
                for (Asignacion a : asignacionRepository.listarPorPerfil(sesion.getPerfilId())) {
                    if (a.getProyecto() != null && vistos.add(a.getProyecto().getId())) propios.add(a.getProyecto());
                }
                if (Roles.PROJECT_MANAGER.equals(rol)) {
                    for (Proyecto p : asignacionRepository.listarProyectosGestionados(sesion.getPerfilId())) {
                        gestionados.add(p.getId());
                        if (vistos.add(p.getId())) propios.add(p);
                    }
                }
            }
            proyectos = propios;
        }
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
            for (Perfil p : perfilRepository.listarActivosConUsuario()) {
                if (n >= MAX_POR_GRUPO) break;
                String nombre = p.getUsuario() != null ? p.getUsuario().getNombreCompleto() : "";
                String correo = p.getUsuario() != null ? p.getUsuario().getCorreo() : "";
                String cargo = p.getCargo() != null ? p.getCargo() : "";
                if (normalizar(nombre + " " + cargo + " " + correo).contains(q)) {
                    salida.add(item("Colaboradores", "icon-user", nombre,
                            cargo.isEmpty() ? correo : cargo, destinoColaborador(slug, p.getId(), nombre)));
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

    /** Destino de un proyecto en el buscador global. */
    private static String destinoProyecto(String slug, Proyecto p, boolean loGestiona) {
        // RM y el PM que lo lidera: vista de detalle del proyecto
        if ("resource-manager".equals(slug)) return "/resource-manager/proyectos/" + p.getId() + ".html";
        if ("project-manager".equals(slug) && loGestiona) return "/project-manager/proyectos/" + p.getId() + ".html";
        String b = codificar(p.getNombre());
        return "/" + slug + "/proyectos.html?buscar=" + b;
    }

    /**
     * Admin: Usuarios filtrado. RM: directo a la ficha tipo CV del
     * colaborador. PM: Asignaciones filtrado por la persona (la tabla
     * ahora tambien busca por nombre de integrante).
     */
    private static String destinoColaborador(String slug, Long perfilId, String nombre) {
        String b = codificar(nombre);
        switch (slug) {
            case "administrador": return "/administrador/usuarios.html?buscar=" + b;
            case "resource-manager": return "/resource-manager/colaboradores/" + perfilId;
            default: return "/project-manager/asignaciones.html?buscar=" + b;
        }
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

    private static String etiquetaEstado(String estado) {
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

    private static Map<String, Object> item(String tipo, String icono, String titulo, String detalle, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("icono", icono);
        m.put("titulo", titulo);
        m.put("detalle", detalle == null ? "" : detalle);
        m.put("url", url);
        return m;
    }
}
