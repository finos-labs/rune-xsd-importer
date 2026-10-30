package org.finos.rune.importer.xsd.inspector;

import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorSimpleTypeNode;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import java.util.stream.Collectors;

/**
 * Inspection Facade for the XSD Object Model.
 * <p>
 * This class provides a high-level API to query XSD structures loaded by {@link XsdParser}.
 * It delegates complex traversal logic to {@link XsdParserInspectorHelper} while offering
 * simplified methods for retrieving namespaces, global types, groups, and structured members
 * needed for Rune model generation.
 * <p>
 * This Inspector abstracts away the details of the underlying {@code xmlet} library, providing
 * normalized return types (e.g., lists of strings or {@link InspectorNode} objects).
 */
public class XsdParserInspector {

    private final XsdParserInspectorHelper helper;

    /**
     * Constructs a new Inspector for the given parser instance.
     *
     * @param parser The XSD parser instance containing the loaded schema model.
     */
    public XsdParserInspector(XsdParser parser) {
        this.helper = new XsdParserInspectorHelper(parser);
    }

    /**
     * Retrieves all target namespaces found in the loaded schemas.
     * <p>
     * This includes the entry file's namespace and any imported namespaces.
     *
     * @return A set of namespace URI strings.
     */
    public Set<String> getLoadedNamespaces() {
        return helper.getSchemas()
                .map(XsdSchema::getTargetNamespace)
                .collect(Collectors.toSet());
    }

    /**
     * Extracts all xmlns:prefix declarations from the root schema of a given namespace.
     * @param targetNamespace The namespace to inspect.
     * @return A map of prefix to URI strings.
     */
    public Map<String, String> getSchemaNamespaces(String targetNamespace) {
        return helper.getSchemaNamespaces(targetNamespace);
    }

    public List<String> getAllGlobalSimpleTypes(String namespace) {
        return helper.getGlobalSimpleTypeKeys(namespace)
                .map(RegistryKey::localName)
                .toList();
    }

    public List<String> getAllGlobalComplexTypes(String namespace) {
        return helper.getGlobalComplexTypeKeys(namespace)
                .map(RegistryKey::localName)
                .toList();
    }

    /**
     * Retrieves ALL global elements from ALL files belonging to the given namespace.
     * This handles 'xs:include' by aggregating elements from every XsdSchema object
     * that maps to this targetNamespace.
     */
    public List<String> getAllGlobalElements(String namespace) {
        return helper.getGlobalElementKeys(namespace)
                .map(RegistryKey::localName)
                .toList();
    }

    /**
     * Retrieves all global groups from all files belonging to the given namespace.
     */
    public List<String> getAllGlobalGroups(String namespace) {
        return helper.getGlobalGroupKeys(namespace)
                .map(RegistryKey::localName)
                .toList();
    }

    public boolean isGlobalComplexType(RegistryKey key) {
        return helper.isGlobalComplexType(key);
    }

    public Set<XsdDeclaration> getDeclaredDeclarations() {
        return helper.getDeclaredDeclarations();
    }

    /** Returns the qualified references required by the loaded declarations. */
    public List<XsdDependency> getRequiredDependencies() {
        return helper.getRequiredDependencies();
    }

    /**
     * Returns lexical references that the parser could not resolve after loading the schema set.
     * These references must fail dependency preflight rather than be silently omitted from mapping.
     */
    public List<String> getUnsolvedReferences() {
        return helper.getUnsolvedReferences();
    }


    public boolean isAbstract(QName name) {
        XsdElement element = helper.findGlobalElement(name);
        if (element != null) return element.isAbstractObj();

        XsdComplexType type = helper.findGlobalComplexType(name);
        return type != null && type.isElementAbstract();
    }

    public boolean isSimpleTypeEnumeration(QName simpleTypeName) {
        return simpleTypeName != null && helper.isSimpleTypeEnumeration(simpleTypeName);
    }

    public RegistryKey getInlineProxyTypeKey(QName elementName) {
        XsdElement element = helper.findGlobalElement(elementName);
        if (element == null || element.getType() != null || element.getXsdComplexType() == null) return null;
        return resolvedKey(
                helper.getSingleGroupReference(element.getXsdComplexType()),
                "inline proxy type of element '" + elementName + "'");
    }

