package org.finos.rune.importer.xsd.mapper.serialization;

import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorChoiceNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorGroupNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.registry.RegistryKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * Generation-scoped cache for XML configuration lookups.
 *
 * <p>The XML mapper asks the inspector for the same schema declarations while
 * processing every generated type. This cache keeps those results stable for
 * one parser/generation scope and performs the reverse element-reference scan
 * once.</p>
 */
public final class XmlConfigurationIndex {

    private final XsdParserInspector inspector;
    private final Set<String> loadedNamespaces;
    private final Map<String, Map<String, String>> schemaNamespaces = new HashMap<>();
    private final Map<String, List<String>> globalComplexTypes = new HashMap<>();
    private final Map<String, List<String>> globalElements = new HashMap<>();
    private final Map<RegistryKey, List<InspectorNode>> complexTypeMembers = new HashMap<>();
    private final Map<RegistryKey, List<InspectorAttributeNode>> complexTypeAttributes = new HashMap<>();
    private final Map<RegistryKey, List<InspectorNode>> groupMembers = new HashMap<>();
    private final Map<RegistryKey, List<InspectorNode>> inlineElementMembers = new HashMap<>();
    private final Map<RegistryKey, RegistryKey> elementTypes = new HashMap<>();
    private final Map<RegistryKey, RegistryKey> complexTypeBases = new HashMap<>();
    private final Map<RegistryKey, RegistryKey> substitutionGroups = new HashMap<>();
    private final Map<String, Map<String, String>> namespacePrefixes = new HashMap<>();
    private Map<String, String> prefixesByNamespace;

    private Map<String, Set<String>> elementReferenceSources;

    public XmlConfigurationIndex(XsdParserInspector inspector) {
        this.inspector = inspector;
        this.loadedNamespaces = Collections.unmodifiableSet(new LinkedHashSet<>(inspector.getLoadedNamespaces()));
    }

    public Set<String> loadedNamespaces() {
        return loadedNamespaces;
    }

    public List<InspectorNode> complexTypeMembers(RegistryKey type) {
        return complexTypeMembers.computeIfAbsent(type, key -> inspector.getComplexTypeStructuredMembers(
                new QName(key.namespace(), key.localName())));
    }

    public List<InspectorAttributeNode> complexTypeAttributes(RegistryKey type) {
        return complexTypeAttributes.computeIfAbsent(type, key -> inspector.getComplexTypeAttributeMembers(
                new QName(key.namespace(), key.localName())));
    }

    public List<InspectorNode> groupMembers(RegistryKey group) {
        return groupMembers.computeIfAbsent(group, key -> inspector.getGroupStructuredMembers(
                new QName(key.namespace(), key.localName())));
    }

    public List<InspectorNode> inlineElementMembers(RegistryKey element) {
        return inlineElementMembers.computeIfAbsent(element, key -> inspector.getElementInlineStructuredMembers(
                new QName(key.namespace(), key.localName())));
    }

    public List<String> globalComplexTypes(String namespace) {
        return globalComplexTypes.computeIfAbsent(namespace, inspector::getAllGlobalComplexTypes);
    }

    public List<String> globalElements(String namespace) {
        return globalElements.computeIfAbsent(namespace, inspector::getAllGlobalElements);
    }

    public RegistryKey elementType(RegistryKey element) {
        return elementTypes.computeIfAbsent(element, key -> inspector.getElementTypeKey(
                new QName(key.namespace(), key.localName())));
    }

    public RegistryKey complexTypeBase(RegistryKey type) {
        return complexTypeBases.computeIfAbsent(type, key -> inspector.getComplexTypeBaseKey(
                new QName(key.namespace(), key.localName())));
    }

    public RegistryKey substitutionGroup(RegistryKey element) {
        return substitutionGroups.computeIfAbsent(element, key -> inspector.getElementSubstitutionGroupKey(
                new QName(key.namespace(), key.localName())));
    }

    public boolean isDocumentRootCandidate(RegistryKey element) {
        if (substitutionGroup(element) != null) {
            return false;
        }
        return !referenceSources().containsKey(elementUri(element));
    }

    public boolean hasForeignElementReference(RegistryKey element) {
        Set<String> sources = referenceSources().get(elementUri(element));
        return sources != null && sources.stream().anyMatch(source -> !source.equals(element.namespace()));
    }

    public String prefixForNamespace(String namespace, String contextNamespace) {
        return prefixesByNamespace().get(namespace);
    }

