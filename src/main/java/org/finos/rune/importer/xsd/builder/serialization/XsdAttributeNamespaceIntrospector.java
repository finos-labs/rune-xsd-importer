package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlAnnotationIntrospector;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.model.lib.annotations.RosettaAttribute;
import com.rosetta.util.serialisation.AttributeXMLConfiguration;
import com.rosetta.util.serialisation.AttributeXMLRepresentation;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Preserves the namespace of qualified XSD attributes for both XML reads and
 * writes. The standard Rosetta XML introspector already understands the
 * configured property name, but not the namespace carried by a global
 * attribute reference.
 */
public final class XsdAttributeNamespaceIntrospector extends JacksonXmlAnnotationIntrospector {

    private final RosettaXMLConfiguration xmlConfig;
    private final Map<TypeXMLConfiguration, XmlNamespaceBindings> namespaceBindings;
    private final XmlNamespaceBindings globalNamespaceBindings;

    public XsdAttributeNamespaceIntrospector(RosettaXMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
        this.namespaceBindings = namespaceBindings(xmlConfig);
        this.globalNamespaceBindings = XmlNamespaceBindings.from(xmlConfig);
    }

    @Override
    public PropertyName findNameForSerialization(Annotated annotated) {
        return configuredAttribute(annotated)
                .flatMap(configuration -> serializationPropertyName(configuration))
                .orElseGet(() -> super.findNameForSerialization(annotated));
    }

    @Override
    public PropertyName findNameForDeserialization(Annotated annotated) {
        return configuredAttribute(annotated)
                .flatMap(this::qualifiedAttributeName)
                .orElseGet(() -> super.findNameForDeserialization(annotated));
    }

    @Override
    public String findNamespace(MapperConfig<?> config, Annotated annotated) {
        Optional<ConfiguredAttribute> attribute = configuredAttribute(annotated);
        if (config instanceof SerializationConfig && attribute.flatMap(this::serializationPropertyName).isPresent()) {
            // The configured property name carries the output QName used by the writer.
            return null;
        }
        String namespace = attribute.flatMap(this::qualifiedAttributeName)
                .map(PropertyName::getNamespace)
                .orElseGet(() -> super.findNamespace(config, annotated));
        return namespace;
    }

