package org.finos.rune.importer.xsd.builder.serialization;

import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Provides the output namespace bindings declared by generated XML
 * configuration.
 *
 * <p>XML namespace identity remains the URI. Prefixes are not imported schema
 * identity; they are writer bindings selected during configuration generation
 * so a serializer can reuse declarations placed on the document root.
 */
final class XmlNamespaceBindings {

    private static final String XMLNS = "xmlns";
    private static final String XMLNS_PREFIX = XMLNS + ":";

    private final Map<String, String> prefixesByNamespace;
    private final Set<String> defaultNamespaces;

    private XmlNamespaceBindings(Map<String, String> prefixesByNamespace, Set<String> defaultNamespaces) {
        this.prefixesByNamespace = Collections.unmodifiableMap(new TreeMap<>(prefixesByNamespace));
        this.defaultNamespaces = Collections.unmodifiableSet(new TreeSet<>(defaultNamespaces));
    }

    static XmlNamespaceBindings from(RosettaXMLConfiguration configuration) {
        if (configuration == null || configuration.getTypeConfigMap() == null) {
            return new XmlNamespaceBindings(Map.of(), Set.of());
        }
        Map<String, String> prefixesByNamespace = new HashMap<>();
        Set<String> defaultNamespaces = new TreeSet<>();
        configuration.getTypeConfigMap().values().forEach(typeConfiguration -> {
            XmlNamespaceBindings bindings = from(typeConfiguration);
            defaultNamespaces.addAll(bindings.defaultNamespaces);
            bindings.prefixesByNamespace.forEach((namespace, prefix) ->
                    prefixesByNamespace.merge(namespace, prefix, XmlNamespaceBindings::firstPrefix));
        });
        return new XmlNamespaceBindings(prefixesByNamespace, defaultNamespaces);
    }

    static XmlNamespaceBindings from(TypeXMLConfiguration typeConfiguration) {
        Map<String, String> prefixesByNamespace = new HashMap<>();
        Optional<String> defaultNamespace = Optional.ofNullable(typeConfiguration)
                .flatMap(TypeXMLConfiguration::getXmlAttributes)
                .map(attributes -> attributes.get(XMLNS))
                .filter(namespace -> !namespace.isBlank());
        Optional.ofNullable(typeConfiguration)
                .flatMap(TypeXMLConfiguration::getXmlAttributes)
                .orElseGet(Map::of)
                .forEach((attributeName, namespace) -> addPrefix(prefixesByNamespace, attributeName, namespace));
        return new XmlNamespaceBindings(prefixesByNamespace, defaultNamespace.map(Set::of).orElseGet(Set::of));
    }

    boolean declaresDefaultNamespace(String namespace) {
        return namespace != null && defaultNamespaces.contains(namespace);
    }

    Optional<String> prefixForNamespace(String namespace) {
        return Optional.ofNullable(namespace)
                .filter(value -> !value.isBlank())
                .map(prefixesByNamespace::get)
                .filter(prefix -> !prefix.isBlank());
    }

    private static void addPrefix(Map<String, String> prefixesByNamespace, String attributeName, String namespace) {
        if (attributeName == null || namespace == null || namespace.isBlank()
                || !attributeName.startsWith(XMLNS_PREFIX)
                || attributeName.length() == XMLNS_PREFIX.length()) {
            return;
        }
        String prefix = attributeName.substring(XMLNS_PREFIX.length());
        prefixesByNamespace.merge(namespace, prefix, XmlNamespaceBindings::firstPrefix);
    }

    private static String firstPrefix(String first, String second) {
        return Objects.compare(first, second, String::compareTo) <= 0 ? first : second;
    }

}
