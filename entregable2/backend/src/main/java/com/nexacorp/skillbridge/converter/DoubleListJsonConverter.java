package com.nexacorp.skillbridge.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * Mapea conocimiento_embeddings.vector_embedding (JSON, arreglo de números)
 * a List&lt;Double&gt;. Ver limitación declarada en schema.sql: es una
 * columna JSON normal, no un tipo vector nativo — la similitud de coseno se
 * calcula en la aplicación, no con un índice de base de datos.
 */
@Converter
public class DoubleListJsonConverter implements AttributeConverter<List<Double>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Double> attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo serializar el vector a JSON", e);
        }
    }

    @Override
    public List<Double> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el vector JSON de la base de datos", e);
        }
    }
}
