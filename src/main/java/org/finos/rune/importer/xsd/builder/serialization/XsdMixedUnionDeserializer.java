package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.records.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class XsdMixedUnionDeserializer extends StdDeserializer<RosettaModelObject> {

    private static final Logger log = LoggerFactory.getLogger(XsdMixedUnionDeserializer.class);
    private final Class<? extends RosettaModelObject> targetClass;

    public XsdMixedUnionDeserializer(Class<? extends RosettaModelObject> targetClass) {
        super(targetClass);
        this.targetClass = targetClass;
        log.debug("Initialized XsdMixedUnionDeserializer for target class: {}", targetClass.getSimpleName());
    }

    @Override
    public RosettaModelObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String textValue = p.getValueAsString();

        try {
            Method builderMethod = targetClass.getMethod("builder");
            RosettaModelObjectBuilder builder = (RosettaModelObjectBuilder) builderMethod.invoke(null);

            for (Method method : orderedSetters(builder.getClass())) {
                Class<?> paramType = method.getParameterTypes()[0];
                try {
                    Object coercedValue = coerceToType(textValue, paramType);
                    if (coercedValue != null) {
                        method.invoke(builder, coercedValue);
                        return builder.build();
                    }
                } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException exception) {
                    log.trace("Failed to coerce mixed-union value into '{}' via '{}'; trying the next member.",
                            paramType.getSimpleName(), method.getName(), exception);
                }
            }
            log.error("Failed to map Mixed Union value to any member in {}", targetClass.getSimpleName());
            throw new IllegalArgumentException("Cannot map value to any union member in " + targetClass.getSimpleName());
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            log.error("Dynamic deserialization exception for {}", targetClass.getSimpleName(), e);
            throw new IOException("Failed to dynamically deserialize Mixed Union: " + targetClass.getSimpleName(), e);
        }
    }

    static List<Method> orderedSetters(Class<?> builderType) {
        return java.util.Arrays.stream(builderType.getMethods())
                .filter(method -> method.getName().startsWith("set") && method.getParameterCount() == 1)
                .sorted(Comparator.comparing(Method::getName)
                        .thenComparing(method -> method.getParameterTypes()[0].getName()))
                .toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object coerceToType(String text, Class<?> target) throws InvocationTargetException, IllegalAccessException {
        if (target == String.class) return text;
        if (target == Integer.class || target == int.class) return Integer.parseInt(text);
        if (target == Boolean.class || target == boolean.class) {
            String cleanText = text.trim().toLowerCase();
            if ("true".equals(cleanText) || "1".equals(cleanText)) return Boolean.TRUE;
            if ("false".equals(cleanText) || "0".equals(cleanText)) return Boolean.FALSE;
            throw new IllegalArgumentException("Strict XSD parsing failed: '" + text + "' is not a valid boolean.");
        }
        if (target == java.math.BigDecimal.class) return new java.math.BigDecimal(text);

        if (target == Date.class) {
            LocalDate localDate = LocalDate.parse(text);
            return Date.of(localDate);
        }

        if (target == LocalDate.class) return java.time.LocalDate.parse(text);

        if (Enum.class.isAssignableFrom(target)) {
            try {
                Method fromString = target.getMethod("fromDisplayName", String.class);
                Object enumValue = fromString.invoke(null, text);
                if (enumValue != null) return enumValue;
            } catch (NoSuchMethodException e) {
                // Fall back to the standard Java enum constant lookup.
            }
            return Enum.valueOf((Class<Enum>) target, text);
        }

        return null;
    }
}
