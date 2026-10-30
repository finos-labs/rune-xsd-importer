package org.finos.rune.importer.xsd.mapper.serialization;

import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.DottedPath;
import com.rosetta.util.serialisation.AttributeXMLConfiguration;
import com.rosetta.util.serialisation.AttributeXMLRepresentation;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorChoiceNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorGroupNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.mapping.EnumMapping;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierNamingUtils;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierValidator;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import javax.xml.namespace.QName;

/**
 * Builds XML serialization configurations as a sibling output concern to Rune
 * model mutation. It derives XML identity from the source tag and consumes the
 * same mapping context as the model mappers.
 */
public final class XmlConfigurationMapper {

    private static final Logger log = LoggerFactory.getLogger(XmlConfigurationMapper.class);
    private static final String XMLNS_ATTR = "xmlns";
    private static final String XMLNSXSI_ATTR = "xmlns:xsi";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    public void registerData(
            Data data,
            String xmlTag,
            List<InspectorNode> members,
            List<InspectorAttributeNode> xmlAttributes,
            boolean isGlobalElement,
            boolean isMixedUnion,
            MappingContext context) {
        XsdParserInspector inspector = context.inspector();
        XmlConfigurationIndex lookup = context.xmlConfigurationIndex();
        RegistryKey sourceKey = RegistryKey.of(context.namespace(), xmlTag);
        Map<String, AttributeXMLConfiguration> attrConfigs = new TreeMap<>();

        if (isMixedUnion) {
            for (Attribute attr : data.getAttributes()) {
                putAttributeConfig(attrConfigs, attr.getName(), new AttributeXMLConfiguration(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(AttributeXMLRepresentation.VALUE),
                        Optional.empty(),
                        Optional.empty()
                ), "generated mixed-union attribute '" + attr.getName() + "'");

                log.trace("     -> Mapped mixed union attribute config: '{}'", attr.getName());
            }
        } else {
            if (!isGlobalElement || hasForeignGlobalInheritedMember(xmlTag, context, new HashSet<>())) {
                collectInheritedXmlConfigs(attrConfigs, context, new HashSet<>(), sourceTypeKey(sourceKey, isGlobalElement, context));
            }
            collectXmlConfigs(members, attrConfigs, context);

            for (InspectorAttributeNode m : xmlAttributes) {
                String runeName = putInspectorAttributeConfig(
                        attrConfigs, m, context, "XSD attribute");

                log.trace("     -> Mapped attribute config: '{}' -> '{}'", runeName, m.name());
            }
        }

        QName sourceName = new QName(sourceKey.namespace(), sourceKey.localName());
        RegistryKey substitutionGroup = lookup.substitutionGroup(sourceKey);
        boolean rootNamespaceConfiguration = isGlobalElement
                && lookup.isDocumentRootCandidate(sourceKey);
        String configuredXmlElementName = isGlobalElement
                ? xmlElementName(sourceKey, lookup, context)
                : null;

        Map<String, String> nsAttributes = rootNamespaceConfiguration
                ? rootNamespaceAttributes(sourceKey, members, xmlAttributes, attrConfigs, context, lookup)
                : Map.of();

        DottedPath namespacePath = DottedPath.splitOnDots(data.getModel().getName());
        ModelSymbolId id = new ModelSymbolId(namespacePath, data.getName());
        Optional<Boolean> abstractConfiguration = inspector.isAbstract(sourceName)
                ? Optional.of(true)
                : isGlobalElement && !context.registry().getXmlConfigs().containsKey(id)
                ? Optional.of(false)
                : Optional.empty();

        TypeXMLConfiguration typeConfig = new TypeXMLConfiguration(
                isGlobalElement && substitutionGroup != null
                        ? Optional.of(substitutionGroup.namespace() + "/" + substitutionGroup.localName())
                        : Optional.empty(),
                isGlobalElement ? Optional.of(configuredXmlElementName) : Optional.empty(),
                Optional.of(sourceKey.namespace().concat("/").concat(sourceKey.localName())), // Follows rune-runtime format
                abstractConfiguration,
                rootNamespaceConfiguration ? Optional.of(nsAttributes) : Optional.empty(),
                Optional.of(attrConfigs),
                Optional.empty()
        );

        context.registry().registerConfig(id, typeConfig);
        log.debug("  -> Registered XML Config for {}", id);
    }