    @Override
    public JsonInclude.Value findPropertyInclusion(Annotated annotated) {
        if (configuredAttribute(annotated).map(this::isElementProperty).orElse(false)) {
            // Preserve a present empty XML element.
            return JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_NULL);
        }
        return super.findPropertyInclusion(annotated);
    }

    private Optional<PropertyName> qualifiedAttributeName(ConfiguredAttribute attribute) {
        return Optional.of(attribute.configuration())
                .filter(configuration -> configuration.getXmlRepresentation()
                        .filter(representation -> representation == AttributeXMLRepresentation.ATTRIBUTE
                                || representation == AttributeXMLRepresentation.ELEMENT)
                        .isPresent())
                .flatMap(configuration -> qualifiedPropertyName(configuration));
    }

    private boolean isElementProperty(ConfiguredAttribute attribute) {
        return attribute.configuration().getXmlRepresentation()
                .filter(representation -> representation == AttributeXMLRepresentation.ELEMENT)
                .isPresent();
    }

    private Optional<ConfiguredAttribute> configuredAttribute(Annotated annotated) {
        if (!(annotated instanceof AnnotatedMember member)) {
            return Optional.empty();
        }

        RosettaAttribute rosettaAttribute = member.getAnnotation(RosettaAttribute.class);
        if (rosettaAttribute == null || xmlConfig == null) {
            return Optional.empty();
        }

        TypeXMLConfiguration typeConfiguration = configurationFor(member.getDeclaringClass()).orElse(null);
        if (typeConfiguration == null) {
            return Optional.empty();
        }
        AttributeXMLConfiguration attribute = typeConfiguration.getAttributes()
                .map(attributes -> attributes.get(rosettaAttribute.value()))
                .orElse(null);
        if (attribute == null) {
            return Optional.empty();
        }
        return Optional.of(new ConfiguredAttribute(attribute, namespaceBindings.get(typeConfiguration)));
    }

    private Optional<TypeXMLConfiguration> configurationFor(Class<?> declaringClass) {
        return xmlConfig.getConfigurationForType(
                ModelSymbolId.fromQualifiedName(XsdUnionIntrospector.modelQualifiedName(declaringClass)));
    }

    private Optional<PropertyName> qualifiedPropertyName(AttributeXMLConfiguration configuration) {
        return configuration.getElementRef()
                .flatMap(this::namespacePart)
                .map(namespace -> PropertyName.construct(
                        localName(configuration.getXmlName().orElseThrow()), namespace));
    }

    private Optional<PropertyName> serializationPropertyName(ConfiguredAttribute attribute) {
        AttributeXMLConfiguration configuration = attribute.configuration();
        String xmlName = configuration.getXmlName().orElse(null);
        if (xmlName == null || xmlName.isBlank()) {
            return Optional.empty();
        }

        String localName = localName(xmlName);
        Optional<String> namespace = configuration.getElementRef().flatMap(this::namespacePart);
        if (isSubstitutionGroupHead(configuration)) {
            // Rosetta's substitution-aware XML property needs the URI-based name during deserializer construction
            return qualifiedPropertyName(configuration);
        }
        boolean isAttribute = configuration.getXmlRepresentation()
                .filter(representation -> representation == AttributeXMLRepresentation.ATTRIBUTE)
                .isPresent();
        if (xmlName.contains(":")) {
            if (!isAttribute && namespace.filter(value -> isDefaultNamespace(attribute, value)).isPresent()) {
                return Optional.of(PropertyName.construct(localName));
            }
            return Optional.of(PropertyName.construct(xmlName, namespace.orElse(null)));
        }

        if (namespace.isPresent()) {
            if (isAttribute || !isDefaultNamespace(attribute, namespace.get())) {
                String lexicalName = prefixForNamespace(attribute, namespace.get())
                        .map(prefix -> prefix + ":" + localName)
                        .orElse(localName);
                return Optional.of(PropertyName.construct(lexicalName, namespace.get()));
            }
            return Optional.of(PropertyName.construct(localName));
        }
        return Optional.of(PropertyName.construct(localName));
    }

    private boolean isSubstitutionGroupHead(AttributeXMLConfiguration configuration) {
        String elementReference = configuration.getElementRef().orElse(null);
        if (elementReference == null || xmlConfig == null || xmlConfig.getTypeConfigMap() == null) {
            return false;
        }
        return xmlConfig.getTypeConfigMap().values().stream()
                .anyMatch(typeConfiguration -> typeConfiguration.getSubstitutionGroup()
                        .filter(elementReference::equals)
                        .isPresent());
    }

    private boolean isDefaultNamespace(ConfiguredAttribute attribute, String namespace) {
        // A default namespace is scoped to the element that declares it. A
        // default binding from some other document-root candidate cannot make
        // a nested member unqualified in the document currently being written.
        return attribute.namespaceBindings().declaresDefaultNamespace(namespace);
    }

    private Optional<String> prefixForNamespace(ConfiguredAttribute attribute, String namespace) {
        return attribute.namespaceBindings().prefixForNamespace(namespace)
                .or(() -> globalNamespaceBindings.prefixForNamespace(namespace));
    }

    private static Map<TypeXMLConfiguration, XmlNamespaceBindings> namespaceBindings(
            RosettaXMLConfiguration configuration) {
        if (configuration == null || configuration.getTypeConfigMap() == null) {
            return Map.of();
        }
        Map<TypeXMLConfiguration, XmlNamespaceBindings> bindings = new IdentityHashMap<>();
        configuration.getTypeConfigMap().values()
                .forEach(typeConfiguration -> bindings.put(typeConfiguration, XmlNamespaceBindings.from(typeConfiguration)));
        return Collections.unmodifiableMap(bindings);
    }

    private String localName(String xmlName) {
        int separator = xmlName.indexOf(':');
        return separator < 0 ? xmlName : xmlName.substring(separator + 1);
    }

    private Optional<String> namespacePart(String elementReference) {
        int separator = elementReference.lastIndexOf('/');
        if (separator <= 0) {
            return Optional.empty();
        }
        return Optional.of(elementReference.substring(0, separator));
    }

    private record ConfiguredAttribute(
            AttributeXMLConfiguration configuration,
            XmlNamespaceBindings namespaceBindings) {
    }
}
