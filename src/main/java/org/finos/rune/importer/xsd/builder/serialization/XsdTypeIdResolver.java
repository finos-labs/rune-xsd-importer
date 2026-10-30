package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XsdTypeIdResolver extends TypeIdResolverBase {

    private static final Logger log = LoggerFactory.getLogger(XsdTypeIdResolver.class);
    private final RosettaXMLConfiguration xmlConfig;
    private final XmlNamespaceBindings namespaceBindings;

    public XsdTypeIdResolver(JavaType baseType, RosettaXMLConfiguration xmlConfig) {
        super(baseType, null);
        this.xmlConfig = xmlConfig;
        this.namespaceBindings = XmlNamespaceBindings.from(xmlConfig);
    }

    @Override
    public String idFromValue(Object value) {
        if (!requiresTypeId(value)) {
            return null;
        }
        return configuredTypeId(value.getClass());
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        log.trace("Inbound Polymorphism: Resolving raw Type ID='{}' for base type '{}'", id, _baseType.getRawClass().getSimpleName());

        TypeIdParts typeId = TypeIdParts.parse(id);
        String namespaceUri = typeId.prefix() == null
                ? null
                : namespaceUri(context, typeId.prefix());

        if (typeId.prefix() != null && (namespaceUri == null || namespaceUri.isBlank())) {
            return unknownTypeId(context, id, "the XML namespace prefix cannot be resolved");
        }

        return resolveConfiguredType(context, id, typeId.localName(), namespaceUri, false);
    }

    /**
     * Resolves a local XML tag when the parser has already provided its namespace URI.
     * When {@code allowNullTag} is true, an unconfigured local name is delegated to
     * Jackson's enclosing bean deserializer; namespace mismatches and ambiguities
     * still fail. This is package-private so the tag-aware deserializer can preserve
     * XML identity without exposing a new public resolver API.
     */
    JavaType typeFromXmlTag(
            DatabindContext context,
            String localName,
            String namespaceUri,
            boolean allowNullTag) throws IOException {
        String resolvedNamespace = namespaceUri == null || namespaceUri.isBlank() ? null : namespaceUri;
        String requestedId = resolvedNamespace == null
                ? localName
                : "{" + resolvedNamespace + "}" + localName;
        return resolveConfiguredType(context, requestedId, localName, resolvedNamespace, allowNullTag);
    }

    private JavaType resolveConfiguredType(
            DatabindContext context,
            String requestedId,
            String localName,
            String namespaceUri,
            boolean allowNullTag) throws IOException {
        if (xmlConfig == null || xmlConfig.getTypeConfigMap() == null) {
            return unknownTypeId(context, requestedId, "no XML type configuration is available");
        }

        List<Map.Entry<ModelSymbolId, TypeXMLConfiguration>> matches = new ArrayList<>();
        boolean localNameConfigured = false;
        for (Map.Entry<ModelSymbolId, TypeXMLConfiguration> entry : xmlConfig.getTypeConfigMap().entrySet()) {
            TypeXMLConfiguration typeConfig = entry.getValue();
            if (!matchesLocalName(typeConfig, localName)) {
                continue;
            }
            localNameConfigured = true;

            String configuredNamespace = namespacePart(
                    typeConfig.getXmlElementFullyQualifiedName().orElse(null), localName);
            if (namespaceUri != null && !namespaceUri.equals(configuredNamespace)) {
                continue;
            }
            matches.add(entry);
        }

        if (matches.isEmpty()) {
            if (allowNullTag && !localNameConfigured) {
                return null;
            }
            String reason = localNameConfigured
                    ? "the XML namespace does not match any configured type"
                    : "the ID is not present in the generated XML type configuration";
            return unknownTypeId(context, requestedId, reason);
        }
        if (matches.size() > 1) {
            return unknownTypeId(context, requestedId,
                    "the local name is ambiguous across configured XML namespaces");
        }

        ModelSymbolId symbolId = matches.getFirst().getKey();
        String fqn = symbolId.getNamespace().withDots() + "." + symbolId.getName();
        final JavaType resolvedType;
        try {
            resolvedType = context.getTypeFactory().constructFromCanonical(fqn);
        } catch (IllegalArgumentException exception) {
            return unknownTypeId(context, requestedId,
                    "the configured Java type cannot be loaded: " + fqn);
        }

        if (!_baseType.getRawClass().isAssignableFrom(resolvedType.getRawClass())) {
            return unknownTypeId(context, requestedId,
                    "the configured Java type is not assignable to base type " + _baseType.getRawClass().getName());
        }

        log.trace(" -> Matched XML type ID '{}' to configured FQN: {}", requestedId, fqn);
        return resolvedType;
    }

    boolean requiresTypeId(Object value) {
        return value != null && !XsdUnionIntrospector.modelQualifiedName(value.getClass())
                .equals(XsdUnionIntrospector.modelQualifiedName(_baseType.getRawClass()));
    }

    boolean supportsTypeIdOutput() {
        if (xmlConfig == null) {
            return false;
        }
        ModelSymbolId baseTypeId = ModelSymbolId.fromQualifiedName(
                XsdUnionIntrospector.modelQualifiedName(_baseType.getRawClass()));
        return xmlConfig.getConfigurationForType(baseTypeId)
                .filter(configuration -> configuration.getXmlElementName().isEmpty())
                .flatMap(TypeXMLConfiguration::getAbstract)
                .orElse(false);
    }

    private String configuredTypeId(Class<?> runtimeType) {
        if (xmlConfig == null) {
            throw new IllegalStateException("Cannot serialize an XSD type ID without XML configuration");
        }
        ModelSymbolId symbolId = ModelSymbolId.fromQualifiedName(XsdUnionIntrospector.modelQualifiedName(runtimeType));
        TypeXMLConfiguration configuration = xmlConfig.getConfigurationForType(symbolId)
                .orElseThrow(() -> new IllegalStateException(
                        "No XML configuration is registered for runtime type " + runtimeType.getName()));
        String qualifiedName = configuration.getXmlElementFullyQualifiedName()
                .orElseThrow(() -> new IllegalStateException(
                        "No qualified XML type name is configured for runtime type " + runtimeType.getName()));
        int separator = qualifiedName.lastIndexOf('/');
        if (separator <= 0 || separator == qualifiedName.length() - 1) {
            throw new IllegalStateException(
                    "Malformed qualified XML type name '" + qualifiedName + "' for runtime type " + runtimeType.getName());
        }
        String namespace = qualifiedName.substring(0, separator);
        String localName = qualifiedName.substring(separator + 1);
        return namespaceBindings.prefixForNamespace(namespace)
                .map(prefix -> prefix + ":" + localName)
                .orElse(localName);
    }

    private boolean matchesLocalName(TypeXMLConfiguration typeConfig, String localName) {
        if (typeConfig.getXmlElementName().filter(localName::equals).isPresent()) {
            return true;
        }
        return typeConfig.getXmlElementFullyQualifiedName()
                .map(qualifiedName -> qualifiedName.endsWith("/" + localName))
                .orElse(false);
    }

    private String namespacePart(String qualifiedName, String localName) {
        if (qualifiedName == null || localName == null) {
            return null;
        }
        String suffix = "/" + localName;
        return qualifiedName.endsWith(suffix)
                ? qualifiedName.substring(0, qualifiedName.length() - suffix.length())
                : null;
    }

    private JavaType unknownTypeId(DatabindContext context, String id, String reason) throws IOException {
        String message = "Cannot resolve XML type ID '" + id + "' for base type '"
                + _baseType.getRawClass().getName() + "': " + reason;

        if (context instanceof DeserializationContext deserializationContext) {
            JavaType handled = deserializationContext.handleUnknownTypeId(_baseType, id, this, reason);
            if (handled != null) {
                throw JsonMappingException.from(deserializationContext.getParser(), message);
            }
        }
        JsonParser parser = context instanceof DeserializationContext deserializationContext
                ? deserializationContext.getParser()
                : null;
        throw JsonMappingException.from(parser, message);
    }

    private String namespaceUri(DatabindContext context, String prefix) {
        if (!(context instanceof DeserializationContext deserializationContext)) {
            return null;
        }

        JsonParser parser = deserializationContext.getParser();
        while (parser instanceof JsonParserDelegate delegate) {
            parser = delegate.delegate();
        }
        if (!(parser instanceof FromXmlParser xmlParser)) {
            return null;
        }

        XMLStreamReader reader = xmlParser.getStaxReader();
        String namespace = reader.getNamespaceContext().getNamespaceURI(prefix);
        return namespace == null || XMLConstants.NULL_NS_URI.equals(namespace) ? null : namespace;
    }

    private record TypeIdParts(String prefix, String localName) {
        private static TypeIdParts parse(String id) throws JsonMappingException {
            if (id == null || id.isBlank()) {
                throw JsonMappingException.from((JsonParser) null, "XML type ID must not be null or blank");
            }

            int separator = id.indexOf(':');
            if (separator < 0) {
                return new TypeIdParts(null, id);
            }
            if (separator == 0 || separator == id.length() - 1 || id.indexOf(':', separator + 1) >= 0) {
                throw JsonMappingException.from((JsonParser) null, "Malformed XML type ID: '" + id + "'");
            }
            return new TypeIdParts(id.substring(0, separator), id.substring(separator + 1));
        }
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