    private Map<String, String> rootNamespaceAttributes(
            RegistryKey sourceKey,
            List<InspectorNode> members,
            List<InspectorAttributeNode> xmlAttributes,
            Map<String, AttributeXMLConfiguration> attrConfigs,
            MappingContext context,
            XmlConfigurationIndex lookup) {
        Map<String, String> namespaceAttributes = new LinkedHashMap<>();
        namespaceAttributes.put(XMLNS_ATTR, context.namespace());
        namespaceAttributes.put(XMLNSXSI_ATTR, XSI_NS);
        lookup.namespacePrefixes(context.namespace()).forEach((prefix, namespace) -> {
            if (!isRedundantTargetNamespacePrefix(namespace, attrConfigs, context)) {
                namespaceAttributes.put(XMLNS_ATTR + ":" + prefix, namespace);
            }
        });
        collectNamespaceDeclarations(members, xmlAttributes, context, lookup, namespaceAttributes);
        collectRootTypeNamespaces(sourceKey, context, lookup, namespaceAttributes);
        return namespaceAttributes;
    }

    /**
     * A typed global element can inherit its XML members from a separate
     * complex type. Those members are configured on the complex type, while
     * the element configuration owns the document-root namespace declarations.
     * Include namespaces used by the referenced type's members here so
     * serialization has usable root bindings without requiring a particular
     * lexical prefix.
     */
    private void collectRootTypeNamespaces(
            RegistryKey sourceKey,
            MappingContext context,
            XmlConfigurationIndex lookup,
            Map<String, String> namespaceAttributes) {
        RegistryKey elementType = lookup.elementType(sourceKey);
        if (elementType == null) {
            return;
        }

        Set<String> namespaces = new LinkedHashSet<>();
        collectMemberNamespaces(
                lookup.complexTypeMembers(elementType), namespaces, lookup, new HashSet<>(), ignored -> true);
        lookup.complexTypeAttributes(elementType).stream()
                .map(InspectorAttributeNode::xmlNamespace)
                .filter(Objects::nonNull)
                .filter(namespace -> !namespace.isBlank())
                .forEach(namespaces::add);
        namespaces.forEach(namespace -> addRootNamespace(namespace, context, namespaceAttributes));
    }

    private void addRootNamespace(
            String namespace,
            MappingContext context,
            Map<String, String> namespaceAttributes) {
        if (namespace == null || namespace.isBlank()) {
            return;
        }
        String prefix = prefixForNamespace(namespace, context.xmlConfigurationIndex(), context);
        if (prefix != null && !prefix.isBlank()) {
            namespaceAttributes.putIfAbsent(XMLNS_ATTR + ":" + prefix, namespace);
        }
    }

    private void collectInheritedXmlConfigs(
            Map<String, AttributeXMLConfiguration> attrConfigs,
            MappingContext context,
            Set<RegistryKey> visitedTypes,
            RegistryKey typeKey) {
        if (typeKey == null || !visitedTypes.add(typeKey)) {
            return;
        }

        RegistryKey baseType = context.xmlConfigurationIndex().complexTypeBase(typeKey);
        if (baseType == null) {
            return;
        }

        collectInheritedXmlConfigs(attrConfigs, context, visitedTypes, baseType);
        collectXmlConfigs(
                context.xmlConfigurationIndex().complexTypeMembers(baseType),
                attrConfigs,
                context);
        for (InspectorAttributeNode attribute : context.xmlConfigurationIndex().complexTypeAttributes(baseType)) {
            putInspectorAttributeConfig(attrConfigs, attribute, context, "inherited XSD attribute");
        }
    }

    private RegistryKey sourceTypeKey(
            RegistryKey sourceKey,
            boolean isGlobalElement,
            MappingContext context) {
        if (!isGlobalElement) {
            return sourceKey;
        }
        return context.xmlConfigurationIndex().elementType(sourceKey);
    }

    private boolean hasForeignGlobalInheritedMember(
            String elementName,
            MappingContext context,
            Set<RegistryKey> visitedTypes) {
        RegistryKey elementType = context.xmlConfigurationIndex().elementType(
                RegistryKey.of(context.namespace(), elementName));
        return elementType != null && hasForeignInheritedMember(elementType, context, visitedTypes);
    }

    private boolean hasForeignInheritedMember(
            RegistryKey type,
            MappingContext context,
            Set<RegistryKey> visitedTypes) {
        if (!visitedTypes.add(type)) {
            return false;
        }

        RegistryKey baseType = context.xmlConfigurationIndex().complexTypeBase(type);
        if (baseType == null) {
            return false;
        }

        Set<String> memberNamespaces = new LinkedHashSet<>();
        collectMemberNamespaces(
                context.xmlConfigurationIndex().complexTypeMembers(baseType),
                memberNamespaces,
                context.xmlConfigurationIndex(),
                new HashSet<>(),
                ignored -> true);
        boolean foreignContent = memberNamespaces.stream()
                .anyMatch(namespace -> isForeignNamespace(namespace, context));
        boolean foreignAttributes = context.xmlConfigurationIndex()
                .complexTypeAttributes(baseType).stream()
                .anyMatch(member -> isForeignNamespace(member.xmlNamespace(), context));
        return foreignContent || foreignAttributes || hasForeignInheritedMember(baseType, context, visitedTypes);
    }

