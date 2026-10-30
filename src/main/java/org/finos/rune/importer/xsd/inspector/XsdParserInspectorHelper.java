package org.finos.rune.importer.xsd.inspector;

import com.rosetta.util.serialisation.AttributeXMLRepresentation;
import org.finos.rune.importer.xsd.inspector.model.*;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.core.utils.NamespaceInfo;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdEnumeration;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.xml.namespace.QName;

/**
 * Package-facing adapter for navigating the XSD object model provided by the {@code xmlet} parser.
 * <p>
 * {@link XsdParserInspector} is the public inspection façade. This class owns the parser-specific
 * traversal and QName handling needed to transform XSD structures into normalized inspector nodes.
 * <p>
 * Key Responsibilities:
 * <ul>
 * <li><b>Deep Traversal:</b> Recursively walks XSD content models (Sequence, Choice, All) to collect members.</li>
 * <li><b>Structure Normalization:</b> Converts raw XSD elements into structured nodes (Attributes, Group References, Choices).</li>
 * <li><b>Reference Resolution:</b> Resolves type QNames from parser links and namespace context before dependency validation.</li>
 * <li><b>Safe Access:</b> Handles {@code xmlet}'s missing-particle behavior during traversal.</li>
 * </ul>
 */
final class XsdParserInspectorHelper {

