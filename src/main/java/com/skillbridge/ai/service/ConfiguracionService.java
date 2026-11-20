package com.skillbridge.ai.service;

import com.skillbridge.ai.model.ConfiguracionGlobal;
import com.skillbridge.ai.repository.ConfiguracionGlobalRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Panel de Configuración (administrador/configuracion.html) real, sobre la
 * tabla clave-valor "configuracion_global" ya sembrada por
 * skillbridge_db.sql (13 parámetros). Cada campo del formulario original
 * se reescribió para apuntar 1 a 1 a una clave real en vez de un toggle
 * decorativo sin persistencia.
 */
@Service
public class ConfiguracionService {

    private final ConfiguracionGlobalRepository repository;
    private final AuditoriaService auditoriaService;

    public ConfiguracionService(ConfiguracionGlobalRepository repository, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    public Map<String, ConfiguracionGlobal> obtenerMapa() {
        Map<String, ConfiguracionGlobal> mapa = new HashMap<>();
        repository.findAll().forEach(c -> mapa.put(c.getClave(), c));
        return mapa;
    }

    public String valor(Map<String, ConfiguracionGlobal> mapa, String clave, String porDefecto) {
        ConfiguracionGlobal c = mapa.get(clave);
        return (c != null && c.getValor() != null) ? c.getValor() : porDefecto;
    }

    public boolean valorBooleano(Map<String, ConfiguracionGlobal> mapa, String clave, boolean porDefecto) {
        String v = valor(mapa, clave, null);
        return v != null ? Boolean.parseBoolean(v) : porDefecto;
    }

    /** Tope duro de carga (modelo v7): nadie supera el 100%, aunque se configure más. */
    public static final int LIMITE_CARGA_MAXIMO = 100;

    /**
     * Límite de carga (%) vigente, leído de "limite_carga_colaborador".
     * Fuente única: lo usan la asignación (ProyectoService) y todos los
     * reportes y dashboards, para que "sobre-asignado" signifique lo mismo
     * en toda la plataforma. Siempre queda entre 1 y {@link #LIMITE_CARGA_MAXIMO}.
     *
     * @return límite de carga configurado, 100 si no está definido o es inválido
     */
    public int limiteCarga() {
        int configurado;
        try {
            configurado = Integer.parseInt(valor(obtenerMapa(), "limite_carga_colaborador", "100").trim());
        } catch (NumberFormatException ex) {
            configurado = LIMITE_CARGA_MAXIMO;
        }
        return Math.max(1, Math.min(LIMITE_CARGA_MAXIMO, configurado));
    }

    public boolean activo(String clave) {
        return repository.findById(clave).map(c -> Boolean.parseBoolean(c.getValor())).orElse(false);
    }

    private static final Map<String, int[]> RANGOS = Map.of(
            "expiracion_sesion_minutos", new int[]{1, Integer.MAX_VALUE / 60},
            "limite_carga_colaborador", new int[]{1, 100},
            "maximo_proyectos_simultaneos", new int[]{1, Integer.MAX_VALUE},
            "auto_aprobar_asignaciones_menores_a", new int[]{0, 100},
            "peso_matching_habilidades", new int[]{0, 100},
            "peso_matching_experiencia", new int[]{0, 100},
            "peso_matching_disponibilidad", new int[]{0, 100},
            "foro_max_ediciones", new int[]{0, 20});

    @Transactional
    public void guardarFormulario(Map<String, String> params, Long usuario, Long perfil) {
        Map<String, String> cambios = new java.util.TreeMap<>();
        int pesos = 0;
        for (var entry : RANGOS.entrySet()) {
            String clave = entry.getKey();
            int numero;
            try {
                numero = Integer.parseInt(params.get(clave));
            } catch (NumberFormatException ex) {
                throw new OperacionInvalidaException("Se requiere un entero para " + clave);
            }
            int[] rango = entry.getValue();
            if (numero < rango[0] || numero > rango[1])
                throw new OperacionInvalidaException("Valor fuera de rango para " + clave);
            if (clave.startsWith("peso_matching_")) pesos += numero;
            cambios.put(clave, Integer.toString(numero));
        }
        if (pesos == 0) throw new OperacionInvalidaException("La suma de los pesos debe ser mayor que cero.");
        String dominio = params.getOrDefault("dominio_correo_permitido", "").trim();
        if (!params.containsKey("dominio_correo_permitido")) throw new OperacionInvalidaException("Falta el dominio.");
        if (!dominio.isEmpty() && !"*".equals(dominio)) {
            String etiqueta = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
            for (String parte : dominio.split(",", -1)) {
                if (dominio.length() > 255 || !parte.trim().matches(etiqueta + "(?:[.]" + etiqueta + ")+"))
                    throw new OperacionInvalidaException("Dominio de correo inválido.");
            }
        }
        cambios.put("dominio_correo_permitido", dominio);
        for (String clave : java.util.List.of("asistente_ia_habilitado", "resumenes_ia_foros_activos",
                "doble_factor_obligatorio_admin")) {
            String valor = params.getOrDefault(clave, "false");
            if (!java.util.Set.of("on", "true", "false").contains(valor))
                throw new OperacionInvalidaException("Interruptor inválido para " + clave);
            cambios.put(clave, String.valueOf(!valor.equals("false")));
        }
        Map<String, ConfiguracionGlobal> actual = new HashMap<>();
        repository.bloquearFormulario().forEach(c -> actual.put(c.getClave(), c));
        for (var cambio : cambios.entrySet()) {
            if (!cambio.getValue().equals(valor(actual, cambio.getKey(), null)))
                actualizar(cambio.getKey(), cambio.getValue(), usuario, perfil);
        }
    }

    @Transactional
    public void actualizar(String clave, String nuevoValor, Long actorUsuarioId, Long actorPerfilId) {
        ConfiguracionGlobal c = repository.findById(clave)
                .orElseThrow(() -> new OperacionInvalidaException("Parámetro de configuración desconocido: " + clave));
        String anterior = c.getValor();
        c.setValor(nuevoValor);
        c.setActualizadoPorId(actorPerfilId);
        repository.save(c);
        auditoriaService.registrar(actorUsuarioId, "CONFIGURACION_ACTUALIZADA", "configuracion_global", null,
                AuditoriaService.json(clave, anterior), AuditoriaService.json(clave, nuevoValor), clave);
    }
}