    private boolean isForeignNamespace(String namespace, MappingContext context) {
        return namespace != null
                && !namespace.isBlank()
                && !context.namespace().equals(namespace);
    }

    private String xmlElementName(
            RegistryKey sourceKey,
            XmlConfigurationIndex lookup,
            MappingContext context) {
        if (!lookup.hasForeignElementReference(sourceKey)) {
            return sourceKey.localName();
        }

        String prefix = prefixForNamespace(sourceKey.namespace(), lookup, context);
        return prefix == null || prefix.isBlank()
                ? sourceKey.localName()
                : prefix + ":" + sourceKey.localName();
    }

    public void registerEnumeration(
            RosettaEnumeration runeEnum,
            String xmlTag,
            EnumMapping enumMapping,
            MappingContext context) {
        Map<String, String> enumValueMap = new LinkedHashMap<>();

        for (EnumMapping.Value valueMapping : enumMapping.values()) {
            String runeName = valueMapping.runeLabel();
            enumValueMap.put(runeName, valueMapping.xmlValue());
        }

        TypeXMLConfiguration typeConfig = new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(enumValueMap)
        );

        DottedPath namespacePath = DottedPath.splitOnDots(runeEnum.getModel().getName());
        ModelSymbolId id = new ModelSymbolId(namespacePath, runeEnum.getName());