    public Map<String, String> namespacePrefixes(String contextNamespace) {
        return namespacePrefixes.computeIfAbsent(contextNamespace, this::createNamespacePrefixes);
    }

    private Map<String, Set<String>> referenceSources() {
        if (elementReferenceSources == null) {
            Map<String, Set<String>> references = new HashMap<>();
            for (String sourceNamespace : loadedNamespaces) {
                for (String type : globalComplexTypes(sourceNamespace)) {
                    collectElementReferences(
                            complexTypeMembers(RegistryKey.of(sourceNamespace, type)),
                            sourceNamespace,
                            references,
                            new HashSet<>());
                }
                for (String element : globalElements(sourceNamespace)) {
                    collectElementReferences(
                            inlineElementMembers(RegistryKey.of(sourceNamespace, element)),
                            sourceNamespace,
                            references,
                            new HashSet<>());
                }
            }
            elementReferenceSources = references;
        }
        return elementReferenceSources;
    }

    private void collectElementReferences(
            List<InspectorNode> nodes,
            String sourceNamespace,
            Map<String, Set<String>> references,
            Set<RegistryKey> visitedGroups) {
        for (InspectorNode node : nodes) {
            if (node instanceof InspectorAttributeNode member) {
                member.elementRefUri().ifPresent(uri ->
                        references.computeIfAbsent(uri, ignored -> new HashSet<>()).add(sourceNamespace));
            } else if (node instanceof InspectorChoiceNode choice && choice.options() != null) {
                collectElementReferences(choice.options(), sourceNamespace, references, visitedGroups);
            } else if (node instanceof InspectorGroupNode group) {
                RegistryKey groupKey = RegistryKey.parse(group.groupTypeQName());
                if (groupKey != null && visitedGroups.add(groupKey)) {
                    collectElementReferences(groupMembers(groupKey), sourceNamespace, references, visitedGroups);
                }
            }
        }
    }

    private String elementUri(RegistryKey element) {
        return element.namespace() + "/" + element.localName();
    }

    private Map<String, String> schemaNamespaces(String namespace) {
        return schemaNamespaces.computeIfAbsent(namespace, key -> {
            Map<String, String> prefixes = inspector.getSchemaNamespaces(key);
            return prefixes == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(prefixes));
        });
    }

    private Map<String, String> createNamespacePrefixes(String contextNamespace) {
        Map<String, String> prefixes = new LinkedHashMap<>();
        prefixesByNamespace().forEach((namespace, prefix) -> prefixes.put(prefix, namespace));
        schemaNamespaces(contextNamespace).forEach((prefix, namespace) -> {
            if (prefix != null
                    && !prefix.isBlank()
                    && namespace != null
                    && !namespace.isBlank()
                    && !loadedNamespaces.contains(namespace)) {
                prefixes.putIfAbsent(prefix, namespace);
            }
        });
        return Collections.unmodifiableMap(prefixes);
    }

    private Map<String, String> prefixesByNamespace() {
        if (prefixesByNamespace != null) {
            return prefixesByNamespace;
        }

        Map<String, String> prefixes = new LinkedHashMap<>();
        Set<String> usedPrefixes = new HashSet<>();
        List<String> namespaces = loadedNamespaces.stream()
                .filter(namespace -> namespace != null && !namespace.isBlank())
                .sorted()
                .toList();
        List<String> withoutPreferredPrefix = new ArrayList<>();

        for (String namespace : namespaces) {
            String prefix = preferredSchemaPrefix(namespace);
            if (prefix != null && usedPrefixes.add(prefix)) {
                prefixes.put(namespace, prefix);
            } else {
                withoutPreferredPrefix.add(namespace);
            }
        }

        int generatedIndex = 1;
        for (String namespace : withoutPreferredPrefix) {
            String prefix;
            do {
                prefix = "ns" + generatedIndex++;
            } while (!usedPrefixes.add(prefix));
            prefixes.put(namespace, prefix);
        }
        prefixesByNamespace = Collections.unmodifiableMap(prefixes);
        return prefixesByNamespace;
    }

    private String preferredSchemaPrefix(String namespace) {
        return loadedNamespaces.stream()
                .sorted()
                .map(this::schemaNamespaces)
                .flatMap(prefixes -> prefixes.entrySet().stream())
                .filter(entry -> namespace.equals(entry.getValue()))
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst()
                .orElse(null);
    }
}
