package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Repara tildes y eñes guardadas "dos veces" en la base de datos, del tipo
 * "IvÃ¡n Rojas" en vez de "Iván Rojas" o "PerÃº" en vez de "Perú".
 *
 * Por qué pasa: algunos scripts SQL antiguos (datos demo, foros v6, perfil
 * profesional v9...) no tenían "SET NAMES utf8mb4"; cargados desde una consola
 * de MySQL en latin1, cada letra con tilde (2 bytes en UTF-8) se guardó como
 * 2 caracteres latin1. El script actual (db/skillbridge_db.sql) ya no tiene
 * ese problema.
 *
 * Cómo repara: en cada columna de texto busca valores con la huella típica
 * (bytes de "Ã", "Â" o "â€") y deshace la doble codificación SOLO si el
 * resultado es UTF-8 válido y distinto; si hay cualquier duda, no toca el valor.
 * Es idempotente: una vez reparado, no vuelve a encontrar nada.
 */
@Component
@Order(50)
public class ReparadorTildes implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ReparadorTildes.class);
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private final JdbcTemplate jdbc;

    public ReparadorTildes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> columnas = jdbc.queryForList(
                    "SELECT TABLE_NAME AS tabla, COLUMN_NAME AS columna FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() "
                            + "AND DATA_TYPE IN ('char','varchar','tinytext','text','mediumtext','longtext','json') "
                            + "AND TABLE_NAME NOT LIKE 'SPRING_SESSION%'");
            int reparados = 0;
            for (Map<String, Object> c : columnas) {
                reparados += repararColumna(String.valueOf(c.get("tabla")), String.valueOf(c.get("columna")));
            }
            if (reparados > 0) log.info("Tildes: se repararon {} valor(es) con doble codificación (p. ej. \"IvÃ¡n\" -> \"Iván\").", reparados);
        } catch (Exception e) {
            log.warn("Tildes: no se pudo revisar la base de datos: {}", e.getMessage());
        }
    }

    private int repararColumna(String tabla, String columna) {
        String t = "`" + tabla.replace("`", "") + "`", col = "`" + columna.replace("`", "") + "`";
        List<String> candidatos;
        try {
            // Huella en bytes: "Ã" = C3 83, "Â" = C3 82, "â€" = C3 A2 E2 82 AC (comparación binaria:
            // con la intercalación normal, "Ã" coincidiría también con "a").
            candidatos = jdbc.queryForList("SELECT DISTINCT CAST(" + col + " AS CHAR) FROM " + t + " WHERE "
                    + "INSTR(CAST(" + col + " AS BINARY), UNHEX('C383')) > 0 OR "
                    + "INSTR(CAST(" + col + " AS BINARY), UNHEX('C382')) > 0 OR "
                    + "INSTR(CAST(" + col + " AS BINARY), UNHEX('C3A2E282AC')) > 0", String.class);
        } catch (Exception e) {
            return 0;   // tabla/columna que no se puede leer así: se omite
        }
        int n = 0;
        for (String original : candidatos) {
            String arreglado = deshacerDobleCodificacion(original);
            if (arreglado == null || arreglado.equals(original)) continue;
            try {
                n += jdbc.update("UPDATE " + t + " SET " + col + " = ? WHERE CAST(" + col + " AS BINARY) = CAST(? AS BINARY)",
                        arreglado, original);
            } catch (Exception e) {
                log.debug("Tildes: no se pudo actualizar {}.{}: {}", tabla, columna, e.getMessage());
            }
        }
        return n;
    }

    /**
     * "IvÃ¡n" -> "Iván". Cada carácter se vuelve a su byte en el "latin1" de
     * MySQL (que es cp1252, salvo 0x81, 0x8D, 0x8F, 0x90 y 0x9D, que pasan tal
     * cual) y los bytes se leen como UTF-8 estricto. Devuelve null si el texto
     * no tenía esa forma (algún carácter fuera de latin1 o bytes UTF-8 inválidos).
     */
    static String deshacerDobleCodificacion(String s) {
        if (s == null) return null;
        CharsetEncoder cp1252 = CP1252.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x80 || (ch >= 0xA0 && ch <= 0xFF) || ch == 0x81 || ch == 0x8D || ch == 0x8F || ch == 0x90 || ch == 0x9D) {
                bytes.write(ch);
            } else {
                try {
                    ByteBuffer b = cp1252.encode(CharBuffer.wrap(String.valueOf(ch)));
                    if (b.remaining() != 1) return null;
                    bytes.write(b.get());
                } catch (CharacterCodingException e) {
                    return null;   // carácter que no viene de latin1: el texto es genuino
                }
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
