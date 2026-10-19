package com.deliverypulse.insights.llm;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds a JSON Schema for a response record: records become objects with every component required,
 * {@code List<X>} becomes an array, enums become string enums, and {@link JsonPropertyDescription}
 * becomes {@code description}.
 */
// ponytail: covers only the shapes the insight records use (records, lists, enums, strings, numbers,
// booleans). Swap for a schema-generator library if responses grow maps or polymorphic types.
final class JsonSchemas {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonSchemas() {
    }

    static ObjectNode of(Type type) {
        ObjectNode schema = JSON.createObjectNode();
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class) {
            schema.put("type", "array");
            schema.set("items", of(parameterized.getActualTypeArguments()[0]));
            return schema;
        }
        Class<?> raw = (Class<?>) type;
        if (raw.isRecord()) {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            for (RecordComponent component : raw.getRecordComponents()) {
                ObjectNode property = of(component.getGenericType());
                JsonPropertyDescription description = component.getAccessor().getAnnotation(JsonPropertyDescription.class);
                if (description == null) {
                    description = fieldDescription(raw, component.getName());
                }
                if (description != null) {
                    property.put("description", description.value());
                }
                properties.set(component.getName(), property);
                required.add(component.getName());
            }
            schema.put("additionalProperties", false);
        } else if (raw.isEnum()) {
            schema.put("type", "string");
            ArrayNode values = schema.putArray("enum");
            Arrays.stream(raw.getEnumConstants()).forEach(constant -> values.add(((Enum<?>) constant).name()));
        } else if (raw == String.class) {
            schema.put("type", "string");
        } else if (raw == boolean.class || raw == Boolean.class) {
            schema.put("type", "boolean");
        } else if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) {
            schema.put("type", "integer");
        } else if (Number.class.isAssignableFrom(raw) || raw == double.class) {
            schema.put("type", "number");
        } else {
            throw new IllegalArgumentException("No JSON schema mapping for " + raw.getName());
        }
        return schema;
    }

    /** Annotations on record components land on the field, the accessor or both depending on targets. */
    private static JsonPropertyDescription fieldDescription(Class<?> record, String name) {
        try {
            return record.getDeclaredField(name).getAnnotation(JsonPropertyDescription.class);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }
}
