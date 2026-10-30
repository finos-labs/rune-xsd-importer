package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class XsdUnionIntrospector extends JacksonAnnotationIntrospector {

    private static final Logger log = LoggerFactory.getLogger(XsdUnionIntrospector.class);
    private final RosettaXMLConfiguration xmlConfig;

    public XsdUnionIntrospector(RosettaXMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object findDeserializer(Annotated am) {
        if (am instanceof AnnotatedClass) {
            Class<?> rawType = am.getRawType();
            if (isMixedUnion(rawType)) {
                log.trace("XSD Union Introspector: Bypassing standard deserialization. Wiring XsdMixedUnionDeserializer for '{}'", rawType.getSimpleName());
                return new XsdMixedUnionDeserializer((Class<? extends RosettaModelObject>) rawType);
            }
        }
        return super.findDeserializer(am);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object findSerializer(Annotated am) {
        if (am instanceof AnnotatedClass) {
            Class<?> rawType = am.getRawType();
            if (isMixedUnion(rawType)) {
                log.trace("XSD Union Introspector: Bypassing standard serialization. Wiring XsdMixedUnionSerializer for '{}'", rawType.getSimpleName());
                return new XsdMixedUnionSerializer((Class<? extends RosettaModelObject>) rawType);
            }
        }
        return super.findSerializer(am);
    }

    private boolean isMixedUnion(Class<?> rawType) {
        String fqn = modelQualifiedName(rawType);

        if (xmlConfig != null) {
            ModelSymbolId symbolId = ModelSymbolId.fromQualifiedName(fqn);
            Optional<TypeXMLConfiguration> typeConfig = xmlConfig.getConfigurationForType(symbolId);

            if (typeConfig.isPresent() && typeConfig.get().getAttributes().isPresent()) {
                long valueMappedCount = typeConfig.get().getAttributes().get().values().stream()
                        .filter(attr -> {
                            if (attr.getXmlRepresentation().isPresent()) {
                                return "VALUE".equals(String.valueOf(attr.getXmlRepresentation().get()));
                            }
                            return false;
                        })
                        .count();

                // If more than one field maps to the text node, it is a Mixed Union
                return valueMappedCount > 1;
            }
        }
        return false;
    }

    static String modelQualifiedName(Class<?> type) {
        String qualifiedName = type.getName();
        int innerClass = qualifiedName.indexOf('$');
        if (innerClass >= 0) {
            qualifiedName = qualifiedName.substring(0, innerClass);
        }
        return qualifiedName.endsWith("Impl")
                ? qualifiedName.substring(0, qualifiedName.length() - 4)
                : qualifiedName;
    }
}