    /**
     * Determines if a Global Element should be mapped to a TypeAlias (simple wrapper)
     * instead of a full Data type.
     * Returns true if the element has a simple type (e.g. xs:string) and no complex structure.
     */
    public boolean isTypeAliasWrapperElement(QName elementName) {
        return helper.isVirtualTypeAliasElement(helper.findGlobalElement(elementName));
    }

    public boolean isNativeWrapperElement(QName elementName) {
        return helper.isNativeWrapperElement(helper.findGlobalElement(elementName));
    }

    public RegistryKey getElementSubstitutionGroupKey(QName elementName) {
        XsdElement element = helper.findGlobalElement(elementName);
        return resolvedKey(
                helper.resolveElementSubstitutionGroupQName(
                        element,
                        elementName == null ? null : elementName.getNamespaceURI()),
                "substitution-group head of element '" + elementName + "'");
    }

    public List<InspectorNode> getComplexTypeStructuredMembers(QName complexTypeName) {
        return helper.getStructuredMembers(helper.findGlobalComplexType(complexTypeName));
    }

    public List<InspectorNode> getElementInlineStructuredMembers(QName elementName) {
        return helper.getStructuredMembers(helper.findGlobalElement(elementName));
    }

    public List<InspectorNode> getGroupStructuredMembers(QName groupName) {
        return helper.getStructuredMembers(helper.findGlobalGroup(groupName));
    }

    public List<InspectorAttributeNode> getComplexTypeAttributeMembers(QName complexTypeName) {
        return helper.getStructuredAttributeMembers(helper.findGlobalComplexType(complexTypeName));
    }

    public List<InspectorAttributeNode> getElementInlineAttributeMembers(QName elementName) {
        XsdElement element = helper.findGlobalElement(elementName);
        if (element != null && element.getType() == null && element.getXsdComplexType() != null) {
            return helper.getStructuredAttributeMembers(element.getXsdComplexType());
        }
        return Collections.emptyList();
    }

    public InspectorSimpleTypeNode getStructuredSimpleType(QName name) {
        XsdSimpleType type = helper.findGlobalSimpleType(name);
        return type == null ? null : helper.getStructuredMembers(type);
    }

    public InspectorSimpleTypeNode getStructuredGlobalElementAsSimpleTypeNode(QName name) {
        XsdElement element = helper.findGlobalElement(name);
        return element == null ? null : helper.getStructuredMembers(element.getXsdSimpleType());
    }


    public RegistryKey getElementTypeKey(QName elementName) {
        XsdElement element = helper.findGlobalElement(elementName);
        return resolvedKey(
                helper.resolveElementTypeQName(element, elementName == null ? null : elementName.getNamespaceURI()),
                "type of element '" + elementName + "'");
    }

    public boolean isGlobalElement(String namespace, String elementName) {
        return helper.isGlobalElement(namespace, elementName);
    }

    public RegistryKey getComplexTypeBaseKey(QName complexTypeName) {
        return resolvedKey(
                helper.getComplexTypeBaseQName(helper.findGlobalComplexType(complexTypeName)),
                "base type of complex type '" + complexTypeName + "'");
    }

    public List<String> getSimpleTypeEnumValues(QName name) {
        return name == null ? Collections.emptyList() : helper.getSimpleTypeEnumValues(name);
    }

    public boolean isUnionType(QName name) {
        return name != null && helper.isUnionType(name);
    }

    public boolean isUnionOfEnums(QName name) {
        return name != null && helper.isUnionOfEnums(name);
    }

    public List<String> getUnionEnumValues(QName name) {
        return name == null ? Collections.emptyList() : helper.getUnionEnumValues(name);
    }

    public List<String> getUnionMemberTypes(QName name) {
        return name == null ? Collections.emptyList() : helper.getUnionMemberTypes(name);
    }

    private RegistryKey resolvedKey(String reference, String context) {
        RegistryKey key = RegistryKey.parse(reference);
        if (reference != null && key == null) {
            throw MappingResolutionException.invalidReference(reference, context);
        }
        return key;
    }

}
