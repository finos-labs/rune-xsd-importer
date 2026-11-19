package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XsdTypeAttributeSerializerTest {

    @Test
    void reportsXsiTypeAsTheMetadataProperty() {
        Object value = new Object();
        XsdTypeIdResolver resolver = new XsdTypeIdResolver(
                TypeFactory.defaultInstance().constructType(Object.class), null);
        XsdTypeAttributeSerializer serializer = new XsdTypeAttributeSerializer(resolver, null);

        assertEquals("xsi:type", serializer.getPropertyName());
        assertEquals("xsi:type", serializer.typeId(value, JsonToken.START_OBJECT).asProperty);
    }
}
