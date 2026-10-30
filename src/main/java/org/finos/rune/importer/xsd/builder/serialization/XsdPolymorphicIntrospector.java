package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.AsPropertyTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.rosetta.model.lib.annotations.RuneDataType;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Collection;

public class XsdPolymorphicIntrospector extends JacksonAnnotationIntrospector {

    private static final Logger log = LoggerFactory.getLogger(XsdPolymorphicIntrospector.class);
    private final RosettaXMLConfiguration xmlConfig;

    public XsdPolymorphicIntrospector(RosettaXMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    @Override
    public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config, AnnotatedClass ac, JavaType baseType) {
        if (baseType.getRawClass().getAnnotation(RuneDataType.class) != null) {
            // Custom resolver builders preserve qualified XSD type identity on both XML reads and writes.
            XsdTypeIdResolver typeIdResolver = new XsdTypeIdResolver(baseType, xmlConfig);
            return new XsdTypeResolverBuilder()
                    .init(JsonTypeInfo.Id.CUSTOM, typeIdResolver)
                    .inclusion(JsonTypeInfo.As.PROPERTY)
                    .typeProperty("type")
                    .defaultImpl(baseType.getRawClass());
        }
        return super.findTypeResolver(config, ac, baseType);
    }

    /**
     * Custom Builder to inject our XML Tag-Aware Deserializer
     */
    private static class XsdTypeResolverBuilder extends StdTypeResolverBuilder {
        @Override
        public TypeSerializer buildTypeSerializer(
                SerializationConfig config,
                JavaType baseType,
                Collection<NamedType> subtypes) {
            if (_customIdResolver instanceof XsdTypeIdResolver resolver && resolver.supportsTypeIdOutput()) {
                return new XsdTypeAttributeSerializer(resolver, null);
            }
            return null;
        }

        @Override
        public TypeDeserializer buildTypeDeserializer(DeserializationConfig config, JavaType baseType, Collection<NamedType> subtypes) {
            TypeDeserializer defaultDeserializer = super.buildTypeDeserializer(config, baseType, subtypes);
            if (defaultDeserializer instanceof AsPropertyTypeDeserializer asPropertyTypeDeserializer) {
                return new XsdTagAwareTypeDeserializer(asPropertyTypeDeserializer);
            }
            return defaultDeserializer;
        }
    }

    /**
     * Custom Deserializer that checks xsi:type, then falls back to XML Tag Name
     */
    private static class XsdTagAwareTypeDeserializer extends AsPropertyTypeDeserializer {
        public XsdTagAwareTypeDeserializer(AsPropertyTypeDeserializer src) {
            super(src, null);
        }

        public XsdTagAwareTypeDeserializer(XsdTagAwareTypeDeserializer src, BeanProperty prop) {
            super(src, prop);
        }

        @Override
        public TypeDeserializer forProperty(BeanProperty prop) {
            return (prop == _property) ? this : new XsdTagAwareTypeDeserializer(this, prop);
        }

        @Override
        protected Object _deserializeTypedUsingDefaultImpl(JsonParser p, DeserializationContext ctxt, com.fasterxml.jackson.databind.util.TokenBuffer tb, String priorFailureMsg) throws IOException {

            JsonParser currentParser = p;
            while (currentParser instanceof JsonParserDelegate delegateParser) {
                currentParser = delegateParser.delegate();
            }

            XmlTag xmlTag = currentXmlTag(currentParser);
            if (xmlTag != null) {
                log.trace("Fallback Tag-Name Polymorphism: Evaluating XML tag '<{}>' in namespace '{}' against base type '{}'",
                        xmlTag.localName(), xmlTag.namespaceUri(), _baseType.getRawClass().getSimpleName());

                JavaType resolvedType = typeIdResolver().typeFromXmlTag(
                        ctxt, xmlTag.localName(), xmlTag.namespaceUri(), _property != null);

                if (resolvedType != null && !resolvedType.getRawClass().equals(_baseType.getRawClass())) {
                    log.trace(" -> Successfully routed tag '<{}>' to class '{}'", xmlTag.localName(), resolvedType.getRawClass().getSimpleName());
                    JsonDeserializer<Object> jsDeserializer = ctxt.findContextualValueDeserializer(resolvedType, _property);

                    if (tb != null) {
                        tb.writeEndObject();
                        p = tb.asParser(p);
                        p.nextToken();
                    }
                    return jsDeserializer.deserialize(p, ctxt);
                }

                log.trace(" -> Tag '<{}>' is configured for the base type; retaining Jackson base-type deserialization", xmlTag.localName());
            }

            return super._deserializeTypedUsingDefaultImpl(p, ctxt, tb, priorFailureMsg);
        }

        private XsdTypeIdResolver typeIdResolver() {
            if (_idResolver instanceof XsdTypeIdResolver resolver) {
                return resolver;
            }
            throw new IllegalStateException("XSD type deserializer requires an XsdTypeIdResolver");
        }

        private XmlTag currentXmlTag(JsonParser parser) throws IOException {
            if (parser instanceof FromXmlParser xmlParser) {
                XMLStreamReader reader = xmlParser.getStaxReader();
                return new XmlTag(reader.getLocalName(), reader.getNamespaceURI());
            }
            String currentName = parser.currentName();
            return currentName == null ? null : new XmlTag(currentName, null);
        }

        private record XmlTag(String localName, String namespaceUri) {
        }
    }
}
