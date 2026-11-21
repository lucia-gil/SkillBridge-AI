package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FusionHabilidadService {
    private final JdbcTemplate jdbc;
    private final HabilidadRepository habilidades;
    private final AuditoriaService auditoria;
    private final ObjectMapper json;

    public FusionHabilidadService(JdbcTemplate jdbc, HabilidadRepository habilidades,
                                  AuditoriaService auditoria, ObjectMapper json) {
        this.jdbc = jdbc;
        this.habilidades = habilidades;
        this.auditoria = auditoria;
        this.json = json;
    }

    public record Opcion(Long id, String nombre, String categoria) {}
    public record Habilidad(Long id, String nombre) implements Serializable {}
    public record Vista(Habilidad origen, Habilidad destino, long perfiles, long perfilesCompartidos,
                        long requisitos, long tecnologias, long cursos, long constancias) implements Serializable {}
    private record Declaracion(int nivel, Long validador, LocalDateTime fecha) {}

    @Transactional(readOnly = true)
    public List<Opcion> opciones() {
        return habilidades.findAllConCategoriaOrderByNombre().stream()
                .map(h -> new Opcion(h.getId(), h.getNombre(), h.getCategoria().getNombre())).toList();
    }

    @Transactional(readOnly = true)
    public Vista revisar(Long origen, Long destino) {
        List<Habilidad> par = par(origen, destino, false);
        return medir(par.get(0), par.get(1), etiquetas(par.get(0), par.get(1), false));
    }

    @Transactional
    public void fusionar(Vista vista, Long actor) {
        List<Habilidad> par = par(vista.origen().id(), vista.destino().id(), true);
        Habilidad origen = par.get(0);
        Habilidad destino = par.get(1);
        Map<Long, String> tecnologias = etiquetas(origen, destino, true);
        if (!vista.equals(medir(origen, destino, tecnologias))) {
            throw new OperacionInvalidaException("El catálogo cambió. Revisa el impacto antes de confirmar de nuevo.");
        }

        migrarPerfiles(origen.id(), destino.id());
        migrarRequisitos(origen.id(), destino.id());
        for (var proyecto : tecnologias.entrySet()) {
            jdbc.update("UPDATE proyectos SET tecnologias=? WHERE id=?", proyecto.getValue(), proyecto.getKey());
        }
        jdbc.update("UPDATE cursos SET habilidad_id=? WHERE habilidad_id=?", destino.id(), origen.id());
        jdbc.update("DELETE FROM habilidades WHERE id=?", origen.id());

        auditoria.registrar(actor, "HABILIDAD_FUSIONADA", "habilidad", destino.id(),
                AuditoriaService.json("habilidad", origen.nombre()), AuditoriaService.json("habilidad", destino.nombre()),
                "Se incorporó «" + origen.nombre() + "» a «" + destino.nombre() + "». Se movieron "
                        + vista.perfiles() + " declaraciones, " + vista.requisitos() + " requisitos, "
                        + vista.tecnologias() + " etiquetas de proyecto, " + vista.cursos() + " cursos y "
                        + vista.constancias() + " constancias.");
    }

    private List<Habilidad> par(Long origen, Long destino, boolean bloquear) {
        if (origen == null || destino == null || Objects.equals(origen, destino)) {
            throw new OperacionInvalidaException("Selecciona dos habilidades distintas.");
        }
        List<Habilidad> par = jdbc.query("SELECT id,nombre FROM habilidades WHERE id IN (?,?) ORDER BY id"
                        + (bloquear ? " FOR UPDATE" : ""),
                (rs, i) -> new Habilidad(rs.getLong(1), rs.getString(2)), origen, destino);
        if (par.size() != 2) throw new OperacionInvalidaException("Una de las habilidades ya no existe en el catálogo.");
        return origen.equals(par.get(0).id()) ? par : List.of(par.get(1), par.get(0));
    }

    private Vista medir(Habilidad origen, Habilidad destino, Map<Long, String> tecnologias) {
        Long id = origen.id();
        return new Vista(origen, destino,
                contar("SELECT count(*) FROM perfil_habilidad WHERE habilidad_id=?", id),
                contar("""
                        SELECT count(*) FROM perfil_habilidad o JOIN perfil_habilidad d ON d.perfil_id=o.perfil_id
                        WHERE o.habilidad_id=? AND d.habilidad_id=?
                        """, id, destino.id()),
                contar("SELECT count(*) FROM proyecto_habilidad_requerida WHERE habilidad_id=?", id),
                tecnologias.size(),
                contar("SELECT count(*) FROM cursos WHERE habilidad_id=?", id),
                contar("SELECT count(*) FROM certificados_habilidad WHERE habilidad_id=?", id));
    }

    private long contar(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Long.class, parametros);
    }

    private Map<Long, String> etiquetas(Habilidad origen, Habilidad destino, boolean bloquear) {
        Map<Long, String> cambios = new LinkedHashMap<>();
        jdbc.query("SELECT id,tecnologias FROM proyectos WHERE tecnologias IS NOT NULL ORDER BY id"
                + (bloquear ? " FOR UPDATE" : ""), rs -> {
            String actualizada = reemplazarEtiqueta(rs.getString(2), origen.nombre(), destino.nombre());
            if (actualizada != null) cambios.put(rs.getLong(1), actualizada);
        });
        return cambios;
    }

    private String reemplazarEtiqueta(String contenido, String origen, String destino) {
        try {
            JsonNode lista = json.readTree(contenido);
            if (lista == null || !lista.isArray()) return null;
            var salida = json.createArrayNode();
            boolean cambia = false;
            boolean agregada = false;
            for (JsonNode elemento : lista) {
                boolean esOrigen = elemento.isTextual() && origen.equalsIgnoreCase(elemento.asText().strip());
                boolean esDestino = elemento.isTextual() && destino.equalsIgnoreCase(elemento.asText().strip());
                cambia |= esOrigen;
                if (!esOrigen && !esDestino) {
                    salida.add(elemento);
                } else if (!agregada) {
                    salida.add(destino);
                    agregada = true;
                }
            }
            return cambia ? json.writeValueAsString(salida) : null;
        } catch (JsonProcessingException ex) {
            throw new OperacionInvalidaException("No se pudieron leer las tecnologías de un proyecto. Corrige el dato y vuelve a intentar.");
        }
    }

    private void migrarPerfiles(Long origen, Long destino) {
        Map<Long, Declaracion> fuentes = declaraciones(origen);
        Map<Long, Declaracion> existentes = declaraciones(destino);
        for (var fila : fuentes.entrySet()) {
            Long perfil = fila.getKey();
            Declaracion fuente = fila.getValue();
            Declaracion previa = existentes.get(perfil);
            if (previa == null) {
                jdbc.update("""
                        INSERT INTO perfil_habilidad(perfil_id,habilidad_id,nivel,validado_por_id,fecha_declaracion)
                        VALUES(?,?,?,?,?)
                        """, perfil, destino, fuente.nivel(), fuente.validador(), fuente.fecha());
            } else {
                Declaracion elegida = preferir(fuente, previa);
                LocalDateTime fecha = fuente.fecha().isBefore(previa.fecha()) ? fuente.fecha() : previa.fecha();
                jdbc.update("""
                        UPDATE perfil_habilidad SET nivel=?,validado_por_id=?,fecha_declaracion=?
                        WHERE perfil_id=? AND habilidad_id=?
                        """, elegida.nivel(), elegida.validador(), fecha, perfil, destino);
            }
            jdbc.update("DELETE FROM constancia_analisis_ia WHERE perfil_id=? AND habilidad_id IN (?,?)",
                    perfil, origen, destino);
        }
        jdbc.update("UPDATE certificados_habilidad SET habilidad_id=? WHERE habilidad_id=?", destino, origen);
        jdbc.update("DELETE FROM perfil_habilidad WHERE habilidad_id=?", origen);
    }

    private Map<Long, Declaracion> declaraciones(Long habilidad) {
        Map<Long, Declaracion> filas = new LinkedHashMap<>();
        jdbc.query("""
                SELECT perfil_id,nivel,validado_por_id,fecha_declaracion FROM perfil_habilidad
                WHERE habilidad_id=? ORDER BY perfil_id FOR UPDATE
                """, rs -> {
            filas.put(rs.getLong(1), new Declaracion(rs.getInt(2), rs.getObject(3, Long.class),
                    rs.getObject(4, LocalDateTime.class)));
        }, habilidad);
        return filas;
    }

    private Declaracion preferir(Declaracion fuente, Declaracion previa) {
        boolean validadaOrigen = fuente.validador() != null;
        boolean validadaDestino = previa.validador() != null;
        if (validadaOrigen != validadaDestino) return validadaOrigen ? fuente : previa;
        return fuente.nivel() > previa.nivel() ? fuente : previa;
    }

    private void migrarRequisitos(Long origen, Long destino) {
        Map<Long, Integer> fuentes = requisitos(origen);
        Map<Long, Integer> existentes = requisitos(destino);
        for (var fila : fuentes.entrySet()) {
            Long proyecto = fila.getKey();
            Integer nivel = existentes.get(proyecto);
            if (nivel == null) {
                jdbc.update("UPDATE proyecto_habilidad_requerida SET habilidad_id=? WHERE proyecto_id=? AND habilidad_id=?",
                        destino, proyecto, origen);
            } else {
                jdbc.update("UPDATE proyecto_habilidad_requerida SET nivel_requerido=? WHERE proyecto_id=? AND habilidad_id=?",
                        Math.max(nivel, fila.getValue()), proyecto, destino);
                jdbc.update("DELETE FROM proyecto_habilidad_requerida WHERE proyecto_id=? AND habilidad_id=?", proyecto, origen);
            }
        }
    }

    private Map<Long, Integer> requisitos(Long habilidad) {
        Map<Long, Integer> filas = new LinkedHashMap<>();
        jdbc.query("""
                SELECT proyecto_id,nivel_requerido FROM proyecto_habilidad_requerida
                WHERE habilidad_id=? ORDER BY proyecto_id FOR UPDATE
                """, rs -> {
            filas.put(rs.getLong(1), rs.getInt(2));
        }, habilidad);
        return filas;
    }
}
