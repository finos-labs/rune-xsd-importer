package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.regnosys.rosetta.common.serialisation.xml.SubstitutionMap;
import java.io.IOException;

/**
 * Writes an XSD {@code xsi:type} attribute for a nested runtime subtype.
 *
 * <p>The XML root element and its payload type remain separate concerns. This
 * serializer adds type metadata when the runtime type differs from the type
 * declared by the element being written.</p>
 */
final class XsdTypeAttributeSerializer extends TypeSerializer {

    private final XsdTypeIdResolver typeIdResolver;
    private final SubstitutionMap substitutions;

    XsdTypeAttributeSerializer(XsdTypeIdResolver typeIdResolver, SubstitutionMap substitutions) {
        this.typeIdResolver = typeIdResolver;
        this.substitutions = substitutions;
    }

    @Override
    public TypeSerializer forProperty(BeanProperty property) {
        return this;
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return JsonTypeInfo.As.PROPERTY;
    }

    @Override
    public String getPropertyName() {
        return "xsi:type";
    }

    @Override
    public TypeIdResolver getTypeIdResolver() {
        return typeIdResolver;
    }

    @Override
    public WritableTypeId writeTypePrefix(JsonGenerator generator, WritableTypeId typeId) throws IOException {
        if (typeId.valueShape != JsonToken.START_OBJECT) {
            throw new IOException("XSD xsi:type can only be written for object values");
        }
        generator.writeStartObject(typeId.forValue);
        String id = substitutions == null
                ? typeIdResolver.idFromValue(typeId.forValue)
                : typeIdResolver.idForSubstitution(typeId.forValue, substitutions);
        if (id != null) {
            writeXsiType(generator, id);
        }
        return typeId;
    }

    @Override
    public WritableTypeId writeTypeSuffix(JsonGenerator generator, WritableTypeId typeId) throws IOException {
        generator.writeEndObject();
        return typeId;
    }

    private void writeXsiType(JsonGenerator generator, String typeId) throws IOException {
        // ToXmlGenerator recognises this special field name and changes its
        // QName to the XML Schema-instance type attribute. Going through the
        // normal field-name API is important: it also advances Jackson's
        // write context before the value is written.
        generator.writeStringField("xsi:type", typeId);
    }
}