        context.registry().registerConfig(id, typeConfig);
        log.debug("  -> Registered XML Config for {}", id);
    }

    private void collectXmlConfigs(
            List<InspectorNode> nodes,
            Map<String, AttributeXMLConfiguration> attrConfigs,
            MappingContext context) {
        for (InspectorNode node : nodes) {
            if (node instanceof InspectorAttributeNode member) {
                putInspectorAttributeConfig(attrConfigs, member, context, "XSD member");
            } else if (node instanceof InspectorGroupNode groupNode) {
                String runeName = runeAttributeName(groupNode.groupName());
                putAttributeConfig(attrConfigs, runeName, new AttributeXMLConfiguration(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(AttributeXMLRepresentation.VIRTUAL),
                        Optional.empty(),
                        Optional.empty()
                ), "XSD group '" + groupNode.groupName() + "'");
            } else if (node instanceof InspectorChoiceNode choice && choice.options() != null) {
                collectXmlConfigs(choice.options(), attrConfigs, context);
            }
        }
    }

    private String putInspectorAttributeConfig(
            Map<String, AttributeXMLConfiguration> configurations,
            InspectorAttributeNode member,
            MappingContext context,
            String sourceKind) {
        String runeName = runeAttributeName(member.name());
        putAttributeConfig(
                configurations,
                runeName,
                attributeConfiguration(member, context),
                sourceKind + " '" + member.name() + "' in namespace '" + member.xmlNamespace() + "'");
        return runeName;
    }

    private AttributeXMLConfiguration attributeConfiguration(
            InspectorAttributeNode member,
            MappingContext context) {
        return new AttributeXMLConfiguration(
                Optional.of(xmlName(member, context)),
                Optional.empty(),
                Optional.of(member.xmlRepresentation()),
                Optional.empty(),
                xmlElementReference(member));
    }

    private void putAttributeConfig(
            Map<String, AttributeXMLConfiguration> configurations,
            String runeName,
            AttributeXMLConfiguration candidate,
            String sourceDescription) {
        AttributeXMLConfiguration previous = configurations.putIfAbsent(runeName, candidate);
        if (previous != null && !previous.equals(candidate)) {
            throw new IllegalStateException(
                    "Conflicting XML configuration attribute name '" + runeName
                            + "': existing XML name '" + previous.getXmlName().orElse("<none>")
                            + "' conflicts with " + sourceDescription
                            + " (XML name '" + candidate.getXmlName().orElse("<none>") + "')");
        }
    }

    private String runeAttributeName(String sourceName) {
        String runeName = RuneIdentifierNamingUtils.toAttributeCase(sourceName);
        return RuneIdentifierValidator.requireValid(runeName, sourceName, "attribute");
    }

    private String xmlName(InspectorAttributeNode member, MappingContext context) {
        // elementRefUri already carries the qualified XML identity. Keep the
        // name lexical and let the root namespace configuration provide the
        // binding instead of manufacturing a prefix from inspector state.
        if (member.elementRefUri().isPresent()) {
            return member.name();
        }

        String namespace = member.xmlNamespace();
        if (namespace == null || namespace.isBlank()) {
            return member.name();
        }

        boolean isQualifiedElement = member.xmlRepresentation() == AttributeXMLRepresentation.ELEMENT;
        if (!isQualifiedElement) {
            return member.name();
        }

        if (context.namespace().equals(namespace)) {
            return member.name();
        }

        String prefix = prefixForNamespace(namespace, context.xmlConfigurationIndex(), context);
        return prefix == null ? member.name() : prefix + ":" + member.name();
    }

    private Optional<String> xmlElementReference(InspectorAttributeNode member) {
        if (member.xmlRepresentation() == AttributeXMLRepresentation.ATTRIBUTE
                && (member.xmlNamespace() == null || member.xmlNamespace().isBlank())) {
            return Optional.empty();
        }
        if (member.elementRefUri().isPresent()) {
            return member.elementRefUri();
        }
        if (member.xmlRepresentation() == AttributeXMLRepresentation.ELEMENT
                && member.xmlNamespace() != null
                && !member.xmlNamespace().isBlank()
        ) {
            return Optional.of(member.xmlNamespace() + "/" + member.name());
        }
        return Optional.empty();
    }

    private void collectNamespaceDeclarations(
            List<InspectorNode> members,
            List<InspectorAttributeNode> xmlAttributes,
            MappingContext context,
            XmlConfigurationIndex lookup,
            Map<String, String> namespaceAttributes) {
        Set<String> namespaces = new LinkedHashSet<>();
        collectMemberNamespaces(
                members,
                namespaces,
                lookup,
                new HashSet<>(),
                member -> requiresNamespaceDeclaration(member, context));
        xmlAttributes.stream()
                .filter(member -> requiresNamespaceDeclaration(member, context))
                .map(InspectorAttributeNode::xmlNamespace)
                .forEach(namespaces::add);

        for (String namespace : namespaces) {
            String prefix = prefixForNamespace(namespace, context.xmlConfigurationIndex(), context);
            if (prefix != null && !prefix.isBlank()) {
                namespaceAttributes.putIfAbsent(XMLNS_ATTR + ":" + prefix, namespace);
            }
        }
    }

    private void collectMemberNamespaces(
            List<InspectorNode> nodes,
            Set<String> namespaces,
            XmlConfigurationIndex lookup,
            Set<RegistryKey> visitedGroups,
            Predicate<InspectorAttributeNode> includeMember) {
        for (InspectorNode node : nodes) {
            if (node instanceof InspectorAttributeNode member) {
                if (includeMember.test(member)
                        && member.xmlNamespace() != null
                        && !member.xmlNamespace().isBlank()) {
                    namespaces.add(member.xmlNamespace());
                }
            } else if (node instanceof InspectorChoiceNode choice && choice.options() != null) {
                collectMemberNamespaces(choice.options(), namespaces, lookup, visitedGroups, includeMember);
            } else if (node instanceof InspectorGroupNode group) {
                RegistryKey groupKey = RegistryKey.parse(group.groupTypeQName());
                if (groupKey != null && visitedGroups.add(groupKey)) {
                    collectMemberNamespaces(lookup.groupMembers(groupKey), namespaces, lookup, visitedGroups, includeMember);
                }
            }
        }
    }

    private boolean isRedundantTargetNamespacePrefix(
            String namespace,
            Map<String, AttributeXMLConfiguration> attributes,
            MappingContext context) {
        return context.namespace().equals(namespace)
                && !requiresTargetNamespacePrefix(attributes, context);
    }

    private boolean requiresTargetNamespacePrefix(
            Map<String, AttributeXMLConfiguration> attributes,
            MappingContext context) {
        return attributes.values().stream()
                .anyMatch(attribute -> requiresTargetNamespacePrefix(attribute, context));
    }

    private boolean requiresTargetNamespacePrefix(
            AttributeXMLConfiguration attribute,
            MappingContext context) {
        boolean explicitlyPrefixed = attribute.getXmlName()
                .map(xmlName -> xmlName.indexOf(':') > 0)
                .orElse(false);
        if (explicitlyPrefixed) {
            return attribute.getElementRef()
                    .map(this::namespacePart)
                    .filter(context.namespace()::equals)
                    .isPresent();
        }

        return attribute.getXmlRepresentation()
                .filter(AttributeXMLRepresentation.ATTRIBUTE::equals)
                .flatMap(ignored -> attribute.getElementRef())
                .map(this::namespacePart)
                .filter(context.namespace()::equals)
                .isPresent();
    }

    private boolean requiresNamespaceDeclaration(
            InspectorAttributeNode member,
            MappingContext context) {
        String namespace = member.xmlNamespace();
        if (namespace == null || namespace.isBlank()) {
            return false;
        }
        return !context.namespace().equals(namespace)
                || member.xmlRepresentation() == AttributeXMLRepresentation.ATTRIBUTE;
    }

    private String namespacePart(String elementReference) {
        int separator = elementReference.lastIndexOf('/');
        return separator > 0 ? elementReference.substring(0, separator) : null;
    }

    private String prefixForNamespace(
            String namespace,
            XmlConfigurationIndex lookup,
            MappingContext context) {
        return lookup.prefixForNamespace(namespace, context.namespace());
    }
}