    private static final Logger log = LoggerFactory.getLogger(XsdParserInspectorHelper.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final int UNBOUNDED_OCCURS = Integer.MAX_VALUE;
    private static final String SYNTHETIC_ATTRIBUTE_NAME = "value";

    private static final String XSD_LIST_TOKENS_PATTERN = "^(\\S+(\\s+\\S+)*)?$";

    private final List<XsdSchema> schemas;
    private final Map<RegistryKey, XsdComplexType> globalComplexTypes;
    private final Map<RegistryKey, XsdGroup> globalGroups;
    private final Map<RegistryKey, XsdSimpleType> globalSimpleTypes;
    private final Map<RegistryKey, XsdElement> globalElements;
    private final Map<RegistryKey, XsdAttribute> globalAttributes;
    private final Map<RegistryKey, XsdAttributeGroup> globalAttributeGroups;
    private final Map<String, List<XsdGroup>> globalGroupsByName;
    private final Map<String, List<XsdAttribute>> globalAttributesByName;
    private final IdentityHashMap<XsdAbstractElement, XsdSchema> owningSchemas;
    private final List<String> unsolvedReferences;

    /**
     * Constructs a helper wrapper around the provided XSD parser model.
     *
     * @param parser The parsed XSD model. Must not be null.
     */
    XsdParserInspectorHelper(XsdParser parser) {
        XsdParser parsedSchema = Objects.requireNonNull(parser, "parser must not be null");
        this.schemas = parsedSchema.getResultXsdSchemas().toList();
        this.unsolvedReferences = parsedSchema.getUnsolvedReferences().stream()
                .map(item -> item.getUnsolvedReference().getRef())
                .filter(reference -> reference != null && !reference.isBlank())
                .distinct()
                .toList();
        this.globalComplexTypes = new LinkedHashMap<>();
        this.globalGroups = new LinkedHashMap<>();
        this.globalSimpleTypes = new LinkedHashMap<>();
        this.globalElements = new LinkedHashMap<>();
        this.globalAttributes = new LinkedHashMap<>();
        this.globalAttributeGroups = new LinkedHashMap<>();
        this.globalGroupsByName = new LinkedHashMap<>();
        this.globalAttributesByName = new LinkedHashMap<>();
        this.owningSchemas = new IdentityHashMap<>();

        // Retain the first declaration in parser loading order for duplicate
        // qualified declarations, matching the established type, group, and
        // element lookup behavior. The two unqualified parser compatibility
        // paths below remain unique-only and therefore fail closed.
        for (XsdSchema schema : schemas) {
            String namespace = schema.getTargetNamespace();
            schema.getChildrenComplexTypes()
                    .filter(type -> type.getName() != null)
                    .forEach(type -> {
                        globalComplexTypes.putIfAbsent(key(namespace, type.getName()), type);
                        owningSchemas.put(type, schema);
                    });
            schema.getChildrenGroups()
                    .filter(group -> groupDeclarationName(group) != null)
                    .forEach(group -> {
                        String name = groupDeclarationName(group);
                        globalGroups.putIfAbsent(key(namespace, name), group);
                        globalGroupsByName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(group);
                        owningSchemas.put(group, schema);
                    });
            schema.getChildrenSimpleTypes()
                    .filter(type -> type.getName() != null)
                    .forEach(type -> {
                        globalSimpleTypes.putIfAbsent(key(namespace, type.getName()), type);
                        owningSchemas.put(type, schema);
                    });
            schema.getChildrenElements()
                    .filter(element -> element.getName() != null)
                    .forEach(element -> {
                        globalElements.putIfAbsent(key(namespace, element.getName()), element);
                        owningSchemas.put(element, schema);
                    });
            schema.getChildrenAttributes()
                    .filter(attribute -> attribute.getName() != null)
                    .forEach(attribute -> {
                        String name = attribute.getName();
                        globalAttributes.putIfAbsent(key(namespace, name), attribute);
                        globalAttributesByName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(attribute);
                        owningSchemas.put(attribute, schema);
                    });
            schema.getChildrenAttributeGroups()
                    .filter(group -> attributeGroupDeclarationName(group) != null)
                    .forEach(group -> {
                        globalAttributeGroups.putIfAbsent(
                                key(namespace, attributeGroupDeclarationName(group)), group);
                        owningSchemas.put(group, schema);
                    });
        }
    }

    /**
     * Retrieves a stream of all XSD schemas loaded by the parser.
     *
     * @return A stream of {@link XsdSchema} objects.
     */
    public Stream<XsdSchema> getSchemas() {
        return schemas.stream();
    }

    public Stream<XsdSchema> getSchemas(String namespace) {
        return getSchemas()
                .filter(s -> Objects.equals(s.getTargetNamespace(), namespace));
    }

    public List<String> getUnsolvedReferences() {
        return unsolvedReferences;
    }

    /**
     * Extracts all xmlns:prefix declarations from the root schema of a given namespace.
     */
    public Map<String, String> getSchemaNamespaces(String targetNamespace) {
        Map<String, String> prefixMap = new LinkedHashMap<>();

        List<XsdSchema> schemas = getSchemas().filter(s ->
                targetNamespace.equals(s.getTargetNamespace())).toList();

        if (!schemas.isEmpty()) {
            XsdSchema schema = schemas.getFirst();

            Map<String, NamespaceInfo> xmletNamespaces = schema.getNamespaces();
            if (xmletNamespaces != null) {
                xmletNamespaces.forEach((prefix, uri) -> {
                    if (prefix != null && !prefix.isEmpty() && !uri.getName().equals(targetNamespace)) {
                        prefixMap.put(prefix, uri.getName());
                    }
                });
            }
        }
        return prefixMap;
    }

    public XsdComplexType findGlobalComplexType(String namespace, String name) {
        return name == null ? null : globalComplexTypes.get(key(namespace, name));
    }

    public XsdComplexType findGlobalComplexType(QName name) {
        return name == null ? null : findGlobalComplexType(name.getNamespaceURI(), name.getLocalPart());
    }

    boolean isGlobalComplexType(RegistryKey key) {
        return key != null && globalComplexTypes.containsKey(key);
    }

    public XsdGroup findGlobalGroup(String namespace, String name) {
        return name == null ? null : globalGroups.get(key(namespace, name));
    }

    public XsdGroup findGlobalGroup(QName name) {
        return name == null ? null : findGlobalGroup(name.getNamespaceURI(), name.getLocalPart());
    }

    public XsdSimpleType findGlobalSimpleType(String namespace, String name) {
        return name == null ? null : globalSimpleTypes.get(key(namespace, name));
    }

    public XsdSimpleType findGlobalSimpleType(QName name) {
        return name == null ? null : findGlobalSimpleType(name.getNamespaceURI(), name.getLocalPart());
    }

    public XsdElement findGlobalElement(String namespace, String name) {
        return name == null ? null : globalElements.get(key(namespace, name));
    }

    public XsdElement findGlobalElement(QName name) {
        return name == null ? null : findGlobalElement(name.getNamespaceURI(), name.getLocalPart());
    }

    public Stream<RegistryKey> getGlobalSimpleTypeKeys(String namespace) {
        return globalSimpleTypes.keySet().stream().filter(key -> Objects.equals(key.namespace(), namespaceKey(namespace)));
    }

    public Stream<RegistryKey> getGlobalComplexTypeKeys(String namespace) {
        return globalComplexTypes.keySet().stream().filter(key -> Objects.equals(key.namespace(), namespaceKey(namespace)));
    }

    public Stream<RegistryKey> getGlobalElementKeys(String namespace) {
        return globalElements.keySet().stream().filter(key -> Objects.equals(key.namespace(), namespaceKey(namespace)));
    }

    public Stream<RegistryKey> getGlobalGroupKeys(String namespace) {
        return globalGroups.keySet().stream().filter(key -> Objects.equals(key.namespace(), namespaceKey(namespace)));
    }

    public String getSingleGroupReference(XsdComplexType ct) {
        if (ct.getComplexContent() != null) return null;
        if (ct.getSimpleContent() != null) return null;

        String defaultNs = safeGetTargetNamespace(ct);
        Optional<XsdAbstractElement> particle = particleOf(ct);
        if (particle.isEmpty()) return null;

        XsdAbstractElement child = particle.get();
        if (child instanceof XsdGroup group) {
            return getRefFromGroup(group, defaultNs);
        }
        if (child instanceof XsdMultipleElements container) {
            return checkContainerForSingleGroup(container.getXsdElements(), defaultNs);
        }
        return null;
    }

    /**
     * Helper to check if a container (Sequence/Choice/All) holds exactly one Group Reference.
     */
    private String checkContainerForSingleGroup(Stream<XsdAbstractElement> stream, String defaultNs) {
        List<XsdAbstractElement> children = stream.toList();

        if (children.size() == 1) {
            if (children.getFirst() instanceof XsdGroup innerGroup) {
                return getRefFromGroup(innerGroup, defaultNs);
            }
        }
        return null;
    }

    private String getRefFromGroup(XsdGroup group, String defaultNs) {
        String rawReference = rawReference(group);
        String name = rawReference != null ? rawReference : group.getName();
        if (name != null) {
            return resolveTypeQName(group, name, defaultNs, group);
        }
        return null;
    }

    public List<InspectorNode> getStructuredMembers(XsdElement element) {
        if (element == null) return Collections.emptyList();
        if (element.getType() == null && element.getXsdComplexType() != null) {
            XsdComplexType inlineType = element.getXsdComplexType();
            return getStructuredMembers(inlineType);

        }
        return Collections.emptyList();
    }

    /**
     * Determines if a Global Element is a "Virtual Type Alias".
     * * Returns TRUE if:
     * - The element defines an INLINE simpleType (e.g. <xs:restriction>).
     * - Use Case: TypeAliasMapper generates 'typeAlias Name: string(pattern: ...)'
     * * Returns FALSE if:
     * - The element is a pure wrapper (e.g. type="xs:string").
     * - Use Case: DataMapper ignores it; Inspector unwraps references to 'string'.
     */
    public boolean isVirtualTypeAliasElement(XsdElement element) {
        if (element == null) return false;

        return element.getXsdSimpleType() != null;
    }

    /**
     * Checks if the element is a pure wrapper around a native type (e.g. type="xs:string").
     * These should NOT be generated as Types or Aliases; references to them are unwrapped.
     */
    public boolean isNativeWrapperElement(org.xmlet.xsdparser.xsdelements.XsdElement element) {
        if (element == null) return false;

        if (element.getXsdSimpleType() != null) return false; // Virtual Alias (has facets)
        if (element.getXsdComplexType() != null) return false; // Complex Data

        // xmlet removes the lexical `type` attribute from some resolved
        // element clones. In that case an unprefixed XSD built-in such as
        // `type="string"` is exposed through the typed parser link rather
        // than through the raw value returned by getType().
        if (element.getTypeAsBuiltInDataType() != null) return true;

        String type = element.getType();
        if (type == null) return false;

        return type.startsWith("xs:") || type.startsWith("xsd:");
    }

    /**
     * Traverses the content model of a {@link XsdComplexType} to extract a structured list of members.
     * <p>
     * This includes members defined in the type itself as well as members inherited via
     * {@code <xs:extension>} or {@code <xs:restriction>}.
     *
     * @param ct The complex type to inspect.
     * @return A list of {@link InspectorNode} representing elements, groups, or choices.
     */
    public List<InspectorNode> getStructuredMembers(XsdComplexType ct) {
        if (ct == null) return Collections.emptyList();
        List<InspectorNode> result = new ArrayList<>();

        // Context for naming anonymous choices: "ComplexTypeName" + "Choice" + Index
        String contextName = ct.getName();
        AtomicInteger choiceCounter = new AtomicInteger(1);

        String defaultNs = safeGetTargetNamespace(ct);

        log.trace("Inspecting ComplexType '{}' (Default NS: {})", contextName, defaultNs);

        XsdComplexContent cc = ct.getComplexContent();
        if (cc != null) {
            XsdExtension ext = cc.getXsdExtension();
            if (ext != null) {
                particleOf(ext).ifPresent(particle ->
                        collectFromContainer(particle, result, contextName, choiceCounter, defaultNs));
            }
            XsdRestriction rest = cc.getXsdRestriction();
            if (rest != null) {
                collectFromContainer(rest.getSequence(), result, contextName, choiceCounter, defaultNs);
                collectFromContainer(rest.getChoice(), result, contextName, choiceCounter, defaultNs);
                collectFromContainer(rest.getAll(), result, contextName, choiceCounter, defaultNs);
                collectFromContainer(rest.getGroup(), result, contextName, choiceCounter, defaultNs);
            }
        }

        XsdSimpleContent sc = ct.getSimpleContent();
        if (sc != null) {
            handleSimpleContent(sc, result, ct);
        }

        particleOf(ct).ifPresent(particle ->
                collectFromContainer(particle, result, contextName, choiceCounter, defaultNs));

        return result;
    }

    /**
     * Synthesizes a "value" attribute for types defined with <xs:simpleContent>.
     * e.g., <xs:extension base="xs:string"> generates "value string (1..1)".
     */
    private void handleSimpleContent(XsdSimpleContent simpleContent, List<InspectorNode> nodes, XsdComplexType context) {
        String base = null;
        String documentation = null;
        String typeQName = null;

        String defaultNs = safeGetTargetNamespace(context);

        XsdExtension ext = simpleContent.getXsdExtension();
        if (ext != null) {
            base = rawAttributeValue(ext, "base");
            if (base == null && ext.getBase() != null) {
                base = ext.getBase().getRawName();
            }
            documentation = "Extension of " + base;
            typeQName = resolveTypeQName(ext.getBase(), base, defaultNs, ext);
        }

        XsdRestriction rest = simpleContent.getXsdRestriction();
        if (rest != null) {
            base = rest.getBase();
            documentation = "Restriction of " + base;
            typeQName = resolveTypeQName(null, base, defaultNs, rest);
        }

        if (base != null) {
            nodes.add(new InspectorAttributeNode(
                    SYNTHETIC_ATTRIBUTE_NAME,      // Standard Rune name for simple content value
                    typeQName,    // The base type (string, decimal, etc.)
                    1, 1,         // Always mandatory (1..1) for simple content
                    documentation,
                    defaultNs,
                    AttributeXMLRepresentation.VALUE,
                    Optional.empty()
            ));
        }
    }

    /**
     * Traverses the content model of a {@link XsdGroup} to extract its members.
     * <p>
     * Groups acts as containers wrapping Sequence, Choice, or All particles.
     *
     * @param group The global group to inspect.
     * @return A list of {@link InspectorNode} representing the group's content.
     */
    public List<InspectorNode> getStructuredMembers(XsdGroup group) {
        if (group == null) return Collections.emptyList();
        List<InspectorNode> result = new ArrayList<>();

        String contextName = groupDeclarationName(group);
        AtomicInteger choiceCounter = new AtomicInteger(1);
        String defaultNs = safeGetTargetNamespace(group);

        log.trace("Inspecting Group '{}'", contextName);

        particleOf(group).ifPresent(particle ->
                collectFromContainer(particle, result, contextName, choiceCounter, defaultNs));

        return result;
    }

    public InspectorSimpleTypeNode getStructuredMembers (XsdSimpleType simpleType) {
        if (simpleType == null) return null;

        String contextName = simpleType.getName();
        String defaultNs = safeGetTargetNamespace(simpleType);

        log.trace("Inspecting SimpleType '{}'", contextName);

        String baseTypeQName = "{http://www.w3.org/2001/XMLSchema}string"; // Default
        XsdRestriction rest = simpleType.getRestriction();
        XsdList list = simpleType.getList();

        List<String> patterns = new ArrayList<>();
        Integer minLen = null;
        Integer maxLen = null;
        String minInc = null;
        String maxInc = null;
        Integer totDig = null;
        Integer fracDig = null;

        if (rest != null) {
            baseTypeQName = resolveTypeQName(rest.getBaseAsSimpleType() != null ? rest.getBaseAsSimpleType(): null,
                    rest.getBase(),
                    defaultNs,
                    rest);

            rest.getPatterns().forEach(p -> patterns.add(p.getValue()));
            if (rest.getLength() != null) {
                minLen = rest.getLength().getValue();
                maxLen = minLen;
            }
            if (rest.getMinLength() != null) minLen = rest.getMinLength().getValue();
            if (rest.getMaxLength() != null) maxLen = rest.getMaxLength().getValue();

            if (rest.getMinInclusive() != null) minInc = rest.getMinInclusive().getValue();
            if (rest.getMaxInclusive() != null) maxInc = rest.getMaxInclusive().getValue();
            if (rest.getTotalDigits() != null) totDig = rest.getTotalDigits().getValue();
            if (rest.getFractionDigits() != null) fracDig = rest.getFractionDigits().getValue();
        }

        if (list != null) {
            patterns.add(XSD_LIST_TOKENS_PATTERN);
        }

        return new InspectorSimpleTypeNode(
                baseTypeQName, patterns, minLen, maxLen,
                minInc, maxInc, totDig, fracDig
        );
    }

    /**
     * Extracts and converts effective XML attributes from a complex type into structured attribute nodes.
     *
     * @param ct The complex type to inspect.
     * @return A list of {@link InspectorAttributeNode} representing the XML attributes.
     */
    public List<InspectorAttributeNode> getStructuredAttributeMembers(XsdComplexType ct) {
        String namespace = safeGetTargetNamespace(ct);
        return getEffectiveAttributes(ct).map(attribute -> createAttributeNode(attribute, namespace)).toList();
    }

    public Set<XsdDeclaration> getDeclaredDeclarations() {
        Set<XsdDeclaration> result = new LinkedHashSet<>();
        globalSimpleTypes.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.TYPE)));
        globalComplexTypes.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.TYPE)));
        globalElements.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.ELEMENT)));
        globalGroups.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.GROUP)));
        globalAttributes.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.ATTRIBUTE)));
        globalAttributeGroups.keySet().forEach(key -> result.add(new XsdDeclaration(key, XsdDeclarationRole.ATTRIBUTE_GROUP)));
        return Set.copyOf(result);
    }

    /**
     * Collects the required references exposed by the normalized inspector
     * model. This is intentionally separate from mapper population so that an
     * invalid dependency cannot publish a partially populated Rune model.
     */
    public List<XsdDependency> getRequiredDependencies() {
        List<XsdDependency> result = new ArrayList<>();
        getSchemas().forEach(schema -> {
            String namespace = schema.getTargetNamespace();

            schema.getChildrenSimpleTypes().forEach(simpleType -> {
                XsdDeclaration source = declaration(namespace, simpleType.getName(), XsdDeclarationRole.TYPE);
                InspectorSimpleTypeNode node = getStructuredMembers(simpleType);
                addDependency(result, source, node.baseTypeQName(), XsdDeclarationRole.TYPE,
                        "simple type '" + source + "' base type",
                        XsdDependencyType.BASE_TYPE_REFERENCE);
                addUnionDependencies(result, source, simpleType);
            });

            schema.getChildrenComplexTypes().forEach(complexType -> {
                XsdDeclaration source = declaration(namespace, complexType.getName(), XsdDeclarationRole.TYPE);
                addDependency(result, source, getComplexTypeBaseQName(complexType), XsdDeclarationRole.TYPE,
                        "complex type '" + source + "' base type",
                        XsdDependencyType.BASE_TYPE_REFERENCE);
                addNodeDependencies(result, source, getStructuredMembers(complexType),
                        "complex type '" + source + "' content",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE);
                addAttributeDependencies(result, source, complexType, namespace);
                addAttributeGroupDependencies(result, source, complexType, namespace);
            });

            schema.getChildrenGroups().forEach(group -> {
                XsdDeclaration source = declaration(namespace, groupDeclarationName(group), XsdDeclarationRole.GROUP);
                addNodeDependencies(result, source, getStructuredMembers(group),
                        "group '" + source + "' content",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE);
            });

            schema.getChildrenElements().forEach(element -> {
                XsdDeclaration source = declaration(namespace, element.getName(), XsdDeclarationRole.ELEMENT);
                if (element.getType() != null) {
                    addDependency(result, source,
                            resolveTypeQName(element.getTypeAsXsd(), rawAttributeValue(element, "type"), namespace, element),
                            XsdDeclarationRole.TYPE,
                            "element '" + source + "' type",
                            XsdDependencyType.ELEMENT_REFERENCE);
                }
                addNodeDependencies(result, source, getStructuredMembers(element),
                        "element '" + source + "' inline content",
                        XsdDependencyType.TYPE_MEMBER_REFERENCE);
                if (element.getType() == null && element.getXsdComplexType() != null) {
                    getStructuredAttributeMembers(element.getXsdComplexType()).forEach(attribute ->
                            addDependency(result, source, attribute.typeQName(),
                                    XsdDeclarationRole.TYPE,
                                    "element '" + source + "' inline attribute '" + attribute.name() + "'",
                                    XsdDependencyType.TYPE_MEMBER_REFERENCE));
                }
                String substitutionGroup = resolveElementSubstitutionGroupQName(element, namespace);
                if (substitutionGroup != null) {
                    addDependency(result, source,
                            substitutionGroup,
                            XsdDeclarationRole.ELEMENT,
                            "element '" + source + "' substitution head",
                            XsdDependencyType.SUBSTITUTION_REFERENCE);
                }
            });
        });
        return List.copyOf(result);
    }

    private void addUnionDependencies(
            List<XsdDependency> result,
            XsdDeclaration source,
            XsdSimpleType simpleType) {
        XsdUnion union = simpleType.getUnion();
        if (union == null) return;

        for (String memberType : union.getMemberTypesList()) {
            addDependency(result, source, resolveUnionMemberType(simpleType, memberType), XsdDeclarationRole.TYPE,
                    "simple type '" + source + "' union member '" + memberType + "'",
                    XsdDependencyType.BASE_TYPE_REFERENCE);
        }
        for (XsdSimpleType inlineType : union.getUnionElements()) {
            XsdRestriction restriction = inlineType.getRestriction();
            if (inlineType.getName() == null && restriction != null && restriction.getBase() != null) {
                addDependency(result, source, resolveTypeQName(
                                null,
                                restriction.getBase(),
                                safeGetTargetNamespace(simpleType),
                                restriction),
                        XsdDeclarationRole.TYPE,
                        "simple type '" + source + "' inline union member base type",
                        XsdDependencyType.BASE_TYPE_REFERENCE);
            }
        }
    }

    private RegistryKey key(String namespace, String localName) {
        return RegistryKey.of(namespaceKey(namespace), localName);
    }

    private String namespaceKey(String namespace) {
        return namespace == null ? "" : namespace;
    }

    private XsdDeclaration declaration(String namespace, String localName, XsdDeclarationRole role) {
        return new XsdDeclaration(key(namespace, localName), role);
    }

    private void addAttributeDependencies(
            List<XsdDependency> result,
            XsdDeclaration source,
            XsdComplexType complexType,
            String namespace) {
        getEffectiveAttributes(complexType).forEach(attribute ->
                addDependency(result, source, createAttributeNode(attribute, namespace).typeQName(),
                        XsdDeclarationRole.TYPE,
                         "complex type '" + source + "' attribute '" + attributeEffectiveName(attribute) + "'",
                         XsdDependencyType.TYPE_MEMBER_REFERENCE));
    }

    private void addAttributeGroupDependencies(
            List<XsdDependency> result,
            XsdDeclaration source,
            XsdComplexType complexType,
            String namespace) {
        complexType.getAllXsdAttributeGroups().forEach(group -> {
            String rawReference = rawAttributeValue(group, "ref");
            if (rawReference == null || rawReference.isBlank()) {
                return;
            }
            addDependency(result, source,
                    resolveReferenceQName(rawReference, group, namespace),
                    XsdDeclarationRole.ATTRIBUTE_GROUP,
                    "complex type '" + source + "' attribute group '" + localPart(rawReference) + "'",
                    XsdDependencyType.GROUP_REFERENCE);
        });
    }

    private void addNodeDependencies(
            List<XsdDependency> result,
            XsdDeclaration source,
            List<InspectorNode> nodes,
            String context,
            XsdDependencyType memberType) {
        for (InspectorNode node : nodes) {
            if (node instanceof InspectorAttributeNode attribute) {
                XsdDependencyType type = attribute.xmlRepresentation() == AttributeXMLRepresentation.VALUE
                        ? XsdDependencyType.BASE_TYPE_REFERENCE
                        : referencesElementDeclaration(attribute)
                                ? XsdDependencyType.ELEMENT_REFERENCE
                                : memberType;
                addDependency(result, source, attribute.typeQName(),
                        type == XsdDependencyType.ELEMENT_REFERENCE
                                ? XsdDeclarationRole.ELEMENT
                                : XsdDeclarationRole.TYPE,
                        context + " attribute '" + attribute.name() + "'",
                        type);
            } else if (node instanceof InspectorGroupNode group) {
                addDependency(result, source, group.groupTypeQName(), XsdDeclarationRole.GROUP,
                        context + " group '" + group.groupName() + "'",
                        XsdDependencyType.GROUP_REFERENCE);
            } else if (node instanceof InspectorChoiceNode choice) {
                addNodeDependencies(result, source, choice.options(),
                        context + " choice '" + choice.syntheticTypeName() + "'", memberType);
            }
        }
    }

    private boolean referencesElementDeclaration(InspectorAttributeNode attribute) {
        return attribute.elementRefUri()
                .map(RegistryKey::parse)
                .map(RegistryKey::toString)
                .map(elementQName -> elementQName.equals(attribute.typeQName()))
                .orElse(false);
    }

    private void addDependency(
            List<XsdDependency> result,
            XsdDeclaration source,
            String targetQName,
            XsdDeclarationRole targetRole,
            String context,
            XsdDependencyType type) {
        if (targetQName != null && !targetQName.isBlank()) {
            result.add(new XsdDependency(source, targetQName, targetRole, context, type));
        }
    }

    public String getComplexTypeBaseQName(XsdComplexType complexType) {
        if (complexType == null || complexType.getComplexContent() == null) return null;
        XsdExtension extension = complexType.getComplexContent().getXsdExtension();
        if (extension == null) return null;
        String namespace = safeGetTargetNamespace(complexType);
        if (extension.getBase() == null) return unresolvedQName(null);
        String rawBase = rawAttributeValue(extension, "base");
        if (rawBase == null) {
            rawBase = extension.getBase().getRawName();
        }
        return resolveTypeQName(extension.getBase(), rawBase, namespace, extension);
    }

    /**
     * Recursively collects nodes from a generic container element.
     *
     * @param element        The container element (e.g., XsdGroup, XsdSequence).
     * @param accumulator    The list to populate with found nodes.
     * @param contextName    The name of the parent type (for naming context).
     * @param choiceCounter  Counter for generating unique names for anonymous choices.
     * @param namespace      The target namespace.
     */
    private void collectFromContainer(XsdAbstractElement element, List<InspectorNode> accumulator, String contextName, AtomicInteger choiceCounter, String namespace) {
        collectFromContainer(element, accumulator, contextName, choiceCounter, namespace, new Occurrence(1, 1));
    }

    private void collectFromContainer(XsdAbstractElement element, List<InspectorNode> accumulator, String contextName, AtomicInteger choiceCounter, String namespace, Occurrence context) {
        if (element == null) return;

        if (element instanceof XsdMultipleElements me) {
            collectFromMultipleElements(me, accumulator, contextName, choiceCounter, namespace, context);
        }
        else if (element instanceof XsdGroup g) {
            accumulator.add(createGroupNode(g, namespace, context));
        }
    }

    /**
     * Recursively collects nodes from multi-element containers (Sequence, Choice, All).
     */
    private void collectFromMultipleElements(XsdMultipleElements container, List<InspectorNode> accumulator, String contextName, AtomicInteger choiceCounter, String namespace, Occurrence context) {
        Occurrence effectiveContainer = multiply(context, occurrenceOf(container));
        if (container instanceof XsdSequence || container instanceof XsdAll) {
            container.getXsdElements()
                    .forEach(child -> processChild(child, accumulator, contextName, choiceCounter, namespace, effectiveContainer));
        } else if (container instanceof XsdChoice choice) {
            String syntheticName = contextName + "Choice" + (choiceCounter.getAndIncrement());

            List<InspectorNode> options = new ArrayList<>();
            choice.getXsdElements().forEach(child -> processChild(child, options, contextName, choiceCounter, namespace, effectiveContainer));

            accumulator.add(createChoiceNode(syntheticName, options, effectiveContainer));
        }
    }

    /**
     * Processes a single child element encountered during traversal.
     */
    private void processChild(XsdAbstractElement child, List<InspectorNode> accumulator, String contextName, AtomicInteger choiceCounter, String namespace, Occurrence context) {
        if (child instanceof XsdElement e) {
            accumulator.add(createAttributeNode(e, namespace, context));
        } else if (child instanceof XsdGroup g) {
            accumulator.add(createGroupNode(g, namespace, context));
        } else if (child instanceof XsdMultipleElements me) {
            collectFromMultipleElements(me, accumulator, contextName, choiceCounter, namespace, context);
        }
    }

    /**
     * Creates an {@link InspectorAttributeNode} from an XSD Element.
     * <p>
     * Resolves type references and handles global element pointers.
     */
    private InspectorAttributeNode createAttributeNode(XsdElement e, String namespace, Occurrence context) {
        Occurrence occurrence = multiply(context, occurrenceOf(e));
        String name = e.getName();

        String rawRef = rawAttributeValue(e, "ref");
        boolean isExplicitRef = rawRef != null;
        XsdElement parserResolvedGlobal = findParserResolvedGlobalElement(e, namespace);

        boolean isImplicitRef = !isExplicitRef
                && (isGlobalElementDeclaration(e, namespace) || parserResolvedGlobal != null);

        XsdElement targetGlobal = null;
        Optional<String> elementRefUri = Optional.empty();
        String targetNamespace = namespace;

        if (isExplicitRef) {
            String refName = localPart(rawRef);
            name = refName;
            String referenceNamespace = resolveReferenceNamespace(rawRef, e, namespace);
            if (referenceNamespace != null) {
                targetGlobal = findGlobalElement(referenceNamespace, refName);
            }
        } else if (isImplicitRef) {
            targetGlobal = parserResolvedGlobal == null ? e : parserResolvedGlobal;
        }

        if (targetGlobal != null) {
            targetNamespace = safeGetTargetNamespace(targetGlobal);
            if (targetNamespace == null || targetNamespace.isBlank()) {
                targetNamespace = namespace;
            }
            String uri = targetNamespace + "/" + targetGlobal.getName();
            elementRefUri = Optional.of(uri);

            log.trace("Resolved elementRefUri: '{}' for element '{}'", uri, name);
        }

        // Only virtual aliases remain composition references. Complex types and
        // native wrappers are unwrapped to avoid nested rootType wrappers.
        boolean isVirtualAlias = targetGlobal != null && this.isVirtualTypeAliasElement(targetGlobal);

        log.trace("Evaluating Reference Strategy for '{}': targetGlobalFound={}, isVirtualAlias={}",
                name, targetGlobal != null, isVirtualAlias);

        if (isVirtualAlias) {
            log.trace(" -> Strategy: Composition (Rune Reference)");

            String elementQName = "{" + targetNamespace + "}" + name;

            return new InspectorAttributeNode(
                    name,
                    elementQName,
                    occurrence.min(),
                    occurrence.max(),
                    "Element Ref: " + name,
                    targetNamespace,
                    AttributeXMLRepresentation.ELEMENT,
                    elementRefUri
            );
        }

        log.trace(" -> Strategy: Unwrap underlying type");

        // xmlet can represent an imported element reference as a clone under
        // the referencing schema. When that happens, the copied lexical type
        // QName belongs to the referenced element's schema, not the clone's
        // owning schema (for example ds:SignatureType in XMLDSig).
        XsdElement typeSource = targetGlobal != null ? targetGlobal : e;
        String rawType = rawAttributeValue(typeSource, "type");
        XsdNamedElements typeObj = typeSource.getXsdComplexType();
        if (typeObj == null) typeObj = typeSource.getXsdSimpleType();
        String typeNamespace = safeGetTargetNamespace(typeSource);
        if (typeNamespace == null || typeNamespace.isBlank()) typeNamespace = namespace;

        String typeQName = resolveTypeQName(typeObj, rawType, typeNamespace, typeSource);
        String elementNamespace = targetGlobal == null ? namespace : targetNamespace;

        log.trace("    createAttributeNode unwrapped '{}' to typeQName='{}'", name, typeQName);

        return new InspectorAttributeNode(
                name,
                typeQName,
                occurrence.min(),
                occurrence.max(),
                "Element: " + name,
                elementNamespace,
                AttributeXMLRepresentation.ELEMENT,
                elementRefUri
        );
    }

    private boolean isGlobalElementDeclaration(XsdElement element, String namespace) {
        if (element == null) return false;
        if (element.getCloneOf() instanceof XsdElement clone) {
            return isGlobalElementDeclaration(clone, namespace);
        }
        return element.getName() != null
                && globalElements.get(key(namespace, element.getName())) == element
                && element.getParent() instanceof XsdSchema;
    }

    public boolean isGlobalElement(String namespace, String name) {
        return isGlobalElementDeclaration(findGlobalElement(namespace, name), namespace);
    }

    private XsdElement findParserResolvedGlobalElement(XsdElement element, String namespace) {
        if (element == null || element.getCloneOf() == null) return null;
        if (element.getCloneOf() instanceof XsdElement clone
                // Imported references may be materialized as clones beneath
                // the referencing schema. Validate the clone in its own
                // declaration namespace rather than the caller's namespace.
                && isGlobalElementDeclaration(clone, safeGetTargetNamespace(clone))) {
            return clone;
        }
        return null;
    }

    private InspectorAttributeNode createAttributeNode(XsdAttribute attr, String namespace) {
        String attributeContextNamespace = safeGetTargetNamespace(attr);
        if (attributeContextNamespace == null || attributeContextNamespace.isBlank()) {
            attributeContextNamespace = namespace;
        }
        String name = attributeEffectiveName(attr);
        String rawReference = rawAttributeReference(attr);
        String referenceNamespace = resolveReferenceNamespace(rawReference, attr, attributeContextNamespace);
        XsdAttribute referencedAttribute = rawReference == null || referenceNamespace == null
                ? null
                : findGlobalAttribute(referenceNamespace, localPart(rawReference));
        if (referencedAttribute == null
                && rawReference == null
                && attr.getParent() == null
                && attr.getCloneOf() != null) {
            referencedAttribute = findOnlyGlobalAttribute(name);
            if (referencedAttribute != null) {
                referenceNamespace = safeGetTargetNamespace(referencedAttribute);
            }
        }
        String rawType = rawAttributeValue(attr, "type");
        String typeNamespace = attributeContextNamespace;
        if (rawType == null && referencedAttribute != null) {
            rawType = rawAttributeValue(referencedAttribute, "type");
            typeNamespace = referenceNamespace;
        }
        String typeQName = resolveTypeQName(null, rawType, typeNamespace, attr);
        String attributeNamespace;
        Optional<String> attributeRefUri = Optional.empty();
        boolean detachedGlobalReference = attr.getParent() == null
                && isGlobalAttributeDeclaration(attr, attributeContextNamespace);
        boolean parserResolvedGlobalReference = rawReference == null
                && attr.getParent() == null
                && attr.getCloneOf() != null
                && referencedAttribute != null;
        if ((rawReference != null && !rawReference.isBlank())
                || detachedGlobalReference
                || parserResolvedGlobalReference) {
            if (referenceNamespace == null) {
                referenceNamespace = attributeContextNamespace == null ? "" : attributeContextNamespace;
            }
            attributeNamespace = referenceNamespace;
            String referenceName = rawReference == null ? name : localPart(rawReference);
            attributeRefUri = Optional.of(referenceNamespace + "/" + referenceName);
        } else {
            attributeNamespace = "";
        }
        log.trace("createAttributeNode for XsdAttribute with name='{}' and typeQName='{}'", name, typeQName);

        return new InspectorAttributeNode(
                name,
                typeQName,
                "required".equalsIgnoreCase(attr.getUse()) ? 1 : 0,
                1,
                "Attribute: " + name,
                attributeNamespace,
                AttributeXMLRepresentation.ATTRIBUTE,
                attributeRefUri
        );
    }

    private boolean isGlobalAttributeDeclaration(XsdAttribute attribute, String namespace) {
        String attributeNamespace = safeGetTargetNamespace(attribute);
        if (attributeNamespace == null || attributeNamespace.isBlank()) {
            attributeNamespace = namespace == null ? "" : namespace;
        }
        String attributeName = attributeEffectiveName(attribute);
        String resolvedNamespace = attributeNamespace;
        return getSchemas()
                .filter(schema -> Objects.equals(schema.getTargetNamespace(), resolvedNamespace))
                .anyMatch(schema -> schema.getChildrenAttributes()
                .anyMatch(candidate -> Objects.equals(candidate.getName(), attributeName)));
    }

    /**
     * xmlet can replace an attribute reference with a detached clone and drop
     * the lexical {@code ref} attribute from the materialized node. Preserve
     * the original reference when the parser exposes the clone provenance.
     */
    private String rawAttributeReference(XsdAttribute attribute) {
        return rawAttributeValue(attribute, "ref");
    }

    /**
     * Safely retrieves the targetNamespace for a given element.
     * Navigates up the hierarchy to find the owning XsdSchema.
     *
     * @param element The XSD element (ComplexType, Element, Attribute, etc.)
     * @return The targetNamespace string, or an empty string if not found.
     */
    private String safeGetTargetNamespace(XsdAbstractElement element) {
        if (element == null) return "";

        XsdSchema owningSchema = findOwningSchema(element);
        if (owningSchema != null && owningSchema.getTargetNamespace() != null) {
            return owningSchema.getTargetNamespace();
        }

        return "";
    }

    /**
     * Creates an {@link InspectorGroupNode} representing a reference to a Global Group.
     */
    private InspectorGroupNode createGroupNode(XsdGroup g, String namespace, Occurrence context) {
        Occurrence occurrence = multiply(context, occurrenceOf(g));
        String rawReference = rawReference(g);
        String name = localPart(rawReference != null ? rawReference : g.getName());
        String referenceNamespace = resolveReferenceNamespace(rawReference, g, namespace);
        XsdGroup global = referenceNamespace == null || name == null
                ? null
                : findGlobalGroup(referenceNamespace, name);
        if (global == null && prefixPart(rawReference) == null) {
            global = findResolvedGroup(name);
        }
        String typeQName = global != null
                ? "{" + safeGetTargetNamespace(global) + "}" + name
                : resolveTypeQName(g, rawReference != null ? rawReference : name, namespace, g);

        log.trace("createGroupNode for XsdGroup with name='{}' and typeQName='{}'", name, typeQName);

        return new InspectorGroupNode(
                name,
                typeQName,
                occurrence.min(),
                occurrence.max(),
                "Group Ref: " + name);
    }

    private InspectorChoiceNode createChoiceNode(String name, List<InspectorNode> options, Occurrence occurrence) {
        return new InspectorChoiceNode(
                name,
                options,
                occurrence.min(),
                occurrence.max()
        );
    }

    private record Occurrence(int min, int max) {}

    private record AttributeIdentity(String namespace, String localName) {}

    private Occurrence occurrenceOf(XsdAbstractElement element) {
        if (element instanceof XsdElement e) {
            return new Occurrence(defaultOccurs(e.getMinOccurs()), parseOccurs(e.getMaxOccurs()));
        }
        if (element instanceof XsdGroup g) {
            return new Occurrence(defaultOccurs(g.getMinOccurs()), parseOccurs(g.getMaxOccurs()));
        }
        if (element instanceof XsdSequence sequence) {
            return new Occurrence(defaultOccurs(sequence.getMinOccurs()), parseOccurs(sequence.getMaxOccurs()));
        }
        if (element instanceof XsdChoice choice) {
            return new Occurrence(defaultOccurs(choice.getMinOccurs()), parseOccurs(choice.getMaxOccurs()));
        }
        if (element instanceof XsdAll all) {
            return new Occurrence(defaultOccurs(all.getMinOccurs()), parseOccurs(all.getMaxOccurs()));
        }
        return new Occurrence(1, 1);
    }

    private Occurrence multiply(Occurrence outer, Occurrence inner) {
        return new Occurrence(
                multiplyOccurs(outer.min(), inner.min()),
                multiplyOccurs(outer.max(), inner.max()));
    }

    private int multiplyOccurs(int left, int right) {
        if (left == 0 || right == 0) return 0;
        long product = (long) left * right;
        return product >= UNBOUNDED_OCCURS ? UNBOUNDED_OCCURS : (int) product;
    }

    private int defaultOccurs(Integer occurs) {
        return occurs == null ? 1 : occurs;
    }

    /**
     * Retrieves all effective XML attributes for a ComplexType, including those inherited from
     * extensions or attribute groups.
     *
     * @param ct The complex type to inspect.
     * @return A stream of {@link XsdAttribute} objects.
     */
    private Stream<XsdAttribute> getEffectiveAttributes(XsdComplexType ct) {
        if (ct == null) return Stream.empty();

        Stream<XsdAttribute> fromLocal = safeStream(ct.getXsdAttributes());
        Stream<XsdAttribute> fromTypeAll = safeStream(ct.getAllXsdAttributes());
        Stream<XsdAttribute> fromExtensionLocal = getExtensionLocalAttributes(ct);
        Stream<XsdAttribute> fromAttributeGroups = getAttributesFromAllAttributeGroups(ct);

        Stream<XsdAttribute> combined = Stream.concat(
                fromLocal,
                Stream.concat(
                        Stream.concat(fromTypeAll, fromExtensionLocal),
                        fromAttributeGroups
                )
        );

        Optional<XsdComplexType> baseCt = getComplexContentExtensionBase(ct);
        if (baseCt.isPresent()) {
            combined = Stream.concat(getEffectiveAttributes(baseCt.get()), combined);
        }

        return combined
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        this::attributeIdentity,
                        a -> a,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream();
    }

    private Stream<XsdAttribute> getExtensionLocalAttributes(XsdComplexType ct) {
        XsdComplexContent cc = ct.getComplexContent();
        if (cc != null) {
            XsdExtension ext = cc.getXsdExtension();
            if (ext != null) return safeStream(ext.getXsdAttributes());
        }
        XsdSimpleContent sc = ct.getSimpleContent();
        if (sc != null) {
            XsdExtension ext = sc.getXsdExtension();
            if (ext != null) return safeStream(ext.getXsdAttributes());
        }
        return Stream.empty();
    }

    private Stream<XsdAttribute> getAttributesFromAllAttributeGroups(XsdComplexType ct) {
        if (ct == null) return Stream.empty();
        String namespace = safeGetTargetNamespace(ct);
        return ct.getAllXsdAttributeGroups()
                .filter(Objects::nonNull)
                .map(group -> resolveAttributeGroup(group, namespace))
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(group -> safeStream(group.getXsdAttributes()));
    }

    private XsdAttributeGroup resolveAttributeGroup(XsdAttributeGroup group, String defaultNamespace) {
        String rawReference = rawAttributeValue(group, "ref");
        String name = localPart(rawReference == null ? attributeGroupDeclarationName(group) : rawReference);
        String namespace = rawReference == null
                ? safeGetTargetNamespace(group)
                : resolveReferenceNamespace(rawReference, group, defaultNamespace);
        if ((namespace == null || namespace.isBlank()) && prefixPart(rawReference) == null) {
            namespace = defaultNamespace;
        }
        return name == null || namespace == null ? null : globalAttributeGroups.get(key(namespace, name));
    }

    /**
     * Resolves the effective name of an attribute, handling references.
     *
     * @param a The attribute to inspect.
     * @return The local name, or the referenced name if applicable.
     */
    private String attributeEffectiveName(XsdAttribute a) {
        if (a == null) return null;
        String name = rawAttributeValue(a, "name");
        if (name == null || name.isBlank()) {
            name = a.getName();
        }
        if (name != null && !name.isBlank()) return name;
        String ref = rawAttributeValue(a, "ref");
        return (ref == null || ref.isBlank()) ? null : localPart(ref);
    }

    private AttributeIdentity attributeIdentity(XsdAttribute attribute) {
        String rawReference = rawAttributeValue(attribute, "ref");
        String namespace = rawReference == null
                ? safeGetTargetNamespace(attribute)
                : resolveReferenceNamespace(rawReference, attribute, safeGetTargetNamespace(attribute));
        return new AttributeIdentity(namespaceKey(namespace), attributeEffectiveName(attribute));
    }

    private Optional<XsdComplexType> getComplexContentExtensionBase(XsdComplexType ct) {
        if (ct == null) return Optional.empty();
        XsdComplexContent cc = ct.getComplexContent();
        if (cc != null && cc.getXsdExtension() != null) {
            XsdNamedElements base = cc.getXsdExtension().getBase();
            if (base instanceof XsdComplexType t) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * Finds the XsdUnion restriction for a given global SimpleType.
     */
    private XsdUnion getUnionForType(QName typeName) {
        return getUnionForType(findSimpleType(typeName));
    }

    private XsdUnion getUnionForType(XsdSimpleType simpleType) {
        if (simpleType != null && simpleType.getUnion() != null) {
            return simpleType.getUnion();
        }
        return null;
    }

    /**
     * Checks if the given SimpleType is a Union type.
     */
    public boolean isUnionType(QName typeName) {
        return getUnionForType(typeName) != null;
    }

    /**
     * Checks if a specific qualified simple type is an enumeration.
     */
    public boolean isSimpleTypeEnumeration(QName simpleTypeName) {
        return !getSimpleTypeEnumValues(simpleTypeName).isEmpty();
    }

    /**
     * Retrieves the enumeration values associated with a specific qualified simple type.
     */
    public List<String> getSimpleTypeEnumValues(QName simpleTypeName) {
        return getSimpleTypeEnumValues(findSimpleType(simpleTypeName));
    }

    private List<String> getSimpleTypeEnumValues(XsdSimpleType simpleType) {
        if (simpleType == null || simpleType.getRestriction() == null) {
            return Collections.emptyList();
        }
        return simpleType.getRestriction().getEnumeration().stream()
                .map(XsdEnumeration::getValue)
                .toList();
    }

    /**
     * Recursively evaluates if a union consists strictly of Enumeration members.
     * This handles both referenced member types and inline anonymous simple types.
     */
    public boolean isUnionOfEnums(QName typeName) {
        return enumValues(findSimpleType(typeName), new HashSet<>()).isPresent();
    }

    private Optional<List<String>> enumValues(XsdSimpleType simpleType, Set<RegistryKey> visiting) {
        XsdUnion union = getUnionForType(simpleType);
        RegistryKey typeKey = simpleTypeKey(simpleType);
        if (union == null || typeKey == null || !visiting.add(typeKey)) return Optional.empty();

        try {
            List<String> values = new ArrayList<>();
            for (String part : union.getMemberTypesList()) {
                String memberQName = resolveUnionMemberType(simpleType, part);
                XsdSimpleType memberType = findSimpleType(RegistryKey.parse(memberQName));
                List<String> memberValues = getSimpleTypeEnumValues(memberType);
                if (!memberValues.isEmpty()) {
                    values.addAll(memberValues);
                    continue;
                }

                Optional<List<String>> nestedValues = enumValues(memberType, visiting);
                if (nestedValues.isEmpty()) return Optional.empty();
                values.addAll(nestedValues.get());
            }

            for (XsdSimpleType inlineType : union.getUnionElements()) {
                if (inlineType.getName() != null) continue;
                List<String> inlineValues = getSimpleTypeEnumValues(inlineType);
                if (inlineValues.isEmpty()) return Optional.empty();
                values.addAll(inlineValues);
            }

            return values.isEmpty() ? Optional.empty() : Optional.of(values.stream().distinct().toList());
        } finally {
            visiting.remove(typeKey);
        }
    }

    /**
     * Extracts and flattens all enumeration values from a pure Enum Union.
     */
    public List<String> getUnionEnumValues(QName typeName) {
        return enumValues(findSimpleType(typeName), new HashSet<>()).orElseGet(Collections::emptyList);
    }

    /**
     * Resolves the QNames of all types participating in a mixed Union.
     * Uses the base type for inline anonymous simple types.
     */
    public List<String> getUnionMemberTypes(QName typeName) {
        log.trace("Resolving union member types for '{}'", typeName);
        XsdSimpleType simpleType = findSimpleType(typeName);
        XsdUnion union = getUnionForType(simpleType);
        if (union == null) return Collections.emptyList();

        List<String> memberQNames = new ArrayList<>();

        List<String> memberTypes = union.getMemberTypesList();
        for (String part : memberTypes) {
            String resolvedName = resolveUnionMemberType(simpleType, part);
            log.trace(" -> Resolved member type '{}' to '{}'", part, resolvedName);
            memberQNames.add(resolvedName);
        }

        List<XsdSimpleType> inlineTypes = union.getUnionElements();
        log.trace(" -> Processing {} inline elements for base types", inlineTypes.size());
        for (XsdSimpleType inlineType : inlineTypes) {
            if (inlineType.getName() != null) continue;
            log.trace("    -> Inline element name: {}", inlineType.getName());
            if (inlineType.getRestriction() != null && inlineType.getRestriction().getBase() != null) {
                String baseResolved = resolveTypeQName(
                        null,
                        inlineType.getRestriction().getBase(),
                        safeGetTargetNamespace(simpleType),
                        inlineType.getRestriction());
                log.trace("    -> Found inline restriction base, resolved to: '{}'", baseResolved);
                memberQNames.add(baseResolved);
            } else {
                log.trace("    -> No restriction base found, defaulting to xs:string fallback");
                memberQNames.add("{http://www.w3.org/2001/XMLSchema}string"); // Absolute fallback
            }
        }

        log.debug(" -> Final member types for '{}': {}", typeName, memberQNames);
        return memberQNames;
    }

    /**
     * Extracts the local part of a QName.
     * Securely handles both standard XML prefixed QNames (e.g., "xs:string")
     * and resolved Clark QNames (e.g., "{http://www.w3.org/2001/XMLSchema}string").
     *
     * @param qname The qualified name.
     * @return The local part (e.g., "string").
     */
    private String localPart(String qname) {
        if (qname == null) return null;

        int closingBraceIdx = qname.lastIndexOf('}');
        if (closingBraceIdx >= 0) {
            return qname.substring(closingBraceIdx + 1);
        }

        int colonIdx = qname.indexOf(':');
        if (colonIdx >= 0) {
            return qname.substring(colonIdx + 1);
        }

        return qname;
    }

    /**
     * Resolves the Fully Qualified Name (FQN) of a type.
     * <p>
     * Strategies:
     * <ol>
     * <li><b>Raw String Check:</b> Fast path for built-in types (e.g. "xs:string", "int").</li>
     * <li><b>Object Link:</b> Use the linked {@link XsdSchema} from the object model if available.</li>
     * <li><b>Schema Context:</b> Resolve unqualified names only in the supplied schema namespace.</li>
     * <li><b>Fail Closed:</b> Preserve unresolved values when namespace identity is unavailable.</li>
     * </ol>
     *
     * @param typeDef       The XSD type definition object (may be null).
     * @param rawTypeString The raw value of the 'type' attribute (e.g. "xs:boolean", "tns:AnimalType").
     * @param defaultNs     The target namespace of the current file context.
     * @return The resolved QName (e.g., "{http://namespace}TypeName").
     */
    /**
     * Resolves a lexical QName using the namespace declarations visible from
     * the node that owns the reference. The parser's linked declaration is
     * useful as a fallback only when the lexical value is not available; it
     * must not replace the owning node's namespace context.
     */
    public String resolveTypeQName(
            XsdNamedElements typeDef,
            String rawTypeString,
            String defaultNs,
            XsdAbstractElement context) {

        if (rawTypeString != null) {
            if (rawTypeString.isBlank()) return unresolvedQName(rawTypeString);
            String local = localPart(rawTypeString);

            if (rawTypeString.startsWith("xs:") || rawTypeString.startsWith("xsd:")) {
                return isValidLocalName(local) ? "{" + XSD_NS + "}" + local : unresolvedQName(rawTypeString);
            }

            if (rawTypeString.startsWith("{")) {
                RegistryKey parsed = RegistryKey.parse(rawTypeString);
                String canonicalLocal = parsed == null ? null : normalizedLocalPart(parsed.localName());
                return parsed == null || canonicalLocal == null
                        ? unresolvedQName(rawTypeString)
                        : "{" + parsed.namespace() + "}" + canonicalLocal;
            }

            String prefix = prefixPart(rawTypeString);
            if (prefix == null && isLikelyBuiltInXsdType(local)) {
                return "{" + XSD_NS + "}" + local;
            }

            if (prefix != null) {
                String explicitNamespace = resolveExplicitPrefixNamespace(rawTypeString, context, defaultNs);
                return explicitNamespace == null || !isValidLocalName(local)
                        ? unresolvedQName(rawTypeString)
                        : "{" + explicitNamespace + "}" + local;
            }

            return defaultNs == null || defaultNs.isBlank()
                    ? unresolvedQName(rawTypeString)
                    : "{" + defaultNs + "}" + local;
        }

        if (typeDef != null) {
            if (typeDef instanceof XsdBuiltInDataType) {
                return "{" + XSD_NS + "}" + localPart(typeDef.getRawName());
            }

            XsdSchema schema = findOwningSchema(typeDef);
            if (schema != null) {
                String local = normalizedLocalPart(typeDef.getRawName());
                return local == null
                        ? unresolvedQName(typeDef.getRawName())
                        : "{" + schema.getTargetNamespace() + "}" + local;
            }
        }

        if (typeDef != null) {
            String rawName = typeDef.getRawName();
            if (rawName != null) {
                    return resolveTypeQName(null, rawName, defaultNs, typeDef);
            }
        }

        return unresolvedQName(typeDef == null ? null : typeDef.getRawName());
    }

    /**
     * Resolves the declared type of a global or imported element while
     * preserving the lexical QName recovered from parser attributes.
     *
     * <p>xmlet can materialize an element with a linked type declaration but
     * without retaining the raw {@code type} attribute in its attribute map.
     * The shared raw-attribute path also checks clone provenance and the
     * parser's typed accessor before falling back to the linked declaration.</p>
     */
    String resolveElementTypeQName(XsdElement element, String defaultNamespace) {
        if (element == null) return null;

        String rawType = rawAttributeValue(element, "type");
        XsdNamedElements typeDefinition = element.getTypeAsXsd();
        if (rawType == null && typeDefinition == null) return null;

        String owningNamespace = defaultNamespace;
        XsdSchema owningSchema = findOwningSchema(element);
        if (owningSchema != null && owningSchema.getTargetNamespace() != null
                && !owningSchema.getTargetNamespace().isBlank()) {
            owningNamespace = owningSchema.getTargetNamespace();
        }

        return resolveTypeQName(typeDefinition, rawType, owningNamespace, element);
    }

    String resolveElementSubstitutionGroupQName(XsdElement element, String defaultNamespace) {
        if (element == null) return null;

        String rawGroup = rawAttributeValue(element, "substitutionGroup");
        if (rawGroup == null && element.getXsdSubstitutionGroup() != null) {
            rawGroup = element.getXsdSubstitutionGroup().getRawName();
        }
        if (rawGroup == null) return null;

        String owningNamespace = safeGetTargetNamespace(element);
        return resolveReferenceQName(
                rawGroup,
                element,
                owningNamespace.isBlank() ? defaultNamespace : owningNamespace);
    }

    private String resolveReferenceQName(
            String rawQName,
            XsdAbstractElement context,
            String defaultNamespace) {
        if (rawQName == null || rawQName.isBlank()) return unresolvedQName(rawQName);

        if (rawQName.startsWith("{")) {
            RegistryKey parsed = RegistryKey.parse(rawQName);
            String local = parsed == null ? null : normalizedLocalPart(parsed.localName());
            return parsed == null || local == null
                    ? unresolvedQName(rawQName)
                    : "{" + parsed.namespace() + "}" + local;
        }

        String local = normalizedLocalPart(rawQName);
        String namespace = resolveReferenceNamespace(rawQName, context, defaultNamespace);
        return local == null || namespace == null
                ? unresolvedQName(rawQName)
                : "{" + namespace + "}" + local;
    }

    private String prefixPart(String rawQName) {
        if (rawQName == null) return null;
        int separator = rawQName.indexOf(':');
        return separator > 0 ? rawQName.substring(0, separator) : null;
    }

    private String normalizedLocalPart(String qname) {
        String local = localPart(qname);
        if (local != null && local.contains(":")) {
            local = localPart(local);
        }
        return isValidLocalName(local) ? local : null;
    }

    private String rawReference(XsdGroup group) {
        String rawName = group.getRawName();
        if (rawName != null && rawName.contains(":")) return rawName;
        String attributeReference = rawAttributeValue(group, "ref");
        return attributeReference != null ? attributeReference : rawName;
    }

    private String rawAttributeValue(XsdAbstractElement element, String attributeName) {
        XsdAbstractElement current = element;
        Set<XsdAbstractElement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            String rawValue = current.getAttributesMap().get(attributeName);
            if (rawValue != null && !rawValue.isBlank()) {
                return rawValue;
            }
            current = current.getCloneOf();
        }
        if ("type".equals(attributeName) && element instanceof XsdElement xsdElement) {
            return xsdElement.getType();
        }
        if ("type".equals(attributeName) && element instanceof XsdAttribute xsdAttribute) {
            return xsdAttribute.getType();
        }
        return null;
    }

    private String resolveReferenceNamespace(
            String rawQName,
            XsdAbstractElement context,
            String defaultNamespace) {
        String prefix = prefixPart(rawQName);
        if (prefix == null) {
            return defaultNamespace == null || defaultNamespace.isBlank() ? null : defaultNamespace;
        }
        return resolveExplicitPrefixNamespace(rawQName, context, defaultNamespace);
    }

    private String resolveExplicitPrefixNamespace(
            String rawQName,
            XsdAbstractElement context,
            String defaultNamespace) {
        String prefix = prefixPart(rawQName);
        if (prefix == null || prefix.isBlank() || "xs".equals(prefix) || "xsd".equals(prefix)) {
            return null;
        }
        String mapped = resolvePrefixInOwningSchema(prefix, context);
        if (mapped == null && defaultNamespace != null && !defaultNamespace.isBlank()) {
            mapped = resolvePrefixInSchema(prefix, defaultNamespace);
        }
        return mapped == null || mapped.isBlank() ? null : mapped;
    }

    private String resolvePrefixInOwningSchema(String prefix, XsdAbstractElement context) {
        XsdSchema schema = findOwningSchema(context);
        if (schema == null || schema.getNamespaces() == null) {
            return null;
        }
        NamespaceInfo namespace = schema.getNamespaces().get(prefix);
        return namespace == null ? null : namespace.getName();
    }

    private String resolvePrefixInSchema(String prefix, String defaultNamespace) {
        if (prefix == null || prefix.isBlank() || defaultNamespace == null) {
            return null;
        }
        return getSchemas(defaultNamespace)
                .map(XsdSchema::getNamespaces)
                .filter(Objects::nonNull)
                .map(namespaces -> namespaces.get(prefix))
                .filter(Objects::nonNull)
                .map(NamespaceInfo::getName)
                .filter(uri -> !uri.isBlank())
                .findFirst()
                .orElse(null);
    }

    private XsdSchema findOwningSchema(XsdAbstractElement element) {
        if (element == null) {
            return null;
        }

        XsdAbstractElement current = element;
        while (current != null) {
            XsdSchema owner = owningSchemas.get(current);
            if (owner != null) {
                return owner;
            }
            XsdAbstractElement parent = current.getParent();
            if (parent == current) {
                break;
            }
            current = parent;
        }

        return element.getXsdSchema();
    }

    private XsdSimpleType findSimpleType(QName typeQName) {
        return typeQName == null
                ? null
                : findGlobalSimpleType(typeQName.getNamespaceURI(), typeQName.getLocalPart());
    }

    private XsdSimpleType findSimpleType(RegistryKey typeKey) {
        return typeKey == null ? null : findGlobalSimpleType(typeKey.namespace(), typeKey.localName());
    }

    private RegistryKey simpleTypeKey(XsdSimpleType simpleType) {
        if (simpleType == null || simpleType.getName() == null) return null;
        String namespace = safeGetTargetNamespace(simpleType);
        return namespace == null || namespace.isBlank() ? null : RegistryKey.of(namespace, simpleType.getName());
    }

    private String resolveUnionMemberType(XsdSimpleType owner, String rawType) {
        return resolveTypeQName(owner, rawType, safeGetTargetNamespace(owner), owner);
    }

    /**
     * Returns the lexical XSD declaration name when xmlet has normalized the
     * parser-facing name. For example, xmlet may expose {@code Outer.model}
     * as {@code Outer_model}; dependency QNames must retain the lexical name.
     */
    String groupDeclarationName(XsdGroup group) {
        if (group == null) return null;
        String rawName = rawAttributeValue(group, "name");
        return rawName == null || rawName.isBlank() ? group.getName() : rawName;
    }

    private String attributeGroupDeclarationName(XsdAttributeGroup group) {
        if (group == null) return null;
        String rawName = rawAttributeValue(group, "name");
        return rawName == null || rawName.isBlank() ? group.getName() : rawName;
    }

    /**
     * xmlet materializes some imported group references as detached group
     * nodes and drops the original QName. Preserve that established behavior
     * only when exactly one global group can represent the parser-resolved
     * node; duplicate local names remain unresolved.
     */
    private XsdGroup findResolvedGroup(String name) {
        List<XsdGroup> matches = globalGroupsByName.get(name);
        return matches != null && matches.size() == 1 ? matches.getFirst() : null;
    }

    private XsdAttribute findGlobalAttribute(String namespace, String name) {
        return name == null ? null : globalAttributes.get(key(namespace, name));
    }

    private XsdAttribute findOnlyGlobalAttribute(String name) {
        List<XsdAttribute> matches = globalAttributesByName.get(name);
        return matches != null && matches.size() == 1 ? matches.getFirst() : null;
    }

    private String unresolvedQName(String rawQName) {
        return rawQName == null || rawQName.isBlank() ? "<unresolved-qname>" : rawQName;
    }

    private boolean isValidLocalName(String localName) {
        return localName != null && !localName.isBlank() && !localName.contains(":");
    }

    /**
     * Checks if a type name corresponds to a built-in XSD type.
     *
     * @param local The local name of the type.
     * @return {@code true} if it matches a known XSD built-in.
     */
    private boolean isLikelyBuiltInXsdType(String local) {
        if (local == null) return false;
        return RuneBuiltinRegistry.primitiveFamily(RegistryKey.of(XSD_NS, local)).isPresent();
    }

    private int parseOccurs(String max) {
        if (max == null || max.isBlank()) return 1;
        if ("unbounded".equalsIgnoreCase(max)) return UNBOUNDED_OCCURS;
        try {
            return Integer.parseInt(max);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int parseOccurs(Integer max) {
        return max == null ? 1 : max;
    }

    private Optional<XsdAbstractElement> particleOf(XsdAnnotatedElements parent) {
        if (parent instanceof XsdGroup group) {
            return Optional.ofNullable(group.getChildElement());
        }
        try {
            if (parent instanceof XsdComplexType complexType) {
                return firstParticle(complexType.getChildAsSequence(), complexType.getChildAsChoice(),
                        complexType.getChildAsAll(), complexType.getChildAsGroup());
            }
            if (parent instanceof XsdExtension extension) {
                return firstParticle(extension.getChildAsSequence(), extension.getChildAsChoice(),
                        extension.getChildAsAll(), extension.getChildAsGroup());
            }
        } catch (NullPointerException ignored) {
            // xmlet's typed child accessors dereference childElement when no particle is present.
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<XsdAbstractElement> firstParticle(XsdAbstractElement... particles) {
        return Arrays.stream(particles).filter(Objects::nonNull).findFirst();
    }

    private <T> Stream<T> safeStream(Stream<T> s) {
        return s == null ? Stream.empty() : s;
    }
}
